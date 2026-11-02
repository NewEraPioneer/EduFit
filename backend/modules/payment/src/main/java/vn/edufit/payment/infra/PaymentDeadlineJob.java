package vn.edufit.payment.infra;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import vn.edufit.payment.application.SessionPaymentService;

@Configuration
public class PaymentDeadlineJob {
  private final SessionPaymentService service;

  public PaymentDeadlineJob(SessionPaymentService service) {
    this.service = service;
  }

  @Scheduled(fixedDelayString = "${edufit.payment.deadline-delay-ms:30000}")
  public void run() {
    service.processDeadlines();
  }
}
