package com.getlancer.accounts;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PrivacyController {
  private final PrivacyService service;

  public PrivacyController(PrivacyService service) {
    this.service = service;
  }

  @GetMapping("/policies/config")
  public Map<String, Object> config() {
    return service.config();
  }

  @GetMapping("/admin/deletion-requests")
  public Map<String, Object> requests(HttpServletRequest r) {
    return service.requests(r);
  }

  @PostMapping("/admin/deletion-requests/{id}/review")
  public Map<String, Object> review(
      @PathVariable UUID id, @RequestBody Map<String, Object> body, HttpServletRequest r) {
    return service.review(id, body, r);
  }
}
