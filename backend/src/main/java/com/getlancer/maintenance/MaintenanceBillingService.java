package com.getlancer.maintenance;

import static com.getlancer.shared.Support.*;
import static com.getlancer.maintenance.MaintenanceRepository.*;
import static com.getlancer.maintenance.MaintenanceProviderFacts.*;
import com.fasterxml.jackson.databind.*;
import com.getlancer.payments.*;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.function.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MaintenanceBillingService {
  private final MaintenanceRepository repo; private final MaintenanceAccess access; private final PaymentRepository payments;
  private final RazorpayClient provider; private final Security security; private final ObjectMapper json; private final TransactionTemplate transaction;
  private final boolean enabled,approved,jobs; private final String webhookSecret;
  public MaintenanceBillingService(MaintenanceRepository repo,MaintenanceAccess access,PaymentRepository payments,RazorpayClient provider,Security security,ObjectMapper json,PlatformTransactionManager manager,
      @Value("${app.maintenance.enabled:false}") boolean enabled,@Value("${app.maintenance.subscriptions-approved:false}") boolean approved,@Value("${app.maintenance.webhook-secret:}") String secret,@Value("${app.jobs-enabled:true}") boolean jobs) {
    this.repo=repo;this.access=access;this.payments=payments;this.provider=provider;this.security=security;this.json=json;transaction=new TransactionTemplate(manager);this.enabled=enabled;this.approved=approved;webhookSecret=secret;this.jobs=jobs;
  }
  private <T>T tx(Supplier<T> work) {return transaction.execute(s->work.get());}
  public Map<String,Object> configuration() {
    var base=provider.configuration();boolean ready=enabled&&approved&&webhookSecret.length()>=16&&Boolean.TRUE.equals(base.get("enabled"));
    return Map.of("enabled",ready,"keyId",ready?provider.keyId():"","mode",provider.mode(),"reason",ready?"":!enabled?"Maintenance billing is not enabled. Contact the workspace operator.":!approved?"Subscriptions and recurring Route collection require operator approval.":webhookSecret.length()<16?"Maintenance webhooks require configuration.":base.get("reason"));
  }
  private void requireCollection() {if(!Boolean.TRUE.equals(configuration().get("enabled")))throw new ApiError(503,"MAINTENANCE_BILLING_UNAVAILABLE",configuration().get("reason").toString());provider.requireCollection();}
  private void mode(Map<String,Object> s) {if(!provider.mode().equals(s.get("mode")))throw new ApiError(409,"PAYMENT_MODE_MISMATCH","Reconcile using the subscription's original provider mode.");}
  public Map<String,Object> start(UUID offer,Map<String,Object> body,HttpServletRequest r) {
    if(!Boolean.TRUE.equals(body.get("billingConsent")))throw new ApiError(400,"BILLING_CONSENT_REQUIRED","Explicitly authorize the agreed monthly recurring billing before checkout.");
    UUID key=uuid(r.getHeader("Idempotency-Key"));requireCollection();
    var reserved=tx(()->{
      UUID actor=security.user(r);repo.advisory("maintenance-key:"+actor+":"+key);var o=access.offer(offer,r,"BUYER",true);
      equal(o.get("status").equals("ACCEPTED"));var previous=repo.key(actor,key);
      if(previous!=null&&!offer.equals(previous.get("offer_id")))throw new ApiError(409,"IDEMPOTENCY_CONFLICT","Use a fresh key for a different agreement.");
      if(previous==null)previous=repo.forOffer(offer,true);
      if(previous!=null){mode(previous);return new LinkedHashMap<>(previous);}
      if(repo.enrollmentExists((UUID)o.get("engagement_id")))throw new ApiError(409,"MAINTENANCE_ALREADY_ENROLLED","Existing or unresolved billing and prepaid coverage must finish before another enrollment.");
      var engagement=access.delivery.lockEngagement((UUID)o.get("engagement_id"),r,"BUYER");
      UUID id=UUID.randomUUID();repo.reserve(id,o,actor,key,payments.payee(engagement,provider.mode()),provider.mode());repo.audit(offer,actor,"BILLING_AUTHORIZED","Buyer authorized the frozen monthly amount and cycle cap; a live paid invoice is still required.");
      var out=new LinkedHashMap<>(repo.billing(id,false));out.put("create",true);return out;
    });
    UUID id=(UUID)reserved.get("id");
    if(Boolean.TRUE.equals(reserved.get("create"))) {
      try {var plan=provider.createPlan(number(reserved,"amount_minor"),"getLancer care "+id,id,reserved.get("digest").toString());String planId=plan(reserved,plan);boolean bound=tx(()->{access.system(id);return repo.bindPlan(id,planId);});if(bound)createSubscription(id);}
      catch(RazorpayClient.ProviderFailure e){failed(id,e.rejected());throw unknown(e.rejected());}
      catch(ApiError e){failed(id,false);throw e;}
    }
    return tx(()->checkout(access.billing(id,r,"BUYER",true)));
  }
  private void createSubscription(UUID id) {
    var s=tx(()->{var b=access.system(id);mode(b);if(Boolean.TRUE.equals(b.get("cancel_requested"))){if(b.get("provider_subscription_id")==null&&Set.of("NOT_CREATED","REJECTED").contains(Objects.toString(b.get("subscription_state"))))repo.noSubscriptionCancelled(id);b.put("create",false);return new LinkedHashMap<>(b);}access.requireEligible(access.delivery.lockEngagementSystem((UUID)b.get("engagement_id")));requireCollection();if(!"NOT_CREATED".equals(b.get("subscription_state")))throw new ApiError(409,"BILLING_REQUIRES_RECOVERY","Subscription creation is already reserved and must not be repeated.");repo.subscriptionReserved(id);b.put("create",true);return new LinkedHashMap<>(b);});
    if(!Boolean.TRUE.equals(s.get("create")))return;
    var response=provider.createSubscription(s.get("provider_plan_id").toString(),(int)number(s,"total_cycles"),id,s.get("digest").toString());
    String sub=subscription(s,response);tx(()->{access.system(id);repo.bindSubscription(id,sub,response.path("status").asText().toUpperCase(Locale.ROOT));repo.audit((UUID)s.get("offer_id"),null,"SUBSCRIPTION_BOUND","Provider subscription confirmed; invoice payment is still required.");return null;});
    if(Boolean.TRUE.equals(tx(()->repo.billing(id,false)).get("cancel_requested")))cancelSystem(id);
  }
  private void failed(UUID id,boolean rejected){tx(()->{var s=access.system(id);repo.creationFailed(id,s.get("creation_step").toString(),rejected);repo.audit((UUID)s.get("offer_id"),null,"BILLING_CREATION_ATTENTION","Provider creation could not be safely completed. Duplicate creation is blocked.");return null;});}
  private static ApiError unknown(boolean rejected){return new ApiError(502,rejected?"MAINTENANCE_BILLING_REJECTED":"MAINTENANCE_BILLING_UNKNOWN","Billing creation needs operator verification. Do not create another subscription.");}
  private Map<String,Object> checkout(Map<String,Object> s) {var out=summary(s);out.put("keyId",provider.keyId());return out;}
  public Map<String,Object> confirm(UUID offer,Map<String,Object> body,HttpServletRequest r) {
    var s=tx(()->{var o=access.offer(offer,r,"BUYER",false);var b=repo.forOffer((UUID)o.get("id"),true);if(b==null||b.get("provider_subscription_id")==null)throw new ApiError(409,"BILLING_NOT_READY","No confirmed subscription is available.");mode(b);return new LinkedHashMap<>(b);});
    String sub=Objects.toString(body.get("razorpay_subscription_id"),""),payment=RazorpayClient.providerId(Objects.toString(body.get("razorpay_payment_id"),""),"pay_");
    if(!sub.equals(s.get("provider_subscription_id"))||!provider.subscriptionSignature(sub,payment,Objects.toString(body.get("razorpay_signature"),"")))throw new ApiError(400,"PAYMENT_SIGNATURE_INVALID","Subscription checkout signature could not be verified.");
    // Auth payment may be nominal and is not itself support entitlement. Reconcile invoice membership instead.
    reconcile((UUID)s.get("id"));return tx(()->checkout(access.billing((UUID)s.get("id"),r,"BUYER",false)));
  }
  public void refresh(UUID offer,HttpServletRequest r) {UUID id=tx(()->{access.offer(offer,r,"ANY",false);var s=repo.forOffer(offer,true);if(s==null)throw new ApiError(409,"BILLING_NOT_READY","No subscription exists.");return (UUID)s.get("id");});reconcile(id);tx(()->{access.billing(id,r,"ANY",false);return null;});}
  private List<JsonNode> collection(IntFunction<JsonNode> fetch,Instant started) {
    var out=new ArrayList<JsonNode>();var ids=new HashSet<String>();int offset=0;
    for(int page=0;page<101;page++) {if(Instant.now().isAfter(started.plusSeconds(90)))throw new ApiError(409,"PROVIDER_INCOMPLETE","Financial discovery exceeded its time bound. New service is held.");var n=fetch.apply(offset);var items=n.path("items");equal(n.path("entity").asText().equals("collection")&&items.isArray()&&items.size()<=100&&integer(n,"count")==items.size());if(items.isEmpty())return out;
      for(var item:items){equal(item.isObject()&&ids.add(item.path("id").asText())&&!item.path("id").asText().isBlank());out.add(item);}offset+=items.size();if(offset>10000)break;
    }
    throw new ApiError(409,"PROVIDER_INCOMPLETE","Financial discovery exceeded its safe bound. Operator reconciliation is required.");
  }
  public void reconcile(UUID id) {
    Instant started=Instant.now();var initial=tx(()->new LinkedHashMap<>(access.system(id)));mode(initial);provider.requireCredentials();
    var s=tx(()->{access.system(id);repo.beginReconcile(id);return new LinkedHashMap<>(repo.billing(id,false));});
    if(s.get("provider_subscription_id")==null)throw new ApiError(409,"BILLING_REQUIRES_RECOVERY","Provider creation must be recovered by an MFA operator.");
    // Generation checks prevent an older concurrent fetch from overwriting newer financial discovery.
    try {
      plan(s,provider.plan(s.get("provider_plan_id").toString()));var sub=provider.subscription(s.get("provider_subscription_id").toString());subscription(s,sub);
      var invoices=collection(skip->provider.invoices(s.get("provider_subscription_id").toString(),skip),started);
      var discovered=collection(provider::disputes,started);var seen=new HashSet<String>();
      for(var n:invoices) {
        equal(n.path("subscription_id").asText().equals(s.get("provider_subscription_id")));String invoiceId=id(n,"id","inv_");seen.add(invoiceId);
        if(!n.path("status").asText().equals("paid")) {equal(tx(()->repo.byInvoice(invoiceId))==null);continue;}
        var i=invoice(s,n);var pay=provider.payment(i.payment());long refunded=payment(i,pay);var refundRows=collection(skip->provider.refunds(i.payment(),skip),started);var refunds=refunds(i,refundRows,refunded);
        UUID period=tx(()->{generation(s);var prior=repo.byInvoice(i.id());if(prior==null){UUID p=UUID.randomUUID();repo.addPeriod(p,id,i.id(),i.payment(),i.order(),i.amount(),i.start(),i.end());return p;}equal(prior.get("subscription_id").equals(id)&&prior.get("payment_id").equals(i.payment())&&prior.get("order_id").equals(i.order())&&number(prior,"amount_minor")==i.amount()&&instant(prior.get("period_start")).equals(i.start())&&instant(prior.get("period_end")).equals(i.end()));return (UUID)prior.get("id");});
        var disputes=new LinkedHashMap<String,JsonNode>();for(var d:discovered)if(d.path("payment_id").asText().equals(i.payment()))disputes.put(id(d,"id","disp_"),d);
        var knownIds=new HashSet<>(tx(()->repo.knownDisputes(period)));knownIds.addAll(tx(()->repo.eventDisputes(i.payment(),started)));if(knownIds.size()>500)throw new ApiError(409,"PROVIDER_INCOMPLETE","Dispute discovery exceeded its safe bound.");for(String known:knownIds){if(Instant.now().isAfter(started.plusSeconds(90)))throw new ApiError(409,"PROVIDER_INCOMPLETE","Dispute discovery exceeded its time bound.");var fetched=provider.dispute(known);equal(fetched.path("id").asText().equals(known));disputes.put(known,fetched);}
        for(var d:disputes.values())dispute(i,d);
        tx(()->{generation(s);repo.period(period,true);for(var refund:refundRows)repo.refund(period,refund.path("id").asText(),integer(refund,"amount"),refund.path("status").asText());repo.financial(period,refunds.total(),refunds.pending()||repo.pendingRefund(period));for(var d:disputes.values())repo.dispute(period,d.path("id").asText(),d.path("status").asText(),integer(d,"amount_deducted"));return null;});
        budget(started);route(s,period,started);
      }
      budget(started);tx(()->{var current=generation(s);budget(started);for(var p:repo.periods(id))equal(seen.contains(p.get("provider_invoice_id")));repo.status(id,sub.path("status").asText().toUpperCase(Locale.ROOT),terminal(sub.path("status").asText()));repo.fresh(id,started);repo.processed(id,started);if(Boolean.TRUE.equals(current.get("cancel_requested"))&&!terminal(sub.path("status").asText()))repo.cancelUnknown(id);return null;});
    }catch(RuntimeException failure){if(failure instanceof ApiError e&&e.code.equals("RECONCILIATION_SUPERSEDED"))throw e;tx(()->{var current=access.system(id);if(number(current,"reconcile_generation")==number(s,"reconcile_generation"))repo.stale(id,"Provider reconciliation could not confirm all financial facts. New support requests are held.");return null;});if(failure instanceof ApiError error)throw error;throw new ApiError(502,"MAINTENANCE_RECONCILIATION_FAILED","Provider reconciliation is unavailable. New support requests remain held.");}
  }
  private Map<String,Object> generation(Map<String,Object> snapshot) {var current=access.system((UUID)snapshot.get("id"));if(number(current,"reconcile_generation")!=number(snapshot,"reconcile_generation"))throw new ApiError(409,"RECONCILIATION_SUPERSEDED","A newer financial refresh is running. Refresh before continuing.");return current;}
  private static void budget(Instant started) {if(Instant.now().isAfter(started.plusSeconds(90)))throw new ApiError(409,"PROVIDER_INCOMPLETE","Financial discovery exceeded its time bound. New service is held.");}
  private void route(Map<String,Object> s,UUID period,Instant started) {
    var p=tx(()->repo.period(period,false));var response=provider.transfers(p.get("payment_id").toString());var items=response.path("items");equal(items.isArray()&&items.size()<=1);
    if(items.size()==1) {
      String status=transfer(s,p,items.get(0));
      // An ambiguous operation is deliberately bound only by an MFA operator.
      if(Set.of("UNKNOWN","CREATING","REJECTED").contains(p.get("transfer_state"))&&p.get("transfer_id")==null){tx(()->{generation(s);repo.period(period,true);repo.transferObserved(period,status,integer(items.get(0),"amount_reversed"));return null;});return;}
      tx(()->{generation(s);repo.period(period,true);repo.transferBound(period,items.get(0).path("id").asText(),status,integer(items.get(0),"amount_reversed"));return null;});return;
    }
    if(!p.get("transfer_state").equals("NOT_CREATED")){if(p.get("transfer_id")!=null)throw mismatch();return;}
    budget(started);requireCollection();boolean create=tx(()->{var current=generation(s);budget(started);var row=repo.period(period,true);if(!row.get("transfer_state").equals("NOT_CREATED"))return false;
      if(number(row,"refunded_minor")>0||Boolean.TRUE.equals(row.get("pending_refund"))||"HELD".equals(row.get("dispute_status"))||Boolean.TRUE.equals(current.get("local_hold")))return false;
      access.requireEligible(access.delivery.lockEngagementSystem((UUID)s.get("engagement_id")));repo.transferReserved(period);return true;});
    if(!create)return;
    try{var created=provider.createPaymentTransfer(p.get("payment_id").toString(),s.get("account_id").toString(),number(p,"amount_minor"),(UUID)p.get("transfer_key"),p.get("provider_invoice_id").toString());var transfers=created.path("items");equal(transfers.isArray()&&transfers.size()==1);String status=transfer(s,p,transfers.get(0));tx(()->{generation(s);repo.period(period,true);repo.transferBound(period,transfers.get(0).path("id").asText(),status,integer(transfers.get(0),"amount_reversed"));return null;});}
    catch(RuntimeException e){tx(()->{access.system((UUID)s.get("id"));repo.period(period,true);repo.transferUnknown(period,e instanceof RazorpayClient.ProviderFailure f&&f.rejected());return null;});}
  }
  public void cancel(UUID offer,HttpServletRequest r) {UUID id=tx(()->{access.offer(offer,r,"BUYER",false);var s=repo.forOffer(offer,true);if(s==null)throw new ApiError(409,"BILLING_NOT_READY","No subscription exists.");return (UUID)s.get("id");});cancelSystem(id);tx(()->{access.billing(id,r,"BUYER",false);return null;});}
  private void cancelSystem(UUID id) {
    var before=tx(()->{var current=access.system(id);repo.cancelIntent(id);if(!Boolean.TRUE.equals(current.get("cancel_requested")))repo.audit((UUID)current.get("offer_id"),null,"CANCELLATION_INTENT_REQUESTED","Future billing stop requested before provider reads; prepaid eligibility is independent.");if(current.get("provider_subscription_id")==null&&(current.get("subscription_state")==null||Set.of("NOT_CREATED","REJECTED").contains(Objects.toString(current.get("subscription_state")))))repo.noSubscriptionCancelled(id);return new LinkedHashMap<>(repo.billing(id,false));});if(Boolean.TRUE.equals(before.get("cancel_confirmed")))return;
    if(before.get("provider_subscription_id")==null)throw new ApiError(409,"BILLING_REQUIRES_RECOVERY","Recover provider creation before cancelling possible future billing.");
    JsonNode fetched;try{mode(before);provider.requireCredentials();fetched=provider.subscription(before.get("provider_subscription_id").toString());subscription(before,fetched);}catch(RuntimeException failure){tx(()->{access.system(id);repo.audit((UUID)before.get("offer_id"),null,"CANCELLATION_PREFLIGHT_UNCONFIRMED","Cancellation intent retained; provider read could not be confirmed and no cancel POST was made.");return null;});throw failure;}
    if(terminal(fetched.path("status").asText())){tx(()->{access.system(id);repo.status(id,fetched.path("status").asText().toUpperCase(Locale.ROOT),true);return null;});return;}
    boolean call=tx(()->{var s=access.system(id);if(Boolean.TRUE.equals(s.get("cancel_confirmed")))return false;if(s.get("cancel_state")!=null)return false;repo.cancelReserved(id);repo.audit((UUID)s.get("offer_id"),null,"CANCELLATION_REQUESTED","Future billing cancellation requested; prepaid access is evaluated separately.");return true;});
    if(!call)throw new ApiError(409,"CANCELLATION_REQUIRES_RECOVERY","Cancellation is unconfirmed. An operator must reconcile it; no duplicate cancel request was sent.");
    try {provider.cancelSubscription(before.get("provider_subscription_id").toString());var confirmed=provider.subscription(before.get("provider_subscription_id").toString());subscription(before,confirmed);equal(terminal(confirmed.path("status").asText()));tx(()->{access.system(id);repo.status(id,confirmed.path("status").asText().toUpperCase(Locale.ROOT),true);repo.audit((UUID)before.get("offer_id"),null,"CANCELLATION_CONFIRMED","Provider confirmed billing stopped. An undisputed paid period may remain available.");return null;});}
    catch(RuntimeException failure){tx(()->{access.system(id);repo.cancelUnknown(id);return null;});try{var state=provider.subscription(before.get("provider_subscription_id").toString());subscription(before,state);if(terminal(state.path("status").asText())){tx(()->{access.system(id);repo.status(id,state.path("status").asText().toUpperCase(Locale.ROOT),true);return null;});return;}}catch(RuntimeException ignored){}throw new ApiError(502,"CANCELLATION_UNKNOWN","Future billing cancellation is not confirmed. Refresh or ask the operator to reconcile it.");}
  }
  public Map<String,Object> operator(UUID id,String action,Map<String,Object> body,HttpServletRequest r) {
    UUID actor=security.admin(r);String reason=text(body,"reason",10,1000);var s=tx(()->{security.admin(r);var row=access.system(id);mode(row);return new LinkedHashMap<>(row);});
    switch(action) {
      case "bind-plan" -> {equal(s.get("provider_plan_id")==null&&Set.of("UNKNOWN","CREATING").contains(s.get("plan_state")));String candidate=RazorpayClient.providerId(text(body,"providerId",10,40),"plan_");String confirmed=plan(s,provider.plan(candidate));equal(candidate.equals(confirmed));tx(()->{security.admin(r);var current=access.system(id);equal(current.get("provider_plan_id")==null);repo.bindPlan(id,confirmed);repo.audit((UUID)s.get("offer_id"),actor,"OPERATOR_PLAN_BOUND",reason);return null;});try{createSubscription(id);}catch(RuntimeException e){failed(id,e instanceof RazorpayClient.ProviderFailure f&&f.rejected());throw unknown(false);}}
      case "bind-subscription" -> {equal(s.get("provider_subscription_id")==null&&s.get("provider_plan_id")!=null);String candidate=RazorpayClient.providerId(text(body,"providerId",9,40),"sub_");plan(s,provider.plan(s.get("provider_plan_id").toString()));var fetched=provider.subscription(candidate);equal(candidate.equals(subscription(s,fetched)));tx(()->{security.admin(r);var current=access.system(id);equal(current.get("provider_subscription_id")==null);repo.bindSubscription(id,candidate,fetched.path("status").asText().toUpperCase(Locale.ROOT));repo.audit((UUID)s.get("offer_id"),actor,"OPERATOR_SUBSCRIPTION_BOUND",reason);return null;});if(Boolean.TRUE.equals(tx(()->repo.billing(id,false)).get("cancel_requested")))cancelSystem(id);reconcile(id);}
      case "bind-transfer" -> {UUID period=uuid(body.get("periodId"));var p=tx(()->{access.system(id);var row=repo.period(period,true);equal(row.get("subscription_id").equals(id));return new LinkedHashMap<>(row);});String candidate=RazorpayClient.providerId(text(body,"providerId",10,40),"trf_");var items=provider.transfers(p.get("payment_id").toString()).path("items");equal(items.isArray()&&items.size()==1&&items.get(0).path("id").asText().equals(candidate));String status=transfer(s,p,items.get(0));tx(()->{security.admin(r);generation(s);var current=repo.period(period,true);equal(current.get("subscription_id").equals(id));transfer(s,current,items.get(0));repo.transferBound(period,candidate,status,integer(items.get(0),"amount_reversed"));repo.audit((UUID)s.get("offer_id"),actor,"OPERATOR_TRANSFER_BOUND",reason);return null;});reconcile(id);}
      case "hold","release-hold" -> {tx(()->{security.admin(r);access.system(id);repo.hold(id,action.equals("hold"));repo.audit((UUID)s.get("offer_id"),actor,action.equals("hold")?"OPERATOR_HOLD":"OPERATOR_HOLD_RELEASED",reason);return null;});if(action.equals("release-hold"))reconcile(id);}
      case "reconcile" -> {if(Boolean.TRUE.equals(s.get("cancel_requested"))&&!Boolean.TRUE.equals(s.get("cancel_confirmed")))cancelSystem(id);reconcile(id);}
      case "cancel" -> cancelSystem(id);
      default -> throw new ApiError(400,"VALIDATION_ERROR","Unknown maintenance operator action.");
    }
    return tx(()->{security.admin(r);var current=access.system(id);repo.audit((UUID)s.get("offer_id"),actor,"OPERATOR_"+action.toUpperCase(Locale.ROOT).replace('-','_'),reason);var out=summary(current);out.put("periods",repo.periods(id));return out;});
  }
  public Map<String,Object> attention(HttpServletRequest r){security.admin(r);var page=repo.attention(r);for(var row:com.getlancer.shared.Pages.items(page))row.put("periods",repo.periods((UUID)row.get("id")).stream().map(p->{var out=new LinkedHashMap<String,Object>();out.put("id",p.get("id"));out.put("providerInvoiceId",p.get("provider_invoice_id"));out.put("pendingRefund",p.get("pending_refund"));out.put("refundedMinor",p.get("refunded_minor"));out.put("disputeStatus",p.get("dispute_status"));out.put("transferState",p.get("transfer_state"));out.put("transferStatus",p.get("transfer_status"));out.put("attentionReason",p.get("attention_reason"));return out;}).toList());return page;}
  public Map<String,Object> webhook(HttpServletRequest r) {
    byte[] body;try{body=r.getInputStream().readNBytes(65537);}catch(IOException e){throw new ApiError(400,"INVALID_WEBHOOK","Webhook body unavailable.");}
    if(body.length>65536)throw new ApiError(413,"PAYLOAD_TOO_LARGE","Webhook exceeds its bound.");
    if(!RazorpayClient.signed(webhookSecret,body,r.getHeader("X-Razorpay-Signature")))throw new ApiError(400,"INVALID_SIGNATURE","Webhook signature could not be verified.");
    String event=r.getHeader("X-Razorpay-Event-Id");if(event==null||!event.matches("[A-Za-z0-9_.:-]{1,100}"))throw new ApiError(400,"INVALID_WEBHOOK","A provider event identifier is required.");
    try {var n=json.readTree(body);String kind=n.path("event").asText();if(!kind.matches("[a-z_.]{1,100}"))throw new ApiError(400,"INVALID_WEBHOOK","Invalid event kind.");var payload=n.path("payload");String sub=optionalId(payload.path("subscription").path("entity").path("id"),"sub_");if(sub==null)sub=optionalId(payload.path("invoice").path("entity").path("subscription_id"),"sub_");String pay=optionalId(payload.path("payment").path("entity").path("id"),"pay_");if(pay==null)pay=optionalId(payload.path("refund").path("entity").path("payment_id"),"pay_");if(pay==null)pay=optionalId(payload.path("dispute").path("entity").path("payment_id"),"pay_");if(pay==null)pay=optionalId(payload.path("transfer").path("entity").path("source"),"pay_");String dispute=optionalId(payload.path("dispute").path("entity").path("id"),"disp_");String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));String subscription=sub,payment=pay;tx(()->{repo.event(event,digest,kind,subscription,payment,dispute);return null;});return Map.of("accepted",true);}
    catch(ApiError e){throw e;}catch(Exception e){throw new ApiError(400,"INVALID_WEBHOOK","Invalid signed webhook payload.");}
  }
  private static String optionalId(JsonNode n,String prefix){return n.isMissingNode()||n.isNull()?null:RazorpayClient.providerId(n.asText(),prefix);}
  @Scheduled(fixedDelayString="${app.maintenance.reconcile-interval-ms:60000}")
  public void pending(){if(!jobs||!enabled||webhookSecret.length()<16)return;for(UUID id:repo.pending())try{var current=tx(()->repo.billing(id,false));if(Boolean.TRUE.equals(current.get("cancel_requested"))&&!Boolean.TRUE.equals(current.get("cancel_confirmed")))cancelSystem(id);reconcile(id);}catch(RuntimeException ignored){/* Durable inbox and safe attention remain for operator/next bounded pass. */}}
  static Instant instant(Object value){return value instanceof java.sql.Timestamp t?t.toInstant():((java.time.OffsetDateTime)value).toInstant();}
  static Map<String,Object> summary(Map<String,Object> s){var out=new LinkedHashMap<String,Object>();for(String key:List.of("id","status","mode"))out.put(key,s.get(key));out.put("providerSubscriptionId",s.get("provider_subscription_id"));out.put("providerPlanId",s.get("provider_plan_id"));out.put("creationStep",s.get("creation_step"));out.put("attentionReason",s.get("attention_reason"));out.put("cancelRequested",s.get("cancel_requested"));out.put("cancelConfirmed",s.get("cancel_confirmed"));out.put("createdAt",s.get("created_at"));return out;}
}
