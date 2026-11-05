package com.getlancer.profiles;

import static com.getlancer.shared.Support.text;

import com.getlancer.shared.ApiError;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Map;

/** Shared calendar policy for profile edits and dedicated availability updates. */
public final class AvailabilityDates {
  private AvailabilityDates() {}

  public static java.sql.Date bookedUntil(
      Map<String, Object> body, String availability, String timeZone, Clock clock) {
    if (!"BOOKED_UNTIL".equals(availability)) return null;
    ZoneId zone;
    try {
      zone = timeZone == null || timeZone.isBlank() ? ZoneOffset.UTC : ZoneId.of(timeZone);
    } catch (DateTimeException e) {
      throw invalid("timeZone", "Use an IANA time zone, such as Asia/Kolkata.");
    }
    LocalDate date;
    try {
      date = LocalDate.parse(text(body, "bookedUntil", 10, 10));
    } catch (DateTimeParseException e) {
      throw invalid("bookedUntil", "Choose a valid booked-until date.");
    }
    LocalDate today = LocalDate.now(clock.withZone(zone));
    if (date.isBefore(today) || date.isAfter(today.plusYears(2)))
      throw invalid("bookedUntil", "Choose today through two years ahead in your time zone.");
    return java.sql.Date.valueOf(date);
  }

  private static ApiError invalid(String field, String message) {
    return new ApiError(400, "VALIDATION_ERROR", message, Map.of(field, message));
  }
}
