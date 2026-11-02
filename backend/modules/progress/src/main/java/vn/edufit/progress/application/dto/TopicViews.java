package vn.edufit.progress.application.dto;

import java.util.UUID;
import vn.edufit.progress.domain.model.Topic;

/** DTO của danh mục chủ đề chuẩn theo môn × khối. */
public final class TopicViews {

  private TopicViews() {}

  public record TopicView(
      UUID topicId,
      int subjectId,
      int educationLevelId,
      String name,
      int sortOrder,
      boolean archived,
      int version
  ) {
    public static TopicView from(Topic t) {
      return new TopicView(t.getTopicId(), t.getSubjectId(), t.getEducationLevelId(), t.getName(), t.getSortOrder(),
          t.isArchived(), t.getVersion());
    }
  }

  /** Môn và khối chỉ dùng khi tạo mới; {@code version} chỉ dùng khi sửa. */
  public record SaveTopicCommand(
      Integer subjectId,
      Integer educationLevelId,
      String name,
      Integer sortOrder,
      boolean archived,
      Integer version
  ) {}
}
