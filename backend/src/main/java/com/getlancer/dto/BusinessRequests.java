package com.getlancer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Validated business JSON commands; semantic and ownership checks stay in services. */
public final class BusinessRequests {
  private BusinessRequests() {}

  public record Profile(
      @NotBlank @Size(min = 2, max = 120) String name,
      @NotBlank @Size(min = 10, max = 2000) String summary) {}

  public record Invitation(
      @NotBlank @Email @Size(max = 254) String email) {}

  public record Response(
      @NotBlank @Pattern(regexp = "ACCEPT|DECLINE") String action) {}

  public record Brief(
      @NotBlank @Size(min = 3, max = 120) String title,
      @NotBlank @Size(min = 20, max = 5000) String description,
      @Size(min = 0, max = 100) String category,
      @Size(min = 0, max = 300) String technology,
      @NotBlank @Size(min = 1, max = 200) String budget,
      @NotBlank @Size(min = 1, max = 200) String timeline,
      Boolean availableOnly,
      Boolean repositoryVerifiedOnly,
      @NotBlank @Pattern(regexp = "DRAFT|OPEN|CLOSED") String status) {}

  public record Shortlist(
      @NotBlank @Pattern(regexp = "BUILDER|TEAM") String kind,
      @NotNull UUID targetId,
      @NotBlank @Size(min = 3, max = 2000) String reason) {}

  public record TalentEntry(
      @NotBlank @Pattern(regexp = "BUILDER|TEAM") String kind,
      @NotNull UUID targetId,
      @Size(min = 0, max = 2000) String reason) {}

  public record TalentList(
      @NotBlank @Size(min = 2, max = 120) String name) {}

  public record ConciergeStatus(
      @NotBlank @Pattern(regexp = "IN_PROGRESS|FULFILLED") String status) {}
}
