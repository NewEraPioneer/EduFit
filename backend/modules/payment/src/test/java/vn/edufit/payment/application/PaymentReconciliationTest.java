package vn.edufit.payment.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import vn.edufit.payment.infra.*;
import vn.edufit.shared.auth.CurrentUser;

class PaymentReconciliationTest {
  final UUID id = UUID.randomUUID(), owner = UUID.randomUUID();
  final PaymentStore store = mock(PaymentStore.class);
  final PayosGateway gateway = mock(PayosGateway.class);
  final PaymentSettings settings =
      vn.edufit.payment.TestPaymentSettings.builder().payos("client", "api", "key").bankAccount("12345678").build();

  PaymentReconciliationService service() {
    when(store.locked(any())).thenAnswer(i -> ((Supplier<?>) i.getArgument(0)).get());
    when(store.topup(id))
        .thenReturn(
            Map.of(
                "id",
                id,
                "user_id",
                owner,
                "order_code",
                42L,
                "amount",
                100000L,
                "provider_id",
                "link42"));
    when(store.claimTopup(eq(id), any())).thenReturn(true);
    return new PaymentReconciliationService(
        store, mock(PaymentAccess.class), settings, gateway, new PaymentTime(Clock.systemUTC()));
  }

  Map<String, Object> status(long paid, String account) {
    return Map.of(
        "id",
        "link42",
        "orderCode",
        42,
        "amount",
        100000,
        "amountPaid",
        paid,
        "status",
        "PAID",
        "transactions",
        List.of(Map.of("amount", 100000, "accountNumber", account, "reference", "BANKREF1")));
  }

  CurrentUser actor(UUID user) {
    var actor = mock(CurrentUser.class);
    when(actor.getUserId()).thenReturn(user);
    return actor;
  }

  @Test
  void ownerCanReconcileButAnotherUserCannotCallProvider() {
    var service = service();
    assertThrows(RuntimeException.class, () -> service.refresh(actor(UUID.randomUUID()), id));
    verifyNoInteractions(gateway);
    when(gateway.lookup(42)).thenReturn(new ObjectMapper().valueToTree(status(100000, "12345678")));
    service.refresh(actor(owner), id);
    verify(store).creditTopup(id, "BANKREF1", null);
  }

  @Test
  void wrongAmountAccountOrProviderTimeoutNeverCredits() {
    var service = service();
    for (var data : List.of(status(99999, "12345678"), status(100000, "87654321"))) {
      when(gateway.lookup(42)).thenReturn(new ObjectMapper().valueToTree(data));
      assertThrows(RuntimeException.class, () -> service.refresh(actor(owner), id));
    }
    when(gateway.lookup(42)).thenThrow(new IllegalStateException("timeout"));
    assertThrows(RuntimeException.class, () -> service.refresh(actor(owner), id));
    verify(store, never()).creditTopup(any(), any(), any());
  }

  @Test
  void leasePreventsRefreshStormAndPendingNeverCredits() {
    var service = service();
    when(store.claimTopup(eq(id), any())).thenReturn(false);
    service.refresh(actor(owner), id);
    verifyNoInteractions(gateway);
  }

  @Test
  void reconciliationUsesTheStoredVirtualAccountAndRejectsAnotherAccount() {
    var service = service();
    when(store.topup(id)).thenReturn(Map.of(
        "id", id, "user_id", owner, "order_code", 42L, "amount", 100000L,
        "provider_id", "link42", "provider_account_number", "VQR123456789"));
    when(gateway.lookup(42)).thenReturn(new ObjectMapper().valueToTree(status(100000, "12345678")));
    assertThrows(RuntimeException.class, () -> service.refresh(actor(owner), id));
    verify(store, never()).creditTopup(any(), any(), any());
    when(gateway.lookup(42)).thenReturn(new ObjectMapper().valueToTree(status(100000, "VQR123456789")));
    service.refresh(actor(owner), id);
    verify(store).creditTopup(id, "BANKREF1", null);
  }
}
