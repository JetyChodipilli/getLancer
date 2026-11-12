package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.*;
import com.getlancer.commerce.*;
import com.getlancer.payments.CommerceProviderIntegrationTest;
import com.getlancer.shared.*;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@org.springframework.test.context.ContextConfiguration(initializers=TestDatabaseGuard.class)
@SpringBootTest(properties={"app.environment=local","app.jobs-enabled=false","app.admin-email=admin@example.test","app.admin-password=","app.admin-totp=","spring.config.import=",
 "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}","spring.datasource.username=${TEST_DB_USERNAME:postgres}","spring.datasource.password=${TEST_DB_PASSWORD:}",
 "spring.datasource.hikari.schema=getlancer_test","spring.flyway.default-schema=getlancer_test","spring.flyway.schemas=getlancer_test","app.origin=http://localhost:3000","app.secure-cookie=false","app.storage.access-key=","app.storage.secret-key=","app.commerce.webhook-secret=source-fixture-webhook-not-real","app.discovery-rate-limit=300"})
@AutoConfigureMockMvc
@Import(CommerceProviderIntegrationTest.Configuration.class)
class CommerceIntegrationTest {
  @Autowired JdbcTemplate db;@Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired CommerceProviderIntegrationTest.Fixture provider;
  @Autowired CommerceRepository repo;@Autowired CommercePaymentService payments;
  @Autowired com.getlancer.security.Security security;@Autowired com.getlancer.publishing.PublishingCapacity publishing;
  @MockBean CommerceStorage storage;
  UUID buyer,seller,outsider,admin,product,template,version;String slug,key;byte[] zip;
  UUID user(String name,boolean approved) {UUID id=UUID.randomUUID();db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",id,name+"@example.test");db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT'),(?,'DEVELOPER')",id,id);db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,?,?)",id,name,name,approved?"APPROVED":"DRAFT");db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",Support.hash(name),id);return id;}
  MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder r,String actor) {return r.cookie(new Cookie("gl_session",actor));}
  MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder r,String actor,String body) {return as(r,actor).header("Origin","http://localhost:3000").header("X-Requested-With","getlancer").contentType("application/json").content(body);}
  byte[] zip() throws IOException {var bytes=new ByteArrayOutputStream();try(var stream=new ZipOutputStream(bytes)) {for(var entry:Map.of("README.md","Build with npm install and npm run build. Complete source and setup instructions.","LICENSE","One commercial end product; no competing redistribution. Third-party source remains MIT licensed.","package.json","{\"name\":\"complete-source\",\"scripts\":{\"build\":\"vite build\"}}","src/index.ts","export const source = 'complete source';").entrySet()) {stream.putNextEntry(new ZipEntry(entry.getKey()));stream.write(entry.getValue().getBytes(StandardCharsets.UTF_8));stream.closeEntry();}}return bytes.toByteArray();}
  UUID upload(String release) throws Exception {var request=multipart("/api/v1/me/templates/"+template+"/versions").file(new MockMultipartFile("file","source.zip","application/zip",zip)).param("version",release).param("releaseNotes","Complete source with setup and third-party notices.").param("rightsConsent","true").header("Origin","http://localhost:3000").header("X-Requested-With","getlancer").cookie(new Cookie("gl_session","seller"));String result=mvc.perform(request).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();return UUID.fromString(json.readTree(result).path("id").asText());}
  void approve(UUID release) throws Exception {mvc.perform(body(post("/api/v1/me/templates/"+template+"/versions/"+release+"/submit"),"seller","{\"rightsConsent\":true}")).andExpect(status().isOk());mvc.perform(body(post("/api/v1/admin/templates/"+template+"/versions/"+release+"/review"),"admin","{\"action\":\"APPROVE\",\"reason\":\"Reviewed package manifest README and third-party redistribution rights.\",\"rightsReviewed\":true,\"packageReviewed\":true}")).andExpect(status().isOk());}
  MockHttpServletRequestBuilder order(String actor,String requestKey) {return body(post("/api/v1/templates/"+template+"/orders"),actor,"{\"versionId\":\""+version+"\",\"licenseConsent\":true}").header("Idempotency-Key",requestKey);}
  UUID purchase() {return db.queryForObject("SELECT id FROM template_purchases ORDER BY created_at DESC LIMIT 1",UUID.class);}
  String sign(String secret,byte[] data) throws Exception {var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(data));}
  String callback() throws Exception {return "{\"razorpay_payment_id\":\""+provider.paymentId()+"\",\"razorpay_signature\":\""+sign(CommerceProviderIntegrationTest.SECRET,(provider.orderId()+"|"+provider.paymentId()).getBytes(StandardCharsets.UTF_8))+"\"}";}
  String event(String kind) {return "{\"event\":\""+kind+"\",\"payload\":{\"payment\":{\"entity\":{\"id\":\""+provider.paymentId()+"\",\"order_id\":\""+provider.orderId()+"\"}}}}";}
  MockHttpServletRequestBuilder webhook(String event,String payload) throws Exception {return post("/api/v1/commerce/razorpay/webhook").contentType("application/json").content(payload).header("x-razorpay-event-id",event).header("X-Razorpay-Signature",sign(CommerceProviderIntegrationTest.WEBHOOK,payload.getBytes(StandardCharsets.UTF_8)));}
  String disputeEvent() {return "{\"event\":\"payment.dispute.created\",\"payload\":{\"dispute\":{\"entity\":{\"id\":\"disp_sourcefixture123\",\"payment_id\":\""+provider.paymentId()+"\"}}}}";}
  boolean noProviderHold() {return !repo.providerHeld(purchase());}
  String refundEvent(String id) {return "{\"event\":\"refund.created\",\"payload\":{\"refund\":{\"entity\":{\"id\":\""+id+"\",\"payment_id\":\""+provider.paymentId()+"\"}}}}";}
  void capturedPurchase() throws Exception {
    mvc.perform(order("buyer",key)).andExpect(status().isOk());provider.captured=true;
    mvc.perform(body(post("/api/v1/template-purchases/"+purchase()+"/verify"),"buyer",callback())).andExpect(status().isOk());
  }
  void revokeAfterPurchaseLockWait(UUID id,MockHttpServletRequestBuilder call,Runnable revoke,int expectedStatus) throws Exception {
    var executor=Executors.newSingleThreadExecutor();
    try(var holder=db.getDataSource().getConnection()) {
      holder.setAutoCommit(false);int holderPid;
      try(var statement=holder.createStatement();var result=statement.executeQuery("SELECT pg_backend_pid()")) {assertTrue(result.next());holderPid=result.getInt(1);}
      try(var statement=holder.prepareStatement("SELECT id FROM template_purchases WHERE id=? FOR UPDATE")) {
        statement.setObject(1,id);try(var result=statement.executeQuery()) {assertTrue(result.next());}
      }
      var pending=executor.submit(()->mvc.perform(call).andReturn().getResponse().getStatus());
      long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean waiting=false;
      while(System.nanoTime()<deadline&&!pending.isDone()) {
        if(db.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))",Integer.class,holderPid)>0) {waiting=true;break;}
        Thread.sleep(5);
      }
      assertTrue(waiting,"The source purchase request must reach an actual PostgreSQL lock wait");
      revoke.run();holder.commit();assertEquals(expectedStatus,pending.get(10,TimeUnit.SECONDS).intValue());
    } finally {executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));}
  }
  @Test void buyerDisputeRejectsSessionRevokedDuringPurchaseLockWait() throws Exception {
    capturedPurchase();UUID id=purchase();int audits=db.queryForObject("SELECT count(*) FROM commerce_audit",Integer.class);
    revokeAfterPurchaseLockWait(id,body(post("/api/v1/template-purchases/"+id+"/disputes"),"buyer","{\"reason\":\"The session was revoked while this source purchase waited for its lock.\"}"),
      ()->db.update("DELETE FROM sessions WHERE token_hash=?",Support.hash("buyer")),401);
    assertEquals(0,db.queryForObject("SELECT count(*) FROM commerce_disputes WHERE purchase_id=?",Integer.class,id));
    assertEquals("CAPTURED",db.queryForObject("SELECT status FROM template_purchases WHERE id=?",String.class,id));
    assertEquals(audits,db.queryForObject("SELECT count(*) FROM commerce_audit",Integer.class).intValue());
  }
  @Test void operatorResolutionRejectsAdminRoleRevokedDuringPurchaseLockWait() throws Exception {
    capturedPurchase();UUID id=purchase();
    mvc.perform(body(post("/api/v1/template-purchases/"+id+"/disputes"),"buyer","{\"reason\":\"Request a licensed source review before any operator resolution.\"}")).andExpect(status().isOk());
    int audits=db.queryForObject("SELECT count(*) FROM commerce_audit",Integer.class);
    revokeAfterPurchaseLockWait(id,body(post("/api/v1/admin/template-purchases/"+id+"/resolve"),"admin","{\"resolution\":\"REVOKE\",\"reason\":\"A revoked administrator must not revoke this buyer entitlement.\"}"),
      ()->db.update("DELETE FROM user_roles WHERE user_id=? AND role='ADMIN'",admin),403);
    assertEquals("OPEN",db.queryForObject("SELECT status FROM commerce_disputes WHERE purchase_id=?",String.class,id));
    assertFalse(db.queryForObject("SELECT entitlement_revoked FROM template_purchases WHERE id=?",Boolean.class,id));
    assertEquals(audits,db.queryForObject("SELECT count(*) FROM commerce_audit",Integer.class).intValue());
  }
  @Test void refundInboxSurvivesFirstProviderFailureAndAnAggregateCannotReleaseItsHold() throws Exception {
    capturedPurchase();String payload=refundEvent("rfnd_sourcepending123");provider.failPayment=true;
    mvc.perform(webhook("source_refund_outage",payload)).andExpect(status().isBadGateway());
    assertFalse(noProviderHold());assertEquals("rfnd_sourcepending123",db.queryForObject("SELECT refund_id FROM commerce_webhook_events WHERE event_id='source_refund_outage'",String.class));
    provider.failPayment=false;provider.refundStatus="pending";
    mvc.perform(body(post("/api/v1/template-purchases/"+purchase()+"/reconcile"),"buyer","{}")).andExpect(status().isOk());
    assertFalse(noProviderHold());assertEquals(0L,db.queryForObject("SELECT refunded_minor FROM template_purchases",Long.class));
    mvc.perform(body(post("/api/v1/admin/template-purchases/"+purchase()+"/resolve"),"admin","{\"resolution\":\"RESUME\",\"reason\":\"Pending provider refund evidence cannot be locally cleared.\"}")).andExpect(status().isConflict());
    mvc.perform(webhook("source_refund_outage",payload+" ")).andExpect(status().isConflict());
    provider.refundStatus="failed";
    mvc.perform(webhook("source_refund_outage",payload)).andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true));
    assertTrue(noProviderHold());assertNotNull(db.queryForObject("SELECT processed_at FROM commerce_webhook_events WHERE event_id='source_refund_outage'",java.sql.Timestamp.class));
    assertEquals("failed",db.queryForObject("SELECT status FROM commerce_provider_refunds",String.class));
  }
  @Test void exactProcessedRefundOverridesAStaleZeroPaymentAggregate() throws Exception {
    capturedPurchase();provider.refundStatus="processed";provider.refundAmount=2500;provider.refunded=0;
    mvc.perform(webhook("source_exact_refund",refundEvent("rfnd_sourceprocessed123"))).andExpect(status().isOk());
    assertEquals(2500L,db.queryForObject("SELECT refunded_minor FROM template_purchases",Long.class));
    assertEquals("PARTIALLY_REFUNDED",db.queryForObject("SELECT status FROM template_purchases",String.class));
    assertEquals(2500L,db.queryForObject("SELECT sum(amount_minor) FROM commerce_ledger WHERE kind='REFUND'",Long.class));
    provider.refundStatus="failed";
    mvc.perform(body(post("/api/v1/template-purchases/"+purchase()+"/reconcile"),"buyer","{}")).andExpect(status().isOk());
    assertEquals(2500L,db.queryForObject("SELECT refunded_minor FROM template_purchases",Long.class));
    mvc.perform(body(post("/api/v1/admin/template-purchases/"+purchase()+"/resolve"),"admin","{\"resolution\":\"RESUME\",\"reason\":\"Confirmed refunded source cannot regain entitlement.\"}")).andExpect(status().isConflict());
  }
  @Test void wrongRefundMembershipOrIdentityCannotResolveAuthenticatedEvidence() throws Exception {
    capturedPurchase();provider.refundStatus="failed";String payload=refundEvent("rfnd_sourcewrong123");provider.wrongRefundPayment=true;
    mvc.perform(webhook("source_wrong_refund",payload)).andExpect(status().isConflict());assertFalse(noProviderHold());
    provider.wrongRefundPayment=false;provider.wrongRefundId=true;
    mvc.perform(webhook("source_wrong_refund",payload)).andExpect(status().isConflict());assertFalse(noProviderHold());
    provider.wrongRefundId=false;
    mvc.perform(webhook("source_wrong_refund",payload)).andExpect(status().isOk());assertTrue(noProviderHold());
  }
  @Test void historicalRefundWithoutAReferenceRequiresMatchingSignedRecovery() throws Exception {
    capturedPurchase();String payload=refundEvent("rfnd_sourcelegacy123");
    db.update("INSERT INTO commerce_webhook_events(event_id,payload_hash,event_kind,order_id,payment_id) VALUES('source_legacy_refund',?,'refund.created',?,?)",Support.hash(payload),provider.orderId(),provider.paymentId());
    assertFalse(noProviderHold());
    mvc.perform(body(post("/api/v1/template-purchases/"+purchase()+"/reconcile"),"buyer","{}")).andExpect(status().isConflict());
    assertNull(db.queryForObject("SELECT refund_id FROM commerce_webhook_events WHERE event_id='source_legacy_refund'",String.class));
    provider.refundStatus="failed";
    mvc.perform(webhook("source_legacy_refund",payload)).andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true));assertTrue(noProviderHold());
  }
  @Test void liveSourceDownloadRejectsKnownAndUnmappedPendingRefunds() throws Exception {
    UUID id=UUID.randomUUID();repo.reserve(id,buyer,UUID.randomUUID(),repo.template(template,false),repo.version(template,version,false),CommerceProviderIntegrationTest.ACCOUNT,"live");
    repo.bind(id,"order_refundlive123","CREATED");repo.capture(id,"pay_refundlive123",0,"PROCESSED");assertTrue(repo.entitled(id,buyer));
    db.update("INSERT INTO commerce_webhook_events(event_id,payload_hash,event_kind,payment_id,refund_id) VALUES('source_live_refund',?,'refund.created','pay_refundlive123','rfnd_livefixture123')",Support.hash("synthetic refund"));
    assertFalse(repo.entitled(id,buyer));mvc.perform(as(get("/api/v1/template-purchases/"+id+"/download"),"buyer")).andExpect(status().isForbidden());
    db.update("UPDATE commerce_webhook_events SET processed_at=clock_timestamp() WHERE event_id='source_live_refund'");assertTrue(repo.entitled(id,buyer));
    db.update("INSERT INTO commerce_webhook_events(event_id,payload_hash,event_kind) VALUES('source_unmapped_refund',?,'refund.created')",Support.hash("synthetic legacy refund"));
    assertFalse(repo.entitled(id,buyer));
  }
  @Test void staleRefundResponseCannotOverwriteANewerTerminalObservationOrClearLaterReceipt() throws Exception {
    capturedPurchase();String payload=refundEvent("rfnd_sourcescheduled123");provider.blockRefund=true;
    var workers=Executors.newSingleThreadExecutor();
    var pending=workers.submit(()->mvc.perform(webhook("source_scheduled_refund",payload)).andReturn().getResponse().getStatus());
    try {
      assertTrue(provider.refundEntered.await(2,TimeUnit.SECONDS));
      var transaction=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(db.getDataSource()));
      transaction.execute(status->{db.execute("SET LOCAL lock_timeout='300ms'");db.queryForObject("SELECT id FROM template_purchases WHERE id=? FOR UPDATE",UUID.class,purchase());return null;});
      provider.blockRefund=false;provider.refundStatus="failed";
      mvc.perform(body(post("/api/v1/template-purchases/"+purchase()+"/reconcile"),"buyer","{}")).andExpect(status().isOk());
      db.update("INSERT INTO commerce_webhook_events(event_id,payload_hash,event_kind,payment_id,refund_id) VALUES('source_later_refund',?,'refund.created',?,'rfnd_sourcescheduled123')",Support.hash("later signed fixture"),provider.paymentId());
      provider.releaseRefund.countDown();assertEquals(200,pending.get(5,TimeUnit.SECONDS));
      assertEquals("failed",db.queryForObject("SELECT status FROM commerce_provider_refunds",String.class));
      assertNull(db.queryForObject("SELECT processed_at FROM commerce_webhook_events WHERE event_id='source_later_refund'",java.sql.Timestamp.class));assertFalse(noProviderHold());
    } finally {provider.releaseRefund.countDown();workers.shutdownNow();}
  }
  @Test void earlyDisputeSurvivesUncertainBindingAndDeniesSource() throws Exception {
    provider.uncertain=true;mvc.perform(order("buyer",key)).andExpect(status().isBadGateway());provider.captured=true;
    mvc.perform(webhook("early_source_dispute",disputeEvent())).andExpect(status().isOk());
    mvc.perform(body(post("/api/v1/admin/template-purchases/"+purchase()+"/bind-order"),"admin","{\"orderId\":\""+provider.orderId()+"\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISPUTED"));
    assertFalse(noProviderHold());
  }
  @Test void unavailableDisputeRetainsHoldAndOperatorCannotResumeUntilReconciled() throws Exception {
    mvc.perform(order("buyer",key)).andExpect(status().isOk());provider.captured=true;
    mvc.perform(body(post("/api/v1/template-purchases/"+purchase()+"/verify"),"buyer",callback())).andExpect(status().isOk());assertTrue(noProviderHold());
    provider.failDispute=true;mvc.perform(webhook("failed_source_dispute",disputeEvent())).andExpect(status().isBadGateway());assertFalse(noProviderHold());
    mvc.perform(body(post("/api/v1/admin/template-purchases/"+purchase()+"/resolve"),"admin","{\"resolution\":\"RESUME\",\"reason\":\"An operator cannot override pending provider evidence.\"}")).andExpect(status().isConflict());
    provider.failDispute=false;mvc.perform(webhook("failed_source_dispute",disputeEvent())).andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true));
    provider.disputeStatus="won";mvc.perform(body(post("/api/v1/template-purchases/"+purchase()+"/reconcile"),"buyer","{}")).andExpect(status().isOk());assertTrue(noProviderHold());
  }
  @Test void disputeHttpReleasesPurchaseLockWhilePendingEvidenceDeniesSource() throws Exception {
    mvc.perform(order("buyer",key)).andExpect(status().isOk());provider.captured=true;provider.blockDispute=true;
    var executor=Executors.newSingleThreadExecutor();var future=executor.submit(()->mvc.perform(webhook("slow_source_dispute",disputeEvent())).andExpect(status().isOk()));
    try {assertTrue(provider.disputeEntered.await(5,TimeUnit.SECONDS));var transaction=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(db.getDataSource()));
      transaction.execute(status->{db.execute("SET LOCAL lock_timeout='300ms'");db.queryForObject("SELECT id FROM template_purchases WHERE id=? FOR UPDATE",UUID.class,purchase());return null;});assertFalse(noProviderHold());
    } finally {provider.releaseDispute.countDown();future.get(5,TimeUnit.SECONDS);executor.shutdownNow();}
  }
  String storedSourceDispute(int n,String state) {
    String id="disp_sourcebatch"+String.format(Locale.ROOT,"%02d",n);
    db.update("INSERT INTO commerce_provider_disputes(id,purchase_id,status,deducted_minor,updated_at) VALUES(?,?,?,0,'2000-01-01T00:00:00Z')",id,purchase(),state);return id;
  }
  void reconcileSource() throws Exception {mvc.perform(body(post("/api/v1/template-purchases/"+purchase()+"/reconcile"),"buyer","{}")).andExpect(status().isOk());}
  @Test void wonHistoryCannotStarveLaterStoredDisputesOrEraseAnIndependentHold() throws Exception {
    capturedPurchase();for(int n=0;n<8;n++)storedSourceDispute(n,"won");
    String ninth=storedSourceDispute(8,"open"),tenth=storedSourceDispute(9,"lost");provider.disputeStatus="won";provider.disputeStatuses.put(tenth,"lost");
    assertFalse(noProviderHold());reconcileSource();
    assertEquals(8,provider.disputeReads.size());assertEquals(List.of(ninth,tenth),provider.disputeReads.subList(0,2));
    assertEquals("won",db.queryForObject("SELECT status FROM commerce_provider_disputes WHERE id=?",String.class,ninth));assertFalse(noProviderHold());
    provider.disputeStatuses.put(tenth,"won");provider.disputeReads.clear();reconcileSource();
    assertEquals(8,provider.disputeReads.size());assertTrue(provider.disputeReads.contains(tenth));assertTrue(noProviderHold());
  }
  @Test void unchangedHeldObservationsRotateSoANinthDisputeEventuallyRecovers() throws Exception {
    capturedPurchase();var ids=new ArrayList<String>();for(int n=0;n<9;n++)ids.add(storedSourceDispute(n,"open"));
    provider.disputeStatuses.put(ids.get(8),"won");reconcileSource();
    assertEquals(ids.subList(0,8),provider.disputeReads);assertFalse(noProviderHold());
    assertTrue(db.queryForObject("SELECT updated_at>'2000-01-01T00:00:00Z'::timestamptz FROM commerce_provider_disputes WHERE id=?",Boolean.class,ids.get(0)));
    provider.disputeReads.clear();reconcileSource();
    assertEquals(8,provider.disputeReads.size());assertEquals(ids.get(8),provider.disputeReads.get(0));
    assertEquals("won",db.queryForObject("SELECT status FROM commerce_provider_disputes WHERE id=?",String.class,ids.get(8)));assertFalse(noProviderHold());
    provider.disputeStatus="won";provider.disputeReads.clear();reconcileSource();assertEquals(8,provider.disputeReads.size());assertTrue(noProviderHold());
  }
  @Test void unrelatedBoundedDisputeObservationsCannotResolveAFreshPendingReceipt() throws Exception {
    capturedPurchase();for(int n=0;n<8;n++)storedSourceDispute(n,"won");provider.disputeStatus="won";provider.blockDispute=true;
    var executor=Executors.newSingleThreadExecutor();var pending=executor.submit(()->{reconcileSource();return null;});
    try {
      assertTrue(provider.disputeEntered.await(2,TimeUnit.SECONDS));
      db.update("INSERT INTO commerce_webhook_events(event_id,payload_hash,event_kind,payment_id,dispute_id) VALUES('source_fresh_batch',?,'payment.dispute.created',?,'disp_sourcebatch99')",Support.hash("fresh authenticated receipt fixture"),provider.paymentId());
      provider.blockDispute=false;provider.releaseDispute.countDown();pending.get(5,TimeUnit.SECONDS);
      assertEquals(8,provider.disputeReads.size());assertFalse(provider.disputeReads.contains("disp_sourcebatch99"));assertFalse(noProviderHold());
      assertNull(db.queryForObject("SELECT processed_at FROM commerce_webhook_events WHERE event_id='source_fresh_batch'",java.sql.Timestamp.class));
      provider.disputeReads.clear();reconcileSource();assertEquals(8,provider.disputeReads.size());assertEquals("disp_sourcebatch99",provider.disputeReads.get(0));
      assertNotNull(db.queryForObject("SELECT processed_at FROM commerce_webhook_events WHERE event_id='source_fresh_batch'",java.sql.Timestamp.class));assertTrue(noProviderHold());
    } finally {provider.releaseDispute.countDown();executor.shutdownNow();}
  }
  @Test void realLiveEntitlementRejectsPendingAndUnmappedHistoricalDisputes() throws Exception {
    UUID id=UUID.randomUUID();repo.reserve(id,buyer,UUID.randomUUID(),repo.template(template,false),repo.version(template,version,false),CommerceProviderIntegrationTest.ACCOUNT,"live");
    repo.bind(id,"order_pendingfixture123","CREATED");repo.capture(id,"pay_pendingfixture123",0,"PROCESSED");assertTrue(repo.entitled(id,buyer));
    db.update("INSERT INTO commerce_webhook_events(event_id,payload_hash,event_kind,order_id,dispute_id) VALUES('pending-live',?,'payment.dispute.created','order_pendingfixture123','disp_pendingfixture123')",Support.hash("synthetic pending"));
    assertFalse(repo.entitled(id,buyer));
    mvc.perform(as(get("/api/v1/template-purchases/"+id+"/download"),"buyer")).andExpect(status().isForbidden());
    mvc.perform(body(post("/api/v1/admin/template-purchases/"+id+"/resolve"),"admin","{\"resolution\":\"RESUME\",\"reason\":\"An operator cannot override unresolved provider evidence.\"}")).andExpect(status().isConflict());
    db.update("UPDATE commerce_webhook_events SET processed_at=clock_timestamp()");assertTrue(repo.entitled(id,buyer));
    db.update("INSERT INTO commerce_webhook_events(event_id,payload_hash,event_kind) VALUES('legacy-unmapped',?,'payment.dispute.created')",Support.hash("legacy synthetic"));assertFalse(repo.entitled(id,buyer));
    db.update("UPDATE commerce_webhook_events SET processed_at=clock_timestamp()");assertTrue(repo.entitled(id,buyer));
  }
  @Test void firstLookupFailureRetainsProviderHoldAndMatchingReplayRecoversHistoricalReceipt() throws Exception {
    mvc.perform(order("buyer",key)).andExpect(status().isOk());provider.captured=true;
    mvc.perform(body(post("/api/v1/template-purchases/"+purchase()+"/verify"),"buyer",callback())).andExpect(status().isOk());assertTrue(noProviderHold());
    provider.failPayment=true;mvc.perform(webhook("first_source_lookup",disputeEvent())).andExpect(status().isBadGateway());assertFalse(noProviderHold());
    assertEquals(provider.paymentId(),db.queryForObject("SELECT payment_id FROM commerce_webhook_events WHERE event_id='first_source_lookup'",String.class));
    provider.failPayment=false;provider.disputeStatus="won";
    mvc.perform(webhook("first_source_lookup",disputeEvent())).andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true));assertTrue(noProviderHold());
    db.update("INSERT INTO commerce_webhook_events(event_id,payload_hash,event_kind) VALUES('old_source_dispute',?,'payment.dispute.created')",Support.hash(disputeEvent()));assertFalse(noProviderHold());
    mvc.perform(webhook("old_source_dispute",disputeEvent()+" ")).andExpect(status().isConflict());assertFalse(noProviderHold());
    mvc.perform(webhook("old_source_dispute",disputeEvent())).andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true));assertTrue(noProviderHold());
  }
  /** Synthetic retained immutable rows cover every listing/release state, without stored objects. */
  List<UUID> retainedSourceVersions(int total) {
    var listings=new ArrayList<UUID>();
    for(String state:List.of("DRAFT","DRAFT","ARCHIVED","SUSPENDED")) {
      UUID listing=UUID.randomUUID();
      repo.create(listing,seller,product,"retained-"+listing,"Retained source","A retained immutable source listing.","Historical source snapshots remain pinned to their original licenses.",10000,"One commercial end product; retain third-party notices.");
      db.update("UPDATE source_templates SET status=? WHERE id=?",state,listing);listings.add(listing);
    }
    var states=List.of("DRAFT","PENDING","CHANGES_REQUESTED","APPROVED","REJECTED","SUSPENDED");
    for(int n=0;n<total-1;n++) {
      UUID id=UUID.randomUUID(),listing=listings.get(n%listings.size());
      repo.addVersion(id,listing,"historic-"+n,"Historical immutable source release notes.","commerce/"+id+"/"+UUID.randomUUID()+".zip",new SourceArchive.Inspection("a".repeat(64),22,2,List.of("package.json")),"One commercial end product; retain third-party notices.");
      db.update("UPDATE source_versions SET status=? WHERE id=?",states.get(n%states.size()),id);
    }
    return listings;
  }
  MockHttpServletRequestBuilder retainedUpload(UUID listing,String release) {
    return multipart("/api/v1/me/templates/"+listing+"/versions")
        .file(new MockMultipartFile("file","source.zip","application/zip",zip))
        .param("version",release).param("releaseNotes","A complete immutable release with redistribution notices.").param("rightsConsent","true")
        .header("Origin","http://localhost:3000").header("X-Requested-With","getlancer").cookie(new Cookie("gl_session","seller"));
  }
  @Test void concurrentUploadsAcrossDraftListingsCommitOnlyOneRetainedSourceSlot() throws Exception {
    var listings=retainedSourceVersions(99);clearInvocations(storage);
    var objects=new ConcurrentHashMap<String,byte[]>();var uploaded=new CountDownLatch(2);
    when(storage.put(any(UUID.class),any(byte[].class),anyString())).thenAnswer(call->{
      String object="commerce/"+call.getArgument(0)+"/"+UUID.randomUUID()+".zip";
      objects.put(object,((byte[])call.getArgument(1)).clone());uploaded.countDown();
      assertTrue(uploaded.await(10,TimeUnit.SECONDS),"Storage I/O must occur after releasing the owner admission lock.");
      return object;
    });
    doAnswer(call->{objects.remove(call.getArgument(0));return null;}).when(storage).discardUnlinked(anyString());
    var pool=Executors.newFixedThreadPool(2);
    try {
      var one=pool.submit(()->mvc.perform(retainedUpload(listings.get(0),"race-one")).andReturn().getResponse());
      var two=pool.submit(()->mvc.perform(retainedUpload(listings.get(1),"race-two")).andReturn().getResponse());
      var responses=List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS));
      assertEquals(List.of(201,409),responses.stream().map(response->response.getStatus()).sorted().toList());
      var denied=responses.stream().filter(response->response.getStatus()==409).findFirst().orElseThrow();
      assertEquals("SOURCE_RETENTION_LIMIT",json.readTree(denied.getContentAsString()).path("error").path("code").asText());
      assertEquals(100,db.queryForObject("SELECT count(*) FROM source_versions v JOIN source_templates t ON t.id=v.template_id WHERE t.seller_id=?",Integer.class,seller));
      assertEquals(1,objects.size());verify(storage,times(2)).put(any(UUID.class),any(byte[].class),anyString());verify(storage).discardUnlinked(anyString());
    } finally {pool.shutdownNow();assertTrue(pool.awaitTermination(15,TimeUnit.SECONDS));}
  }
  @Test void historicalOverLimitSourceRemainsReadableAndLicensedWhileNewUploadsAreDenied() throws Exception {
    var listings=retainedSourceVersions(105);clearInvocations(storage);
    mvc.perform(retainedUpload(listings.get(0),"over-budget")).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SOURCE_RETENTION_LIMIT"));
    verify(storage,never()).put(any(UUID.class),any(byte[].class),anyString());
    assertEquals(100,repo.retainedVersions(seller,100),"The admission count itself is capped.");
    assertFalse(repo.ownerDetail(template,seller).isEmpty());
    UUID purchase=UUID.randomUUID();repo.reserve(purchase,buyer,UUID.randomUUID(),repo.template(template,false),repo.version(template,version,false),CommerceProviderIntegrationTest.ACCOUNT,"live");
    repo.bind(purchase,"order_retainedfixture123","CREATED");repo.capture(purchase,"pay_retainedfixture123",0,"PROCESSED");assertTrue(repo.entitled(purchase,buyer));
    assertArrayEquals(zip,mvc.perform(as(get("/api/v1/template-purchases/"+purchase+"/download"),"buyer")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    assertEquals(105,db.queryForObject("SELECT count(*) FROM source_versions v JOIN source_templates t ON t.id=v.template_id WHERE t.seller_id=?",Integer.class,seller));
  }
  @BeforeEach void prepare() throws Exception {
    provider.reset();reset(storage);db.execute("TRUNCATE users CASCADE");db.execute("TRUNCATE rate_buckets");db.execute("TRUNCATE commerce_webhook_events");
    buyer=user("buyer",false);seller=user("seller",true);outsider=user("outsider",false);admin=user("admin",false);db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')",admin);db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?",admin);
    product=UUID.randomUUID();key=UUID.randomUUID().toString();zip=zip();
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,approval_status,lifecycle_status,visibility,repository_url) VALUES(?,?,?,'Verified proof','Approved public proof','Complete working proof product','SAAS','CRM','React, Java','Built complete source','APPROVED','ACTIVE','PUBLIC','https://github.com/example/source')",product,seller,product.toString());
    db.update("INSERT INTO repository_verifications(product_id,repository_url,challenge,status,reviewed_at,reviewer_id) VALUES(?,'https://github.com/example/source','private-verification-challenge','VERIFIED',now(),?)",product,admin);
    db.update("INSERT INTO payment_accounts(id,builder_user_id,account_id,mode,provider_status,activation_confirmed,verified_by) VALUES(?,?,?,'test','created',true,?)",UUID.randomUUID(),seller,CommerceProviderIntegrationTest.ACCOUNT,admin);
    when(storage.put(any(UUID.class),any(byte[].class),anyString())).thenAnswer(i->"commerce/"+i.getArgument(0)+"/"+UUID.randomUUID()+".zip");when(storage.read(anyString(),anyString(),anyInt())).thenReturn(zip);
    String result=mvc.perform(body(post("/api/v1/me/templates"),"seller","{\"productId\":\""+product+"\",\"title\":\"Source portal\",\"summary\":\"Complete working source portal\",\"description\":\"A complete reviewed portal with setup instructions and third-party notices.\",\"priceMinor\":10000,\"licenseTerms\":\"Commercial use for one end product with modification; preserve third-party MIT notices.\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();var detail=json.readTree(result);template=UUID.fromString(detail.path("id").asText());slug=detail.path("slug").asText();version=upload("1.0.0");approve(version);
  }
  @Test void fullSellerCatalogReviewContractKeepsStorageAndChallengesPrivate() throws Exception {
    mvc.perform(get("/api/v1/templates?size=12")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].version").value("1.0.0")).andExpect(jsonPath("$.items[0].storage_key").doesNotExist());
    String publicJson=mvc.perform(get("/api/v1/templates/"+slug)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertFalse(publicJson.contains("storage_key"));assertFalse(publicJson.contains("sha256"));assertFalse(publicJson.contains("challenge"));
    mvc.perform(as(get("/api/v1/me/templates"),"seller")).andExpect(jsonPath("$.items[0].versions[0].sha256").isNotEmpty()).andExpect(jsonPath("$.items[0].versions[0].manifestFiles[0]").value("package.json"));
    mvc.perform(as(get("/api/v1/me/templates/"+template+"/versions/"+version+"/package"),"seller")).andExpect(status().isOk()).andExpect(header().string("Content-Type","application/zip")).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store"))).andExpect(header().string("X-Content-Type-Options","nosniff"));
    mvc.perform(as(get("/api/v1/me/templates/"+template+"/versions/"+version+"/package"),"outsider")).andExpect(status().isNotFound());mvc.perform(as(get("/api/v1/admin/templates"),"buyer")).andExpect(status().isForbidden());
    db.update("DELETE FROM user_roles WHERE user_id=? AND role='DEVELOPER'",buyer);mvc.perform(as(get("/api/v1/me/templates"),"buyer")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
  }
  @Test void privateSourceDownloadsAuditCurrentActorAndResourceWithoutPackageContents() throws Exception {
    String ownerPath="/api/v1/me/templates/"+template+"/versions/"+version+"/package";
    String adminPath="/api/v1/admin/templates/"+template+"/versions/"+version+"/package";
    mvc.perform(as(get(ownerPath),"seller")).andExpect(status().isOk());
    mvc.perform(as(get(adminPath),"admin")).andExpect(status().isOk());
    assertEquals(1,db.queryForObject("SELECT count(*) FROM security_audit_events WHERE actor_id=? AND event='PRIVATE_EXPORT' AND target=? AND result='SUCCESS'",Integer.class,seller,ownerPath));
    assertEquals(1,db.queryForObject("SELECT count(*) FROM security_audit_events WHERE actor_id=? AND event='PRIVATE_EXPORT' AND target=? AND result='SUCCESS'",Integer.class,admin,adminPath));
    db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?",admin);
    mvc.perform(as(get(adminPath),"admin")).andExpect(status().isForbidden());
    assertEquals(1,db.queryForObject("SELECT count(*) FROM security_audit_events WHERE actor_id=? AND event='PRIVATE_EXPORT' AND target=? AND result='SUCCESS'",Integer.class,admin,adminPath));
    UUID purchaseId=UUID.randomUUID();
    repo.reserve(purchaseId,buyer,UUID.randomUUID(),repo.template(template,false),repo.version(template,version,false),CommerceProviderIntegrationTest.ACCOUNT,"live");
    repo.bind(purchaseId,"order_exportfixture123","CREATED");repo.capture(purchaseId,"pay_exportfixture123",0,"PROCESSED");
    String buyerPath="/api/v1/template-purchases/"+purchaseId+"/download";
    mvc.perform(as(get(buyerPath),"buyer")).andExpect(status().isOk());
    assertEquals(1,db.queryForObject("SELECT count(*) FROM security_audit_events WHERE actor_id=? AND event='PRIVATE_EXPORT' AND target=? AND result='SUCCESS'",Integer.class,buyer,buyerPath));
    String metadata=json.writeValueAsString(db.queryForList("SELECT event,target,result,request_id FROM security_audit_events WHERE actor_id IN (?,?,?) AND event='PRIVATE_EXPORT'",seller,admin,buyer));
    assertFalse(metadata.contains("storage_key"));assertFalse(metadata.contains("Complete source"));assertFalse(metadata.contains("license_terms"));
  }
  @Test void submissionRequiresRightsMfaReviewAndCurrentRepositoryOwnership() throws Exception {
    UUID release=upload("2.0.0");String submit="/api/v1/me/templates/"+template+"/versions/"+release+"/submit",review="/api/v1/admin/templates/"+template+"/versions/"+release+"/review";
    mvc.perform(body(post(submit),"seller","{}")).andExpect(status().isBadRequest());mvc.perform(body(post(submit),"outsider","{\"rightsConsent\":true}")).andExpect(status().isNotFound());
    db.update("UPDATE products SET repository_url='https://github.com/example/changed' WHERE id=?",product);mvc.perform(body(post(submit),"seller","{\"rightsConsent\":true}")).andExpect(status().isConflict());db.update("UPDATE products SET repository_url='https://github.com/example/source' WHERE id=?",product);
    mvc.perform(body(post(submit),"seller","{\"rightsConsent\":true}")).andExpect(status().isOk());mvc.perform(body(post(review),"admin","{\"action\":\"APPROVE\",\"reason\":\"Reviewed source package.\"}")).andExpect(status().isBadRequest());db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?",admin);mvc.perform(body(post(review),"admin","{\"action\":\"APPROVE\",\"reason\":\"Reviewed source package.\",\"rightsReviewed\":true,\"packageReviewed\":true}")).andExpect(status().isForbidden());
  }
  @Test void reviewedReleaseAndPurchaseSnapshotsAreImmutableAndNewReleaseIndependent() throws Exception {
    mvc.perform(order("buyer",key)).andExpect(status().isOk());UUID purchase=purchase();assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE source_versions SET sha256=repeat('a',64) WHERE id=?",version));assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE template_purchases SET amount_minor=1 WHERE id=?",purchase));
    UUID old=version;UUID newer=upload("2.0.0");approve(newer);mvc.perform(get("/api/v1/templates/"+slug)).andExpect(jsonPath("$.version").value("2.0.0")).andExpect(jsonPath("$.versions.length()").value(2));mvc.perform(as(get("/api/v1/me/template-purchases"),"buyer")).andExpect(jsonPath("$.items[0].version").value("1.0.0")).andExpect(jsonPath("$.items[0].versionId").value(old.toString()));assertEquals(0,db.queryForObject("SELECT count(*) FROM commerce_ledger",Integer.class));
  }
  @Test void checkoutUsesServerPriceSellerVersionAndCommittedSingleReservation() throws Exception {
    mvc.perform(body(post("/api/v1/templates/"+template+"/orders"),"buyer","{\"versionId\":\""+version+"\",\"licenseConsent\":true,\"amountMinor\":1,\"accountId\":\"acc_attacker123\"}").header("Idempotency-Key",key)).andExpect(status().isBadRequest());
    assertEquals(0,provider.creates.get());
    mvc.perform(order("seller",key)).andExpect(status().isForbidden());mvc.perform(body(post("/api/v1/templates/"+template+"/orders"),"buyer","{\"versionId\":\""+version+"\"}").header("Idempotency-Key",key)).andExpect(status().isBadRequest());
    mvc.perform(order("buyer",key)).andExpect(status().isOk()).andExpect(jsonPath("$.amountMinor").value(10000)).andExpect(jsonPath("$.mode").value("test"));assertTrue(provider.reservationCommitted);assertEquals(10000,provider.lastBody.path("amount").longValue());assertEquals(CommerceProviderIntegrationTest.ACCOUNT,provider.lastBody.path("transfers").get(0).path("account").asText());mvc.perform(order("buyer",key)).andExpect(status().isOk());mvc.perform(order("buyer",UUID.randomUUID().toString())).andExpect(status().isOk());assertEquals(1,provider.creates.get());
    UUID other=upload("2.0.0");approve(other);UUID before=version;version=other;mvc.perform(order("buyer",key)).andExpect(status().isConflict());version=before;assertEquals(1,provider.creates.get());
  }
  @Test void pendingSuspendedOrArchivedSourcesAndIneligibleSellersCannotReceiveCheckout() throws Exception {
    UUID approved=version;version=upload("2.0.0");mvc.perform(order("buyer",key)).andExpect(status().isConflict());version=approved;
    db.update("UPDATE developer_profiles SET approval_status='DRAFT' WHERE user_id=?",seller);mvc.perform(order("buyer",key)).andExpect(status().isConflict());db.update("UPDATE developer_profiles SET approval_status='APPROVED' WHERE user_id=?",seller);db.update("UPDATE users SET account_status='SUSPENDED' WHERE id=?",seller);mvc.perform(order("buyer",key)).andExpect(status().isConflict());db.update("UPDATE users SET account_status='ACTIVE' WHERE id=?",seller);mvc.perform(body(post("/api/v1/me/templates/"+template+"/archive"),"seller","{}")).andExpect(status().isOk());mvc.perform(order("buyer",key)).andExpect(status().isConflict());mvc.perform(get("/api/v1/templates/"+slug)).andExpect(status().isNotFound());assertEquals(0,provider.creates.get());
  }
  @Test void signatureAuthorityPartyAndTestModeGuardsProtectEntitlements() throws Exception {
    mvc.perform(order("buyer",key)).andExpect(status().isOk());UUID id=purchase();String path="/api/v1/template-purchases/"+id+"/verify";mvc.perform(body(post(path),"outsider",callback())).andExpect(status().isNotFound());mvc.perform(body(post(path),"buyer","{\"razorpay_payment_id\":\""+provider.paymentId()+"\",\"razorpay_signature\":\""+"0".repeat(64)+"\"}")).andExpect(status().isBadRequest());provider.captured=true;provider.wrongAmount=true;mvc.perform(body(post(path),"buyer",callback())).andExpect(status().isConflict());provider.wrongAmount=false;for(int n=0;n<2;n++) mvc.perform(body(post(path),"buyer",callback())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CAPTURED")).andExpect(jsonPath("$.downloadAvailable").value(false));assertEquals(1,db.queryForObject("SELECT count(*) FROM commerce_ledger WHERE kind='CAPTURE'",Integer.class));mvc.perform(as(get("/api/v1/template-purchases/"+id+"/download"),"buyer")).andExpect(status().isForbidden());mvc.perform(as(get("/api/v1/template-purchases/"+id+"/download"),"outsider")).andExpect(status().isNotFound());assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE commerce_ledger SET amount_minor=1"));
  }
  @Test void exactSignedBoundedWebhookDedupAndMonotonicRefundsHoldDownloads() throws Exception {
    mvc.perform(order("buyer",key)).andExpect(status().isOk());provider.captured=true;String raw=event("payment.captured");mvc.perform(webhook("capture",raw)).andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(false));mvc.perform(webhook("capture",raw)).andExpect(jsonPath("$.duplicate").value(true));mvc.perform(webhook("capture",raw+" ")).andExpect(status().isConflict());mvc.perform(post("/api/v1/commerce/razorpay/webhook").content("invalid json").header("x-razorpay-event-id","bad").header("X-Razorpay-Signature","0".repeat(64))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_WEBHOOK_SIGNATURE"));
    provider.refunded=3000;mvc.perform(webhook("refund",event("payment.captured"))).andExpect(status().isOk());provider.refunded=1000;mvc.perform(webhook("older",event("payment.authorized"))).andExpect(status().isOk());assertEquals(3000L,db.queryForObject("SELECT refunded_minor FROM template_purchases",Long.class));provider.refunded=10000;mvc.perform(webhook("full",event("payment.captured"))).andExpect(status().isOk());assertEquals("REFUNDED",db.queryForObject("SELECT status FROM template_purchases",String.class));assertEquals(10000L,db.queryForObject("SELECT sum(amount_minor) FROM commerce_ledger WHERE kind='REFUND'",Long.class));mvc.perform(body(post("/api/v1/admin/template-purchases/"+purchase()+"/resolve"),"admin","{\"resolution\":\"RESUME\",\"reason\":\"Operator review cannot remove a confirmed refund.\"}")).andExpect(status().isConflict());
  }
  @Test void uncertainCreateCannotRetryBeforeMfaReceiptPayeeRecovery() throws Exception {
    provider.uncertain=true;mvc.perform(order("buyer",key)).andExpect(status().isBadGateway());UUID id=purchase();mvc.perform(order("buyer",key)).andExpect(status().isConflict());mvc.perform(order("buyer",UUID.randomUUID().toString())).andExpect(status().isConflict());assertEquals(1,provider.creates.get());String path="/api/v1/admin/template-purchases/"+id+"/bind-order",b="{\"orderId\":\""+provider.orderId()+"\"}";mvc.perform(body(post(path),"buyer",b)).andExpect(status().isForbidden());db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?",admin);mvc.perform(body(post(path),"admin",b)).andExpect(status().isForbidden());db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?",admin);provider.wrongPayee=true;mvc.perform(body(post(path),"admin",b)).andExpect(status().isConflict());provider.wrongPayee=false;provider.captured=true;mvc.perform(body(post(path),"admin",b)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CAPTURED"));assertEquals(1,provider.creates.get());
  }
  @Test void concurrentDifferentKeysStillCreateExactlyOneProviderOrder() throws Exception {
    provider.blocked=true;var executor=Executors.newFixedThreadPool(2);try {var first=executor.submit(()->mvc.perform(order("buyer",key)).andReturn().getResponse().getStatus());assertTrue(provider.entered.await(3,TimeUnit.SECONDS));var second=executor.submit(()->mvc.perform(order("buyer",UUID.randomUUID().toString())).andReturn().getResponse().getStatus());assertEquals(409,second.get(3,TimeUnit.SECONDS));provider.release.countDown();assertEquals(200,first.get(5,TimeUnit.SECONDS));assertEquals(1,provider.creates.get());}finally {provider.release.countDown();executor.shutdownNow();}
  }
  @Test void authoritativeProviderDisputesSurviveLocalResumeAndOnlyVerifiedWinClearsHold() throws Exception {
    mvc.perform(order("buyer",key)).andExpect(status().isOk());provider.captured=true;String event="{\"event\":\"payment.dispute.created\",\"payload\":{\"dispute\":{\"entity\":{\"id\":\"disp_sourcefixture123\",\"payment_id\":\""+provider.paymentId()+"\"}}}}";mvc.perform(webhook("dispute",event)).andExpect(status().isOk());UUID id=purchase();mvc.perform(body(post("/api/v1/template-purchases/"+id+"/reconcile"),"buyer","{}")).andExpect(jsonPath("$.status").value("DISPUTED"));mvc.perform(body(post("/api/v1/admin/template-purchases/"+id+"/resolve"),"admin","{\"resolution\":\"RESUME\",\"reason\":\"Operator reviewed local support concern.\"}")).andExpect(status().isConflict());provider.disputeStatus="lost";mvc.perform(body(post("/api/v1/admin/template-purchases/"+id+"/reconcile"),"admin","{}")).andExpect(jsonPath("$.status").value("DISPUTED"));provider.disputeStatus="won";mvc.perform(body(post("/api/v1/admin/template-purchases/"+id+"/reconcile"),"admin","{}")).andExpect(jsonPath("$.status").value("CAPTURED"));
  }
  @Test void localBuyerDisputeRevocationAndMfaResolutionAreAudited() throws Exception {
    mvc.perform(order("buyer",key)).andExpect(status().isOk());provider.captured=true;mvc.perform(body(post("/api/v1/template-purchases/"+purchase()+"/verify"),"buyer",callback())).andExpect(status().isOk());UUID id=purchase();String path="/api/v1/template-purchases/"+id+"/disputes";mvc.perform(body(post(path),"outsider","{\"reason\":\"Source package is missing an advertised required feature.\"}")).andExpect(status().isNotFound());mvc.perform(body(post(path),"buyer","{\"reason\":\"Source package is missing an advertised required feature.\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISPUTED"));mvc.perform(body(post("/api/v1/admin/template-purchases/"+id+"/resolve"),"admin","{\"resolution\":\"REVOKE\",\"reason\":\"Source refund handled through provider dashboard; source access revoked.\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.downloadAvailable").value(false));assertTrue(db.queryForObject("SELECT entitlement_revoked FROM template_purchases WHERE id=?",Boolean.class,id));assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("DELETE FROM commerce_audit"));
  }
  @Test void liveEntitlementPinnedReleaseSurvivesArchiveButNotSuspensionOrRefund() throws Exception {
    // Disposable database financial fixture tests the actual Java entitlement gate. It does not impersonate a live provider or production capture.
    UUID id=UUID.randomUUID();repo.reserve(id,buyer,UUID.randomUUID(),repo.template(template,false),repo.version(template,version,false),CommerceProviderIntegrationTest.ACCOUNT,"live");repo.bind(id,"order_livefixture123","CREATED");repo.capture(id,"pay_livefixture123",0,"PROCESSED");assertTrue(repo.entitled(id,buyer));mvc.perform(as(get("/api/v1/template-purchases/"+id+"/download"),"buyer")).andExpect(status().isOk());mvc.perform(body(post("/api/v1/me/templates/"+template+"/archive"),"seller","{}")).andExpect(status().isOk());mvc.perform(as(get("/api/v1/template-purchases/"+id+"/download"),"buyer")).andExpect(status().isOk());db.update("UPDATE users SET account_status='SUSPENDED' WHERE id=?",seller);mvc.perform(as(get("/api/v1/template-purchases/"+id+"/download"),"buyer")).andExpect(status().isForbidden());db.update("UPDATE users SET account_status='ACTIVE' WHERE id=?",seller);repo.capture(id,"pay_livefixture123",1,"PROCESSED");mvc.perform(as(get("/api/v1/template-purchases/"+id+"/download"),"buyer")).andExpect(status().isForbidden());
  }
  @Test void malformedAndNonconsentedUploadsNeverReachStorageAndDuplicateReleaseCompensates() throws Exception {
    clearInvocations(storage);var invalid=multipart("/api/v1/me/templates/"+template+"/versions").file(new MockMultipartFile("file","bad.zip","application/zip","not a ZIP".getBytes())).param("version","2.0.0").param("releaseNotes","Complete release notes").param("rightsConsent","true").header("Origin","http://localhost:3000").header("X-Requested-With","getlancer").cookie(new Cookie("gl_session","seller"));mvc.perform(invalid).andExpect(status().isBadRequest());verify(storage,never()).put(any(),any(),anyString());
    var noConsent=multipart("/api/v1/me/templates/"+template+"/versions").file(new MockMultipartFile("file","source.zip","application/zip",zip)).param("version","2.0.0").param("releaseNotes","Complete release notes").param("rightsConsent","false").header("Origin","http://localhost:3000").header("X-Requested-With","getlancer").cookie(new Cookie("gl_session","seller"));mvc.perform(noConsent).andExpect(status().isBadRequest());verify(storage,never()).put(any(),any(),anyString());
    var duplicate=multipart("/api/v1/me/templates/"+template+"/versions").file(new MockMultipartFile("file","source.zip","application/zip",zip)).param("version","1.0.0").param("releaseNotes","Complete release notes").param("rightsConsent","true").header("Origin","http://localhost:3000").header("X-Requested-With","getlancer").cookie(new Cookie("gl_session","seller"));mvc.perform(duplicate).andExpect(status().isConflict());assertEquals(1,db.queryForObject("SELECT count(*) FROM source_versions",Integer.class));assertTrue(mockingDetails(storage).getInvocations().stream().anyMatch(i->i.getMethod().getName().equals("discardUnlinked")));
  }
  @Test void lostCommitAcknowledgementKeepsTheCommittedArchiveAndExistingPurchaseSnapshot() throws Exception {
    UUID purchase=UUID.randomUUID();repo.reserve(purchase,buyer,UUID.randomUUID(),repo.template(template,false),repo.version(template,version,false),CommerceProviderIntegrationTest.ACCOUNT,"live");repo.bind(purchase,"order_archiveack123","CREATED");repo.capture(purchase,"pay_archiveack123",0,"PROCESSED");
    var paidSnapshot=db.queryForMap("SELECT p.version_id,v.storage_key,p.sha256,p.license_terms FROM template_purchases p JOIN source_versions v ON v.id=p.version_id WHERE p.id=?",purchase);
    String paidKey=Objects.toString(paidSnapshot.get("storage_key"));var objects=new HashMap<String,byte[]>();objects.put(paidKey,zip.clone());clearInvocations(storage);
    when(storage.put(any(UUID.class),any(byte[].class),anyString())).thenAnswer(call->{String path="commerce/"+call.getArgument(0)+"/"+UUID.randomUUID()+".zip";objects.put(path,((byte[])call.getArgument(1)).clone());return path;});
    when(storage.read(anyString(),anyString(),anyInt())).thenAnswer(call->{byte[] bytes=objects.get(call.getArgument(0));if(bytes==null)throw new IllegalStateException("Archive was removed");return bytes.clone();});
    doAnswer(call->{objects.remove(call.getArgument(0));return null;}).when(storage).discardUnlinked(anyString());
    var delegate=new org.springframework.jdbc.datasource.DataSourceTransactionManager(Objects.requireNonNull(db.getDataSource()));var injected=new java.util.concurrent.atomic.AtomicBoolean();
    org.springframework.transaction.PlatformTransactionManager uncertain=new org.springframework.transaction.PlatformTransactionManager(){
      public org.springframework.transaction.TransactionStatus getTransaction(org.springframework.transaction.TransactionDefinition definition){return delegate.getTransaction(definition);}
      public void rollback(org.springframework.transaction.TransactionStatus status){delegate.rollback(status);}
      public void commit(org.springframework.transaction.TransactionStatus status){delegate.commit(status);if(db.queryForObject("SELECT count(*) FROM source_versions WHERE template_id=? AND version='2.0.0'",Integer.class,template)>0&&injected.compareAndSet(false,true))throw new org.springframework.transaction.TransactionSystemException("Synthetic lost COMMIT acknowledgement");}
    };
    var request=new org.springframework.mock.web.MockHttpServletRequest();request.setCookies(new Cookie("gl_session","seller"));var service=new CommerceService(repo,security,storage,uncertain,publishing);
    assertThrows(org.springframework.transaction.TransactionSystemException.class,()->service.upload(template,new MockMultipartFile("file","source.zip","application/zip",zip),"2.0.0","Complete immutable release notes.",true,request));
    var committed=db.queryForMap("SELECT storage_key,sha256,size_bytes FROM source_versions WHERE template_id=? AND version='2.0.0'",template);
    assertEquals(2,objects.size());assertArrayEquals(zip,objects.get(committed.get("storage_key")));verify(storage,never()).discardUnlinked(anyString());
    assertEquals(paidSnapshot,db.queryForMap("SELECT p.version_id,v.storage_key,p.sha256,p.license_terms FROM template_purchases p JOIN source_versions v ON v.id=p.version_id WHERE p.id=?",purchase));
    mvc.perform(as(get("/api/v1/template-purchases/"+purchase+"/download"),"buyer")).andExpect(status().isOk()).andExpect(content().bytes(zip));
  }
  @Test void sellerRevokedDuringPrivateStorageUploadRollsBackAndCompensatesObject() throws Exception {
    clearInvocations(storage);when(storage.put(any(UUID.class),any(byte[].class),anyString())).thenAnswer(i->{db.update("UPDATE users SET account_status='SUSPENDED' WHERE id=?",seller);return "commerce/"+i.getArgument(0)+"/"+UUID.randomUUID()+".zip";});var request=multipart("/api/v1/me/templates/"+template+"/versions").file(new MockMultipartFile("file","source.zip","application/zip",zip)).param("version","2.0.0").param("releaseNotes","Complete release notes").param("rightsConsent","true").header("Origin","http://localhost:3000").header("X-Requested-With","getlancer").cookie(new Cookie("gl_session","seller"));mvc.perform(request).andExpect(status().isUnauthorized());assertEquals(1,db.queryForObject("SELECT count(*) FROM source_versions",Integer.class));assertTrue(mockingDetails(storage).getInvocations().stream().anyMatch(i->i.getMethod().getName().equals("discardUnlinked")));
  }
  @Test void currentSourceProofAndSellerApprovalAreRequiredForExistingLiveDownloads() throws Exception {
    UUID id=UUID.randomUUID();repo.reserve(id,buyer,UUID.randomUUID(),repo.template(template,false),repo.version(template,version,false),CommerceProviderIntegrationTest.ACCOUNT,"live");repo.bind(id,"order_eligibility123","CREATED");repo.capture(id,"pay_eligibility123",0,"PROCESSED");assertTrue(repo.entitled(id,buyer));db.update("UPDATE repository_verifications SET status='REJECTED' WHERE product_id=?",product);assertFalse(repo.entitled(id,buyer));db.update("UPDATE repository_verifications SET status='VERIFIED' WHERE product_id=?",product);db.update("UPDATE products SET visibility='PRIVATE_CASE_STUDY' WHERE id=?",product);assertFalse(repo.entitled(id,buyer));db.update("UPDATE products SET visibility='PUBLIC' WHERE id=?",product);db.update("UPDATE developer_profiles SET approval_status='SUSPENDED' WHERE user_id=?",seller);assertFalse(repo.entitled(id,buyer));
  }
  @Test void chunkedWebhookIsBoundedBeforeSignatureAndJsonAndUnsignedSiblingCannotBypassOrigin() throws Exception {
    HttpServletRequest r=mock(HttpServletRequest.class);var input=new ByteArrayInputStream(new byte[65537]);when(r.getInputStream()).thenReturn(new ServletInputStream(){public int read(){return input.read();}public boolean isFinished(){return input.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener l){}});ApiError e=assertThrows(ApiError.class,()->payments.webhook(r));assertEquals(413,e.status);verify(r,never()).getHeader("X-Razorpay-Signature");mvc.perform(post("/api/v1/commerce/razorpay/webhook-extra").content("{}")).andExpect(status().isForbidden());
  }
}
