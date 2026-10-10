package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.commerce.*;
import com.getlancer.payments.CommerceProviderIntegrationTest;
import com.getlancer.payments.RazorpayClient;
import com.getlancer.shared.ApiError;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ContextConfiguration(initializers=TestDatabaseGuard.class)
@SpringBootTest(properties={"app.environment=local","app.jobs-enabled=false","app.admin-email=admin@example.test","app.admin-password=","app.admin-totp=","spring.config.import=",
 "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}","spring.datasource.username=${TEST_DB_USERNAME:postgres}","spring.datasource.password=${TEST_DB_PASSWORD:}",
 "spring.datasource.hikari.schema=getlancer_test","spring.flyway.default-schema=getlancer_test","spring.flyway.schemas=getlancer_test","app.origin=http://localhost:3000","app.secure-cookie=false",
 "app.storage.access-key=","app.storage.secret-key=","app.commerce.webhook-secret=source-fixture-webhook-not-real","app.education.paid-enabled=true","app.discovery-rate-limit=300"})
@AutoConfigureMockMvc
@Import(CommerceProviderIntegrationTest.Configuration.class)
class EducationCommerceIntegrationTest {
  @Autowired JdbcTemplate db;@Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired CommerceRepository repo;
  @Autowired CommerceEducationBinding education;@Autowired PlatformTransactionManager manager;
  @Autowired CommerceProviderIntegrationTest.Fixture provider;@Autowired RazorpayClient client;
  @MockBean CommerceStorage storage;
  UUID buyer,seller,admin,product,template,version,release;byte[] archive;

