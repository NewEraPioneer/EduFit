package vn.edufit.payment.infra;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PayosTransportTest {
  @SuppressWarnings("unchecked")
  @Test void verifiesProviderSignatureAndNeverAcceptsUnsignedMoneyStatus() throws Exception {
    var http=mock(HttpClient.class);
    HttpResponse<String> response=mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenReturn("{\"code\":\"00\",\"data\":{\"amount\":10000,\"status\":\"PAID\"}}");
    when(response.headers()).thenReturn(HttpHeaders.of(Map.of(),(a,b)->true));
    when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);
    var transport=new PayosTransport(http);
    assertThrows(RuntimeException.class,()->transport.call("GET","/v2/payment-requests/42",null,"client","api","secret",false,null));
    when(response.body()).thenReturn("{\"code\":\"00\",\"data\":{\"amount\":10000,\"status\":\"PAID\"},\"signature\":\""+PayosGateway.sign("amount=10000&status=PAID","secret")+"\"}");
    assertEquals("PAID",transport.call("GET","/v2/payment-requests/42",null,"client","api","secret",false,null).path("status").asText());
  }
  @Test void payoutCanonicalMatchesOfficialSdkEncodingAndNestedSorting() {
    assertEquals("amount=10000&description=Hoc%20phi%20%26%20rut&transactions=%5B%7B%22amount%22%3A1%2C%22state%22%3A%22SUCCEEDED%22%7D%5D",
      PayosTransport.canonical(Map.of("description","Hoc phi & rut","amount",10000,"transactions",List.of(Map.of("state","SUCCEEDED","amount",1))),true));
  }

  @SuppressWarnings("unchecked")
  @Test void payoutRequiresSignedHeaderAndKeepsStableIdempotencyKey() throws Exception {
    var http=mock(HttpClient.class);
    HttpResponse<String> response=mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenReturn("{\"code\":\"00\",\"data\":{\"id\":\"payout_1\",\"referenceId\":\"ref1\",\"transactions\":[{\"state\":\"PROCESSING\",\"amount\":10000}]}}");
    String signature=PayosGateway.sign("id=payout_1&referenceId=ref1&transactions=%5B%7B%22amount%22%3A10000%2C%22state%22%3A%22PROCESSING%22%7D%5D","secret");
    when(response.headers()).thenReturn(HttpHeaders.of(Map.of("x-signature",List.of(signature)),(a,b)->true));
    when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);
    var body=Map.<String,Object>of("amount",10000,"referenceId","ref1","toBin","970422","toAccountNumber","12345678","description","Hoc phi");
    var transport=new PayosTransport(http);
    assertEquals("payout_1",transport.call("POST","/v1/payouts",body,"client","api","secret",true,"stable-uuid").path("id").asText());
    var capture=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);verify(http).send(capture.capture(),any(HttpResponse.BodyHandler.class));
    assertEquals("stable-uuid",capture.getValue().headers().firstValue("x-idempotency-key").orElseThrow());
    assertEquals(PayosGateway.sign("amount=10000&description=Hoc%20phi&referenceId=ref1&toAccountNumber=12345678&toBin=970422","secret"),capture.getValue().headers().firstValue("x-signature").orElseThrow());
    when(response.headers()).thenReturn(HttpHeaders.of(Map.of("x-signature",List.of("0".repeat(64))),(a,b)->true));
    assertThrows(RuntimeException.class,()->transport.call("POST","/v1/payouts",body,"client","api","secret",true,"stable-uuid"));
  }
}
