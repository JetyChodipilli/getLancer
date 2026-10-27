package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
@SpringBootTest(properties={"app.environment=local","app.jobs-enabled=false","app.admin-email=audit-admin@example.test","app.admin-password=","app.admin-totp=",
 "spring.config.import=","app.origin=http://localhost:3000","app.secure-cookie=false","app.rate-limit=1000","app.storage.access-key=","app.storage.secret-key=",
 "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}","spring.datasource.username=${TEST_DB_USERNAME:postgres}","spring.datasource.password=${TEST_DB_PASSWORD:}",
 "spring.datasource.hikari.schema=getlancer_test","spring.flyway.default-schema=getlancer_test","spring.flyway.schemas=getlancer_test"})
@AutoConfigureMockMvc
class AdminHostingAuditRegressionTest {
 @Autowired JdbcTemplate db;
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 UUID admin,owner,stranger,hosted,oldMail,expiredMail,freshMail,appeal;
 UUID user(String token,boolean operator){UUID id=UUID.randomUUID();db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",id,token+"@example.test");db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT')",id);if(operator)db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')",id);db.update("INSERT INTO sessions(token_hash,user_id,expires_at,mfa_verified,issued_at) VALUES(?,?,now()+interval '1 hour',?,now())",Support.hash(token),id,operator);return id;}
 @BeforeEach void seed(){
  clear();admin=user("audit-admin",true);owner=user("audit-owner",false);stranger=user("audit-stranger",false);
  for(UUID builder:new UUID[]{owner,stranger}){db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER')",builder);db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,'Hosting audit builder','APPROVED')",builder,builder.toString());}
  UUID product=UUID.randomUUID();db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text) VALUES(?,?,'audit-hosted','Audit hosted','Controlled package','Private hosting ownership fixture','SAAS','CRM','Java','Built this fixture')",product,owner);
  hosted=UUID.randomUUID();db.update("INSERT INTO hosted_demos(id,owner_id,product_id,title,version,storage_key,archive_sha256,manifest_sha256,size_bytes,expanded_bytes,file_count,files) VALUES(?,?,?,'Retained private archive','1.0.0','private/audit-locator',?,?,22,1,1,'[]'::jsonb)",hosted,owner,product,"a".repeat(64),"b".repeat(64));
  oldMail=mail("Old confirmation","Private expired confirmation",26);expiredMail=mail("Redacted confirmation","[expired]",1);freshMail=mail("Fresh failed delivery","Private current confirmation",0);
  UUID decision=UUID.randomUUID();db.update("INSERT INTO moderation_actions(id,admin_id,target_type,target_id,action,reason) VALUES(?,?,'ACCOUNT',?,'suspend','Independent decision fixture')",decision,admin,owner);appeal=UUID.randomUUID();db.update("INSERT INTO moderation_appeals(id,decision_id,appellant_id,statement,evidence_url) VALUES(?,?,?,'Independent evidence for this decision','https://evidence.example.test/private-review')",appeal,decision,owner);
 }
 UUID mail(String subject,String body,int hours){UUID id=UUID.randomUUID();db.update("INSERT INTO email_outbox(id,recipient,subject,body,attempts,created_at) VALUES(?,'audit-owner@example.test',?,?,8,now()-(? * interval '1 hour'))",id,subject,body,hours);return id;}
 @AfterEach void clear(){db.execute("TRUNCATE users CASCADE");db.execute("TRUNCATE email_outbox,rate_buckets");}
 MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request,String token){return request.cookie(new Cookie("gl_session",token));}
 MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request,String token,Map<String,Object> value)throws Exception{return as(request,token).header("Origin","http://localhost:3000").header("X-Requested-With","getlancer").contentType("application/json").content(json.writeValueAsString(value));}
 JsonNode ok(MockHttpServletRequestBuilder request)throws Exception{return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}

 @Test void failedMailPagesExposeTheSameExpiryPolicyAsRetryWithoutLeakingBodies()throws Exception{
  JsonNode first=ok(as(get("/api/v1/admin/email-jobs").param("size","2"),"audit-admin")),last=ok(as(get("/api/v1/admin/email-jobs").param("size","2").param("page","1"),"audit-admin"));
  assertEquals(3,first.path("totalItems").asInt());assertTrue(first.path("hasMore").asBoolean());assertEquals(oldMail.toString(),first.at("/items/0/id").asText());assertTrue(first.at("/items/0/expired").asBoolean());assertEquals(expiredMail.toString(),first.at("/items/1/id").asText());assertTrue(first.at("/items/1/expired").asBoolean());assertEquals(freshMail.toString(),last.at("/items/0/id").asText());assertFalse(last.at("/items/0/expired").asBoolean());assertFalse(first.toString().contains("Private expired"));assertFalse(first.toString().contains("audit-owner@example.test"));
  for(UUID id:new UUID[]{oldMail,expiredMail})mvc.perform(body(post("/api/v1/admin/email-jobs/"+id+"/retry"),"audit-admin",Map.of("reason","Delivery settings reviewed."))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("MAIL_RENEWAL_REQUIRED"));
  mvc.perform(body(post("/api/v1/admin/email-jobs/"+freshMail+"/retry"),"audit-admin",Map.of("reason","Delivery settings reviewed."))).andExpect(status().isOk());assertEquals(0,db.queryForObject("SELECT attempts FROM email_outbox WHERE id=?",Integer.class,freshMail));assertEquals(1,db.queryForObject("SELECT count(*) FROM moderation_actions WHERE target_type='EMAIL' AND target_id=?",Integer.class,freshMail));
 }
 @Test void appellantCanReadTheirSubmittedEvidenceAndOtherAccountsCannotReadIt()throws Exception{
  JsonNode own=ok(as(get("/api/v1/me/appeals"),"audit-owner"));assertEquals(appeal.toString(),own.at("/items/0/id").asText());assertEquals("https://evidence.example.test/private-review",own.at("/items/0/evidence_url").asText());assertEquals(0,ok(as(get("/api/v1/me/appeals"),"audit-stranger")).path("items").size());
 }
 @Test void currentAdministratorAuthorityAndBoundsApplyToEverySensitiveCollection()throws Exception{
  for(String path:new String[]{"/api/v1/admin/appeals","/api/v1/admin/email-jobs","/api/v1/admin/hosting","/api/v1/admin/reports","/api/v1/admin/deletion-requests"}){
   mvc.perform(get(path).param("page","1")).andExpect(status().isUnauthorized());mvc.perform(as(get(path).param("page","1"),"audit-owner")).andExpect(status().isForbidden());
   mvc.perform(as(get(path).param("page","-1"),"audit-admin")).andExpect(status().isBadRequest());
   db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?",admin);mvc.perform(as(get(path).param("page","1"),"audit-admin")).andExpect(status().isForbidden());db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?",admin);
  }
 }
 @Test void hostingOwnershipAndDisabledRuntimeFailClosedWithoutProviderCalls()throws Exception{
  mvc.perform(as(get("/api/v1/me/hosting/"+hosted),"audit-stranger")).andExpect(status().isNotFound());mvc.perform(body(post("/api/v1/me/hosting/"+hosted+"/withdraw"),"audit-stranger",Map.of())).andExpect(status().isNotFound());
  JsonNode projection=ok(as(get("/api/v1/admin/hosting"),"audit-admin"));assertEquals(1,projection.path("items").size());assertFalse(projection.toString().contains("private/audit-locator"));assertTrue(projection.at("/items/0/url").isNull());
  assertFalse(ok(as(get("/api/v1/admin/labs"),"audit-admin")).at("/runtime/enabled").asBoolean());assertEquals(0,db.queryForObject("SELECT count(*) FROM lab_outbox",Integer.class));
 }
}
