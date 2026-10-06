package com.getlancer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** Validated inquiry JSON commands; semantic and ownership checks stay in services. */
public final class InquiryRequests {
  private InquiryRequests() {}

  public record Create(
      @NotNull UUID referenceProductId,
      @NotBlank @Email @Size(max = 254) String clientEmail,
      @NotBlank @Size(min = 2, max = 100) String clientName,
      @NotBlank @Size(min = 20, max = 5000) String description,
      @NotBlank @Pattern(regexp = "UNDER_1K|USD_1K_3K|USD_3K_10K|USD_10K_25K|USD_25K_PLUS|NEED_ESTIMATE") String budgetBand,
      @NotBlank @Pattern(regexp = "WITHIN_MONTH|ONE_TO_THREE_MONTHS|THREE_TO_SIX_MONTHS|FLEXIBLE") String timelineBand,
      @NotBlank @Pattern(regexp = "SIMILAR_BUILD|CUSTOMIZE|NEW_BUILD|CONSULTATION") String requestType,
      @Size(min = 0, max = 150) String companyName,
      @Size(min = 0, max = 1000) String website,
      @Pattern(regexp = "direct|organic|social|referral|builder_share|founder_outreach|agency_referral") String acquisitionSource) {}

  public record Transition(
      @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal reportedValue,
      @Size(min = 3, max = 3) @Pattern(regexp = "[A-Za-z]{3}") String currency) {}

  public record Report(
      @NotBlank @Pattern(regexp = "PRODUCT|USER|REVIEW|INQUIRY") String targetType,
      @NotNull UUID targetId,
      @NotBlank @Pattern(regexp = "FAKE_PRODUCT|STOLEN_WORK|IMPERSONATION|MALICIOUS_LINK|PHISHING|SPAM|HARASSMENT|COPYRIGHT_IP|CONFIDENTIAL_DATA|MISLEADING_CLAIM|FAKE_REVIEW|CLIENT_SPAM|APPEAL|OTHER") String reason,
      @NotBlank @Size(min = 10, max = 3000) String detail) {}

  public record Decision(
      @NotBlank @Pattern(regexp = "HIRE_CONFIRMATION|COMPLETION_CONFIRMATION") String kind,
      @NotBlank @Pattern(regexp = "ACCEPT|REJECT") String decision) {}

  public record Review(
      @NotNull @Min(1) @Max(5) Integer rating,
      @NotBlank @Size(min = 10, max = 2000) String reviewText,
      @Pattern(regexp = "ANONYMOUS|NAMED") String visibility) {}

  public record Confirmation(
      @Size(min = 0, max = 200) String token,
      @Pattern(regexp = "ACCEPT|REJECT") String decision) {}
}
