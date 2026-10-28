package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.admin.AdminService;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
class Round3OperationsIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired AdminService adminService;
  @Autowired PlatformTransactionManager transactions;
  UUID builder, operator, product, inquiry, review;

  record Target(String type, UUID id, String table, String key, String column,
      String route, String action, String restricted, String restored) {}

  UUID user(String token) {
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())", id, token + "@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT'),(?,'DEVELOPER')", id, id);
    db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,?,'APPROVED')", id, token, token);
    db.update("INSERT INTO showcase_entitlements(user_id) VALUES(?)", id);
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')", Support.hash(token), id);
    return id;
  }

  @BeforeEach void prepare() {
    db.execute("TRUNCATE users CASCADE");
    db.execute("TRUNCATE rate_buckets");
    builder = user("operations-builder");
    operator = user("operations-admin");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')", operator);
    db.update("UPDATE sessions SET mfa_verified=true,issued_at=now() WHERE user_id=?", operator);
    product = UUID.randomUUID(); inquiry = UUID.randomUUID(); review = UUID.randomUUID();
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,approval_status,lifecycle_status,rights_confirmed) VALUES(?,?,?,'Appeal project','Working public evidence','A controlled appeal regression project.','SAAS','CRM','Java','Built this test project','APPROVED','ACTIVE',true)", product, builder, product.toString());
    db.update("INSERT INTO inquiries(id,reference_product_id,developer_user_id,client_email,client_name,request_type,description,budget_band,timeline_band,current_status,email_confirmed_at,idempotency_key,request_hash) VALUES(?,?,?,'client@example.test','Client','SIMILAR_BUILD','Controlled private engagement requirements','USD_3K_10K','ONE_TO_THREE_MONTHS','COMPLETED',now(),?,'test')", inquiry, product, builder, UUID.randomUUID());
    db.update("INSERT INTO reviews(id,inquiry_id,developer_user_id,rating,review_text,moderation_status) VALUES(?,?,?,5,'The work was completed as agreed.','PUBLISHED')", review, inquiry, builder);
  }

  Target target(String type) {
    return switch (type) {
      case "ACCOUNT" -> new Target(type, builder, "users", "id", "account_status", "accounts", "suspend", "SUSPENDED", "ACTIVE");
      case "USER" -> new Target(type, builder, "developer_profiles", "user_id", "approval_status", "profiles", "suspend", "SUSPENDED", "APPROVED");
      case "PRODUCT" -> new Target(type, product, "products", "id", "approval_status", "products", "suspend", "SUSPENDED", "APPROVED");
      case "INQUIRY" -> new Target(type, inquiry, "inquiries", "id", "moderation_status", "inquiries", "block", "BLOCKED", "CLEAR");
      case "REVIEW" -> new Target(type, review, "reviews", "id", "moderation_status", "reviews", "hide", "HIDDEN", "PUBLISHED");
      default -> throw new IllegalArgumentException(type);
    };
  }

  MockHttpServletRequestBuilder body(String path, String actor, Map<String, Object> value) throws Exception {
    return post("/api/v1" + path).cookie(new Cookie("gl_session", actor)).header("Origin", "http://localhost:3000")
        .header("X-Requested-With", "getlancer").contentType("application/json").content(json.writeValueAsString(value));
  }

  UUID manual(Target target, String action, String reason) throws Exception {
    mvc.perform(body("/admin/" + target.route() + "/" + target.id() + "/" + action, "operations-admin", Map.of("reason", reason)))
        .andExpect(status().isOk());
    return db.queryForObject("SELECT id FROM moderation_actions WHERE target_type=? AND target_id=? AND reason=?", UUID.class, target.type(), target.id(), reason);
  }

  UUID appeal(UUID decision) {
    // Account suspension revokes sessions. Retained account appeals can be entered by support;
    // their restoration must use the same guard as authenticated artifact appeals.
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO moderation_appeals(id,decision_id,appellant_id,statement) VALUES(?,?,?,'Please independently review this exact restriction.')", id, decision, builder);
    return id;
  }

  void decide(UUID appeal, String state, int expected) throws Exception {
    var result = mvc.perform(body("/admin/appeals/" + appeal + "/decision", "operations-admin", Map.of("status", state, "reason", "Independent review of the selected restriction.")))
        .andExpect(status().is(expected));
    if (expected == 409) result.andExpect(jsonPath("$.error.code").value("MODERATION_APPEAL_STALE"));
  }

  String state(Target target) {
    return db.queryForObject("SELECT " + target.column() + " FROM " + target.table() + " WHERE " + target.key() + "=?", String.class, target.id());
  }

  void unchangedConflict(Target target, UUID appeal) throws Exception {
    String before = state(target);
    int history = db.queryForObject("SELECT count(*) FROM moderation_actions", Integer.class);
    int notices = db.queryForObject("SELECT count(*) FROM notifications", Integer.class);
    int mail = db.queryForObject("SELECT count(*) FROM email_outbox", Integer.class);
    decide(appeal, "OVERTURNED", 409);
    assertEquals(before, state(target));
    assertEquals("OPEN", db.queryForObject("SELECT status FROM moderation_appeals WHERE id=?", String.class, appeal));
    assertEquals(history, db.queryForObject("SELECT count(*) FROM moderation_actions", Integer.class));
    assertEquals(notices, db.queryForObject("SELECT count(*) FROM notifications", Integer.class));
    assertEquals(mail, db.queryForObject("SELECT count(*) FROM email_outbox", Integer.class));
  }

  @ParameterizedTest @ValueSource(strings = {"ACCOUNT", "USER", "PRODUCT", "INQUIRY", "REVIEW"})
  void oldAppealCannotUndoLaterManualRestrictionAndExactCurrentAppealStillWorks(String type) throws Exception {
    Target target = target(type);
    UUID first = manual(target, target.action(), "First reviewed restriction."), stale = appeal(first);
    UUID second = manual(target, target.action(), "Second independently reviewed restriction.");
    assertTrue(db.queryForObject("SELECT decision_order FROM moderation_actions WHERE id=?", Long.class, second)
        > db.queryForObject("SELECT decision_order FROM moderation_actions WHERE id=?", Long.class, first));
    unchangedConflict(target, stale);
    decide(appeal(second), "OVERTURNED", 200);
    assertEquals(target.restored(), state(target));
    if (type.equals("PRODUCT")) assertEquals("ACTIVE", db.queryForObject("SELECT lifecycle_status FROM products WHERE id=?", String.class, product));
  }

  @ParameterizedTest @ValueSource(strings = {"ACCOUNT", "USER", "PRODUCT", "INQUIRY", "REVIEW"})
  void targetLockWaitMustObserveRestrictionCommittedDuringWait(String type) throws Exception {
    Target target = target(type);
    UUID first = manual(target, target.action(), "Original restriction before contention."), pending = appeal(first);
    var original = db.queryForMap("SELECT id,target_type,target_id,action,decision_order FROM moderation_actions WHERE id=?", first);
    var executor = Executors.newSingleThreadExecutor();
    var started = new CountDownLatch(1);
    var waiter = new AtomicInteger();
    var tx = new TransactionTemplate(transactions); tx.setTimeout(15);
    try {
      @SuppressWarnings("unchecked")
      java.util.concurrent.Future<Integer>[] work = new java.util.concurrent.Future[1];
      tx.executeWithoutResult(held -> {
        adminService.requireCurrentAppealDecision(original);
        int holder = db.queryForObject("SELECT pg_backend_pid()", Integer.class);
        work[0] = executor.submit(() -> tx.execute(status -> {
          waiter.set(db.queryForObject("SELECT pg_backend_pid()", Integer.class)); started.countDown();
          try {
            int code = mvc.perform(body("/admin/appeals/" + pending + "/decision", "operations-admin", Map.of("status", "OVERTURNED", "reason", "Review selected appeal after the target lock wait.")))
                .andReturn().getResponse().getStatus();
            status.setRollbackOnly(); return code;
          } catch (Exception e) { throw new RuntimeException(e); }
        }));
        try {
          assertTrue(started.await(5, TimeUnit.SECONDS));
          long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
          boolean blocked = false;
          while (System.nanoTime() < deadline) {
            blocked = Boolean.TRUE.equals(db.queryForObject("SELECT ?=ANY(pg_blocking_pids(?))", Boolean.class, holder, waiter.get()));
            if (blocked) break;
            LockSupport.parkNanos(Duration.ofMillis(5).toNanos());
          }
          assertTrue(blocked, "The actual PostgreSQL lock wait must start before the new restriction.");
          manual(target, target.action(), "New restriction committed while appeal waited.");
        } catch (Exception e) { throw new RuntimeException(e); }
      });
      assertEquals(409, work[0].get(10, TimeUnit.SECONDS));
      assertEquals(target.restricted(), state(target));
      assertEquals("OPEN", db.queryForObject("SELECT status FROM moderation_appeals WHERE id=?", String.class, pending));
    } finally { executor.shutdownNow(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); }
  }

  @Test void sequenceOrderOutranksEarlierTransactionStartTimestamp() throws Exception {
    Target target = target("INQUIRY");
    var executor = Executors.newSingleThreadExecutor();
    var began = new CountDownLatch(1); var continueLater = new CountDownLatch(1);
    var tx = new TransactionTemplate(transactions); tx.setTimeout(15);
    try {
      var later = executor.submit(() -> tx.execute(status -> {
        db.queryForObject("SELECT now()", java.sql.Timestamp.class); began.countDown();
        try { assertTrue(continueLater.await(5, TimeUnit.SECONDS)); return manual(target, "block", "Later effect from an earlier transaction."); }
        catch (Exception e) { throw new RuntimeException(e); }
      }));
      assertTrue(began.await(5, TimeUnit.SECONDS));
      UUID first = manual(target, "quarantine", "First effect from a later transaction.");
      UUID pending = appeal(first); continueLater.countDown(); UUID second = later.get(10, TimeUnit.SECONDS);
      assertTrue(db.queryForObject("SELECT a.created_at<b.created_at AND a.decision_order>b.decision_order FROM moderation_actions a,moderation_actions b WHERE a.id=? AND b.id=?", Boolean.class, second, first));
      unchangedConflict(target, pending); decide(appeal(second), "OVERTURNED", 200);
    } finally { continueLater.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); }
  }

  void restrictFixture(Target target) {
    // Preserve active_requires_approval while arranging retained history without replaying it.
    db.update("UPDATE " + target.table() + " SET " + target.column() + "=?"
        + (target.type().equals("PRODUCT") ? ",lifecycle_status='SUSPENDED'" : "")
        + " WHERE " + target.key() + "=?", target.restricted(), target.id());
  }

  UUID legacy(Target target) {
    restrictFixture(target);
    UUID decision = UUID.randomUUID();
    db.update("INSERT INTO moderation_actions(id,admin_id,target_type,target_id,action,reason,decision_order) VALUES(?,?,?,?,?,'Retained legacy restriction.',NULL)", decision, operator, target.type(), target.id(), target.action());
    return decision;
  }

  @ParameterizedTest @ValueSource(strings = {"ACCOUNT", "USER", "PRODUCT", "INQUIRY", "REVIEW"})
  void uniqueMatchingLegacyRestrictionCanStillBeRestored(String type) throws Exception {
    Target target = target(type); decide(appeal(legacy(target)), "OVERTURNED", 200); assertEquals(target.restored(), state(target));
  }

  @ParameterizedTest @ValueSource(strings = {"ACCOUNT", "USER", "PRODUCT", "INQUIRY", "REVIEW"})
  void ambiguousLegacyAndThenMixedHistoryRequireCurrentDecision(String type) throws Exception {
    Target target = target(type); UUID pending = appeal(legacy(target)); legacy(target);
    unchangedConflict(target, pending);
    UUID current = manual(target, target.action(), "Current ordered restriction supersedes retained history.");
    unchangedConflict(target, pending); decide(appeal(current), "OVERTURNED", 200); assertEquals(target.restored(), state(target));
  }

  @ParameterizedTest @ValueSource(strings = {"ACCOUNT", "USER", "PRODUCT", "INQUIRY", "REVIEW"})
  void changedOrClosedResourceCannotBeRestoredFromOldAppeal(String type) throws Exception {
    Target target = target(type); UUID pending = appeal(manual(target, target.action(), "Restriction before resource changed."));
    if (type.equals("PRODUCT")) db.update("UPDATE products SET lifecycle_status='ARCHIVED' WHERE id=?", product);
    else db.update("UPDATE " + target.table() + " SET " + target.column() + "=? WHERE " + target.key() + "=?",
        type.equals("ACCOUNT") ? "DELETED" : type.equals("USER") ? "DRAFT" : type.equals("REVIEW") ? "HELD_FOR_REVIEW" : "CLEAR", target.id());
    unchangedConflict(target, pending);
    if (type.equals("PRODUCT")) assertEquals("ARCHIVED", db.queryForObject("SELECT lifecycle_status FROM products WHERE id=?", String.class, product));
  }

  @ParameterizedTest @ValueSource(strings = {"USER/request-changes", "PRODUCT/request-changes", "PRODUCT/reject", "INQUIRY/quarantine"})
  void otherCurrentRestrictionActionsStillRestore(String command) throws Exception {
    String[] parts = command.split("/"); Target target = target(parts[0]);
    if (target.type().equals("USER")) db.update("UPDATE developer_profiles SET approval_status='PROFILE_PENDING' WHERE user_id=?", builder);
    if (target.type().equals("PRODUCT")) db.update("UPDATE products SET approval_status='PENDING_REVIEW',lifecycle_status='DRAFT' WHERE id=?", product);
    decide(appeal(manual(target, parts[1], "Current matching alternate restriction.")), "OVERTURNED", 200);
    assertEquals(target.restored(), state(target));
  }

  @ParameterizedTest @ValueSource(strings = {"UNDER_REVIEW", "UPHELD"})
  void staleAppealCanBeReviewedWithoutRestoringResource(String outcome) throws Exception {
    Target target = target("INQUIRY"); UUID pending = appeal(manual(target, "block", "First restriction for non-restoring review."));
    manual(target, "block", "Current restriction remains independently enforced.");
    decide(pending, outcome, 200); assertEquals("BLOCKED", state(target));
    assertEquals(outcome, db.queryForObject("SELECT status FROM moderation_appeals WHERE id=?", String.class, pending));
  }

  @Test void unrelatedRepositoryEvidenceDoesNotSupersedeProjectRestriction() throws Exception {
    Target target = target("PRODUCT"); UUID pending = appeal(manual(target, "suspend", "Current project publication restriction."));
    db.update("INSERT INTO moderation_actions(id,admin_id,target_type,target_id,action,reason) VALUES(?,?,'PRODUCT',?,'REPOSITORY_REVOKE','Independent repository evidence.')", UUID.randomUUID(), operator, product);
    decide(pending, "OVERTURNED", 200); assertEquals("APPROVED", state(target));
  }

  @Test void earnedCapacityEvidenceDoesNotSupersedeInquiryRestriction() throws Exception {
    Target target = target("INQUIRY"); UUID pending = appeal(manual(target, "block", "Current private engagement restriction."));
    db.update("INSERT INTO moderation_actions(id,admin_id,target_type,target_id,action,reason) VALUES(?,?,'INQUIRY',?,'EARNED_CAPACITY','Independent capacity evidence.')", UUID.randomUUID(), operator, inquiry);
    decide(pending, "OVERTURNED", 200); assertEquals("CLEAR", state(target));
  }

  @ParameterizedTest @ValueSource(strings = {"ACCOUNT", "USER", "PRODUCT", "INQUIRY", "REVIEW"})
  void nonrestrictiveDecisionCannotAuthorizeRestoration(String type) throws Exception {
    Target target = target(type); restrictFixture(target);
    String action = type.equals("ACCOUNT") || type.equals("INQUIRY") ? "restore" : type.equals("REVIEW") ? "publish" : "approve";
    UUID selected = UUID.randomUUID();
    db.update("INSERT INTO moderation_actions(id,admin_id,target_type,target_id,action,reason) VALUES(?,?,?,?,?,'Retained nonrestrictive decision.')", selected, operator, type, target.id(), action);
    unchangedConflict(target, appeal(selected));
  }

  @Test void actionMustMatchCurrentRestrictionEvenWhenItIsTheLatestDecision() throws Exception {
    Target target = target("INQUIRY"); UUID pending = appeal(manual(target, "quarantine", "Selected quarantine restriction."));
    db.update("UPDATE inquiries SET moderation_status='BLOCKED' WHERE id=?", inquiry);
    unchangedConflict(target, pending);
  }

  @Test void restorationStillRequiresCurrentAdministratorMfa() throws Exception {
    Target target = target("INQUIRY"); UUID pending = appeal(manual(target, "block", "Restricted engagement for authority controls."));
    var request = body("/admin/appeals/" + pending + "/decision", "operations-builder", Map.of("status", "OVERTURNED", "reason", "Unauthorized restoration must fail."));
    mvc.perform(request).andExpect(status().isForbidden());
    db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?", operator);
    decide(pending, "OVERTURNED", 403); assertEquals("BLOCKED", state(target));
    db.update("UPDATE sessions SET mfa_verified=true,issued_at=now()-interval '16 minutes' WHERE user_id=?", operator);
    decide(pending, "OVERTURNED", 403); assertEquals("BLOCKED", state(target));
    db.update("UPDATE sessions SET issued_at=now() WHERE user_id=?", operator);
    decide(pending, "OVERTURNED", 200); assertEquals("CLEAR", state(target));
  }
}
