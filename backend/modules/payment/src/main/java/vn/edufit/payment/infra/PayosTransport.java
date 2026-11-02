package vn.edufit.payment.infra;

import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;
import vn.edufit.shared.exception.InvalidOperationException;

/** Wire signing follows payOSHQ/payos-lib-java CryptoProviderImpl (BODY vs HEADER). */
@Component
public class PayosTransport {
  private static final ObjectMapper JSON=new ObjectMapper();
  private final HttpClient client;
  public PayosTransport() { this.client=null; }
  PayosTransport(HttpClient client) { this.client=client; }
  private static class Holder {
    static final HttpClient CLIENT=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }
  public JsonNode call(String method,String path,Map<String,Object> body,String clientId,String apiKey,String checksum,boolean payout,String idempotency) {
    try {
      var request=HttpRequest.newBuilder(URI.create("https://api-merchant.payos.vn"+path))
          .timeout(Duration.ofSeconds(20)).header("Content-Type","application/json")
          .header("x-client-id",clientId).header("x-api-key",apiKey);
      if(payout && body!=null) request.header("x-signature",PayosGateway.sign(canonical(body,true),checksum));
      if(idempotency!=null)request.header("x-idempotency-key",idempotency);
      request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
      var response=(client==null?Holder.CLIENT:client).send(request.build(),HttpResponse.BodyHandlers.ofString());
      if(response.statusCode()!=200 || response.body().length()>1_000_000)throw unavailable();
      var envelope=JSON.readTree(response.body());
      if(!"00".equals(envelope.path("code").asText()) || !envelope.path("data").isObject())throw unavailable();
      var data=envelope.path("data");
      String signature=payout?response.headers().firstValue("x-signature").orElse(""):envelope.path("signature").asText();
      String expected=PayosGateway.sign(canonical(JSON.convertValue(data,Map.class),payout),checksum);
      if(!signature.matches("[0-9a-fA-F]{64}") || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),signature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)))
        throw new InvalidOperationException("Chữ ký phản hồi payOS không hợp lệ; chưa thay đổi số dư");
      return data;
    } catch(InterruptedException e) { Thread.currentThread().interrupt();throw unavailable(); }
      catch(java.io.IOException e) { throw unavailable(); }
  }
  private InvalidOperationException unavailable() { return new InvalidOperationException("payOS chưa xác nhận kết quả. Tiền được giữ để đối soát; không tạo giao dịch khác."); }
  public static String canonical(Map<String,?> input,boolean encode) {
    var pairs=new ArrayList<String>();
    new TreeMap<>(input).forEach((key,value)->{
      String text;
      try { text=value==null?"":value instanceof Map || value instanceof List?JSON.writeValueAsString(sorted(value)):value.toString(); }
      catch(Exception e) {throw new IllegalArgumentException("Invalid signing data",e);}
      pairs.add((encode?uri(key):key)+"="+(encode?uri(text):text));
    });
    return String.join("&",pairs);
  }
  private static String uri(String value) {return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20");}
  private static Object sorted(Object value) {
    if(value instanceof Map<?,?> map) {var result=new TreeMap<String,Object>();map.forEach((k,v)->result.put(k.toString(),sorted(v)));return result;}
    if(value instanceof List<?> list)return list.stream().map(PayosTransport::sorted).toList();
    return value;
  }
}
