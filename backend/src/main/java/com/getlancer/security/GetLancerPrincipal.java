package com.getlancer.security;

import java.security.Principal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record GetLancerPrincipal(UUID userId, String email, Instant emailVerifiedAt,
    boolean mfaVerified, Set<String> roles, Instant issuedAt, Instant observedAt) implements Principal {
  public GetLancerPrincipal { roles = Set.copyOf(roles); }
  public GetLancerPrincipal(UUID userId, String email, Instant emailVerifiedAt,
      boolean mfaVerified, Set<String> roles, Instant issuedAt) {
    this(userId, email, emailVerifiedAt, mfaVerified, roles, issuedAt, Instant.now());
  }
  public GetLancerPrincipal(UUID userId, String email, Instant emailVerifiedAt,
      boolean mfaVerified, Set<String> roles) {
    this(userId, email, emailVerifiedAt, mfaVerified, roles, Instant.now());
  }
  public boolean recentMfa() {
    // Production requests observe and issue sessions with the database clock.
    return mfaVerified && issuedAt != null && observedAt != null && !issuedAt.isAfter(observedAt)
        && issuedAt.isAfter(observedAt.minusSeconds(15 * 60));
  }
  @Override public String getName() { return userId.toString(); }
}
