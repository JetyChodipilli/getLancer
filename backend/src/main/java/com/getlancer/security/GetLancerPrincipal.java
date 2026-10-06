package com.getlancer.security;

import java.security.Principal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record GetLancerPrincipal(UUID userId, String email, Instant emailVerifiedAt,
    boolean mfaVerified, Set<String> roles, Instant issuedAt) implements Principal {
  public GetLancerPrincipal { roles = Set.copyOf(roles); }
  public GetLancerPrincipal(UUID userId, String email, Instant emailVerifiedAt,
      boolean mfaVerified, Set<String> roles) {
    this(userId, email, emailVerifiedAt, mfaVerified, roles, Instant.now());
  }
  public boolean recentMfa() {
    Instant now = Instant.now();
    return mfaVerified && issuedAt != null && !issuedAt.isAfter(now)
        && issuedAt.isAfter(now.minusSeconds(15 * 60));
  }
  @Override public String getName() { return userId.toString(); }
}
