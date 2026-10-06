package com.getlancer.business;

import com.getlancer.dto.BusinessRequests;
import com.getlancer.shared.TypedInputs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("@authorization.routeAllowed(authentication)")
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
      @PathVariable UUID id, @Valid @RequestBody BusinessRequests.ConciergeStatus b, HttpServletRequest r) {
    return service.status(id, TypedInputs.map(b), r);
  }

  @PostMapping("/admin/concierge/{id}/shortlist")
  public Map<String, Object> save(
      @PathVariable UUID id, @Valid @RequestBody BusinessRequests.Shortlist b, HttpServletRequest r) {
    return service.save(id, TypedInputs.map(b), r);
  }
}
