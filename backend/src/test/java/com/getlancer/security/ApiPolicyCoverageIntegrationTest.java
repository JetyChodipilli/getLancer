package com.getlancer.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.util.EnumSet;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Exercises the actual MVC security chain for every declared protected route. */
@ContextConfiguration(initializers = TestDatabaseGuard.class)
@SpringBootTest(properties = {
    "app.environment=local", "app.jobs-enabled=false", "app.admin-email=operator@example.test",
    "app.admin-password=", "app.admin-totp=", "spring.config.import=", "app.secure-cookie=false",
    "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}",
    "spring.datasource.username=${TEST_DB_USERNAME:postgres}", "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.hikari.schema=getlancer_test", "spring.flyway.default-schema=getlancer_test",
    "spring.flyway.schemas=getlancer_test", "app.origin=http://localhost:3000",
    "app.storage.access-key=", "app.storage.secret-key=", "app.rate-limit=10000",
    "app.discovery-rate-limit=10000"
})
@AutoConfigureMockMvc
class ApiPolicyCoverageIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired AuthorizationService authorization;
  @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;

  @BeforeEach @AfterEach void clear() {
    db.execute("TRUNCATE users CASCADE");
    db.execute("TRUNCATE rate_buckets");
    db.execute("TRUNCATE security_audit_events");
  }

  UUID user(String token, String role, boolean mfa) {
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",
        id, id + "@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,?)", id, role);
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at,mfa_verified) VALUES(?,?,now()+interval '1 hour',?)",
        Support.hash(token), id, mfa);
    return id;
  }

  static String path(AuthorizationService.Route route) {
    return route.path().replaceAll("\\{kind:categories\\|technologies\\}", "categories")
        .replaceAll("\\{[^}]+\\}", "00000000-0000-4000-8000-000000000001");
  }

  void denied(AuthorizationService.Route route, String token, int status) throws Exception {
    var call = request(HttpMethod.valueOf(route.method()), URI.create(path(route)))
        .header("Origin", "http://localhost:3000").header("X-Requested-With", "getlancer");
    if (token != null) call.cookie(new Cookie("gl_session", token));
    var response = mvc.perform(call).andReturn().getResponse();
    assertEquals(status, response.getStatus(), route.method() + " " + route.path());
    assertNotNull(response.getHeader("X-Request-ID"));
  }

  @Test void actualMvcHandlersAndPolicyHaveExactlyTheSameApplicationRoutes() {
    var actual = new TreeSet<String>();
    for (var mapping : mappings.getHandlerMethods().keySet())
      for (String pattern : mapping.getPatternValues())
        if (pattern.startsWith("/api/v1/"))
          for (var method : mapping.getMethodsCondition().getMethods()) actual.add(method + " " + pattern);
    var declared = new TreeSet<String>();
    for (var route : authorization.routes())
      if (route.path().startsWith("/api/v1/")) declared.add(route.method() + " " + route.path());
    assertEquals(declared, actual);
  }

  @Test void allPrivateApisRejectAnonymousExpiredRevokedAndSuspendedSessions() throws Exception {
    UUID expired = user("expired", "CLIENT", true);
    db.update("UPDATE sessions SET expires_at=now()-interval '1 minute' WHERE user_id=?", expired);
    UUID suspended = user("suspended", "CLIENT", true);
    db.update("UPDATE users SET account_status='SUSPENDED' WHERE id=?", suspended);
    UUID revoked = user("revoked", "CLIENT", true);
    db.update("DELETE FROM sessions WHERE user_id=?", revoked);
    var policies = EnumSet.of(AuthorizationService.Policy.SESSION,
        AuthorizationService.Policy.DEVELOPER, AuthorizationService.Policy.ADMIN_MFA);
    for (var route : authorization.routes()) if (policies.contains(route.policy())) {
      for (String token : new String[] {null, "expired", "revoked", "suspended"}) denied(route, token, 401);
    }
  }

  @Test void allElevatedApisRejectOrdinaryUsersAndAdminsWithoutMfa() throws Exception {
    user("client", "CLIENT", false);
    user("admin-no-mfa", "ADMIN", false);
    for (var route : authorization.routes()) {
      if (route.policy() == AuthorizationService.Policy.ADMIN_MFA) {
        denied(route, "client", 403);
        denied(route, "admin-no-mfa", 403);
      } else if (route.policy() == AuthorizationService.Policy.DEVELOPER) denied(route, "client", 403);
      else if (route.policy() == AuthorizationService.Policy.SERVICE) denied(route, "client", 403);
    }
  }

  @Test void everyPrivilegedWriteAndPrivateExportRejectsStaleMfa() throws Exception {
    UUID admin = user("stale-mfa", "ADMIN", true);
    db.update("UPDATE sessions SET issued_at=now()-interval '16 minutes' WHERE user_id=?", admin);
    for (var route : authorization.routes())
      if (AuthorizationService.recentMfaRequired(route)) denied(route, "stale-mfa", 403);
  }
}
