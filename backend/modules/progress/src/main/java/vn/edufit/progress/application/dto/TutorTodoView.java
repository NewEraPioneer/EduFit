package vn.edufit.progress.application.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Ngăn "Việc cần làm" trên dashboard gia sư (mục 3.14). Báo cáo kỳ chờ gửi thuộc Giai đoạn 3.
 */
public record TutorTodoView(
    List<MissingFeedbackItem> sessionsMissingFeedback,
    List<ClassItem> classesWithoutPlan,
    List<OverdueMilestoneItem> overdueMilestones,
    List<ClassItem> plansMissingFinalDate
) {
  public int totalCount() {
    return sessionsMissingFeedback.size() + classesWithoutPlan.size()
        + overdueMilestones.size() + plansMissingFinalDate.size();
  }

  /** @param late đã quá 48 giờ: phản hồi bổ sung bây giờ sẽ được đánh dấu "bổ sung muộn". */
  public record MissingFeedbackItem(
      UUID sessionId, UUID classId, String studentName, Instant startAt, Instant endAt, boolean late) {}

  public record ClassItem(UUID classId, String studentName, String classTitle) {}

  public record OverdueMilestoneItem(
      UUID classId, String studentName, UUID milestoneId, String milestoneTitle, LocalDate targetDate, long daysLate) {}
}
