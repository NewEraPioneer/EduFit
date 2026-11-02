package vn.edufit.payment.application;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;
/** Own time source: deliberately not a Clock bean, so IAM/Scheduling defaults stay unambiguous. */
@Component
public final class PaymentTime {
 private final Clock clock;
 public PaymentTime(Clock clock) {this.clock=java.util.Objects.requireNonNull(clock);}
 public Instant instant() {return clock.instant();}
}
