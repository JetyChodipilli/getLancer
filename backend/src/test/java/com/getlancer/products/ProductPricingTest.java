package com.getlancer.products;

import static org.junit.jupiter.api.Assertions.*;
import com.getlancer.shared.ApiError;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductPricingTest {
  @Test void modesAndBounds() {
    assertEquals("NONE", ProductPricing.parse(Map.of()).mode());
    assertNull(ProductPricing.parse(Map.of("pricingMode", "CUSTOM_QUOTE")).min());
    var range = ProductPricing.parse(Map.of("pricingMode", "RANGE", "priceMinMinor", 12500, "priceMaxMinor", 25000, "currency", "INR"));
    assertEquals(12500L, range.min());
    assertEquals(25000L, range.max());
    assertNull(ProductPricing.parse(Map.of("pricingMode", "STARTING_FROM", "priceMinMinor", 0, "currency", "USD")).max());
  }
  @Test void rejectsAmbiguousOrInvalidNumericTerms() {
    for (Object value : new Object[] {-1, 1.5, "NaN", "100000000001", ""})
      assertThrows(ApiError.class, () -> ProductPricing.parse(Map.of("pricingMode", "STARTING_FROM", "priceMinMinor", value, "currency", "USD")));
    assertThrows(ApiError.class, () -> ProductPricing.parse(Map.of("pricingMode", "RANGE", "priceMinMinor", 20, "priceMaxMinor", 10, "currency", "USD")));
    assertThrows(ApiError.class, () -> ProductPricing.parse(Map.of("pricingMode", "STARTING_FROM", "priceMinMinor", 20, "currency", "ZZZ")));
    assertThrows(ApiError.class, () -> ProductPricing.parse(Map.of("pricingMode", "NONE", "priceMinMinor", 20)));
    assertThrows(ApiError.class, () -> ProductPricing.parse(Map.of("pricingMode", "FIXED")));
  }
}
