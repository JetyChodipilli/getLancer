package com.getlancer.publishing;

import com.getlancer.components.ComponentSlotService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

/** All slot categories share the existing provider reconciliation and immutable receipt ledger. */
@RestController @RequestMapping("/api/v1")
public class PublishingSlotsController {
  private final ComponentSlotService service;
  public PublishingSlotsController(ComponentSlotService service) { this.service=service; }
  @GetMapping("/publishing-slots/pricing") public Object prices() { return service.allPricing(); }
  @GetMapping("/me/publishing-slots") public Object overview(HttpServletRequest r) { return service.overview(r); }
  @GetMapping("/me/publishing-slot-purchases") public Object history(@RequestParam(defaultValue="PROJECT") String pool,HttpServletRequest r) { return service.history(pool,r); }
  @PostMapping("/me/publishing-slot-purchases") public Object order(@RequestBody Map<String,Object> b,HttpServletRequest r) { return service.order(Objects.toString(b.get("pool"),""),b,r); }
  @PostMapping("/me/publishing-slot-purchases/{id}/verify") public Object verify(@PathVariable UUID id,@RequestBody Map<String,Object> b,HttpServletRequest r) { return service.verify(id,b,r); }
  @PostMapping("/me/publishing-slot-purchases/{id}/reconcile") public Object reconcile(@PathVariable UUID id,HttpServletRequest r) { return service.reconcile(id,r,false); }
  @GetMapping("/admin/publishing-slot-purchases") public Object attention(@RequestParam(defaultValue="PROJECT") String pool,HttpServletRequest r) { return service.attention(pool,r); }
  @PutMapping("/admin/publishing-slots/{pool}/pricing") public Object setPrice(@PathVariable String pool,@RequestBody Map<String,Object> b,HttpServletRequest r) { return service.setPrice(pool,b,r); }
  @PostMapping("/admin/publishing-slot-purchases/{id}/reconcile") public Object adminReconcile(@PathVariable UUID id,HttpServletRequest r) { return service.reconcile(id,r,true); }
  @PostMapping("/admin/publishing-slot-purchases/{id}/bind-order") public Object bind(@PathVariable UUID id,@RequestBody Map<String,Object> b,HttpServletRequest r) { return service.bind(id,b,r); }
}
