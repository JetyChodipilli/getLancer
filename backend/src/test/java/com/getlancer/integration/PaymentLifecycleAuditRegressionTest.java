package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.delivery.DeliveryRepository;
import com.getlancer.payments.RazorpayClient;
import com.getlancer.payments.RazorpayFixture;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Local HTTP provider fixture and PostgreSQL evidence; never a merchant transaction. */
@org.springframework.test.context.ContextConfiguration(initializers=TestDatabaseGuard.class)
@SpringBootTest(properties={"app.environment=local","app.jobs-enabled=false","app.admin-email=admin@example.test","app.admin-password=","app.admin-totp=","spring.config.import=",
  "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}","spring.datasource.username=${TEST_DB_USERNAME:postgres}","spring.datasource.password=${TEST_DB_PASSWORD:}",
  "spring.datasource.hikari.schema=getlancer_test","spring.flyway.default-schema=getlancer_test","spring.flyway.schemas=getlancer_test","app.origin=http://localhost:3000","app.secure-cookie=false","app.storage.access-key=","app.storage.secret-key="})
@AutoConfigureMockMvc
@Import(RazorpayFixture.Configuration.class)
class PaymentLifecycleAuditRegressionTest {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired RazorpayFixture fixture;
  @Autowired DeliveryRepository delivery;
  @SpyBean(name="fixtureProvider") RazorpayClient provider;
  UUID engagement,attempt;
  volatile String refundStatus="pending",refundPayment;
  volatile long refundAmount=1000;
  static final String REFUND="rfnd_audit123456";

  MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request){return request.cookie(new Cookie("gl_session","audit-buyer"));}
  MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder request){return auth(request).header("Origin","http://localhost:3000").header("X-Requested-With","getlancer").contentType("application/json").content("{}");}
  String refundEvent(){return "{\"event\":\"refund.created\",\"payload\":{\"refund\":{\"entity\":{\"id\":\""+REFUND+"\",\"payment_id\":\""+fixture.paymentId()+"\"}}}}";}
  MockHttpServletRequestBuilder webhook(String event,String body)throws Exception{var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(RazorpayFixture.WEBHOOK.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return post("/api/v1/payments/razorpay/webhook").header("x-razorpay-event-id",event).header("X-Razorpay-Signature",HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)))).contentType("application/json").content(body);}
  boolean completionEligible(){db.update("UPDATE payment_attempts SET mode='live' WHERE id=?",attempt);try{return delivery.allPaid(engagement);}finally{db.update("UPDATE payment_attempts SET mode='test' WHERE id=?",attempt);}}
  boolean pending(String event){return db.queryForObject("SELECT processed_at IS NULL FROM payment_webhook_events WHERE event_id=?",Boolean.class,event);}
  void reconcile()throws Exception{mvc.perform(write(post("/api/v1/payments/"+attempt+"/reconcile"))).andExpect(status().isOk());}

  @BeforeEach void prepare()throws Exception{
    fixture.reset();db.execute("TRUNCATE users CASCADE");db.execute("TRUNCATE rate_buckets");db.execute("TRUNCATE payment_webhook_events");
    UUID buyer=UUID.randomUUID(),seller=UUID.randomUUID(),product=UUID.randomUUID(),inquiry=UUID.randomUUID(),milestone=UUID.randomUUID();engagement=UUID.randomUUID();
    for(UUID user:List.of(buyer,seller)){db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",user,user+"@example.test");db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT'),(?,'DEVELOPER')",user,user);db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,?,'APPROVED')",user,user.toString(),"Audit user");}
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",Support.hash("audit-buyer"),buyer);
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,approval_status,lifecycle_status,visibility) VALUES(?,?,?,'Portal','Customer portal','Working portal','SAAS','CRM','React, Java','Built it','APPROVED','ACTIVE','PUBLIC')",product,seller,product.toString());
    db.update("INSERT INTO inquiries(id,reference_product_id,developer_user_id,client_email,client_name,request_type,description,budget_band,timeline_band,current_status,email_confirmed_at,idempotency_key,request_hash) VALUES(?,?,?,?,'Buyer','CUSTOMIZE','Portal delivery','10k','month','DISCUSSION',now(),?,'test')",inquiry,product,seller,buyer+"@example.test",UUID.randomUUID());
    db.update("INSERT INTO delivery_engagements(id,source_inquiry_id,buyer_user_id,builder_user_id,title,created_by,status) VALUES(?,?,?,?,'Audit delivery',?,'ACTIVE')",engagement,inquiry,buyer,seller,buyer);
    db.update("INSERT INTO delivery_milestones(id,engagement_id,ordinal,title,description,amount_minor,due_date,status,accepted_by,accepted_at) VALUES(?,?,1,'Portal','Accepted deliverable',10000,current_date+1,'ACCEPTED',?,now())",milestone,engagement,buyer);
    db.update("INSERT INTO payment_accounts(id,builder_user_id,account_id,mode,provider_status,activation_confirmed,verified_by) VALUES(?,?,?,'test','created',true,?)",UUID.randomUUID(),seller,RazorpayFixture.ACCOUNT,buyer);
    mvc.perform(write(post("/api/v1/milestones/"+milestone+"/payment-order")).header("Idempotency-Key",UUID.randomUUID().toString())).andExpect(status().isOk());
    attempt=db.queryForObject("SELECT id FROM payment_attempts",UUID.class);fixture.captured=true;refundStatus="pending";refundAmount=1000;refundPayment=fixture.paymentId();
    doAnswer(invocation->json.valueToTree(Map.of("id",invocation.getArgument(0,String.class),"entity","refund","payment_id",refundPayment,"amount",refundAmount,"currency","INR","status",refundStatus))).when(provider).refund(anyString());
    reconcile();assertTrue(completionEligible());
  }

  @Test void createdRefundWithZeroCapturedRefundTotalStaysHeldUntilProviderConfirmsFailure()throws Exception{
    mvc.perform(webhook("pending-refund",refundEvent())).andExpect(status().isOk());assertTrue(pending("pending-refund"));assertFalse(completionEligible());
    reconcile();assertTrue(pending("pending-refund"));assertFalse(completionEligible());
    refundStatus="failed";reconcile();assertFalse(pending("pending-refund"));assertTrue(completionEligible());
    assertEquals(0L,db.queryForObject("SELECT refunded_minor FROM payment_attempts",Long.class));assertEquals(0,db.queryForObject("SELECT count(*) FROM payment_ledger WHERE kind='REFUND'",Integer.class));
  }
  @Test void processedRefundEvidenceIsMonotoneAndSignedReplayCannotDuplicateTheLedger()throws Exception{
    mvc.perform(webhook("processed-refund",refundEvent())).andExpect(status().isOk());refundStatus="processed";
    reconcile();reconcile();mvc.perform(webhook("processed-refund",refundEvent())).andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true));
    assertFalse(pending("processed-refund"));assertFalse(completionEligible());assertEquals(1000L,db.queryForObject("SELECT refunded_minor FROM payment_attempts",Long.class));
    assertEquals(1000L,db.queryForObject("SELECT sum(amount_minor) FROM payment_ledger WHERE kind='REFUND'",Long.class));assertEquals(1,db.queryForObject("SELECT count(*) FROM payment_ledger WHERE kind='CAPTURE'",Integer.class));
    mvc.perform(webhook("processed-refund",refundEvent()+" ")).andExpect(status().isConflict());
    refundStatus="failed";reconcile();assertEquals(1000L,db.queryForObject("SELECT refunded_minor FROM payment_attempts",Long.class));
  }
  @Test void reopenedLegacyReceiptRequiresItsOriginalSignedReferencesAndTerminalProviderEvidence()throws Exception{
    String body=refundEvent();db.update("INSERT INTO payment_webhook_events(event_id,payload_hash,event_kind) VALUES('legacy-refund',?,'refund.created')",Support.hash(body));assertFalse(completionEligible());
    reconcile();assertTrue(pending("legacy-refund"));assertFalse(completionEligible());
    mvc.perform(webhook("legacy-refund",body+" ")).andExpect(status().isConflict());assertTrue(pending("legacy-refund"));
    mvc.perform(webhook("legacy-refund",body)).andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true));assertTrue(pending("legacy-refund"));assertFalse(completionEligible());
    refundStatus="failed";reconcile();assertFalse(pending("legacy-refund"));assertTrue(completionEligible());assertEquals(REFUND,db.queryForObject("SELECT refund_id FROM payment_webhook_events WHERE event_id='legacy-refund'",String.class));
  }
  @Test void mismatchedProviderRefundCannotClearItsPendingHold()throws Exception{
    refundPayment="pay_other123456";mvc.perform(webhook("wrong-refund",refundEvent())).andExpect(status().isConflict());assertTrue(pending("wrong-refund"));assertFalse(completionEligible());
    refundPayment=fixture.paymentId();refundStatus="failed";reconcile();assertFalse(pending("wrong-refund"));assertTrue(completionEligible());
  }
  @Test void moreThanTwoHundredDuplicateReceiptsDrainWithoutDeletingEvidence()throws Exception{
    refundStatus="failed";
    db.update("INSERT INTO payment_webhook_events(event_id,payload_hash,event_kind,payment_id,refund_id) SELECT 'queued-refund-'||n,?,'refund.created',?,? FROM generate_series(1,601) n",Support.hash("queued signed fixture evidence"),fixture.paymentId(),REFUND);
    assertFalse(completionEligible());
    for(int batch=0;batch<3;batch++){reconcile();assertFalse(completionEligible());}
    assertEquals(1,db.queryForObject("SELECT count(*) FROM payment_webhook_events WHERE processed_at IS NULL",Integer.class));
    reconcile();assertTrue(completionEligible());assertEquals(601,db.queryForObject("SELECT count(*) FROM payment_webhook_events",Integer.class));
    assertEquals(1,db.queryForObject("SELECT count(*) FROM payment_provider_refunds",Integer.class));
  }
  @Test void confirmedRefundMembershipAndAmountCannotBeRebound()throws Exception{
    mvc.perform(webhook("immutable-refund",refundEvent())).andExpect(status().isOk());
    refundAmount=2000;refundStatus="failed";mvc.perform(write(post("/api/v1/payments/"+attempt+"/reconcile"))).andExpect(status().isConflict());
    assertTrue(pending("immutable-refund"));assertFalse(completionEligible());assertEquals(1000L,db.queryForObject("SELECT amount_minor FROM payment_provider_refunds",Long.class));
    refundAmount=1000;reconcile();assertFalse(pending("immutable-refund"));assertTrue(completionEligible());
  }
  @Test void anOlderTerminalReadCannotConsumeANewerRefundReceipt()throws Exception{
    mvc.perform(webhook("first-refund",refundEvent())).andExpect(status().isOk());
    var entered=new CountDownLatch(1);var release=new CountDownLatch(1);refundStatus="failed";
    var terminal=json.valueToTree(Map.of("id",REFUND,"entity","refund","payment_id",fixture.paymentId(),"amount",1000,"currency","INR","status","failed"));
    doAnswer(invocation->{entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return terminal;}).when(provider).refund(REFUND);
    var pool=Executors.newSingleThreadExecutor();var future=pool.submit(()->{reconcile();return null;});
    try{assertTrue(entered.await(5,TimeUnit.SECONDS));db.update("INSERT INTO payment_webhook_events(event_id,payload_hash,event_kind,payment_id,refund_id) VALUES('newer-refund',?,'refund.created',?,?)",Support.hash("new evidence"),fixture.paymentId(),REFUND);}
    finally{release.countDown();future.get(5,TimeUnit.SECONDS);pool.shutdownNow();}
    assertFalse(pending("first-refund"));assertTrue(pending("newer-refund"));assertFalse(completionEligible());
    doReturn(terminal).when(provider).refund(REFUND);reconcile();assertFalse(pending("newer-refund"));assertTrue(completionEligible());
  }
}