  UUID user(String name,boolean approved) {
    UUID id=UUID.randomUUID();db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",id,name+"@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT'),(?,'DEVELOPER')",id,id);
    db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,?,?)",id,name,name,approved?"APPROVED":"DRAFT");
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",Support.hash(name),id);return id;
  }
  MockHttpServletRequestBuilder request(MockHttpServletRequestBuilder r,String actor,String body) {
    return r.cookie(new Cookie("gl_session",actor)).header("Origin","http://localhost:3000").header("X-Requested-With","getlancer").contentType("application/json").content(body);
  }
  MockHttpServletRequestBuilder order(UUID educationRelease,String key) {
    String body="{\"versionId\":\""+version+"\",\"licenseConsent\":true"+(educationRelease==null?"":",\"educationReleaseId\":\""+educationRelease+"\"")+"}";
    return request(post("/api/v1/templates/"+template+"/orders"),"buyer",body).header("Idempotency-Key",key);
  }
  UUID newRelease() throws Exception {
    return newRelease(List.of());
  }
  UUID newRelease(List<Map<String,Object>> links) throws Exception {
    return newRelease(links,null);
  }
  UUID newRelease(List<Map<String,Object>> links,Map<String,Object> dataAiEvidence) throws Exception {
    var snapshot=new LinkedHashMap<String,Object>();snapshot.put("productId",product.toString());snapshot.put("slug",product.toString());snapshot.put("title","College booking project");
    snapshot.put("category","FULL_STACK");snapshot.put("mode","PAID");snapshot.put("difficulty","INTERMEDIATE");snapshot.put("demoMode","SOURCE_ONLY");
    if(dataAiEvidence!=null){snapshot.put("category","AI_ML");snapshot.put("dataAiEvidence",dataAiEvidence);}
    snapshot.put("contribution",Map.of("text","Built the original application and its setup guide."));snapshot.put("componentLinks",links);
    snapshot.put("institution","private-university");snapshot.put("academicYear","private-year");snapshot.put("branch","private-branch");
    snapshot.put("package",Map.of("includedAssets",List.of("Original application, tests and setup guide."),"excludedAssets",List.of("Hosting and production data."),
      "setupSteps",List.of("Run local migrations then start the application."),"prerequisites",List.of("Java 17 and Node 22."),"limitations",List.of("Original demonstration application; no production operations."),
      "supportTerms","Seven days of setup support. No customization.","licenseTerms","One end product with third-party notices."));
    var bound=new LinkedHashMap<>(education.freezePaid(version,product,seller));snapshot.put("priceMinor",bound.remove("priceMinor"));snapshot.put("currency",bound.remove("currency"));snapshot.put("sourceBinding",bound);
    String frozen=json.writeValueAsString(snapshot);UUID id=UUID.randomUUID();
    // Disposable reviewed fixtures isolate commerce; EducationIntegrationTest exercises the actual review path.
    db.update("INSERT INTO education_releases(id,product_id,owner_id,revision,status,draft,snapshot,source_hash,source_version_id,submitted_at,reviewed_at) VALUES(?,?,?,1,'APPROVED',?::jsonb,?::jsonb,?,?,now(),now())",id,product,seller,frozen,frozen,Support.hash(frozen),version);return id;
  }

  @BeforeEach void prepare() throws Exception {
    provider.reset();reset(storage);db.execute("TRUNCATE users CASCADE");db.execute("TRUNCATE rate_buckets");db.execute("TRUNCATE commerce_webhook_events");
    buyer=user("buyer",false);seller=user("seller",true);admin=user("admin",false);db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')",admin);db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?",admin);
    product=UUID.randomUUID();template=UUID.randomUUID();version=UUID.randomUUID();
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,approval_status,lifecycle_status,visibility,repository_url) VALUES(?,?,?,'College booking project','Reproducible student application','An original complete project with reviewed setup and rights.','SAAS','CRM','Java, TypeScript','Built original project','APPROVED','ACTIVE','PUBLIC','https://github.com/example/college')",product,seller,product.toString());
    db.update("INSERT INTO repository_verifications(product_id,repository_url,challenge,status,reviewed_at,reviewer_id) VALUES(?,'https://github.com/example/college','private-challenge','VERIFIED',now(),?)",product,admin);
    db.update("INSERT INTO college_project_metadata(product_id,category,language,problem,outcome,prerequisites,contribution,status) VALUES(?,'FULL_STACK','Java','Booking without conflicts','Repeatable source results','Java 17','Original implementation','APPROVED')",product);
    db.update("INSERT INTO payment_accounts(id,builder_user_id,account_id,mode,provider_status,activation_confirmed,verified_by) VALUES(?,?,?,'test','created',true,?)",UUID.randomUUID(),seller,CommerceProviderIntegrationTest.ACCOUNT,admin);
    var bytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(bytes)) {
      for(var entry:Map.of("README.md","Run locally with Java 17 and Node 22. Setup instructions and complete project source.","LICENSE","One commercial end product with modification allowed. Keep all third-party MIT notices.","pom.xml","<project><modelVersion>4.0.0</modelVersion></project>","src/Main.java","class Main { public static void main(String[] args) { System.out.println(\"College project\"); } }").entrySet()) {
        zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
      }
    }
    archive=bytes.toByteArray();when(storage.read(anyString(),anyString(),anyInt())).thenReturn(archive);
    repo.create(template,seller,product,"college-source","College source","Original reproducible college source","Complete source, setup and required dependency disclosures.",10000,"One end product with modification; preserve third-party notices. No source resale.");
    repo.addVersion(version,template,"1.0.0","Original project source with reproducible setup.","commerce/"+version+"/"+UUID.randomUUID()+".zip",SourceArchive.inspect(archive),"One end product with modification; preserve third-party notices. No source resale.");
    repo.review(version,"APPROVED","Reviewed package rights and setup.",admin);repo.templateStatus(template,"ACTIVE");release=newRelease();
  }

  @Test void genericCollegeCheckoutCannotBypassReviewOrOperatorActivation() throws Exception {
    mvc.perform(get("/api/v1/templates/college-source")).andExpect(status().isOk()).andExpect(jsonPath("$.educationProduct").value(true))
      .andExpect(jsonPath("$.educationProjectSlug").value(product.toString())).andExpect(jsonPath("$.currentEducationRelease").value(release.toString()));
    mvc.perform(order(null,UUID.randomUUID().toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EDUCATION_OFFER_CHANGED"));
    var disabled=new CommerceEducationBinding(db,repo,storage,json,client,false);
    ApiError error=assertThrows(ApiError.class,()->new TransactionTemplate(manager).execute(s->disabled.bind(release,repo.template(template,true),repo.version(template,version,true))));
    assertEquals("EDUCATION_PAID_UNAVAILABLE",error.code);assertEquals(503,error.status);assertEquals(false,disabled.offer(release).get("checkoutAvailable"));
    assertEquals(0,provider.creates.get());assertEquals(0,db.queryForObject("SELECT count(*) FROM template_purchases",Integer.class));
    db.update("UPDATE college_project_metadata SET status='SUSPENDED' WHERE product_id=?",product);
    mvc.perform(order(release,UUID.randomUUID().toString())).andExpect(status().isConflict());
    mvc.perform(get("/api/v1/templates/college-source")).andExpect(status().isOk()).andExpect(jsonPath("$.educationProduct").value(true)).andExpect(jsonPath("$.educationProjectSlug").doesNotExist());
  }

  @Test void frozenBuyerPackagePreservesPriceAndSupportButOmitsPrivateAcademics() throws Exception {
    mvc.perform(order(release,UUID.randomUUID().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.educationReleaseId").value(release.toString()))
      .andExpect(jsonPath("$.educationSnapshot.package.supportTerms").value("Seven days of setup support. No customization."))
      .andExpect(jsonPath("$.educationSnapshot.sourceBinding.versionId").value(version.toString())).andExpect(jsonPath("$.downloadAvailable").value(false));
    String stored=db.queryForObject("SELECT education_snapshot::text FROM template_purchases",String.class);
    assertFalse(stored.contains("private-university"));assertFalse(stored.contains("private-year"));assertFalse(stored.contains("private-branch"));assertFalse(stored.contains("storage_key"));
    UUID purchase=db.queryForObject("SELECT id FROM template_purchases",UUID.class);
    assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE template_purchases SET education_snapshot='{}'::jsonb WHERE id=?",purchase));
    assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE template_purchases SET education_release_id=null,education_snapshot=null WHERE id=?",purchase));
    db.update("UPDATE source_templates SET price_minor=20000 WHERE id=?",template);
    mvc.perform(get("/api/v1/me/template-purchases").cookie(new Cookie("gl_session","buyer"))).andExpect(jsonPath("$.items[0].amountMinor").value(10000)).andExpect(jsonPath("$.items[0].educationSnapshot.priceMinor").value(10000));
    mvc.perform(order(release,UUID.randomUUID().toString())).andExpect(status().isConflict());assertEquals(1,provider.creates.get());
    assertEquals("DRAFT",db.queryForObject("SELECT approval_status FROM developer_profiles WHERE user_id=?",String.class,buyer));
  }
  @Test void newDataAiAgreementPinsDistinctRightsAcrossLaterOfferAndOwnerExport()throws Exception{
    var evidence=com.getlancer.education.EducationDataAiTest.evidence(true);UUID current=newRelease(List.of(),evidence);mvc.perform(order(current,UUID.randomUUID().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.educationSnapshot.dataAiEvidence.codeLicense").value(evidence.get("codeLicense"))).andExpect(jsonPath("$.educationSnapshot.dataAiEvidence.modelFormat").value("JSON"));String frozen=db.queryForObject("SELECT education_snapshot::text FROM template_purchases",String.class);assertEquals(json.valueToTree(evidence),json.readTree(frozen).path("dataAiEvidence"));
    newRelease();mvc.perform(get("/api/v1/me/template-purchases").cookie(new Cookie("gl_session","buyer"))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].educationSnapshot.dataAiEvidence.modelSha256").value(evidence.get("modelSha256"))).andExpect(jsonPath("$.items[0].educationSnapshot.priceMinor").value(10000));mvc.perform(get("/api/v1/me/export").cookie(new Cookie("gl_session","buyer"))).andExpect(status().isOk()).andExpect(jsonPath("$.sourcePurchases[0].education_snapshot.dataAiEvidence.modelLicense").value(evidence.get("modelLicense")));assertEquals(frozen,db.queryForObject("SELECT education_snapshot::text FROM template_purchases",String.class));
  }
  @Test void historicalAcceptedAgreementProjectsPrivateNestedFieldsWithoutRewritingStoredTerms()throws Exception{
    var t=repo.template(template,false);var v=repo.version(template,version,false);var accepted=new TransactionTemplate(manager).execute(s->education.bind(release,t,v));assertFalse(accepted.containsKey("dataAiEvidence"));accepted.put("institution","historical-private-canary");var pack=new LinkedHashMap<>((Map<String,Object>)accepted.get("package"));pack.put("providerToken","historical-private-canary");accepted.put("package",pack);var source=new LinkedHashMap<>((Map<String,Object>)accepted.get("sourceBinding"));source.put("storage_key","historical-private-canary");source.put("sourceBytes",List.of("historical-private-canary"));accepted.put("sourceBinding",source);UUID purchase=UUID.randomUUID();
    // Disposable historical reservation exercises current reads, never a live payment or entitlement.
    repo.reserve(purchase,buyer,UUID.randomUUID(),t,v,CommerceProviderIntegrationTest.ACCOUNT,"test",release,education.encode(accepted));String stored=db.queryForObject("SELECT education_snapshot::text FROM template_purchases WHERE id=?",String.class,purchase);assertTrue(stored.contains("historical-private-canary"));var shown=json.readTree(mvc.perform(get("/api/v1/me/template-purchases").cookie(new Cookie("gl_session","buyer"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("items").get(0).path("educationSnapshot");assertFalse(shown.toString().contains("historical-private-canary"));assertFalse(shown.has("dataAiEvidence"));assertEquals(10000,shown.path("priceMinor").asInt());assertEquals(version.toString(),shown.path("sourceBinding").path("versionId").asText());assertEquals(stored,db.queryForObject("SELECT education_snapshot::text FROM template_purchases WHERE id=?",String.class,purchase));
  }

  @Test void changedReviewedReleaseCannotReuseIdempotencyOrDuplicateExistingVersionPurchase() throws Exception {
    String key=UUID.randomUUID().toString();mvc.perform(order(release,key)).andExpect(status().isOk());UUID newer=newRelease();
    mvc.perform(order(newer,key)).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));
    mvc.perform(order(newer,UUID.randomUUID().toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));
    mvc.perform(order(release,UUID.randomUUID().toString())).andExpect(status().isConflict());assertEquals(1,provider.creates.get());
  }

  @Test void wrongProjectAndHeldSourceNeverReserveMoney() throws Exception {
    UUID unrelated=user("unrelated",true),otherProduct=UUID.randomUUID(),otherRelease=UUID.randomUUID();
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text) VALUES(?,?,?,'Other project','Other summary','Other project description','SAAS','CRM','Java','Original contribution')",otherProduct,unrelated,otherProduct.toString());
    db.update("INSERT INTO education_releases(id,product_id,owner_id,status,draft) VALUES(?,?,?,'DRAFT','{}'::jsonb)",otherRelease,otherProduct,unrelated);
    mvc.perform(order(otherRelease,UUID.randomUUID().toString())).andExpect(status().isConflict());
    db.update("UPDATE source_versions SET status='SUSPENDED' WHERE id=?",version);mvc.perform(order(release,UUID.randomUUID().toString())).andExpect(status().isConflict());
    assertEquals(0,provider.creates.get());assertEquals(0,db.queryForObject("SELECT count(*) FROM template_purchases",Integer.class));
  }

  @Test void capturedTestEducationPaymentNeverGrantsRealSourceAndDuplicateCaptureRemainsOneLedgerEntry() throws Exception {
    mvc.perform(order(release,UUID.randomUUID().toString())).andExpect(status().isOk());UUID purchase=db.queryForObject("SELECT id FROM template_purchases",UUID.class);provider.captured=true;
    for(int attempt=0;attempt<2;attempt++) mvc.perform(request(post("/api/v1/template-purchases/"+purchase+"/reconcile"),"buyer","{}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CAPTURED")).andExpect(jsonPath("$.downloadAvailable").value(false)).andExpect(jsonPath("$.educationReleaseId").value(release.toString()));
    assertEquals(1,db.queryForObject("SELECT count(*) FROM commerce_ledger WHERE kind='CAPTURE'",Integer.class));
    mvc.perform(get("/api/v1/template-purchases/"+purchase+"/download").cookie(new Cookie("gl_session","buyer"))).andExpect(status().isForbidden());
  }

  @Test void concurrentPriceChangeDuringExternalOrderCallKeepsOneRecoverableFrozenReservation() throws Exception {
    provider.blocked=true;var executor=Executors.newSingleThreadExecutor();try {
      var result=executor.submit(()->mvc.perform(order(release,UUID.randomUUID().toString())).andReturn().getResponse().getStatus());assertTrue(provider.entered.await(3,TimeUnit.SECONDS));
      db.update("UPDATE source_templates SET price_minor=20000 WHERE id=?",template);provider.release.countDown();
      assertEquals(409,result.get(5,TimeUnit.SECONDS));assertEquals(1,provider.creates.get());assertEquals("ORDER_CREATED",db.queryForObject("SELECT status FROM template_purchases",String.class));
      assertEquals(10000L,db.queryForObject("SELECT amount_minor FROM template_purchases",Long.class));mvc.perform(order(release,UUID.randomUUID().toString())).andExpect(status().isConflict());assertEquals(1,provider.creates.get());
    } finally {provider.release.countDown();executor.shutdownNow();}
  }

  @Test void livePinnedSourceSurvivesNewOfferButReleaseSuspensionDisputeAndRefundHoldDownloads() throws Exception {
    UUID purchase=UUID.randomUUID();var t=repo.template(template,false);var v=repo.version(template,version,false);var accepted=new TransactionTemplate(manager).execute(s->education.bind(release,t,v));
    // Disposable financial fixture tests the production entitlement SQL; no test gateway grants a live purchase.
    repo.reserve(purchase,buyer,UUID.randomUUID(),t,v,CommerceProviderIntegrationTest.ACCOUNT,"live",release,education.encode(accepted));repo.bind(purchase,"order_collegelive123","CREATED");repo.capture(purchase,"pay_collegelive123",0,"PROCESSED");
    String download="/api/v1/template-purchases/"+purchase+"/download";mvc.perform(get(download).cookie(new Cookie("gl_session","buyer"))).andExpect(status().isOk());newRelease();
    mvc.perform(get(download).cookie(new Cookie("gl_session","buyer"))).andExpect(status().isOk());db.update("UPDATE education_releases SET status='SUSPENDED' WHERE id=?",release);mvc.perform(get(download).cookie(new Cookie("gl_session","buyer"))).andExpect(status().isForbidden());
    db.update("UPDATE education_releases SET status='APPROVED' WHERE id=?",release);repo.dispute(purchase,buyer,"The advertised setup cannot be reproduced with included source.");mvc.perform(get(download).cookie(new Cookie("gl_session","buyer"))).andExpect(status().isForbidden());
    repo.resolve(purchase,admin,"RESUME","Reviewed the original setup guide.");mvc.perform(get(download).cookie(new Cookie("gl_session","buyer"))).andExpect(status().isOk());repo.capture(purchase,"pay_collegelive123",1,"PROCESSED");mvc.perform(get(download).cookie(new Cookie("gl_session","buyer"))).andExpect(status().isForbidden());
  }

  @Test void exactFullFileManifestDoesNotChangeBuildManifestMeaningAndIsImmutable() {
    var inspection=SourceArchive.inspect(archive);assertEquals(List.of("pom.xml"),inspection.manifestFiles());assertEquals(4,inspection.files().size());
    assertTrue(inspection.files().containsAll(List.of("README.md","LICENSE","pom.xml","src/Main.java")));assertThrows(UnsupportedOperationException.class,()->inspection.files().add("invented"));
    assertEquals(List.of(),new SourceArchive.Inspection("a".repeat(64),100,4,List.of("pom.xml")).files());
    var offer=education.offer(release);assertEquals(true,offer.get("checkoutAvailable"));assertEquals(4,((List<?>)offer.get("files")).size());assertFalse(offer.containsKey("storage_key"));
    assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE source_versions SET safe_source_files='[\"invented.txt\"]'::jsonb WHERE id=?",version));
  }

  @Test void legacyVersionFileListComesFromActualChecksumVerifiedArchive() throws Exception {
    UUID legacy=UUID.randomUUID();var inspection=SourceArchive.inspect(archive);
    repo.addVersion(legacy,template,"0.9.0","Legacy reviewed release with a build-only manifest.","commerce/"+legacy+"/"+UUID.randomUUID()+".zip",
      new SourceArchive.Inspection(inspection.sha256(),inspection.sizeBytes(),inspection.entryCount(),inspection.manifestFiles()),"One end product with third-party notices.");
    repo.review(legacy,"APPROVED","Reviewed legacy archive.",admin);clearInvocations(storage);
    var frozen=education.freezePaid(legacy,product,seller);assertEquals(inspection.files(),frozen.get("files"));verify(storage).read(anyString(),eq(inspection.sha256()),eq(archive.length));
    when(storage.read(anyString(),anyString(),anyInt())).thenReturn("different archive bytes".getBytes(StandardCharsets.UTF_8));
    assertThrows(ApiError.class,()->education.freezePaid(legacy,product,seller));
  }

  @Test void withdrawnComponentAttributionIsHiddenWhileAcceptedSourceTermsRemainPinned() throws Exception {
    UUID component=UUID.randomUUID();
    db.update("INSERT INTO component_entries(id,owner_id,recipe_slug,slug,title,summary,contribution,status,published_at,published_source,published_context) VALUES(?,?,'studio-navigation',?,'Original navigation','Original reusable navigation','Original authored contribution','ACTIVE',now(),'{\"license\":\"MIT\"}'::jsonb,'{}'::jsonb)",component,seller,component.toString());
    db.update("INSERT INTO component_releases(component_id,revision,source,context,source_sha256) VALUES(?,1,'{\"license\":\"MIT\"}'::jsonb,'{}'::jsonb,?)",component,"a".repeat(64));
    release=newRelease(List.of(Map.of("componentId",component.toString(),"revision",1,"license","MIT","attribution","Original navigation authors; retain MIT notice.")));
    mvc.perform(order(release,UUID.randomUUID().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.educationSnapshot.componentLinks.length()").value(1));
    String frozen=db.queryForObject("SELECT education_snapshot::text FROM template_purchases",String.class);
    db.update("UPDATE component_entries SET withdrawn_at=now() WHERE id=?",component);
    mvc.perform(get("/api/v1/me/template-purchases").cookie(new Cookie("gl_session","buyer")))
      .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].educationSnapshot.componentLinks.length()").value(0))
      .andExpect(jsonPath("$.items[0].educationSnapshot.priceMinor").value(10000)).andExpect(jsonPath("$.items[0].educationSnapshot.sourceBinding.versionId").value(version.toString()));
    assertEquals(frozen,db.queryForObject("SELECT education_snapshot::text FROM template_purchases",String.class));
  }
}
