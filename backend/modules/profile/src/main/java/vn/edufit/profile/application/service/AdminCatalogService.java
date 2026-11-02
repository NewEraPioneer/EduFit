package vn.edufit.profile.application.service;

import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.profile.infra.persistence.entity.Subject;
import vn.edufit.profile.infra.persistence.repository.SubjectRepository;
import vn.edufit.profile.web.request.CreateSubjectRequest;
import vn.edufit.profile.web.response.SubjectAdminResponse;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ResourceConflictException;

/**
 * Service nghiệp vụ quản lý Danh mục Môn học (Admin Catalog) dành cho Quản trị viên.
 *
 * <p>Đặc tính kỹ thuật:
 * <ul>
 *   <li><b>Soft-Toggle:</b> Chỉ bật/tắt cờ {@code isActive}, nghiêm cấm xóa cứng để bảo vệ toàn vẹn khóa ngoại CSDL.</li>
 *   <li><b>Type-Safe Enriching:</b> Bổ sung mã nhận diện (code/slug), phân loại (category) và mô tả cho môn học.</li>
 * </ul>
 */
@Service
public class AdminCatalogService {

  private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
  private static final Pattern WHITESPACE = Pattern.compile("[\\s]+");
  private static final Instant SYSTEM_INIT_TIME = Instant.parse("2026-01-01T00:00:00Z");

  private final SubjectRepository subjectRepository;
  private final Map<Integer, SubjectExtraMetadata> metadataStore = new ConcurrentHashMap<>();

  public AdminCatalogService(SubjectRepository subjectRepository) {
    this.subjectRepository = subjectRepository;
  }

  @Transactional(readOnly = true)
  public List<SubjectAdminResponse> getAllSubjects() {
    return subjectRepository.findAllByOrderByNameAsc()
        .stream()
        .map(this::toAdminResponse)
        .toList();
  }

  @Transactional
  public SubjectAdminResponse createSubject(CreateSubjectRequest request) {
    String name = request.name().trim();
    if (subjectRepository.existsByName(name)) {
      throw new ResourceConflictException("Môn học '" + name + "' đã tồn tại trong hệ thống");
    }

    Subject subject = new Subject(name, true);
    Subject saved = subjectRepository.save(subject);

    String code = (request.code() != null && !request.code().isBlank())
        ? request.code().trim().toUpperCase(Locale.ROOT)
        : toSlug(name).replace("-", "_").toUpperCase(Locale.ROOT);

    String slug = (request.slug() != null && !request.slug().isBlank())
        ? request.slug().trim().toLowerCase(Locale.ROOT)
        : toSlug(name);

    String category = (request.category() != null && !request.category().isBlank())
        ? request.category().trim()
        : inferCategory(name);

    String description = (request.description() != null && !request.description().isBlank())
        ? request.description().trim()
        : "Môn học " + name + " trong chương trình đào tạo EduFit";

    SubjectExtraMetadata metadata = new SubjectExtraMetadata(
        code,
        slug,
        category,
        description,
        Instant.now()
    );
    metadataStore.put(saved.getSubjectId(), metadata);

    return toAdminResponse(saved);
  }

  @Transactional
  public SubjectAdminResponse toggleSubjectStatus(Integer id) {
    Subject subject = subjectRepository.findById(id)
        .orElseThrow(() -> EntityNotFoundException.of("Subject", id));

    boolean newStatus = !Boolean.TRUE.equals(subject.getIsActive());
    subject.setIsActive(newStatus);
    Subject saved = subjectRepository.save(subject);

    return toAdminResponse(saved);
  }

  @Transactional
  public SubjectAdminResponse updateSubjectStatus(Integer id, Boolean isActive) {
    Subject subject = subjectRepository.findById(id)
        .orElseThrow(() -> EntityNotFoundException.of("Subject", id));

    boolean newStatus = (isActive != null) ? isActive : !Boolean.TRUE.equals(subject.getIsActive());
    subject.setIsActive(newStatus);
    Subject saved = subjectRepository.save(subject);

    return toAdminResponse(saved);
  }

  private SubjectAdminResponse toAdminResponse(Subject subject) {
    Integer id = subject.getSubjectId();
    SubjectExtraMetadata extra = metadataStore.get(id);

    String code = (extra != null && extra.code() != null)
        ? extra.code()
        : toSlug(subject.getName()).replace("-", "_").toUpperCase(Locale.ROOT);

    String slug = (extra != null && extra.slug() != null)
        ? extra.slug()
        : toSlug(subject.getName());

    String category = (extra != null && extra.category() != null)
        ? extra.category()
        : inferCategory(subject.getName());

    String description = (extra != null && extra.description() != null)
        ? extra.description()
        : "Môn học " + subject.getName() + " trong danh mục đào tạo EduFit";

    Instant createdAt = (extra != null && extra.createdAt() != null)
        ? extra.createdAt()
        : SYSTEM_INIT_TIME;

    return new SubjectAdminResponse(
        id,
        subject.getName(),
        code,
        slug,
        category,
        description,
        Boolean.TRUE.equals(subject.getIsActive()),
        createdAt
    );
  }

  private static String toSlug(String input) {
    if (input == null) return "";
    String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
    String noMarks = normalized.replaceAll("\\p{M}", "");
    noMarks = noMarks.replace("đ", "d").replace("Đ", "D");
    String noWhitespace = WHITESPACE.matcher(noMarks).replaceAll("-");
    String normalizedSlug = NONLATIN.matcher(noWhitespace).replaceAll("");
    return normalizedSlug.toLowerCase(Locale.ROOT).replaceAll("^-+|-+$", "");
  }

  private static String inferCategory(String name) {
    String lower = name.toLowerCase(Locale.ROOT);
    if (lower.contains("toán") || lower.contains("lý") || lower.contains("hóa")
        || lower.contains("sinh") || lower.contains("tin")) {
      return "Khoa học tự nhiên";
    }
    if (lower.contains("văn") || lower.contains("sử") || lower.contains("địa")
        || lower.contains("gdcd") || lower.contains("kinh tế") || lower.contains("pháp luật")) {
      return "Khoa học xã hội";
    }
    if (lower.contains("tiếng") || lower.contains("anh") || lower.contains("nhật")
        || lower.contains("hàn") || lower.contains("trung") || lower.contains("pháp")) {
      return "Ngoại ngữ";
    }
    return "Chung";
  }

  private record SubjectExtraMetadata(
      String code,
      String slug,
      String category,
      String description,
      Instant createdAt
  ) {}
}
