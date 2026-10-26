package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@ContextConfiguration(initializers=TestDatabaseGuard.class)
@SpringBootTest(properties={"app.environment=local","app.jobs-enabled=false","app.admin-email=assessment-admin@example.test","app.admin-password=","app.admin-totp=","spring.config.import=",
 "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}","spring.datasource.username=${TEST_DB_USERNAME:postgres}","spring.datasource.password=${TEST_DB_PASSWORD:}",
 "spring.datasource.hikari.schema=getlancer_test","spring.flyway.default-schema=getlancer_test","spring.flyway.schemas=getlancer_test","app.origin=http://localhost:3000","app.secure-cookie=false","app.storage.access-key=","app.storage.secret-key="})
@AutoConfigureMockMvc
class ComponentReviewAssessmentsIntegrationTest {
 @Autowired JdbcTemplate db; @Autowired MockMvc mvc; @Autowired ObjectMapper json;
 UUID builder,admin,component; JsonNode shown;
 static final String NOTE="Private assessment canary: independently inspected this exact artifact.";
 UUID user(String token,boolean developer){UUID id=UUID.randomUUID();db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",id,token+"@example.test");db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT')",id);if(developer){db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER')",id);db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,?,'APPROVED')",id,token,token);db.update("INSERT INTO showcase_entitlements(user_id) VALUES(?)",id);}db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",Support.hash(token),id);return id;}
 MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request,String actor){return request.cookie(new Cookie("gl_session",actor));}
 MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request,String actor,Object value)throws Exception{return as(request,actor).header("Origin","http://localhost:3000").header("X-Requested-With","getlancer").contentType("application/json").content(json.writeValueAsString(value));}
 JsonNode response(MockHttpServletRequestBuilder request)throws Exception{return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
 @BeforeEach void setup()throws Exception{
  db.execute("TRUNCATE users CASCADE");db.execute("TRUNCATE rate_buckets,component_slot_events");builder=user("assessment-builder",true);admin=user("assessment-admin",false);db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')",admin);db.update("UPDATE sessions SET mfa_verified=true,issued_at=now() WHERE user_id=?",admin);
  component=UUID.fromString(response(body(post("/api/v1/me/components"),"assessment-builder",Map.of("recipeSlug","portfolio-card","title","Assessed source example","summary","A specific attributed recipe selection.","contribution","Selected and inspected the MIT recipe for this original use case.","rightsConsent",true))).path("id").asText());
  response(body(post("/api/v1/me/components/"+component+"/submit"),"assessment-builder",Map.of()));shown=response(as(get("/api/v1/admin/components/"+component),"assessment-admin"));
 }
 Map<String,Object> review(){var value=new LinkedHashMap<String,Object>();value.put("revision",shown.path("revision").asLong());value.put("sourceHash",shown.path("sourceHash").asText());value.put("decision","APPROVE");value.put("reason","Public decision reason for this exact reviewed source.");for(String key:List.of("originality","quality","license","execution"))value.put(key,Map.of("result","PASS","note",NOTE));return value;}
 MockHttpServletRequestBuilder command(Map<String,Object> value)throws Exception{return body(post("/api/v1/admin/components/"+component+"/review"),"assessment-admin",value);}
 @Test void everyAssessmentRequiresBoundedExplicitEvidenceAndRunnableApprovalCannotSkipExecution()throws Exception{
  for(String criterion:List.of("originality","quality","license","execution")){
   var missing=review();missing.remove(criterion);mvc.perform(command(missing)).andExpect(status().isBadRequest());
   var blank=review();blank.put(criterion,Map.of("result","PASS","note"," ".repeat(25)));mvc.perform(command(blank)).andExpect(status().isBadRequest());
   var failed=review();failed.put(criterion,Map.of("result","FAIL","note",NOTE));mvc.perform(command(failed)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("REVIEW_ASSESSMENT_FAILED"));
  }
  var skipped=review();skipped.put("execution",Map.of("result","NOT_APPLICABLE","note",NOTE));mvc.perform(command(skipped)).andExpect(status().isBadRequest());
  var internal=review();internal.put("decision","APPEAL_RESTORE");mvc.perform(command(internal)).andExpect(status().isBadRequest());
  assertEquals(0,db.queryForObject("SELECT count(*) FROM component_review_assessments",Integer.class));assertEquals("PENDING",db.queryForObject("SELECT status FROM component_entries WHERE id=?",String.class,component));
 }
 @Test void approvalPersistsExactIdentityAndPrivateImmutableHistory()throws Exception{
  response(command(review()));var row=db.queryForMap("SELECT * FROM component_review_assessments WHERE component_id=?",component);assertEquals(admin,row.get("admin_id"));assertEquals(shown.path("revision").asLong(),((Number)row.get("revision")).longValue());assertEquals(shown.path("sourceHash").asText(),row.get("source_hash"));assertEquals("APPROVE",row.get("decision"));assertTrue(row.get("assessments").toString().contains(NOTE));
  var detail=response(as(get("/api/v1/admin/components/"+component),"assessment-admin"));assertEquals(1,detail.path("reviewHistory").size());assertEquals(NOTE,detail.path("reviewHistory").get(0).path("assessments").path("originality").path("note").asText());
  for(String path:List.of("/api/v1/components", "/api/v1/components/"+shown.path("slug").asText(),"/api/v1/me/components/"+component)){
   String output=response(as(get(path),"assessment-builder")).toString();assertFalse(output.contains(NOTE));assertFalse(output.contains("reviewHistory"));
  }
  assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE component_review_assessments SET reason='Changed historical assessment' WHERE component_id=?",component));assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("DELETE FROM component_review_assessments WHERE component_id=?",component));
 }
 @Test void staleHashRevisionAndRevokedAdministratorCannotRecordAssessment()throws Exception{
  var stale=review();stale.put("revision",shown.path("revision").asLong()+1);mvc.perform(command(stale)).andExpect(status().isConflict());
  var forged=review();forged.put("sourceHash","f".repeat(64));mvc.perform(command(forged)).andExpect(status().isConflict());
  mvc.perform(body(post("/api/v1/admin/components/"+component+"/review"),"assessment-builder",review())).andExpect(status().isForbidden());
  db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?",admin);mvc.perform(command(review())).andExpect(status().isForbidden());mvc.perform(as(get("/api/v1/admin/components/"+component),"assessment-admin")).andExpect(status().isForbidden());assertEquals(0,db.queryForObject("SELECT count(*) FROM component_review_assessments",Integer.class));
 }
 @Test void changesAndSuspensionKeepExplicitFailEvidenceWithoutPublishing()throws Exception{
  var changed=review();changed.put("decision","CHANGES_REQUESTED");changed.put("quality",Map.of("result","FAIL","note",NOTE));response(command(changed));assertEquals("DRAFT",db.queryForObject("SELECT status FROM component_entries WHERE id=?",String.class,component));
  response(body(post("/api/v1/me/components/"+component+"/submit"),"assessment-builder",Map.of()));shown=response(as(get("/api/v1/admin/components/"+component),"assessment-admin"));var suspended=review();suspended.put("decision","SUSPEND");suspended.put("license",Map.of("result","FAIL","note",NOTE));response(command(suspended));assertEquals(2,db.queryForObject("SELECT count(*) FROM component_review_assessments WHERE component_id=?",Integer.class,component));assertEquals(0,db.queryForObject("SELECT count(*) FROM component_releases WHERE component_id=?",Integer.class,component));
 }
}
