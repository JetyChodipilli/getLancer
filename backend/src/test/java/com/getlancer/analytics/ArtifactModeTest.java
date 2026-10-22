package com.getlancer.analytics;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.getlancer.components.ComponentService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ArtifactModeTest {
  @Test void componentObservationsUseReviewedExecutionModeInsteadOfKindOrClientClaims() {
    for(String mode:new String[]{"Browser local","Source only","unsupported"}) {
      var db=mock(JdbcTemplate.class,call->call.getMethod().getName().equals("update")?1:RETURNS_DEFAULTS.answer(call));
      var components=mock(ComponentService.class);var counters=new UsageCounters(db);
      when(components.detail("released-component")).thenReturn(Map.of("kind","FRONTEND","executionMode",mode,"sha256","a".repeat(64)));
      var analytics=new AnalyticsService(db,true,"",components,null,counters);
      assertEquals(true,analytics.record(Map.of("consent",true,"eventName","component_source_view","artifactSlug","released-component","eventId",UUID.randomUUID(),"properties",Map.of("mode","LIVE"))).get("recorded"));
      verify(db).update(startsWith("INSERT INTO analytics_daily_counts"),eq("component_source_view"),eq(mode.equals("Browser local")?"BROWSER_LOCAL":"SOURCE_ONLY"),eq("a".repeat(64)));
    }
  }
}
