package com.getlancer.commerce;

import com.getlancer.dto.CommerceRequests;
import com.getlancer.dto.PaymentRequests;
import com.getlancer.shared.TypedInputs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
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
public class CommercePaymentsController {
  private final CommercePaymentService payments;
  public CommercePaymentsController(CommercePaymentService payments) {this.payments=payments;}
  @GetMapping("/me/template-purchases") public Map<String,Object> purchases(HttpServletRequest r) {return payments.purchases(r);}
  @PostMapping("/templates/{id}/orders") public Map<String,Object> order(@PathVariable UUID id,@Valid @RequestBody CommerceRequests.Order b,HttpServletRequest r) {return payments.order(id,TypedInputs.map(b),r);}
  @PostMapping("/template-purchases/{id}/verify") public Map<String,Object> verify(@PathVariable UUID id,@Valid @RequestBody PaymentRequests.Checkout b,HttpServletRequest r) {return payments.verify(id,TypedInputs.map(b),r);}
  @PostMapping("/template-purchases/{id}/reconcile") public Map<String,Object> reconcile(@PathVariable UUID id,HttpServletRequest r) {return payments.reconcile(id,r);}
  @PostMapping("/template-purchases/{id}/disputes") public Map<String,Object> dispute(@PathVariable UUID id,@Valid @RequestBody CommerceRequests.Dispute b,HttpServletRequest r) {return payments.dispute(id,TypedInputs.map(b),r);}
  @GetMapping("/template-purchases/{id}/download") public ResponseEntity<byte[]> download(@PathVariable UUID id,HttpServletRequest r) {return payments.download(id,r);}
  @PostMapping("/commerce/razorpay/webhook") public Map<String,Object> webhook(HttpServletRequest r) throws IOException {return payments.webhook(r);}
  @GetMapping("/admin/template-purchases/attention") public Map<String,Object> attention(HttpServletRequest r) {return payments.attention(r);}
  @PostMapping("/admin/template-purchases/{id}/bind-order") public Map<String,Object> bind(@PathVariable UUID id,@Valid @RequestBody PaymentRequests.Bind b,HttpServletRequest r) {return payments.bind(id,TypedInputs.map(b),r);}
  @PostMapping("/admin/template-purchases/{id}/reconcile") public Map<String,Object> adminReconcile(@PathVariable UUID id,HttpServletRequest r) {return payments.adminReconcile(id,r);}
  @PostMapping("/admin/template-purchases/{id}/resolve") public Map<String,Object> resolve(@PathVariable UUID id,@Valid @RequestBody CommerceRequests.Resolve b,HttpServletRequest r) {return payments.resolve(id,TypedInputs.map(b),r);}
}
