package com.getlancer.inquiries;

import com.getlancer.dto.InquiryRequests;
import com.getlancer.shared.TypedInputs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("@authorization.routeAllowed(authentication)")
@RestController
@RequestMapping("/api/v1")
public class InquiriesController {
  private final InquiryService service;

  public InquiriesController(InquiryService service) {
    this.service = service;
  }

  @PostMapping("/inquiries")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(
      @Valid @RequestBody InquiryRequests.Create b,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return service.create(TypedInputs.map(b), key);
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
      @Valid @RequestBody(required = false) InquiryRequests.Transition body,
      HttpServletRequest r) {
    return service.transition(id, action, TypedInputs.map(body), r);
  }

  @PostMapping("/reports")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> report(
      @Valid @RequestBody InquiryRequests.Report b, HttpServletRequest request) {
    return service.report(TypedInputs.map(b), request);
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
