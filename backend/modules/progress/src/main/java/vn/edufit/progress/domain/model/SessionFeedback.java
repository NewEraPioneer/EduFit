package vn.edufit.progress.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Aggregate phản hồi 10 giây sau buổi học: mức tự lập cho từng cột mốc được dạy (bắt buộc),
 * chip việc về nhà (bắt buộc), chip thái độ và lời nhắn riêng cho phụ huynh (tùy chọn).
 * Mỗi buổi học có tối đa một phản hồi; ghi sau {@code FeedbackTimingPolicy.LATE_AFTER} thì đánh dấu "bổ sung muộn".
 *
 * <p>Các cột mốc có điểm chấm chính là các cột mốc "thực tế" được dạy trong buổi.
 */
public class SessionFeedback {

  public static final int MAX_PARENT_MESSAGE_LENGTH = 300;

  private final UUID feedbackId;
  private final UUID sessionId;
  private final UUID classId;
  private final HomeworkStatus homeworkStatus;
  private final Set<AttitudeChip> attitudeChips;
  private final String parentMessage;
  private final UUID createdBy;
  private final Instant createdAt;
  private final boolean submittedLate;
  private final List<MilestoneAssessment> assessments;

  public SessionFeedback(
      UUID feedbackId,
      UUID sessionId,
      UUID classId,
      HomeworkStatus homeworkStatus,
      Collection<AttitudeChip> attitudeChips,
      String parentMessage,
      UUID createdBy,
      Instant createdAt,
      boolean submittedLate,
      List<MilestoneAssessment> assessments
  ) {
    if (sessionId == null || classId == null || createdBy == null) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Phản hồi thiếu buổi học, lớp học hoặc người ghi");
    }
    if (homeworkStatus == null) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Cần chọn chip việc về nhà");
    }
    List<MilestoneAssessment> items = assessments != null ? assessments : List.of();
    if (items.isEmpty()) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED, "Cần chấm mức tự lập cho ít nhất một cột mốc được dạy trong buổi");
    }
    Set<UUID> seen = new HashSet<>();
    for (MilestoneAssessment a : items) {
      if (!sessionId.equals(a.getSessionId())) {
        throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Điểm chấm không thuộc buổi học này");
      }
      if (!seen.add(a.getMilestoneId())) {
        throw new InvalidOperationException(
            ErrorCode.VALIDATION_FAILED, "Mỗi cột mốc chỉ được chấm một lần trong một phản hồi");
      }
    }
    String message = parentMessage != null && !parentMessage.isBlank() ? parentMessage.trim() : null;
    if (message != null && message.length() > MAX_PARENT_MESSAGE_LENGTH) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED, "Lời nhắn riêng cho phụ huynh tối đa " + MAX_PARENT_MESSAGE_LENGTH + " ký tự");
    }
    this.feedbackId = feedbackId != null ? feedbackId : UUID.randomUUID();
    this.sessionId = sessionId;
    this.classId = classId;
    this.homeworkStatus = homeworkStatus;
    this.attitudeChips = attitudeChips == null || attitudeChips.isEmpty()
        ? EnumSet.noneOf(AttitudeChip.class)
        : EnumSet.copyOf(attitudeChips);
    this.parentMessage = message;
    this.createdBy = createdBy;
    this.createdAt = createdAt != null ? createdAt : Instant.now();
    this.submittedLate = submittedLate;
    this.assessments = new ArrayList<>(items);
  }

  public UUID getFeedbackId() { return feedbackId; }
  public UUID getSessionId() { return sessionId; }
  public UUID getClassId() { return classId; }
  public HomeworkStatus getHomeworkStatus() { return homeworkStatus; }
  public Set<AttitudeChip> getAttitudeChips() { return Collections.unmodifiableSet(attitudeChips); }
  /** Chỉ phụ huynh (và người ghi) được xem; không bao giờ trả về cho học sinh. */
  public String getParentMessage() { return parentMessage; }
  public boolean isSubmittedLate() { return submittedLate; }
  public UUID getCreatedBy() { return createdBy; }
  public Instant getCreatedAt() { return createdAt; }
  public List<MilestoneAssessment> getAssessments() { return Collections.unmodifiableList(assessments); }
}
