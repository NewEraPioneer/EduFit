package vn.edufit.profile.application.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.ai.api.AiFeature;
import vn.edufit.ai.api.AiGateway;
import vn.edufit.ai.api.AiRequest;
import vn.edufit.ai.api.AiResponse;
import vn.edufit.ai.api.AiUsageQuery;
import vn.edufit.profile.api.dto.CatalogRef;
import vn.edufit.profile.infra.persistence.entity.TutorProfile;
import vn.edufit.profile.infra.persistence.repository.TutorProfileRepository;
import vn.edufit.profile.web.request.GenerateTutorBioRequest;
import vn.edufit.profile.web.response.GenerateTutorBioResponse;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ForbiddenOperationException;

/**
 * Nghiệp vụ hỗ trợ gia sư soạn thảo tiểu sử (Bio Drafting) thông qua phân hệ AI Gateway (UC1.7).
 *
 * <p>Nguyên lý thiết kế & Quy tắc kiến trúc:
 * <ul>
 *   <li><b>Ports & Adapters (Hexagonal Architecture):</b> Chỉ tương tác với module {@code platform/ai}
 *       thông qua Inbound Port công khai {@link AiGateway} và {@link AiUsageQuery}. Tuyệt đối không
 *       truy cập tầng {@code application} hay {@code infra} nội bộ của AI.</li>
 *   <li><b>Chống rập khuôn (Anti-Template):</b> Ngăn chặn các câu mở đầu sáo rỗng, tạo sự đa dạng,
 *       tự nhiên và cá nhân hóa theo từng phong cách giảng dạy.</li>
 *   <li><b>Ưu tiên hồ sơ khi có xung đột (Conflict Resolution):</b> Thông tin hồ sơ chính thức
 *       (môn học, cấp lớp, số năm kinh nghiệm) là nguồn chân lý tối cao (Source of Truth).
 *       Mọi chi tiết trong bản nháp tự viết nếu xung đột với hồ sơ sẽ bị ghi đè theo hồ sơ.</li>
 *   <li><b>Kiểm soát hạn mức (BR-10 Quota Enforcement):</b> Giới hạn tối đa 10 lượt gọi/giờ cho mỗi
 *       tài khoản gia sư nhằm chống spam và tối ưu tài nguyên.</li>
 *   <li><b>Bảo vệ dữ liệu cá nhân (NFR-09 Data Minimisation):</b> Loại bỏ mọi thông tin PII
 *       (SĐT, Email, CCCD, địa chỉ chi tiết) trước khi tạo prompt gửi LLM.</li>
 *   <li><b>Cơ chế suy thoái mềm (FR-06 Graceful Fallback):</b> Tự động chuyển đổi sang mẫu quy tắc
 *       chuẩn hóa nếu AI Gateway gặp lỗi mạng hoặc quá hạn 15 giây (NFR-10).</li>
 * </ul>
 */
@Service
public class TutorBioService {

  private static final Logger log = LoggerFactory.getLogger(TutorBioService.class);
  public static final int HOURLY_QUOTA_LIMIT = 10;

  private final AiGateway aiGateway;
  private final AiUsageQuery aiUsageQuery;
  private final TutorProfileRepository tutorProfileRepository;
  private final TutorProfileAssembler tutorProfileAssembler;

  public TutorBioService(
      AiGateway aiGateway,
      AiUsageQuery aiUsageQuery,
      TutorProfileRepository tutorProfileRepository,
      TutorProfileAssembler tutorProfileAssembler
  ) {
    this.aiGateway = aiGateway;
    this.aiUsageQuery = aiUsageQuery;
    this.tutorProfileRepository = tutorProfileRepository;
    this.tutorProfileAssembler = tutorProfileAssembler;
  }

