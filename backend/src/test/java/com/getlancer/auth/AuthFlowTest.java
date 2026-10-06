package com.getlancer.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.inquiries.InquiryOutcomeService;
import com.getlancer.notifications.Mail;
import com.getlancer.security.Security;
import com.getlancer.security.SecurityAudit;
import com.getlancer.shared.ApiError;
import com.getlancer.shared.Support;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AuthFlowTest {
  final JdbcTemplate db = mock(JdbcTemplate.class);
  final Security security = mock(Security.class);
  final Mail mail = mock(Mail.class);
  final MfaSecrets secrets = MfaSecretsTest.keys();
  final AuthService auth =
      new AuthService(
          db, security, mail, true, "admin@example.com", new InquiryOutcomeService(db, mail), secrets);

  @Test
  void correctAdminPasswordIssuesChallengeWithoutSession() {
    UUID id = UUID.randomUUID();
    when(db.queryForList(
            "SELECT id,password_hash FROM users WHERE email=? AND account_status='ACTIVE' FOR UPDATE", "admin@example.com"))
        .thenReturn(
            List.of(
                Map.of(
                    "id",
                    id,
                    "password_hash",
                    new BCryptPasswordEncoder(12).encode("example-password-123"))));
    when(security.role(id, "ADMIN")).thenReturn(true);
    var res = new MockHttpServletResponse();
    var result =
        auth.login(Map.of("email", "admin@example.com", "password", "example-password-123"), res);
    assertEquals(true, result.get("mfaRequired"));
    String cookie = res.getHeaders("Set-Cookie").stream()
        .filter(c -> c.startsWith("__Host-gl_mfa=")).findFirst().orElseThrow();
    assertTrue(cookie.startsWith("__Host-gl_mfa="));
    assertTrue(cookie.contains("HttpOnly"));
    assertTrue(cookie.contains("Secure"));
    assertFalse(cookie.contains("gl_session"));
  }

  @Test
  void wrongPasswordDoesNotRevealAdministratorStep() {
    when(db.queryForList(
            "SELECT id,password_hash FROM users WHERE email=? AND account_status='ACTIVE' FOR UPDATE", "absent@example.com"))
        .thenReturn(List.of());
    var res = new MockHttpServletResponse();
    var body = Map.<String, Object>of("email", "absent@example.com", "password", "wrong");
    auth.preflightLogin(body);
    assertThrows(ApiError.class, () -> auth.login(body, res));
    assertNull(res.getHeader("Set-Cookie"));
    verify(security).limitIdentity(anyString(), eq("login"));
    verify(security, never()).role(any(), eq("ADMIN"));
  }

  @Test
  void ordinaryAccountSignsInWithoutMfa() {
    UUID id = UUID.randomUUID();
    when(db.queryForList(
            "SELECT id,password_hash FROM users WHERE email=? AND account_status='ACTIVE' FOR UPDATE", "member@example.com"))
        .thenReturn(
            List.of(
                Map.of(
                    "id",
                    id,
                    "password_hash",
                    new BCryptPasswordEncoder(12).encode("member-password-123"))));
    var res = new MockHttpServletResponse();
    var result =
        auth.login(Map.of("email", "member@example.com", "password", "member-password-123"), res);
    assertEquals(id, result.get("id"));
    assertFalse(result.containsKey("mfaRequired"));
    assertTrue(res.getHeaders("Set-Cookie").stream()
        .anyMatch(c -> c.startsWith("__Host-gl_session=") && !c.contains("Max-Age=0")));
  }

  @Test
  void missingChallengeCannotAuthenticate() {
    var req = new MockHttpServletRequest();
    var res = new MockHttpServletResponse();
    when(db.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
    ApiError failure =
        assertThrows(ApiError.class, () -> auth.mfa(Map.of("totp", "123456"), req, res));
    assertEquals("MFA_EXPIRED", failure.code);
    assertNull(res.getHeader("Set-Cookie"));
  }

  @Test
  void invalidMfaConsumesAnAttemptAndNeverIssuesSession() {
    UUID id = UUID.randomUUID();
    String raw = "challenge";
    var req = new MockHttpServletRequest();
    req.setCookies(new Cookie("gl_mfa", raw));
    var res = new MockHttpServletResponse();
    var encrypted = secrets.encrypt(id, "JBSWY3DPEHPK3PXP");
    when(db.queryForList(anyString(), eq(Support.hash(raw))))
        .thenReturn(
            List.of(
                Map.of(
                    "user_id",
                    id,
                    "admin_totp_key_version", encrypted.keyId(),
                    "admin_totp_nonce", encrypted.nonce(),
                    "admin_totp_ciphertext", encrypted.ciphertext(),
                    "account_status",
                    "ACTIVE",
                    "attempts",
                    0,
                    "expires_at",
                    Timestamp.from(Instant.now().plusSeconds(300)))));
    when(security.role(id, "ADMIN")).thenReturn(true);
    ApiError failure =
        assertThrows(ApiError.class, () -> auth.mfa(Map.of("totp", "invalid"), req, res));
    assertEquals("MFA_INVALID", failure.code);
    verify(db)
        .update(
            "UPDATE login_challenges SET attempts=attempts+1 WHERE token_hash=?",
            Support.hash(raw));
    assertNull(res.getHeader("Set-Cookie"));
  }

  @Test
  void challengeWithFiveFailedAttemptsCannotAuthenticate() {
    UUID id = UUID.randomUUID();
    var req = new MockHttpServletRequest();
    req.setCookies(new Cookie("gl_mfa", "locked"));
    var res = new MockHttpServletResponse();
    when(db.queryForList(anyString(), eq(Support.hash("locked"))))
        .thenReturn(
            List.of(
                Map.of(
                    "user_id",
                    id,
                    "account_status",
                    "ACTIVE",
                    "attempts",
                    5,
                    "expires_at",
                    Timestamp.from(Instant.now().plusSeconds(300)))));
    ApiError failure =
        assertThrows(ApiError.class, () -> auth.mfa(Map.of("totp", "123456"), req, res));
    assertEquals("MFA_EXPIRED", failure.code);
    verify(db).update("DELETE FROM login_challenges WHERE token_hash=?", Support.hash("locked"));
    assertTrue(res.getHeaders("Set-Cookie").stream().noneMatch(c -> c.startsWith("gl_session=")));
  }

  @Test
  void waitingMfaReloadsAttemptCounterBeforeCheckingCode() {
    UUID id = UUID.randomUUID();
    var req = new MockHttpServletRequest();
    req.setCookies(new Cookie("gl_mfa", "queued"));
    var before = Map.<String, Object>of("user_id", id, "account_status", "ACTIVE", "attempts", 0,
        "expires_at", Timestamp.from(Instant.now().plusSeconds(300)));
    var after = new java.util.LinkedHashMap<String, Object>(before);
    after.put("attempts", 5);
    when(db.queryForList(anyString(), eq(Support.hash("queued"))))
        .thenReturn(List.of(before), List.of(after));
    assertEquals("MFA_EXPIRED", assertThrows(ApiError.class,
        () -> auth.mfa(Map.of("totp", "123456"), req, new MockHttpServletResponse())).code);
    verify(db).queryForList(org.mockito.ArgumentMatchers.contains("FOR UPDATE OF c"),
        eq(Support.hash("queued")));
    verify(db, never()).update(org.mockito.ArgumentMatchers.contains("attempts=attempts+1"), any(Object[].class));
    verify(db, never()).update(org.mockito.ArgumentMatchers.contains("INSERT INTO sessions"), any(Object[].class));
  }

  @Test
  void validMfaIssuesOneSessionAndReplayedChallengeFails() throws Exception {
    UUID id = UUID.randomUUID();
    var req = new MockHttpServletRequest();
    req.setCookies(new Cookie("gl_mfa", "one-time"));
    var res = new MockHttpServletResponse();
    var encrypted = secrets.encrypt(id, "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
    var record =
        Map.<String, Object>of(
            "user_id",
            id,
            "admin_totp_key_version", encrypted.keyId(),
            "admin_totp_nonce", encrypted.nonce(),
            "admin_totp_ciphertext", encrypted.ciphertext(),
            "account_status",
            "ACTIVE",
            "attempts",
            0,
            "expires_at",
            Timestamp.from(Instant.now().plusSeconds(300)));
    when(db.queryForList(anyString(), eq(Support.hash("one-time"))))
        .thenReturn(List.of(record), List.of(record), List.of());
    when(security.role(id, "ADMIN")).thenReturn(true);
    when(db.update("DELETE FROM login_challenges WHERE token_hash=?", Support.hash("one-time")))
        .thenReturn(1);
    // RFC 6238 fixture key; calculate the current moving code independently of the verifier.
    var mac = javax.crypto.Mac.getInstance("HmacSHA1");
    mac.init(
        new javax.crypto.spec.SecretKeySpec(
            "12345678901234567890".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
            "HmacSHA1"));
    byte[] digest =
        mac.doFinal(
            java.nio.ByteBuffer.allocate(8).putLong(Instant.now().getEpochSecond() / 30).array());
    int offset = digest[digest.length - 1] & 15;
    int value = java.nio.ByteBuffer.wrap(digest, offset, 4).getInt() & 0x7fffffff;
    String code = String.format("%06d", value % 1000000);
    assertEquals(id, auth.mfa(Map.of("totp", code), req, res).get("id"));
    assertTrue(
        res.getHeaders("Set-Cookie").stream()
            .anyMatch(c -> c.startsWith("__Host-gl_session=") && c.contains("Max-Age=3600")));
    verify(db).update("DELETE FROM login_challenges WHERE token_hash=?", Support.hash("one-time"));
    var replay = new MockHttpServletResponse();
    assertEquals(
        "MFA_EXPIRED",
        assertThrows(ApiError.class, () -> auth.mfa(Map.of("totp", code), req, replay)).code);
    assertNull(replay.getHeader("Set-Cookie"));
  }

  @Test
  void mfaPreflightLimitsTheLiveServerUserBeforeTheTransaction() {
    UUID user = UUID.randomUUID();
    var request = new MockHttpServletRequest();
    request.setCookies(new Cookie("gl_mfa", "preflight"));
    when(db.queryForList(
        "SELECT c.user_id FROM login_challenges c JOIN users u ON u.id=c.user_id"
            + " WHERE c.token_hash=? AND c.expires_at>now() AND c.attempts<5"
            + " AND u.account_status='ACTIVE'", UUID.class, Support.hash("preflight")))
        .thenReturn(List.of(user));
    when(security.role(user, "ADMIN")).thenReturn(true);
    auth.preflightMfa(request);
    verify(security).limitIdentity(user.toString(), "mfa");
    verify(db, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void invalidMfaPreflightDoesNotTrustACookieAsAUserIdentity() {
    var request = new MockHttpServletRequest();
    request.setCookies(new Cookie("gl_mfa", "unknown"));
    auth.preflightMfa(request);
    verify(security, never()).limitIdentity(anyString(), anyString());
  }

  @Test
  void oauthSessionIssuanceRevokesPreviousBrowserSessionAndPendingMfa() {
    var request = new MockHttpServletRequest();
    request.setCookies(new Cookie("gl_session", "prior-session"), new Cookie("gl_mfa", "prior-challenge"));
    var response = new MockHttpServletResponse();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    try {
      auth.issueSession(UUID.randomUUID(), false, response);
    } finally {
      RequestContextHolder.resetRequestAttributes();
    }
    verify(db).update("DELETE FROM sessions WHERE token_hash=?", Support.hash("prior-session"));
    verify(db).update("DELETE FROM login_challenges WHERE token_hash=?", Support.hash("prior-challenge"));
    assertTrue(response.getHeaders("Set-Cookie").stream()
        .anyMatch(cookie -> cookie.startsWith("__Host-gl_mfa=") && cookie.contains("Max-Age=0")));
    assertTrue(response.getHeaders("Set-Cookie").stream()
        .anyMatch(cookie -> cookie.startsWith("__Host-gl_session=") && !cookie.contains("Max-Age=0")));
  }

  AuthService withAudit(SecurityAudit audit) {
    return new AuthService(db, security, mail, true, "admin@example.com",
        new InquiryOutcomeService(db, mail), secrets, audit, null, "v1-draft");
  }

  @Test
  void anonymousAndUnknownLogoutDoNotAppendAuditSuccesses() {
    var audit = mock(SecurityAudit.class);
    var audited = withAudit(audit);
    audited.logout(new MockHttpServletRequest(), new MockHttpServletResponse());
    var unknown = new MockHttpServletRequest();
    unknown.setCookies(new Cookie("gl_session", "unknown-session"), new Cookie("gl_mfa", "unknown-challenge"));
    audited.logout(unknown, new MockHttpServletResponse());
    verifyNoInteractions(audit);
  }

  @Test
  void realLogoutCapturesServerActorAndRepeatedLogoutIsSilent() {
    UUID user = UUID.randomUUID();
    var audit = mock(SecurityAudit.class);
    var audited = withAudit(audit);
    var request = new MockHttpServletRequest();
    request.setCookies(new Cookie("gl_session", "real-session"));
    when(db.queryForList("SELECT user_id FROM sessions WHERE token_hash=?", UUID.class,
        Support.hash("real-session"))).thenReturn(List.of(user), List.of());
    when(db.update("DELETE FROM sessions WHERE token_hash=?", Support.hash("real-session")))
        .thenReturn(1, 0);
    audited.logout(request, new MockHttpServletResponse());
    audited.logout(request, new MockHttpServletResponse());
    verify(audit).defer(request, "SESSION_REVOKED", user, user.toString(), "SUCCESS");
    org.mockito.Mockito.verifyNoMoreInteractions(audit);
  }

  @Test
  void challengeOnlyLogoutUsesServerActorAndRevokesOnce() {
    UUID user = UUID.randomUUID();
    var audit = mock(SecurityAudit.class);
    var request = new MockHttpServletRequest();
    request.setCookies(new Cookie("gl_mfa", "real-challenge"));
    when(db.queryForList("SELECT user_id FROM login_challenges WHERE token_hash=?", UUID.class,
        Support.hash("real-challenge"))).thenReturn(List.of(user));
    when(db.update("DELETE FROM login_challenges WHERE token_hash=?", Support.hash("real-challenge")))
        .thenReturn(1);
    withAudit(audit).logout(request, new MockHttpServletResponse());
    verify(audit).defer(request, "SESSION_REVOKED", user, user.toString(), "SUCCESS");
  }

  @Test
  void lockedChallengeEmitsExplicitMfaLockedEvent() {
    UUID user = UUID.randomUUID();
    var audit = mock(SecurityAudit.class);
    var request = new MockHttpServletRequest();
    request.setCookies(new Cookie("gl_mfa", "locked-audit"));
    when(db.queryForList(anyString(), eq(Support.hash("locked-audit")))).thenReturn(List.of(
        Map.of("user_id", user, "account_status", "ACTIVE", "attempts", 5,
            "expires_at", Timestamp.from(Instant.now().plusSeconds(300)))));
    assertThrows(ApiError.class,
        () -> withAudit(audit).mfa(Map.of("totp", "123456"), request, new MockHttpServletResponse()));
    verify(audit).defer(request, "MFA_LOCKED", user, user.toString(), "LOCKED");
  }

  GoogleAuthService google(String id, String secret) {
    return new GoogleAuthService(
        db,
        auth,
        mock(GoogleTokens.class),
        mock(GoogleAccounts.class),
        new ObjectMapper(),
        id,
        secret,
        "https://getlancer.example");
  }

  @Test
  void missingGoogleConfigurationDoesNotPretendToSignIn() {
    var controller = google("", "");
    assertEquals(false, controller.enabled());
    assertThrows(
        ApiError.class,
        () -> controller.start(Map.of("intent", "login"), new MockHttpServletResponse()));
  }

  @Test
  void googleSignupRequiresConsent() {
    assertThrows(
        ApiError.class,
        () ->
            google("id", "secret")
                .start(Map.of("intent", "signup"), new MockHttpServletResponse()));
    verifyNoInteractions(db);
  }

  @Test
  void googleStartUsesPkceAndBrowserBinding() throws Exception {
    var res = new MockHttpServletResponse();
    var result = google("client", "secret").start(Map.of("intent", "login"), res);
    String url = (String) result.get("authorizationUrl");
    assertTrue(url.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"));
    assertTrue(url.contains("code_challenge_method=S256"));
    assertTrue(url.contains("nonce="));
    assertTrue(url.contains("state="));
    assertFalse(url.contains("secret"));
    assertTrue(res.getHeader("Set-Cookie").startsWith("__Host-gl_oauth="));
    assertTrue(res.getHeader("Set-Cookie").contains("HttpOnly"));
  }

  @Test
  void callbackWithoutBrowserCookieNeverExchangesCode() {
    var response =
        google("id", "secret")
            .callback(
                "state", "code", "", new MockHttpServletRequest(), new MockHttpServletResponse());
    assertEquals(303, response.getStatusCode().value());
    assertEquals("/login?auth_error=expired", response.getHeaders().getLocation().toString());
    verifyNoInteractions(db);
  }
}
