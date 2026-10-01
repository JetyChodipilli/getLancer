package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

import com.getlancer.delivery.DeliveryRepository;
import com.getlancer.payments.*;
import com.getlancer.shared.*;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@org.springframework.test.context.ContextConfiguration(initializers=TestDatabaseGuard.class)
@SpringBootTest(properties={"app.environment=local","app.jobs-enabled=false","app.admin-email=admin@example.test","app.admin-password=","app.admin-totp=","spring.config.import=",
  "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}","spring.datasource.username=${TEST_DB_USERNAME:postgres}","spring.datasource.password=${TEST_DB_PASSWORD:}",
  "spring.datasource.hikari.schema=getlancer_test","spring.flyway.default-schema=getlancer_test","spring.flyway.schemas=getlancer_test","app.origin=http://localhost:3000","app.secure-cookie=false","app.storage.access-key=","app.storage.secret-key="})
@AutoConfigureMockMvc
@Import(RazorpayFixture.Configuration.class)
class PaymentIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired RazorpayFixture fixture;
  @Autowired DeliveryRepository delivery;
  @Autowired PaymentService payments;
  UUID buyer,seller,outsider,admin,engagement,milestone;
  String key;

  UUID user(String name,boolean approved) {
    UUID user=UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",user,name+"@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT'),(?,'DEVELOPER')",user,user);
    db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,?,?)",user,name,name,approved?"APPROVED":"DRAFT");
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",Support.hash(name),user);
    return user;
  }
  MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder req,String actor) {return req.cookie(new Cookie("gl_session",actor));}
  MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req,String actor,String body) {return as(req,actor).header("Origin","http://localhost:3000").header("X-Requested-With","getlancer").contentType("application/json").content(body);}
  MockHttpServletRequestBuilder create(String actor,String requestKey) {return json(post("/api/v1/milestones/"+milestone+"/payment-order"),actor,"{\"amountMinor\":1,\"accountId\":\"acc_attacker123456\"}").header("Idempotency-Key",requestKey);}
  UUID attempt() {return db.queryForObject("SELECT id FROM payment_attempts ORDER BY created_at DESC LIMIT 1",UUID.class);}
  String state() {return db.queryForObject("SELECT status FROM payment_attempts WHERE id=?",String.class,attempt());}
  String sign(String secret,byte[] bytes) throws Exception {var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(bytes));}
  String callback() throws Exception {String id=fixture.paymentId();return "{\"razorpay_payment_id\":\""+id+"\",\"razorpay_signature\":\""+sign(RazorpayFixture.SECRET,(fixture.orderId()+"|"+id).getBytes(StandardCharsets.UTF_8))+"\"}";}
  String event(String kind) {return "{\"event\":\""+kind+"\",\"payload\":{\"payment\":{\"entity\":{\"id\":\""+fixture.paymentId()+"\",\"order_id\":\""+fixture.orderId()+"\"}}}}";}
  MockHttpServletRequestBuilder webhook(String id,String body) throws Exception {return post("/api/v1/payments/razorpay/webhook").contentType("application/json").header("x-razorpay-event-id",id).header("X-Razorpay-Signature",sign(RazorpayFixture.WEBHOOK,body.getBytes(StandardCharsets.UTF_8))).content(body);}

  @BeforeEach void prepare() throws Exception {
    fixture.reset();db.execute("TRUNCATE users CASCADE");db.execute("TRUNCATE rate_buckets");db.execute("TRUNCATE payment_webhook_events");
    buyer=user("buyer",false);seller=user("seller",true);outsider=user("outsider",false);admin=user("admin",false);
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')",admin);db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?",admin);
    UUID product=UUID.randomUUID(), inquiry=UUID.randomUUID();engagement=UUID.randomUUID();milestone=UUID.randomUUID();key=UUID.randomUUID().toString();
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,approval_status,lifecycle_status,visibility) VALUES(?,?,?,'Portal','Customer portal','Working portal','SAAS','CRM','React, Java','Built it','APPROVED','ACTIVE','PUBLIC')",product,seller,product.toString());
    db.update("INSERT INTO inquiries(id,reference_product_id,developer_user_id,client_email,client_name,request_type,description,budget_band,timeline_band,current_status,email_confirmed_at,idempotency_key,request_hash) VALUES(?,?,?,'buyer@example.test','Buyer','CUSTOMIZE','Portal delivery','10k','month','DISCUSSION',now(),?,'test')",inquiry,product,seller,UUID.randomUUID());
    db.update("INSERT INTO delivery_engagements(id,source_inquiry_id,buyer_user_id,builder_user_id,title,created_by,status) VALUES(?,?,?,?,'Portal delivery',?,'ACTIVE')",engagement,inquiry,buyer,seller,buyer);
    db.update("INSERT INTO delivery_milestones(id,engagement_id,ordinal,title,description,amount_minor,due_date,status,accepted_by,accepted_at) VALUES(?,?,1,'Portal','Accepted deliverable',10000,current_date+1,'ACCEPTED',?,now())",milestone,engagement,buyer);
    mvc.perform(json(post("/api/v1/admin/payments/accounts"),"admin","{\"builderUserId\":\""+seller+"\",\"accountId\":\""+RazorpayFixture.ACCOUNT+"\",\"activationConfirmed\":true}" )).andExpect(status().isOk());
  }

  @Test void frozenAmountPartyAccessAndRepeatRequestNeverDuplicateOrder() throws Exception {
    mvc.perform(create("seller",key)).andExpect(status().isForbidden());mvc.perform(create("outsider",key)).andExpect(status().isNotFound());
    mvc.perform(json(post("/api/v1/milestones/"+milestone+"/payment-order"),"buyer","{}")).andExpect(status().isBadRequest());
    mvc.perform(create("buyer",key)).andExpect(status().isOk()).andExpect(jsonPath("$.amountMinor").value(10000)).andExpect(jsonPath("$.mode").value("test"));
    assertTrue(fixture.reservationCommitted);assertEquals(10000,fixture.lastBody.path("amount").longValue());assertEquals(RazorpayFixture.ACCOUNT,fixture.lastBody.path("transfers").get(0).path("account").asText());
    mvc.perform(create("buyer",key)).andExpect(status().isOk());mvc.perform(create("buyer",UUID.randomUUID().toString())).andExpect(status().isOk());assertEquals(1,fixture.creates.get());
    mvc.perform(as(get("/api/v1/engagements/"+engagement+"/payments"),"outsider")).andExpect(status().isNotFound());
    mvc.perform(as(get("/api/v1/engagements/"+engagement+"/payments"),"seller")).andExpect(jsonPath("$.items[0].milestoneId").value(milestone.toString()));
    assertEquals(0,db.queryForObject("SELECT count(*) FROM payment_ledger",Integer.class));
    UUID other=UUID.randomUUID();db.update("INSERT INTO delivery_milestones(id,engagement_id,ordinal,title,description,amount_minor,due_date,status,accepted_by,accepted_at) VALUES(?,?,2,'Other','Other delivery',10000,current_date+1,'ACCEPTED',?,now())",other,engagement,buyer);
    mvc.perform(json(post("/api/v1/milestones/"+other+"/payment-order"),"buyer","{}").header("Idempotency-Key",key)).andExpect(status().isConflict());assertEquals(1,fixture.creates.get());
  }

  @Test void signatureAndAuthoritativeAmountCaptureChecksProtectLedger() throws Exception {
    mvc.perform(create("buyer",key)).andExpect(status().isOk());UUID id=attempt();String path="/api/v1/payments/"+id+"/verify";
    mvc.perform(json(post(path),"outsider",callback())).andExpect(status().isNotFound());
    mvc.perform(json(post(path),"buyer",callback().replace(sign(RazorpayFixture.SECRET,(fixture.orderId()+"|"+fixture.paymentId()).getBytes(StandardCharsets.UTF_8)),"0".repeat(64)))).andExpect(status().isBadRequest());
    mvc.perform(json(post(path),"buyer",callback())).andExpect(jsonPath("$.status").value("ORDER_CREATED"));
    fixture.captured=true;fixture.wrongAmount=true;mvc.perform(json(post(path),"buyer",callback())).andExpect(status().isConflict());assertEquals("ORDER_CREATED",state());
    fixture.wrongAmount=false;for(int n=0;n<2;n++) mvc.perform(json(post(path),"buyer",callback())).andExpect(jsonPath("$.status").value("CAPTURED"));
    assertEquals(1,db.queryForObject("SELECT count(*) FROM payment_ledger WHERE kind='CAPTURE'",Integer.class));assertFalse(delivery.allPaid(engagement),"Provider test mode cannot qualify as real commercial payment");
    assertEquals("PENDING",db.queryForObject("SELECT settlement_status FROM payment_attempts",String.class));
    assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE payment_ledger SET amount_minor=1"));
  }

  @Test void signedRawWebhooksDeduplicateAndRefundTotalsNeverDecrease() throws Exception {
    mvc.perform(create("buyer",key)).andExpect(status().isOk());fixture.captured=true;String body=event("payment.captured");
    mvc.perform(webhook("event_capture",body)).andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(false));
    mvc.perform(webhook("event_capture",body)).andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true));
    mvc.perform(webhook("event_capture",body+" ")).andExpect(status().isConflict());
    mvc.perform(post("/api/v1/payments/razorpay/webhook").contentType("application/json").header("X-Razorpay-Signature","0".repeat(64)).header("x-razorpay-event-id","bad").content("broken json")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_WEBHOOK_SIGNATURE"));
    fixture.refunded=3000;mvc.perform(webhook("event_refund",event("payment.captured"))).andExpect(status().isOk());
    fixture.refunded=1000;mvc.perform(webhook("event_older",event("payment.authorized"))).andExpect(status().isOk());assertEquals(3000L,db.queryForObject("SELECT refunded_minor FROM payment_attempts",Long.class));
    fixture.refunded=10000;mvc.perform(webhook("event_full",event("payment.captured"))).andExpect(status().isOk());assertEquals("REFUNDED",state());
    assertEquals(10000L,db.queryForObject("SELECT sum(amount_minor) FROM payment_ledger WHERE kind='REFUND'",Long.class));
    assertEquals(1,db.queryForObject("SELECT count(*) FROM payment_ledger WHERE kind='CAPTURE'",Integer.class));
    assertFalse(delivery.allPaid(engagement));
    mvc.perform(post("/api/v1/payments/"+attempt()+"/reconcile").content("{}")).andExpect(status().isForbidden());
  }

  @Test void uncertainCreateRequiresMfaVerifiedReceiptPayeeOrderBinding() throws Exception {
    fixture.uncertain=true;mvc.perform(create("buyer",key)).andExpect(status().isBadGateway());assertEquals("UNKNOWN",state());
    mvc.perform(create("buyer",key)).andExpect(status().isConflict());mvc.perform(create("buyer",UUID.randomUUID().toString())).andExpect(status().isConflict());assertEquals(1,fixture.creates.get());
    UUID id=attempt();String path="/api/v1/admin/payments/"+id+"/bind-order",body="{\"orderId\":\""+fixture.orderId()+"\"}";
    mvc.perform(json(post("/api/v1/payments/"+id+"/reconcile"),"buyer","{}")).andExpect(status().isConflict());
    mvc.perform(json(post(path),"buyer",body)).andExpect(status().isForbidden());db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?",admin);
    mvc.perform(json(post(path),"admin",body)).andExpect(status().isForbidden());db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?",admin);
    fixture.wrongPayee=true;mvc.perform(json(post(path),"admin",body)).andExpect(status().isConflict());assertEquals("UNKNOWN",state());
    fixture.wrongPayee=false;fixture.captured=true;mvc.perform(json(post(path),"admin",body)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CAPTURED"));
    mvc.perform(json(post("/api/v1/admin/payments/"+id+"/reconcile"),"admin","{}")).andExpect(status().isOk());
    mvc.perform(json(post("/api/v1/admin/payments/"+id+"/reconcile"),"buyer","{}")).andExpect(status().isForbidden());assertEquals(1,fixture.creates.get());
  }

  @Test void concurrentCreateUsesCommittedReservationAndOneProviderRequest() throws Exception {
    fixture.blocked=true;ExecutorService executor=Executors.newFixedThreadPool(2);
    try {
      var first=executor.submit(()->mvc.perform(create("buyer",key)).andReturn().getResponse().getStatus());
      assertTrue(fixture.createEntered.await(3,TimeUnit.SECONDS));
      var second=executor.submit(()->mvc.perform(create("buyer",key)).andReturn().getResponse().getStatus());
      assertEquals(409,second.get(3,TimeUnit.SECONDS));fixture.releaseCreate.countDown();assertEquals(200,first.get(4,TimeUnit.SECONDS));assertEquals(1,fixture.creates.get());
    } finally {fixture.releaseCreate.countDown();executor.shutdownNow();}
  }

  @Test void disputeUnacceptedAndSuspendedPayeeBlockNewCheckout() throws Exception {
    db.update("UPDATE delivery_milestones SET status='SUBMITTED',accepted_by=null,accepted_at=null WHERE id=?",milestone);mvc.perform(create("buyer",key)).andExpect(status().isConflict());
    db.update("UPDATE delivery_milestones SET status='ACCEPTED',accepted_by=?,accepted_at=now() WHERE id=?",buyer,milestone);
    db.update("UPDATE delivery_engagements SET status='DISPUTED' WHERE id=?",engagement);mvc.perform(create("buyer",key)).andExpect(status().isConflict());
    db.update("UPDATE delivery_engagements SET status='ACTIVE' WHERE id=?",engagement);db.update("UPDATE users SET account_status='SUSPENDED' WHERE id=?",seller);mvc.perform(create("buyer",key)).andExpect(status().isConflict());assertEquals(0,fixture.creates.get());
  }

  @Test void mappingRequiresMfaAndExplicitActivatedSellerConfirmation() throws Exception {
    db.execute("TRUNCATE payment_accounts CASCADE");String body="{\"builderUserId\":\""+seller+"\",\"accountId\":\""+RazorpayFixture.ACCOUNT+"\"}";
    mvc.perform(json(post("/api/v1/admin/payments/accounts"),"admin",body)).andExpect(status().isBadRequest());
    mvc.perform(json(post("/api/v1/admin/payments/accounts"),"buyer",body)).andExpect(status().isForbidden());
    mvc.perform(json(post("/api/v1/admin/payments/accounts"),"admin",body.replace("}",",\"activationConfirmed\":true}"))).andExpect(status().isOk());
    assertEquals(1,db.queryForObject("SELECT count(*) FROM payment_account_audit",Integer.class));
    mvc.perform(as(get("/api/v1/admin/payments/accounts"),"buyer")).andExpect(status().isForbidden());
  }

  @Test void providerDisputesSurviveOrdinaryReconciliationAndOnlyVerifiedWinClearsHold() throws Exception {
    mvc.perform(create("buyer",key)).andExpect(status().isOk());fixture.captured=true;
    String dispute="{\"event\":\"payment.dispute.created\",\"payload\":{\"dispute\":{\"entity\":{\"id\":\"disp_fixture123456\",\"payment_id\":\""+fixture.paymentId()+"\"}}}}";
    mvc.perform(webhook("event_dispute",dispute)).andExpect(status().isOk());assertEquals("DISPUTED",state());
    mvc.perform(json(post("/api/v1/payments/"+attempt()+"/reconcile"),"buyer","{}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISPUTED"));
    fixture.disputeStatus="lost";mvc.perform(json(post("/api/v1/admin/payments/"+attempt()+"/reconcile"),"admin","{}")).andExpect(jsonPath("$.status").value("DISPUTED"));
    fixture.disputeStatus="won";mvc.perform(json(post("/api/v1/admin/payments/"+attempt()+"/reconcile"),"admin","{}")).andExpect(jsonPath("$.status").value("CAPTURED"));
    assertEquals(1,db.queryForObject("SELECT count(*) FROM payment_ledger WHERE kind='CAPTURE'",Integer.class));
  }

  @Test void chunkedWebhookBodyIsBoundedBeforeSignatureOrJsonParsing() throws Exception {
    HttpServletRequest request=mock(HttpServletRequest.class);byte[] huge=new byte[65537];ByteArrayInputStream input=new ByteArrayInputStream(huge);
    when(request.getContentLengthLong()).thenReturn(-1L);
    when(request.getInputStream()).thenReturn(new ServletInputStream(){public int read(){return input.read();}public boolean isFinished(){return input.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){}});
    ApiError error=assertThrows(ApiError.class,()->payments.webhook(request));assertEquals(413,error.status);verify(request,never()).getHeader("X-Razorpay-Signature");
  }
}
