package vn.edufit.payment.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.edufit.scheduling.api.event.SessionOutcomeRecordedEvent;

@Component
public class PaymentOutcomeListener {
  private final SessionPaymentService service;
  public PaymentOutcomeListener(SessionPaymentService service) { this.service = service; }
  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onOutcome(SessionOutcomeRecordedEvent event) {
    if ("COMPLETED".equals(event.outcome())) service.outcomeRecorded(event.sessionId(), event.recordedAt());
  }
}
