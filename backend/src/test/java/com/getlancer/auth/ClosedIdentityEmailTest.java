package com.getlancer.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.getlancer.notifications.Mail;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import com.getlancer.shared.Support;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;

class ClosedIdentityEmailTest {
  final String closed = "closed-00000000-0000-4000-8000-000000000001@example.invalid";

  @Test void publicEmailInputsCannotClaimTheClosedIdentityNamespace() {
    assertEquals("EMAIL_UNAVAILABLE", assertThrows(ApiError.class,
        () -> Support.email(Map.of("email", " " + closed.toUpperCase() + " "), "email")).code);
    assertEquals("client@example.test", Support.email(Map.of("email", "Client@Example.Test"), "email"));
  }

  @Test void googleRegistrationAlsoRejectsTheClosedIdentityBeforeDatabaseWork() {
    var db = mock(JdbcTemplate.class); var security = mock(Security.class); var mail = mock(Mail.class);
    var accounts = new GoogleAccounts(db, security, mail, "operator@example.test");
    var jwt = Jwt.withTokenValue("synthetic").header("alg", "none").subject("fixture-subject")
        .claim("email", closed).claim("email_verified", true).build();
    assertEquals("EMAIL_UNAVAILABLE", assertThrows(ApiError.class, () -> accounts.resolve(jwt, "signup")).code);
    verifyNoInteractions(db, security, mail);
  }

  @Test void githubRegistrationAlsoRejectsTheClosedIdentityBeforeDatabaseWork() {
    var db = mock(JdbcTemplate.class); var security = mock(Security.class);
    var accounts = new GitHubAccounts(db, security, "operator@example.test");
    assertEquals("EMAIL_UNAVAILABLE", assertThrows(ApiError.class,
        () -> accounts.resolve(new GitHubAccounts.Identity("fixture-subject", closed, "Fixture"), "signup")).code);
    verifyNoInteractions(db, security);
  }
}
