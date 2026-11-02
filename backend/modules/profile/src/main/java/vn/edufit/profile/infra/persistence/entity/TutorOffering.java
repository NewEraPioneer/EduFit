package vn.edufit.profile.infra.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.annotations.BatchSize;

/**
 * Gói dạy: một môn (+ nhánh) với các khối lớp, mục tiêu, bộ sách, tỉnh luyện đề và học phí riêng.
 */
@Entity
@Table(name = "tutor_offering")
public class TutorOffering {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "offering_id", updatable = false, nullable = false)
  private UUID offeringId;

  @Column(name = "tutor_id", nullable = false, updatable = false)
  private UUID tutorId;

  @Column(name = "subject_id", nullable = false)
  private Integer subjectId;

  @Column(name = "track_id")
  private Integer trackId;

  @Column(name = "price_per_session", nullable = false)
  private Long pricePerSession;

  @BatchSize(size = 50)
  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "tutor_offering_level", joinColumns = @JoinColumn(name = "offering_id"))
  @Column(name = "level_id")
  private Set<Integer> levelIds = new HashSet<>();

  @BatchSize(size = 50)
  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "tutor_offering_goal", joinColumns = @JoinColumn(name = "offering_id"))
  private Set<OfferingGoal> goals = new HashSet<>();

  @BatchSize(size = 50)
  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "tutor_offering_textbook", joinColumns = @JoinColumn(name = "offering_id"))
  @Column(name = "series_id")
  private Set<Integer> textbookIds = new HashSet<>();

  @BatchSize(size = 50)
  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "tutor_offering_exam_province", joinColumns = @JoinColumn(name = "offering_id"))
  @Column(name = "province_id")
  private Set<Integer> examProvinceIds = new HashSet<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected TutorOffering() {
    // JPA required
  }

  public TutorOffering(UUID tutorId, Integer subjectId, Integer trackId) {
    this.tutorId = tutorId;
    this.subjectId = subjectId;
    this.trackId = trackId;
  }

  /**
   * Ghi đè nội dung gói. Mục tiêu premium đã được duyệt bằng chứng giữ nguyên trạng thái duyệt;
   * mục tiêu mới thêm luôn bắt đầu ở trạng thái chưa xác minh.
   */
  public void define(
      Collection<Integer> levelIds,
      Collection<Integer> goalTagIds,
      Collection<Integer> textbookIds,
      Collection<Integer> examProvinceIds,
      long pricePerSession) {
    Map<Integer, Boolean> verified = goals.stream()
        .collect(Collectors.toMap(OfferingGoal::getGoalTagId, OfferingGoal::isEvidenceVerified));
    this.levelIds.clear();
    this.levelIds.addAll(levelIds);
    this.goals.clear();
    goalTagIds.forEach(tagId -> this.goals.add(new OfferingGoal(tagId, verified.getOrDefault(tagId, false))));
    this.textbookIds.clear();
    this.textbookIds.addAll(textbookIds);
    this.examProvinceIds.clear();
    this.examProvinceIds.addAll(examProvinceIds);
    this.pricePerSession = pricePerSession;
    this.updatedAt = Instant.now();
  }

  /** Admin duyệt bằng chứng gắn với gói: mọi mục tiêu đang chọn được xác minh. */
  public void verifyGoalEvidence() {
    goals.forEach(OfferingGoal::verifyEvidence);
    this.updatedAt = Instant.now();
  }

  public void revokeGoalEvidence() {
    goals.forEach(OfferingGoal::revokeEvidence);
    this.updatedAt = Instant.now();
  }

  public Map<Integer, Boolean> goalEvidence() {
    return goals.stream().collect(Collectors.toMap(OfferingGoal::getGoalTagId, OfferingGoal::isEvidenceVerified));
  }

  public UUID getOfferingId() {
    return offeringId;
  }

  public UUID getTutorId() {
    return tutorId;
  }

  public Integer getSubjectId() {
    return subjectId;
  }

  public Integer getTrackId() {
    return trackId;
  }

  public Long getPricePerSession() {
    return pricePerSession;
  }

  public Set<Integer> getLevelIds() {
    return Set.copyOf(levelIds);
  }

  public Set<Integer> getGoalTagIds() {
    return goals.stream().map(OfferingGoal::getGoalTagId).collect(Collectors.toUnmodifiableSet());
  }

  public Set<Integer> getTextbookIds() {
    return Set.copyOf(textbookIds);
  }

  public Set<Integer> getExamProvinceIds() {
    return Set.copyOf(examProvinceIds);
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
