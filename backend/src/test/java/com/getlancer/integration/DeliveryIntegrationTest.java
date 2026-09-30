package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@org.springframework.test.context.ContextConfiguration(initializers = TestDatabaseGuard.class)
@SpringBootTest(properties = {
    "app.environment=local", "app.jobs-enabled=false", "app.admin-email=admin@example.test", "app.admin-password=", "app.admin-totp=", "spring.config.import=",
    "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}",
    "spring.datasource.username=${TEST_DB_USERNAME:postgres}", "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.hikari.schema=getlancer_test", "spring.flyway.default-schema=getlancer_test", "spring.flyway.schemas=getlancer_test",
    "app.origin=http://localhost:3000", "app.secure-cookie=false", "app.storage.access-key=", "app.storage.secret-key=", "app.rate-limit=1000"
})
@AutoConfigureMockMvc
class DeliveryIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  UUID buyer, seller, outsider, manager, admin, inquiry, business, brief, team, product;

  UUID user(String name, boolean approved) {
    UUID user = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())", user, name + "@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT'),(?,'DEVELOPER')", user, user);
    db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status,availability_status) VALUES(?,?,?,?,'AVAILABLE_NOW')", user, name, name, approved ? "APPROVED" : "DRAFT");
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')", Support.hash(name), user);
    return user;
  }
  MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String actor) {
    return request.cookie(new Cookie("gl_session", actor));
  }
  MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, String actor, Object value) throws Exception {
    return as(request, actor).header("Origin", "http://localhost:3000").header("X-Requested-With", "getlancer").contentType("application/json").content(json.writeValueAsString(value));
  }
  UUID createInquiry(String actor) throws Exception {
    var result = mvc.perform(body(post("/api/v1/engagements"), actor, Map.of("inquiryId", inquiry))).andExpect(status().isOk()).andReturn();
    return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }
  UUID createBusiness(String actor, String kind, UUID candidate) throws Exception {
    var result = mvc.perform(body(post("/api/v1/engagements"), actor, Map.of("businessRequestId", brief, "candidateKind", kind, "candidateId", candidate))).andExpect(status().isOk()).andReturn();
    return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }
  String route(UUID engagement) { return "/api/v1/engagements/" + engagement; }
  Map<String, Object> proposal(Object amount) {
    return Map.of("scope", "Build the customer approval workflow with accessible delivery screens.", "terms", "Acceptance follows the documented checks. Payment follows acceptance.", "milestones", List.of(Map.of("title", "Customer approvals", "description", "Deliver and demonstrate the agreed approval workflow.", "amountMinor", amount, "dueDate", "2030-12-31")));
  }
  UUID draft(UUID engagement) throws Exception {
    var result = mvc.perform(body(post(route(engagement) + "/proposals"), "seller", proposal(150000))).andExpect(status().isOk()).andReturn();
    return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }
  void action(UUID engagement, UUID proposal, String action, String actor, Object value) throws Exception {
    mvc.perform(body(post(route(engagement) + "/proposals/" + proposal + "/" + action), actor, value)).andExpect(status().isOk());
  }
  UUID agreed(UUID engagement) throws Exception {
    UUID proposal = draft(engagement);
    action(engagement, proposal, "send", "seller", Map.of("consent", true));
    action(engagement, proposal, "accept", "buyer", Map.of("consent", true));
    return db.queryForObject("SELECT id FROM delivery_milestones WHERE engagement_id=?", UUID.class, engagement);
  }
  void milestone(UUID engagement, UUID milestone, String action, String actor, Object value) throws Exception {
    mvc.perform(body(post(route(engagement) + "/milestones/" + milestone + "/" + action), actor, value)).andExpect(status().isOk());
  }
  void accepted(UUID engagement, UUID milestone) throws Exception {
    milestone(engagement, milestone, "start", "seller", Map.of());
    milestone(engagement, milestone, "submit", "seller", Map.of("deliveryNote", "Accessible approval workflow and its checks delivered.", "deliveryUrl", "https://example.test/delivery"));
    milestone(engagement, milestone, "accept", "buyer", Map.of());
  }
  UUID captured(UUID milestone, String mode, long refund) {
    // Test-only records isolate completion policy; these never assert actual provider money movement.
    UUID payment = UUID.randomUUID();
    db.update("INSERT INTO payment_attempts(id,milestone_id,payer_user_id,idempotency_key,amount_minor,account_id,mode,status,order_id,payment_id,refunded_minor) VALUES(?,?,?,?,150000,'acc_test_fixture',?,'CAPTURED',?,?,?)", payment, milestone, buyer, UUID.randomUUID(), mode, "order_" + payment.toString().replace("-", ""), "pay_" + payment.toString().replace("-", ""), refund);
    return payment;
  }

  @BeforeEach
  void prepare() {
    db.execute("TRUNCATE users CASCADE"); db.execute("TRUNCATE rate_buckets");
    buyer = user("buyer", false); seller = user("seller", true); outsider = user("outsider", false); manager = user("manager", true); admin = user("admin", false);
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')", admin);
    db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?", admin);
    product = UUID.randomUUID();
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,approval_status,lifecycle_status,visibility) VALUES(?,?,?,'Portal','Customer portal','Working portal','SAAS','CRM','React, Java','Built it','APPROVED','ACTIVE','PUBLIC')", product, seller, product.toString());
    inquiry = UUID.randomUUID();
    db.update("INSERT INTO inquiries(id,reference_product_id,developer_user_id,client_email,client_name,request_type,description,budget_band,timeline_band,idempotency_key,request_hash,current_status,email_confirmed_at) VALUES(?,?,?,'buyer@example.test','Buyer','CUSTOMIZE','Customer approval workflow','NEED_ESTIMATE','FLEXIBLE',?,'fixture','DISCUSSION',now())", inquiry, product, seller, UUID.randomUUID());
    business = UUID.randomUUID();
    db.update("INSERT INTO businesses(id,name,summary,owner_id) VALUES(?,'Customer business','Private commercial workspace',?)", business, buyer);
    db.update("INSERT INTO business_members(business_id,user_id,role) VALUES(?,?,'OWNER'),(?,?,'HIRING_MANAGER')", business, buyer, business, manager);
    brief = UUID.randomUUID();
    db.update("INSERT INTO business_requests(id,business_id,created_by,title,description,category,technology,budget,timeline,status) VALUES(?,?,?,'Customer approvals','Private approval workflow','CRM','React','1500 INR','One month','OPEN')", brief, business, buyer);
    team = UUID.randomUUID();
    db.update("INSERT INTO teams(id,slug,name,summary,availability,project_range,owner_id) VALUES(?,?,'Delivery studio','Customer software studio','AVAILABLE_NOW','1500 INR',?)", team, team.toString(), seller);
    db.update("INSERT INTO team_members(team_id,user_id,role,membership_type) VALUES(?,?,'OWNER','PERMANENT'),(?,?,'BUSINESS_MANAGER','PERMANENT')", team, seller, team, manager);
    db.update("INSERT INTO team_projects(team_id,product_id,consented_by) VALUES(?,?,?)", team, product, seller);
  }

  @Test
  void qualifiedSourcesRequireTheActualPartiesAndDeduplicate() throws Exception {
    mvc.perform(body(post("/api/v1/engagements"), "outsider", Map.of("inquiryId", inquiry))).andExpect(status().isNotFound());
    mvc.perform(body(post("/api/v1/engagements"), "buyer", Map.of("inquiryId", inquiry, "businessRequestId", brief))).andExpect(status().isBadRequest());
    db.update("UPDATE inquiries SET current_status='CREATED_UNVERIFIED',email_confirmed_at=null WHERE id=?", inquiry);
    mvc.perform(body(post("/api/v1/engagements"), "seller", Map.of("inquiryId", inquiry))).andExpect(status().isNotFound());
    db.update("UPDATE inquiries SET current_status='DISCUSSION',email_confirmed_at=now() WHERE id=?", inquiry);
    UUID engagement = createInquiry("seller");
    assertEquals(engagement, createInquiry("buyer"));
    assertEquals(1, db.queryForObject("SELECT count(*) FROM delivery_engagements", Integer.class));
    mvc.perform(as(get(route(engagement)), "outsider")).andExpect(status().isNotFound());
    mvc.perform(as(get("/api/v1/engagements"), "outsider")).andExpect(jsonPath("$.items.length()").value(0));
    mvc.perform(as(get(route(engagement)), "buyer")).andExpect(jsonPath("$.side").value("BUYER")).andExpect(jsonPath("$.agreement").isEmpty());
  }

  @Test
  void eligibilityAndPrivateRequestInvitationRemainCurrent() throws Exception {
    mvc.perform(body(post("/api/v1/engagements"), "seller", Map.of("businessRequestId", brief, "candidateKind", "BUILDER", "candidateId", seller))).andExpect(status().isNotFound());
    db.update("INSERT INTO request_shortlist(id,request_id,kind,builder_id,reason,source,created_by) VALUES(?,?,'BUILDER',?,'Matching public proof','BUSINESS',?)", UUID.randomUUID(), brief, seller, buyer);
    UUID engagement = createBusiness("seller", "BUILDER", seller);
    assertEquals(engagement, createBusiness("buyer", "BUILDER", seller));
    db.update("UPDATE products SET visibility='PRIVATE_CASE_STUDY' WHERE id=?", product);
    // Proof privacy after agreement-source creation does not strand the authorized existing parties.
    mvc.perform(as(get(route(engagement)), "seller")).andExpect(status().isOk());
    mvc.perform(body(post("/api/v1/engagements"), "buyer", Map.of("businessRequestId", brief, "candidateKind", "TEAM", "candidateId", team))).andExpect(status().isNotFound());
    db.update("UPDATE products SET visibility='PUBLIC' WHERE id=?", product);
    db.update("UPDATE business_requests SET technology='Rust',category='Education' WHERE id=?", brief);
    mvc.perform(body(post("/api/v1/engagements"), "buyer", Map.of("businessRequestId", brief, "candidateKind", "TEAM", "candidateId", team))).andExpect(status().isNotFound());
  }

  @Test
  void membershipRevocationRoleAndExpiryRevokeCommercialAuthority() throws Exception {
    // Remove manager's buyer authority so its team commercial authority has one unambiguous side.
    db.update("DELETE FROM business_members WHERE business_id=? AND user_id=?", business, manager);
    UUID engagement = createBusiness("buyer", "TEAM", team);
    mvc.perform(as(get(route(engagement)), "manager")).andExpect(jsonPath("$.side").value("SELLER"));
    db.update("UPDATE team_members SET role='PROJECT_MANAGER' WHERE team_id=? AND user_id=?", team, manager);
    mvc.perform(as(get(route(engagement)), "manager")).andExpect(status().isNotFound());
    db.update("UPDATE team_members SET role='BUSINESS_MANAGER',membership_type='CONTRACT',expires_at=now()-interval '1 day' WHERE team_id=? AND user_id=?", team, manager);
    mvc.perform(as(get(route(engagement)), "manager")).andExpect(status().isNotFound());
    db.update("INSERT INTO business_members(business_id,user_id,role) VALUES(?,?,'HIRING_MANAGER')", business, outsider);
    mvc.perform(as(get(route(engagement)), "outsider")).andExpect(jsonPath("$.side").value("BUYER"));
    db.update("DELETE FROM business_members WHERE business_id=? AND user_id=?", business, outsider);
    mvc.perform(as(get(route(engagement)), "outsider")).andExpect(status().isNotFound());
    db.update("UPDATE developer_profiles SET approval_status='SUSPENDED' WHERE user_id=?", seller);
    mvc.perform(as(get(route(engagement)), "seller")).andExpect(status().isNotFound());
  }

  @Test
  void explicitConsentLocksTheCommercialSnapshotAndNeverChangesLegacyReviews() throws Exception {
    UUID engagement = createInquiry("buyer"), proposal = draft(engagement);
    String actions = route(engagement) + "/proposals/" + proposal;
    mvc.perform(body(post(actions + "/send"), "seller", Map.of("consent", false))).andExpect(status().isBadRequest());
    mvc.perform(body(post(actions + "/send"), "buyer", Map.of("consent", true))).andExpect(status().isForbidden());
    action(engagement, proposal, "send", "seller", Map.of("consent", true));
    assertThrows(DataAccessException.class, () -> db.update("UPDATE delivery_proposals SET scope='Changed after sending' WHERE id=?", proposal));
    mvc.perform(body(post(actions + "/accept"), "buyer", Map.of())).andExpect(status().isBadRequest());
    action(engagement, proposal, "accept", "buyer", Map.of("consent", true));
    mvc.perform(body(post(actions + "/accept"), "buyer", Map.of("consent", true))).andExpect(status().isConflict());
    mvc.perform(as(get(route(engagement)), "seller")).andExpect(jsonPath("$.agreement.digest").value(org.hamcrest.Matchers.matchesPattern("[a-f0-9]{64}"))).andExpect(jsonPath("$.agreement.amountMinor").value(150000)).andExpect(jsonPath("$.milestones.length()").value(1));
    assertThrows(DataAccessException.class, () -> db.update("UPDATE delivery_agreements SET terms='Overwritten' WHERE engagement_id=?", engagement));
    assertEquals("DISCUSSION", db.queryForObject("SELECT current_status FROM inquiries WHERE id=?", String.class, inquiry));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM reviews", Integer.class));
  }

  @Test
  void revisionsKeepHistoryAndRequireExplicitWithdrawalOfSentOffers() throws Exception {
    UUID engagement = createInquiry("seller"), first = draft(engagement), second = draft(engagement);
    assertEquals("WITHDRAWN", db.queryForObject("SELECT status FROM delivery_proposals WHERE id=?", String.class, first));
    action(engagement, second, "send", "seller", Map.of("consent", true));
    mvc.perform(body(post(route(engagement) + "/proposals"), "seller", proposal(150000))).andExpect(status().isConflict());
    action(engagement, second, "withdraw", "seller", Map.of());
    UUID third = draft(engagement);
    action(engagement, third, "send", "seller", Map.of("consent", true));
    action(engagement, third, "reject", "buyer", Map.of());
    draft(engagement);
    assertEquals(4, db.queryForObject("SELECT count(*) FROM delivery_proposals WHERE engagement_id=?", Integer.class, engagement));
  }

  @Test
  void moneyAndMilestoneValidationRejectFractionalUnsafeOrMismatchedTotals() throws Exception {
    UUID engagement = createInquiry("seller");
    for (Object invalid : List.of(0, 99, -1, 100.5, "150000", 1_000_000_001L))
      mvc.perform(body(post(route(engagement) + "/proposals"), "seller", proposal(invalid))).andExpect(status().isBadRequest());
    var mismatched = new HashMap<>(proposal(150000)); mismatched.put("amountMinor", 160000);
    mvc.perform(body(post(route(engagement) + "/proposals"), "seller", mismatched)).andExpect(status().isBadRequest());
    mismatched = new HashMap<>(proposal(150000)); mismatched.put("currency", "USD");
    mvc.perform(body(post(route(engagement) + "/proposals"), "seller", mismatched)).andExpect(status().isBadRequest());
    var oversized = new HashMap<>(proposal(700000000));
    oversized.put("milestones", List.of(((List<?>) oversized.get("milestones")).get(0), ((List<?>) oversized.get("milestones")).get(0)));
    mvc.perform(body(post(route(engagement) + "/proposals"), "seller", oversized)).andExpect(status().isBadRequest());
    assertEquals(0, db.queryForObject("SELECT count(*) FROM delivery_proposals", Integer.class));
  }

  @Test
  void deliveryRequiresSellerSubmissionBuyerAcceptanceAndSafeLinks() throws Exception {
    UUID engagement = createInquiry("buyer"), milestone = agreed(engagement);
    String actions = route(engagement) + "/milestones/" + milestone;
    mvc.perform(body(post(actions + "/accept"), "buyer", Map.of())).andExpect(status().isConflict());
    mvc.perform(body(post(actions + "/start"), "buyer", Map.of())).andExpect(status().isForbidden());
    milestone(engagement, milestone, "start", "seller", Map.of());
    mvc.perform(body(post(actions + "/submit"), "seller", Map.of("deliveryNote", "Complete approval workflow delivered.", "deliveryUrl", "javascript:alert(1)"))).andExpect(status().isBadRequest());
    milestone(engagement, milestone, "submit", "seller", Map.of("deliveryNote", "Complete approval workflow delivered.", "deliveryUrl", "https://example.test/delivery"));
    mvc.perform(body(post(actions + "/accept"), "seller", Map.of())).andExpect(status().isForbidden());
    milestone(engagement, milestone, "request-revision", "buyer", Map.of("reason", "Include keyboard checks and the error summary."));
    milestone(engagement, milestone, "start", "seller", Map.of());
    milestone(engagement, milestone, "submit", "seller", Map.of("deliveryNote", "Added keyboard checks and the error summary."));
    milestone(engagement, milestone, "accept", "buyer", Map.of());
    mvc.perform(body(post(actions + "/request-revision"), "buyer", Map.of("reason", "Attempt to reverse acceptance after payment"))).andExpect(status().isConflict());
    assertEquals(1, db.queryForObject("SELECT count(*) FROM delivery_activity WHERE engagement_id=? AND detail LIKE '%keyboard checks%'", Integer.class, engagement));
  }

  @Test
  void aMilestoneFromAnotherEngagementCannotBeActionedThroughTheRoute() throws Exception {
    UUID engagement = createInquiry("seller"), milestone = agreed(engagement);
    UUID other = createBusiness("buyer", "BUILDER", seller);
    mvc.perform(body(post(route(other) + "/milestones/" + milestone + "/start"), "seller", Map.of())).andExpect(status().isConflict());
    // Make both routes active, then verify the foreign milestone produces a concealed 404.
    UUID proposal = draft(other); action(other, proposal, "send", "seller", Map.of("consent", true)); action(other, proposal, "accept", "buyer", Map.of("consent", true));
    mvc.perform(body(post(route(other) + "/milestones/" + milestone + "/start"), "seller", Map.of())).andExpect(status().isNotFound());
    assertEquals("PLANNED", db.queryForObject("SELECT status FROM delivery_milestones WHERE id=?", String.class, milestone));
  }

  @Test
  void completionRequiresLiveUnrefundedCaptureAndBothAcknowledgements() throws Exception {
    UUID engagement = createInquiry("buyer"), milestone = agreed(engagement);
    mvc.perform(body(post(route(engagement) + "/completion"), "seller", Map.of())).andExpect(status().isConflict());
    accepted(engagement, milestone);
    mvc.perform(body(post(route(engagement) + "/completion"), "seller", Map.of())).andExpect(status().isConflict());
    UUID payment = captured(milestone, "test", 0);
    mvc.perform(body(post(route(engagement) + "/completion"), "seller", Map.of())).andExpect(status().isConflict());
    db.update("UPDATE payment_attempts SET mode='live',refunded_minor=100 WHERE id=?", payment);
    mvc.perform(body(post(route(engagement) + "/completion"), "seller", Map.of())).andExpect(status().isConflict());
    db.update("UPDATE payment_attempts SET refunded_minor=0 WHERE id=?", payment);
    mvc.perform(body(post(route(engagement) + "/completion"), "buyer", Map.of())).andExpect(status().isConflict());
    mvc.perform(body(post(route(engagement) + "/completion"), "seller", Map.of())).andExpect(jsonPath("$.status").value("COMPLETION_PENDING"));
    mvc.perform(body(post(route(engagement) + "/completion"), "seller", Map.of())).andExpect(status().isConflict());
    mvc.perform(body(post(route(engagement) + "/completion"), "buyer", Map.of())).andExpect(jsonPath("$.status").value("COMPLETED"));
  }

  @Test
  void disputesPauseWorkRequireMfaReasonedResolutionAndNeverPretendToRefund() throws Exception {
    UUID engagement = createInquiry("buyer"), milestone = agreed(engagement);
    mvc.perform(body(post(route(engagement) + "/disputes"), "buyer", Map.of("reason", "The acceptance requirements need an administrator review."))).andExpect(status().isOk());
    UUID dispute = db.queryForObject("SELECT id FROM delivery_disputes", UUID.class);
    mvc.perform(body(post(route(engagement) + "/milestones/" + milestone + "/start"), "seller", Map.of())).andExpect(status().isConflict());
    mvc.perform(as(get("/api/v1/admin/delivery/disputes"), "buyer")).andExpect(status().isForbidden());
    db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?", admin);
    mvc.perform(body(post("/api/v1/admin/delivery/disputes/" + dispute + "/resolve"), "admin", Map.of("resolution", "RESUME", "reason", "Both parties agreed to resume the recorded scope."))).andExpect(status().isForbidden());
    db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?", admin);
    mvc.perform(body(post("/api/v1/admin/delivery/disputes/" + dispute + "/resolve"), "admin", Map.of("resolution", "RESUME", "reason", "short"))).andExpect(status().isBadRequest());
    mvc.perform(body(post("/api/v1/admin/delivery/disputes/" + dispute + "/resolve"), "admin", Map.of("resolution", "RESUME", "reason", "Both parties agreed to resume the recorded scope."))).andExpect(jsonPath("$.status").value("ACTIVE"));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM payment_attempts", Integer.class));
    milestone(engagement, milestone, "start", "seller", Map.of());
    mvc.perform(body(post(route(engagement) + "/disputes"), "seller", Map.of("reason", "The client requested cancellation of the remaining work."))).andExpect(status().isOk());
    dispute = db.queryForObject("SELECT id FROM delivery_disputes WHERE status='OPEN'", UUID.class);
    mvc.perform(body(post("/api/v1/admin/delivery/disputes/" + dispute + "/resolve"), "admin", Map.of("resolution", "CANCEL", "reason", "Both parties approved cancellation; provider refunds require separate action."))).andExpect(jsonPath("$.status").value("CANCELLED"));
    mvc.perform(body(post(route(engagement) + "/completion"), "seller", Map.of())).andExpect(status().isConflict());
  }

  @Test
  void unverifiedSuspendedAndUnapprovedActorsLoseAccess() throws Exception {
    UUID engagement = createInquiry("seller");
    db.update("UPDATE users SET email_verified_at=null WHERE id=?", buyer);
    mvc.perform(as(get(route(engagement)), "buyer")).andExpect(status().isForbidden());
    db.update("UPDATE users SET email_verified_at=now(),account_status='SUSPENDED' WHERE id=?", buyer);
    mvc.perform(as(get(route(engagement)), "buyer")).andExpect(status().isUnauthorized());
    db.update("UPDATE developer_profiles SET approval_status='SUSPENDED' WHERE user_id=?", seller);
    mvc.perform(body(post(route(engagement) + "/proposals"), "seller", proposal(150000))).andExpect(status().isNotFound());
  }

  @Test
  void exportIncludesOnlyTheActorsCommercialConsentsDisputesAndPayments() throws Exception {
    UUID engagement = createInquiry("seller"), milestone = agreed(engagement);
    accepted(engagement, milestone);
    captured(milestone, "test", 0);
    mvc.perform(body(post(route(engagement) + "/disputes"), "buyer", Map.of("reason", "Please review the requested acceptance requirements."))).andExpect(status().isOk());
    mvc.perform(as(get("/api/v1/me/export"), "buyer"))
        .andExpect(jsonPath("$.commercialConsents.length()").value(1))
        .andExpect(jsonPath("$.deliveryDisputes.length()").value(1))
        .andExpect(jsonPath("$.milestonePayments.length()").value(1))
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("acc_test_fixture"))));
    mvc.perform(as(get("/api/v1/me/export"), "seller"))
        .andExpect(jsonPath("$.commercialConsents.length()").value(1))
        .andExpect(jsonPath("$.deliveryDisputes.length()").value(0))
        .andExpect(jsonPath("$.milestonePayments.length()").value(0));
    mvc.perform(as(get("/api/v1/me/export"), "outsider"))
        .andExpect(jsonPath("$.commercialConsents.length()").value(0))
        .andExpect(jsonPath("$.deliveryDisputes.length()").value(0))
        .andExpect(jsonPath("$.milestonePayments.length()").value(0));
  }
}
