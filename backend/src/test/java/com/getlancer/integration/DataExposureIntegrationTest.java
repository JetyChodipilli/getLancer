package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.responses.AccountExportResponse;
import com.getlancer.payments.PaymentRepository;
import com.getlancer.payments.PaymentService;
import com.getlancer.payments.RazorpayClient;
import com.getlancer.delivery.DeliveryRepository;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import com.getlancer.responses.AdminResponses;
import com.getlancer.responses.PaymentResponses;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@ContextConfiguration(initializers = TestDatabaseGuard.class)
@SpringBootTest(properties = {
    "app.environment=local", "app.jobs-enabled=false", "app.admin-email=admin@example.test",
    "app.admin-password=", "app.admin-totp=", "spring.config.import=",
    "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}",
    "spring.datasource.username=${TEST_DB_USERNAME:postgres}",
    "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.hikari.schema=getlancer_test", "spring.flyway.default-schema=getlancer_test",
    "spring.flyway.schemas=getlancer_test", "app.origin=http://localhost:3000",
    "app.secure-cookie=false", "app.storage.access-key=", "app.storage.secret-key="
})
@AutoConfigureMockMvc
@Transactional
class DataExposureIntegrationTest {
  private static final String CANARY = "DO_NOT_EXPOSE_CANARY";
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired PaymentRepository paymentRepository;
  @Autowired DeliveryRepository delivery;
  @Autowired Security security;
  @Autowired PlatformTransactionManager transactions;
  @Autowired Environment environment;
  UUID owner, buyer, outsider, admin, product, privateProduct, engagement, milestone, payment;
  private boolean committedFixture;

  @BeforeEach
  void prepare() {
    committedFixture = !TransactionSynchronizationManager.isActualTransactionActive();
    TestDatabaseGuard.validate(environment);
    var reset = new TransactionTemplate(transactions);
    reset.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    // Transport rate limits commit independently; release this table lock before response probes.
    reset.executeWithoutResult(ignored -> db.execute("TRUNCATE getlancer_test.rate_buckets"));
    db.execute("TRUNCATE users CASCADE");
    // Transactional DDL rolls back after each test: simulate future migrations adding secret columns.
    if (!committedFixture) {
      for (String table : List.of("users", "developer_profiles", "products", "reports", "inquiries",
          "component_entries", "payment_attempts", "product_access_grants", "college_project_metadata")) {
        db.execute("ALTER TABLE " + table + " ADD COLUMN fixture_secret_canary text DEFAULT '" + CANARY + "'");
      }
    }
    owner = user("owner", true); buyer = user("buyer", false); outsider = user("outsider", true);
    admin = user("admin", false);
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')", admin);
    db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?", admin);
    product = product("PUBLIC"); privateProduct = product("PRIVATE_CASE_STUDY");
    db.update("INSERT INTO product_access_grants(product_id,client_email,expires_at) VALUES(?,'buyer@example.test',now()+interval '1 day')", privateProduct);
    db.update("INSERT INTO component_entries(id,owner_id,recipe_slug,slug,title,summary,contribution) VALUES(?,?,'fixture-recipe','fixture-remix','Safe component','Safe component summary','Explicit contribution')", UUID.randomUUID(), owner);
    UUID inquiry = UUID.randomUUID(); engagement = UUID.randomUUID(); milestone = UUID.randomUUID(); payment = UUID.randomUUID();
    db.update("INSERT INTO inquiries(id,reference_product_id,developer_user_id,client_email,client_name,request_type,description,budget_band,timeline_band,current_status,email_confirmed_at,idempotency_key,request_hash) VALUES(?,?,?,'buyer@example.test','Buyer','CUSTOMIZE','Private client brief','10k','month','DISCUSSION',now(),?,'fixture-request-hash')", inquiry, product, owner, UUID.randomUUID());
    db.update("INSERT INTO delivery_engagements(id,source_inquiry_id,buyer_user_id,builder_user_id,title,created_by,status) VALUES(?,?,?,?,'Delivery facts',?,'ACTIVE')", engagement, inquiry, buyer, owner, buyer);
    db.update("INSERT INTO delivery_milestones(id,engagement_id,ordinal,title,description,amount_minor,due_date,status,accepted_by,accepted_at) VALUES(?,?,1,'Milestone','Accepted deliverable',10000,current_date+1,'ACCEPTED',?,now())", milestone, engagement, buyer);
    db.update("INSERT INTO payment_attempts(id,milestone_id,payer_user_id,idempotency_key,amount_minor,account_id,mode,status,attention_reason) VALUES(?,?,?,?,10000,'acc_fixture','test','UNKNOWN','Reconciliation pending')", payment, milestone, buyer, UUID.randomUUID());
    db.update("INSERT INTO reports(id,reporter_id,target_type,target_id,reason,detail) VALUES(?,?,'PRODUCT',?,'SPAM','Review fixture evidence')", UUID.randomUUID(), buyer, product);
  }

