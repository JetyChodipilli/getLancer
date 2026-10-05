package com.getlancer.integration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.*;
import com.getlancer.payments.RazorpayClient;
import com.getlancer.shared.*;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
@org.springframework.test.context.ContextConfiguration(initializers=TestDatabaseGuard.class)
@SpringBootTest(properties={
  "app.environment=local","app.jobs-enabled=false","app.admin-email=admin@example.test","app.admin-password=","app.admin-totp=","spring.config.import=",  "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}","spring.datasource.username=${TEST_DB_USERNAME:postgres}","spring.datasource.password=${TEST_DB_PASSWORD:}",  "spring.datasource.hikari.schema=getlancer_test","spring.flyway.default-schema=getlancer_test","spring.flyway.schemas=getlancer_test","app.origin=http://localhost:3000","app.secure-cookie=false","app.storage.access-key=","app.storage.secret-key="
}
) @AutoConfigureMockMvc class ComponentsIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockBean RazorpayClient provider;
  UUID builder,other,admin,product;
  JsonNode order;
  AtomicInteger creates=new AtomicInteger();
  boolean captured,wrongAmount;
  long refunded;
  UUID user(String name,boolean approved){
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",id,name+"@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT'),(?,'DEVELOPER')",id,id);
    db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,?,?)",id,name,name,approved?"APPROVED":"DRAFT");
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",Support.hash(name),id);
    return id;
  }
  MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder r,String actor){
    return r.cookie(new Cookie("gl_session",actor));
  }
  MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder r,String actor,Object b)throws Exception{
    return as(r,actor).header("Origin","http://localhost:3000").header("X-Requested-With","getlancer").contentType("application/json").content(json.writeValueAsString(b));
  }
  JsonNode response(MockHttpServletRequestBuilder r)throws Exception{
    return json.readTree(mvc.perform(r).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }
  void price(long amount)throws Exception{
    response(body(put("/api/v1/admin/component-slot-pricing"),"admin",Map.of("amountMinor",amount,"enabled",true)));
  }
  MockHttpServletRequestBuilder buy(String actor,String key,long amount)throws Exception{
    return body(post("/api/v1/me/component-slot-purchases"),actor,Map.of("amountMinor",amount,"purchaseConsent",true)).header("Idempotency-Key",key);
  }
  UUID draft()throws Exception{
    var c=response(body(post("/api/v1/me/components"),"builder",Map.of("recipeSlug","portfolio-card","title","Original recipe selection","summary","An original recipe with honest attribution.","contribution","Selected the original MIT recipe for a specific portfolio use case.","rightsConsent",true)));
    return UUID.fromString(c.path("id").asText());
  }
  void submit(UUID id)throws Exception{
    response(body(post("/api/v1/me/components/"+id+"/submit"),"builder",Map.of()));
  }
  MockHttpServletRequestBuilder approve(UUID id)throws Exception{
    return body(post("/api/v1/admin/components/"+id+"/review"),"admin",Map.of("revision",db.queryForObject("SELECT revision FROM component_entries WHERE id=?",Long.class,id),"decision","APPROVE","reason","Reviewed original curated recipe and explicit attribution."));
  }
  @AfterEach void cleanup(){db.execute("TRUNCATE users CASCADE");}
  @BeforeEach void setup(){
    reset(provider);
    db.execute("TRUNCATE users CASCADE");
    db.execute("TRUNCATE rate_buckets,component_slot_events");
    db.update("UPDATE component_slot_pricing SET amount_minor=NULL,enabled=false");
    builder=user("builder",true);
    db.update("INSERT INTO showcase_entitlements(user_id) VALUES(?)",builder);
    other=user("other",true);
    admin=user("admin",false);
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')",admin);
    db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?",admin);
    product=UUID.randomUUID();
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,approval_status,lifecycle_status,visibility) VALUES(?,?,?,'College original','An approved project','An original approved project with evidence','LEARNING','CRM','React','Built the project','APPROVED','ACTIVE','PUBLIC')",product,builder,product.toString());
    captured=false;
    wrongAmount=false;
    refunded=0;
    creates.set(0);
    order=null;
    when(provider.mode()).thenReturn("live");
    when(provider.keyId()).thenReturn("rzp_live_fixtureonly123");
    when(provider.configuration()).thenReturn(Map.of("enabled",true,"mode","live","reason",""));
    when(provider.checkoutSignature(anyString(),anyString(),anyString())).thenAnswer(i->"a".repeat(64).equals(i.getArgument(2)));
    when(provider.maintenanceWebhookSignature(any(byte[].class),anyString())).thenAnswer(i->"a".repeat(64).equals(i.getArgument(1)));
    when(provider.createPlatformOrder(any(UUID.class),anyLong())).thenAnswer(i->{
      creates.incrementAndGet();UUID id=i.getArgument(0);long value=i.getArgument(1);assertEquals(1,db.queryForObject("SELECT count(*) FROM component_slot_purchases WHERE id=? AND status='CREATING'",Integer.class,id));order=json.valueToTree(Map.of("id","order_component123","receipt",id.toString(),"amount",value,"currency","INR","partial_payment",false));return order;
    }
    );
    when(provider.order(anyString())).thenAnswer(i->order);
    when(provider.orderPayments(anyString())).thenAnswer(i->json.valueToTree(Map.of("items",captured?List.of(Map.of("id","pay_component123","status","captured")):List.of())));
    when(provider.payment(anyString())).thenAnswer(i->json.valueToTree(Map.of("id","pay_component123","order_id","order_component123","amount",order.path("amount").longValue()-(wrongAmount?1:0),"currency","INR","status",captured?"captured":"authorized","captured",captured,"amount_refunded",refunded)));
  }
  @Test void publicCuratedSourcesStayFreeAndBackendLabAvailabilityIsHonest()throws Exception{
    mvc.perform(get("/api/v1/components")).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(6)).andExpect(jsonPath("$.items[0].files").doesNotExist());
    mvc.perform(get("/api/v1/components/quiet-sign-in")).andExpect(status().isOk()).andExpect(jsonPath("$.license").value("MIT")).andExpect(jsonPath("$.files['index.html']").isString());
    mvc.perform(get("/api/v1/components?kind=BACKEND")).andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.executionMode").value("Source only"));
    mvc.perform(get("/api/v1/components?page=-1")).andExpect(status().isBadRequest());
  }
  @Test void exactlyThreeFreeSlotsAreAtomicAndIndependentOfFullProjectCapacity()throws Exception{
    for(int n=0;n<3;n++){
      UUID id=draft();
      submit(id);
      response(approve(id));
    }
    UUID fourth=draft();
    submit(fourth);
    mvc.perform(approve(fourth)).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("COMPONENT_CAPACITY_REACHED"));
    var own=response(as(get("/api/v1/me/components"),"builder"));
    assertEquals(3,own.path("capacity").path("free").asInt());
    assertEquals(3,own.path("capacity").path("used").asInt());
    assertEquals(3,db.queryForObject("SELECT active_slot_limit FROM showcase_entitlements WHERE user_id=?",Integer.class,builder));
    UUID oldest=db.queryForObject("SELECT id FROM component_entries WHERE status='ACTIVE' ORDER BY published_at,id LIMIT 1",UUID.class);
    response(body(post("/api/v1/me/components/"+oldest+"/archive"),"builder",Map.of()));
    response(approve(fourth));
    mvc.perform(get("/api/v1/components/remix-"+oldest)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
  }
  @Test void publicationOwnershipProfileAndAdminMfaAreCurrent()throws Exception{
    UUID id=draft();
    mvc.perform(body(post("/api/v1/me/components/"+id+"/submit"),"other",Map.of())).andExpect(status().isNotFound());
    submit(id);
    mvc.perform(body(post("/api/v1/admin/components/"+id+"/review"),"builder",Map.of("decision","APPROVE","reason","A reason cannot manufacture administrator authority."))).andExpect(status().isForbidden());
    db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?",admin);
    mvc.perform(approve(id)).andExpect(status().isForbidden());
    db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?",admin);
    response(approve(id));
    db.update("UPDATE users SET account_status='SUSPENDED' WHERE id=?",builder);
    mvc.perform(get("/api/v1/components/remix-"+id)).andExpect(status().isNotFound());
  }
  @Test void priceIsUnsetUntilMfaAdminConfiguresItAndStalePricesCannotCharge()throws Exception{
    mvc.perform(buy("builder",UUID.randomUUID().toString(),10000)).andExpect(status().isConflict());
    mvc.perform(body(put("/api/v1/admin/component-slot-pricing"),"builder",Map.of("amountMinor",10000,"enabled",true))).andExpect(status().isForbidden());
    mvc.perform(body(put("/api/v1/admin/component-slot-pricing"),"admin",Map.of("amountMinor",0,"enabled",true))).andExpect(status().isBadRequest());
    price(10000);
    mvc.perform(buy("builder",UUID.randomUUID().toString(),9999)).andExpect(status().isConflict());
    assertEquals(0,creates.get());
  }
  @Test void committedFrozenReservationDuplicateKeysAndNewPricesNeverDuplicateACharge()throws Exception{
    price(10000);
    String key=UUID.randomUUID().toString();
    var first=response(buy("builder",key,10000));
    price(20000);
    var again=response(buy("builder",key,20000));
    var different=response(buy("builder",UUID.randomUUID().toString(),20000));
    assertEquals(first.path("id").asText(),again.path("id").asText());
    assertEquals(first.path("id").asText(),different.path("id").asText());
    assertEquals(10000,again.path("amountMinor").asInt());
    assertEquals(1,creates.get());
    assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE component_slot_purchases SET amount_minor=15000"));
  }
  @Test void callbackCannotGrantWithoutCaptureOrOwnerSignatureAndTestModeNeverAddsLiveCapacity()throws Exception{
    price(10000);
    var p=response(buy("builder",UUID.randomUUID().toString(),10000));
    String id=p.path("id").asText(),path="/api/v1/me/component-slot-purchases/"+id+"/verify";
    var callback=Map.of("razorpay_payment_id","pay_component123","razorpay_signature","a".repeat(64));
    mvc.perform(body(post(path),"other",callback)).andExpect(status().isNotFound());
    mvc.perform(body(post(path),"builder",Map.of("razorpay_payment_id","pay_component123","razorpay_signature","0".repeat(64)))).andExpect(status().isBadRequest());
    var uncaptured=response(body(post(path),"builder",callback));
    assertFalse(uncaptured.path("grantsSlot").asBoolean());
    captured=true;
    wrongAmount=true;
    mvc.perform(body(post(path),"builder",callback)).andExpect(status().isConflict());
    wrongAmount=false;
    for(int n=0;n<2;n++)assertTrue(response(body(post(path),"builder",callback)).path("grantsSlot").asBoolean());
    assertEquals(1,db.queryForObject("SELECT count(*) FROM component_slot_ledger WHERE kind='CAPTURE'",Integer.class));
    assertEquals(4,response(as(get("/api/v1/me/components"),"builder")).path("capacity").path("limit").asInt());
    db.execute("TRUNCATE component_slot_purchases CASCADE");
    when(provider.mode()).thenReturn("test");
    when(provider.configuration()).thenReturn(Map.of("enabled",true,"mode","test","reason",""));
    var test=response(buy("builder",UUID.randomUUID().toString(),10000));
    assertFalse(response(body(post("/api/v1/me/component-slot-purchases/"+test.path("id").asText()+"/reconcile"),"builder",Map.of())).path("grantsSlot").asBoolean());
    assertEquals(3,response(as(get("/api/v1/me/components"),"builder")).path("capacity").path("limit").asInt());
  }
  @Test void monotonicRefundRemovesCapacityArchivesExcessAndPreservesPublishedFreeSource()throws Exception{
    price(10000);
    String id=response(buy("builder",UUID.randomUUID().toString(),10000)).path("id").asText();
    captured=true;
    String path="/api/v1/me/component-slot-purchases/"+id+"/reconcile";
    response(body(post(path),"builder",Map.of()));
    UUID newest=null;
    for(int n=0;n<4;n++){
      newest=draft();
      submit(newest);
      response(approve(newest));
    }
    refunded=2000;
    response(body(post(path),"builder",Map.of()));
    refunded=1000;
    response(body(post(path),"builder",Map.of()));
    assertEquals(2000L,db.queryForObject("SELECT refunded_minor FROM component_slot_purchases",Long.class));
    assertEquals(3,db.queryForObject("SELECT count(*) FROM component_entries WHERE status='ACTIVE'",Integer.class));
    mvc.perform(get("/api/v1/components/remix-"+newest)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED")).andExpect(jsonPath("$.files.LICENSE").isString());
    assertEquals(2000L,db.queryForObject("SELECT sum(amount_minor) FROM component_slot_ledger WHERE kind='REFUND'",Long.class));
    assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("DELETE FROM component_slot_ledger"));
  }
  @Test void unknownCreationBlocksFreshKeysUntilReceiptVerifiedMfaRecovery()throws Exception{
    price(10000);
    when(provider.createPlatformOrder(any(UUID.class),anyLong())).thenAnswer(i->{
      creates.incrementAndGet();order=json.valueToTree(Map.of("id","order_component123","receipt",i.getArgument(0).toString(),"amount",10000,"currency","INR"));throw new ApiError(409,"PROVIDER_MISMATCH","Uncertain provider response.");
    }
    );
    String key=UUID.randomUUID().toString();
    mvc.perform(buy("builder",key,10000)).andExpect(status().isConflict());
    mvc.perform(buy("builder",UUID.randomUUID().toString(),10000)).andExpect(status().isConflict());
    assertEquals(1,creates.get());
    UUID id=db.queryForObject("SELECT id FROM component_slot_purchases",UUID.class);
    var bind=body(post("/api/v1/admin/component-slot-purchases/"+id+"/bind-order"),"admin",Map.of("orderId","order_component123"));
    captured=true;
    assertTrue(response(bind).path("grantsSlot").asBoolean());
    assertEquals(1,creates.get());
  }
  @Test void concurrentDifferentKeysReserveOnlyOneExternalOrder()throws Exception{
    price(10000);
    CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
    when(provider.createPlatformOrder(any(UUID.class),anyLong())).thenAnswer(i->{
      creates.incrementAndGet();order=json.valueToTree(Map.of("id","order_component123","receipt",i.getArgument(0).toString(),"amount",10000,"currency","INR"));entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return order;
    }
    );
    var first=buy("builder",UUID.randomUUID().toString(),10000);
    var second=buy("builder",UUID.randomUUID().toString(),10000);
    var executor=Executors.newFixedThreadPool(2);
    try{
      var pending=executor.submit(()->mvc.perform(first).andReturn().getResponse().getStatus());
      assertTrue(entered.await(3,TimeUnit.SECONDS));
      assertEquals(409,mvc.perform(second).andReturn().getResponse().getStatus());
      release.countDown();
      assertEquals(200,pending.get(5,TimeUnit.SECONDS));
      assertEquals(1,creates.get());
    }
    finally{
      release.countDown();
      executor.shutdownNow();
    }
  }
  @Test void signedRawWebhookDedupDisputeAndMalformedSignaturesProtectCapacity()throws Exception{
    price(10000);
    String id=response(buy("builder",UUID.randomUUID().toString(),10000)).path("id").asText();
    captured=true;
    String raw="{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_component123\",\"order_id\":\"order_component123\"}}}}";
    var hook=post("/api/v1/components/razorpay/webhook").contentType("application/json").content(raw).header("x-razorpay-event-id","capture").header("X-Razorpay-Signature","a".repeat(64));
    response(hook);
    assertTrue(response(hook).path("duplicate").asBoolean());
    mvc.perform(post("/api/v1/components/razorpay/webhook").contentType("application/json").content(raw+" ").header("x-razorpay-event-id","capture").header("X-Razorpay-Signature","a".repeat(64))).andExpect(status().isConflict());
    mvc.perform(post("/api/v1/components/razorpay/webhook").content(raw).header("X-Razorpay-Signature","0".repeat(64))).andExpect(status().isBadRequest());
    when(provider.dispute(anyString())).thenReturn(json.valueToTree(Map.of("id","disp_component123","payment_id","pay_component123","currency","INR","amount",10000,"amount_deducted",0,"status","open")));
    String dispute="{\"event\":\"payment.dispute.created\",\"payload\":{\"dispute\":{\"entity\":{\"id\":\"disp_component123\",\"payment_id\":\"pay_component123\"}}}}";
    response(post("/api/v1/components/razorpay/webhook").contentType("application/json").content(dispute).header("x-razorpay-event-id","dispute").header("X-Razorpay-Signature","a".repeat(64)));
    assertEquals(3,response(as(get("/api/v1/me/components"),"builder")).path("capacity").path("limit").asInt());
    assertEquals("DISPUTED",response(body(post("/api/v1/me/component-slot-purchases/"+id+"/reconcile"),"builder",Map.of())).path("status").asText());
  }
  @Test void collegeMetadataRetainsProjectIdentityPrivacyConsentAndIndependentReview()throws Exception{
    Map<String,Object> b=new LinkedHashMap<>(Map.of("category","AI_ML","language","Python / PyTorch","problem","Classify a tiny synthetic dataset with reproducible evidence.","outcome","A documented evaluation with clear limitations.","prerequisites","Python fundamentals and a local CPU runtime.","contribution","I implemented the data preparation and evaluation modules.","institution","Private college","academicYear","2026","branch","CSE","rightsConsent",true));
    mvc.perform(body(put("/api/v1/me/college-projects/"+product),"other",b)).andExpect(status().isNotFound());
    response(body(put("/api/v1/me/college-projects/"+product),"builder",b));
    mvc.perform(get("/api/v1/college-projects/"+product)).andExpect(status().isNotFound());
    response(body(post("/api/v1/admin/college-projects/"+product+"/review"),"admin",Map.of("revision",1,"decision","APPROVED","reason","Reviewed contribution and educational context against the original proof.")));
    var publicRecord=response(get("/api/v1/college-projects/"+product));
    assertFalse(publicRecord.path("education").has("institution"));
    assertEquals(product.toString(),publicRecord.path("id").asText());
    assertEquals("Source only",publicRecord.path("education").path("executionMode").asText());
    b.put("shareAcademicDetails",true);
    response(body(put("/api/v1/me/college-projects/"+product),"builder",b));
    mvc.perform(get("/api/v1/college-projects/"+product)).andExpect(status().isNotFound());
    response(body(post("/api/v1/admin/college-projects/"+product+"/review"),"admin",Map.of("revision",3,"decision","APPROVED","reason","Reviewed opt-in academic visibility and current source evidence.")));
    assertEquals("Private college",response(get("/api/v1/college-projects/"+product)).path("education").path("institution").asText());
    db.update("UPDATE products SET visibility='NDA_SAFE' WHERE id=?",product);
    mvc.perform(get("/api/v1/college-projects/"+product)).andExpect(status().isNotFound());
  }
  @Test void editedPublishedContextNeverBypassesReviewAndSourceSnapshotStaysPinned()throws Exception{
    UUID id=draft();
    submit(id);
    response(approve(id));
    var original=response(get("/api/v1/components/remix-"+id));
    response(body(post("/api/v1/me/components/"+id+"/archive"),"builder",Map.of()));
    var edit=Map.of("recipeSlug","portfolio-card","title","Unreviewed changed title","summary","Unreviewed description must not become public.","contribution","New unreviewed contribution requiring independent approval.","rightsConsent",true);
    response(body(patch("/api/v1/me/components/"+id),"builder",edit));
    assertEquals(original.path("title"),response(get("/api/v1/components/remix-"+id)).path("title"));
    response(body(post("/api/v1/me/components/"+id+"/archive"),"builder",Map.of()));
    var archived=response(get("/api/v1/components/remix-"+id));
    assertEquals(original.path("title"),archived.path("title"));
    assertEquals(original.path("files"),archived.path("files"));
    assertFalse(archived.has("reviewReason"));
    submit(id);
    response(approve(id));
    var approved=response(get("/api/v1/components/remix-"+id));
    assertEquals("Unreviewed changed title",approved.path("title").asText());
    assertEquals(original.path("files"),approved.path("files"));
    assertNotNull(db.queryForObject("SELECT published_source::text FROM component_entries WHERE id=?",String.class,id));
  }
  @Test void concurrentApprovalsCannotOverfillTheLastFreeSlot()throws Exception{
    for(int n=0;n<2;n++){
      UUID id=draft();
      submit(id);
      response(approve(id));
    }
    UUID a=draft(),b=draft();
    submit(a);
    submit(b);
    var first=approve(a);
    var second=approve(b);
    var executor=Executors.newFixedThreadPool(2);
    try{
      var one=executor.submit(()->mvc.perform(first).andReturn().getResponse().getStatus());
      var two=executor.submit(()->mvc.perform(second).andReturn().getResponse().getStatus());
      assertEquals(List.of(200,409),java.util.stream.Stream.of(one.get(5,TimeUnit.SECONDS),two.get(5,TimeUnit.SECONDS)).sorted().toList());
      assertEquals(3,db.queryForObject("SELECT count(*) FROM component_entries WHERE status='ACTIVE'",Integer.class));
    }
    finally{
      executor.shutdownNow();
    }
  }
  @Test void signedDisputeRecoversUnknownReceiptBeforeGrantingCapacity()throws Exception{
    price(10000);
    when(provider.createPlatformOrder(any(UUID.class),anyLong())).thenAnswer(i->{
      order=json.valueToTree(Map.of("id","order_component123","receipt",i.getArgument(0).toString(),"amount",10000,"currency","INR"));throw new ApiError(409,"PROVIDER_MISMATCH","Uncertain provider response.");
    }
    );
    mvc.perform(buy("builder",UUID.randomUUID().toString(),10000)).andExpect(status().isConflict());
    captured=true;
    when(provider.dispute(anyString())).thenReturn(json.valueToTree(Map.of("id","disp_component123","payment_id","pay_component123","currency","INR","amount",10000,"amount_deducted",0,"status","open")));
    String raw="{\"event\":\"payment.dispute.created\",\"payload\":{\"dispute\":{\"entity\":{\"id\":\"disp_component123\",\"payment_id\":\"pay_component123\"}}}}";
    response(post("/api/v1/components/razorpay/webhook").contentType("application/json").content(raw).header("x-razorpay-event-id","unbound-dispute").header("X-Razorpay-Signature","a".repeat(64)));
    UUID id=db.queryForObject("SELECT id FROM component_slot_purchases",UUID.class);
    var recovered=response(body(post("/api/v1/admin/component-slot-purchases/"+id+"/bind-order"),"admin",Map.of("orderId","order_component123")));
    assertFalse(recovered.path("grantsSlot").asBoolean());
    assertEquals("DISPUTED",recovered.path("status").asText());
    assertEquals(3,response(as(get("/api/v1/me/components"),"builder")).path("capacity").path("limit").asInt());
  }
  @Test void staleSameDisputeObservationCannotOverwriteNewerStatus()throws Exception{
    price(10000);
    String id=response(buy("builder",UUID.randomUUID().toString(),10000)).path("id").asText();
    captured=true;
    db.update("UPDATE component_slot_purchases SET dispute_id='disp_component123',dispute_status='open' WHERE id=?",UUID.fromString(id));
    CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
    AtomicInteger observations=new AtomicInteger();
    when(provider.dispute(anyString())).thenAnswer(i->{
      boolean old=observations.incrementAndGet()==1;if(old){
        entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));
      }
      return json.valueToTree(Map.of("id","disp_component123","payment_id","pay_component123","currency","INR","amount",10000,"amount_deducted",0,"status",old?"open":"won"));
    }
    );
    var reconcile=body(post("/api/v1/me/component-slot-purchases/"+id+"/reconcile"),"builder",Map.of());
    var executor=Executors.newSingleThreadExecutor();
    try{
      var pending=executor.submit(()->mvc.perform(reconcile).andReturn().getResponse().getStatus());
      assertTrue(entered.await(3,TimeUnit.SECONDS));
      String raw="{\"event\":\"payment.dispute.won\",\"payload\":{\"dispute\":{\"entity\":{\"id\":\"disp_component123\",\"payment_id\":\"pay_component123\"}}}}";
      response(post("/api/v1/components/razorpay/webhook").contentType("application/json").content(raw).header("x-razorpay-event-id","new-dispute-status").header("X-Razorpay-Signature","a".repeat(64)));
      release.countDown();
      assertEquals(200,pending.get(5,TimeUnit.SECONDS));
      assertEquals("won",db.queryForObject("SELECT dispute_status FROM component_slot_purchases",String.class));
      assertEquals("CAPTURED",db.queryForObject("SELECT status FROM component_slot_purchases",String.class));
    }
    finally{
      release.countDown();
      executor.shutdownNow();
    }
  }
  @Test void operatorQueuesPageBeyondFiftyRecordsAndPrioritizePending()throws Exception{
    for(int n=0;n<51;n++)db.update("INSERT INTO component_entries(id,owner_id,recipe_slug,slug,title,summary,contribution,status) VALUES(?,?,'portfolio-card',?,'Queued title','Queued summary text','Original contribution awaiting review','PENDING')",UUID.randomUUID(),builder,"queue-"+n);
    var first=response(as(get("/api/v1/admin/components/review"),"admin"));
    assertEquals(50,first.path("components").size());
    assertTrue(first.path("componentHasMore").asBoolean());
    var second=response(as(get("/api/v1/admin/components/review?componentPage=1"),"admin"));
    assertEquals(1,second.path("components").size());
    assertFalse(second.path("componentHasMore").asBoolean());
    mvc.perform(as(get("/api/v1/admin/components/review?collegePage=-1"),"admin")).andExpect(status().isBadRequest());
  }
  @Test void approvalIsBoundToTheRevisionActuallyReviewed()throws Exception{
    UUID id=draft();
    submit(id);
    var stale=approve(id);
    response(body(post("/api/v1/me/components/"+id+"/archive"),"builder",Map.of()));
    response(body(patch("/api/v1/me/components/"+id),"builder",Map.of("recipeSlug","portfolio-card","title","Changed content title","summary","This is a changed description that requires review.","contribution","A replacement contribution must be reviewed before publication.","rightsConsent",true)));
    submit(id);
    mvc.perform(stale).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("REVIEW_CONTENT_CHANGED"));
    mvc.perform(get("/api/v1/components/remix-"+id)).andExpect(status().isNotFound());
    response(approve(id));
  }
 @Test void suspendedSourceIsPrivateToCurrentMfaOperatorsAndKeepsFrozenBytes()throws Exception{
  UUID id=draft();submit(id);response(approve(id));var original=response(get("/api/v1/components/remix-"+id));response(body(post("/api/v1/admin/components/"+id+"/review"),"admin",Map.of("revision",db.queryForObject("SELECT revision FROM component_entries WHERE id=?",Long.class,id),"decision","SUSPEND","reason","Temporarily suspended for independent moderation review.")));
  mvc.perform(get("/api/v1/components/remix-"+id)).andExpect(status().isNotFound());mvc.perform(as(get("/api/v1/admin/components/"+id),"builder")).andExpect(status().isForbidden());assertEquals(original.path("files"),response(as(get("/api/v1/admin/components/"+id),"admin")).path("files"));db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?",admin);mvc.perform(as(get("/api/v1/admin/components/"+id),"admin")).andExpect(status().isForbidden());
 }

  void slotPrice(String pool,long amount)throws Exception{
    response(body(put("/api/v1/admin/publishing-slots/"+pool+"/pricing"),"admin",Map.of("amountMinor",amount,"enabled",true)));
  }
  MockHttpServletRequestBuilder poolBuy(String pool,String key,long amount)throws Exception{
    return body(post("/api/v1/me/publishing-slot-purchases"),"builder",Map.of("pool",pool,"amountMinor",amount,"purchaseConsent",true)).header("Idempotency-Key",key);
  }
  JsonNode publishing()throws Exception{return response(as(get("/api/v1/me/publishing-slots"),"builder")).path("capacities");}
  UUID project(boolean college,String state){
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,approval_status,lifecycle_status,visibility) VALUES(?,?,?,'Capacity proof','A useful project','A documented project with evidence','LEARNING','CRM','React','Built the project','APPROVED',?,'PUBLIC')",id,builder,id.toString(),state);
    if(college)db.update("INSERT INTO college_project_metadata(product_id,category,language,problem,outcome,prerequisites,contribution) VALUES(?,'FULL_STACK','Java','A specific educational problem','A reproducible educational result','Local prerequisites','Implemented the documented system')",id);
    return id;
  }
  MockHttpServletRequestBuilder activateProject(UUID id)throws Exception{return body(post("/api/v1/developer/products/"+id+"/activate"),"builder",Map.of());}
  void fillProjects(){for(int i=0;i<2;i++)project(false,"ACTIVE");for(int i=0;i<3;i++)project(true,"ACTIVE");}
  UUID sourceTemplate(String state){
    UUID id=UUID.randomUUID(),version=UUID.randomUUID();
    db.update("UPDATE products SET repository_url='https://github.com/example/proof' WHERE id=?",product);
    db.update("INSERT INTO repository_verifications(product_id,repository_url,challenge,status) VALUES(?,'https://github.com/example/proof','challenge','VERIFIED') ON CONFLICT(product_id) DO NOTHING",product);
    db.update("INSERT INTO source_templates(id,seller_id,product_id,slug,title,summary,description,price_minor,license_terms,status) VALUES(?,?,?,?,'Template proof','An approved source template','A documented source template',10000,'One commercial end product; notices retained',?)",id,builder,product,id.toString(),state);
    db.update("INSERT INTO source_versions(id,template_id,version,release_notes,storage_key,sha256,size_bytes,entry_count,license_terms,manifest_files,rights_consented_at,status) VALUES(?,?,'1.0','Reviewed release',? ,?,100,3,'One commercial end product','README.md\nLICENSE\npackage.json',now(),?)",version,id,"commerce/"+id+"/"+version+".zip","a".repeat(64),state.equals("ACTIVE")?"APPROVED":"PENDING");
    return id;
  }
  MockHttpServletRequestBuilder approveTemplate(UUID id)throws Exception{
    UUID version=db.queryForObject("SELECT id FROM source_versions WHERE template_id=?",UUID.class,id);
    return body(post("/api/v1/admin/templates/"+id+"/versions/"+version+"/review"),"admin",Map.of("action","APPROVE","reason","Inspected source, license and documentation.","rightsReviewed",true,"packageReviewed",true));
  }
  @Test void eachProjectCategoryHasThreeFreePlacesAndCannotBorrowTheOthersUnusedFreePlaces()throws Exception{
    fillProjects();var caps=publishing();
    assertEquals(3,caps.path("PROJECT").path("regular").path("free").asInt());assertEquals(3,caps.path("PROJECT").path("college").path("free").asInt());assertEquals(6,caps.path("PROJECT").path("used").asInt());
    assertEquals(3,caps.path("TEMPLATE").path("free").asInt());assertEquals(3,caps.path("COMPONENT").path("free").asInt());
    UUID regular=project(false,"DRAFT"),college=project(true,"DRAFT");
    mvc.perform(activateProject(regular)).andExpect(status().isConflict());mvc.perform(activateProject(college)).andExpect(status().isConflict());
    mvc.perform(body(post("/api/v1/developer/products/"+product+"/archive"),"builder",Map.of())).andExpect(status().isNoContent());
    mvc.perform(activateProject(college)).andExpect(status().isConflict());mvc.perform(activateProject(regular)).andExpect(status().isNoContent());
    assertEquals(0,publishing().path("PROJECT").path("extraUsed").asInt());
  }
  @Test void onePaidProjectExtraCanServeEitherCategoryButOnlyOneActiveProjectAtATime()throws Exception{
    fillProjects();slotPrice("PROJECT",10000);var p=response(poolBuy("PROJECT",UUID.randomUUID().toString(),10000));captured=true;
    response(body(post("/api/v1/me/publishing-slot-purchases/"+p.path("id").asText()+"/reconcile"),"builder",Map.of()));
    UUID college=project(true,"DRAFT"),regular=project(false,"DRAFT");mvc.perform(activateProject(college)).andExpect(status().isNoContent());mvc.perform(activateProject(regular)).andExpect(status().isConflict());
    assertEquals(1,publishing().path("PROJECT").path("extraUsed").asInt());assertEquals(3,publishing().path("COMPONENT").path("limit").asInt());
    mvc.perform(body(post("/api/v1/developer/products/"+college+"/archive"),"builder",Map.of())).andExpect(status().isNoContent());mvc.perform(activateProject(regular)).andExpect(status().isNoContent());
  }
  @Test void earnedProjectCapacitySurvivesAndDoesNotAddTemplateOrComponentPlaces()throws Exception{
    fillProjects();db.update("UPDATE showcase_entitlements SET active_slot_limit=4 WHERE user_id=?",builder);
    mvc.perform(activateProject(project(false,"DRAFT"))).andExpect(status().isNoContent());
    var c=publishing();assertEquals(1,c.path("PROJECT").path("earned").asInt());assertEquals(3,c.path("TEMPLATE").path("limit").asInt());assertEquals(3,c.path("COMPONENT").path("limit").asInt());
  }
  @Test void collegeConversionChecksCollegeCapacityBeforeChangingTheActiveProject()throws Exception{
    for(int i=0;i<3;i++)project(true,"ACTIVE");
    Map<String,Object> context=Map.of("category","FULL_STACK","language","Java","problem","A documented original student problem.","outcome","A measured and reproducible student outcome.","prerequisites","Java and local setup.","contribution","Implemented the original application modules.","rightsConsent",true);
    mvc.perform(body(put("/api/v1/me/college-projects/"+product),"builder",context)).andExpect(status().isConflict());
    assertEquals(0,db.queryForObject("SELECT count(*) FROM college_project_metadata WHERE product_id=?",Integer.class,product));
    UUID college=db.queryForObject("SELECT product_id FROM college_project_metadata LIMIT 1",UUID.class);
    mvc.perform(body(post("/api/v1/developer/products/"+college+"/archive"),"builder",Map.of())).andExpect(status().isNoContent());
    response(body(put("/api/v1/me/college-projects/"+product),"builder",context));
    assertEquals(0,publishing().path("PROJECT").path("regular").path("used").asInt());assertEquals(3,publishing().path("PROJECT").path("college").path("used").asInt());
  }
  @Test void collegeDraftContextDoesNotReserveOrRequireAnActiveSlot()throws Exception{
    for(int i=0;i<3;i++)project(true,"ACTIVE");UUID draft=project(false,"DRAFT");
    response(body(put("/api/v1/me/college-projects/"+draft),"builder",Map.of("category","FULL_STACK","language","Java","problem","A documented original student problem.","outcome","A measured and reproducible student outcome.","prerequisites","Java and local setup.","contribution","Implemented the original application modules.","rightsConsent",true)));
    assertEquals(3,publishing().path("PROJECT").path("college").path("used").asInt());
    mvc.perform(activateProject(draft)).andExpect(status().isConflict());
  }
  @Test void concurrentProjectActivationCannotOccupyTheLastFreePlaceTwice()throws Exception{
    project(false,"ACTIVE");UUID a=project(false,"DRAFT"),b=project(false,"DRAFT");var ready=new CountDownLatch(2);var start=new CountDownLatch(1);var workers=Executors.newFixedThreadPool(2);
    try{var results=new ArrayList<Future<Integer>>();for(UUID id:List.of(a,b))results.add(workers.submit(()->{ready.countDown();assertTrue(start.await(10,TimeUnit.SECONDS));return mvc.perform(activateProject(id)).andReturn().getResponse().getStatus();}));assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();var statuses=new ArrayList<Integer>();for(var f:results)statuses.add(f.get(20,TimeUnit.SECONDS));Collections.sort(statuses);assertEquals(List.of(204,409),statuses);assertEquals(3,publishing().path("PROJECT").path("regular").path("used").asInt());}finally{workers.shutdownNow();}
  }
  @Test void templatesConsumeThreeIndependentPlacesAndArchiveAllowsReviewedReactivation()throws Exception{
    UUID first=null;for(int i=0;i<3;i++){UUID t=sourceTemplate("DRAFT");response(approveTemplate(t));if(first==null)first=t;}
    UUID fourth=sourceTemplate("DRAFT");mvc.perform(approveTemplate(fourth)).andExpect(status().isConflict());
    response(body(post("/api/v1/me/templates/"+first+"/archive"),"builder",Map.of()));response(approveTemplate(fourth));
    mvc.perform(body(post("/api/v1/me/templates/"+first+"/activate"),"builder",Map.of())).andExpect(status().isConflict());
    response(body(post("/api/v1/me/templates/"+fourth+"/archive"),"builder",Map.of()));response(body(post("/api/v1/me/templates/"+first+"/activate"),"builder",Map.of()));
    assertEquals(3,publishing().path("TEMPLATE").path("used").asInt());assertEquals(1,publishing().path("PROJECT").path("regular").path("used").asInt());
  }
  @Test void concurrentTemplateReviewsCannotPublishBeyondCapacity()throws Exception{
    for(int i=0;i<2;i++)sourceTemplate("ACTIVE");UUID a=sourceTemplate("DRAFT"),b=sourceTemplate("DRAFT");var start=new CountDownLatch(1);var workers=Executors.newFixedThreadPool(2);
    try{var results=new ArrayList<Future<Integer>>();for(UUID id:List.of(a,b))results.add(workers.submit(()->{assertTrue(start.await(10,TimeUnit.SECONDS));return mvc.perform(approveTemplate(id)).andReturn().getResponse().getStatus();}));start.countDown();var statuses=new ArrayList<Integer>();for(var f:results)statuses.add(f.get(20,TimeUnit.SECONDS));Collections.sort(statuses);assertEquals(List.of(200,409),statuses);assertEquals(3,publishing().path("TEMPLATE").path("used").asInt());}finally{workers.shutdownNow();}
  }
  @Test void administratorPricesAreIndependentAndUnauthorizedPricingIsRejected()throws Exception{
    slotPrice("PROJECT",10000);slotPrice("TEMPLATE",20000);slotPrice("COMPONENT",30000);
    var prices=response(get("/api/v1/publishing-slots/pricing"));assertEquals(10000,prices.path("PROJECT").path("amountMinor").asInt());assertEquals(20000,prices.path("TEMPLATE").path("amountMinor").asInt());assertEquals(30000,prices.path("COMPONENT").path("amountMinor").asInt());
    mvc.perform(body(put("/api/v1/admin/publishing-slots/TEMPLATE/pricing"),"builder",Map.of("amountMinor",10000,"enabled",true))).andExpect(status().isForbidden());
    mvc.perform(body(put("/api/v1/admin/publishing-slots/COLLEGE/pricing"),"admin",Map.of("amountMinor",10000,"enabled",true))).andExpect(status().isBadRequest());
  }
  @Test void paidPoolsFreezeCategoryAndPriceAndCannotReuseACheckoutKeyAcrossCategories()throws Exception{
    slotPrice("PROJECT",10000);slotPrice("TEMPLATE",20000);String key=UUID.randomUUID().toString();var p=response(poolBuy("PROJECT",key,10000));assertEquals("PROJECT",p.path("pool").asText());slotPrice("PROJECT",15000);
    assertEquals(p.path("id").asText(),response(poolBuy("PROJECT",key,15000)).path("id").asText());assertEquals(10000,response(poolBuy("PROJECT",key,15000)).path("amountMinor").asLong());
    mvc.perform(poolBuy("TEMPLATE",key,20000)).andExpect(status().isConflict());
    when(provider.createPlatformOrder(any(UUID.class),anyLong())).thenAnswer(i->{UUID id=i.getArgument(0);return json.valueToTree(Map.of("id","order_template123","receipt",id.toString(),"amount",20000,"currency","INR","partial_payment",false));});
    var template=response(poolBuy("TEMPLATE",UUID.randomUUID().toString(),20000));assertEquals("TEMPLATE",template.path("pool").asText());
    assertEquals(1,response(as(get("/api/v1/me/publishing-slot-purchases?pool=PROJECT"),"builder")).path("items").size());assertEquals(1,response(as(get("/api/v1/me/publishing-slot-purchases?pool=TEMPLATE"),"builder")).path("items").size());
    assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE component_slot_purchases SET pool='COMPONENT' WHERE id=?",UUID.fromString(p.path("id").asText())));
  }
  @Test void projectRefundRemovesOnlySharedExtrasAndRetainsThreeFreePerProjectCategory()throws Exception{
    fillProjects();for(int i=0;i<3;i++)sourceTemplate("ACTIVE");slotPrice("PROJECT",10000);var p=response(poolBuy("PROJECT",UUID.randomUUID().toString(),10000));captured=true;String path="/api/v1/me/publishing-slot-purchases/"+p.path("id").asText()+"/reconcile";response(body(post(path),"builder",Map.of()));
    mvc.perform(activateProject(project(true,"DRAFT"))).andExpect(status().isNoContent());refunded=1;response(body(post(path),"builder",Map.of()));var c=publishing();assertEquals(3,c.path("PROJECT").path("regular").path("used").asInt());assertEquals(3,c.path("PROJECT").path("college").path("used").asInt());assertEquals(3,c.path("TEMPLATE").path("used").asInt());assertEquals(3,c.path("COMPONENT").path("limit").asInt());assertEquals(0,c.path("PROJECT").path("purchased").asInt());
  }
  @Test void templateRefundDoesNotConsumeOrArchiveProjectCapacity()throws Exception{
    for(int i=0;i<3;i++)sourceTemplate("ACTIVE");slotPrice("TEMPLATE",10000);var p=response(poolBuy("TEMPLATE",UUID.randomUUID().toString(),10000));captured=true;String path="/api/v1/me/publishing-slot-purchases/"+p.path("id").asText()+"/reconcile";response(body(post(path),"builder",Map.of()));response(approveTemplate(sourceTemplate("DRAFT")));assertEquals(4,publishing().path("TEMPLATE").path("used").asInt());refunded=10000;response(body(post(path),"builder",Map.of()));assertEquals(3,publishing().path("TEMPLATE").path("used").asInt());assertEquals(1,publishing().path("PROJECT").path("used").asInt());
  }
  @Test void paidTemplateAndProjectTestCapturesNeverGrantLivePlaces()throws Exception{
    when(provider.mode()).thenReturn("test");when(provider.configuration()).thenReturn(Map.of("enabled",true,"mode","test","reason",""));slotPrice("TEMPLATE",10000);var p=response(poolBuy("TEMPLATE",UUID.randomUUID().toString(),10000));captured=true;assertFalse(response(body(post("/api/v1/me/publishing-slot-purchases/"+p.path("id").asText()+"/reconcile"),"builder",Map.of())).path("grantsSlot").asBoolean());assertEquals(3,publishing().path("TEMPLATE").path("limit").asInt());assertEquals(6,publishing().path("PROJECT").path("limit").asInt());
  }
  @Test void retainedTemplateCapacityIsImmutableAndOnlyAppliesToTemplates()throws Exception{
    db.update("INSERT INTO publishing_capacity_grants(owner_id,pool,slots,reason) VALUES(?,'TEMPLATE',2,'Existing active template capacity before V25')",builder);
    assertEquals(5,publishing().path("TEMPLATE").path("limit").asInt());assertEquals(6,publishing().path("PROJECT").path("limit").asInt());assertEquals(3,publishing().path("COMPONENT").path("limit").asInt());
    assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE publishing_capacity_grants SET slots=10 WHERE owner_id=?",builder));
  }
}
