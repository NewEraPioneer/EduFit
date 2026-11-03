package vn.edufit.progress.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import vn.edufit.progress.application.dto.SubmitSessionFeedbackCommand;
import vn.edufit.progress.domain.model.AttitudeChip;
import vn.edufit.progress.domain.model.HomeworkStatus;

/**
 * Phản hồi 10 giây sau buổi học: POST /api/v1/progress/sessions/{sessionId}/feedback.
 */
public record SubmitSessionFeedbackRequest(
    @NotNull(message = "Cần chọn chip việc về nhà")
    HomeworkStatus homeworkStatus,

    @Size(max = 6, message = "Chọn tối đa 6 chip thái độ")
    List<AttitudeChip> attitudeChips,

    @Size(max = 300, message = "Lời nhắn riêng cho phụ huynh tối đa 300 ký tự")
    String parentMessage,

    @NotEmpty(message = "Cần chấm mức tự lập cho ít nhất một cột mốc được dạy")
    @Size(max = 8, message = "Một buổi học gắn tối đa 8 cột mốc")
    @Valid
    List<TaughtMilestone> milestones
) {
  public record TaughtMilestone(
      @NotNull(message = "milestoneId không được để trống")
      UUID milestoneId,

      @NotNull(message = "Cần chọn mức tự lập")
      @Min(value = 1, message = "Mức tự lập từ 1 đến 4")
      @Max(value = 4, message = "Mức tự lập từ 1 đến 4")
      Integer level,

      @Min(value = 0, message = "Số bài đúng không được âm")
      Integer correctCount,

      @Min(value = 1, message = "Tổng số bài tối thiểu là 1")
      @Max(value = 100, message = "Tổng số bài tối đa là 100")
      Integer totalCount,

      @Size(max = 200, message = "Câu bằng chứng tối đa 200 ký tự")
      String evidenceNote,

      @Size(max = 4, message = "Mỗi cột mốc có tối đa 4 tiêu chí đạt")
      List<UUID> checkedCriterionIds,

      boolean markAchieved
  ) {}

  public SubmitSessionFeedbackCommand toCommand() {
    return new SubmitSessionFeedbackCommand(
        homeworkStatus,
        attitudeChips != null ? attitudeChips : List.of(),
        parentMessage,
        milestones.stream()
            .map(m -> new SubmitSessionFeedbackCommand.TaughtMilestone(
                m.milestoneId(), m.level(), m.correctCount(), m.totalCount(), m.evidenceNote(),
                m.checkedCriterionIds(), m.markAchieved()))
            .toList()
    );
  }
}
