package vn.edufit.notification.application.listener;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.edufit.iam.api.IamFacade;
import vn.edufit.payment.api.event.PaymentSessionChangedEvent;
import vn.edufit.payment.api.event.WalletBalanceChangedEvent;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.shared.notification.NotificationSink;

/** Synchronous in-transaction delivery also works when an outcome listener opens the window. */
@Component
public class PaymentNotificationListener {
  private final NotificationSink sink;
  private final ProfileFacade profiles;
  private final IamFacade accounts;
  public PaymentNotificationListener(NotificationSink sink, ProfileFacade profiles, IamFacade accounts) {
    this.sink = sink; this.profiles = profiles; this.accounts = accounts;
  }
  @org.springframework.context.event.EventListener
  @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public void onWalletChanged(WalletBalanceChangedEvent event) {
    String title = switch (event.kind()) {
      case "TOPUP" -> "Nạp tiền thành công";
      case "WITHDRAW_RESERVED" -> "Đã tiếp nhận yêu cầu rút tiền";
      case "WITHDRAW_PAID" -> "Rút tiền thành công";
      case "WITHDRAW_REJECTED", "WITHDRAW_FAILED" -> "Khoản rút đã được hoàn về ví";
      default -> null;
    };
    if (title == null) return;
    sink.notify(event.userId(), null, "PAYMENT_" + event.kind(), title,
        "Số dư thay đổi " + event.availableDelta() + " VND; đang giữ thay đổi " + event.heldDelta()
            + " VND. Xem sổ ví để biết chi tiết.",
        "PAYMENT_WALLET", event.referenceId());
  }

  @org.springframework.context.event.EventListener
  @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public void onChange(PaymentSessionChangedEvent event) {
    String title = switch (event.state()) {
      case "AWAITING_CONFIRMATION" -> "Buổi học đã hoàn thành: có 24 giờ để phản hồi";
      case "DISPUTED" -> "Đã mở hồ sơ vấn đề; học phí đang tạm giữ";
      case "AWAITING_ADMIN" -> "Hai bên thống nhất; chờ Admin xem xét";
      case "ADMIN_REVIEW" -> "Hết hạn phản hồi; Admin sẽ phân xử, tiền vẫn tạm giữ";
      case "SETTLED" -> "Học phí buổi học đã được tất toán";
      case "REFUNDED" -> "Học phí đã hoàn về ví người trả tiền";
      case "CASE_MESSAGE" -> "Có phản hồi mới trong hồ sơ vấn đề buổi học";
      default -> null;
    };
    if (title == null) return;
    Set<UUID> recipients = new HashSet<>();
    recipients.add(event.payerId());
    recipients.add(event.tutorUserId());
    profiles.findStudentById(event.studentId()).ifPresent(student -> {
      if (student.userId() != null) recipients.add(student.userId());
    });
    // Admin cần biết khi hồ sơ chuyển sang họ, và khi có tin nhắn mới lúc hồ sơ đang ở tay họ.
    Set<String> adminStates = Set.of("AWAITING_ADMIN", "ADMIN_REVIEW");
    if (adminStates.contains(event.state())
        || ("CASE_MESSAGE".equals(event.state()) && adminStates.contains(event.sessionStatus())))
      recipients.addAll(accounts.findActiveAdminIds());
    recipients.remove(event.actorUserId()); // người vừa viết không tự nhận thông báo về chính mình
    String detail = "AWAITING_CONFIRMATION".equals(event.state())
        ? "Hạn phản hồi: " + java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).format(event.reviewDeadline()) + " (giờ Việt Nam)" + ". Xác nhận đã học không kết thúc thời hạn báo vấn đề."
        : "Mở chi tiết buổi học để xem trạng thái tiền, phản hồi và bước tiếp theo.";
    for (UUID user : recipients) sink.notify(user, event.studentId(), "PAYMENT_" + event.state(),
        title, detail, "PAYMENT_SESSION", event.sessionId());
  }
}
