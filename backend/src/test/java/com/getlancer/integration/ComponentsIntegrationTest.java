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
  static final com.getlancer.hosting.HostingPublisherFixture previewPublisher=new com.getlancer.hosting.HostingPublisherFixture();
  @org.springframework.test.context.DynamicPropertySource static void previewProperties(org.springframework.test.context.DynamicPropertyRegistry registry){
    registry.add("app.hosting.enabled",()->true);registry.add("app.hosting.publisher-url",previewPublisher::url);
    registry.add("app.hosting.publisher-secret",()->com.getlancer.hosting.HostingPublisherFixture.SECRET);registry.add("app.hosting.gateway-secret",()->com.getlancer.hosting.HostingPublisherFixture.GATEWAY);
    registry.add("app.hosting.public-url-template",()->com.getlancer.hosting.HostingPublisherFixture.TEMPLATE);
  }
  @AfterAll static void closePreviewPublisher(){previewPublisher.close();}
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
  @Autowired com.getlancer.publishing.PublishingCapacity capacity;
  @Autowired org.springframework.context.ApplicationContext applicationContext;
  @MockBean RazorpayClient provider;
  UUID builder,other,admin,product;
  JsonNode order;
  AtomicInteger creates=new AtomicInteger();
  boolean captured,wrongAmount;
  long refunded;
  @Test void mvcHandlersDoNotExposeXsltViewRendering(){
    var mappings=applicationContext.getBeansOfType(org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class);
    int applicationHandlers=0;
    for(var mapping:mappings.values()) for(var handler:mapping.getHandlerMethods().values()){
      if(!handler.getBeanType().getPackageName().startsWith("com.getlancer.")) continue;
      applicationHandlers++;
      assertTrue(org.springframework.core.annotation.AnnotatedElementUtils.hasAnnotation(handler.getBeanType(),org.springframework.web.bind.annotation.ResponseBody.class)
        || handler.hasMethodAnnotation(org.springframework.web.bind.annotation.ResponseBody.class),handler.toString());
    }
    assertTrue(applicationHandlers>100,"Inspect the real application handler registry, not an empty context.");
    for(var resolver:applicationContext.getBeansOfType(org.springframework.web.servlet.ViewResolver.class).values())
      assertFalse(resolver instanceof org.springframework.web.servlet.view.xslt.XsltViewResolver,resolver.getClass().getName());
    for(var view:applicationContext.getBeansOfType(org.springframework.web.servlet.View.class).values())
      assertFalse(view instanceof org.springframework.web.servlet.view.xslt.XsltView,view.getClass().getName());
  }
  @Test void mvcHandlersDoNotExposeSseFragmentRendering(){
    var mappings=applicationContext.getBeansOfType(org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class);
    int applicationHandlers=0;
    for(var mapping:mappings.values()) for(var entry:mapping.getHandlerMethods().entrySet()){
      var handler=entry.getValue();
      if(!handler.getBeanType().getPackageName().startsWith("com.getlancer."))continue;
      applicationHandlers++;
      var type=handler.getReturnType().getParameterType();
      assertFalse(org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.class.isAssignableFrom(type),handler.toString());
      assertFalse(org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody.class.isAssignableFrom(type),handler.toString());
      assertFalse(org.springframework.web.servlet.View.class.isAssignableFrom(type),handler.toString());
      assertFalse(type.getName().contains("FragmentsRendering"),handler.toString());
      for(var media:entry.getKey().getProducesCondition().getProducibleMediaTypes())
        assertFalse(org.springframework.http.MediaType.TEXT_EVENT_STREAM.isCompatibleWith(media),handler.toString());
      assertTrue(org.springframework.core.annotation.AnnotatedElementUtils.hasAnnotation(handler.getBeanType(),org.springframework.web.bind.annotation.ResponseBody.class)
        ||handler.hasMethodAnnotation(org.springframework.web.bind.annotation.ResponseBody.class),handler.toString());
    }
    assertTrue(applicationHandlers>100,"Inspect the real application registry.");
    assertTrue(applicationContext.getBeansOfType(org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.class).isEmpty());
    assertTrue(applicationContext.getBeansOfType(org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody.class).isEmpty());
  }
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
    return body(post("/api/v1/admin/components/"+id+"/review"),"admin",Map.of("revision",db.queryForObject("SELECT revision FROM component_entries WHERE id=?",Long.class,id),"sourceHash",response(as(get("/api/v1/admin/components/"+id),"admin")).path("sourceHash").asText(),"decision","APPROVE","reason","Reviewed original curated recipe and explicit attribution."));
  }
  @AfterEach void cleanup(){db.execute("TRUNCATE users CASCADE");}
  @BeforeEach void setup(){
    reset(provider);previewPublisher.reset();
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
    mvc.perform(get("/api/v1/components")).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(24)).andExpect(jsonPath("$.items[0].files").doesNotExist());
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
    var reviewedRequest=approve(id);
    db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?",admin);
    mvc.perform(reviewedRequest).andExpect(status().isForbidden());
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
  UUID id=draft();submit(id);response(approve(id));var original=response(get("/api/v1/components/remix-"+id));response(body(post("/api/v1/admin/components/"+id+"/review"),"admin",Map.of("revision",db.queryForObject("SELECT revision FROM component_entries WHERE id=?",Long.class,id),"sourceHash",response(as(get("/api/v1/admin/components/"+id),"admin")).path("sourceHash").asText(),"decision","SUSPEND","reason","Temporarily suspended for independent moderation review.")));
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
  @Test void legacyComponentHistoryRetainsOneHundredReceiptDefaultWhileNewHistoryPages()throws Exception{
    for(int i=0;i<51;i++)db.update("INSERT INTO component_slot_purchases(id,owner_id,idempotency_key,pool,amount_minor,currency,mode,status) VALUES(?,?,?,'COMPONENT',10000,'INR','live','REJECTED')",UUID.randomUUID(),builder,UUID.randomUUID());
    assertEquals(51,response(as(get("/api/v1/me/component-slot-purchases"),"builder")).path("items").size());
    var first=response(as(get("/api/v1/me/publishing-slot-purchases?pool=COMPONENT"),"builder"));assertEquals(50,first.path("items").size());assertTrue(first.path("hasMore").asBoolean());
    assertEquals(1,response(as(get("/api/v1/me/publishing-slot-purchases?pool=COMPONENT&page=1"),"builder")).path("items").size());
  }
  @Test void templateReactivationRejectsOtherOwnersIneligibleSellersAndUnapprovedListingsWithoutSideEffects()throws Exception{
    UUID approved=sourceTemplate("ACTIVE"),pending=sourceTemplate("ARCHIVED"),suspended=sourceTemplate("SUSPENDED");db.update("UPDATE source_templates SET status='ARCHIVED' WHERE id=?",approved);
    mvc.perform(body(post("/api/v1/me/templates/"+approved+"/activate"),"other",Map.of())).andExpect(status().isNotFound());
    db.update("UPDATE developer_profiles SET approval_status='DRAFT' WHERE user_id=?",builder);
    mvc.perform(body(post("/api/v1/me/templates/"+approved+"/activate"),"builder",Map.of())).andExpect(status().isForbidden());
    db.update("UPDATE developer_profiles SET approval_status='APPROVED' WHERE user_id=?",builder);
    mvc.perform(body(post("/api/v1/me/templates/"+pending+"/activate"),"builder",Map.of())).andExpect(status().isConflict());
    mvc.perform(body(post("/api/v1/me/templates/"+suspended+"/activate"),"builder",Map.of())).andExpect(status().isConflict());
    assertEquals("ARCHIVED",db.queryForObject("SELECT status FROM source_templates WHERE id=?",String.class,approved));assertEquals("ARCHIVED",db.queryForObject("SELECT status FROM source_templates WHERE id=?",String.class,pending));assertEquals("SUSPENDED",db.queryForObject("SELECT status FROM source_templates WHERE id=?",String.class,suspended));
    assertEquals(0,db.queryForObject("SELECT count(*) FROM commerce_audit WHERE kind='TEMPLATE_ACTIVATED'",Integer.class));
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
  @Test void editingAnActiveProjectSerializesWithRefundTrimmingAndKeepsItsNewDraftState()throws Exception{
    project(false,"ACTIVE");project(false,"ACTIVE");UUID editing=project(false,"ACTIVE");db.update("UPDATE products SET updated_at=now()+interval '1 second' WHERE id=?",editing);
    var request=body(patch("/api/v1/developer/products/"+editing),"builder",Map.of("title","Edited original project","summary","A useful edited project","description","A documented original project with updated evidence.","projectType","LEARNING","category","CRM","technology","React","visibility","PUBLIC","contribution","Implemented the updated project modules.","rightsConfirmed",true));
    var workers=Executors.newSingleThreadExecutor();var pending=new java.util.concurrent.atomic.AtomicReference<Future<Integer>>();
    try{
      new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status->{
        capacity.lock(builder);pending.set(workers.submit(()->mvc.perform(request).andReturn().getResponse().getStatus()));
        boolean waiting=false;
        for(int i=0;i<500;i++){if(db.queryForObject("SELECT count(*) FROM pg_locks WHERE locktype='advisory' AND NOT granted",Integer.class)>0){waiting=true;break;}if(pending.get().isDone())break;try{Thread.sleep(10);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}}
        assertTrue(waiting,"Project edits must wait for the same owner capacity transaction as refunds.");assertEquals("ACTIVE",db.queryForObject("SELECT lifecycle_status FROM products WHERE id=?",String.class,editing));
        capacity.trim(builder,"PROJECT");assertEquals("ARCHIVED",db.queryForObject("SELECT lifecycle_status FROM products WHERE id=?",String.class,editing));
      });
      assertEquals(200,pending.get().get(10,TimeUnit.SECONDS));assertEquals("DRAFT",db.queryForObject("SELECT lifecycle_status FROM products WHERE id=?",String.class,editing));assertEquals(3,publishing().path("PROJECT").path("regular").path("used").asInt());
    }finally{workers.shutdownNow();}
  }
  @Test void administratorMetricsUseSixFreeProjectPlacesAndOnlySharedLiveProjectExtras()throws Exception{
    fillProjects();var slots=response(as(get("/api/v1/admin/metrics"),"admin")).path("marketplace").path("slots");assertEquals(6,slots.path("totalCapacity").asInt());assertEquals(6,slots.path("activeUsage").asInt());assertEquals(0,slots.path("overCapacityBuilders").asInt());assertEquals(100,slots.path("utilisationPercent").asInt());
    slotPrice("PROJECT",10000);var receipt=response(poolBuy("PROJECT",UUID.randomUUID().toString(),10000));captured=true;response(body(post("/api/v1/me/publishing-slot-purchases/"+receipt.path("id").asText()+"/reconcile"),"builder",Map.of()));db.update("UPDATE showcase_entitlements SET active_slot_limit=4 WHERE user_id=?",builder);project(false,"ACTIVE");project(false,"ACTIVE");
    slots=response(as(get("/api/v1/admin/metrics"),"admin")).path("marketplace").path("slots");assertEquals(8,slots.path("totalCapacity").asInt());assertEquals(8,slots.path("activeUsage").asInt());assertEquals(0,slots.path("overCapacityBuilders").asInt());assertEquals(0,slots.path("availableCapacity").asInt());
    project(false,"ACTIVE");slots=response(as(get("/api/v1/admin/metrics"),"admin")).path("marketplace").path("slots");assertEquals(1,slots.path("overCapacityBuilders").asInt());
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
    var exported=response(as(get("/api/v1/me/export"),"builder")).path("componentSlotPurchases");assertEquals(2,exported.size());var exportedPools=new HashSet<String>();exported.forEach(row->exportedPools.add(row.path("pool").asText()));assertEquals(Set.of("PROJECT","TEMPLATE"),exportedPools);
    assertEquals(1,response(as(get("/api/v1/me/publishing-slot-purchases?pool=PROJECT"),"builder")).path("items").size());assertEquals(1,response(as(get("/api/v1/me/publishing-slot-purchases?pool=TEMPLATE"),"builder")).path("items").size());
    assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE component_slot_purchases SET pool='COMPONENT' WHERE id=?",UUID.fromString(p.path("id").asText())));
  }
  @Test void projectRefundRemovesOnlySharedExtrasAndRetainsThreeFreePerProjectCategory()throws Exception{
    fillProjects();for(int i=0;i<3;i++)sourceTemplate("ACTIVE");slotPrice("PROJECT",10000);var p=response(poolBuy("PROJECT",UUID.randomUUID().toString(),10000));captured=true;String path="/api/v1/me/publishing-slot-purchases/"+p.path("id").asText()+"/reconcile";response(body(post(path),"builder",Map.of()));
    mvc.perform(activateProject(project(true,"DRAFT"))).andExpect(status().isNoContent());refunded=1;response(body(post(path),"builder",Map.of()));var c=publishing();assertEquals(3,c.path("PROJECT").path("regular").path("used").asInt());assertEquals(3,c.path("PROJECT").path("college").path("used").asInt());assertEquals(3,c.path("TEMPLATE").path("used").asInt());assertEquals(3,c.path("COMPONENT").path("limit").asInt());assertEquals(0,c.path("PROJECT").path("purchased").asInt());
    assertEquals(1,db.queryForObject("SELECT count(*) FROM analytics_events WHERE event_name='product_archived' AND context->>'builderId'=? AND context->>'activeCount'='6'",Integer.class,builder.toString()));
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
  @Test void migrationPreservesExistingComponentReceiptsAndRetainsOnlyActiveLegacyTemplateCapacity()throws Exception{
    String schema="publishing_migration_"+UUID.randomUUID().toString().replace("-","");
    String migration;
    try(var input=getClass().getResourceAsStream("/db/migration/V25__shared_publishing_capacity.sql")){migration=new String(Objects.requireNonNull(input).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
    UUID receipt=UUID.randomUUID();
    var source=Objects.requireNonNull(db.getDataSource()).unwrap(com.zaxxer.hikari.HikariDataSource.class);
    // A schema-changing rehearsal must not reuse the application's prepared-statement cache.
    try(var connection=java.sql.DriverManager.getConnection(source.getJdbcUrl(),source.getUsername(),source.getPassword())){
      connection.setAutoCommit(false);
      var db=new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));
      db.execute("CREATE SCHEMA "+schema);db.execute("SET LOCAL search_path = "+schema+",getlancer_test");
      db.execute("CREATE TABLE users(id uuid PRIMARY KEY)");db.update("INSERT INTO users(id) VALUES(?)",builder);
      db.execute("CREATE TABLE component_slot_pricing(id boolean PRIMARY KEY DEFAULT true CHECK(id),amount_minor bigint,enabled boolean DEFAULT false,updated_at timestamptz DEFAULT now())");db.update("INSERT INTO component_slot_pricing(id,amount_minor,enabled) VALUES(true,10000,true)");
      db.execute("CREATE TABLE component_slot_purchases(id uuid PRIMARY KEY,owner_id uuid,idempotency_key uuid,amount_minor bigint,currency varchar(3),mode varchar(4),status varchar(20),refunded_minor bigint DEFAULT 0,order_id varchar(40),payment_id varchar(40),created_at timestamptz DEFAULT now())");
      db.execute("CREATE UNIQUE INDEX component_slot_unresolved ON component_slot_purchases(owner_id,mode) WHERE status IN ('CREATING','UNKNOWN','ORDER_CREATED')");
      db.update("INSERT INTO component_slot_purchases(id,owner_id,idempotency_key,amount_minor,currency,mode,status,order_id,payment_id) VALUES(?,?,?,10000,'INR','live','CAPTURED','order_existing123','pay_existing123')",receipt,builder,UUID.randomUUID());
      db.execute("CREATE TABLE source_templates(id uuid PRIMARY KEY,seller_id uuid,status varchar(20),created_at timestamptz DEFAULT now())");
      for(int i=0;i<6;i++)db.update("INSERT INTO source_templates(id,seller_id,status) VALUES(?,?,?)",UUID.randomUUID(),builder,i==5?"ARCHIVED":"ACTIVE");
      db.execute(migration);
      var p=db.queryForMap("SELECT * FROM component_slot_purchases WHERE id=?",receipt);assertEquals("COMPONENT",p.get("pool"));assertEquals(10000L,p.get("amount_minor"));assertEquals("order_existing123",p.get("order_id"));assertEquals("pay_existing123",p.get("payment_id"));assertEquals("CAPTURED",p.get("status"));
      assertEquals(2L,db.queryForObject("SELECT slots FROM publishing_capacity_grants WHERE owner_id=?",Long.class,builder));
      assertEquals(10000L,db.queryForObject("SELECT amount_minor FROM component_slot_pricing WHERE pool='COMPONENT'",Long.class));assertEquals(2,db.queryForObject("SELECT count(*) FROM component_slot_pricing WHERE pool<>'COMPONENT' AND amount_minor IS NULL AND NOT enabled",Integer.class));
      connection.rollback(); // Isolated migration rehearsal leaves the shared test schema untouched.
    }
  }
  @Test void submittedSourceAndPublishedHistoryAreImmutable() throws Exception {
    UUID component=draft();submit(component);
    assertNotNull(db.queryForObject("SELECT submitted_source FROM component_entries WHERE id=?",String.class,component));
    assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE component_entries SET title='Mutated while pending' WHERE id=?",component));
    mvc.perform(body(post("/api/v1/admin/components/"+component+"/review"),"admin",Map.of("revision",2,"sourceHash","0".repeat(64),"decision","APPROVE","reason","A source hash mismatch must prevent this publication."))).andExpect(status().isConflict());
    response(approve(component));
    assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE component_releases SET source_sha256=? WHERE component_id=?","0".repeat(64),component));
    assertEquals(1,response(get("/api/v1/components/remix-"+component+"/versions")).path("items").size());
  }
  @Test void v45ToV46MigrationPreservesPendingSourceAndPublishedHistory()throws Exception{
    String schema="component_upgrade_"+UUID.randomUUID().toString().replace("-","");
    var source=Objects.requireNonNull(db.getDataSource()).unwrap(com.zaxxer.hikari.HikariDataSource.class);
    var flyway=org.flywaydb.core.Flyway.configure().dataSource(source.getJdbcUrl(),source.getUsername(),source.getPassword()).schemas(schema).defaultSchema(schema).target("28").load();
    try{
      flyway.migrate();
      try(var connection=java.sql.DriverManager.getConnection(source.getJdbcUrl(),source.getUsername(),source.getPassword())){
        var upgrade=new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));
        upgrade.execute("SET search_path = "+schema+",public");UUID owner=UUID.randomUUID();
        upgrade.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,'upgrade@example.test','unused',now())",owner);
        var catalogue=json.readTree(Objects.requireNonNull(getClass().getResourceAsStream("/catalog/components.json")));
        JsonNode recipe=null;for(var entry:catalogue)if(entry.path("slug").asText().equals("portfolio-card"))recipe=entry;
        assertNotNull(recipe);String original=json.writeValueAsString(recipe);
        var ids=new LinkedHashMap<String,UUID>();
        for(String state:List.of("DRAFT","PENDING","ACTIVE","SUSPENDED")){
          UUID id=UUID.randomUUID();ids.put(state,id);
          upgrade.update("INSERT INTO component_entries(id,owner_id,recipe_slug,slug,title,summary,contribution,status) VALUES(?,?,'portfolio-card',?,'Original upgrade entry','Retain original source and context','Original attribution and explicit MIT consent',?)",id,owner,id.toString(),state);
          if(state.equals("ACTIVE"))upgrade.update("UPDATE component_entries SET published_source=?::jsonb,published_context=?::jsonb,published_at=now() WHERE id=?",original,"{\"title\":\"Original upgrade entry\",\"summary\":\"Retain original source and context\",\"contribution\":\"Original attribution and explicit MIT consent\"}",id);
        }
        org.flywaydb.core.Flyway.configure().dataSource(source.getJdbcUrl(),source.getUsername(),source.getPassword()).schemas(schema).defaultSchema(schema).load().migrate();
        assertNull(upgrade.queryForObject("SELECT submitted_source FROM component_entries WHERE id=?",String.class,ids.get("DRAFT")));
        for(String state:List.of("PENDING","SUSPENDED")){
          var preserved=json.readTree(upgrade.queryForObject("SELECT submitted_source::text FROM component_entries WHERE id=?",String.class,ids.get(state)));
          assertEquals(recipe.path("files"),preserved.path("files"));assertEquals(recipe.path("sha256"),preserved.path("sha256"));
        }
        var released=json.readTree(upgrade.queryForObject("SELECT source::text FROM component_releases WHERE component_id=?",String.class,ids.get("ACTIVE")));
        assertEquals(recipe,released);assertEquals(1,upgrade.queryForObject("SELECT count(*) FROM component_releases",Integer.class));
        assertThrows(org.springframework.dao.DataAccessException.class,()->upgrade.update("UPDATE component_entries SET title='Unreviewed migration mutation' WHERE id=?",ids.get("PENDING")));
        assertThrows(org.springframework.dao.DataAccessException.class,()->upgrade.update("UPDATE component_releases SET source_sha256=?","0".repeat(64)));
        assertEquals(0,upgrade.queryForObject("SELECT count(*) FROM component_previews",Integer.class));
      }
    }finally{db.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");}
  }
  @Test void componentReviewWaitsForConcurrentMfaRevocationAndRechecksBeforePublication()throws Exception{
    UUID id=draft();submit(id);var request=approve(id);
    var workers=Executors.newSingleThreadExecutor();var pending=new java.util.concurrent.atomic.AtomicReference<Future<Integer>>();
    try{
      new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status->{
        db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?",admin);
        pending.set(workers.submit(()->mvc.perform(request).andReturn().getResponse().getStatus()));
        boolean waiting=false;
        for(int i=0;i<500;i++){
          if(db.queryForObject("SELECT count(*) FROM pg_locks WHERE locktype='transactionid' AND NOT granted",Integer.class)>0){waiting=true;break;}
          if(pending.get().isDone())break;
          try{Thread.sleep(10);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}
        }
        assertTrue(waiting,"Review must serialize against current administrator session authority.");
        assertEquals("PENDING",db.queryForObject("SELECT status FROM component_entries WHERE id=?",String.class,id));
      });
      assertEquals(403,pending.get().get(10,TimeUnit.SECONDS));
      assertEquals(0,db.queryForObject("SELECT count(*) FROM component_releases WHERE component_id=?",Integer.class,id));
    }finally{workers.shutdownNow();}
  }
  @Test void savedComponentsArePrivateIdempotentAndUnavailableAfterWithdrawal() throws Exception {
    UUID component=draft();submit(component);response(approve(component));String slug="remix-"+component;
    response(body(post("/api/v1/components/"+slug+"/save"),"other",Map.of()));
    response(body(post("/api/v1/components/"+slug+"/save"),"other",Map.of()));
    assertEquals(1,response(as(get("/api/v1/me/saved-components"),"other")).path("items").size());
    assertEquals(0,response(as(get("/api/v1/me/saved-components"),"builder")).path("items").size());
    assertEquals(1,response(as(get("/api/v1/me/export"),"other")).path("savedComponents").size());
    assertEquals(0,response(as(get("/api/v1/me/export"),"builder")).path("savedComponents").size());
    mvc.perform(get("/api/v1/me/saved-components")).andExpect(status().isUnauthorized());
    response(body(post("/api/v1/me/components/"+component+"/withdraw"),"builder",Map.of()));
    mvc.perform(get("/api/v1/components/"+slug)).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/components/"+slug+"/versions")).andExpect(status().isNotFound());
    var saved=response(as(get("/api/v1/me/saved-components"),"other")).path("items").get(0);
    assertFalse(saved.path("available").asBoolean());assertFalse(saved.has("title"));assertFalse(saved.has("files"));
    response(body(delete("/api/v1/components/"+slug+"/save"),"other",Map.of()));
    response(body(delete("/api/v1/components/"+slug+"/save"),"other",Map.of()));
    assertEquals(0,response(as(get("/api/v1/me/saved-components"),"other")).path("items").size());
  }

  byte[] contributionZip(String marker)throws Exception {
    var seed=json.readTree(Objects.requireNonNull(getClass().getResourceAsStream("/catalog/components.json"))).findValues("files").get(0);
    var output=new java.io.ByteArrayOutputStream();try(var zip=new java.util.zip.ZipOutputStream(output)){
      for(String name:List.of("index.html","README.md","LICENSE")){zip.putNextEntry(new java.util.zip.ZipEntry(name));String content=seed.path(name).asText();if(name.equals("index.html"))content=content.replace("</body>","<p>"+marker+"</p></body>");zip.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}
    }return output.toByteArray();
  }
  MockHttpServletRequestBuilder sourceUpload(UUID id,String version,byte[] bytes,String actor){return as(multipart("/api/v1/me/components/"+id+"/source").file(new org.springframework.mock.web.MockMultipartFile("file","component.zip","application/zip",bytes)).param("version",version).param("scenario","Try this original synthetic interaction.").param("rightsConsent","true"),actor).header("Origin","http://localhost:3000").header("X-Requested-With","getlancer");}
  @Test void privateUploadedSourceExportsRequireRecentAdminMfaAndAuditTheCurrentActor()throws Exception{
    UUID id=draft();response(sourceUpload(id,"1.0.0",contributionZip("Private original source"),"builder"));
    String adminPath="/api/v1/admin/components/"+id,ownerPath="/api/v1/me/components/"+id;
    db.update("UPDATE sessions SET issued_at=now()-interval '16 minutes' WHERE user_id=?",admin);
    mvc.perform(as(get(adminPath),"admin")).andExpect(status().isForbidden());
    db.update("UPDATE sessions SET issued_at=now() WHERE user_id=?",admin);
    response(as(get(adminPath),"admin"));response(as(get(ownerPath),"builder"));
    for(var actor:Map.of(admin,adminPath,builder,ownerPath).entrySet())assertEquals(1,db.queryForObject("SELECT count(*) FROM security_audit_events WHERE actor_id=? AND event='PRIVATE_EXPORT' AND target=? AND result='SUCCESS'",Integer.class,actor.getKey(),actor.getValue()));
    mvc.perform(as(get(ownerPath),"other")).andExpect(status().isNotFound());
    assertFalse(json.writeValueAsString(db.queryForList("SELECT event,target,result,request_id FROM security_audit_events WHERE event='PRIVATE_EXPORT'")).contains("Private original source"));
  }
  @Test void directConcurrentReleaseInsertsCannotReuseTheSameVersion()throws Exception{
    UUID id=draft();submit(id);response(approve(id));var source=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(db.queryForObject("SELECT source::text FROM component_releases WHERE component_id=?",String.class,id));source.put("version","2.0.0");
    String snapshot=json.writeValueAsString(source);var workers=Executors.newSingleThreadExecutor();var pending=new java.util.concurrent.atomic.AtomicReference<Future<Boolean>>();
    try{
      new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status->{
        db.update("INSERT INTO component_releases(component_id,revision,source,context,source_sha256) VALUES(?,200,?::jsonb,'{}'::jsonb,?)",id,snapshot,source.path("sha256").asText());
        pending.set(workers.submit(()->{try{db.update("INSERT INTO component_releases(component_id,revision,source,context,source_sha256) VALUES(?,201,?::jsonb,'{}'::jsonb,?)",id,snapshot,source.path("sha256").asText());return true;}catch(org.springframework.dao.DataAccessException expected){return false;}}));
        boolean waiting=false;for(int i=0;i<500;i++){
          if(db.queryForObject("SELECT count(*) FROM pg_locks WHERE locktype='transactionid' AND NOT granted",Integer.class)>0){waiting=true;break;}if(pending.get().isDone())break;
          try{Thread.sleep(10);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}
        }
        assertTrue(waiting,"The database version invariant must serialize direct concurrent inserts.");
      });
      assertFalse(pending.get().get(10,TimeUnit.SECONDS));assertEquals(1,db.queryForObject("SELECT count(*) FROM component_releases WHERE component_id=? AND source->>'version'='2.0.0'",Integer.class,id));
    }finally{workers.shutdownNow();}
  }
  @Test void uploadedSourceChangingVersionsRequireFreshReviewAndFreezeAllFiles()throws Exception{
    UUID id=draft();byte[] first=contributionZip("Original custom source");response(sourceUpload(id,"1.0.0",first,"builder"));
    mvc.perform(sourceUpload(id,"1.0.1",first,"other")).andExpect(status().isNotFound());
    var draftSource=response(as(get("/api/v1/me/components/"+id),"builder"));assertTrue(draftSource.path("uploaded").asBoolean());assertFalse(draftSource.has("archiveBase64"));
    var privateExport=response(as(get("/api/v1/me/export"),"builder"));
    assertEquals(draftSource.path("files"),privateExport.path("components").get(0).path("draft_source").path("files"));
    assertFalse(privateExport.path("components").get(0).path("draft_source").has("archiveBase64"));
    assertTrue(response(as(get("/api/v1/me/export"),"other")).path("components").isEmpty());
    submit(id);assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE component_entries SET draft_source=jsonb_set(draft_source,'{files,README.md}','\"changed\"') WHERE id=?",id));
    mvc.perform(sourceUpload(id,"1.0.1",first,"builder")).andExpect(status().isConflict());response(approve(id));
    var published=response(get("/api/v1/components/remix-"+id));assertEquals(draftSource.path("files"),published.path("files"));assertFalse(published.has("archiveBase64"));
    response(body(post("/api/v1/me/components/"+id+"/new-version"),"builder",Map.of()));
    mvc.perform(sourceUpload(id,"1.0.0",first,"builder")).andExpect(status().isConflict());response(sourceUpload(id,"1.0.1",contributionZip("Changed custom source"),"builder"));
    assertEquals(published.path("files"),response(get("/api/v1/components/remix-"+id)).path("files"));submit(id);response(approve(id));
    assertNotEquals(published.path("sha256"),response(get("/api/v1/components/remix-"+id)).path("sha256"));
    assertEquals(2,response(get("/api/v1/components/remix-"+id+"/versions")).path("items").size());
    assertEquals(published.path("sha256").asText(),db.queryForObject("SELECT source_sha256 FROM component_releases WHERE component_id=? ORDER BY revision LIMIT 1",String.class,id));
    var releasedExport=response(as(get("/api/v1/me/export"),"builder")).path("componentReleases");
    assertEquals(2,releasedExport.size());assertEquals("1.0.0",releasedExport.get(0).path("source").path("version").asText());assertEquals("1.0.1",releasedExport.get(1).path("source").path("version").asText());
    for(var release:releasedExport){assertEquals(id.toString(),release.path("component_id").asText());assertTrue(release.path("source").path("files").has("index.html"));assertFalse(release.path("source").has("archiveBase64"));}
    assertTrue(response(as(get("/api/v1/me/export"),"other")).path("componentReleases").isEmpty());
  }
  @Test void controlledComponentPreviewRetriesExactIdentityAndChecksCurrentAuthority()throws Exception{
    UUID id=draft();response(sourceUpload(id,"1.0.0",contributionZip("Free reviewed preview"),"builder"));submit(id);response(approve(id));
    previewPublisher.failPutAfter=true;mvc.perform(body(post("/api/v1/me/components/"+id+"/preview"),"builder",Map.of())).andExpect(status().isBadGateway());
    UUID deployment=db.queryForObject("SELECT deployment_id FROM component_previews WHERE component_id=?",UUID.class,id);
    var expiry=db.queryForObject("SELECT expires_at FROM component_previews WHERE component_id=?",java.sql.Timestamp.class,id);
    mvc.perform(get("/api/v1/hosting/gateway/"+deployment).header("X-GetLancer-Demo-Gateway",com.getlancer.hosting.HostingPublisherFixture.GATEWAY)).andExpect(jsonPath("$.allowed").value(false));
    previewPublisher.failPutAfter=false;response(body(post("/api/v1/me/components/"+id+"/preview"),"builder",Map.of()));
    assertEquals(1,previewPublisher.records.size());assertEquals(expiry,db.queryForObject("SELECT expires_at FROM component_previews WHERE deployment_id=?",java.sql.Timestamp.class,deployment));
    assertTrue(response(get("/api/v1/components/remix-"+id+"/preview")).path("available").asBoolean());
    mvc.perform(get("/api/v1/hosting/gateway/"+deployment)).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/hosting/gateway/"+deployment).header("X-GetLancer-Demo-Gateway",com.getlancer.hosting.HostingPublisherFixture.GATEWAY)).andExpect(jsonPath("$.preview").value("COMPONENT"));
    assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE component_previews SET expires_at=now() WHERE deployment_id=?",deployment));
    for(String sql:List.of("UPDATE users SET account_status='SUSPENDED' WHERE id=?","UPDATE developer_profiles SET approval_status='PROFILE_PENDING' WHERE user_id=?")){
      db.update(sql,builder);assertFalse(response(get("/api/v1/components/remix-"+id+"/preview")).path("available").asBoolean());
      db.update("UPDATE users SET account_status='ACTIVE' WHERE id=?",builder);db.update("UPDATE developer_profiles SET approval_status='APPROVED' WHERE user_id=?",builder);
    }
    db.update("DELETE FROM user_roles WHERE user_id=? AND role='DEVELOPER'",builder);assertFalse(response(get("/api/v1/components/remix-"+id+"/preview")).path("available").asBoolean());mvc.perform(get("/api/v1/components/remix-"+id)).andExpect(status().isNotFound());db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER')",builder);
    response(body(post("/api/v1/me/components/"+id+"/new-version"),"builder",Map.of()));assertFalse(response(get("/api/v1/components/remix-"+id+"/preview")).path("available").asBoolean());
    response(sourceUpload(id,"1.0.1",contributionZip("New preview"),"builder"));submit(id);response(approve(id));
    mvc.perform(get("/api/v1/hosting/gateway/"+deployment).header("X-GetLancer-Demo-Gateway",com.getlancer.hosting.HostingPublisherFixture.GATEWAY)).andExpect(jsonPath("$.allowed").value(false));
    response(body(post("/api/v1/me/components/"+id+"/preview"),"builder",Map.of()));response(body(post("/api/v1/me/components/"+id+"/withdraw"),"builder",Map.of()));assertFalse(response(get("/api/v1/components/remix-"+id+"/preview")).path("available").asBoolean());
    assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE component_previews SET state='READY' WHERE component_id=?",id));
    submit(id);response(approve(id));assertFalse(response(get("/api/v1/components/remix-"+id+"/preview")).path("available").asBoolean());mvc.perform(body(post("/api/v1/me/components/"+id+"/preview"),"builder",Map.of())).andExpect(status().isConflict());

  }

  @Test void moderationDisplaysExactlyTheSourceValidatedForArchivedAndSuspendedReleases()throws Exception{
    UUID id=draft();response(sourceUpload(id,"1.0.0",contributionZip("Published A"),"builder"));submit(id);response(approve(id));
    response(body(post("/api/v1/me/components/"+id+"/new-version"),"builder",Map.of()));response(sourceUpload(id,"1.0.1",contributionZip("Draft B"),"builder"));response(body(post("/api/v1/me/components/"+id+"/archive"),"builder",Map.of()));
    response(body(patch("/api/v1/me/components/"+id),"builder",Map.of("recipeSlug","portfolio-card","title","Private unsubmitted title","summary","Private unsubmitted summary for the next release","contribution","Private unsubmitted contribution text that cannot be published by moderation.","rightsConsent",true)));
    response(body(post("/api/v1/me/components/"+id+"/archive"),"builder",Map.of()));
    assertEquals("Private unsubmitted title",response(as(get("/api/v1/me/components/"+id),"builder")).path("title").asText());
    var shown=response(as(get("/api/v1/admin/components/"+id),"admin"));assertEquals("Original recipe selection",shown.path("title").asText());response(body(post("/api/v1/admin/components/"+id+"/review"),"admin",Map.of("revision",shown.path("revision").asLong(),"sourceHash",shown.path("sourceHash").asText(),"decision","SUSPEND","reason","Suspend this exact archived record after reviewing its source.")));
    var suspended=response(as(get("/api/v1/admin/components/"+id),"admin"));assertEquals("1.0.0",suspended.path("version").asText());assertEquals(shown.path("title"),suspended.path("title"));response(approve(id));var restored=response(get("/api/v1/components/remix-"+id));assertEquals(suspended.path("files"),restored.path("files"));assertEquals(shown.path("title"),restored.path("title"));
    var publicSearch=response(get("/api/v1/components").param("builder","builder").param("q","Original recipe selection"));
    assertEquals(1,publicSearch.path("totalItems").asInt());assertEquals(restored.path("title"),publicSearch.path("items").get(0).path("title"));
    for(String privateTerm:List.of("Private unsubmitted title","Private unsubmitted summary")){
      var privateSearch=response(get("/api/v1/components").param("builder","builder").param("q",privateTerm));
      assertEquals(0,privateSearch.path("totalItems").asInt(),"Unsubmitted draft text must not affect public search");assertTrue(privateSearch.path("items").isEmpty());
    }
    response(body(post("/api/v1/me/components/"+id+"/new-version"),"builder",Map.of()));assertEquals("Private unsubmitted title",response(as(get("/api/v1/me/components/"+id),"builder")).path("title").asText());submit(id);response(approve(id));assertEquals("Private unsubmitted title",response(get("/api/v1/components/remix-"+id)).path("title").asText());
    assertEquals(1,response(get("/api/v1/components").param("builder","builder").param("q","Private unsubmitted title")).path("totalItems").asInt());
  }

  @Test void restorationAndContextOnlyReviewReuseTheSameReleaseAndFixedPreviewExpiry()throws Exception{
    UUID id=draft();submit(id);response(approve(id));response(body(post("/api/v1/me/components/"+id+"/preview"),"builder",Map.of()));
    var first=db.queryForMap("SELECT deployment_id,expires_at FROM component_previews WHERE component_id=?",id);
    response(body(post("/api/v1/me/components/"+id+"/new-version"),"builder",Map.of()));submit(id);response(approve(id));response(body(post("/api/v1/me/components/"+id+"/preview"),"builder",Map.of()));
    assertEquals(1,db.queryForObject("SELECT count(*) FROM component_releases WHERE component_id=?",Integer.class,id));assertEquals(first,db.queryForMap("SELECT deployment_id,expires_at FROM component_previews WHERE component_id=?",id));assertEquals(1,previewPublisher.puts.get());
    var shown=response(as(get("/api/v1/admin/components/"+id),"admin"));response(body(post("/api/v1/admin/components/"+id+"/review"),"admin",Map.of("revision",shown.path("revision").asLong(),"sourceHash",shown.path("sourceHash").asText(),"decision","SUSPEND","reason","Suspend the reviewed component without allocating a release.")));response(approve(id));
    assertEquals(1,db.queryForObject("SELECT count(*) FROM component_releases WHERE component_id=?",Integer.class,id));assertEquals(first,db.queryForMap("SELECT deployment_id,expires_at FROM component_previews WHERE component_id=?",id));
  }

}
