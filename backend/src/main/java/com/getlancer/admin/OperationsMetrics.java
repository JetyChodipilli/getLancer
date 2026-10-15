package com.getlancer.admin;

import com.getlancer.labs.LabConfiguration;
import com.getlancer.labs.LabService;
import com.getlancer.payments.RazorpayClient;
import com.getlancer.responses.OperationsResponse;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Bounded aggregate evidence; browser observations never assert financial or runtime success. */
@Service
public class OperationsMetrics {
  private final JdbcTemplate db;
  private final Security security;
  private final LabConfiguration configuration;
  private final LabService labs;
  private final RazorpayClient payments;
  public OperationsMetrics(JdbcTemplate db, Security security, LabConfiguration configuration,
      LabService labs, RazorpayClient payments) {
    this.db=db; this.security=security; this.configuration=configuration; this.labs=labs; this.payments=payments;
  }

  @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
  public OperationsResponse snapshot(HttpServletRequest request) {
    security.admin(request);
    if (!security.loadPrincipal(request).recentMfa())
      throw new ApiError(403,"FORBIDDEN","Recent administrator MFA is required.");
    Instant to=db.queryForObject("SELECT transaction_timestamp()",Timestamp.class).toInstant();
    // Thirty UTC calendar dates. Do not pretend daily aggregates have sub-day precision.
    Instant from=to.truncatedTo(ChronoUnit.DAYS).minus(29,ChronoUnit.DAYS);
    Instant started=db.queryForObject("SELECT started_at FROM analytics_instrumentation WHERE name='v52_operations'",Timestamp.class).toInstant();
    Timestamp start=Timestamp.from(started), first=Timestamp.from(from), end=Timestamp.from(to);
    var funnels=new ArrayList<OperationsResponse.Funnel>();
    var alerts=new ArrayList<OperationsResponse.Alert>();
    for(var row:db.queryForList("""
        WITH stages AS (
          SELECT stage,mode,version,sum(observations)::bigint AS observations FROM analytics_daily_counts
          WHERE day>=?::timestamptz AT TIME ZONE 'UTC' AND day<=?::timestamptz AT TIME ZONE 'UTC'
            AND stage NOT IN ('quota_denial','validation_denial') GROUP BY stage,mode,version
        ) SELECT stage,mode,version,observations,sum(observations) OVER(PARTITION BY mode,version)::bigint AS denominator
        FROM stages ORDER BY mode,version,stage LIMIT 500
        """,first,end)) funnels.add(funnel(row,"Consented event observations in this mode/version; event share, not visitor conversion."));
    var inquiry=db.queryForMap("""
        SELECT count(*) AS denominator,count(*) FILTER(WHERE email_confirmed_at IS NOT NULL AND email_confirmed_at<=?
          AND moderation_status='CLEAR') AS observations FROM inquiries WHERE created_at>=? AND created_at<=?
        """,end,first,end);
    funnels.add(funnel("qualified_inquiry","PROJECT","current",inquiry,"All server-created inquiries in this window; qualified means email confirmed and currently clear of moderation."));
    for(var row:db.queryForList("""
        SELECT upper(p.mode) AS mode,p.sha256 AS version,count(*) AS denominator,
          count(*) FILTER(WHERE EXISTS(SELECT 1 FROM commerce_ledger l WHERE l.purchase_id=p.id AND l.kind='CAPTURE' AND l.created_at<=?)) AS observations
        FROM template_purchases p WHERE p.created_at>=? AND p.created_at<=? GROUP BY p.mode,p.sha256 ORDER BY p.mode,p.sha256 LIMIT 500
        """,end,first,end)) funnels.add(funnel("purchase_captured",(String)row.get("mode"),((String)row.get("version")).strip(),row,"Durable reserved receipts in this mode/source hash; server capture ledger only. Test receipts do not establish live commerce."));
    for(var row:db.queryForList("""
        SELECT p.sha256 AS version,count(*) AS denominator,
          count(*) FILTER(WHERE EXISTS(SELECT 1 FROM commerce_audit a WHERE a.purchase_id=p.id AND a.kind='SOURCE_DOWNLOADED'
            AND a.created_at>=? AND a.created_at<=?)) AS observations
        FROM template_purchases p WHERE p.mode='live' AND p.created_at>=? AND p.created_at<=?
          AND EXISTS(SELECT 1 FROM commerce_ledger l WHERE l.purchase_id=p.id AND l.kind='CAPTURE' AND l.created_at<=?)
        GROUP BY p.sha256 ORDER BY p.sha256 LIMIT 500
        """,first,end,first,end,end)) funnels.add(funnel("source_download","LIVE",((String)row.get("version")).strip(),row,"Distinct captured live receipts from this purchase cohort with an authorized source delivery; repeated downloads count once."));
    var runtime=db.queryForMap("""
        SELECT count(*) AS admitted,count(*) FILTER(WHERE healthy_at IS NOT NULL) AS healthy,
          count(*) FILTER(WHERE healthy_at IS NULL AND cleanup_confirmed_at IS NULL) AS pending
        FROM lab_runs WHERE requested_at>=? AND requested_at<=?
        """,start,end);
    long admitted=n(runtime,"admitted"), healthy=n(runtime,"healthy"), pending=n(runtime,"pending"), completed=admitted-pending;
    long scenarioFailures=db.queryForObject("SELECT count(*) FROM lab_requests WHERE confirmed_failure AND created_at>=? AND created_at<=?",Long.class,start,end);
    var denials=db.queryForMap("SELECT quota_denials,validation_denials FROM analytics_instrumentation WHERE name='v52_operations'");
    long quota=n(denials,"quota_denials"), validation=n(denials,"validation_denials");
    for(var row:db.queryForList("""
        SELECT manifest_sha256 AS version,count(*) AS denominator,count(*) FILTER(WHERE healthy_at IS NOT NULL) AS observations
        FROM lab_runs WHERE requested_at>=? AND requested_at<=? GROUP BY manifest_sha256 ORDER BY manifest_sha256 LIMIT 100
        """,start,end)) funnels.add(funnel("provisioned_run","LIVE",(String)row.get("version"),row,"All admitted server reservations for this manifest since instrumentation; pending, owner cancellation and revocation remain in this denominator."));
    var reserved=labs.reservedResources();
    var resources=new OperationsResponse.Resources(n(reserved,"active"),n(reserved,"memory"),n(reserved,"daily"),n(reserved,"monthly"),LabService.DAILY_BUDGET_MICROS,LabService.MONTHLY_BUDGET_MICROS);
    if(funnels.stream().filter(f->!List.of("qualified_inquiry","purchase_captured","source_download","provisioned_run").contains(f.stage())).count()>=500
        || funnels.stream().filter(f->f.stage().equals("purchase_captured")).count()>=500
        || funnels.stream().filter(f->f.stage().equals("source_download")).count()>=500
        || funnels.stream().filter(f->f.stage().equals("provisioned_run")).count()>=100)alerts.add(new OperationsResponse.Alert("COHORT_LIMIT","WARNING",1,"Cohort tables are capped at five hundred observation/purchase/source cohorts and one hundred manifests. Review complete exported evidence before claiming whole-catalogue coverage."));
    if(quota>0)alerts.add(new OperationsResponse.Alert("QUOTA_DENIALS","INFO",quota,"Capacity and quota denials are excluded from admitted-runtime denominators."));
    long stale=db.queryForObject("SELECT CASE WHEN EXISTS(SELECT 1 FROM lab_runs WHERE cleanup_confirmed_at IS NULL) AND (worker_observed_at IS NULL OR worker_observed_at<now()-interval '30 seconds') THEN 1 ELSE 0 END FROM lab_runtime_settings WHERE id=1",Long.class);
    if(stale>0)alerts.add(new OperationsResponse.Alert("STALE_WORKER","CRITICAL",stale,"The watchdog has not completed a pass within thirty seconds while reservations remain active."));
    long orphans=db.queryForObject("SELECT count(*) FROM lab_runs WHERE cleanup_confirmed_at IS NULL AND (attention OR expires_at<now()-interval '30 seconds')",Long.class);
    if(orphans>0)alerts.add(new OperationsResponse.Alert("ORPHAN_RESERVATIONS","CRITICAL",orphans,"Provider cleanup is unconfirmed or overdue. Capacity remains reserved until confirmation."));
    if(resources.dailyReservedMicros()>=resources.dailyBudgetMicros()*9/10 || resources.monthlyReservedMicros()>=resources.monthlyBudgetMicros()*9/10)
      alerts.add(new OperationsResponse.Alert("BUDGET_PRESSURE","WARNING",1,"At least ninety percent of a monetary reservation ceiling is in use. Admissions cannot reserve beyond the cap."));
    if(Boolean.TRUE.equals(db.queryForObject("SELECT paused FROM lab_runtime_settings WHERE id=1",Boolean.class)))
      alerts.add(new OperationsResponse.Alert("ADMISSIONS_PAUSED","WARNING",1,"Operator admissions are paused; source availability and financial agreements retain their own authority."));
    var runtimeConfig=configuration.projection(); var runtimeLane=labs.runtimeAvailability(); var commerce=payments.configuration();
    boolean commerceReady=Boolean.TRUE.equals(commerce.get("enabled"));
    var readiness=new OperationsResponse.Readiness(new OperationsResponse.Lane(runtimeLane.enabled(),runtimeConfig.enabled(),runtimeLane.reason()),
        new OperationsResponse.CommerceLane(commerceReady,commerceReady,(String)commerce.get("mode"),commerceReady?"Provider collection configuration is ready; independent LIVE merchant/domain evidence is still required.":(String)commerce.get("reason")),false);
    return new OperationsResponse(new OperationsResponse.Window(from,to,30,"Aggregate counts only. Optional web events require consent, raw event/session data expires within twenty-four hours; daily unlinked counts expire after ninety days and the observation window covers thirty UTC calendar dates. Financial and runtime facts retain their domain authority.",started),List.copyOf(funnels),
        new OperationsResponse.Runtime(admitted,healthy,pending,completed,completed-healthy,percent(healthy,completed),percent(completed,admitted),scenarioFailures,quota,validation,started.toString(),started,db.queryForObject("SELECT observed_epoch FROM lab_runtime_settings WHERE id=1",java.util.UUID.class)),List.copyOf(alerts),readiness,resources);
  }
  private static OperationsResponse.Funnel funnel(Map<String,Object> row,String label) {
    return funnel((String)row.get("stage"),(String)row.get("mode"),(String)row.get("version"),row,label);
  }
  private static OperationsResponse.Funnel funnel(String stage,String mode,String version,Map<String,Object> row,String label) {
    long count=n(row,"observations"),denominator=n(row,"denominator");
    return new OperationsResponse.Funnel(stage,mode,version,count,denominator,percent(count,denominator),label);
  }
  private static long n(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
  private static BigDecimal percent(long numerator,long denominator) {
    return denominator==0?null:BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(denominator),2,RoundingMode.HALF_UP);
  }
}
