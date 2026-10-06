package com.getlancer.teams;

import com.getlancer.dto.TeamLeadUpdate;
import com.getlancer.dto.TeamRequests;
import com.getlancer.shared.TypedInputs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("@authorization.routeAllowed(authentication)")
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
  public Map<String, Object> create(@Valid @RequestBody TeamRequests.Profile b, HttpServletRequest r) {
    return service.create(TypedInputs.map(b), r);
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
      @PathVariable UUID id, @Valid @RequestBody TeamRequests.Profile b, HttpServletRequest r) {
    return service.edit(id, TypedInputs.map(b), r);
  }

  @GetMapping("/teams/{id}/candidates")
  public Map<String, Object> candidates(
      @PathVariable UUID id, @RequestParam(defaultValue = "") String q, HttpServletRequest r) {
    return service.candidates(id, q, r);
  }

  @PostMapping("/teams/{id}/invitations")
  public Map<String, Object> invite(
      @PathVariable UUID id, @Valid @RequestBody TeamRequests.Invitation b, HttpServletRequest r) {
    return service.invite(id, TypedInputs.map(b), r);
  }

  @PostMapping("/team-invitations/{id}/respond")
  public Map<String, Object> respond(
      @PathVariable UUID id, @Valid @RequestBody TeamRequests.Response b, HttpServletRequest r) {
    return service.respond(id, TypedInputs.map(b), r);
  }

  @PatchMapping("/teams/{id}/members/{userId}")
  public Map<String, Object> changeRole(
      @PathVariable UUID id,
      @PathVariable UUID userId,
      @Valid @RequestBody TeamRequests.MemberRole b,
      HttpServletRequest r) {
    return service.changeRole(id, userId, TypedInputs.map(b), r);
  }

  @DeleteMapping("/teams/{id}/members/{userId}")
  public Map<String, Object> remove(
      @PathVariable UUID id, @PathVariable UUID userId, HttpServletRequest r) {
    return service.remove(id, userId, r);
  }

  @PostMapping("/teams/{id}/roles")
  public Map<String, Object> createRole(
      @PathVariable UUID id, @Valid @RequestBody TeamRequests.OpenRole b, HttpServletRequest r) {
    return service.createRole(id, TypedInputs.map(b), r);
  }

  @PatchMapping("/teams/{id}/roles/{roleId}")
  public Map<String, Object> roleStatus(
      @PathVariable UUID id,
      @PathVariable UUID roleId,
      @Valid @RequestBody TeamRequests.RoleStatus b,
      HttpServletRequest r) {
    return service.roleStatus(id, roleId, TypedInputs.map(b), r);
  }

  @PostMapping("/teams/{id}/roles/{roleId}/applications")
  public Map<String, Object> apply(
      @PathVariable UUID id,
      @PathVariable UUID roleId,
      @Valid @RequestBody TeamRequests.Application b,
      HttpServletRequest r) {
    return service.apply(id, roleId, TypedInputs.map(b), r);
  }

  @PostMapping("/teams/{id}/applications/{applicationId}/decision")
  public Map<String, Object> decision(
      @PathVariable UUID id,
      @PathVariable UUID applicationId,
      @Valid @RequestBody TeamRequests.ApplicationDecision b,
      HttpServletRequest r) {
    return service.decision(id, applicationId, TypedInputs.map(b), r);
  }

  @PostMapping("/teams/{id}/leads")
  public Map<String, Object> lead(
      @PathVariable UUID id, @Valid @RequestBody TeamRequests.Lead b, HttpServletRequest r) {
    return service.lead(id, TypedInputs.map(b), r);
  }

  @PostMapping("/teams/{id}/leads/{leadId}/respond")
  public Map<String, Object> clientDecision(
      @PathVariable UUID id,
      @PathVariable UUID leadId,
      @Valid @RequestBody TeamRequests.Response b,
      HttpServletRequest r) {
    return service.clientDecision(id, leadId, TypedInputs.map(b), r);
  }

  @PatchMapping("/teams/{id}/leads/{leadId}")
  public Map<String, Object> updateLead(
      @PathVariable UUID id,
      @PathVariable UUID leadId,
      @Valid @RequestBody TeamLeadUpdate b,
      HttpServletRequest r) {
    return service.updateLead(id, leadId, TypedInputs.map(b), r);
  }

  @PostMapping("/teams/{id}/projects")
  public Map<String, Object> addProject(
      @PathVariable UUID id, @Valid @RequestBody TeamRequests.Project b, HttpServletRequest r) {
    return service.addProject(id, TypedInputs.map(b), r);
  }

  @DeleteMapping("/teams/{id}/projects/{productId}")
  public Map<String, Object> removeProject(
      @PathVariable UUID id, @PathVariable UUID productId, HttpServletRequest r) {
    return service.removeProject(id, productId, r);
  }

  @PostMapping("/teams/{id}/staffing")
  public Map<String, Object> addStaffing(
      @PathVariable UUID id, @Valid @RequestBody TeamRequests.Staffing b, HttpServletRequest r) {
    return service.addStaffing(id, TypedInputs.map(b), r);
  }

  @PatchMapping("/teams/{id}/staffing/{staffingId}")
  public Map<String, Object> updateStaffing(
      @PathVariable UUID id,
      @PathVariable UUID staffingId,
      @Valid @RequestBody TeamRequests.StaffingStatus b,
      HttpServletRequest r) {
    return service.updateStaffing(id, staffingId, TypedInputs.map(b), r);
  }

  @GetMapping("/admin/teams")
  public Map<String, Object> admin(HttpServletRequest r) {
    return service.admin(r);
  }

  @PostMapping("/admin/teams/{id}/moderate")
  public Map<String, Object> moderate(
      @PathVariable UUID id, @Valid @RequestBody TeamRequests.Moderate b, HttpServletRequest r) {
    return service.moderate(id, TypedInputs.map(b), r);
  }
}
