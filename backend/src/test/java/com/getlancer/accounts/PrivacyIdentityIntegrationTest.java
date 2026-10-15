package com.getlancer.accounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.getlancer.auth.AuthService;
import com.getlancer.products.PrivateProjectService;
import com.getlancer.shared.ApiError;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@ContextConfiguration(initializers = TestDatabaseGuard.class)
@SpringBootTest(properties = {
    "app.environment=local", "app.jobs-enabled=false", "app.admin-email=operator@example.test",
    "app.admin-password=", "app.admin-totp=", "spring.config.import=", "app.secure-cookie=false",
    "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}",
    "spring.datasource.username=${TEST_DB_USERNAME:postgres}", "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.hikari.schema=getlancer_test", "spring.flyway.default-schema=getlancer_test",
    "spring.flyway.schemas=getlancer_test", "app.origin=http://localhost:3000",
    "app.storage.access-key=", "app.storage.secret-key=", "app.policies-approved=true"
})
@AutoConfigureMockMvc
class PrivacyIdentityIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired AuthService auth;
  @Autowired PrivacyService privacy;
  @Autowired AccountService accounts;
  UUID closed, builder, product, inquiry;
  final String originalEmail = "former-client@example.test";
  final String guestToken = "synthetic-old-guest-capability";

  @BeforeEach void seed() {
    clear(); closed = user(originalEmail, "DELETED"); builder = user("builder@example.test", "ACTIVE");
    UUID admin = user("operator@example.test", "ACTIVE");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')", admin);
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at,mfa_verified) VALUES(?,?,now()+interval '1 hour',true)", Support.hash("operator-session"), admin);
    db.update("INSERT INTO deletion_requests(user_id) VALUES(?)", closed);
    product = UUID.randomUUID(); inquiry = UUID.randomUUID();
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,visibility,approval_status,lifecycle_status)"
        + " VALUES(?,?,'private-fixture','Private project','Fixture summary','Fixture description','WEB_APP','fixture','Java','Fixture contribution','PRIVATE_CASE_STUDY','APPROVED','ACTIVE')", product, builder);
    db.update("INSERT INTO inquiries(id,reference_product_id,developer_user_id,client_email,client_name,request_type,description,budget_band,timeline_band,idempotency_key,request_hash,current_status,email_confirmed_at)"
        + " VALUES(?,?,?,?,'Former client','SIMILAR_PROJECT','Retained confidential inquiry','DISCUSS','FLEXIBLE',?,?,'INQUIRY_RECEIVED',now())",
        inquiry, product, builder, originalEmail, UUID.randomUUID(), Support.hash("fixture"));
    db.update("INSERT INTO product_access_grants(product_id,client_email,expires_at) VALUES(?,?,now()+interval '1 day')", product, originalEmail);
    for (boolean used : new boolean[] {false, true}) db.update("INSERT INTO account_tokens(id,token_hash,kind,inquiry_id,email,expires_at,used_at)"
        + " VALUES(?,?,'HIRE_CONFIRMATION',?,?,now()+interval '1 hour',CASE WHEN ? THEN now() ELSE NULL END)",
        UUID.randomUUID(), Support.hash(used ? guestToken + "-used" : guestToken), inquiry, originalEmail, used);
  }

  UUID user(String email, String state) {
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at,account_status) VALUES(?,?,'unused',now(),?)", id, email, state);
    db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,'Fixture person','APPROVED')", id, "fixture-" + id);
    return id;
  }

  @AfterEach void clear() { db.execute("TRUNCATE users CASCADE"); db.execute("TRUNCATE rate_buckets"); }

  void anonymize() throws Exception {
    mvc.perform(post("/api/v1/admin/deletion-requests/" + closed + "/review")
        .cookie(new Cookie("gl_session", "operator-session")).header("Origin", "http://localhost:3000")
        .header("X-Requested-With", "getlancer").contentType("application/json")
        .content("{\"action\":\"ANONYMIZE_PROFILE\",\"reason\":\"Approved synthetic retention review\"}"))
        .andExpect(status().isOk());
  }

  MockHttpServletRequest replacement() {
    auth.signup(Map.of("email", originalEmail, "password", "synthetic-password-long", "acceptedTerms", true, "displayName", "New client"));
    UUID id = db.queryForObject("SELECT id FROM users WHERE email=?", UUID.class, originalEmail);
    db.update("UPDATE users SET email_verified_at=now() WHERE id=?", id);
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')", Support.hash("replacement"), id);
    var request = new MockHttpServletRequest(); request.setCookies(new Cookie("gl_session", "replacement"));
    return request;
  }

  @Test void replacementAccountCannotInheritRetainedInquiryExportMutationsOrPrivateGrants() throws Exception {
    anonymize(); var request = replacement();
    assertNotEquals(closed, db.queryForObject("SELECT id FROM users WHERE email=?", UUID.class, originalEmail));
    mvc.perform(get("/api/v1/me/inquiries").cookie(new Cookie("gl_session", "replacement")))
        .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items").isEmpty());
    mvc.perform(get("/api/v1/me/inquiries/" + inquiry).cookie(new Cookie("gl_session", "replacement"))).andExpect(status().isNotFound());
    for (String action : new String[] {"decision", "review", "not-hired", "confirmation-link"})
      mvc.perform(post("/api/v1/me/inquiries/" + inquiry + "/" + action)
          .cookie(new Cookie("gl_session", "replacement")).header("Origin", "http://localhost:3000")
          .header("X-Requested-With", "getlancer").contentType("application/json")
          .content(action.equals("decision") ? "{\"kind\":\"HIRE_CONFIRMATION\",\"decision\":\"ACCEPT\"}"
              : action.equals("review") ? "{\"rating\":5,\"reviewText\":\"Synthetic private review\",\"visibility\":\"ANONYMOUS\"}" : "{}"))
          .andExpect(status().isNotFound());
    assertTrue(accounts.export(request).requests().isEmpty());
    assertFalse(PrivateProjectService.granted(db, product, Map.of("email", originalEmail, "email_verified_at", Timestamp.from(java.time.Instant.now()))));
    assertEquals("closed-" + closed + "@example.invalid", db.queryForObject("SELECT client_email FROM inquiries WHERE id=?", String.class, inquiry));
    assertEquals(1, db.queryForObject("SELECT count(*) FROM inquiries WHERE id=?", Integer.class, inquiry));
    for (String token : new String[] {guestToken, guestToken + "-used"}) assertEquals("INVALID_TOKEN",
        assertThrows(ApiError.class, () -> auth.confirmationContext(Map.of("token", token))).code);
    // New, explicitly issued authority for the new account remains valid.
    db.update("INSERT INTO product_access_grants(product_id,client_email,expires_at) VALUES(?,?,now()+interval '1 day')", product, originalEmail);
    assertTrue(PrivateProjectService.granted(db, product, Map.of("email", originalEmail, "email_verified_at", Timestamp.from(java.time.Instant.now()))));
  }

  @Test void repeatedAnonymizationKeepsTheSameClosedIdentity() throws Exception {
    anonymize(); String identity = db.queryForObject("SELECT email FROM users WHERE id=?", String.class, closed);
    anonymize(); assertEquals(identity, db.queryForObject("SELECT email FROM users WHERE id=?", String.class, closed));
    assertEquals(identity, db.queryForObject("SELECT client_email FROM inquiries WHERE id=?", String.class, inquiry));
  }

  @Test void preexistingClosedNamespaceCollisionDoesNotOverwriteAnotherAccountOrBlockClosure() throws Exception {
    UUID collision = user("closed-" + closed + "@example.invalid", "ACTIVE");
    anonymize(); String identity = db.queryForObject("SELECT email FROM users WHERE id=?", String.class, closed);
    assertNotEquals("closed-" + closed + "@example.invalid", identity);
    assertEquals(identity, db.queryForObject("SELECT client_email FROM inquiries WHERE id=?", String.class, inquiry));
    assertEquals("ACTIVE", db.queryForObject("SELECT account_status FROM users WHERE id=?", String.class, collision));
    anonymize(); assertEquals(identity, db.queryForObject("SELECT email FROM users WHERE id=?", String.class, closed));
  }

  @Test void legacyMigrationQuarantinesPreviouslyAnonymizedAuthorityAndPreservesKnownOwners() throws Exception {
    // Model a closure performed by the old implementation, with the former email lost.
    db.update("UPDATE users SET email=? WHERE id=?", "closed-" + closed + "@example.invalid", closed);
    db.update("UPDATE deletion_requests SET status='PROFILE_ANONYMIZED' WHERE user_id=?", closed);
    db.update("UPDATE inquiries SET created_at=now()-interval '30 days' WHERE id=?", inquiry);
    var replacementRequest = replacement();
    String safeEmail = "established-client@example.test"; UUID established = user(safeEmail, "ACTIVE");
    db.update("UPDATE users SET created_at=now()-interval '60 days' WHERE id=?", established);
    UUID safeInquiry = UUID.randomUUID();
    db.update("INSERT INTO inquiries(id,reference_product_id,developer_user_id,client_email,client_name,request_type,description,budget_band,timeline_band,idempotency_key,request_hash)"
        + " VALUES(?,?,?,?,'Established client','SIMILAR_PROJECT','Known owner inquiry','DISCUSS','FLEXIBLE',?,?)",
        safeInquiry, product, builder, safeEmail, UUID.randomUUID(), Support.hash("safe fixture"));
    db.update("INSERT INTO product_access_grants(product_id,client_email,expires_at) VALUES(?,?,now()+interval '7 days')", product, safeEmail);
    for (int attempt = 0; attempt < 2; attempt++) {
      try (var connection = db.getDataSource().getConnection()) {
        connection.setSchema("getlancer_test");
        org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
            new org.springframework.core.io.ClassPathResource("db/migration/V35__legacy_requester_identity.sql"));
      }
      assertEquals(originalEmail, db.queryForObject("SELECT legacy_client_email FROM inquiries WHERE id=?", String.class, inquiry));
      assertEquals("QUARANTINED", db.queryForObject("SELECT moderation_status FROM inquiries WHERE id=?", String.class, inquiry));
      assertEquals("closed-legacy-" + inquiry + "@example.invalid", db.queryForObject("SELECT client_email FROM inquiries WHERE id=?", String.class, inquiry));
      assertTrue(accounts.export(replacementRequest).requests().isEmpty());
      assertEquals(0, db.queryForObject("SELECT count(*) FROM product_access_grants WHERE client_email=?", Integer.class, originalEmail));
      assertEquals(0, db.queryForObject("SELECT count(*) FROM account_tokens WHERE inquiry_id=?", Integer.class, inquiry));
      assertEquals(safeEmail, db.queryForObject("SELECT client_email FROM inquiries WHERE id=?", String.class, safeInquiry));
      assertEquals(1, db.queryForObject("SELECT count(*) FROM product_access_grants WHERE client_email=?", Integer.class, safeEmail));
    }
    mvc.perform(get("/api/v1/me/inquiries/" + inquiry).cookie(new Cookie("gl_session", "replacement"))).andExpect(status().isNotFound());
  }
}
