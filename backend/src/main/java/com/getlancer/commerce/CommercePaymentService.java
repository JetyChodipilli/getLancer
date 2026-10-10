package com.getlancer.commerce;

import static com.getlancer.commerce.CommerceRepository.number;
import static com.getlancer.shared.Support.hash;
import static com.getlancer.shared.Support.id;
import static com.getlancer.shared.Support.text;
import static com.getlancer.shared.Support.uuid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.payments.RazorpayClient;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CommercePaymentService {
  private final CommerceRepository repo;private final Security security;private final RazorpayClient provider;private final CommerceService sources;private final ObjectMapper json;private final TransactionTemplate transaction;private final String webhookSecret;private final CommerceEducationBinding education;
  public CommercePaymentService(CommerceRepository repo,Security security,RazorpayClient provider,CommerceService sources,ObjectMapper json,PlatformTransactionManager manager,CommerceEducationBinding education,@Value("${app.commerce.webhook-secret:}") String secret) {this.repo=repo;this.security=security;this.provider=provider;this.sources=sources;this.json=json;transaction=new TransactionTemplate(manager);webhookSecret=secret;this.education=education;}
  private <T> T tx(java.util.function.Supplier<T> work) {return transaction.execute(status->work.get());}
  private Map<String,Object> accessible(UUID id,HttpServletRequest r) {UUID buyer=security.user(r);var row=repo.purchase(id,true);if(!buyer.equals(row.get("buyer_id"))) throw new ApiError(404,"NOT_FOUND","Purchase not found.");return row;}
  private void mode(Map<String,Object> row) {if(!provider.mode().equals(row.get("mode"))) throw new ApiError(409,"PAYMENT_MODE_MISMATCH","Use the original provider mode to reconcile this purchase.");}
  private Map<String,Object> checkout(Map<String,Object> row) {var out=repo.summary(row);out.put("keyId",provider.keyId());return out;}
  private void available(Map<String,Object> t,Map<String,Object> v) {if(!"ACTIVE".equals(t.get("status")) || !"APPROVED".equals(v.get("status"))) throw new ApiError(409,"TEMPLATE_UNAVAILABLE","Only a current approved release can be purchased.");repo.sellerEligible((UUID)t.get("seller_id"),(UUID)t.get("product_id"),true);}
  public Map<String,Object> order(UUID template,Map<String,Object> body,HttpServletRequest r) {
    UUID key=uuid(r.getHeader("Idempotency-Key")),version=uuid(body.get("versionId"));
    UUID educationRelease=body.get("educationReleaseId")==null?null:uuid(body.get("educationReleaseId"));
    if(!Boolean.TRUE.equals(body.get("licenseConsent"))) throw new ApiError(400,"LICENSE_CONSENT_REQUIRED","Accept the immutable release license before checkout.");
    provider.requireCollection();
    var reservation=tx(()->{
      UUID buyer=security.user(r);education.lockTemplateProduct(template);
      repo.lock("key:"+buyer+":"+key);repo.lock("purchase:"+buyer+":"+version+":"+provider.mode());
      var t=repo.template(template,true);var v=repo.version(template,version,true);available(t,v);
      if(buyer.equals(t.get("seller_id"))) throw new ApiError(403,"SELF_PURCHASE","A seller cannot purchase their own release.");
      var accepted=education.bind(educationRelease,t,v);var prior=repo.byKey(buyer,key);
      if(prior!=null && (!version.equals(prior.get("version_id")) || !Objects.equals(educationRelease,prior.get("education_release_id"))))
        throw new ApiError(409,"IDEMPOTENCY_CONFLICT","Use a new checkout key for a different source or education release.");
      if(prior==null) prior=repo.current(buyer,version,provider.mode());
      if(prior!=null) {
        if(!Objects.equals(educationRelease,prior.get("education_release_id"))) throw new ApiError(409,"IDEMPOTENCY_CONFLICT","This source version already has a purchase with different accepted package disclosures.");
        mode(prior);return new LinkedHashMap<>(prior);
      }
      UUID id=UUID.randomUUID();repo.reserve(id,buyer,key,t,v,repo.payee((UUID)t.get("seller_id"),provider.mode()),provider.mode(),educationRelease,education.encode(accepted));
      repo.audit(template,id,buyer,"PURCHASE_RESERVED","Buyer accepted frozen source license, price and any reviewed college package; provider capture remains required.");
      var out=new LinkedHashMap<>(repo.purchase(id,false));out.put("create",true);return out;
    });
    UUID id=(UUID)reservation.get("id");if(!Boolean.TRUE.equals(reservation.get("create"))) {if(Set.of("CREATING","UNKNOWN","REJECTED").contains(reservation.get("status"))) throw new ApiError(409,"PAYMENT_REQUIRES_RECONCILIATION","Order creation is unresolved. Operator recovery is required before another order can be created.");return checkout(reservation);}
    // Committed reservation precedes all provider calls; uncertain outcomes cannot silently create a second order.
    try {JsonNode order=provider.createOrder(id,number(reservation,"amount_minor"),Objects.toString(reservation.get("account_id")));String transfer=validateOrder(reservation,order);tx(()->{var p=repo.purchase(id,true);if(p.get("order_id")==null) repo.bind(id,order.path("id").asText(),transfer);return null;});return tx(()->{education.lockTemplateProduct(template);var t=repo.template(template,true);var v=repo.version(template,version,true);security.user(r);available(t,v);education.bind(educationRelease,t,v);return checkout(repo.purchase(id,false));});}
    catch(RazorpayClient.ProviderFailure e) {tx(()->{repo.purchase(id,true);repo.failed(id,e.rejected());return null;});throw new ApiError(502,e.rejected()?"PAYMENT_ORDER_REJECTED":"PAYMENT_ORDER_UNKNOWN",e.rejected()?"The provider rejected order creation. Check seller configuration before trying a fresh checkout key.":"Provider order creation could not be confirmed. Do not pay again; ask the operator to recover this receipt.");}
    catch(ApiError e) {if(e.code.equals("PROVIDER_MISMATCH")) tx(()->{repo.purchase(id,true);repo.failed(id,false);return null;});throw e;}
  }
  private static ApiError mismatch() {return new ApiError(409,"PROVIDER_MISMATCH","Provider facts do not match this frozen source purchase.");}
  private static long integer(JsonNode n,String field) {var value=n.path(field);if(!value.isIntegralNumber() || !value.canConvertToLong()) throw mismatch();return value.longValue();}
  private String validateTransfers(Map<String,Object> p,JsonNode field) {var items=field.isArray()?field:field.path("items");if(!items.isArray() || items.size()!=1) throw mismatch();var transfer=items.get(0);if(!Objects.toString(p.get("account_id")).equals(transfer.path("recipient").asText(transfer.path("account").asText())) || integer(transfer,"amount")!=number(p,"amount_minor") || !"INR".equals(transfer.path("currency").asText())) throw mismatch();String source=transfer.path("source").asText();if(!source.isBlank() && !source.equals(p.get("order_id")) && !source.equals(p.get("payment_id"))) throw mismatch();String status=transfer.path("status").asText("unknown").toUpperCase(Locale.ROOT);return Set.of("CREATED","PENDING","PROCESSED","FAILED","REVERSED","PARTIALLY_REVERSED","UNKNOWN").contains(status)?status:"UNKNOWN";}
  private String validateOrder(Map<String,Object> p,JsonNode order) {String id=RazorpayClient.providerId(order.path("id").asText(),"order_");if(!Objects.toString(p.get("id")).equals(order.path("receipt").asText()) || integer(order,"amount")!=number(p,"amount_minor") || !"INR".equals(order.path("currency").asText()) || order.path("partial_payment").asBoolean(false)) throw mismatch();var expected=new LinkedHashMap<>(p);expected.put("order_id",id);return validateTransfers(expected,order.path("transfers"));}
  public Map<String,Object> purchases(HttpServletRequest r) {return repo.summaries(security.user(r),r);}
  public Map<String,Object> verify(UUID id,Map<String,Object> body,HttpServletRequest r) {var p=tx(()->accessible(id,r));mode(p);provider.requireCredentials();String payment=RazorpayClient.providerId(text(body,"razorpay_payment_id",10,40),"pay_");if(p.get("order_id")==null || !provider.checkoutSignature(Objects.toString(p.get("order_id")),payment,text(body,"razorpay_signature",64,64))) throw new ApiError(400,"INVALID_PAYMENT_SIGNATURE","Checkout signature verification failed.");return reconcilePayment(p,payment,r);}
  public Map<String,Object> reconcile(UUID id,HttpServletRequest r) {return reconcileOrder(tx(()->accessible(id,r)),r);}
  private Map<String,Object> reconcileOrder(Map<String,Object> p,HttpServletRequest r) {mode(p);provider.requireCredentials();if(p.get("order_id")==null) throw new ApiError(409,"ORDER_BINDING_REQUIRED","Operator must bind the provider order for this reserved receipt.");try {
    // Recheck receipt/amount/payee on each reconciliation, including orders recovered after a crash.
    validateOrder(p,provider.order(Objects.toString(p.get("order_id"))));
    if(p.get("payment_id")!=null) {reconcilePayment(p,Objects.toString(p.get("payment_id")),r);for(String dispute:repo.providerDisputes((UUID)p.get("id"))) reconcileDispute(repo.purchase((UUID)p.get("id"),false),dispute);return repo.summary(repo.purchase((UUID)p.get("id"),false));}
    var items=provider.orderPayments(Objects.toString(p.get("order_id"))).path("items");if(!items.isArray() || items.size()>100) throw mismatch();JsonNode captured=null;for(var item:items) if(Set.of("captured","refunded").contains(item.path("status").asText())) {if(captured!=null) throw mismatch();captured=item;}return captured==null?repo.summary(p):reconcilePayment(p,captured.path("id").asText(),r);
  } catch(RazorpayClient.ProviderFailure e) {throw unavailable();}}
  private static ApiError unavailable() {return new ApiError(502,"PAYMENT_RECONCILIATION_UNAVAILABLE","Provider status is unavailable. Stored payment facts remain unchanged; reconcile later.");}
  private Map<String,Object> reconcilePayment(Map<String,Object> snapshot,String payment,HttpServletRequest r) {mode(snapshot);try {var entity=provider.payment(payment);if(!payment.equals(entity.path("id").asText()) || !Objects.toString(snapshot.get("order_id")).equals(entity.path("order_id").asText()) || integer(entity,"amount")!=number(snapshot,"amount_minor") || !"INR".equals(entity.path("currency").asText())) throw mismatch();if(!Set.of("captured","refunded").contains(entity.path("status").asText()) || !entity.path("captured").asBoolean(false)) return repo.summary(snapshot);long refunded=integer(entity,"amount_refunded");if(refunded<0 || refunded>number(snapshot,"amount_minor")) throw mismatch();String transfer="UNKNOWN";try {var expected=new LinkedHashMap<>(snapshot);expected.put("payment_id",payment);transfer=validateTransfers(expected,provider.transfers(payment));} catch(RazorpayClient.ProviderFailure|ApiError e) {/* Captured funds are recorded; entitlement remains held until the payee transfer is confirmed. */}final String transferState=transfer;
    return tx(()->{var current=r==null?repo.purchase((UUID)snapshot.get("id"),true):accessible((UUID)snapshot.get("id"),r);if(current.get("payment_id")!=null && !payment.equals(current.get("payment_id"))) throw mismatch();long prior=number(current,"refunded_minor"),total=Math.max(prior,refunded);repo.ledger((UUID)current.get("id"),"capture:"+payment,"CAPTURE",number(current,"amount_minor"));if(total>prior) repo.ledger((UUID)current.get("id"),"refund-total:"+total,"REFUND",total-prior);repo.capture((UUID)current.get("id"),payment,total,transferState);return repo.summary(repo.purchase((UUID)current.get("id"),false));});
  } catch(RazorpayClient.ProviderFailure e) {throw unavailable();}}
  private void reconcileDispute(Map<String,Object> p,String id) {var d=provider.dispute(id);String status=d.path("status").asText();long amount=integer(d,"amount"),deducted=integer(d,"amount_deducted");if(!id.equals(d.path("id").asText()) || !Objects.toString(p.get("payment_id")).equals(d.path("payment_id").asText()) || !"INR".equals(d.path("currency").asText()) || amount<=0 || amount>number(p,"amount_minor") || deducted<0 || deducted>amount || !Set.of("open","under_review","action_required","lost","closed","won").contains(status)) throw mismatch();tx(()->{repo.purchase((UUID)p.get("id"),true);repo.providerDispute((UUID)p.get("id"),id,status,deducted);return null;});}
  public Map<String,Object> dispute(UUID id,Map<String,Object> b,HttpServletRequest r) {return tx(()->{var p=accessible(id,r);if(p.get("payment_id")==null) throw new ApiError(409,"PURCHASE_NOT_CAPTURED","A captured purchase is required to raise a source dispute.");if(repo.dispute(id)!=null) throw new ApiError(409,"DISPUTE_EXISTS","This purchase already has a recorded dispute; contact support with its reference.");String reason=text(b,"reason",20,2000);repo.dispute(id,(UUID)p.get("buyer_id"),reason);repo.audit((UUID)p.get("template_id"),id,(UUID)p.get("buyer_id"),"PURCHASE_DISPUTED",reason);return repo.summary(repo.purchase(id,false));});}
  public ResponseEntity<byte[]> download(UUID id,HttpServletRequest r) {return tx(()->{UUID buyer=security.user(r);var snapshot=repo.purchase(id,false);if(!buyer.equals(snapshot.get("buyer_id"))) throw new ApiError(404,"NOT_FOUND","Purchase not found.");education.lockTemplateProduct((UUID)snapshot.get("template_id"));repo.template((UUID)snapshot.get("template_id"),true);var v=repo.version((UUID)snapshot.get("template_id"),(UUID)snapshot.get("version_id"),true);var p=accessible(id,r);repo.lockEntitlementParties(id);if(!repo.entitled(id,buyer)) throw new ApiError(403,"SOURCE_ENTITLEMENT_REQUIRED","Source download requires a captured live purchase with no refund, dispute or suspension hold.");var response=sources.archiveResponse(v);if(!repo.entitled(id,security.user(r))) throw new ApiError(403,"SOURCE_ENTITLEMENT_REQUIRED","This source entitlement now has a hold.");repo.audit((UUID)p.get("template_id"),id,buyer,"SOURCE_DOWNLOADED","Authenticated buyer downloaded their immutable licensed version.");return response;});}
  public Map<String,Object> attention(HttpServletRequest r) {security.admin(r);return repo.attention(r);}
  public Map<String,Object> adminReconcile(UUID id,HttpServletRequest r) {security.admin(r);var p=tx(()->repo.purchase(id,true));return reconcileOrder(p,null);}
  public Map<String,Object> bind(UUID id,Map<String,Object> b,HttpServletRequest r) {security.admin(r);provider.requireCredentials();var p=tx(()->repo.purchase(id,true));mode(p);if(p.get("order_id")!=null || !Set.of("CREATING","UNKNOWN").contains(p.get("status"))) throw new ApiError(409,"ORDER_ALREADY_BOUND","Only an unresolved order may be bound.");String order=RazorpayClient.providerId(text(b,"orderId",10,40),"order_");try {String transfer=validateOrder(p,provider.order(order));tx(()->{UUID actor=security.admin(r);var current=repo.purchase(id,true);if(current.get("order_id")!=null) throw new ApiError(409,"ORDER_ALREADY_BOUND","This order is already bound.");repo.bind(id,order,transfer);repo.audit((UUID)p.get("template_id"),id,actor,"PURCHASE_ORDER_BOUND","Operator verified provider receipt, amount, currency and seller payee before binding.");return null;});return reconcileOrder(repo.purchase(id,false),null);} catch(RazorpayClient.ProviderFailure e) {throw unavailable();}}
  public Map<String,Object> resolve(UUID id,Map<String,Object> b,HttpServletRequest r) {return tx(()->{UUID actor=security.admin(r);var p=repo.purchase(id,true);String resolution=text(b,"resolution",6,6),reason=text(b,"reason",20,2000);if(!Set.of("RESUME","REVOKE").contains(resolution)) throw new ApiError(400,"VALIDATION_ERROR","Choose RESUME or REVOKE.");if(resolution.equals("RESUME") && (number(p,"refunded_minor")>0 || repo.providerHeld(id))) throw new ApiError(409,"PROVIDER_HOLD","Local resolution cannot clear confirmed refunds or provider disputes.");repo.resolve(id,actor,resolution,reason);repo.audit((UUID)p.get("template_id"),id,actor,"PURCHASE_"+resolution,reason);return repo.summary(repo.purchase(id,false));});}
  public Map<String,Object> webhook(HttpServletRequest r) throws IOException {
    byte[] bytes=r.getInputStream().readNBytes(65537);if(bytes.length>65536) throw new ApiError(413,"PAYLOAD_TOO_LARGE","Webhook body exceeds 64 KiB.");provider.requireCredentials();if(webhookSecret.length()<16) throw new ApiError(503,"COMMERCE_WEBHOOK_UNAVAILABLE","The source-commerce webhook signing secret is not configured.");if(!RazorpayClient.signed(webhookSecret,bytes,r.getHeader("X-Razorpay-Signature"))) throw new ApiError(400,"INVALID_WEBHOOK_SIGNATURE","Webhook signature verification failed.");String eventId=r.getHeader("x-razorpay-event-id");if(eventId==null || !eventId.matches("[A-Za-z0-9_.-]{1,100}")) throw new ApiError(400,"INVALID_WEBHOOK_EVENT","A provider event identifier is required.");String digest=hash(new String(bytes,StandardCharsets.UTF_8));
    // Hash the exact bytes, including malformed UTF-8; parsing is allowed only after signature validation.
    try {digest=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException e) {throw new IllegalStateException(e);}final String bodyHash=digest;
    try {return tx(()->{repo.lock("event:"+eventId);String old=repo.eventHash(eventId);if(old!=null) {if(!old.equals(bodyHash)) throw new ApiError(409,"WEBHOOK_EVENT_CONFLICT","Another payload used this provider event identifier.");return Map.of("received",true,"duplicate",true);}JsonNode event;try {event=json.readTree(bytes);}catch(IOException e) {throw new ApiError(400,"INVALID_WEBHOOK_EVENT","Invalid webhook JSON.");}if(event==null || !event.isObject()) throw new ApiError(400,"INVALID_WEBHOOK_EVENT","Invalid webhook event.");String kind=event.path("event").asText();if(kind.isBlank() || kind.length()>100) throw new ApiError(400,"INVALID_WEBHOOK_EVENT","Invalid webhook event kind.");var payload=event.path("payload");String order="",payment="",dispute="";
      if(kind.startsWith("payment.dispute.")) {var d=payload.path("dispute").path("entity");dispute=d.path("id").asText();payment=d.path("payment_id").asText();order=provider.payment(payment).path("order_id").asText();}
      else if(Set.of("payment.captured","payment.authorized","payment.failed").contains(kind)) {var p=payload.path("payment").path("entity");order=p.path("order_id").asText();payment=p.path("id").asText();}
      else if(Set.of("refund.created","refund.processed","refund.failed").contains(kind)) {payment=payload.path("refund").path("entity").path("payment_id").asText();order=provider.payment(payment).path("order_id").asText();}
      else if(kind.equals("order.paid")) order=payload.path("order").path("entity").path("id").asText();
      else if(kind.startsWith("transfer.")) {String source=payload.path("transfer").path("entity").path("source").asText();if(source.startsWith("order_")) order=source;else if(source.startsWith("pay_")) {payment=source;order=provider.payment(payment).path("order_id").asText();}}
      var p=order.isBlank()?null:repo.byOrder(order);if(p!=null) {mode(p);validateOrder(p,provider.order(order));if(payment.isBlank()) reconcileOrder(p,null);else reconcilePayment(p,payment,null);if(!dispute.isBlank()) reconcileDispute(repo.purchase((UUID)p.get("id"),false),dispute);}repo.event(eventId,bodyHash,kind);return Map.of("received",true,"duplicate",false);});}catch(RazorpayClient.ProviderFailure e) {throw unavailable();}
  }
}
