package com.getlancer.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.getlancer.config.MfaStorageUpgrade;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ContextConfiguration(initializers = TestDatabaseGuard.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "app.environment=local", "app.jobs-enabled=false", "app.admin-email=operator@example.test",
    "app.admin-password=", "app.admin-totp=", "spring.config.import=",
    "app.mfa.active-key-id=v1", "app.auth-rate-limit=10",
    "spring.datasource.hikari.maximum-pool-size=10", "spring.datasource.hikari.minimum-idle=1",
    "spring.datasource.hikari.connection-timeout=3000",
    "app.mfa.keyring=v1:AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
    "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}",
    "spring.datasource.username=${TEST_DB_USERNAME:postgres}",
    "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.hikari.schema=getlancer_test", "spring.flyway.default-schema=getlancer_test",
    "spring.flyway.schemas=getlancer_test", "app.origin=http://localhost:3000",
    "app.secure-cookie=false", "app.storage.access-key=", "app.storage.secret-key="
})
@AutoConfigureMockMvc
class AuthenticationHardeningIntegrationTest {
  @LocalServerPort int port;
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired MfaSecrets secrets;
  @Autowired AuthService auth;
  @Autowired MfaStorageUpgrade upgrade;
  @Autowired PlatformTransactionManager transactions;

  @BeforeEach
  @AfterEach
  void clear() {
    db.execute("TRUNCATE users CASCADE");
    db.execute("TRUNCATE rate_buckets");
  }

