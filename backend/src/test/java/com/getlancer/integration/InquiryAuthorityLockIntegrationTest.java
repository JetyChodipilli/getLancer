package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@ContextConfiguration(initializers = TestDatabaseGuard.class)
@SpringBootTest(properties = {
    "app.environment=local", "app.jobs-enabled=false", "app.admin-password=", "app.admin-totp=",
    "spring.config.import=",
    "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}",
    "spring.datasource.username=${TEST_DB_USERNAME:postgres}",
    "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.hikari.schema=getlancer_test", "spring.flyway.default-schema=getlancer_test",
    "spring.flyway.schemas=getlancer_test", "app.origin=http://localhost:3000",
    "app.secure-cookie=false", "app.storage.access-key=", "app.storage.secret-key="
})
@AutoConfigureMockMvc
class InquiryAuthorityLockIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  UUID builder, client, product, inquiry;

  UUID user(String token) {
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",
        id, token + "@example.test");
    // Builders may also be clients through their verified email, without a CLIENT role.
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER')", id);
    db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,?,'APPROVED')",
        id, token, token);
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",
        Support.hash(token), id);
    return id;
  }

  @BeforeEach
  void prepare() {
    cleanup();
    builder = user("inquiry-authority-builder");
    client = user("inquiry-authority-client");
    product = UUID.randomUUID();
    inquiry = UUID.randomUUID();
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,approval_status,lifecycle_status,rights_confirmed)"
        + " VALUES(?,?,?,'Inquiry authority project','Working public evidence','Controlled inquiry authority regression project.','SAAS','Developer tools','Java','Built this test project','APPROVED','ACTIVE',true)",
        product, builder, product.toString());
    assertEquals(0, db.queryForObject("SELECT count(*) FROM user_roles WHERE user_id=? AND role='CLIENT'",
        Integer.class, client));
  }

  @AfterEach
  void cleanup() {
    db.execute("TRUNCATE users CASCADE");
    db.execute("TRUNCATE rate_buckets,analytics_events,email_outbox");
  }

  void inquiry(String status, String tokenKind) {
    db.update("INSERT INTO inquiries(id,reference_product_id,developer_user_id,client_email,client_name,request_type,description,budget_band,timeline_band,current_status,email_confirmed_at,idempotency_key,request_hash,updated_at)"
        + " VALUES(?,?,?,'inquiry-authority-client@example.test','Client','SIMILAR_BUILD','Controlled private engagement requirements','USD_3K_10K','ONE_TO_THREE_MONTHS',?,now()-interval '2 days',?,'test',now()-interval '1 day')",
        inquiry, product, builder, status, UUID.randomUUID());
    db.update("INSERT INTO inquiry_events(id,inquiry_id,event_type,actor_type,actor_id) VALUES(?,?,?,?,?)",
        UUID.randomUUID(), inquiry, status, status.equals("COMPLETED") ? "CLIENT" : "DEVELOPER",
        status.equals("COMPLETED") ? client : builder);
    db.update("INSERT INTO account_tokens(id,token_hash,kind,inquiry_id,email,expires_at)"
        + " VALUES(?,?,?,?,'inquiry-authority-client@example.test',now()+interval '1 day')",
        UUID.randomUUID(), Support.hash(UUID.randomUUID().toString()), tokenKind, inquiry);
  }

  MockHttpServletRequestBuilder body(String path, String actor, String json) {
    return post("/api/v1" + path).cookie(new Cookie("gl_session", actor))
        .header("Origin", "http://localhost:3000").header("X-Requested-With", "getlancer")
        .contentType("application/json").content(json);
  }

  void revokeDuringInquiryLockWait(MockHttpServletRequestBuilder call, Runnable revoke,
      int expectedStatus, String expectedCode) throws Exception {
    var executor = Executors.newSingleThreadExecutor();
    try (var holder = db.getDataSource().getConnection()) {
      holder.setAutoCommit(false);
      int holderPid;
      try (var statement = holder.createStatement();
          var result = statement.executeQuery("SELECT pg_backend_pid()")) {
        assertTrue(result.next());
        holderPid = result.getInt(1);
      }
      try (var statement = holder.prepareStatement("SELECT id FROM inquiries WHERE id=? FOR UPDATE")) {
        statement.setObject(1, inquiry);
        try (var result = statement.executeQuery()) {
          assertTrue(result.next(), "The inquiry must exist before the request starts");
        }
      }
      var pending = executor.submit(() -> mvc.perform(call).andReturn());
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      boolean waiting = false;
      while (System.nanoTime() < deadline && !pending.isDone()) {
        if (db.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock'"
                + " AND ? = ANY(pg_blocking_pids(pid))"
                + " AND query LIKE '%FROM inquiries WHERE id=%FOR UPDATE%'",
                Integer.class, holderPid) > 0) {
          waiting = true;
          break;
        }
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
      }
      assertTrue(waiting, "Revocation must follow the actual PostgreSQL inquiry row lock wait");
      revoke.run();
      holder.commit();
      var result = pending.get(10, TimeUnit.SECONDS);
      assertEquals(expectedStatus, result.getResponse().getStatus(), result.getResponse().getContentAsString());
      jsonPath("$.error.code").value(expectedCode).match(result);
    } finally {
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
  }

  Map<String, Object> effects() {
    return Map.of(
        "inquiry", db.queryForMap("SELECT * FROM inquiries WHERE id=?", inquiry),
        "events", db.queryForList("SELECT * FROM inquiry_events WHERE inquiry_id=? ORDER BY id", inquiry),
        "tokens", db.queryForList("SELECT * FROM account_tokens WHERE inquiry_id=? ORDER BY id", inquiry),
        "reviews", db.queryForList("SELECT * FROM reviews WHERE inquiry_id=? ORDER BY id", inquiry),
        "analytics", db.queryForList("SELECT * FROM analytics_events WHERE entity_id=? ORDER BY id", inquiry),
        "notifications", db.queryForList("SELECT * FROM notifications ORDER BY id"),
        "email", db.queryForList("SELECT * FROM email_outbox ORDER BY id"));
  }

  void assertUnchanged(Map<String, Object> before) {
    var after = effects();
    before.forEach((kind, expected) -> assertEquals(expected, after.get(kind),
        kind + " must remain unchanged after authority revocation"));
  }

  @Test
  void confirmHireRejectsSessionRevokedDuringInquiryLockWait() throws Exception {
    inquiry("HIRE_PENDING_CONFIRMATION", "HIRE_CONFIRMATION");
    var before = effects();
    revokeDuringInquiryLockWait(
        body("/inquiries/" + inquiry + "/confirm-hire", "inquiry-authority-client", "{}"),
        () -> db.update("DELETE FROM sessions WHERE user_id=?", client), 401, "UNAUTHENTICATED");
    assertEquals(0, db.queryForObject("SELECT count(*) FROM sessions WHERE user_id=?", Integer.class, client));
    assertUnchanged(before);
  }

  @Test
  void reviewRejectsChangedVerifiedEmailDuringInquiryLockWait() throws Exception {
    inquiry("COMPLETED", "REVIEW");
    var before = effects();
    revokeDuringInquiryLockWait(
        body("/inquiries/" + inquiry + "/review", "inquiry-authority-client",
            "{\"rating\":5,\"reviewText\":\"The agreed project was delivered successfully.\",\"visibility\":\"NAMED\"}"),
        () -> db.update("UPDATE users SET email='inquiry-authority-renamed@example.test' WHERE id=?", client),
        403, "FORBIDDEN");
    assertEquals("inquiry-authority-renamed@example.test",
        db.queryForObject("SELECT email FROM users WHERE id=?", String.class, client));
    assertNotNull(db.queryForObject("SELECT email_verified_at FROM users WHERE id=?", Object.class, client));
    assertUnchanged(before);
  }

  @Test
  void sellerTransitionRejectsDeveloperRoleRevokedDuringInquiryLockWait() throws Exception {
    inquiry("PROPOSAL_SENT", "HIRE_CONFIRMATION");
    var before = effects();
    revokeDuringInquiryLockWait(
        body("/inquiries/" + inquiry + "/request-hire-confirmation", "inquiry-authority-builder", "{}"),
        () -> db.update("DELETE FROM user_roles WHERE user_id=? AND role='DEVELOPER'", builder),
        403, "FORBIDDEN");
    assertEquals(0, db.queryForObject("SELECT count(*) FROM user_roles WHERE user_id=? AND role='DEVELOPER'",
        Integer.class, builder));
    assertUnchanged(before);
  }
}
