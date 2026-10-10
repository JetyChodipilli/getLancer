package com.getlancer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Validated commerce JSON commands; semantic and ownership checks stay in services. */
public final class CommerceRequests {
  private CommerceRequests() {}

  public record Template(
      @NotNull UUID productId,
      @NotBlank @Size(min = 3, max = 100) String title,
      @NotBlank @Size(min = 10, max = 240) String summary,
      @NotBlank @Size(min = 20, max = 12000) String description,
      @NotBlank @Size(min = 40, max = 8000) String licenseTerms,
      @NotNull @Min(100) @Max(1000000000) Long priceMinor) {}

  public record Submit(
      Boolean rightsConsent) {}

  public record Review(
      @NotBlank @Pattern(regexp = "APPROVE|CHANGES_REQUESTED|REJECT|SUSPEND") String action,
      @NotBlank @Size(min = 10, max = 2000) String reason,
      Boolean rightsReviewed,
      Boolean packageReviewed) {}

  public record Order(
      @NotNull UUID versionId,
      Boolean licenseConsent,
      UUID educationReleaseId) {}

  public record Dispute(
      @NotBlank @Size(min = 20, max = 2000) String reason) {}

  public record Resolve(
      @NotBlank @Pattern(regexp = "RESUME|REVOKE") String resolution,
      @NotBlank @Size(min = 20, max = 2000) String reason) {}
}
