package vn.edufit.notification.application.listener;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.connection.api.event.ConnectionRequestChangedEvent;
import vn.edufit.connection.api.event.LinkInvitationChangedEvent;
import vn.edufit.connection.api.event.ParentStudentLinkChangedEvent;
import vn.edufit.notification.application.service.NotificationService;
import vn.edufit.profile.api.ProfileFacade;
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

/**
 * Trung tâm đón nhận và chuyển hóa các Sự kiện Miền (Domain Events) thành thông báo người dùng.
 *
 * <p>Tiếp nhận sự kiện từ các module nghiệp vụ:
 * <ul>
 *   <li>{@code connection}: Lời mời liên kết, yêu cầu kết nối gia sư, thu hồi ủy quyền.</li>
 *   <li>{@code scheduling}: Đề xuất giờ học, xác nhận, đổi lịch, hủy, hết hạn, nhắc lịch và thay đổi hàng loạt.</li>
 *   <li>{@code review}: Đánh giá gia sư mới hoặc được cập nhật.</li>
 *   <li>{@code verification}: Quyết định duyệt, từ chối, thu hồi hồ sơ xác minh gia sư.</li>
 * </ul>
 *
 * <p>Quy tắc người nhận của mọi sự kiện buổi học nằm ở {@link #participants}: gia sư, học sinh và phụ huynh đã
 * xác nhận, trừ người vừa thực hiện hành động. Mỗi loại sự kiện có {@code type} riêng để frontend phân biệt.
 *
 * <p>Thiếu dữ liệu tham chiếu (ví dụ id đích) chỉ làm bỏ thông báo đó, không ném lỗi để khỏi rollback nghiệp vụ.
 */
@Slf4j
@Component
public class NotificationEventListener {

  private final NotificationService notifications;
  private final ConnectionFacade connectionFacade;
  private final ProfileFacade profileFacade;

  public NotificationEventListener(
      NotificationService notifications,
      ConnectionFacade connectionFacade,
      ProfileFacade profileFacade
  ) {
    this.notifications = notifications;
    this.connectionFacade = connectionFacade;
    this.profileFacade = profileFacade;
  }

