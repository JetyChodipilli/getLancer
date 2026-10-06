package com.getlancer.analytics;

import com.getlancer.dto.AnalyticsRequests;
import com.getlancer.shared.TypedInputs;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("@authorization.routeAllowed(authentication)")
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {
  private final AnalyticsService service;

  public AnalyticsController(AnalyticsService service) {
    this.service = service;
  }

  @PostMapping("/events")
  public Map<String, Object> record(@Valid @RequestBody AnalyticsRequests.Event body) {
    return service.record(TypedInputs.map(body));
  }
}
