package com.getlancer.products;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.getlancer.notifications.Mail;
import com.getlancer.publishing.PublishingCapacity;
import com.getlancer.security.Security;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ProductDemoHealthTest {
  private static final String URL = "https://example.org/demo";
  private final JdbcTemplate db = mock(JdbcTemplate.class);
  private final ProductRepository repository = mock(ProductRepository.class);
  private final ProductService products =
      new ProductService(
          db,
          mock(Security.class),
          mock(Mail.class),
          repository,
          mock(ShowcaseMeasurements.class),
          mock(PublishingCapacity.class));

  @Test
  void currentProductObservationsNeedNoSourceHashAndKeepTheirCheckedTime() {
    Timestamp checked = Timestamp.from(Instant.now().minusSeconds(300));
    for (String health : List.of("REACHABLE", "UNREACHABLE", "BLOCKED")) {
      var dto = products.dto(observation(health, checked), List.of());
      assertEquals(health, dto.get("demoHealth"));
      assertSame(checked, dto.get("demoCheckedAt"));
    }
    verifyNoInteractions(db, repository);
  }

  @Test
  void staleFutureAndMissingProductObservationsBecomeUnknownWithoutLosingContext() {
    Instant now = Instant.now();
    for (Timestamp checked :
        List.of(
            Timestamp.from(now.minusSeconds(2 * 86400)),
            Timestamp.from(now.plusSeconds(3600)))) {
      var dto = products.dto(observation("REACHABLE", checked), List.of());
      assertEquals("UNKNOWN", dto.get("demoHealth"));
      assertSame(checked, dto.get("demoCheckedAt"));
    }
    var missing = observation("REACHABLE", null);
    assertEquals("UNKNOWN", products.dto(missing, List.of()).get("demoHealth"));
    assertNull(products.dto(missing, List.of()).get("demoCheckedAt"));
    missing.remove("demo_checked_at");
    assertEquals("UNKNOWN", products.dto(missing, List.of()).get("demoHealth"));
  }

  @Test
  void changedOrEmptyDemoUrlCannotReuseAnOtherwiseFreshObservation() {
    Timestamp checked = Timestamp.from(Instant.now().minusSeconds(300));
    var row = observation("REACHABLE", checked);
    row.put("live_url", URL + "?revision=2");
    var dto = products.dto(row, List.of());
    assertEquals("UNKNOWN", dto.get("demoHealth"));
    assertSame(checked, dto.get("demoCheckedAt"));
    row.put("live_url", "");
    row.put("demo_checked_url", "");
    assertEquals("UNKNOWN", products.dto(row, List.of()).get("demoHealth"));
  }

  private static Map<String, Object> observation(String health, Timestamp checked) {
    var row = new HashMap<String, Object>();
    row.put("live_url", URL);
    row.put("demo_checked_url", URL);
    row.put("demo_health", health);
    row.put("demo_checked_at", checked);
    return row;
  }
}
