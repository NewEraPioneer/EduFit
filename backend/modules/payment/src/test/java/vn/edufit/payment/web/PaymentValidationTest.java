package vn.edufit.payment.web;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
class PaymentValidationTest {
 @Test void fractionalNegativeMissingOrExcessiveAmountsAreRejected() {
  try(var factory=Validation.buildDefaultValidatorFactory()) {
   var validator=factory.getValidator();
   for(var value:new BigDecimal[]{null,new BigDecimal("-1"),new BigDecimal("9999"),new BigDecimal("10000.5"),new BigDecimal("100000001")})assertFalse(validator.validate(new PaymentController.Money(value)).isEmpty());
   assertTrue(validator.validate(new PaymentController.Money(new BigDecimal("10000"))).isEmpty());
   assertTrue(validator.validate(new PaymentController.Report(BigDecimal.ZERO,"Hoàn đủ")).isEmpty());
   assertFalse(validator.validate(new PaymentController.PayoutAccount("Bank","12345678","Tutor",null,null)).isEmpty());
   assertFalse(validator.validate(new PaymentController.SavedWithdrawal(new BigDecimal("10000"),null)).isEmpty());
  }
 }
}
