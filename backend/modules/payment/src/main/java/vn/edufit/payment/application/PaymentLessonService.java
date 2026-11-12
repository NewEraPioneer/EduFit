package vn.edufit.payment.application;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.payment.infra.PaymentStore;
import vn.edufit.scheduling.api.SessionFacade;
import vn.edufit.scheduling.api.SessionFundingGuard;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.*;

@Service
public class PaymentLessonService implements SessionFundingGuard {
  private final PaymentStore store;
  private final SessionPaymentService payments;
  private final SessionFacade sessions;
  private final PaymentTime time;

  public PaymentLessonService(PaymentStore store, SessionPaymentService payments, SessionFacade sessions, PaymentTime time) {
    this.store=store; this.payments=payments; this.sessions=sessions; this.time=time;
  }

  public record LearningAccess(boolean funded, Instant startedAt, boolean canStart, String message, String meetingLink) {}

  @Override
  public void requireFunded(UUID id) {
    if (!funded(id)) throw new InvalidOperationException("Buổi học chưa được giữ học phí. Người học cần chọn ví và xác nhận giữ tiền trước khi học.");
  }

  private boolean funded(UUID id) {
    return store.find(id).map(c -> c.state().awaitingConfirmation()).orElse(false);
  }

  public LearningAccess readiness(CurrentUser actor, UUID id) {
    var payment = payments.get(actor,id); // Authorization, including payer-only historical access.
    boolean funded = funded(id);
    boolean learnerOrTutor = actor.hasRole("STUDENT") && payment.canActAsLearner()
        || actor.hasRole("TUTOR") && payment.canActAsTutor();
    boolean scheduled = "SCHEDULED".equals(payment.sessionStatus());
    boolean window = !time.instant().isBefore(payment.startAt()) && time.instant().isBefore(payment.endAt());
    String message = !funded ? "Chưa giữ học phí. Người học cần xác nhận giữ tiền từ ví trước khi học."
        : !scheduled ? "Buổi học không còn ở trạng thái đã lên lịch."
        : !window ? "Chỉ bắt đầu học trong thời gian của buổi học đã lên lịch."
        : !learnerOrTutor ? "Phụ huynh có thể cấp học phí; học sinh hoặc gia sư bắt đầu buổi học."
        : "Đã giữ học phí, sẵn sàng bắt đầu buổi học.";
    Instant started = store.learningStartedAt(id);
    boolean canStart = funded && scheduled && window && learnerOrTutor;
    String link = started != null && canStart ? sessions.findMeetingLink(id).filter(PaymentLessonService::isWebLink).orElse(null) : null;
    return new LearningAccess(funded, started, canStart, message, link);
  }

  @Transactional
  public LearningAccess start(CurrentUser actor, UUID id) {
    payments.get(actor,id); // Check access before acquiring a session lock.
    sessions.lockSessionById(id).orElseThrow(() -> EntityNotFoundException.of("Session",id));
    return store.locked(() -> {
      var state = readiness(actor,id);
      if (!state.canStart()) throw new InvalidOperationException(state.message());
      var source = sessions.findSessionById(id).orElseThrow(() -> EntityNotFoundException.of("Session",id));
      String link = "ONLINE".equals(source.mode()) ? sessions.findMeetingLink(id).filter(PaymentLessonService::isWebLink).orElse(null) : null;
      if ("ONLINE".equals(source.mode()) && link == null) throw new InvalidOperationException("Gia sư cần cập nhật liên kết phòng học hợp lệ trước khi bắt đầu.");
      store.recordLearningStart(id,actor.getUserId(),time.instant());
      return new LearningAccess(true, store.learningStartedAt(id), true, "Buổi học đã bắt đầu", link);
    });
  }

  private static boolean isWebLink(String value) {
    try {
      URI uri=URI.create(value);
      return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
          && uri.getHost()!=null && uri.getUserInfo()==null;
    } catch (IllegalArgumentException e) { return false; }
  }
}
