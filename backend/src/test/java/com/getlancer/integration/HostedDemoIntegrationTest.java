package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.*;
import com.getlancer.commerce.CommerceStorage;
import com.getlancer.hosting.*;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Real PostgreSQL transactions and real provider HTTP. Private S3 transport and startup admin bootstrap are replaced to isolate synthetic fixtures. */
@org.springframework.test.context.ContextConfiguration(initializers=TestDatabaseGuard.class)
@SpringBootTest(properties={"app.environment=local","app.jobs-enabled=false","app.admin-email=admin@example.test","app.admin-password=","app.admin-totp=","spring.config.import=",
 "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}","spring.datasource.username=${TEST_DB_USERNAME:postgres}","spring.datasource.password=${TEST_DB_PASSWORD:}",
 "spring.datasource.hikari.schema=getlancer_test","spring.flyway.default-schema=getlancer_test","spring.flyway.schemas=getlancer_test","app.origin=http://localhost:3000","app.secure-cookie=false","app.storage.access-key=","app.storage.secret-key=","app.rate-limit=1000","app.discovery-rate-limit=1000"})
@AutoConfigureMockMvc
class HostedDemoIntegrationTest {
  static final HostingPublisherFixture provider=new HostingPublisherFixture();
  @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){registry.add("app.hosting.enabled",()->true);registry.add("app.hosting.publisher-url",provider::url);registry.add("app.hosting.publisher-secret",()->HostingPublisherFixture.SECRET);registry.add("app.hosting.gateway-secret",()->HostingPublisherFixture.GATEWAY);registry.add("app.hosting.public-url-template",()->HostingPublisherFixture.TEMPLATE);registry.add("app.hosting.expiry-days",()->7);registry.add("app.hosting.timeout-ms",()->2000);}
  @AfterAll static void close(){provider.close();}
  @Autowired JdbcTemplate db;@Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired HostingRepository repo;
  @MockBean CommerceStorage storage;
  @MockBean com.getlancer.config.Bootstrap bootstrap;
  UUID owner,outsider,admin,product,demo;byte[] zip;
  UUID user(String token,boolean approved){UUID id=UUID.randomUUID();db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",id,token+"@example.test");db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER')",id);db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,?,?)",id,token,token,approved?"APPROVED":"DRAFT");db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",Support.hash(token),id);return id;}
  MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request,String token){return request.cookie(new Cookie("gl_session",token));}
  MockHttpServletRequestBuilder body(String path,String token,String payload){return as(post(path),token).header("Origin","http://localhost:3000").header("X-Requested-With","getlancer").contentType("application/json").content(payload);}
  MockHttpServletRequestBuilder uploadRequest(String release,byte[] bytes,boolean consent){return multipart("/api/v1/me/hosting").file(new MockMultipartFile("file","frontend.zip","application/zip",bytes)).param("productId",product.toString()).param("title","Reviewed static demo").param("version",release).param("rightsConsent",Boolean.toString(consent)).header("Origin","http://localhost:3000").header("X-Requested-With","getlancer").cookie(new Cookie("gl_session","owner"));}
  UUID upload(String release)throws Exception{return UUID.fromString(json.readTree(mvc.perform(uploadRequest(release,zip,true)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asText());}
  String own(String action){return "/api/v1/me/hosting/"+demo+"/"+action;}
  String review(String action){return "{\"action\":\""+action+"\",\"reason\":\"Reviewed exact built package and third-party publication rights.\",\"rightsReviewed\":true,\"packageReviewed\":true}";}
  void submit()throws Exception{mvc.perform(body(own("submit"),"owner","{\"rightsConsent\":true}")).andExpect(status().isOk());}
  void approve()throws Exception{submit();mvc.perform(as(get("/api/v1/admin/hosting/"+demo+"/package"),"admin")).andExpect(status().isOk());mvc.perform(body("/api/v1/admin/hosting/"+demo+"/review","admin",review("APPROVE"))).andExpect(status().isOk());}
  void deploy()throws Exception{mvc.perform(body(own("deploy"),"owner","{}")).andExpect(status().isOk()).andExpect(jsonPath("$.deploymentState").value("READY"));}
  UUID deployment(){return db.queryForObject("SELECT deployment_id FROM hosted_demos WHERE id=?",UUID.class,demo);}
  void gateway(boolean allowed)throws Exception{mvc.perform(get("/api/v1/hosting/gateway/"+deployment()).header("X-GetLancer-Demo-Gateway",HostingPublisherFixture.GATEWAY)).andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(allowed)).andExpect(header().string("Cache-Control","no-store"));}
  @BeforeEach void prepare()throws Exception{provider.reset();reset(storage);db.execute("TRUNCATE users CASCADE");db.execute("TRUNCATE rate_buckets");owner=user("owner",true);outsider=user("outsider",false);admin=user("admin",false);db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')",admin);db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?",admin);product=UUID.randomUUID();db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,approval_status,lifecycle_status,visibility,repository_url) VALUES(?,?,?,'Approved proof','Approved public proof','Complete proof product description','SAAS','CRM','React','Built complete proof','APPROVED','ACTIVE','PUBLIC','https://github.com/example/proof')",product,owner,product.toString());db.update("INSERT INTO repository_verifications(product_id,repository_url,challenge,status,reviewed_at,reviewer_id) VALUES(?,'https://github.com/example/proof','private-challenge','VERIFIED',now(),?)",product,admin);zip=HostingTestArchives.built();when(storage.put(any(),any(),anyString())).thenAnswer(i->"commerce/"+i.getArgument(0)+"/"+UUID.randomUUID()+".zip");when(storage.read(anyString(),anyString(),anyInt())).thenReturn(zip);demo=upload("1.0.0");provider.observer=operation->{if(operation.equals("PUT"))provider.committed=db.queryForObject("SELECT count(*) FROM hosted_demos WHERE id=? AND deployment_id IS NOT NULL AND deployment_state='CREATING'",Integer.class,demo)==1;if(operation.equals("DELETE"))provider.committed=db.queryForObject("SELECT count(*) FROM hosted_demos WHERE id=? AND desired_state='WITHDRAWN' AND deployment_state='DELETE_PENDING'",Integer.class,demo)==1;};}
  @Test void configSourcesSafeCamelCasePagesDownloadsAndOwnershipAreEnforced()throws Exception{
    mvc.perform(get("/api/v1/hosting/config")).andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.maxFiles").value(256)).andExpect(jsonPath("$.maxExpandedBytes").value(10485760));
    mvc.perform(as(get("/api/v1/me/hosting/sources?size=1"),"owner")).andExpect(jsonPath("$.items[0].id").value(product.toString())).andExpect(jsonPath("$.size").value(1));mvc.perform(as(get("/api/v1/me/hosting?size=1"),"owner")).andExpect(jsonPath("$.items[0].archiveSha256").isString()).andExpect(jsonPath("$.items[0].files[0].sizeBytes").isNumber()).andExpect(jsonPath("$.items[0].rightsConsentAt").isString()).andExpect(jsonPath("$.items[0].url").isEmpty());
    String safe=mvc.perform(as(get("/api/v1/me/hosting/"+demo),"owner")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertFalse(safe.contains("storage_key"));assertFalse(safe.contains("storageKey"));assertFalse(safe.contains("contentBase64"));assertFalse(safe.contains("private-challenge"));assertFalse(safe.contains(HostingPublisherFixture.SECRET));
    mvc.perform(as(get("/api/v1/me/hosting/"+demo),"outsider")).andExpect(status().isNotFound());mvc.perform(get("/api/v1/me/hosting/"+demo)).andExpect(status().isUnauthorized());mvc.perform(as(get(own("package")),"owner")).andExpect(status().isOk()).andExpect(content().bytes(zip)).andExpect(header().string("Content-Type","application/zip"));mvc.perform(as(get(own("package")),"outsider")).andExpect(status().isNotFound());mvc.perform(as(get("/api/v1/admin/hosting"),"owner")).andExpect(status().isForbidden());
    String export=json.writeValueAsString(repo.export(owner));assertTrue(export.contains("archiveSha256"));assertFalse(export.contains("storage_key"));assertFalse(repo.blocksAccountDeletion(owner));
  }
  @Test void missingRequiredHostingMetadataReturnsValidationInsteadOfServerError()throws Exception{
    clearInvocations(storage);
    for(String missing:List.of("productId","title","version","rightsConsent")){
      var request=multipart("/api/v1/me/hosting").file(new MockMultipartFile("file","frontend.zip","application/zip",zip));
      var fields=Map.of("productId",product.toString(),"title","Reviewed static demo","version","2.0.0","rightsConsent","true");
      fields.forEach((key,value)->{if(!key.equals(missing))request.param(key,value);});
      request.header("Origin","http://localhost:3000").header("X-Requested-With","getlancer").cookie(new Cookie("gl_session","owner"));
      mvc.perform(request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
    verify(storage,never()).put(any(),any(),anyString());
  }
  String retainedCare(UUID party,UUID payer)throws Exception{
    String suffix=UUID.randomUUID().toString().replace("-","");
    UUID counterparty=user("export-"+suffix,false);
    UUID inquiry=UUID.randomUUID(),engagement=UUID.randomUUID(),offer=UUID.randomUUID(),subscription=UUID.randomUUID(),period=UUID.randomUUID();
    db.update("INSERT INTO inquiries(id,reference_product_id,developer_user_id,client_email,client_name,request_type,description,budget_band,timeline_band,idempotency_key,request_hash,current_status,email_confirmed_at) VALUES(?,?,?,'export@example.test','Export buyer','CUSTOMIZE','Retained export fixture care','NEED_ESTIMATE','FLEXIBLE',?,'fixture','COMPLETED',now())",inquiry,product,party,UUID.randomUUID());
    db.update("INSERT INTO delivery_engagements(id,source_inquiry_id,buyer_user_id,builder_user_id,title,created_by,status) VALUES(?,?,?,?,'Retained care export',?,'COMPLETED')",engagement,inquiry,counterparty,party,party);
    db.update("INSERT INTO maintenance_offers(id,engagement_id,revision,status,title,scope,terms,amount_minor,requests_per_cycle,response_hours,total_cycles,seller_id,seller_consented_at,buyer_id,buyer_consented_at,digest) VALUES(?,?,1,'ACCEPTED','Retained care','Bounded retained support and fixes','Retained maintenance terms require distinct billing consent.',10000,1,24,12,?,now(),?,now(),repeat('a',64))",offer,engagement,party,counterparty);
    db.update("INSERT INTO maintenance_subscriptions(id,engagement_id,offer_id,payer_id,request_key,amount_minor,currency,requests_per_cycle,response_hours,total_cycles,digest,account_id,mode,status,creation_step,plan_state,subscription_state,provider_plan_id,provider_subscription_id) VALUES(?,?,?,?,?,10000,'INR',1,24,12,repeat('a',64),'acc_exportfixture','test','ACTIVE','COMPLETE','CONFIRMED','CONFIRMED',?,?)",subscription,engagement,offer,payer,UUID.randomUUID(),"plan_"+suffix,"sub_"+suffix);
    db.update("INSERT INTO maintenance_periods(id,subscription_id,provider_invoice_id,payment_id,order_id,amount_minor,currency,period_start,period_end,transfer_key) VALUES(?,?,?,?,?,10000,'INR',now()-interval '1 day',now()+interval '29 days',?)",period,subscription,"inv_"+suffix,"pay_"+suffix,"order_"+suffix,UUID.randomUUID());
    db.update("INSERT INTO maintenance_provider_disputes(id,period_id,status,deducted_minor) VALUES(?,?,'open',1000)","disp_"+suffix,period);
    db.update("INSERT INTO maintenance_refunds(id,period_id,amount_minor,status) VALUES(?,?,500,'pending')","rfnd_"+suffix,period);
    // Exercise every independently supported event association without exporting its payload.
    db.update("INSERT INTO maintenance_webhook_events(event_id,payload_hash,event_kind,subscription_id,payment_id,dispute_id,refund_id) VALUES(?,repeat('b',64),'subscription.updated',?,null,null,null),(?,repeat('c',64),'payment.captured',null,?,null,null),(?,repeat('d',64),'payment.dispute.created',null,null,?,null),(?,repeat('e',64),'refund.created',null,null,null,?)","evt_sub_"+suffix,"sub_"+suffix,"evt_pay_"+suffix,"pay_"+suffix,"evt_disp_"+suffix,"disp_"+suffix,"evt_rfnd_"+suffix,"rfnd_"+suffix);
    return suffix;
  }
  @Test void maintenanceExportIncludesRetainedDisputesAndEventIdentitiesOnlyForOwnedAccounts()throws Exception{
    String mine=retainedCare(owner,admin),theirs=retainedCare(outsider,outsider);
    for(String actor:List.of("owner","admin","outsider","export-"+mine,"export-"+theirs)){
      boolean other=actor.equals("outsider")||actor.equals("export-"+theirs);
      String suffix=other?theirs:mine,foreign=other?mine:theirs;
      var exported=json.readTree(mvc.perform(as(get("/api/v1/me/export"),actor)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
      assertEquals(1,exported.path("maintenanceDisputes").size());
      assertEquals("disp_"+suffix,exported.path("maintenanceDisputes").get(0).path("id").asText());
      assertEquals(1000,exported.path("maintenanceDisputes").get(0).path("deducted_minor").asLong());
      assertEquals(4,exported.path("maintenanceEvents").size());
      String events=exported.path("maintenanceEvents").toString();
      assertTrue(events.contains("evt_rfnd_"+suffix));assertFalse(events.contains(foreign));
      for(JsonNode event:exported.path("maintenanceEvents")){
        assertEquals(Set.of("event_id","payload_hash","event_kind","received_at","processed_at","attempts"),json.convertValue(event,Map.class).keySet());
      }
      assertFalse(exported.toString().contains("private-challenge"));assertFalse(exported.toString().contains(HostingPublisherFixture.SECRET));
    }
  }
  @Test void consentZipValidationCurrentProofAndPostUploadRevocationPreventUnlinkedRecords()throws Exception{
    clearInvocations(storage);mvc.perform(uploadRequest("2.0.0","bad zip".getBytes(StandardCharsets.UTF_8),true)).andExpect(status().isBadRequest());mvc.perform(uploadRequest("2.0.0",zip,false)).andExpect(status().isBadRequest());verify(storage,never()).put(any(),any(),anyString());
    db.update("UPDATE repository_verifications SET status='REJECTED' WHERE product_id=?",product);mvc.perform(uploadRequest("2.0.0",zip,true)).andExpect(status().isConflict());mvc.perform(as(get("/api/v1/me/hosting/sources"),"owner")).andExpect(jsonPath("$.items.length()").value(0));db.update("UPDATE repository_verifications SET status='VERIFIED' WHERE product_id=?",product);
    when(storage.put(any(),any(),anyString())).thenAnswer(i->{db.update("UPDATE users SET account_status='SUSPENDED' WHERE id=?",owner);return "commerce/"+i.getArgument(0)+"/"+UUID.randomUUID()+".zip";});mvc.perform(uploadRequest("2.0.0",zip,true)).andExpect(status().isUnauthorized());verify(storage).discardUnlinked(anyString());assertEquals(1,db.queryForObject("SELECT count(*) FROM hosted_demos",Integer.class));
  }
  @Test void draftWithdrawalNeverCallsProviderAndDuplicateImmutableVersionCompensatesUpload()throws Exception{
    clearInvocations(storage);mvc.perform(uploadRequest("1.0.0",zip,true)).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("HOSTING_VERSION_EXISTS"));verify(storage).discardUnlinked(anyString());mvc.perform(body(own("withdraw"),"owner","{}")).andExpect(status().isOk()).andExpect(jsonPath("$.deploymentState").value("DELETED"));assertEquals(0,provider.deletes.get());mvc.perform(body(own("submit"),"owner","{\"rightsConsent\":true}")).andExpect(status().isConflict());
  }
  @Test void immutableBundleRightsConsentDeploymentIdentityAndAuditAreDatabaseProtected()throws Exception{
    for(String column:List.of("archive_sha256=repeat('a',64)","manifest_sha256=repeat('a',64)","storage_key='changed'","rights_consent_at=now()+interval '1 hour'","title='changed'","files='[]'::jsonb"))assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE hosted_demos SET "+column+" WHERE id=?",demo));assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("DELETE FROM hosted_demos WHERE id=?",demo));assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE hosting_audit SET detail='changed'"));approve();deploy();assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE hosted_demos SET deployment_id=? WHERE id=?",UUID.randomUUID(),demo));assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE hosted_demos SET expires_at=now()+interval '1 day' WHERE id=?",demo));
  }
  @Test void actualPackageDownloadMfaAndRightsReviewMustPrecedeApproval()throws Exception{
    mvc.perform(body(own("submit"),"owner","{}")).andExpect(status().isBadRequest());submit();String path="/api/v1/admin/hosting/"+demo+"/review";mvc.perform(body(path,"admin",review("APPROVE"))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("PACKAGE_REVIEW_REQUIRED"));mvc.perform(body(path,"admin","{\"action\":\"APPROVE\",\"reason\":\"Reviewed package.\"}")).andExpect(status().isBadRequest());db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?",admin);mvc.perform(as(get("/api/v1/admin/hosting/"+demo+"/package"),"admin")).andExpect(status().isForbidden());mvc.perform(body(path,"admin",review("APPROVE"))).andExpect(status().isForbidden());db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?",admin);mvc.perform(as(get("/api/v1/admin/hosting/"+demo+"/package"),"admin")).andExpect(status().isOk());mvc.perform(body(path,"admin",review("APPROVE"))).andExpect(status().isOk());
  }
  @Test void reservationIsCommittedBeforeRealHttpAndRepeatDeployKeepsIdentity()throws Exception{
    mvc.perform(body(own("deploy"),"owner","{}")).andExpect(status().isConflict());approve();deploy();assertTrue(provider.committed);UUID reserved=deployment();assertTrue(repo.blocksAccountDeletion(owner));gateway(true);mvc.perform(get("/api/v1/hosting/products/"+product)).andExpect(jsonPath("$.items[0].url").value(HostingPublisherFixture.TEMPLATE.replace("{id}",reserved.toString())));deploy();assertEquals(reserved,deployment());assertEquals(1,provider.puts.get());
  }
  @Test void lostSuccessfulResponseRecoversByGetWithoutNewPutOrIdentity()throws Exception{
    approve();provider.failPutAfter=true;mvc.perform(body(own("deploy"),"owner","{}")).andExpect(status().isBadGateway());UUID fixed=deployment();assertEquals("UNKNOWN",db.queryForObject("SELECT deployment_state FROM hosted_demos WHERE id=?",String.class,demo));gateway(false);mvc.perform(body(own("deploy"),"owner","{}")).andExpect(status().isConflict());provider.failPutAfter=false;mvc.perform(body(own("reconcile"),"owner","{}")).andExpect(status().isOk()).andExpect(jsonPath("$.deploymentState").value("READY"));assertEquals(fixed,deployment());assertEquals(1,provider.puts.get());assertEquals(1,provider.gets.get());gateway(true);
  }
  @Test void absentUnknownReservationRetriesOnlySameIdExactBytesAndFixedExpiry()throws Exception{
    approve();provider.failPutBefore=true;mvc.perform(body(own("deploy"),"owner","{}")).andExpect(status().isBadGateway());UUID fixed=deployment();var expiry=db.queryForObject("SELECT expires_at FROM hosted_demos WHERE id=?",java.sql.Timestamp.class,demo);provider.failPutBefore=false;mvc.perform(body(own("reconcile"),"owner","{}")).andExpect(status().isOk()).andExpect(jsonPath("$.deploymentState").value("READY"));assertEquals(fixed,deployment());assertEquals(expiry,db.queryForObject("SELECT expires_at FROM hosted_demos WHERE id=?",java.sql.Timestamp.class,demo));assertEquals(2,provider.puts.get());assertTrue(provider.committed);
  }
  @Test void absentRetryRechecksProofAndAuthorityAfterPrivatePackageRead()throws Exception{
    approve();provider.failPutBefore=true;mvc.perform(body(own("deploy"),"owner","{}")).andExpect(status().isBadGateway());provider.failPutBefore=false;when(storage.read(anyString(),anyString(),anyInt())).thenAnswer(i->{db.update("UPDATE developer_profiles SET approval_status='SUSPENDED' WHERE user_id=?",owner);return zip;});mvc.perform(body(own("reconcile"),"owner","{}")).andExpect(status().isForbidden());assertEquals(1,provider.puts.get());assertEquals("UNKNOWN",db.queryForObject("SELECT deployment_state FROM hosted_demos WHERE id=?",String.class,demo));
  }
  @Test void mismatchAndOversizedFactsNeverBecomePublicAndWithdrawalCreatesExactTombstone()throws Exception{
    approve();provider.mismatch=true;mvc.perform(body(own("deploy"),"owner","{}")).andExpect(status().isConflict());gateway(false);mvc.perform(body(own("reconcile"),"owner","{}")).andExpect(status().isConflict());provider.mismatch=false;provider.oversized=true;mvc.perform(body(own("reconcile"),"owner","{}")).andExpect(status().isBadGateway());provider.oversized=false;mvc.perform(body(own("withdraw"),"owner","{}")).andExpect(status().isOk()).andExpect(jsonPath("$.deploymentState").value("DELETED")).andExpect(jsonPath("$.desiredState").value("WITHDRAWN"));assertFalse(repo.blocksAccountDeletion(owner));gateway(false);assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE hosted_demos SET desired_state='PUBLISHED',deployment_state='READY' WHERE id=?",demo));
  }
  @Test void committedWithdrawalDeniesGatewayWhileDeleteIsStillWaiting()throws Exception{
    approve();deploy();provider.blockDelete=true;var executor=Executors.newSingleThreadExecutor();try{var pending=executor.submit(()->mvc.perform(body(own("withdraw"),"owner","{}")).andReturn().getResponse().getStatus());assertTrue(provider.entered.await(3,TimeUnit.SECONDS));assertTrue(provider.committed);gateway(false);assertTrue(repo.blocksAccountDeletion(owner));mvc.perform(get("/api/v1/hosting/products/"+product)).andExpect(jsonPath("$.items.length()").value(0));provider.release.countDown();assertEquals(200,pending.get(4,TimeUnit.SECONDS));assertFalse(repo.blocksAccountDeletion(owner));}finally{provider.release.countDown();executor.shutdownNow();}
  }
  @Test void delayedPutCannotResurrectAfterConcurrentWithdrawalAndDeletion()throws Exception{
    approve();provider.blockPut=true;var executor=Executors.newSingleThreadExecutor();try{var pending=executor.submit(()->mvc.perform(body(own("deploy"),"owner","{}")).andReturn().getResponse().getStatus());assertTrue(provider.entered.await(3,TimeUnit.SECONDS));UUID fixed=deployment();mvc.perform(body(own("withdraw"),"owner","{}")).andExpect(status().isOk()).andExpect(jsonPath("$.deploymentState").value("DELETED"));provider.release.countDown();assertEquals(502,pending.get(4,TimeUnit.SECONDS));assertEquals(fixed,deployment());assertEquals("DELETED",db.queryForObject("SELECT deployment_state FROM hosted_demos WHERE id=?",String.class,demo));assertTrue(provider.deleted.contains(fixed.toString()));gateway(false);}finally{provider.release.countDown();executor.shutdownNow();}
  }
  @Test void suspensionWhilePutIsInFlightCannotPublishAVisibleUrl()throws Exception{
    approve();provider.blockPut=true;var executor=Executors.newSingleThreadExecutor();try{var pending=executor.submit(()->mvc.perform(body(own("deploy"),"owner","{}")).andReturn().getResponse().getContentAsString());assertTrue(provider.entered.await(3,TimeUnit.SECONDS));mvc.perform(body("/api/v1/admin/hosting/"+demo+"/review","admin",review("SUSPEND"))).andExpect(status().isOk());provider.release.countDown();var result=json.readTree(pending.get(4,TimeUnit.SECONDS));assertEquals("SUSPENDED",result.path("status").asText());assertTrue(result.path("url").isNull());gateway(false);mvc.perform(get("/api/v1/hosting/products/"+product)).andExpect(jsonPath("$.items.length()").value(0));}finally{provider.release.countDown();executor.shutdownNow();}
  }
  @Test void simultaneousDifferentPackageDeploysRespectSerializedOwnerActiveLimit()throws Exception{
    approve();deploy();demo=upload("2.0.0");approve();deploy();demo=upload("3.0.0");approve();UUID third=demo;demo=upload("4.0.0");approve();UUID fourth=demo;provider.blockPut=true;var executor=Executors.newSingleThreadExecutor();try{var pending=executor.submit(()->mvc.perform(body("/api/v1/me/hosting/"+third+"/deploy","owner","{}")).andReturn().getResponse().getStatus());assertTrue(provider.entered.await(3,TimeUnit.SECONDS));mvc.perform(body("/api/v1/me/hosting/"+fourth+"/deploy","owner","{}")).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("HOSTING_ACTIVE_LIMIT"));provider.release.countDown();assertEquals(200,pending.get(4,TimeUnit.SECONDS));assertEquals(3,provider.puts.get());assertNull(deployment());}finally{provider.release.countDown();executor.shutdownNow();}
  }
  @Test void unknownDeleteBlocksClosureUntilExactDeleteReconciliationAndOwnerCanWithdrawAfterProfileRevocation()throws Exception{
    approve();deploy();db.update("UPDATE developer_profiles SET approval_status='SUSPENDED' WHERE user_id=?",owner);provider.failDelete=true;mvc.perform(body(own("withdraw"),"owner","{}")).andExpect(status().isBadGateway());assertEquals("DELETE_PENDING",db.queryForObject("SELECT deployment_state FROM hosted_demos WHERE id=?",String.class,demo));assertTrue(repo.blocksAccountDeletion(owner));gateway(false);provider.failDelete=false;mvc.perform(body(own("reconcile"),"owner","{}")).andExpect(status().isOk()).andExpect(jsonPath("$.deploymentState").value("DELETED"));assertFalse(repo.blocksAccountDeletion(owner));
  }
  @Test void everyGatewayReadUsesCurrentAccountProfileRoleProofStatusVisibilityAndExpiry()throws Exception{
    approve();deploy();gateway(true);mvc.perform(get("/api/v1/hosting/gateway/"+deployment())).andExpect(status().isForbidden());mvc.perform(get("/api/v1/hosting/gateway/"+deployment()).header("X-GetLancer-Demo-Gateway","wrong")).andExpect(status().isForbidden());
    db.update("UPDATE users SET account_status='SUSPENDED' WHERE id=?",owner);gateway(false);db.update("UPDATE users SET account_status='ACTIVE' WHERE id=?",owner);gateway(true);
    db.update("UPDATE developer_profiles SET approval_status='SUSPENDED' WHERE user_id=?",owner);gateway(false);db.update("UPDATE developer_profiles SET approval_status='APPROVED' WHERE user_id=?",owner);
    db.update("DELETE FROM user_roles WHERE user_id=? AND role='DEVELOPER'",owner);gateway(false);db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER')",owner);
    db.update("UPDATE products SET visibility='PRIVATE_CASE_STUDY' WHERE id=?",product);gateway(false);db.update("UPDATE products SET visibility='PUBLIC' WHERE id=?",product);
    db.update("UPDATE repository_verifications SET status='REJECTED' WHERE product_id=?",product);gateway(false);db.update("UPDATE repository_verifications SET status='VERIFIED' WHERE product_id=?",product);
    db.update("UPDATE products SET repository_url='https://github.com/example/changed' WHERE id=?",product);gateway(false);db.update("UPDATE products SET repository_url='https://github.com/example/proof' WHERE id=?",product);gateway(true);
    mvc.perform(body("/api/v1/admin/hosting/"+demo+"/review","admin",review("SUSPEND"))).andExpect(status().isOk());gateway(false);
  }
  @Test void limitsSerializeConcurrentReservationsAndImmutableRecordsRetainIndependentVersions()throws Exception{
    approve();deploy();UUID first=demo;for(int n=2;n<=3;n++){demo=upload(n+".0.0");approve();deploy();}demo=upload("4.0.0");approve();mvc.perform(body(own("deploy"),"owner","{}")).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("HOSTING_ACTIVE_LIMIT"));assertNull(deployment());for(int n=5;n<=10;n++)upload(n+".0.0");mvc.perform(uploadRequest("11.0.0",zip,true)).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("HOSTING_RECORD_LIMIT"));mvc.perform(as(get("/api/v1/me/hosting?size=3"),"owner")).andExpect(jsonPath("$.totalItems").value(10)).andExpect(jsonPath("$.hasMore").value(true));assertEquals(3,provider.puts.get());assertEquals("READY",db.queryForObject("SELECT deployment_state FROM hosted_demos WHERE id=?",String.class,first));
  }
  @Test void realAccountDeletionWaitsForKnownAbsenceAndExportContainsOnlyOwnSafeProjections()throws Exception{
    approve();deploy();String token="hosting-delete-confirm-token-123456789012345";db.update("INSERT INTO account_tokens(id,token_hash,kind,user_id,email,expires_at) VALUES(?,?,'ACCOUNT_DELETION',?,'owner@example.test',now()+interval '1 hour')",UUID.randomUUID(),Support.hash(token),owner);String confirm="{\"token\":\""+token+"\",\"decision\":\"DELETE\"}";
    mvc.perform(body("/api/v1/auth/confirm","owner",confirm)).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("HOSTED_DEMOS_ACTIVE"));assertEquals("ACTIVE",db.queryForObject("SELECT account_status FROM users WHERE id=?",String.class,owner));
    String exported=mvc.perform(as(get("/api/v1/me/export"),"owner")).andExpect(status().isOk()).andExpect(jsonPath("$.hostedDemos.hosting[0].id").value(demo.toString())).andReturn().getResponse().getContentAsString();assertFalse(exported.contains("storage_key"));assertFalse(exported.contains("storageKey"));assertFalse(exported.contains("contentBase64"));assertFalse(exported.contains(HostingPublisherFixture.SECRET));mvc.perform(as(get("/api/v1/me/export"),"outsider")).andExpect(jsonPath("$.hostedDemos.hosting.length()").value(0));
    provider.failDelete=true;mvc.perform(body(own("withdraw"),"owner","{}")).andExpect(status().isBadGateway());mvc.perform(body("/api/v1/auth/confirm","owner",confirm)).andExpect(status().isConflict());provider.failDelete=false;mvc.perform(body(own("reconcile"),"owner","{}")).andExpect(status().isOk());mvc.perform(body("/api/v1/auth/confirm","owner",confirm)).andExpect(status().isOk());assertEquals("DELETED",db.queryForObject("SELECT account_status FROM users WHERE id=?",String.class,owner));assertEquals(1,db.queryForObject("SELECT count(*) FROM hosted_demos",Integer.class));assertTrue(db.queryForObject("SELECT count(*) FROM hosting_audit",Integer.class)>0);gateway(false);
  }
  @Test void fixedExpiryDeniesServingAndDiscoveryWithoutErasingUncertainIdentity()throws Exception{
    approve();UUID fixed=UUID.randomUUID();db.update("UPDATE hosted_demos SET deployment_id=?,expires_at=now()+interval '1 second',deployment_state='CREATING' WHERE id=?",fixed,demo);db.update("UPDATE hosted_demos SET deployment_state='READY' WHERE id=?",demo);gateway(true);Thread.sleep(1200);gateway(false);mvc.perform(get("/api/v1/hosting/products/"+product)).andExpect(jsonPath("$.items.length()").value(0));mvc.perform(as(get("/api/v1/me/hosting/"+demo),"owner")).andExpect(jsonPath("$.url").isEmpty());assertTrue(repo.blocksAccountDeletion(owner));assertEquals(fixed,deployment());
  }
  @Test void revocationDuringDeploymentArchiveReadPreventsReservationAndProviderHttp()throws Exception{
    approve();when(storage.read(anyString(),anyString(),anyInt())).thenAnswer(i->{db.update("UPDATE products SET visibility='PRIVATE_CASE_STUDY' WHERE id=?",product);return zip;});mvc.perform(body(own("deploy"),"owner","{}")).andExpect(status().isConflict());assertNull(deployment());assertEquals(0,provider.puts.get());
  }
  @Test void realSchemaUsesPrivateRlsAndNoPublicRolePrivileges(){for(String table:List.of("hosted_demos","hosting_audit")){assertTrue(db.queryForObject("SELECT relrowsecurity FROM pg_class WHERE oid=?::regclass",Boolean.class,table));assertFalse(db.queryForObject("SELECT coalesce(bool_or(privilege_type IN ('SELECT','INSERT','UPDATE','DELETE')),false) FROM information_schema.table_privileges WHERE table_schema=current_schema() AND table_name=? AND grantee='PUBLIC'",Boolean.class,table));}}
}
