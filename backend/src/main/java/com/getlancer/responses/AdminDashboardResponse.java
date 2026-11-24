package com.getlancer.responses;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Aggregate admin data. Queue records and sensitive operational details use their existing routes. */
public record AdminDashboardResponse(Window window, Outcomes outcomes, List<Week> trend,
    List<Queue> queues, Catalog catalog, List<Commerce> commerce, Readiness readiness) {
  public record Window(int weeks, Instant from, Instant asOf) {}
  public record Outcomes(long qualified, long hired, long completed, long responded, long eligible,
      BigDecimal responsePercent, BigDecimal medianResponseHours, BigDecimal inquiryToHirePercent,
      BigDecimal hireToCompletionPercent) {}
  public record Week(LocalDate start, long qualified) {}
  public record Queue(String key, long count, Instant oldest, String severity) {}
  public record Catalog(long activeShowcases, long demosUnreachable) {}
  public record Commerce(String product, String mode, String currency, String capturedMinor, String refundedMinor) {}
  public record Readiness(boolean runtimeEnabled, boolean livePaymentsEnabled, boolean productionCertified) {}
}
