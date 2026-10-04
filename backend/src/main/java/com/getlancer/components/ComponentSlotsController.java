package com.getlancer.components;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import org.springframework.web.bind.annotation.*;
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
  @PostMapping("/me/component-slot-purchases") public Object order(@RequestBody Map<String,Object>b,HttpServletRequest r){
    return service.order(b,r);
  }
  @PostMapping("/me/component-slot-purchases/{id}/verify") public Object verify(@PathVariable UUID id,@RequestBody Map<String,Object>b,HttpServletRequest r){
    return service.verify(id,b,r);
  }
  @PostMapping("/me/component-slot-purchases/{id}/reconcile") public Object reconcile(@PathVariable UUID id,HttpServletRequest r){
    return service.reconcile(id,r,false);
  }
  @GetMapping("/admin/component-slot-purchases") public Object attention(HttpServletRequest r){
    return service.attention(r);
  }
  @PutMapping("/admin/component-slot-pricing") public Object setPrice(@RequestBody Map<String,Object>b,HttpServletRequest r){
    return service.setPrice(b,r);
  }
  @PostMapping("/admin/component-slot-purchases/{id}/reconcile") public Object adminReconcile(@PathVariable UUID id,HttpServletRequest r){
    return service.reconcile(id,r,true);
  }
  @PostMapping("/admin/component-slot-purchases/{id}/bind-order") public Object bind(@PathVariable UUID id,@RequestBody Map<String,Object>b,HttpServletRequest r){
    return service.bind(id,b,r);
  }
  @PostMapping("/components/razorpay/webhook") public Object webhook(HttpServletRequest r)throws IOException{
    return service.webhook(r);
  }
}
