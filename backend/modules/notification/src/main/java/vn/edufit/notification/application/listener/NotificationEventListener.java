package vn.edufit.notification.application.listener;

import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.connection.api.event.ConnectionRequestChangedEvent;
import vn.edufit.connection.api.event.LinkInvitationChangedEvent;
import vn.edufit.connection.api.event.ParentStudentLinkChangedEvent;
import vn.edufit.notification.infra.persistence.entity.Notification;
import vn.edufit.notification.infra.persistence.repository.NotificationRepository;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.review.api.event.ReviewSubmittedEvent;
import vn.edufit.scheduling.api.event.SessionCancelledEvent;
import vn.edufit.scheduling.api.event.SessionProposedEvent;
import vn.edufit.scheduling.api.event.SessionRescheduledEvent;
import vn.edufit.scheduling.api.event.SessionScheduledEvent;
import vn.edufit.scheduling.api.event.SessionUpcomingReminderEvent;
import vn.edufit.shared.domain.BaseDomainEvent;

/**
 * Trung tâm đón nhận và chuyển hóa các Sự kiện Miền (Domain Events) thành thông báo người dùng.
 *
 * <p>Tiếp nhận sự kiện từ 4 module nghiệp vụ:
 * <ul>
 *   <li>{@code connection}: Lời mời liên kết, yêu cầu kết nối gia sư, thu hồi ủy quyền.</li>
 *   <li>{@code scheduling}: Đề xuất giờ học, xác nhận buổi học, đổi lịch, hủy lịch, nhắc nhở trước giờ học.</li>
 *   <li>{@code review}: Đánh giá gia sư mới từ học sinh/phụ huynh.</li>
 * </ul>
 */
@Slf4j
@Component
public class NotificationEventListener {

  private final NotificationRepository repository;
  private final ConnectionFacade connectionFacade;
  private final ProfileFacade profileFacade;

  public NotificationEventListener(
      NotificationRepository repository,
      ConnectionFacade connectionFacade,
      ProfileFacade profileFacade
  ) {
    this.repository = repository;
    this.connectionFacade = connectionFacade;
    this.profileFacade = profileFacade;
  }

