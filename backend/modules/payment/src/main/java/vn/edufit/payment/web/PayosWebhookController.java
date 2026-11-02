package vn.edufit.payment.web;

import java.util.Map;
import org.springframework.web.bind.annotation.*;
import vn.edufit.payment.application.WalletService;

@RestController
public class PayosWebhookController {
  private final WalletService service;

  public PayosWebhookController(WalletService service) {
    this.service = service;
  }

  @PostMapping("/api/v1/payment/webhooks/payos")
  public Map<String, Object> receive(@RequestBody String payload) {
    service.webhook(payload);
    return Map.of("success", true);
  }
}