  @Transactional(readOnly = true)
  public GenerateTutorBioResponse generateBio(CurrentUser currentUser, GenerateTutorBioRequest request) {
    ensureAuthenticated(currentUser);
    UUID userId = currentUser.getUserId();

    // 1. Kiểm tra hạn mức sử dụng (BR-10)
    long requestsInLastHour = aiUsageQuery.countRequestsSince(
        userId,
        AiFeature.TUTOR_BIO,
        Instant.now().minus(1, ChronoUnit.HOURS)
    );

    // 2. Thu thập và làm giàu dữ liệu chuyên môn của gia sư
    TutorContext context = resolveTutorContext(userId, request);

    if (requestsInLastHour >= HOURLY_QUOTA_LIMIT) {
      log.info("Gia sư [{}] đã vượt hạn mức gọi AI trong giờ ({} >= {}). Kích hoạt Fallback.",
          userId, requestsInLastHour, HOURLY_QUOTA_LIMIT);
      String fallbackBio = ruleBasedBio(context);
      return new GenerateTutorBioResponse(fallbackBio, false, 0);
    }

    // 3. Chuẩn bị prompt sư phạm chống rập khuôn và giải quyết xung đột
    String systemPrompt = """
        Bạn là chuyên gia cố vấn sư phạm và biên tập viên nội dung cao cấp của nền tảng giáo dục EduFit tại Việt Nam.
        Nhiệm vụ của bạn là soạn thảo hoặc nâng cấp tiểu sử (Bio) cho Gia sư thành một đoạn giới thiệu cuốn hút, truyền cảm hứng và đậm chất nhân văn, phản ánh chân thực cá tính giảng dạy của gia sư.
        
        CÁC QUY TẮC BẮT BUỘC:
        1. CHỐNG RẬP KHUÔN & TỰ NHIÊN HÓA (ANTI-TEMPLATE):
           - TUYỆT ĐỐI KHÔNG mở đầu theo các khuôn mẫu sáo rỗng hoặc lặp lại câu từ máy móc (như "Chào các em và quý phụ huynh tôi là...", "Tôi là gia sư tâm huyết với...").
           - Đa dạng hóa câu mở đầu: có thể bắt đầu từ quan điểm giáo dục, niềm đam mê môn học, phương pháp tiếp cận học sinh, hoặc trực tiếp nêu bật giá trị mang lại.
           - Hành văn tự nhiên, mượt mà, giàu cảm xúc, thể hiện sự tận tâm và am hiểu tâm lý học trò.
        
        2. CHẮT LỌC VÀ PHÁT TRIỂN TỪ BẢN NHÁP CỦA GIA SƯ (NẾU CÓ):
           - Nếu gia sư có cung cấp bản thảo/ý tưởng tự viết (Current Draft Bio): Hãy trân trọng và giữ lại giọng điệu cá nhân, các ví dụ, phương pháp sư phạm riêng hoặc thành tích có thật mà gia sư đã chia sẻ, sau đó trau chuốt câu từ để tỏa sáng nhất.
        
        3. NGUYÊN TẮC ƯU TIÊN THÔNG TIN HỒ SƠ CHÍNH THỨC KHI CÓ XUNG ĐỘT (STRICT CONFLICT RESOLUTION):
           - Dữ liệu hồ sơ chính thức (Tên, Môn học, Khối lớp, Số năm kinh nghiệm) là NGUỒN CHÂN LÝ TỐI CAO (SOURCE OF TRUTH).
           - BẤT KỲ khi nào thông tin trong bản nháp tự viết mâu thuẫn với dữ liệu hồ sơ chính thức (ví dụ: bản nháp nói số năm kinh nghiệm hoặc môn dạy khác với hồ sơ), BẮT BUỘC PHẢI ƯU TIÊN VÀ TUÂN THEO DỮ LIỆU HỒ SƠ CHÍNH THỨC. Tuyệt đối không được giữ dữ liệu sai lệch từ bản nháp.
        
        4. BẢO VỆ DỮ LIỆU RIÊNG TƯ (NFR-09):
           - Tuyệt đối không đưa số điện thoại, email, link mạng xã hội, CCCD, địa chỉ nhà chi tiết vào nội dung.
        
        5. ĐỊNH DẠNG ĐẦU RA:
           - Trả về văn bản thuần túy (Plain text), không tiêu đề Markdown lớn (#, ##), không gạch đầu dòng dài. Độ dài lý tưởng từ 120 đến 220 từ. Ngắt đoạn tự nhiên (1-2 đoạn văn ngắn) để hiển thị đẹp mắt trên thẻ hồ sơ.
        """;

    String userPrompt = buildUserPrompt(context);

    // 4. Gửi yêu cầu qua AI Gateway
    try {
      AiRequest aiRequest = AiRequest.of(
          userId,
          AiFeature.TUTOR_BIO,
          systemPrompt,
          userPrompt
      );

      AiResponse aiResponse = aiGateway.complete(aiRequest);
      int remainingQuota = Math.max(0, (int) (HOURLY_QUOTA_LIMIT - requestsInLastHour - 1));
      log.info("Sinh Bio thành công qua AI cho gia sư [{}] trong {}ms", userId, aiResponse.latencyMs());
      return new GenerateTutorBioResponse(aiResponse.text(), true, remainingQuota);

    } catch (RuntimeException ex) {
      log.warn("Không thể sinh Bio qua AI cho gia sư [{}] (lỗi: {}). Sử dụng fallback template.",
          userId, ex.getMessage());
      String fallbackBio = ruleBasedBio(context);
      return new GenerateTutorBioResponse(fallbackBio, false, 0);
    }
  }

