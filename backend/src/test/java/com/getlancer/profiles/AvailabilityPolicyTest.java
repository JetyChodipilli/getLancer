package com.getlancer.profiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.getlancer.products.ProductService;
import com.getlancer.publishing.PublishingCapacity;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import com.getlancer.trust.TrustService;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AvailabilityPolicyTest {
  private static final String DISPLAY = "Calendar builder";
  private static final String HEADLINE = "Customer workflow builder";
  private static final String BIO = "I build reproducible customer workflow software.";
  private static final Instant NOW = Instant.parse("2028-03-01T00:30:00Z");

  @Test
  void bothEntryPointsAcceptTodayAndTheInclusiveTwoYearLimitInTheSavedZone() {
    for (CalendarCase calendar : calendars()) {
      for (LocalDate date : List.of(calendar.today(), calendar.today().plusYears(2))) {
        var fixture = new Fixture(calendar);
        fixture.profile.profile(profileBody("BOOKED_UNTIL", date.toString()), fixture.request);
        fixture.trust.availability(
            Map.of("status", "BOOKED_UNTIL", "bookedUntil", date.toString()), fixture.request);
        fixture.verifySaved("BOOKED_UNTIL", Date.valueOf(date));
        verify(fixture.trustDb)
            .queryForObject(
                "SELECT time_zone FROM developer_profiles WHERE user_id=? FOR UPDATE",
                String.class,
                fixture.user);
      }
    }
  }

  @Test
  void bothEntryPointsRejectYesterdayBeyondTwoYearsAndMalformedDatesBeforeWriting() {
    for (CalendarCase calendar : calendars()) {
      for (String date :
          List.of(
              calendar.today().minusDays(1).toString(),
              calendar.today().plusYears(2).plusDays(1).toString(),
              "2028-02-30")) {
        var fixture = new Fixture(calendar);
        ApiError profileError =
            assertThrows(
                ApiError.class,
                () ->
                    fixture.profile.profile(
                        profileBody("BOOKED_UNTIL", date), fixture.request));
        ApiError availabilityError =
            assertThrows(
                ApiError.class,
                () ->
                    fixture.trust.availability(
                        Map.of("status", "BOOKED_UNTIL", "bookedUntil", date), fixture.request));
        assertEquals(400, profileError.status);
        assertEquals(400, availabilityError.status);
        assertEquals(profileError.code, availabilityError.code);
        assertEquals(profileError.fieldErrors, availabilityError.fieldErrors);
        assertTrue(profileError.fieldErrors.containsKey("bookedUntil"));
        verify(fixture.profileDb, never()).update(anyString(), any(Object[].class));
        verify(fixture.trustDb, never()).update(anyString(), any(Object[].class));
      }
    }
  }

  @Test
  void bothEntryPointsClearTheDateWhenAvailabilityIsNoLongerBooked() {
    var fixture = new Fixture(new CalendarCase(NOW, "America/Los_Angeles", "2028-02-29"));
    fixture.profile.profile(profileBody("AVAILABLE_NOW", null), fixture.request);
    fixture.trust.availability(Map.of("status", "AVAILABLE_NOW"), fixture.request);
    fixture.verifySaved("AVAILABLE_NOW", null);
    verify(fixture.trustDb, never())
        .queryForObject(anyString(), eq(String.class), eq(fixture.user));
  }

  private static List<CalendarCase> calendars() {
    return List.of(
        new CalendarCase(NOW, "", "2028-03-01"),
        new CalendarCase(NOW, null, "2028-03-01"),
        new CalendarCase(NOW, "UTC", "2028-03-01"),
        new CalendarCase(NOW, "America/Los_Angeles", "2028-02-29"),
        new CalendarCase(NOW, "Asia/Kolkata", "2028-03-01"),
        new CalendarCase(
            Instant.parse("2028-02-29T23:30:00Z"), "Pacific/Kiritimati", "2028-03-01"));
  }

  private static Map<String, Object> profileBody(String status, String date) {
    var body = new HashMap<String, Object>();
    body.put("displayName", DISPLAY);
    body.put("headline", HEADLINE);
    body.put("bio", BIO);
    body.put("technology", "Java");
    body.put("category", "CRM");
    body.put("availabilityStatus", status);
    if (date != null) body.put("bookedUntil", date);
    return body;
  }

  private record CalendarCase(Instant instant, String zone, LocalDate today) {
    CalendarCase(Instant instant, String zone, String today) {
      this(instant, zone, LocalDate.parse(today));
    }
  }

  private static final class Fixture {
    final JdbcTemplate profileDb = mock(JdbcTemplate.class);
    final JdbcTemplate trustDb = mock(JdbcTemplate.class);
    final Security security = mock(Security.class);
    final HttpServletRequest request = mock(HttpServletRequest.class);
    final UUID user = UUID.randomUUID();
    final ProfileService profile;
    final TrustService trust;

    Fixture(CalendarCase calendar) {
      // A different clock zone ensures neither path accidentally uses the JVM calendar.
      Clock clock = Clock.fixed(calendar.instant(), ZoneId.of("Pacific/Honolulu"));
      when(security.developer(request, false)).thenReturn(user);
      when(security.developer(request, true)).thenReturn(user);
      var previous = new HashMap<String, Object>();
      previous.put("approval_status", "APPROVED");
      previous.put("display_name", DISPLAY);
      previous.put("headline", HEADLINE);
      previous.put("bio", BIO);
      previous.put("technology", "Java");
      previous.put("category", "CRM");
      previous.put("time_zone", calendar.zone());
      for (String key : List.of("github_url", "linkedin_url", "website_url", "country", "languages"))
        previous.put(key, "");
      when(profileDb.queryForMap(anyString(), eq(user))).thenReturn(previous);
      when(trustDb.queryForObject(anyString(), eq(String.class), eq(user)))
          .thenReturn(calendar.zone());
      profile = new ProfileService(profileDb, security, clock);
      trust =
          new TrustService(
              trustDb, security, mock(ProductService.class), mock(PublishingCapacity.class), clock);
    }

    void verifySaved(String status, Date date) {
      verify(profileDb)
          .update(
              startsWith("UPDATE developer_profiles SET display_name="),
              eq(DISPLAY),
              eq(HEADLINE),
              eq(BIO),
              eq("Java"),
              eq("CRM"),
              eq(status),
              eq(date),
              eq(""),
              eq(""),
              eq(user));
      verify(trustDb)
          .update(
              startsWith("UPDATE developer_profiles SET availability_status="),
              eq(status),
              eq(date),
              eq(user));
    }
  }
}
