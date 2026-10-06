package com.getlancer.accounts;

import static com.getlancer.shared.Support.*;

import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class AccountService {
  final JdbcTemplate db;
  final Security security;
  private com.getlancer.hosting.HostingRepository hosting;

  @org.springframework.beans.factory.annotation.Autowired
  public void setHostingRepository(com.getlancer.hosting.HostingRepository hosting) { this.hosting = hosting; }

  public AccountService(JdbcTemplate db, Security security) {
    this.db = db;
    this.security = security;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> export(HttpServletRequest request) {
    var user = security.principal(request);
    UUID id = (UUID) user.get("id");
    if (user.get("email_verified_at") == null)
      throw new ApiError(
          403, "EMAIL_NOT_VERIFIED", "Confirm your email before exporting account data.");
    Map<String, Object> result = new LinkedHashMap<>();
    result.put(
        "account",
        db.queryForMap("SELECT id,email,email_verified_at,created_at FROM users WHERE id=?", id));
    result.put("profile", db.queryForMap("SELECT * FROM developer_profiles WHERE user_id=?", id));
    result.put(
        "products",
        db.queryForList(
            "SELECT"
                + " id,slug,title,summary,description,contribution_text,approval_status,lifecycle_status,created_at"
                + " FROM products WHERE owner_user_id=?",
            id));
    result.put(
        "requests",
        db.queryForList(
            "SELECT"
                + " id,client_email,client_name,description,budget_band,timeline_band,current_status,created_at"
                + " FROM inquiries WHERE client_email=? OR (developer_user_id=? AND"
                + " email_confirmed_at IS NOT NULL AND moderation_status='CLEAR')",
            user.get("email"),
            id));
    result.put(
        "savedProducts",
        db.queryForList("SELECT product_id,created_at FROM saved_products WHERE user_id=?", id));
    result.put(
        "legalAcceptances",
        db.queryForList(
            "SELECT document_version,accepted_at FROM legal_acceptances WHERE user_id=?", id));
    result.put(
        "repositoryVerifications",
        db.queryForList("SELECT v.product_id,v.repository_url,v.status,v.requested_at,v.expires_at,v.reviewed_at,v.reason FROM repository_verifications v JOIN products p ON p.id=v.product_id WHERE p.owner_user_id=?", id));
    result.put(
        "earnedCapacityAwards",
        db.queryForList("SELECT inquiry_id,reason,created_at FROM earned_capacity_awards WHERE user_id=?", id));
    result.put(
        "teamStaffing",
        db.queryForList("SELECT id,team_id,project_label,skills,ends_at,status,created_at FROM team_staffing WHERE user_id=?", id));
    result.put(
        "teamMemberships",
        db.queryForList(
            "SELECT m.team_id,m.role,m.membership_type,m.expires_at,m.project_label,t.name FROM"
                + " team_members m JOIN teams t ON t.id=m.team_id WHERE m.user_id=?",
            id));
    result.put(
        "teamInvitations",
        db.queryForList(
            "SELECT id,team_id,role,membership_type,status,expires_at,project_label FROM"
                + " team_invitations WHERE user_id=?",
            id));
    result.put(
        "teamApplications",
        db.queryForList(
            "SELECT id,team_id,role_id,message,status FROM team_applications WHERE user_id=?", id));
    result.put(
        "teamRequests",
        db.queryForList(
            "SELECT id,team_id,title,description,budget,timeline,status FROM team_leads WHERE"
                + " client_id=?",
            id));
    result.put(
        "teamProductConsent",
        db.queryForList(
            "SELECT team_id,product_id,created_at FROM team_projects WHERE consented_by=?", id));
    result.put(
        "businessMemberships",
        db.queryForList(
            "SELECT m.business_id,m.role,b.name FROM business_members m JOIN businesses b ON"
                + " b.id=m.business_id WHERE m.user_id=?",
            id));
    result.put(
        "businessInvitations",
        db.queryForList(
            "SELECT id,business_id,status,expires_at FROM business_invitations WHERE user_id=?",
            id));
    result.put(
        "businessRequests",
        db.queryForList(
            "SELECT"
                + " r.id,r.business_id,r.title,r.description,r.category,r.technology,r.budget,r.timeline,r.status"
                + " FROM business_requests r WHERE r.created_by=? AND EXISTS(SELECT 1 FROM"
                + " business_members m WHERE m.business_id=r.business_id AND m.user_id=?)",
            id,
            id));
    result.put(
        "savedTalent",
        db.queryForList(
            "SELECT e.id,e.list_id,e.kind,e.builder_id,e.team_id FROM talent_entries e JOIN"
                + " talent_lists l ON l.id=e.list_id WHERE e.created_by=? AND EXISTS(SELECT 1 FROM"
                + " business_members m WHERE m.business_id=l.business_id AND m.user_id=?)",
            id,
            id));
    result.put(
        "requestShortlist",
        db.queryForList(
            "SELECT s.id,s.request_id,s.kind,s.builder_id,s.team_id,s.reason,s.source FROM"
                + " request_shortlist s JOIN business_requests r ON r.id=s.request_id WHERE"
                + " s.created_by=? AND EXISTS(SELECT 1 FROM business_members m WHERE"
                + " m.business_id=r.business_id AND m.user_id=?)",
            id,
            id));
    result.put("commercialConsents", db.queryForList(
        "SELECT id,engagement_id,revision,status,scope,terms,amount_minor,currency,milestones,sent_at,accepted_at FROM delivery_proposals WHERE seller_consented_by=? OR buyer_consented_by=?", id, id));
    result.put("deliveryDisputes", db.queryForList(
        "SELECT id,engagement_id,reason,status,resolution,resolution_reason,created_at,resolved_at FROM delivery_disputes WHERE opened_by=?", id));
    result.put("milestonePayments", db.queryForList(
        "SELECT id,milestone_id,amount_minor,currency,mode,status,order_id,payment_id,refunded_minor,transfer_status,settlement_status,created_at FROM payment_attempts WHERE payer_user_id=?", id));
    result.put("sourceTemplates", db.queryForList(
        "SELECT id,product_id,slug,title,summary,description,price_minor,currency,license_terms,status,created_at FROM source_templates WHERE seller_id=?", id));
    result.put("sourceReleases", db.queryForList(
        "SELECT v.id,v.template_id,v.version,v.release_notes,v.sha256,v.size_bytes,v.entry_count,v.license_terms,v.manifest_files,v.rights_consented_at,v.status,v.review_reason,v.created_at FROM source_versions v JOIN source_templates t ON t.id=v.template_id WHERE t.seller_id=?", id));
    result.put("sourcePurchases", db.queryForList(
        "SELECT id,template_id,version_id,title,version,license_terms,sha256,amount_minor,currency,mode,status,refunded_minor,entitlement_revoked,license_consented_at,created_at FROM template_purchases WHERE buyer_id=? OR seller_id=?", id,id));
    result.put("sourceDisputes", db.queryForList(
        "SELECT d.id,d.purchase_id,d.reason,d.status,d.resolution_reason,d.created_at,d.resolved_at FROM commerce_disputes d JOIN template_purchases p ON p.id=d.purchase_id WHERE p.buyer_id=? OR p.seller_id=?", id,id));
    result.put("maintenanceConsents",db.queryForList("SELECT id,engagement_id,revision,status,title,scope,terms,amount_minor,currency,requests_per_cycle,response_hours,total_cycles,digest,seller_consented_at,buyer_consented_at,created_at FROM maintenance_offers WHERE seller_id=? OR buyer_id=?",id,id));
    result.put("maintenanceBilling",db.queryForList("SELECT s.id,s.offer_id,s.amount_minor,s.currency,s.mode,s.status,s.creation_step,s.plan_state,s.subscription_state,s.provider_plan_id,s.provider_subscription_id,s.cancel_requested,s.cancel_confirmed,s.cancel_state,s.local_hold,s.reconciled_at,s.billing_consented_at,s.created_at FROM maintenance_subscriptions s JOIN maintenance_offers o ON o.id=s.offer_id WHERE s.payer_id=? OR o.seller_id=? OR o.buyer_id=?",id,id,id));
    result.put("maintenancePeriods",db.queryForList("SELECT p.id,p.subscription_id,p.provider_invoice_id,p.payment_id,p.order_id,p.amount_minor,p.currency,p.period_start,p.period_end,p.status,p.refunded_minor,p.pending_refund,p.transfer_state,p.transfer_id,p.reversed_minor,p.transfer_status,p.dispute_status,p.reconciled_at FROM maintenance_periods p JOIN maintenance_subscriptions s ON s.id=p.subscription_id JOIN maintenance_offers o ON o.id=s.offer_id WHERE s.payer_id=? OR o.seller_id=? OR o.buyer_id=?",id,id,id));
    result.put("maintenanceRefunds",db.queryForList("SELECT r.id,r.period_id,r.amount_minor,r.status,r.created_at,r.updated_at FROM maintenance_refunds r JOIN maintenance_periods p ON p.id=r.period_id JOIN maintenance_subscriptions s ON s.id=p.subscription_id JOIN maintenance_offers o ON o.id=s.offer_id WHERE s.payer_id=? OR o.seller_id=? OR o.buyer_id=?",id,id,id));
    result.put("maintenanceDisputes",db.queryForList("SELECT d.id,d.period_id,d.status,d.deducted_minor,d.updated_at FROM maintenance_provider_disputes d JOIN maintenance_periods p ON p.id=d.period_id JOIN maintenance_subscriptions s ON s.id=p.subscription_id JOIN maintenance_offers o ON o.id=s.offer_id WHERE s.payer_id=? OR o.seller_id=? OR o.buyer_id=?",id,id,id));
    // Export signed identities, not raw provider payloads or unverified foreign references.
    result.put("maintenanceEvents",db.queryForList("SELECT e.event_id,e.payload_hash,e.event_kind,e.received_at,e.processed_at,e.attempts FROM maintenance_webhook_events e WHERE EXISTS (SELECT 1 FROM maintenance_subscriptions s JOIN maintenance_offers o ON o.id=s.offer_id WHERE (s.payer_id=? OR o.seller_id=? OR o.buyer_id=?) AND (s.provider_subscription_id=e.subscription_id OR EXISTS (SELECT 1 FROM maintenance_periods p WHERE p.subscription_id=s.id AND (p.payment_id=e.payment_id OR EXISTS (SELECT 1 FROM maintenance_provider_disputes d WHERE d.period_id=p.id AND d.id=e.dispute_id) OR EXISTS (SELECT 1 FROM maintenance_refunds r WHERE r.period_id=p.id AND r.id=e.refund_id)))))",id,id,id));
    result.put("maintenanceLedger",db.queryForList("SELECT l.id,l.period_id,l.kind,l.amount_minor,l.currency,l.created_at FROM maintenance_ledger l JOIN maintenance_periods p ON p.id=l.period_id JOIN maintenance_subscriptions s ON s.id=p.subscription_id JOIN maintenance_offers o ON o.id=s.offer_id WHERE s.payer_id=? OR o.seller_id=? OR o.buyer_id=?",id,id,id));
    result.put("maintenanceRequests",db.queryForList("SELECT id,subscription_id,period_id,title,description,status,delivery_note,delivery_url,response_due_at,created_at,resolved_at FROM maintenance_requests WHERE actor_id=?",id));
    result.put("maintenanceActions",db.queryForList("SELECT id,offer_id,kind,detail,created_at FROM maintenance_audit WHERE actor_id=?",id));
    result.put("components",db.queryForList("SELECT * FROM component_entries WHERE owner_id=?",id));
    result.put("collegeContext",db.queryForList("SELECT e.* FROM college_project_metadata e JOIN products p ON p.id=e.product_id WHERE p.owner_user_id=?",id));
    result.put("componentSlotPurchases",db.queryForList("SELECT id,pool,amount_minor,currency,mode,status,order_id,payment_id,refunded_minor,dispute_status,created_at FROM component_slot_purchases WHERE owner_id=?",id));
    result.put("componentSlotLedger",db.queryForList("SELECT l.* FROM component_slot_ledger l JOIN component_slot_purchases p ON p.id=l.purchase_id WHERE p.owner_id=?",id));
    if (hosting != null) result.put("hostedDemos", hosting.export(id));
    return result;
  }

  public Map<String, Object> read(UUID id, HttpServletRequest request) {
    if (db.update(
            "UPDATE notifications SET read_at=COALESCE(read_at,now()) WHERE id=? AND user_id=?",
            id,
            security.user(request))
        == 0) throw new ApiError(404, "NOT_FOUND", "Notification not found.");
    return Map.of("ok", true);
  }
}
