package com.getlancer.payments;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/** Provider HTTP test fixture, never compiled into the production artifact. */
public final class RazorpayFixture implements AutoCloseable {
  public static final String KEY="rzp_test_fixture123456", SECRET="test-secret-not-for-production", WEBHOOK="test-webhook-not-for-production", ACCOUNT="acc_fixture123456";
  final HttpServer server; final ObjectMapper json; final JdbcTemplate db; final ExecutorService executor;
  public final AtomicInteger creates=new AtomicInteger();
  public volatile boolean uncertain, wrongAmount, wrongPayee, captured, reservationCommitted, blocked;
  public volatile long refunded;
  public volatile String disputeStatus="open";
  public volatile CountDownLatch createEntered=new CountDownLatch(1), releaseCreate=new CountDownLatch(1);
  public volatile JsonNode lastBody;
  final Map<String,ObjectNode> orders=new ConcurrentHashMap<>();

  public RazorpayFixture(JdbcTemplate db,ObjectMapper json) throws IOException {
    this.db=db; this.json=json; server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    executor=Executors.newCachedThreadPool();server.setExecutor(executor);server.createContext("/",this::handle);server.start();
  }
  public void reset() { creates.set(0);uncertain=false;wrongAmount=false;wrongPayee=false;captured=false;reservationCommitted=false;blocked=false;refunded=0;disputeStatus="open";orders.clear();lastBody=null;createEntered=new CountDownLatch(1);releaseCreate=new CountDownLatch(1); }
  public URI origin() { return URI.create("http://127.0.0.1:"+server.getAddress().getPort()); }
  public String orderId() { return orders.keySet().iterator().next(); }
  public String paymentId() { return "pay_fixture"+orderId().substring(6); }
  ObjectNode transfer(String order) {
    var node=json.createObjectNode();node.put("id","trf_fixture123456");node.put("recipient",wrongPayee?"acc_attacker123456":ACCOUNT);node.put("amount",10000);node.put("currency","INR");node.put("source",order);node.put("status",captured?"processed":"created");node.put("settlement_status",captured?"pending":"unknown");return node;
  }
  ObjectNode payment(String id) {
    String order="order_"+id.substring("pay_fixture".length());
    var node=json.createObjectNode();node.put("id",id);node.put("order_id",order);node.put("amount",wrongAmount?9999:10000);node.put("currency","INR");node.put("status",captured?(refunded==10000?"refunded":"captured"):"authorized");node.put("captured",captured);node.put("amount_refunded",refunded);return node;
  }
  void response(HttpExchange exchange,int status,Object body) throws IOException {byte[] bytes=json.writeValueAsBytes(body);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();}
  void handle(HttpExchange exchange) throws IOException {
    String path=exchange.getRequestURI().getPath();
    String expected="Basic "+Base64.getEncoder().encodeToString((KEY+":"+SECRET).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    if (!expected.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {response(exchange,401,Map.of("error","wrong test auth"));return;}
    if (path.equals("/v1/orders") && exchange.getRequestMethod().equals("POST")) {
      creates.incrementAndGet();lastBody=json.readTree(exchange.getRequestBody());
      reservationCommitted=db.queryForObject("SELECT count(*) FROM payment_attempts WHERE id=? AND status='CREATING'",Integer.class,UUID.fromString(lastBody.path("receipt").asText()))==1;
      String id="order_"+UUID.randomUUID().toString().replace("-","").substring(0,14);
      var order=json.createObjectNode();order.put("id",id);order.put("receipt",lastBody.path("receipt").asText());order.put("amount",lastBody.path("amount").longValue());order.put("currency","INR");order.set("transfers",json.createArrayNode().add(transfer(id)));orders.put(id,order);
      createEntered.countDown(); if (blocked) try {releaseCreate.await(2,TimeUnit.SECONDS);} catch (InterruptedException ex) {Thread.currentThread().interrupt();}
      if (uncertain) response(exchange,503,Map.of("error","uncertain fixture response"));else response(exchange,200,order);return;
    }
    if (path.startsWith("/v2/accounts/")) {response(exchange,200,Map.of("id",path.substring("/v2/accounts/".length()),"type","route","status","created"));return;}
    if (path.startsWith("/v1/disputes/")) {response(exchange,200,Map.of("id",path.substring("/v1/disputes/".length()),"payment_id",paymentId(),"amount",10000,"currency","INR","amount_deducted",disputeStatus.equals("lost")?10000:0,"status",disputeStatus));return;}
    if (path.startsWith("/v1/orders/")) {
      String[] parts=path.split("/");String order=parts[3];ObjectNode node=orders.get(order);
      if (node==null) {response(exchange,404,Map.of("error","not found"));return;}
      if (parts.length>4 && parts[4].equals("payments")) {response(exchange,200,Map.of("items",captured?List.of(payment("pay_fixture"+order.substring(6))):List.of()));return;}
      var copy=node.deepCopy();copy.set("transfers",json.createArrayNode().add(transfer(order)));response(exchange,200,copy);return;
    }
    if (path.startsWith("/v1/payments/")) {
      String[] parts=path.split("/");String id=parts[3];
      if (parts.length>4 && parts[4].equals("transfers")) {response(exchange,200,Map.of("items",List.of(transfer("order_"+id.substring("pay_fixture".length())))));return;}
      response(exchange,200,payment(id));return;
    }
    response(exchange,404,Map.of("error","unsupported test path"));
  }
  public void close() {releaseCreate.countDown();server.stop(0);executor.shutdownNow();}

  @TestConfiguration
  public static class Configuration {
    @Bean(destroyMethod="close") RazorpayFixture razorpayFixture(JdbcTemplate db,ObjectMapper json) throws IOException {return new RazorpayFixture(db,json);}
    @Bean @Primary RazorpayClient fixtureProvider(RazorpayFixture fixture,ObjectMapper json) {return new RazorpayClient(json,true,KEY,SECRET,WEBHOOK,"test",true,true,fixture.origin(),Duration.ofSeconds(3));}
  }
}
