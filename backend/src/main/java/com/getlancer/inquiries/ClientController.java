package com.getlancer.inquiries;

import com.getlancer.dto.InquiryRequests;
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
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("@authorization.routeAllowed(authentication)")
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
      @PathVariable UUID id, @Valid @RequestBody InquiryRequests.Decision body, HttpServletRequest request) {
    return service.decision(id, TypedInputs.map(body), request);
  }

  @PostMapping("/{id}/review")
  public Map<String, Object> review(
      @PathVariable UUID id, @Valid @RequestBody InquiryRequests.Review body, HttpServletRequest request) {
    return service.review(id, TypedInputs.map(body), request);
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
