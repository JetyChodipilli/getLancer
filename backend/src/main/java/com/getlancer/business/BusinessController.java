package com.getlancer.business;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class BusinessController {
  private final BusinessService service;

  public BusinessController(BusinessService service) {
    this.service = service;
  }

  @GetMapping("/businesses")
  public Map<String, Object> mine(HttpServletRequest r) {
    return service.mine(r);
  }

  @PostMapping("/businesses")
  public Map<String, Object> create(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.create(b, r);
  }

  @GetMapping("/businesses/{id}")
  public Map<String, Object> workspace(@PathVariable UUID id, HttpServletRequest r) {
    return service.workspace(id, r);
  }

  @PutMapping("/businesses/{id}")
  public Map<String, Object> edit(
      @PathVariable UUID id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.edit(id, b, r);
  }

  @PostMapping("/businesses/{id}/invitations")
  public Map<String, Object> invite(
      @PathVariable UUID id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.invite(id, b, r);
  }

  @PostMapping("/business-invitations/{id}/respond")
  public Map<String, Object> respond(
      @PathVariable UUID id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.respond(id, b, r);
  }

  @DeleteMapping("/businesses/{id}/members/{userId}")
  public Map<String, Object> remove(
      @PathVariable UUID id, @PathVariable UUID userId, HttpServletRequest r) {
    return service.remove(id, userId, r);
  }
}
