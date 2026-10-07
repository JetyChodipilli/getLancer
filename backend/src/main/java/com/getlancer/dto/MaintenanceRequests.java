package com.getlancer.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Validated maintenance JSON commands; semantic and ownership checks stay in services. */
public final class MaintenanceRequests {
  private MaintenanceRequests() {}

  public record Terms(
      @NotNull UUID engagementId,
      @NotBlank @Size(min = 3, max = 100) String title,
      @NotBlank @Size(min = 20, max = 4000) String scope,
      @NotBlank @Size(min = 40, max = 8000) String terms,
      @JsonDeserialize(using = WholeNumber.class) @NotNull @Min(100) @Max(1000000000) Long amountMinor,
      @JsonDeserialize(using = WholeNumber.class) @NotNull @Min(1) @Max(50) Long requestsPerCycle,
      @JsonDeserialize(using = WholeNumber.class) @NotNull @Min(1) @Max(720) Long responseHours,
      @JsonDeserialize(using = WholeNumber.class) @NotNull @Min(1) @Max(12) Long totalCycles,
      @Pattern(regexp = "INR") String currency) {}

  public record OfferAction(
      Boolean consent,
      @Size(min = 0, max = 128) String digest) {}

  public record Billing(
      Boolean billingConsent) {}

  public record Checkout(
      @NotBlank @Size(min = 9, max = 40) @Pattern(regexp = "sub_[A-Za-z0-9]+") String razorpay_subscription_id,
      @NotBlank @Size(min = 10, max = 40) @Pattern(regexp = "pay_[A-Za-z0-9]+") String razorpay_payment_id,
      @Size(min = 10, max = 40) @Pattern(regexp = "order_[A-Za-z0-9]+") String razorpay_order_id,
      @NotBlank @Size(min = 64, max = 64) String razorpay_signature) {}

  public record Support(
      @NotBlank @Size(min = 3, max = 100) String title,
      @NotBlank @Size(min = 20, max = 4000) String description) {}

  public record SupportAction(
      @Size(min = 0, max = 4000) String deliveryNote,
      @Size(min = 0, max = 500) String deliveryUrl,
      @Size(min = 0, max = 2000) String reason) {}

  public record Operator(
      @NotBlank @Size(min = 10, max = 1000) String reason,
      @Size(min = 0, max = 40) String providerId,
      UUID periodId) {}
}
