package com.getlancer.payments;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.shared.ApiError;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RazorpayClientTest {
  static final String KEY="rzp_test_fixture123456", SECRET="test-secret-not-for-production", WEBHOOK="test-webhook-not-for-production";
  RazorpayClient client(URI uri,Duration timeout) { return new RazorpayClient(new ObjectMapper(),true,KEY,SECRET,WEBHOOK,"test",true,true,uri,timeout); }

  @Test void hmacMatchesIndependentRfc4231GoldenVector() {
    // RFC 4231 case 1: key is twenty 0x0b bytes, message is ASCII Hi There.
    assertEquals("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",HexFormat.of().formatHex(RazorpayClient.mac("\u000b".repeat(20),"Hi There".getBytes(StandardCharsets.US_ASCII))));
  }

  @Test void hmacUsesStoredOrderAndExactRawBytes() {
    var client=client(URI.create("http://127.0.0.1:1"),Duration.ofMillis(200));
    byte[] exact="{\"event\": \"payment.captured\"}".getBytes(StandardCharsets.UTF_8);
    String signature=HexFormat.of().formatHex(RazorpayClient.mac(WEBHOOK,exact));
    assertTrue(client.webhookSignature(exact,signature));
    assertFalse(client.webhookSignature("{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8),signature));
    assertFalse(client.webhookSignature(exact,"g".repeat(64)));
    assertFalse(client.webhookSignature(exact,null));
    String checkout=HexFormat.of().formatHex(RazorpayClient.mac(SECRET,"order_fixture123|pay_fixture123".getBytes(StandardCharsets.UTF_8)));
    assertTrue(client.checkoutSignature("order_fixture123","pay_fixture123",checkout));
    assertFalse(client.checkoutSignature("order_attacker123","pay_fixture123",checkout));
  }

  @Test void disabledAndMismatchedCredentialsNeverCallProvider() {
    var disabled=new RazorpayClient(new ObjectMapper(),false,KEY,SECRET,WEBHOOK,"test",true,true,URI.create("http://127.0.0.1:1"));
    assertEquals("disabled",disabled.configuration().get("mode"));
    assertEquals("",disabled.configuration().get("keyId"));
    assertEquals(503,assertThrows(ApiError.class,()->disabled.createOrder(UUID.randomUUID(),100,"acc_fixture123")).status);
    var mismatch=new RazorpayClient(new ObjectMapper(),true,KEY,SECRET,WEBHOOK,"live",true,true,URI.create("http://127.0.0.1:1"));
    assertFalse((Boolean)mismatch.configuration().get("enabled"));
    assertThrows(IllegalArgumentException.class,()->client(URI.create("https://attacker.example"),Duration.ofSeconds(1)));
    assertThrows(ApiError.class,()->disabled.payment("pay_../../secret"));
  }

  @Test void stalledBodyTimesOutAndReleasesCapacity() throws Exception {
    HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    ExecutorService executor=Executors.newCachedThreadPool(); server.setExecutor(executor);
    CountDownLatch finish=new CountDownLatch(1); AtomicInteger hits=new AtomicInteger();
    server.createContext("/",exchange->{
      hits.incrementAndGet();
      exchange.sendResponseHeaders(200,0); exchange.getResponseBody().write('{'); exchange.getResponseBody().flush();
      try { finish.await(3,TimeUnit.SECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
      exchange.close();
    }); server.start();
    var client=client(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),Duration.ofMillis(250));
    try {
      for (int n=0;n<5;n++) assertTimeoutPreemptively(Duration.ofSeconds(1),()->assertThrows(RazorpayClient.ProviderFailure.class,()->client.payment("pay_fixture123")));
      assertEquals(5,hits.get());
    } finally { finish.countDown(); server.stop(0); executor.shutdownNow(); }
  }

  @Test void oversizedBodyAndRedirectsAreRejected() throws Exception {
    HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); AtomicInteger forbidden=new AtomicInteger();
    server.createContext("/v1/payments/pay_fixture123",exchange->{exchange.getResponseHeaders().add("Location","/leak");exchange.sendResponseHeaders(302,-1);exchange.close();});
    server.createContext("/v1/payments/pay_hugefixture",exchange->{byte[] bytes=new byte[300000];exchange.sendResponseHeaders(200,bytes.length);try {exchange.getResponseBody().write(bytes);} finally {exchange.close();}});
    server.createContext("/leak",exchange->{forbidden.incrementAndGet();exchange.sendResponseHeaders(200,-1);exchange.close();});server.start();
    try {
      var client=client(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),Duration.ofSeconds(2));
      assertThrows(RazorpayClient.ProviderFailure.class,()->client.payment("pay_fixture123"));
      assertThrows(RazorpayClient.ProviderFailure.class,()->client.payment("pay_hugefixture"));
      assertEquals(0,forbidden.get());
    } finally {server.stop(0);}
  }

  @Test void refundLookupUsesValidatedExactIdentifierAndCanonicalEndpoint()throws Exception{
    HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicInteger hits=new AtomicInteger();server.createContext("/v1/refunds/rfnd_fixture123",exchange->{hits.incrementAndGet();assertEquals("GET",exchange.getRequestMethod());byte[] body="{\"id\":\"rfnd_fixture123\",\"entity\":\"refund\"}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();try{var client=client(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),Duration.ofSeconds(2));assertEquals("rfnd_fixture123",client.refund("rfnd_fixture123").path("id").asText());assertThrows(ApiError.class,()->client.refund("rfnd_../../payment"));assertEquals(1,hits.get());}finally{server.stop(0);}
  }
  @Test void publishingSlotOrderUsesPlatformReceiptWithoutSellerTransfers() throws Exception {
    HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    var observed=new java.util.concurrent.atomic.AtomicReference<com.fasterxml.jackson.databind.JsonNode>();
    var method=new java.util.concurrent.atomic.AtomicReference<String>();
    server.createContext("/v1/orders",exchange->{
      method.set(exchange.getRequestMethod());
      observed.set(new ObjectMapper().readTree(exchange.getRequestBody().readNBytes(65536)));
      byte[] body="{\"id\":\"order_fixture123\"}".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type","application/json");
      exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
    });
    server.start();
    try {
      UUID receipt=UUID.randomUUID();
      var result=client(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),Duration.ofSeconds(2)).createPlatformOrder(receipt,10000);
      assertEquals("order_fixture123",result.path("id").asText());assertEquals("POST",method.get());
      assertEquals(receipt.toString(),observed.get().path("receipt").asText());assertEquals(10000,observed.get().path("amount").asInt());
      assertEquals("INR",observed.get().path("currency").asText());assertFalse(observed.get().path("partial_payment").asBoolean());assertFalse(observed.get().has("transfers"));
    } finally {server.stop(0);}
  }

}
