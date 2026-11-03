package vn.edufit.progress.application.dto;

import java.util.List;
import java.util.UUID;
import vn.edufit.progress.domain.model.AttitudeChip;
import vn.edufit.progress.domain.model.HomeworkStatus;

/**
 * Phản hồi 10 giây sau buổi học.
 *
 * @param parentMessage lời nhắn riêng cho phụ huynh (tùy chọn), học sinh không thấy.
 * @param milestones    cột mốc được dạy thực tế trong buổi (ít nhất một).
 */
public record SubmitSessionFeedbackCommand(
    HomeworkStatus homeworkStatus,
    List<AttitudeChip> attitudeChips,
    String parentMessage,
    List<TaughtMilestone> milestones
) {
  /**
   * @param evidenceNote        câu bằng chứng, bắt buộc (hoặc đúng x/y) khi tăng từ 2 mức trở lên trong một buổi.
   * @param checkedCriterionIds trạng thái tick đầy đủ của tiêu chí sau buổi; {@code null} = giữ nguyên.
   * @param markAchieved        gia sư chốt cột mốc đạt (yêu cầu tick đủ tiêu chí).
   */
  public record TaughtMilestone(
      UUID milestoneId,
      Integer level,
      Integer correctCount,
      Integer totalCount,
      String evidenceNote,
      List<UUID> checkedCriterionIds,
      boolean markAchieved
  ) {}
}
