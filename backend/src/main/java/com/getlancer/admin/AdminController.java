package com.getlancer.admin;

import com.getlancer.dto.AdminRequests;
import com.getlancer.shared.TypedInputs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("@authorization.routeAllowed(authentication)")
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
  public Object profiles(HttpServletRequest r) {
    return service.profiles(r);
  }

  @GetMapping("/reports")
  public Object reports(HttpServletRequest r) {
    return service.reports(r);
  }

  @GetMapping("/reviews")
  public Object reviews(HttpServletRequest r) {
    return service.reviews(r);
  }

  @PostMapping("/products/{id}/{action}")
  public Map<String, Object> productAction(
      @PathVariable UUID id,
      @PathVariable String action,
      @Valid @RequestBody AdminRequests.Reason b,
      HttpServletRequest r) {
    return service.productAction(id, action, TypedInputs.map(b), r);
  }

  @PostMapping("/profiles/{id}/{action}")
  public Map<String, Object> profileAction(
      @PathVariable UUID id,
      @PathVariable String action,
      @Valid @RequestBody AdminRequests.Reason b,
      HttpServletRequest r) {
    return service.profileAction(id, action, TypedInputs.map(b), r);
  }

  @GetMapping("/reports/{id}")
  public Map<String, Object> reportDetail(@PathVariable UUID id, HttpServletRequest request) {
    return service.reportDetail(id, request);
  }

  @PostMapping("/reports/{id}/resolve")
  public Map<String, Object> resolve(
      @PathVariable UUID id, @Valid @RequestBody AdminRequests.Resolve body, HttpServletRequest request) {
    return service.resolve(id, TypedInputs.map(body), request);
  }

  @PostMapping("/reviews/{id}/{action}")
  public Map<String, Object> reviewAction(
      @PathVariable UUID id,
      @PathVariable String action,
      @Valid @RequestBody AdminRequests.Reason b,
      HttpServletRequest r) {
    return service.reviewAction(id, action, TypedInputs.map(b), r);
  }

  @PostMapping("/{kind:categories|technologies}")
  public Map<String, Object> taxonomy(
      @PathVariable String kind, @Valid @RequestBody AdminRequests.Taxonomy b, HttpServletRequest r) {
    return service.taxonomy(kind, TypedInputs.map(b), r);
  }

  @GetMapping("/audit")
  public Object history(
      @RequestParam(required = false) UUID targetId, HttpServletRequest r) {
    return service.history(targetId, r);
  }

  @GetMapping("/accounts")
  public Object accounts(
      @RequestParam(defaultValue = "") String q, HttpServletRequest r) {
    return service.accounts(q, r);
  }

  @PostMapping("/accounts/{id}/{action}")
  public Map<String, Object> accountAction(
      @PathVariable UUID id,
      @PathVariable String action,
      @Valid @RequestBody AdminRequests.Reason b,
      HttpServletRequest r) {
    return service.accountAction(id, action, TypedInputs.map(b), r);
  }

  @PostMapping("/inquiries/{id}/{action}")
  public Map<String, Object> inquiryAction(
      @PathVariable UUID id,
      @PathVariable String action,
      @Valid @RequestBody AdminRequests.Reason b,
      HttpServletRequest r) {
    return service.inquiryAction(id, action, TypedInputs.map(b), r);
  }

  @GetMapping("/{kind:categories|technologies}")
  public Object allTaxonomy(@PathVariable String kind, HttpServletRequest r) {
    return service.allTaxonomy(kind, r);
  }

  @GetMapping("/metrics")
  public Map<String, Object> metrics(HttpServletRequest r) {
    return service.metrics(r);
  }
}
