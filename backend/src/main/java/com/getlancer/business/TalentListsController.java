package com.getlancer.business;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

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
      @PathVariable UUID id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.create(id, b, r);
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
      @RequestBody Map<String, Object> b,
      HttpServletRequest r) {
    return service.save(id, listId, b, r);
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
