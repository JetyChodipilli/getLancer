package com.getlancer.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class GoogleTokensTest {
  final Instant now = Instant.parse("2026-09-08T00:00:00Z");

  Jwt token(Consumer<Map<String, Object>> change) {
    var claims = new HashMap<String, Object>();
    claims.put("iss", "https://accounts.google.com");
    claims.put("aud", List.of("test-client"));
    claims.put("sub", "google-user-123");
    claims.put("nonce", "expected-nonce");
    claims.put("email", "person@gmail.com");
    claims.put("email_verified", true);
    claims.put("iat", now.minusSeconds(10));
    claims.put("exp", now.plusSeconds(300));
    change.accept(claims);
    return new Jwt(
        "test-token",
        (Instant) claims.get("iat"),
        (Instant) claims.get("exp"),
        Map.of("alg", "RS256"),
        claims);
  }

  boolean valid(Jwt jwt) {
    return GoogleTokens.validClaims(jwt, "test-client", "expected-nonce", now);
  }

  @Test
  void acceptsFreshExpectedIdentity() {
    assertTrue(valid(token(c -> {})));
  }

  @Test
  void rejectsWrongAudience() {
    assertFalse(valid(token(c -> c.put("aud", List.of("another-client")))));
  }

  @Test
  void rejectsWrongIssuer() {
    assertFalse(valid(token(c -> c.put("iss", "https://attacker.example"))));
  }

  @Test
  void rejectsWrongNonce() {
    assertFalse(valid(token(c -> c.put("nonce", "another-browser"))));
  }

  @Test
  void rejectsExpiredIdentity() {
    assertFalse(valid(token(c -> c.put("exp", now.minusSeconds(1)))));
  }

  @Test
  void rejectsStaleOrFutureIdentity() {
    assertFalse(valid(token(c -> c.put("iat", now.minusSeconds(901)))));
    assertFalse(valid(token(c -> c.put("iat", now.plusSeconds(120)))));
  }

  @Test
  void rejectsUnverifiedEmail() {
    assertFalse(valid(token(c -> c.put("email_verified", false))));
  }

  @Test
  void rejectsMissingSubject() {
    assertFalse(valid(token(c -> c.remove("sub"))));
  }

  @Test
  void requiresAuthorizedPartyWithMultipleAudiences() {
    assertFalse(valid(token(c -> c.put("aud", List.of("test-client", "other")))));
    assertTrue(
        valid(
            token(
                c -> {
                  c.put("aud", List.of("test-client", "other"));
                  c.put("azp", "test-client");
                })));
  }

  @Test
  void externalGoogleAddressNeedsIndependentEmailConfirmation() {
    assertTrue(GoogleTokens.authoritativeEmail(token(c -> {})));
    assertFalse(GoogleTokens.authoritativeEmail(token(c -> c.put("email", "person@example.com"))));
    assertTrue(
        GoogleTokens.authoritativeEmail(
            token(
                c -> {
                  c.put("email", "person@example.com");
                  c.put("hd", "example.com");
                })));
  }

  @Test
  void malformedTokenNeverAuthenticates() {
    assertThrows(
        Exception.class, () -> new GoogleTokens("test-client").verify("not-a-jwt", "nonce"));
  }
}
