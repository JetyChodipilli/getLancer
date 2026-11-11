package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
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

@ContextConfiguration(initializers = TestDatabaseGuard.class)
@SpringBootTest(properties = {
    "app.environment=local", "app.jobs-enabled=false", "app.admin-password=", "app.admin-totp=",
    "spring.config.import=", "app.secure-cookie=false", "app.origin=http://localhost:3000",
    "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}",
    "spring.datasource.username=${TEST_DB_USERNAME:postgres}", "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.hikari.schema=getlancer_test", "spring.flyway.default-schema=getlancer_test",
    "spring.flyway.schemas=getlancer_test", "app.storage.access-key=", "app.storage.secret-key="
})
@AutoConfigureMockMvc
class PrivateAccessPaginationIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  UUID owner, outsider, product;

  UUID user(String name, boolean developer) {
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())", id, name + "@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT')", id);
    if (developer) {
      db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER')", id);
      db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,?,'APPROVED')", id, name, name);
    }
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')", Support.hash(name), id);
    return id;
  }

  @BeforeEach void seed() {
    clear(); owner = user("grant-owner", true); outsider = user("grant-outsider", true); user("client054", false);
    product = UUID.randomUUID();
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,visibility,approval_status,lifecycle_status)"
        + " VALUES(?,?,'private-continuation','Private fixture','Private fixture summary','Working private project','SAAS','CRM','Java','Built this project','PRIVATE_CASE_STUDY','APPROVED','ACTIVE')", product, owner);
    // All fifty-five grants are current; this boundary does not rely on expired rows surviving maintenance.
    for (int index = 0; index < 55; index++) db.update("INSERT INTO product_access_grants(product_id,client_email,expires_at) VALUES(?,?,now()+interval '7 days')", product, String.format("client%03d@example.test", index));
  }

  @AfterEach void clear() { db.execute("TRUNCATE users CASCADE"); db.execute("TRUNCATE rate_buckets"); }
  String access() { return "/api/v1/developer/products/" + product + "/access"; }
  MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) { return request.cookie(new Cookie("gl_session", token)); }
  MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, String token, String value) {
    return as(request, token).header("Origin", "http://localhost:3000").header("X-Requested-With", "getlancer").contentType("application/json").content(value);
  }
  JsonNode ok(MockHttpServletRequestBuilder request) throws Exception { return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); }

  void revokedOwnerAfterProductLock(boolean removeRole) throws Exception {
    var executor = Executors.newSingleThreadExecutor();
    try (var holder = db.getDataSource().getConnection()) {
      holder.setAutoCommit(false);
      int pid;
      try (var statement = holder.createStatement(); var row = statement.executeQuery("SELECT pg_backend_pid()")) {
        assertTrue(row.next()); pid = row.getInt(1);
      }
      try (var statement = holder.prepareStatement("SELECT id FROM products WHERE id=? FOR UPDATE")) {
        statement.setObject(1, product); try (var row = statement.executeQuery()) { assertTrue(row.next()); }
      }
      var pending = executor.submit(() -> mvc.perform(body(post(access()), "grant-owner", "{\"email\":\"new-client@example.test\"}"))
          .andReturn().getResponse().getStatus());
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5); boolean waiting = false;
      while (System.nanoTime() < deadline) {
        waiting = db.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE ?=ANY(pg_blocking_pids(pid))", Integer.class, pid) > 0;
        if (waiting) break;
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
      }
      assertTrue(waiting, "The real product lock wait must precede owner revocation.");
      if (removeRole) db.update("DELETE FROM user_roles WHERE user_id=? AND role='DEVELOPER'", owner);
      else db.update("DELETE FROM sessions WHERE token_hash=?", Support.hash("grant-owner"));
      holder.commit();
      assertEquals(removeRole ? 403 : 401, pending.get(10, TimeUnit.SECONDS));
      assertEquals(0, db.queryForObject("SELECT count(*) FROM product_access_grants WHERE product_id=? AND client_email='new-client@example.test'", Integer.class, product));
      assertEquals(55, db.queryForObject("SELECT count(*) FROM product_access_grants WHERE product_id=?", Integer.class, product));
    } finally { executor.shutdownNow(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); }
  }

  @Test void sessionRevokedDuringProductLockWaitCannotGrantPrivateAccess() throws Exception {
    revokedOwnerAfterProductLock(false);
  }

  @Test void developerRoleRemovedDuringProductLockWaitCannotGrantPrivateAccess() throws Exception {
    revokedOwnerAfterProductLock(true);
  }

  @Test void ownerCanContinueCurrentGrantsAndRevokeTheClientOnPageTwo() throws Exception {
    JsonNode first = ok(as(get(access()), "grant-owner"));
    assertEquals(50, first.path("items").size()); assertTrue(first.path("hasMore").asBoolean()); assertEquals(50, first.path("size").asInt());
    assertEquals("client000@example.test", first.path("items").get(0).path("client_email").asText());
    JsonNode second = ok(as(get(access()).param("page", "1"), "grant-owner"));
    assertEquals(5, second.path("items").size()); assertEquals(1, second.path("page").asInt()); assertFalse(second.path("hasMore").asBoolean()); assertEquals(55, second.path("totalItems").asInt());
    Set<String> identities = new HashSet<>();
    for (JsonNode page : new JsonNode[] {first, second}) for (JsonNode grant : page.path("items")) {
      assertTrue(identities.add(grant.path("client_email").asText()));
      Set<String> fields = new HashSet<>(); grant.fieldNames().forEachRemaining(fields::add); assertEquals(Set.of("client_email", "expires_at"), fields);
    }
    assertEquals("client054@example.test", second.path("items").get(4).path("client_email").asText());
    mvc.perform(as(get("/api/v1/private/products/" + product), "client054")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/products/private-continuation")).andExpect(status().isNotFound());
    JsonNode publicProjects = ok(get("/api/v1/products"));
    assertFalse(publicProjects.toString().contains("client050@example.test")); assertFalse(publicProjects.toString().contains("client_email"));
    mvc.perform(body(delete(access()), "grant-owner", "{\"email\":\"client054@example.test\"}")).andExpect(status().isOk());
    assertEquals(0, db.queryForObject("SELECT count(*) FROM product_access_grants WHERE product_id=? AND client_email='client054@example.test'", Integer.class, product));
    mvc.perform(as(get("/api/v1/private/products/" + product), "client054")).andExpect(status().isNotFound());
    JsonNode remaining = ok(as(get(access()).param("page", "1"), "grant-owner")); assertEquals(4, remaining.path("items").size()); assertFalse(remaining.toString().contains("client054@example.test"));
  }

  @Test void everyPageAndRevokeRequireTheCurrentApprovedOwner() throws Exception {
    ok(as(get(access()), "grant-owner"));
    mvc.perform(get(access()).param("page", "1")).andExpect(status().isUnauthorized());
    mvc.perform(as(get(access()).param("page", "1"), "grant-outsider")).andExpect(status().isNotFound());
    mvc.perform(body(delete(access()), "grant-outsider", "{\"email\":\"client054@example.test\"}")).andExpect(status().isNotFound());
    db.update("UPDATE products SET owner_user_id=? WHERE id=?", outsider, product);
    mvc.perform(as(get(access()).param("page", "1"), "grant-owner")).andExpect(status().isNotFound());
    mvc.perform(body(delete(access()), "grant-owner", "{\"email\":\"client054@example.test\"}")).andExpect(status().isNotFound());
    assertEquals(5, ok(as(get(access()).param("page", "1"), "grant-outsider")).path("items").size());
    db.update("UPDATE developer_profiles SET approval_status='SUSPENDED' WHERE user_id=?", outsider);
    mvc.perform(as(get(access()).param("page", "1"), "grant-outsider")).andExpect(status().isForbidden());
    assertEquals(55, db.queryForObject("SELECT count(*) FROM product_access_grants WHERE product_id=?", Integer.class, product));
  }
}
