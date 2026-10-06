package com.getlancer.payments;

import com.getlancer.dto.PaymentRequests;
import com.getlancer.shared.TypedInputs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
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
public class PaymentController {
  private final PaymentService service;
  public PaymentController(PaymentService service) { this.service=service; }
  @GetMapping("/payments/config") public Object config() { return service.configuration(); }
  @GetMapping("/engagements/{id}/payments") public Object summaries(@PathVariable UUID id,HttpServletRequest request) { return service.summaries(id,request); }
  @PostMapping("/milestones/{id}/payment-order") public Object order(@PathVariable UUID id,HttpServletRequest request) { return service.order(id,request); }
  @PostMapping("/payments/{id}/verify") public Object verify(@PathVariable UUID id,@Valid @RequestBody PaymentRequests.Checkout body,HttpServletRequest request) { return service.verify(id,TypedInputs.map(body),request); }
  @PostMapping("/payments/{id}/reconcile") public Object reconcile(@PathVariable UUID id,HttpServletRequest request) { return service.reconcile(id,request); }
  @PostMapping("/payments/razorpay/webhook") public Object webhook(HttpServletRequest request) throws IOException { return service.webhook(request); }
  @GetMapping("/admin/payments/accounts") public Object accounts(HttpServletRequest request) { return service.accounts(request); }
  @PostMapping("/admin/payments/accounts") public Object account(@Valid @RequestBody PaymentRequests.Account body,HttpServletRequest request) { return service.account(TypedInputs.map(body),request); }
  @GetMapping("/admin/payments/attention") public Object attention(HttpServletRequest request) { return service.attention(request); }
  @PostMapping("/admin/payments/{id}/reconcile") public Object adminReconcile(@PathVariable UUID id,HttpServletRequest request) { return service.adminReconcile(id,request); }
  @PostMapping("/admin/payments/{id}/bind-order") public Object bind(@PathVariable UUID id,@Valid @RequestBody PaymentRequests.Bind body,HttpServletRequest request) { return service.bind(id,TypedInputs.map(body),request); }
}
