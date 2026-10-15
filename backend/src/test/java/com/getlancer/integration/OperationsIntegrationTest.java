package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.jobs.Maintenance;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@org.springframework.test.context.ContextConfiguration(initializers=TestDatabaseGuard.class)
@SpringBootTest(properties={"app.environment=local","app.jobs-enabled=false","app.analytics-enabled=true","app.analytics-salt=operations-test-only", "app.admin-password=","app.admin-totp=",
  "spring.config.import=","spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}","spring.datasource.username=${TEST_DB_USERNAME:postgres}","spring.datasource.password=${TEST_DB_PASSWORD:}",
  "spring.datasource.hikari.schema=getlancer_test","spring.flyway.default-schema=getlancer_test","spring.flyway.schemas=getlancer_test","app.origin=http://localhost:3000","app.secure-cookie=false","app.storage.access-key=","app.storage.secret-key="})
@AutoConfigureMockMvc
class OperationsIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  UUID admin;
  UUID user(String token,boolean administrator) {
    UUID id=UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",id,token+"@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT')",id);
    if(administrator)db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')",id);
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at,mfa_verified,issued_at) VALUES(?,?,now()+interval '1 hour',?,now())",Support.hash(token),id,administrator);
    return id;
  }
  @BeforeEach void seed() {
    db.execute("TRUNCATE users CASCADE");
    db.execute("TRUNCATE analytics_events,analytics_daily_counts,rate_buckets");
    db.update("UPDATE lab_runtime_settings SET paused=false,pause_reason='',observed_epoch=NULL,worker_observed_at=NULL WHERE id=1");
    admin=user("operations-admin",true);
    user("operations-member",false);
  }
  JsonNode snapshot()throws Exception {
    String raw=mvc.perform(get("/api/v1/admin/operations").cookie(new Cookie("gl_session","operations-admin")))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertFalse(raw.contains("operations-admin@example.test"));
    assertFalse(raw.contains("Secret search input"));
    return json.readTree(raw);
  }
  org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder observation(Map<String,Object> body)throws Exception {
    return post("/api/v1/analytics/events").header("Origin","http://localhost:3000").header("X-Requested-With","getlancer").contentType("application/json").content(json.writeValueAsString(body));
  }
  @Test void freshMfaRequiredAndEmptyCohortsDoNotInventPerformanceOrReadiness()throws Exception {
    mvc.perform(get("/api/v1/admin/operations")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/admin/operations").cookie(new Cookie("gl_session","operations-member"))).andExpect(status().isForbidden());
    db.update("UPDATE sessions SET issued_at=now()-interval '16 minutes' WHERE user_id=?",admin);
    mvc.perform(get("/api/v1/admin/operations").cookie(new Cookie("gl_session","operations-admin"))).andExpect(status().isForbidden());
    db.update("UPDATE sessions SET issued_at=now(),mfa_verified=false WHERE user_id=?",admin);
    mvc.perform(get("/api/v1/admin/operations").cookie(new Cookie("gl_session","operations-admin"))).andExpect(status().isForbidden());
    db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?",admin);
    var result=snapshot();
    assertEquals(30,result.at("/window/days").asInt());
    assertEquals(0,result.at("/runtime/admitted").asInt());
    assertTrue(result.at("/runtime/infraSuccessPercent").isNull());
    assertTrue(result.at("/runtime/coveragePercent").isNull());
    assertFalse(result.at("/readiness/runtime/enabled").asBoolean());
    assertFalse(result.at("/readiness/commerce/enabled").asBoolean());
    assertFalse(result.at("/readiness/productionCertified").asBoolean());
    assertEquals(50_000_000,result.at("/resources/dailyBudgetMicros").asLong());
    assertTrue(result.get("funnels").get(0).get("ratePercent").isNull());
  }
  @Test void consentIdentityArtifactAuthorityAndFixedPropertiesPreventForgedLiveEvents()throws Exception {
    UUID event=UUID.randomUUID();
    mvc.perform(observation(Map.of("eventName","home_view","eventId",event))).andExpect(status().isOk()).andExpect(jsonPath("$.recorded").value(false));
    assertEquals(0,db.queryForObject("SELECT count(*) FROM analytics_events",Integer.class));
    var body=Map.<String,Object>of("consent",true,"eventName","component_source_view","artifactSlug","quiet-sign-in","eventId",event,"sessionId",UUID.randomUUID(),
        "properties",Map.of("mode","LIVE","version","forged-version","sourcePage","other"));
    mvc.perform(observation(body)).andExpect(status().isOk()).andExpect(jsonPath("$.recorded").value(true));
    mvc.perform(observation(body)).andExpect(status().isOk()).andExpect(jsonPath("$.recorded").value(false));
    assertEquals(1,db.queryForObject("SELECT count(*) FROM analytics_events",Integer.class));
    assertEquals("BROWSER_LOCAL",db.queryForObject("SELECT context->>'mode' FROM analytics_events",String.class));
    assertTrue(db.queryForObject("SELECT context->>'version' FROM analytics_events",String.class).matches("[a-f0-9]{64}"));
    assertEquals(1,db.queryForObject("SELECT observations FROM analytics_daily_counts",Long.class));
    for(String mode:new String[]{"REPLAY","ILLUSTRATIVE"})mvc.perform(observation(Map.of("consent",true,"eventName","home_view","eventId",UUID.randomUUID(),"properties",Map.of("mode",mode))))
        .andExpect(status().isBadRequest());
    mvc.perform(observation(Map.of("consent",true,"eventName","component_view","artifactSlug","does-not-exist","eventId",UUID.randomUUID()))).andExpect(status().isNotFound());
    mvc.perform(observation(Map.of("consent",true,"eventName","purchase_captured","eventId",UUID.randomUUID()))).andExpect(status().isBadRequest());
    mvc.perform(observation(Map.of("consent",true,"eventName","home_view","eventId",UUID.randomUUID(),"properties",Map.of("query","Secret search input")))).andExpect(status().isBadRequest());
    assertEquals(1,db.queryForObject("SELECT count(*) FROM analytics_events",Integer.class));
    var source=java.util.stream.StreamSupport.stream(snapshot().get("funnels").spliterator(),false).filter(row->row.get("stage").asText().equals("component_source_view")).findFirst().orElseThrow();
    assertEquals("BROWSER_LOCAL",source.get("mode").asText());
    assertEquals(1,source.get("denominator").asInt());
    assertEquals(100,source.get("ratePercent").asDouble());
    assertTrue(source.get("denominatorLabel").asText().contains("not visitor conversion"));
  }
  @Test void shortRawRetentionPreservesUnlinkedCountsAndCannotBeExtendedByEnvironment()throws Exception {
    UUID old=UUID.randomUUID();
    db.update("INSERT INTO analytics_events(id,event_name,source,created_at,session_hash,context) VALUES(?,'home_view','web',now()-interval '25 hours',?,'{}'::jsonb)",old,"f".repeat(64));
    db.update("INSERT INTO analytics_events(id,event_name,source,created_at) VALUES(?,'product_activated','server',now()-interval '25 hours')",UUID.randomUUID());
    db.update("INSERT INTO analytics_daily_counts(day,stage,mode,version,observations) VALUES((now() AT TIME ZONE 'UTC')::date-1,'home_view','ALL','catalogue',7),((now() AT TIME ZONE 'UTC')::date-91,'home_view','ALL','catalogue',99)");
    new Maintenance(db,90,true).expire();
    assertEquals(0,db.queryForObject("SELECT count(*) FROM analytics_events WHERE source='web'",Integer.class));
    assertEquals(1,db.queryForObject("SELECT count(*) FROM analytics_events WHERE source='server'",Integer.class));
    assertEquals(7,db.queryForObject("SELECT sum(observations) FROM analytics_daily_counts",Long.class));
    assertEquals(0,db.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='analytics_daily_counts' AND column_name IN ('entity_id','actor_id','session_hash','context','inputs')",Integer.class));
  }
  @Test void publicCatalogueApplicationTimingIsMeasuredButPrivateResponsesHaveNoTimingHeader()throws Exception {
    String header=mvc.perform(get("/api/v1/components")).andExpect(status().isOk()).andReturn().getResponse().getHeader("Server-Timing");
    assertNotNull(header);
    assertTrue(header.matches("app;dur=[0-9]+\\.[0-9]{3}"));
    mvc.perform(get("/api/v1/admin/operations").cookie(new Cookie("gl_session","operations-admin"))).andExpect(status().isOk()).andExpect(header().doesNotExist("Server-Timing"));
  }
}
