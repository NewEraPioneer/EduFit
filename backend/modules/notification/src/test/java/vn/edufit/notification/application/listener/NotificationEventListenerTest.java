package vn.edufit.notification.application.listener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.connection.api.event.ConnectionRequestChangedEvent;
import vn.edufit.connection.api.event.LinkInvitationChangedEvent;
import vn.edufit.notification.application.service.NotificationService;
import vn.edufit.notification.infra.persistence.entity.Notification;
import vn.edufit.notification.infra.persistence.repository.NotificationRepository;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.review.api.event.ReviewSubmittedEvent;
import vn.edufit.review.api.event.ReviewUpdatedEvent;
import vn.edufit.scheduling.api.event.ClassScheduleChangedEvent;
import vn.edufit.scheduling.api.event.OverdueOutcomeReminderEvent;
import vn.edufit.scheduling.api.event.SessionCancelledEvent;
import vn.edufit.scheduling.api.event.SessionExpiredEvent;
import vn.edufit.scheduling.api.event.SessionProposalRejectedEvent;
import vn.edufit.scheduling.api.event.SessionProposedEvent;
import vn.edufit.scheduling.api.event.SessionRescheduleProposedEvent;
import vn.edufit.scheduling.api.event.SessionRescheduleRejectedEvent;
import vn.edufit.scheduling.api.event.SessionRescheduledEvent;
import vn.edufit.scheduling.api.event.SessionScheduledEvent;
import vn.edufit.scheduling.api.event.SessionUpcomingReminderEvent;
import vn.edufit.shared.domain.BaseDomainEvent;
import vn.edufit.verification.api.event.VerificationDecidedEvent;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

  @Mock private NotificationRepository repository;
  @Mock private ConnectionFacade connectionFacade;
  @Mock private ProfileFacade profileFacade;

  private NotificationEventListener listener;

  private final UUID tutorProfileId = UUID.randomUUID();
  private final UUID studentProfileId = UUID.randomUUID();
  private final UUID tutorUserId = UUID.randomUUID();
  private final UUID studentUserId = UUID.randomUUID();
  private final UUID parentUserId = UUID.randomUUID();
  private final UUID sessionId = UUID.randomUUID();
  private final UUID classId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    listener = new NotificationEventListener(new NotificationService(repository), connectionFacade, profileFacade);
    var tutor = new TutorSummaryDto(tutorProfileId, tutorUserId, "T", "h", "b", "ONLINE", null, false, null, "HN",
        (short) 2, "m", "ACTIVE", Instant.now(), BigDecimal.valueOf(5.0), 1);
    var student = new StudentSummaryDto(studentProfileId, studentUserId, 1, 1, 1, true);
    lenient().when(profileFacade.findTutorById(tutorProfileId)).thenReturn(Optional.of(tutor));
    lenient().when(profileFacade.findStudentById(studentProfileId)).thenReturn(Optional.of(student));
    lenient().when(connectionFacade.findConfirmedParentUserIds(studentProfileId)).thenReturn(List.of(parentUserId));
  }

  /** userId → notification đã lưu; một người nhận đúng một thông báo. */
  private Map<UUID, Notification> saved() {
    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(repository, atLeastOnce()).save(captor.capture());
    return captor.getAllValues().stream().collect(Collectors.toMap(Notification::getUserId, n -> n));
  }

  private int savedCount() {
    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(repository, atLeastOnce()).save(captor.capture());
    return captor.getAllValues().size();
  }

  @Test
  @DisplayName("handleDomainEvent: Tiếp nhận BaseDomainEvent an toàn không ném ngoại lệ")
  void handleDomainEvent_processesEventSafely() {
    BaseDomainEvent dummy = new BaseDomainEvent() {
      private final UUID eventId = UUID.randomUUID();
      private final Instant occurredAt = Instant.now();
      @Override public UUID getEventId() { return eventId; }
      @Override public Instant getOccurredAt() { return occurredAt; }
    };
    assertDoesNotThrow(() -> listener.handleDomainEvent(dummy));
  }

  @Test
  @DisplayName("onInvitationChanged: Lưu notification khi nhận LinkInvitationChangedEvent PENDING")
  void onInvitationChanged_pendingSavesNotification() {
    UUID inviter = UUID.randomUUID();
    listener.onInvitationChanged(new LinkInvitationChangedEvent(UUID.randomUUID(), UUID.randomUUID(), inviter,
        studentProfileId, null, "PENDING", Instant.now(), inviter, UUID.randomUUID(), null,
        LinkInvitationChangedEvent.Cause.USER_ACTION));
    verify(repository).save(any(Notification.class));
  }

  @Test
  @DisplayName("A-03: chấp nhận yêu cầu thiếu classId không ném lỗi (không rollback nghiệp vụ), trỏ về yêu cầu")
  void connectionAcceptedWithoutClassIdDoesNotThrow() {
    UUID requestId = UUID.randomUUID();
    var event = new ConnectionRequestChangedEvent(UUID.randomUUID(), requestId, tutorUserId, studentProfileId,
        "PENDING", "ACCEPTED", Instant.now(), studentUserId, studentUserId, tutorUserId, null);

    assertDoesNotThrow(() -> listener.onRequestChanged(event));

    Notification n = saved().get(studentUserId);
    assertEquals("CONNECTION_ACCEPTED", n.getType());
    assertEquals("CONNECTION_REQUEST", n.getTargetType());
    assertEquals(requestId, n.getTargetId());
  }

  @Test
  @DisplayName("onReviewSubmitted / onReviewUpdated (B-17): báo gia sư")
  void reviewEventsNotifyTheTutor() {
    UUID reviewId = UUID.randomUUID();
    listener.onReviewSubmitted(ReviewSubmittedEvent.of(reviewId, tutorProfileId, studentProfileId, studentUserId, "STUDENT", 5));
    listener.onReviewUpdated(ReviewUpdatedEvent.of(reviewId, tutorProfileId, studentProfileId, studentUserId, "STUDENT", 3));

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());
    assertEquals(List.of("REVIEW_RECEIVED", "REVIEW_UPDATED"),
        captor.getAllValues().stream().map(Notification::getType).toList());
    assertTrue(captor.getAllValues().stream().allMatch(n -> tutorUserId.equals(n.getUserId())));
    assertTrue(captor.getAllValues().get(1).getShortText().contains("3"));
  }

  @Test
  @DisplayName("B-15: gia sư đề xuất buổi học -> học sinh và phụ huynh nhận, gia sư không")
  void sessionProposedByTutorReachesStudentAndParents() {
    listener.onSessionProposed(SessionProposedEvent.of(sessionId, classId, tutorProfileId, studentProfileId,
        tutorUserId, Instant.now(), Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200)));

    Map<UUID, Notification> out = saved();
    assertEquals(java.util.Set.of(studentUserId, parentUserId), out.keySet());
    assertEquals("SESSION_PROPOSED", out.get(studentUserId).getType());
  }

  @Test
  @DisplayName("B-10: phụ huynh đề xuất đổi lịch -> gia sư VÀ học sinh đều được báo")
  void rescheduleProposedByParentReachesTutorAndStudent() {
    listener.onSessionRescheduleProposed(SessionRescheduleProposedEvent.of(UUID.randomUUID(), sessionId, classId,
        tutorProfileId, studentProfileId, parentUserId, Instant.now().plusSeconds(3600),
        Instant.now().plusSeconds(7200), "x", Instant.now().plusSeconds(1800)));

    assertEquals(java.util.Set.of(tutorUserId, studentUserId), saved().keySet());
  }

  @Test
  @DisplayName("onSessionRescheduleProposed: gia sư đề xuất -> học sinh + phụ huynh")
  void rescheduleProposedByTutor() {
    listener.onSessionRescheduleProposed(SessionRescheduleProposedEvent.of(UUID.randomUUID(), sessionId, classId,
        tutorProfileId, studentProfileId, tutorUserId, Instant.now().plusSeconds(3600),
        Instant.now().plusSeconds(7200), "x", Instant.now().plusSeconds(1800)));

    Map<UUID, Notification> out = saved();
    assertEquals(java.util.Set.of(studentUserId, parentUserId), out.keySet());
    assertEquals("SESSION_RESCHEDULE_PROPOSED", out.get(parentUserId).getType());
  }

  @Test
  @DisplayName("B-11: học sinh từ chối đổi lịch -> gia sư và phụ huynh nhận kết quả, học sinh thì không")
  void rescheduleRejectedReachesEveryoneWhoWasAsked() {
    listener.onSessionRescheduleRejected(SessionRescheduleRejectedEvent.of(UUID.randomUUID(), sessionId, classId,
        tutorProfileId, studentProfileId, tutorUserId, studentUserId, "Kẹt lịch"));

    Map<UUID, Notification> out = saved();
    assertEquals(java.util.Set.of(tutorUserId, parentUserId), out.keySet());
    assertEquals("SESSION_RESCHEDULE_REJECTED", out.get(tutorUserId).getType());
    assertTrue(out.get(tutorUserId).getShortText().endsWith(": Kẹt lịch"));
  }

  @Test
  @DisplayName("B-13: từ chối đề xuất buổi học -> người đề xuất biết, người từ chối không tự nhận")
  void sessionProposalRejectedNotifiesTheProposer() {
    listener.onSessionProposalRejected(SessionProposalRejectedEvent.of(sessionId, classId, tutorProfileId,
        studentProfileId, tutorUserId, studentUserId, "Bận"));

    Map<UUID, Notification> out = saved();
    assertEquals(java.util.Set.of(tutorUserId, parentUserId), out.keySet());
    assertEquals("SESSION_PROPOSAL_REJECTED", out.get(tutorUserId).getType());
  }

  @Test
  @DisplayName("B-03/B-06: chốt lịch loại người xác nhận, type riêng")
  void sessionScheduledSkipsTheConfirmer() {
    listener.onSessionScheduled(SessionScheduledEvent.of(sessionId, classId, tutorProfileId, studentProfileId,
        Instant.now(), Instant.now().plusSeconds(3600), tutorUserId));

    Map<UUID, Notification> out = saved();
    assertEquals(java.util.Set.of(studentUserId, parentUserId), out.keySet());
    assertEquals("SESSION_SCHEDULED", out.get(studentUserId).getType());
  }

  @Test
  @DisplayName("onSessionRescheduled: người chấp nhận không tự nhận; gia sư + phụ huynh được báo")
  void sessionRescheduledSkipsTheAccepter() {
    listener.onSessionRescheduled(SessionRescheduledEvent.of(UUID.randomUUID(), sessionId, classId, tutorProfileId,
        studentProfileId, Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), studentUserId));

    Map<UUID, Notification> out = saved();
    assertEquals(java.util.Set.of(tutorUserId, parentUserId), out.keySet());
    assertEquals("SESSION_RESCHEDULED", out.get(tutorUserId).getType());
  }

  @Test
  @DisplayName("B-02/B-03: hủy buổi -> người hủy không tự nhận; hiển thị ghi chú, không lộ tên enum lý do")
  void sessionCancelledShowsTheCommentNotTheEnum() {
    listener.onSessionCancelled(SessionCancelledEvent.of(sessionId, classId, tutorProfileId, studentProfileId,
        tutorUserId, "PERSONAL_REASON", "Con bị ốm", false));

    Map<UUID, Notification> out = saved();
    assertEquals(java.util.Set.of(studentUserId, parentUserId), out.keySet());
    assertEquals("SESSION_CANCELLED", out.get(studentUserId).getType());
    assertTrue(out.get(studentUserId).getShortText().endsWith(": Con bị ốm"));
    assertFalse(out.get(studentUserId).getShortText().contains("PERSONAL_REASON"));
  }

  @Test
  @DisplayName("hủy buổi không có ghi chú -> không hiển thị tên enum")
  void sessionCancelledWithoutCommentHasNoEnumName() {
    listener.onSessionCancelled(SessionCancelledEvent.of(sessionId, classId, tutorProfileId, studentProfileId,
        studentUserId, "PERSONAL_REASON", null, false));

    assertFalse(saved().get(tutorUserId).getShortText().contains("PERSONAL_REASON"));
  }

  @Test
  @DisplayName("B-12: đề xuất hết hạn -> gia sư, học sinh, phụ huynh đều biết")
  void sessionExpiredReachesEveryone() {
    listener.onSessionExpired(SessionExpiredEvent.of(sessionId, classId, tutorProfileId, studentProfileId));

    Map<UUID, Notification> out = saved();
    assertEquals(java.util.Set.of(tutorUserId, studentUserId, parentUserId), out.keySet());
    assertEquals("SESSION_EXPIRED", out.get(tutorUserId).getType());
  }

  @Test
  @DisplayName("B-12: nhắc ghi nhận kết quả chỉ tới gia sư")
  void overdueOutcomeReminderGoesToTheTutorOnly() {
    listener.onOverdueOutcomeReminder(OverdueOutcomeReminderEvent.of(sessionId, classId, tutorProfileId, Instant.now()));

    Map<UUID, Notification> out = saved();
    assertEquals(java.util.Set.of(tutorUserId), out.keySet());
    assertEquals("SESSION_OUTCOME_OVERDUE", out.get(tutorUserId).getType());
  }

  @Test
  @DisplayName("B-15: nhắc lịch tới cả phụ huynh, type riêng")
  void upcomingReminderReachesParents() {
    listener.onSessionUpcomingReminder(SessionUpcomingReminderEvent.of(sessionId, classId, tutorProfileId,
        studentProfileId, Instant.now().plusSeconds(7200), 2));

    Map<UUID, Notification> out = saved();
    assertEquals(java.util.Set.of(tutorUserId, studentUserId, parentUserId), out.keySet());
    assertEquals("SESSION_REMINDER", out.get(parentUserId).getType());
  }

  @Test
  @DisplayName("B-05/B-14: thay đổi lịch hàng loạt -> mỗi người đúng MỘT thông báo cố định, trừ người thực hiện")
  void bulkScheduleChangesProduceOneFixedNotificationPerRecipient() {
    for (String kind : List.of(ClassScheduleChangedEvent.RECURRING_APPLIED, ClassScheduleChangedEvent.CLASS_ENDED,
        ClassScheduleChangedEvent.BATCH_ACCEPTED, ClassScheduleChangedEvent.BATCH_REJECTED)) {
      org.mockito.Mockito.clearInvocations(repository);
      listener.onClassScheduleChanged(ClassScheduleChangedEvent.of(classId, tutorProfileId, studentProfileId,
          studentUserId, kind, 7, 9));

      assertEquals(2, savedCount(), kind);
      Map<UUID, Notification> out = saved();
      assertEquals(java.util.Set.of(tutorUserId, parentUserId), out.keySet(), kind);
      assertEquals("CLASS", out.get(tutorUserId).getTargetType(), kind);
      assertEquals(classId, out.get(tutorUserId).getTargetId(), kind);
    }
  }

  @Test
  @DisplayName("lớp kết thúc báo số buổi bị hủy; áp dụng lịch định kỳ có type riêng")
  void bulkScheduleTextsCarryTheCounts() {
    listener.onClassScheduleChanged(ClassScheduleChangedEvent.of(classId, tutorProfileId, studentProfileId,
        tutorUserId, ClassScheduleChangedEvent.CLASS_ENDED, 7, 0));

    Notification n = saved().get(studentUserId);
    assertEquals("SESSION_BULK_CANCELLED", n.getType());
    assertTrue(n.getShortText().contains("7"));
  }

  @Test
  @DisplayName("B-16: quyết định xác minh chỉ báo gia sư, mỗi quyết định một type")
  void verificationDecisionsNotifyTheTutor() {
    UUID requestId = UUID.randomUUID();
    for (String decision : List.of(VerificationDecidedEvent.APPROVED, VerificationDecidedEvent.REJECTED,
        VerificationDecidedEvent.REVOKED)) {
      org.mockito.Mockito.clearInvocations(repository);
      listener.onVerificationDecided(VerificationDecidedEvent.of(requestId, tutorProfileId, decision, "Lý do"));

      Notification n = saved().get(tutorUserId);
      assertEquals("VERIFICATION_" + decision, n.getType());
      assertEquals("VERIFICATION", n.getTargetType());
      assertEquals(requestId, n.getTargetId());
    }
  }

  @Test
  @DisplayName("sự kiện thiếu hồ sơ gia sư/học sinh thì bỏ qua, không ném lỗi")
  void unknownProfilesAreSkippedSilently() {
    assertDoesNotThrow(() -> listener.onSessionExpired(
        SessionExpiredEvent.of(sessionId, classId, UUID.randomUUID(), UUID.randomUUID())));
    verify(repository, never()).save(any());
  }
}