  // =========================================================================
  // 1. MODULE CONNECTION EVENTS
  // =========================================================================

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void onInvitationChanged(LinkInvitationChangedEvent event) {
    if (event == null) return;
    switch (event.newStatus()) {
      case "PENDING" -> {
        if (event.oldStatus() == null) {
          write(event.invitedUserId(), event.studentId(), "LINK_INVITED",
              "Lời mời liên kết", "Bạn có lời mời liên kết mới", "LINK_INVITATION", event.aggregateId(), event.occurredAt());
        }
      }
      case "ACCEPTED" -> {
        boolean hasLink = event.linkId() != null;
        write(event.inviterUserId(), event.studentId(), "LINK_CONFIRMED",
            "Liên kết đã xác nhận", "Người nhận đã chấp nhận lời mời",
            hasLink ? "PARENT_STUDENT_LINK" : "LINK_INVITATION",
            hasLink ? event.linkId() : event.aggregateId(), event.occurredAt());
      }
      case "DECLINED" -> {
        write(event.inviterUserId(), null, "LINK_DECLINED",
            "Lời mời đã bị từ chối", "Người nhận đã từ chối lời mời", "LINK_INVITATION", event.aggregateId(), event.occurredAt());
      }
      case "EXPIRED" -> {
        write(event.inviterUserId(), event.studentId(), "LINK_EXPIRED",
            "Lời mời đã hết hạn", "Lời mời liên kết đã hết hạn", "LINK_INVITATION",
            event.aggregateId(), event.occurredAt());
      }
      default -> {}
    }
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void onLinkChanged(ParentStudentLinkChangedEvent event) {
    if (event == null) return;
    if ("REVOKED".equals(event.newStatus())) {
      write(event.parentUserId(), null, "LINK_REVOKED",
          "Liên kết đã thu hồi", "Quyền truy cập hồ sơ học sinh đã kết thúc", "PARENT_STUDENT_LINK",
          event.aggregateId(), event.occurredAt());
      if (!event.studentUserId().equals(event.actorUserId())) {
        write(event.studentUserId(), event.studentId(), "LINK_REVOKED",
            "Liên kết đã thu hồi", "Liên kết phụ huynh đã được thu hồi", "PARENT_STUDENT_LINK",
            event.aggregateId(), event.occurredAt());
      }
    }
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void onRequestChanged(ConnectionRequestChangedEvent event) {
    if (event == null) return;
    switch (event.newStatus()) {
      case "PENDING" -> {
        if (event.oldStatus() == null) {
          write(event.tutorUserId(), event.studentId(), "CONNECTION_REQUESTED",
              "Yêu cầu kết nối mới", "Bạn có yêu cầu dạy học mới", "CONNECTION_REQUEST",
              event.aggregateId(), event.occurredAt());
          if (!event.studentUserId().equals(event.sentByUserId())) {
            write(event.studentUserId(), event.studentId(), "CONNECTION_REQUESTED",
                "Yêu cầu kết nối mới", "Phụ huynh đã gửi yêu cầu kết nối gia sư", "CONNECTION_REQUEST",
                event.aggregateId(), event.occurredAt());
          }
        }
      }
      case "CANCELLED" -> {
        write(event.tutorUserId(), event.studentId(), "CONNECTION_CANCELLED",
            "Yêu cầu đã bị hủy", "Yêu cầu kết nối đã bị hủy", "CONNECTION_REQUEST",
            event.aggregateId(), event.occurredAt());
        if (!event.studentUserId().equals(event.actorUserId())) {
          write(event.studentUserId(), event.studentId(), "CONNECTION_CANCELLED",
              "Yêu cầu đã bị hủy", "Yêu cầu kết nối đã bị hủy", "CONNECTION_REQUEST",
              event.aggregateId(), event.occurredAt());
        }
      }
      case "ACCEPTED" -> {
        // Thiếu classId thì vẫn báo, trỏ về yêu cầu kết nối thay vì làm hỏng giao dịch chấp nhận.
        boolean hasClass = event.classId() != null;
        String targetType = hasClass ? "CLASS" : "CONNECTION_REQUEST";
        UUID targetId = hasClass ? event.classId() : event.aggregateId();
        write(event.studentUserId(), event.studentId(), "CONNECTION_ACCEPTED",
            "Yêu cầu đã được chấp nhận", "Gia sư đã chấp nhận yêu cầu", targetType, targetId, event.occurredAt());
        if (!event.studentUserId().equals(event.sentByUserId())
            && connectionFacade.hasConfirmedParentLink(event.sentByUserId(), event.studentId())) {
          write(event.sentByUserId(), event.studentId(), "CONNECTION_ACCEPTED",
              "Yêu cầu đã được chấp nhận", "Gia sư đã chấp nhận yêu cầu", targetType, targetId, event.occurredAt());
        }
      }
      case "REJECTED" -> {
        write(event.studentUserId(), event.studentId(), "CONNECTION_REJECTED",
            "Yêu cầu đã bị từ chối", "Gia sư đã từ chối yêu cầu", "CONNECTION_REQUEST",
            event.aggregateId(), event.occurredAt());
        if (!event.studentUserId().equals(event.sentByUserId())
            && connectionFacade.hasConfirmedParentLink(event.sentByUserId(), event.studentId())) {
          write(event.sentByUserId(), event.studentId(), "CONNECTION_REJECTED",
              "Yêu cầu đã bị từ chối", "Gia sư đã từ chối yêu cầu", "CONNECTION_REQUEST",
              event.aggregateId(), event.occurredAt());
        }
      }
      case "EXPIRED" -> {
        write(event.tutorUserId(), event.studentId(), "CONNECTION_EXPIRED",
            "Yêu cầu đã hết hạn", "Yêu cầu kết nối đã hết hạn", "CONNECTION_REQUEST",
            event.aggregateId(), event.occurredAt());
        write(event.studentUserId(), event.studentId(), "CONNECTION_EXPIRED",
            "Yêu cầu đã hết hạn", "Yêu cầu kết nối đã hết hạn", "CONNECTION_REQUEST",
            event.aggregateId(), event.occurredAt());
      }
      default -> {}
    }
  }

  // =========================================================================
  // 2. MODULE SCHEDULING EVENTS
  // =========================================================================

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void onSessionProposed(SessionProposedEvent event) {
    if (event == null) return;
    notifyParticipants(event.tutorId(), event.studentId(), event.proposedBy(), "SESSION_PROPOSED",
        "Đề xuất buổi học mới", "Có đề xuất buổi học mới cần xác nhận", "SESSION", event.sessionId(), event.occurredAt());
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void onSessionProposalRejected(SessionProposalRejectedEvent event) {
    if (event == null) return;
    notifyParticipants(event.tutorId(), event.studentId(), event.rejectedBy(), "SESSION_PROPOSAL_REJECTED",
        "Đề xuất buổi học bị từ chối", "Đề xuất buổi học đã bị từ chối" + reasonSuffix(event.reason()),
        "SESSION", event.sessionId(), event.occurredAt());
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void onSessionScheduled(SessionScheduledEvent event) {
    if (event == null) return;
    notifyParticipants(event.tutorId(), event.studentId(), event.actorUserId(), "SESSION_SCHEDULED",
        "Buổi học đã chốt lịch", "Buổi học đã được xác nhận vào lịch học", "SESSION", event.sessionId(),
        event.occurredAt());
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void onSessionRescheduled(SessionRescheduledEvent event) {
    if (event == null) return;
    notifyParticipants(event.tutorId(), event.studentId(), event.actorUserId(), "SESSION_RESCHEDULED",
        "Lịch học đã thay đổi", "Thời gian buổi học đã được cập nhật lại", "SESSION", event.sessionId(),
        event.occurredAt());
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void onSessionRescheduleProposed(SessionRescheduleProposedEvent event) {
    if (event == null) return;
    notifyParticipants(event.tutorId(), event.studentId(), event.proposedBy(), "SESSION_RESCHEDULE_PROPOSED",
        "Đề xuất đổi lịch học", "Có đề xuất dời buổi học sang khung giờ mới, cần xác nhận",
        "SESSION", event.sessionId(), event.occurredAt());
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void onSessionRescheduleRejected(SessionRescheduleRejectedEvent event) {
    if (event == null) return;
    // Mọi người đã nhận "cần xác nhận" đều được báo kết thúc, trừ bên vừa từ chối.
    notifyParticipants(event.tutorId(), event.studentId(), event.rejectedBy(), "SESSION_RESCHEDULE_REJECTED",
        "Đề xuất đổi lịch bị từ chối", "Đề xuất đổi lịch buổi học đã bị từ chối" + reasonSuffix(event.reason()),
        "SESSION", event.sessionId(), event.occurredAt());
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void onSessionCancelled(SessionCancelledEvent event) {
    if (event == null) return;
    // Chỉ hiển thị ghi chú người dùng nhập; tên enum lý do (PERSONAL_REASON...) không dành cho người đọc.
    notifyParticipants(event.tutorId(), event.studentId(), event.cancelledBy(), "SESSION_CANCELLED",
        "Buổi học đã bị hủy", "Buổi học đã bị hủy" + reasonSuffix(event.cancelComment()),
        "SESSION", event.sessionId(), event.occurredAt());
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void onSessionExpired(SessionExpiredEvent event) {
    if (event == null) return;
    notifyParticipants(event.tutorId(), event.studentId(), null, "SESSION_EXPIRED",
        "Đề xuất buổi học đã hết hạn", "Đề xuất buổi học không được phản hồi kịp thời nên đã hết hạn",
        "SESSION", event.sessionId(), event.occurredAt());
  }

  /** Nhắc gia sư ghi nhận kết quả buổi đã kết thúc (FR-20). Chỉ gia sư mới có việc phải làm. */
  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void onOverdueOutcomeReminder(OverdueOutcomeReminderEvent event) {
    if (event == null) return;
    profileFacade.findTutorById(event.tutorId()).ifPresent(tutor -> {
      if (tutor.userId() != null) {
        write(tutor.userId(), null, "SESSION_OUTCOME_OVERDUE", "Cần ghi nhận kết quả buổi học",
            "Buổi học đã kết thúc nhưng bạn chưa ghi nhận kết quả (hoàn thành hoặc vắng)", "SESSION",
            event.sessionId(), event.occurredAt());
      }
    });
  }

  /** Thay đổi lịch hàng loạt: đúng một thông báo cố định cho mỗi người, không phải một thông báo cho mỗi buổi. */
  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void onClassScheduleChanged(ClassScheduleChangedEvent event) {
    if (event == null) return;
    String[] text = switch (event.kind()) {
      case ClassScheduleChangedEvent.RECURRING_APPLIED -> new String[] {"SESSION_RECURRING_CHANGED",
          "Lịch học định kỳ đã thay đổi",
          "Lịch cố định mới đã được áp dụng; các buổi cũ chưa diễn ra đã được thay thế"};
      case ClassScheduleChangedEvent.CLASS_ENDED -> new String[] {"SESSION_BULK_CANCELLED",
          "Các buổi học sắp tới đã bị hủy",
          "Lớp học đã kết thúc nên " + event.cancelledCount() + " buổi học sắp tới đã bị hủy"};
      case ClassScheduleChangedEvent.BATCH_ACCEPTED -> new String[] {"SESSION_BATCH_SCHEDULED",
          "Các buổi học đã chốt lịch", event.generatedCount() + " buổi học đã được xác nhận vào lịch học"};
      case ClassScheduleChangedEvent.BATCH_REJECTED -> new String[] {"SESSION_BATCH_REJECTED",
          "Các đề xuất buổi học bị từ chối", event.cancelledCount() + " đề xuất buổi học đã bị từ chối"};
      default -> null;
    };
    if (text == null) return;
    notifyParticipants(event.tutorId(), event.studentId(), event.actorUserId(), text[0], text[1], text[2],
        "CLASS", event.classId(), event.occurredAt());
  }

  /**
   * Nhắc lịch do job quét phát ra, có thể từ giao dịch chỉ đọc (Hibernate không flush nên thông báo sẽ mất âm thầm)
   * hoặc không có giao dịch. Vì vậy ghi trong giao dịch riêng, không phụ thuộc giao dịch của bên phát.
   */
  @EventListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onSessionUpcomingReminder(SessionUpcomingReminderEvent event) {
    if (event == null) return;
    String msg = String.format("Buổi học của bạn sẽ bắt đầu sau %d giờ", event.hoursBefore());
    notifyParticipants(event.tutorId(), event.studentId(), null, "SESSION_REMINDER",
        "Nhắc nhở buổi học sắp tới", msg, "SESSION", event.sessionId(), null);
  }

  // =========================================================================
  // 3. MODULE REVIEW EVENTS
  // =========================================================================

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void onReviewSubmitted(ReviewSubmittedEvent event) {
    if (event == null) return;
    profileFacade.findTutorById(event.tutorId()).ifPresent(tutor -> {
      if (tutor.userId() != null) {
        write(tutor.userId(), event.studentId(), "REVIEW_RECEIVED", "Đánh giá mới từ học sinh",
            String.format("Bạn vừa nhận được đánh giá %d sao từ học sinh", event.rating()),
            "REVIEW", event.reviewId(), null);
      }
    });
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void onReviewUpdated(ReviewUpdatedEvent event) {
    if (event == null) return;
    profileFacade.findTutorById(event.tutorId()).ifPresent(tutor -> {
      if (tutor.userId() != null) {
        write(tutor.userId(), event.studentId(), "REVIEW_UPDATED", "Đánh giá đã được cập nhật",
            String.format("Một đánh giá về bạn đã được chỉnh sửa thành %d sao", event.rating()),
            "REVIEW", event.reviewId(), event.occurredAt());
      }
    });
  }

  // =========================================================================
  // 4. MODULE VERIFICATION EVENTS
  // =========================================================================

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void onVerificationDecided(VerificationDecidedEvent event) {
    if (event == null) return;
    String[] text = switch (event.decision()) {
      case VerificationDecidedEvent.APPROVED -> new String[] {"VERIFICATION_APPROVED",
          "Hồ sơ xác minh đã được duyệt", "Hồ sơ của bạn đã được xác minh; huy hiệu gia sư đã hiển thị trên hồ sơ"};
      case VerificationDecidedEvent.REJECTED -> new String[] {"VERIFICATION_REJECTED",
          "Hồ sơ xác minh bị từ chối", "Hồ sơ xác minh chưa đạt" + reasonSuffix(event.reason())};
      case VerificationDecidedEvent.REVOKED -> new String[] {"VERIFICATION_REVOKED",
          "Xác minh đã bị thu hồi", "Huy hiệu xác minh của bạn đã bị thu hồi" + reasonSuffix(event.reason())};
      default -> null;
    };
    if (text == null) return;
    profileFacade.findTutorById(event.tutorId()).ifPresent(tutor -> {
      if (tutor.userId() != null) {
        write(tutor.userId(), null, text[0], text[1], text[2], "VERIFICATION", event.requestId(), event.occurredAt());
      }
    });
  }

  @EventListener
  public void handleDomainEvent(BaseDomainEvent event) {
    log.debug("DomainEvent received: {} [id: {}]", event.getClass().getSimpleName(), event.getEventId());
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  private static String reasonSuffix(String reason) {
    return reason != null && !reason.isBlank() ? ": " + reason.trim() : "";
  }

  /** Gia sư, học sinh và phụ huynh đã xác nhận của buổi học, trừ người thực hiện hành động. */
  private Set<UUID> participants(UUID tutorProfileId, UUID studentProfileId, UUID actorUserId) {
    Set<UUID> recipients = new LinkedHashSet<>();
    profileFacade.findTutorById(tutorProfileId).ifPresent(tutor -> addIfPresent(recipients, tutor.userId()));
    profileFacade.findStudentById(studentProfileId).ifPresent(student -> addIfPresent(recipients, student.userId()));
    List<UUID> parentUserIds = connectionFacade.findConfirmedParentUserIds(studentProfileId);
    if (parentUserIds != null) parentUserIds.forEach(parent -> addIfPresent(recipients, parent));
    recipients.remove(actorUserId);
    return recipients;
  }

  private static void addIfPresent(Set<UUID> recipients, UUID userId) {
    if (userId != null) recipients.add(userId);
  }

  private void notifyParticipants(UUID tutorProfileId, UUID studentProfileId, UUID actorUserId, String type,
      String title, String text, String targetType, UUID targetId, Instant occurredAt) {
    for (UUID userId : participants(tutorProfileId, studentProfileId, actorUserId)) {
      write(userId, studentProfileId, type, title, text, targetType, targetId, occurredAt);
    }
  }

  private void write(UUID userId, UUID studentId, String type, String title, String text,
      String targetType, UUID targetId, Instant occurredAt) {
    notifications.createNotification(userId, studentId, type, title, text, targetType, targetId, occurredAt);
  }
}
