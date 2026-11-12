package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
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
class ProductProfileTrustAuthorityIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  UUID builder, operator, product, inquiry;
  Map<String, Object> originalProduct, originalProfile, originalProof, originalEntitlement, originalInquiry;
  List<Map<String, Object>> originalCategories, originalInquiryEvents;
  int analyticsCount;

  UUID user(String token) {
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",
        id, token + "@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT'),(?,'DEVELOPER')", id, id);
    db.update("INSERT INTO developer_profiles(user_id,slug,display_name,headline,bio,technology,category,approval_status)"
        + " VALUES(?,?,?,'Existing builder headline','Existing builder biography with retained public evidence.','Java','Authority boundary category','APPROVED')",
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
    builder = user("product-authority-builder");
    operator = user("product-authority-admin");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')", operator);
    db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?", operator);
    db.update("INSERT INTO categories(slug,name,active) VALUES('product-authority-category','Authority boundary category',true)"
        + " ON CONFLICT(slug) DO UPDATE SET name=excluded.name,active=true");
    product = UUID.randomUUID();
    inquiry = UUID.randomUUID();
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,approval_status,lifecycle_status,rights_confirmed,repository_url)"
        + " VALUES(?,?,?,'Existing authority project','Existing project summary','Existing project description with retained public evidence.','SAAS','Authority boundary category','','Built the existing public evidence','APPROVED','ACTIVE',true,'https://github.com/example/existing-authority-project')",
        product, builder, product.toString());
    db.update("INSERT INTO product_categories(product_id,category_slug) VALUES(?,'product-authority-category')", product);
    db.update("INSERT INTO repository_verifications(product_id,repository_url,challenge)"
        + " VALUES(?,'https://github.com/example/existing-authority-project','getlancer-proof-existing-authority')", product);
    db.update("INSERT INTO inquiries(id,reference_product_id,developer_user_id,client_email,client_name,request_type,description,budget_band,timeline_band,current_status,email_confirmed_at,idempotency_key,request_hash)"
        + " VALUES(?,?,?,'separate-client@example.test','Separate client','SIMILAR_BUILD','Retained client-confirmed engagement evidence','USD_3K_10K','ONE_TO_THREE_MONTHS','COMPLETED',now(),?,'authority-test')",
        inquiry, product, builder, UUID.randomUUID());
    db.update("INSERT INTO inquiry_events(id,inquiry_id,event_type,actor_type) VALUES(?,?,'COMPLETED','CLIENT_TOKEN')",
        UUID.randomUUID(), inquiry);
    originalProduct = db.queryForMap("SELECT * FROM products WHERE id=?", product);
    originalProfile = db.queryForMap("SELECT * FROM developer_profiles WHERE user_id=?", builder);
    originalProof = db.queryForMap("SELECT * FROM repository_verifications WHERE product_id=?", product);
    originalEntitlement = db.queryForMap("SELECT * FROM showcase_entitlements WHERE user_id=?", builder);
    originalInquiry = db.queryForMap("SELECT * FROM inquiries WHERE id=?", inquiry);
    originalCategories = db.queryForList("SELECT * FROM product_categories WHERE product_id=?", product);
    originalInquiryEvents = db.queryForList("SELECT * FROM inquiry_events WHERE inquiry_id=? ORDER BY id", inquiry);
    analyticsCount = db.queryForObject("SELECT count(*) FROM analytics_events", Integer.class);
  }

  MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder call, String token) {
    return call.cookie(new Cookie("gl_session", token)).header("Origin", "http://localhost:3000")
        .header("X-Requested-With", "getlancer").contentType("application/json");
  }

  void revokeDuringObservedLockWait(String lockSql, UUID key, MockHttpServletRequestBuilder call,
      Runnable revoke, int expectedStatus) throws Exception {
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
          assertTrue(result.next(), "The row held by the test must already exist");
        }
      }
      var pending = executor.submit(() -> mvc.perform(call).andReturn().getResponse().getStatus());
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      boolean waiting = false;
      while (System.nanoTime() < deadline && !pending.isDone()) {
        if (db.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))",
            Integer.class, holderPid) > 0) {
          waiting = true;
          break;
        }
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
      }
      assertTrue(waiting, "The request must be observed waiting on the actual PostgreSQL lock before revocation");
      revoke.run();
      holder.commit();
      assertEquals(expectedStatus, pending.get(10, TimeUnit.SECONDS).intValue());
    } finally {
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
  }

  void assertNoCommittedEffects() {
    assertEquals(originalProduct, db.queryForMap("SELECT * FROM products WHERE id=?", product));
    assertEquals(originalProfile, db.queryForMap("SELECT * FROM developer_profiles WHERE user_id=?", builder));
    assertEquals(originalProof, db.queryForMap("SELECT * FROM repository_verifications WHERE product_id=?", product));
    assertEquals(originalEntitlement, db.queryForMap("SELECT * FROM showcase_entitlements WHERE user_id=?", builder));
    assertEquals(originalInquiry, db.queryForMap("SELECT * FROM inquiries WHERE id=?", inquiry));
    assertEquals(originalCategories, db.queryForList("SELECT * FROM product_categories WHERE product_id=?", product));
    assertEquals(originalInquiryEvents, db.queryForList("SELECT * FROM inquiry_events WHERE inquiry_id=? ORDER BY id", inquiry));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM earned_capacity_awards", Integer.class));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM moderation_actions", Integer.class));
    assertEquals(analyticsCount, db.queryForObject("SELECT count(*) FROM analytics_events", Integer.class));
  }

  @Test
  void projectEditRollsBackWhenSessionRevokedDuringLaterRepositoryProofWrite() throws Exception {
    var call = as(patch("/api/v1/developer/products/" + product), "product-authority-builder")
        .content("""
            {"title":"Changed authority project","summary":"Changed project summary",
             "description":"Changed project description with new public evidence.","projectType":"SAAS",
             "category":"Authority boundary category","technology":"","visibility":"PUBLIC",
             "contribution":"Built changed public evidence","liveUrl":"","videoUrl":"",
             "repositoryUrl":"https://github.com/example/changed-authority-project","pricingNote":"",
             "rightsConfirmed":true,"availableForSimilarWork":true}
            """);
    revokeDuringObservedLockWait("SELECT product_id FROM repository_verifications WHERE product_id=? FOR UPDATE",
        product, call, () -> db.update("DELETE FROM sessions WHERE user_id=?", builder), 401);
    assertNoCommittedEffects();
  }

  @Test
  void profileEditRejectsDeveloperRoleRevokedDuringProfileLockWait() throws Exception {
    var call = as(put("/api/v1/developer/profile"), "product-authority-builder")
        .content("""
            {"displayName":"Changed builder name","headline":"Changed builder headline",
             "bio":"Changed builder biography with new public evidence.","technology":"Java",
             "category":"Authority boundary category","availabilityStatus":"AVAILABLE_NOW",
             "githubUrl":"","linkedinUrl":""}
            """);
    revokeDuringObservedLockWait("SELECT user_id FROM developer_profiles WHERE user_id=? FOR UPDATE",
        builder, call, () -> db.update("DELETE FROM user_roles WHERE user_id=? AND role='DEVELOPER'", builder), 403);
    assertNoCommittedEffects();
  }

  @Test
  void repositoryApprovalRejectsMfaRevokedDuringVerificationLockWait() throws Exception {
    var call = as(post("/api/v1/admin/verifications/" + product + "/approve"), "product-authority-admin")
        .content("{\"reason\":\"Independently checked the repository proof\"}");
    revokeDuringObservedLockWait("SELECT product_id FROM repository_verifications WHERE product_id=? FOR UPDATE",
        product, call, () -> db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?", operator), 403);
    assertNoCommittedEffects();
  }

  @Test
  void earnedCapacityRejectsAdminRoleRevokedDuringEntitlementLockWait() throws Exception {
    var call = as(post("/api/v1/admin/earned-capacity/" + inquiry), "product-authority-admin")
        .content("{\"reason\":\"Independently checked the completed client outcome\"}");
    revokeDuringObservedLockWait("SELECT user_id FROM showcase_entitlements WHERE user_id=? FOR UPDATE",
        builder, call, () -> db.update("DELETE FROM user_roles WHERE user_id=? AND role='ADMIN'", operator), 403);
    assertNoCommittedEffects();
  }
}
