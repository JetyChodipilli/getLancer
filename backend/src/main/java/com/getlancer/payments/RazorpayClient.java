package com.getlancer.payments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.shared.ApiError;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Concrete provider boundary. Redirects and caller-selected upstream hosts are never allowed. */
@Component
public class RazorpayClient {
  private final ObjectMapper json;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
  private final Semaphore requests = new Semaphore(4);
  private final URI origin;
  private final Duration deadline;
  final String keyId, keySecret, webhookSecret, mode;
  final boolean enabled, policiesApproved, routeApproved;

  @Autowired
  public RazorpayClient(ObjectMapper json, @Value("${app.payments.enabled:false}") boolean enabled,
      @Value("${app.payments.key-id:}") String keyId, @Value("${app.payments.key-secret:}") String keySecret,
      @Value("${app.payments.webhook-secret:}") String webhookSecret, @Value("${app.payments.mode:test}") String mode,
      @Value("${app.payments.route-approved:false}") boolean routeApproved,
      @Value("${app.policies-approved:false}") boolean policiesApproved) {
    this(json, enabled, keyId, keySecret, webhookSecret, mode, routeApproved, policiesApproved, URI.create("https://api.razorpay.com"));
  }

  // Test-source HTTP fixtures can use this package-private constructor. No runtime property changes the provider host.
  RazorpayClient(ObjectMapper json, boolean enabled, String keyId, String keySecret, String webhookSecret,
      String mode, boolean routeApproved, boolean policiesApproved, URI origin) {
    this(json,enabled,keyId,keySecret,webhookSecret,mode,routeApproved,policiesApproved,origin,Duration.ofSeconds(8));
  }

  RazorpayClient(ObjectMapper json, boolean enabled, String keyId, String keySecret, String webhookSecret,
      String mode, boolean routeApproved, boolean policiesApproved, URI origin, Duration deadline) {
    if (!origin.equals(URI.create("https://api.razorpay.com"))
        && !("http".equals(origin.getScheme()) && Set.of("localhost", "127.0.0.1", "[::1]").contains(origin.getHost())))
      throw new IllegalArgumentException("Untrusted provider origin");
    this.json=json; this.enabled=enabled; this.keyId=keyId; this.keySecret=keySecret;
    this.webhookSecret=webhookSecret; this.mode=mode; this.routeApproved=routeApproved;
    this.policiesApproved=policiesApproved; this.origin=origin; this.deadline=deadline;
  }

  boolean credentialsReady() {
    return Set.of("test", "live").contains(mode) && keyId.matches("rzp_" + mode + "_[A-Za-z0-9]{6,40}")
        && keySecret.length() >= 16 && webhookSecret.length() >= 16;
  }

  public Map<String,Object> configuration() {
    boolean ready=enabled && policiesApproved && routeApproved && credentialsReady();
    String reason=ready ? "" : !policiesApproved ? "Commercial policies require operator approval."
        : !routeApproved ? "Razorpay Route requires operator approval."
        : "Payment collection is not configured. Contact the workspace operator.";
    return Map.of("enabled",ready,"keyId",ready?keyId:"","mode",ready?mode:"disabled","reason",reason);
  }

  public String mode() { return mode; }
  public String keyId() { return keyId; }

  public void requireCollection() {
    if (!Boolean.TRUE.equals(configuration().get("enabled")))
      throw new ApiError(503,"PAYMENTS_UNAVAILABLE", Objects.toString(configuration().get("reason")));
  }

  public void requireCredentials() {
    if (!credentialsReady()) throw new ApiError(503,"PAYMENTS_UNAVAILABLE","Provider reconciliation is not configured.");
  }

  public JsonNode createOrder(UUID attempt, long amount, String account) {
    requireCollection();
    return request("POST", "/v1/orders", Map.of("amount",amount,"currency","INR","receipt",attempt.toString(),
        "partial_payment",false,"transfers",List.of(Map.of("account",account,"amount",amount,"currency","INR","on_hold",false))));
  }

