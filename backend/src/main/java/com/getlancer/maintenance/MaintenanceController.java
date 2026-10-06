package com.getlancer.maintenance;

import com.getlancer.dto.MaintenanceRequests;
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
public class MaintenanceController {
  private final MaintenanceService service;
  public MaintenanceController(MaintenanceService service){this.service=service;}
  @GetMapping("/maintenance/config") public Map<String,Object> config(HttpServletRequest r){return service.configuration(r);}
  @GetMapping("/maintenance/sources") public Map<String,Object> sources(HttpServletRequest r){return service.sources(r);}
  @GetMapping("/maintenance") public Map<String,Object> list(HttpServletRequest r){return service.list(r);}
  @PostMapping("/maintenance") public Map<String,Object> create(@Valid @RequestBody MaintenanceRequests.Terms b,HttpServletRequest r){return service.create(TypedInputs.map(b),r);}
  @GetMapping("/maintenance/{id}") public Map<String,Object> detail(@PathVariable UUID id,HttpServletRequest r){return service.detail(id,r);}
  @PostMapping("/maintenance/{id}/{action}") public Map<String,Object> offer(@PathVariable UUID id,@PathVariable String action,@Valid @RequestBody(required=false) MaintenanceRequests.OfferAction b,HttpServletRequest r){return service.offerAction(id,action,TypedInputs.map(b),r);}
  @PostMapping("/maintenance/{id}/billing") public Map<String,Object> billing(@PathVariable UUID id,@Valid @RequestBody MaintenanceRequests.Billing b,HttpServletRequest r){return service.start(id,TypedInputs.map(b),r);}
  @PostMapping("/maintenance/{id}/billing/confirm") public Map<String,Object> confirm(@PathVariable UUID id,@Valid @RequestBody MaintenanceRequests.Checkout b,HttpServletRequest r){return service.confirm(id,TypedInputs.map(b),r);}
  @PostMapping("/maintenance/{id}/billing/refresh") public Map<String,Object> refresh(@PathVariable UUID id,HttpServletRequest r){return service.refresh(id,r);}
  @PostMapping("/maintenance/{id}/billing/cancel") public Map<String,Object> cancel(@PathVariable UUID id,HttpServletRequest r){return service.cancel(id,r);}
  @PostMapping("/maintenance/{id}/requests") public Map<String,Object> request(@PathVariable UUID id,@Valid @RequestBody MaintenanceRequests.Support b,HttpServletRequest r){return service.request(id,TypedInputs.map(b),r);}
  @PostMapping("/maintenance/{id}/requests/{requestId}/{action}") public Map<String,Object> requestAction(@PathVariable UUID id,@PathVariable UUID requestId,@PathVariable String action,@Valid @RequestBody(required=false) MaintenanceRequests.SupportAction b,HttpServletRequest r){return service.requestAction(id,requestId,action,TypedInputs.map(b),r);}
  @GetMapping("/admin/maintenance/attention") public Map<String,Object> attention(HttpServletRequest r){return service.attention(r);}
  @PostMapping("/admin/maintenance/{id}/{action}") public Map<String,Object> operator(@PathVariable UUID id,@PathVariable String action,@Valid @RequestBody MaintenanceRequests.Operator b,HttpServletRequest r){return service.operator(id,action,TypedInputs.map(b),r);}
  @PostMapping("/maintenance/razorpay/webhook") public Map<String,Object> webhook(HttpServletRequest r){return service.webhook(r);}
}