  private TutorContext resolveTutorContext(UUID userId, GenerateTutorBioRequest request) {
    Optional<TutorProfile> profileOpt = tutorProfileRepository.findByUserId(userId);

    // Tên hiển thị
    String displayName = (request != null && request.displayName() != null && !request.displayName().isBlank())
        ? request.displayName().trim()
        : profileOpt.map(TutorProfile::getDisplayName).orElse("Gia sư EduFit");

    // Số năm kinh nghiệm
    int experienceYears = (request != null && request.experienceYears() != null && request.experienceYears() >= 0)
        ? request.experienceYears()
        : profileOpt.map(p -> p.getExperienceYears() != null ? (int) p.getExperienceYears() : 1).orElse(1);

    // Môn học & Khối lớp
    List<String> subjects = new ArrayList<>();
    List<String> educationLevels = new ArrayList<>();

    if (request != null && request.subjects() != null && !request.subjects().isEmpty()) {
      subjects.addAll(request.subjects());
    }
    if (request != null && request.educationLevels() != null && !request.educationLevels().isEmpty()) {
      educationLevels.addAll(request.educationLevels());
    }

    // Nếu request chưa có môn/cấp học, nạp từ cơ sở dữ liệu
    if (subjects.isEmpty() && profileOpt.isPresent()) {
      var offerings = tutorProfileAssembler.offeringsOf(profileOpt.get().getTutorId());
      subjects = offerings.stream()
          .map(offering -> offering.trackName() == null
              ? offering.subjectName() : offering.subjectName() + " (" + offering.trackName() + ")")
          .distinct().toList();
      educationLevels = offerings.stream()
          .flatMap(offering -> offering.levels().stream())
          .map(CatalogRef::name).distinct().toList();
    }

    String tone = request != null ? request.resolvedTone() : "friendly";
    String focusArea = request != null ? request.resolvedFocusArea() : "exam";
    String customPrompt = (request != null && request.customPrompt() != null) ? request.customPrompt().trim() : "";

    // Bản nháp bio người dùng đã viết
    String currentBio = (request != null && request.currentBio() != null && !request.currentBio().isBlank())
        ? request.currentBio().trim()
        : profileOpt.map(TutorProfile::getBio).filter(b -> !b.isBlank()).orElse("");

    return new TutorContext(displayName, subjects, educationLevels, experienceYears, tone, focusArea, customPrompt, currentBio);
  }

