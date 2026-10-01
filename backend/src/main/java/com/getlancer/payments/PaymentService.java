package com.getlancer.payments;

import static com.getlancer.shared.Support.*;
import static com.getlancer.payments.PaymentRepository.number;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.delivery.DeliveryRepository;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentService {
  final PaymentRepository repo;
  final DeliveryRepository delivery;
  final Security security;
  final RazorpayClient provider;
  final ObjectMapper json;
  final TransactionTemplate transaction;
  public PaymentService(PaymentRepository repo,DeliveryRepository delivery,Security security,RazorpayClient provider,ObjectMapper json,PlatformTransactionManager manager) {
    this.repo=repo; this.delivery=delivery; this.security=security; this.provider=provider; this.json=json;
    this.transaction=new TransactionTemplate(manager);
  }
  private <T> T tx(Supplier<T> work) { return transaction.execute(status->work.get()); }

  public Map<String,Object> configuration() { return provider.configuration(); }
  public Map<String,Object> summaries(UUID engagement,HttpServletRequest request) {
    return tx(()-> { delivery.lockEngagement(engagement,request,"ANY"); return Map.of("items",repo.summaries(engagement)); });
  }
  private Map<String,Object> accessible(UUID id,HttpServletRequest request,String side) {
    var row=repo.attempt(id,false);
    delivery.lockMilestone((UUID)row.get("milestone_id"),request,side);
    return repo.attempt(id,true);
  }
  private Map<String,Object> system(UUID id) {
    var row=repo.attempt(id,false);
    delivery.lockMilestoneSystem((UUID)row.get("milestone_id"));
    return repo.attempt(id,true);
  }
  private void mode(Map<String,Object> row) {
    if (!provider.mode.equals(row.get("mode"))) throw new ApiError(409,"PAYMENT_MODE_MISMATCH","This payment belongs to a different provider mode. Ask the operator to reconcile it with its original credentials.");
  }
  private Map<String,Object> checkout(Map<String,Object> row) {
    var result=new LinkedHashMap<>(PaymentRepository.summary(row));
    result.put("keyId",provider.keyId); result.put("mode",row.get("mode")); return result;
  }

  public Map<String,Object> order(UUID milestone,HttpServletRequest request) {
    UUID key=uuid(request.getHeader("Idempotency-Key"));
    provider.requireCollection();
    Map<String,Object> reservation=tx(()-> {
      UUID payer=security.user(request);
      repo.advisoryLock("payment-key:"+payer+":"+key);
      var m=delivery.lockMilestone(milestone,request,"BUYER");
      if (!"ACCEPTED".equals(m.get("status")) || !"ACTIVE".equals(m.get("engagementStatus")))
        throw new ApiError(409,"PAYMENT_NOT_READY","Payment requires an accepted milestone in an active, undisputed engagement.");
      var prior=repo.byKey(payer,key);
      if (prior!=null && !milestone.equals(prior.get("milestone_id")))
        throw new ApiError(409,"IDEMPOTENCY_CONFLICT","Use a fresh request key for a different milestone.");
      if (prior==null) prior=repo.active(milestone);
      if (prior!=null) { mode(prior); return new LinkedHashMap<>(prior); }
      long amount=((Number)m.get("amountMinor")).longValue();
      if (amount<100 || amount>1000000000 || !"INR".equals(m.get("currency")))
        throw new ApiError(409,"INVALID_AGREEMENT_AMOUNT","The agreed milestone amount cannot be collected.");
      UUID id=UUID.randomUUID();
      repo.reserve(id,milestone,payer,key,amount,repo.payee(m,provider.mode),provider.mode);
      delivery.event((UUID)m.get("engagementId"),payer,"PAYMENT_RESERVED","Payment reserved; provider capture is still required.");
      var created=new LinkedHashMap<>(repo.attempt(id,false)); created.put("create",true); return created;
    });
    UUID id=(UUID)reservation.get("id");
    if (!Boolean.TRUE.equals(reservation.get("create"))) {
      if (Set.of("CREATING","UNKNOWN","REJECTED").contains(Objects.toString(reservation.get("status"))))
        throw new ApiError(409,"PAYMENT_REQUIRES_RECONCILIATION","Order creation is unresolved. Check its status or contact the operator; another order will not be created.");
      return checkout(reservation);
    }
    // Reservation is committed before making the external call. A crash cannot result in an unrecorded retry.
    try {
      var order=provider.createOrder(id,number(reservation,"amount_minor"),Objects.toString(reservation.get("account_id")));
      var transfer=validateOrder(reservation,order);
      tx(()-> { var current=system(id); if (current.get("order_id")==null) repo.bind(id,order.path("id").asText(),transfer.status(),transfer.settlement()); return null; });
      return tx(()-> {
        var m=delivery.lockMilestoneSystem(milestone);
        if (!"ACTIVE".equals(m.get("engagementStatus")))
          throw new ApiError(409,"PAYMENT_HELD","The engagement now has a hold. Contact the operator before checkout.");
        return checkout(repo.attempt(id,false));
      });
    } catch (RazorpayClient.ProviderFailure ex) {
      tx(()->{ system(id); repo.creationFailed(id,ex.rejected); return null; });
      throw new ApiError(502,ex.rejected?"PAYMENT_ORDER_REJECTED":"PAYMENT_ORDER_UNKNOWN",ex.rejected?"Razorpay rejected the order. Check the seller setup before trying with a fresh request key.":"Razorpay order creation could not be confirmed. Do not pay again; ask the operator to reconcile this receipt.");
    } catch (ApiError ex) {
      if ("PROVIDER_MISMATCH".equals(ex.code)) tx(()->{ system(id); repo.creationFailed(id,false); return null; });
      throw ex;
    }
  }

  record TransferState(String status,String settlement) {}
  private static ApiError mismatch() { return new ApiError(409,"PROVIDER_MISMATCH","Provider details do not match the frozen payment. Contact the operator."); }
  private static long integer(JsonNode entity,String name) {
    JsonNode field=entity.path(name);
    if (!field.isIntegralNumber() || !field.canConvertToLong()) throw mismatch();
    return field.longValue();
  }
  private static JsonNode transferItems(JsonNode field) { return field.isArray()?field:field.path("items"); }
  private TransferState validateTransfers(Map<String,Object> row,JsonNode field) {
    JsonNode items=transferItems(field);
    if (!items.isArray() || items.size()!=1) throw mismatch();
    JsonNode transfer=items.get(0);
    if (!Objects.toString(row.get("account_id")).equals(transfer.path("recipient").asText(transfer.path("account").asText()))
        || integer(transfer,"amount")!=number(row,"amount_minor") || !"INR".equals(transfer.path("currency").asText())) throw mismatch();
    String source=transfer.path("source").asText();
    if (!source.isEmpty() && !source.equals(row.get("order_id")) && !source.equals(row.get("payment_id"))) throw mismatch();
    String status=transfer.path("status").asText("unknown").toUpperCase(Locale.ROOT);
    String settlement=transfer.path("settlement_status").asText("unknown").toUpperCase(Locale.ROOT);
    if (!Set.of("CREATED","PENDING","PROCESSED","FAILED","REVERSED","PARTIALLY_REVERSED","UNKNOWN").contains(status)) status="UNKNOWN";
    if (!Set.of("PENDING","ON_HOLD","SETTLED","UNKNOWN").contains(settlement)) settlement="UNKNOWN";
    return new TransferState(status,settlement);
  }
  private TransferState validateOrder(Map<String,Object> row,JsonNode order) {
    RazorpayClient.providerId(order.path("id").asText(),"order_");
    if (!Objects.toString(row.get("id")).equals(order.path("receipt").asText())
        || integer(order,"amount")!=number(row,"amount_minor") || !"INR".equals(order.path("currency").asText())
        || order.path("partial_payment").asBoolean(false)) throw mismatch();
    var expected=new LinkedHashMap<>(row); expected.put("order_id",order.path("id").asText());
    return validateTransfers(expected,order.path("transfers"));
  }

  public Map<String,Object> verify(UUID id,Map<String,Object> body,HttpServletRequest request) {
    var row=tx(()->accessible(id,request,"BUYER")); mode(row); provider.requireCredentials();
    String payment=RazorpayClient.providerId(text(body,"razorpay_payment_id",10,40),"pay_");
    if (row.get("order_id")==null || !provider.checkoutSignature(Objects.toString(row.get("order_id")),payment,text(body,"razorpay_signature",64,64)))
      throw new ApiError(400,"INVALID_PAYMENT_SIGNATURE","Payment signature verification failed.");
    return reconcilePayment(row,payment,request);
  }
  public Map<String,Object> reconcile(UUID id,HttpServletRequest request) {
    var row=tx(()->accessible(id,request,"BUYER")); mode(row); provider.requireCredentials();
    if (row.get("order_id")==null) throw new ApiError(409,"ORDER_BINDING_REQUIRED","Ask the operator to find this receipt in the provider dashboard and bind its verified order.");
    return reconcileOrder(row,request);
  }
  private Map<String,Object> reconcileOrder(Map<String,Object> row,HttpServletRequest request) {
    try {
      if (row.get("payment_id")!=null) {
        var summary=reconcilePayment(row,Objects.toString(row.get("payment_id")),request);
        for (String dispute:repo.disputes((UUID)row.get("id"))) reconcileDispute(row,dispute);
        return PaymentRepository.summary(tx(()->system((UUID)row.get("id"))));
      }
      JsonNode collection=provider.orderPayments(Objects.toString(row.get("order_id")));
      JsonNode items=collection.path("items");
      if (!items.isArray() || items.size()>100) throw mismatch();
      JsonNode captured=null;
      for (JsonNode payment:items) if (Set.of("captured","refunded").contains(payment.path("status").asText())) {
        if (captured!=null) throw mismatch(); captured=payment;
      }
      if (captured==null) return PaymentRepository.summary(row);
      return reconcilePayment(row,captured.path("id").asText(),request);
    } catch (RazorpayClient.ProviderFailure ex) { throw new ApiError(502,"PAYMENT_RECONCILIATION_UNAVAILABLE","Provider status is unavailable. The stored payment has not been changed; try reconciliation later."); }
  }
  private Map<String,Object> reconcilePayment(Map<String,Object> snapshot,String payment,HttpServletRequest request) {
    try {
      mode(snapshot);
      var entity=provider.payment(payment);
      if (!payment.equals(entity.path("id").asText()) || !Objects.toString(snapshot.get("order_id")).equals(entity.path("order_id").asText())
          || integer(entity,"amount")!=number(snapshot,"amount_minor") || !"INR".equals(entity.path("currency").asText())) throw mismatch();
      boolean captured=Set.of("captured","refunded").contains(entity.path("status").asText()) && entity.path("captured").asBoolean(false);
      if (!captured) return PaymentRepository.summary(snapshot);
      long refunded=integer(entity,"amount_refunded");
      if (refunded<0 || refunded>number(snapshot,"amount_minor")) throw mismatch();
      TransferState transfer=new TransferState("UNKNOWN","UNKNOWN"); boolean transferUnknown=false;
      try {
        var expected=new LinkedHashMap<>(snapshot); expected.put("payment_id",payment);
        transfer=validateTransfers(expected,provider.transfers(payment));
      } catch (RazorpayClient.ProviderFailure | ApiError ex) { transferUnknown=true; }
      final TransferState confirmedTransfer=transfer; final boolean unknown=transferUnknown;
      return tx(()-> {
        var current=request==null?system((UUID)snapshot.get("id")):accessible((UUID)snapshot.get("id"),request,"BUYER");
        if (current.get("payment_id")!=null && !payment.equals(current.get("payment_id"))) throw mismatch();
        long previous=number(current,"refunded_minor"), total=Math.max(previous,refunded);
        repo.ledger((UUID)current.get("id"),"capture:"+payment,"CAPTURE",number(current,"amount_minor"));
        if (total>previous) repo.ledger((UUID)current.get("id"),"refund-total:"+total,"REFUND",total-previous);
        boolean first=current.get("payment_id")==null;
        repo.captured((UUID)current.get("id"),payment,total,confirmedTransfer.status(),confirmedTransfer.settlement());
        if (unknown) repo.transferAttention((UUID)current.get("id"),"Payment captured; transfer and settlement require provider reconciliation.");
        var milestone=delivery.lockMilestoneSystem((UUID)current.get("milestone_id"));
        if (first) delivery.event((UUID)milestone.get("engagementId"),null,"PAYMENT_CAPTURED","Razorpay confirmed payment capture. This does not establish seller bank settlement.");
        if (total>previous) delivery.event((UUID)milestone.get("engagementId"),null,"PAYMENT_REFUNDED","Razorpay confirmed an increased refund total; milestone completion is held.");
        return PaymentRepository.summary(repo.attempt((UUID)current.get("id"),false));
      });
    } catch (RazorpayClient.ProviderFailure ex) { throw new ApiError(502,"PAYMENT_RECONCILIATION_UNAVAILABLE","Provider status is unavailable. The stored payment has not been changed; try reconciliation later."); }
  }

  public Map<String,Object> accounts(HttpServletRequest request) { security.admin(request); return Map.of("items",repo.accounts()); }
  public Map<String,Object> account(Map<String,Object> body,HttpServletRequest request) {
    UUID admin=security.admin(request); provider.requireCredentials();
    boolean builder=body.get("builderUserId")!=null && !Objects.toString(body.get("builderUserId")).isBlank();
    boolean team=body.get("teamId")!=null && !Objects.toString(body.get("teamId")).isBlank();
    if (builder==team) throw new ApiError(400,"VALIDATION_ERROR","Select exactly one builder or team.");
    UUID builderId=builder?uuid(body.get("builderUserId")):null, teamId=team?uuid(body.get("teamId")):null;
    if (!Boolean.TRUE.equals(body.get("activationConfirmed"))) throw new ApiError(400,"ACTIVATION_CONFIRMATION_REQUIRED","Confirm that this seller has completed provider onboarding and its Route product is activated in the provider dashboard.");
    String account=RazorpayClient.providerId(text(body,"accountId",10,40),"acc_");
    try {
      var entity=provider.account(account);
      if (!account.equals(entity.path("id").asText()) || !"route".equals(entity.path("type").asText()) || !"created".equals(entity.path("status").asText()))
        throw new ApiError(409,"INVALID_LINKED_ACCOUNT","Razorpay did not confirm an available Route linked account.");
      return tx(()->{ security.admin(request); repo.mapAccount(builderId,teamId,account,provider.mode,admin); return Map.of("items",repo.accounts()); });
    } catch (RazorpayClient.ProviderFailure ex) { throw new ApiError(502,"ACCOUNT_VERIFICATION_UNAVAILABLE","Razorpay account verification failed. No seller mapping was saved."); }
  }
  private void reconcileDispute(Map<String,Object> row,String id) {
    var dispute=provider.dispute(id);
    if (!id.equals(dispute.path("id").asText()) || !Objects.toString(row.get("payment_id")).equals(dispute.path("payment_id").asText())
        || !"INR".equals(dispute.path("currency").asText()) || integer(dispute,"amount")<=0 || integer(dispute,"amount")>number(row,"amount_minor")) throw mismatch();
    String state=dispute.path("status").asText();
    if (!Set.of("open","under_review","action_required","lost","closed","won").contains(state)) throw mismatch();
    tx(()->{ var current=system((UUID)row.get("id")); long deducted=integer(dispute,"amount_deducted");
    if (deducted<0 || deducted>number(row,"amount_minor")) throw mismatch();
    repo.dispute((UUID)current.get("id"),id,state,deducted); return null; });
  }
  public Map<String,Object> adminReconcile(UUID id,HttpServletRequest request) {
    security.admin(request); provider.requireCredentials(); var row=tx(()->system(id)); mode(row);
    if (row.get("order_id")==null) throw new ApiError(409,"ORDER_BINDING_REQUIRED","Find the reserved receipt in the provider dashboard and bind its order first.");
    return reconcileOrder(row,null);
  }
  public Map<String,Object> attention(HttpServletRequest request) { security.admin(request); return Map.of("items",repo.attention()); }
  public Map<String,Object> bind(UUID id,Map<String,Object> body,HttpServletRequest request) {
    security.admin(request); provider.requireCredentials();
    var row=tx(()->system(id)); mode(row);
    if (row.get("order_id")!=null || !Set.of("CREATING","UNKNOWN").contains(row.get("status"))) throw new ApiError(409,"ORDER_ALREADY_BOUND","Only an unresolved order can be bound.");
    String order=RazorpayClient.providerId(text(body,"orderId",10,40),"order_");
    try {
      var transfer=validateOrder(row,provider.order(order));
      tx(()->{ security.admin(request); var current=system(id); if (current.get("order_id")!=null) throw new ApiError(409,"ORDER_ALREADY_BOUND","This order is already bound."); repo.bind(id,order,transfer.status(),transfer.settlement()); var m=delivery.lockMilestoneSystem((UUID)current.get("milestone_id")); delivery.event((UUID)m.get("engagementId"),security.user(request),"PAYMENT_ORDER_BOUND","Operator verified and recovered the provider order for its reserved receipt."); return null; });
      // Provider capture can already exist when recovering an uncertain create.
      return reconcileOrder(tx(()->system(id)),null);
    } catch (RazorpayClient.ProviderFailure ex) { throw new ApiError(502,"ORDER_VERIFICATION_UNAVAILABLE","Provider order verification failed. No order was bound."); }
  }

  public Map<String,Object> webhook(HttpServletRequest request) throws IOException {
    byte[] body=request.getInputStream().readNBytes(65537);
    if (body.length>65536) throw new ApiError(413,"PAYLOAD_TOO_LARGE","Webhook exceeds the supported size.");
    provider.requireCredentials();
    if (!provider.webhookSignature(body,request.getHeader("X-Razorpay-Signature"))) throw new ApiError(400,"INVALID_WEBHOOK_SIGNATURE","Webhook signature verification failed.");
    String eventId=request.getHeader("x-razorpay-event-id");
    if (eventId==null || !eventId.matches("[A-Za-z0-9_.-]{1,100}")) throw new ApiError(400,"INVALID_WEBHOOK_EVENT","Webhook event identifier is required.");
    String digest;
    try { digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)); } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    // Serialize only duplicate event processing, never global payment work. Unlock at transaction end.
    return tx(()-> {
      repo.advisoryLock(eventId);
      String previous=repo.existingEvent(eventId);
      if (previous!=null) {
        if (!previous.equals(digest)) throw new ApiError(409,"WEBHOOK_EVENT_CONFLICT","A different payload used the same webhook event identifier.");
        return Map.of("received",true,"duplicate",true);
      }
      JsonNode event;
      try { event=json.readTree(body); } catch (IOException ex) { throw new ApiError(400,"INVALID_WEBHOOK_EVENT","Invalid webhook JSON."); }
      if (event==null || !event.isObject()) throw new ApiError(400,"INVALID_WEBHOOK_EVENT","Invalid webhook event.");
      String kind=event.path("event").asText();
      if (kind.isBlank() || kind.length()>100) throw new ApiError(400,"INVALID_WEBHOOK_EVENT","Invalid webhook event kind.");
      JsonNode payload=event.path("payload"); String order="",payment="";
      String disputeId="";
      if (kind.startsWith("payment.dispute.")) {
        var d=payload.path("dispute").path("entity"); disputeId=d.path("id").asText(); payment=d.path("payment_id").asText();
        order=provider.payment(payment).path("order_id").asText();
      } else if (Set.of("payment.captured","payment.authorized","payment.failed").contains(kind)) {
        var p=payload.path("payment").path("entity"); order=p.path("order_id").asText(); payment=p.path("id").asText();
      } else if (Set.of("refund.created","refund.processed","refund.failed").contains(kind)) {
        payment=payload.path("refund").path("entity").path("payment_id").asText();
        if (!payment.isBlank()) order=provider.payment(payment).path("order_id").asText();
      } else if (kind.equals("order.paid")) order=payload.path("order").path("entity").path("id").asText();
      else if (kind.startsWith("transfer.")) {
        String source=payload.path("transfer").path("entity").path("source").asText();
        if (source.startsWith("order_")) order=source;
        else if (source.startsWith("pay_")) { payment=source; order=provider.payment(payment).path("order_id").asText(); }
      }
      var row=order.isBlank()?null:repo.byOrder(order);
      if (row!=null) {
        if (payment.isBlank()) reconcileOrder(row,null); else reconcilePayment(row,payment,null);
        if (!disputeId.isBlank()) reconcileDispute(repo.attempt((UUID)row.get("id"),false),disputeId);
      }
      repo.event(eventId,digest,kind);
      return Map.of("received",true,"duplicate",false);
    });
  }
}
