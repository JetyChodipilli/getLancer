package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.getlancer.config.Bootstrap;
import com.getlancer.jobs.Maintenance;
import com.getlancer.moderation.ModerationService;
import com.getlancer.products.ProductService;
import com.getlancer.shared.ApiError;
import com.getlancer.shared.RulesSmoke;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@org.springframework.test.context.ContextConfiguration(initializers = TestDatabaseGuard.class)
@SpringBootTest(
    properties = {
      "app.environment=local",
      "app.jobs-enabled=false",
      "app.admin-password=",
      "app.admin-totp=",
      "spring.config.import=",
      "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}",
      "spring.datasource.username=${TEST_DB_USERNAME:postgres}",
      "spring.datasource.password=${TEST_DB_PASSWORD:}",
      "spring.datasource.hikari.schema=getlancer_test",
      "spring.flyway.default-schema=getlancer_test",
      "spring.flyway.schemas=getlancer_test",
      "app.origin=http://localhost:3000",
      "app.secure-cookie=false",
      "app.storage.access-key=",
      "app.storage.secret-key="
    })
@AutoConfigureMockMvc
class MarketplaceIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired ProductService products;
  @Autowired PlatformTransactionManager tm;
  UUID owner, other, product;
  String session;

  @Test
  void configuredAdministratorDoesNotSeedDemoMarketplaceRecords() throws Exception {
    db.execute("TRUNCATE users CASCADE");
    new TransactionTemplate(tm)
        .executeWithoutResult(
            tx ->
                new Bootstrap(
                        db,
                        "operator@example.test",
                        "test-only-strong-password-123",
                        "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567",
                        "production",
                        "https://getlancer.example.test",
                        true)
                    .run(null));
    assertEquals(1, db.queryForObject("SELECT count(*) FROM users", Integer.class));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM products", Integer.class));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM teams", Integer.class));
    mvc.perform(get("/api/v1/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
    mvc.perform(get("/api/v1/teams"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
    mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
  }

  @BeforeEach
  void prepare() {
    db.execute("TRUNCATE users CASCADE");
    db.execute("TRUNCATE rate_buckets");
    owner = user("owner@example.com");
    other = user("other@example.com");
    product = product(owner, "ACTIVE", "PUBLIC");
    session = "integration-session";
    db.update(
        "INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",
        Support.hash(session),
        owner);
  }

  UUID user(String email) {
    UUID u = UUID.randomUUID();
    db.update(
        "INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",
        u,
        email);
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER')", u);
    db.update(
        "INSERT INTO"
            + " developer_profiles(user_id,slug,display_name,approval_status,availability_status)"
            + " VALUES(?,?,?,'APPROVED','AVAILABLE_NOW')",
        u,
        u.toString(),
        email.split("@")[0]);
    db.update("INSERT INTO showcase_entitlements(user_id) VALUES(?)", u);
    return u;
  }

  UUID product(UUID owner, String lifecycle, String visibility) {
    UUID p = UUID.randomUUID();
    db.update(
        "INSERT INTO"
            + " products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,approval_status,lifecycle_status,visibility)"
            + " VALUES(?,?,?,'Inventory','Inventory product','Inventory project"
            + " description','SAAS','Inventory','React','Built everything','APPROVED',?,?)",
        p,
        owner,
        p.toString(),
        lifecycle,
        visibility);
    return p;
  }

  @Test
  void v15AvailabilityAndSimilarBuildersRespectPublicScope() throws Exception {
    mvc.perform(
            put("/api/v1/developer/availability")
                .cookie(new Cookie("gl_session", session))
                .header("Origin", "http://localhost:3000")
                .header("X-Requested-With", "getlancer")
                .contentType("application/json")
                .content("{\"status\":\"LIMITED\"}"))
        .andExpect(status().isOk());
    assertEquals(
        "APPROVED",
        db.queryForObject(
            "SELECT approval_status FROM developer_profiles WHERE user_id=?", String.class, owner));
    assertNotNull(
        db.queryForObject(
            "SELECT availability_confirmed_at FROM developer_profiles WHERE user_id=?",
            java.sql.Timestamp.class,
            owner));
    UUID candidate = product(other, "ACTIVE", "PUBLIC");
    product(other, "ACTIVE", "PUBLIC");
    mvc.perform(get("/api/v1/products/" + product + "/similar-builders"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1));
    db.update(
        "UPDATE developer_profiles SET availability_status='NOT_ACCEPTING' WHERE user_id=?", other);
    mvc.perform(get("/api/v1/products/" + product + "/similar-builders"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0));
  }

  @Test
  void v15RepositoryProofIsScopedAndEarnedAwardsAreIdempotent() throws Exception {
    db.update(
        "UPDATE products SET repository_url='https://github.com/example/project' WHERE id=?",
        product);
    mvc.perform(postJson("/api/v1/developer/products/" + product + "/verification", "{}", session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"));
    String challenge =
        db.queryForObject(
            "SELECT challenge FROM repository_verifications WHERE product_id=?",
            String.class,
            product);
    mvc.perform(postJson("/api/v1/developer/products/" + product + "/verification", "{}", session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.challenge").value(challenge));
    db.update("UPDATE repository_verifications SET status='VERIFIED' WHERE product_id=?", product);
    db.update(
        "UPDATE products SET repository_url='https://github.com/example/different' WHERE id=?",
        product);
    mvc.perform(get("/api/v1/products/" + product))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.repositoryVerified").value(false));
    UUID completed = inquiry("COMPLETED");
    db.update("UPDATE inquiries SET client_email='other@example.com' WHERE id=?", completed);
    db.update(
        "INSERT INTO inquiry_events(id,inquiry_id,event_type,actor_type)"
            + " VALUES(?,?,'COMPLETED','CLIENT_TOKEN')",
        UUID.randomUUID(),
        completed);
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')", other);
    db.update(
        "INSERT INTO sessions(token_hash,user_id,expires_at,mfa_verified) VALUES(?,?,now()+interval"
            + " '1 hour',true)",
        Support.hash("trust-admin"),
        other);
    for (int n = 0; n < 2; n++)
      mvc.perform(
              postJson(
                  "/api/v1/admin/earned-capacity/" + completed,
                  "{\"reason\":\"Reviewed verified completion evidence\"}",
                  "trust-admin"))
          .andExpect(status().isOk());
    assertEquals(
        4,
        db.queryForObject(
            "SELECT active_slot_limit FROM showcase_entitlements WHERE user_id=?",
            Integer.class,
            owner));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT count(*) FROM earned_capacity_awards WHERE inquiry_id=?",
            Integer.class,
            completed));
  }

  @Test
  void publicReportsAcceptAnonymousExpiredRevokedAndSuspendedSessions() throws Exception {
    String body =
        "{\"targetType\":\"PRODUCT\",\"targetId\":\""
            + product
            + "\",\"reason\":\"APPEAL\",\"detail\":\"Please review this moderation decision.\"}";
    mvc.perform(postJson("/api/v1/reports", body, "")).andExpect(status().isCreated());
    mvc.perform(postJson("/api/v1/reports", body, "revoked-session"))
        .andExpect(status().isCreated());
    db.update("UPDATE sessions SET expires_at=now()-interval '1 hour' WHERE user_id=?", owner);
    mvc.perform(postJson("/api/v1/reports", body, session)).andExpect(status().isCreated());
    db.update("UPDATE sessions SET expires_at=now()+interval '1 hour' WHERE user_id=?", owner);
    db.update("UPDATE users SET account_status='SUSPENDED' WHERE id=?", owner);
    mvc.perform(postJson("/api/v1/reports", body, session)).andExpect(status().isCreated());
    mvc.perform(get("/api/v1/developer/products").cookie(new Cookie("gl_session", session)))
        .andExpect(status().isUnauthorized());
    assertEquals(
        4,
        db.queryForObject("SELECT count(*) FROM reports WHERE reporter_id IS NULL", Integer.class));
    db.update("UPDATE users SET account_status='ACTIVE' WHERE id=?", owner);
    mvc.perform(postJson("/api/v1/reports", body, session)).andExpect(status().isCreated());
    assertEquals(
        1,
        db.queryForObject(
            "SELECT count(*) FROM reports WHERE reporter_id=?", Integer.class, owner));
  }

  @Test
  void ownerStateFilteringHappensBeforePagination() throws Exception {
    for (int i = 0; i < 55; i++) product(owner, "DRAFT", "PUBLIC");
    mvc.perform(
            get("/api/v1/developer/products")
                .param("lifecycleStatus", "ACTIVE")
                .cookie(new Cookie("gl_session", session)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(product.toString()))
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.totalOwned").value(56));
    mvc.perform(
            get("/api/v1/developer/products")
                .param("lifecycleStatus", "DRAFT")
                .param("page", "1")
                .cookie(new Cookie("gl_session", session)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(5))
        .andExpect(jsonPath("$.totalItems").value(55));
    mvc.perform(
            get("/api/v1/developer/products")
                .param("lifecycleStatus", "INVALID")
                .cookie(new Cookie("gl_session", session)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void visibleSaveStateCanBeQueriedBeyondFirstPage() throws Exception {
    db.update(
        "INSERT INTO saved_products(user_id,product_id,created_at) VALUES(?,?,now()-interval '1"
            + " day')",
        owner,
        product);
    for (int i = 0; i < 55; i++) {
      UUID saved = product(other, "ACTIVE", "PUBLIC");
      db.update("INSERT INTO saved_products(user_id,product_id) VALUES(?,?)", owner, saved);
    }
    mvc.perform(
            get("/api/v1/me/saved-products")
                .param("ids", product.toString())
                .cookie(new Cookie("gl_session", session)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(product.toString()));
    mvc.perform(
            get("/api/v1/me/saved-products")
                .param("ids", "invalid")
                .cookie(new Cookie("gl_session", session)))
        .andExpect(status().isBadRequest());
  }

  @Autowired ModerationService moderation;

  @Test
  void guestEngagementAppealsRequireBuilderOwnershipOrVerifiedClient() throws Exception {
    UUID inquiry = inquiry("COMPLETED"), review = UUID.randomUUID();
    db.update("UPDATE inquiries SET client_email='guest@example.com' WHERE id=?", inquiry);
    db.update(
        "INSERT INTO reviews(id,inquiry_id,developer_user_id,rating,review_text)"
            + " VALUES(?,?,?,5,'The work was completed as agreed.')",
        review,
        inquiry,
        owner);
    UUID inquiryDecision = UUID.randomUUID(), reviewDecision = UUID.randomUUID();
    db.update(
        "INSERT INTO moderation_actions(id,admin_id,target_type,target_id,action,reason)"
            + " VALUES(?,?,'INQUIRY',?,'QUARANTINE','Reported by a participant')",
        inquiryDecision,
        owner,
        inquiry);
    db.update(
        "INSERT INTO moderation_actions(id,admin_id,target_type,target_id,action,reason)"
            + " VALUES(?,?,'REVIEW',?,'HIDE','Reported by a participant')",
        reviewDecision,
        owner,
        review);
    for (UUID decision : List.of(inquiryDecision, reviewDecision))
      mvc.perform(
              postJson(
                  "/api/v1/appeals",
                  "{\"decisionId\":\""
                      + decision
                      + "\",\"statement\":\"Please reconsider this engagement decision.\"}",
                  session))
          .andExpect(status().isCreated());
    db.update("UPDATE inquiries SET client_email='other@example.com' WHERE id=?", inquiry);
    String client = clientSession();
    db.update("UPDATE users SET email_verified_at=NULL WHERE id=?", other);
    mvc.perform(get("/api/v1/me/moderation-decisions").cookie(new Cookie("gl_session", client)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0));
    for (UUID decision : List.of(inquiryDecision, reviewDecision))
      mvc.perform(
              postJson(
                  "/api/v1/appeals",
                  "{\"decisionId\":\""
                      + decision
                      + "\",\"statement\":\"Please reconsider this engagement decision.\"}",
                  client))
          .andExpect(status().isNotFound());
    db.update("UPDATE users SET email_verified_at=now() WHERE id=?", other);
    mvc.perform(get("/api/v1/me/moderation-decisions").cookie(new Cookie("gl_session", client)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2));
    assertTrue(moderation.affected(Map.of("target_type", "INQUIRY", "target_id", inquiry), other));
    assertTrue(moderation.affected(Map.of("target_type", "REVIEW", "target_id", review), other));
  }

  @Test
  void accountClosureRequiresExplicitEmailConfirmationAndRevokesSessions() throws Exception {
    mvc.perform(
            delete("/api/v1/me")
                .cookie(new Cookie("gl_session", session))
                .header("Origin", "http://localhost:3000")
                .header("X-Requested-With", "getlancer"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("EMAIL_CONFIRMATION_REQUIRED"));
    assertEquals(
        "ACTIVE",
        db.queryForObject("SELECT account_status FROM users WHERE id=?", String.class, owner));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT count(*) FROM deletion_requests WHERE user_id=?", Integer.class, owner));
    String raw = "closure-confirmation-test-token";
    db.update(
        "UPDATE account_tokens SET token_hash=? WHERE user_id=? AND kind='ACCOUNT_DELETION' AND"
            + " used_at IS NULL",
        Support.hash(raw),
        owner);
    String body = "{\"token\":\"" + raw + "\",\"decision\":\"ACCEPT\"}";
    mvc.perform(postJson("/api/v1/auth/confirm", body, "")).andExpect(status().isBadRequest());
    db.update(
        "UPDATE account_tokens SET expires_at=now()-interval '1 minute' WHERE token_hash=?",
        Support.hash(raw));
    mvc.perform(postJson("/api/v1/auth/confirm", body.replace("ACCEPT", "DELETE"), ""))
        .andExpect(status().isGone());
    db.update(
        "UPDATE account_tokens SET expires_at=now()+interval '5 minutes' WHERE token_hash=?",
        Support.hash(raw));
    for (int i = 0; i < 2; i++)
      mvc.perform(postJson("/api/v1/auth/confirm", body.replace("ACCEPT", "DELETE"), ""))
          .andExpect(status().isOk());
    assertEquals(
        "DELETED",
        db.queryForObject("SELECT account_status FROM users WHERE id=?", String.class, owner));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT count(*) FROM deletion_requests WHERE user_id=?", Integer.class, owner));
    assertEquals(
        0,
        db.queryForObject("SELECT count(*) FROM sessions WHERE user_id=?", Integer.class, owner));
    mvc.perform(get("/api/v1/products/" + product)).andExpect(status().isNotFound());
  }

  @Test
  void onlyOneAdministratorCanExist() {
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')", owner);
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () -> db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')", other));
    assertEquals(
        1, db.queryForObject("SELECT count(*) FROM user_roles WHERE role='ADMIN'", Integer.class));
  }

  @Test
  void administratorEmailCannotBeClaimedByPublicSignup() throws Exception {
    mvc.perform(
            post("/api/v1/auth/signup")
                .header("Origin", "http://localhost:3000")
                .header("X-Requested-With", "getlancer")
                .contentType("application/json")
                .content(
                    "{\"email\":\"jetychodipilli@gmail.com\",\"password\":\"test-password-long-enough\",\"displayName\":\"Attempted"
                        + " signup\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("EMAIL_UNAVAILABLE"));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT count(*) FROM users WHERE email='jetychodipilli@gmail.com'", Integer.class));
  }

  @Test
  void privateAndArchivedNeverPublic() throws Exception {
    UUID privateId = product(owner, "ACTIVE", "PRIVATE_CASE_STUDY"),
        archived = product(owner, "ARCHIVED", "PUBLIC");
    mvc.perform(get("/api/v1/products/" + privateId)).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/products/" + archived)).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1));
  }

  @Test
  void publicProductShowsOnlyPublishedEligibleReviewSummaryAndHidesModerationNotes()
      throws Exception {
    db.update(
        "UPDATE products SET moderation_reason='Private moderation evidence' WHERE id=?", product);
    UUID published = inquiry("COMPLETED"),
        second = inquiry("COMPLETED"),
        held = inquiry("COMPLETED"),
        restricted = inquiry("COMPLETED");
    for (UUID id : List.of(published, second, held, restricted))
      db.update(
          "INSERT INTO"
              + " reviews(id,inquiry_id,developer_user_id,rating,review_text,moderation_status)"
              + " VALUES(?,?,?,?,?,?)",
          UUID.randomUUID(),
          id,
          owner,
          id.equals(published) ? 5 : id.equals(second) ? 3 : 1,
          "The agreed delivery was completed.",
          id.equals(held) ? "HELD_FOR_REVIEW" : "PUBLISHED");
    db.update("UPDATE inquiries SET moderation_status='QUARANTINED' WHERE id=?", restricted);
    mvc.perform(get("/api/v1/products/" + product))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reviewSummary.reviewCount").value(2))
        .andExpect(jsonPath("$.reviewSummary.averageRating").value(4.0))
        .andExpect(jsonPath("$.moderationReason").doesNotExist());
    mvc.perform(get("/api/v1/products"))
        .andExpect(jsonPath("$.items[0].moderationReason").doesNotExist());
    mvc.perform(get("/api/v1/developer/products").cookie(new Cookie("gl_session", session)))
        .andExpect(jsonPath("$.items[0].moderationReason").value("Private moderation evidence"));
    db.update(
        "UPDATE reviews SET moderation_status='HIDDEN' WHERE inquiry_id IN (?,?)",
        published,
        second);
    mvc.perform(get("/api/v1/products/" + product))
        .andExpect(jsonPath("$.reviewSummary.reviewCount").value(0))
        .andExpect(jsonPath("$.reviewSummary.averageRating").isEmpty());
  }

  @Test
  void inquiryKeyIsOptionalButDuplicateAndMalformedRequestsRemainRejected() throws Exception {
    String body =
        "{\"referenceProductId\":\""
            + product
            + "\",\"clientName\":\"Alex\",\"clientEmail\":\"alex@example.com\",\"description\":\"I"
            + " need inventory software for five"
            + " stores.\",\"budgetBand\":\"USD_3K_10K\",\"timelineBand\":\"ONE_TO_THREE_MONTHS\",\"requestType\":\"SIMILAR_BUILD\"}";
    mvc.perform(postJson("/api/v1/inquiries", body, "")).andExpect(status().isCreated());
    mvc.perform(postJson("/api/v1/inquiries", body, ""))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("DUPLICATE_INQUIRY"));
    mvc.perform(postJson("/api/v1/inquiries", body, "").header("Idempotency-Key", "invalid"))
        .andExpect(status().isBadRequest());
    assertEquals(1, db.queryForObject("SELECT count(*) FROM inquiries", Integer.class));
  }

  @Test
  void normalUserCannotModerate() throws Exception {
    mvc.perform(get("/api/v1/admin/products/pending").cookie(new Cookie("gl_session", session)))
        .andExpect(status().isForbidden());
  }

  @Test
  void cannotEditOthersProduct() throws Exception {
    UUID p = product(other, "DRAFT", "PUBLIC");
    mvc.perform(
            post("/api/v1/developer/products/" + p + "/archive")
                .cookie(new Cookie("gl_session", session))
                .header("Origin", "http://localhost:3000")
                .header("X-Requested-With", "getlancer"))
        .andExpect(status().isNotFound());
  }

  @Test
  void csrfMutationRejected() throws Exception {
    mvc.perform(
            post("/api/v1/developer/products/" + product + "/archive")
                .cookie(new Cookie("gl_session", session)))
        .andExpect(status().isForbidden());
  }

  @Test
  void atomicSlotLimit() throws Exception {
    product(owner, "ACTIVE", "PUBLIC");
    UUID a = product(owner, "DRAFT", "PUBLIC"), b = product(owner, "DRAFT", "PUBLIC");
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
    List<Future<Boolean>> futures = new ArrayList<>();
    for (UUID p : List.of(a, b))
      futures.add(
          pool.submit(
              () -> {
                ready.countDown();
                go.await();
                try {
                  new TransactionTemplate(tm)
                      .executeWithoutResult(s -> products.activate(p, owner, true));
                  return true;
                } catch (ApiError e) {
                  assertEquals("SLOT_LIMIT_REACHED", e.code);
                  return false;
                }
              }));
    ready.await();
    go.countDown();
    int successes = 0;
    for (var f : futures) if (f.get(10, TimeUnit.SECONDS)) successes++;
    pool.shutdown();
    assertEquals(1, successes);
    assertEquals(
        3,
        db.queryForObject(
            "SELECT count(*) FROM products WHERE owner_user_id=? AND lifecycle_status='ACTIVE'",
            Integer.class,
            owner));
  }

  @Test
  void inquiryPersistsBeforeQualificationAndCannotLeak() throws Exception {
    String key = UUID.randomUUID().toString();
    String body =
        "{\"referenceProductId\":\""
            + product
            + "\",\"clientName\":\"Alex\",\"clientEmail\":\"alex@example.com\",\"description\":\"I"
            + " need inventory software for five"
            + " stores.\",\"budgetBand\":\"USD_3K_10K\",\"timelineBand\":\"ONE_TO_THREE_MONTHS\",\"requestType\":\"SIMILAR_BUILD\"}";
    for (int n = 0; n < 2; n++)
      mvc.perform(
              post("/api/v1/inquiries")
                  .header("Origin", "http://localhost:3000")
                  .header("X-Requested-With", "getlancer")
                  .header("Idempotency-Key", key)
                  .contentType("application/json")
                  .content(body))
          .andExpect(status().isCreated());
    assertEquals(1, db.queryForObject("SELECT count(*) FROM inquiries", Integer.class));
    assertEquals(
        "CREATED_UNVERIFIED",
        db.queryForObject("SELECT current_status FROM inquiries", String.class));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM notifications", Integer.class));
    mvc.perform(get("/api/v1/developer/inquiries").cookie(new Cookie("gl_session", session)))
        .andExpect(jsonPath("$.items.length()").value(0));
  }

  @Test
  void expiredTokenRejected() throws Exception {
    db.update(
        "INSERT INTO account_tokens(id,token_hash,kind,user_id,email,expires_at)"
            + " VALUES(?,?,'EMAIL_VERIFICATION',?,'owner@example.com',now()-interval '1 hour')",
        UUID.randomUUID(),
        Support.hash("expired-confirmation-token"),
        owner);
    mvc.perform(
            post("/api/v1/auth/confirm")
                .header("Origin", "http://localhost:3000")
                .header("X-Requested-With", "getlancer")
                .contentType("application/json")
                .content("{\"token\":\"expired-confirmation-token\"}"))
        .andExpect(status().isGone());
  }

  @Test
  void rules() throws Exception {
    RulesSmoke.main(new String[0]);
  }

  @Test
  void publicSearchUsesFiltersAndPagination() throws Exception {
    product(owner, "ACTIVE", "PUBLIC");
    mvc.perform(
            get("/api/v1/products")
                .param("q", "inventory")
                .param("technology", "React")
                .param("projectType", "SAAS")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(2))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.totalPages").value(2));
    mvc.perform(get("/api/v1/products").param("technology", "Vue"))
        .andExpect(jsonPath("$.totalItems").value(0));
  }

  UUID inquiry(String state) {
    UUID id = UUID.randomUUID();
    db.update(
        "INSERT INTO"
            + " inquiries(id,reference_product_id,developer_user_id,client_email,client_name,request_type,description,budget_band,timeline_band,idempotency_key,request_hash,current_status,email_confirmed_at)"
            + " VALUES(?,?,?,'other@example.com','Client','SIMILAR_BUILD','Build an inventory"
            + " system for our stores.','USD_3K_10K','ONE_TO_THREE_MONTHS',?,'fixture',?,CASE WHEN"
            + " ?='CREATED_UNVERIFIED' THEN NULL ELSE now() END)",
        id,
        product,
        owner,
        UUID.randomUUID(),
        state,
        state);
    return id;
  }

  String clientSession() {
    String token = "verified-client-session";
    db.update(
        "INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",
        Support.hash(token),
        other);
    return token;
  }

  @Test
  void archivedProductCannotQualifyAnOldInquiry() throws Exception {
    UUID id = inquiry("CREATED_UNVERIFIED");
    String token = "confirmation-" + UUID.randomUUID();
    db.update(
        "INSERT INTO account_tokens(id,token_hash,kind,inquiry_id,email,expires_at)"
            + " VALUES(?,?,'CLIENT_INQUIRY_CONFIRMATION',?,'other@example.com',now()+interval '1"
            + " hour')",
        UUID.randomUUID(),
        Support.hash(token),
        id);
    db.update("UPDATE products SET lifecycle_status='ARCHIVED' WHERE id=?", product);
    mvc.perform(
            post("/api/v1/auth/confirm")
                .header("Origin", "http://localhost:3000")
                .header("X-Requested-With", "getlancer")
                .contentType("application/json")
                .content("{\"token\":\"" + token + "\"}"))
        .andExpect(status().isConflict());
    assertEquals(
        "CREATED_UNVERIFIED",
        db.queryForObject("SELECT current_status FROM inquiries WHERE id=?", String.class, id));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM notifications", Integer.class));
  }

  @Test
  void developerAccountCanAlsoActAsClientWithoutSeeingOthersRequests() throws Exception {
    UUID id = inquiry("HIRE_PENDING_CONFIRMATION");
    String client = clientSession();
    mvc.perform(get("/api/v1/me/inquiries").cookie(new Cookie("gl_session", client)))
        .andExpect(jsonPath("$.items.length()").value(1));
    mvc.perform(get("/api/v1/me/inquiries/" + id).cookie(new Cookie("gl_session", session)))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/me/inquiries/" + id + "/decision")
                .cookie(new Cookie("gl_session", client))
                .header("Origin", "http://localhost:3000")
                .header("X-Requested-With", "getlancer")
                .contentType("application/json")
                .content("{\"kind\":\"HIRE_CONFIRMATION\",\"decision\":\"REJECT\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISCUSSION"));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT count(*) FROM inquiry_events WHERE event_type='HIRED'", Integer.class));
  }

  @Test
  void confirmedCompletionMakesOneModeratedReviewEligible() throws Exception {
    UUID id = inquiry("COMPLETION_PENDING_CONFIRMATION");
    String client = clientSession();
    for (int attempt = 0; attempt < 2; attempt++)
      mvc.perform(
              post("/api/v1/me/inquiries/" + id + "/decision")
                  .cookie(new Cookie("gl_session", client))
                  .header("Origin", "http://localhost:3000")
                  .header("X-Requested-With", "getlancer")
                  .contentType("application/json")
                  .content("{\"kind\":\"COMPLETION_CONFIRMATION\",\"decision\":\"ACCEPT\"}"))
          .andExpect(status().isOk());
    assertEquals(
        1,
        db.queryForObject(
            "SELECT count(*) FROM inquiry_events WHERE inquiry_id=? AND event_type='COMPLETED'",
            Integer.class,
            id));
    for (int attempt = 0; attempt < 2; attempt++)
      mvc.perform(
              post("/api/v1/me/inquiries/" + id + "/review")
                  .cookie(new Cookie("gl_session", client))
                  .header("Origin", "http://localhost:3000")
                  .header("X-Requested-With", "getlancer")
                  .contentType("application/json")
                  .content(
                      "{\"rating\":4,\"reviewText\":\"Clear communication and the agreed work was"
                          + " completed.\"}"))
          .andExpect(status().isOk());
    assertEquals(
        1, db.queryForObject("SELECT count(*) FROM reviews WHERE inquiry_id=?", Integer.class, id));
    assertEquals(
        "HELD_FOR_REVIEW",
        db.queryForObject(
            "SELECT moderation_status FROM reviews WHERE inquiry_id=?", String.class, id));
  }

  @Test
  void cannotReviewAnUncompletedEngagement() throws Exception {
    UUID id = inquiry("HIRED");
    String client = clientSession();
    mvc.perform(
            post("/api/v1/me/inquiries/" + id + "/review")
                .cookie(new Cookie("gl_session", client))
                .header("Origin", "http://localhost:3000")
                .header("X-Requested-With", "getlancer")
                .contentType("application/json")
                .content("{\"rating\":5,\"reviewText\":\"This has not been completed yet.\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void accountEmailMustBeVerifiedToClaimRequests() throws Exception {
    inquiry("INQUIRY_RECEIVED");
    String client = clientSession();
    db.update("UPDATE users SET email_verified_at=NULL WHERE id=?", other);
    mvc.perform(get("/api/v1/me/inquiries").cookie(new Cookie("gl_session", client)))
        .andExpect(status().isForbidden());
  }

  @Test
  void exportEnforcesQualificationForRecipientAndPreservesClientOwnership() throws Exception {
    UUID hidden = inquiry("CREATED_UNVERIFIED"), visible = inquiry("INQUIRY_RECEIVED");
    mvc.perform(get("/api/v1/me/export").cookie(new Cookie("gl_session", session)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requests.length()").value(1))
        .andExpect(jsonPath("$.requests[0].id").value(visible.toString()));
    mvc.perform(get("/api/v1/me/export").cookie(new Cookie("gl_session", clientSession())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requests.length()").value(2));
    mvc.perform(
            get("/api/v1/developer/inquiries/" + hidden).cookie(new Cookie("gl_session", session)))
        .andExpect(status().isNotFound());
    db.update("UPDATE inquiries SET moderation_status='BLOCKED' WHERE id=?", visible);
    mvc.perform(get("/api/v1/me/export").cookie(new Cookie("gl_session", session)))
        .andExpect(jsonPath("$.requests.length()").value(0));
    mvc.perform(get("/api/v1/developer/analytics").cookie(new Cookie("gl_session", session)))
        .andExpect(jsonPath("$.qualifiedInquiries").value(0));
  }

  @Test
  void builderCanReachDraftsBeyondFirstHundred() throws Exception {
    for (int i = 0; i < 105; i++) product(owner, "DRAFT", "PUBLIC");
    mvc.perform(
            get("/api/v1/developer/products")
                .param("size", "100")
                .cookie(new Cookie("gl_session", session)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(100))
        .andExpect(jsonPath("$.hasMore").value(true));
    mvc.perform(
            get("/api/v1/developer/products")
                .param("size", "100")
                .param("page", "1")
                .cookie(new Cookie("gl_session", session)))
        .andExpect(jsonPath("$.items.length()").value(6))
        .andExpect(jsonPath("$.hasMore").value(false));
  }

  @Test
  void privateAccessCanBeRevokedWithoutPublishingProject() throws Exception {
    UUID id = product(owner, "ACTIVE", "PRIVATE_CASE_STUDY");
    String client = clientSession();
    mvc.perform(get("/api/v1/private/products/" + id).cookie(new Cookie("gl_session", client)))
        .andExpect(status().isNotFound());
    db.update(
        "INSERT INTO product_access_grants(product_id,client_email,expires_at)"
            + " VALUES(?,'other@example.com',now()+interval '1 hour')",
        id);
    mvc.perform(get("/api/v1/private/products/" + id).cookie(new Cookie("gl_session", client)))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/products/" + id)).andExpect(status().isNotFound());
    db.update("DELETE FROM product_access_grants WHERE product_id=?", id);
    mvc.perform(get("/api/v1/private/products/" + id).cookie(new Cookie("gl_session", client)))
        .andExpect(status().isNotFound());
  }

  @Test
  void expiryJobDoesNotExpireRenewedConfirmation() throws Exception {
    UUID expired = inquiry("CREATED_UNVERIFIED"), renewed = inquiry("CREATED_UNVERIFIED");
    db.update("UPDATE inquiries SET updated_at=now()-interval '2 days'");
    db.update(
        "INSERT INTO account_tokens(id,token_hash,kind,inquiry_id,email,expires_at)"
            + " VALUES(?,?,'CLIENT_INQUIRY_CONFIRMATION',?,'other@example.com',now()+interval '1"
            + " hour')",
        UUID.randomUUID(),
        Support.hash("renewed"),
        renewed);
    var maintenance = new Maintenance(db);
    org.springframework.test.util.ReflectionTestUtils.setField(maintenance, "enabled", true);
    new TransactionTemplate(tm).executeWithoutResult(tx -> maintenance.expire());
    assertEquals(
        "EXPIRED",
        db.queryForObject(
            "SELECT current_status FROM inquiries WHERE id=?", String.class, expired));
    assertEquals(
        "CREATED_UNVERIFIED",
        db.queryForObject(
            "SELECT current_status FROM inquiries WHERE id=?", String.class, renewed));
  }

  String adminSession() {
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')", owner);
    db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?", owner);
    return session;
  }

  org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postJson(
      String path, String body, String token) {
    return post(path)
        .cookie(new Cookie("gl_session", token))
        .header("Origin", "http://localhost:3000")
        .header("X-Requested-With", "getlancer")
        .contentType("application/json")
        .content(body);
  }

  @Test
  void documentedSaveContractIsIdempotentNoContent() throws Exception {
    for (int i = 0; i < 2; i++)
      mvc.perform(postJson("/api/v1/products/" + product + "/save", "{}", session))
          .andExpect(status().isNoContent());
    assertEquals(1, db.queryForObject("SELECT count(*) FROM saved_products", Integer.class));
  }

  @Test
  void documentedConfirmationAliasAcceptsClientSession() throws Exception {
    UUID id = inquiry("HIRE_PENDING_CONFIRMATION");
    mvc.perform(postJson("/api/v1/inquiries/" + id + "/confirm-hire", "{}", clientSession()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("HIRED"));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT count(*) FROM inquiry_events WHERE inquiry_id=? AND event_type='HIRED'",
            Integer.class,
            id));
  }

  @Test
  void clientCanCloseViaDocumentedContract() throws Exception {
    UUID id = inquiry("DISCUSSION");
    mvc.perform(postJson("/api/v1/inquiries/" + id + "/not-hired", "{}", clientSession()))
        .andExpect(status().isOk());
    assertEquals(
        "NOT_HIRED",
        db.queryForObject("SELECT current_status FROM inquiries WHERE id=?", String.class, id));
  }

  @Test
  void proposalValueIsPrivateAndValidated() throws Exception {
    UUID id = inquiry("DISCUSSION");
    mvc.perform(
            postJson(
                "/api/v1/inquiries/" + id + "/proposal-sent",
                "{\"reportedValue\":1234.56,\"currency\":\"USD\"}",
                session))
        .andExpect(status().isOk());
    assertEquals(
        "1234.56",
        db.queryForObject(
                "SELECT reported_value FROM inquiries WHERE id=?", java.math.BigDecimal.class, id)
            .toString());
    mvc.perform(get("/api/v1/products/" + product))
        .andExpect(jsonPath("$.reportedValue").doesNotExist());
  }

  @Test
  void reportTriageRequiresAdminAndRespectsResolvedState() throws Exception {
    UUID report = UUID.randomUUID();
    db.update(
        "INSERT INTO reports(id,target_type,target_id,reason,detail)"
            + " VALUES(?,'PRODUCT',?,'SPAM','Repeated misleading content')",
        report,
        product);
    String body =
        "{\"severity\":\"HIGH\",\"status\":\"UNDER_REVIEW\",\"reason\":\"Investigating repeated"
            + " spam\"}";
    mvc.perform(postJson("/api/v1/admin/reports/" + report + "/triage", body, session))
        .andExpect(status().isForbidden());
    adminSession();
    mvc.perform(postJson("/api/v1/admin/reports/" + report + "/triage", body, session))
        .andExpect(status().isOk());
    db.update("UPDATE reports SET status='RESOLVED' WHERE id=?", report);
    mvc.perform(postJson("/api/v1/admin/reports/" + report + "/triage", body, session))
        .andExpect(status().isConflict());
  }

  @Test
  void dataApiRolesHaveNoApplicationGrants() {
    assertEquals(
        0,
        db.queryForObject(
            "SELECT count(*) FROM information_schema.role_table_grants WHERE"
                + " table_schema=current_schema() AND grantee IN ('anon','authenticated','PUBLIC')"
                + " AND table_name IN ('users','inquiries','sessions','product_media')",
            Integer.class));
    assertEquals(
        4,
        db.queryForObject(
            "SELECT count(*) FROM pg_tables WHERE schemaname=current_schema() AND tablename IN"
                + " ('users','inquiries','sessions','product_media') AND rowsecurity",
            Integer.class));
  }

  @Test
  void proofUploadRequiresOwnerAndConfiguredStorage() throws Exception {
    UUID draft = product(other, "DRAFT", "PUBLIC");
    mvc.perform(
            postJson(
                "/api/v1/developer/products/" + draft + "/media/upload-request",
                "{\"filename\":\"proof.png\",\"contentType\":\"image/png\",\"sizeBytes\":100}",
                session))
        .andExpect(status().isNotFound());
  }

  @Test
  void galleryPaginationHasContractTotals() throws Exception {
    mvc.perform(get("/api/v1/developer/products").cookie(new Cookie("gl_session", session)))
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.totalPages").value(1));
  }

  @Test
  void reviewIdentityDefaultsPrivateAndNamedChoiceIsExplicit() throws Exception {
    String client = clientSession();
    for (String visibility : List.of("ANONYMOUS", "NAMED")) {
      UUID id = inquiry("COMPLETED");
      db.update(
          "INSERT INTO inquiry_events(id,inquiry_id,event_type,actor_type)"
              + " VALUES(?,?,'COMPLETED','CLIENT')",
          UUID.randomUUID(),
          id);
      String body =
          "{\"rating\":5,\"reviewText\":\"Delivered the software we agreed on.\",\"visibility\":\""
              + visibility
              + "\"}";
      mvc.perform(postJson("/api/v1/inquiries/" + id + "/review", body, client))
          .andExpect(status().isCreated());
      assertEquals(
          visibility,
          db.queryForObject("SELECT visibility FROM reviews WHERE inquiry_id=?", String.class, id));
      db.update("UPDATE reviews SET moderation_status='PUBLISHED' WHERE inquiry_id=?", id);
    }
    mvc.perform(get("/api/v1/builders/" + owner + "/reviews"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(
            jsonPath(
                "$.items[*].clientName",
                org.hamcrest.Matchers.containsInAnyOrder("Client", "Verified client")))
        .andExpect(jsonPath("$.items[*].clientEmail").isEmpty());
  }

  @Test
  void invalidReviewIdentityDoesNotInsert() throws Exception {
    UUID id = inquiry("COMPLETED");
    db.update(
        "INSERT INTO inquiry_events(id,inquiry_id,event_type,actor_type)"
            + " VALUES(?,?,'COMPLETED','CLIENT')",
        UUID.randomUUID(),
        id);
    mvc.perform(
            postJson(
                "/api/v1/inquiries/" + id + "/review",
                "{\"rating\":5,\"reviewText\":\"Good work delivered as"
                    + " agreed.\",\"visibility\":\"PUBLIC_EMAIL\"}",
                clientSession()))
        .andExpect(status().isBadRequest());
    assertEquals(
        0, db.queryForObject("SELECT count(*) FROM reviews WHERE inquiry_id=?", Integer.class, id));
  }

  @Test
  void bootstrapCreatesOneAdminAndKeepsPasswordOnRestart() {
    String email = "seed-admin@example.com", password = "test-administrator-password";
    var bootstrap =
        new Bootstrap(
            db,
            email,
            password,
            "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP",
            "local",
            "http://localhost:3000",
            false);
    var tx = new TransactionTemplate(tm);
    tx.executeWithoutResult(status -> bootstrap.run(null));
    String stored =
        db.queryForObject("SELECT password_hash FROM users WHERE email=?", String.class, email);
    assertTrue(
        new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
            .matches(password, stored));
    tx.executeWithoutResult(
        status ->
            new Bootstrap(
                    db,
                    email,
                    "changed-test-password-long",
                    "KRUGS4ZANFZSAYJAON2HE2LOM4QHI2DF",
                    "local",
                    "http://localhost:3000",
                    false)
                .run(null));
    assertEquals(
        stored,
        db.queryForObject("SELECT password_hash FROM users WHERE email=?", String.class, email));
    assertEquals(
        1, db.queryForObject("SELECT count(*) FROM user_roles WHERE role='ADMIN'", Integer.class));
  }
}
