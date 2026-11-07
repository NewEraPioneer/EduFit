package vn.edufit.notification.application.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.progress.api.event.FeedbackReminderDueEvent;
import vn.edufit.progress.api.event.MilestoneAchievedEvent;
import vn.edufit.progress.api.event.PeriodReportSentEvent;
import vn.edufit.shared.notification.NotificationSink;

class ProgressNotificationListenerTest {

  private final UUID classId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID studentUserId = UUID.randomUUID();
  private final UUID parentUserId = UUID.randomUUID();
  private final UUID tutorId = UUID.randomUUID();
  private final UUID tutorUserId = UUID.randomUUID();

  private NotificationSink sink;
  private ProfileFacade profiles;
  private ConnectionFacade connections;
  private ProgressNotificationListener listener;

  @BeforeEach
  void setUp() {
    sink = mock(NotificationSink.class);
    profiles = mock(ProfileFacade.class);
    connections = mock(ConnectionFacade.class);
    when(profiles.findStudentById(studentId))
        .thenReturn(Optional.of(new StudentSummaryDto(studentId, studentUserId, null, null, null, true)));
    when(connections.findConfirmedParentUserIds(studentId)).thenReturn(List.of(parentUserId));
    listener = new ProgressNotificationListener(sink, profiles, connections);
  }

  @Test
  void milestoneAchievedNotifiesStudentAndParentWithDistinctWording() {
    UUID milestoneId = UUID.randomUUID();
    listener.onMilestoneAchieved(MilestoneAchievedEvent.of(classId, studentId, milestoneId, "Hệ phương trình"));

    verify(sink).notify(eq(studentUserId), eq(studentId), eq("MILESTONE_ACHIEVED"),
        eq("Chúc mừng! Đạt cột mốc mới"), contains("Hệ phương trình"), eq("MILESTONE"), eq(milestoneId));
    verify(sink).notify(eq(parentUserId), eq(studentId), eq("MILESTONE_ACHIEVED"),
        eq("Học sinh đạt cột mốc mới"), contains("Hệ phương trình"), eq("MILESTONE"), eq(milestoneId));
    verifyNoMoreInteractions(sink);
  }

  @Test
  void parentWhoSharesTheStudentAccountReceivesOnlyOneCopy() {
    when(connections.findConfirmedParentUserIds(studentId)).thenReturn(List.of(studentUserId, parentUserId));
    listener.onMilestoneAchieved(MilestoneAchievedEvent.of(classId, studentId, UUID.randomUUID(), "A"));
    verify(sink, times(2)).notify(any(), any(), anyString(), anyString(), anyString(), anyString(), any());
  }

  @Test
  void periodReportSentNotifiesStudentAndParentWithPeriodRange() {
    UUID reportId = UUID.randomUUID();
    listener.onPeriodReportSent(new PeriodReportSentEvent(UUID.randomUUID(), Instant.now(),
        reportId, classId, studentId, 2, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30")));

    for (UUID user : List.of(studentUserId, parentUserId)) {
      verify(sink).notify(eq(user), eq(studentId), eq("PERIOD_REPORT_SENT"),
          eq("Báo cáo tiến độ kỳ 2"), contains("01/09–30/09"), eq("PERIOD_REPORT"), eq(reportId));
    }
    verifyNoMoreInteractions(sink);
  }

  @Test
  void feedbackReminderGoesToTutorOnly() {
    UUID sessionId = UUID.randomUUID();
    TutorSummaryDto tutor = mock(TutorSummaryDto.class);
    when(tutor.userId()).thenReturn(tutorUserId);
    when(profiles.findTutorById(tutorId)).thenReturn(Optional.of(tutor));

    listener.onFeedbackReminderDue(FeedbackReminderDueEvent.of(sessionId, classId, studentId, tutorId, 12, 36));

    verify(sink).notify(eq(tutorUserId), eq(studentId), eq("SESSION_FEEDBACK_REMINDER"),
        eq("Nhắc phản hồi buổi học"), contains("12 giờ"), eq("SESSION"), eq(sessionId));
    verifyNoMoreInteractions(sink);
  }

  @Test
  void unknownTutorProducesNoNotification() {
    when(profiles.findTutorById(tutorId)).thenReturn(Optional.empty());
    listener.onFeedbackReminderDue(FeedbackReminderDueEvent.of(UUID.randomUUID(), classId, studentId, tutorId, 12, 36));
    verify(sink, never()).notify(any(), any(), anyString(), anyString(), anyString(), anyString(), any());
  }
}
