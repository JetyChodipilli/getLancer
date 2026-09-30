package com.getlancer.business;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ConciergeController {
  private final ConciergeService service;

  public ConciergeController(ConciergeService service) {
    this.service = service;
  }

  @GetMapping("/admin/concierge")
  public Map<String, Object> queue(HttpServletRequest r) {
    return service.queue(r);
  }

  @GetMapping("/admin/concierge/{id}/matches")
  public Map<String, Object> matches(@PathVariable UUID id, HttpServletRequest r) {
    return service.matches(id, r);
  }

  @PatchMapping("/admin/concierge/{id}")
  public Map<String, Object> status(
      @PathVariable UUID id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.status(id, b, r);
  }

  @PostMapping("/admin/concierge/{id}/shortlist")
  public Map<String, Object> save(
      @PathVariable UUID id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.save(id, b, r);
  }
}
