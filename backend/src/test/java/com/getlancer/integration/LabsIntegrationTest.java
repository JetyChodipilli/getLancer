package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.labs.LabProtocolFixture;
import com.getlancer.labs.LabService;
import com.getlancer.labs.LabDataAiPolicy;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
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

/** Disposable PostgreSQL and controlled HTTP protocol tests; never evidence of a VM or live Redis. */
@ContextConfiguration(initializers=TestDatabaseGuard.class)
@SpringBootTest(properties={"app.environment=local","app.jobs-enabled=false","app.admin-email=lab-admin@example.test","app.admin-password=","app.admin-totp=","spring.config.import=",
  "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}","spring.datasource.username=${TEST_DB_USERNAME:postgres}","spring.datasource.password=${TEST_DB_PASSWORD:}",
  "spring.datasource.hikari.schema=getlancer_test","spring.flyway.default-schema=getlancer_test","spring.flyway.schemas=getlancer_test","app.origin=http://localhost:3000","app.secure-cookie=false","app.storage.access-key=","app.storage.secret-key="})
@AutoConfigureMockMvc
class LabsIntegrationTest {
  static final LabProtocolFixture fixture=new LabProtocolFixture();
  @DynamicPropertySource static void labs(DynamicPropertyRegistry registry){registry.add("app.labs.enabled",()->true);registry.add("app.labs.gateway-origin",fixture::origin);registry.add("app.labs.gateway-secret",()->LabProtocolFixture.SECRET);registry.add("app.labs.operator-public-key",fixture::publicKey);registry.add("app.labs.admission-evidence",()->fixture.evidence.toString());registry.add("app.labs.operator-epoch",()->fixture.epoch.toString());registry.add("app.labs.timeout-ms",()->1000);}
  @AfterAll static void close(){fixture.close();}
  @Autowired JdbcTemplate db;@Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired LabService labs;@Autowired LabDataAiPolicy dataAi;
  UUID sourceOwner,owner,other,admin,component;
  UUID user(String token,boolean builder){UUID id=UUID.randomUUID();db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",id,token+"@example.test");db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT')",id);if(builder){db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER')",id);db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,?,'APPROVED')",id,token,token);}db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",Support.hash(token),id);return id;}
  @BeforeEach void seed()throws Exception{
    org.springframework.test.util.ReflectionTestUtils.setField(dataAi,"enabled",false);
    fixture.reset();db.execute("TRUNCATE lab_manifests CASCADE");db.execute("TRUNCATE users CASCADE");db.execute("TRUNCATE rate_buckets");db.update("UPDATE lab_runtime_settings SET observed_epoch=NULL,paused=false,pause_reason='' WHERE id=1");
    sourceOwner=user("lab-source",true);owner=user("lab-owner",false);other=user("lab-other",false);admin=user("lab-admin",false);
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')",admin);db.update("UPDATE sessions SET mfa_verified=true,issued_at=now() WHERE user_id=?",admin);
    component=UUID.randomUUID();var source=Map.of("kind","BACKEND","sha256","a".repeat(64),"archiveSha256","c".repeat(64),"manifestSha256","d".repeat(64),"version","1.0.0","files",Map.of("README.md","Protocol fixture source; not executable runtime evidence."));
    db.update("INSERT INTO component_entries(id,owner_id,recipe_slug,slug,title,summary,contribution,status,published_at,published_source,published_context) VALUES(?,?,'protocol-fixture',?,'Protocol fixture','Only a controlled HTTP fixture','Test fixture source admission','ACTIVE',now(),?::jsonb,'{}'::jsonb)",component,sourceOwner,"protocol-"+component,json.writeValueAsString(source));
    db.update("INSERT INTO component_releases(component_id,revision,source,context,source_sha256) VALUES(?,1,?::jsonb,'{}'::jsonb,?)",component,json.writeValueAsString(source),"a".repeat(64));
    String payload=json.writeValueAsString(fixture.manifest(component));db.update("INSERT INTO lab_manifests(id,payload_text,payload_sha256,signature) VALUES('protocol-fixture',?,?,?)",payload,Support.hash(payload),fixture.sign(payload));
  }
  MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request,String token){return request.cookie(new Cookie("gl_session",token));}
  MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request,String token,Object value)throws Exception{return as(request,token).header("Origin","http://localhost:3000").header("X-Requested-With","getlancer").contentType("application/json").content(json.writeValueAsString(value));}
  MockHttpServletRequestBuilder start(String token,String key)throws Exception{return body(post("/api/v1/lab-runs"),token,Map.of("manifestId","protocol-fixture","scenarioId","request","inputs",Map.of("message","Hello"))).header("Idempotency-Key",key);}
  void certifyDataAi(String id,String scenario,String kind)throws Exception{
    var manifest=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(fixture.manifest(component));manifest.put("id",id).put("memoryMiB",256);
    if(kind!=null)manifest.put("resourceClass",kind);((com.fasterxml.jackson.databind.node.ObjectNode)manifest.path("scenarios").get(0)).put("id",scenario);
    String payload=manifest.toString();db.update("INSERT INTO lab_manifests(id,payload_text,payload_sha256,signature) VALUES(?,?,?,?)",id,payload,Support.hash(payload),fixture.sign(payload));
  }
  MockHttpServletRequestBuilder startDataAi(String token,String key,String id,String scenario)throws Exception{return body(post("/api/v1/lab-runs"),token,Map.of("manifestId",id,"scenarioId",scenario,"inputs",Map.of("message","Controlled HTTP fixture only"))).header("Idempotency-Key",key);}
  UUID reserveDataAi(String token,String key,String id,String scenario)throws Exception{return UUID.fromString(json.readTree(mvc.perform(startDataAi(token,key,id,scenario)).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()).path("id").asText());}
  @Test void dataAiDefaultDenialPrecedesReservationAndPreservesGeneralLabs()throws Exception{
    certifyDataAi("renamed-data","revenue-summary",null);certifyDataAi("renamed-inference","renamed-scenario","AI_ML");certifyDataAi("data-ai-namespace","request",null);
    for(var pair:java.util.List.of(new String[]{"renamed-data","revenue-summary"},new String[]{"renamed-inference","renamed-scenario"},new String[]{"data-ai-namespace","request"})){
      mvc.perform(startDataAi("lab-owner","deny-"+pair[0],pair[0],pair[1])).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("DATA_AI_DISABLED"));
    }
    assertEquals(0,db.queryForObject("SELECT count(*) FROM lab_runs",Integer.class));assertEquals(0,db.queryForObject("SELECT count(*) FROM lab_outbox",Integer.class));
    var catalogue=json.readTree(mvc.perform(get("/api/v1/lab-manifests")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());assertEquals(1,catalogue.path("items").size());
    reserve("lab-owner","ordinary-still-admitted");
  }
  @Test void dataAiRevocationClosesRequestsEventsAndFencesConfirmedCleanup()throws Exception{
    certifyDataAi("signed-ai","equipment-inference","AI_ML");org.springframework.test.util.ReflectionTestUtils.setField(dataAi,"enabled",true);
    UUID id=reserveDataAi("lab-owner","ai-healthy","signed-ai","equipment-inference");labs.tick();assertTrue(getRun(id,"lab-owner").path("verified").asBoolean());
    org.springframework.test.util.ReflectionTestUtils.setField(dataAi,"enabled",false);
    mvc.perform(body(post("/api/v1/lab-runs/"+id+"/requests"),"lab-owner",Map.of("operationId","echo","inputs",Map.of("message","Denied"))).header("Idempotency-Key","ai-denied-request")).andExpect(status().isForbidden());
    mvc.perform(as(get("/api/v1/lab-runs/"+id+"/events"),"lab-owner")).andExpect(status().isForbidden());assertFalse(getRun(id,"lab-owner").path("verified").asBoolean());
    assertEquals(0,db.queryForObject("SELECT count(*) FROM lab_requests WHERE run_id=?",Integer.class,id));
    labs.tick();assertEquals("FAILED",getRun(id,"lab-owner").path("status").asText());assertNotNull(db.queryForObject("SELECT cleanup_confirmed_at FROM lab_runs WHERE id=?",java.sql.Timestamp.class,id));assertTrue(db.queryForObject("SELECT quota_counted FROM lab_runs WHERE id=?",Boolean.class,id));
    reserve("lab-owner","ordinary-after-ai-cleanup");
  }
  @Test void dataAiQueuedRevocationNeverStartsProviderAndRefundsAfterCleanup()throws Exception{
    certifyDataAi("signed-data","sensor-quality","DATA_ANALYTICS");org.springframework.test.util.ReflectionTestUtils.setField(dataAi,"enabled",true);
    UUID id=reserveDataAi("lab-owner","data-queued","signed-data","sensor-quality");org.springframework.test.util.ReflectionTestUtils.setField(dataAi,"enabled",false);
    fixture.failStop=true;labs.tick();assertEquals("CANCELLING",getRun(id,"lab-owner").path("status").asText());assertNull(db.queryForObject("SELECT cleanup_confirmed_at FROM lab_runs WHERE id=?",java.sql.Timestamp.class,id));assertTrue(db.queryForObject("SELECT quota_counted FROM lab_runs WHERE id=?",Boolean.class,id));
    assertTrue(fixture.commands.values().stream().noneMatch(c->c.runId().equals(id)&&c.action().equals("START")));
    fixture.failStop=false;db.update("UPDATE lab_outbox SET next_attempt_at=now() WHERE run_id=?",id);labs.tick();assertEquals("FAILED",getRun(id,"lab-owner").path("status").asText());assertFalse(db.queryForObject("SELECT quota_counted FROM lab_runs WHERE id=?",Boolean.class,id));
  }
  @Test void dataAndAiHaveSeparateReservationsWhichUncertainCleanupRetains()throws Exception{
    certifyDataAi("signed-data","revenue-summary","DATA_ANALYTICS");certifyDataAi("signed-ai","sentiment-inference","AI_ML");org.springframework.test.util.ReflectionTestUtils.setField(dataAi,"enabled",true);
    UUID first=null;
    for(int i=0;i<4;i++){String token="data-class-"+i;user(token,false);UUID id=reserveDataAi(token,"class-data-"+i,"signed-data","revenue-summary");if(i==0)first=id;}
    user("data-overflow",false);mvc.perform(startDataAi("data-overflow","data-class-overflow","signed-data","revenue-summary")).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error.code").value("DATA_AI_CAPACITY"));
    for(int i=0;i<4;i++){String token="ai-class-"+i;user(token,false);reserveDataAi(token,"class-ai-"+i,"signed-ai","sentiment-inference");}
    assertEquals(2048,db.queryForObject("SELECT sum(memory_mib) FROM lab_runs WHERE cleanup_confirmed_at IS NULL",Integer.class));
    fixture.failStop=true;mvc.perform(body(post("/api/v1/lab-runs/"+first+"/stop"),"data-class-0",Map.of())).andExpect(status().isOk());labs.tick();
    mvc.perform(startDataAi("data-overflow","data-before-confirmed-cleanup","signed-data","revenue-summary")).andExpect(status().isTooManyRequests());
    fixture.failStop=false;db.update("UPDATE lab_outbox SET next_attempt_at=now() WHERE run_id=?",first);labs.tick();reserveDataAi("data-overflow","data-after-confirmed-cleanup","signed-data","revenue-summary");
  }
  UUID reserve(String token,String key)throws Exception{return UUID.fromString(json.readTree(mvc.perform(start(token,key)).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()).path("id").asText());}
  JsonNode getRun(UUID id,String token)throws Exception{return json.readTree(mvc.perform(as(get("/api/v1/lab-runs/"+id),token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
  @Test void ownerPrivateIdempotentReservationAndHealthyFixtureOperation()throws Exception{
    UUID id=reserve("lab-owner","stable-run-identity");assertEquals(id,reserve("lab-owner","stable-run-identity"));assertEquals(1,db.queryForObject("SELECT count(*) FROM lab_runs",Integer.class));assertFalse(getRun(id,"lab-owner").path("verified").asBoolean());
    mvc.perform(as(get("/api/v1/lab-runs/"+id),"lab-other")).andExpect(status().isNotFound());mvc.perform(as(get("/api/v1/lab-runs/"+id+"/events"),"lab-other")).andExpect(status().isNotFound());
    mvc.perform(body(post("/api/v1/lab-runs"),"lab-owner",Map.of("manifestId","protocol-fixture","scenarioId","request","inputs",Map.of("message","Different"))).header("Idempotency-Key","stable-run-identity")).andExpect(status().isConflict());
    labs.tick();assertEquals("RUNNING",getRun(id,"lab-owner").path("status").asText());assertTrue(getRun(id,"lab-owner").path("verified").asBoolean());
    var operation=body(post("/api/v1/lab-runs/"+id+"/requests"),"lab-owner",Map.of("operationId","echo","inputs",Map.of("message","Hello"))).header("Idempotency-Key","stable-operation");
    String first=mvc.perform(operation).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertEquals(first,mvc.perform(operation).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    var stream=mvc.perform(as(get("/api/v1/lab-runs/"+id+"/events"),"lab-owner")).andExpect(status().isOk()).andReturn().getResponse();assertTrue(stream.getContentType().startsWith("text/event-stream"));assertTrue(stream.getContentAsString().contains("REQUEST_COMPLETED"));assertFalse(stream.getContentAsString().contains(LabProtocolFixture.SECRET));
  }
  @Test void quotaRacesAllowOnlyOneActiveAccountAndDoNotTouchPublishingPools()throws Exception{
    var pool=Executors.newFixedThreadPool(2);try{var a=pool.submit(()->mvc.perform(start("lab-owner","parallel-one")).andReturn().getResponse().getStatus());var b=pool.submit(()->mvc.perform(start("lab-owner","parallel-two")).andReturn().getResponse().getStatus());var statuses=java.util.List.of(a.get(),b.get()).stream().sorted().toList();assertEquals(java.util.List.of(202,429),statuses);assertEquals(1,db.queryForObject("SELECT count(*) FROM lab_runs",Integer.class));assertEquals(0,db.queryForObject("SELECT count(*) FROM component_slot_purchases",Integer.class));}finally{pool.shutdownNow();}
  }
  @Test void cancellationClosesRequestsBeforeCleanupAndRetainsUnconfirmedCapacity()throws Exception{
    UUID id=reserve("lab-owner","cancel-reservation");labs.tick();fixture.failStop=true;
    mvc.perform(body(post("/api/v1/lab-runs/"+id+"/stop"),"lab-owner",Map.of())).andExpect(status().isOk());labs.tick();assertEquals("CANCELLING",getRun(id,"lab-owner").path("status").asText());assertNull(db.queryForObject("SELECT cleanup_confirmed_at FROM lab_runs WHERE id=?",java.sql.Timestamp.class,id));
    mvc.perform(body(post("/api/v1/lab-runs/"+id+"/requests"),"lab-owner",Map.of("operationId","echo","inputs",Map.of("message","Hello"))).header("Idempotency-Key","cancelled-operation")).andExpect(status().isConflict());
    mvc.perform(start("lab-owner","new-before-cleanup")).andExpect(status().isTooManyRequests());fixture.failStop=false;db.update("UPDATE lab_outbox SET next_attempt_at=now() WHERE run_id=?",id);labs.tick();assertEquals("CANCELLED",getRun(id,"lab-owner").path("status").asText());assertNotNull(db.queryForObject("SELECT cleanup_confirmed_at FROM lab_runs WHERE id=?",java.sql.Timestamp.class,id));assertTrue(db.queryForObject("SELECT quota_counted FROM lab_runs WHERE id=?",Boolean.class,id));
  }
  @Test void startupFailureRefundsOnlyAfterCleanupAndRetriesStableCommand()throws Exception{
    fixture.failStart=true;UUID id=reserve("lab-owner","startup-failure");labs.tick();UUID command=db.queryForObject("SELECT command_id FROM lab_outbox WHERE run_id=? AND action='START'",UUID.class,id);assertEquals("STARTING",getRun(id,"lab-owner").path("status").asText());
    db.update("UPDATE lab_outbox SET next_attempt_at=now() WHERE run_id=?",id);labs.tick();assertEquals(command,db.queryForObject("SELECT command_id FROM lab_outbox WHERE run_id=? AND action='START'",UUID.class,id));
    mvc.perform(body(post("/api/v1/lab-runs/"+id+"/stop"),"lab-owner",Map.of())).andExpect(status().isOk());labs.tick();assertFalse(db.queryForObject("SELECT quota_counted FROM lab_runs WHERE id=?",Boolean.class,id));
  }
  @Test void currentSourceAndAccountRevocationCloseRunAndNeverLeakOtherRuns()throws Exception{
    UUID id=reserve("lab-owner","revoked-source");labs.tick();db.update("UPDATE component_entries SET status='SUSPENDED' WHERE id=?",component);assertEquals("CANCELLING",getRun(id,"lab-owner").path("status").asText());assertEquals("RUNNING",db.queryForObject("SELECT status FROM lab_runs WHERE id=?",String.class,id));
    mvc.perform(body(post("/api/v1/lab-runs/"+id+"/requests"),"lab-owner",Map.of("operationId","echo","inputs",Map.of("message","Hello"))).header("Idempotency-Key","revoked-operation")).andExpect(status().isForbidden());labs.tick();assertEquals("FAILED",getRun(id,"lab-owner").path("status").asText());
    db.update("UPDATE users SET account_status='SUSPENDED' WHERE id=?",owner);mvc.perform(as(get("/api/v1/lab-runs/"+id),"lab-owner")).andExpect(status().isUnauthorized());
  }
  @Test void operatorEpochRestoreFencesAndAdminPauseNeedCurrentMfa()throws Exception{
    UUID id=reserve("lab-owner","restore-reservation");labs.tick();db.update("UPDATE lab_runtime_settings SET observed_epoch=? WHERE id=1",UUID.randomUUID());
    mvc.perform(start("lab-other","restore-denied")).andExpect(status().isServiceUnavailable());assertTrue(db.queryForObject("SELECT paused FROM lab_runtime_settings WHERE id=1",Boolean.class));
    mvc.perform(body(post("/api/v1/admin/labs/pause-admissions"),"lab-owner",Map.of("paused",true,"reason","Pause by a nonadministrator actor."))).andExpect(status().isForbidden());
    db.update("UPDATE sessions SET issued_at=now()-interval '16 minutes' WHERE user_id=?",admin);mvc.perform(body(post("/api/v1/admin/labs/pause-admissions"),"lab-admin",Map.of("paused",true,"reason","Current admission operator review."))).andExpect(status().isForbidden());
    db.update("UPDATE sessions SET issued_at=now() WHERE user_id=?",admin);mvc.perform(body(post("/api/v1/admin/labs/pause-admissions"),"lab-admin",Map.of("paused",true,"reason","Current admission operator review."))).andExpect(status().isOk());assertEquals(1,db.queryForObject("SELECT count(*) FROM lab_operator_audit",Integer.class));
    assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE lab_runs SET expires_at=now()+interval '1 hour' WHERE id=?",id));assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE lab_manifests SET payload_text='{}'"));
  }
  @Test void lostOperationResponsePreservesCommittedCommandIdentity()throws Exception{
    UUID id=reserve("lab-owner","lost-response-run");labs.tick();fixture.failRequest=true;
    var operation=body(post("/api/v1/lab-runs/"+id+"/requests"),"lab-owner",Map.of("operationId","echo","inputs",Map.of("message","Hello"))).header("Idempotency-Key","lost-operation");mvc.perform(operation).andExpect(status().isBadGateway());UUID command=db.queryForObject("SELECT command_id FROM lab_requests WHERE run_id=?",UUID.class,id);fixture.failRequest=false;mvc.perform(operation).andExpect(status().isOk());assertEquals(command,db.queryForObject("SELECT command_id FROM lab_requests WHERE run_id=?",UUID.class,id));
  }
  @Test void fiveHealthyRunsConsumeDailyQuotaButNeverPublishingSlots()throws Exception{
    for(int i=0;i<5;i++){UUID id=reserve("lab-owner","daily-run-"+i);labs.tick();mvc.perform(body(post("/api/v1/lab-runs/"+id+"/stop"),"lab-owner",Map.of())).andExpect(status().isOk());labs.tick();}
    mvc.perform(start("lab-owner","daily-sixth-run")).andExpect(status().isTooManyRequests());assertEquals(5,db.queryForObject("SELECT count(*) FROM lab_runs WHERE owner_id=? AND quota_counted",Integer.class,owner));
    mvc.perform(as(get("/api/v1/lab-quota"),"lab-owner")).andExpect(status().isOk()).andExpect(jsonPath("$.remaining").value(0));
    assertEquals(0,db.queryForObject("SELECT count(*) FROM component_slot_purchases",Integer.class));
  }
  @Test void concurrentAccountsReserveAtMostTenGlobalSlots()throws Exception{
    for(int i=0;i<12;i++)user("lab-race-"+i,false);var pool=Executors.newFixedThreadPool(12);try{var tasks=new java.util.ArrayList<java.util.concurrent.Future<Integer>>();for(int i=0;i<12;i++){int actor=i;tasks.add(pool.submit(()->mvc.perform(start("lab-race-"+actor,"global-race-"+actor)).andReturn().getResponse().getStatus()));}
      int admitted=0,rejected=0;for(var task:tasks){int status=task.get();if(status==202)admitted++;else if(status==429)rejected++;else fail("Unexpected global admission response "+status);}assertEquals(10,admitted);assertEquals(2,rejected);assertEquals(10,db.queryForObject("SELECT count(*) FROM lab_runs WHERE cleanup_confirmed_at IS NULL",Integer.class));
      assertEquals(640,db.queryForObject("SELECT sum(memory_mib) FROM lab_runs WHERE cleanup_confirmed_at IS NULL",Integer.class));
    }finally{pool.shutdownNow();}
  }
  @Test void idleExpiryCancelsAndCleanupRedactionPreservesIdentity()throws Exception{
    UUID id=reserve("lab-owner","idle-expiry-run");labs.tick();db.update("UPDATE lab_runs SET last_activity_at=now()-interval '91 seconds' WHERE id=?",id);labs.tick();assertEquals("EXPIRED",getRun(id,"lab-owner").path("status").asText());
    String identity=db.queryForObject("SELECT request_hash FROM lab_runs WHERE id=?",String.class,id);labs.redactOwner(owner);assertEquals("{}",db.queryForObject("SELECT inputs::text FROM lab_runs WHERE id=?",String.class,id));assertEquals(identity,db.queryForObject("SELECT request_hash FROM lab_runs WHERE id=?",String.class,id));
    assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE lab_runs SET status='RUNNING' WHERE id=?",id));
  }
  @Test void replayCountsUtf8BytesAndRetainsOwnerAuthorization()throws Exception{
    UUID id=reserve("lab-owner","unicode-replay-run");for(int i=2;i<=100;i++)db.update("INSERT INTO lab_events(run_id,sequence,event_type,status,reason) VALUES(?,?,'TEST','QUEUED',?)",id,i,"😀".repeat(240));
    String data=mvc.perform(as(get("/api/v1/lab-runs/"+id+"/events"),"lab-owner")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);assertTrue(data.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=60000);assertTrue(data.contains("data: "));assertFalse(data.contains("operatorEpoch"));
    mvc.perform(as(get("/api/v1/lab-runs/"+id+"/events"),"lab-owner").header("Last-Event-ID","-1")).andExpect(status().isBadRequest());mvc.perform(as(get("/api/v1/lab-runs/"+id+"/events"),"lab-other")).andExpect(status().isNotFound());
  }

  @Test void finiteOwnerReplayUsesJsonFramesWithoutFragmentRendering()throws Exception{
    var method=com.getlancer.labs.LabController.class.getMethod("events",UUID.class,String.class,jakarta.servlet.http.HttpServletRequest.class);
    var returnType=(java.lang.reflect.ParameterizedType)method.getGenericReturnType();
    assertEquals(org.springframework.http.ResponseEntity.class,returnType.getRawType());assertEquals(String.class,returnType.getActualTypeArguments()[0]);
    UUID id=reserve("lab-owner","finite-json-replay");String reason="Text with\n\nid: 999\nevent: forged\ndata: <script>fragment</script> 😀";
    db.update("INSERT INTO lab_events(run_id,sequence,event_type,status,reason) VALUES(?,2,'TEST','QUEUED',?)",id,reason);
    var reply=mvc.perform(as(get("/api/v1/lab-runs/"+id+"/events"),"lab-owner")).andExpect(status().isOk()).andReturn().getResponse();
    assertTrue(reply.getContentType().startsWith("text/event-stream"));assertTrue(java.util.Arrays.stream(reply.getHeader("Cache-Control").split(",")).anyMatch(value->value.trim().equalsIgnoreCase("no-store")));assertTrue(reply.getContentAsByteArray().length<=60000);
    String body=reply.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);assertFalse(body.contains("\nid: 999\n"));
    var frames=body.lines().filter(line->line.startsWith("data: ")).map(line->{try{return json.readTree(line.substring(6));}catch(Exception invalid){throw new AssertionError(invalid);}}).toList();
    assertEquals(2,frames.size());assertEquals(id.toString(),frames.get(1).path("runId").asText());assertEquals(reason,frames.get(1).path("data").path("reason").asText());
    mvc.perform(as(get("/api/v1/lab-runs/"+id+"/events"),"lab-other")).andExpect(status().isNotFound());
  }

  @Test void currentReviewedReleaseReplacesFirstPublicationAndStableRetryClosesRevokedSource()throws Exception{
    UUID id=reserve("lab-owner","latest-source-retry");labs.tick();
    var source=Map.of("kind","BACKEND","sha256","f".repeat(64),"archiveSha256","9".repeat(64),"manifestSha256","8".repeat(64),"version","2.0.0","files",Map.of("README.md","Version two test-only backend protocol fixture."));
    db.update("INSERT INTO component_releases(component_id,revision,source,context,source_sha256) VALUES(?,2,?::jsonb,'{}'::jsonb,?)",component,json.writeValueAsString(source),"f".repeat(64));
    var stale=json.readTree(mvc.perform(start("lab-owner","latest-source-retry")).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());assertEquals(id.toString(),stale.path("id").asText());assertEquals("CANCELLING",stale.path("status").asText());assertFalse(stale.path("verified").asBoolean());
    var manifest=fixture.manifest(component);var latest=new com.getlancer.labs.LabManifest("latest-fixture",component,2,"f".repeat(64),manifest.imageDigest(),"9".repeat(64),"8".repeat(64),"9".repeat(64),manifest.protocolVersion(),manifest.title(),manifest.summary(),manifest.language(),manifest.framework(),manifest.setup(),manifest.memoryMiB(),manifest.maxCostMicros(),manifest.approvedUntil(),manifest.evidenceSha256(),manifest.scenarios());
    String payload=json.writeValueAsString(latest);db.update("INSERT INTO lab_manifests(id,payload_text,payload_sha256,signature) VALUES(?,?,?,?)",latest.id(),payload,Support.hash(payload),fixture.sign(payload));
    var catalogue=json.readTree(mvc.perform(get("/api/v1/lab-manifests")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());assertEquals(1,catalogue.path("items").size());assertEquals("latest-fixture",catalogue.path("items").get(0).path("id").asText());
    mvc.perform(body(post("/api/v1/lab-runs"),"lab-other",Map.of("manifestId","latest-fixture","scenarioId","request","inputs",Map.of("message","Version two"))).header("Idempotency-Key","latest-version-run")).andExpect(status().isAccepted());
  }

  @Test void lostHealthyStartResponseStillConsumesQuotaAfterCertifiedCleanup()throws Exception{
    fixture.slowBody=true;UUID id=reserve("lab-owner","lost-healthy-start");labs.tick();assertNull(db.queryForObject("SELECT healthy_at FROM lab_runs WHERE id=?",java.sql.Timestamp.class,id));
    fixture.slowBody=false;mvc.perform(body(post("/api/v1/lab-runs/"+id+"/stop"),"lab-owner",Map.of())).andExpect(status().isOk());
    // The controlled provider may finish writing its abandoned body before accepting cleanup.
    for(int i=0;i<6;i++){db.update("UPDATE lab_outbox SET next_attempt_at=now() WHERE run_id=?",id);labs.tick();if(db.queryForObject("SELECT cleanup_confirmed_at IS NOT NULL FROM lab_runs WHERE id=?",Boolean.class,id))break;}
    assertNotNull(db.queryForObject("SELECT cleanup_confirmed_at FROM lab_runs WHERE id=?",java.sql.Timestamp.class,id));assertTrue(db.queryForObject("SELECT quota_counted FROM lab_runs WHERE id=?",Boolean.class,id));
    mvc.perform(as(get("/api/v1/lab-quota"),"lab-owner")).andExpect(status().isOk()).andExpect(jsonPath("$.remaining").value(4));
    mvc.perform(as(get("/api/v1/admin/labs"),"lab-admin")).andExpect(status().isOk()).andExpect(jsonPath("$.dailyReservedMicros").value(1000));
  }

  @Test void explicitIsolationLossClosesRoutingInCommittedFenceAndRetainsCapacity()throws Exception{
    UUID id=reserve("lab-owner","unsafe-isolation-run");labs.tick();fixture.notIsolated=true;
    mvc.perform(body(post("/api/v1/lab-runs/"+id+"/requests"),"lab-owner",Map.of("operationId","echo","inputs",Map.of("message","Hello"))).header("Idempotency-Key","unsafe-operation")).andExpect(status().isBadGateway()).andExpect(jsonPath("$.error.code").value("LAB_ISOLATION_LOST"));
    assertEquals("CANCELLING",db.queryForObject("SELECT status FROM lab_runs WHERE id=?",String.class,id));assertEquals(2,db.queryForObject("SELECT lease_generation FROM lab_runs WHERE id=?",Integer.class,id));assertEquals(1,db.queryForObject("SELECT count(*) FROM lab_outbox WHERE run_id=? AND action='STOP' AND delivered_at IS NULL",Integer.class,id));assertNull(db.queryForObject("SELECT response::text FROM lab_requests WHERE run_id=?",String.class,id));
    mvc.perform(start("lab-owner","unsafe-next-run")).andExpect(status().isTooManyRequests());
  }

}
