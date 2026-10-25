package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
class BusinessIntegrationTest {
  @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  UUID owner, manager, outsider, builder, admin, business, product, team;

  UUID user(String name, boolean approved) {
    UUID u = UUID.randomUUID();
    db.update(
        "INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",
        u,
        name + "@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER'),(?,'CLIENT')", u, u);
    db.update(
        "INSERT INTO"
            + " developer_profiles(user_id,slug,display_name,approval_status,availability_status)"
            + " VALUES(?,?,?,?, 'AVAILABLE_NOW')",
        u,
        name,
        name,
        approved ? "APPROVED" : "DRAFT");
    db.update("INSERT INTO showcase_entitlements(user_id) VALUES(?)", u);
    db.update(
        "INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",
        Support.hash(name),
        u);
    return u;
  }

  MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder req, String actor) {
    return req.cookie(new Cookie("gl_session", actor));
  }

  MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, String actor, String body) {
    return as(req, actor)
        .header("Origin", "http://localhost:3000")
        .header("X-Requested-With", "getlancer")
        .contentType("application/json")
        .content(body);
  }

  String root() {
    return "/api/v1/businesses/" + business;
  }

  String brief(String status) {
    return "{\"title\":\"Private portal\",\"description\":\"Confidential customer approval"
               + " workflow\",\"category\":\"CRM\",\"technology\":\"React\",\"budget\":\"10k\",\"timeline\":\"2"
               + " months\",\"availableOnly\":true,\"repositoryVerifiedOnly\":false,\"status\":\""
        + status
        + "\"}";
  }

  UUID request(String status) throws Exception {
    mvc.perform(json(post(root() + "/requests"), "owner", brief(status)))
        .andExpect(status().isOk());
    return db.queryForObject(
        "SELECT id FROM business_requests ORDER BY created_at DESC LIMIT 1", UUID.class);
  }

  String candidate() {
    return "{\"kind\":\"BUILDER\",\"targetId\":\""
        + builder
        + "\",\"reason\":\"Approved CRM and React evidence\"}";
  }

  @BeforeEach
  void prepare() throws Exception {
    db.execute("TRUNCATE users CASCADE");
    db.execute("TRUNCATE rate_buckets");
    owner = user("owner", false);
    manager = user("manager", false);
    outsider = user("outsider", false);
    builder = user("builder", true);
    admin = user("admin", false);
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')", admin);
    db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?", admin);
    product = UUID.randomUUID();
    db.update(
        "INSERT INTO"
            + " products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,approval_status,lifecycle_status,visibility)"
            + " VALUES(?,?,?,'Portal','Customer portal','Working portal','SAAS','CRM','React,"
            + " Java','Built it','APPROVED','ACTIVE','PUBLIC')",
        product,
        builder,
        product.toString());
    team = UUID.randomUUID();
    db.update(
        "INSERT INTO teams(id,slug,name,summary,availability,project_range,owner_id)"
            + " VALUES(?,?,'Studio','Customer software studio','AVAILABLE_NOW','10k',?)",
        team,
        team.toString(),
        builder);
    db.update(
        "INSERT INTO team_members(team_id,user_id,role,membership_type)"
            + " VALUES(?,?,'OWNER','PERMANENT')",
        team,
        builder);
    db.update(
        "INSERT INTO team_projects(team_id,product_id,consented_by) VALUES(?,?,?)",
        team,
        product,
        builder);
    mvc.perform(
            json(
                post("/api/v1/businesses"),
                "owner",
                "{\"name\":\"Business\",\"summary\":\"Customer software hiring\"}"))
        .andExpect(status().isOk());
    business = db.queryForObject("SELECT id FROM businesses", UUID.class);
  }

  @Test
  void invitationConsentExpiryOwnerProtectionAndRevocation() throws Exception {
    mvc.perform(as(get(root()), "manager")).andExpect(status().isNotFound());
    mvc.perform(
            json(post(root() + "/invitations"), "owner", "{\"email\":\"manager@example.test\"}"))
        .andExpect(status().isOk());
    UUID invite = db.queryForObject("SELECT id FROM business_invitations", UUID.class);
    mvc.perform(
            json(
                post("/api/v1/business-invitations/" + invite + "/respond"),
                "outsider",
                "{\"action\":\"ACCEPT\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(as(get(root()), "manager")).andExpect(status().isNotFound());
    mvc.perform(
            json(
                post("/api/v1/business-invitations/" + invite + "/respond"),
                "manager",
                "{\"action\":\"ACCEPT\"}"))
        .andExpect(status().isOk());
    mvc.perform(as(get(root()), "manager")).andExpect(jsonPath("$.myRole").value("HIRING_MANAGER"));
    mvc.perform(
            json(
                put(root()), "manager", "{\"name\":\"Changed\",\"summary\":\"Unauthorized edit\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(json(delete(root() + "/members/" + owner), "owner", "{}"))
        .andExpect(status().isConflict());
    mvc.perform(json(delete(root() + "/members/" + manager), "owner", "{}"))
        .andExpect(status().isOk());
    mvc.perform(as(get(root()), "manager")).andExpect(status().isNotFound());
    mvc.perform(
            json(post(root() + "/invitations"), "owner", "{\"email\":\"manager@example.test\"}"))
        .andExpect(status().isOk());
    db.update(
        "UPDATE business_invitations SET expires_at=now()-interval '1 day' WHERE status='PENDING'");
    invite =
        db.queryForObject("SELECT id FROM business_invitations WHERE status='PENDING'", UUID.class);
    mvc.perform(
            json(
                post("/api/v1/business-invitations/" + invite + "/respond"),
                "manager",
                "{\"action\":\"ACCEPT\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void privateBriefTenantIsolationAndLifecycle() throws Exception {
    UUID r = request("DRAFT");
    String path = root() + "/requests/" + r;
    mvc.perform(as(get(root() + "/requests"), "outsider")).andExpect(status().isNotFound());
    mvc.perform(as(get(path + "/matches"), "owner")).andExpect(status().isConflict());
    mvc.perform(as(get("/api/v1/admin/concierge"), "admin"))
        .andExpect(jsonPath("$.items.length()").value(0));
    mvc.perform(json(post(path + "/concierge"), "owner", "{}")).andExpect(status().isConflict());
    mvc.perform(json(put(path), "owner", brief("OPEN"))).andExpect(status().isOk());
    mvc.perform(as(get(path + "/matches"), "owner"))
        .andExpect(jsonPath("$.items.length()").value(2));
    mvc.perform(json(put(path), "owner", brief("DRAFT"))).andExpect(status().isConflict());
    mvc.perform(json(post(path + "/concierge"), "owner", "{}")).andExpect(status().isOk());
    mvc.perform(json(put(path), "owner", brief("CLOSED"))).andExpect(status().isOk());
    mvc.perform(json(put(path), "owner", brief("OPEN"))).andExpect(status().isConflict());
    assertEquals(
        "CANCELLED", db.queryForObject("SELECT status FROM concierge_requests", String.class));
    mvc.perform(
            json(
                post("/api/v1/businesses"),
                "outsider",
                "{\"name\":\"Other\",\"summary\":\"Another business workspace\"}"))
        .andExpect(status().isOk());
    UUID other =
        db.queryForObject("SELECT id FROM businesses WHERE owner_id=?", UUID.class, outsider);
    mvc.perform(
            json(put("/api/v1/businesses/" + other + "/requests/" + r), "outsider", brief("OPEN")))
        .andExpect(status().isNotFound());
    mvc.perform(get(root() + "/requests")).andExpect(status().isUnauthorized());
  }

  @Test
  void availableOnlyTeamMatchingRecognizesLegacyLabelsAndCanonicalProfileSubmissions() throws Exception {
    UUID r = request("OPEN");
    String path = root() + "/requests/" + r + "/matches";
    for (String legacy : List.of("Open to projects", "Available now", "AVAILABLE_NOW", "  aVailable now  ")) {
      // Existing rows have not been migrated or rewritten through the profile API.
      db.update("UPDATE teams SET availability=? WHERE id=?", legacy, team);
      String response = mvc.perform(as(get(path), "owner"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items[?(@.kind == 'TEAM')].targetId").value(org.hamcrest.Matchers.contains(team.toString())))
          .andReturn().getResponse().getContentAsString();
      for (var candidate : mapper.readTree(response).get("items"))
        if (candidate.get("kind").asText().equals("TEAM"))
          assertTrue(candidate.get("reasons").toString().contains("Available now (self-reported)"));
      assertEquals(legacy, db.queryForObject("SELECT availability FROM teams WHERE id=?", String.class, team));
    }
    for (String unavailable : List.of("Unavailable", "NOT_ACCEPTING", "LIMITED", "Available after summer", "Ask after our R&D milestone")) {
      db.update("UPDATE teams SET availability=? WHERE id=?", unavailable, team);
      mvc.perform(as(get(path), "owner"))
          .andExpect(jsonPath("$.items[?(@.kind == 'TEAM')]").isEmpty());
      db.update("UPDATE business_requests SET available_only=false WHERE id=?", r);
      String response = mvc.perform(as(get(path), "owner"))
          .andExpect(jsonPath("$.items[?(@.kind == 'TEAM')].availability").value(org.hamcrest.Matchers.contains(unavailable)))
          .andReturn().getResponse().getContentAsString();
      for (var candidate : mapper.readTree(response).get("items"))
        if (candidate.get("kind").asText().equals("TEAM"))
          for (var reason : candidate.get("reasons"))
            assertNotEquals("Available now (self-reported)", reason.asText());
      db.update("UPDATE business_requests SET available_only=true WHERE id=?", r);
    }
    // The ordinary team form's default canonical status must reach the same matcher.
    mvc.perform(json(put("/api/v1/teams/" + team), "builder",
        "{\"name\":\"Studio\",\"summary\":\"Customer software studio\",\"availability\":\"AVAILABLE_NOW\",\"projectRange\":\"10k\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.availability").value("AVAILABLE_NOW"));
    mvc.perform(as(get(path), "owner"))
        .andExpect(jsonPath("$.items[?(@.kind == 'TEAM')].targetId").value(org.hamcrest.Matchers.contains(team.toString())));
    mvc.perform(json(put("/api/v1/teams/" + team), "builder",
        "{\"name\":\"Studio\",\"summary\":\"Customer software studio\",\"availability\":\"NOT_ACCEPTING\",\"projectRange\":\"10k\"}"))
        .andExpect(status().isOk());
    mvc.perform(as(get(path), "owner"))
        .andExpect(jsonPath("$.items[?(@.kind == 'TEAM')]").isEmpty());
  }

  @Test
  void matchingReflectsCurrentProofMembershipAndVerification() throws Exception {
    UUID r = request("OPEN");
    String path = root() + "/requests/" + r + "/matches";
    mvc.perform(as(get(path), "owner")).andExpect(jsonPath("$.items.length()").value(2));
    db.update("UPDATE products SET visibility='PRIVATE_CASE_STUDY' WHERE id=?", product);
    mvc.perform(as(get(path), "owner")).andExpect(jsonPath("$.items.length()").value(0));
    db.update("UPDATE products SET visibility='PUBLIC' WHERE id=?", product);
    db.update("UPDATE developer_profiles SET approval_status='SUSPENDED' WHERE user_id=?", builder);
    mvc.perform(as(get(path), "owner")).andExpect(jsonPath("$.items.length()").value(0));
    db.update("UPDATE developer_profiles SET approval_status='APPROVED' WHERE user_id=?", builder);
    db.update(
        "UPDATE team_members SET role='MEMBER',membership_type='CONTRACT',expires_at=now()-interval"
            + " '1 day' WHERE user_id=?",
        builder);
    mvc.perform(as(get(path), "owner")).andExpect(jsonPath("$.items.length()").value(1));
    db.update("UPDATE business_requests SET repository_verified_only=true WHERE id=?", r);
    mvc.perform(as(get(path), "owner")).andExpect(jsonPath("$.items.length()").value(0));
    db.update(
        "UPDATE products SET repository_url='https://github.com/example/project' WHERE id=?",
        product);
    db.update(
        "INSERT INTO repository_verifications(product_id,repository_url,challenge,status)"
            + " VALUES(?,'https://github.com/example/project','challenge','VERIFIED')",
        product);
    mvc.perform(as(get(path), "owner")).andExpect(jsonPath("$.items.length()").value(1));
    db.update(
        "UPDATE products SET repository_url='https://github.com/example/changed' WHERE id=?",
        product);
    mvc.perform(as(get(path), "owner")).andExpect(jsonPath("$.items.length()").value(0));
  }

  @Test
  void savedTalentAndShortlistAreTenantScopedIdempotentAndEligibilityChecked() throws Exception {
    UUID r = request("OPEN");
    mvc.perform(json(post(root() + "/talent-lists"), "owner", "{\"name\":\"Portal builders\"}"))
        .andExpect(status().isOk());
    UUID list = db.queryForObject("SELECT id FROM talent_lists", UUID.class);
    String entries = root() + "/talent-lists/" + list + "/entries",
        shortlist = root() + "/requests/" + r + "/shortlist";
    for (int i = 0; i < 2; i++) {
      mvc.perform(json(post(entries), "owner", candidate())).andExpect(status().isOk());
      mvc.perform(json(post(shortlist), "owner", candidate())).andExpect(status().isOk());
    }
    assertEquals(1, db.queryForObject("SELECT count(*) FROM talent_entries", Integer.class));
    assertEquals(1, db.queryForObject("SELECT count(*) FROM request_shortlist", Integer.class));
    mvc.perform(as(get(entries), "outsider")).andExpect(status().isNotFound());
    mvc.perform(
            json(
                post(entries), "owner", "{\"kind\":\"BUILDER\",\"targetId\":\"" + outsider + "\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(as(get(entries), "owner"))
        .andExpect(jsonPath("$.items[0].candidate.name").value("builder"));
    db.update("UPDATE products SET lifecycle_status='SUSPENDED' WHERE id=?", product);
    mvc.perform(json(post(shortlist), "owner", candidate())).andExpect(status().isNotFound());
    UUID entry = db.queryForObject("SELECT id FROM talent_entries", UUID.class);
    mvc.perform(json(delete(entries + "/" + entry), "owner", "{}")).andExpect(status().isOk());
  }

  @Test
  void conciergeRequiresOptInMfaAndReasonedCurrentRecommendation() throws Exception {
    UUID r = request("OPEN");
    String request = root() + "/requests/" + r;
    mvc.perform(as(get("/api/v1/admin/concierge"), "admin"))
        .andExpect(jsonPath("$.items.length()").value(0));
    mvc.perform(json(post(request + "/concierge"), "owner", "{}")).andExpect(status().isOk());
    UUID c = db.queryForObject("SELECT id FROM concierge_requests", UUID.class);
    String path = "/api/v1/admin/concierge/" + c;
    mvc.perform(as(get(path + "/matches"), "owner")).andExpect(status().isForbidden());
    db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?", admin);
    mvc.perform(as(get("/api/v1/admin/concierge"), "admin")).andExpect(status().isForbidden());
    db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?", admin);
    mvc.perform(json(patch(path), "admin", "{\"status\":\"FULFILLED\"}"))
        .andExpect(status().isConflict());
    mvc.perform(json(patch(path), "admin", "{\"status\":\"IN_PROGRESS\"}"))
        .andExpect(status().isOk());
    mvc.perform(json(patch(path), "admin", "{\"status\":\"FULFILLED\"}"))
        .andExpect(status().isConflict());
    mvc.perform(
            json(
                post(path + "/shortlist"),
                "admin",
                candidate().replace("Approved CRM and React evidence", "")))
        .andExpect(status().isBadRequest());
    mvc.perform(json(post(request + "/shortlist"), "owner", candidate()))
        .andExpect(status().isOk());
    mvc.perform(json(post(path + "/shortlist"), "admin", candidate())).andExpect(status().isOk());
    assertEquals(1, db.queryForObject("SELECT count(*) FROM request_shortlist", Integer.class));
    db.update("UPDATE products SET visibility='PRIVATE_CASE_STUDY' WHERE id=?", product);
    mvc.perform(json(patch(path), "admin", "{\"status\":\"FULFILLED\"}"))
        .andExpect(status().isConflict());
    db.update("UPDATE products SET visibility='PUBLIC' WHERE id=?", product);
    mvc.perform(json(patch(path), "admin", "{\"status\":\"FULFILLED\"}"))
        .andExpect(status().isOk());
    mvc.perform(as(get(request + "/shortlist"), "owner"))
        .andExpect(jsonPath("$.items[0].source").value("CONCIERGE"));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM inquiries", Integer.class));
  }

  @Test
  void unverifiedAndSuspendedAccountsCannotAccessBusinessRecords() throws Exception {
    db.update("UPDATE users SET email_verified_at=null WHERE id=?", owner);
    mvc.perform(as(get(root()), "owner")).andExpect(status().isForbidden());
    db.update(
        "UPDATE users SET email_verified_at=now(),account_status='SUSPENDED' WHERE id=?", owner);
    mvc.perform(as(get(root()), "owner")).andExpect(status().isUnauthorized());
  }

  @Test
  void exportIncludesOwnBusinessDataWithoutOtherTenantsOrTeamNotes() throws Exception {
    request("OPEN");
    mvc.perform(as(get("/api/v1/me/export"), "owner"))
        .andExpect(jsonPath("$.businessRequests.length()").value(1))
        .andExpect(jsonPath("$.businessMemberships.length()").value(1));
    mvc.perform(as(get("/api/v1/me/export"), "outsider"))
        .andExpect(jsonPath("$.businessRequests.length()").value(0))
        .andExpect(jsonPath("$.businessMemberships.length()").value(0));
  }

  @Test
  void exportIncludesTrustAndStaffingWithoutChallengesOrOtherUsersData() throws Exception {
    db.update("INSERT INTO repository_verifications(product_id,repository_url,challenge) VALUES(?,?,'private-challenge')", product, "https://github.com/example/proof");
    db.update("INSERT INTO team_staffing(id,team_id,user_id,project_label,skills,ends_at) VALUES(?,?,?,'Internal project','Java',now()+interval '1 day')", UUID.randomUUID(), team, builder);
    mvc.perform(as(get("/api/v1/me/export"), "builder"))
        .andExpect(jsonPath("$.repositoryVerifications.length()").value(1))
        .andExpect(jsonPath("$.teamStaffing.length()").value(1))
        .andExpect(jsonPath("$.earnedCapacityAwards").isArray())
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-challenge"))));
    mvc.perform(as(get("/api/v1/me/export"), "outsider"))
        .andExpect(jsonPath("$.repositoryVerifications.length()").value(0))
        .andExpect(jsonPath("$.teamStaffing.length()").value(0));
  }
  UUID retainedBrief(String title, String status) {
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO business_requests(id,business_id,created_by,title,description,category,technology,budget,timeline,status)"
        + " VALUES(?,?,?,?,'A retained private customer approval workflow','CRM','React','10k','2 months',?)",
        id, business, owner, title, status);
    return id;
  }

  void assertAllPages(String path, String actor, String collection, String metadata, int expected, String identity) throws Exception {
    var ids = new HashSet<String>();
    int page = 0;
    boolean more;
    do {
      var response = mapper.readTree(mvc.perform(as(get(path).param("page", String.valueOf(page)).param("size", "37"), actor))
          .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
      var information = metadata == null ? response : response.path(metadata);
      assertEquals(page, information.path("page").asInt());
      assertEquals(37, information.path("size").asInt());
      assertEquals(expected, information.path("totalItems").asInt());
      assertEquals((expected + 36) / 37, information.path("totalPages").asInt());
      assertTrue(response.path(collection).size() <= 37);
      for (var row : response.path(collection)) {
        assertFalse(row.path(identity).asText().isBlank());
        assertTrue(ids.add(row.path(identity).asText()), "Pages must not repeat retained records");
      }
      more = information.path("hasMore").asBoolean();
      page++;
      assertTrue(page <= (expected + 36) / 37 + 1, "Pagination must terminate");
    } while (more);
    assertEquals(expected, ids.size());
  }

  @Test
  void everyRetainedBusinessCollectionContinuesBeyondOneHundredWithoutTenantLeakage() throws Exception {
    for (int i = 0; i < 105; i++) {
      UUID member = user("retained-member-" + i, false), other = UUID.randomUUID();
      db.update("INSERT INTO business_members(business_id,user_id,role) VALUES(?,?,'HIRING_MANAGER')", business, member);
      db.update("INSERT INTO businesses(id,name,summary,owner_id) VALUES(?,'Retained business','Private retained workspace',?)", other, owner);
      db.update("INSERT INTO business_members(business_id,user_id,role) VALUES(?,?,'OWNER')", other, owner);
      db.update("INSERT INTO business_invitations(id,business_id,user_id,status) VALUES(?,?,?,'DECLINED')", UUID.randomUUID(), business, member);
      db.update("INSERT INTO business_invitations(id,business_id,user_id,status) VALUES(?,?,?,'DECLINED')", UUID.randomUUID(), other, owner);
      db.update("INSERT INTO business_activity(id,business_id,actor_id,action,detail) VALUES(?,?,?,'REQUEST_UPDATED','Retained audit history')", UUID.randomUUID(), business, owner);
      db.update("INSERT INTO talent_lists(id,business_id,name,created_by) VALUES(?,?,'Retained list',?)", UUID.randomUUID(), business, owner);
      retainedBrief("Retained request " + i, "CLOSED");
    }
    assertAllPages("/api/v1/businesses", "owner", "items", "itemsPage", 106, "id");
    assertAllPages("/api/v1/businesses", "owner", "invitations", "invitationsPage", 105, "id");
    assertAllPages(root(), "owner", "members", "membersPage", 106, "userId");
    assertAllPages(root(), "owner", "invitations", "invitationsPage", 105, "id");
    assertAllPages(root(), "owner", "activity", "activityPage", 106, "id");
    assertAllPages(root() + "/requests", "owner", "items", null, 105, "id");
    assertAllPages(root() + "/talent-lists", "owner", "items", null, 105, "id");
    mvc.perform(as(get(root() + "/requests?page=2&size=37"), "outsider")).andExpect(status().isNotFound());
    mvc.perform(as(get(root() + "?size=101"), "owner")).andExpect(status().isBadRequest());
    mvc.perform(as(get(root() + "/requests?page=-1"), "owner")).andExpect(status().isBadRequest());
    mvc.perform(as(get(root() + "/talent-lists?page=100001"), "owner")).andExpect(status().isBadRequest());
    mvc.perform(as(get(root() + "/requests?page=99&size=100"), "owner"))
        .andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.totalItems").value(105));
    // A hiring manager's invitation page is empty and carries no owner-only count.
    db.update("INSERT INTO business_members(business_id,user_id,role) VALUES(?,?,'HIRING_MANAGER')", business, manager);
    mvc.perform(as(get(root() + "?page=2&size=37"), "manager"))
        .andExpect(jsonPath("$.invitations.length()").value(0)).andExpect(jsonPath("$.invitationsPage.totalItems").value(0));
  }

  @Test
  void savedAndShortlistedUnavailableRecordsStayReachableAndFulfillmentSeesProofPastPageOneHundred() throws Exception {
    UUID request = retainedBrief("Retained shortlist", "OPEN"), list = UUID.randomUUID(), concierge = UUID.randomUUID();
    db.update("INSERT INTO talent_lists(id,business_id,name,created_by) VALUES(?,?,'Large retained list',?)", list, business, owner);
    // Eligible concierge proof is intentionally older than one hundred unavailable records.
    db.update("INSERT INTO request_shortlist(id,request_id,kind,builder_id,created_by,reason,source,created_at)"
        + " VALUES(?,?,'BUILDER',?,?,'Current approved proof','CONCIERGE',now()-interval '10 days')", UUID.randomUUID(), request, builder, owner);
    for (int i = 0; i < 105; i++) {
      UUID unavailable = user("unavailable-proof-" + i, false);
      db.update("INSERT INTO talent_entries(id,list_id,kind,builder_id,created_by) VALUES(?,?,'BUILDER',?,?)", UUID.randomUUID(), list, unavailable, owner);
      db.update("INSERT INTO request_shortlist(id,request_id,kind,builder_id,created_by,reason,source)"
          + " VALUES(?,?,'BUILDER',?,?,'Previously saved evidence','BUSINESS')", UUID.randomUUID(), request, unavailable, owner);
    }
    String entries = root() + "/talent-lists/" + list + "/entries", shortlist = root() + "/requests/" + request + "/shortlist";
    assertAllPages(entries, "owner", "items", null, 105, "id");
    assertAllPages(shortlist, "owner", "items", null, 106, "id");
    mvc.perform(as(get(entries + "?page=2&size=37"), "owner"))
        .andExpect(jsonPath("$.items[0].unavailable").value(true));
    mvc.perform(as(get(shortlist + "?page=2&size=37"), "outsider")).andExpect(status().isNotFound());
    db.update("INSERT INTO concierge_requests(id,request_id,requested_by,status) VALUES(?,?,?,'IN_PROGRESS')", concierge, request, owner);
    db.update("UPDATE products SET visibility='PRIVATE_CASE_STUDY' WHERE id=?", product);
    mvc.perform(json(patch("/api/v1/admin/concierge/" + concierge), "admin", "{\"status\":\"FULFILLED\"}"))
        .andExpect(status().isConflict());
    db.update("UPDATE products SET visibility='PUBLIC' WHERE id=?", product);
    mvc.perform(json(patch("/api/v1/admin/concierge/" + concierge), "admin", "{\"status\":\"FULFILLED\"}"))
        .andExpect(status().isOk());
    assertEquals(106, db.queryForObject("SELECT count(*) FROM request_shortlist WHERE request_id=?", Integer.class, request));
  }

  @Test
  void oldFulfilledSourcingCannotHideNewWorkAndHistoryIncludesCompletedClosedBriefs() throws Exception {
    for (int i = 0; i < 105; i++) {
      UUID history = retainedBrief("Completed sourcing " + i, i % 2 == 0 ? "CLOSED" : "OPEN");
      db.update("INSERT INTO concierge_requests(id,request_id,requested_by,status,created_at) VALUES(?,?,?,'FULFILLED',now()-interval '10 days')",
          UUID.randomUUID(), history, owner);
      UUID active = retainedBrief("Sourcing in progress " + i, "OPEN");
      db.update("INSERT INTO concierge_requests(id,request_id,requested_by,status) VALUES(?,?,?,'IN_PROGRESS')", UUID.randomUUID(), active, owner);
    }
    UUID newest = retainedBrief("Newest requested sourcing", "OPEN");
    db.update("INSERT INTO concierge_requests(id,request_id,requested_by,status) VALUES(?,?,?,'REQUESTED')", UUID.randomUUID(), newest, owner);
    mvc.perform(as(get("/api/v1/admin/concierge?size=1"), "admin"))
        .andExpect(jsonPath("$.items[0].requestId").value(newest.toString()))
        .andExpect(jsonPath("$.items[0].status").value("REQUESTED"))
        .andExpect(jsonPath("$.itemsPage.totalItems").value(106))
        .andExpect(jsonPath("$.historyPage.totalItems").value(105));
    assertAllPages("/api/v1/admin/concierge", "admin", "items", "itemsPage", 106, "id");
    assertAllPages("/api/v1/admin/concierge", "admin", "history", "historyPage", 105, "id");
    db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?", admin);
    mvc.perform(as(get("/api/v1/admin/concierge?page=2&size=37"), "admin")).andExpect(status().isForbidden());
    mvc.perform(as(get("/api/v1/admin/concierge?page=2&size=37"), "owner")).andExpect(status().isForbidden());
    assertEquals(211, db.queryForObject("SELECT count(*) FROM concierge_requests", Integer.class));
  }

}
