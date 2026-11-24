package com.getlancer.admin;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("@authorization.routeAllowed(authentication)")
@RestController
@RequestMapping("/api/v1/admin")
public class QueueSummaryController {
  private final QueueSummaryService service;

  public QueueSummaryController(QueueSummaryService service) {
    this.service = service;
  }

  @GetMapping(value = "/metrics", params = "section=moderation")
  public QueueSummaryService.Totals totals(HttpServletRequest request) {
    return service.totals(request);
  }
}
