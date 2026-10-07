package com.getlancer.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Validated delivery JSON commands; semantic and ownership checks stay in services. */
public final class DeliveryRequests {
  private DeliveryRequests() {}

  public record Source(
      UUID inquiryId,
      UUID businessRequestId,
      @Pattern(regexp = "BUILDER|TEAM") String candidateKind,
      UUID candidateId) {}

  public record Proposal(
      @NotBlank @Size(min = 20, max = 10000) String scope,
      @NotBlank @Size(min = 10, max = 5000) String terms,
      @Pattern(regexp = "INR") String currency,
      @JsonDeserialize(using = WholeNumber.class) @Min(100) @Max(1000000000) Long amountMinor,
      @NotNull @Size(min = 1, max = 20) List<@NotNull @Valid Milestone> milestones) {}

  public record Milestone(
      @NotBlank @Size(min = 2, max = 120) String title,
      @NotBlank @Size(min = 10, max = 3000) String description,
      @JsonDeserialize(using = WholeNumber.class) @NotNull @Min(100) @Max(1000000000) Long amountMinor,
      @NotNull LocalDate dueDate) {}

  public record ProposalAction(
      Boolean consent) {}

  public record MilestoneAction(
      @Size(min = 0, max = 5000) String deliveryNote,
      @Size(min = 0, max = 2000) String deliveryUrl,
      @Size(min = 0, max = 2000) String reason,
      Boolean consent) {}

  public record Dispute(
      @NotBlank @Size(min = 10, max = 3000) String reason) {}

  public record Resolve(
      @NotBlank @Pattern(regexp = "RESUME|CANCEL") String resolution,
      @NotBlank @Size(min = 10, max = 3000) String reason) {}
}
