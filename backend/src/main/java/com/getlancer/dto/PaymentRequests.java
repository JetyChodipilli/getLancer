package com.getlancer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Validated payment JSON commands; semantic and ownership checks stay in services. */
public final class PaymentRequests {
  private PaymentRequests() {}

  public record Checkout(
      @NotBlank @Size(min = 10, max = 40) @Pattern(regexp = "pay_[A-Za-z0-9]+") String razorpay_payment_id,
      @Size(min = 10, max = 40) @Pattern(regexp = "order_[A-Za-z0-9]+") String razorpay_order_id,
      @NotBlank @Size(min = 64, max = 64) String razorpay_signature) {}

  public record Bind(
      @NotBlank @Size(min = 10, max = 40) @Pattern(regexp = "order_[A-Za-z0-9]+") String orderId,
      @Size(max = 2000) String reason) {}

  public record Account(
      UUID builderUserId,
      UUID teamId,
      @NotBlank @Size(min = 10, max = 40) @Pattern(regexp = "acc_[A-Za-z0-9]+") String accountId,
      Boolean activationConfirmed) {}
}
