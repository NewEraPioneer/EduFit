package vn.edufit.progress.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.edufit.progress.domain.model.SessionFeedback;

/**
 * Phản hồi có cấu trúc của một buổi học (sổ liên lạc).
 *
 * @param parentMessage lời nhắn riêng cho phụ huynh; luôn {@code null} khi người xem là học sinh.
 * @param late          phản hồi được bổ sung sau 48 giờ kể từ khi buổi học kết thúc.
 */
public record SessionFeedbackView(
    String homeworkStatus,
    List<String> attitudeChips,
    List<AssessmentView> assessments,
    String parentMessage,
    boolean late,
    Instant createdAt
) {
  public record AssessmentView(
      UUID milestoneId,
      String milestoneTitle,
      int level,
      Integer correctCount,
      Integer totalCount,
      String evidenceNote
  ) {}

  public static SessionFeedbackView from(
      SessionFeedback feedback, Map<UUID, String> milestoneTitles, boolean includeParentMessage) {
    if (feedback == null) return null;
    return new SessionFeedbackView(
        feedback.getHomeworkStatus().name(),
        feedback.getAttitudeChips().stream().map(Enum::name).toList(),
        feedback.getAssessments().stream()
            .map(a -> new AssessmentView(
                a.getMilestoneId(),
                milestoneTitles.get(a.getMilestoneId()),
                a.getLevel().value(),
                a.getCorrectCount(),
                a.getTotalCount(),
                a.getEvidenceNote()))
            .toList(),
        includeParentMessage ? feedback.getParentMessage() : null,
        feedback.isSubmittedLate(),
        feedback.getCreatedAt()
    );
  }
}
