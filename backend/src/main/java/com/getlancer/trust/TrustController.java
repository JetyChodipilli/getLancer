package com.getlancer.trust;

import com.getlancer.dto.TrustRequests;
import com.getlancer.shared.TypedInputs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("@authorization.routeAllowed(authentication)")
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
      @Valid @RequestBody TrustRequests.Availability b, HttpServletRequest r) {
    return service.availability(TypedInputs.map(b), r);
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
      @Valid @RequestBody TrustRequests.Reason b,
      HttpServletRequest r) {
    return service.review(id, action, TypedInputs.map(b), r);
  }

  @PostMapping("/admin/earned-capacity/{id}")
  public Map<String, Object> award(
      @PathVariable UUID id, @Valid @RequestBody TrustRequests.Reason b, HttpServletRequest r) {
    return service.award(id, TypedInputs.map(b), r);
  }

  @GetMapping("/products/{slug}/similar-builders")
  public Map<String, Object> similar(@PathVariable String slug) {
    return service.similar(slug);
  }
}
