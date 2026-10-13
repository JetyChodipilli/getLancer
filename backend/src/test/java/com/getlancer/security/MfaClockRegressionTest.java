package com.getlancer.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// Regression: ISSUE-002 — separate database and JVM clocks denied fresh administrator MFA.
// Found by /qa on 2026-10-13. Report: docs/audits/2026-10-13-local-audit.md
class MfaClockRegressionTest {
  private GetLancerPrincipal principal(boolean verified, Instant issued, Instant observed) {
    return new GetLancerPrincipal(UUID.randomUUID(), "admin@example.test", observed,
        verified, Set.of("ADMIN"), issued, observed);
  }

  @Test void databaseClockDeterminesFreshnessEvenWhenTheJvmClockDiffers() {
    Instant databaseNow = Instant.parse("2100-01-01T00:00:00Z");
    assertTrue(principal(true, databaseNow, databaseNow).recentMfa());
    assertTrue(principal(true, databaseNow.minusSeconds(899), databaseNow).recentMfa());
  }

  @Test void futureExpiredMissingAndUnverifiedSessionsRemainDenied() {
    Instant databaseNow = Instant.parse("2026-10-13T00:00:00Z");
    assertFalse(principal(true, databaseNow.plusNanos(1), databaseNow).recentMfa());
    assertFalse(principal(true, databaseNow.minusSeconds(900), databaseNow).recentMfa());
    assertFalse(principal(true, null, databaseNow).recentMfa());
    assertFalse(principal(true, databaseNow, null).recentMfa());
    assertFalse(principal(false, databaseNow, databaseNow).recentMfa());
  }
}
