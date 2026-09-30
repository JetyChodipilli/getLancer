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
class TeamsIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  UUID owner, client, applicant, product, team;

  UUID user(String name) {
    UUID id = UUID.randomUUID();
    db.update(
        "INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?, 'unused',now())",
        id,
        name + "@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER'),(?,'CLIENT')", id, id);
    db.update(
        "INSERT INTO developer_profiles(user_id,slug,display_name,approval_status)"
            + " VALUES(?,?,?,'APPROVED')",
        id,
        id.toString(),
        name);
    db.update("INSERT INTO showcase_entitlements(user_id) VALUES(?)", id);
    db.update(
        "INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",
        Support.hash(name),
        id);
    return id;
  }

  MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, String actor, String body) {
    return req.cookie(new Cookie("gl_session", actor))
        .header("Origin", "http://localhost:3000")
        .header("X-Requested-With", "getlancer")
        .contentType("application/json")
        .content(body);
  }

  @BeforeEach
  void prepare() throws Exception {
    db.execute("TRUNCATE users CASCADE");
    db.execute("TRUNCATE rate_buckets");
    owner = user("owner");
    client = user("client");
    applicant = user("applicant");
    product = UUID.randomUUID();
    db.update(
        "INSERT INTO"
            + " products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,approval_status,lifecycle_status,visibility)"
            + " VALUES(?,?,?,'Portal','Customer portal','A working customer"
            + " portal','SAAS','CRM','React','Built it','APPROVED','ACTIVE','PUBLIC')",
        product,
        owner,
        product.toString());
    mvc.perform(
            json(
                post("/api/v1/teams"),
                "owner",
                "{\"name\":\"Studio\",\"summary\":\"A team that builds customer"
                    + " software\",\"availability\":\"Available\",\"projectRange\":\"5k-10k\"}"))
        .andExpect(status().isOk());
    team = db.queryForObject("SELECT id FROM teams", UUID.class);
  }

  @Test
  void publicConsentRecruitmentAndExpiry() throws Exception {
    String base = "/api/v1/teams/" + team;
    mvc.perform(json(post(base + "/projects"), "owner", "{\"productId\":\"" + product + "\"}"))
        .andExpect(status().isOk());
    mvc.perform(get(base)).andExpect(jsonPath("$.projects.length()").value(1));
    mvc.perform(
            json(
                post(base + "/roles"),
                "owner",
                "{\"title\":\"React builder\",\"description\":\"Build accessible client"
                    + " experiences\",\"skills\":\"React\",\"contractType\":\"CONTRACT\",\"compensationBand\":\"5k\"}"))
        .andExpect(status().isOk());
    UUID role = db.queryForObject("SELECT id FROM team_roles", UUID.class);
    mvc.perform(
            json(
                post(base + "/roles/" + role + "/applications"),
                "applicant",
                "{\"message\":\"I build React products\"}"))
        .andExpect(status().isOk());
    UUID application = db.queryForObject("SELECT id FROM team_applications", UUID.class);
    mvc.perform(
            json(
                post(base + "/applications/" + application + "/decision"),
                "owner",
                "{\"status\":\"INVITED\"}"))
        .andExpect(status().isOk());
    assertEquals(1, db.queryForObject("SELECT count(*) FROM team_members", Integer.class));
    UUID invite = db.queryForObject("SELECT id FROM team_invitations", UUID.class);
    mvc.perform(
            json(
                post("/api/v1/team-invitations/" + invite + "/respond"),
                "applicant",
                "{\"action\":\"ACCEPT\"}"))
        .andExpect(status().isOk());
    mvc.perform(get(base + "/workspace").cookie(new Cookie("gl_session", "applicant")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.leads.length()").value(0));
    db.update(
        "UPDATE team_members SET expires_at=now()-interval '1 day' WHERE user_id=?", applicant);
    mvc.perform(get(base + "/workspace").cookie(new Cookie("gl_session", "applicant")))
        .andExpect(status().isForbidden());
    db.update("UPDATE products SET lifecycle_status='ARCHIVED' WHERE id=?", product);
    mvc.perform(get(base)).andExpect(jsonPath("$.projects.length()").value(0));
  }

  @Test
  void realLeadSqlPrivacyAndClientProposalResponse() throws Exception {
    String base = "/api/v1/teams/" + team;
    String body =
        "{\"title\":\"Client portal\",\"description\":\"Build a portal for"
            + " approvals\",\"budget\":\"10k\",\"timeline\":\"2 months\"}";
    mvc.perform(json(post(base + "/leads"), "owner", body)).andExpect(status().isForbidden());
    mvc.perform(json(post(base + "/leads"), "client", body)).andExpect(status().isOk());
    UUID lead = db.queryForObject("SELECT id FROM team_leads", UUID.class);
    mvc.perform(
            json(
                patch(base + "/leads/" + lead),
                "owner",
                "{\"status\":\"INTERESTED\",\"note\":\"Private capacity note\"}"))
        .andExpect(status().isOk());
    mvc.perform(json(patch(base + "/leads/" + lead), "owner", "{\"status\":\"PROPOSAL_SENT\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/me/teams").cookie(new Cookie("gl_session", "client")))
        .andExpect(jsonPath("$.requests[0].notes.length()").value(0));
    mvc.perform(
            json(
                post(base + "/leads/" + lead + "/respond"), "applicant", "{\"action\":\"ACCEPT\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            json(post(base + "/leads/" + lead + "/respond"), "client", "{\"action\":\"ACCEPT\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("WON"));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM reviews", Integer.class));
    db.update("UPDATE teams SET status='SUSPENDED' WHERE id=?", team);
    mvc.perform(get(base)).andExpect(status().isNotFound());
    mvc.perform(json(post(base + "/leads"), "client", body)).andExpect(status().isConflict());
  }

  @Test
  void suspendedProofDoesNotUnlockTeamCreationAndAccountExportKeepsClientOwnership()
      throws Exception {
    db.update("UPDATE products SET lifecycle_status='SUSPENDED' WHERE id=?", product);
    mvc.perform(
            json(
                post("/api/v1/teams"),
                "owner",
                "{\"name\":\"Other\",\"summary\":\"A second software"
                    + " team\",\"availability\":\"AVAILABLE_NOW\",\"projectRange\":\"5k\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            json(
                post("/api/v1/teams/" + team + "/leads"),
                "client",
                "{\"title\":\"Portal\",\"description\":\"A private customer"
                    + " workflow\",\"budget\":\"10k\",\"timeline\":\"2 months\"}"))
        .andExpect(status().isOk());
    UUID lead = db.queryForObject("SELECT id FROM team_leads", UUID.class);
    mvc.perform(
            json(
                patch("/api/v1/teams/" + team + "/leads/" + lead),
                "owner",
                "{\"note\":\"Internal pricing deliberation\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/me/export").cookie(new Cookie("gl_session", "client")))
        .andExpect(jsonPath("$.teamRequests.length()").value(1))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Internal pricing deliberation"))));
    mvc.perform(get("/api/v1/me/export").cookie(new Cookie("gl_session", "applicant")))
        .andExpect(jsonPath("$.teamRequests.length()").value(0));
  }

  @Test
  void everyTeamRoleHasServerEnforcedPrivateCapabilities() throws Exception {
    String base = "/api/v1/teams/" + team;
    mvc.perform(
            json(
                post(base + "/leads"),
                "client",
                "{\"title\":\"Portal\",\"description\":\"A client approval"
                    + " portal\",\"budget\":\"10k\",\"timeline\":\"2 months\"}"))
        .andExpect(status().isOk());
    UUID lead = db.queryForObject("SELECT id FROM team_leads", UUID.class);
    for (String role : List.of("BUSINESS_MANAGER", "RECRUITER", "PROJECT_MANAGER", "MEMBER")) {
      String actor = role.toLowerCase();
      UUID u = user(actor);
      db.update(
          "INSERT INTO team_members(team_id,user_id,role,membership_type)"
              + " VALUES(?,?,?,'PERMANENT')",
          team,
          u,
          role);
      boolean commercial = role.equals("BUSINESS_MANAGER");
      mvc.perform(get(base + "/workspace").cookie(new Cookie("gl_session", actor)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.leads.length()").value(commercial ? 1 : 0));
      mvc.perform(
              json(patch(base + "/leads/" + lead), actor, "{\"note\":\"Private capacity check\"}"))
          .andExpect(commercial ? status().isOk() : status().isForbidden());
      mvc.perform(
              json(
                  post(base + "/roles"),
                  actor,
                  "{\"title\":\"API builder\",\"description\":\"Build secure Java"
                      + " APIs\",\"skills\":\"Java\",\"contractType\":\"CONTRACT\",\"compensationBand\":\"5k\"}"))
          .andExpect(role.equals("RECRUITER") ? status().isOk() : status().isForbidden());
      mvc.perform(
              json(
                  post(base + "/staffing"),
                  actor,
                  "{\"userId\":\""
                      + u
                      + "\",\"projectLabel\":\"Portal\",\"skills\":\"Java\",\"endsAt\":\""
                      + java.time.Instant.now().plusSeconds(86400)
                      + "\"}"))
          .andExpect(role.equals("PROJECT_MANAGER") ? status().isOk() : status().isForbidden());
      mvc.perform(json(patch(base + "/members/" + u), actor, "{\"role\":\"RECRUITER\"}"))
          .andExpect(status().isForbidden());
    }
  }
}
