package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.labs.LabProtocolFixture;
import com.getlancer.labs.LabService;
import com.getlancer.labs.LabDataAiPolicy;
import com.getlancer.labs.LabIotPolicy;
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
  @Autowired JdbcTemplate db;@Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired LabService labs;@Autowired LabDataAiPolicy dataAi;@Autowired LabIotPolicy iot;
  UUID sourceOwner,owner,other,admin,component;
  long quotaBefore,validationBefore;
  UUID user(String token,boolean builder){UUID id=UUID.randomUUID();db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",id,token+"@example.test");db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT')",id);if(builder){db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER')",id);db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,?,'APPROVED')",id,token,token);}db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",Support.hash(token),id);return id;}
  @BeforeEach void seed()throws Exception{
    org.springframework.test.util.ReflectionTestUtils.setField(dataAi,"enabled",false);
    org.springframework.test.util.ReflectionTestUtils.setField(iot,"enabled",false);
    fixture.reset();db.execute("TRUNCATE lab_manifests CASCADE");db.execute("TRUNCATE users CASCADE");db.execute("TRUNCATE rate_buckets,analytics_daily_counts,analytics_events");quotaBefore=db.queryForObject("SELECT quota_denials FROM analytics_instrumentation WHERE name='v52_operations'",Long.class);validationBefore=db.queryForObject("SELECT validation_denials FROM analytics_instrumentation WHERE name='v52_operations'",Long.class);db.update("UPDATE lab_runtime_settings SET observed_epoch=NULL,paused=false,pause_reason='',daily_run_limit=5,max_active_runs=10,max_memory_mib=5120,daily_budget_micros=50000000,monthly_budget_micros=1000000000 WHERE id=1");
    sourceOwner=user("lab-source",true);owner=user("lab-owner",false);other=user("lab-other",false);admin=user("lab-admin",false);
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')",admin);db.update("UPDATE sessions SET mfa_verified=true,issued_at=now() WHERE user_id=?",admin);
    component=UUID.randomUUID();var source=Map.of("kind","BACKEND","sha256","a".repeat(64),"archiveSha256","c".repeat(64),"manifestSha256","d".repeat(64),"version","1.0.0","files",Map.of("README.md","Protocol fixture source; not executable runtime evidence."));
    db.update("INSERT INTO component_entries(id,owner_id,recipe_slug,slug,title,summary,contribution,status,published_at,published_source,published_context) VALUES(?,?,'protocol-fixture',?,'Protocol fixture','Only a controlled HTTP fixture','Test fixture source admission','ACTIVE',now(),?::jsonb,'{}'::jsonb)",component,sourceOwner,"protocol-"+component,json.writeValueAsString(source));
    db.update("INSERT INTO component_releases(component_id,revision,source,context,source_sha256) VALUES(?,1,?::jsonb,'{}'::jsonb,?)",component,json.writeValueAsString(source),"a".repeat(64));
    String payload=json.writeValueAsString(fixture.manifest(component));db.update("INSERT INTO lab_manifests(id,payload_text,payload_sha256,signature) VALUES('protocol-fixture',?,?,?)",payload,Support.hash(payload),fixture.sign(payload));
  }
  @org.junit.jupiter.api.AfterEach void resetLimits(){db.update("UPDATE lab_runtime_settings SET daily_run_limit=5,max_active_runs=10,max_memory_mib=5120,daily_budget_micros=50000000,monthly_budget_micros=1000000000 WHERE id=1");}
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
  void certifyIot()throws Exception{String payload=json.writeValueAsString(fixture.iotManifest(component));db.update("INSERT INTO lab_manifests(id,payload_text,payload_sha256,signature) VALUES('iot-greenhouse',?,?,?)",payload,Support.hash(payload),fixture.sign(payload));}
  MockHttpServletRequestBuilder startIot(String token,String key)throws Exception{return body(post("/api/v1/lab-runs"),token,Map.of("manifestId","iot-greenhouse","scenarioId","greenhouse-monitor","inputs",Map.of("temperatureC","24","humidityPercent","50","thresholdC","30"))).header("Idempotency-Key",key);}
  UUID reserveIot(String token,String key)throws Exception{return UUID.fromString(json.readTree(mvc.perform(startIot(token,key)).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()).path("id").asText());}
  @Test void iotDefaultDenialRetainsSignedSourceWithoutRuntimeOrReservation()throws Exception{
    certifyIot();mvc.perform(startIot("lab-owner","iot-disabled-default")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("IOT_DISABLED"));
    assertEquals(0,db.queryForObject("SELECT count(*) FROM lab_runs",Integer.class));assertEquals(0,db.queryForObject("SELECT count(*) FROM lab_outbox",Integer.class));
    var catalogue=json.readTree(mvc.perform(get("/api/v1/lab-manifests")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    var source=java.util.stream.StreamSupport.stream(catalogue.path("items").spliterator(),false).filter(item->item.path("id").asText().equals("iot-greenhouse")).findFirst().orElseThrow();
    assertEquals("Source only",source.path("executionMode").asText());assertFalse(source.path("availability").path("enabled").asBoolean());assertTrue(source.path("availability").path("reason").asText().contains("disabled"));assertEquals("node",source.path("adapter").path("runtime").asText());
    db.update("UPDATE developer_profiles SET approval_status='SUSPENDED' WHERE user_id=?",sourceOwner);
    mvc.perform(get("/api/v1/lab-manifests")).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
  }
  @Test void iotRevocationClosesRequestAndReplayBeforeConfirmedCleanup()throws Exception{
    certifyIot();org.springframework.test.util.ReflectionTestUtils.setField(iot,"enabled",true);UUID id=reserveIot("lab-owner","iot-healthy-run");labs.tick();assertTrue(getRun(id,"lab-owner").path("verified").asBoolean());
    org.springframework.test.util.ReflectionTestUtils.setField(iot,"enabled",false);
    mvc.perform(body(post("/api/v1/lab-runs/"+id+"/requests"),"lab-owner",Map.of("operationId","publish","inputs",Map.of("temperatureC","50","humidityPercent","50","thresholdC","30"))).header("Idempotency-Key","iot-revoked-request")).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("IOT_DISABLED"));
    mvc.perform(as(get("/api/v1/lab-runs/"+id+"/events"),"lab-owner")).andExpect(status().isForbidden());assertFalse(getRun(id,"lab-owner").path("verified").asBoolean());assertEquals(0,db.queryForObject("SELECT count(*) FROM lab_requests",Integer.class));
    fixture.failStop=true;labs.tick();assertEquals("CANCELLING",getRun(id,"lab-owner").path("status").asText());assertNull(db.queryForObject("SELECT cleanup_confirmed_at FROM lab_runs WHERE id=?",java.sql.Timestamp.class,id));
    fixture.failStop=false;db.update("UPDATE lab_outbox SET next_attempt_at=now() WHERE run_id=?",id);labs.tick();assertEquals("FAILED",getRun(id,"lab-owner").path("status").asText());assertTrue(db.queryForObject("SELECT quota_counted FROM lab_runs WHERE id=?",Boolean.class,id));
  }
  @Test void iotQueuedRevocationNeverStartsAndNeverRefundsUnconfirmedCleanup()throws Exception{
    certifyIot();org.springframework.test.util.ReflectionTestUtils.setField(iot,"enabled",true);UUID id=reserveIot("lab-owner","iot-queued-run");org.springframework.test.util.ReflectionTestUtils.setField(iot,"enabled",false);fixture.failStop=true;
    labs.tick();assertEquals("CANCELLING",getRun(id,"lab-owner").path("status").asText());assertTrue(fixture.commands.values().stream().noneMatch(command->command.runId().equals(id)&&command.action().equals("START")));assertTrue(db.queryForObject("SELECT quota_counted FROM lab_runs WHERE id=?",Boolean.class,id));
    fixture.failStop=false;db.update("UPDATE lab_outbox SET next_attempt_at=now() WHERE run_id=?",id);labs.tick();assertEquals("FAILED",getRun(id,"lab-owner").path("status").asText());assertFalse(db.queryForObject("SELECT quota_counted FROM lab_runs WHERE id=?",Boolean.class,id));
  }
  @Test void iotRequestsEnforceCurrentRunTopicAndBoundsBeforeCommandPersistence()throws Exception{
    certifyIot();org.springframework.test.util.ReflectionTestUtils.setField(iot,"enabled",true);UUID id=reserveIot("lab-owner","iot-topic-bound-run");labs.tick();
    assertEquals("getlancer/"+id+"/telemetry",db.queryForObject("SELECT inputs->>'topic' FROM lab_runs WHERE id=?",String.class,id));
    for(var values:java.util.List.of(Map.of("temperatureC","81","humidityPercent","50","thresholdC","30"),Map.of("temperatureC","20.5","humidityPercent","50","thresholdC","30"),
        Map.of("temperatureC","20","humidityPercent","101","thresholdC","30"),Map.of("temperatureC","20","humidityPercent","50","thresholdC","61"),
        Map.of("temperatureC","20","humidityPercent","50","thresholdC","30","topic","getlancer/"+UUID.randomUUID()+"/telemetry"),Map.of("temperatureC","20","humidityPercent","50","thresholdC","30","broker","https://foreign.example"))){
      mvc.perform(body(post("/api/v1/lab-runs/"+id+"/requests"),"lab-owner",Map.of("operationId","publish","inputs",values)).header("Idempotency-Key","iot-invalid-operation")).andExpect(status().isBadRequest());
    }
    assertEquals(0,db.queryForObject("SELECT count(*) FROM lab_requests",Integer.class));assertTrue(fixture.commands.values().stream().noneMatch(command->command.action().equals("REQUEST")));
    var operation=body(post("/api/v1/lab-runs/"+id+"/requests"),"lab-owner",Map.of("operationId","publish","inputs",Map.of("temperatureC","-20","humidityPercent","100","thresholdC","60","topic","getlancer/"+id+"/telemetry"))).header("Idempotency-Key","iot-valid-operation");
    mvc.perform(operation).andExpect(status().isOk());assertEquals("getlancer/"+id+"/telemetry",fixture.commands.values().stream().filter(command->command.action().equals("REQUEST")).findFirst().orElseThrow().inputs().get("topic"));
  }
  @Test void iotClassCapacityIsIndependentAndUncertainCleanupRetainsItsReservations()throws Exception{
    certifyIot();org.springframework.test.util.ReflectionTestUtils.setField(iot,"enabled",true);UUID first=null;
    for(int i=0;i<4;i++){String token="iot-class-"+i;user(token,false);UUID id=reserveIot(token,"iot-class-run-"+i);if(i==0)first=id;}
    user("iot-overflow",false);mvc.perform(startIot("iot-overflow","iot-class-overflow")).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error.code").value("IOT_CAPACITY"));
    certifyDataAi("signed-data","revenue-summary","DATA_ANALYTICS");org.springframework.test.util.ReflectionTestUtils.setField(dataAi,"enabled",true);reserveDataAi("lab-owner","independent-data-capacity","signed-data","revenue-summary");
    fixture.failStop=true;mvc.perform(body(post("/api/v1/lab-runs/"+first+"/stop"),"iot-class-0",Map.of())).andExpect(status().isOk());labs.tick();
    mvc.perform(startIot("iot-overflow","iot-before-confirmed-cleanup")).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error.code").value("IOT_CAPACITY"));
    fixture.failStop=false;db.update("UPDATE lab_outbox SET next_attempt_at=now() WHERE run_id=?",first);labs.tick();reserveIot("iot-overflow","iot-after-confirmed-cleanup");
  }
  @Test void unsupportedDisabledRetiredAndExpiredAdaptersRetainSourceAndDenyAdmission()throws Exception{
    for(String condition:java.util.List.of("disabled","retired","mismatch","scenario","expired","unsigned")){
      String id="adapter-"+condition;var manifest=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(fixture.manifest(component));manifest.put("id",id).put("language","Go").put("framework","Standard library");
      ((com.fasterxml.jackson.databind.node.ObjectNode)manifest.path("scenarios").get(0)).put("id",condition.equals("scenario")?"shell":"cache");
      manifest.set("adapter",json.valueToTree(Map.of("runtime",condition.equals("mismatch")?"php":"go","version",condition.equals("mismatch")?"8.3.6":"1.24.13","state",condition.equals("disabled")?"DISABLED":condition.equals("retired")?"RETIRED":"ACTIVE")));
      if(condition.equals("expired"))manifest.put("approvedUntil",java.time.Instant.now().minusSeconds(1).toString());
      String payload=manifest.toString();db.update("INSERT INTO lab_manifests(id,payload_text,payload_sha256,signature) VALUES(?,?,?,?)",id,payload,Support.hash(payload),condition.equals("unsigned")?fixture.sign(payload+" "):fixture.sign(payload));
      mvc.perform(startDataAi("lab-owner","deny-"+id,id,condition.equals("scenario")?"shell":"cache")).andExpect(status().isServiceUnavailable());
    }
    assertEquals(0,db.queryForObject("SELECT count(*) FROM lab_runs",Integer.class));
    var catalogue=json.readTree(mvc.perform(get("/api/v1/lab-manifests")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    assertEquals(6,catalogue.path("items").size());
    for(var item:catalogue.path("items"))if(item.path("id").asText().startsWith("adapter-")){assertEquals("Source only",item.path("executionMode").asText());assertFalse(item.path("availability").path("enabled").asBoolean());}
    mvc.perform(startDataAi("lab-owner","unknown-manifest-denial","unknown-manifest","cache")).andExpect(status().isNotFound());
  }
  @Test void supportedNativeAdapterUsesFullSignedPayloadInProviderCommandBinding()throws Exception{
    var manifest=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(fixture.manifest(component));manifest.put("id","signed-go-cache").put("language","Go").put("framework","Standard library");
    ((com.fasterxml.jackson.databind.node.ObjectNode)manifest.path("scenarios").get(0)).put("id","cache");manifest.set("adapter",json.valueToTree(Map.of("runtime","go","version","1.24.13","state","ACTIVE")));
    manifest.set("certification",json.valueToTree(fixture.certification("1.24.13")));String payload=manifest.toString(),hash=Support.hash(payload);db.update("INSERT INTO lab_manifests(id,payload_text,payload_sha256,signature) VALUES('signed-go-cache',?,?,?)",payload,hash,fixture.sign(payload));
    UUID id=reserveDataAi("lab-owner","signed-go-cache-run","signed-go-cache","cache");labs.tick();
    var command=fixture.commands.values().stream().filter(item->item.runId().equals(id)&&item.action().equals("START")).findFirst().orElseThrow();
    assertEquals(hash,command.manifestSha256());assertTrue(getRun(id,"lab-owner").path("verified").asBoolean());
    assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE lab_manifests SET payload_text=? WHERE id='signed-go-cache'",payload.replace("ACTIVE","RETIRED")));
    db.update("UPDATE lab_manifests SET revoked_at=now() WHERE id='signed-go-cache'");assertFalse(getRun(id,"lab-owner").path("verified").asBoolean());
    mvc.perform(body(post("/api/v1/lab-runs/"+id+"/requests"),"lab-owner",Map.of("operationId","echo","inputs",Map.of("message","Not forwarded"))).header("Idempotency-Key","revoked-go-operation")).andExpect(status().isNotFound());
    labs.tick();assertEquals("FAILED",getRun(id,"lab-owner").path("status").asText());
  }
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

  JsonNode operations()throws Exception {
    String body=mvc.perform(as(get("/api/v1/admin/operations"),"lab-admin")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertFalse(body.contains(LabProtocolFixture.SECRET));assertFalse(body.contains("lab-owner@example.test"));assertFalse(body.contains("Hello"));
    return json.readTree(body);
  }
  @Test void operationsMeasureAdmittedProvisioningAndPendingCoverageWithoutReplayOrDenialInflation()throws Exception {
    UUID healthy=reserve("lab-owner","operations-healthy");labs.tick();
    fixture.failStart=true;UUID pending=reserve("lab-other","operations-pending");labs.tick();
    mvc.perform(start("lab-other","operations-denied-active")).andExpect(status().isTooManyRequests());
    mvc.perform(body(post("/api/v1/lab-runs"),"lab-other",Map.of("manifestId","protocol-fixture","scenarioId","request","inputs",Map.of("message","Hello","secretField","private")))
        .header("Idempotency-Key","operations-invalid-input")).andExpect(status().isBadRequest());
    for(int i=0;i<3;i++)mvc.perform(as(get("/api/v1/lab-runs/"+healthy+"/events"),"lab-owner")).andExpect(status().isOk());
    var first=operations();assertEquals(2,first.at("/runtime/admitted").asInt());assertEquals(1,first.at("/runtime/healthy").asInt());assertEquals(1,first.at("/runtime/pending").asInt());
    assertEquals(100,first.at("/runtime/infraSuccessPercent").asDouble());assertEquals(50,first.at("/runtime/coveragePercent").asDouble());
    assertEquals(quotaBefore+1,first.at("/runtime/quotaDenials").asLong());assertEquals(validationBefore+1,first.at("/runtime/validationDenials").asLong());
    mvc.perform(body(post("/api/v1/lab-runs/"+pending+"/stop"),"lab-other",Map.of())).andExpect(status().isOk());labs.tick();
    var settled=operations();assertEquals(0,settled.at("/runtime/pending").asInt());assertEquals(1,settled.at("/runtime/infraFailures").asInt());assertEquals(50,settled.at("/runtime/infraSuccessPercent").asDouble());
    assertEquals(100,settled.at("/runtime/coveragePercent").asDouble());assertFalse(settled.at("/readiness/productionCertified").asBoolean());
  }
  @Test void confirmedScenarioFailureIsSeparateIdempotentAndNeverChangesInfrastructureSuccess()throws Exception {
    UUID run=reserve("lab-owner","scenario-failure-run");labs.tick();fixture.confirmedRequestFailure=true;
    var request=body(post("/api/v1/lab-runs/"+run+"/requests"),"lab-owner",Map.of("operationId","echo","inputs",Map.of("message","Hello"))).header("Idempotency-Key","confirmed-scenario-failure");
    for(int i=0;i<2;i++)mvc.perform(request).andExpect(status().isBadGateway()).andExpect(jsonPath("$.error.code").value("LAB_SCENARIO_FAILED"));
    var first=operations();assertEquals(1,first.at("/runtime/scenarioFailures").asInt());assertEquals(1,first.at("/runtime/healthy").asInt());assertEquals(0,first.at("/runtime/infraFailures").asInt());
    fixture.confirmedRequestFailure=false;mvc.perform(request).andExpect(status().isOk());
    assertEquals(1,operations().at("/runtime/scenarioFailures").asInt());
    assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE lab_requests SET confirmed_failure=false WHERE run_id=?",run));
  }
  @Test void reservationAtMonetaryCapBlocksAdmissionAndReportsBudgetPressureWithoutCreatingAuthority()throws Exception {
    for(int i=0;i<50;i++)db.update("INSERT INTO lab_runs(id,owner_id,manifest_id,scenario_id,request_hash,idempotency_key,inputs,manifest_sha256,image_digest,operator_epoch,status,expires_at,healthy_at,cleanup_confirmed_at,memory_mib,cost_micros) VALUES(?,?,'protocol-fixture','request',?,?, '{}'::jsonb,?,?,?,'SUCCEEDED',now()+interval '5 minutes',now(),now(),64,1000000)",UUID.randomUUID(),sourceOwner,"a".repeat(64),"budget-history-"+i,"b".repeat(64),"sha256:"+"c".repeat(64),fixture.epoch);
    mvc.perform(start("lab-owner","budget-at-ceiling")).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error.code").value("LAB_CAPACITY"));
    assertEquals(50,db.queryForObject("SELECT count(*) FROM lab_runs",Integer.class));assertEquals(0,db.queryForObject("SELECT count(*) FROM lab_outbox",Integer.class));
    var state=operations();assertEquals(50_000_000,state.at("/resources/dailyReservedMicros").asLong());
    assertTrue(java.util.stream.StreamSupport.stream(state.get("alerts").spliterator(),false).anyMatch(alert->alert.get("code").asText().equals("BUDGET_PRESSURE")));
  }
  @Test void operatorRestoreEpochChangesProjectionAndOldAuthorityStaysClosed()throws Exception {
    reserve("lab-owner","epoch-measurement-run");labs.tick();
    var before=operations();assertEquals(fixture.epoch.toString(),before.at("/runtime/operatorEpoch").asText());
    UUID restoredEpoch=UUID.randomUUID();db.update("UPDATE lab_runtime_settings SET observed_epoch=? WHERE id=1",restoredEpoch);
    var after=operations();assertEquals(restoredEpoch.toString(),after.at("/runtime/operatorEpoch").asText());assertFalse(after.at("/readiness/runtime/enabled").asBoolean());
    assertEquals(before.at("/runtime/counterEpoch").asText(),after.at("/runtime/counterEpoch").asText());
    assertEquals(before.at("/runtime/admitted").asInt(),after.at("/runtime/admitted").asInt());
  }

  @Test void explicitPauseBlocksFreshReservationAndOutboxButOwnerCanStillConfirmCleanup()throws Exception{
    UUID run=reserve("lab-owner","pause-cleanup-run");int outbox=db.queryForObject("SELECT count(*) FROM lab_outbox",Integer.class);
    mvc.perform(body(post("/api/v1/admin/labs/pause-admissions"),"lab-admin",Map.of("paused",true,"reason","Pause fresh admission while existing reservations are cleaned."))).andExpect(status().isOk());
    mvc.perform(start("lab-other","fresh-admission-after-pause")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("LAB_PAUSED"));assertEquals(1,db.queryForObject("SELECT count(*) FROM lab_runs",Integer.class));assertEquals(outbox,db.queryForObject("SELECT count(*) FROM lab_outbox",Integer.class));
    mvc.perform(body(post("/api/v1/lab-runs/"+run+"/stop"),"lab-owner",Map.of())).andExpect(status().isOk());labs.tick();assertNotNull(db.queryForObject("SELECT cleanup_confirmed_at FROM lab_runs WHERE id=?",java.sql.Timestamp.class,run));assertFalse(db.queryForObject("SELECT quota_counted FROM lab_runs WHERE id=?",Boolean.class,run));
  }
  @Test void currentMfaLimitsTightenRealAdmissionAndRevocationCannotMutateOrAudit()throws Exception{
    var limit=Map.of("dailyRunLimit",1,"maxActiveRuns",1,"maxMemoryMiB",64,"dailyBudgetMicros",1000,"monthlyBudgetMicros",1000,"reason","Tighten all ceilings for the controlled admission test.");
    mvc.perform(body(post("/api/v1/admin/labs/limits"),"lab-admin",limit)).andExpect(status().isOk()).andExpect(jsonPath("$.limits.maxActiveRuns").value(1));
    assertTrue(db.queryForObject("SELECT details->'before'->>'maxActiveRuns'='10' AND details->'after'->>'maxActiveRuns'='1' FROM lab_operator_audit WHERE action='LIMITS_UPDATED'",Boolean.class));
    reserve("lab-owner","within-operator-limits");mvc.perform(start("lab-other","above-operator-limits")).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error.code").value("LAB_CAPACITY"));
    db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?",admin);int audit=db.queryForObject("SELECT count(*) FROM lab_operator_audit",Integer.class);
    mvc.perform(body(post("/api/v1/admin/labs/limits"),"lab-admin",limit)).andExpect(status().isForbidden());mvc.perform(body(post("/api/v1/admin/labs/certifications"),"lab-admin",Map.of("action","REVOKE","manifestId","protocol-fixture","reason","A revoked MFA session cannot revoke certification."))).andExpect(status().isForbidden());
    assertEquals(audit,db.queryForObject("SELECT count(*) FROM lab_operator_audit",Integer.class));assertNull(db.queryForObject("SELECT revoked_at FROM lab_manifests WHERE id='protocol-fixture'",java.sql.Timestamp.class));assertEquals(1,db.queryForObject("SELECT max_active_runs FROM lab_runtime_settings WHERE id=1",Integer.class));
  }
  private com.fasterxml.jackson.databind.node.ObjectNode nativeCertificate(String id){var node=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(fixture.manifest(component));node.put("id",id).put("language","Go").put("framework","Standard library");((com.fasterxml.jackson.databind.node.ObjectNode)node.path("scenarios").get(0)).put("id","cache");node.set("adapter",json.valueToTree(Map.of("runtime","go","version","1.24.13","state","ACTIVE")));node.set("certification",json.valueToTree(fixture.certification("1.24.13")));return node;}
  @Test void unknownLanguageCanRegisterSignedSourceOnlyButNeverReserveOrPromoteInPlace()throws Exception{
    var node=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(fixture.manifest(component));node.put("id","ruby-source-only").put("language","Ruby").put("framework","Unknown framework");String payload=node.toString();var input=Map.of("action","REGISTER_SOURCE","manifestId","ruby-source-only","payload",payload,"signature",fixture.sign(payload),"reason","Register independently reviewed source without runtime certification.");
    mvc.perform(body(post("/api/v1/admin/labs/certifications"),"lab-admin",input)).andExpect(status().isOk());assertFalse(db.queryForObject("SELECT execution_approved FROM lab_manifests WHERE id='ruby-source-only'",Boolean.class));
    var items=json.readTree(mvc.perform(get("/api/v1/lab-manifests")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("items");var source=java.util.stream.StreamSupport.stream(items.spliterator(),false).filter(item->item.path("id").asText().equals("ruby-source-only")).findFirst().orElseThrow();assertEquals("Source only",source.path("executionMode").asText());assertFalse(source.path("availability").path("enabled").asBoolean());
    mvc.perform(startDataAi("lab-owner","unknown-source-only-run","ruby-source-only","request")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("LAB_MANIFEST_SOURCE_ONLY"));mvc.perform(body(post("/api/v1/admin/labs/certifications"),"lab-admin",input)).andExpect(status().isConflict());assertEquals(0,db.queryForObject("SELECT count(*) FROM lab_runs",Integer.class));assertEquals(0,db.queryForObject("SELECT count(*) FROM lab_outbox",Integer.class));assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE lab_manifests SET execution_approved=true WHERE id='ruby-source-only'"));
  }
  @Test void signedPublicationIsImmutableAndPermanentRevocationClosesCurrentRoutesBeforeConfirmedCleanup()throws Exception{
    String payload=nativeCertificate("certified-go-cache").toString();var publish=Map.of("action","PUBLISH","manifestId","certified-go-cache","payload",payload,"signature",fixture.sign(payload),"reason","Reviewed signed fixture attestations; no actual host qualification claim.");mvc.perform(body(post("/api/v1/admin/labs/certifications"),"lab-admin",publish)).andExpect(status().isOk());mvc.perform(body(post("/api/v1/admin/labs/certifications"),"lab-admin",publish)).andExpect(status().isConflict());
    UUID run=reserveDataAi("lab-owner","current-certified-cache","certified-go-cache","cache");labs.tick();assertTrue(getRun(run,"lab-owner").path("verified").asBoolean());mvc.perform(body(post("/api/v1/admin/labs/certifications"),"lab-admin",Map.of("action","REVOKE","manifestId","certified-go-cache","reason","Revoke this exact immutable certificate and its current execution authority."))).andExpect(status().isOk());assertEquals(payload,db.queryForObject("SELECT payload_text FROM lab_manifests WHERE id='certified-go-cache'",String.class));
    mvc.perform(as(get("/api/v1/lab-runs/"+run+"/events"),"lab-owner")).andExpect(status().isNotFound());mvc.perform(body(post("/api/v1/lab-runs/"+run+"/requests"),"lab-owner",Map.of("operationId","echo","inputs",Map.of("message","Never forwarded"))).header("Idempotency-Key","revoked-certificate-op")).andExpect(status().isNotFound());mvc.perform(startDataAi("lab-other","revoked-certificate-admit","certified-go-cache","cache")).andExpect(status().isNotFound());assertEquals(0,db.queryForObject("SELECT count(*) FROM lab_requests",Integer.class));
    fixture.failStop=true;labs.tick();assertEquals("CANCELLING",getRun(run,"lab-owner").path("status").asText());assertNull(db.queryForObject("SELECT cleanup_confirmed_at FROM lab_runs WHERE id=?",java.sql.Timestamp.class,run));fixture.failStop=false;db.update("UPDATE lab_outbox SET next_attempt_at=now() WHERE run_id=?",run);labs.tick();assertNotNull(db.queryForObject("SELECT cleanup_confirmed_at FROM lab_runs WHERE id=?",java.sql.Timestamp.class,run));assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE lab_manifests SET revoked_at=NULL WHERE id='certified-go-cache'"));
  }
  @Test void staleWorkerOrphanAndCleanupAttentionAlertsUseActualFaultRows()throws Exception{
    UUID id=reserve("lab-owner","orphan-alert-fixture");db.update("UPDATE lab_runtime_settings SET worker_observed_at=now()-interval '2 minutes' WHERE id=1");db.update("UPDATE lab_outbox SET delivered_at=now() WHERE run_id=?",id);db.update("UPDATE lab_runs SET attention=true WHERE id=?",id);var result=json.readTree(mvc.perform(as(get("/api/v1/admin/labs"),"lab-admin")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());var codes=new java.util.HashSet<String>();result.path("alerts").forEach(alert->codes.add(alert.path("code").asText()));assertEquals(java.util.Set.of("STALE_WORKER","ORPHAN_RESERVATION","CLEANUP_ATTENTION"),codes);
  }
  @Test void observedNativeEvidenceIsDurableAndForeignOrReplayedBatchesNeverAdvanceCursor()throws Exception{
    String payload=nativeCertificate("observed-cache").toString();db.update("INSERT INTO lab_manifests(id,payload_text,payload_sha256,signature) VALUES('observed-cache',?,?,?)",payload,Support.hash(payload),fixture.sign(payload));UUID id=reserveDataAi("lab-owner","observed-cache-run","observed-cache","cache");labs.tick();fixture.nativeObservations=true;
    mvc.perform(body(post("/api/v1/lab-runs/"+id+"/requests"),"lab-owner",Map.of("operationId","echo","inputs",Map.of("message","Observe"))).header("Idempotency-Key","observed-first-result")).andExpect(status().isOk()).andExpect(jsonPath("$.observation.sourceHash").value("a".repeat(64))).andExpect(jsonPath("$.observation.events[1].edge").value("service-store"));assertEquals(2,db.queryForObject("SELECT observation_sequence FROM lab_runs WHERE id=?",Integer.class,id));String replay=mvc.perform(as(get("/api/v1/lab-runs/"+id+"/events"),"lab-owner")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertTrue(replay.contains("Controlled store read."));
    fixture.foreignObservation=true;mvc.perform(body(post("/api/v1/lab-runs/"+id+"/requests"),"lab-owner",Map.of("operationId","echo","inputs",Map.of("message","Foreign"))).header("Idempotency-Key","observed-foreign-result")).andExpect(status().isBadGateway()).andExpect(jsonPath("$.error.code").value("LAB_EVIDENCE_UNCONFIRMED"));assertEquals(2,db.queryForObject("SELECT observation_sequence FROM lab_runs WHERE id=?",Integer.class,id));fixture.foreignObservation=false;fixture.replayedObservation=true;
    mvc.perform(body(post("/api/v1/lab-runs/"+id+"/requests"),"lab-owner",Map.of("operationId","echo","inputs",Map.of("message","Replay"))).header("Idempotency-Key","observed-replayed-result")).andExpect(status().isBadGateway());assertEquals(2,db.queryForObject("SELECT observation_sequence FROM lab_runs WHERE id=?",Integer.class,id));assertEquals(1,db.queryForObject("SELECT count(*) FROM lab_requests WHERE response IS NOT NULL",Integer.class));assertEquals(1,db.queryForObject("SELECT count(*) FROM lab_events WHERE command_id IS NOT NULL",Integer.class));
  }
  @Autowired javax.sql.DataSource labConnections;
  /** Real target-row waits after the initial MFA check, with natural clock expiry and unchanged credentials. */
  private void certificationAfterTargetWait(String action,boolean expires)throws Exception{
    String identity=action.equals("REVOKE")?"protocol-fixture":"queued-native-certificate";
    String payload=nativeCertificate(identity).toString();var input=action.equals("REVOKE")?Map.of("action",action,"manifestId",identity,"reason","Queued certificate review must use current MFA after target locking."):Map.of("action",action,"manifestId",identity,"payload",payload,"signature",fixture.sign(payload),"reason","Queued certificate review must use current MFA after source locking.");
    int auditBefore=db.queryForObject("SELECT count(*) FROM lab_operator_audit",Integer.class),manifestsBefore=db.queryForObject("SELECT count(*) FROM lab_manifests",Integer.class);
    var executor=Executors.newSingleThreadExecutor();
    try(var holder=labConnections.getConnection()){
      holder.setAutoCommit(false);int holderPid;try(var statement=holder.createStatement();var result=statement.executeQuery("SELECT pg_backend_pid()")){assertTrue(result.next());holderPid=result.getInt(1);}
      String table=action.equals("REVOKE")?"lab_manifests":"users";
      try(var lock=holder.prepareStatement("SELECT id FROM "+table+" WHERE id=? FOR UPDATE")){lock.setObject(1,action.equals("REVOKE")?identity:sourceOwner);try(var result=lock.executeQuery()){assertTrue(result.next());}}
      try{
        db.update("UPDATE sessions SET issued_at=clock_timestamp()-"+(expires?"interval '894 seconds'":"interval '60 seconds'")+" WHERE user_id=?",admin);
        var deadline=db.queryForObject("SELECT issued_at+interval '15 minutes' FROM sessions WHERE user_id=?",java.sql.Timestamp.class,admin);
        var builder=body(post("/api/v1/admin/labs/certifications"),"lab-admin",input);var response=executor.submit(()->mvc.perform(builder).andReturn());
        boolean blocked=false;long stop=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<stop){int waits=db.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND ?=ANY(pg_blocking_pids(pid)) AND xact_start<? AND query LIKE ?",Integer.class,holderPid,deadline,"%FROM "+table+"%FOR UPDATE%");if(waits>0){blocked=true;break;}java.util.concurrent.locks.LockSupport.parkNanos(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(20));}
        assertTrue(blocked,"The certification must pass initial MFA then wait on its real target/source row");assertTrue(db.queryForObject("SELECT clock_timestamp()<?",Boolean.class,deadline));
        if(expires){db.queryForObject("SELECT pg_sleep(GREATEST(0,extract(epoch FROM (?::timestamptz-clock_timestamp()))+0.2))",Object.class,deadline);assertTrue(db.queryForObject("SELECT clock_timestamp()>=?",Boolean.class,deadline));}
        holder.commit();var result=response.get(10,java.util.concurrent.TimeUnit.SECONDS);
        if(expires){assertEquals(403,result.getResponse().getStatus(),result.getResponse().getContentAsString());assertEquals(auditBefore,db.queryForObject("SELECT count(*) FROM lab_operator_audit",Integer.class));assertEquals(manifestsBefore,db.queryForObject("SELECT count(*) FROM lab_manifests",Integer.class));assertNull(db.queryForObject("SELECT revoked_at FROM lab_manifests WHERE id='protocol-fixture'",java.sql.Timestamp.class));}
        else{assertEquals(200,result.getResponse().getStatus(),result.getResponse().getContentAsString());assertEquals(auditBefore+1,db.queryForObject("SELECT count(*) FROM lab_operator_audit",Integer.class));if(action.equals("REVOKE"))assertNotNull(db.queryForObject("SELECT revoked_at FROM lab_manifests WHERE id='protocol-fixture'",java.sql.Timestamp.class));else assertEquals(manifestsBefore+1,db.queryForObject("SELECT count(*) FROM lab_manifests",Integer.class));}
      }finally{holder.rollback();}
    }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(15,java.util.concurrent.TimeUnit.SECONDS));}
  }
  @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"REVOKE","PUBLISH"})
  void expiredMfaAfterManifestOrSourceWaitCannotCommitCertificationOrAudit(String action)throws Exception{certificationAfterTargetWait(action,true);}
  @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"REVOKE","PUBLISH"})
  void currentMfaAfterManifestOrSourceWaitCanCommitCertificationAndAudit(String action)throws Exception{certificationAfterTargetWait(action,false);}
}
