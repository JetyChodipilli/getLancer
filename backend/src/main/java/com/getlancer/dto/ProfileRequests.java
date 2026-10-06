package com.getlancer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Validated profile JSON commands; semantic and ownership checks stay in services. */
public final class ProfileRequests {
  private ProfileRequests() {}

  public record Profile(
      @NotBlank @Size(min = 2, max = 100) String displayName,
      @NotBlank @Size(min = 5, max = 160) String headline,
      @NotBlank @Size(min = 20, max = 3000) String bio,
      @NotBlank @Size(min = 1, max = 300) String technology,
      @NotBlank @Size(min = 1, max = 200) String category,
      @NotBlank @Pattern(regexp = "AVAILABLE_NOW|ONE_SLOT_LEFT|LIMITED|BOOKED_UNTIL|NOT_ACCEPTING") String availabilityStatus,
      @Size(min = 0, max = 500) String githubUrl,
      @Size(min = 0, max = 500) String linkedinUrl,
      @Size(min = 0, max = 500) String websiteUrl,
      @Size(min = 0, max = 2) String country,
      @Size(min = 0, max = 100) String timeZone,
      @Size(min = 0, max = 200) String languages,
      LocalDate bookedUntil) {}
}
