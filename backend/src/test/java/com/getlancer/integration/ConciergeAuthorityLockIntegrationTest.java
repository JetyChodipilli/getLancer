package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.util.Map;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
class ConciergeAuthorityLockIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  UUID owner, builder, operator, business, brief, concierge;

  private UUID user(String token) {
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",
        id, token + "@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT'),(?,'DEVELOPER')", id, id);
    db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,?,'APPROVED')",
        id, token, token);
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",
        Support.hash(token), id);
    return id;
  }

  @BeforeEach
  void prepare() {
    db.execute("TRUNCATE users CASCADE");
    db.execute("TRUNCATE rate_buckets");
    owner = user("concierge-authority-owner");
    builder = user("concierge-authority-builder");
    operator = user("concierge-authority-admin");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')", operator);
    db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?", operator);
    UUID product = UUID.randomUUID();
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,approval_status,lifecycle_status,visibility,available_for_similar_work)"
        + " VALUES(?,?,?,'Portal proof','Public portal evidence','A working customer approval portal','SAAS','CRM','React','Built the portal','APPROVED','ACTIVE','PUBLIC',true)",
        product, builder, product.toString());
    business = UUID.randomUUID();
    brief = UUID.randomUUID();
    concierge = UUID.randomUUID();
    db.update("INSERT INTO businesses(id,name,summary,owner_id) VALUES(?,'Authority business','Private candidate sourcing',?)",
        business, owner);
    db.update("INSERT INTO business_members(business_id,user_id,role) VALUES(?,?,'OWNER')", business, owner);
    db.update("INSERT INTO business_requests(id,business_id,created_by,title,description,category,technology,budget,timeline,status)"
        + " VALUES(?,?,?,'Private portal','A private customer approval portal','CRM','React','10000','One month','OPEN')",
        brief, business, owner);
    db.update("INSERT INTO concierge_requests(id,request_id,requested_by) VALUES(?,?,?)", concierge, brief, owner);
  }

  @AfterEach
  void clear() {
    db.execute("TRUNCATE users CASCADE");
    db.execute("TRUNCATE rate_buckets");
  }

  private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder call, String body) {
    return call.cookie(new Cookie("gl_session", "concierge-authority-admin"))
        .header("Origin", "http://localhost:3000").header("X-Requested-With", "getlancer")
        .contentType("application/json").content(body);
  }

  private Map<String, Object> effects() {
    return Map.of(
        "business", db.queryForMap("SELECT * FROM businesses WHERE id=?", business),
        "brief", db.queryForMap("SELECT * FROM business_requests WHERE id=?", brief),
        "concierge", db.queryForMap("SELECT * FROM concierge_requests WHERE id=?", concierge),
        "shortlist", db.queryForList("SELECT * FROM request_shortlist WHERE request_id=? ORDER BY id", brief),
        "audit", db.queryForList("SELECT * FROM business_activity WHERE business_id=? ORDER BY id", business));
  }

  private void revokeAfterLockWait(String lockSql, UUID key, String waitingQuery,
      MockHttpServletRequestBuilder call, Runnable revoke, int expectedStatus, String expectedCode)
      throws Exception {
    var before = effects();
    var executor = Executors.newSingleThreadExecutor();
    try (var holder = db.getDataSource().getConnection()) {
      holder.setAutoCommit(false);
      int holderPid;
      try (var statement = holder.createStatement();
          var result = statement.executeQuery("SELECT pg_backend_pid()")) {
        assertTrue(result.next());
        holderPid = result.getInt(1);
      }
      try (var statement = holder.prepareStatement(lockSql)) {
        statement.setObject(1, key);
        try (var result = statement.executeQuery()) {
          assertTrue(result.next(), "The locked record must exist before the request starts");
        }
      }
      var pending = executor.submit(() -> mvc.perform(call).andReturn());
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      boolean waiting = false;
      while (System.nanoTime() < deadline && !pending.isDone()) {
        if (db.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database()"
                + " AND wait_event_type='Lock' AND ?=ANY(pg_blocking_pids(pid)) AND query LIKE ?",
                Integer.class, holderPid, waitingQuery) > 0) {
          waiting = true;
          break;
        }
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
      }
      assertTrue(waiting, "Authority must be revoked after observing the actual PostgreSQL lock wait");
      revoke.run();
      holder.commit();
      var result = pending.get(10, TimeUnit.SECONDS);
      assertEquals(expectedStatus, result.getResponse().getStatus(), result.getResponse().getContentAsString());
      jsonPath("$.error.code").value(expectedCode).match(result);
      assertEquals(before, effects(), "Revocation must preserve concierge, shortlist, business and audit state");
    } finally {
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
  }

  @Test
  void statusRejectsSessionRevokedDuringBusinessLockWait() throws Exception {
    revokeAfterLockWait("SELECT id FROM businesses WHERE id=? FOR UPDATE", business,
        "%FROM businesses WHERE id=%FOR UPDATE%",
        json(patch("/api/v1/admin/concierge/" + concierge), "{\"status\":\"IN_PROGRESS\"}"),
        () -> db.update("DELETE FROM sessions WHERE user_id=?", operator), 401, "UNAUTHENTICATED");
    assertEquals(0, db.queryForObject("SELECT count(*) FROM sessions WHERE user_id=?", Integer.class, operator));
    assertEquals("REQUESTED", db.queryForObject("SELECT status FROM concierge_requests WHERE id=?", String.class, concierge));
  }

  @Test
  void shortlistRollsBackReasonAndSourceWhenMfaIsRevokedDuringConflictUpdateWait() throws Exception {
    db.update("UPDATE concierge_requests SET status='IN_PROGRESS' WHERE id=?", concierge);
    UUID entry = UUID.randomUUID();
    db.update("INSERT INTO request_shortlist(id,request_id,kind,builder_id,created_by,reason,source)"
        + " VALUES(?,?,'BUILDER',?,?,'Original business reason','BUSINESS')", entry, brief, builder, owner);
    revokeAfterLockWait("SELECT id FROM request_shortlist WHERE id=? FOR UPDATE", entry,
        "%INSERT INTO request_shortlist%ON CONFLICT%DO UPDATE%",
        json(post("/api/v1/admin/concierge/" + concierge + "/shortlist"),
            "{\"kind\":\"BUILDER\",\"targetId\":\"" + builder + "\",\"reason\":\"Reviewed concierge recommendation\"}"),
        () -> db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?", operator), 403, "FORBIDDEN");
    assertFalse(db.queryForObject("SELECT mfa_verified FROM sessions WHERE user_id=?", Boolean.class, operator));
    assertEquals("Original business reason", db.queryForObject("SELECT reason FROM request_shortlist WHERE id=?", String.class, entry));
    assertEquals("BUSINESS", db.queryForObject("SELECT source FROM request_shortlist WHERE id=?", String.class, entry));
    assertEquals(owner, db.queryForObject("SELECT created_by FROM request_shortlist WHERE id=?", UUID.class, entry));
  }
}
