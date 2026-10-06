package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@org.springframework.test.context.ContextConfiguration(initializers = TestDatabaseGuard.class)
@SpringBootTest(properties = {
    "app.environment=local", "app.jobs-enabled=false", "app.admin-password=", "app.admin-totp=",
    "spring.config.import=", "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}",
    "spring.datasource.username=${TEST_DB_USERNAME:postgres}", "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.hikari.schema=getlancer_test", "spring.flyway.default-schema=getlancer_test",
    "spring.flyway.schemas=getlancer_test", "app.origin=http://localhost:3000", "app.secure-cookie=false",
    "app.storage.access-key=", "app.storage.secret-key="
})
@AutoConfigureMockMvc
class AnalyticsCoverageIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  UUID admin;

  UUID user(String name, boolean approved) {
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())", id, name+"@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT'),(?,'DEVELOPER')", id,id);
    db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status,availability_status) VALUES(?,?,?,?, 'AVAILABLE_NOW')",id,name,name,approved?"APPROVED":"DRAFT");
    db.update("INSERT INTO showcase_entitlements(user_id) VALUES(?)",id);
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",Support.hash(name),id);
    return id;
  }

  UUID product(UUID owner, String visibility, String lifecycle, boolean live, boolean video) {
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,approval_status,lifecycle_status,visibility,live_url,video_url) VALUES(?,?,?,'Private identifying title','Working proof','Confidential source description','SAAS','CRM','Java','Built it','APPROVED',?,?,?,?)",id,owner,id.toString(),lifecycle,visibility,live?"https://example.com":"",video?"https://www.youtube.com/watch?v=abcdefghijk":"");
    return id;
  }

  UUID inquiry(UUID product, UUID builder, String email, Instant confirmed, String moderation, String state) {
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO inquiries(id,reference_product_id,developer_user_id,client_email,client_name,request_type,description,budget_band,timeline_band,current_status,email_confirmed_at,idempotency_key,request_hash,moderation_status) VALUES(?,?,?,?,'Private client','SIMILAR_BUILD','Secret client requirements','USD_3K_10K','ONE_TO_THREE_MONTHS',?,?,?,'hash',?)",id,product,builder,email,state,confirmed==null?null:Timestamp.from(confirmed),UUID.randomUUID(),moderation);
    return id;
  }

  void event(UUID inquiry, String kind, Instant at) {
    db.update("INSERT INTO inquiry_events(id,inquiry_id,event_type,actor_type,created_at) VALUES(?,?,?,?,?)",UUID.randomUUID(),inquiry,kind,kind.equals("RESPONDED")?"DEVELOPER":"CLIENT",Timestamp.from(at));
  }

  void observation(String name, String source, UUID entity, String context, Instant at) {
    db.update("INSERT INTO analytics_events(id,event_name,source,entity_id,context,created_at) VALUES(?,?,?,?,?::jsonb,?)",UUID.randomUUID(),name,source,entity,context,Timestamp.from(at));
  }

  JsonNode metrics() throws Exception {
    String raw=mvc.perform(get("/api/v1/admin/metrics").cookie(new Cookie("gl_session","operator")))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertFalse(raw.contains("private-client@"));
    assertFalse(raw.contains("Secret client requirements"));
    assertFalse(raw.contains("Private identifying title"));
    JsonNode root=json.readTree(raw);
    assertTrue(root.has("weekly") && root.has("sources") && root.has("reportedValues") && root.has("failedEmails"));
    return root.get("marketplace");
  }

  @BeforeEach void prepare() {
    db.execute("TRUNCATE users CASCADE");
    db.execute("TRUNCATE analytics_events,rate_buckets");
    admin=user("operator",false);
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')",admin);
    db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?",admin);
  }

  @Test void operatorMetricsRequireMfaAndEmptyCohortsHaveNoInventedRates() throws Exception {
    mvc.perform(get("/api/v1/admin/metrics")).andExpect(status().isUnauthorized());
    user("viewer",false);
    mvc.perform(get("/api/v1/admin/metrics").cookie(new Cookie("gl_session","viewer"))).andExpect(status().isForbidden());
    db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?",admin);
    mvc.perform(get("/api/v1/admin/metrics").cookie(new Cookie("gl_session","operator"))).andExpect(status().isForbidden());
    db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?",admin);
    JsonNode m=metrics();
    assertEquals(84,m.at("/window/days").asInt());
    assertEquals(0,m.at("/supply/approvedBuilders").asInt());
    assertEquals(0,m.at("/outcomes/qualifiedInquiries").asInt());
    assertEquals(0,m.at("/search/sampleSearches").asInt());
    for(String path:new String[]{"/supply/liveDemoPercent","/outcomes/responseRatePercent","/outcomes/inquiryToHirePercent","/outcomes/medianResponseHours","/outcomes/top10LeadSharePercent","/slots/utilisationPercent","/search/threeResultPercent","/safety/resolutionRatePercent","/safety/reversalRatePercent"}) assertTrue(m.at(path).isNull(),path);
    assertEquals(0,m.get("portfolioCohorts").size());
    assertEquals(0,m.at("/slots/blockedActivationBuilders").asInt());
    assertFalse(m.at("/slots/blockedActivationReason").asText().isBlank());
  }

  @Test void realSupplyQualifiedCohortsResponseConcentrationAndSlotActivityAreAggregated() throws Exception {
    Instant now=Instant.now(),first=now.minus(7,ChronoUnit.DAYS);
    UUID a=user("builder-a",true),b=user("builder-b",true),suspended=user("restricted-builder",true);
    db.update("UPDATE users SET account_status='SUSPENDED' WHERE id=?",suspended);
    UUID p=product(a,"PUBLIC","ACTIVE",true,false);
    product(a,"PUBLIC","ACTIVE",false,true);
    product(a,"PRIVATE_CASE_STUDY","ACTIVE",true,true);
    product(a,"PUBLIC","ARCHIVED",true,true);
    product(b,"PRIVATE_CASE_STUDY","ACTIVE",false,false);
    UUID archivedB=product(b,"PUBLIC","ARCHIVED",false,false);
    product(suspended,"PUBLIC","ACTIVE",true,true);
    db.update("INSERT INTO product_media(id,product_id,storage_key,content_type,size_bytes,verified) VALUES(?,?,?,'image/png',100,true)",UUID.randomUUID(),p,UUID.randomUUID().toString());
    UUID q1=inquiry(p,a,"private-client@one.test",first,"CLEAR","COMPLETED");
    event(q1,"RESPONDED",first.plus(4,ChronoUnit.HOURS));event(q1,"HIRED",first.plus(1,ChronoUnit.DAYS));event(q1,"COMPLETED",first.plus(2,ChronoUnit.DAYS));
    UUID q2=inquiry(p,a,"private-client@one.test",now.minus(5,ChronoUnit.DAYS),"CLEAR","HIRED");
    event(q2,"RESPONDED",now.minus(5,ChronoUnit.DAYS).plus(2,ChronoUnit.HOURS));event(q2,"HIRED",now.minus(4,ChronoUnit.DAYS));
    inquiry(p,a,"private-client@two.test",now.minus(4,ChronoUnit.DAYS),"CLEAR","INQUIRY_RECEIVED");
    inquiry(archivedB,b,"private-client@three.test",now.minus(12,ChronoUnit.HOURS),"CLEAR","INQUIRY_RECEIVED");
    UUID restricted=inquiry(p,a,"private-client@blocked.test",first,"BLOCKED","COMPLETED");
    event(restricted,"HIRED",first.plus(1,ChronoUnit.DAYS));event(restricted,"COMPLETED",first.plus(2,ChronoUnit.DAYS));
    inquiry(p,a,"private-client@old.test",now.minus(90,ChronoUnit.DAYS),"CLEAR","INQUIRY_RECEIVED");
    inquiry(p,a,"private-client@unconfirmed.test",null,"CLEAR","CREATED_UNVERIFIED");
    db.update("INSERT INTO reviews(id,inquiry_id,developer_user_id,rating,review_text,moderation_status) VALUES(?,?,?,5,'Private review content','PUBLISHED')",UUID.randomUUID(),q1,a);
    db.update("INSERT INTO reviews(id,inquiry_id,developer_user_id,rating,review_text,moderation_status) VALUES(?,?,?,5,'Restricted review','PUBLISHED')",UUID.randomUUID(),restricted,a);
    observation("product_activated","server",p,"{}",first);
    observation("product_activated","server",p,"{}",now.minus(2,ChronoUnit.DAYS));
    observation("product_archived","server",p,"{}",now.minus(3,ChronoUnit.DAYS));
    observation("product_activated","web",p,"{}",first);
    observation("product_archived","server",p,"{}",now.minus(90,ChronoUnit.DAYS));
    JsonNode m=metrics();
    assertEquals(2,m.at("/supply/approvedBuilders").asInt());assertEquals(1,m.at("/supply/activeBuilders").asInt());assertEquals(2,m.at("/supply/activePublicShowcases").asInt());
    assertEquals(50,m.at("/supply/liveDemoPercent").asDouble());assertEquals(50,m.at("/supply/videoPercent").asDouble());assertEquals(1,m.at("/supply/imageShowcases").asInt());
    assertEquals(4,m.at("/outcomes/qualifiedInquiries").asInt());assertEquals(3,m.at("/outcomes/responseEligibleInquiries").asInt());assertEquals(2,m.at("/outcomes/respondedEligibleInquiries").asInt());
    assertEquals(66.67,m.at("/outcomes/responseRatePercent").asDouble(),0.01);assertEquals(3,m.at("/outcomes/medianResponseHours").asDouble(),0.01);
    assertEquals(2,m.at("/outcomes/confirmedHires").asInt());assertEquals(1,m.at("/outcomes/confirmedCompletions").asInt());assertEquals(50,m.at("/outcomes/inquiryToHirePercent").asDouble());assertEquals(50,m.at("/outcomes/hireToCompletionPercent").asDouble());
    assertEquals(3,m.at("/outcomes/uniqueClients").asInt());assertEquals(1,m.at("/outcomes/repeatClients").asInt());assertEquals(1,m.at("/outcomes/repeatHiredClients").asInt());assertEquals(1,m.at("/outcomes/verifiedReviews").asInt());
    assertEquals(2,m.at("/outcomes/leadRecipients").asInt());assertEquals(1,m.at("/outcomes/topBuilderCount").asInt());assertEquals(3,m.at("/outcomes/topBuilderLeadCount").asInt());assertEquals(75,m.at("/outcomes/top10LeadSharePercent").asDouble());
    assertEquals(12,m.at("/slots/totalCapacity").asInt());assertEquals(4,m.at("/slots/activeUsage").asInt());assertEquals(8,m.at("/slots/availableCapacity").asInt());assertEquals(33.33,m.at("/slots/utilisationPercent").asDouble(),0.01);
    assertEquals(2,m.at("/slots/activationEvents").asInt());assertEquals(1,m.at("/slots/archiveEvents").asInt());assertEquals(1,m.at("/slots/repeatActivationProducts").asInt());
    assertEquals(2,m.get("portfolioCohorts").size());assertEquals(1,m.get("portfolioCohorts").get(0).get("activeShowcaseCohort").asInt());assertEquals(3,m.get("portfolioCohorts").get(1).get("activeShowcaseCohort").asInt());
  }

  @Test void consentedSearchSamplesIgnoreMalformedCountsAndKeepNoSearchText() throws Exception {
    Instant at=Instant.now().minus(1,ChronoUnit.DAYS);
    for(String context:new String[]{"{\"resultCount\":0}","{\"resultCount\":3}","{\"resultCount\":8}","{\"resultCount\":\"3\"}","{\"resultCount\":-1}","{\"resultCount\":3.5}","{\"resultCount\":100001,\"query\":\"Private search text\"}"}) observation("search_performed","web",null,context,at);
    observation("search_performed","server",null,"{\"resultCount\":100}",at);
    observation("search_performed","web",null,"{\"resultCount\":100}",at.minus(90,ChronoUnit.DAYS));
    observation("unavailable_builder_fallback","web",null,"{\"resultCount\":0}",at);
    observation("unavailable_builder_fallback","web",null,"{\"resultCount\":2}",at);
    observation("unavailable_builder_fallback","server",null,"{\"resultCount\":9}",at);
    JsonNode m=metrics();assertFalse(m.toString().contains("Private search text"));
    assertEquals(7,m.at("/search/receivedEvents").asInt());assertEquals(3,m.at("/search/sampleSearches").asInt());assertEquals(2,m.at("/search/searchesWithThreeResults").asInt());assertEquals(1,m.at("/search/noResultSearches").asInt());
    assertEquals(66.67,m.at("/search/threeResultPercent").asDouble(),0.01);assertEquals(33.33,m.at("/search/noResultPercent").asDouble(),0.01);
    assertEquals(50,m.at("/search/fallbackCoveragePercent").asDouble());
    assertEquals(2,m.at("/search/fallbackPageSamples").asInt());
  }

  @Test void safetyBreakdownsAndAppealReversalsUseStoredCohorts() throws Exception {
    UUID a=user("report-owner",true),target=UUID.randomUUID();
    String[] reasons={"MALICIOUS_LINK","COPYRIGHT_IP","SPAM"},severities={"CRITICAL","HIGH","MEDIUM"};
    for(int i=0;i<3;i++) db.update("INSERT INTO reports(id,target_type,target_id,reason,detail,status,severity) VALUES(?,'PRODUCT',?,?,'Confidential evidence',?,?)",UUID.randomUUID(),target,reasons[i],i==1?"OPEN":"RESOLVED",severities[i]);
    db.update("INSERT INTO reports(id,target_type,target_id,reason,detail,status,severity,created_at) VALUES(?,'PRODUCT',?,'SPAM','Old report','OPEN','LOW',now()-interval '90 days')",UUID.randomUUID(),target);
    String[] actions={"suspend","hide","RESOLVE"},states={"OVERTURNED","UPHELD","OPEN"};
    for(int i=0;i<3;i++) {
      UUID decision=UUID.randomUUID();
      db.update("INSERT INTO moderation_actions(id,admin_id,target_type,target_id,action,reason) VALUES(?,?,'PRODUCT',?,?,'Reasoned decision')",decision,admin,target,actions[i]);
      db.update("INSERT INTO moderation_appeals(id,decision_id,appellant_id,statement,status) VALUES(?,?,?,'Requested review of evidence',?)",UUID.randomUUID(),decision,a,states[i]);
    }
    db.update("UPDATE reports SET enforcement_action=CASE WHEN reason='SPAM' THEN 'NONE' ELSE 'SUSPEND' END WHERE status='RESOLVED'");
    JsonNode m=metrics();
    assertEquals(3,m.at("/safety/reports").asInt());assertEquals(2,m.at("/safety/resolvedReports").asInt());assertEquals(1,m.at("/safety/openReports").asInt());assertEquals(66.67,m.at("/safety/resolutionRatePercent").asDouble(),0.01);
    assertEquals(1,m.at("/safety/maliciousLinkReports").asInt());assertEquals(1,m.at("/safety/ipComplaints").asInt());assertEquals(1,m.at("/safety/spamReports").asInt());
    assertEquals(3,m.at("/safety/appeals").asInt());assertEquals(2,m.at("/safety/decidedAppeals").asInt());assertEquals(1,m.at("/safety/overturnedAppeals").asInt());assertEquals(50,m.at("/safety/reversalRatePercent").asDouble());
    assertEquals(2,m.at("/safety/enforcementActions").asInt());assertEquals(3,m.at("/safety/bySeverity").size());assertEquals(3,m.at("/safety/byReason").size());
    assertEquals("CRITICAL",m.at("/safety/bySeverity/0/severity").asText());assertEquals(50,m.at("/safety/reportActionRatePercent").asDouble());
    assertFalse(m.toString().contains("Confidential evidence"));
  }
  @Test void concurrentSlotCohortsExcludeLegacyAccountsAndWebSpoofing() throws Exception {
    UUID fresh=user("fresh-capacity",true),legacy=user("legacy-capacity",true);
    Instant start=Instant.now().minus(3,ChronoUnit.DAYS);
    db.update("UPDATE analytics_instrumentation SET started_at=? WHERE name='showcase_capacity'",Timestamp.from(start));
    db.update("UPDATE users SET created_at=? WHERE id=?",Timestamp.from(start.plus(1,ChronoUnit.DAYS)),fresh);
    db.update("UPDATE users SET created_at=? WHERE id=?",Timestamp.from(start.minus(1,ChronoUnit.DAYS)),legacy);
    for(UUID id:new UUID[]{fresh,legacy}) observation("product_activated","server",UUID.randomUUID(),"{\"builderId\":\""+id+"\",\"activeCount\":3}",start.plus(2,ChronoUnit.DAYS));
    observation("showcase_capacity_blocked","server",UUID.randomUUID(),"{\"builderId\":\""+fresh+"\"}",Instant.now());
    observation("showcase_capacity_blocked","web",UUID.randomUUID(),"{\"builderId\":\""+legacy+"\"}",Instant.now());
    JsonNode m=metrics();
    assertEquals(1,m.at("/slots/timeToThreeBuilders").asInt());
    assertEquals(1,m.at("/slots/timeToThreeActiveShowcasesDays").asDouble(),0.01);
    assertEquals(1,m.at("/slots/blockedActivationBuilders").asInt());
  }

}
