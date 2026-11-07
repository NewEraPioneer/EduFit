package vn.edufit.notification.application.listener;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.progress.api.event.FeedbackReminderDueEvent;
import vn.edufit.progress.api.event.MilestoneAchievedEvent;
import vn.edufit.progress.api.event.PeriodReportSentEvent;
import vn.edufit.shared.notification.NotificationSink;

/**
 * Chuyển sự kiện của module progress thành thông báo. Progress chỉ phát sự thật nghiệp vụ;
 * người nhận và câu chữ do module notification quyết định. Chạy đồng bộ trong giao dịch của nguồn phát
 * để thông báo và dữ liệu nghiệp vụ cùng commit hoặc cùng rollback.
 */
@Component
public class ProgressNotificationListener {

  static final String MILESTONE_ACHIEVED = "MILESTONE_ACHIEVED";
  static final String PERIOD_REPORT_SENT = "PERIOD_REPORT_SENT";
  static final String FEEDBACK_REMINDER = "SESSION_FEEDBACK_REMINDER";
  private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

  private final NotificationSink sink;
  private final ProfileFacade profiles;
  private final ConnectionFacade connections;

  public ProgressNotificationListener(NotificationSink sink, ProfileFacade profiles, ConnectionFacade connections) {
    this.sink = sink;
    this.profiles = profiles;
    this.connections = connections;
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void onMilestoneAchieved(MilestoneAchievedEvent e) {
    String detail = e.milestoneTitle();
    notifyStudent(e.studentId(), MILESTONE_ACHIEVED, "Chúc mừng! Đạt cột mốc mới",
        "Bạn đã hoàn thành cột mốc: " + detail, "MILESTONE", e.milestoneId());
    notifyParents(e.studentId(), MILESTONE_ACHIEVED, "Học sinh đạt cột mốc mới",
        "Học sinh đã hoàn thành cột mốc: " + detail, "MILESTONE", e.milestoneId());
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void onPeriodReportSent(PeriodReportSentEvent e) {
    String title = "Báo cáo tiến độ kỳ " + e.periodIndex();
    String text = "Báo cáo 30 ngày (" + DAY_MONTH.format(e.periodStart()) + "–" + DAY_MONTH.format(e.periodEnd())
        + ") đã sẵn sàng để xem.";
    notifyStudent(e.studentId(), PERIOD_REPORT_SENT, title, text, "PERIOD_REPORT", e.reportId());
    notifyParents(e.studentId(), PERIOD_REPORT_SENT, title, text, "PERIOD_REPORT", e.reportId());
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void onFeedbackReminderDue(FeedbackReminderDueEvent e) {
    profiles.findTutorById(e.tutorId()).map(TutorSummaryDto::userId).ifPresent(tutorUserId -> sink.notify(
        tutorUserId, e.studentId(), FEEDBACK_REMINDER, "Nhắc phản hồi buổi học",
        "Buổi học đã kết thúc " + e.hoursSinceEnd() + " giờ nhưng chưa có phản hồi 10 giây. Sau " + e.hoursLeft()
            + " giờ nữa phản hồi sẽ được đánh dấu \"bổ sung muộn\".",
        "SESSION", e.sessionId()));
  }

  private void notifyStudent(UUID studentId, String type, String title, String text, String targetType, UUID targetId) {
    profiles.findStudentById(studentId).map(StudentSummaryDto::userId).ifPresent(userId ->
        sink.notify(userId, studentId, type, title, text, targetType, targetId));
  }

  /** Phụ huynh đã xác nhận; bỏ qua tài khoản trùng với tài khoản học sinh để không báo hai lần. */
  private void notifyParents(UUID studentId, String type, String title, String text, String targetType, UUID targetId) {
    Set<UUID> parents = new LinkedHashSet<>(connections.findConfirmedParentUserIds(studentId));
    profiles.findStudentById(studentId).map(StudentSummaryDto::userId).ifPresent(parents::remove);
    for (UUID parentUserId : parents) {
      sink.notify(parentUserId, studentId, type, title, text, targetType, targetId);
    }
  }
}
