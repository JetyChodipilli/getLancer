package com.getlancer.admin;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Internal aggregates only. Authentication and MFA are enforced by AdminService.metrics. */
@Component
public class MarketplaceMetrics {
  private final JdbcTemplate db;

  public MarketplaceMetrics(JdbcTemplate db) {
    this.db = db;
  }

  public Map<String, Object> snapshot() {
    Instant now = Instant.now();
    Timestamp to = Timestamp.from(now), from = Timestamp.from(now.minus(84, ChronoUnit.DAYS));
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("window", Map.of(
        "days", 84, "from", from.toInstant().toString(), "to", to.toInstant().toString(),
        "responseMaturityHours", 48,
        "qualification", "Email-confirmed inquiries currently clear of moderation restrictions; grouped by confirmation time.",
        "supply", "Current active, email-verified, approved builders; active public showcases only for proof coverage.",
        "privacy", "Aggregate counts only. Search observations are optional opt-in web events; no email, query text or private brief is returned."));
    result.put("supply", db.queryForMap("""
        WITH builders AS (
          SELECT d.user_id FROM developer_profiles d JOIN users u ON u.id=d.user_id
          WHERE d.approval_status='APPROVED' AND u.account_status='ACTIVE'
            AND u.email_verified_at IS NOT NULL
        ), public_proof AS (
          SELECT p.* FROM products p JOIN builders b ON b.user_id=p.owner_user_id
          WHERE p.approval_status='APPROVED' AND p.lifecycle_status='ACTIVE' AND p.visibility='PUBLIC'
        )
        SELECT (SELECT count(*) FROM builders) AS "approvedBuilders",
          count(DISTINCT owner_user_id) AS "activeBuilders",
          count(*) AS "activePublicShowcases",
          round(count(*)::numeric / nullif((SELECT count(*) FROM builders),0),2) AS "averageShowcasesPerApprovedBuilder",
          count(*) FILTER(WHERE coalesce(live_url,'')<>'') AS "liveDemoShowcases",
          round(100.0 * count(*) FILTER(WHERE coalesce(live_url,'')<>'') / nullif(count(*),0),2) AS "liveDemoPercent",
          count(*) FILTER(WHERE coalesce(video_url,'')<>'') AS "videoShowcases",
          round(100.0 * count(*) FILTER(WHERE coalesce(video_url,'')<>'') / nullif(count(*),0),2) AS "videoPercent",
          count(*) FILTER(WHERE EXISTS(SELECT 1 FROM product_media m WHERE m.product_id=public_proof.id AND m.verified)) AS "imageShowcases"
        FROM public_proof
        """));
    result.put("outcomes", db.queryForMap("""
        WITH qualified AS (
          SELECT i.id,i.developer_user_id,lower(i.client_email) AS client_key,i.email_confirmed_at,
            min(e.created_at) FILTER(WHERE e.event_type IN ('RESPONDED','DEVELOPER_RESPONDED')) AS responded_at,
            min(e.created_at) FILTER(WHERE e.event_type IN ('HIRED','HIRE_CONFIRMED')) AS hired_at,
            min(e.created_at) FILTER(WHERE e.event_type IN ('COMPLETED','COMPLETION_CONFIRMED')) AS completed_at
          FROM inquiries i LEFT JOIN inquiry_events e ON e.inquiry_id=i.id
            AND e.created_at>=i.email_confirmed_at AND e.created_at<=?
          WHERE i.email_confirmed_at>=? AND i.email_confirmed_at<=? AND i.moderation_status='CLEAR'
          GROUP BY i.id,i.developer_user_id,lower(i.client_email),i.email_confirmed_at
        ), recipient_counts AS (
          SELECT developer_user_id,count(*) AS leads FROM qualified GROUP BY developer_user_id
        ), top_builders AS (
          SELECT leads FROM recipient_counts ORDER BY leads DESC,developer_user_id
          LIMIT (SELECT ceil(count(*) * 0.1)::int FROM recipient_counts)
        ), clients AS (
          SELECT client_key,count(*) AS leads,count(*) FILTER(WHERE hired_at IS NOT NULL) AS hires
          FROM qualified GROUP BY client_key
        )
        SELECT count(*) AS "qualifiedInquiries",
          count(*) FILTER(WHERE responded_at IS NOT NULL) AS "respondedInquiries",
          count(*) FILTER(WHERE email_confirmed_at<=? - interval '48 hours') AS "responseEligibleInquiries",
          count(*) FILTER(WHERE email_confirmed_at<=? - interval '48 hours' AND responded_at IS NOT NULL) AS "respondedEligibleInquiries",
          round(100.0 * count(*) FILTER(WHERE email_confirmed_at<=? - interval '48 hours' AND responded_at IS NOT NULL)
            / nullif(count(*) FILTER(WHERE email_confirmed_at<=? - interval '48 hours'),0),2) AS "responseRatePercent",
          percentile_cont(0.5) WITHIN GROUP(ORDER BY extract(epoch FROM (responded_at-email_confirmed_at))/3600.0)
            FILTER(WHERE email_confirmed_at<=? - interval '48 hours' AND responded_at IS NOT NULL) AS "medianResponseHours",
          count(*) FILTER(WHERE hired_at IS NOT NULL) AS "confirmedHires",
          count(*) FILTER(WHERE completed_at IS NOT NULL) AS "confirmedCompletions",
          round(100.0 * count(*) FILTER(WHERE hired_at IS NOT NULL) / nullif(count(*),0),2) AS "inquiryToHirePercent",
          round(100.0 * count(*) FILTER(WHERE completed_at IS NOT NULL AND hired_at IS NOT NULL)
            / nullif(count(*) FILTER(WHERE hired_at IS NOT NULL),0),2) AS "hireToCompletionPercent",
          (SELECT count(*) FROM clients) AS "uniqueClients",
          (SELECT count(*) FROM clients WHERE leads>1) AS "repeatClients",
          (SELECT count(*) FROM clients WHERE hires>1) AS "repeatHiredClients",
          (SELECT count(*) FROM recipient_counts) AS "leadRecipients",
          (SELECT count(*) FROM top_builders) AS "topBuilderCount",
          coalesce((SELECT sum(leads) FROM top_builders),0) AS "topBuilderLeadCount",
          round(100.0 * (SELECT sum(leads) FROM top_builders) / nullif(count(*),0),2) AS "top10LeadSharePercent",
          (SELECT count(*) FROM reviews r JOIN qualified q ON q.id=r.inquiry_id
            WHERE r.moderation_status='PUBLISHED' AND q.completed_at IS NOT NULL AND r.created_at>=? AND r.created_at<=?) AS "verifiedReviews"
        FROM qualified
        """, to, from, to, to, to, to, to, to, from, to));
    Map<String, Object> slots = db.queryForMap("""
        WITH usage AS (
          SELECT d.user_id,s.active_slot_limit,count(p.id) AS used
          FROM developer_profiles d JOIN users u ON u.id=d.user_id
          JOIN showcase_entitlements s ON s.user_id=d.user_id
          LEFT JOIN products p ON p.owner_user_id=d.user_id AND p.lifecycle_status='ACTIVE'
          WHERE d.approval_status='APPROVED' AND u.account_status='ACTIVE' AND u.email_verified_at IS NOT NULL
          GROUP BY d.user_id,s.active_slot_limit
        )
        SELECT count(*) AS "eligibleBuilders",coalesce(sum(active_slot_limit),0) AS "totalCapacity",
          coalesce(sum(used),0) AS "activeUsage",coalesce(sum(greatest(active_slot_limit-used,0)),0) AS "availableCapacity",
          count(*) FILTER(WHERE used>active_slot_limit) AS "overCapacityBuilders",
          round(100.0 * sum(used) / nullif(sum(active_slot_limit),0),2) AS "utilisationPercent"
        FROM usage
        """);
    slots.putAll(db.queryForMap("""
        SELECT count(*) FILTER(WHERE event_name='product_activated') AS "activationEvents",
          count(*) FILTER(WHERE event_name='product_archived') AS "archiveEvents",
          count(DISTINCT entity_id) FILTER(WHERE event_name='product_activated') AS "activatedProducts"
        FROM analytics_events WHERE source='server' AND event_name IN ('product_activated','product_archived')
          AND created_at>=? AND created_at<=?
        """, from, to));
    slots.put("repeatActivationProducts", db.queryForObject("""
        SELECT count(*) FROM (
          SELECT entity_id FROM analytics_events WHERE source='server' AND event_name='product_activated'
            AND created_at>=? AND created_at<=? GROUP BY entity_id HAVING count(*)>1
        ) repeated
        """, Long.class, from, to));
    slots.put("timeToThreeActiveShowcasesDays", null);
    slots.put("timeToThreeReason", "No historical concurrent slot snapshot is recorded; activation totals cannot prove first reaching three simultaneous active showcases.");
    slots.put("blockedActivationBuilders", null);
    slots.put("blockedActivationReason", "Slot-limit errors are not stored as analytics facts; an empty count would incorrectly mean none occurred.");
    result.put("slots", slots);
    result.put("portfolioCohorts", db.queryForList("""
        WITH builders AS (
          SELECT d.user_id,count(p.id) AS active FROM developer_profiles d JOIN users u ON u.id=d.user_id
          LEFT JOIN products p ON p.owner_user_id=d.user_id AND p.lifecycle_status='ACTIVE'
          WHERE d.approval_status='APPROVED' AND u.account_status='ACTIVE' AND u.email_verified_at IS NOT NULL
          GROUP BY d.user_id
        ), leads AS (
          SELECT i.developer_user_id,count(*) AS qualified,
            count(*) FILTER(WHERE EXISTS(SELECT 1 FROM inquiry_events e WHERE e.inquiry_id=i.id
              AND e.event_type IN ('HIRED','HIRE_CONFIRMED') AND e.created_at>=i.email_confirmed_at AND e.created_at<=?)) AS hires
          FROM inquiries i WHERE i.moderation_status='CLEAR' AND i.email_confirmed_at>=? AND i.email_confirmed_at<=?
          GROUP BY i.developer_user_id
        )
        SELECT least(active,4) AS "activeShowcaseCohort",count(*) AS builders,
          coalesce(sum(l.qualified),0) AS "qualifiedInquiries",coalesce(sum(l.hires),0) AS "confirmedHires",
          round(100.0 * sum(l.hires) / nullif(sum(l.qualified),0),2) AS "inquiryToHirePercent"
        FROM builders b LEFT JOIN leads l ON l.developer_user_id=b.user_id
        GROUP BY least(active,4) ORDER BY least(active,4) LIMIT 5
        """, to, from, to));
    Map<String, Object> search = db.queryForMap("""
        WITH observations AS (
          SELECT CASE WHEN jsonb_typeof(context->'resultCount')='number'
            AND context->>'resultCount' ~ '^[0-9]{1,6}$'
            THEN (context->>'resultCount')::integer ELSE NULL END AS results
          FROM analytics_events WHERE source='web' AND event_name='search_performed'
            AND created_at>=? AND created_at<=?
        )
        SELECT count(*) AS "receivedEvents",count(results) FILTER(WHERE results<=100000) AS "sampleSearches",
          count(*) FILTER(WHERE results BETWEEN 3 AND 100000) AS "searchesWithThreeResults",
          count(*) FILTER(WHERE results=0) AS "noResultSearches",
          round(100.0 * count(*) FILTER(WHERE results BETWEEN 3 AND 100000)
            / nullif(count(results) FILTER(WHERE results<=100000),0),2) AS "threeResultPercent",
          round(100.0 * count(*) FILTER(WHERE results=0)
            / nullif(count(results) FILTER(WHERE results<=100000),0),2) AS "noResultPercent"
        FROM observations
        """, from, to);
    search.put("reason", "Optional consented web observations with a valid bounded result count, not all visitors or unique clients. Empty cohorts have no rate.");
    search.put("fallbackCoveragePercent", null);
    search.put("fallbackCoverageReason", "Unavailable-builder page impressions and candidate counts are not paired in stored events.");
    result.put("search", search);
    Map<String, Object> safety = db.queryForMap("""
        SELECT count(*) AS reports,count(*) FILTER(WHERE status='RESOLVED') AS "resolvedReports",
          count(*) FILTER(WHERE status<>'RESOLVED') AS "openReports",
          round(100.0 * count(*) FILTER(WHERE status='RESOLVED') / nullif(count(*),0),2) AS "resolutionRatePercent",
          count(*) FILTER(WHERE reason IN ('MALICIOUS_LINK','PHISHING')) AS "maliciousLinkReports",
          count(*) FILTER(WHERE reason IN ('COPYRIGHT_IP','STOLEN_WORK')) AS "ipComplaints",
          count(*) FILTER(WHERE reason IN ('CLIENT_SPAM','SPAM')) AS "spamReports"
        FROM reports WHERE created_at>=? AND created_at<=?
        """, from, to);
    safety.putAll(db.queryForMap("""
        SELECT count(*) AS appeals,count(*) FILTER(WHERE status IN ('UPHELD','OVERTURNED')) AS "decidedAppeals",
          count(*) FILTER(WHERE status='OVERTURNED') AS "overturnedAppeals",
          round(100.0 * count(*) FILTER(WHERE status='OVERTURNED')
            / nullif(count(*) FILTER(WHERE status IN ('UPHELD','OVERTURNED')),0),2) AS "reversalRatePercent"
        FROM moderation_appeals WHERE created_at>=? AND created_at<=?
        """, from, to));
    safety.put("bySeverity", db.queryForList("""
        SELECT severity,count(*) AS reports FROM reports WHERE created_at>=? AND created_at<=?
        GROUP BY severity ORDER BY CASE severity WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END LIMIT 4
        """, from, to));
    safety.put("byReason", db.queryForList("""
        SELECT reason,count(*) AS reports FROM reports WHERE created_at>=? AND created_at<=?
        GROUP BY reason ORDER BY count(*) DESC,reason LIMIT 32
        """, from, to));
    safety.put("enforcementActions", db.queryForObject("""
        SELECT count(*) FROM moderation_actions WHERE created_at>=? AND created_at<=?
          AND lower(action) IN ('suspend','hide','quarantine','block')
        """, Long.class, from, to));
    safety.put("reportActionRatePercent", null);
    safety.put("reportActionRateReason", "Reports store resolution text, not a structured report-to-enforcement link. Resolution rate and actual enforcement action count are shown separately.");
    result.put("safety", safety);
    return result;
  }
}
