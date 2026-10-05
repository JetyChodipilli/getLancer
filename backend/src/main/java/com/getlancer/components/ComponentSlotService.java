package com.getlancer.components;
import static com.getlancer.shared.Support.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.payments.RazorpayClient;
import com.getlancer.security.Security;
import com.getlancer.publishing.PublishingCapacity;
import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
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
    publishing.lock(owner);
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
    var p=db.queryForMap("SELECT * FROM component_slot_pricing WHERE pool=?",pool);
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
      db.queryForMap("SELECT * FROM component_slot_pricing WHERE pool=? FOR UPDATE",pool);
      security.admin(r);db.update("UPDATE component_slot_pricing SET amount_minor=?,enabled=?,updated_at=now() WHERE pool=?",value,enabled,pool);
      audit(admin,null,"SLOT_PRICE_CHANGED",pool+" INR minor units: "+value+"; new orders enabled: "+enabled+". Existing reservations unchanged.");return pricing(pool);
    });
  }
  Map<String,Object> purchase(UUID id){
    var rows=db.queryForList("SELECT * FROM component_slot_purchases WHERE id=?",id);
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
    out.put("grantsSlot","live".equals(p.get("mode"))&&"CAPTURED".equals(p.get("status"))&&number(p,"refunded_minor")==0&&(p.get("dispute_status")==null||"won".equals(p.get("dispute_status"))));
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
  public Object history(HttpServletRequest r){ return history("COMPONENT",r); }
  public Object history(String requested,HttpServletRequest r){
    String pool=PublishingCapacity.pool(requested);UUID owner=security.developer(r,false);
    var page=com.getlancer.shared.Pages.query(db,r,"SELECT * FROM component_slot_purchases WHERE owner_id=? AND pool=? ORDER BY created_at DESC,id",owner,pool);
    page.put("items",com.getlancer.shared.Pages.items(page).stream().map(this::summary).toList());
    page.put("capacity",publishing.capacity(owner,pool));page.put("pricing",pricing(pool));return page;
  }
  public Object overview(HttpServletRequest r){
    UUID owner=security.developer(r,false);return Map.of("capacities",publishing.all(owner),"prices",allPricing());
  }
  public Object attention(HttpServletRequest r){ return attention("COMPONENT",r); }
  public Object attention(String requested,HttpServletRequest r){
    String pool=PublishingCapacity.pool(requested);security.admin(r);
    var page=com.getlancer.shared.Pages.query(db,r,"SELECT * FROM component_slot_purchases WHERE pool=? ORDER BY CASE WHEN status IN ('CREATING','UNKNOWN','ORDER_CREATED') THEN 0 ELSE 1 END,created_at DESC,id",pool);
    page.put("items",com.getlancer.shared.Pages.items(page).stream().map(this::summary).toList());
    page.put("pricing",pricing(pool));return page;
  }
  public Object order(Map<String,Object>b,HttpServletRequest r){ return order("COMPONENT",b,r); }
  public Object order(String requested,Map<String,Object>b,HttpServletRequest r){
    String pool=PublishingCapacity.pool(requested);
    UUID key=uuid(r.getHeader("Idempotency-Key"));
    provider.requireCollection();
    var reservation=tx(()->{
      UUID owner=security.developer(r,true);lock(owner);security.developer(r,true);       var prior=db.queryForList("SELECT * FROM component_slot_purchases WHERE owner_id=? AND idempotency_key=?",owner,key);       if(!prior.isEmpty()&&!pool.equals(prior.get(0).get("pool")))throw new ApiError(409,"IDEMPOTENCY_CONFLICT","This purchase key belongs to another slot category.");
      if(prior.isEmpty())prior=db.queryForList("SELECT * FROM component_slot_purchases WHERE owner_id=? AND pool=? AND mode=? AND status IN ('CREATING','UNKNOWN','ORDER_CREATED')",owner,pool,provider.mode());       if(!prior.isEmpty()){
        mode(prior.get(0));return new LinkedHashMap<>(prior.get(0));
      }
      var price=db.queryForMap("SELECT * FROM component_slot_pricing WHERE pool=? FOR SHARE",pool);if(!Boolean.TRUE.equals(price.get("enabled"))||price.get("amount_minor")==null)throw new ApiError(409,"SLOT_SALES_DISABLED","The administrator has not enabled additional publishing slots.");       long expected=amount(b.get("amountMinor")),value=number(price,"amount_minor");if(expected!=value)throw new ApiError(409,"SLOT_PRICE_CHANGED","The slot price changed. Review the current price before buying.");       if(!Boolean.TRUE.equals(b.get("purchaseConsent")))throw new ApiError(400,"PURCHASE_CONSENT_REQUIRED","Confirm one reusable publishing slot and the displayed price.");       if(((Number)publishing.capacity(owner,pool).get("limit")).intValue()>=100)throw new ApiError(409,"SLOT_LIMIT","This account has the maximum 100 active publishing slots.");       UUID id=id();db.update("INSERT INTO component_slot_purchases(id,owner_id,pool,idempotency_key,amount_minor,mode,status) VALUES(?,?,?,?,?,?,'CREATING')",id,owner,pool,key,value,provider.mode());audit(owner,id,"SLOT_RESERVED",pool+" reusable publishing slot; frozen INR minor amount "+value+". Provider capture required.");var out=new LinkedHashMap<>(purchase(id));out.put("create",true);return out;
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
  Object reconcilePayment(Map<String,Object>p,String payment,HttpServletRequest r,boolean admin){
    mode(p);
    try{
      var entity=provider.payment(RazorpayClient.providerId(payment,"pay_"));
      if(!payment.equals(entity.path("id").asText())||!Objects.toString(p.get("order_id")).equals(entity.path("order_id").asText())||integer(entity,"amount")!=number(p,"amount_minor")||!"INR".equals(entity.path("currency").asText()))throw mismatch();
      if(!entity.path("captured").asBoolean(false)||!Set.of("captured","refunded").contains(entity.path("status").asText()))return summary(p);
      long refunded=integer(entity,"amount_refunded");
      if(refunded<0||refunded>number(p,"amount_minor"))throw mismatch();
      String dispute=p.get("dispute_id")==null?null:verifiedDispute(p,Objects.toString(p.get("dispute_id")),payment);
      return tx(()->{
        lock((UUID)p.get("owner_id"));if(r!=null)current((UUID)p.get("id"),r,admin);var row=purchase((UUID)p.get("id"));if(row.get("payment_id")!=null&&!payment.equals(row.get("payment_id")))throw mismatch();       // A concurrently recorded dispute cannot be cleared by a payment-only observation.
        String ds=Objects.equals(row.get("dispute_id"),p.get("dispute_id"))&&Objects.equals(row.get("dispute_status"),p.get("dispute_status"))?dispute:Objects.toString(row.get("dispute_status"),null);       long total=Math.max(refunded,number(row,"refunded_minor"));String status=total>0?"REFUNDED":ds!=null&&!ds.equals("won")?"DISPUTED":"CAPTURED";       db.update("INSERT INTO component_slot_ledger(id,purchase_id,entry_key,kind,amount_minor) VALUES(?,?,?,'CAPTURE',?) ON CONFLICT(purchase_id,entry_key) DO NOTHING",id(),row.get("id"),"capture:"+payment,number(row,"amount_minor"));       if(total>number(row,"refunded_minor"))db.update("INSERT INTO component_slot_ledger(id,purchase_id,entry_key,kind,amount_minor) VALUES(?,?,?,'REFUND',?) ON CONFLICT(purchase_id,entry_key) DO NOTHING",id(),row.get("id"),"refund-total:"+total,total-number(row,"refunded_minor"));       db.update("UPDATE component_slot_purchases SET payment_id=?,refunded_minor=?,dispute_status=?,status=?,updated_at=now() WHERE id=?",payment,total,ds,status,row.get("id"));       if(!status.equals(row.get("status"))||total!=number(row,"refunded_minor"))audit((UUID)row.get("owner_id"),(UUID)row.get("id"),"SLOT_"+status,"Authoritative provider reconciliation; refunded minor units "+total+"; mode "+row.get("mode"));publishing.trim((UUID)row.get("owner_id"),Objects.toString(row.get("pool")));return summary(purchase((UUID)p.get("id")));
      }
      );
    }
    catch(RazorpayClient.ProviderFailure ex){
      throw unavailable();
    }
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
  public Object webhook(HttpServletRequest r)throws IOException{
    provider.requireCredentials();
    byte[] raw=r.getInputStream().readNBytes(65537);
    if(raw.length>65536)throw new ApiError(413,"PAYLOAD_TOO_LARGE","Webhook exceeds the bounded limit.");
    if(!provider.maintenanceWebhookSignature(raw,r.getHeader("X-Razorpay-Signature")))throw new ApiError(400,"INVALID_WEBHOOK_SIGNATURE","Webhook signature verification failed.");
    String event=r.getHeader("x-razorpay-event-id");
    if(event==null||!event.matches("[A-Za-z0-9_-]{1,100}"))throw new ApiError(400,"INVALID_WEBHOOK_EVENT","Missing event identifier.");
    String digest;
    try{
      digest=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw));
    }
    catch(java.security.NoSuchAlgorithmException ex){
      throw new IllegalStateException(ex);
    }
    JsonNode node;
    try{
      node=json.readTree(raw);
    }
    catch(IOException ex){
      throw new ApiError(400,"INVALID_WEBHOOK_EVENT","Invalid webhook JSON.");
    }
    if(node==null||!node.isObject()||node.path("event").asText().isBlank()||node.path("event").asText().length()>100)throw new ApiError(400,"INVALID_WEBHOOK_EVENT","Invalid webhook event.");
    try{
      return tx(()->{
        db.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"component-event:"+event);var prior=db.queryForList("SELECT payload_hash FROM component_slot_events WHERE event_id=?",event);if(!prior.isEmpty()){
          if(!digest.equals(prior.get(0).get("payload_hash")))throw new ApiError(409,"WEBHOOK_EVENT_CONFLICT","Conflicting webhook redelivery.");return Map.of("received",true,"duplicate",true);
        }
        String kind=node.path("event").asText(),payment="",order="",dispute="";var payload=node.path("payload");       if(kind.startsWith("payment.dispute.")){
          var d=payload.path("dispute").path("entity");dispute=d.path("id").asText();payment=d.path("payment_id").asText();order=provider.payment(payment).path("order_id").asText();
        }
        else if(Set.of("payment.captured","payment.authorized","payment.failed").contains(kind)){
          var e=payload.path("payment").path("entity");payment=e.path("id").asText();order=e.path("order_id").asText();
        }
        else if(kind.startsWith("refund.")){
          payment=payload.path("refund").path("entity").path("payment_id").asText();order=provider.payment(payment).path("order_id").asText();
        }
        var matches=order.isBlank()?List.<Map<String,Object>>of():db.queryForList("SELECT * FROM component_slot_purchases WHERE order_id=?",order);       // Recover a committed reservation from authoritative receipt facts before recording an unmatched event.
        JsonNode providerOrder=null;       if(matches.isEmpty()&&!order.isBlank()){
          providerOrder=provider.order(RazorpayClient.providerId(order,"order_"));String receipt=providerOrder.path("receipt").asText();if(receipt.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))matches=db.queryForList("SELECT * FROM component_slot_purchases WHERE id=?",UUID.fromString(receipt));
        }
        if(!matches.isEmpty()){
          var p=matches.get(0);mode(p);validateOrder(p,providerOrder==null?provider.order(order):providerOrder);lock((UUID)p.get("owner_id"));p=purchase((UUID)p.get("id"));if(p.get("order_id")!=null&&!order.equals(p.get("order_id")))throw mismatch();if(p.get("order_id")==null){
            db.update("UPDATE component_slot_purchases SET order_id=?,status='ORDER_CREATED',updated_at=now() WHERE id=?",order,p.get("id"));audit((UUID)p.get("owner_id"),(UUID)p.get("id"),"SLOT_ORDER_RECOVERED","Signed event matched authoritative frozen receipt and amount.");p=purchase((UUID)p.get("id"));
          }
          if(!dispute.isBlank()){
            String state=verifiedDispute(p,dispute,payment);db.update("UPDATE component_slot_purchases SET dispute_id=?,dispute_status=?,updated_at=now() WHERE id=?",dispute,state,p.get("id"));p=purchase((UUID)p.get("id"));
          }
          reconcilePayment(p,payment,null,false);
        }
        db.update("INSERT INTO component_slot_events(event_id,payload_hash) VALUES(?,?)",event,digest);return Map.of("received",true,"duplicate",false);
      }
      );
    }
    catch(RazorpayClient.ProviderFailure ex){
      throw unavailable();
    }
  }
}
