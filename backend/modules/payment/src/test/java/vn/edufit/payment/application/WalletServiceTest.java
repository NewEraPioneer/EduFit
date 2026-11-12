package vn.edufit.payment.application;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import vn.edufit.payment.infra.*;
class WalletServiceTest {
 final PaymentStore store=mock(PaymentStore.class);
 final PayosGateway gateway=mock(PayosGateway.class);
 final PaymentSettings settings=vn.edufit.payment.TestPaymentSettings.builder().bankAccount("12345678").build();
 WalletService service() {
  when(store.locked(any())).thenAnswer(i->((Supplier<?>)i.getArgument(0)).get());
  return new WalletService(store,mock(PaymentAccess.class),settings,gateway,new PaymentTime(Clock.systemUTC()));
 }
 Map<String,Object> topup() {return Map.of("id",UUID.randomUUID(),"provider","PAYOS","amount",100000L,"provider_id","link42");}
 @Test void signedRegistrationProbeNeverCreditsAnyWallet() {
  var service=service();when(gateway.verify("probe")).thenReturn(new PayosGateway.Receipt(123,3000,"PROBE123","12345678","probe","VND"));when(store.findTopupByOrder(123)).thenReturn(Optional.empty());
  service.webhook("probe");verify(store,never()).creditTopup(any(),anyString(),any());
 }
 @Test void wrongAccountOrAmountCannotCreditAWallet() {
  var service=service();when(store.findTopupByOrder(42)).thenReturn(Optional.of(topup()));
  when(gateway.verify("wrong-account")).thenReturn(new PayosGateway.Receipt(42,100000,"BANK123","87654321","link42","VND"));
  when(gateway.verify("wrong-amount")).thenReturn(new PayosGateway.Receipt(42,200000,"BANK123","12345678","link42","VND"));
  assertThrows(RuntimeException.class,()->service.webhook("wrong-account"));assertThrows(RuntimeException.class,()->service.webhook("wrong-amount"));verify(store,never()).creditTopup(any(),anyString(),any());
 }
 @Test void payosCannotBeEnabledWithoutExpectedReceivingAccount() {
  var keysOnly=vn.edufit.payment.TestPaymentSettings.builder().payos("client","api","key");
  assertFalse(keysOnly.build().payos());assertTrue(keysOnly.bankAccount("12345678").build().payos());
 }
 @Test void webhookMatchesTheStoredVirtualAccountRatherThanOnlyTheConfiguredBankAccount() {
  var service=service();
  var topup=new HashMap<>(topup());topup.put("provider_account_number","VQR123456789");
  when(store.findTopupByOrder(42)).thenReturn(Optional.of(topup));
  when(gateway.verify("virtual")).thenReturn(new PayosGateway.Receipt(42,100000,"BANK123","12345678","link42","VND","VQR123456789"));
  service.webhook("virtual");verify(store).creditTopup((UUID)topup.get("id"),"BANK123",null);
 }
 WalletService payosService() {
  when(store.locked(any())).thenAnswer(i->((Supplier<?>)i.getArgument(0)).get());
  var payos=vn.edufit.payment.TestPaymentSettings.builder().payos("client","api","key").bankAccount("12345678").build();
  return new WalletService(store,mock(PaymentAccess.class),payos,gateway,new PaymentTime(Clock.systemUTC()));
 }
 vn.edufit.shared.auth.CurrentUser learner() {
  var actor=mock(vn.edufit.shared.auth.CurrentUser.class);when(actor.getUserId()).thenReturn(UUID.randomUUID());return actor;
 }
 @Test void duplicateOrderCodeAtPayosGetsANewOrderCodeOnTheSameTopup() {
  var service=payosService();UUID id=UUID.randomUUID();
  when(store.createTopup(any(),eq(100000L),eq("PAYOS"),any())).thenReturn(Map.of("id",id,"order_code",2L));
  when(gateway.create(eq(2L),eq(100000L),anyString(),any())).thenThrow(new PayosRejectedException("231","Đơn thanh toán đã tồn tại"));
  when(store.renewOrderCode(id)).thenReturn(1794470279123456L);
  when(gateway.create(eq(1794470279123456L),eq(100000L),anyString(),any()))
      .thenReturn(new PayosGateway.Checkout("https://pay.payos.vn/web/link-new","link-new","VA1"));
  var result=service.topup(learner(),100000);
  assertEquals(id,result.get("id"));assertEquals(1794470279123456L,result.get("orderCode"));
  verify(store).checkout(id,"https://pay.payos.vn/web/link-new","link-new","VA1");
 }
 @Test void otherPayosRejectionsAreReportedWithoutRetryingOrRenewingTheOrderCode() {
  var service=payosService();UUID id=UUID.randomUUID();
  when(store.createTopup(any(),anyLong(),anyString(),any())).thenReturn(Map.of("id",id,"order_code",7L));
  when(gateway.create(anyLong(),anyLong(),anyString(),any())).thenThrow(new PayosRejectedException("20","Thông tin truyền lên không đúng"));
  var error=assertThrows(vn.edufit.shared.exception.InvalidOperationException.class,()->service.topup(learner(),100000));
  assertTrue(error.getMessage().contains("payOS từ chối"));
  verify(gateway,times(1)).create(anyLong(),anyLong(),anyString(),any());verify(store,never()).renewOrderCode(any());
 }
 @Test void repeatedDuplicateOrderCodesStopAfterABoundedNumberOfAttempts() {
  var service=payosService();UUID id=UUID.randomUUID();
  when(store.createTopup(any(),anyLong(),anyString(),any())).thenReturn(Map.of("id",id,"order_code",7L));
  when(store.renewOrderCode(id)).thenReturn(8L,9L,10L);
  when(gateway.create(anyLong(),anyLong(),anyString(),any())).thenThrow(new PayosRejectedException("231","Đơn thanh toán đã tồn tại"));
  assertThrows(vn.edufit.shared.exception.InvalidOperationException.class,()->service.topup(learner(),100000));
  verify(gateway,times(3)).create(anyLong(),anyLong(),anyString(),any());verify(store,never()).checkout(any(),any(),any(),any());
 }
 @Test void matchingMainAccountDoesNotBypassAMismatchedStoredVirtualAccount() {
  var service=service();var topup=new HashMap<>(topup());topup.put("provider_account_number","VQR123456789");
  when(store.findTopupByOrder(42)).thenReturn(Optional.of(topup));
  when(gateway.verify("wrong-virtual")).thenReturn(new PayosGateway.Receipt(42,100000,"BANK123","12345678","link42","VND","VQR999999999"));
  assertThrows(RuntimeException.class,()->service.webhook("wrong-virtual"));verify(store,never()).creditTopup(any(),anyString(),any());
 }
}
