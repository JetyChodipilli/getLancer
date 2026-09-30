package com.getlancer.payments;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {
  final PaymentService service;
  public PaymentController(PaymentService service) { this.service=service; }
  @GetMapping("/payments/config") public Object config() { return service.configuration(); }
  @GetMapping("/engagements/{id}/payments") public Object summaries(@PathVariable UUID id,HttpServletRequest request) { return service.summaries(id,request); }
  @PostMapping("/milestones/{id}/payment-order") public Object order(@PathVariable UUID id,HttpServletRequest request) { return service.order(id,request); }
  @PostMapping("/payments/{id}/verify") public Object verify(@PathVariable UUID id,@RequestBody Map<String,Object> body,HttpServletRequest request) { return service.verify(id,body,request); }
  @PostMapping("/payments/{id}/reconcile") public Object reconcile(@PathVariable UUID id,HttpServletRequest request) { return service.reconcile(id,request); }
  @PostMapping("/payments/razorpay/webhook") public Object webhook(HttpServletRequest request) throws IOException { return service.webhook(request); }
  @GetMapping("/admin/payments/accounts") public Object accounts(HttpServletRequest request) { return service.accounts(request); }
  @PostMapping("/admin/payments/accounts") public Object account(@RequestBody Map<String,Object> body,HttpServletRequest request) { return service.account(body,request); }
  @GetMapping("/admin/payments/attention") public Object attention(HttpServletRequest request) { return service.attention(request); }
  @PostMapping("/admin/payments/{id}/reconcile") public Object adminReconcile(@PathVariable UUID id,HttpServletRequest request) { return service.adminReconcile(id,request); }
  @PostMapping("/admin/payments/{id}/bind-order") public Object bind(@PathVariable UUID id,@RequestBody Map<String,Object> body,HttpServletRequest request) { return service.bind(id,body,request); }
}
