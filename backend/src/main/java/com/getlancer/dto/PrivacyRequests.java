package com.getlancer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Validated privacy JSON commands; semantic and ownership checks stay in services. */
public final class PrivacyRequests {
  private PrivacyRequests() {}

  public record Review(
      @NotBlank @Pattern(regexp = "HOLD|ANONYMIZE_PROFILE") String action,
      @NotBlank @Size(min = 10, max = 2000) String reason) {}
}
