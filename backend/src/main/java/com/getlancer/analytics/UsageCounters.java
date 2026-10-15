package com.getlancer.analytics;

import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Daily aggregate facts have no visitor, artifact ID or free-text payload. */
@Service
public final class UsageCounters {
  private final JdbcTemplate db;
  public UsageCounters(JdbcTemplate db) { this.db = db; }

  public void observation(String stage, String mode, String version) {
    if (!stage.matches("[a-z_]{1,60}") || !Set.of("ALL", "PROJECT", "BROWSER_LOCAL", "SOURCE_ONLY", "LIVE", "TEST").contains(mode)
        || !version.matches("[A-Za-z0-9._-]{1,80}")) throw new IllegalArgumentException("Invalid aggregate vocabulary");
    db.update("INSERT INTO analytics_daily_counts(day,stage,mode,version,observations)"
        + " VALUES((now() AT TIME ZONE 'UTC')::date,?,?,?,1) ON CONFLICT(day,stage,mode,version)"
        + " DO UPDATE SET observations=analytics_daily_counts.observations+1", stage, mode, version);
  }

  /** Runs only after the rejected admission transaction has rolled back. Never stores supplied input. */
  public void admissionDenial(String code) {
    String stage = switch (code) {
      case "LAB_QUOTA_EXHAUSTED", "LAB_ACTIVE_RUN", "LAB_CAPACITY", "IOT_CAPACITY", "DATA_AI_CAPACITY" -> "quota_denial";
      case "INVALID_LAB_INPUT", "INVALID_LAB_SCENARIO", "INVALID_LAB_OPERATION", "INVALID_IDEMPOTENCY_KEY", "VALIDATION_ERROR" -> "validation_denial";
      default -> null;
    };
    if (stage != null) {
      // Epoch-bound totals outlive the rolling raw-event/daily retention windows.
      if(stage.equals("quota_denial"))db.update("UPDATE analytics_instrumentation SET quota_denials=quota_denials+1 WHERE name='v52_operations'");
      else db.update("UPDATE analytics_instrumentation SET validation_denials=validation_denials+1 WHERE name='v52_operations'");
    }
  }
}
