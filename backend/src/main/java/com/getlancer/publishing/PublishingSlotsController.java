package com.getlancer.publishing;

import com.getlancer.components.ComponentSlotService;
import com.getlancer.dto.PaymentRequests;
import com.getlancer.dto.SlotRequests;
import com.getlancer.shared.TypedInputs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** All slot categories share the existing provider reconciliation and immutable receipt ledger. */
@PreAuthorize("@authorization.routeAllowed(authentication)")
@RestController @RequestMapping("/api/v1")
public class PublishingSlotsController {
  private final ComponentSlotService service;
  public PublishingSlotsController(ComponentSlotService service) { this.service=service; }
  @GetMapping("/publishing-slots/pricing") public Object prices() { return service.allPricing(); }
  @GetMapping("/me/publishing-slots") public Object overview(HttpServletRequest r) { return service.overview(r); }
  @GetMapping("/me/publishing-slot-purchases") public Object history(@RequestParam(defaultValue="PROJECT") String pool,HttpServletRequest r) { return service.history(pool,r); }
  @PostMapping("/me/publishing-slot-purchases") public Object order(@Valid @RequestBody SlotRequests.PublishingOrder b,HttpServletRequest r) { return service.order(b.pool(),TypedInputs.map(b),r); }
  @PostMapping("/me/publishing-slot-purchases/{id}/verify") public Object verify(@PathVariable UUID id,@Valid @RequestBody PaymentRequests.Checkout b,HttpServletRequest r) { return service.verify(id,TypedInputs.map(b),r); }
  @PostMapping("/me/publishing-slot-purchases/{id}/reconcile") public Object reconcile(@PathVariable UUID id,HttpServletRequest r) { return service.reconcile(id,r,false); }
  @GetMapping("/admin/publishing-slot-purchases") public Object attention(@RequestParam(defaultValue="PROJECT") String pool,HttpServletRequest r) { return service.attention(pool,r); }
  @PutMapping("/admin/publishing-slots/{pool}/pricing") public Object setPrice(@PathVariable String pool,@Valid @RequestBody SlotRequests.Price b,HttpServletRequest r) { return service.setPrice(pool,TypedInputs.map(b),r); }
  @PostMapping("/admin/publishing-slot-purchases/{id}/reconcile") public Object adminReconcile(@PathVariable UUID id,HttpServletRequest r) { return service.reconcile(id,r,true); }
  @PostMapping("/admin/publishing-slot-purchases/{id}/bind-order") public Object bind(@PathVariable UUID id,@Valid @RequestBody PaymentRequests.Bind b,HttpServletRequest r) { return service.bind(id,TypedInputs.map(b),r); }
}
