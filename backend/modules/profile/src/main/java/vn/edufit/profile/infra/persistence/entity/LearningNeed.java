package vn.edufit.profile.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Nhu cầu học của một học sinh: tự lưu khối lớp, môn, nhánh, đúng một mục tiêu và các điều kiện
 * học (hình thức, khu vực, ngân sách, lịch). Không phụ thuộc vào hồ sơ để có thể thay đổi độc lập.
 */
@Entity
@Table(name = "learning_need")
public class LearningNeed {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "need_id", updatable = false, nullable = false)
  private UUID needId;

  @Column(name = "student_id", nullable = false, updatable = false)
  private UUID studentId;

  @Column(name = "created_by", nullable = false, updatable = false)
  private UUID createdBy;

  @Column(name = "education_level_id")
  private Integer educationLevelId;

  @Column(name = "subject_id", nullable = false)
  private Integer subjectId;

  @Column(name = "track_id")
  private Integer trackId;

  @Column(name = "goal_tag_id")
  private Integer goalTagId;

  @Column(name = "textbook_series_id")
  private Integer textbookSeriesId;

  @Column(name = "exam_province_id")
  private Integer examProvinceId;

  @Column(name = "target_school_id")
  private Integer targetSchoolId;

  @Enumerated(EnumType.STRING)
  @Column(name = "mode", length = 20)
  private TeachingMode mode;

  @Column(name = "province_id")
  private Integer provinceId;

  @Column(name = "district_id")
  private Integer districtId;

  @Column(name = "budget_max_per_session")
  private Long budgetMaxPerSession;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private NeedStatus status = NeedStatus.INCOMPLETE;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected LearningNeed() {
    // JPA required
  }

  public LearningNeed(UUID studentId, UUID createdBy, Integer subjectId) {
    this.studentId = studentId;
    this.createdBy = createdBy;
    this.subjectId = subjectId;
  }

  /** Ghi đè phân loại đã được danh mục kiểm tra và chuẩn hóa. */
  public void classify(
      Integer educationLevelId,
      Integer trackId,
      Integer goalTagId,
      Integer textbookSeriesId,
      Integer examProvinceId,
      Integer targetSchoolId) {
    this.educationLevelId = educationLevelId;
    this.trackId = trackId;
    this.goalTagId = goalTagId;
    this.textbookSeriesId = textbookSeriesId;
    this.examProvinceId = examProvinceId;
    this.targetSchoolId = targetSchoolId;
  }

  /** Ghi đè điều kiện học; khu vực chỉ giữ lại khi có học trực tiếp. */
  public void arrange(TeachingMode mode, Integer provinceId, Integer districtId, Long budgetMaxPerSession) {
    this.mode = mode;
    boolean inPerson = mode == TeachingMode.OFFLINE || mode == TeachingMode.BOTH;
    this.provinceId = inPerson ? provinceId : null;
    this.districtId = inPerson ? districtId : null;
    this.budgetMaxPerSession = budgetMaxPerSession;
  }

  public void markStatus(NeedStatus status) {
    this.status = status;
    this.updatedAt = Instant.now();
  }

  public UUID getNeedId() {
    return needId;
  }

  public UUID getStudentId() {
    return studentId;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Integer getEducationLevelId() {
    return educationLevelId;
  }

  public Integer getSubjectId() {
    return subjectId;
  }

  public Integer getTrackId() {
    return trackId;
  }

  public Integer getGoalTagId() {
    return goalTagId;
  }

  public Integer getTextbookSeriesId() {
    return textbookSeriesId;
  }

  public Integer getExamProvinceId() {
    return examProvinceId;
  }

  public Integer getTargetSchoolId() {
    return targetSchoolId;
  }

  public TeachingMode getMode() {
    return mode;
  }

  public Integer getProvinceId() {
    return provinceId;
  }

  public Integer getDistrictId() {
    return districtId;
  }

  public Long getBudgetMaxPerSession() {
    return budgetMaxPerSession;
  }

  public NeedStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
