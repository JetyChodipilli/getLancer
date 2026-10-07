package com.getlancer.components;

import com.getlancer.components.ComponentSlotService;
import com.getlancer.dto.PaymentRequests;
import com.getlancer.dto.SlotRequests;
import com.getlancer.shared.TypedInputs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("@authorization.routeAllowed(authentication)")
@RestController @RequestMapping("/api/v1")
public class ComponentSlotsController {
  final ComponentSlotService service;
  public ComponentSlotsController(ComponentSlotService service){
    this.service=service;
  }
  @GetMapping("/components/slots/pricing") public Object pricing(){
    return service.pricing();
  }
  @GetMapping("/me/component-slot-purchases") public Object history(HttpServletRequest r){
    return service.history(r);
  }
  @PostMapping("/me/component-slot-purchases") public Object order(@Valid @RequestBody SlotRequests.Order b,HttpServletRequest r){
    return service.order(TypedInputs.map(b),r);
  }
  @PostMapping("/me/component-slot-purchases/{id}/verify") public Object verify(@PathVariable UUID id,@Valid @RequestBody PaymentRequests.Checkout b,HttpServletRequest r){
    return service.verify(id,TypedInputs.map(b),r);
  }
  @PostMapping("/me/component-slot-purchases/{id}/reconcile") public Object reconcile(@PathVariable UUID id,HttpServletRequest r){
    return service.reconcile(id,r,false);
  }
  @GetMapping("/admin/component-slot-purchases") public Object attention(HttpServletRequest r){
    return service.attention(r);
  }
  @PutMapping("/admin/component-slot-pricing") public Object setPrice(@Valid @RequestBody SlotRequests.Price b,HttpServletRequest r){
    return service.setPrice(TypedInputs.map(b),r);
  }
  @PostMapping("/admin/component-slot-purchases/{id}/reconcile") public Object adminReconcile(@PathVariable UUID id,HttpServletRequest r){
    return service.reconcile(id,r,true);
  }
  @PostMapping("/admin/component-slot-purchases/{id}/bind-order") public Object bind(@PathVariable UUID id,@Valid @RequestBody PaymentRequests.Bind b,HttpServletRequest r){
    return service.bind(id,TypedInputs.map(b),r);
  }
  @PostMapping("/components/razorpay/webhook") public Object webhook(HttpServletRequest r)throws IOException{
    return service.webhook(r);
  }
}
