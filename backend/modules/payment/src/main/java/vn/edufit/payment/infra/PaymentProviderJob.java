package vn.edufit.payment.infra;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.edufit.payment.application.*;

@Component
public class PaymentProviderJob {
  private final PaymentReconciliationService topups;
  private final PaymentPayoutService payouts;

  public PaymentProviderJob(PaymentReconciliationService topups, PaymentPayoutService payouts) {
    this.topups = topups;
    this.payouts = payouts;
  }

  @Scheduled(fixedDelayString = "${edufit.payment.provider-delay-ms:30000}")
  public void reconcile() {
    topups.process();
    payouts.process();
  }
}
