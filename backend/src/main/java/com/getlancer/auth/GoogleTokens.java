package com.getlancer.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class GoogleTokens {
  private final String clientId;
  private final NimbusJwtDecoder decoder;

  public GoogleTokens(@Value("${app.google.client-id:}") String clientId) {
    this.clientId = clientId;
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);
    factory.setReadTimeout(5000);
    decoder =
        NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
            .restOperations(new RestTemplate(factory))
            .build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefault(),
            jwt ->
                Set.of("https://accounts.google.com", "accounts.google.com")
                        .contains(jwt.getClaimAsString("iss"))
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
  }

  Jwt verify(String raw, String nonce) {
    if (raw == null || raw.length() > 20000) throw new IllegalArgumentException("Missing token");
    Jwt jwt = decoder.decode(raw);
    if (!validClaims(jwt, clientId, nonce, Instant.now()))
      throw new IllegalArgumentException("Invalid identity claims");
    return jwt;
  }

  static boolean validClaims(Jwt jwt, String clientId, String nonce, Instant now) {
    try {
      if (clientId.isBlank()
          || nonce.isBlank()
          || !Set.of("https://accounts.google.com", "accounts.google.com")
              .contains(jwt.getClaimAsString("iss"))) return false;
      if (!jwt.getAudience().contains(clientId)) return false;
      String authorizedParty = jwt.getClaimAsString("azp");
      if ((jwt.getAudience().size() > 1 || authorizedParty != null)
          && !clientId.equals(authorizedParty)) return false;
      if (jwt.getExpiresAt() == null
          || !jwt.getExpiresAt().isAfter(now)
          || jwt.getIssuedAt() == null
          || jwt.getIssuedAt().isAfter(now.plusSeconds(60))
          || jwt.getIssuedAt().isBefore(now.minusSeconds(900))) return false;
      if (jwt.getSubject() == null || jwt.getSubject().isBlank() || jwt.getSubject().length() > 255)
        return false;
      if (!MessageDigest.isEqual(
          nonce.getBytes(StandardCharsets.UTF_8),
          Objects.toString(jwt.getClaimAsString("nonce"), "").getBytes(StandardCharsets.UTF_8)))
        return false;
      String email = jwt.getClaimAsString("email");
      return Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))
          && email != null
          && email.length() <= 254
          && email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
    } catch (RuntimeException e) {
      return false;
    }
  }

  static boolean authoritativeEmail(Jwt jwt) {
    String email = jwt.getClaimAsString("email").toLowerCase(Locale.ROOT),
        domain = email.substring(email.lastIndexOf('@') + 1);
    return domain.equals("gmail.com")
        || domain.equalsIgnoreCase(Objects.toString(jwt.getClaimAsString("hd"), ""));
  }
}
