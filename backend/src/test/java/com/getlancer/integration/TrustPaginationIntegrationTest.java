package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@org.springframework.test.context.ContextConfiguration(initializers = TestDatabaseGuard.class)
@SpringBootTest(properties = {
    "app.environment=local", "app.jobs-enabled=false", "app.admin-password=", "app.admin-totp=",
    "spring.config.import=",
    "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}",
    "spring.datasource.username=${TEST_DB_USERNAME:postgres}", "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.hikari.schema=getlancer_test", "spring.flyway.default-schema=getlancer_test",
    "spring.flyway.schemas=getlancer_test", "app.origin=http://localhost:3000",
    "app.secure-cookie=false", "app.storage.access-key=", "app.storage.secret-key="
})
@AutoConfigureMockMvc
class TrustPaginationIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  UUID owner, outsider, admin;

  UUID user(String name) {
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())", id, name + "@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER'),(?,'CLIENT')", id, id);
    db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,?,'APPROVED')", id, id.toString(), name);
    db.update("INSERT INTO showcase_entitlements(user_id) VALUES(?)", id);
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')", Support.hash(name), id);
    return id;
  }

  MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String actor) {
    return request.cookie(new Cookie("gl_session", actor)).header("Origin", "http://localhost:3000")
        .header("X-Requested-With", "getlancer");
  }

  JsonNode getPage(String path, String actor) throws Exception {
    return json.readTree(mvc.perform(as(get("/api/v1" + path), actor)).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString());
  }

  UUID project(int n, UUID user, String verification) {
    UUID id = new UUID(0, n);
    String repository = "https://github.com/example/project-" + n;
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,approval_status,lifecycle_status,visibility,repository_url,updated_at) VALUES(?,?,?,?,'Private proof','Retained private proof project','SAAS','CRM','Java','Built this proof','DRAFT',?,'PRIVATE_CASE_STUDY',?,'2026-01-01T00:00:00Z')",
        id, user, id.toString(), "Retained project " + n, n % 2 == 0 ? "ARCHIVED" : "DRAFT", repository);
    if (verification != null)
      db.update("INSERT INTO repository_verifications(product_id,repository_url,challenge,status,requested_at,reviewed_at,reviewer_id) VALUES(?,?,?,?, '2026-01-01T00:00:00Z','2026-01-01T00:00:00Z',?)",
          id, repository, "private-challenge-" + n, verification, admin);
    return id;
  }

  UUID outcome(int n, UUID product, boolean awarded) {
    UUID id = new UUID(1, n);
    db.update("INSERT INTO inquiries(id,reference_product_id,developer_user_id,client_email,client_name,request_type,description,budget_band,timeline_band,current_status,email_confirmed_at,idempotency_key,request_hash,moderation_status,updated_at) VALUES(?,?,?,'client@example.test','Client','SIMILAR_BUILD','Client confirmed completion','USD_3K_10K','ONE_TO_THREE_MONTHS','COMPLETED',now(),?,?,'CLEAR','2026-01-01T00:00:00Z')",
        id, product, owner, UUID.randomUUID(), Support.hash("outcome-" + n));
    db.update("INSERT INTO inquiry_events(id,inquiry_id,event_type,actor_type) VALUES(?,?,'COMPLETED','CLIENT_TOKEN')", UUID.randomUUID(), id);
    if (awarded)
      db.update("INSERT INTO earned_capacity_awards(inquiry_id,user_id,admin_id,reason,created_at) VALUES(?,?,?,'Reviewed completed client work','2026-01-01T00:00:00Z')", id, owner, admin);
    return id;
  }

  List<JsonNode> all(String path, String actor, String collection, String key, int count) throws Exception {
    List<JsonNode> rows = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    for (int page = 0; ; page++) {
      assertTrue(page < 10, "bounded pages must terminate");
      JsonNode response = getPage(path + "?collection=" + collection + "&size=37&page=" + page, actor);
      JsonNode metadata = response.path("pages").path(collection);
      assertEquals(page, metadata.path("page").asInt());
      assertEquals(37, metadata.path("size").asInt());
      assertEquals(count, metadata.path("totalItems").asInt());
      assertTrue(response.path(collection).size() <= 37);
      for (JsonNode row : response.path(collection)) {
        assertTrue(ids.add(row.path(key).asText()), "equal timestamps must not repeat records across pages");
        rows.add(row);
      }
      if (!metadata.path("hasMore").asBoolean()) break;
    }
    assertEquals(count, rows.size());
    return rows;
  }

  @BeforeEach
  void prepare() {
    db.execute("TRUNCATE users CASCADE");
    db.execute("TRUNCATE rate_buckets");
    owner = user("trust-owner");
    outsider = user("trust-outsider");
    admin = user("trust-admin");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')", admin);
    db.update("UPDATE sessions SET mfa_verified=true,issued_at=now() WHERE user_id=?", admin);
  }

  @Test
  void ownerKeepsDraftsArchivesAndAwardsReachableBeyondOneHundred() throws Exception {
    UUID old = null;
    for (int n = 1; n <= 107; n++) old = project(n, owner, null);
    project(1000, outsider, "PENDING");
    for (int n = 1; n <= 106; n++) outcome(n, old, true);
    JsonNode initial = getPage("/developer/trust", "trust-owner");
    assertTrue(initial.path("projects").isArray());
    assertTrue(initial.path("awards").isArray());
    assertEquals(50, initial.path("projects").size());
    assertEquals(50, initial.path("awards").size());
    assertTrue(initial.path("pages").path("projects").path("hasMore").asBoolean());
    var projects = all("/developer/trust", "trust-owner", "projects", "id", 107);
    assertEquals(old.toString(), projects.get(106).path("id").asText());
    var awards = all("/developer/trust", "trust-owner", "awards", "inquiry_id", 106);
    assertEquals(new UUID(1, 106).toString(), awards.get(105).path("inquiry_id").asText());
    JsonNode selected = getPage("/developer/trust?collection=projects&page=2&size=50", "trust-owner");
    assertFalse(selected.has("awards"));
    assertFalse(selected.toString().contains("private-challenge-1000"));
    mvc.perform(as(post("/api/v1/developer/products/" + old + "/verification"), "trust-owner"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
    assertTrue(getPage("/developer/trust?collection=projects&page=2&size=50", "trust-owner")
        .path("projects").get(6).path("challenge").asText().startsWith("getlancer-proof-"));
    mvc.perform(as(post("/api/v1/developer/products/" + old + "/verification"), "trust-outsider"))
        .andExpect(status().isNotFound());
    assertEquals(0, getPage("/developer/trust?collection=projects", "trust-admin").path("projects").size());
  }

  @Test
  void retainedVerifiedEvidenceCanBeRevokedBeyondOneHundredWithoutExposingChallenges() throws Exception {
    UUID old = null;
    for (int n = 1; n <= 106; n++) old = project(n, owner, "VERIFIED");
    JsonNode initial = getPage("/admin/trust", "trust-admin");
    for (String collection : List.of("verified", "verifications", "eligibleOutcomes")) assertTrue(initial.path(collection).isArray());
    assertEquals(50, initial.path("verified").size());
    var verified = all("/admin/trust", "trust-admin", "verified", "product_id", 106);
    assertEquals(old.toString(), verified.get(105).path("product_id").asText());
    assertFalse(verified.toString().contains("private-challenge"));
    JsonNode selected = getPage("/admin/trust?collection=verified&page=1&size=100", "trust-admin");
    assertFalse(selected.has("verifications"));
    assertFalse(selected.has("eligibleOutcomes"));
    mvc.perform(as(post("/api/v1/admin/verifications/" + old + "/revoke"), "trust-admin")
            .contentType("application/json").content("{\"reason\":\"Repository control evidence was revoked after review.\"}"))
        .andExpect(status().isOk());
    assertEquals("REJECTED", db.queryForObject("SELECT status FROM repository_verifications WHERE product_id=?", String.class, old));
    assertEquals(105, getPage("/admin/trust?collection=verified", "trust-admin").path("pages").path("verified").path("totalItems").asInt());
    assertEquals(1, db.queryForObject("SELECT count(*) FROM moderation_actions WHERE target_id=? AND action='REPOSITORY_REVOKE'", Integer.class, old));
  }

  @Test
  void pendingRequestsAndEligibleOutcomesHaveIndependentBoundedContinuation() throws Exception {
    UUID product = null;
    for (int n = 1; n <= 104; n++) product = project(n, owner, "PENDING");
    for (int n = 1; n <= 103; n++) outcome(n, product, false);
    assertEquals(104, all("/admin/trust", "trust-admin", "verifications", "product_id", 104).size());
    assertEquals(103, all("/admin/trust", "trust-admin", "eligibleOutcomes", "id", 103).size());
    JsonNode selected = getPage("/admin/trust?collection=verifications&page=1&size=100", "trust-admin");
    assertEquals("private-challenge-101", selected.path("verifications").get(0).path("challenge").asText());
    assertFalse(selected.has("verified"));
    assertFalse(selected.has("eligibleOutcomes"));
    mvc.perform(get("/api/v1/admin/trust?collection=verifications&page=1")).andExpect(status().is4xxClientError());
    mvc.perform(as(get("/api/v1/admin/trust?collection=verifications&page=1"), "trust-owner")).andExpect(status().isForbidden());
    db.update("UPDATE products SET approval_status='APPROVED',lifecycle_status='ACTIVE',visibility='PUBLIC' WHERE id=?", product);
    String publicBody = mvc.perform(get("/api/v1/products/" + product)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertFalse(publicBody.contains("private-challenge"));
  }

  @Test
  void everyPageRechecksCurrentAuthorityAndRejectsMalformedSelectors() throws Exception {
    project(1, owner, "PENDING");
    getPage("/developer/trust?collection=projects", "trust-owner");
    getPage("/admin/trust?collection=verifications", "trust-admin");
    for (String query : List.of("collection=constructor", "collection=challenge", "collection=projects&size=101", "collection=projects&size=0", "collection=projects&page=-1", "collection=projects&page=1.5"))
      mvc.perform(as(get("/api/v1/developer/trust?" + query), "trust-owner")).andExpect(status().isBadRequest());
    mvc.perform(as(get("/api/v1/admin/trust?collection=projects"), "trust-admin")).andExpect(status().isBadRequest());
    db.update("DELETE FROM user_roles WHERE user_id=? AND role='DEVELOPER'", owner);
    mvc.perform(as(get("/api/v1/developer/trust?collection=projects&page=1"), "trust-owner")).andExpect(status().isForbidden());
    db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?", admin);
    mvc.perform(as(get("/api/v1/admin/trust?collection=verifications&page=1"), "trust-admin")).andExpect(status().isForbidden());
    db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?", admin);
    db.update("DELETE FROM user_roles WHERE user_id=? AND role='ADMIN'", admin);
    mvc.perform(as(get("/api/v1/admin/trust?collection=verified&page=1"), "trust-admin")).andExpect(status().isForbidden());
    db.update("UPDATE sessions SET expires_at=now()-interval '1 second' WHERE user_id=?", outsider);
    mvc.perform(as(get("/api/v1/developer/trust?collection=awards&page=1"), "trust-outsider")).andExpect(status().isUnauthorized());
  }
}