  UUID user(String email, boolean admin) {
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,?,now())", id,
        email, new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12)
            .encode("test-password-12345"));
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT')", id);
    if (admin) db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')", id);
    db.update("INSERT INTO developer_profiles(user_id,slug,display_name) VALUES(?,?,?)", id,
        "test-" + id, "Test member");
    db.update("INSERT INTO showcase_entitlements(user_id) VALUES(?)", id);
    return id;
  }

  void encrypted(UUID user) {
    var encrypted = secrets.encrypt(user, MfaSecretsTest.SECRET);
    db.update("UPDATE users SET admin_totp_key_version=?,admin_totp_nonce=?,admin_totp_ciphertext=? WHERE id=?",
        encrypted.keyId(), encrypted.nonce(), encrypted.ciphertext(), user);
  }

  void session(UUID user, String token) {
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",
        Support.hash(token), user);
  }

  void challenge(UUID user, String token, int attempts) {
    db.update("INSERT INTO login_challenges(token_hash,user_id,expires_at,attempts) VALUES(?,?,now()+interval '5 minutes',?)",
        Support.hash(token), user, attempts);
  }

  MockHttpServletRequestBuilder json(String path, String body) {
    return post("/api/v1" + path).header("Origin", "http://localhost:3000")
        .header("X-Requested-With", "getlancer").contentType("application/json").content(body);
  }

  String code() throws Exception { return code(Instant.now().getEpochSecond() / 30); }

  String code(long step) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA1");
    mac.init(new SecretKeySpec("12345678901234567890".getBytes(StandardCharsets.US_ASCII), "HmacSHA1"));
    byte[] digest = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
    int offset = digest[digest.length - 1] & 15;
    int value = ByteBuffer.wrap(digest, offset, 4).getInt() & 0x7fffffff;
    return String.format("%06d", value % 1000000);
  }

  HttpRequest loginRequest(String password) {
    return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/login"))
        .timeout(Duration.ofSeconds(25)).header("Origin", "http://localhost:3000")
        .header("X-Requested-With", "getlancer").header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"member@example.test\",\"password\":\"" + password + "\"}"))
        .build();
  }

  @Test
  void tenRealHttpLoginsCompleteWithProductionPoolAndFailedAuditSurvivesRollback() throws Exception {
    UUID id = user("member@example.test", false);
    var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(10);
    try {
      var futures = new java.util.ArrayList<java.util.concurrent.Future<HttpResponse<String>>>();
      for (int i = 0; i < 10; i++) futures.add(executor.submit(() -> {
        start.await(10, TimeUnit.SECONDS);
        return client.send(loginRequest("test-password-12345"), HttpResponse.BodyHandlers.ofString());
      }));
      start.countDown();
      var successfulRequestIds = new java.util.ArrayList<String>();
      for (var future : futures) {
        var response = future.get(30, TimeUnit.SECONDS);
        assertEquals(200, response.statusCode(), response.body());
        assertFalse(response.body().contains("timeout"));
        assertFalse(response.body().contains("password_hash"));
        successfulRequestIds.add(response.headers().firstValue("X-Request-ID").orElseThrow());
      }
      assertEquals(10, db.queryForObject("SELECT count(*) FROM sessions WHERE user_id=?", Integer.class, id));
      for (String requestId : successfulRequestIds)
        assertEquals(1, db.queryForObject("SELECT count(*) FROM security_audit_events WHERE event='LOGIN_SUCCESS' AND actor_id=? AND request_id=?",
            Integer.class, id, requestId));
      var limited = client.send(loginRequest("test-password-12345"), HttpResponse.BodyHandlers.ofString());
      assertEquals(429, limited.statusCode(), limited.body());
      assertEquals(10, db.queryForObject("SELECT count(*) FROM sessions WHERE user_id=?", Integer.class, id));
      db.execute("TRUNCATE rate_buckets");
      var failed = client.send(loginRequest("incorrect-password-12345"), HttpResponse.BodyHandlers.ofString());
      assertEquals(401, failed.statusCode(), failed.body());
      String failedRequestId = failed.headers().firstValue("X-Request-ID").orElseThrow();
      assertEquals(1, db.queryForObject("SELECT count(*) FROM security_audit_events WHERE event='LOGIN_FAILURE' AND result='FAILURE' AND request_id=?",
          Integer.class, failedRequestId));
      assertEquals(10, db.queryForObject("SELECT count(*) FROM sessions WHERE user_id=?", Integer.class, id));
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void invalidAuthInputsFailBeforeMutationIncludingUtf8BcryptLimit() throws Exception {
    mvc.perform(json("/auth/signup", "{\"email\":\"bad\",\"password\":\"short\",\"acceptedTerms\":true}"))
        .andExpect(status().isBadRequest());
    mvc.perform(json("/auth/signup", "{\"email\":\"valid@example.test\",\"password\":\"test-password-12345\",\"acceptedTerms\":false}"))
        .andExpect(status().isBadRequest());
    mvc.perform(json("/auth/login", "{\"email\":\"valid@example.test\",\"password\":\"" + "é".repeat(37) + "\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(json("/auth/login", "{\"email\":123,\"password\":\"test-password-12345\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(json("/auth/password-reset/confirm", "{\"token\":\"" + "a".repeat(43) + "\"}"))
        .andExpect(status().isBadRequest());
    assertEquals(0, db.queryForObject("SELECT count(*) FROM users", Integer.class));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM sessions", Integer.class));
  }

  @Test
  void upgradeClearsPlaintextAndRotationRevokesSessionsAndChallenges() {
    UUID id = user("operator@example.test", true);
    db.update("UPDATE users SET admin_totp=? WHERE id=?", MfaSecretsTest.SECRET, id);
    session(id, "old-session");
    challenge(id, "old-challenge", 0);
    upgrade.run(null);
    assertNull(db.queryForObject("SELECT admin_totp FROM users WHERE id=?", String.class, id));
    assertEquals("v1", db.queryForObject("SELECT admin_totp_key_version FROM users WHERE id=?", String.class, id));
    assertNotNull(db.queryForObject("SELECT admin_totp_ciphertext FROM users WHERE id=?", byte[].class, id));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM sessions", Integer.class));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM login_challenges", Integer.class));
    session(id, "before-rotation");
    var rotated = new MfaSecrets("v2", "v1:" + MfaSecretsTest.key(1) + ",v2:" + MfaSecretsTest.key(2), "local");
    new TransactionTemplate(transactions).executeWithoutResult(tx -> new MfaStorageUpgrade(db, rotated).run(null));
    var row = db.queryForMap("SELECT admin_totp_key_version,admin_totp_nonce,admin_totp_ciphertext FROM users WHERE id=?", id);
    assertEquals("v2", row.get("admin_totp_key_version"));
    assertEquals(MfaSecretsTest.SECRET, rotated.decrypt(id, "v2", (byte[]) row.get("admin_totp_nonce"), (byte[]) row.get("admin_totp_ciphertext")));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM sessions", Integer.class));
  }

  @Test
  void tamperedEnvelopeRollsBackTheEntireStartupUpgrade() {
    UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
    db.update("INSERT INTO users(id,email,password_hash,admin_totp) VALUES(?,'legacy@example.test','unused',?)", first, MfaSecretsTest.SECRET);
    var encrypted = secrets.encrypt(second, MfaSecretsTest.SECRET);
    byte[] tampered = encrypted.ciphertext();
    tampered[0] ^= 1;
    db.update("INSERT INTO users(id,email,password_hash,admin_totp_key_version,admin_totp_nonce,admin_totp_ciphertext) VALUES(?,'broken@example.test','unused',?,?,?)",
        second, encrypted.keyId(), encrypted.nonce(), tampered);
    session(first, "must-survive-rollback");
    assertThrows(MfaSecrets.SecretUnavailable.class, () -> upgrade.run(null));
    assertEquals(MfaSecretsTest.SECRET, db.queryForObject("SELECT admin_totp FROM users WHERE id=?", String.class, first));
    assertNull(db.queryForObject("SELECT admin_totp_key_version FROM users WHERE id=?", String.class, first));
    assertEquals(1, db.queryForObject("SELECT count(*) FROM sessions", Integer.class));
  }

  @Test
  void validMfaIsOneTimeAndPlaintextCannotAuthenticate() throws Exception {
    UUID id = user("operator@example.test", true);
    db.update("UPDATE users SET admin_totp=? WHERE id=?", MfaSecretsTest.SECRET, id);
    challenge(id, "plaintext-challenge", 0);
    mvc.perform(json("/auth/login/mfa", "{\"totp\":\"" + code() + "\"}")
        .cookie(new Cookie("gl_mfa", "plaintext-challenge"))).andExpect(status().isUnauthorized());
    assertEquals(0, db.queryForObject("SELECT count(*) FROM sessions", Integer.class));
    upgrade.run(null);
    challenge(id, "one-time", 0);
    mvc.perform(json("/auth/login/mfa", "{\"totp\":\"" + code() + "\"}")
        .cookie(new Cookie("gl_mfa", "one-time"))).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()));
    mvc.perform(json("/auth/login/mfa", "{\"totp\":\"" + code() + "\"}")
        .cookie(new Cookie("gl_mfa", "one-time"))).andExpect(status().isUnauthorized());
    assertEquals(1, db.queryForObject("SELECT count(*) FROM sessions", Integer.class));
  }

  @Test
  void suspendedRemovedRoleExpiredAndAttemptLockedChallengesCannotAuthenticate() throws Exception {
    UUID id = user("operator@example.test", true);
    encrypted(id);
    for (String state : new String[] {"SUSPENDED", "REMOVED_ROLE", "EXPIRED", "LOCKED"}) {
      db.update("UPDATE users SET account_status='ACTIVE' WHERE id=?", id);
      db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN') ON CONFLICT DO NOTHING", id);
      challenge(id, state, state.equals("LOCKED") ? 5 : 0);
      if (state.equals("SUSPENDED")) db.update("UPDATE users SET account_status='SUSPENDED' WHERE id=?", id);
      if (state.equals("REMOVED_ROLE")) db.update("DELETE FROM user_roles WHERE user_id=? AND role='ADMIN'", id);
      if (state.equals("EXPIRED")) db.update("UPDATE login_challenges SET expires_at=now()-interval '1 second' WHERE token_hash=?", Support.hash(state));
      mvc.perform(json("/auth/login/mfa", "{\"totp\":\"" + code() + "\"}").cookie(new Cookie("gl_mfa", state)))
          .andExpect(status().isUnauthorized());
    }
    assertEquals(0, db.queryForObject("SELECT count(*) FROM sessions", Integer.class));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM login_challenges", Integer.class));
  }

  @Test
  void fifthWrongCodeConsumesChallengeWithoutSession() throws Exception {
    UUID id = user("operator@example.test", true);
    encrypted(id);
    challenge(id, "last-attempt", 4);
    long step = Instant.now().getEpochSecond() / 30;
    var valid = List.of(code(step - 1), code(step), code(step + 1));
    int candidate = 0;
    while (valid.contains(String.format("%06d", candidate))) candidate++;
    String invalid = String.format("%06d", candidate);
    var result = mvc.perform(json("/auth/login/mfa", "{\"totp\":\"" + invalid + "\"}")
        .cookie(new Cookie("gl_mfa", "last-attempt"))).andExpect(status().isUnauthorized()).andReturn();
    assertEquals(0, db.queryForObject("SELECT count(*) FROM login_challenges", Integer.class));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM sessions", Integer.class));
    assertTrue(result.getResponse().getHeaders("Set-Cookie").stream()
        .anyMatch(cookie -> cookie.startsWith("gl_mfa=") && cookie.contains("Max-Age=0")));
  }

  @Test
  void concurrentMfaReplayCreatesExactlyOneSession() throws Exception {
    UUID id = user("operator@example.test", true);
    encrypted(id);
    challenge(id, "concurrent-challenge", 0);
    String current = code();
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      java.util.concurrent.Callable<String> attempt = () -> {
        start.await(10, TimeUnit.SECONDS);
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setCookies(new Cookie("gl_mfa", "concurrent-challenge"));
        try {
          auth.mfa(java.util.Map.of("totp", current), request,
              new org.springframework.mock.web.MockHttpServletResponse());
          return "SUCCESS";
        } catch (com.getlancer.shared.ApiError failure) {
          return failure.code;
        }
      };
      var first = executor.submit(attempt);
      var second = executor.submit(attempt);
      start.countDown();
      assertEquals(List.of("MFA_EXPIRED", "SUCCESS"),
          java.util.stream.Stream.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))
              .sorted().toList());
      assertEquals(1, db.queryForObject("SELECT count(*) FROM sessions", Integer.class));
      assertEquals(0, db.queryForObject("SELECT count(*) FROM login_challenges", Integer.class));
    } finally {
      executor.shutdownNow();
    }
  }

  String mfaAttempt(String token, String value) {
    var request = new org.springframework.mock.web.MockHttpServletRequest();
    request.setCookies(new Cookie("gl_mfa", token));
    try {
      auth.mfa(java.util.Map.of("totp", value), request,
          new org.springframework.mock.web.MockHttpServletResponse());
      return "SUCCESS";
    } catch (com.getlancer.shared.ApiError failure) {
      return failure.code;
    }
  }

  @Test
  void twentyParallelWrongCodesCannotExceedFiveEffectiveFailures() throws Exception {
    UUID id = user("operator@example.test", true);
    encrypted(id);
    challenge(id, "parallel-wrong", 0);
    long step = Instant.now().getEpochSecond() / 30;
    var valid = List.of(code(step - 1), code(step), code(step + 1));
    int candidate = 0;
    while (valid.contains(String.format("%06d", candidate))) candidate++;
    String invalid = String.format("%06d", candidate);
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(20);
    try {
      var futures = new java.util.ArrayList<java.util.concurrent.Future<String>>();
      for (int i = 0; i < 20; i++) futures.add(executor.submit(() -> {
        start.await(10, TimeUnit.SECONDS);
        return mfaAttempt("parallel-wrong", invalid);
      }));
      start.countDown();
      var outcomes = new java.util.ArrayList<String>();
      for (var future : futures) outcomes.add(future.get(20, TimeUnit.SECONDS));
      assertEquals(5, outcomes.stream().filter("MFA_INVALID"::equals).count());
      assertEquals(15, outcomes.stream().filter("MFA_EXPIRED"::equals).count());
      assertEquals(0, db.queryForObject("SELECT count(*) FROM login_challenges", Integer.class));
      assertEquals(0, db.queryForObject("SELECT count(*) FROM sessions", Integer.class));
      assertEquals("MFA_EXPIRED", mfaAttempt("parallel-wrong", code()));
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void correctCodeQueuedBeforeLockoutUsesFreshChallengeState() throws Exception {
    UUID id = user("operator@example.test", true);
    encrypted(id);
    challenge(id, "queued-correct", 4);
    String current = code();
    var start = new CountDownLatch(1);
    var executor = Executors.newSingleThreadExecutor();
    try {
      var queued = executor.submit(() -> {
        start.await(10, TimeUnit.SECONDS);
        return mfaAttempt("queued-correct", current);
      });
      new TransactionTemplate(transactions).executeWithoutResult(tx -> {
        db.queryForMap("SELECT id FROM users WHERE id=? FOR UPDATE", id);
        start.countDown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        boolean blocked = false;
        while (System.nanoTime() < deadline) {
          db.execute("SELECT pg_stat_clear_snapshot()");
          Integer waits = db.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database()"
              + " AND wait_event_type='Lock' AND query LIKE '%login_challenges%' AND query LIKE '%FOR UPDATE OF u%'", Integer.class);
          if (waits != null && waits > 0) { blocked = true; break; }
          java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        }
        assertTrue(blocked, "Correct-code request must be queued on the user lock before lockout");
        // Model the fifth failed attempt while the correct request waits with its old snapshot.
        db.update("UPDATE login_challenges SET attempts=5 WHERE token_hash=?", Support.hash("queued-correct"));
      });
      assertEquals("MFA_EXPIRED", queued.get(15, TimeUnit.SECONDS));
      assertEquals(0, db.queryForObject("SELECT count(*) FROM sessions", Integer.class));
      assertEquals(0, db.queryForObject("SELECT count(*) FROM login_challenges", Integer.class));
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void passwordLoginRotatesCallerSessionAndLogoutRevokesReplacement() throws Exception {
    UUID id = user("member@example.test", false);
    session(id, "old-browser-session");
    var result = mvc.perform(json("/auth/login", "{\"email\":\"member@example.test\",\"password\":\"test-password-12345\"}")
        .cookie(new Cookie("gl_session", "old-browser-session"))).andExpect(status().isOk()).andReturn();
    String cookie = result.getResponse().getHeaders("Set-Cookie").stream().filter(c -> c.startsWith("gl_session=")).findFirst().orElseThrow();
    assertTrue(cookie.contains("HttpOnly"));
    assertTrue(cookie.contains("SameSite=Lax"));
    assertTrue(cookie.contains("Path=/"));
    assertFalse(cookie.contains("Max-Age"));
    String token = cookie.substring("gl_session=".length(), cookie.indexOf(';'));
    assertFalse(token.equals("old-browser-session"));
    mvc.perform(get("/api/v1/me").cookie(new Cookie("gl_session", "old-browser-session"))).andExpect(status().isUnauthorized());
    var me = mvc.perform(get("/api/v1/me").cookie(new Cookie("gl_session", token))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertFalse(me.contains("password_hash"));
    assertFalse(me.contains("admin_totp"));
    assertFalse(me.contains("token_hash"));
    Integer before = db.queryForObject("SELECT count(*) FROM security_audit_events WHERE event='SESSION_REVOKED'", Integer.class);
    mvc.perform(json("/auth/logout", "{}").cookie(new Cookie("gl_session", token))).andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/me").cookie(new Cookie("gl_session", token))).andExpect(status().isUnauthorized());
    mvc.perform(json("/auth/logout", "{}").cookie(new Cookie("gl_session", token))).andExpect(status().isNoContent());
    mvc.perform(json("/auth/logout", "{}")).andExpect(status().isNoContent());
    assertEquals(before + 1, db.queryForObject("SELECT count(*) FROM security_audit_events WHERE event='SESSION_REVOKED'", Integer.class));
    assertEquals(id, db.queryForObject("SELECT actor_id FROM security_audit_events WHERE event='SESSION_REVOKED' ORDER BY created_at DESC LIMIT 1", UUID.class));
  }

  @Test
  void confirmedAccountDeletionEmitsAccountDeletedWithSafeActor() throws Exception {
    UUID id = user("member@example.test", false);
    session(id, "before-account-deletion");
    String token = "d".repeat(43);
    db.update("INSERT INTO account_tokens(id,token_hash,kind,user_id,email,expires_at) VALUES(?,?,'ACCOUNT_DELETION',?,'member@example.test',now()+interval '1 hour')",
        UUID.randomUUID(), Support.hash(token), id);
    mvc.perform(json("/auth/confirm", "{\"token\":\"" + token + "\",\"decision\":\"DELETE\"}"))
        .andExpect(status().isOk());
    assertEquals("DELETED", db.queryForObject("SELECT account_status FROM users WHERE id=?", String.class, id));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM sessions WHERE user_id=?", Integer.class, id));
    assertEquals(1, db.queryForObject("SELECT count(*) FROM security_audit_events WHERE event='ACCOUNT_DELETED' AND actor_id=? AND target=? AND result='SUCCESS'",
        Integer.class, id, id.toString()));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM security_audit_events WHERE event='ACCOUNT_DELETION' AND actor_id=?", Integer.class, id));
  }

  @Test
  void resetIsSingleUseAndRevokesAllSessionsAndChallenges() throws Exception {
    UUID id = user("member@example.test", false);
    session(id, "device-one");
    session(id, "device-two");
    challenge(id, "pending", 0);
    String token = "r".repeat(43);
    db.update("INSERT INTO account_tokens(id,token_hash,kind,user_id,email,expires_at) VALUES(?,?,'PASSWORD_RESET',?,'member@example.test',now()+interval '1 hour')",
        UUID.randomUUID(), Support.hash(token), id);
    String body = "{\"token\":\"" + token + "\",\"password\":\"changed-password-12345\"}";
    mvc.perform(json("/auth/password-reset/confirm", body)).andExpect(status().isOk());
    assertEquals(0, db.queryForObject("SELECT count(*) FROM sessions", Integer.class));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM login_challenges", Integer.class));
    mvc.perform(json("/auth/password-reset/confirm", body)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    assertTrue(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
        .matches("changed-password-12345", db.queryForObject("SELECT password_hash FROM users WHERE id=?", String.class, id)));
  }
}
