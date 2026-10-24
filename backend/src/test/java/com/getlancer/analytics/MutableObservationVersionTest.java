package com.getlancer.analytics;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.sql.Timestamp;
class MutableObservationVersionTest {
 @Test void observedMutableRevisionChangesWithServerTimestamp() {
  UUID id=UUID.randomUUID();Timestamp first=Timestamp.from(java.time.Instant.parse("2026-10-01T00:00:00Z")),next=Timestamp.from(java.time.Instant.parse("2026-10-01T00:00:01Z"));
  String version=AnalyticsService.observedVersion("PROJECT",id,first);
  assertTrue(version.matches("observed-[a-f0-9]{64}"));
  assertEquals(version,AnalyticsService.observedVersion("PROJECT",id,first.toInstant()));
  assertNotEquals(version,AnalyticsService.observedVersion("PROJECT",id,next));
  assertNotEquals(version,AnalyticsService.observedVersion("PROFILE",id,first));
  assertThrows(com.getlancer.shared.ApiError.class,()->AnalyticsService.observedVersion("PROJECT",id,null));
 }
}
