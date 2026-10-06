package com.getlancer.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@ContextConfiguration(initializers = TestDatabaseGuard.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "app.environment=local", "app.jobs-enabled=false", "app.admin-email=operator@example.test",
    "app.admin-password=", "app.admin-totp=", "spring.config.import=", "app.secure-cookie=false",
    "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}",
    "spring.datasource.username=${TEST_DB_USERNAME:postgres}", "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.hikari.schema=getlancer_test", "spring.flyway.default-schema=getlancer_test",
    "spring.flyway.schemas=getlancer_test", "app.origin=http://localhost:3000",
    "app.storage.access-key=", "app.storage.secret-key=", "app.discovery-rate-limit=3"
})
@AutoConfigureMockMvc
class AuthorizationIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @LocalServerPort int port;

  @BeforeEach @AfterEach void clear() {
    db.execute("TRUNCATE users CASCADE"); db.execute("TRUNCATE rate_buckets");
    db.execute("TRUNCATE security_audit_events");
  }

  UUID user(String token, boolean admin, boolean mfa) {
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?, 'unused',now())", id, id + "@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT')", id);
    if (admin) db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')", id);
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at,mfa_verified) VALUES(?,?,now()+interval '1 hour',?)", Support.hash(token), id, mfa);
    return id;
  }

  @Test void missingExpiredSuspendedAndRemovedRolesFailBeforeControllerWork() throws Exception {
    mvc.perform(get("/api/v1/admin/accounts")).andExpect(status().isUnauthorized());
    UUID admin = user("admin", true, false);
    mvc.perform(get("/api/v1/admin/accounts").cookie(new Cookie("gl_session", "admin"))).andExpect(status().isForbidden());
    db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?", admin);
    mvc.perform(get("/api/v1/admin/accounts").cookie(new Cookie("gl_session", "admin"))).andExpect(status().isOk());
    db.execute("TRUNCATE rate_buckets");
    db.update("DELETE FROM user_roles WHERE user_id=? AND role='ADMIN'", admin);
    mvc.perform(get("/api/v1/admin/accounts").cookie(new Cookie("gl_session", "admin"))).andExpect(status().isForbidden());
    db.update("UPDATE users SET account_status='SUSPENDED' WHERE id=?", admin);
    mvc.perform(get("/api/v1/me").cookie(new Cookie("gl_session", "admin"))).andExpect(status().isUnauthorized());
    db.update("UPDATE users SET account_status='ACTIVE' WHERE id=?", admin);
    db.update("UPDATE sessions SET expires_at=now()-interval '1 second' WHERE user_id=?", admin);
    mvc.perform(get("/api/v1/me").cookie(new Cookie("gl_session", "admin"))).andExpect(status().isUnauthorized());
  }

  @Test void unknownReadDenialsHaveBoundedAuditAndStableServerRequestIds() throws Exception {
    for (int i = 0; i < 3; i++) {
      var response = mvc.perform(get("/api/v1/unknown-" + i).header("X-Request-ID", "attacker"))
          .andExpect(status().isUnauthorized()).andReturn().getResponse();
      UUID.fromString(response.getHeader("X-Request-ID"));
      assertFalse(response.getContentAsString().contains("attacker"));
    }
    for (int i = 0; i < 5; i++) mvc.perform(get("/api/v1/another-" + i)).andExpect(status().isTooManyRequests());
    assertEquals(3, db.queryForObject("SELECT count(*) FROM security_audit_events WHERE event='AUTHORIZATION_DENIED'", Integer.class));
    assertEquals(1, db.queryForObject("SELECT count(DISTINCT target) FROM security_audit_events", Integer.class));
  }

  @Test void taxonomyMutationAndDenialAuditHandleTheFiniteRegexRoute() throws Exception {
    user("admin", true, true);
    mvc.perform(post("/api/v1/admin/categories").cookie(new Cookie("gl_session", "admin"))
        .header("Origin", "http://localhost:3000").header("X-Requested-With", "getlancer")
        .contentType("application/json").content("{\"name\":\"Security integration taxonomy\"}"))
        .andExpect(status().isOk());
    assertEquals(1, db.queryForObject("SELECT count(*) FROM security_audit_events WHERE event='ADMIN_ACTION' AND target LIKE '%categories|technologies%'", Integer.class));
    mvc.perform(get("/api/v1/admin/categories")).andExpect(status().isUnauthorized());
    assertEquals(1, db.queryForObject("SELECT count(*) FROM security_audit_events WHERE event='AUTHORIZATION_DENIED' AND target LIKE '%categories|technologies%'", Integer.class));
  }

  @Test void privilegedWritesRequireRecentMfaWhileReadOnlyAdministrationRemainsAvailable() throws Exception {
    UUID admin = user("admin", true, true);
    db.update("UPDATE sessions SET issued_at=now()-interval '16 minutes' WHERE user_id=?", admin);
    mvc.perform(get("/api/v1/admin/accounts").cookie(new Cookie("gl_session", "admin")))
        .andExpect(status().isOk());
    mvc.perform(post("/api/v1/admin/categories").cookie(new Cookie("gl_session", "admin"))
        .header("Origin", "http://localhost:3000").header("X-Requested-With", "getlancer")
        .contentType("application/json").content("{\"name\":\"Stale MFA category\"}"))
        .andExpect(status().isForbidden());
    db.execute("TRUNCATE rate_buckets");
    db.update("UPDATE sessions SET issued_at=now() WHERE user_id=?", admin);
    mvc.perform(post("/api/v1/admin/categories").cookie(new Cookie("gl_session", "admin"))
        .header("Origin", "http://localhost:3000").header("X-Requested-With", "getlancer")
        .contentType("application/json").content("{\"name\":\"Fresh MFA category\"}"))
        .andExpect(status().isOk());
  }

  @Test void browserMutationsAndPreflightRejectSiblingOriginsAndMissingNonSimpleHeader() throws Exception {
    mvc.perform(post("/api/v1/auth/logout").header("Origin", "http://localhost:3000"))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/auth/logout").header("Origin", "http://sibling.localhost:3000")
        .header("X-Requested-With", "getlancer")).andExpect(status().isForbidden());
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/v1/auth/login")
        .header("Origin", "http://localhost:3000").header("Access-Control-Request-Method", "POST")
        .header("Access-Control-Request-Headers", "X-Requested-With,Content-Type"))
        .andExpect(status().isNoContent());
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/v1/admin/accounts")
        .header("Origin", "http://sibling.localhost:3000").header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden());
  }

  @Test void realChunkedHttpBodyCannotBypassTheServletStreamLimit() throws Exception {
    byte[] payload = ("{\"email\":\"client@example.test\",\"password\":\"test-password-long\",\"displayName\":\""
        + "x".repeat(6 * 1024 * 1024) + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
    var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/signup"))
        .timeout(Duration.ofSeconds(20)).header("Origin", "http://localhost:3000")
        .header("X-Requested-With", "getlancer").header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(payload))).build();
    var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(413, response.statusCode(), response.body());
    assertNotNull(response.headers().firstValue("X-Request-ID").orElse(null));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM users", Integer.class));
  }

  @Test void detailedManagementEndpointsAreDeniedAndHealthIsLoopbackOnly() throws Exception {
    mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    mvc.perform(get("/actuator/health").with(request -> { request.setRemoteAddr("192.0.2.4"); return request; }))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }
}
