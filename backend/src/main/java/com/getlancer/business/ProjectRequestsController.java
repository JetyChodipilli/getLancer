package com.getlancer.business;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

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
      @PathVariable UUID id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.create(id, b, r);
  }

  @PutMapping("/businesses/{id}/requests/{requestId}")
  public Map<String, Object> edit(
      @PathVariable UUID id,
      @PathVariable UUID requestId,
      @RequestBody Map<String, Object> b,
      HttpServletRequest r) {
    return service.edit(id, requestId, b, r);
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
      @RequestBody Map<String, Object> b,
      HttpServletRequest r) {
    return service.save(id, requestId, b, r);
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
