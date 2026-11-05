package com.getlancer.components;

import static com.getlancer.shared.Support.id;
import static com.getlancer.shared.Support.text;
import static com.getlancer.shared.Support.uuid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.payments.RazorpayClient;
import com.getlancer.payments.RefundReconciliation;
import com.getlancer.publishing.PublishingCapacity;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ComponentSlotService {
  final JdbcTemplate db;
  final Security security;
  final RazorpayClient provider;
  final ObjectMapper json;
  final TransactionTemplate transaction;
  final PublishingCapacity publishing;
  public ComponentSlotService(JdbcTemplate db,Security security,RazorpayClient provider,ObjectMapper json,PlatformTransactionManager manager,PublishingCapacity publishing){
    this.db=db;
    this.security=security;
    this.provider=provider;
    this.json=json;
    this.publishing=publishing;
    transaction=new TransactionTemplate(manager);
  }
  <T>T tx(Supplier<T> work){
    return transaction.execute(s->work.get());
  }
  void lock(UUID owner){
    publishing.lock(owner);lockAuthority(owner);
  }
  void lockAuthority(UUID actor){
    db.queryForList("SELECT id FROM users WHERE id=? FOR SHARE",actor);
    db.queryForList("SELECT user_id FROM developer_profiles WHERE user_id=? FOR SHARE",actor);
    db.queryForList("SELECT user_id FROM user_roles WHERE user_id=? ORDER BY role FOR SHARE",actor);
    db.queryForList("SELECT token_hash FROM sessions WHERE user_id=? ORDER BY token_hash FOR SHARE",actor);
  }
  void audit(UUID actor,UUID target,String kind,String detail){
    db.update("INSERT INTO component_audit(id,actor_id,target_id,kind,detail) VALUES(?,?,?,?,?)",id(),actor,target,kind,detail);
  }
  public Map<String,Object> capacity(UUID owner){ return publishing.capacity(owner,"COMPONENT"); }
  public Map<String,Object> pricing(){ return pricing("COMPONENT"); }
  public Map<String,Object> allPricing(){
    return Map.of("PROJECT",pricing("PROJECT"),"TEMPLATE",pricing("TEMPLATE"),"COMPONENT",pricing("COMPONENT"));
  }
  public Map<String,Object> pricing(String requested){
    String pool=PublishingCapacity.pool(requested);
    var p=db.queryForMap("SELECT id,amount_minor,enabled,updated_at,pool FROM component_slot_pricing WHERE pool=?",pool);
    var config=provider.configuration();
    var out=new LinkedHashMap<String,Object>();
    out.put("pool",pool);
    out.put("amountMinor",p.get("amount_minor"));
    out.put("currency","INR");
    out.put("enabled",Boolean.TRUE.equals(p.get("enabled"))&&Boolean.TRUE.equals(config.get("enabled")));
    out.put("configured",p.get("amount_minor")!=null);
    out.put("salesEnabled",p.get("enabled"));
    out.put("mode",config.get("mode"));
    out.put("reason",Boolean.TRUE.equals(p.get("enabled"))?config.get("reason"):"Additional slots are not available yet. The administrator sets their price.");
    return out;
  }
  static long amount(Object value){
    if(!(value instanceof Integer||value instanceof Long))throw new ApiError(400,"VALIDATION_ERROR","Enter a whole minor-unit amount.");
    long n=((Number)value).longValue();
    if(n<100||n>1000000000)throw new ApiError(400,"VALIDATION_ERROR","Use an INR price between ₹1 and ₹1,00,00,000.");
    return n;
  }
  public Object setPrice(Map<String,Object>b,HttpServletRequest r){ return setPrice("COMPONENT",b,r); }
  public Object setPrice(String requested,Map<String,Object>b,HttpServletRequest r){
    String pool=PublishingCapacity.pool(requested);
    return tx(()->{
      UUID admin=security.admin(r);long value=amount(b.get("amountMinor"));boolean enabled=Boolean.TRUE.equals(b.get("enabled"));
      db.queryForMap("SELECT id,amount_minor,enabled,updated_at,pool FROM component_slot_pricing WHERE pool=? FOR UPDATE",pool);
      security.admin(r);db.update("UPDATE component_slot_pricing SET amount_minor=?,enabled=?,updated_at=now() WHERE pool=?",value,enabled,pool);
      audit(admin,null,"SLOT_PRICE_CHANGED",pool+" INR minor units: "+value+"; new orders enabled: "+enabled+". Existing reservations unchanged.");return pricing(pool);
    });
  }
  Map<String,Object> purchase(UUID id){
    var rows=db.queryForList("SELECT id,owner_id,idempotency_key,amount_minor,currency,mode,status,order_id,payment_id,refunded_minor,dispute_id,dispute_status,created_at,updated_at,pool FROM component_slot_purchases WHERE id=?",id);
    if(rows.isEmpty())throw new ApiError(404,"NOT_FOUND","Slot purchase not found.");
    return rows.get(0);
  }
  Map<String,Object> current(UUID id,HttpServletRequest r,boolean admin){
    var p=purchase(id);
    UUID actor=admin?security.admin(r):security.developer(r,false);
    if(!admin&&!actor.equals(p.get("owner_id")))throw new ApiError(404,"NOT_FOUND","Slot purchase not found.");
    return p;
  }
  Map<String,Object> summary(Map<String,Object>p){
    var out=new LinkedHashMap<String,Object>();
    out.put("id",p.get("id"));
    out.put("pool",p.get("pool"));
    out.put("amountMinor",p.get("amount_minor"));
    out.put("currency","INR");
    out.put("mode",p.get("mode"));
    out.put("status",p.get("status"));
    out.put("orderId",p.get("order_id"));
    out.put("refundedMinor",p.get("refunded_minor"));
    out.put("createdAt",p.get("created_at"));
    out.put("grantsSlot",publishing.grantsSlot((UUID)p.get("id")));
    return out;
  }
  Map<String,Object> checkout(Map<String,Object>p){
    var out=summary(p);
    out.put("keyId",provider.keyId());
    return out;
  }
  static long number(Map<String,Object>p,String key){
    return ((Number)p.get(key)).longValue();
  }
  void mode(Map<String,Object>p){
    if(!provider.mode().equals(p.get("mode")))throw new ApiError(409,"PAYMENT_MODE_MISMATCH","Reconcile this purchase using its original provider mode.");
  }
  public Object history(HttpServletRequest r){
    UUID owner=security.developer(r,false);
    return Map.of("items",db.queryForList("SELECT id,owner_id,idempotency_key,amount_minor,currency,mode,status,order_id,payment_id,refunded_minor,dispute_id,dispute_status,created_at,updated_at,pool FROM component_slot_purchases WHERE owner_id=? AND pool='COMPONENT' ORDER BY created_at DESC,id LIMIT 100",owner).stream().map(this::summary).toList(),"capacity",publishing.capacity(owner,"COMPONENT"),"pricing",pricing());
  }
  public Object history(String requested,HttpServletRequest r){
    String pool=PublishingCapacity.pool(requested);UUID owner=security.developer(r,false);
    var page=com.getlancer.shared.Pages.query(db,r,"SELECT id,owner_id,idempotency_key,amount_minor,currency,mode,status,order_id,payment_id,refunded_minor,dispute_id,dispute_status,created_at,updated_at,pool FROM component_slot_purchases WHERE owner_id=? AND pool=? ORDER BY created_at DESC,id",owner,pool);
    page.put("items",com.getlancer.shared.Pages.items(page).stream().map(this::summary).toList());
    page.put("capacity",publishing.capacity(owner,pool));page.put("pricing",pricing(pool));return page;
  }
  public Object overview(HttpServletRequest r){
    UUID owner=security.developer(r,false);return Map.of("capacities",publishing.all(owner),"prices",allPricing());
  }
  public Object attention(HttpServletRequest r){ return attention("COMPONENT",r); }
  public Object attention(String requested,HttpServletRequest r){
    String pool=PublishingCapacity.pool(requested);security.admin(r);
    var page=com.getlancer.shared.Pages.query(db,r,"SELECT id,owner_id,idempotency_key,amount_minor,currency,mode,status,order_id,payment_id,refunded_minor,dispute_id,dispute_status,created_at,updated_at,pool FROM component_slot_purchases WHERE pool=? ORDER BY CASE WHEN status IN ('CREATING','UNKNOWN','ORDER_CREATED') THEN 0 ELSE 1 END,created_at DESC,id",pool);
    page.put("items",com.getlancer.shared.Pages.items(page).stream().map(this::summary).toList());
    page.put("pricing",pricing(pool));return page;
  }
  public Object order(Map<String,Object>b,HttpServletRequest r){ return order("COMPONENT",b,r); }
  public Object order(String requested,Map<String,Object>b,HttpServletRequest r){
    String pool=PublishingCapacity.pool(requested);
    UUID key=uuid(r.getHeader("Idempotency-Key"));
    provider.requireCollection();
    var reservation=tx(()->{
      UUID owner=security.developer(r,true);lock(owner);security.developer(r,true);       var prior=db.queryForList("SELECT id,owner_id,idempotency_key,amount_minor,currency,mode,status,order_id,payment_id,refunded_minor,dispute_id,dispute_status,created_at,updated_at,pool FROM component_slot_purchases WHERE owner_id=? AND idempotency_key=?",owner,key);       if(!prior.isEmpty()&&!pool.equals(prior.get(0).get("pool")))throw new ApiError(409,"IDEMPOTENCY_CONFLICT","This purchase key belongs to another slot category.");
      if(prior.isEmpty())prior=db.queryForList("SELECT id,owner_id,idempotency_key,amount_minor,currency,mode,status,order_id,payment_id,refunded_minor,dispute_id,dispute_status,created_at,updated_at,pool FROM component_slot_purchases WHERE owner_id=? AND pool=? AND mode=? AND status IN ('CREATING','UNKNOWN','ORDER_CREATED')",owner,pool,provider.mode());       if(!prior.isEmpty()){
        mode(prior.get(0));return new LinkedHashMap<>(prior.get(0));
      }
      var price=db.queryForMap("SELECT id,amount_minor,enabled,updated_at,pool FROM component_slot_pricing WHERE pool=? FOR SHARE",pool);if(!Boolean.TRUE.equals(price.get("enabled"))||price.get("amount_minor")==null)throw new ApiError(409,"SLOT_SALES_DISABLED","The administrator has not enabled additional publishing slots.");       long expected=amount(b.get("amountMinor")),value=number(price,"amount_minor");if(expected!=value)throw new ApiError(409,"SLOT_PRICE_CHANGED","The slot price changed. Review the current price before buying.");       if(!Boolean.TRUE.equals(b.get("purchaseConsent")))throw new ApiError(400,"PURCHASE_CONSENT_REQUIRED","Confirm one reusable publishing slot and the displayed price.");       if(((Number)publishing.capacity(owner,pool).get("limit")).intValue()>=100)throw new ApiError(409,"SLOT_LIMIT","This account has the maximum 100 active publishing slots.");       UUID id=id();db.update("INSERT INTO component_slot_purchases(id,owner_id,pool,idempotency_key,amount_minor,mode,status) VALUES(?,?,?,?,?,?,'CREATING')",id,owner,pool,key,value,provider.mode());audit(owner,id,"SLOT_RESERVED",pool+" reusable publishing slot; frozen INR minor amount "+value+". Provider capture required.");var out=new LinkedHashMap<>(purchase(id));out.put("create",true);return out;
    }
    );
    UUID id=(UUID)reservation.get("id");
    if(!Boolean.TRUE.equals(reservation.get("create"))){
      if(Set.of("CREATING","UNKNOWN","REJECTED").contains(reservation.get("status")))throw new ApiError(409,"PAYMENT_REQUIRES_RECONCILIATION","This order is unresolved. Reconcile the existing receipt; do not pay again.");
      return checkout(reservation);
    }
    try{
      var order=provider.createPlatformOrder(id,number(reservation,"amount_minor"));
      validateOrder(reservation,order);
      return tx(()->{
        lock((UUID)reservation.get("owner_id"));var p=purchase(id);if(p.get("order_id")==null)db.update("UPDATE component_slot_purchases SET order_id=?,status='ORDER_CREATED',updated_at=now() WHERE id=?",order.path("id").asText(),id);security.developer(r,true);return checkout(purchase(id));
      }
      );
    }
    catch(RazorpayClient.ProviderFailure ex){
      tx(()->{
        lock((UUID)reservation.get("owner_id"));db.update("UPDATE component_slot_purchases SET status=?,updated_at=now() WHERE id=? AND order_id IS NULL",ex.rejected()?"REJECTED":"UNKNOWN",id);return null;
      }
      );
      throw new ApiError(502,ex.rejected()?"PAYMENT_ORDER_REJECTED":"PAYMENT_ORDER_UNKNOWN","Order creation could not be completed. Check the existing receipt before trying again.");
    }
    catch(ApiError ex){
      if("PROVIDER_MISMATCH".equals(ex.code))tx(()->{
        lock((UUID)reservation.get("owner_id"));db.update("UPDATE component_slot_purchases SET status='UNKNOWN',updated_at=now() WHERE id=? AND order_id IS NULL",id);return null;
      }
      );
      throw ex;
    }
  }
  static ApiError mismatch(){
    return new ApiError(409,"PROVIDER_MISMATCH","Provider facts do not match the frozen slot purchase. Contact the administrator.");
  }
  static long integer(JsonNode entity,String key){
    var field=entity.path(key);
    if(!field.isIntegralNumber()||!field.canConvertToLong())throw mismatch();
    return field.longValue();
  }
  void validateOrder(Map<String,Object>p,JsonNode order){
    RazorpayClient.providerId(order.path("id").asText(),"order_");
    if(p.get("order_id")!=null&&!p.get("order_id").equals(order.path("id").asText()))throw mismatch();
    if(!Objects.toString(p.get("id")).equals(order.path("receipt").asText())||integer(order,"amount")!=number(p,"amount_minor")||!"INR".equals(order.path("currency").asText())||order.path("partial_payment").asBoolean(false))throw mismatch();
    var transfers=order.path("transfers");
    if(!transfers.isMissingNode()&&!transfers.isNull()){
      var items=transfers.isArray()?transfers:transfers.path("items");
      if(!items.isArray()||!items.isEmpty())throw mismatch();
    }
  }
  public Object verify(UUID id,Map<String,Object>b,HttpServletRequest r){
    var p=current(id,r,false);
    mode(p);
    provider.requireCredentials();
    String payment=RazorpayClient.providerId(text(b,"razorpay_payment_id",10,40),"pay_");
    if(p.get("order_id")==null||!provider.checkoutSignature(Objects.toString(p.get("order_id")),payment,text(b,"razorpay_signature",64,64)))throw new ApiError(400,"INVALID_PAYMENT_SIGNATURE","Checkout signature verification failed.");
    return reconcilePayment(p,payment,r,false);
  }
  public Object reconcile(UUID id,HttpServletRequest r,boolean admin){
    var p=current(id,r,admin);
    mode(p);
    provider.requireCredentials();
    try{
      return reconcileOrder(p,r,admin);
    }
    catch(RazorpayClient.ProviderFailure ex){
      throw unavailable();
    }
  }
  static ApiError unavailable(){
    return new ApiError(502,"PAYMENT_RECONCILIATION_UNAVAILABLE","Provider status is unavailable. Your stored payment and capacity are unchanged; reconcile later.");
  }
  Object reconcileOrder(Map<String,Object>p,HttpServletRequest r,boolean admin){
    if(p.get("order_id")==null)throw new ApiError(409,"ORDER_BINDING_REQUIRED","The administrator must recover and bind this receipt before reconciliation.");
    validateOrder(p,provider.order(Objects.toString(p.get("order_id"))));
    if(p.get("payment_id")!=null)return reconcilePayment(p,Objects.toString(p.get("payment_id")),r,admin);
    var items=provider.orderPayments(Objects.toString(p.get("order_id"))).path("items");
    if(!items.isArray()||items.size()>100)throw mismatch();
    JsonNode captured=null;
    for(var item:items)if(Set.of("captured","refunded").contains(item.path("status").asText())){
      if(captured!=null)throw mismatch();
      captured=item;
    }
    return captured==null?summary(p):reconcilePayment(p,captured.path("id").asText(),r,admin);
  }
  private record DisputeObservation(String id,String status,List<String> events) {}
  private List<String> disputes(UUID purchase,String order,String payment) {
    // updated_at records every accepted observation, even an unchanged status.
    // Rotate within pending/held/won tiers; the purchase field is a fallback
    // only until its independent dispute fact has been retained.
    return db.queryForList("SELECT work.id FROM (SELECT id,CASE WHEN status<>'won' THEN 1 ELSE 2 END AS priority FROM component_slot_disputes WHERE purchase_id=? UNION ALL SELECT p.dispute_id,CASE WHEN p.dispute_status<>'won' THEN 1 ELSE 2 END AS priority FROM component_slot_purchases p WHERE p.id=? AND p.dispute_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM component_slot_disputes d WHERE d.id=p.dispute_id AND d.purchase_id=p.id) UNION ALL SELECT dispute_id,0 AS priority FROM component_slot_events WHERE processed_at IS NULL AND event_kind LIKE 'payment.dispute.%' AND dispute_id IS NOT NULL AND (order_id=? OR payment_id=?)) work LEFT JOIN component_slot_disputes observed ON observed.id=work.id GROUP BY work.id,observed.updated_at ORDER BY min(priority),observed.updated_at NULLS FIRST,work.id LIMIT 8",String.class,purchase,purchase,order,payment);
  }
  private List<Map<String,Object>> refundEvents(String order,String payment) {
    return db.queryForList("SELECT event_id,refund_id FROM component_slot_events WHERE processed_at IS NULL AND event_kind LIKE 'refund.%' AND (order_id=? OR payment_id=?) ORDER BY received_at,event_id LIMIT 200",order,payment);
  }
  private void processedEvent(String event) {db.update("UPDATE component_slot_events SET processed_at=clock_timestamp() WHERE event_id=? AND processed_at IS NULL",event);}
  private void refund(UUID purchase,String id,long amount,String status) {
    int changed=db.update("INSERT INTO component_slot_refunds(id,purchase_id,amount_minor,status) VALUES(?,?,?,?) ON CONFLICT(id) DO UPDATE SET status=CASE WHEN component_slot_refunds.status='processed' THEN 'processed' WHEN component_slot_refunds.status='failed' AND excluded.status='pending' THEN 'failed' ELSE excluded.status END,updated_at=now() WHERE component_slot_refunds.purchase_id=excluded.purchase_id AND component_slot_refunds.amount_minor=excluded.amount_minor",id,purchase,amount,status);
    if(changed!=1)throw mismatch();
  }
  Object reconcilePayment(Map<String,Object>p,String payment,HttpServletRequest r,boolean admin){
    mode(p);UUID purchase=(UUID)p.get("id"),owner=(UUID)p.get("owner_id");
    long generation=tx(()->{lock(owner);if(r!=null)current(purchase,r,admin);return db.queryForObject("UPDATE component_slot_purchases SET reconciliation_generation=reconciliation_generation+1 WHERE id=? RETURNING reconciliation_generation",Long.class,purchase);});
    try{
      var events=refundEvents(Objects.toString(p.get("order_id")),payment);
      var entity=provider.payment(RazorpayClient.providerId(payment,"pay_"));
      if(!payment.equals(entity.path("id").asText())||!Objects.toString(p.get("order_id")).equals(entity.path("order_id").asText())||integer(entity,"amount")!=number(p,"amount_minor")||!"INR".equals(entity.path("currency").asText()))throw mismatch();
      if(!entity.path("captured").asBoolean(false)||!Set.of("captured","refunded").contains(entity.path("status").asText())){if(r!=null)current(purchase,r,admin);return summary(purchase(purchase));}
      long aggregate=integer(entity,"amount_refunded");if(aggregate<0||aggregate>number(p,"amount_minor"))throw mismatch();
      var refunds=RefundReconciliation.observe(provider,events,payment,number(p,"amount_minor"));
      var observed=new ArrayList<DisputeObservation>();
      for(String dispute:disputes(purchase,Objects.toString(p.get("order_id")),payment)){
        var pending=db.queryForList("SELECT event_id FROM component_slot_events WHERE processed_at IS NULL AND dispute_id=? AND (order_id=? OR payment_id=?) ORDER BY received_at,event_id LIMIT 200",String.class,dispute,p.get("order_id"),payment);
        observed.add(new DisputeObservation(dispute,verifiedDispute(p,dispute,payment),pending));
      }
      tx(()->{
        lock(owner);var row=purchase(purchase);
        if(db.queryForObject("SELECT reconciliation_generation FROM component_slot_purchases WHERE id=?",Long.class,purchase)!=generation)return null;
        if(row.get("payment_id")!=null&&!payment.equals(row.get("payment_id")))throw mismatch();
        for(var entry:refunds.refunds().entrySet())refund(purchase,entry.getKey(),integer(entry.getValue(),"amount"),entry.getValue().path("status").asText());
        String ds=Objects.toString(row.get("dispute_status"),null),disputeId=Objects.toString(row.get("dispute_id"),null);
        for(var dispute:observed){
          int changed=db.update("INSERT INTO component_slot_disputes(id,purchase_id,status,updated_at) VALUES(?,?,?,clock_timestamp()) ON CONFLICT(id) DO UPDATE SET status=excluded.status,updated_at=clock_timestamp() WHERE component_slot_disputes.purchase_id=excluded.purchase_id",dispute.id(),purchase,dispute.status());
          if(changed!=1)throw mismatch();ds=dispute.status();disputeId=dispute.id();
        }
        long confirmed=db.queryForObject("SELECT coalesce(sum(amount_minor),0) FROM component_slot_refunds WHERE purchase_id=? AND status='processed'",Long.class,purchase);
        long total=Math.max(number(row,"refunded_minor"),Math.max(aggregate,confirmed));if(total>number(row,"amount_minor"))throw mismatch();
        boolean held=db.queryForObject("SELECT count(*) FROM component_slot_disputes WHERE purchase_id=? AND status<>'won'",Integer.class,purchase)>0;
        String state=total>0?"REFUNDED":held||(ds!=null&&!ds.equals("won"))?"DISPUTED":"CAPTURED";
        db.update("INSERT INTO component_slot_ledger(id,purchase_id,entry_key,kind,amount_minor) VALUES(?,?,?,'CAPTURE',?) ON CONFLICT(purchase_id,entry_key) DO NOTHING",id(),purchase,"capture:"+payment,number(row,"amount_minor"));
        if(total>number(row,"refunded_minor"))db.update("INSERT INTO component_slot_ledger(id,purchase_id,entry_key,kind,amount_minor) VALUES(?,?,?,'REFUND',?) ON CONFLICT(purchase_id,entry_key) DO NOTHING",id(),purchase,"refund-total:"+total,total-number(row,"refunded_minor"));
        db.update("UPDATE component_slot_purchases SET payment_id=?,refunded_minor=?,dispute_id=?,dispute_status=?,status=?,updated_at=now() WHERE id=?",payment,total,disputeId,ds,state,purchase);
        for(String event:refunds.terminalEvents())processedEvent(event);
        for(var dispute:observed)for(String event:dispute.events())processedEvent(event);
        if(!state.equals(row.get("status"))||total!=number(row,"refunded_minor"))audit(owner,purchase,"SLOT_"+state,"Authoritative provider reconciliation; refunded minor units "+total+"; mode "+row.get("mode"));
        publishing.trim(owner,Objects.toString(row.get("pool")));return null;
      });
      if(r!=null)current(purchase,r,admin);
      return summary(purchase(purchase));
    }catch(RazorpayClient.ProviderFailure ex){throw unavailable();}
  }
  String verifiedDispute(Map<String,Object>p,String id,String payment){
    var d=provider.dispute(RazorpayClient.providerId(id,"disp_"));
    String state=d.path("status").asText();
    long value=integer(d,"amount"),deducted=integer(d,"amount_deducted");
    if(!id.equals(d.path("id").asText())||!payment.equals(d.path("payment_id").asText())||!"INR".equals(d.path("currency").asText())||value<=0||value>number(p,"amount_minor")||deducted<0||deducted>value||!Set.of("open","under_review","action_required","lost","closed","won").contains(state))throw mismatch();
    return state;
  }
  public Object bind(UUID id,Map<String,Object>b,HttpServletRequest r){
    var p=current(id,r,true);
    mode(p);
    String orderId=RazorpayClient.providerId(text(b,"orderId",10,40),"order_");
    try{
      var order=provider.order(orderId);
      if(!orderId.equals(order.path("id").asText()))throw mismatch();
      validateOrder(p,order);
      tx(()->{
        lock((UUID)p.get("owner_id"));UUID admin=security.admin(r);var row=purchase(id);if(row.get("order_id")!=null&&!orderId.equals(row.get("order_id")))throw mismatch();db.update("UPDATE component_slot_purchases SET order_id=?,status=CASE WHEN order_id IS NULL THEN 'ORDER_CREATED' ELSE status END,updated_at=now() WHERE id=?",orderId,id);audit(admin,id,"SLOT_ORDER_BOUND","Verified provider order, frozen receipt and amount recovered.");return null;
      }
      );
      return reconcileOrder(purchase(id),r,true);
    }
    catch(RazorpayClient.ProviderFailure ex){
      throw unavailable();
    }
  }
  private Map<String,Object> existingEvent(String event){
    var rows=db.queryForList("SELECT payload_hash,event_kind,processed_at IS NULL AS pending FROM component_slot_events WHERE event_id=?",event);
    return rows.isEmpty()?null:rows.get(0);
  }
  private Map<String,Object> eventPurchase(String order,String payment){
    var rows=db.queryForList("SELECT id,owner_id,pool FROM component_slot_purchases WHERE order_id=? OR payment_id=?",order,payment);
    if(rows.size()>1)throw mismatch();return rows.isEmpty()?null:rows.get(0);
  }
  private void associateEvent(String event,String order){
    if(db.update("UPDATE component_slot_events SET order_id=? WHERE event_id=? AND (order_id IS NULL OR order_id=?)",order,event,order)!=1)throw mismatch();
  }
  private boolean acceptEvent(String event,String digest,String kind,String order,String payment,String dispute,String refund){
    return tx(()->{
      db.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"component-event:"+event);
      var prior=existingEvent(event);
      if(prior!=null&&!digest.equals(prior.get("payload_hash")))throw new ApiError(409,"WEBHOOK_EVENT_CONFLICT","Conflicting webhook redelivery.");
      var known=eventPurchase(order,payment);if(known!=null)lock((UUID)known.get("owner_id"));
      if(prior==null){
        db.update("INSERT INTO component_slot_events(event_id,payload_hash,event_kind,order_id,payment_id,dispute_id,refund_id,processed_at) VALUES(?,?,?,nullif(?,''),nullif(?,''),nullif(?,''),nullif(?,''),CASE WHEN ?='' AND ?='' THEN clock_timestamp() ELSE NULL END)",event,digest,kind,order,payment,dispute,refund,order,payment);
      }else{
        // A matching signed legacy replay can supply its previously unknown
        // type/references. Reopened adverse evidence then gets the new checks.
        int changed=db.update("UPDATE component_slot_events SET event_kind=coalesce(event_kind,?),order_id=coalesce(order_id,nullif(?,'')),payment_id=coalesce(payment_id,nullif(?,'')),dispute_id=coalesce(dispute_id,nullif(?,'')),refund_id=coalesce(refund_id,nullif(?,'')),processed_at=CASE WHEN event_kind IS NULL AND (?<>'' OR ?<>'') THEN NULL ELSE processed_at END WHERE event_id=? AND (event_kind IS NULL OR event_kind=?) AND (?='' OR order_id IS NULL OR order_id=?) AND (?='' OR payment_id IS NULL OR payment_id=?) AND (?='' OR dispute_id IS NULL OR dispute_id=?) AND (?='' OR refund_id IS NULL OR refund_id=?)",kind,order,payment,dispute,refund,order,payment,event,kind,order,order,payment,payment,dispute,dispute,refund,refund);
        if(changed!=1)throw mismatch();
      }
      if(known!=null&&(kind.startsWith("refund.")||kind.startsWith("payment.dispute.")))publishing.trim((UUID)known.get("owner_id"),Objects.toString(known.get("pool")));
      return prior!=null;
    });
  }
  public Object webhook(HttpServletRequest r)throws IOException{
    provider.requireCredentials();byte[] raw=r.getInputStream().readNBytes(65537);
    if(raw.length>65536)throw new ApiError(413,"PAYLOAD_TOO_LARGE","Webhook exceeds the bounded limit.");
    if(!provider.maintenanceWebhookSignature(raw,r.getHeader("X-Razorpay-Signature")))throw new ApiError(400,"INVALID_WEBHOOK_SIGNATURE","Webhook signature verification failed.");
    String event=r.getHeader("x-razorpay-event-id");if(event==null||!event.matches("[A-Za-z0-9_-]{1,100}"))throw new ApiError(400,"INVALID_WEBHOOK_EVENT","Missing event identifier.");
    String digest;
    try{digest=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw));}catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    var prior=existingEvent(event);
    if(prior!=null){
      if(!digest.equals(prior.get("payload_hash")))throw new ApiError(409,"WEBHOOK_EVENT_CONFLICT","Conflicting webhook redelivery.");
      if(prior.get("event_kind")!=null&&!Boolean.TRUE.equals(prior.get("pending")))return Map.of("received",true,"duplicate",true);
    }
    JsonNode node;try{node=json.readTree(raw);}catch(IOException ex){throw new ApiError(400,"INVALID_WEBHOOK_EVENT","Invalid webhook JSON.");}
    if(node==null||!node.isObject()||node.path("event").asText().isBlank()||node.path("event").asText().length()>100)throw new ApiError(400,"INVALID_WEBHOOK_EVENT","Invalid webhook event.");
    String kind=node.path("event").asText(),payment="",order="",dispute="",refund="";var payload=node.path("payload");
    if(kind.startsWith("payment.dispute.")){
      var d=payload.path("dispute").path("entity");dispute=RazorpayClient.providerId(d.path("id").asText(),"disp_");payment=RazorpayClient.providerId(d.path("payment_id").asText(),"pay_");
    }else if(Set.of("payment.captured","payment.authorized","payment.failed").contains(kind)){
      var p=payload.path("payment").path("entity");payment=RazorpayClient.providerId(p.path("id").asText(),"pay_");order=RazorpayClient.providerId(p.path("order_id").asText(),"order_");
    }else if(kind.startsWith("refund.")){
      var f=payload.path("refund").path("entity");payment=RazorpayClient.providerId(f.path("payment_id").asText(),"pay_");refund=RazorpayClient.providerId(f.path("id").asText(),"rfnd_");
    }else if(kind.equals("order.paid"))order=RazorpayClient.providerId(payload.path("order").path("entity").path("id").asText(),"order_");
    boolean duplicate=acceptEvent(event,digest,kind,order,payment,dispute,refund);
    try{
      if(order.isBlank()&&!payment.isBlank()){
        var entity=provider.payment(payment);if(!payment.equals(entity.path("id").asText()))throw mismatch();order=RazorpayClient.providerId(entity.path("order_id").asText(),"order_");
        final String associatedOrder=order,paymentRef=payment;
        tx(()->{var known=eventPurchase(associatedOrder,paymentRef);if(known!=null)lock((UUID)known.get("owner_id"));associateEvent(event,associatedOrder);if(known!=null&&(kind.startsWith("refund.")||kind.startsWith("payment.dispute.")))publishing.trim((UUID)known.get("owner_id"),Objects.toString(known.get("pool")));return null;});
      }
      if(order.isBlank())return Map.of("received",true,"duplicate",duplicate);
      JsonNode providerOrder=provider.order(order);if(!order.equals(providerOrder.path("id").asText()))throw mismatch();
      var known=eventPurchase(order,payment);
      if(known==null){
        String receipt=providerOrder.path("receipt").asText();
        if(receipt.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")){
          var rows=db.queryForList("SELECT id,owner_id,pool FROM component_slot_purchases WHERE id=?",UUID.fromString(receipt));if(!rows.isEmpty())known=rows.get(0);
        }
      }
      if(known!=null){
        UUID purchase=(UUID)known.get("id");var snapshot=purchase(purchase);mode(snapshot);validateOrder(snapshot,providerOrder);final String orderRef=order;
        var bound=tx(()->{
          lock((UUID)snapshot.get("owner_id"));var row=purchase(purchase);
          if(row.get("order_id")!=null&&!orderRef.equals(row.get("order_id")))throw mismatch();
          if(row.get("order_id")==null){db.update("UPDATE component_slot_purchases SET order_id=?,status='ORDER_CREATED',updated_at=now() WHERE id=?",orderRef,purchase);audit((UUID)row.get("owner_id"),purchase,"SLOT_ORDER_RECOVERED","Signed event matched authoritative frozen receipt and amount.");}
          associateEvent(event,orderRef);
          if(kind.startsWith("refund.")||kind.startsWith("payment.dispute."))publishing.trim((UUID)row.get("owner_id"),Objects.toString(row.get("pool")));
          return purchase(purchase);
        });
        if(payment.isBlank())reconcileOrder(bound,null,false);else reconcilePayment(bound,payment,null,false);
        if(dispute.isBlank()&&refund.isBlank())tx(()->{processedEvent(event);return null;});
      }
      return Map.of("received",true,"duplicate",duplicate);
    }catch(RazorpayClient.ProviderFailure ex){throw unavailable();}
  }
}
