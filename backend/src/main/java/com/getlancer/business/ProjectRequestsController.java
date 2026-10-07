package com.getlancer.business;

import com.getlancer.dto.BusinessRequests;
import com.getlancer.shared.TypedInputs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
public class ProjectRequestsController {
  private final ProjectRequestService service;

  public ProjectRequestsController(ProjectRequestService service) {
    this.service = service;
  }

  @GetMapping("/businesses/{id}/requests")
  public Map<String, Object> list(@PathVariable UUID id, HttpServletRequest r) {
    return service.list(id, r);
  }

  @PostMapping("/businesses/{id}/requests")
  public Map<String, Object> create(
      @PathVariable UUID id, @Valid @RequestBody BusinessRequests.Brief b, HttpServletRequest r) {
    return service.create(id, TypedInputs.map(b), r);
  }

  @PutMapping("/businesses/{id}/requests/{requestId}")
  public Map<String, Object> edit(
      @PathVariable UUID id,
      @PathVariable UUID requestId,
      @Valid @RequestBody BusinessRequests.Brief b,
      HttpServletRequest r) {
    return service.edit(id, requestId, TypedInputs.map(b), r);
  }

  @GetMapping("/businesses/{id}/requests/{requestId}/matches")
  public Map<String, Object> matches(
      @PathVariable UUID id, @PathVariable UUID requestId, HttpServletRequest r) {
    return service.matches(id, requestId, r);
  }

  @GetMapping("/businesses/{id}/requests/{requestId}/shortlist")
  public Map<String, Object> shortlist(
      @PathVariable UUID id, @PathVariable UUID requestId, HttpServletRequest r) {
    return service.shortlist(id, requestId, r);
  }

  @PostMapping("/businesses/{id}/requests/{requestId}/shortlist")
  public Map<String, Object> save(
      @PathVariable UUID id,
      @PathVariable UUID requestId,
      @Valid @RequestBody BusinessRequests.Shortlist b,
      HttpServletRequest r) {
    return service.save(id, requestId, TypedInputs.map(b), r);
  }

  @DeleteMapping("/businesses/{id}/requests/{requestId}/shortlist/{entryId}")
  public Map<String, Object> remove(
      @PathVariable UUID id,
      @PathVariable UUID requestId,
      @PathVariable UUID entryId,
      HttpServletRequest r) {
    return service.remove(id, requestId, entryId, r);
  }

  @PostMapping("/businesses/{id}/requests/{requestId}/concierge")
  public Map<String, Object> concierge(
      @PathVariable UUID id, @PathVariable UUID requestId, HttpServletRequest r) {
    return service.concierge(id, requestId, r);
  }
}
