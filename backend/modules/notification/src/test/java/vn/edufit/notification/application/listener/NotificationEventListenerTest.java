package vn.edufit.notification.application.listener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.connection.api.event.LinkInvitationChangedEvent;
import vn.edufit.notification.infra.persistence.entity.Notification;
import vn.edufit.notification.infra.persistence.repository.NotificationRepository;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.review.api.event.ReviewSubmittedEvent;
import vn.edufit.scheduling.api.event.SessionRescheduleProposedEvent;
import vn.edufit.scheduling.api.event.SessionRescheduleRejectedEvent;
import vn.edufit.scheduling.api.event.SessionRescheduledEvent;
import vn.edufit.shared.domain.BaseDomainEvent;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

  @Mock
  private NotificationRepository repository;

  @Mock
  private ConnectionFacade connectionFacade;

  @Mock
  private ProfileFacade profileFacade;

  private NotificationEventListener listener;

  @BeforeEach
  void setUp() {
    listener = new NotificationEventListener(repository, connectionFacade, profileFacade);
  }

  @Test
  @DisplayName("handleDomainEvent: Tiếp nhận BaseDomainEvent an toàn không ném ngoại lệ")
  void handleDomainEvent_processesEventSafely() {
    BaseDomainEvent dummyEvent = new BaseDomainEvent() {
      private final UUID eventId = UUID.randomUUID();
      private final Instant occurredAt = Instant.now();

      @Override
      public UUID getEventId() {
        return eventId;
      }

      @Override
      public Instant getOccurredAt() {
        return occurredAt;
      }
    };

    assertDoesNotThrow(() -> listener.handleDomainEvent(dummyEvent));
  }

  @Test
  @DisplayName("onInvitationChanged: Lưu notification khi nhận LinkInvitationChangedEvent PENDING")
  void onInvitationChanged_pendingSavesNotification() {
    UUID invitedUserId = UUID.randomUUID();
    UUID inviterUserId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    UUID invitationId = UUID.randomUUID();
    LinkInvitationChangedEvent event = new LinkInvitationChangedEvent(
        UUID.randomUUID(),
        invitationId,
        inviterUserId,
        studentId,
        null,
        "PENDING",
        Instant.now(),
        inviterUserId,
        invitedUserId,
        null,
        LinkInvitationChangedEvent.Cause.USER_ACTION
    );

    listener.onInvitationChanged(event);
    verify(repository).save(any(Notification.class));
  }

  @Test
  @DisplayName("onReviewSubmitted: Lưu notification cho tutor userId")
  void onReviewSubmitted_savesNotificationForTutor() {
    UUID tutorProfileId = UUID.randomUUID();
    UUID studentProfileId = UUID.randomUUID();
    UUID tutorUserId = UUID.randomUUID();
    UUID reviewerUserId = UUID.randomUUID();
    UUID reviewId = UUID.randomUUID();

    TutorSummaryDto tutorDto = new TutorSummaryDto(tutorProfileId, tutorUserId, "Tutor Name", "headline", "bio", "ONLINE", null, false, null, "HN", (short) 2, "method", "ACTIVE", Instant.now(), java.math.BigDecimal.valueOf(5.0), 1);

    when(profileFacade.findTutorById(tutorProfileId)).thenReturn(Optional.of(tutorDto));

    ReviewSubmittedEvent event = ReviewSubmittedEvent.of(
        reviewId, tutorProfileId, studentProfileId, reviewerUserId, "STUDENT", 5
    );
    listener.onReviewSubmitted(event);

    verify(repository).save(any(Notification.class));
  }

  @Test
  @DisplayName("onSessionRescheduleProposed: Lưu notification cho đối tác và phụ huynh đã xác nhận")
  void onSessionRescheduleProposed_savesNotificationForCounterpartyAndParents() {
    UUID tutorProfileId = UUID.randomUUID();
    UUID studentProfileId = UUID.randomUUID();
    UUID tutorUserId = UUID.randomUUID();
    UUID studentUserId = UUID.randomUUID();
    UUID parentUserId = UUID.randomUUID();
    UUID proposalId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    UUID classId = UUID.randomUUID();

    TutorSummaryDto tutorDto = new TutorSummaryDto(tutorProfileId, tutorUserId, "Tutor Name", "headline", "bio", "ONLINE", null, false, null, "HN", (short) 2, "method", "ACTIVE", Instant.now(), java.math.BigDecimal.valueOf(5.0), 1);
    StudentSummaryDto studentDto = new StudentSummaryDto(studentProfileId, studentUserId, 1, 1, 1, true);

    when(profileFacade.findTutorById(tutorProfileId)).thenReturn(Optional.of(tutorDto));
    when(profileFacade.findStudentById(studentProfileId)).thenReturn(Optional.of(studentDto));
    when(connectionFacade.findConfirmedParentUserIds(studentProfileId)).thenReturn(List.of(parentUserId));

    // Gia sư đề xuất -> Đối tác nhận là học sinh, phụ huynh cũng nhận
    SessionRescheduleProposedEvent event = SessionRescheduleProposedEvent.of(
        proposalId, sessionId, classId, tutorProfileId, studentProfileId,
        tutorUserId, Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200),
        "Bận việc đột xuất", Instant.now().plusSeconds(1800)
    );

    listener.onSessionRescheduleProposed(event);

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(repository, times(2)).save(captor.capture());

    List<Notification> saved = captor.getAllValues();
    assertEquals(studentUserId, saved.get(0).getUserId());
    assertEquals("SESSION_RESCHEDULE_PROPOSED", saved.get(0).getType());
    assertEquals(parentUserId, saved.get(1).getUserId());
    assertEquals("SESSION_RESCHEDULE_PROPOSED", saved.get(1).getType());
  }

  @Test
  @DisplayName("onSessionRescheduleRejected: Lưu notification cho người đề xuất")
  void onSessionRescheduleRejected_notifiesProposer() {
    UUID proposalId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    UUID classId = UUID.randomUUID();
    UUID tutorProfileId = UUID.randomUUID();
    UUID studentProfileId = UUID.randomUUID();
    UUID tutorUserId = UUID.randomUUID();
    UUID studentUserId = UUID.randomUUID();

    SessionRescheduleRejectedEvent event = SessionRescheduleRejectedEvent.of(
        proposalId, sessionId, classId, tutorProfileId, studentProfileId,
        tutorUserId, studentUserId, "Kẹt lịch học ở trường"
    );

    listener.onSessionRescheduleRejected(event);

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(repository).save(captor.capture());

    Notification saved = captor.getValue();
    assertEquals(tutorUserId, saved.getUserId());
    assertEquals("SESSION_RESCHEDULE_REJECTED", saved.getType());
  }

  @Test
  @DisplayName("onSessionRescheduled: Lưu notification cho gia sư, học sinh và phụ huynh đã xác nhận")
  void onSessionRescheduled_notifiesBothAndParents() {
    UUID tutorProfileId = UUID.randomUUID();
    UUID studentProfileId = UUID.randomUUID();
    UUID tutorUserId = UUID.randomUUID();
    UUID studentUserId = UUID.randomUUID();
    UUID parentUserId = UUID.randomUUID();
    UUID proposalId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    UUID classId = UUID.randomUUID();

    TutorSummaryDto tutorDto = new TutorSummaryDto(tutorProfileId, tutorUserId, "Tutor Name", "headline", "bio", "ONLINE", null, false, null, "HN", (short) 2, "method", "ACTIVE", Instant.now(), java.math.BigDecimal.valueOf(5.0), 1);
    StudentSummaryDto studentDto = new StudentSummaryDto(studentProfileId, studentUserId, 1, 1, 1, true);

    when(profileFacade.findTutorById(tutorProfileId)).thenReturn(Optional.of(tutorDto));
    when(profileFacade.findStudentById(studentProfileId)).thenReturn(Optional.of(studentDto));
    when(connectionFacade.findConfirmedParentUserIds(studentProfileId)).thenReturn(List.of(parentUserId));

    SessionRescheduledEvent event = SessionRescheduledEvent.of(
        proposalId, sessionId, classId, tutorProfileId, studentProfileId,
        Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200)
    );

    listener.onSessionRescheduled(event);

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(repository, times(3)).save(captor.capture());

    List<Notification> saved = captor.getAllValues();
    assertEquals(tutorUserId, saved.get(0).getUserId());
    assertEquals(studentUserId, saved.get(1).getUserId());
    assertEquals(parentUserId, saved.get(2).getUserId());
    assertEquals("SESSION_UPDATE", saved.get(2).getType());
  }
}
