package com.getlancer.accounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.getlancer.auth.AuthService;
import com.getlancer.products.PrivateProjectService;
import com.getlancer.shared.ApiError;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@ContextConfiguration(initializers = TestDatabaseGuard.class)
@SpringBootTest(properties = {
    "app.environment=local", "app.jobs-enabled=false", "app.admin-email=operator@example.test",
    "app.admin-password=", "app.admin-totp=", "spring.config.import=", "app.secure-cookie=false",
    "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}",
    "spring.datasource.username=${TEST_DB_USERNAME:postgres}", "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.hikari.schema=getlancer_test", "spring.flyway.default-schema=getlancer_test",
    "spring.flyway.schemas=getlancer_test", "app.origin=http://localhost:3000",
    "app.storage.access-key=", "app.storage.secret-key=", "app.policies-approved=true"
})
@AutoConfigureMockMvc
class PrivacyIdentityIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired AuthService auth;
  @Autowired PrivacyService privacy;
  @Autowired AccountService accounts;
  UUID closed, builder, product, inquiry;
  final String originalEmail = "former-client@example.test";
  final String guestToken = "synthetic-old-guest-capability";

  @BeforeEach void seed() {
    clear(); closed = user(originalEmail, "DELETED"); builder = user("builder@example.test", "ACTIVE");
    UUID admin = user("operator@example.test", "ACTIVE");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')", admin);
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at,mfa_verified) VALUES(?,?,now()+interval '1 hour',true)", Support.hash("operator-session"), admin);
    db.update("INSERT INTO deletion_requests(user_id) VALUES(?)", closed);
    product = UUID.randomUUID(); inquiry = UUID.randomUUID();
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,visibility,approval_status,lifecycle_status)"
        + " VALUES(?,?,'private-fixture','Private project','Fixture summary','Fixture description','WEB_APP','fixture','Java','Fixture contribution','PRIVATE_CASE_STUDY','APPROVED','ACTIVE')", product, builder);
    db.update("INSERT INTO inquiries(id,reference_product_id,developer_user_id,client_email,client_name,request_type,description,budget_band,timeline_band,idempotency_key,request_hash,current_status,email_confirmed_at)"
        + " VALUES(?,?,?,?,'Former client','SIMILAR_PROJECT','Retained confidential inquiry','DISCUSS','FLEXIBLE',?,?,'INQUIRY_RECEIVED',now())",
        inquiry, product, builder, originalEmail, UUID.randomUUID(), Support.hash("fixture"));
    db.update("INSERT INTO product_access_grants(product_id,client_email,expires_at) VALUES(?,?,now()+interval '1 day')", product, originalEmail);
    for (boolean used : new boolean[] {false, true}) db.update("INSERT INTO account_tokens(id,token_hash,kind,inquiry_id,email,expires_at,used_at)"
        + " VALUES(?,?,'HIRE_CONFIRMATION',?,?,now()+interval '1 hour',CASE WHEN ? THEN now() ELSE NULL END)",
        UUID.randomUUID(), Support.hash(used ? guestToken + "-used" : guestToken), inquiry, originalEmail, used);
  }

  UUID user(String email, String state) {
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at,account_status) VALUES(?,?,'unused',now(),?)", id, email, state);
    db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,'Fixture person','APPROVED')", id, "fixture-" + id);
    return id;
  }

  @AfterEach void clear() { db.execute("TRUNCATE users CASCADE"); db.execute("TRUNCATE rate_buckets"); }

  void anonymize() throws Exception {
    mvc.perform(post("/api/v1/admin/deletion-requests/" + closed + "/review")
        .cookie(new Cookie("gl_session", "operator-session")).header("Origin", "http://localhost:3000")
        .header("X-Requested-With", "getlancer").contentType("application/json")
        .content("{\"action\":\"ANONYMIZE_PROFILE\",\"reason\":\"Approved synthetic retention review\"}"))
        .andExpect(status().isOk());
  }

  MockHttpServletRequest replacement() {
    auth.signup(Map.of("email", originalEmail, "password", "synthetic-password-long", "acceptedTerms", true, "displayName", "New client"));
    UUID id = db.queryForObject("SELECT id FROM users WHERE email=?", UUID.class, originalEmail);
    db.update("UPDATE users SET email_verified_at=now() WHERE id=?", id);
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')", Support.hash("replacement"), id);
    var request = new MockHttpServletRequest(); request.setCookies(new Cookie("gl_session", "replacement"));
    return request;
  }

  void revokedOperatorAfterTargetLock(boolean removeRole) throws Exception {
    revokedOperatorDuringLock("SELECT id FROM users WHERE id=? FOR UPDATE", "%", () -> {
      if (removeRole) db.update("DELETE FROM user_roles WHERE role='ADMIN' AND user_id=(SELECT user_id FROM sessions WHERE token_hash=?)", Support.hash("operator-session"));
      else db.update("DELETE FROM sessions WHERE token_hash=?", Support.hash("operator-session"));
    }, removeRole ? 403 : 401);
  }

  Map<String, Object> retainedPrivacyState() {
    Map<String, Object> state = new java.util.LinkedHashMap<>();
    state.put("user", db.queryForMap("SELECT * FROM users WHERE id=?", closed));
    state.put("profile", db.queryForMap("SELECT * FROM developer_profiles WHERE user_id=?", closed));
    state.put("deletionRequest", db.queryForMap("SELECT * FROM deletion_requests WHERE user_id=?", closed));
    state.put("inquiry", db.queryForMap("SELECT * FROM inquiries WHERE id=?", inquiry));
    state.put("grants", db.queryForList("SELECT * FROM product_access_grants WHERE product_id=? ORDER BY client_email", product));
    state.put("tokens", db.queryForList("SELECT * FROM account_tokens WHERE user_id=? OR email=? ORDER BY id", closed, originalEmail));
    state.put("sessions", db.queryForList("SELECT * FROM sessions WHERE user_id=? ORDER BY token_hash", closed));
    state.put("loginChallenges", db.queryForList("SELECT * FROM login_challenges WHERE user_id=? ORDER BY token_hash", closed));
    state.put("oauthIdentities", db.queryForList("SELECT * FROM oauth_identities WHERE user_id=? ORDER BY provider,subject", closed));
    state.put("savedProducts", db.queryForList("SELECT * FROM saved_products WHERE user_id=? ORDER BY product_id", closed));
    state.put("notifications", db.queryForList("SELECT * FROM notifications WHERE user_id=? ORDER BY id", closed));
    state.put("outbox", db.queryForList("SELECT * FROM email_outbox WHERE recipient=? ORDER BY id", originalEmail));
    state.put("audit", db.queryForList("SELECT * FROM moderation_actions WHERE target_type='DELETION' AND target_id=? ORDER BY id", closed));
    return state;
  }

  void revokedOperatorDuringLock(String lockSql, String blockedQueryPattern, Runnable revoke,
      int expectedStatus) throws Exception {
    var before = retainedPrivacyState();
    var executor = Executors.newSingleThreadExecutor();
    try (var holder = db.getDataSource().getConnection()) {
      holder.setAutoCommit(false);
      int pid;
      try (var statement = holder.createStatement(); var row = statement.executeQuery("SELECT pg_backend_pid()")) {
        assertTrue(row.next()); pid = row.getInt(1);
      }
      try (var statement = holder.prepareStatement(lockSql)) {
        statement.setObject(1, closed); try (var row = statement.executeQuery()) { assertTrue(row.next()); }
      }
      var pending = executor.submit(() -> mvc.perform(post("/api/v1/admin/deletion-requests/" + closed + "/review")
          .cookie(new Cookie("gl_session", "operator-session")).header("Origin", "http://localhost:3000")
          .header("X-Requested-With", "getlancer").contentType("application/json")
          .content("{\"action\":\"ANONYMIZE_PROFILE\",\"reason\":\"Synthetic review waiting for target lock\"}"))
          .andReturn().getResponse().getStatus());
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5); boolean waiting = false;
      while (System.nanoTime() < deadline && !pending.isDone()) {
        waiting = db.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE ?=ANY(pg_blocking_pids(pid)) AND query ILIKE ?", Integer.class, pid, blockedQueryPattern) > 0;
        if (waiting) break;
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
      }
      assertTrue(waiting, "The real target lock wait must precede operator revocation.");
      revoke.run();
      holder.commit();
      assertEquals(expectedStatus, pending.get(10, TimeUnit.SECONDS).intValue());
      assertEquals(before, retainedPrivacyState(), "Denied privacy reviews must roll back all target data and audit changes");
    } finally { executor.shutdownNow(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); }
  }

  @Test void sessionRevokedDuringTargetLockWaitCannotAnonymizeProfile() throws Exception {
    revokedOperatorAfterTargetLock(false);
  }

  @Test void administratorRoleRemovedDuringTargetLockWaitCannotAnonymizeProfile() throws Exception {
    revokedOperatorAfterTargetLock(true);
  }

  @Test void mfaRevokedDuringLaterProfileWriteRollsBackEntirePrivacyReview() throws Exception {
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",
        Support.hash("closed-account-session"), closed);
    db.update("INSERT INTO login_challenges(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",
        Support.hash("closed-account-challenge"), closed);
    db.update("INSERT INTO oauth_identities(provider,subject,user_id) VALUES('google','closed-account-google-subject',?)", closed);
    db.update("INSERT INTO saved_products(user_id,product_id) VALUES(?,?)", closed, product);
    db.update("INSERT INTO notifications(id,user_id,title) VALUES(?,?,'Retained private notification')", UUID.randomUUID(), closed);
    db.update("INSERT INTO email_outbox(id,recipient,subject,body) VALUES(?,?,'Retained private subject','Retained sensitive delivery body')",
        UUID.randomUUID(), originalEmail);
    // This profile write follows the locked deletion request and the first canonical operator check.
    revokedOperatorDuringLock("SELECT user_id FROM developer_profiles WHERE user_id=? FOR UPDATE", "%UPDATE developer_profiles%",
        () -> db.update("UPDATE sessions SET mfa_verified=false WHERE token_hash=?", Support.hash("operator-session")), 403);
  }

  @Test void replacementAccountCannotInheritRetainedInquiryExportMutationsOrPrivateGrants() throws Exception {
    anonymize(); var request = replacement();
    assertNotEquals(closed, db.queryForObject("SELECT id FROM users WHERE email=?", UUID.class, originalEmail));
    mvc.perform(get("/api/v1/me/inquiries").cookie(new Cookie("gl_session", "replacement")))
        .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items").isEmpty());
    mvc.perform(get("/api/v1/me/inquiries/" + inquiry).cookie(new Cookie("gl_session", "replacement"))).andExpect(status().isNotFound());
    for (String action : new String[] {"decision", "review", "not-hired", "confirmation-link"})
      mvc.perform(post("/api/v1/me/inquiries/" + inquiry + "/" + action)
          .cookie(new Cookie("gl_session", "replacement")).header("Origin", "http://localhost:3000")
          .header("X-Requested-With", "getlancer").contentType("application/json")
          .content(action.equals("decision") ? "{\"kind\":\"HIRE_CONFIRMATION\",\"decision\":\"ACCEPT\"}"
              : action.equals("review") ? "{\"rating\":5,\"reviewText\":\"Synthetic private review\",\"visibility\":\"ANONYMOUS\"}" : "{}"))
          .andExpect(status().isNotFound());
    assertTrue(accounts.export(request).requests().isEmpty());
    assertFalse(PrivateProjectService.granted(db, product, Map.of("email", originalEmail, "email_verified_at", Timestamp.from(java.time.Instant.now()))));
    assertEquals("closed-" + closed + "@example.invalid", db.queryForObject("SELECT client_email FROM inquiries WHERE id=?", String.class, inquiry));
    assertEquals(1, db.queryForObject("SELECT count(*) FROM inquiries WHERE id=?", Integer.class, inquiry));
    for (String token : new String[] {guestToken, guestToken + "-used"}) assertEquals("INVALID_TOKEN",
        assertThrows(ApiError.class, () -> auth.confirmationContext(Map.of("token", token))).code);
    // New, explicitly issued authority for the new account remains valid.
    db.update("INSERT INTO product_access_grants(product_id,client_email,expires_at) VALUES(?,?,now()+interval '1 day')", product, originalEmail);
    assertTrue(PrivateProjectService.granted(db, product, Map.of("email", originalEmail, "email_verified_at", Timestamp.from(java.time.Instant.now()))));
  }

  @Test void repeatedAnonymizationKeepsTheSameClosedIdentity() throws Exception {
    anonymize(); String identity = db.queryForObject("SELECT email FROM users WHERE id=?", String.class, closed);
    anonymize(); assertEquals(identity, db.queryForObject("SELECT email FROM users WHERE id=?", String.class, closed));
    assertEquals(identity, db.queryForObject("SELECT client_email FROM inquiries WHERE id=?", String.class, inquiry));
  }

  @Test void preexistingClosedNamespaceCollisionDoesNotOverwriteAnotherAccountOrBlockClosure() throws Exception {
    UUID collision = user("closed-" + closed + "@example.invalid", "ACTIVE");
    anonymize(); String identity = db.queryForObject("SELECT email FROM users WHERE id=?", String.class, closed);
    assertNotEquals("closed-" + closed + "@example.invalid", identity);
    assertEquals(identity, db.queryForObject("SELECT client_email FROM inquiries WHERE id=?", String.class, inquiry));
    assertEquals("ACTIVE", db.queryForObject("SELECT account_status FROM users WHERE id=?", String.class, collision));
    anonymize(); assertEquals(identity, db.queryForObject("SELECT email FROM users WHERE id=?", String.class, closed));
  }

  @Test void legacyMigrationQuarantinesPreviouslyAnonymizedAuthorityAndPreservesKnownOwners() throws Exception {
    // Model a closure performed by the old implementation, with the former email lost.
    db.update("UPDATE users SET email=? WHERE id=?", "closed-" + closed + "@example.invalid", closed);
    db.update("UPDATE deletion_requests SET status='PROFILE_ANONYMIZED' WHERE user_id=?", closed);
    db.update("UPDATE inquiries SET created_at=now()-interval '30 days' WHERE id=?", inquiry);
    var replacementRequest = replacement();
    String safeEmail = "established-client@example.test"; UUID established = user(safeEmail, "ACTIVE");
    db.update("UPDATE users SET created_at=now()-interval '60 days' WHERE id=?", established);
    UUID safeInquiry = UUID.randomUUID();
    db.update("INSERT INTO inquiries(id,reference_product_id,developer_user_id,client_email,client_name,request_type,description,budget_band,timeline_band,idempotency_key,request_hash)"
        + " VALUES(?,?,?,?,'Established client','SIMILAR_PROJECT','Known owner inquiry','DISCUSS','FLEXIBLE',?,?)",
        safeInquiry, product, builder, safeEmail, UUID.randomUUID(), Support.hash("safe fixture"));
    db.update("INSERT INTO product_access_grants(product_id,client_email,expires_at) VALUES(?,?,now()+interval '7 days')", product, safeEmail);
    for (int attempt = 0; attempt < 2; attempt++) {
      try (var connection = db.getDataSource().getConnection()) {
        connection.setSchema("getlancer_test");
        org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
            new org.springframework.core.io.ClassPathResource("db/migration/V35__legacy_requester_identity.sql"));
      }
      assertEquals(originalEmail, db.queryForObject("SELECT legacy_client_email FROM inquiries WHERE id=?", String.class, inquiry));
      assertEquals("QUARANTINED", db.queryForObject("SELECT moderation_status FROM inquiries WHERE id=?", String.class, inquiry));
      assertEquals("closed-legacy-" + inquiry + "@example.invalid", db.queryForObject("SELECT client_email FROM inquiries WHERE id=?", String.class, inquiry));
      assertTrue(accounts.export(replacementRequest).requests().isEmpty());
      assertEquals(0, db.queryForObject("SELECT count(*) FROM product_access_grants WHERE client_email=?", Integer.class, originalEmail));
      assertEquals(0, db.queryForObject("SELECT count(*) FROM account_tokens WHERE inquiry_id=?", Integer.class, inquiry));
      assertEquals(safeEmail, db.queryForObject("SELECT client_email FROM inquiries WHERE id=?", String.class, safeInquiry));
      assertEquals(1, db.queryForObject("SELECT count(*) FROM product_access_grants WHERE client_email=?", Integer.class, safeEmail));
    }
    mvc.perform(get("/api/v1/me/inquiries/" + inquiry).cookie(new Cookie("gl_session", "replacement"))).andExpect(status().isNotFound());
  }
}
