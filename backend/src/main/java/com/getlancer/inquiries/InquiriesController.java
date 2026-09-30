package com.getlancer.inquiries;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class InquiriesController {
  private final InquiryService service;

  public InquiriesController(InquiryService service) {
    this.service = service;
  }

  @PostMapping("/inquiries")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Map<String, Object> create(
      @RequestBody Map<String, Object> b,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return service.create(b, key);
  }

  @GetMapping("/developer/inquiries")
  public Map<String, Object> list(HttpServletRequest r) {
    return service.list(r);
  }

  @GetMapping("/developer/inquiries/{id}")
  public Map<String, Object> detail(@PathVariable UUID id, HttpServletRequest r) {
    return service.detail(id, r);
  }

  @PostMapping("/inquiries/{id}/{action}")
  public Map<String, Object> transition(
      @PathVariable UUID id,
      @PathVariable String action,
      @RequestBody(required = false) Map<String, Object> body,
      HttpServletRequest r) {
    return service.transition(id, action, body, r);
  }

  @PostMapping("/reports")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Map<String, Object> report(
      @RequestBody Map<String, Object> b, HttpServletRequest request) {
    return service.report(b, request);
  }

  @GetMapping("/developer/analytics")
  public Map<String, Object> analytics(HttpServletRequest r) {
    return service.analytics(r);
  }

  @GetMapping("/notifications")
  public Map<String, Object> notifications(HttpServletRequest r) {
    return service.notifications(r);
  }
}
