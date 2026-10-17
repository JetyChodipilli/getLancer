package com.getlancer.business;

import static org.junit.jupiter.api.Assertions.*;

import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

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
class SavedCandidateAuditIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired TalentListService talent;
  @Autowired ProjectRequestService requests;
  @Autowired ConciergeService concierge;
  UUID business, list, brief, sourcing, first, second;

  @BeforeEach
  void prepare() {
    db.execute("TRUNCATE users CASCADE");
    UUID owner = user("saved-owner");
    first = user("saved-first");
    second = user("saved-second");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')", owner);
    db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?", owner);
    proof(first);
    proof(second);
    business = UUID.randomUUID();
    list = UUID.randomUUID();
    brief = UUID.randomUUID();
    sourcing = UUID.randomUUID();
    db.update("INSERT INTO businesses(id,name,summary,owner_id) VALUES(?,'Saved business','Private candidate lists',?)", business, owner);
    db.update("INSERT INTO business_members(business_id,user_id,role) VALUES(?,?,'OWNER')", business, owner);
    db.update("INSERT INTO talent_lists(id,business_id,name,created_by) VALUES(?,?,'Portal builders',?)", list, business, owner);
    db.update("INSERT INTO business_requests(id,business_id,created_by,title,description,category,technology,budget,timeline,status) VALUES(?,?,?,'Portal request','A private customer portal project','CRM','React','10000','One month','OPEN')", brief, business, owner);
    db.update("INSERT INTO concierge_requests(id,request_id,requested_by,status) VALUES(?,?,?,'IN_PROGRESS')", sourcing, brief, owner);
  }

  private UUID user(String name) {
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())", id, name + "@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT'),(?,'DEVELOPER')", id, id);
    db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,?,'APPROVED')", id, id.toString(), name);
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')", Support.hash(name), id);
    return id;
  }

  private void proof(UUID builder) {
    UUID product = UUID.randomUUID();
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,approval_status,lifecycle_status,visibility,available_for_similar_work) VALUES(?,?,?,'Portal proof','Customer portal evidence','A working customer approval portal','SAAS','CRM','React','Built the portal','APPROVED','ACTIVE','PUBLIC',true)", product, builder, product.toString());
  }

  private MockHttpServletRequest actor() {
    var request = new MockHttpServletRequest();
    request.setCookies(new Cookie("gl_session", "saved-owner"));
    return request;
  }

  private Map<String, Object> candidate(UUID target, String reason) {
    return Map.of("kind", "BUILDER", "targetId", target, "reason", reason);
  }

  private int audits(String action) {
    return db.queryForObject("SELECT count(*) FROM business_activity WHERE business_id=? AND action=?", Integer.class, business, action);
  }

  @Test
  void duplicateTalentSaveKeepsOneEntryAndOneAuditWhileDistinctSaveIsAudited() {
    var saved = candidate(first, "Current eligible proof");
    assertEquals(Map.of("ok", true), talent.save(business, list, saved, actor()));
    assertEquals(Map.of("ok", true), talent.save(business, list, saved, actor()));
    assertEquals(1, db.queryForObject("SELECT count(*) FROM talent_entries", Integer.class));
    assertEquals(1, audits("TALENT_SAVED"));
    talent.save(business, list, candidate(second, "Different eligible builder"), actor());
    assertEquals(2, db.queryForObject("SELECT count(*) FROM talent_entries", Integer.class));
    assertEquals(2, audits("TALENT_SAVED"));
  }

  @Test
  void duplicateShortlistSaveKeepsOneEntryAndOneAuditWhileDistinctSaveIsAudited() {
    var saved = candidate(first, "Current eligible proof");
    assertEquals(Map.of("ok", true), requests.save(business, brief, saved, actor()));
    assertEquals(Map.of("ok", true), requests.save(business, brief, saved, actor()));
    assertEquals(1, db.queryForObject("SELECT count(*) FROM request_shortlist", Integer.class));
    assertEquals(1, audits("CANDIDATE_SHORTLISTED"));
    requests.save(business, brief, candidate(second, "Different eligible builder"), actor());
    assertEquals(2, db.queryForObject("SELECT count(*) FROM request_shortlist", Integer.class));
    assertEquals(2, audits("CANDIDATE_SHORTLISTED"));
  }

  @Test
  void explicitConciergeReasonUpdatesKeepTheirAudit() {
    concierge.save(sourcing, candidate(first, "Initial reviewed recommendation"), actor());
    concierge.save(sourcing, candidate(first, "Updated reviewed recommendation"), actor());
    assertEquals(1, db.queryForObject("SELECT count(*) FROM request_shortlist", Integer.class));
    assertEquals("Updated reviewed recommendation", db.queryForObject("SELECT reason FROM request_shortlist", String.class));
    assertEquals(2, audits("CONCIERGE_RECOMMENDATION"));
    requests.save(business, brief, candidate(first, "Duplicate direct save"), actor());
    assertEquals(0, audits("CANDIDATE_SHORTLISTED"));
    assertEquals("Updated reviewed recommendation", db.queryForObject("SELECT reason FROM request_shortlist", String.class));
  }
}