  private String buildUserPrompt(TutorContext context) {
    String subjectsText = context.subjects.isEmpty() ? "các môn học phổ thông" : String.join(", ", context.subjects);
    String levelsText = context.educationLevels.isEmpty() ? "các khối lớp phổ thông" : String.join(", ", context.educationLevels);

    String toneDescription = switch (context.tone.toLowerCase()) {
      case "professional" -> "Chuyên nghiệp, chuẩn mực sư phạm, tự tin và giàu chuyên môn";
      case "concise" -> "Ngắn gọn, súc tích, đi thẳng vào phương pháp giảng dạy và cam kết";
      default -> "Thân thiện, gần gũi, kiên nhẫn, mang lại cảm giác an tâm cho học sinh và phụ huynh";
    };

    String focusDescription = switch (context.focusArea.toLowerCase()) {
      case "foundation" -> "Lấy lại căn bản, xóa mất gốc và củng cố kiến thức nền tảng vững chắc";
      case "mindset" -> "Rèn luyện tư duy tự học, phương pháp giải quyết bài tập logic và chủ động";
      default -> "Luyện thi chuyên sâu, bám sát ma trận đề thi và bứt phá điểm số cao";
    };

    StringBuilder sb = new StringBuilder();
    sb.append("=== THÔNG TIN HỒ SƠ CHÍNH THỨC (SOURCE OF TRUTH - BẮT BUỘC TUÂN THỦ) ===\n");
    sb.append(String.format("- Tên gia sư: %s\n", context.displayName));
    sb.append(String.format("- Môn học chính thức: %s\n", subjectsText));
    sb.append(String.format("- Khối lớp chính thức: %s\n", levelsText));
    sb.append(String.format("- Số năm kinh nghiệm chính thức: %d năm\n", context.experienceYears));
    sb.append(String.format("- Phong cách giọng văn (Tone): %s\n", toneDescription));
    sb.append(String.format("- Định hướng giảng dạy trọng tâm: %s\n", focusDescription));

    if (!context.customPrompt.isBlank()) {
      sb.append(String.format("- Điểm nhấn bổ sung từ gia sư: %s\n", context.customPrompt));
    }

    if (!context.currentBio.isBlank()) {
      sb.append("\n=== NỘI DUNG / Ý TƯỞNG BẢN NHÁP GIA SƯ ĐÃ TỰ VIẾT (DRAFT BIO) ===\n");
      sb.append("\"\"\"\n").append(context.currentBio).append("\n\"\"\"\n");
      sb.append("LƯU Ý QUAN TRỌNG: Hãy chắt lọc và phát triển ý tưởng từ bản nháp trên của gia sư để đoạn Bio giữ được dấu ấn cá nhân tự nhiên. NẾU bản nháp có bất kỳ thông tin nào xung đột với THÔNG TIN HỒ SƠ CHÍNH THỨC ở trên (về số năm kinh nghiệm, môn dạy, khối lớp), BẮT BUỘC PHẢI ƯU TIÊN VÀ SỬA THEO THÔNG TIN HỒ SƠ CHÍNH THỨC.\n");
    } else {
      sb.append("\n(Gia sư chưa viết bản nháp, hãy sáng tạo một đoạn tiểu sử ấn tượng, không rập khuôn dựa trên các dữ kiện hồ sơ trên).\n");
    }

    sb.append("\nHãy viết đoạn tiểu sử hoàn chỉnh, tự nhiên và cuốn hút, không dùng các mẫu câu sáo rỗng.");
    return sb.toString();
  }

