package com.getlancer.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Validated team JSON commands; semantic and ownership checks stay in services. */
public final class TeamRequests {
  private TeamRequests() {}

  public record Profile(
      @NotBlank @Size(min = 2, max = 120) String name,
      @NotBlank @Size(min = 10, max = 3000) String summary,
      @NotBlank @Size(min = 1, max = 60) String availability,
      @Size(min = 0, max = 200) String projectRange) {}

  public record Invitation(
      @NotNull UUID userId,
      @NotBlank @Pattern(regexp = "OWNER|BUSINESS_MANAGER|RECRUITER|PROJECT_MANAGER|MEMBER") String role,
      @NotBlank @Pattern(regexp = "PERMANENT|CONTRACT") String membershipType,
      @Future Instant expiresAt,
      @Size(min = 0, max = 200) String projectLabel) {}

  public record Response(
      @NotBlank @Pattern(regexp = "ACCEPT|DECLINE") String action) {}

  public record MemberRole(
      @NotBlank @Pattern(regexp = "OWNER|BUSINESS_MANAGER|RECRUITER|PROJECT_MANAGER|MEMBER") String role) {}

  public record OpenRole(
      @NotBlank @Size(min = 2, max = 120) String title,
      @NotBlank @Size(min = 10, max = 5000) String description,
      @Size(min = 0, max = 500) String skills,
      @NotBlank @Pattern(regexp = "PERMANENT|CONTRACT") String contractType,
      @Size(min = 0, max = 200) String compensationBand) {}

  public record RoleStatus(
      @NotBlank @Pattern(regexp = "OPEN|CLOSED") String status) {}

  public record Application(
      @NotBlank @Size(min = 1, max = 3000) String message) {}

  public record ApplicationDecision(
      @NotBlank @Pattern(regexp = "SHORTLISTED|REJECTED|INVITED") String status) {}

  public record Lead(
      @NotBlank @Size(min = 3, max = 120) String title,
      @NotBlank @Size(min = 10, max = 5000) String description,
      @Size(min = 0, max = 200) String budget,
      @Size(min = 0, max = 200) String timeline) {}

  public record Project(
      @NotNull UUID productId) {}

  public record Staffing(
      @NotNull UUID userId,
      @NotBlank @Size(min = 1, max = 200) String projectLabel,
      @Size(min = 0, max = 500) String skills,
      @NotNull @Future Instant endsAt) {}

  public record StaffingStatus(
      @NotBlank @Pattern(regexp = "ACTIVE|COMPLETED") String status) {}

  public record Moderate(
      @NotBlank @Pattern(regexp = "ACTIVE|SUSPENDED") String status,
      @NotBlank @Size(min = 10, max = 3000) String reason) {}
}
