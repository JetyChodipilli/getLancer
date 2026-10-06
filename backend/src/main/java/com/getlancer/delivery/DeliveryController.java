package com.getlancer.delivery;

import com.getlancer.dto.DeliveryRequests;
import com.getlancer.shared.TypedInputs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("@authorization.routeAllowed(authentication)")
@RestController
@RequestMapping("/api/v1")
public class DeliveryController {
  private final DeliveryService service;
  public DeliveryController(DeliveryService service) { this.service = service; }

  @GetMapping("/engagements")
  public Map<String, Object> mine(HttpServletRequest request) { return service.mine(request); }

  @PostMapping("/engagements")
  public Map<String, Object> create(@Valid @RequestBody DeliveryRequests.Source body, HttpServletRequest request) { return service.create(TypedInputs.map(body), request); }

  @GetMapping("/engagements/{id}")
  public Map<String, Object> detail(@PathVariable UUID id, HttpServletRequest request) { return service.detail(id, request); }

  @PostMapping("/engagements/{id}/proposals")
  public Map<String, Object> draft(@PathVariable UUID id, @Valid @RequestBody DeliveryRequests.Proposal body, HttpServletRequest request) { return service.draft(id, TypedInputs.map(body), request); }

  @PostMapping("/engagements/{id}/proposals/{proposalId}/{action}")
  public Map<String, Object> proposal(@PathVariable UUID id, @PathVariable UUID proposalId, @PathVariable String action, @Valid @RequestBody(required = false) DeliveryRequests.ProposalAction body, HttpServletRequest request) { return service.proposalAction(id, proposalId, action, TypedInputs.map(body), request); }

  @PostMapping("/engagements/{id}/milestones/{milestoneId}/{action}")
  public Map<String, Object> milestone(@PathVariable UUID id, @PathVariable UUID milestoneId, @PathVariable String action, @Valid @RequestBody(required = false) DeliveryRequests.MilestoneAction body, HttpServletRequest request) { return service.milestoneAction(id, milestoneId, action, TypedInputs.map(body), request); }

  @PostMapping("/engagements/{id}/completion")
  public Map<String, Object> completion(@PathVariable UUID id, HttpServletRequest request) { return service.completion(id, request); }

  @PostMapping("/engagements/{id}/disputes")
  public Map<String, Object> dispute(@PathVariable UUID id, @Valid @RequestBody DeliveryRequests.Dispute body, HttpServletRequest request) { return service.dispute(id, TypedInputs.map(body), request); }

  @GetMapping("/admin/delivery/disputes")
  public Map<String, Object> disputes(HttpServletRequest request) { return service.adminDisputes(request); }

  @PostMapping("/admin/delivery/disputes/{id}/resolve")
  public Map<String, Object> resolve(@PathVariable UUID id, @Valid @RequestBody DeliveryRequests.Resolve body, HttpServletRequest request) { return service.resolve(id, TypedInputs.map(body), request); }
}
