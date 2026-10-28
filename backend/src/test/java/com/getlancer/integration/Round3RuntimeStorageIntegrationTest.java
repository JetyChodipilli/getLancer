package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.getlancer.dto.LabRequests;
import com.getlancer.labs.LabProtocolFixture;
import com.getlancer.labs.LabResponses;
import com.getlancer.labs.LabService;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Real PostgreSQL source-row waits and HTTP routes; the provider is controlled loopback I/O. */
@ContextConfiguration(initializers=TestDatabaseGuard.class)
@SpringBootTest(properties={"app.environment=local","app.jobs-enabled=false","app.admin-email=round3-lab@example.test","app.admin-password=","app.admin-totp=","spring.config.import=",
    "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}","spring.datasource.username=${TEST_DB_USERNAME:postgres}","spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.hikari.schema=getlancer_test","spring.flyway.default-schema=getlancer_test","spring.flyway.schemas=getlancer_test","app.origin=http://localhost:3000","app.secure-cookie=false","app.storage.access-key=","app.storage.secret-key="})
@AutoConfigureMockMvc
class Round3RuntimeStorageIntegrationTest {
  static final LabProtocolFixture provider=new LabProtocolFixture();
  @DynamicPropertySource static void labs(DynamicPropertyRegistry registry){
    registry.add("app.labs.enabled",()->true);registry.add("app.labs.gateway-origin",provider::origin);
    registry.add("app.labs.gateway-secret",()->LabProtocolFixture.SECRET);registry.add("app.labs.operator-public-key",provider::publicKey);
    registry.add("app.labs.admission-evidence",()->provider.evidence.toString());registry.add("app.labs.operator-epoch",()->provider.epoch.toString());registry.add("app.labs.timeout-ms",()->5000);
  }
  @AfterAll static void close(){provider.close();}
  @Autowired JdbcTemplate db;
  @Autowired DataSource connections;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired LabService labs;
  UUID sourceOwner,owner,component;

