package com.getlancer.jobs;

import com.getlancer.shared.Support;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class Maintenance {
  final int analyticsRetentionDays;

  final JdbcTemplate db;

  final boolean enabled;

  public Maintenance(JdbcTemplate db) { this(db,1,true); }

  @Autowired
  public Maintenance(JdbcTemplate db, @Value("${app.analytics-retention-days:1}") int analyticsRetentionDays,
      @Value("${app.jobs-enabled:true}") boolean enabled) {
    this.db = db; this.analyticsRetentionDays=Math.max(0,Math.min(analyticsRetentionDays,1)); this.enabled=enabled;
  }

  @Scheduled(fixedDelay = 60000)
  @Transactional
  public void expire() {
    if (!enabled) return;
    // Renewed rows must not fill the batch ahead of abandoned inquiries. This
    // preliminary token read takes no token locks; lock inquiries first and
    // recheck in a fresh statement below, matching the confirmation lock order.
    var rows =
        db.queryForList(
            "SELECT i.id FROM inquiries i WHERE i.current_status='CREATED_UNVERIFIED' AND"
                + " i.updated_at<clock_timestamp()-interval '24 hours' AND NOT EXISTS(SELECT 1"
                + " FROM account_tokens t WHERE t.inquiry_id=i.id AND"
                + " t.kind='CLIENT_INQUIRY_CONFIRMATION' AND t.used_at IS NULL AND"
                + " t.expires_at>clock_timestamp()) ORDER BY i.updated_at,i.id LIMIT 100"
                + " FOR UPDATE OF i SKIP LOCKED");
    for (var row : rows) {
      Object id = row.get("id");
      if (db.queryForObject(
              "SELECT count(*) FROM account_tokens WHERE inquiry_id=? AND"
                  + " kind='CLIENT_INQUIRY_CONFIRMATION' AND used_at IS NULL AND"
                  + " expires_at>clock_timestamp()",
              Integer.class,
              id)
          > 0) continue;
      db.update(
          "UPDATE inquiries SET current_status='EXPIRED',updated_at=clock_timestamp() WHERE id=?", id);
      db.update(
          "INSERT INTO inquiry_events(id,inquiry_id,event_type,actor_type)"
              + " VALUES(?,?,'EXPIRED','SYSTEM')",
          Support.id(),
          id);
    }
    db.update(
        "INSERT INTO storage_deletions(storage_key) SELECT storage_key FROM media_uploads WHERE"
            + " expires_at<now()-interval '5 minutes' ON CONFLICT DO NOTHING");
    db.update("DELETE FROM media_uploads WHERE expires_at<now()-interval '5 minutes'");
    // This retention applies only to optional usage events, never engagement evidence.
    db.update(
        "DELETE FROM analytics_events WHERE source='web' AND"
            + " created_at<now()-make_interval(days=>?)",
        analyticsRetentionDays);
    db.update("DELETE FROM analytics_daily_counts WHERE day<(now() AT TIME ZONE 'UTC')::date-90");
    db.update("DELETE FROM product_access_grants WHERE expires_at<now()");
    db.update("DELETE FROM sessions WHERE expires_at<now()");
    db.update("DELETE FROM login_challenges WHERE expires_at<now()");
    db.update("DELETE FROM oauth_pending WHERE expires_at<now()");
    db.update("DELETE FROM rate_buckets WHERE expires_at<now()-interval '1 day'");
    db.update("DELETE FROM account_tokens WHERE expires_at<now()-interval '7 days'");
    // Remove expired token bodies from terminal delivery failures, preserving delivery evidence.
    db.update(
        "UPDATE email_outbox SET body='[expired]' WHERE sent_at IS NULL AND attempts>=8 AND"
            + " created_at<now()-interval '7 days' AND body<>'[expired]'");
  }
}
