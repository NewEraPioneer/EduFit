package vn.edufit.profile.infra.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * JPA Entity ánh xạ bảng {@code tutor_profile}.
 */
@Entity
@Table(name = "tutor_profile")
public class TutorProfile {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "tutor_id", updatable = false, nullable = false)
  private UUID tutorId;

  @Column(name = "user_id", nullable = false, unique = true)
  private UUID userId;

  @Column(name = "display_name", nullable = false, length = 100)
  private String displayName;

  @Column(name = "headline", length = 200)
  private String headline;

  @Column(name = "bio", columnDefinition = "TEXT")
  private String bio;

  @Enumerated(EnumType.STRING)
  @Column(name = "teaching_mode", nullable = false, length = 20)
  private TeachingMode teachingMode;

  @Enumerated(EnumType.STRING)
  @Column(name = "tutor_type", length = 30)
  private TutorType tutorType;

  @Column(name = "tutor_type_verified", nullable = false)
  private boolean tutorTypeVerified;

  @Column(name = "province_id")
  private Integer provinceId;

  @org.hibernate.annotations.BatchSize(size = 50)
  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "tutor_district", joinColumns = @JoinColumn(name = "tutor_id"))
  @Column(name = "district_id")
  private Set<Integer> districtIds = new HashSet<>();

  @Column(name = "experience_years")
  private Short experienceYears;

  @Column(name = "teaching_method", columnDefinition = "TEXT")
  private String teachingMethod;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private TutorStatus status = TutorStatus.DRAFT;

  @Column(name = "verified_at")
  private Instant verifiedAt;

  @Column(name = "rating_avg", precision = 3, scale = 2)
  private BigDecimal ratingAvg;

  @Column(name = "review_count", nullable = false)
  private Integer reviewCount = 0;

  @Version
  @Column(name = "version", nullable = false)
  private Integer version = 0;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected TutorProfile() {
    // JPA required
  }

  public TutorProfile(
      UUID userId,
      String displayName,
      TeachingMode teachingMode
  ) {
    this.userId = userId;
    this.displayName = displayName;
    this.teachingMode = teachingMode;
    this.status = TutorStatus.DRAFT;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void markPendingVerification() {
    this.status = TutorStatus.PENDING_VERIFICATION;
    this.updatedAt = Instant.now();
  }

  public void markVerified(Instant verifiedAt) {
    this.status = TutorStatus.VERIFIED;
    this.verifiedAt = verifiedAt != null ? verifiedAt : Instant.now();
    this.updatedAt = Instant.now();
  }

  public void markRejected() {
    this.status = TutorStatus.UNVERIFIED;
    this.verifiedAt = null;
    this.updatedAt = Instant.now();
  }

  public void markUnverified() {
    markRejected();
  }

  public UUID getTutorId() {
    return tutorId;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
    this.updatedAt = Instant.now();
  }

  public String getHeadline() {
    return headline;
  }

  public void setHeadline(String headline) {
    this.headline = headline;
    this.updatedAt = Instant.now();
  }

  public String getBio() {
    return bio;
  }

  public void setBio(String bio) {
    this.bio = bio;
    this.updatedAt = Instant.now();
  }

  public TeachingMode getTeachingMode() {
    return teachingMode;
  }

  public void setTeachingMode(TeachingMode teachingMode) {
    this.teachingMode = teachingMode;
    this.updatedAt = Instant.now();
  }

  public TutorType getTutorType() {
    return tutorType;
  }

  public boolean isTutorTypeVerified() {
    return tutorTypeVerified;
  }

  /** Gia sư đổi loại khai báo thì cần admin xác nhận lại. */
  public void declareTutorType(TutorType tutorType) {
    if (this.tutorType != tutorType) {
      this.tutorType = tutorType;
      this.tutorTypeVerified = false;
      this.updatedAt = Instant.now();
    }
  }

  /** Admin xác nhận loại gia sư khi duyệt hồ sơ, dựa trên giấy tờ đã nộp. */
  public void confirmTutorType(TutorType tutorType) {
    this.tutorType = tutorType;
    this.tutorTypeVerified = true;
    this.updatedAt = Instant.now();
  }

  public Integer getProvinceId() {
    return provinceId;
  }

  public Set<Integer> getDistrictIds() {
    return Set.copyOf(districtIds);
  }

  /** Khu vực dạy trực tiếp: một Tỉnh/Thành và nhiều Khu vực thuộc tỉnh đó. */
  public void setTeachingArea(Integer provinceId, Collection<Integer> districtIds) {
    this.provinceId = provinceId;
    this.districtIds.clear();
    if (provinceId != null) {
      this.districtIds.addAll(districtIds);
    }
    this.updatedAt = Instant.now();
  }

  public Short getExperienceYears() {
    return experienceYears;
  }

  public void setExperienceYears(Short experienceYears) {
    this.experienceYears = experienceYears;
    this.updatedAt = Instant.now();
  }

  public String getTeachingMethod() {
    return teachingMethod;
  }

  public void setTeachingMethod(String teachingMethod) {
    this.teachingMethod = teachingMethod;
    this.updatedAt = Instant.now();
  }

  public TutorStatus getStatus() {
    return status;
  }

  public void setStatus(TutorStatus status) {
    this.status = status;
    this.updatedAt = Instant.now();
  }

  public Instant getVerifiedAt() {
    return verifiedAt;
  }

  public void setVerifiedAt(Instant verifiedAt) {
    this.verifiedAt = verifiedAt;
    this.updatedAt = Instant.now();
  }

  public BigDecimal getRatingAvg() {
    return ratingAvg;
  }

  public void setRatingAvg(BigDecimal ratingAvg) {
    this.ratingAvg = ratingAvg;
    this.updatedAt = Instant.now();
  }

  public Integer getReviewCount() {
    return reviewCount;
  }

  public void setReviewCount(Integer reviewCount) {
    this.reviewCount = reviewCount;
    this.updatedAt = Instant.now();
  }

  public Integer getVersion() {
    return version;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