  public JsonNode order(String id) { return request("GET","/v1/orders/"+providerId(id,"order_")+"?expand%5B%5D=transfers",null); }
  public JsonNode orderPayments(String id) { return request("GET","/v1/orders/"+providerId(id,"order_")+"/payments",null); }
  public JsonNode payment(String id) { return request("GET","/v1/payments/"+providerId(id,"pay_"),null); }
  public JsonNode transfers(String id) { return request("GET","/v1/payments/"+providerId(id,"pay_")+"/transfers",null); }
  public JsonNode dispute(String id) { return request("GET","/v1/disputes/"+providerId(id,"disp_"),null); }
  JsonNode account(String id) { return request("GET","/v2/accounts/"+providerId(id,"acc_"),null); }

  public static String providerId(String value, String prefix) {
    if (value==null || !value.matches(prefix+"[A-Za-z0-9]{6,32}"))
      throw new ApiError(400,"VALIDATION_ERROR","Invalid provider identifier.");
    return value;
  }

  static byte[] mac(String secret, byte[] body) {
    try { var hmac=Mac.getInstance("HmacSHA256"); hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256")); return hmac.doFinal(body); }
    catch (java.security.GeneralSecurityException ex) { throw new IllegalStateException(ex); }
  }

  public static boolean signed(String secret, byte[] body, String signature) {
    if (secret.isBlank() || signature==null || !signature.matches("[a-fA-F0-9]{64}")) return false;
    return MessageDigest.isEqual(mac(secret,body),HexFormat.of().parseHex(signature));
  }

  public boolean checkoutSignature(String order, String payment, String signature) {
    return signed(keySecret,(order+"|"+payment).getBytes(StandardCharsets.UTF_8),signature);
  }

  boolean webhookSignature(byte[] body, String signature) { return signed(webhookSecret,body,signature); }

  private JsonNode request(String method,String path,Object body) {
    requireCredentials();
    if (!requests.tryAcquire()) throw new ProviderFailure(false);
    try {
      byte[] encoded=body==null ? new byte[0] : json.writeValueAsBytes(body);
      var req=HttpRequest.newBuilder(origin.resolve(path)).timeout(Duration.ofSeconds(8))
          .header("Authorization","Basic "+Base64.getEncoder().encodeToString((keyId+":"+keySecret).getBytes(StandardCharsets.UTF_8)))
          .header("Accept","application/json").header("Content-Type","application/json")
          .method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(encoded)).build();
      CompletableFuture<HttpResponse<byte[]>> pending=http.sendAsync(req,ignored->new BoundedBody());
      HttpResponse<byte[]> response;
      try { response=pending.get(deadline.toMillis(),TimeUnit.MILLISECONDS); }
      catch (InterruptedException ex) { pending.cancel(true); throw ex; }
      catch (TimeoutException | ExecutionException ex) { pending.cancel(true); throw new ProviderFailure(false); }
      byte[] bytes=response.body();
      if (response.statusCode()<200 || response.statusCode()>=300)
        throw new ProviderFailure(response.statusCode()>=400 && response.statusCode()<500 && response.statusCode()!=408 && response.statusCode()!=429);
      JsonNode result=json.readTree(bytes);
      if (result==null || !result.isObject()) throw new ProviderFailure(false);
      return result;
    } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new ProviderFailure(false); }
    catch (java.io.IOException ex) { throw new ProviderFailure(false); }
    finally { requests.release(); }
  }

  private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
    final CompletableFuture<byte[]> body=new CompletableFuture<>();
    final ByteArrayOutputStream buffer=new ByteArrayOutputStream();
    Flow.Subscription subscription;
    public CompletionStage<byte[]> getBody() { return body; }
    public void onSubscribe(Flow.Subscription subscription) { this.subscription=subscription; subscription.request(1); }
    public void onNext(List<ByteBuffer> chunks) {
      for (ByteBuffer chunk:chunks) {
        if (chunk.remaining()>262144-buffer.size()) { subscription.cancel(); body.completeExceptionally(new ProviderFailure(false)); return; }
        byte[] bytes=new byte[chunk.remaining()]; chunk.get(bytes); buffer.writeBytes(bytes);
      }
      subscription.request(1);
    }
    public void onError(Throwable error) { body.completeExceptionally(error); }
    public void onComplete() { body.complete(buffer.toByteArray()); }
  }

  public static final class ProviderFailure extends RuntimeException {
    final boolean rejected;
    ProviderFailure(boolean rejected) { super("Provider request could not be confirmed."); this.rejected=rejected; }
    public boolean rejected() { return rejected; }
  }
}
