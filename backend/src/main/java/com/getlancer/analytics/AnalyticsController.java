package com.getlancer.analytics;

import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {
  private final AnalyticsService service;

  public AnalyticsController(AnalyticsService service) {
    this.service = service;
  }

  @PostMapping("/events")
  public Map<String, Object> record(@RequestBody Map<String, Object> body) {
    return service.record(body);
  }
}
