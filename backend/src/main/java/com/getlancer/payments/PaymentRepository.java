package com.getlancer.payments;

import com.getlancer.shared.ApiError;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRepository {
  final JdbcTemplate db;
  public PaymentRepository(JdbcTemplate db) { this.db=db; }

  Map<String,Object> attempt(UUID id,boolean lock) {
    var rows=db.queryForList("SELECT * FROM payment_attempts WHERE id=?"+(lock?" FOR UPDATE":""),id);
    if (rows.isEmpty()) throw new ApiError(404,"NOT_FOUND","Payment not found.");
    return rows.get(0);
  }
  Map<String,Object> byOrder(String order) {
    var rows=db.queryForList("SELECT * FROM payment_attempts WHERE order_id=?",order);
    return rows.isEmpty()?null:rows.get(0);
  }
  Map<String,Object> byKey(UUID payer,UUID key) {
    var rows=db.queryForList("SELECT * FROM payment_attempts WHERE payer_user_id=? AND idempotency_key=?",payer,key);
    return rows.isEmpty()?null:rows.get(0);
  }
  Map<String,Object> active(UUID milestone) {
    var rows=db.queryForList("SELECT * FROM payment_attempts WHERE milestone_id=? AND status<>'REJECTED'",milestone);
    return rows.isEmpty()?null:rows.get(0);
  }
  String payee(Map<String,Object> milestone,String mode) {
    if (milestone.get("builderUserId")!=null) {
      var active=db.queryForList("SELECT u.id FROM users u JOIN developer_profiles d ON d.user_id=u.id JOIN user_roles r ON r.user_id=u.id AND r.role='DEVELOPER' WHERE u.id=? AND u.account_status='ACTIVE' AND u.email_verified_at IS NOT NULL AND d.approval_status='APPROVED' FOR SHARE OF u",milestone.get("builderUserId"));
      if (active.isEmpty()) throw new ApiError(409,"PAYEE_UNAVAILABLE","The seller is no longer eligible for collection.");
    } else {
      var active=db.queryForList("SELECT t.id FROM teams t JOIN users u ON u.id=t.owner_id JOIN developer_profiles d ON d.user_id=u.id JOIN user_roles r ON r.user_id=u.id AND r.role='DEVELOPER' JOIN team_members m ON m.team_id=t.id AND m.user_id=t.owner_id WHERE t.id=? AND t.status='ACTIVE' AND u.account_status='ACTIVE' AND u.email_verified_at IS NOT NULL AND d.approval_status='APPROVED' AND m.role='OWNER' AND (m.expires_at IS NULL OR m.expires_at>now()) FOR SHARE OF t,u",milestone.get("teamId"));
      if (active.isEmpty()) throw new ApiError(409,"PAYEE_UNAVAILABLE","The team is no longer eligible for collection.");
    }
    var rows=db.queryForList("SELECT account_id FROM payment_accounts WHERE mode=? AND provider_status='created' AND activation_confirmed AND (builder_user_id=? OR team_id=?)",mode,milestone.get("builderUserId"),milestone.get("teamId"));
    if (rows.isEmpty()) throw new ApiError(409,"PAYEE_NOT_CONFIGURED","The seller needs an operator-verified Razorpay linked account before payment.");
    return Objects.toString(rows.get(0).get("account_id"));
  }
  void reserve(UUID id,UUID milestone,UUID payer,UUID key,long amount,String account,String mode) {
    db.update("INSERT INTO payment_attempts(id,milestone_id,payer_user_id,idempotency_key,amount_minor,currency,account_id,mode,status) VALUES(?,?,?,?,?,'INR',?,?,'CREATING')",id,milestone,payer,key,amount,account,mode);
  }
  void creationFailed(UUID attempt,boolean rejected) {
    db.update("UPDATE payment_attempts SET status=?,attention_reason=?,updated_at=now() WHERE id=? AND status='CREATING'",rejected?"REJECTED":"UNKNOWN",rejected?"Provider rejected order creation; no order was confirmed.":"Order creation is uncertain. Check the provider dashboard and bind the matching receipt.",attempt);
  }
  void bind(UUID attempt,String order,String transfer,String settlement) {
    db.update("UPDATE payment_attempts SET order_id=?,status='ORDER_CREATED',transfer_status=?,settlement_status=?,attention_reason=null,updated_at=now() WHERE id=?",order,transfer,settlement,attempt);
  }
  List<Map<String,Object>> summaries(UUID engagement) {
    return db.queryForList("SELECT p.id,p.milestone_id AS \"milestoneId\",p.status,p.amount_minor AS \"amountMinor\",p.currency,p.mode,p.order_id AS \"orderId\",p.payment_id AS \"paymentId\",p.refunded_minor AS \"refundedMinor\",p.transfer_status AS \"transferStatus\",p.settlement_status AS \"settlementStatus\",p.attention_reason AS \"attentionReason\" FROM payment_attempts p JOIN delivery_milestones m ON m.id=p.milestone_id WHERE m.engagement_id=? ORDER BY p.created_at DESC",engagement);
  }
  static Map<String,Object> summary(Map<String,Object> p) {
    var result=new LinkedHashMap<String,Object>();
    result.put("id",p.get("id")); result.put("milestoneId",p.get("milestone_id")); result.put("status",p.get("status"));
    result.put("amountMinor",p.get("amount_minor")); result.put("currency",p.get("currency")); result.put("mode",p.get("mode")); result.put("orderId",p.get("order_id")); result.put("paymentId",p.get("payment_id"));
    result.put("refundedMinor",p.get("refunded_minor")); result.put("transferStatus",p.get("transfer_status")); result.put("settlementStatus",p.get("settlement_status")); result.put("attentionReason",p.get("attention_reason"));
    return result;
  }
  void captured(UUID id,String payment,long refunded,String transfer,String settlement) {
    var prior=attempt(id,false);
    String status=refunded==0?"CAPTURED":refunded==number(attempt(id,false),"amount_minor")?"REFUNDED":"PARTIALLY_REFUNDED";
    if ("DISPUTED".equals(prior.get("status"))) status="DISPUTED";
    db.update("UPDATE payment_attempts SET payment_id=?,status=?,refunded_minor=?,transfer_status=?,settlement_status=?,attention_reason=CASE WHEN dispute_status IS NOT NULL AND dispute_status<>'won' THEN attention_reason ELSE null END,updated_at=now() WHERE id=?",payment,status,refunded,transfer,settlement,id);
  }
  List<String> disputes(UUID id) { return db.queryForList("SELECT id FROM payment_provider_disputes WHERE attempt_id=? ORDER BY updated_at LIMIT 20",String.class,id); }
  void dispute(UUID id,String dispute,String state,long deducted) {
    db.update("INSERT INTO payment_provider_disputes(id,attempt_id,status,deducted_minor) VALUES(?,?,?,?) ON CONFLICT(id) DO UPDATE SET status=excluded.status,deducted_minor=excluded.deducted_minor,updated_at=now()",dispute,id,state,deducted);
    boolean held=db.queryForObject("SELECT count(*) FROM payment_provider_disputes WHERE attempt_id=? AND (status<>'won' OR deducted_minor>0)",Integer.class,id)>0;
    String outcome=held?"held":"won";
    db.update("UPDATE payment_attempts SET dispute_id=?,dispute_status=?,status=CASE WHEN ?='won' THEN CASE WHEN refunded_minor=0 THEN 'CAPTURED' WHEN refunded_minor=amount_minor THEN 'REFUNDED' ELSE 'PARTIALLY_REFUNDED' END ELSE 'DISPUTED' END,attention_reason=CASE WHEN ?='won' THEN null ELSE 'Provider dispute requires dashboard review; completion remains held.' END,updated_at=now() WHERE id=?",dispute,state,outcome,outcome,id);
  }
  void transferAttention(UUID id,String reason) {
    db.update("UPDATE payment_attempts SET transfer_status='UNKNOWN',settlement_status='UNKNOWN',attention_reason=CASE WHEN status='DISPUTED' THEN attention_reason ELSE ? END,updated_at=now() WHERE id=?",reason,id);
  }
  void ledger(UUID id,String entry,String kind,long amount) {
    db.update("INSERT INTO payment_ledger(id,attempt_id,entry_key,kind,amount_minor,currency) VALUES(?,?,?,?,?,'INR') ON CONFLICT(attempt_id,entry_key) DO NOTHING",UUID.randomUUID(),id,entry,kind,amount);
  }
  void event(String id,String hash,String kind) {
    db.update("INSERT INTO payment_webhook_events(event_id,payload_hash,event_kind) VALUES(?,?,?)",id,hash,kind);
  }
  String existingEvent(String id) {
    var hashes=db.queryForList("SELECT payload_hash FROM payment_webhook_events WHERE event_id=?",String.class,id);
    return hashes.isEmpty()?null:hashes.get(0);
  }
  List<Map<String,Object>> accounts() {
    return db.queryForList("SELECT id,builder_user_id AS \"builderUserId\",team_id AS \"teamId\",account_id AS \"accountId\",mode,provider_status AS \"providerStatus\",verified_at AS \"verifiedAt\" FROM payment_accounts ORDER BY verified_at DESC");
  }
  void mapAccount(UUID builder,UUID team,String account,String mode,UUID admin) {
    if (builder!=null) {
      var allowed=db.queryForList("SELECT u.id FROM users u JOIN developer_profiles d ON d.user_id=u.id JOIN user_roles r ON r.user_id=u.id AND r.role='DEVELOPER' WHERE u.id=? AND u.account_status='ACTIVE' AND u.email_verified_at IS NOT NULL AND d.approval_status='APPROVED' FOR UPDATE OF u",builder);
      if (allowed.isEmpty()) throw new ApiError(404,"NOT_FOUND","Current approved seller not found.");
    } else {
      var allowed=db.queryForList("SELECT t.id FROM teams t JOIN users u ON u.id=t.owner_id JOIN developer_profiles d ON d.user_id=u.id JOIN team_members m ON m.team_id=t.id AND m.user_id=t.owner_id WHERE t.id=? AND t.status='ACTIVE' AND u.account_status='ACTIVE' AND u.email_verified_at IS NOT NULL AND d.approval_status='APPROVED' AND m.role='OWNER' AND (m.expires_at IS NULL OR m.expires_at>now()) FOR UPDATE OF t",team);
      if (allowed.isEmpty()) throw new ApiError(404,"NOT_FOUND","Current approved team owner not found.");
    }
    var existing=db.queryForList("SELECT * FROM payment_accounts WHERE builder_user_id=? OR team_id=? FOR UPDATE",builder,team);
    UUID id=existing.isEmpty()?UUID.randomUUID():(UUID)existing.get(0).get("id");
    String previous=existing.isEmpty()?null:Objects.toString(existing.get(0).get("account_id"));
    if (db.queryForObject("SELECT count(*) FROM payment_accounts WHERE account_id=? AND id<>?",Integer.class,account,id)>0)
      throw new ApiError(409,"ACCOUNT_IN_USE","This linked account belongs to a different seller mapping.");
    if (existing.isEmpty()) db.update("INSERT INTO payment_accounts(id,builder_user_id,team_id,account_id,mode,provider_status,activation_confirmed,verified_by) VALUES(?,?,?,?,?,'created',true,?)",id,builder,team,account,mode,admin);
    else db.update("UPDATE payment_accounts SET account_id=?,mode=?,provider_status='created',activation_confirmed=true,verified_by=?,verified_at=now() WHERE id=?",account,mode,admin,id);
    db.update("INSERT INTO payment_account_audit(id,account_mapping_id,actor_id,previous_account_id,account_id) VALUES(?,?,?,?,?)",UUID.randomUUID(),id,admin,previous,account);
  }
  List<Map<String,Object>> attention() {
    return db.queryForList("SELECT p.id,p.milestone_id AS \"milestoneId\",m.engagement_id AS \"engagementId\",p.status,p.amount_minor AS \"amountMinor\",p.currency,p.mode,p.order_id AS \"orderId\",p.account_id AS \"accountId\",p.attention_reason AS \"attentionReason\",p.transfer_status AS \"transferStatus\",p.settlement_status AS \"settlementStatus\",p.created_at AS \"createdAt\" FROM payment_attempts p JOIN delivery_milestones m ON m.id=p.milestone_id JOIN delivery_engagements e ON e.id=m.engagement_id WHERE p.status IN ('UNKNOWN','CREATING') OR p.attention_reason IS NOT NULL OR p.status='DISPUTED' OR e.status='DISPUTED' ORDER BY p.created_at");
  }
  static long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
}
