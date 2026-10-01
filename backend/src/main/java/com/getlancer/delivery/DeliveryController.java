package com.getlancer.delivery;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class DeliveryController {
  private final DeliveryService service;
  public DeliveryController(DeliveryService service) { this.service = service; }

  @GetMapping("/engagements")
  public Map<String, Object> mine(HttpServletRequest request) { return service.mine(request); }

  @PostMapping("/engagements")
  public Map<String, Object> create(@RequestBody Map<String, Object> body, HttpServletRequest request) { return service.create(body, request); }

  @GetMapping("/engagements/{id}")
  public Map<String, Object> detail(@PathVariable UUID id, HttpServletRequest request) { return service.detail(id, request); }

  @PostMapping("/engagements/{id}/proposals")
  public Map<String, Object> draft(@PathVariable UUID id, @RequestBody Map<String, Object> body, HttpServletRequest request) { return service.draft(id, body, request); }

  @PostMapping("/engagements/{id}/proposals/{proposalId}/{action}")
  public Map<String, Object> proposal(@PathVariable UUID id, @PathVariable UUID proposalId, @PathVariable String action, @RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) { return service.proposalAction(id, proposalId, action, body == null ? Map.of() : body, request); }

  @PostMapping("/engagements/{id}/milestones/{milestoneId}/{action}")
  public Map<String, Object> milestone(@PathVariable UUID id, @PathVariable UUID milestoneId, @PathVariable String action, @RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) { return service.milestoneAction(id, milestoneId, action, body == null ? Map.of() : body, request); }

  @PostMapping("/engagements/{id}/completion")
  public Map<String, Object> completion(@PathVariable UUID id, HttpServletRequest request) { return service.completion(id, request); }

  @PostMapping("/engagements/{id}/disputes")
  public Map<String, Object> dispute(@PathVariable UUID id, @RequestBody Map<String, Object> body, HttpServletRequest request) { return service.dispute(id, body, request); }

  @GetMapping("/admin/delivery/disputes")
  public Map<String, Object> disputes(HttpServletRequest request) { return service.adminDisputes(request); }

  @PostMapping("/admin/delivery/disputes/{id}/resolve")
  public Map<String, Object> resolve(@PathVariable UUID id, @RequestBody Map<String, Object> body, HttpServletRequest request) { return service.resolve(id, body, request); }
}
