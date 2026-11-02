package vn.edufit.profile.infra.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import java.util.Set;

/**
 * Bộ sách giáo khoa kèm các môn áp dụng.
 */
@Entity
@Table(name = "textbook_series")
public class TextbookSeries {

  @Id
  @Column(name = "series_id")
  private Integer seriesId;

  @Column(name = "code")
  private String code;

  @Column(name = "name")
  private String name;

  @Column(name = "sort_order")
  private Integer sortOrder;

  @Column(name = "is_active")
  private boolean active;

  @ElementCollection(fetch = FetchType.EAGER)
  @Fetch(FetchMode.SUBSELECT)
  @CollectionTable(name = "textbook_series_subject", joinColumns = @JoinColumn(name = "series_id"))
  @Column(name = "subject_id")
  private Set<Integer> subjectIds;

  protected TextbookSeries() {
    // JPA required
  }

  public Integer getSeriesId() {
    return seriesId;
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

  public Set<Integer> getSubjectIds() {
    return subjectIds;
  }
}
