package vn.edufit.notification.application.listener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.connection.api.event.LinkInvitationChangedEvent;
import vn.edufit.notification.infra.persistence.entity.Notification;
import vn.edufit.notification.infra.persistence.repository.NotificationRepository;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.review.api.event.ReviewSubmittedEvent;
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
}
