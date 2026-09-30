package com.getlancer.profiles;

import static com.getlancer.shared.Support.*;

import com.getlancer.shared.ApiError;
import com.getlancer.shared.Rules;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public record ProfileDetails(String websiteUrl, String country, String timeZone, String languages) {
  public static ProfileDetails read(Map<String, Object> body, Map<String, Object> previous) {
    String website = optional(body, previous, "websiteUrl", "website_url", 500);
    Rules.safeUrl(website);
    String country = optional(body, previous, "country", "country", 2).toUpperCase(Locale.ROOT);
    if (!country.isBlank() && !Set.of(Locale.getISOCountries()).contains(country))
      throw invalid("country", "Use a two-letter country code, such as IN.");
    String zone = optional(body, previous, "timeZone", "time_zone", 100);
    if (!zone.isBlank() && !ZoneId.getAvailableZoneIds().contains(zone))
      throw invalid("timeZone", "Use an IANA time zone, such as Asia/Kolkata.");
    return new ProfileDetails(
        website, country, zone, optional(body, previous, "languages", "languages", 200));
  }

  static String optional(
      Map<String, Object> body,
      Map<String, Object> previous,
      String field,
      String column,
      int max) {
    return text(
        Map.of(
            field,
            Objects.toString(body.containsKey(field) ? body.get(field) : previous.get(column), "")),
        field,
        0,
        max);
  }

  public java.sql.Date bookedUntil(Map<String, Object> body, String availability) {
    if (!availability.equals("BOOKED_UNTIL")) return null;
    try {
      LocalDate date = LocalDate.parse(text(body, "bookedUntil", 10, 10));
      if (date.isBefore(LocalDate.now(timeZone.isBlank() ? ZoneId.of("UTC") : ZoneId.of(timeZone))))
        throw invalid("bookedUntil", "Choose today or a future date in your time zone.");
      return java.sql.Date.valueOf(date);
    } catch (java.time.format.DateTimeParseException e) {
      throw invalid("bookedUntil", "Choose a valid booked-until date.");
    }
  }

  public boolean changed(Map<String, Object> previous) {
    return !websiteUrl.equals(Objects.toString(previous.get("website_url"), ""))
        || !country.equals(Objects.toString(previous.get("country"), ""))
        || !timeZone.equals(Objects.toString(previous.get("time_zone"), ""))
        || !languages.equals(Objects.toString(previous.get("languages"), ""));
  }

  static ApiError invalid(String field, String message) {
    return new ApiError(400, "VALIDATION_ERROR", message, Map.of(field, message));
  }
}
