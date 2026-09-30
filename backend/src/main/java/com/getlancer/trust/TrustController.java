package com.getlancer.trust;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class TrustController {
  private final TrustService service;

  public TrustController(TrustService service) {
    this.service = service;
  }

  @GetMapping("/developer/trust")
  public Map<String, Object> overview(HttpServletRequest r) {
    return service.overview(r);
  }

  @PutMapping("/developer/availability")
  public Map<String, Object> availability(
      @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.availability(b, r);
  }

  @PostMapping("/developer/products/{id}/verification")
  public Map<String, Object> request(@PathVariable UUID id, HttpServletRequest r) {
    return service.request(id, r);
  }

  @GetMapping("/admin/trust")
  public Map<String, Object> queue(HttpServletRequest r) {
    return service.queue(r);
  }

  @PostMapping("/admin/verifications/{id}/{action}")
  public Map<String, Object> review(
      @PathVariable UUID id,
      @PathVariable String action,
      @RequestBody Map<String, Object> b,
      HttpServletRequest r) {
    return service.review(id, action, b, r);
  }

  @PostMapping("/admin/earned-capacity/{id}")
  public Map<String, Object> award(
      @PathVariable UUID id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.award(id, b, r);
  }

  @GetMapping("/products/{slug}/similar-builders")
  public Map<String, Object> similar(@PathVariable String slug) {
    return service.similar(slug);
  }
}
