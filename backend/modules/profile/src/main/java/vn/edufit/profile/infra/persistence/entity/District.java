package vn.edufit.profile.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Khu vực (quận/huyện quen dùng) thuộc một Tỉnh/Thành.
 */
@Entity
@Table(name = "district")
public class District {

  @Id
  @Column(name = "district_id")
  private Integer districtId;

  @Column(name = "province_id")
  private Integer provinceId;

  @Column(name = "name")
  private String name;

  @Column(name = "sort_order")
  private Integer sortOrder;

  @Column(name = "is_active")
  private boolean active;

  protected District() {
    // JPA required
  }

  public Integer getDistrictId() {
    return districtId;
  }

  public Integer getProvinceId() {
    return provinceId;
  }

  public String getName() {
    return name;
  }

  public Integer getSortOrder() {
    return sortOrder;
  }

  public boolean isActive() {
    return active;
  }
}
