package com.getlancer.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Validated component JSON commands; semantic and ownership checks stay in services. */
public final class ComponentRequests {
  private ComponentRequests() {}

  public record Mutation(
      @NotBlank @Size(min = 3, max = 100) String recipeSlug,
      @NotBlank @Size(min = 3, max = 100) String title,
      @NotBlank @Size(min = 10, max = 240) String summary,
      @NotBlank @Size(min = 20, max = 2000) String contribution,
      Boolean rightsConsent) {}

  public record Review(
      @JsonDeserialize(using = WholeNumber.class) @Min(1) @Max(2147483647) Long revision,
      @NotBlank @Pattern(regexp = "APPROVE|CHANGES_REQUESTED|SUSPEND") String decision,
      @NotBlank @Size(min = 20, max = 2000) String reason,
      @Pattern(regexp = "[a-f0-9]{64}") String sourceHash) {}

  public record College(
      UUID productId,
      @NotBlank @Pattern(regexp = "FULL_STACK|DATA_ANALYTICS|AI_ML|IOT") String category,
      @NotBlank @Size(min = 1, max = 100) String language,
      @NotBlank @Size(min = 20, max = 2000) String problem,
      @NotBlank @Size(min = 20, max = 2000) String outcome,
      @NotBlank @Size(min = 10, max = 2000) String prerequisites,
      @NotBlank @Size(min = 20, max = 2000) String contribution,
      @Size(min = 0, max = 160) String institution,
      @Size(min = 0, max = 40) String academicYear,
      @Size(min = 0, max = 100) String branch,
      Boolean shareAcademicDetails,
      Boolean rightsConsent) {}

  public record CollegeReview(
      @JsonDeserialize(using = WholeNumber.class) @Min(1) @Max(2147483647) Long revision,
      @NotBlank @Pattern(regexp = "APPROVED|CHANGES_REQUESTED|SUSPENDED") String decision,
      @NotBlank @Size(min = 20, max = 2000) String reason) {}
}
