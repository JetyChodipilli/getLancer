package com.getlancer.security;

import java.security.Principal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record GetLancerPrincipal(UUID userId, String email, Instant emailVerifiedAt,
    boolean mfaVerified, Set<String> roles) implements Principal {
  public GetLancerPrincipal { roles = Set.copyOf(roles); }
  @Override public String getName() { return userId.toString(); }
}
