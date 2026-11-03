package vn.edufit.progress.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import vn.edufit.progress.application.dto.TopicViews.SaveTopicCommand;
import vn.edufit.progress.domain.model.Topic;

/**
 * Tạo/sửa chủ đề chuẩn. Môn và khối bắt buộc khi tạo, bỏ qua khi sửa; {@code version} bắt buộc khi sửa.
 */
public record SaveTopicRequest(
    Integer subjectId,
    Integer educationLevelId,

    @NotBlank(message = "Tên chủ đề không được để trống")
    @Size(max = Topic.MAX_NAME_LENGTH, message = "Tên chủ đề tối đa 120 ký tự")
    String name,

    Integer sortOrder,
    boolean archived,
    Integer version
) {
  public SaveTopicCommand toCommand() {
    return new SaveTopicCommand(subjectId, educationLevelId, name, sortOrder, archived, version);
  }
}
