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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("@authorization.routeAllowed(authentication)")
@RestController
@RequestMapping("/api/v1")
public class TalentListsController {
  private final TalentListService service;

  public TalentListsController(TalentListService service) {
    this.service = service;
  }

  @GetMapping("/businesses/{id}/talent-lists")
  public Map<String, Object> lists(@PathVariable UUID id, HttpServletRequest r) {
    return service.lists(id, r);
  }

  @PostMapping("/businesses/{id}/talent-lists")
  public Map<String, Object> create(
      @PathVariable UUID id, @Valid @RequestBody BusinessRequests.TalentList b, HttpServletRequest r) {
    return service.create(id, TypedInputs.map(b), r);
  }

  @GetMapping("/businesses/{id}/talent-lists/{listId}/entries")
  public Map<String, Object> entries(
      @PathVariable UUID id, @PathVariable UUID listId, HttpServletRequest r) {
    return service.entries(id, listId, r);
  }

  @PostMapping("/businesses/{id}/talent-lists/{listId}/entries")
  public Map<String, Object> save(
      @PathVariable UUID id,
      @PathVariable UUID listId,
      @Valid @RequestBody BusinessRequests.TalentEntry b,
      HttpServletRequest r) {
    return service.save(id, listId, TypedInputs.map(b), r);
  }

  @DeleteMapping("/businesses/{id}/talent-lists/{listId}/entries/{entryId}")
  public Map<String, Object> remove(
      @PathVariable UUID id,
      @PathVariable UUID listId,
      @PathVariable UUID entryId,
      HttpServletRequest r) {
    return service.remove(id, listId, entryId, r);
  }
}
