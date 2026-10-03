package com.getlancer.payments;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
/** HTTP-only test source. No runtime simulation branch or real merchant credentials. */
public final class MaintenanceFixture implements AutoCloseable {
 public static final String KEY="rzp_live_fixture123456",SECRET="fixture-secret-not-for-production",WEBHOOK="fixture-webhook-not-for-production",ACCOUNT="acc_fixture123456",PLAN="plan_fixture123456",SUB="sub_fixture123456",PAYMENT="pay_fixture123456",INVOICE="inv_fixture123456",TRANSFER="trf_fixture123456",DISPUTE="disp_fixture123456";
 final HttpServer server;final ObjectMapper json;final JdbcTemplate db;final ExecutorService executor;
 public final AtomicInteger plans=new AtomicInteger(),subscriptions=new AtomicInteger(),transfers=new AtomicInteger(),cancels=new AtomicInteger();
 public volatile boolean paid,unknownPlan,rejectPlan,unknownTransfer,cancelUncertain,raiseDispute,listDispute,pendingRefund,invalidRefundStatus,wrongInvoice,wrongPayee,expired,blockSubscription;
 public volatile long refunded;public volatile String transferStatus="created",disputeStatus="open";public volatile boolean committedPlan,committedSubscription,committedTransfer;
 public volatile CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);public volatile String providerStatus="created";
 ObjectNode plan,subscription,transfer;
 public MaintenanceFixture(JdbcTemplate db,ObjectMapper json)throws IOException{this.db=db;this.json=json;server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);executor=Executors.newCachedThreadPool();server.setExecutor(executor);server.createContext("/",this::handle);server.start();}
 public URI origin(){return URI.create("http://127.0.0.1:"+server.getAddress().getPort());}
 public void reset(){plans.set(0);subscriptions.set(0);transfers.set(0);cancels.set(0);paid=unknownPlan=rejectPlan=unknownTransfer=cancelUncertain=raiseDispute=listDispute=pendingRefund=invalidRefundStatus=wrongInvoice=wrongPayee=expired=blockSubscription=false;refunded=0;plan=subscription=transfer=null;providerStatus="created";transferStatus="created";disputeStatus="open";entered=new CountDownLatch(1);release=new CountDownLatch(1);}
 void send(HttpExchange x,int code,Object value)throws IOException{byte[] b=json.writeValueAsBytes(value);x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(code,b.length);x.getResponseBody().write(b);x.close();}
 Map<String,Object> collection(List<?> items){return Map.of("entity","collection","count",items.size(),"items",items);}
 boolean first(HttpExchange x){return !Objects.toString(x.getRequestURI().getQuery(),"").matches(".*(?:^|&)skip=[1-9][0-9]*.*");}
 ObjectNode invoice(){long now=Instant.now().getEpochSecond();var n=json.createObjectNode();n.put("id",INVOICE);n.put("entity","invoice");n.put("subscription_id",SUB);n.put("payment_id",PAYMENT);n.put("order_id","order_fixture123456");n.put("status","paid");n.put("currency","INR");n.put("amount",wrongInvoice?9999:10000);n.put("amount_paid",10000);n.put("amount_due",0);n.put("partial_payment",false);n.put("billing_start",periodStart);n.put("billing_end",periodEnd);n.set("line_items",json.valueToTree(List.of(Map.of("type","plan","quantity",1,"amount",10000,"currency","INR"))));return n;}
 long periodStart,periodEnd;
 public void period(boolean past){long now=Instant.now().getEpochSecond();periodStart=now-(past?31*86400L:300);periodEnd=now+(past?-86400:29*86400L);}
 ObjectNode payment(){var n=json.createObjectNode();n.put("id",PAYMENT);n.put("entity","payment");n.put("order_id","order_fixture123456");n.put("invoice_id",INVOICE);n.put("amount",10000);n.put("currency","INR");n.put("status",refunded==10000?"refunded":"captured");n.put("captured",true);n.put("amount_refunded",refunded);if(invalidRefundStatus)n.put("refund_status","partial");else if(refunded>0)n.put("refund_status",refunded==10000?"full":"partial");else n.putNull("refund_status");return n;}
 Map<String,Object> dispute(){return Map.of("id",DISPUTE,"payment_id",PAYMENT,"amount",10000,"currency","INR","amount_deducted",disputeStatus.equals("won")?1000:0,"status",disputeStatus);}
 void handle(HttpExchange x)throws IOException{
  String p=x.getRequestURI().getPath(),method=x.getRequestMethod();String auth="Basic "+Base64.getEncoder().encodeToString((KEY+":"+SECRET).getBytes(StandardCharsets.UTF_8));if(!auth.equals(x.getRequestHeaders().getFirst("Authorization"))){send(x,401,Map.of("error","fixture auth"));return;}
  if(p.equals("/v1/plans")&&method.equals("POST")){plans.incrementAndGet();var b=json.readTree(x.getRequestBody());UUID id=UUID.fromString(b.path("notes").path("maintenance_attempt").asText());committedPlan=db.queryForObject("SELECT count(*) FROM maintenance_subscriptions WHERE id=? AND plan_state='CREATING'",Integer.class,id)==1;plan=b.deepCopy();plan.put("id",PLAN);plan.put("entity","plan");send(x,rejectPlan?400:unknownPlan?503:200,rejectPlan?Map.of("error","definitive rejection"):plan);return;}
  if(p.equals("/v1/plans/"+PLAN)){send(x,200,plan);return;}
  if(p.equals("/v1/subscriptions")&&method.equals("POST")){subscriptions.incrementAndGet();var b=json.readTree(x.getRequestBody());UUID id=UUID.fromString(b.path("notes").path("maintenance_attempt").asText());committedSubscription=db.queryForObject("SELECT count(*) FROM maintenance_subscriptions WHERE id=? AND subscription_state='CREATING' AND provider_plan_id IS NOT NULL",Integer.class,id)==1;subscription=b.deepCopy();subscription.put("id",SUB);subscription.put("entity","subscription");subscription.put("status",providerStatus);entered.countDown();if(blockSubscription)try{if(!release.await(7,TimeUnit.SECONDS))throw new IOException("Fixture subscription was not explicitly released.");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Fixture subscription release interrupted.",e);}send(x,200,subscription);return;}
  if(p.equals("/v1/subscriptions/"+SUB)){var n=subscription.deepCopy();n.put("status",providerStatus.equals("cancelled")?"cancelled":paid?"active":providerStatus);send(x,200,n);return;}
  if(p.equals("/v1/subscriptions/"+SUB+"/cancel")){cancels.incrementAndGet();providerStatus="cancelled";send(x,cancelUncertain?503:200,Map.of("id",SUB,"status","cancelled"));return;}
  if(p.equals("/v1/invoices")){send(x,200,collection(paid&&first(x)?List.of(invoice()):List.of()));return;}
  if(p.equals("/v1/disputes")){send(x,200,collection(raiseDispute&&listDispute&&first(x)?List.of(dispute()):List.of()));return;}
  if(p.equals("/v1/disputes/"+DISPUTE)){send(x,200,dispute());return;}
  if(p.equals("/v1/payments/"+PAYMENT)){send(x,200,payment());return;}
  if(p.equals("/v1/payments/"+PAYMENT+"/refunds")){send(x,200,collection(first(x)&&(refunded>0||pendingRefund)?List.of(Map.of("id","rfnd_fixture123456","payment_id",PAYMENT,"amount",refunded>0?refunded:1000,"currency","INR","status",pendingRefund?"pending":"processed")):List.of()));return;}
  if(p.equals("/v1/payments/"+PAYMENT+"/transfers")){if(method.equals("POST")){transfers.incrementAndGet();var b=json.readTree(x.getRequestBody()).path("transfers").get(0);UUID key=UUID.fromString(b.path("notes").path("maintenance_route").asText());committedTransfer=db.queryForObject("SELECT count(*) FROM maintenance_periods WHERE transfer_key=? AND transfer_state='CREATING'",Integer.class,key)==1;transfer=b.deepCopy();transfer.put("id",TRANSFER);transfer.put("entity","transfer");transfer.put("source",PAYMENT);transfer.put("recipient",wrongPayee?"acc_attacker123456":ACCOUNT);transfer.put("status",transferStatus);transfer.put("amount_reversed",0);send(x,unknownTransfer?503:200,unknownTransfer?Map.of("error","ambiguous after commit"):collection(List.of(transfer)));return;}if(transfer!=null){var copy=transfer.deepCopy();copy.put("status",transferStatus);copy.put("amount_reversed",transferStatus.equals("reversed")?10000:0);send(x,200,collection(List.of(copy)));}else send(x,200,collection(List.of()));return;}
  send(x,404,Map.of("error","unsupported fixture path"));
 }
 public void close(){release.countDown();server.stop(0);executor.shutdownNow();}
 @TestConfiguration public static class Configuration{
  @Bean(destroyMethod="close") MaintenanceFixture maintenanceFixture(JdbcTemplate db,ObjectMapper json)throws IOException{return new MaintenanceFixture(db,json);}
  @Bean @Primary RazorpayClient maintenanceProvider(MaintenanceFixture f,ObjectMapper json){return new RazorpayClient(json,true,KEY,SECRET,WEBHOOK,"live",true,true,f.origin(),Duration.ofSeconds(5));}
 }
}