  @BeforeEach void seed()throws Exception{
    provider.reset();db.execute("TRUNCATE lab_manifests CASCADE");db.execute("TRUNCATE users CASCADE");db.execute("TRUNCATE rate_buckets");
    db.update("UPDATE lab_runtime_settings SET observed_epoch=?,paused=false,pause_reason='',daily_run_limit=5,max_active_runs=10,max_memory_mib=5120,daily_budget_micros=50000000,monthly_budget_micros=1000000000 WHERE id=1",provider.epoch);
    sourceOwner=user("round3-source",true);owner=user("round3-runner",false);component=UUID.randomUUID();
    var source=Map.of("kind","BACKEND","sha256","a".repeat(64),"archiveSha256","c".repeat(64),"manifestSha256","d".repeat(64),"version","1.0.0","files",Map.of("README.md","Controlled source-lock fixture; no runtime execution evidence."));
    db.update("INSERT INTO component_entries(id,owner_id,recipe_slug,slug,title,summary,contribution,status,published_at,published_source,published_context) VALUES(?,?,'protocol-fixture',?,'Source lock fixture','Controlled PostgreSQL source lock','Synthetic source authority fixture','ACTIVE',now(),?::jsonb,'{}'::jsonb)",component,sourceOwner,"round3-"+component,json.writeValueAsString(source));
    db.update("INSERT INTO component_releases(component_id,revision,source,context,source_sha256) VALUES(?,1,?::jsonb,'{}'::jsonb,?)",component,json.writeValueAsString(source),"a".repeat(64));
  }
  UUID user(String token,boolean builder){
    UUID id=UUID.randomUUID();db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",id,token+"@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT')",id);
    if(builder){db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER')",id);db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,?,'APPROVED')",id,token,token);}
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,clock_timestamp()+interval '1 hour')",Support.hash(token),id);return id;
  }
  void certificate(Instant expiry)throws Exception{
    ObjectNode value=json.valueToTree(provider.manifest(component));value.put("approvedUntil",expiry.toString());String payload=value.toString();
    db.update("INSERT INTO lab_manifests(id,payload_text,payload_sha256,signature) VALUES('protocol-fixture',?,?,?)",payload,Support.hash(payload),provider.sign(payload));
  }
  MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request){return request.cookie(new Cookie("gl_session","round3-runner"));}
  MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request,Object value)throws Exception{return authenticated(request).header("Origin","http://localhost:3000").header("X-Requested-With","getlancer").contentType("application/json").content(json.writeValueAsString(value));}
  LabRequests.Request operation(){return new LabRequests.Request("echo",LabRequests.Inputs.from(Map.of("message","Hello")));}
  String requestHash(Object input)throws Exception{return Support.hash(json.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(input));}
  UUID cachedRun(Instant expiry)throws Exception{return cachedRun(expiry,"f".repeat(64));}
  UUID cachedRun(Instant expiry,String admissionHash)throws Exception{
    UUID run=UUID.randomUUID(),command=UUID.randomUUID();String manifestHash=db.queryForObject("SELECT payload_sha256 FROM lab_manifests WHERE id='protocol-fixture'",String.class);
    db.update("INSERT INTO lab_runs(id,owner_id,manifest_id,scenario_id,request_hash,idempotency_key,inputs,manifest_sha256,image_digest,operator_epoch,status,expires_at,healthy_at,memory_mib,cost_micros) VALUES(?,?,'protocol-fixture','request',?,'round3-cached-run','{}'::jsonb,?,?,?,'RUNNING',?,now(),64,1000)",run,owner,admissionHash,manifestHash,"sha256:"+"b".repeat(64),provider.epoch,Timestamp.from(expiry));
    String hash=requestHash(operation());
    db.update("INSERT INTO lab_requests(command_id,run_id,idempotency_key,request_hash,lease_generation,operation_id,inputs,response) VALUES(?,?,'round3-cached-request',?,1,'echo',?::jsonb,?::jsonb)",command,run,hash,json.writeValueAsString(operation().inputs().values()),json.writeValueAsString(new LabResponses.RequestResult(run,command,"Previously confirmed private output","Controlled fixture result")));
    db.update("INSERT INTO lab_events(run_id,sequence,event_type,status,reason,command_id) VALUES(?,1,'REQUEST_COMPLETED','RUNNING','Previously confirmed private activity',?)",run,command);return run;
  }
  MvcResult afterSourceWait(MockHttpServletRequestBuilder request,Instant deadline,boolean expires)throws Exception{return afterSourceWait(request,deadline,expires,false);}
  MvcResult afterSourceWait(MockHttpServletRequestBuilder request,Instant deadline,boolean expires,boolean afterProvider)throws Exception{
    var executor=Executors.newSingleThreadExecutor();
    try(var holder=connections.getConnection()){
      holder.setAutoCommit(false);int pid;
      try(var statement=holder.createStatement();var rows=statement.executeQuery("SELECT pg_backend_pid()")){assertTrue(rows.next());pid=rows.getInt(1);}
      java.util.concurrent.Future<MvcResult> response=null;
      if(afterProvider){
        response=executor.submit(()->mvc.perform(request).andReturn());long stop=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(System.nanoTime()<stop&&provider.commands.values().stream().noneMatch(command->command.action().equals("REQUEST")))LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        assertTrue(provider.commands.values().stream().anyMatch(command->command.action().equals("REQUEST")),"The initial transaction must commit and start actual provider HTTP before the result-phase source lock");
      }
      try(var statement=holder.prepareStatement("SELECT id FROM users WHERE id=? FOR UPDATE")){statement.setObject(1,sourceOwner);try(var rows=statement.executeQuery()){assertTrue(rows.next());}}
      try{
        if(response==null)response=executor.submit(()->mvc.perform(request).andReturn());boolean blocked=false;long stop=System.nanoTime()+TimeUnit.SECONDS.toNanos(afterProvider?4:3);
        while(System.nanoTime()<stop){int waits=db.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND ?=ANY(pg_blocking_pids(pid)) AND xact_start<? AND query LIKE '%FROM users WHERE id=%FOR UPDATE%'",Integer.class,pid,Timestamp.from(deadline));if(waits>0){blocked=true;break;}LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));}
        assertTrue(blocked,"The real request must pass initial authority and block on the source owner row");
        assertTrue(db.queryForObject("SELECT clock_timestamp()<?",Boolean.class,Timestamp.from(deadline)),"The blocked request was initially unexpired");
        if(expires){db.queryForObject("SELECT pg_sleep(GREATEST(0,extract(epoch FROM (?::timestamptz-clock_timestamp()))+0.1))",Object.class,Timestamp.from(deadline));assertTrue(db.queryForObject("SELECT clock_timestamp()>=?",Boolean.class,Timestamp.from(deadline)));}
        holder.commit();return response.get(10,TimeUnit.SECONDS);
      }finally{holder.rollback();}
    }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(15,TimeUnit.SECONDS));}
  }

  @ParameterizedTest @ValueSource(strings={"current","session"})
  void admissionReplayRequiresSessionStillCurrentAfterSourceLock(String boundary)throws Exception{
    Instant deadline=Instant.now().plusSeconds(6);certificate(deadline.plusSeconds(60));
    var input=new LabRequests.Start("protocol-fixture","request",LabRequests.Inputs.from(Map.of("message","Hello")));
    UUID run=cachedRun(deadline.plusSeconds(60),requestHash(input));
    if(boundary.equals("session"))db.update("UPDATE sessions SET expires_at=? WHERE user_id=?",Timestamp.from(deadline),owner);
    var result=afterSourceWait(body(post("/api/v1/lab-runs"),input).header("Idempotency-Key","round3-cached-run"),deadline,boundary.equals("session")).getResponse();
    assertEquals(boundary.equals("session")?401:202,result.getStatus(),result.getContentAsString());
    if(boundary.equals("session")){assertEquals("UNAUTHENTICATED",json.readTree(result.getContentAsString()).at("/error/code").asText());assertFalse(result.getContentAsString().contains(run.toString()));}
    else assertEquals(run.toString(),json.readTree(result.getContentAsString()).path("id").asText());
    assertEquals(1,db.queryForObject("SELECT count(*) FROM lab_runs",Integer.class));assertEquals("RUNNING",db.queryForObject("SELECT status FROM lab_runs WHERE id=?",String.class,run));assertEquals(0,db.queryForObject("SELECT count(*) FROM lab_outbox",Integer.class));assertTrue(provider.commands.isEmpty());
  }

  @ParameterizedTest @ValueSource(strings={"current","certificate","session"})
  void freshAdmissionRequiresAuthorityStillCurrentAfterSourceLock(String boundary)throws Exception{
    boolean expires=!boundary.equals("current");Instant deadline=Instant.now().plusSeconds(6);certificate(boundary.equals("certificate")?deadline:deadline.plusSeconds(60));
    if(boundary.equals("session"))db.update("UPDATE sessions SET expires_at=? WHERE user_id=?",Timestamp.from(deadline),owner);
    var request=body(post("/api/v1/lab-runs"),new LabRequests.Start("protocol-fixture","request",LabRequests.Inputs.from(Map.of("message","Hello")))).header("Idempotency-Key","round3-new-admission");
    var result=afterSourceWait(request,deadline,expires).getResponse();
    if(expires){assertEquals(boundary.equals("session")?401:503,result.getStatus(),result.getContentAsString());assertEquals(boundary.equals("session")?"UNAUTHENTICATED":"LAB_MANIFEST_UNAVAILABLE",json.readTree(result.getContentAsString()).at("/error/code").asText());assertEquals(0,db.queryForObject("SELECT count(*) FROM lab_runs",Integer.class));assertEquals(0,db.queryForObject("SELECT count(*) FROM lab_outbox",Integer.class));}
    else{assertEquals(202,result.getStatus(),result.getContentAsString());assertEquals(1,db.queryForObject("SELECT count(*) FROM lab_runs",Integer.class));assertEquals(1,db.queryForObject("SELECT count(*) FROM lab_outbox WHERE action='START' AND delivered_at IS NULL",Integer.class));}
    assertTrue(provider.commands.isEmpty(),"Admission only commits its outbox; it never calls the provider");
  }

  @ParameterizedTest @ValueSource(strings={"current","lease","certificate","session"})
  void cachedRequestRequiresAuthorityStillCurrentAfterSourceLock(String boundary)throws Exception{
    Instant deadline=Instant.now().plusSeconds(6);boolean expired=!boundary.equals("current");certificate(boundary.equals("certificate")?deadline:deadline.plusSeconds(60));UUID run=cachedRun(boundary.equals("lease")?deadline:deadline.plusSeconds(60));
    if(boundary.equals("session"))db.update("UPDATE sessions SET expires_at=? WHERE user_id=?",Timestamp.from(deadline),owner);
    String saved=db.queryForObject("SELECT response::text FROM lab_requests WHERE run_id=?",String.class,run);
    var result=afterSourceWait(body(post("/api/v1/lab-runs/"+run+"/requests"),operation()).header("Idempotency-Key","round3-cached-request"),deadline,expired).getResponse();
    assertEquals(expired?(boundary.equals("session")?401:boundary.equals("lease")?410:503):200,result.getStatus(),result.getContentAsString());
    if(expired)assertEquals(boundary.equals("session")?"UNAUTHENTICATED":boundary.equals("lease")?"LAB_EXPIRED":"LAB_MANIFEST_UNAVAILABLE",json.readTree(result.getContentAsString()).at("/error/code").asText());else assertEquals("Previously confirmed private output",json.readTree(result.getContentAsString()).path("output").asText());
    assertEquals(saved,db.queryForObject("SELECT response::text FROM lab_requests WHERE run_id=?",String.class,run));assertEquals(1,db.queryForObject("SELECT count(*) FROM lab_requests WHERE run_id=?",Integer.class,run));assertTrue(provider.commands.isEmpty(),"Cached results must never replay provider execution");
    assertNull(db.queryForObject("SELECT cleanup_confirmed_at FROM lab_runs WHERE id=?",Timestamp.class,run));assertEquals(1L,labs.reservedResources().get("active"));
    if(expired&&!boundary.equals("session")){labs.tick();assertNotNull(db.queryForObject("SELECT cleanup_confirmed_at FROM lab_runs WHERE id=?",Timestamp.class,run));assertTrue(provider.commands.values().stream().anyMatch(command->command.action().equals("STOP")&&command.runId().equals(run)));}
  }

  @ParameterizedTest @ValueSource(strings={"current-detail","current-events","lease-detail","lease-events","session-detail","session-events"})
  void detailAndEventsRequireAuthorityStillCurrentAfterSourceLock(String boundary)throws Exception{
    Instant deadline=Instant.now().plusSeconds(6);certificate(deadline.plusSeconds(60));UUID run=cachedRun(boundary.startsWith("lease")?deadline:deadline.plusSeconds(60));
    if(boundary.startsWith("session"))db.update("UPDATE sessions SET expires_at=? WHERE user_id=?",Timestamp.from(deadline),owner);
    var result=afterSourceWait(authenticated(get("/api/v1/lab-runs/"+run+(boundary.endsWith("events")?"/events":""))),deadline,!boundary.startsWith("current")).getResponse();
    if(boundary.startsWith("session")){assertEquals(401,result.getStatus(),result.getContentAsString());assertFalse(result.getContentAsString().contains("Previously confirmed private activity"));}
    else if(boundary.endsWith("detail")){assertEquals(200,result.getStatus(),result.getContentAsString());var value=json.readTree(result.getContentAsString());assertEquals(boundary.startsWith("lease")?"EXPIRED":"RUNNING",value.path("status").asText());assertEquals(boundary.startsWith("current"),value.path("verified").asBoolean());}
    else if(boundary.startsWith("current")){assertEquals(200,result.getStatus(),result.getContentAsString());assertTrue(result.getContentAsString().contains("Previously confirmed private activity"));}
    else{assertEquals(410,result.getStatus(),result.getContentAsString());assertFalse(result.getContentAsString().contains("Previously confirmed private activity"));}
    assertTrue(provider.commands.isEmpty());assertNull(db.queryForObject("SELECT cleanup_confirmed_at FROM lab_runs WHERE id=?",Timestamp.class,run));
  }

  @ParameterizedTest @ValueSource(strings={"current-admin","expired-admin","expired-owner"})
  void crossOwnerDetailRequiresRecentAdministratorMfaAfterSourceLock(String boundary)throws Exception{
    Instant deadline=Instant.now().plusSeconds(6);certificate(deadline.plusSeconds(60));UUID run=cachedRun(deadline.plusSeconds(60));
    boolean own=boundary.equals("expired-owner");UUID inspector=own?owner:user("round3-inspector",false);String token=own?"round3-runner":"round3-inspector";
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')",inspector);
    db.update("UPDATE sessions SET mfa_verified=true,issued_at=? WHERE user_id=?",Timestamp.from(deadline.minusSeconds(900)),inspector);
    var result=afterSourceWait(get("/api/v1/lab-runs/"+run).cookie(new Cookie("gl_session",token)),deadline,!boundary.equals("current-admin")).getResponse();
    boolean rejected=boundary.equals("expired-admin");assertEquals(rejected?403:200,result.getStatus(),result.getContentAsString());
    if(rejected){assertEquals("FORBIDDEN",json.readTree(result.getContentAsString()).at("/error/code").asText());assertFalse(result.getContentAsString().contains(run.toString()));}
    else{assertEquals(run.toString(),json.readTree(result.getContentAsString()).path("id").asText());assertTrue(json.readTree(result.getContentAsString()).path("verified").asBoolean());}
    assertTrue(db.queryForObject("SELECT expires_at>clock_timestamp() FROM sessions WHERE user_id=?",Boolean.class,inspector),"The MFA boundary must expire independently of the still-valid session");
    assertEquals("RUNNING",db.queryForObject("SELECT status FROM lab_runs WHERE id=?",String.class,run));assertEquals(0,db.queryForObject("SELECT count(*) FROM lab_outbox",Integer.class));assertTrue(provider.commands.isEmpty());
  }

  @ParameterizedTest @ValueSource(strings={"current","lease","certificate","session"})
  void providerResultRequiresAuthorityStillCurrentAfterResultPhaseSourceLock(String boundary)throws Exception{
    Instant deadline=Instant.now().plusSeconds(8);certificate(boundary.equals("certificate")?deadline:deadline.plusSeconds(60));UUID run=cachedRun(boundary.equals("lease")?deadline:deadline.plusSeconds(60));
    if(boundary.equals("session"))db.update("UPDATE sessions SET expires_at=? WHERE user_id=?",Timestamp.from(deadline),owner);
    provider.slowBody=true;
    var request=body(post("/api/v1/lab-runs/"+run+"/requests"),operation()).header("Idempotency-Key","round3-result-phase");
    var result=afterSourceWait(request,deadline,!boundary.equals("current"),true).getResponse();provider.slowBody=false;
    assertEquals(boundary.equals("current")?200:boundary.equals("session")?401:boundary.equals("lease")?410:503,result.getStatus(),result.getContentAsString());
    assertEquals(1,provider.commands.values().stream().filter(command->command.action().equals("REQUEST")).count());
    assertEquals(boundary.equals("current"),db.queryForObject("SELECT response IS NOT NULL FROM lab_requests WHERE run_id=? AND idempotency_key='round3-result-phase'",Boolean.class,run));
    assertNull(db.queryForObject("SELECT request_delivery_command FROM lab_runs WHERE id=?",UUID.class,run));
    assertEquals(boundary.equals("current")?2:1,db.queryForObject("SELECT count(*) FROM lab_events WHERE run_id=? AND command_id IS NOT NULL",Integer.class,run));
    assertNull(db.queryForObject("SELECT cleanup_confirmed_at FROM lab_runs WHERE id=?",Timestamp.class,run));
  }
}
