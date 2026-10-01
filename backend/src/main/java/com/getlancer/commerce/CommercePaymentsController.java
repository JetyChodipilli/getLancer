package com.getlancer.commerce;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class CommercePaymentsController {
  private final CommercePaymentService payments;
  public CommercePaymentsController(CommercePaymentService payments) {this.payments=payments;}
  @GetMapping("/me/template-purchases") public Map<String,Object> purchases(HttpServletRequest r) {return payments.purchases(r);}
  @PostMapping("/templates/{id}/orders") public Map<String,Object> order(@PathVariable UUID id,@RequestBody Map<String,Object> b,HttpServletRequest r) {return payments.order(id,b,r);}
  @PostMapping("/template-purchases/{id}/verify") public Map<String,Object> verify(@PathVariable UUID id,@RequestBody Map<String,Object> b,HttpServletRequest r) {return payments.verify(id,b,r);}
  @PostMapping("/template-purchases/{id}/reconcile") public Map<String,Object> reconcile(@PathVariable UUID id,HttpServletRequest r) {return payments.reconcile(id,r);}
  @PostMapping("/template-purchases/{id}/disputes") public Map<String,Object> dispute(@PathVariable UUID id,@RequestBody Map<String,Object> b,HttpServletRequest r) {return payments.dispute(id,b,r);}
  @GetMapping("/template-purchases/{id}/download") public ResponseEntity<byte[]> download(@PathVariable UUID id,HttpServletRequest r) {return payments.download(id,r);}
  @PostMapping("/commerce/razorpay/webhook") public Map<String,Object> webhook(HttpServletRequest r) throws IOException {return payments.webhook(r);}
  @GetMapping("/admin/template-purchases/attention") public Map<String,Object> attention(HttpServletRequest r) {return payments.attention(r);}
  @PostMapping("/admin/template-purchases/{id}/bind-order") public Map<String,Object> bind(@PathVariable UUID id,@RequestBody Map<String,Object> b,HttpServletRequest r) {return payments.bind(id,b,r);}
  @PostMapping("/admin/template-purchases/{id}/reconcile") public Map<String,Object> adminReconcile(@PathVariable UUID id,HttpServletRequest r) {return payments.adminReconcile(id,r);}
  @PostMapping("/admin/template-purchases/{id}/resolve") public Map<String,Object> resolve(@PathVariable UUID id,@RequestBody Map<String,Object> b,HttpServletRequest r) {return payments.resolve(id,b,r);}
}
