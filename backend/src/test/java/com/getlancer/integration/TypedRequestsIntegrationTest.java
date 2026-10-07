package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@ContextConfiguration(initializers = TestDatabaseGuard.class)
@SpringBootTest(properties = {
    "app.environment=local", "app.jobs-enabled=false", "app.admin-password=", "app.admin-totp=",
    "spring.config.import=",
    "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}",
    "spring.datasource.username=${TEST_DB_USERNAME:postgres}",
    "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.hikari.schema=getlancer_test", "spring.flyway.default-schema=getlancer_test",
    "spring.flyway.schemas=getlancer_test", "app.origin=http://localhost:3000",
    "app.secure-cookie=false", "app.storage.access-key=", "app.storage.secret-key="
})
@AutoConfigureMockMvc
class TypedRequestsIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  UUID builder, client, business, team, lead;

  private UUID user(String name, boolean approved) {
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",
        id, name + "@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER'),(?,'CLIENT')", id, id);
    db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,?,?)",
        id, name, name, approved ? "APPROVED" : "DRAFT");
    db.update("INSERT INTO showcase_entitlements(user_id) VALUES(?)", id);
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",
        Support.hash(name), id);
    return id;
  }

  @BeforeEach
  void setup() {
    db.execute("TRUNCATE users CASCADE");
    db.execute("TRUNCATE rate_buckets,analytics_events");
    builder = user("typed-builder", true);
    client = user("typed-client", false);
    UUID admin = user("typed-admin", false);
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')", admin);
    db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?", admin);
    business = UUID.randomUUID();
    db.update("INSERT INTO businesses(id,name,summary,owner_id) VALUES(?,'Typed business','Customer software hiring',?)",
        business, builder);
    db.update("INSERT INTO business_members(business_id,user_id,role) VALUES(?,?,'OWNER')", business, builder);
    team = UUID.randomUUID();
    db.update("INSERT INTO teams(id,slug,name,summary,availability,project_range,owner_id) VALUES(?,?,'Typed team','Customer software team','AVAILABLE_NOW','10k',?)",
        team, team.toString(), builder);
    db.update("INSERT INTO team_members(team_id,user_id,role,membership_type) VALUES(?,?,'OWNER','PERMANENT')", team, builder);
    lead = UUID.randomUUID();
    db.update("INSERT INTO team_leads(id,team_id,client_id,title,description,budget,timeline,assignee_id,follow_up_at) VALUES(?,?,?,'Typed lead','Customer software inquiry','','',?,now()+interval '3 days')",
        lead, team, client, builder);
  }

  @AfterEach
  void cleanup() { db.execute("TRUNCATE users CASCADE"); }

  private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder req, String actor, Object body) throws Exception {
    return req.cookie(new Cookie("gl_session", actor))
        .header("Origin", "http://localhost:3000").header("X-Requested-With", "getlancer")
        .contentType("application/json").content(json.writeValueAsBytes(body));
  }

  private JsonNode ok(MockHttpServletRequestBuilder req) throws Exception {
    return json.readTree(mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }

  private record Invalid(String method, String path, Object body) {}

  @Test
  void domainBoundariesRejectMissingOversizedMalformedAndWrongTypeFieldsBeforeServices() throws Exception {
    UUID id = UUID.randomUUID();
    var cases = List.of(
        new Invalid("POST", "/api/v1/businesses", Map.of("name", "x".repeat(121), "summary", "Customer software hiring")),
        new Invalid("POST", "/api/v1/businesses", Map.of("name", "Missing summary")),
        new Invalid("POST", "/api/v1/businesses/" + business + "/invitations", Map.of("email", "not-an-email")),
        new Invalid("POST", "/api/v1/businesses/" + business + "/requests", Map.of("title", "Private portal", "description", "Customer approval workflow", "category", "CRM", "budget", "10k", "timeline", "2 months", "status", "OPEN", "availableOnly", Map.of("unexpected", true))),
        new Invalid("POST", "/api/v1/businesses/" + business + "/talent-lists", Map.of("name", List.of("wrong type"))),
        new Invalid("POST", "/api/v1/teams", Map.of("name", "Studio", "summary", "Customer software team", "availability", "x".repeat(61))),
        new Invalid("POST", "/api/v1/teams/" + team + "/invitations", Map.of("userId", "malformed", "role", "MEMBER", "membershipType", "PERMANENT")),
        new Invalid("PUT", "/api/v1/developer/profile", Map.of("displayName", "Missing profile fields")),
        new Invalid("POST", "/api/v1/developer/products", Map.of("title", "x".repeat(121))),
        new Invalid("POST", "/api/v1/developer/products/" + id + "/access", Map.of("email", "bad")),
        new Invalid("POST", "/api/v1/inquiries", Map.of("referenceProductId", "invalid")),
        new Invalid("POST", "/api/v1/reports", Map.of("targetType", "ACCOUNT", "targetId", id, "reason", "OTHER", "detail", "A detailed report about a target")),
        new Invalid("POST", "/api/v1/engagements/" + id + "/proposals", Map.of("scope", "Complete customer approval workflow", "terms", "Agreed terms of delivery", "milestones", List.of(Map.of("title", "Build", "description", "Working customer portal", "amountMinor", 1000, "dueDate", "not-a-date")))),
        new Invalid("POST", "/api/v1/engagements/" + id + "/proposals", Map.of("scope", "Complete customer approval workflow", "terms", "Agreed terms of delivery", "milestones", List.of(Map.of("title", "Build", "description", "Working customer portal", "amountMinor", 100.5, "dueDate", "2027-01-01")))),
        new Invalid("POST", "/api/v1/engagements/" + id + "/proposals", Map.of("scope", "Complete customer approval workflow", "terms", "Agreed terms of delivery", "milestones", List.of(Map.of("title", "Build", "description", "Working customer portal", "amountMinor", "1000", "dueDate", "2027-01-01")))),
        new Invalid("POST", "/api/v1/me/templates", Map.of("productId", id, "title", "A source listing", "summary", "A source template description", "description", "Complete customer workflow source listing", "licenseTerms", "One end product with modification rights and required attribution", "priceMinor", -1)),
        new Invalid("POST", "/api/v1/me/components", Map.of("recipeSlug", "portfolio-card", "title", "x".repeat(101), "summary", "A complete component description", "contribution", "Selected and attributed original MIT source", "rightsConsent", true)),
        new Invalid("POST", "/api/v1/me/publishing-slot-purchases", Map.of("pool", "UNKNOWN", "amountMinor", 1000, "purchaseConsent", true)),
        new Invalid("POST", "/api/v1/me/component-slot-purchases", Map.of("amountMinor", 100.5, "purchaseConsent", true)),
        new Invalid("POST", "/api/v1/payments/" + id + "/verify", Map.of("razorpay_payment_id", "invalid", "razorpay_signature", "x".repeat(64))),
        new Invalid("POST", "/api/v1/developer/products/" + id + "/media/upload-request", Map.of("filename", "proof.png", "contentType", "image/png", "sizeBytes", 5242881)),
        new Invalid("PATCH", "/api/v1/developer/products/" + id + "/media", Map.of("items", List.of(Map.of("id", id, "alt", "x".repeat(301))))),
        new Invalid("POST", "/api/v1/maintenance", Map.of("engagementId", id, "title", "Portal support", "scope", "Customer approval workflow support", "terms", "Three requests per monthly cycle; feature work requires separate terms", "amountMinor", 1000, "requestsPerCycle", 51, "responseHours", 24, "totalCycles", 12)),
        new Invalid("POST", "/api/v1/appeals", Map.of("decisionId", id, "statement", "x".repeat(3001))),
        new Invalid("PUT", "/api/v1/developer/availability", Map.of("status", "UNLIMITED")),
        new Invalid("POST", "/api/v1/auth/github/start", Map.of("rememberMe", true)),
        new Invalid("POST", "/api/v1/auth/google/start", Map.of("intent", List.of("login"))),
        new Invalid("POST", "/api/v1/analytics/events", Map.of("eventName", "unsupported_event")),
        new Invalid("POST", "/api/v1/analytics/events", Map.of("eventName", "home_view", "properties", Map.of("queryLength", 100001))));
    for (var invalid : cases) {
      mvc.perform(body(request(HttpMethod.valueOf(invalid.method()), invalid.path()), "typed-builder", invalid.body()))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
    assertEquals(1, db.queryForObject("SELECT count(*) FROM businesses", Integer.class));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM products", Integer.class));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM analytics_events WHERE source='web'", Integer.class));
  }

  @Test
  void administratorCommandsHaveTheSameTypedBoundary() throws Exception {
    UUID id = UUID.randomUUID();
    var cases = List.of(
        new Invalid("POST", "/api/v1/admin/categories", Map.of("name", "New category", "slug", "../unsafe")),
        new Invalid("POST", "/api/v1/admin/deletion-requests/" + id + "/review", Map.of("action", "DELETE_EVERYTHING", "reason", "Reviewed retention request")),
        new Invalid("POST", "/api/v1/admin/reports/" + id + "/triage", Map.of("severity", "UNKNOWN", "status", "OPEN", "reason", "Reviewed report")),
        new Invalid("POST", "/api/v1/admin/hosting/" + id + "/review", Map.of("action", "PUBLISH_ANYTHING", "reason", "Reviewed the exact built package", "rightsReviewed", true, "packageReviewed", true)),
        new Invalid("POST", "/api/v1/admin/payments/accounts", Map.of("builderUserId", builder, "accountId", "invalid", "activationConfirmed", true)),
        new Invalid("PUT", "/api/v1/admin/publishing-slots/PROJECT/pricing", Map.of("amountMinor", 1000000001L, "enabled", true)));
    for (var invalid : cases) {
      mvc.perform(body(request(HttpMethod.valueOf(invalid.method()), invalid.path()), "typed-admin", invalid.body()))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
  }

  @Test
  void malformedJsonUnknownFieldsAndBooleanCoercionReturnSafeErrors() throws Exception {
    for (String content : List.of("{", "[]", "null", "{\"name\":\"Business\",\"summary\":\"Customer software hiring\",\"ownerId\":\"unexpected\"}")) {
      var response = mvc.perform(post("/api/v1/businesses").cookie(new Cookie("gl_session", "typed-builder"))
          .header("Origin", "http://localhost:3000").header("X-Requested-With", "getlancer")
          .contentType("application/json").content(content))
          .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
      assertFalse(response.contains("com.fasterxml.jackson"));
      assertFalse(response.contains("java.lang"));
    }
    mvc.perform(body(post("/api/v1/businesses/" + business + "/requests"), "typed-builder",
        Map.of("title", "Private portal", "description", "Customer approval workflow", "category", "CRM", "budget", "10k", "timeline", "2 months", "status", "OPEN", "availableOnly", "true")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void validBusinessProfileAndOptionalPatchInputsKeepTheirEstablishedBehavior() throws Exception {
    var created = ok(body(post("/api/v1/businesses"), "typed-builder",
        Map.of("name", "Typed customer", "summary", "Private customer software hiring")));
    assertTrue(created.hasNonNull("id"));
    ok(body(post("/api/v1/businesses/" + business + "/invitations"), "typed-builder",
        Map.of("email", "typed-client@example.test")));
    UUID invitation = db.queryForObject("SELECT id FROM business_invitations WHERE business_id=?", UUID.class, business);
    ok(body(post("/api/v1/business-invitations/" + invitation + "/respond"), "typed-client", Map.of("action", "ACCEPT")));
    assertEquals("HIRING_MANAGER", db.queryForObject("SELECT role FROM business_members WHERE business_id=? AND user_id=?", String.class, business, client));

    // Team membership requires an approved profile; exercise PATCH before the material profile edit.
    ok(body(patch("/api/v1/teams/" + team + "/leads/" + lead), "typed-builder", Map.of("note", "Private follow-up note")));
    assertEquals(builder, db.queryForObject("SELECT assignee_id FROM team_leads WHERE id=?", UUID.class, lead));
    assertTrue(db.queryForObject("SELECT follow_up_at IS NOT NULL FROM team_leads WHERE id=?", Boolean.class, lead));
    var clear = new LinkedHashMap<String, Object>();
    clear.put("assigneeId", null); clear.put("followUpAt", null);
    ok(body(patch("/api/v1/teams/" + team + "/leads/" + lead), "typed-builder", clear));
    assertTrue(db.queryForObject("SELECT assignee_id IS NULL AND follow_up_at IS NULL FROM team_leads WHERE id=?", Boolean.class, lead));
    ok(body(patch("/api/v1/teams/" + team + "/leads/" + lead), "typed-builder",
        Map.of("assigneeId", builder, "followUpAt", Instant.now().plusSeconds(86400).toString())));
    assertEquals(builder, db.queryForObject("SELECT assignee_id FROM team_leads WHERE id=?", UUID.class, lead));

    // The retained URL must satisfy the same public-DNS checks as a newly supplied profile URL.
    db.update("UPDATE developer_profiles SET website_url='https://example.com',country='IN',time_zone='Asia/Kolkata',languages='English' WHERE user_id=?", builder);
    ok(body(put("/api/v1/developer/profile"), "typed-builder", Map.of(
        "displayName", "Typed builder", "headline", "Customer software builder", "bio", "Builds complete customer software workflows",
        "technology", "React", "category", "CRM", "availabilityStatus", "AVAILABLE_NOW")));
    assertEquals("https://example.com", db.queryForObject("SELECT website_url FROM developer_profiles WHERE user_id=?", String.class, builder));
    assertEquals("DRAFT", db.queryForObject("SELECT approval_status FROM developer_profiles WHERE user_id=?", String.class, builder));

    ok(body(post("/api/v1/analytics/events"), "typed-builder", Map.of(
        "eventName", "home_view", "source", "web", "eventId", UUID.randomUUID(),
        "sessionId", UUID.randomUUID(), "occurredAt", Instant.now().toString(),
        "properties", Map.of("sourcePage", "explore", "acquisitionSource", "direct", "queryLength", 4))));
  }
}
