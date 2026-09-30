package com.getlancer.admin;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
  private final AdminService service;

  public AdminController(AdminService service) {
    this.service = service;
  }

  @GetMapping("/products/pending")
  public Map<String, Object> pending(HttpServletRequest r) {
    return service.pending(r);
  }

  @GetMapping("/profiles/pending")
  public Map<String, Object> profiles(HttpServletRequest r) {
    return service.profiles(r);
  }

  @GetMapping("/reports")
  public Map<String, Object> reports(HttpServletRequest r) {
    return service.reports(r);
  }

  @GetMapping("/reviews")
  public Map<String, Object> reviews(HttpServletRequest r) {
    return service.reviews(r);
  }

  @PostMapping("/products/{id}/{action}")
  public Map<String, Object> productAction(
      @PathVariable UUID id,
      @PathVariable String action,
      @RequestBody Map<String, Object> b,
      HttpServletRequest r) {
    return service.productAction(id, action, b, r);
  }

  @PostMapping("/profiles/{id}/{action}")
  public Map<String, Object> profileAction(
      @PathVariable UUID id,
      @PathVariable String action,
      @RequestBody Map<String, Object> b,
      HttpServletRequest r) {
    return service.profileAction(id, action, b, r);
  }

  @GetMapping("/reports/{id}")
  public Map<String, Object> reportDetail(@PathVariable UUID id, HttpServletRequest request) {
    return service.reportDetail(id, request);
  }

  @PostMapping("/reports/{id}/resolve")
  public Map<String, Object> resolve(
      @PathVariable UUID id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
    return service.resolve(id, body, request);
  }

  @PostMapping("/reviews/{id}/{action}")
  public Map<String, Object> reviewAction(
      @PathVariable UUID id,
      @PathVariable String action,
      @RequestBody Map<String, Object> b,
      HttpServletRequest r) {
    return service.reviewAction(id, action, b, r);
  }

  @PostMapping("/{kind:categories|technologies}")
  public Map<String, Object> taxonomy(
      @PathVariable String kind, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.taxonomy(kind, b, r);
  }

  @GetMapping("/audit")
  public Map<String, Object> history(
      @RequestParam(required = false) UUID targetId, HttpServletRequest r) {
    return service.history(targetId, r);
  }

  @GetMapping("/accounts")
  public Map<String, Object> accounts(
      @RequestParam(defaultValue = "") String q, HttpServletRequest r) {
    return service.accounts(q, r);
  }

  @PostMapping("/accounts/{id}/{action}")
  public Map<String, Object> accountAction(
      @PathVariable UUID id,
      @PathVariable String action,
      @RequestBody Map<String, Object> b,
      HttpServletRequest r) {
    return service.accountAction(id, action, b, r);
  }

  @PostMapping("/inquiries/{id}/{action}")
  public Map<String, Object> inquiryAction(
      @PathVariable UUID id,
      @PathVariable String action,
      @RequestBody Map<String, Object> b,
      HttpServletRequest r) {
    return service.inquiryAction(id, action, b, r);
  }

  @GetMapping("/{kind:categories|technologies}")
  public Map<String, Object> allTaxonomy(@PathVariable String kind, HttpServletRequest r) {
    return service.allTaxonomy(kind, r);
  }

  @GetMapping("/metrics")
  public Map<String, Object> metrics(HttpServletRequest r) {
    return service.metrics(r);
  }
}
