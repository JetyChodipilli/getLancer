package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
    "spring.config.import=",
    "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}",
    "spring.datasource.username=${TEST_DB_USERNAME:postgres}",
    "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.hikari.schema=getlancer_test", "spring.flyway.default-schema=getlancer_test",
    "spring.flyway.schemas=getlancer_test", "app.origin=http://localhost:3000",
    "app.secure-cookie=false", "app.storage.access-key=", "app.storage.secret-key="
})
@AutoConfigureMockMvc
class TeamBusinessAuditRegressionTest {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  UUID owner, manager, member, outsider, team, lead, business, request, talentList;
  final Instant overdue = Instant.parse("2020-01-02T03:04:57.123Z");

  UUID user(String name) {
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",
        id, name + "@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER'),(?,'CLIENT')", id, id);
    db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,?,'APPROVED')",
        id, id.toString(), name);
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",
        Support.hash(name), id);
    return id;
  }

  MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String actor, String body) {
    return request.cookie(new Cookie("gl_session", actor)).header("Origin", "http://localhost:3000")
        .header("X-Requested-With", "getlancer").contentType("application/json").content(body);
  }

  @BeforeEach
  void prepare() {
    db.execute("TRUNCATE users CASCADE");
    db.execute("TRUNCATE rate_buckets");
    owner = user("owner"); manager = user("manager"); member = user("member"); outsider = user("outsider");
    team = UUID.randomUUID(); lead = UUID.randomUUID(); business = UUID.randomUUID();
    request = UUID.randomUUID(); talentList = UUID.randomUUID();
    db.update("INSERT INTO teams(id,slug,name,summary,availability,project_range,owner_id) VALUES(?,?,'Audit studio','Customer software studio','AVAILABLE_NOW','10k',?)",
        team, team.toString(), owner);
    for (var entry : List.of(new Object[] {owner, "OWNER"}, new Object[] {manager, "BUSINESS_MANAGER"}, new Object[] {member, "MEMBER"}))
      db.update("INSERT INTO team_members(team_id,user_id,role,membership_type) VALUES(?,?,?,'PERMANENT')",
          team, entry[0], entry[1]);
    db.update("INSERT INTO team_leads(id,team_id,client_id,title,description,budget,timeline,assignee_id,follow_up_at) VALUES(?,?,?,'Audit portal','Customer approval workflow','10k','2 months',?,?)",
        lead, team, outsider, manager, Timestamp.from(overdue));
    db.update("INSERT INTO team_lead_notes(id,lead_id,actor_id,body) VALUES(?,?,?,'Private original note')",
        UUID.randomUUID(), lead, owner);
    db.update("INSERT INTO businesses(id,name,summary,owner_id) VALUES(?,'Audit business','Customer software hiring',?)",
        business, owner);
    db.update("INSERT INTO business_members(business_id,user_id,role) VALUES(?,?,'OWNER'),(?,?,'HIRING_MANAGER')",
        business, owner, business, manager);
    db.update("INSERT INTO business_requests(id,business_id,created_by,title,description,category,technology,budget,timeline,status) VALUES(?,?,?,'Private audit request','Confidential customer approval workflow','CRM','React','10k','2 months','OPEN')",
        request, business, owner);
    db.update("INSERT INTO talent_lists(id,business_id,name,created_by) VALUES(?,?,'Private audit list',?)",
        talentList, business, owner);
  }

  @Test
  void editingStatusAndNotesWithoutFollowUpPreservesOverdueTimestampAndAssignment() throws Exception {
    mvc.perform(json(patch("/api/v1/teams/" + team + "/leads/" + lead), "manager",
            "{\"status\":\"INTERESTED\",\"note\":\"Discuss the existing approval workflow.\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INTERESTED"))
        .andExpect(jsonPath("$.assigneeId").value(manager.toString()));
    assertEquals(overdue, db.queryForObject("SELECT follow_up_at FROM team_leads WHERE id=?", Timestamp.class, lead).toInstant());
    assertEquals(2, db.queryForObject("SELECT count(*) FROM team_lead_notes WHERE lead_id=?", Integer.class, lead));
    assertEquals(manager, db.queryForObject("SELECT assignee_id FROM team_leads WHERE id=?", UUID.class, lead));
  }

  @Test
  void unrelatedLeadEditsPreserveAnUnchangedExpiredAssignee() throws Exception {
    db.update("UPDATE team_members SET membership_type='CONTRACT',expires_at=now()-interval '1 hour' WHERE team_id=? AND user_id=?", team, member);
    db.update("UPDATE team_leads SET assignee_id=? WHERE id=?", member, lead);
    mvc.perform(json(patch("/api/v1/teams/" + team + "/leads/" + lead), "manager",
            "{\"status\":\"INTERESTED\",\"note\":\"Follow up on the existing scope.\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.assigneeId").value(member.toString()));
    assertEquals(overdue, db.queryForObject("SELECT follow_up_at FROM team_leads WHERE id=?", Timestamp.class, lead).toInstant());
  }

  @Test
  void explicitClearingAndFutureReschedulingStillWorkButNewPastDatesAreRejected() throws Exception {
    String path = "/api/v1/teams/" + team + "/leads/" + lead;
    mvc.perform(json(patch(path), "owner", "{\"followUpAt\":null,\"assigneeId\":null}"))
        .andExpect(status().isOk());
    assertNull(db.queryForObject("SELECT follow_up_at FROM team_leads WHERE id=?", Timestamp.class, lead));
    assertNull(db.queryForObject("SELECT assignee_id FROM team_leads WHERE id=?", UUID.class, lead));
    Instant future = Instant.now().plusSeconds(86400).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    mvc.perform(json(patch(path), "owner", "{\"followUpAt\":\"" + future + "\"}"))
        .andExpect(status().isOk());
    mvc.perform(json(patch(path), "owner", "{\"followUpAt\":\"" + overdue + "\",\"note\":\"Must not be saved\"}"))
        .andExpect(status().isBadRequest());
    assertEquals(future, db.queryForObject("SELECT follow_up_at FROM team_leads WHERE id=?", Timestamp.class, lead).toInstant());
    assertEquals(1, db.queryForObject("SELECT count(*) FROM team_lead_notes WHERE lead_id=?", Integer.class, lead));
  }

  @Test
  void teamCapabilitiesAreRecheckedOnNoteAndWorkspaceContinuationsAfterRoleChange() throws Exception {
    String root = "/api/v1/teams/" + team;
    mvc.perform(get(root + "/leads/" + lead + "/notes?size=1").cookie(new Cookie("gl_session", "manager")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].text").value("Private original note"));
    db.update("UPDATE team_members SET role='MEMBER' WHERE team_id=? AND user_id=?", team, manager);
    for (String path : List.of(root + "/workspace?collection=leads&page=1", root + "/leads/" + lead + "/notes?page=1", root + "/candidates?page=1"))
      mvc.perform(get(path).cookie(new Cookie("gl_session", "manager"))).andExpect(status().isForbidden());
    mvc.perform(json(patch(root + "/leads/" + lead), "manager", "{\"note\":\"No longer permitted\"}"))
        .andExpect(status().isForbidden());
    assertEquals(1, db.queryForObject("SELECT count(*) FROM team_lead_notes WHERE lead_id=?", Integer.class, lead));
  }

  @Test
  void businessContinuationAndMutationPathsRejectRemovedMembersAndOtherTenants() throws Exception {
    String root = "/api/v1/businesses/" + business;
    mvc.perform(get(root + "/requests?size=1").cookie(new Cookie("gl_session", "manager")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].title").value("Private audit request"));
    mvc.perform(json(post(root + "/invitations"), "manager", "{\"email\":\"outsider@example.test\"}"))
        .andExpect(status().isForbidden());
    db.update("DELETE FROM business_members WHERE business_id=? AND user_id=?", business, manager);
    for (String actor : List.of("manager", "outsider")) {
      for (String path : List.of(root + "?page=1", root + "/requests?page=1", root + "/requests/" + request + "/shortlist?page=1", root + "/talent-lists/" + talentList + "/entries?page=1"))
        mvc.perform(get(path).cookie(new Cookie("gl_session", actor))).andExpect(status().isNotFound());
      mvc.perform(json(post(root + "/talent-lists"), actor, "{\"name\":\"Unauthorized list\"}"))
          .andExpect(status().isNotFound());
    }
    assertEquals(1, db.queryForObject("SELECT count(*) FROM talent_lists WHERE business_id=?", Integer.class, business));
  }
}
