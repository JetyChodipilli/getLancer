package com.getlancer.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Validated slot JSON commands; semantic and ownership checks stay in services. */
public final class SlotRequests {
  private SlotRequests() {}

  public record Order(
      @JsonDeserialize(using = WholeNumber.class) @NotNull @Min(100) @Max(1000000000) Long amountMinor,
      Boolean purchaseConsent) {}

  public record PublishingOrder(
      @NotBlank @Pattern(regexp = "(?i)PROJECT|TEMPLATE|COMPONENT") String pool,
      @JsonDeserialize(using = WholeNumber.class) @NotNull @Min(100) @Max(1000000000) Long amountMinor,
      Boolean purchaseConsent) {}

  public record Price(
      @JsonDeserialize(using = WholeNumber.class) @NotNull @Min(100) @Max(1000000000) Long amountMinor,
      Boolean enabled) {}
}
