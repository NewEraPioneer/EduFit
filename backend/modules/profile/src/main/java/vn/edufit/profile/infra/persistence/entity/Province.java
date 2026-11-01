package vn.edufit.profile.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tỉnh/Thành phố.
 */
@Entity
@Table(name = "province")
public class Province {

  @Id
  @Column(name = "province_id")
  private Integer provinceId;

  @Column(name = "code")
  private String code;

  @Column(name = "name")
  private String name;

  @Column(name = "sort_order")
  private Integer sortOrder;

  @Column(name = "is_active")
  private boolean active;

  protected Province() {
    // JPA required
  }

  public Integer getProvinceId() {
    return provinceId;
  }

  public String getCode() {
    return code;
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
