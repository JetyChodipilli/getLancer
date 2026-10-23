package com.getlancer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.getlancer.commerce.CommerceStorage;
import com.getlancer.commerce.SourceArchive;
import com.getlancer.payments.RazorpayClient;
import com.getlancer.shared.ApiError;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import com.getlancer.trust.DemoHealth;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@org.springframework.test.context.ContextConfiguration(initializers=TestDatabaseGuard.class)
@SpringBootTest(properties={"app.environment=local","app.jobs-enabled=false","app.admin-email=admin@example.test","app.admin-password=","app.admin-totp=","spring.config.import=",
  "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}","spring.datasource.username=${TEST_DB_USERNAME:postgres}","spring.datasource.password=${TEST_DB_PASSWORD:}",
  "spring.datasource.hikari.schema=getlancer_test","spring.flyway.default-schema=getlancer_test","spring.flyway.schemas=getlancer_test","app.origin=http://localhost:3000","app.secure-cookie=false","app.storage.access-key=","app.storage.secret-key=","app.education.paid-enabled=false","app.policies-approved=true"})
@AutoConfigureMockMvc
class EducationIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired PlatformTransactionManager transactions;
  @MockBean CommerceStorage storage;
  @MockBean RazorpayClient provider;
  UUID owner,other,admin,product;
  List<Map<String,Object>> examples;
  Map<String,byte[]> objects=new ConcurrentHashMap<>();
  UUID user(String name){UUID id=UUID.randomUUID();db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",id,name+"@example.test");db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT'),(?,'DEVELOPER')",id,id);db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,?,'APPROVED')",id,name,name);db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",Support.hash(name),id);db.update("INSERT INTO showcase_entitlements(user_id) VALUES(?)",id);return id;}
  UUID product(UUID owner){UUID id=UUID.randomUUID();db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,rights_confirmed,approval_status,lifecycle_status,visibility) VALUES(?,?,?,'Original college project','Original reproducible project outcome','Original source with independently inspectable category evidence and realistic local setup.','LEARNING','CRM','JavaScript','Built the original source and reproduced the stated category evidence.',true,'APPROVED','ACTIVE','PUBLIC')",id,owner,id.toString());return id;}
  @BeforeEach void setup()throws Exception{
    db.execute("TRUNCATE users CASCADE");db.execute("TRUNCATE rate_buckets");objects.clear();reset(storage,provider);owner=user("owner");other=user("other");admin=user("admin");db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')",admin);db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?",admin);product=product(owner);
    try(var input=getClass().getResourceAsStream("/catalog/education.json")){examples=json.readValue(input,new TypeReference<List<Map<String,Object>>>(){});}
    when(provider.configuration()).thenReturn(Map.of("enabled",false,"mode","disabled"));
    when(storage.put(any(UUID.class),any(byte[].class),anyString())).thenAnswer(invocation->{String key="commerce/"+invocation.getArgument(0)+"/"+UUID.randomUUID()+".zip";objects.put(key,((byte[])invocation.getArgument(1)).clone());return key;});
    when(storage.read(anyString(),anyString(),anyInt())).thenAnswer(invocation->{byte[] bytes=objects.get(invocation.getArgument(0));if(bytes==null)throw new ApiError(503,"STORAGE_UNAVAILABLE","Archive unavailable.");var inspection=SourceArchive.inspect(bytes);if(!inspection.sha256().equals(invocation.getArgument(1))||bytes.length!=(Integer)invocation.getArgument(2))throw new ApiError(409,"ARCHIVE_INTEGRITY_ERROR","Archive integrity failed.");return bytes.clone();});
    doAnswer(invocation->{objects.remove(invocation.getArgument(0));return null;}).when(storage).discardUnlinked(anyString());
  }
  @AfterEach void cleanup(){db.execute("TRUNCATE users CASCADE");}
  MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request,String actor){return actor==null?request:request.cookie(new Cookie("gl_session",actor));}
  MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request,String actor,Object value)throws Exception{return as(request,actor).header("Origin","http://localhost:3000").header("X-Requested-With","getlancer").contentType("application/json").content(json.writeValueAsBytes(value));}
  JsonNode ok(MockHttpServletRequestBuilder request)throws Exception{return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
  Map<String,Object> draftBody(String mode,int index){var example=json.convertValue(examples.get(index).get("release"),new TypeReference<LinkedHashMap<String,Object>>(){});var source=json.convertValue(example.get("snapshot"),new TypeReference<LinkedHashMap<String,Object>>(){});var result=new LinkedHashMap<String,Object>();for(String key:List.of("category","difficulty","demoMode","demoUrl","package","categoryEvidence","componentLinks","dataAiEvidence"))if(source.containsKey(key))result.put(key,source.get(key));result.put("mode",mode);return result;}
  JsonNode create(String mode,int index)throws Exception{return ok(body(post("/api/v1/me/college-projects/"+product+"/releases"),"owner",draftBody(mode,index)));}
  JsonNode submit(JsonNode release)throws Exception{return ok(body(post("/api/v1/me/education-releases/"+release.path("id").asText()+"/submit"),"owner",Map.of("revision",release.path("revision").asLong(),"rightsConsent",true)));}
  byte[] archive(int index)throws Exception{return Files.readAllBytes(Path.of("..","public",examples.get(index).get("previewPackageUrl").toString().substring(1)));}
  MockHttpServletRequestBuilder upload(JsonNode release,byte[] bytes,boolean rights){return as(multipart("/api/v1/me/education-releases/"+release.path("id").asText()+"/source").file(new MockMultipartFile("file","source.zip","application/zip",bytes)).param("revision",release.path("revision").asText()).param("rightsConsent",Boolean.toString(rights)),"owner").header("Origin","http://localhost:3000").header("X-Requested-With","getlancer");}
  Map<String,Object> review(JsonNode release,String decision){return Map.of("revision",release.path("revision").asLong(),"sourceHash",release.path("sourceHash").asText(),"decision",decision,"reason","Reviewed category evidence, package disclosures and exact source redistribution rights.","rightsReviewed",true,"packageReviewed",true);}
  JsonNode approve(JsonNode pending)throws Exception{return ok(body(post("/api/v1/admin/education-releases/"+pending.path("id").asText()+"/review"),"admin",review(pending,"APPROVE")));}
  JsonNode freePending()throws Exception{return submit(ok(upload(create("FREE",0),archive(0),true)));}
  JsonNode freeApproved()throws Exception{var pending=freePending();mvc.perform(body(post("/api/v1/admin/education-releases/"+pending.path("id").asText()+"/package"),"admin",Map.of())).andExpect(status().isOk());return approve(pending);}
  JsonNode historicalFixture(JsonNode draft,Map<String,Object> snapshot)throws Exception{
    String encoded=json.writeValueAsString(snapshot),hash=Support.hash(json.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(snapshot));
    // Disposable immutable historical fixture; no public API bypasses new submission checks.
    db.update("UPDATE education_releases SET snapshot=?::jsonb,source_hash=?,status='PENDING',submitted_at=now(),revision=revision+1 WHERE id=?",encoded,hash,UUID.fromString(draft.path("id").asText()));return ok(as(get("/api/v1/me/education-releases/"+draft.path("id").asText()),"owner"));
  }

  @Test void unfinishedDraftSavesAndOwnershipStaleRevisionAndConcreteInputAreEnforced()throws Exception{
    var release=ok(body(post("/api/v1/me/college-projects/"+product+"/releases"),"owner",Map.of("mode","SHOWCASE")));String id=release.path("id").asText();var command=Map.of("revision",1,"mode","SHOWCASE","category","FULL_STACK");var saved=ok(body(patch("/api/v1/me/education-releases/"+id),"owner",command));assertEquals(2,saved.path("revision").asInt());
    mvc.perform(body(patch("/api/v1/me/education-releases/"+id),"owner",command)).andExpect(status().isConflict());mvc.perform(as(get("/api/v1/me/education-releases/"+id),"other")).andExpect(status().isNotFound());mvc.perform(body(patch("/api/v1/me/education-releases/"+id),"other",command)).andExpect(status().isNotFound());
    mvc.perform(body(patch("/api/v1/me/education-releases/"+id),"owner",Map.of("revision",2.5,"mode","SHOWCASE"))).andExpect(status().isBadRequest());mvc.perform(body(patch("/api/v1/me/education-releases/"+id),"owner",Map.of("revision",2,"mode","SHOWCASE","categoryEvidence",Map.of("secretKey","never accepted")))).andExpect(status().isBadRequest());mvc.perform(body(patch("/api/v1/me/education-releases/"+id),"owner",Map.of("revision",2,"mode","SHOWCASE","categoryEvidence",Map.of("modules","x".repeat(4001))))).andExpect(status().isBadRequest());
    mvc.perform(body(post("/api/v1/me/education-releases/"+id+"/submit"),"owner",Map.of("revision",2,"rightsConsent",true))).andExpect(status().isBadRequest());assertEquals("DRAFT",db.queryForObject("SELECT status FROM education_releases WHERE id=?",String.class,UUID.fromString(id)));
  }
  @Test void allExpandedOriginalSeedsPassOrdinaryFreeRightsUploadAndOperatorReview()throws Exception {
    assertEquals(12,examples.size());
    for(int index=4;index<examples.size();index++) {
      String name="expanded-seed-"+index;UUID actor=user(name),parent=product(actor);
      var draft=ok(body(post("/api/v1/me/college-projects/"+parent+"/releases"),name,draftBody("FREE",index)));
      var uploaded=ok(as(multipart("/api/v1/me/education-releases/"+draft.path("id").asText()+"/source")
          .file(new MockMultipartFile("file","source.zip","application/zip",archive(index)))
          .param("revision",draft.path("revision").asText()).param("rightsConsent","true"),name)
          .header("Origin","http://localhost:3000").header("X-Requested-With","getlancer"));
      var pending=ok(body(post("/api/v1/me/education-releases/"+uploaded.path("id").asText()+"/submit"),name,
          Map.of("revision",uploaded.path("revision").asLong(),"rightsConsent",true)));
      mvc.perform(body(post("/api/v1/admin/education-releases/"+pending.path("id").asText()+"/package"),"admin",Map.of())).andExpect(status().isOk());
      var approved=approve(pending);assertEquals("APPROVED",approved.path("status").asText());assertEquals("FREE",approved.path("snapshot").path("mode").asText());
    }
  }
  @Test void allFourOriginalCategorySchemasPublishAndExactModeDifficultyFiltersWork()throws Exception{
    for(int index=0;index<4;index++){String name="category"+index;UUID actor=user(name),p=product(actor);var draft=ok(body(post("/api/v1/me/college-projects/"+p+"/releases"),name,draftBody("SHOWCASE",index)));var pending=ok(body(post("/api/v1/me/education-releases/"+draft.path("id").asText()+"/submit"),name,Map.of("revision",1,"rightsConsent",true)));approve(pending);var catalog=ok(get("/api/v1/college-projects").param("category",draftBody("SHOWCASE",index).get("category").toString()).param("mode","SHOWCASE").param("difficulty",draftBody("SHOWCASE",index).get("difficulty").toString()));assertEquals(1,catalog.path("totalItems").asInt());assertEquals("APPROVED",ok(get("/api/v1/college-projects/"+p)).path("education").path("release").path("status").asText());}
    assertEquals(4,ok(get("/api/v1/college-projects")).path("totalItems").asInt());assertEquals(0,ok(get("/api/v1/college-projects").param("mode","PAID")).path("totalItems").asInt());
  }
  @Test void unfinishedDataAiDraftsSaveButNewSubmissionRejectsMissingRightsAndUnsafeConsents()throws Exception{
    for(int index:List.of(1,2)){
      var command=draftBody("SHOWCASE",index);command.remove("dataAiEvidence");var missing=ok(body(post("/api/v1/me/college-projects/"+product+"/releases"),"owner",command));mvc.perform(body(post("/api/v1/me/education-releases/"+missing.path("id").asText()+"/submit"),"owner",Map.of("revision",1,"rightsConsent",true))).andExpect(status().isBadRequest());
      command.put("dataAiEvidence",Map.of("codeLicense","MIT","syntheticData",false));var partial=ok(body(post("/api/v1/me/college-projects/"+product+"/releases"),"owner",command));assertEquals("MIT",partial.path("snapshot").path("dataAiEvidence").path("codeLicense").asText());mvc.perform(body(post("/api/v1/me/education-releases/"+partial.path("id").asText()+"/submit"),"owner",Map.of("revision",1,"rightsConsent",true))).andExpect(status().isBadRequest());
      for(String flag:List.of("redistributionAllowed","syntheticData","noRemoteCode")){var unsafe=json.convertValue(draftBody("SHOWCASE",index).get("dataAiEvidence"),new TypeReference<LinkedHashMap<String,Object>>(){});unsafe.put(flag,false);command.put("dataAiEvidence",unsafe);var draft=ok(body(post("/api/v1/me/college-projects/"+product+"/releases"),"owner",command));mvc.perform(body(post("/api/v1/me/education-releases/"+draft.path("id").asText()+"/submit"),"owner",Map.of("revision",1,"rightsConsent",true))).andExpect(status().isBadRequest());}
    }
    assertEquals(0,db.queryForObject("SELECT count(*) FROM education_releases WHERE submitted_at IS NOT NULL",Integer.class));
  }
  @Test void typedDataAiWireFieldsDenyUnknownTypesLoadersAndUnrelatedCategories()throws Exception{
    for(Object unsafe:List.<Object>of("not an object",Map.of("privateSource","never accepted"),Map.of("dataLicense",42),Map.of("syntheticData","true"),Map.of("noRemoteCode",1),Map.of("modelFormat","pickle"),Map.of("modelFormat","joblib"),Map.of("dataSha256","A".repeat(64)),Map.of("limitations","x".repeat(4001)))){var command=draftBody("SHOWCASE",2);command.put("dataAiEvidence",unsafe);mvc.perform(body(post("/api/v1/me/college-projects/"+product+"/releases"),"owner",command)).andExpect(status().isBadRequest());}
    for(int category:List.of(0,3)){var command=draftBody("SHOWCASE",category);command.put("dataAiEvidence",Map.of());mvc.perform(body(post("/api/v1/me/college-projects/"+product+"/releases"),"owner",command)).andExpect(status().isBadRequest());}
    assertEquals(0,db.queryForObject("SELECT count(*) FROM education_releases",Integer.class));assertEquals("JSON",create("SHOWCASE",2).path("snapshot").path("dataAiEvidence").path("modelFormat").asText());
  }
  @Test void newEvidenceFreezesForReviewPublicAndOwnerExportWhilePrivateDetailsRemainSeparate()throws Exception{
    var pending=submit(create("SHOWCASE",2));String id=pending.path("id").asText();UUID release=UUID.fromString(id);String stored=db.queryForObject("SELECT snapshot::text FROM education_releases WHERE id=?",String.class,release),hash=pending.path("sourceHash").asText();var approved=approve(pending);
    ok(body(patch("/api/v1/me/education-releases/"+id+"/academic"),"owner",Map.of("revision",1,"institution","Private AI academic annotation","shareAcademicDetails",false)));var publicProject=ok(get("/api/v1/college-projects/"+product));assertEquals(pending.path("snapshot").path("dataAiEvidence"),publicProject.path("education").path("dataAiEvidence"));assertFalse(publicProject.toString().contains("Private AI"));
    var exported=ok(as(get("/api/v1/me/export"),"owner")).path("educationReleases").get(0);assertEquals(pending.path("snapshot").path("dataAiEvidence"),exported.path("snapshot").path("dataAiEvidence"));assertEquals("Private AI academic annotation",exported.path("institution").asText());assertFalse(exported.path("snapshot").toString().contains("Private AI"));
    var changed=draftBody("SHOWCASE",2);changed.put("revision",approved.path("revision").asLong());changed.put("dataAiEvidence",Map.of("codeLicense","Different license"));mvc.perform(body(patch("/api/v1/me/education-releases/"+id),"owner",changed)).andExpect(status().isConflict());assertEquals(stored,db.queryForObject("SELECT snapshot::text FROM education_releases WHERE id=?",String.class,release));assertEquals(hash,db.queryForObject("SELECT source_hash FROM education_releases WHERE id=?",String.class,release));
  }
  @Test void reviewRechecksNewEvidenceAndPreservesImmutableHistoricalAbsence()throws Exception{
    var current=create("SHOWCASE",2);var unsafe=json.convertValue(current.path("snapshot"),new TypeReference<LinkedHashMap<String,Object>>(){});var evidence=json.convertValue(unsafe.get("dataAiEvidence"),new TypeReference<LinkedHashMap<String,Object>>(){});evidence.put("noRemoteCode",false);unsafe.put("dataAiEvidence",evidence);var denied=historicalFixture(current,unsafe);mvc.perform(body(post("/api/v1/admin/education-releases/"+denied.path("id").asText()+"/review"),"admin",review(denied,"APPROVE"))).andExpect(status().isBadRequest());
    var command=draftBody("SHOWCASE",2);command.remove("dataAiEvidence");var draft=ok(body(post("/api/v1/me/college-projects/"+product+"/releases"),"owner",command));var legacy=json.convertValue(draft.path("snapshot"),new TypeReference<LinkedHashMap<String,Object>>(){});legacy.put("institution","private-historical-canary");legacy.put("storage_key","private-historical-canary");var pending=historicalFixture(draft,legacy);UUID id=UUID.fromString(pending.path("id").asText());String stored=db.queryForObject("SELECT snapshot::text FROM education_releases WHERE id=?",String.class,id);approve(pending);var publicProject=ok(get("/api/v1/college-projects/"+product));assertFalse(publicProject.toString().contains("private-historical-canary"));assertFalse(publicProject.path("education").has("dataAiEvidence"));assertEquals(stored,db.queryForObject("SELECT snapshot::text FROM education_releases WHERE id=?",String.class,id));
  }
  @Test void categoryEvidencePackageDisclosuresAndDistributionConsentAreRequiredAtSubmission()throws Exception{
    for(int index=0;index<4;index++){var command=draftBody("SHOWCASE",index);command.put("categoryEvidence",Map.of());var release=ok(body(post("/api/v1/me/college-projects/"+product+"/releases"),"owner",command));mvc.perform(body(post("/api/v1/me/education-releases/"+release.path("id").asText()+"/submit"),"owner",Map.of("revision",1,"rightsConsent",true))).andExpect(status().isBadRequest());}
    var release=create("SHOWCASE",0);mvc.perform(body(post("/api/v1/me/education-releases/"+release.path("id").asText()+"/submit"),"owner",Map.of("revision",1,"rightsConsent",false))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("RIGHTS_CONSENT_REQUIRED"));var command=draftBody("SHOWCASE",0);command.put("package",Map.of("licenseTerms","A correct license alone is insufficient."));var incomplete=ok(body(post("/api/v1/me/college-projects/"+product+"/releases"),"owner",command));mvc.perform(body(post("/api/v1/me/education-releases/"+incomplete.path("id").asText()+"/submit"),"owner",Map.of("revision",1,"rightsConsent",true))).andExpect(status().isBadRequest());
  }
  @Test void privateNetworkCredentialsAndFakeHostedClaimsAreRejected()throws Exception{
    for(String url:List.of("https://127.0.0.1/demo","http://localhost/demo","https://user:password@example.com/demo")){var command=draftBody("SHOWCASE",0);command.put("demoMode","EXTERNAL");command.put("demoUrl",url);var release=ok(body(post("/api/v1/me/college-projects/"+product+"/releases"),"owner",command));mvc.perform(body(post("/api/v1/me/education-releases/"+release.path("id").asText()+"/submit"),"owner",Map.of("revision",1,"rightsConsent",true))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("UNSAFE_EXTERNAL_URL"));}
    var command=draftBody("SHOWCASE",0);command.put("demoMode","HOSTED");var release=ok(body(post("/api/v1/me/college-projects/"+product+"/releases"),"owner",command));mvc.perform(body(post("/api/v1/me/education-releases/"+release.path("id").asText()+"/submit"),"owner",Map.of("revision",1,"rightsConsent",true))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EDUCATION_DEMO_UNAVAILABLE"));
  }
  @Test void privateFreeUploadHasExactFilesAndNeverCreatesPurchaseAccounting()throws Exception{
    var draft=create("FREE",0);mvc.perform(upload(draft,archive(0),false)).andExpect(status().isBadRequest());mvc.perform(upload(draft,new byte[32],true)).andExpect(status().isBadRequest());var uploaded=ok(upload(draft,archive(0),true));assertEquals(2,uploaded.path("revision").asInt());assertEquals(SourceArchive.inspect(archive(0)).sha256(),uploaded.path("sourceBinding").path("sha256").asText());assertEquals(SourceArchive.inspect(archive(0)).files().size(),ok(as(get("/api/v1/me/education-releases/"+uploaded.path("id").asText()),"owner")).path("sourceBinding").path("files").size());mvc.perform(upload(draft,archive(0),true)).andExpect(status().isConflict());assertEquals(0,db.queryForObject("SELECT count(*) FROM template_purchases",Integer.class));assertEquals(SourceArchive.inspect(archive(0)).files(),json.readValue(db.queryForObject("SELECT files::text FROM education_free_packages",String.class),List.class));assertFalse(uploaded.toString().contains("storage_key"));assertFalse(uploaded.toString().contains("commerce/"));
  }
  // Synthetic historical package rows have no stored objects. They include superseded/unlinked uploads.
  void retainedPackages(UUID user,int count){
    db.update("INSERT INTO education_free_packages(id,owner_id,storage_key,sha256,size_bytes,entry_count,files) SELECT gen_random_uuid(),?,'commerce/'||gen_random_uuid()||'/'||gen_random_uuid()||'.zip',repeat('a',64),22,2,'[]'::jsonb FROM generate_series(1,?)",user,count);
  }
  @Test void retainedSourceBudgetCountsReplacementsModeChangesAndOtherProjectsPerOwner()throws Exception{
    byte[] bytes=archive(0);
    var first=ok(upload(create("FREE",0),bytes,true));
    var replacement=ok(upload(first,bytes,true));
    assertNotEquals(first.path("freePackageId"),replacement.path("freePackageId"));
    retainedPackages(owner,98);
    mvc.perform(upload(replacement,bytes,true)).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EDUCATION_SOURCE_LIMIT"));
    String id=replacement.path("id").asText();
    var showcase=ok(body(patch("/api/v1/me/education-releases/"+id),"owner",Map.of("revision",replacement.path("revision").asLong(),"mode","SHOWCASE")));
    var free=ok(body(patch("/api/v1/me/education-releases/"+id),"owner",Map.of("revision",showcase.path("revision").asLong(),"mode","FREE")));
    mvc.perform(upload(free,bytes,true)).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EDUCATION_SOURCE_LIMIT"));
    var another=ok(body(post("/api/v1/me/college-projects/"+product(owner)+"/releases"),"owner",draftBody("FREE",0)));
    mvc.perform(upload(another,bytes,true)).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EDUCATION_SOURCE_LIMIT"));
    verify(storage,times(2)).put(any(UUID.class),any(byte[].class),anyString());
    var independent=ok(body(post("/api/v1/me/college-projects/"+product(other)+"/releases"),"other",draftBody("FREE",0)));
    ok(as(multipart("/api/v1/me/education-releases/"+independent.path("id").asText()+"/source").file(new MockMultipartFile("file","source.zip","application/zip",bytes)).param("revision","1").param("rightsConsent","true"),"other").header("Origin","http://localhost:3000").header("X-Requested-With","getlancer"));
    assertEquals(100,db.queryForObject("SELECT count(*) FROM education_free_packages WHERE owner_id=?",Integer.class,owner));
    assertEquals(1,db.queryForObject("SELECT count(*) FROM education_free_packages WHERE owner_id=?",Integer.class,other));
  }
  @Test void concurrentUploadsAcrossProjectsCommitOnlyOneFinalSourceSlot()throws Exception{
    var first=create("FREE",0);
    var second=ok(body(post("/api/v1/me/college-projects/"+product(owner)+"/releases"),"owner",draftBody("FREE",0)));
    retainedPackages(owner,99);byte[] bytes=archive(0);
    var uploaded=new java.util.concurrent.CountDownLatch(2);
    when(storage.put(any(UUID.class),any(byte[].class),anyString())).thenAnswer(invocation->{
      String key="commerce/"+invocation.getArgument(0)+"/"+UUID.randomUUID()+".zip";
      objects.put(key,((byte[])invocation.getArgument(1)).clone());uploaded.countDown();
      assertTrue(uploaded.await(10,java.util.concurrent.TimeUnit.SECONDS),"Storage calls must run outside the owner transaction lock.");return key;
    });
    var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
    try{
      var one=pool.submit(()->mvc.perform(upload(first,bytes,true)).andReturn().getResponse().getStatus());
      var two=pool.submit(()->mvc.perform(upload(second,bytes,true)).andReturn().getResponse().getStatus());
      assertEquals(List.of(200,409),List.of(one.get(20,java.util.concurrent.TimeUnit.SECONDS),two.get(20,java.util.concurrent.TimeUnit.SECONDS)).stream().sorted().toList());
      assertEquals(100,db.queryForObject("SELECT count(*) FROM education_free_packages WHERE owner_id=?",Integer.class,owner));
      assertEquals(1,objects.size());verify(storage,times(1)).discardUnlinked(anyString());
      assertEquals(1,db.queryForObject("SELECT count(*) FROM education_audit WHERE action='SOURCE_UPLOADED'",Integer.class));
    }finally{pool.shutdownNow();assertTrue(pool.awaitTermination(15,java.util.concurrent.TimeUnit.SECONDS));}
  }
  @Test void sourceBudgetDoesNotRevokeHistoricalApprovalOrDownloads()throws Exception{
    var uploaded=ok(upload(create("FREE",0),archive(0),true));retainedPackages(owner,105);
    var pending=submit(uploaded);String id=pending.path("id").asText();
    mvc.perform(body(post("/api/v1/admin/education-releases/"+id+"/package"),"admin",Map.of())).andExpect(status().isOk());approve(pending);
    assertTrue(ok(get("/api/v1/education-releases/"+id+"/source-offer")).path("available").asBoolean());
    assertArrayEquals(archive(0),mvc.perform(get("/api/v1/education-releases/"+id+"/source")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    assertEquals(106,db.queryForObject("SELECT count(*) FROM education_free_packages WHERE owner_id=?",Integer.class,owner));
    mvc.perform(upload(create("FREE",0),archive(0),true)).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EDUCATION_SOURCE_LIMIT"));
    verify(storage,times(1)).put(any(UUID.class),any(byte[].class),anyString());
  }
  @Test void confirmedUploadRollbackDiscardsOnlyItsUnlinkedPrivateObject()throws Exception{
    var draft=create("FREE",0);String id=draft.path("id").asText(),key="commerce/"+UUID.randomUUID()+"/"+UUID.randomUUID()+".zip";when(storage.put(any(UUID.class),any(byte[].class),anyString())).thenAnswer(invocation->{objects.put(key,archive(0));db.update("UPDATE education_releases SET revision=revision+1 WHERE id=?",UUID.fromString(id));return key;});mvc.perform(upload(draft,archive(0),true)).andExpect(status().isConflict());verify(storage).discardUnlinked(key);assertFalse(objects.containsKey(key));assertEquals(0,db.queryForObject("SELECT count(*) FROM education_free_packages",Integer.class));
  }
  @Test void exactHashInspectionRequiresUnsafeProtectedPostAndRecentMfaBeforeApproval()throws Exception{
    var pending=freePending();String id=pending.path("id").asText(),path="/api/v1/admin/education-releases/"+id+"/package";mvc.perform(get("/api/v1/education-releases/"+id+"/source")).andExpect(status().isNotFound());mvc.perform(body(post("/api/v1/admin/education-releases/"+id+"/review"),"admin",review(pending,"APPROVE"))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EDUCATION_PACKAGE_REVIEW_REQUIRED"));
    mvc.perform(as(get(path),"admin")).andExpect(status().isForbidden());mvc.perform(as(post(path),"admin")).andExpect(status().isForbidden());mvc.perform(as(post(path),"admin").header("Origin","https://attacker.example.test").header("X-Requested-With","getlancer")).andExpect(status().isForbidden());mvc.perform(body(post(path),"other",Map.of())).andExpect(status().isForbidden());db.update("UPDATE sessions SET issued_at=now()-interval '1 hour' WHERE user_id=?",admin);mvc.perform(body(post(path),"admin",Map.of())).andExpect(status().isForbidden());db.update("UPDATE sessions SET issued_at=now() WHERE user_id=?",admin);assertEquals(0,db.queryForObject("SELECT count(*) FROM education_audit WHERE release_id=? AND action='PACKAGE_REVIEW_DOWNLOAD'",Integer.class,UUID.fromString(id)));
    byte[] inspected=mvc.perform(body(post(path),"admin",Map.of())).andExpect(status().isOk()).andExpect(header().stringValues("Cache-Control","no-store","private, no-store")).andReturn().getResponse().getContentAsByteArray();assertArrayEquals(archive(0),inspected);var wrong=new LinkedHashMap<>(review(pending,"APPROVE"));wrong.put("sourceHash","a".repeat(64));mvc.perform(body(post("/api/v1/admin/education-releases/"+id+"/review"),"admin",wrong)).andExpect(status().isConflict());approve(pending);var offer=ok(get("/api/v1/education-releases/"+id+"/source-offer"));assertTrue(offer.path("available").asBoolean());assertEquals(SourceArchive.inspect(archive(0)).sha256(),offer.path("package").path("sha256").asText());assertArrayEquals(archive(0),mvc.perform(get("/api/v1/education-releases/"+id+"/source")).andExpect(status().isOk()).andExpect(content().contentType("application/zip")).andExpect(header().string("X-Content-Type-Options","nosniff")).andReturn().getResponse().getContentAsByteArray());
    int auditBefore=db.queryForObject("SELECT count(*) FROM education_audit WHERE release_id=?",Integer.class,UUID.fromString(id));
    mvc.perform(get("/api/v1/education-releases/"+id+"/source")).andExpect(status().isOk());
    mvc.perform(as(get("/api/v1/education-releases/"+id+"/source"),"admin")).andExpect(status().isOk());
    assertEquals(auditBefore,db.queryForObject("SELECT count(*) FROM education_audit WHERE release_id=?",Integer.class,UUID.fromString(id)),"Public GET downloads must never create inspection evidence or any audit writes.");
  }
  @Test void reviewedCoreIsImmutableAndHistoricalFreeRemainsFreeAfterNewShowcase()throws Exception{
    var release=freeApproved();String id=release.path("id").asText();var command=draftBody("SHOWCASE",0);command.put("revision",release.path("revision").asLong());mvc.perform(body(patch("/api/v1/me/education-releases/"+id),"owner",command)).andExpect(status().isConflict());assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE education_releases SET snapshot='{}'::jsonb WHERE id=?",UUID.fromString(id)));assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("DELETE FROM education_free_packages"));assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("DELETE FROM education_audit"));var next=approve(submit(create("SHOWCASE",0)));assertEquals("SHOWCASE",ok(get("/api/v1/college-projects/"+product)).path("education").path("mode").asText());assertTrue(ok(get("/api/v1/education-releases/"+id+"/source-offer")).path("available").asBoolean());mvc.perform(get("/api/v1/education-releases/"+id+"/source")).andExpect(status().isOk());assertFalse(ok(get("/api/v1/education-releases/"+next.path("id").asText()+"/source-offer")).path("available").asBoolean());
  }
  @Test void serializedApprovalOrderOverridesEarlierWaitingTransactionStart()throws Exception{
    var first=submit(create("SHOWCASE",0));
    var later=submit(create("SHOWCASE",1));
    var ready=new java.util.concurrent.CountDownLatch(1);
    var proceed=new java.util.concurrent.CountDownLatch(1);
    var waiterPid=new java.util.concurrent.atomic.AtomicInteger();
    var waiterStarted=new java.util.concurrent.atomic.AtomicReference<java.sql.Timestamp>();
    var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
    var transaction=new TransactionTemplate(transactions);
    try{
      var waiting=executor.submit(()->transaction.execute(status->{
        waiterPid.set(db.queryForObject("SELECT pg_backend_pid()",Integer.class));
        waiterStarted.set(db.queryForObject("SELECT transaction_timestamp()",java.sql.Timestamp.class));
        ready.countDown();
        try{
          assertTrue(proceed.await(10,java.util.concurrent.TimeUnit.SECONDS));
          return approve(later);
        }catch(Exception error){throw new IllegalStateException(error);}
      }));
      assertTrue(ready.await(10,java.util.concurrent.TimeUnit.SECONDS));
      transaction.execute(status->{
        assertTrue(db.queryForObject("SELECT transaction_timestamp() > ?",Boolean.class,waiterStarted.get()),"The waiting approval must start its transaction before the holder.");
        db.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","education:"+product);
        proceed.countDown();
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        boolean blocked=false;
        while(System.nanoTime()<deadline){
          blocked=db.queryForObject("SELECT count(*) FROM pg_locks WHERE pid=? AND locktype='advisory' AND NOT granted",Integer.class,waiterPid.get())>0;
          if(blocked)break;
          java.util.concurrent.locks.LockSupport.parkNanos(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(10));
        }
        assertTrue(blocked,"Observe the real PostgreSQL advisory-lock waiter before approving the first release.");
        try{return approve(first);}catch(Exception error){throw new IllegalStateException(error);}
      });
      assertEquals("APPROVED",waiting.get(15,java.util.concurrent.TimeUnit.SECONDS).path("status").asText());
      assertTrue(db.queryForObject("SELECT a.reviewed_at > b.reviewed_at FROM education_releases a JOIN education_releases b ON b.id=? WHERE a.id=?",Boolean.class,UUID.fromString(first.path("id").asText()),UUID.fromString(later.path("id").asText())));
      assertEquals(later.path("id").asText(),ok(get("/api/v1/college-projects/"+product)).path("education").path("release").path("id").asText());
    }finally{proceed.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(15,java.util.concurrent.TimeUnit.SECONDS));}
  }
  @Test void academicSharingCanBeWithdrawnWithoutChangingReleaseRevisionOrHash()throws Exception{
    var release=approve(submit(create("SHOWCASE",0)));String id=release.path("id").asText(),hash=release.path("sourceHash").asText();var command=Map.of("revision",1,"institution","Private College","academicYear","Final year","branch","Private branch","shareAcademicDetails",false);var annotated=ok(body(patch("/api/v1/me/education-releases/"+id+"/academic"),"owner",command));assertEquals("Private College",annotated.path("academic").path("institution").asText());assertFalse(ok(get("/api/v1/college-projects/"+product)).toString().contains("Private College"));assertFalse(db.queryForObject("SELECT snapshot::text FROM education_releases WHERE id=?",String.class,UUID.fromString(id)).contains("Private College"));
    var shared=ok(body(patch("/api/v1/me/education-releases/"+id+"/academic"),"owner",Map.of("revision",2,"institution","Private College","academicYear","Final year","branch","Private branch","shareAcademicDetails",true)));assertEquals("Private College",ok(get("/api/v1/college-projects/"+product)).path("education").path("academic").path("institution").asText());mvc.perform(body(patch("/api/v1/me/education-releases/"+id+"/academic"),"owner",command)).andExpect(status().isConflict());var withdrawn=ok(body(patch("/api/v1/me/education-releases/"+id+"/academic"),"owner",Map.of("revision",shared.path("academic").path("revision").asLong(),"shareAcademicDetails",false)));assertEquals("Private College",withdrawn.path("academic").path("institution").asText());assertEquals(hash,withdrawn.path("sourceHash").asText());assertEquals(release.path("revision"),withdrawn.path("revision"));assertFalse(ok(get("/api/v1/college-projects/"+product)).toString().contains("Private College"));
  }
  @Test void currentProfileRoleProductAndReleaseSuspensionRevokeFreeAccess()throws Exception{
    var release=freeApproved();String id=release.path("id").asText();db.update("UPDATE developer_profiles SET approval_status='CHANGES_REQUESTED' WHERE user_id=?",owner);mvc.perform(get("/api/v1/education-releases/"+id+"/source")).andExpect(status().isConflict());db.update("UPDATE developer_profiles SET approval_status='APPROVED' WHERE user_id=?",owner);db.update("DELETE FROM user_roles WHERE user_id=? AND role='DEVELOPER'",owner);mvc.perform(get("/api/v1/education-releases/"+id+"/source")).andExpect(status().isConflict());db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER')",owner);ok(body(post("/api/v1/admin/education-releases/"+id+"/review"),"admin",review(release,"SUSPEND")));mvc.perform(get("/api/v1/education-releases/"+id+"/source")).andExpect(status().isNotFound());assertEquals("SUSPENDED",db.queryForObject("SELECT approval_status FROM products WHERE id=?",String.class,product));assertEquals("SUSPENDED",db.queryForObject("SELECT status FROM college_project_metadata WHERE product_id=?",String.class,product));
  }
  @Test void componentLinksPinExistingImmutableLicenseAndCurrentWithdrawalRemovesSourceAccess()throws Exception{
    UUID component=UUID.randomUUID();String source=json.writeValueAsString(Map.of("license","MIT","sha256","b".repeat(64),"kind","FRONTEND","version","1.0.0")),context=json.writeValueAsString(Map.of("title","Reusable source","summary","Original reusable MIT source","contribution","Built the reusable source"));db.update("INSERT INTO component_entries(id,owner_id,recipe_slug,slug,title,summary,contribution,status,published_at,published_source,published_context) VALUES(?,?,'original',?,'Reusable source','Original reusable MIT source','Built the reusable source','ACTIVE',now(),?::jsonb,?::jsonb)",component,other,"component-"+component,source,context);db.update("INSERT INTO component_releases(component_id,revision,source,context,source_sha256) VALUES(?,1,?::jsonb,?::jsonb,?)",component,source,context,"b".repeat(64));var command=draftBody("SHOWCASE",0);command.put("componentLinks",List.of(Map.of("componentId",component,"revision",1,"license","MIT","attribution","Original MIT reusable component by the Other builder; notice retained.")));var draft=ok(body(post("/api/v1/me/college-projects/"+product+"/releases"),"owner",command));approve(submit(draft));assertEquals("MIT",ok(get("/api/v1/college-projects/"+product)).path("education").path("componentLinks").get(0).path("license").asText());for(String editing:List.of("DRAFT","PENDING")){db.update("UPDATE component_entries SET status=? WHERE id=?",editing,component);assertEquals(1,ok(get("/api/v1/college-projects/"+product)).path("education").path("componentLinks").size());}db.update("UPDATE component_entries SET status='ARCHIVED',withdrawn_at=now() WHERE id=?",component);assertEquals(0,ok(get("/api/v1/college-projects/"+product)).path("education").path("componentLinks").size());var publicEducation=ok(get("/api/v1/college-projects/"+product)).path("education");assertEquals(0,publicEducation.path("release").path("snapshot").path("componentLinks").size());assertFalse(publicEducation.toString().contains(component.toString()));assertEquals(0,ok(get("/api/v1/education-releases/"+draft.path("id").asText()+"/source-offer")).path("release").path("snapshot").path("componentLinks").size());var second=ok(body(post("/api/v1/me/college-projects/"+product+"/releases"),"owner",command));mvc.perform(body(post("/api/v1/me/education-releases/"+second.path("id").asText()+"/submit"),"owner",Map.of("revision",1,"rightsConsent",true))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EDUCATION_COMPONENT_UNAVAILABLE"));
  }
  @Test void existingTeamOwnerConsentProjectsOnlyCurrentEligibleMembership()throws Exception{
    approve(submit(create("SHOWCASE",0)));UUID team=UUID.randomUUID();
    db.update("INSERT INTO teams(id,slug,name,summary,availability,project_range,owner_id) VALUES(?,?,'Consenting team','Team of original contributors','Available','Small original projects',?)",team,"team-"+team,other);
    db.update("INSERT INTO team_members(team_id,user_id,role,membership_type) VALUES(?,?,'OWNER','PERMANENT'),(?,?,'MEMBER','PERMANENT')",team,other,team,owner);
    db.update("INSERT INTO team_projects(team_id,product_id,consented_by) VALUES(?,?,?)",team,product,owner);
    assertEquals("Consenting team",ok(get("/api/v1/college-projects/"+product)).path("education").path("teams").get(0).path("name").asText());
    db.update("UPDATE users SET account_status='SUSPENDED' WHERE id=?",other);assertEquals(0,ok(get("/api/v1/college-projects/"+product)).path("education").path("teams").size());
    db.update("UPDATE users SET account_status='ACTIVE' WHERE id=?",other);db.update("UPDATE developer_profiles SET approval_status='SUSPENDED' WHERE user_id=?",other);assertEquals(0,ok(get("/api/v1/college-projects/"+product)).path("education").path("teams").size());
    db.update("UPDATE developer_profiles SET approval_status='APPROVED' WHERE user_id=?",other);db.update("DELETE FROM team_projects WHERE team_id=?",team);
    assertEquals(0,ok(get("/api/v1/college-projects/"+product)).path("education").path("teams").size());assertFalse(ok(get("/api/v1/college-projects/"+product)).toString().contains("other@example.test"));
  }
  @Test void concurrentDraftUpdatesAcceptOnlyOneCurrentRevision()throws Exception{
    var draft=create("SHOWCASE",0);String id=draft.path("id").asText();var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var start=new java.util.concurrent.CountDownLatch(1);try{var one=pool.submit(()->{start.await();return mvc.perform(body(patch("/api/v1/me/education-releases/"+id),"owner",Map.of("revision",1,"mode","SHOWCASE","category","AI_ML"))).andReturn().getResponse().getStatus();});var two=pool.submit(()->{start.await();return mvc.perform(body(patch("/api/v1/me/education-releases/"+id),"owner",Map.of("revision",1,"mode","SHOWCASE","category","IOT"))).andReturn().getResponse().getStatus();});start.countDown();assertEquals(Set.of(200,409),Set.of(one.get(15,java.util.concurrent.TimeUnit.SECONDS),two.get(15,java.util.concurrent.TimeUnit.SECONDS)));assertEquals(2,db.queryForObject("SELECT revision FROM education_releases WHERE id=?",Integer.class,UUID.fromString(id)));}finally{pool.shutdownNow();}
  }
  @Test void separateCollegePublishingCapacityDoesNotExpandFromDrafts()throws Exception{
    for(int index=0;index<3;index++){UUID p=index==0?product:product(owner);submit(ok(body(post("/api/v1/me/college-projects/"+p+"/releases"),"owner",draftBody("SHOWCASE",0))));}UUID p=product(owner);var draft=ok(body(post("/api/v1/me/college-projects/"+p+"/releases"),"owner",draftBody("SHOWCASE",0)));mvc.perform(body(post("/api/v1/me/education-releases/"+draft.path("id").asText()+"/submit"),"owner",Map.of("revision",1,"rightsConsent",true))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SLOT_LIMIT_REACHED"));assertEquals(3,db.queryForObject("SELECT count(*) FROM college_project_metadata e JOIN products p ON p.id=e.product_id WHERE p.owner_user_id=?",Integer.class,owner));
  }
  @Test void reportPinsCanonicalArtifactObjectWithoutPublicPrivateFactsAndModerationRevokesSource()throws Exception{
    var release=freeApproved();String id=release.path("id").asText();var reference=json.readTree(mvc.perform(body(post("/api/v1/reports"),null,Map.of("targetType","PRODUCT","targetId",product,"educationReleaseId",id,"reason","MISLEADING_CLAIM","detail","The advertised setup evidence needs independent artifact review."))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());assertEquals(1,reference.size());String report=reference.path("reference").asText();mvc.perform(as(get("/api/v1/admin/reports/"+report),"other")).andExpect(status().isForbidden());var detail=ok(as(get("/api/v1/admin/reports/"+report),"admin")).path("report");assertEquals(id,detail.path("education_release_id").asText());assertEquals(release.path("sourceHash").asText(),detail.path("source_hash").asText());assertTrue(detail.path("education_snapshot").isObject());assertEquals(release.path("snapshot"),detail.path("education_snapshot"));assertFalse(detail.path("education_snapshot").toString().contains("institution"));ok(body(post("/api/v1/admin/reports/"+report+"/resolve"),"admin",Map.of("targetAction","SUSPEND","reason","Suspend the exact reported package pending independent evidence review.")));mvc.perform(get("/api/v1/education-releases/"+id+"/source")).andExpect(status().isConflict());
  }
  @Test void actualOwnerExportEmailConfirmedDeletionAndRetentionEraseAnnotationsButPreserveAgreement()throws Exception{
    var release=freeApproved();String id=release.path("id").asText();UUID releaseId=UUID.fromString(id);ok(body(patch("/api/v1/me/education-releases/"+id+"/academic"),"owner",Map.of("revision",1,"institution","Private retained college","academicYear","Final year","branch","Private retained branch","shareAcademicDetails",false)));db.update("UPDATE college_project_metadata SET institution='Private legacy college',academic_year='Final year',branch='Private legacy branch',share_academic_details=false WHERE product_id=?",product);String frozen=db.queryForObject("SELECT snapshot::text FROM education_releases WHERE id=?",String.class,releaseId);int audit=db.queryForObject("SELECT count(*) FROM education_audit WHERE release_id=?",Integer.class,releaseId);
    var ownExport=ok(as(get("/api/v1/me/export"),"owner"));var exported=ownExport.path("educationReleases").get(0);assertEquals(id,exported.path("id").asText());assertEquals("Private retained college",exported.path("institution").asText());assertEquals(release.path("sourceHash").asText(),exported.path("source_hash").asText());assertTrue(exported.hasNonNull("free_package_id"));assertFalse(ownExport.toString().contains("storage_key"));var foreign=ok(as(get("/api/v1/me/export"),"other"));assertEquals(0,foreign.path("educationReleases").size());assertFalse(foreign.toString().contains(id));assertFalse(foreign.toString().contains("Private retained college"));
    mvc.perform(body(delete("/api/v1/me"),"owner",Map.of())).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("EMAIL_CONFIRMATION_REQUIRED"));String email=db.queryForObject("SELECT body FROM email_outbox WHERE recipient='owner@example.test' AND subject='Confirm closing your getLancer account' ORDER BY created_at DESC LIMIT 1",String.class);String token=email.substring(email.indexOf("/confirm#token=")+"/confirm#token=".length()).split("\\n",2)[0].trim();ok(body(post("/api/v1/auth/confirm"),null,Map.of("token",token,"decision","DELETE")));mvc.perform(as(get("/api/v1/me/export"),"owner")).andExpect(status().isUnauthorized());mvc.perform(get("/api/v1/education-releases/"+id+"/source")).andExpect(status().isConflict());var retained=ok(body(post("/api/v1/admin/deletion-requests/"+owner+"/review"),"admin",Map.of("action","ANONYMIZE_PROFILE","reason","Apply the approved retention policy and erase private academic annotations.")));assertEquals("PROFILE_ANONYMIZED",retained.path("status").asText());assertEquals(0,db.queryForObject("SELECT count(*) FROM education_private_details WHERE release_id=?",Integer.class,releaseId));var legacy=db.queryForMap("SELECT institution,academic_year,branch,share_academic_details FROM college_project_metadata WHERE product_id=?",product);assertEquals("",legacy.get("institution"));assertEquals("",legacy.get("academic_year"));assertEquals("",legacy.get("branch"));assertEquals(false,legacy.get("share_academic_details"));assertEquals(frozen,db.queryForObject("SELECT snapshot::text FROM education_releases WHERE id=?",String.class,releaseId));assertEquals(release.path("sourceHash").asText(),db.queryForObject("SELECT source_hash FROM education_releases WHERE id=?",String.class,releaseId));assertEquals(audit,db.queryForObject("SELECT count(*) FROM education_audit WHERE release_id=?",Integer.class,releaseId));assertEquals(1,db.queryForObject("SELECT count(*) FROM education_free_packages",Integer.class));
  }
  @Test void paidReleaseBindsActualReviewedSourceApisAndStaysUnavailableWithoutActivation()throws Exception{
    db.update("UPDATE products SET repository_url='https://github.com/example/college-source' WHERE id=?",product);db.update("INSERT INTO repository_verifications(product_id,repository_url,challenge,status,reviewed_at,reviewer_id) VALUES(?,'https://github.com/example/college-source','private-repository-challenge','VERIFIED',now(),?)",product,admin);
    var template=json.readTree(mvc.perform(body(post("/api/v1/me/templates"),"owner",Map.of("productId",product,"title","Original paid source","summary","Original inspected source package","description","Original source with exact package, setup and redistribution notices.","priceMinor",12000,"licenseTerms","Commercial use for one end product; retain original MIT and third-party source notices."))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());String templateId=template.path("id").asText();
    var source=json.readTree(mvc.perform(as(multipart("/api/v1/me/templates/"+templateId+"/versions").file(new MockMultipartFile("file","source.zip","application/zip",archive(0))).param("version","paid-source-1").param("releaseNotes","Original inspectable source, setup and third-party notices.").param("rightsConsent","true"),"owner").header("Origin","http://localhost:3000").header("X-Requested-With","getlancer")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());String sourceId=source.path("id").asText();ok(body(post("/api/v1/me/templates/"+templateId+"/versions/"+sourceId+"/submit"),"owner",Map.of("rightsConsent",true)));ok(body(post("/api/v1/admin/templates/"+templateId+"/versions/"+sourceId+"/review"),"admin",Map.of("action","APPROVE","reason","Reviewed exact original source, supported manifest and redistribution notices.","rightsReviewed",true,"packageReviewed",true)));
    String license=db.queryForObject("SELECT license_terms FROM source_versions WHERE id=?",String.class,UUID.fromString(sourceId));var command=draftBody("PAID",0);var pack=json.convertValue(command.get("package"),new TypeReference<LinkedHashMap<String,Object>>(){});pack.put("licenseTerms",license);command.put("package",pack);command.put("versionId",sourceId);var draft=ok(body(post("/api/v1/me/college-projects/"+product+"/releases"),"owner",command));assertEquals(sourceId,draft.path("sourceBinding").path("versionId").asText());assertEquals(sourceId,ok(as(get("/api/v1/me/education-releases/"+draft.path("id").asText()),"owner")).path("sourceBinding").path("versionId").asText());var approved=approve(submit(draft));String id=approved.path("id").asText();assertEquals(12000,approved.path("snapshot").path("priceMinor").asInt());assertEquals(sourceId,approved.path("snapshot").path("sourceBinding").path("versionId").asText());assertEquals(license,approved.path("snapshot").path("sourceBinding").path("licenseTerms").asText());assertEquals(SourceArchive.inspect(archive(0)).sha256(),approved.path("snapshot").path("sourceBinding").path("sha256").asText());
    ok(body(patch("/api/v1/me/education-releases/"+id+"/academic"),"owner",Map.of("revision",1,"institution","Private paid college","shareAcademicDetails",false)));var ownExport=ok(as(get("/api/v1/me/export"),"owner")).path("educationReleases").get(0);assertEquals(sourceId,ownExport.path("source_version_id").asText());assertEquals(templateId,ownExport.path("snapshot").path("sourceBinding").path("templateId").asText());assertEquals(approved.path("sourceHash").asText(),ownExport.path("source_hash").asText());assertEquals("Private paid college",ownExport.path("institution").asText());var foreign=ok(as(get("/api/v1/me/export"),"other"));assertEquals(0,foreign.path("educationReleases").size());assertFalse(foreign.toString().contains(sourceId));assertFalse(foreign.toString().contains("Private paid college"));
    var offer=ok(get("/api/v1/education-releases/"+id+"/source-offer"));assertFalse(offer.path("available").asBoolean());assertFalse(offer.path("checkoutAvailable").asBoolean());assertFalse(offer.path("paidEnabled").asBoolean());mvc.perform(get("/api/v1/education-releases/"+id+"/source")).andExpect(status().isNotFound());mvc.perform(body(post("/api/v1/templates/"+templateId+"/orders"),"other",Map.of("versionId",sourceId,"educationReleaseId",id,"licenseConsent",true)).header("Idempotency-Key",UUID.randomUUID().toString())).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("EDUCATION_PAID_UNAVAILABLE"));assertEquals(0,db.queryForObject("SELECT count(*) FROM template_purchases",Integer.class));verify(provider,never()).createOrder(any(UUID.class),anyLong(),anyString());
    var next=submit(ok(body(post("/api/v1/me/college-projects/"+product+"/releases"),"owner",command)));db.update("UPDATE source_templates SET price_minor=13000 WHERE id=?",UUID.fromString(templateId));mvc.perform(body(post("/api/v1/admin/education-releases/"+next.path("id").asText()+"/review"),"admin",review(next,"APPROVE"))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EDUCATION_SOURCE_CHANGED"));
  }

  /** Frozen historical fixture isolates health projection from live external DNS/publication I/O. */
  JsonNode externalFixture(String url)throws Exception{
    approve(submit(create("SHOWCASE",0)));
    var draft=create("SHOWCASE",0);var snapshot=json.convertValue(draft.path("snapshot"),new TypeReference<LinkedHashMap<String,Object>>(){});
    snapshot.put("demoMode","EXTERNAL");snapshot.put("demoUrl",url);var pending=historicalFixture(draft,snapshot);
    db.update("UPDATE education_releases SET status='APPROVED',reviewed_at=clock_timestamp() WHERE id=?",UUID.fromString(pending.path("id").asText()));
    return ok(get("/api/v1/college-projects/"+product)).path("education").path("release");
  }
  void health(JsonNode release,String status,String url,String hash,String age){
    db.update("UPDATE education_releases SET demo_health=?,demo_checked_at=clock_timestamp()+(?*interval '1 second'),demo_checked_url=?,demo_checked_source_hash=? WHERE id=?",status,Long.parseLong(age),url,hash,UUID.fromString(release.path("id").asText()));
  }
  @Test void currentExternalHealthLabelsBrokenReachableAndRestrictedWithoutChangingFrozenTerms()throws Exception{
    String url="https://example.org/college-demo";var release=externalFixture(url);String id=release.path("id").asText(),hash=release.path("sourceHash").asText(),snapshot=release.path("snapshot").toString();
    for(String state:List.of("UNREACHABLE","REACHABLE","BLOCKED")){
      health(release,state,url,hash,"-60");var current=ok(get("/api/v1/college-projects/"+product)).path("education");
      assertEquals(state,current.path("demoHealth").asText());assertEquals(url,current.path("demoCheckedUrl").asText());assertFalse(current.path("demoCheckedAt").asText().isBlank());
      assertEquals(state.equals("UNREACHABLE")?"Broken external link":state.equals("REACHABLE")?"External link reachable":"External link blocked or restricted",current.path("demoAvailability").asText());
      var offered=ok(get("/api/v1/education-releases/"+id+"/source-offer")).path("release");assertEquals(state,offered.path("demoHealth").asText());assertEquals(hash,offered.path("sourceHash").asText());assertEquals(snapshot,offered.path("snapshot").toString());
      assertFalse(offered.path("demoAvailability").asText().toLowerCase().contains("verified"));
    }
  }
  @Test void uncheckedStaleFutureWrongUrlAndWrongHashExternalObservationsRemainUnverified()throws Exception{
    String url="https://example.org/college-demo";var release=externalFixture(url);String hash=release.path("sourceHash").asText();
    assertEquals("UNKNOWN",release.path("demoHealth").asText());assertFalse(release.has("demoCheckedAt"));
    for(String age:List.of("-86401","60")){health(release,"UNREACHABLE",url,hash,age);var current=ok(get("/api/v1/college-projects/"+product)).path("education").path("release");assertEquals("UNKNOWN",current.path("demoHealth").asText());assertEquals("Unverified external link",current.path("demoAvailability").asText());assertFalse(current.has("demoCheckedAt"));}
    health(release,"UNREACHABLE",url+"?old",hash,"-60");assertEquals("UNKNOWN",ok(get("/api/v1/college-projects/"+product)).path("education").path("demoHealth").asText());
    health(release,"UNREACHABLE",url,"b".repeat(64),"-60");assertEquals("UNKNOWN",ok(get("/api/v1/college-projects/"+product)).path("education").path("demoHealth").asText());
    var next=create("SHOWCASE",0);health(next,"UNREACHABLE",url,hash,"-60");assertEquals("UNKNOWN",ok(as(get("/api/v1/me/education-releases/"+next.path("id").asText()),"owner")).path("demoHealth").asText("UNKNOWN"));
    db.update("UPDATE products SET visibility='PRIVATE' WHERE id=?",product);mvc.perform(get("/api/v1/college-projects/"+product)).andExpect(status().isNotFound());mvc.perform(get("/api/v1/education-releases/"+release.path("id").asText()+"/source-offer")).andExpect(status().isConflict());
  }
  @Test void scheduledExternalHealthReusesExactCurrentUrlAndSkipsPrivateAndSupersededReleases()throws Exception{
    String url="https://example.org/college-demo";var release=externalFixture(url);UUID id=UUID.fromString(release.path("id").asText());
    db.update("UPDATE products SET live_url=?,demo_health='UNREACHABLE',demo_checked_at=clock_timestamp()-interval '2 minutes',demo_checked_url=? WHERE id=?",url,url,product);
    Object checked=db.queryForObject("SELECT demo_checked_at FROM products WHERE id=?",Object.class,product);
    new DemoHealth(db,true).check();assertEquals("UNREACHABLE",db.queryForObject("SELECT demo_health FROM education_releases WHERE id=?",String.class,id));assertEquals(checked,db.queryForObject("SELECT demo_checked_at FROM education_releases WHERE id=?",Object.class,id));assertEquals(release.path("sourceHash").asText(),db.queryForObject("SELECT demo_checked_source_hash FROM education_releases WHERE id=?",String.class,id));
    var next=create("SHOWCASE",0);var nextSnapshot=json.convertValue(next.path("snapshot"),new TypeReference<LinkedHashMap<String,Object>>(){});nextSnapshot.put("demoMode","EXTERNAL");nextSnapshot.put("demoUrl",url+"?new");var pending=historicalFixture(next,nextSnapshot);UUID nextId=UUID.fromString(pending.path("id").asText());
    health(pending,"UNREACHABLE",url,release.path("sourceHash").asText(),"-60");assertEquals("UNKNOWN",ok(as(get("/api/v1/me/education-releases/"+nextId),"owner")).path("demoHealth").asText());
    db.update("UPDATE education_releases SET status='APPROVED',reviewed_at=clock_timestamp() WHERE id=?",nextId);
    assertEquals("UNKNOWN",ok(get("/api/v1/college-projects/"+product)).path("education").path("demoHealth").asText());
    db.update("UPDATE products SET visibility='PRIVATE' WHERE id=?",product);new DemoHealth(db,true).check();assertEquals(url,db.queryForObject("SELECT demo_checked_url FROM education_releases WHERE id=?",String.class,nextId));
    db.update("UPDATE products SET visibility='PUBLIC',live_url=?,demo_checked_url=? WHERE id=?",url+"?new",url+"?new",product);
    new DemoHealth(db,true).check();assertEquals(url+"?new",db.queryForObject("SELECT demo_checked_url FROM education_releases WHERE id=?",String.class,nextId));assertEquals(url,db.queryForObject("SELECT demo_checked_url FROM education_releases WHERE id=?",String.class,id));
  }

  @Test void unknownBoundedCollegeLanguageStaysPendingAndUnlistedUntilAdministratorApproval()throws Exception{
    String language="Uncatalogued ResearchLang "+"x".repeat(74);assertEquals(100,language.length());
    var command=new LinkedHashMap<String,Object>(Map.of("category","FULL_STACK","language",language,"problem","A new language still needs ordinary contribution review.","outcome","An inspectable original project implemented in the new language.","prerequisites","Install the documented research compiler.","contribution","I built and reproduced the original project with this compiler.","rightsConsent",true));
    assertEquals("PENDING",ok(body(put("/api/v1/me/college-projects/"+product),"owner",command)).path("status").asText());
    assertEquals(language,db.queryForObject("SELECT language FROM college_project_metadata WHERE product_id=?",String.class,product));assertEquals("PENDING",db.queryForObject("SELECT status FROM college_project_metadata WHERE product_id=?",String.class,product));assertEquals(0,ok(get("/api/v1/college-projects").param("language",language)).path("totalItems").asInt());mvc.perform(get("/api/v1/college-projects/"+product)).andExpect(status().isNotFound());
    command.put("language",language+"x");mvc.perform(body(put("/api/v1/me/college-projects/"+product),"owner",command)).andExpect(status().isBadRequest());assertEquals(language,db.queryForObject("SELECT language FROM college_project_metadata WHERE product_id=?",String.class,product));
    long revision=db.queryForObject("SELECT revision FROM college_project_metadata WHERE product_id=?",Long.class,product);ok(body(post("/api/v1/admin/college-projects/"+product+"/review"),"admin",Map.of("revision",revision,"decision","APPROVED","reason","Reviewed the bounded new language and independently reproduced this contribution.")));
    assertEquals(1,ok(get("/api/v1/college-projects").param("language",language)).path("totalItems").asInt());assertEquals(language,ok(get("/api/v1/college-projects/"+product)).path("education").path("language").asText());
  }
  @Test void collegeInquiryUsesOriginalProductAndRechecksAcceptingWorkAndOwnerBeforeEmailConfirmation()throws Exception{
    approve(submit(create("SHOWCASE",0)));db.update("UPDATE developer_profiles SET availability_status='AVAILABLE_NOW' WHERE user_id=?",owner);
    var college=ok(get("/api/v1/college-projects/"+product));assertEquals(product.toString(),college.path("id").asText());
    var command=Map.of("referenceProductId",college.path("id").asText(),"clientEmail","other@example.test","clientName","College visitor","description","Please build an original implementation with similar documented capabilities.","budgetBand","NEED_ESTIMATE","timelineBand","FLEXIBLE","requestType","SIMILAR_BUILD");
    var created=json.readTree(mvc.perform(body(post("/api/v1/inquiries"),null,command).header("Idempotency-Key",UUID.randomUUID().toString())).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());UUID inquiry=UUID.fromString(created.path("id").asText());assertTrue(created.path("confirmationRequired").asBoolean());assertEquals(product,db.queryForObject("SELECT reference_product_id FROM inquiries WHERE id=?",UUID.class,inquiry));assertEquals(owner,db.queryForObject("SELECT developer_user_id FROM inquiries WHERE id=?",UUID.class,inquiry));
    String email=db.queryForObject("SELECT body FROM email_outbox WHERE recipient='other@example.test' AND body LIKE '%/confirm#token=%' ORDER BY created_at DESC LIMIT 1",String.class);String token=email.substring(email.indexOf("#token=")+7).split("\\s",2)[0];int notifications=db.queryForObject("SELECT count(*) FROM notifications WHERE user_id=?",Integer.class,owner);
    db.update("UPDATE products SET available_for_similar_work=false WHERE id=?",product);mvc.perform(body(post("/api/v1/inquiries"),null,command).header("Idempotency-Key",UUID.randomUUID().toString())).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_AVAILABLE"));
    mvc.perform(body(post("/api/v1/inquiries/confirm-email"),null,Map.of("token",token))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_AVAILABLE"));
    db.update("UPDATE products SET available_for_similar_work=true WHERE id=?",product);db.update("UPDATE users SET account_status='SUSPENDED' WHERE id=?",owner);mvc.perform(body(post("/api/v1/inquiries/confirm-email"),null,Map.of("token",token))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_AVAILABLE"));
    assertEquals("CREATED_UNVERIFIED",db.queryForObject("SELECT current_status FROM inquiries WHERE id=?",String.class,inquiry));assertNull(db.queryForObject("SELECT email_confirmed_at FROM inquiries WHERE id=?",Object.class,inquiry));assertNull(db.queryForObject("SELECT used_at FROM account_tokens WHERE token_hash=?",Object.class,Support.hash(token)));assertEquals(notifications,db.queryForObject("SELECT count(*) FROM notifications WHERE user_id=?",Integer.class,owner));
    db.update("UPDATE users SET account_status='ACTIVE' WHERE id=?",owner);ok(body(post("/api/v1/inquiries/confirm-email"),null,Map.of("token",token)));assertEquals("INQUIRY_RECEIVED",db.queryForObject("SELECT current_status FROM inquiries WHERE id=?",String.class,inquiry));assertNotNull(db.queryForObject("SELECT email_confirmed_at FROM inquiries WHERE id=?",Object.class,inquiry));assertEquals(notifications+1,db.queryForObject("SELECT count(*) FROM notifications WHERE user_id=?",Integer.class,owner));
  }
  byte[] sourceWithReviewFindings()throws Exception{
    var bytes=new ByteArrayOutputStream();try(var out=new ZipOutputStream(bytes,StandardCharsets.UTF_8);var in=new ZipInputStream(new ByteArrayInputStream(archive(2)),StandardCharsets.UTF_8)){
      for(ZipEntry entry;(entry=in.getNextEntry())!=null;){out.putNextEntry(new ZipEntry(entry.getName()));in.transferTo(out);out.closeEntry();}
      for(var finding:Map.of("unsupported-accuracy.md","This model claims 99.99% real-world accuracy without a held-out evaluation or supporting evidence.","remote_loader.py","# Reviewer fixture only; this file is never executed.\nfrom transformers import AutoModel\nmodel = AutoModel.from_pretrained('unreviewed/model', trust_remote_code=True)\n").entrySet()){out.putNextEntry(new ZipEntry(finding.getKey()));out.write(finding.getValue().getBytes(StandardCharsets.UTF_8));out.closeEntry();}
    }return bytes.toByteArray();
  }
  @Test void reviewerRequestsChangesForActualUnsupportedModelClaimsAndRemoteLoaderAtExactSourceHash()throws Exception{
    byte[] bytes=sourceWithReviewFindings();var uploaded=ok(upload(create("FREE",2),bytes,true));var pending=submit(uploaded);String id=pending.path("id").asText(),hash=pending.path("sourceHash").asText();assertEquals(SourceArchive.inspect(bytes).sha256(),pending.path("snapshot").path("sourceBinding").path("sha256").asText());assertTrue(pending.path("snapshot").path("dataAiEvidence").path("noRemoteCode").asBoolean());
    // Archive structure/rights attestations are automated; arbitrary-code semantics require a human decision.
    byte[] inspected=mvc.perform(body(post("/api/v1/admin/education-releases/"+id+"/package"),"admin",Map.of())).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();assertArrayEquals(bytes,inspected);
    var content=new LinkedHashMap<String,String>();try(var zip=new ZipInputStream(new ByteArrayInputStream(inspected),StandardCharsets.UTF_8)){for(ZipEntry entry;(entry=zip.getNextEntry())!=null;)content.put(entry.getName(),new String(zip.readAllBytes(),StandardCharsets.UTF_8));}assertTrue(content.get("unsupported-accuracy.md").contains("99.99%"));assertTrue(content.get("remote_loader.py").contains("trust_remote_code=True"));
    var decision=new LinkedHashMap<String,Object>(review(pending,"CHANGES_REQUESTED"));decision.put("reason","The inspected source claims unsupported accuracy and enables a remote loader. Provide a reproduced evaluation and a frozen non-executable model.");
    db.update("UPDATE sessions SET issued_at=now()-interval '1 hour' WHERE user_id=?",admin);mvc.perform(body(post("/api/v1/admin/education-releases/"+id+"/review"),"admin",decision)).andExpect(status().isForbidden());assertEquals("PENDING",db.queryForObject("SELECT status FROM education_releases WHERE id=?",String.class,UUID.fromString(id)));
    db.update("UPDATE sessions SET issued_at=clock_timestamp(),mfa_verified=true WHERE user_id=?",admin);decision.put("sourceHash","b".repeat(64));mvc.perform(body(post("/api/v1/admin/education-releases/"+id+"/review"),"admin",decision)).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EDUCATION_HASH_CONFLICT"));decision.put("sourceHash",hash);
    var reviewed=ok(body(post("/api/v1/admin/education-releases/"+id+"/review"),"admin",decision));assertEquals("CHANGES_REQUESTED",reviewed.path("status").asText());assertEquals(hash,reviewed.path("sourceHash").asText());assertEquals(1,db.queryForObject("SELECT count(*) FROM education_audit WHERE release_id=? AND actor_id=? AND action='CHANGES_REQUESTED' AND source_hash=? AND reason=?",Integer.class,UUID.fromString(id),admin,hash,decision.get("reason")));assertEquals(0,ok(get("/api/v1/college-projects")).path("totalItems").asInt());mvc.perform(get("/api/v1/education-releases/"+id+"/source-offer")).andExpect(status().isNotFound());
  }
}
