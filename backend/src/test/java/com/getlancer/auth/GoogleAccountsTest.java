package com.getlancer.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.getlancer.notifications.Mail;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;

class GoogleAccountsTest {
  final JdbcTemplate db = mock(JdbcTemplate.class);
  final Security security = mock(Security.class);
  final GoogleAccounts accounts =
      new GoogleAccounts(db, security, mock(Mail.class), "admin@example.com");

  Jwt token(String email) {
    return new Jwt(
        "fixture",
        Instant.now(),
        Instant.now().plusSeconds(300),
        Map.of("alg", "RS256"),
        Map.of("sub", "subject-123", "email", email, "email_verified", true,
            "hd", email.substring(email.lastIndexOf('@') + 1)));
  }

  Jwt externalMailboxToken() {
    return new Jwt(
        "fixture", Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "RS256"),
        Map.of("sub", "subject-123", "email", "member@example.com", "email_verified", true));
  }

  @Test
  void nonAuthoritativeGoogleMailboxCannotCreateOrReusePermanentIdentity() {
    for (String intent : List.of("signup", "login")) {
      assertEquals(
          "email_password_required",
          assertThrows(ApiError.class, () -> accounts.resolve(externalMailboxToken(), intent)).code);
    }
    // Reject before consulting any legacy mapping, whether its account is still
    // unverified or has since been verified/recovered by the current mailbox owner.
    verifyNoInteractions(db, security, accounts.mail);
  }

  @Test
  void authoritativeWorkspaceIdentityStillResolvesItsActiveLinkedAccount() {
    UUID id = UUID.randomUUID();
    when(db.queryForList(startsWith("SELECT u.id,u.email,u.account_status FROM users"), eq("subject-123")))
        .thenReturn(List.of(Map.of("id", id, "email", "member@example.com", "account_status", "ACTIVE")));
    assertEquals(id, accounts.resolve(token("member@example.com"), "login"));
    verify(db, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void reservedAdminAddressCannotUseGoogle() {
    assertEquals(
        "admin_password",
        assertThrows(ApiError.class, () -> accounts.resolve(token("admin@example.com"), "signup"))
            .code);
    verifyNoInteractions(db);
  }

  @Test
  void existingPasswordEmailIsNeverAutomaticallyLinked() {
    when(db.queryForObject(
            "SELECT count(*) FROM users WHERE email=?", Integer.class, "member@example.com"))
        .thenReturn(1);
    assertEquals(
        "email_account",
        assertThrows(ApiError.class, () -> accounts.resolve(token("member@example.com"), "signup"))
            .code);
    verify(db, never()).update(startsWith("INSERT INTO oauth_identities"), any(Object[].class));
  }

  @Test
  void returningSuspendedGoogleAccountCannotSignIn() {
    UUID id = UUID.randomUUID();
    when(db.queryForList(startsWith("SELECT u.id,u.email,u.account_status FROM users"), eq("subject-123")))
        .thenReturn(
            List.of(
                Map.of("id", id, "email", "member@example.com", "account_status", "SUSPENDED")));
    assertEquals(
        "account_unavailable",
        assertThrows(ApiError.class, () -> accounts.resolve(token("member@example.com"), "login"))
            .code);
    verify(db).queryForList(startsWith("SELECT u.id,u.email,u.account_status FROM users"), eq("subject-123"));
  }
}
