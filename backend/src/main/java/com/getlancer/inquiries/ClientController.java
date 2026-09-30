package com.getlancer.inquiries;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/inquiries")
public class ClientController {
  private final ClientInquiryService service;

  public ClientController(ClientInquiryService service) {
    this.service = service;
  }

  @GetMapping
  public Map<String, Object> list(HttpServletRequest request) {
    return service.list(request);
  }

  @GetMapping("/{id}")
  public Map<String, Object> detail(@PathVariable UUID id, HttpServletRequest request) {
    return service.detail(id, request);
  }

  @PostMapping("/{id}/decision")
  public Map<String, Object> decision(
      @PathVariable UUID id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
    return service.decision(id, body, request);
  }

  @PostMapping("/{id}/review")
  public Map<String, Object> review(
      @PathVariable UUID id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
    return service.review(id, body, request);
  }

  @PostMapping("/{id}/confirmation-link")
  public Map<String, Object> resend(@PathVariable UUID id, HttpServletRequest request) {
    return service.resend(id, request);
  }

  @PostMapping("/{id}/not-hired")
  public Map<String, Object> close(@PathVariable UUID id, HttpServletRequest request) {
    return service.close(id, request);
  }
}
