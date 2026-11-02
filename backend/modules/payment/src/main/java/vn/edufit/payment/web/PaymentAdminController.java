package vn.edufit.payment.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vn.edufit.payment.application.*;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/admin/payment")
@Validated
public class PaymentAdminController {
  private final WalletService wallets;
  private final PaymentPayoutService payouts;
  private final SessionPaymentService sessions;
  private final ObjectProvider<CurrentUser> actors;

  public PaymentAdminController(
      WalletService wallets,
      SessionPaymentService sessions,
      ObjectProvider<CurrentUser> actors,
      PaymentPayoutService payouts) {
    this.wallets = wallets;
    this.payouts = payouts;
    this.sessions = sessions;
    this.actors = actors;
  }

  public record Review(
      boolean approve,
      @NotBlank @Size(max = 2000) String note,
      @Size(max = 200) String reference) {}

  @GetMapping("/configuration")
  public ApiResponse<?> configuration() {
    return ApiResponse.success(wallets.configuration(actors.getObject()));
  }

  @GetMapping
  public ApiResponse<?> list(@RequestParam(defaultValue = "0") @Min(0) @Max(100000) int offset) {
    return ApiResponse.success(wallets.admin(actors.getObject(), offset));
  }

  @GetMapping("/disputes")
  public ApiResponse<?> disputes(
      @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int offset) {
    return ApiResponse.success(sessions.adminList(actors.getObject(), offset));
  }

  @PostMapping("/topups/{id}/confirm")
  public ApiResponse<?> confirm(@PathVariable UUID id, @Valid @RequestBody Review input) {
    wallets.confirmManual(actors.getObject(), id, input.reference());
    return ApiResponse.ok();
  }

  @PostMapping("/withdrawals/{id}/review")
  public ApiResponse<?> withdrawal(@PathVariable UUID id, @Valid @RequestBody Review input) {
    wallets.reviewWithdrawal(
        actors.getObject(), id, input.approve(), input.reference(), input.note());
    return ApiResponse.ok();
  }

  @PostMapping("/withdrawals/{id}/payout")
  public ApiResponse<?> payout(@PathVariable UUID id, @Valid @RequestBody Review input) {
    return ApiResponse.success(payouts.approve(actors.getObject(), id, input.note()));
  }

  @PostMapping("/withdrawals/{id}/refresh")
  public ApiResponse<?> refreshPayout(@PathVariable UUID id) {
    return ApiResponse.success(payouts.refresh(actors.getObject(), id));
  }

  @PostMapping("/disputes/{id}/review")
  public ApiResponse<?> dispute(@PathVariable UUID id, @Valid @RequestBody Review input) {
    return ApiResponse.success(
        sessions.review(actors.getObject(), id, input.approve(), input.note()));
  }

  public record Resolution(@NotNull @DecimalMin("0") @DecimalMax("100000000")
      @Digits(integer = 9, fraction = 0) java.math.BigDecimal amount,
      @NotBlank @Size(max = 2000) String note) {}

  @GetMapping("/disputes/{id}")
  public ApiResponse<?> disputeDetail(@PathVariable UUID id) {
    return ApiResponse.success(sessions.adminGet(actors.getObject(), id));
  }

  @PostMapping("/disputes/{id}/resolve")
  public ApiResponse<?> resolve(@PathVariable UUID id, @Valid @RequestBody Resolution input) {
    return ApiResponse.success(sessions.resolve(actors.getObject(), id, input.amount().longValueExact(), input.note()));
  }
}
