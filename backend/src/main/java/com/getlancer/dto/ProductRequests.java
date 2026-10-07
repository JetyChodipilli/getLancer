package com.getlancer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Validated product JSON commands; semantic and ownership checks stay in services. */
public final class ProductRequests {
  private ProductRequests() {}

  public record Mutation(
      @NotBlank @Size(min = 3, max = 120) String title,
      @NotBlank @Size(min = 5, max = 240) String summary,
      @NotBlank @Size(min = 20, max = 10000) String description,
      @NotBlank @Pattern(regexp = "COMMERCIAL|CLIENT|SAAS|PERSONAL|OPEN_SOURCE|PROTOTYPE|HACKATHON|LEARNING|PRIVATE_CASE_STUDY|NDA_CONFIDENTIAL") String projectType,
      @NotBlank @Size(min = 1, max = 100) String category,
      @Size(min = 0, max = 300) String technology,
      @NotBlank @Pattern(regexp = "PUBLIC|PRIVATE_CASE_STUDY|NDA_SAFE") String visibility,
      @NotBlank @Size(min = 10, max = 3000) String contribution,
      @Size(min = 0, max = 1000) String liveUrl,
      @Size(min = 0, max = 1000) String videoUrl,
      @Size(min = 0, max = 1000) String repositoryUrl,
      @Size(min = 0, max = 300) String pricingNote,
      Boolean availableForSimilarWork,
      Boolean rightsConfirmed,
      @Pattern(regexp = "NONE|STARTING_FROM|RANGE|CUSTOM_QUOTE") String pricingMode,
      @Min(0) @Max(100000000000L) Long priceMinMinor,
      @Min(0) @Max(100000000000L) Long priceMaxMinor,
      @Pattern(regexp = "[A-Z]{3}") String currency) {}

  public record Access(
      @NotBlank @Email @Size(max = 254) String email) {}
}
