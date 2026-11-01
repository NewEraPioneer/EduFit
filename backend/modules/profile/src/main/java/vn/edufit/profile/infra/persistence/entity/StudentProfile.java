package vn.edufit.profile.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity ánh xạ bảng {@code student_profile}.
 */
@Entity
@Table(name = "student_profile")
public class StudentProfile {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "student_id", updatable = false, nullable = false)
  private UUID studentId;

  @Column(name = "user_id", nullable = false, unique = true)
  private UUID userId;

  @Column(name = "education_level_id")
  private Integer educationLevelId;

  @Column(name = "province_id")
  private Integer provinceId;

  @Column(name = "district_id")
  private Integer districtId;

  @Column(name = "profile_complete", nullable = false)
  private Boolean profileComplete = false;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected StudentProfile() {
    // JPA required
  }

  public StudentProfile(UUID userId) {
    this.userId = userId;
    this.profileComplete = false;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public UUID getStudentId() {
    return studentId;
  }

  public UUID getUserId() {
    return userId;
  }

  public Integer getEducationLevelId() {
    return educationLevelId;
  }

  public void setEducationLevelId(Integer educationLevelId) {
    this.educationLevelId = educationLevelId;
    this.updatedAt = Instant.now();
  }

  public Integer getProvinceId() {
    return provinceId;
  }

  public Integer getDistrictId() {
    return districtId;
  }

  /** Khu vực mặc định, dùng để điền sẵn khi tạo nhu cầu học có học trực tiếp. */
  public void setLocation(Integer provinceId, Integer districtId) {
    this.provinceId = provinceId;
    this.districtId = provinceId == null ? null : districtId;
    this.updatedAt = Instant.now();
  }

  public Boolean isProfileComplete() {
    return profileComplete;
  }

  public void setProfileComplete(Boolean profileComplete) {
    this.profileComplete = profileComplete;
    this.updatedAt = Instant.now();
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
