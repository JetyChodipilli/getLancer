package com.getlancer.payments;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.getlancer.shared.ApiError;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Concrete provider HTTP fixture. No fake provider implementation is present in runtime source. */
public class CommerceProviderIntegrationTest {
  public static final String KEY="rzp_test_sourcefixture123",SECRET="source-fixture-secret-not-real",WEBHOOK="source-fixture-webhook-not-real",ACCOUNT="acc_sourcefixture123";
  public static class Fixture implements AutoCloseable {
    final HttpServer server;final ObjectMapper json;final JdbcTemplate db;final ExecutorService executor;
    public final AtomicInteger creates=new AtomicInteger();public volatile boolean uncertain,rejected,wrongAmount,wrongPayee,captured,reservationCommitted,blocked,unavailable;
    public volatile long refunded;public volatile String disputeStatus="open";public volatile JsonNode lastBody;
    public volatile CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
    final Map<String,ObjectNode> orders=new ConcurrentHashMap<>();
    public Fixture(JdbcTemplate db,ObjectMapper json) throws IOException {this.db=db;this.json=json;server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);executor=Executors.newCachedThreadPool();server.setExecutor(executor);server.createContext("/",this::handle);server.start();}
    public void reset() {creates.set(0);uncertain=false;rejected=false;wrongAmount=false;wrongPayee=false;captured=false;reservationCommitted=false;blocked=false;unavailable=false;refunded=0;disputeStatus="open";orders.clear();lastBody=null;entered=new CountDownLatch(1);release=new CountDownLatch(1);}
    public URI origin() {return URI.create("http://127.0.0.1:"+server.getAddress().getPort());}
    public String orderId() {return orders.keySet().iterator().next();}
    public String paymentId() {return "pay_source"+orderId().substring(6);}
    ObjectNode transfer(String order) {var n=json.createObjectNode();n.put("id","trf_sourcefixture123");n.put("recipient",wrongPayee?"acc_attacker123":ACCOUNT);n.put("amount",orders.containsKey(order)?orders.get(order).path("amount").longValue():10000);n.put("currency","INR");n.put("source",order);n.put("status",captured?"processed":"created");return n;}
    ObjectNode payment(String id) {String order="order_"+id.substring("pay_source".length());var n=json.createObjectNode();n.put("id",id);n.put("order_id",order);long amount=orders.containsKey(order)?orders.get(order).path("amount").longValue():10000;n.put("amount",wrongAmount?amount-1:amount);n.put("currency","INR");n.put("status",captured?(refunded==amount?"refunded":"captured"):"authorized");n.put("captured",captured);n.put("amount_refunded",refunded);return n;}
    void response(HttpExchange x,int status,Object body) throws IOException {byte[] bytes=json.writeValueAsBytes(body);x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(status,bytes.length);x.getResponseBody().write(bytes);x.close();}
    void handle(HttpExchange x) throws IOException {
      String auth="Basic "+Base64.getEncoder().encodeToString((KEY+":"+SECRET).getBytes(StandardCharsets.UTF_8));if(!auth.equals(x.getRequestHeaders().getFirst("Authorization"))) {response(x,401,Map.of("error","wrong fixture auth"));return;}
      String path=x.getRequestURI().getPath();if(unavailable) {response(x,503,Map.of("error","unavailable fixture"));return;}
      if(path.equals("/v1/orders") && x.getRequestMethod().equals("POST")) {creates.incrementAndGet();lastBody=json.readTree(x.getRequestBody());if(db!=null) reservationCommitted=db.queryForObject("SELECT count(*) FROM template_purchases WHERE id=? AND status='CREATING'",Integer.class,UUID.fromString(lastBody.path("receipt").asText()))==1;String id="order_"+UUID.randomUUID().toString().replace("-","").substring(0,14);var n=json.createObjectNode();n.put("id",id);n.put("receipt",lastBody.path("receipt").asText());n.put("amount",lastBody.path("amount").longValue());n.put("currency","INR");n.put("partial_payment",false);orders.put(id,n);n.set("transfers",json.createArrayNode().add(transfer(id)));entered.countDown();if(blocked) try {release.await(3,TimeUnit.SECONDS);}catch(InterruptedException e) {Thread.currentThread().interrupt();}response(x,rejected?422:uncertain?503:200,rejected||uncertain?Map.of("error","unconfirmed fixture create"):n);return;}
      if(path.startsWith("/v1/orders/")) {String[] parts=path.split("/");String order=parts[3];var n=orders.get(order);if(n==null) {response(x,404,Map.of("error","no fixture order"));return;}if(parts.length>4) {response(x,200,Map.of("items",captured?List.of(payment("pay_source"+order.substring(6))):List.of()));return;}var copy=n.deepCopy();copy.set("transfers",json.createArrayNode().add(transfer(order)));response(x,200,copy);return;}
      if(path.startsWith("/v1/payments/")) {String[] parts=path.split("/");String id=parts[3];if(parts.length>4) response(x,200,Map.of("items",List.of(transfer("order_"+id.substring("pay_source".length())))));else response(x,200,payment(id));return;}
      if(path.startsWith("/v1/disputes/")) {response(x,200,Map.of("id",path.substring("/v1/disputes/".length()),"payment_id",paymentId(),"amount",10000,"currency","INR","amount_deducted",disputeStatus.equals("lost")?10000:0,"status",disputeStatus));return;}
      response(x,404,Map.of("error","unsupported fixture path"));
    }
    public void close() {release.countDown();server.stop(0);executor.shutdownNow();}
  }
  @TestConfiguration public static class Configuration {
    @Bean(destroyMethod="close") Fixture sourceRazorpayFixture(JdbcTemplate db,ObjectMapper json) throws IOException {return new Fixture(db,json);}
    @Bean @Primary RazorpayClient sourceFixtureProvider(Fixture f,ObjectMapper json) {return new RazorpayClient(json,true,KEY,SECRET,WEBHOOK,"test",true,true,f.origin(),Duration.ofSeconds(4));}
  }
  @Test void concreteBoundaryUsesOneGatewayFrozenReceiptPayeeAndNoRedirectHost() throws Exception {var json=new ObjectMapper();try(var f=new Fixture(null,json)) {var client=new RazorpayClient(json,true,KEY,SECRET,WEBHOOK,"test",true,true,f.origin(),Duration.ofSeconds(3));UUID id=UUID.randomUUID();JsonNode order=client.createOrder(id,10000,ACCOUNT);assertEquals(id.toString(),order.path("receipt").asText());assertEquals(ACCOUNT,f.lastBody.path("transfers").get(0).path("account").asText());assertEquals("test",client.mode());assertEquals(KEY,client.keyId());assertThrows(IllegalArgumentException.class,()->new RazorpayClient(json,true,KEY,SECRET,WEBHOOK,"test",true,true,URI.create("https://attacker.invalid")));assertThrows(ApiError.class,()->client.order("order_../../escape"));}}
  @Test void rejectedAndUnknownProviderCreationAreDistinguished() throws Exception {var json=new ObjectMapper();try(var f=new Fixture(null,json)) {var client=new RazorpayClient(json,true,KEY,SECRET,WEBHOOK,"test",true,true,f.origin());f.rejected=true;assertTrue(assertThrows(RazorpayClient.ProviderFailure.class,()->client.createOrder(UUID.randomUUID(),10000,ACCOUNT)).rejected());f.rejected=false;f.uncertain=true;assertFalse(assertThrows(RazorpayClient.ProviderFailure.class,()->client.createOrder(UUID.randomUUID(),10000,ACCOUNT)).rejected());}}
}