  @AfterEach
  void cleanCommittedFixtures() {
    if (!committedFixture) return;
    TestDatabaseGuard.validate(environment);
    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    // These tests commit provider effects; clear their rows only in the guarded disposable schema.
    db.execute("TRUNCATE getlancer_test.users CASCADE");
    db.execute("TRUNCATE getlancer_test.rate_buckets");
  }

  private UUID user(String name, boolean developer) {
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'DO_NOT_EXPOSE_PASSWORD',now())", id, name + "@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT')", id);
    if (developer) db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER')", id);
    db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,?,'APPROVED')", id, name, name);
    db.update("INSERT INTO showcase_entitlements(user_id) VALUES(?)", id);
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')", Support.hash(name), id);
    return id;
  }

  private UUID product(String visibility) {
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,approval_status,lifecycle_status,visibility) VALUES(?, ?, ?, 'Preserved project title','Project summary','Working project description','SAAS','CRM','Java','Built the complete project','APPROVED','ACTIVE',?)", id, owner, id.toString(), visibility);
    return id;
  }

  private JsonNode response(String route, String actor) throws Exception {
    return response(route, actor, Map.of());
  }

  private JsonNode response(String route, String actor, Map<String,String> filters) throws Exception {
    var request = get(route).cookie(new Cookie("gl_session", actor));
    for (var filter : filters.entrySet()) request.param(filter.getKey(), filter.getValue());
    String body = mvc.perform(request)
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertFalse(body.contains(CANARY), route);
    assertFalse(body.contains("fixture_secret_canary"), route);
    assertFalse(body.contains("DO_NOT_EXPOSE_PASSWORD"), route);
    assertFalse(body.contains("password_hash"), route);
    assertFalse(body.contains("token_hash"), route);
    assertFalse(body.contains("admin_totp"), route);
    return json.readTree(body);
  }

  @Test
  void futureColumnsNeverEnterCurrentUserOrFullAccountExport() throws Exception {
    JsonNode me = response("/api/v1/me", "owner");
    assertEquals(owner.toString(), me.path("id").asText());
    assertEquals("owner", me.path("profile").path("display_name").asText());
    JsonNode export = response("/api/v1/me/export", "owner");
    assertEquals("owner@example.test", export.path("account").path("email").asText());
    assertEquals("Preserved project title", export.path("products").get(0).path("title").asText());
    assertEquals("Safe component", export.path("components").get(0).path("title").asText());
    assertTrue(export.path("hostedDemos").path("hosting").isArray());
    JsonNode otherExport = response("/api/v1/me/export", "outsider");
    assertTrue(otherExport.path("products").isEmpty());
    mvc.perform(get("/api/v1/me/export")).andExpect(status().isUnauthorized());
    db.update("UPDATE users SET account_status='SUSPENDED' WHERE id=?", owner);
    mvc.perform(get("/api/v1/me/export").cookie(new Cookie("gl_session", "owner"))).andExpect(status().isUnauthorized());
  }

  @Test
  void administratorRowsHaveFixedFieldsAndStillRequireCurrentMfa() throws Exception {
    JsonNode accounts = response("/api/v1/admin/accounts", "admin");
    assertEquals(4, accounts.path("items").size());
    assertTrue(accounts.path("items").get(0).has("account_status"));
    JsonNode reports = response("/api/v1/admin/reports", "admin");
    assertEquals("Review fixture evidence", reports.path("items").get(0).path("detail").asText());
    db.update("UPDATE developer_profiles SET approval_status='PROFILE_PENDING' WHERE user_id=?", outsider);
    JsonNode profiles = response("/api/v1/admin/profiles/pending", "admin");
    assertEquals("outsider", profiles.path("items").get(0).path("displayName").asText());
    response("/api/v1/admin/audit", "admin");
    mvc.perform(get("/api/v1/admin/accounts").cookie(new Cookie("gl_session", "buyer"))).andExpect(status().isForbidden());
    db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?", admin);
    mvc.perform(get("/api/v1/admin/accounts").cookie(new Cookie("gl_session", "admin"))).andExpect(status().isForbidden());
  }

  @Test
  void administratorInquiryEvidenceGetOnlyAppendsAuditAfterCurrentMfa() throws Exception {
    UUID inquiry = db.queryForObject("SELECT source_inquiry_id FROM delivery_engagements WHERE id=?",
        UUID.class, engagement);
    UUID report = UUID.randomUUID();
    String rawBrief = "Private investigation brief: unreleased client plans and confidential delivery details.";
    db.update("UPDATE inquiries SET description=? WHERE id=?", rawBrief, inquiry);
    db.update("INSERT INTO inquiry_events(id,inquiry_id,event_type,actor_id,actor_type) VALUES(?,?,'RESPONDED',?,'DEVELOPER')",
        UUID.randomUUID(), inquiry, owner);
    db.update("INSERT INTO reports(id,reporter_id,target_type,target_id,reason,detail) VALUES(?,?,'INQUIRY',?,'SPAM','Investigate this private inquiry')",
        report, buyer, inquiry);
    db.update("INSERT INTO moderation_actions(id,admin_id,target_type,target_id,action,reason) VALUES(?,?,'REPORT',?,'TRIAGE','Existing triage evidence must remain unchanged')",
        UUID.randomUUID(), admin, report);
    String domainBefore = reportDomainSnapshot();
    List<String> auditBefore = moderationSnapshot();
    String route = "/api/v1/admin/reports/" + report;

    mvc.perform(get(route)).andExpect(status().isUnauthorized());
    assertEquals(domainBefore, reportDomainSnapshot());
    assertEquals(auditBefore, moderationSnapshot());
    assertEquals(0, db.queryForObject("SELECT count(*) FROM moderation_actions WHERE action='VIEW_EVIDENCE'", Integer.class));

    db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?", admin);
    mvc.perform(get(route).cookie(new Cookie("gl_session", "admin"))).andExpect(status().isForbidden());
    assertEquals(domainBefore, reportDomainSnapshot());
    assertEquals(auditBefore, moderationSnapshot());
    assertEquals(0, db.queryForObject("SELECT count(*) FROM moderation_actions WHERE action='VIEW_EVIDENCE'", Integer.class));

    db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?", admin);
    JsonNode evidence = response(route, "admin");
    assertEquals(report.toString(), evidence.path("report").path("id").asText());
    assertEquals("INQUIRY", evidence.path("report").path("target_type").asText());
    assertEquals(inquiry.toString(), evidence.path("report").path("target_id").asText());
    assertEquals(rawBrief, evidence.path("target").path("description").asText());
    assertEquals("DISCUSSION", evidence.path("target").path("current_status").asText());
    assertEquals(domainBefore, reportDomainSnapshot());
    List<String> auditAfter = moderationSnapshot();
    assertEquals(auditBefore.size() + 1, auditAfter.size());
    assertTrue(auditAfter.containsAll(auditBefore));
    var views = db.queryForList("SELECT id,admin_id,target_type,target_id,action,reason FROM moderation_actions WHERE action='VIEW_EVIDENCE'");
    assertEquals(1, views.size());
    var view = views.get(0);
    assertEquals(admin, view.get("admin_id"));
    assertEquals("REPORT", view.get("target_type"));
    assertEquals(report, view.get("target_id"));
    assertEquals("VIEW_EVIDENCE", view.get("action"));
    assertEquals("Inquiry evidence accessed for report investigation", view.get("reason"));
    assertFalse(view.get("reason").toString().contains(rawBrief));
  }

  private String reportDomainSnapshot() {
    return db.queryForObject("""
        SELECT jsonb_build_object(
          'reports',(SELECT coalesce(jsonb_agg(to_jsonb(s) ORDER BY to_jsonb(s)),'[]'::jsonb) FROM reports s),
          'inquiries',(SELECT coalesce(jsonb_agg(to_jsonb(s) ORDER BY to_jsonb(s)),'[]'::jsonb) FROM inquiries s),
          'inquiry_events',(SELECT coalesce(jsonb_agg(to_jsonb(s) ORDER BY to_jsonb(s)),'[]'::jsonb) FROM inquiry_events s),
          'account_tokens',(SELECT coalesce(jsonb_agg(to_jsonb(s) ORDER BY to_jsonb(s)),'[]'::jsonb) FROM account_tokens s),
          'reviews',(SELECT coalesce(jsonb_agg(to_jsonb(s) ORDER BY to_jsonb(s)),'[]'::jsonb) FROM reviews s),
          'earned_capacity_awards',(SELECT coalesce(jsonb_agg(to_jsonb(s) ORDER BY to_jsonb(s)),'[]'::jsonb) FROM earned_capacity_awards s),
          'products',(SELECT coalesce(jsonb_agg(to_jsonb(s) ORDER BY to_jsonb(s)),'[]'::jsonb) FROM products s),
          'product_media',(SELECT coalesce(jsonb_agg(to_jsonb(s) ORDER BY to_jsonb(s)),'[]'::jsonb) FROM product_media s),
          'developer_profiles',(SELECT coalesce(jsonb_agg(to_jsonb(s) ORDER BY to_jsonb(s)),'[]'::jsonb) FROM developer_profiles s),
          'moderation_appeals',(SELECT coalesce(jsonb_agg(to_jsonb(s) ORDER BY to_jsonb(s)),'[]'::jsonb) FROM moderation_appeals s),
          'delivery_engagements',(SELECT coalesce(jsonb_agg(to_jsonb(s) ORDER BY to_jsonb(s)),'[]'::jsonb) FROM delivery_engagements s),
          'delivery_proposals',(SELECT coalesce(jsonb_agg(to_jsonb(s) ORDER BY to_jsonb(s)),'[]'::jsonb) FROM delivery_proposals s),
          'delivery_agreements',(SELECT coalesce(jsonb_agg(to_jsonb(s) ORDER BY to_jsonb(s)),'[]'::jsonb) FROM delivery_agreements s),
          'delivery_milestones',(SELECT coalesce(jsonb_agg(to_jsonb(s) ORDER BY to_jsonb(s)),'[]'::jsonb) FROM delivery_milestones s),
          'delivery_activity',(SELECT coalesce(jsonb_agg(to_jsonb(s) ORDER BY to_jsonb(s)),'[]'::jsonb) FROM delivery_activity s),
          'delivery_disputes',(SELECT coalesce(jsonb_agg(to_jsonb(s) ORDER BY to_jsonb(s)),'[]'::jsonb) FROM delivery_disputes s),
          'payment_attempts',(SELECT coalesce(jsonb_agg(to_jsonb(s) ORDER BY to_jsonb(s)),'[]'::jsonb) FROM payment_attempts s),
          'payment_ledger',(SELECT coalesce(jsonb_agg(to_jsonb(s) ORDER BY to_jsonb(s)),'[]'::jsonb) FROM payment_ledger s)
        )::text
        """, String.class);
  }

  private List<String> moderationSnapshot() {
    return db.queryForList("SELECT row_to_json(state)::text FROM moderation_actions state ORDER BY row_to_json(state)::text",
        String.class);
  }

  @Test
  void paymentFactsRemainVisibleOnlyToCurrentPartiesOrMfaAdministrator() throws Exception {
    JsonNode payments = response("/api/v1/engagements/" + engagement + "/payments", "buyer");
    assertEquals(10000, payments.path("items").get(0).path("amountMinor").asInt());
    assertEquals(milestone.toString(), payments.path("items").get(0).path("milestoneId").asText());
    response("/api/v1/engagements/" + engagement + "/payments", "owner");
    JsonNode attention = response("/api/v1/admin/payments/attention", "admin");
    assertEquals("Reconciliation pending", attention.path("items").get(0).path("attentionReason").asText());
    mvc.perform(get("/api/v1/engagements/" + engagement + "/payments").cookie(new Cookie("gl_session", "outsider"))).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/engagements/" + engagement + "/payments")).andExpect(status().isUnauthorized());
  }

  @Test
  void privateGrantProjectionPreservesOwnerCheckAndExactJsonNames() throws Exception {
    JsonNode grants = response("/api/v1/developer/products/" + privateProduct + "/access", "owner");
    assertEquals("buyer@example.test", grants.path("items").get(0).path("client_email").asText());
    assertTrue(grants.path("items").get(0).has("expires_at"));
    mvc.perform(get("/api/v1/developer/products/" + privateProduct + "/access").cookie(new Cookie("gl_session", "outsider"))).andExpect(status().isNotFound());
  }

  @Test
  void similarBuilderCardsPreserveIdentityProfileLinksAndAvailability() throws Exception {
    UUID candidate = product("PUBLIC");
    db.update("UPDATE products SET owner_user_id=? WHERE id=?", outsider, candidate);
    db.update("UPDATE developer_profiles SET display_name='Available builder',availability_status='ONE_SLOT_LEFT',booked_until=DATE '2030-05-01' WHERE user_id=?", outsider);

    JsonNode cards = response("/api/v1/products/" + product + "/similar-builders", "buyer").path("items");
    assertEquals(1, cards.size());
    JsonNode card = cards.get(0);
    assertEquals(candidate.toString(), card.path("id").asText());
    assertEquals("Available builder", card.path("builder").asText());
    assertEquals("outsider", card.path("builderSlug").asText());
    assertEquals("ONE_SLOT_LEFT", card.path("availability").asText());
    assertEquals("2030-05-01", card.path("bookedUntil").asText());
  }

  @Test
  void collegeSearchBindsHostileFiltersAndPreservesLiteralMatchingPrivacyAndPaging() throws Exception {
    UUID literalProduct = product("PUBLIC");
    db.update("UPDATE products SET title='College 100%_original' WHERE id=?", literalProduct);
    for (UUID id : List.of(product, literalProduct, privateProduct)) {
      db.update("INSERT INTO college_project_metadata(product_id,category,language,problem,outcome,prerequisites,contribution,institution,status) VALUES(?,'FULL_STACK',?,'A documented learning problem','Reproducible result','Local Java runtime','Original implementation','Private college fixture','APPROVED')",
          id, id.equals(literalProduct) ? "Java%_language" : "Java");
    }
    String route = "/api/v1/college-projects";
    JsonNode matches = response(route, "buyer", Map.of("category", "FULL_STACK", "builder", "owner",
        "language", "jAv", "q", "Preserved"));
    assertEquals(1, matches.path("totalItems").asInt());
    assertEquals(product.toString(), matches.path("items").get(0).path("id").asText());
    assertEquals("FULL_STACK", matches.path("items").get(0).path("education").path("category").asText());
    assertFalse(matches.toString().contains("Private college fixture"));
    assertFalse(matches.path("items").get(0).path("education").has("institution"));

    for (String filter : List.of("q", "language", "builder")) {
      JsonNode hostile = response(route, "buyer", Map.of(filter, "' OR 1=1 --"));
      assertEquals(0, hostile.path("totalItems").asInt(), filter);
      assertTrue(hostile.path("items").isEmpty(), filter);
    }
    mvc.perform(get(route).param("category", "' OR 1=1 --")).andExpect(status().isBadRequest());
    for (String filter : List.of("q", "language")) {
      JsonNode literal = response(route, "buyer", Map.of(filter, "%_"));
      assertEquals(1, literal.path("totalItems").asInt(), filter);
      assertEquals(literalProduct.toString(), literal.path("items").get(0).path("id").asText(), filter);
    }
    JsonNode unfiltered = response(route, "buyer", Map.of("q", " \t", "category", " \t",
        "language", " \t", "builder", " \t"));
    assertEquals(2, unfiltered.path("totalItems").asInt());
    assertEquals(2, unfiltered.path("items").size());
    JsonNode nextPage = response(route, "buyer", Map.of("page", "1"));
    assertEquals(1, nextPage.path("page").asInt());
    assertEquals(2, nextPage.path("totalItems").asInt());
    assertTrue(nextPage.path("items").isEmpty());
    assertFalse(nextPage.path("hasMore").asBoolean());
  }

  @Test
  void concreteDtosIgnoreUnexpectedColumnsEvenIfRepositoryRowContainsThem() {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", payment); row.put("milestone_id", milestone); row.put("amount_minor", 10000L);
    row.put("status", "UNKNOWN"); row.put("currency", "INR"); row.put("mode", "test");
    row.put("refunded_minor", 0L); row.put("fixture_secret_canary", CANARY); row.put("password_hash", CANARY);
    JsonNode summary = json.valueToTree(PaymentResponses.Summary.fromPaymentRow(row));
    assertEquals(Set.of("id", "milestoneId", "status", "amountMinor", "currency", "mode", "orderId",
        "paymentId", "refundedMinor", "transferStatus", "settlementStatus", "attentionReason"), keys(summary));
    assertFalse(summary.toString().contains(CANARY));
    row.put("display_name", "Public display name"); row.put("account_status", "ACTIVE");
    assertFalse(json.valueToTree(AccountExportResponse.Profile.from(row)).toString().contains(CANARY));
    assertFalse(json.valueToTree(AdminResponses.Account.from(row)).toString().contains(CANARY));
  }

  private RazorpayClient spyProvider() {
    return spy(new RazorpayClient(json,true,"rzp_test_fixture123456","fixture-secret-not-production",
        "fixture-webhook-not-production","test",true,true));
  }

  private PaymentService paymentService(RazorpayClient provider) {
    return new PaymentService(paymentRepository,delivery,security,provider,json,transactions);
  }

  private MockHttpServletRequest request(String actor) {
    var request=new MockHttpServletRequest(); request.setCookies(new Cookie("gl_session",actor)); return request;
  }

  private JsonNode orderFacts(UUID id,String orderId) {
    var order=json.createObjectNode(); order.put("id",orderId); order.put("receipt",id.toString());
    order.put("amount",10000); order.put("currency","INR");
    var transfer=json.createObjectNode(); transfer.put("recipient","acc_fixture"); transfer.put("amount",10000);
    transfer.put("currency","INR"); transfer.put("source",orderId); transfer.put("status","created");
    transfer.put("settlement_status","unknown"); order.set("transfers",json.createArrayNode().add(transfer));
    return order;
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void providerOrderIsRecordedButRevokedBuyerReceivesNoCheckout() throws SQLException {
    UUID extraMilestone=UUID.randomUUID();
    db.update("INSERT INTO delivery_milestones(id,engagement_id,ordinal,title,description,amount_minor,due_date,status,accepted_by,accepted_at) VALUES(?,?,2,'Second milestone','Accepted deliverable',10000,current_date+1,'ACCEPTED',?,now())",extraMilestone,engagement,buyer);
    db.update("INSERT INTO payment_accounts(id,builder_user_id,account_id,mode,provider_status,activation_confirmed,verified_by) VALUES(?,?,'acc_fixture','test','created',true,?)",UUID.randomUUID(),owner,admin);
    var provider=spyProvider();
    doAnswer(call -> {
      assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
      UUID reserved=call.getArgument(0);
      db.update("DELETE FROM sessions WHERE user_id=?",buyer);
      return orderFacts(reserved,"order_fixture123456");
    }).when(provider).createOrder(any(UUID.class),anyLong(),anyString());
    var request=request("buyer"); request.addHeader("Idempotency-Key",UUID.randomUUID().toString());
    ApiError denied=assertThrows(ApiError.class,()->paymentService(provider).order(extraMilestone,request));
    assertEquals(401,denied.status);
    assertCommittedPayment(extraMilestone,"ORDER_CREATED",0);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void providerCaptureLedgerSurvivesBuyerRevocationWithoutExposingResult() throws SQLException {
    db.update("UPDATE payment_attempts SET order_id='order_fixture123456' WHERE id=?",payment);
    var provider=spyProvider();
    doAnswer(call -> {
      assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
      db.update("DELETE FROM sessions WHERE user_id=?",buyer);
      return json.readTree("{\"id\":\"pay_fixture123456\",\"order_id\":\"order_fixture123456\",\"amount\":10000,\"currency\":\"INR\",\"status\":\"captured\",\"captured\":true,\"amount_refunded\":0}");
    }).when(provider).payment("pay_fixture123456");
    var transfer=json.createObjectNode(); transfer.put("recipient","acc_fixture"); transfer.put("amount",10000);
    transfer.put("currency","INR"); transfer.put("source","order_fixture123456"); transfer.put("status","processed");
    transfer.put("settlement_status","pending");
    doReturn(json.createObjectNode().set("items",json.createArrayNode().add(transfer))).when(provider).transfers("pay_fixture123456");
    doReturn(json.createObjectNode().set("items",json.createArrayNode().add(json.createObjectNode().put("id","pay_fixture123456").put("status","captured")))).when(provider).orderPayments("order_fixture123456");
    ApiError denied=assertThrows(ApiError.class,()->paymentService(provider).reconcile(payment,request("buyer")));
    assertEquals(401,denied.status);
    assertCommittedPayment(milestone,"CAPTURED",1);
  }

  @Test
  void administratorMfaRevokedDuringProviderReadBlocksFinalSummary() {
    db.update("UPDATE payment_attempts SET order_id='order_fixture123456' WHERE id=?",payment);
    var provider=spyProvider();
    doAnswer(call -> {
      db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?",admin);
      return json.createObjectNode().set("items",json.createArrayNode());
    }).when(provider).orderPayments("order_fixture123456");
    ApiError denied=assertThrows(ApiError.class,()->paymentService(provider).adminReconcile(payment,request("admin")));
    assertEquals(403,denied.status);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void recoveredBindingRemainsRecordedIfAdministratorMfaIsRevokedDuringFinalRead() throws SQLException {
    var provider=spyProvider();
    doReturn(orderFacts(payment,"order_fixture123456")).when(provider).order("order_fixture123456");
    doAnswer(call -> {
      assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
      db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?",admin);
      return json.createObjectNode().set("items",json.createArrayNode());
    }).when(provider).orderPayments("order_fixture123456");
    ApiError denied=assertThrows(ApiError.class,()->paymentService(provider).bind(payment,Map.of("orderId","order_fixture123456"),request("admin")));
    assertEquals(403,denied.status);
    assertCommittedPayment(milestone,"ORDER_CREATED",0);
  }

  private void assertCommittedPayment(UUID milestoneId,String expectedStatus,long expectedCaptures)
      throws SQLException {
    TestDatabaseGuard.validate(environment);
    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    // Bypass Spring and the pool so an independent PostgreSQL connection can see only committed state.
    try (var connection=DriverManager.getConnection(
        environment.getRequiredProperty("spring.datasource.url"),
        environment.getRequiredProperty("spring.datasource.username"),
        environment.getProperty("spring.datasource.password",""))) {
      connection.setSchema("getlancer_test");
      assertTrue(connection.getAutoCommit());
      try (var statement=connection.prepareStatement(
          "SELECT p.order_id,p.status,(SELECT count(*) FROM payment_ledger l WHERE l.attempt_id=p.id AND l.kind='CAPTURE') AS captures FROM payment_attempts p WHERE p.milestone_id=?")) {
        statement.setObject(1,milestoneId);
        try (var result=statement.executeQuery()) {
          assertTrue(result.next(),"Committed payment attempt is missing");
          assertEquals("order_fixture123456",result.getString("order_id"));
          assertEquals(expectedStatus,result.getString("status"));
          assertEquals(expectedCaptures,result.getLong("captures"));
          assertFalse(result.next(),"Milestone has multiple payment attempts");
        }
      }
    }
  }

  private static Set<String> keys(JsonNode node) {
    Set<String> fields = new HashSet<>(); node.fieldNames().forEachRemaining(fields::add); return fields;
  }

  @Test
  void serviceAndRepositoryQueriesCannotReintroduceWildcardRowProjections() throws Exception {
    Path source = Path.of("src/main/java/com/getlancer");
    Pattern wildcard = Pattern.compile("(?i)\\bSELECT\\s+\\*|\\b[a-z][a-z0-9_]*\\.\\*");
    try (var paths = Files.walk(source)) {
      for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
        String code = Files.readString(path).replaceAll("(?m)^import[^\\n]*", "");
        assertFalse(wildcard.matcher(code).find(), "Projected wildcard in " + path);
      }
    }
  }
}
