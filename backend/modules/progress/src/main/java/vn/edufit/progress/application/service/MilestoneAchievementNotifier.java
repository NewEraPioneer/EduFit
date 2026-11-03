package vn.edufit.progress.application.service;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.progress.domain.model.Milestone;
import vn.edufit.shared.notification.NotificationSink;

/**
 * Gửi thông báo tới Học sinh và các Phụ huynh đã liên kết khi một cột mốc được chốt đạt.
 */
@Component
public class MilestoneAchievementNotifier {

  private final ConnectionFacade connectionFacade;
  private final ProfileFacade profileFacade;
  private final NotificationSink notifications;

  public MilestoneAchievementNotifier(
      ConnectionFacade connectionFacade, ProfileFacade profileFacade, NotificationSink notifications) {
    this.connectionFacade = connectionFacade;
    this.profileFacade = profileFacade;
    this.notifications = notifications;
  }

  public void notifyAchieved(ConnectionFacade.ClassDetailView cls, Milestone milestone) {
    profileFacade.findStudentById(cls.studentId()).ifPresent(student -> notifications.notify(
        student.userId(),
        cls.studentId(),
        "MILESTONE_ACHIEVED",
        "Chúc mừng! Đạt cột mốc mới",
        "Bạn đã hoàn thành cột mốc: " + milestone.getTitle(),
        "MILESTONE",
        milestone.getMilestoneId()
    ));

    List<UUID> parentUserIds = connectionFacade.findConfirmedParentUserIds(cls.studentId());
    for (UUID parentUserId : parentUserIds) {
      notifications.notify(
          parentUserId,
          cls.studentId(),
          "MILESTONE_ACHIEVED",
          "Học sinh đạt cột mốc mới",
          "Học sinh đã hoàn thành cột mốc: " + milestone.getTitle(),
          "MILESTONE",
          milestone.getMilestoneId()
      );
    }
  }
}
