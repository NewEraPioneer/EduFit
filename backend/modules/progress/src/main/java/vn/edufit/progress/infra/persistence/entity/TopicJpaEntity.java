package vn.edufit.progress.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** JPA Entity ánh xạ bảng {@code topic_catalog}. */
@Entity
@Table(name = "topic_catalog")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TopicJpaEntity {

  @Id
  @Column(name = "topic_id", nullable = false, updatable = false)
  private UUID topicId;

  @Column(name = "subject_id", nullable = false, updatable = false)
  private Integer subjectId;

  @Column(name = "education_level_id", nullable = false, updatable = false)
  private Integer educationLevelId;

  @Column(name = "name", nullable = false, length = 120)
  private String name;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "is_archived", nullable = false)
  private boolean archived;

  @Column(name = "created_by", nullable = false, updatable = false)
  private UUID createdBy;

  @Version
  @Column(name = "version", nullable = false)
  private int version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