  public String ruleBasedBio(TutorContext context) {
    String name = context.displayName;
    String subjectsText = context.subjects.isEmpty() ? "các môn học" : String.join(", ", context.subjects);
    String levelsText = context.educationLevels.isEmpty() ? "các khối lớp" : String.join(", ", context.educationLevels);
    String expText = context.experienceYears > 0 ? context.experienceYears + " năm" : "nhiều năm";

    // Nếu gia sư đã có bản nháp, ưu tiên giữ lại ý chính từ bản nháp nhưng chuẩn hóa số năm và môn học
    if (!context.currentBio.isBlank()) {
      return String.format(
          "%s Với %s kinh nghiệm chuyên môn môn %s (%s), tôi luôn nỗ lực đồng hành giúp học sinh thấu hiểu bản chất bài học và tự tin bứt phá thành tích.",
          context.currentBio.trim(), expText, subjectsText, levelsText
      );
    }

    if ("professional".equalsIgnoreCase(context.tone)) {
      if ("foundation".equalsIgnoreCase(context.focusArea)) {
        return String.format(
            "Giáo viên chuyên môn môn %s (%s) với %s kinh nghiệm sư phạm. Phương pháp giảng dạy khoa học: khảo sát năng lực ban đầu, xây dựng lộ trình lấp lỗ hổng kiến thức cá nhân hóa, rèn nề nếp học tập nghiêm túc và có báo cáo tiến độ chi tiết tới phụ huynh sau từng tuần.",
            subjectsText, levelsText, expText
        );
      } else if ("mindset".equalsIgnoreCase(context.focusArea)) {
        return String.format(
            "Chuyên gia giảng dạy môn %s (%s) với %s kinh nghiệm đào tạo học sinh giỏi. Triết lý đào tạo hướng đến xây dựng nền tảng tư duy độc lập, chuẩn hóa phương pháp giải toán/khoa học hiện đại và phát triển tối đa năng lực cá nhân của học sinh.",
            subjectsText, levelsText, expText
        );
      } else {
        return String.format(
            "Cử nhân chuyên ngành Sư phạm với %s kinh nghiệm luyện thi môn %s cấp %s. Giáo trình giảng dạy được thiết kế chuẩn hóa, bám sát ma trận đề thi chính thức, chú trọng rèn luyện kỹ năng phân tích đề, xử lý câu hỏi phân hóa và cam kết mục tiêu bứt phá điểm số cao.",
            expText, subjectsText, levelsText
        );
      }
    } else if ("concise".equalsIgnoreCase(context.tone)) {
      if ("foundation".equalsIgnoreCase(context.focusArea)) {
        return String.format(
            "Gia sư %s (%s) - %s kinh nghiệm. Chuyên củng cố căn bản nhanh, xóa mất gốc sau 4-6 tuần, phương pháp dễ hiểu, tận tâm và trách nhiệm cao.",
            subjectsText, levelsText, expText
        );
      } else if ("mindset".equalsIgnoreCase(context.focusArea)) {
        return String.format(
            "Gia sư %s (%s) (%s KN). Đào tạo tư duy logic, đồng hành 1-1 kiên nhẫn, rèn kỹ năng tự học và giải bài tập chuẩn xác. Định kỳ gửi báo cáo tới phụ huynh.",
            subjectsText, levelsText, expText
        );
      } else {
        return String.format(
            "Gia sư %s (%s) - %s kinh nghiệm. Bám sát cấu trúc đề thi, rèn kỹ năng làm bài nhanh và chính xác, cam kết tiến bộ rõ rệt qua từng tháng học.",
            subjectsText, levelsText, expText
        );
      }
    } else {
      // friendly
      if ("foundation".equalsIgnoreCase(context.focusArea)) {
        return String.format(
            "Chào các em học sinh và quý phụ huynh! Thầy/Cô là %s, chuyên giảng dạy %s (%s). Với %s kinh nghiệm, phong cách giảng dạy của tôi luôn lấy sự kiên nhẫn và thấu hiểu làm trọng tâm, giúp các em xóa bỏ nỗi sợ mất gốc, tìm lại niềm vui và sự tự tin trong từng buổi học!",
            name, subjectsText, levelsText, expText
        );
      } else if ("mindset".equalsIgnoreCase(context.focusArea)) {
        return String.format(
            "Chào các em và quý phụ huynh! Tôi là %s, đồng hành cùng học sinh %s môn %s. Với %s kinh nghiệm giảng dạy, tôi không chỉ truyền đạt kiến thức mà còn rèn luyện tư duy tự học, phương pháp giải quyết vấn đề logic để các em chủ động bứt phá thành tích lâu dài.",
            name, levelsText, subjectsText, expText
        );
      } else {
        return String.format(
            "Chào các em và quý phụ huynh! Tôi là %s, gia sư chuyên trách môn %s (%s). Với %s đồng hành cùng nhiều lứa học sinh, tôi luôn kiên nhẫn giảng giải trực quan, biến kiến thức phức tạp thành bài học gần gũi, giúp các em tự tin vượt qua các kỳ thi quan trọng với điểm số cao nhất!",
            name, subjectsText, levelsText, expText
        );
      }
    }
  }

  private void ensureAuthenticated(CurrentUser currentUser) {
    if (currentUser == null || currentUser.getUserId() == null) {
      throw new ForbiddenOperationException("Yêu cầu cần được xác thực danh tính người dùng.");
    }
  }

  public record TutorContext(
      String displayName,
      List<String> subjects,
      List<String> educationLevels,
      int experienceYears,
      String tone,
      String focusArea,
      String customPrompt,
      String currentBio
  ) {}
}
