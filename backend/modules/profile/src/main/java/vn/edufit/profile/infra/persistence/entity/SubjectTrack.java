package vn.edufit.profile.infra.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.Set;

/**
 * Nhánh môn học (ví dụ KHTN – Trọng tâm Vật lý) kèm các khối lớp áp dụng.
 */
@Entity
@Table(name = "subject_track")
public class SubjectTrack {

  @Id
  @Column(name = "track_id")
  private Integer trackId;

  @Column(name = "subject_id")
  private Integer subjectId;

  @Column(name = "code")
  private String code;

  @Column(name = "name")
  private String name;

  @Column(name = "sort_order")
  private Integer sortOrder;

  @Column(name = "is_active")
  private boolean active;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "subject_track_level", joinColumns = @JoinColumn(name = "track_id"))
  @Column(name = "level_id")
  private Set<Integer> levelIds;

  protected SubjectTrack() {
    // JPA required
  }

  public Integer getTrackId() {
    return trackId;
  }

  public Integer getSubjectId() {
    return subjectId;
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

  public Set<Integer> getLevelIds() {
    return levelIds;
  }
}
