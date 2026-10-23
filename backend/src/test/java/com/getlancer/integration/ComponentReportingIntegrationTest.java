package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.labs.LabProtocolFixture;
import com.getlancer.labs.LabService;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
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

/** Disposable PostgreSQL and HTTP control-plane enforcement, never hosted isolation evidence. */
@ContextConfiguration(initializers=TestDatabaseGuard.class)
@SpringBootTest(properties={"app.environment=local","app.jobs-enabled=false","app.admin-email=report-admin@example.test","app.admin-password=","app.admin-totp=","spring.config.import=",
 "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}","spring.datasource.username=${TEST_DB_USERNAME:postgres}","spring.datasource.password=${TEST_DB_PASSWORD:}",
 "spring.datasource.hikari.schema=getlancer_test","spring.flyway.default-schema=getlancer_test","spring.flyway.schemas=getlancer_test","app.origin=http://localhost:3000","app.secure-cookie=false","app.storage.access-key=","app.storage.secret-key="})
@AutoConfigureMockMvc
class ComponentReportingIntegrationTest {
 static final LabProtocolFixture fixture=new LabProtocolFixture();
 @DynamicPropertySource static void properties(DynamicPropertyRegistry values){values.add("app.labs.enabled",()->true);values.add("app.labs.gateway-origin",fixture::origin);values.add("app.labs.gateway-secret",()->LabProtocolFixture.SECRET);values.add("app.labs.operator-public-key",fixture::publicKey);values.add("app.labs.admission-evidence",()->fixture.evidence.toString());values.add("app.labs.operator-epoch",()->fixture.epoch.toString());values.add("app.labs.timeout-ms",()->1000);}
 @AfterAll static void close(){fixture.close();}
 @Autowired JdbcTemplate db;@Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired LabService labs;
 UUID builder,admin,reporter,component;
 Map<String,Object> source;
 UUID user(String token,boolean developer){UUID id=UUID.randomUUID();db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",id,token+"@example.test");db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT')",id);if(developer){db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER')",id);db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,?,'APPROVED')",id,token,token);}db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",Support.hash(token),id);return id;}
 @BeforeEach void setup()throws Exception{
  fixture.reset();db.execute("TRUNCATE lab_manifests CASCADE");db.execute("TRUNCATE users CASCADE");db.execute("TRUNCATE rate_buckets");db.update("UPDATE lab_runtime_settings SET observed_epoch=NULL,paused=false,pause_reason='',daily_run_limit=5,max_active_runs=10,max_memory_mib=5120,daily_budget_micros=50000000,monthly_budget_micros=1000000000 WHERE id=1");
  builder=user("report-builder",true);reporter=user("report-reader",false);user("report-runner",false);user("report-other-runner",false);admin=user("report-admin",false);db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')",admin);db.update("UPDATE sessions SET mfa_verified=true,issued_at=now() WHERE user_id=?",admin);
  source=Map.of("kind","BACKEND","version","1.0.0","sha256","a".repeat(64),"archiveSha256","c".repeat(64),"manifestSha256","d".repeat(64),"files",Map.of("README.md","Controlled protocol fixture source, not production KVM evidence."));component=component("reported");manifest(component,"protocol-fixture");
 }
 UUID component(String slug)throws Exception{UUID id=UUID.randomUUID();db.update("INSERT INTO component_entries(id,owner_id,recipe_slug,slug,title,summary,contribution,status,published_at,published_source,published_context) VALUES(?,?,'protocol-fixture',?,'Reported protocol component','Source reporting protocol fixture','Original controlled test contribution','ACTIVE',now(),?::jsonb,?::jsonb)",id,builder,"report-"+slug,json.writeValueAsString(source),json.writeValueAsString(Map.of("title","Reported protocol component","summary","Source reporting protocol fixture","contribution","Original controlled test contribution")));db.update("INSERT INTO component_releases(component_id,revision,source,context,source_sha256) VALUES(?,1,?::jsonb,'{}'::jsonb,?)",id,json.writeValueAsString(source),source.get("sha256"));return id;}
 void manifest(UUID id,String name)throws Exception{var value=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(fixture.manifest(id));value.put("id",name);String payload=value.toString();db.update("INSERT INTO lab_manifests(id,payload_text,payload_sha256,signature) VALUES(?,?,?,?)",name,payload,Support.hash(payload),fixture.sign(payload));}
 MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request,String token){return request.cookie(new Cookie("gl_session",token));}
 MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request,String token,Object value)throws Exception{return as(request,token).header("Origin","http://localhost:3000").header("X-Requested-With","getlancer").contentType("application/json").content(json.writeValueAsString(value));}
 Map<String,Object> reportBody(UUID target){return Map.of("targetType","COMPONENT","targetId",target,"componentRevision",1,"componentSourceHash","a".repeat(64),"reason","MISLEADING_CLAIM","detail","This exact source version makes a misleading execution claim.");}
 UUID report(UUID target)throws Exception{return UUID.fromString(json.readTree(mvc.perform(body(post("/api/v1/reports"),"report-reader",reportBody(target))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("reference").asText());}
 UUID start(String token,String manifest,String key)throws Exception{return UUID.fromString(json.readTree(mvc.perform(body(post("/api/v1/lab-runs"),token,Map.of("manifestId",manifest,"scenarioId","request","inputs",Map.of("message","Hello"))).header("Idempotency-Key",key)).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()).path("id").asText());}
 void suspend(UUID report)throws Exception{mvc.perform(body(post("/api/v1/admin/reports/"+report+"/resolve"),"report-admin",Map.of("targetAction","SUSPEND","reason","Source claim was independently reviewed and rejected."))).andExpect(status().isOk());}

 @Test void canonicalSnapshotIsFrozenAndModeratorNotesStayPrivate()throws Exception{
  UUID id=report(component);var row=db.queryForMap("SELECT component_revision,component_version,component_source_hash,component_snapshot FROM reports WHERE id=?",id);
  assertEquals(1L,((Number)row.get("component_revision")).longValue());assertEquals("1.0.0",row.get("component_version"));assertEquals("a".repeat(64),row.get("component_source_hash"));var snapshot=json.readTree(row.get("component_snapshot").toString());assertEquals(component.toString(),snapshot.path("componentId").asText());assertEquals("Original controlled test contribution",snapshot.path("context").path("contribution").asText());assertFalse(snapshot.path("source").has("files"));assertFalse(snapshot.toString().contains("archiveBase64"));
  assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE reports SET component_source_hash=? WHERE id=?","f".repeat(64),id));assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE reports SET target_id=? WHERE id=?",UUID.randomUUID(),id));
  assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("INSERT INTO reports(id,target_type,target_id,reason,detail,component_revision,component_version,component_source_hash,component_snapshot) VALUES(?,'COMPONENT',?,'OTHER','Malformed internal snapshot',1,'1.0.0',?,'{}'::jsonb)",UUID.randomUUID(),component,"a".repeat(64)));
  mvc.perform(body(post("/api/v1/admin/reports/"+id+"/triage"),"report-admin",Map.of("severity","HIGH","status","UNDER_REVIEW","reason","Private moderator investigation note."))).andExpect(status().isOk());
  mvc.perform(as(get("/api/v1/admin/reports/"+id),"report-admin")).andExpect(status().isOk()).andExpect(jsonPath("$.report.component_source_hash").value("a".repeat(64))).andExpect(jsonPath("$.report.triage_note").value("Private moderator investigation note."));
  mvc.perform(as(get("/api/v1/admin/reports/"+id),"report-reader")).andExpect(status().isForbidden());mvc.perform(as(get("/api/v1/admin/reports"),"report-builder")).andExpect(status().isForbidden());
  var another=new LinkedHashMap<>(reportBody(component));another.put("detail","Another reporter's private report content canary.");UUID otherReport=UUID.fromString(json.readTree(mvc.perform(body(post("/api/v1/reports"),"report-runner",another)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("reference").asText());
  String exported=mvc.perform(as(get("/api/v1/me/export"),"report-reader")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();var ownReports=json.readTree(exported).path("reports");assertEquals(1,ownReports.size());assertEquals(id.toString(),ownReports.get(0).path("id").asText());assertEquals("a".repeat(64),ownReports.get(0).path("component_source_hash").asText());assertFalse(exported.contains("Private moderator investigation note"));assertFalse(exported.contains("Another reporter's private report content canary"));assertFalse(ownReports.get(0).has("component_snapshot"));assertFalse(ownReports.get(0).has("triage_note"));
  var otherExport=json.readTree(mvc.perform(as(get("/api/v1/me/export"),"report-runner")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("reports");assertEquals(1,otherExport.size());assertEquals(otherReport.toString(),otherExport.get(0).path("id").asText());
  String publicValue=mvc.perform(get("/api/v1/components/report-reported")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertFalse(publicValue.contains("Private moderator"));assertFalse(publicValue.contains("triage_note"));
 }
 @Test void staleForgedMissingAndPrivateBindingsCannotCreateReports()throws Exception{
  for(var change:java.util.List.of(Map.<String,Object>of("componentRevision",2),Map.<String,Object>of("componentSourceHash","f".repeat(64)))){var value=new LinkedHashMap<>(reportBody(component));value.putAll(change);mvc.perform(body(post("/api/v1/reports"),"report-reader",value)).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("REPORT_ARTIFACT_CHANGED"));}
  var missing=new LinkedHashMap<>(reportBody(component));missing.remove("componentRevision");mvc.perform(body(post("/api/v1/reports"),"report-reader",missing)).andExpect(status().isBadRequest());
  var mixed=new LinkedHashMap<>(reportBody(component));mixed.put("educationReleaseId",UUID.randomUUID());mvc.perform(body(post("/api/v1/reports"),"report-reader",mixed)).andExpect(status().isBadRequest());
  db.update("UPDATE component_entries SET withdrawn_at=now() WHERE id=?",component);mvc.perform(body(post("/api/v1/reports"),"report-reader",reportBody(component))).andExpect(status().isNotFound());assertEquals(0,db.queryForObject("SELECT count(*) FROM reports",Integer.class));
 }
 @Test void reportSuspensionDeniesNewAndCurrentLabRoutesButLeavesIndependentComponentRunning()throws Exception{
  UUID independent=component("independent");manifest(independent,"independent-fixture");UUID affectedRun=start("report-runner","protocol-fixture","report-affected-run"),otherRun=start("report-other-runner","independent-fixture","report-independent-run");labs.tick();labs.tick();
  mvc.perform(as(get("/api/v1/lab-runs/"+affectedRun),"report-runner")).andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(true));UUID report=report(component);suspend(report);
  long reservations=db.queryForObject("SELECT count(*) FROM lab_runs",Long.class),commands=db.queryForObject("SELECT count(*) FROM lab_outbox",Long.class);
  mvc.perform(body(post("/api/v1/lab-runs"),"report-reader",Map.of("manifestId","protocol-fixture","scenarioId","request","inputs",Map.of("message","Hello"))).header("Idempotency-Key","report-fresh-denied")).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("LAB_SOURCE_REVOKED"));
  mvc.perform(body(post("/api/v1/lab-runs/"+affectedRun+"/requests"),"report-runner",Map.of("operationId","echo","inputs",Map.of("message","Hello"))).header("Idempotency-Key","report-current-denied")).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("LAB_SOURCE_REVOKED"));mvc.perform(as(get("/api/v1/lab-runs/"+affectedRun+"/events"),"report-runner")).andExpect(status().isForbidden());
  assertEquals(reservations,db.queryForObject("SELECT count(*) FROM lab_runs",Long.class));assertEquals(commands,db.queryForObject("SELECT count(*) FROM lab_outbox",Long.class));assertEquals(0,db.queryForObject("SELECT count(*) FROM lab_requests WHERE run_id=?",Integer.class,affectedRun));
  mvc.perform(body(post("/api/v1/lab-runs/"+otherRun+"/requests"),"report-other-runner",Map.of("operationId","echo","inputs",Map.of("message","Independent"))).header("Idempotency-Key","report-independent-operation")).andExpect(status().isOk());
  mvc.perform(body(post("/api/v1/lab-runs/"+affectedRun+"/stop"),"report-runner",Map.of())).andExpect(status().isOk());labs.tick();labs.tick();assertNotNull(db.queryForObject("SELECT cleanup_confirmed_at FROM lab_runs WHERE id=?",java.sql.Timestamp.class,affectedRun));
  String decisions=mvc.perform(as(get("/api/v1/me/moderation-decisions"),"report-builder")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertTrue(decisions.contains("COMPONENT"));assertFalse(decisions.contains("triage_note"));
 }
 @Test void replacementSourceNeedsASeparateReviewAndMfaCannotBeBypassed()throws Exception{
  UUID id=report(component);db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?",admin);mvc.perform(body(post("/api/v1/admin/reports/"+id+"/resolve"),"report-admin",Map.of("targetAction","SUSPEND","reason","A report enforcement decision."))).andExpect(status().isForbidden());assertEquals("ACTIVE",db.queryForObject("SELECT status FROM component_entries WHERE id=?",String.class,component));db.update("UPDATE sessions SET mfa_verified=true,issued_at=now() WHERE user_id=?",admin);
  db.update("UPDATE sessions SET issued_at=now()-interval '16 minutes' WHERE user_id=?",admin);mvc.perform(body(post("/api/v1/admin/reports/"+id+"/resolve"),"report-admin",Map.of("targetAction","SUSPEND","reason","A report enforcement decision."))).andExpect(status().isForbidden());assertEquals("ACTIVE",db.queryForObject("SELECT status FROM component_entries WHERE id=?",String.class,component));db.update("UPDATE sessions SET issued_at=now() WHERE user_id=?",admin);
  var replacement=new LinkedHashMap<>(source);replacement.put("version","2.0.0");replacement.put("sha256","f".repeat(64));db.update("INSERT INTO component_releases(component_id,revision,source,context,source_sha256) VALUES(?,2,?::jsonb,'{}'::jsonb,?)",component,json.writeValueAsString(replacement),"f".repeat(64));db.update("UPDATE component_entries SET revision=3 WHERE id=?",component);
  mvc.perform(body(post("/api/v1/admin/reports/"+id+"/resolve"),"report-admin",Map.of("targetAction","SUSPEND","reason","A report enforcement decision."))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("REPORTED_COMPONENT_CHANGED"));assertEquals("OPEN",db.queryForObject("SELECT status FROM reports WHERE id=?",String.class,id));assertEquals("ACTIVE",db.queryForObject("SELECT status FROM component_entries WHERE id=?",String.class,component));assertEquals("a".repeat(64),db.queryForObject("SELECT component_source_hash FROM reports WHERE id=?",String.class,id));
 }
 @Test void sameSourceWithChangedPublicationContextRequiresFreshReview()throws Exception{
  UUID id=report(component);db.update("UPDATE component_entries SET revision=revision+1,published_context=jsonb_build_object('title','A newly reviewed claim') WHERE id=?",component);
  mvc.perform(body(post("/api/v1/admin/reports/"+id+"/resolve"),"report-admin",Map.of("targetAction","SUSPEND","reason","A report enforcement decision."))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("REPORTED_COMPONENT_CHANGED"));assertEquals("ACTIVE",db.queryForObject("SELECT status FROM component_entries WHERE id=?",String.class,component));assertEquals("OPEN",db.queryForObject("SELECT status FROM reports WHERE id=?",String.class,id));
 }
 @Test void curatedRecipesAreReportableAndExactHashEnforcementDoesNotSuspendRemixes()throws Exception{
  var catalog=json.readTree(mvc.perform(get("/api/v1/components")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());String slug=catalog.path("items").get(0).path("slug").asText();var detail=json.readTree(mvc.perform(get("/api/v1/components/"+slug)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  db.update("UPDATE component_entries SET recipe_slug=? WHERE id=?",slug,component);UUID target=UUID.fromString(detail.path("reportTargetId").asText());var input=new LinkedHashMap<>(reportBody(target));input.put("componentRevision",detail.path("reportRevision").asLong());input.put("componentSourceHash",detail.path("sha256").asText());UUID id=UUID.fromString(json.readTree(mvc.perform(body(post("/api/v1/reports"),"report-reader",input)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("reference").asText());suspend(id);
  mvc.perform(get("/api/v1/components/"+slug)).andExpect(status().isNotFound());mvc.perform(get("/api/v1/components/"+slug+"/versions")).andExpect(status().isNotFound());mvc.perform(get("/api/v1/components/"+slug+"/source").param("sha256",detail.path("sha256").asText())).andExpect(status().isNotFound());
  String catalogue=mvc.perform(get("/api/v1/components")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertFalse(catalogue.contains("\"slug\":\""+slug+"\""));assertEquals("ACTIVE",db.queryForObject("SELECT status FROM component_entries WHERE id=?",String.class,component));
  String independent=mvc.perform(get("/api/v1/components/report-reported")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertFalse(json.readTree(independent).path("related").toString().contains("\"slug\":\""+slug+"\""));
  var own=json.readTree(mvc.perform(as(get("/api/v1/me/components"),"report-builder")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());assertFalse(own.path("recipes").toString().contains("\"slug\":\""+slug+"\""));
  mvc.perform(body(post("/api/v1/me/components"),"report-builder",Map.of("recipeSlug",slug,"title","Suspended recipe remix","summary","A new copy of the suspended recipe.","contribution","An original new context for this source.","rightsConsent",true))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("COMPONENT_RECIPE_UNAVAILABLE"));
 }
}
