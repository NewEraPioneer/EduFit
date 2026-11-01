package vn.edufit.discovery.application.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.edufit.ai.api.AiFeature;
import vn.edufit.ai.api.AiGateway;
import vn.edufit.ai.api.AiRequest;
import vn.edufit.discovery.domain.model.MatchScore;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.profile.api.dto.TutorDiscoveryProfileDto;
import vn.edufit.profile.api.fit.FitCheck;

@Service
public class MatchExplanationService {

  private static final Logger log = LoggerFactory.getLogger(MatchExplanationService.class);
  private static final Pattern EXPLANATION_LINE = Pattern.compile("^(\\d{1,2})\\s*\\|\\s*(.+)$");
  private static final int MAX_COMMENT_LENGTH = 180;
  private final AiGateway aiGateway;

  public MatchExplanationService(AiGateway aiGateway) {
    this.aiGateway = aiGateway;
  }

  public List<Explanation> explainMatches(UUID userId, LearningNeedView need,
      List<MatchScore> scores, List<TutorDiscoveryProfileDto> tutors) {
    if (scores.isEmpty()) return List.of();
    if (scores.size() != tutors.size()) throw new IllegalArgumentException("Mismatched candidates");
    var fallback = scores.stream().map(score -> new Explanation(ruleBased(score), false)).toList();
    var facts = new StringBuilder("NHU CẦU HỌC: ")
        .append("khối lớp=").append(need.educationLevelName())
        .append(", môn=").append(need.subjectName())
        .append(need.trackName() == null ? "" : " (" + need.trackName() + ")")
        .append(", mục tiêu=").append(need.goalTagName())
        .append(need.textbookSeriesName() == null ? "" : ", bộ sách=" + need.textbookSeriesName())
        .append(need.examProvinceName() == null ? "" : ", tỉnh dự thi=" + need.examProvinceName())
        .append(", hình thức=").append(need.mode())
        .append(", ngân sách mỗi buổi=").append(need.budgetMaxPerSession())
        .append('\n');
    for (int i = 0; i < scores.size(); i++) {
      var score = scores.get(i);
      var tutor = tutors.get(i).tutor();
      // Only structured facts cross the AI boundary; never bio, contact data, or names.
      facts.append("ỨNG VIÊN ").append(i + 1).append(": ")
          .append("loại gia sư=").append(tutor.tutorTypeVerified() ? tutor.tutorType() : "chưa xác nhận")
          .append(", số năm kinh nghiệm tự khai=").append(tutor.experienceYears())
          .append(", đánh giá=").append(tutor.ratingAvg()).append("/5 từ ").append(tutor.reviewCount())
          .append(", học phí gói dạy=").append(score.offering().pricePerSession())
          .append(", điểm tổng=").append(score.total())
          .append(", tiêu chí khớp=").append(labels(score, true))
          .append(", tiêu chí lệch=").append(labels(score, false))
          .append('\n');
    }
    try {
      var response = aiGateway.complete(AiRequest.of(userId, AiFeature.MATCH_EXPLANATION,
          """
          Bạn tư vấn chọn gia sư bằng tiếng Việt dựa đúng dữ kiện được cung cấp.
          Thứ tự ứng viên đã được hệ thống chấm điểm; không thay đổi thứ tự.
          Với MỖI ứng viên, viết một nhận xét riêng 1-2 câu, khoảng 120-160 ký tự, tuyệt đối không quá 180 ký tự.
          Trả lời câu hỏi: 'Với nhu cầu học của bạn, vì sao nên cân nhắc người này?'
          Chọn 1 điểm khác biệt có ý nghĩa từ 'tiêu chí khớp' so với các ứng viên khác;
          nếu có 'tiêu chí lệch' quan trọng thì nhắc ngắn gọn để phụ huynh trao đổi trước khi kết nối.
          Không liệt kê số liệu hoặc phần trăm. Mỗi người một góc đánh giá và cách mở câu riêng.
          Không suy đoán thành tích, phương pháp, bằng cấp hay khả năng đạt điểm khi không có dữ kiện;
          kinh nghiệm tự khai không phải kinh nghiệm đã xác minh. null là chưa có dữ liệu.
          Nếu hai hồ sơ thực sự tương đương, thừa nhận chưa đủ dữ kiện phân biệt, không bịa điểm riêng.
          Chỉ trả đúng một dòng cho mỗi ứng viên dạng: 1|lý do riêng của ứng viên 1
          Không Markdown, không xuống dòng bên trong một lý do, không thêm tiêu đề.
          """, facts.toString()));
      if (!response.generatedByModel() || response.text().isBlank()) return fallback;
      Map<Integer, String> explanations = new HashMap<>();
      var invalid = new HashSet<Integer>();
      for (String line : response.text().split("\\R")) {
        var match = EXPLANATION_LINE.matcher(line.trim());
        if (!match.matches()) continue;
        int index = Integer.parseInt(match.group(1)) - 1;
        String text = match.group(2).trim();
        if (index < 0 || index >= scores.size()) continue;
        if (text.length() < 15 || text.length() > MAX_COMMENT_LENGTH || explanations.putIfAbsent(index, text) != null) {
          invalid.add(index);
        }
      }
      // Repeated generic output is not accepted as personalized model output.
      var counts = explanations.values().stream().collect(Collectors.groupingBy(
          MatchExplanationService::normalizeExplanation, Collectors.counting()));
      return IntStream.range(0, scores.size()).mapToObj(i -> {
        String text = explanations.get(i);
        return text == null || invalid.contains(i) || counts.get(normalizeExplanation(text)) > 1
            ? fallback.get(i) : new Explanation(text, true);
      }).toList();
    } catch (RuntimeException ex) {
      log.warn(
          "Không thể tạo giải thích matching theo nhóm [type: {}, message: {}]",
          ex.getClass().getSimpleName(),
          ex.getMessage());
      return fallback;
    }
  }

  /** Nhận xét theo quy tắc khi không có AI: nêu tiêu chí lệch đầu tiên, hoặc xác nhận khớp toàn bộ. */
  public String ruleBased(MatchScore score) {
    var mismatch = score.checks().stream().filter(check -> !check.matched()).findFirst();
    if (mismatch.isPresent()) {
      return "Khớp " + score.matchedCount() + "/" + score.checks().size()
          + " tiêu chí; cần trao đổi trước: " + lowerFirst(mismatch.get().label()) + ".";
    }
    return "Khớp cả " + score.checks().size() + " tiêu chí bạn đặt ra: " + labels(score, true) + ".";
  }

  private static String labels(MatchScore score, boolean matched) {
    var picked = score.checks().stream().filter(check -> check.matched() == matched)
        .map(FitCheck::label).map(MatchExplanationService::lowerFirst).toList();
    return picked.isEmpty() ? "không có" : String.join("; ", picked);
  }

  private static String lowerFirst(String text) {
    return text.isEmpty() ? text : text.substring(0, 1).toLowerCase(Locale.ROOT) + text.substring(1);
  }

  private static String normalizeExplanation(String text) {
    // Same sentence with only different prices/ratings/years is still the same template.
    return text.toLowerCase(Locale.ROOT).replaceAll("\\d+(?:[.,]\\d+)*", "#")
        .replaceAll("[\\p{Punct}]+", " ").replaceAll("\\s+", " ").trim();
  }

  public record Explanation(String text, boolean aiGenerated) {}
}
