package com.getlancer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Validated oauth JSON commands; semantic and ownership checks stay in services. */
public final class OAuthRequests {
  private OAuthRequests() {}

  public record Start(
      @NotBlank @Pattern(regexp = "login|signup") String intent,
      Boolean acceptedTerms,
      Boolean rememberMe) {}
}
