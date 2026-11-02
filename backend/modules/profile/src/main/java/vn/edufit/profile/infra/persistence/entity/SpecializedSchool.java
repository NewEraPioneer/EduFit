package vn.edufit.profile.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Trường THPT chuyên, dùng làm ngữ cảnh cho mục tiêu HSG & Thi chuyên.
 */
@Entity
@Table(name = "specialized_school")
public class SpecializedSchool {

  @Id
  @Column(name = "school_id")
  private Integer schoolId;

  @Column(name = "province_id")
  private Integer provinceId;

  @Column(name = "name")
  private String name;

  @Column(name = "is_active")
  private boolean active;

  protected SpecializedSchool() {
    // JPA required
  }

  public Integer getSchoolId() {
    return schoolId;
  }

  public Integer getProvinceId() {
    return provinceId;
  }

  public String getName() {
    return name;
  }

  public boolean isActive() {
    return active;
  }
}
