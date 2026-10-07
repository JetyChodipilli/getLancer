package com.getlancer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Validated hosting JSON commands; semantic and ownership checks stay in services. */
public final class HostingRequests {
  private HostingRequests() {}

  public record Submit(
      Boolean rightsConsent) {}

  public record Review(
      @NotBlank @Pattern(regexp = "APPROVE|CHANGES_REQUESTED|REJECT|SUSPEND") String action,
      @NotBlank @Size(min = 10, max = 2000) String reason,
      Boolean rightsReviewed,
      Boolean packageReviewed) {}

  public record Reason(
      @NotBlank @Size(min = 10, max = 2000) String reason) {}
}
