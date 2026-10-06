package com.getlancer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Validated moderation JSON commands; semantic and ownership checks stay in services. */
public final class ModerationRequests {
  private ModerationRequests() {}

  public record Triage(
      @NotBlank @Pattern(regexp = "CRITICAL|HIGH|MEDIUM|LOW") String severity,
      @NotBlank @Pattern(regexp = "OPEN|TRIAGED|UNDER_REVIEW") String status,
      @NotBlank @Size(min = 3, max = 2000) String reason) {}

  public record Appeal(
      @NotNull UUID decisionId,
      @Size(min = 0, max = 1000) String evidenceUrl,
      @NotBlank @Size(min = 20, max = 3000) String statement) {}

  public record Decision(
      @NotBlank @Pattern(regexp = "UNDER_REVIEW|UPHELD|OVERTURNED") String status,
      @NotBlank @Size(min = 3, max = 2000) String reason) {}

  public record Retry(
      @NotBlank @Size(min = 3, max = 2000) String reason) {}
}
