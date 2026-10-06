package com.getlancer.accounts;

import com.getlancer.dto.PrivacyRequests;
import com.getlancer.responses.PageResponse;
import com.getlancer.responses.PrivacyResponses;
import com.getlancer.shared.TypedInputs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1")
public class PrivacyController {
  private final PrivacyService service;

  public PrivacyController(PrivacyService service) {
    this.service = service;
  }

  @GetMapping("/policies/config")
  public PrivacyResponses.Configuration config() {
    return service.config();
  }

  @GetMapping("/admin/deletion-requests")
  public PageResponse<PrivacyResponses.DeletionRequest> requests(HttpServletRequest r) {
    return service.requests(r);
  }

  @PostMapping("/admin/deletion-requests/{id}/review")
  public PrivacyResponses.Review review(
      @PathVariable UUID id, @Valid @RequestBody PrivacyRequests.Review body, HttpServletRequest r) {
    return service.review(id, TypedInputs.map(body), r);
  }
}
