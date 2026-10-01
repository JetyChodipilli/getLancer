package com.getlancer.products;

import com.getlancer.shared.ApiError;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Optional indicative showcase prices. They never create a checkout order. */
public record ProductPricing(String mode, Long min, Long max, String currency) {
  public static ProductPricing parse(Map<String, Object> body) {
    String mode = Objects.toString(body.get("pricingMode"), "NONE");
    if (!Set.of("NONE", "STARTING_FROM", "RANGE", "CUSTOM_QUOTE").contains(mode))
      throw invalid("pricingMode", "Choose a supported pricing mode.");
    if (mode.equals("NONE") || mode.equals("CUSTOM_QUOTE")) {
      if (body.get("priceMinMinor") != null || body.get("priceMaxMinor") != null)
        throw invalid("pricingMode", "Remove amounts when no numeric price is listed.");
      return new ProductPricing(mode, null, null, null);
    }
    String currency = Objects.toString(body.get("currency"), "");
    try {
      if (!currency.matches("[A-Z]{3}")) throw new IllegalArgumentException();
      Currency.getInstance(currency);
    } catch (IllegalArgumentException e) {
      throw invalid("currency", "Choose a valid three-letter currency.");
    }
    Long min = amount(body.get("priceMinMinor"), "priceMinMinor");
    Long max = mode.equals("RANGE") ? amount(body.get("priceMaxMinor"), "priceMaxMinor") : null;
    if (mode.equals("STARTING_FROM") && body.get("priceMaxMinor") != null)
      throw invalid("priceMaxMinor", "Starting prices have no maximum.");
    if (max != null && max < min) throw invalid("priceMaxMinor", "Maximum must be at least the minimum.");
    return new ProductPricing(mode, min, max, currency);
  }

  private static Long amount(Object value, String field) {
    try {
      long number = new BigDecimal(Objects.toString(value, "")).longValueExact();
      if (number < 0 || number > 100000000000L) throw new ArithmeticException();
      return number;
    } catch (NumberFormatException | ArithmeticException e) {
      throw invalid(field, "Use a whole minor-unit amount between 0 and 100000000000.");
    }
  }

  private static ApiError invalid(String field, String message) {
    return new ApiError(400, "VALIDATION_ERROR", message, Map.of(field, message));
  }
}
