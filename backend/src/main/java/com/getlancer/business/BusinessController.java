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
  public Map<String, Object> create(@Valid @RequestBody BusinessRequests.Profile b, HttpServletRequest r) {
    return service.create(TypedInputs.map(b), r);
  }

  @GetMapping("/businesses/{id}")
  public Map<String, Object> workspace(@PathVariable UUID id, HttpServletRequest r) {
    return service.workspace(id, r);
  }

  @PutMapping("/businesses/{id}")
  public Map<String, Object> edit(
      @PathVariable UUID id, @Valid @RequestBody BusinessRequests.Profile b, HttpServletRequest r) {
    return service.edit(id, TypedInputs.map(b), r);
  }

  @PostMapping("/businesses/{id}/invitations")
  public Map<String, Object> invite(
      @PathVariable UUID id, @Valid @RequestBody BusinessRequests.Invitation b, HttpServletRequest r) {
    return service.invite(id, TypedInputs.map(b), r);
  }

  @PostMapping("/business-invitations/{id}/respond")
  public Map<String, Object> respond(
      @PathVariable UUID id, @Valid @RequestBody BusinessRequests.Response b, HttpServletRequest r) {
    return service.respond(id, TypedInputs.map(b), r);
  }

  @DeleteMapping("/businesses/{id}/members/{userId}")
  public Map<String, Object> remove(
      @PathVariable UUID id, @PathVariable UUID userId, HttpServletRequest r) {
    return service.remove(id, userId, r);
  }
}
