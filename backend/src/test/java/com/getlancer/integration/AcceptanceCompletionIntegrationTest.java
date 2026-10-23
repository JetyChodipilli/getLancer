package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.labs.LabProtocolFixture;
import com.getlancer.labs.LabService;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Fifty simultaneous HTTP submissions backed by real PostgreSQL transactions, not mocked admission. */
@ContextConfiguration(initializers=TestDatabaseGuard.class)
@SpringBootTest(properties={"app.environment=local","app.jobs-enabled=false","app.admin-email=acceptance-admin@example.test","app.admin-password=","app.admin-totp=","spring.config.import=",
    "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}","spring.datasource.username=${TEST_DB_USERNAME:postgres}","spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.hikari.schema=getlancer_test","spring.flyway.default-schema=getlancer_test","spring.flyway.schemas=getlancer_test","app.origin=http://localhost:3000","app.secure-cookie=false","app.storage.access-key=","app.storage.secret-key="})
@AutoConfigureMockMvc
class AcceptanceCompletionIntegrationTest {
  static final LabProtocolFixture fixture=new LabProtocolFixture();
  @DynamicPropertySource static void runtime(DynamicPropertyRegistry registry){
    registry.add("app.labs.enabled",()->true);registry.add("app.labs.gateway-origin",fixture::origin);registry.add("app.labs.gateway-secret",()->LabProtocolFixture.SECRET);
    registry.add("app.labs.operator-public-key",fixture::publicKey);registry.add("app.labs.admission-evidence",()->fixture.evidence.toString());registry.add("app.labs.operator-epoch",()->fixture.epoch.toString());registry.add("app.labs.timeout-ms",()->1000);
  }
  @AfterAll static void close(){fixture.close();}
  @Autowired JdbcTemplate db;@Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired LabService labs;
  UUID owner,other,component;String manifestHash;
  UUID user(String token,boolean developer){
    UUID id=UUID.randomUUID();db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",id,token+"@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT')",id);
    if(developer){db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER')",id);db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,?,'APPROVED')",id,token,token);}
    session(id,token);return id;
  }
  void session(UUID id,String token){db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",Support.hash(token),id);}
  @BeforeEach void seed()throws Exception{
    fixture.reset();db.execute("TRUNCATE lab_manifests CASCADE");db.execute("TRUNCATE users CASCADE");db.execute("TRUNCATE rate_buckets,analytics_daily_counts,analytics_events");
    db.update("UPDATE lab_runtime_settings SET observed_epoch=NULL,paused=false,pause_reason='',daily_run_limit=5,max_active_runs=10,max_memory_mib=5120,daily_budget_micros=50000000,monthly_budget_micros=1000000000 WHERE id=1");
    UUID sourceOwner=user("acceptance-source",true);owner=user("acceptance-owner",false);other=user("acceptance-other",false);
    UUID admin=user("acceptance-admin",false);db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')",admin);db.update("UPDATE sessions SET mfa_verified=true,issued_at=now() WHERE user_id=?",admin);
    component=UUID.randomUUID();var source=Map.of("kind","BACKEND","sha256","a".repeat(64),"archiveSha256","c".repeat(64),"manifestSha256","d".repeat(64),"version","1.0.0","files",Map.of("README.md","Controlled protocol test only."));
    db.update("INSERT INTO component_entries(id,owner_id,recipe_slug,slug,title,summary,contribution,status,published_at,published_source,published_context) VALUES(?,?,'protocol-fixture',?,'Protocol source','Controlled PostgreSQL admission','Synthetic source consent','ACTIVE',now(),?::jsonb,'{}'::jsonb)",component,sourceOwner,"acceptance-"+component,json.writeValueAsString(source));
    db.update("INSERT INTO component_releases(component_id,revision,source,context,source_sha256) VALUES(?,1,?::jsonb,'{}'::jsonb,?)",component,json.writeValueAsString(source),"a".repeat(64));
    manifest(64,1000);
  }
  void manifest(int memory,long cost)throws Exception{
    var payload=json.valueToTree(fixture.manifest(component));((com.fasterxml.jackson.databind.node.ObjectNode)payload).put("memoryMiB",memory).put("maxCostMicros",cost);
    String text=payload.toString();manifestHash=Support.hash(text);db.update("INSERT INTO lab_manifests(id,payload_text,payload_sha256,signature) VALUES('protocol-fixture',?,?,?)",text,manifestHash,fixture.sign(text));
  }
  void heavyManifest()throws Exception{db.execute("TRUNCATE lab_manifests CASCADE");manifest(512,1_000_000);}
  MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request,String token){return request.cookie(new Cookie("gl_session",token));}
  MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request,String token,Object input)throws Exception{return as(request,token).header("Origin","http://localhost:3000").header("X-Requested-With","getlancer").contentType("application/json").content(json.writeValueAsString(input));}
  MockHttpServletRequestBuilder start(String token,String key,int client)throws Exception{
    return body(post("/api/v1/lab-runs"),token,Map.of("manifestId","protocol-fixture","scenarioId","request","inputs",Map.of("message","Controlled admission"))).header("Idempotency-Key",key).with(request->{request.setRemoteAddr("198.51.100."+(client+1));return request;});
  }
  record Attempt(int status,String id,String code){}
  List<Attempt> fifty(boolean oneOwner,boolean sameKey)throws Exception{
    var tokens=new ArrayList<String>();
    // Separate legitimate sessions/IPs preserve production HTTP rate limits while exercising account locks.
    for(int n=0;n<50;n++){String token="acceptance-parallel-"+n;if(oneOwner)session(owner,token);else user(token,false);tokens.add(token);}
    var pool=Executors.newFixedThreadPool(50);var ready=new CountDownLatch(50);var release=new CountDownLatch(1);
    try{
      var tasks=new ArrayList<java.util.concurrent.Future<Attempt>>();
      for(int n=0;n<50;n++){final int index=n;tasks.add(pool.submit(()->{
        ready.countDown();if(!release.await(20,TimeUnit.SECONDS))throw new IllegalStateException("Admission barrier timed out");
        var response=mvc.perform(start(tokens.get(index),sameKey?"acceptance-same-key":"acceptance-key-"+index,index)).andReturn().getResponse();
        JsonNode result=json.readTree(response.getContentAsString());return new Attempt(response.getStatus(),result.path("id").asText(),result.path("error").path("code").asText());
      }));}
      assertTrue(ready.await(20,TimeUnit.SECONDS),"Every client must reach the common start barrier.");release.countDown();
      var results=new ArrayList<Attempt>();for(var task:tasks)results.add(task.get(45,TimeUnit.SECONDS));return results;
    }finally{release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
  }
  void admitted(List<Attempt> attempts,int expected,String denial){
    assertEquals(50,attempts.size());assertEquals(expected,attempts.stream().filter(r->r.status()==202).count());
    for(var attempt:attempts)if(attempt.status()!=202){assertEquals(429,attempt.status());assertEquals(denial,attempt.code());}
    assertEquals(expected,db.queryForObject("SELECT count(*) FROM lab_runs WHERE cleanup_confirmed_at IS NULL",Integer.class));
    assertEquals(expected,db.queryForObject("SELECT count(*) FROM lab_outbox WHERE action='START'",Integer.class));
    assertEquals(0,db.queryForObject("SELECT count(*) FROM (SELECT owner_id FROM lab_runs WHERE cleanup_confirmed_at IS NULL GROUP BY owner_id HAVING count(*)>1) duplicate_accounts",Integer.class));
    assertTrue(db.queryForObject("SELECT coalesce(sum(memory_mib),0)<=5120 FROM lab_runs WHERE cleanup_confirmed_at IS NULL",Boolean.class));
    assertEquals(0,db.queryForObject("SELECT count(*) FROM component_slot_purchases",Integer.class));
  }
  @Test void fiftySameKeyRetriesReserveOneIdentityAndOneOutboxCommand()throws Exception{
    var results=fifty(true,true);assertEquals(50,results.stream().filter(r->r.status()==202).count());assertEquals(1,results.stream().map(Attempt::id).distinct().count());
    assertEquals(1,db.queryForObject("SELECT count(*) FROM lab_runs",Integer.class));assertEquals(1,db.queryForObject("SELECT count(*) FROM lab_outbox",Integer.class));
  }
  @Test void fiftyDifferentKeysForOneAccountCannotReserveAdditionalSlots()throws Exception{admitted(fifty(true,false),1,"LAB_ACTIVE_RUN");}
  @Test void fiftyMixedAccountsReachTheGlobalLimitBeforeTheMemoryCeiling()throws Exception{
    admitted(fifty(false,false),10,"LAB_CAPACITY");assertEquals(640,db.queryForObject("SELECT sum(memory_mib) FROM lab_runs",Integer.class));
  }
  @Test void fiftyMixedAccountsFillTheDefaultMemoryCeilingWithoutOversubscription()throws Exception{
    heavyManifest();admitted(fifty(false,false),10,"LAB_CAPACITY");assertEquals(5120,db.queryForObject("SELECT sum(memory_mib) FROM lab_runs",Integer.class));
  }
  @Test void fiftyMixedAccountsRespectAnIndependentlyTightenedMemoryLimit()throws Exception{
    heavyManifest();setLimits(4096,50_000_000,1_000_000_000);admitted(fifty(false,false),8,"LAB_CAPACITY");assertEquals(4096,db.queryForObject("SELECT sum(memory_mib) FROM lab_runs",Integer.class));
  }
  void setLimits(int memory,long daily,long monthly)throws Exception{
    mvc.perform(body(post("/api/v1/admin/labs/limits"),"acceptance-admin",Map.of("dailyRunLimit",5,"maxActiveRuns",10,"maxMemoryMiB",memory,"dailyBudgetMicros",daily,"monthlyBudgetMicros",monthly,"reason","Disposable concurrent admission acceptance."))).andExpect(status().isOk());
  }
  void budgetHistory(String timestamp,int count,long cost){
    db.update("INSERT INTO lab_runs(id,owner_id,manifest_id,scenario_id,request_hash,idempotency_key,inputs,manifest_sha256,image_digest,operator_epoch,status,requested_at,expires_at,healthy_at,cleanup_confirmed_at,memory_mib,cost_micros,quota_counted) SELECT md5('acceptance-history-'||n::text)::uuid,?,'protocol-fixture','request',?,'history-'||n::text,'{}'::jsonb,?,?,?,'SUCCEEDED',"+timestamp+","+timestamp+"+interval '5 minutes',"+timestamp+","+timestamp+",64,?,true FROM generate_series(1,?) n",owner,"a".repeat(64),manifestHash,"sha256:"+"b".repeat(64),fixture.epoch,cost,count);
  }
  @Test void fiftySameAccountAdmissionsCannotRacePastItsLastDailyReservation()throws Exception{
    budgetHistory("now()",4,1000);admitted(fifty(true,false),1,"LAB_QUOTA_EXHAUSTED");assertEquals(5,db.queryForObject("SELECT count(*) FROM lab_runs WHERE owner_id=? AND quota_counted",Integer.class,owner));
  }
  @Test void fiftyMixedAccountsCannotRacePastTheDailyBudget()throws Exception{
    heavyManifest();budgetHistory("now()",49,1_000_000);admitted(fifty(false,false),1,"LAB_CAPACITY");assertEquals(50_000_000L,db.queryForObject("SELECT sum(cost_micros) FROM lab_runs",Long.class));
  }
  @Test void fiftyMixedAccountsCannotRacePastTheConfiguredMonthlyBudget()throws Exception{
    heavyManifest();setLimits(5120,50_000_000,50_000_000);budgetHistory("date_trunc('month',now() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC'",49,1_000_000);
    // On the first UTC day both windows bind; on subsequent days this independently exercises the monthly window.
    admitted(fifty(false,false),1,"LAB_CAPACITY");assertEquals(50_000_000L,db.queryForObject("SELECT sum(cost_micros) FROM lab_runs",Long.class));
  }
  UUID reserve(String token,String key)throws Exception{return UUID.fromString(json.readTree(mvc.perform(start(token,key,100)).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()).path("id").asText());}
  void operation(UUID run,String token,String marker)throws Exception{
    mvc.perform(body(post("/api/v1/lab-runs/"+run+"/requests"),token,Map.of("operationId","echo","inputs",Map.of("message",marker))).header("Idempotency-Key","acceptance-operation-"+run)).andExpect(status().isOk());
  }
  @Test void ownedExportAndConfirmedDeletionDoNotExposeOrCancelAnotherActorsRun()throws Exception{
    UUID mine=reserve("acceptance-owner","owned-private-run"),theirs=reserve("acceptance-other","other-private-run");labs.tick();
    operation(mine,"acceptance-owner","owner-private-marker");operation(theirs,"acceptance-other","other-private-marker");
    String exported=mvc.perform(as(get("/api/v1/me/export"),"acceptance-owner")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertTrue(exported.contains(mine.toString()));assertTrue(exported.contains("owner-private-marker"));assertFalse(exported.contains(theirs.toString()));assertFalse(exported.contains("other-private-marker"));
    for(String secret:List.of("storage_key","gateway-secret",LabProtocolFixture.SECRET,"request_delivery_command","manifest_sha256"))assertFalse(exported.contains(secret));
    String token="acceptance-confirm-deletion-token";db.update("INSERT INTO account_tokens(id,token_hash,kind,user_id,email,expires_at) VALUES(?,?,'ACCOUNT_DELETION',?,'acceptance-owner@example.test',now()+interval '1 hour')",UUID.randomUUID(),Support.hash(token),owner);
    mvc.perform(body(post("/api/v1/auth/confirm"),"acceptance-owner",Map.of("token",token,"decision","DELETE"))).andExpect(status().isOk());
    mvc.perform(as(get("/api/v1/lab-runs/"+mine+"/events"),"acceptance-owner")).andExpect(status().isUnauthorized());
    fixture.failStop=true;labs.tick();assertEquals("CANCELLING",db.queryForObject("SELECT status FROM lab_runs WHERE id=?",String.class,mine));
    assertEquals(1,db.queryForObject("SELECT count(*) FROM lab_outbox WHERE run_id=? AND action='STOP' AND delivered_at IS NULL",Integer.class,mine));
    assertEquals("RUNNING",db.queryForObject("SELECT status FROM lab_runs WHERE id=?",String.class,theirs));
    String otherExport=mvc.perform(as(get("/api/v1/me/export"),"acceptance-other")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertTrue(otherExport.contains("other-private-marker"));assertFalse(otherExport.contains("owner-private-marker"));
  }
  UUID retained(String key,String requested,String requestCreated,String marker)throws Exception{
    UUID id=UUID.randomUUID(),command=UUID.randomUUID();
    db.update("INSERT INTO lab_runs(id,owner_id,manifest_id,scenario_id,request_hash,idempotency_key,inputs,manifest_sha256,image_digest,operator_epoch,status,requested_at,expires_at,healthy_at,cleanup_confirmed_at,memory_mib,cost_micros) VALUES(?,?,'protocol-fixture','request',?,?,?::jsonb,?,?,?,'SUCCEEDED',"+requested+","+requested+"+interval '5 minutes',"+requested+",now(),64,1000)",id,owner,"a".repeat(64),key,json.writeValueAsString(Map.of("message",marker)),manifestHash,"sha256:"+"b".repeat(64),fixture.epoch);
    db.update("INSERT INTO lab_requests(command_id,run_id,idempotency_key,request_hash,lease_generation,operation_id,inputs,response,created_at) VALUES(?,?,? ,?,1,'echo',?::jsonb,?::jsonb,"+requestCreated+")",command,id,"operation-"+key,"b".repeat(64),json.writeValueAsString(Map.of("message",marker)),json.writeValueAsString(Map.of("output",marker)));
    return id;
  }
  @Test void automaticRetentionUsesPayloadAgeAndKeepsRecentBodiesAndImmutableIdentity()throws Exception{
    UUID oldRun=retained("old-run-payload","now()-interval '25 hours'","now()-interval '25 hours'","expired-run-marker");
    UUID recent=retained("recent-payload","now()-interval '23 hours'","now()-interval '23 hours'","recent-safe-marker");labs.tick();
    assertEquals("{}",db.queryForObject("SELECT inputs::text FROM lab_runs WHERE id=?",String.class,oldRun));assertEquals("a".repeat(64),db.queryForObject("SELECT request_hash FROM lab_runs WHERE id=?",String.class,oldRun));
    assertEquals(1,db.queryForObject("SELECT count(*) FROM lab_requests WHERE run_id=? AND inputs='{}'::jsonb AND response IS NULL AND request_hash=?",Integer.class,oldRun,"b".repeat(64)));
    assertTrue(db.queryForObject("SELECT inputs::text FROM lab_runs WHERE id=?",String.class,recent).contains("recent-safe-marker"));assertNotNull(db.queryForObject("SELECT response FROM lab_requests WHERE run_id=?",Object.class,recent));
  }
}
