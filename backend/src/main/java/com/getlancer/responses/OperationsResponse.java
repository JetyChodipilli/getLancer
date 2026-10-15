package com.getlancer.responses;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Aggregate operations projection. No identities, request inputs, credentials or source bytes. */
public record OperationsResponse(Window window, List<Funnel> funnels, Runtime runtime,
    List<Alert> alerts, Readiness readiness, Resources resources) {
  public record Window(Instant from, Instant to, int days, String privacy, Instant observationStartedAt) {}
  public record Funnel(String stage, String mode, String version, long observations,
      long denominator, BigDecimal ratePercent, String denominatorLabel) {}
  public record Runtime(long admitted, long healthy, long pending, long completed,
      long infraFailures, BigDecimal infraSuccessPercent, BigDecimal coveragePercent,
      long scenarioFailures, long quotaDenials, long validationDenials,
      String counterEpoch, Instant measuredFrom, UUID operatorEpoch) {}
  public record Alert(String code, String severity, long count, String detail) {}
  public record Lane(boolean enabled, boolean configurationReady, String reason) {}
  public record CommerceLane(boolean enabled, boolean configurationReady, String mode, String reason) {}
  public record Readiness(Lane runtime, CommerceLane commerce, boolean productionCertified) {}
  public record Resources(long active, long memoryMiB, long dailyReservedMicros,
      long monthlyReservedMicros, long dailyBudgetMicros, long monthlyBudgetMicros) {}
}