  // =========================================================================
  // 1. MODULE CONNECTION EVENTS
  // =========================================================================

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
  @Transactional
  public void onInvitationChanged(LinkInvitationChangedEvent event) {
    if (event == null) return;
    switch (event.newStatus()) {
      case "PENDING" -> {
        if (event.oldStatus() == null) {
          repository.save(new Notification(event.invitedUserId(), event.studentId(), "LINK_INVITED",
              "Lời mời liên kết", "Bạn có lời mời liên kết mới", "LINK_INVITATION", event.aggregateId(), event.occurredAt()));
        }
      }
      case "ACCEPTED" -> {
        repository.save(new Notification(event.inviterUserId(), event.studentId(), "LINK_CONFIRMED",
            "Liên kết đã xác nhận", "Người nhận đã chấp nhận lời mời", "PARENT_STUDENT_LINK",
            Objects.requireNonNull(event.linkId()), event.occurredAt()));
      }
      case "DECLINED" -> {
        repository.save(new Notification(event.inviterUserId(), null, "LINK_DECLINED",
            "Lời mời đã bị từ chối", "Người nhận đã từ chối lời mời", "LINK_INVITATION", event.aggregateId(), event.occurredAt()));
      }
      case "EXPIRED" -> {
        repository.save(new Notification(event.inviterUserId(), event.studentId(), "LINK_EXPIRED",
            "Lời mời đã hết hạn", "Lời mời liên kết đã hết hạn", "LINK_INVITATION",
            event.aggregateId(), event.occurredAt()));
      }
      default -> {}
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
  @Transactional
  public void onLinkChanged(ParentStudentLinkChangedEvent event) {
    if (event == null) return;
    if ("REVOKED".equals(event.newStatus())) {
      repository.save(new Notification(event.parentUserId(), null, "LINK_REVOKED",
          "Liên kết đã thu hồi", "Quyền truy cập hồ sơ học sinh đã kết thúc", "PARENT_STUDENT_LINK",
          event.aggregateId(), event.occurredAt()));
      if (!event.studentUserId().equals(event.actorUserId())) {
        repository.save(new Notification(event.studentUserId(), event.studentId(), "LINK_REVOKED",
            "Liên kết đã thu hồi", "Liên kết phụ huynh đã được thu hồi", "PARENT_STUDENT_LINK",
            event.aggregateId(), event.occurredAt()));
      }
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
  @Transactional
  public void onRequestChanged(ConnectionRequestChangedEvent event) {
    if (event == null) return;
    switch (event.newStatus()) {
      case "PENDING" -> {
        if (event.oldStatus() == null) {
          repository.save(new Notification(event.tutorUserId(), event.studentId(), "CONNECTION_REQUESTED",
              "Yêu cầu kết nối mới", "Bạn có yêu cầu dạy học mới", "CONNECTION_REQUEST",
              event.aggregateId(), event.occurredAt()));
          if (!event.studentUserId().equals(event.sentByUserId())) {
            repository.save(new Notification(event.studentUserId(), event.studentId(), "CONNECTION_REQUESTED",
                "Yêu cầu kết nối mới", "Phụ huynh đã gửi yêu cầu kết nối gia sư", "CONNECTION_REQUEST",
                event.aggregateId(), event.occurredAt()));
          }
        }
      }
      case "CANCELLED" -> {
        repository.save(new Notification(event.tutorUserId(), event.studentId(), "CONNECTION_CANCELLED",
            "Yêu cầu đã bị hủy", "Yêu cầu kết nối đã bị hủy", "CONNECTION_REQUEST",
            event.aggregateId(), event.occurredAt()));
        if (!event.studentUserId().equals(event.actorUserId())) {
          repository.save(new Notification(event.studentUserId(), event.studentId(), "CONNECTION_CANCELLED",
              "Yêu cầu đã bị hủy", "Yêu cầu kết nối đã bị hủy", "CONNECTION_REQUEST",
              event.aggregateId(), event.occurredAt()));
        }
      }
      case "ACCEPTED" -> {
        repository.save(new Notification(event.studentUserId(), event.studentId(), "CONNECTION_ACCEPTED",
            "Yêu cầu đã được chấp nhận", "Gia sư đã chấp nhận yêu cầu", "CLASS",
            Objects.requireNonNull(event.classId(), "classId"), event.occurredAt()));
        if (!event.studentUserId().equals(event.sentByUserId())
            && connectionFacade != null && connectionFacade.hasConfirmedParentLink(event.sentByUserId(), event.studentId())) {
          repository.save(new Notification(event.sentByUserId(), event.studentId(), "CONNECTION_ACCEPTED",
              "Yêu cầu đã được chấp nhận", "Gia sư đã chấp nhận yêu cầu", "CLASS",
              event.classId(), event.occurredAt()));
        }
      }
      case "REJECTED" -> {
        repository.save(new Notification(event.studentUserId(), event.studentId(), "CONNECTION_REJECTED",
            "Yêu cầu đã bị từ chối", "Gia sư đã từ chối yêu cầu", "CONNECTION_REQUEST",
            event.aggregateId(), event.occurredAt()));
        if (!event.studentUserId().equals(event.sentByUserId())
            && connectionFacade != null && connectionFacade.hasConfirmedParentLink(event.sentByUserId(), event.studentId())) {
          repository.save(new Notification(event.sentByUserId(), event.studentId(), "CONNECTION_REJECTED",
              "Yêu cầu đã bị từ chối", "Gia sư đã từ chối yêu cầu", "CONNECTION_REQUEST",
              event.aggregateId(), event.occurredAt()));
        }
      }
      case "EXPIRED" -> {
        repository.save(new Notification(event.tutorUserId(), event.studentId(), "CONNECTION_EXPIRED",
            "Yêu cầu đã hết hạn", "Yêu cầu kết nối đã hết hạn", "CONNECTION_REQUEST",
            event.aggregateId(), event.occurredAt()));
        repository.save(new Notification(event.studentUserId(), event.studentId(), "CONNECTION_EXPIRED",
            "Yêu cầu đã hết hạn", "Yêu cầu kết nối đã hết hạn", "CONNECTION_REQUEST",
            event.aggregateId(), event.occurredAt()));
      }
      default -> {}
    }
  }

  // =========================================================================
  // 2. MODULE SCHEDULING EVENTS
  // =========================================================================

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
  @Transactional
  public void onSessionProposed(SessionProposedEvent event) {
    if (event == null) return;
    resolveRecipientAndNotify(
        event.tutorId(),
        event.studentId(),
        event.proposedBy(),
        "Đề xuất buổi học mới",
        "Có đề xuất buổi học mới cần xác nhận",
        "SESSION",
        event.sessionId()
    );
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
  @Transactional
  public void onSessionScheduled(SessionScheduledEvent event) {
    if (event == null) return;
    notifyBoth(event.tutorId(), event.studentId(), "Buổi học đã chốt lịch", "Buổi học đã được xác nhận vào lịch học", "SESSION", event.sessionId());
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
  @Transactional
  public void onSessionRescheduled(SessionRescheduledEvent event) {
    if (event == null) return;
    notifyBoth(event.tutorId(), event.studentId(), "Lịch học đã thay đổi", "Thời gian buổi học đã được cập nhật lại", "SESSION", event.sessionId());
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
  @Transactional
  public void onSessionCancelled(SessionCancelledEvent event) {
    if (event == null) return;
    String detail = event.cancelReason() != null ? event.cancelReason() : event.cancelComment();
    String reason = detail != null && !detail.isBlank() ? ": " + detail : "";
    notifyBoth(event.tutorId(), event.studentId(), "Buổi học đã bị hủy", "Buổi học đã bị hủy" + reason, "SESSION", event.sessionId());
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
  @Transactional
  public void onSessionUpcomingReminder(SessionUpcomingReminderEvent event) {
    if (event == null) return;
    String msg = String.format("Buổi học của bạn sẽ bắt đầu sau %d giờ", event.hoursBefore());
    notifyBoth(event.tutorId(), event.studentId(), "Nhắc nhở buổi học sắp tới", msg, "SESSION", event.sessionId());
  }

  // =========================================================================
  // 3. MODULE REVIEW EVENTS
  // =========================================================================

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
  @Transactional
  public void onReviewSubmitted(ReviewSubmittedEvent event) {
    if (event == null) return;
    if (profileFacade != null) {
      profileFacade.findTutorById(event.tutorId()).ifPresent(tutor -> {
        if (tutor.userId() != null) {
          repository.save(new Notification(
              tutor.userId(),
              event.studentId(),
              "REVIEW_RECEIVED",
              "Đánh giá mới từ học sinh",
              String.format("Bạn vừa nhận được đánh giá %d sao từ học sinh", event.rating()),
              "REVIEW",
              event.reviewId()
          ));
        }
      });
    }
  }

  @EventListener
  public void handleDomainEvent(BaseDomainEvent event) {
    log.debug("DomainEvent received: {} [id: {}]", event.getClass().getSimpleName(), event.getEventId());
  }

  private void notifyBoth(UUID tutorProfileId, UUID studentProfileId, String title, String shortText, String targetType, UUID targetId) {
    if (profileFacade == null) return;
    profileFacade.findTutorById(tutorProfileId).ifPresent(tutor -> {
      if (tutor.userId() != null) {
        repository.save(new Notification(tutor.userId(), studentProfileId, "SESSION_UPDATE", title, shortText, targetType, targetId));
      }
    });
    profileFacade.findStudentById(studentProfileId).ifPresent(student -> {
      if (student.userId() != null) {
        repository.save(new Notification(student.userId(), studentProfileId, "SESSION_UPDATE", title, shortText, targetType, targetId));
      }
    });
  }

  private void resolveRecipientAndNotify(UUID tutorProfileId, UUID studentProfileId, UUID actorUserId, String title, String shortText, String targetType, UUID targetId) {
    if (profileFacade == null) return;
    profileFacade.findTutorById(tutorProfileId).ifPresent(tutor -> {
      profileFacade.findStudentById(studentProfileId).ifPresent(student -> {
        UUID recipientUserId = actorUserId != null && actorUserId.equals(tutor.userId())
            ? student.userId()
            : tutor.userId();
        if (recipientUserId != null) {
          repository.save(new Notification(recipientUserId, studentProfileId, "SESSION_PROPOSAL", title, shortText, targetType, targetId));
        }
      });
    });
  }
}
