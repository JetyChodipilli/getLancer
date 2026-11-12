package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@org.springframework.test.context.ContextConfiguration(initializers = TestDatabaseGuard.class)
@SpringBootTest(properties = {
    "app.environment=local", "app.jobs-enabled=false", "app.admin-password=", "app.admin-totp=",
    "spring.config.import=", "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}",
    "spring.datasource.username=${TEST_DB_USERNAME:postgres}", "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.hikari.schema=getlancer_test", "spring.flyway.default-schema=getlancer_test",
    "spring.flyway.schemas=getlancer_test", "app.origin=http://localhost:3000", "app.secure-cookie=false",
    "app.storage.access-key=", "app.storage.secret-key="
})
@AutoConfigureMockMvc
class AdminAuthorityLockIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  UUID builder, operator, product, inquiry, report;
  UUID failedMailJob, concurrentCategorySlug;

  UUID user(String token) {
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",
        id, token + "@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT'),(?,'DEVELOPER')", id, id);
    db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,?,'APPROVED')",
        id, token, token);
    db.update("INSERT INTO showcase_entitlements(user_id) VALUES(?)", id);
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",
        Support.hash(token), id);
    return id;
  }

  @BeforeEach
  void prepare() {
    db.execute("TRUNCATE users CASCADE");
    db.execute("TRUNCATE rate_buckets");
    builder = user("authority-builder");
    operator = user("authority-admin");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')", operator);
    db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?", operator);
    db.update("INSERT INTO categories(slug,name,active) VALUES('authority-audit-category','Authority category',true)"
        + " ON CONFLICT(slug) DO UPDATE SET name=excluded.name,active=true");
    product = UUID.randomUUID();
    inquiry = UUID.randomUUID();
    report = UUID.randomUUID();
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,approval_status,lifecycle_status,rights_confirmed)"
        + " VALUES(?,?,?,'Authority project','Working public evidence','Controlled authority regression project.','SAAS','Authority category','Java','Built this test project','APPROVED','ACTIVE',true)",
        product, builder, product.toString());
    db.update("INSERT INTO inquiries(id,reference_product_id,developer_user_id,client_email,client_name,request_type,description,budget_band,timeline_band,current_status,email_confirmed_at,idempotency_key,request_hash)"
        + " VALUES(?,?,?,'client@example.test','Client','SIMILAR_BUILD','Controlled private engagement requirements','USD_3K_10K','ONE_TO_THREE_MONTHS','COMPLETED',now(),?,'test')",
        inquiry, product, builder, UUID.randomUUID());
    db.update("INSERT INTO reports(id,reporter_id,target_type,target_id,reason,detail) VALUES(?,?,'INQUIRY',?,'SPAM','Controlled private inquiry evidence')",
        report, builder, inquiry);
  }

  @AfterEach
  void clear() {
    db.execute("TRUNCATE users CASCADE");
    db.execute("TRUNCATE rate_buckets");
    if (failedMailJob != null) db.update("DELETE FROM email_outbox WHERE id=?", failedMailJob);
    if (concurrentCategorySlug != null)
      db.update("DELETE FROM categories WHERE slug=?", concurrentCategorySlug.toString());
    db.update("DELETE FROM categories WHERE slug='authority-audit-category'");
  }

  MockHttpServletRequestBuilder body(String path, String json) {
    return post("/api/v1" + path).cookie(new Cookie("gl_session", "authority-admin"))
        .header("Origin", "http://localhost:3000").header("X-Requested-With", "getlancer")
        .contentType("application/json").content(json);
  }

  void revokeAfterLockWait(String lockSql, UUID key, MockHttpServletRequestBuilder call,
      Runnable revoke, int expectedStatus) throws Exception {
    String before = protectedState();
    var executor = Executors.newSingleThreadExecutor();
    try (var holder = db.getDataSource().getConnection()) {
      holder.setAutoCommit(false);
      int holderPid;
      try (var statement = holder.createStatement();
          var result = statement.executeQuery("SELECT pg_backend_pid()")) {
        assertTrue(result.next());
        holderPid = result.getInt(1);
      }
      try (var statement = holder.prepareStatement(lockSql)) {
        statement.setObject(1, key);
        try (var result = statement.executeQuery()) {
          assertTrue(result.next(), "The locked target must exist before the request starts");
        }
      }
      var pending = executor.submit(() -> mvc.perform(call).andReturn().getResponse().getStatus());
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      boolean waiting = false;
      while (System.nanoTime() < deadline && !pending.isDone()) {
        if (db.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database()"
                + " AND wait_event_type='Lock' AND ? = ANY(pg_blocking_pids(pid))",
                Integer.class, holderPid) > 0) {
          waiting = true;
          break;
        }
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
      }
      assertTrue(waiting, "The actual PostgreSQL target lock wait must precede revocation");
      revoke.run();
      holder.commit();
      assertEquals(expectedStatus, pending.get(10, TimeUnit.SECONDS).intValue());
      assertEquals(before, protectedState(),
          "Revoked authority must leave domain state, audit, notifications, mail and target credentials unchanged");
    } finally {
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
  }

  String protectedState() {
    return db.queryForObject("""
        SELECT jsonb_build_object(
          'targetAccount',(SELECT to_jsonb(u) FROM users u WHERE id=?),
          'profiles',(SELECT coalesce(jsonb_agg(to_jsonb(p) ORDER BY user_id),'[]'::jsonb) FROM developer_profiles p),
          'products',(SELECT coalesce(jsonb_agg(to_jsonb(p) ORDER BY id),'[]'::jsonb) FROM products p),
          'inquiries',(SELECT coalesce(jsonb_agg(to_jsonb(i) ORDER BY id),'[]'::jsonb) FROM inquiries i),
          'reports',(SELECT coalesce(jsonb_agg(to_jsonb(r) ORDER BY id),'[]'::jsonb) FROM reports r),
          'reviews',(SELECT coalesce(jsonb_agg(to_jsonb(r) ORDER BY id),'[]'::jsonb) FROM reviews r),
          'appeals',(SELECT coalesce(jsonb_agg(to_jsonb(a) ORDER BY id),'[]'::jsonb) FROM moderation_appeals a),
          'audit',(SELECT coalesce(jsonb_agg(to_jsonb(a) ORDER BY id),'[]'::jsonb) FROM moderation_actions a),
          'events',(SELECT coalesce(jsonb_agg(to_jsonb(e) ORDER BY id),'[]'::jsonb) FROM inquiry_events e),
          'analytics',(SELECT coalesce(jsonb_agg(to_jsonb(a) ORDER BY id),'[]'::jsonb) FROM analytics_events a),
          'notifications',(SELECT coalesce(jsonb_agg(to_jsonb(n) ORDER BY id),'[]'::jsonb) FROM notifications n),
          'mail',(SELECT coalesce(jsonb_agg(to_jsonb(e) ORDER BY id),'[]'::jsonb) FROM email_outbox e),
          'targetSessions',(SELECT coalesce(jsonb_agg(to_jsonb(s) ORDER BY token_hash),'[]'::jsonb) FROM sessions s WHERE user_id=?),
          'tokens',(SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY id),'[]'::jsonb) FROM account_tokens t),
          'challenges',(SELECT coalesce(jsonb_agg(to_jsonb(c) ORDER BY token_hash),'[]'::jsonb) FROM login_challenges c)
        )::text
        """, String.class, builder, builder);
  }

  @Test
  void productSuspensionRejectsMfaRevokedDuringProjectLockWait() throws Exception {
    revokeAfterLockWait("SELECT id FROM products WHERE id=? FOR UPDATE", product,
        body("/admin/products/" + product + "/suspend", "{\"reason\":\"Review the waiting project\"}"),
        () -> db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?", operator), 403);
    assertEquals("APPROVED", db.queryForObject("SELECT approval_status FROM products WHERE id=?", String.class, product));
    assertEquals("ACTIVE", db.queryForObject("SELECT lifecycle_status FROM products WHERE id=?", String.class, product));
  }

  @Test
  void profileApprovalRejectsSessionRevokedDuringTargetLockWait() throws Exception {
    db.update("UPDATE developer_profiles SET approval_status='PROFILE_PENDING' WHERE user_id=?", builder);
    revokeAfterLockWait("SELECT user_id FROM developer_profiles WHERE user_id=? FOR UPDATE", builder,
        body("/admin/profiles/" + builder + "/approve", "{\"reason\":\"Review the waiting profile\"}"),
        () -> db.update("DELETE FROM sessions WHERE user_id=?", operator), 401);
    assertEquals("PROFILE_PENDING", db.queryForObject("SELECT approval_status FROM developer_profiles WHERE user_id=?", String.class, builder));
  }

  @Test
  void profileDecisionRollsBackAuditAndDomainWriteWhenMfaIsRevokedDuringNotificationWait() throws Exception {
    db.update("UPDATE developer_profiles SET approval_status='PROFILE_PENDING' WHERE user_id=?", builder);
    // Approval and audit can proceed; the notification's user foreign key waits on this account lock.
    revokeAfterLockWait("SELECT id FROM users WHERE id=? FOR UPDATE", builder,
        body("/admin/profiles/" + builder + "/approve", "{\"reason\":\"Review before notifying the builder\"}"),
        () -> db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?", operator), 403);
    assertEquals("PROFILE_PENDING", db.queryForObject("SELECT approval_status FROM developer_profiles WHERE user_id=?", String.class, builder));
  }

  @Test
  void profileDecisionRejectsReplacementAdministratorIdentityDuringTargetLockWait() throws Exception {
    UUID replacement = user("authority-replacement");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')", replacement);
    revokeAfterLockWait("SELECT user_id FROM developer_profiles WHERE user_id=? FOR UPDATE", builder,
        body("/admin/profiles/" + builder + "/suspend", "{\"reason\":\"Review the original operator decision\"}"),
        () -> db.update("UPDATE sessions SET user_id=? WHERE token_hash=?", replacement, Support.hash("authority-admin")), 403);
    assertEquals("APPROVED", db.queryForObject("SELECT approval_status FROM developer_profiles WHERE user_id=?", String.class, builder));
  }

  @Test
  void accountSuspensionRejectsRoleRevokedDuringTargetLockWaitWithoutRevokingTargetSessions() throws Exception {
    revokeAfterLockWait("SELECT id FROM users WHERE id=? FOR UPDATE", builder,
        body("/admin/accounts/" + builder + "/suspend", "{\"reason\":\"Review the waiting account\"}"),
        () -> db.update("DELETE FROM user_roles WHERE user_id=? AND role='ADMIN'", operator), 403);
    assertEquals("ACTIVE", db.queryForObject("SELECT account_status FROM users WHERE id=?", String.class, builder));
  }

  @Test
  void accountSuspensionRollsBackWhenSessionIsRevokedDuringTargetSessionDeleteWait() throws Exception {
    db.update("INSERT INTO login_challenges(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",
        Support.hash("authority-target-challenge"), builder);
    db.update("INSERT INTO account_tokens(id,token_hash,kind,user_id,email,expires_at) VALUES(?,?,'VERIFY_EMAIL',?,'authority-builder@example.test',now()+interval '1 hour')",
        UUID.randomUUID(), Support.hash("authority-target-token"), builder);
    revokeAfterLockWait("SELECT token_hash FROM sessions WHERE user_id=? FOR UPDATE", builder,
        body("/admin/accounts/" + builder + "/suspend", "{\"reason\":\"Review the waiting target credentials\"}"),
        () -> db.update("DELETE FROM sessions WHERE user_id=?", operator), 401);
    assertEquals("ACTIVE", db.queryForObject("SELECT account_status FROM users WHERE id=?", String.class, builder));
    assertNull(db.queryForObject("SELECT used_at FROM account_tokens WHERE user_id=?", java.sql.Timestamp.class, builder));
  }

  @Test
  void reportResolutionRejectsMfaThatExpiresDuringReportLockWait() throws Exception {
    revokeAfterLockWait("SELECT id FROM reports WHERE id=? FOR UPDATE", report,
        body("/admin/reports/" + report + "/resolve", "{\"reason\":\"Review the waiting report\",\"targetAction\":\"NONE\"}"),
        () -> db.update("UPDATE sessions SET issued_at=clock_timestamp()-interval '16 minutes' WHERE user_id=?", operator), 403);
    assertEquals("OPEN", db.queryForObject("SELECT status FROM reports WHERE id=?", String.class, report));
    assertNull(db.queryForObject("SELECT resolution FROM reports WHERE id=?", String.class, report));
  }

  @Test
  void reportResolutionRejectsMfaRevokedDuringNestedInquiryLockWait() throws Exception {
    revokeAfterLockWait("SELECT id FROM inquiries WHERE id=? FOR UPDATE", inquiry,
        body("/admin/reports/" + report + "/resolve", "{\"reason\":\"Review the waiting inquiry\",\"targetAction\":\"BLOCK\"}"),
        () -> db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?", operator), 403);
    assertEquals("OPEN", db.queryForObject("SELECT status FROM reports WHERE id=?", String.class, report));
    assertNull(db.queryForObject("SELECT resolution FROM reports WHERE id=?", String.class, report));
    assertEquals("CLEAR", db.queryForObject("SELECT moderation_status FROM inquiries WHERE id=?", String.class, inquiry));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM inquiry_events WHERE inquiry_id=?", Integer.class, inquiry));
  }

  @Test
  void reviewPublicationRejectsRoleRevokedDuringReviewLockWait() throws Exception {
    UUID review = UUID.randomUUID();
    db.update("INSERT INTO reviews(id,inquiry_id,developer_user_id,rating,review_text) VALUES(?,?,?,5,'A useful completed engagement')",
        review, inquiry, builder);
    revokeAfterLockWait("SELECT id FROM reviews WHERE id=? FOR UPDATE", review,
        body("/admin/reviews/" + review + "/publish", "{\"reason\":\"Review the waiting client feedback\"}"),
        () -> db.update("DELETE FROM user_roles WHERE user_id=? AND role='ADMIN'", operator), 403);
    assertEquals("HELD_FOR_REVIEW", db.queryForObject("SELECT moderation_status FROM reviews WHERE id=?", String.class, review));
  }

  @Test
  void taxonomyRenameRollsBackWhenAdminRoleIsRevokedDuringCascadingProjectWait() throws Exception {
    revokeAfterLockWait("SELECT id FROM products WHERE id=? FOR UPDATE", product,
        body("/admin/categories", "{\"slug\":\"authority-audit-category\",\"name\":\"Changed authority category\",\"active\":false}"),
        () -> db.update("DELETE FROM user_roles WHERE user_id=? AND role='ADMIN'", operator), 403);
    assertEquals("Authority category", db.queryForObject("SELECT category FROM products WHERE id=?", String.class, product));
    assertEquals("Authority category", db.queryForObject("SELECT name FROM categories WHERE slug='authority-audit-category'", String.class));
    assertTrue(db.queryForObject("SELECT active FROM categories WHERE slug='authority-audit-category'", Boolean.class));
  }

  @Test
  void newTaxonomySlugUpsertRollsBackWhenMfaIsRevokedDuringConcurrentInsertWait() throws Exception {
    UUID slug = UUID.randomUUID();
    concurrentCategorySlug = slug;
    revokeAfterLockWait("WITH fixture AS (SELECT ?::text AS slug)"
            + " INSERT INTO categories(slug,name,active) SELECT slug,'Uncommitted '||slug,true FROM fixture RETURNING slug",
        slug,
        body("/admin/categories", "{\"slug\":\"" + slug + "\",\"name\":\"Changed " + slug + "\",\"active\":false}"),
        () -> db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?", operator), 403);
    assertEquals("Uncommitted " + slug, db.queryForObject("SELECT name FROM categories WHERE slug=?", String.class, slug.toString()));
    assertTrue(db.queryForObject("SELECT active FROM categories WHERE slug=?", Boolean.class, slug.toString()));
  }

  @Test
  void reportTriageRollsBackWhenRoleIsRevokedDuringUpdateWait() throws Exception {
    revokeAfterLockWait("SELECT id FROM reports WHERE id=? FOR UPDATE", report,
        body("/admin/reports/" + report + "/triage", "{\"severity\":\"CRITICAL\",\"status\":\"TRIAGED\",\"reason\":\"Review this waiting report priority\"}"),
        () -> db.update("DELETE FROM user_roles WHERE user_id=? AND role='ADMIN'", operator), 403);
    assertEquals("OPEN", db.queryForObject("SELECT status FROM reports WHERE id=?", String.class, report));
    assertEquals("MEDIUM", db.queryForObject("SELECT severity FROM reports WHERE id=?", String.class, report));
    assertNull(db.queryForObject("SELECT triage_note FROM reports WHERE id=?", String.class, report));
  }

  @Test
  void emailRetryRollsBackWhenMfaIsRevokedDuringUpdateWait() throws Exception {
    UUID job = UUID.randomUUID();
    failedMailJob = job;
    db.update("INSERT INTO email_outbox(id,recipient,subject,body,attempts,next_attempt_at)"
            + " VALUES(?,'authority-builder@example.test','Retained delivery failure','Retained mail evidence',8,now()+interval '1 day')",
        job);
    revokeAfterLockWait("SELECT id FROM email_outbox WHERE id=? FOR UPDATE", job,
        body("/admin/email-jobs/" + job + "/retry", "{\"reason\":\"Review the waiting mail retry\"}"),
        () -> db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?", operator), 403);
    assertEquals(8, db.queryForObject("SELECT attempts FROM email_outbox WHERE id=?", Integer.class, job));
  }

  @Test
  void overturningAppealRejectsMfaRevokedDuringNestedProductLockWait() throws Exception {
    UUID decision = UUID.randomUUID(), appeal = UUID.randomUUID();
    db.update("UPDATE products SET approval_status='SUSPENDED',lifecycle_status='SUSPENDED',moderation_reason='Retained restriction evidence' WHERE id=?", product);
    db.update("INSERT INTO moderation_actions(id,admin_id,target_type,target_id,action,reason) VALUES(?,?,'PRODUCT',?,'suspend','Retained restriction evidence')",
        decision, operator, product);
    db.update("INSERT INTO moderation_appeals(id,decision_id,appellant_id,statement) VALUES(?,?,?,'Please independently review this exact restriction.')",
        appeal, decision, builder);
    revokeAfterLockWait("SELECT id FROM products WHERE id=? FOR UPDATE", product,
        body("/admin/appeals/" + appeal + "/decision", "{\"status\":\"OVERTURNED\",\"reason\":\"Review this waiting product restriction\"}"),
        () -> db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?", operator), 403);
    assertEquals("SUSPENDED", db.queryForObject("SELECT approval_status FROM products WHERE id=?", String.class, product));
    assertEquals("SUSPENDED", db.queryForObject("SELECT lifecycle_status FROM products WHERE id=?", String.class, product));
    assertEquals("OPEN", db.queryForObject("SELECT status FROM moderation_appeals WHERE id=?", String.class, appeal));
  }

  @Test
  void nonRestoringAppealDecisionRejectsSessionRevokedDuringAppealLockWait() throws Exception {
    UUID decision = UUID.randomUUID(), appeal = UUID.randomUUID();
    db.update("INSERT INTO moderation_actions(id,admin_id,target_type,target_id,action,reason) VALUES(?,?,'PRODUCT',?,'suspend','Retained restriction evidence')",
        decision, operator, product);
    db.update("INSERT INTO moderation_appeals(id,decision_id,appellant_id,statement) VALUES(?,?,?,'Please independently review this exact restriction.')",
        appeal, decision, builder);
    revokeAfterLockWait("SELECT id FROM moderation_appeals WHERE id=? FOR UPDATE", appeal,
        body("/admin/appeals/" + appeal + "/decision", "{\"status\":\"UNDER_REVIEW\",\"reason\":\"Review this waiting appeal\"}"),
        () -> db.update("DELETE FROM sessions WHERE user_id=?", operator), 401);
    assertEquals("OPEN", db.queryForObject("SELECT status FROM moderation_appeals WHERE id=?", String.class, appeal));
    assertNull(db.queryForObject("SELECT reviewer_id FROM moderation_appeals WHERE id=?", UUID.class, appeal));
    assertNull(db.queryForObject("SELECT resolution FROM moderation_appeals WHERE id=?", String.class, appeal));
  }
}
