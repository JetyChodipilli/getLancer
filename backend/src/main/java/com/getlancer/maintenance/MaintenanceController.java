package com.getlancer.maintenance;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1")
public class MaintenanceController {
  private final MaintenanceService service;
  public MaintenanceController(MaintenanceService service){this.service=service;}
  @GetMapping("/maintenance/config") public Map<String,Object> config(HttpServletRequest r){return service.configuration(r);}
  @GetMapping("/maintenance/sources") public Map<String,Object> sources(HttpServletRequest r){return service.sources(r);}
  @GetMapping("/maintenance") public Map<String,Object> list(HttpServletRequest r){return service.list(r);}
  @PostMapping("/maintenance") public Map<String,Object> create(@RequestBody Map<String,Object>b,HttpServletRequest r){return service.create(b,r);}
  @GetMapping("/maintenance/{id}") public Map<String,Object> detail(@PathVariable UUID id,HttpServletRequest r){return service.detail(id,r);}
  @PostMapping("/maintenance/{id}/{action}") public Map<String,Object> offer(@PathVariable UUID id,@PathVariable String action,@RequestBody(required=false) Map<String,Object>b,HttpServletRequest r){return service.offerAction(id,action,b==null?Map.of():b,r);}
  @PostMapping("/maintenance/{id}/billing") public Map<String,Object> billing(@PathVariable UUID id,@RequestBody Map<String,Object>b,HttpServletRequest r){return service.start(id,b,r);}
  @PostMapping("/maintenance/{id}/billing/confirm") public Map<String,Object> confirm(@PathVariable UUID id,@RequestBody Map<String,Object>b,HttpServletRequest r){return service.confirm(id,b,r);}
  @PostMapping("/maintenance/{id}/billing/refresh") public Map<String,Object> refresh(@PathVariable UUID id,HttpServletRequest r){return service.refresh(id,r);}
  @PostMapping("/maintenance/{id}/billing/cancel") public Map<String,Object> cancel(@PathVariable UUID id,HttpServletRequest r){return service.cancel(id,r);}
  @PostMapping("/maintenance/{id}/requests") public Map<String,Object> request(@PathVariable UUID id,@RequestBody Map<String,Object>b,HttpServletRequest r){return service.request(id,b,r);}
  @PostMapping("/maintenance/{id}/requests/{requestId}/{action}") public Map<String,Object> requestAction(@PathVariable UUID id,@PathVariable UUID requestId,@PathVariable String action,@RequestBody(required=false) Map<String,Object>b,HttpServletRequest r){return service.requestAction(id,requestId,action,b==null?Map.of():b,r);}
  @GetMapping("/admin/maintenance/attention") public Map<String,Object> attention(HttpServletRequest r){return service.attention(r);}
  @PostMapping("/admin/maintenance/{id}/{action}") public Map<String,Object> operator(@PathVariable UUID id,@PathVariable String action,@RequestBody Map<String,Object>b,HttpServletRequest r){return service.operator(id,action,b,r);}
  @PostMapping("/maintenance/razorpay/webhook") public Map<String,Object> webhook(HttpServletRequest r){return service.webhook(r);}
}
