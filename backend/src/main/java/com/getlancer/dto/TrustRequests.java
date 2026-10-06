package com.getlancer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Validated trust JSON commands; semantic and ownership checks stay in services. */
public final class TrustRequests {
  private TrustRequests() {}

  public record Availability(
      @NotBlank @Pattern(regexp = "AVAILABLE_NOW|ONE_SLOT_LEFT|LIMITED|BOOKED_UNTIL|NOT_ACCEPTING") String status,
      LocalDate bookedUntil) {}

  public record Reason(
      @NotBlank @Size(min = 10, max = 2000) String reason) {}
}
