package com.getlancer.teams;

import jakarta.servlet.http.HttpServletRequest;
import java.time.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class TeamsController {
  private final TeamService service;

  public TeamsController(TeamService service) {
    this.service = service;
  }

  @GetMapping("/teams")
  public Map<String, Object> directory(@RequestParam(defaultValue = "") String q) {
    return service.directory(q);
  }

  @PostMapping("/teams")
  public Map<String, Object> create(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.create(b, r);
  }

  @GetMapping("/teams/{id}")
  public Map<String, Object> detail(@PathVariable UUID id) {
    return service.detail(id);
  }

  @GetMapping("/me/teams")
  public Map<String, Object> mine(HttpServletRequest r) {
    return service.mine(r);
  }

  @GetMapping("/teams/{id}/workspace")
  public Map<String, Object> workspace(@PathVariable UUID id, HttpServletRequest r) {
    return service.workspace(id, r);
  }

  @PutMapping("/teams/{id}")
  public Map<String, Object> edit(
      @PathVariable UUID id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.edit(id, b, r);
  }

  @GetMapping("/teams/{id}/candidates")
  public Map<String, Object> candidates(
      @PathVariable UUID id, @RequestParam(defaultValue = "") String q, HttpServletRequest r) {
    return service.candidates(id, q, r);
  }

  @PostMapping("/teams/{id}/invitations")
  public Map<String, Object> invite(
      @PathVariable UUID id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.invite(id, b, r);
  }

  @PostMapping("/team-invitations/{id}/respond")
  public Map<String, Object> respond(
      @PathVariable UUID id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.respond(id, b, r);
  }

  @PatchMapping("/teams/{id}/members/{userId}")
  public Map<String, Object> changeRole(
      @PathVariable UUID id,
      @PathVariable UUID userId,
      @RequestBody Map<String, Object> b,
      HttpServletRequest r) {
    return service.changeRole(id, userId, b, r);
  }

  @DeleteMapping("/teams/{id}/members/{userId}")
  public Map<String, Object> remove(
      @PathVariable UUID id, @PathVariable UUID userId, HttpServletRequest r) {
    return service.remove(id, userId, r);
  }

  @PostMapping("/teams/{id}/roles")
  public Map<String, Object> createRole(
      @PathVariable UUID id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.createRole(id, b, r);
  }

  @PatchMapping("/teams/{id}/roles/{roleId}")
  public Map<String, Object> roleStatus(
      @PathVariable UUID id,
      @PathVariable UUID roleId,
      @RequestBody Map<String, Object> b,
      HttpServletRequest r) {
    return service.roleStatus(id, roleId, b, r);
  }

  @PostMapping("/teams/{id}/roles/{roleId}/applications")
  public Map<String, Object> apply(
      @PathVariable UUID id,
      @PathVariable UUID roleId,
      @RequestBody Map<String, Object> b,
      HttpServletRequest r) {
    return service.apply(id, roleId, b, r);
  }

  @PostMapping("/teams/{id}/applications/{applicationId}/decision")
  public Map<String, Object> decision(
      @PathVariable UUID id,
      @PathVariable UUID applicationId,
      @RequestBody Map<String, Object> b,
      HttpServletRequest r) {
    return service.decision(id, applicationId, b, r);
  }

  @PostMapping("/teams/{id}/leads")
  public Map<String, Object> lead(
      @PathVariable UUID id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.lead(id, b, r);
  }

  @PostMapping("/teams/{id}/leads/{leadId}/respond")
  public Map<String, Object> clientDecision(
      @PathVariable UUID id,
      @PathVariable UUID leadId,
      @RequestBody Map<String, Object> b,
      HttpServletRequest r) {
    return service.clientDecision(id, leadId, b, r);
  }

  @PatchMapping("/teams/{id}/leads/{leadId}")
  public Map<String, Object> updateLead(
      @PathVariable UUID id,
      @PathVariable UUID leadId,
      @RequestBody Map<String, Object> b,
      HttpServletRequest r) {
    return service.updateLead(id, leadId, b, r);
  }

  @PostMapping("/teams/{id}/projects")
  public Map<String, Object> addProject(
      @PathVariable UUID id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.addProject(id, b, r);
  }

  @DeleteMapping("/teams/{id}/projects/{productId}")
  public Map<String, Object> removeProject(
      @PathVariable UUID id, @PathVariable UUID productId, HttpServletRequest r) {
    return service.removeProject(id, productId, r);
  }

  @PostMapping("/teams/{id}/staffing")
  public Map<String, Object> addStaffing(
      @PathVariable UUID id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.addStaffing(id, b, r);
  }

  @PatchMapping("/teams/{id}/staffing/{staffingId}")
  public Map<String, Object> updateStaffing(
      @PathVariable UUID id,
      @PathVariable UUID staffingId,
      @RequestBody Map<String, Object> b,
      HttpServletRequest r) {
    return service.updateStaffing(id, staffingId, b, r);
  }

  @GetMapping("/admin/teams")
  public Map<String, Object> admin(HttpServletRequest r) {
    return service.admin(r);
  }

  @PostMapping("/admin/teams/{id}/moderate")
  public Map<String, Object> moderate(
      @PathVariable UUID id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.moderate(id, b, r);
  }
}
