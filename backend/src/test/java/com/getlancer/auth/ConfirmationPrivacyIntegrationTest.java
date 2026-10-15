package com.getlancer.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.getlancer.shared.ApiError;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
    "app.storage.access-key=", "app.storage.secret-key="
})
@AutoConfigureMockMvc
class ConfirmationPrivacyIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired AuthService auth;
  @Autowired MockMvc mvc;
  UUID inquiry;
  final String token = "synthetic-confirmation-capability";

  @BeforeEach void seed() {
    clear();
    UUID builder = UUID.randomUUID(), product = UUID.randomUUID(); inquiry = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash) VALUES(?,?,'unused')", builder, "builder@example.test");
    db.update("INSERT INTO developer_profiles(user_id,slug,display_name) VALUES(?,'fixture-builder','Fixture builder')", builder);
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text)"
        + " VALUES(?,?,'fixture-project','Private project','Fixture summary','Fixture description','WEB_APP','fixture','Java','Fixture contribution')", product, builder);
    db.update("INSERT INTO inquiries(id,reference_product_id,developer_user_id,client_email,client_name,request_type,description,budget_band,timeline_band,idempotency_key,request_hash)"
        + " VALUES(?,?,?,'client@example.test','Fixture client','SIMILAR_PROJECT','Confidential inquiry requirements','DISCUSS','FLEXIBLE',?,?)",
        inquiry, product, builder, UUID.randomUUID(), Support.hash("fixture"));
    db.update("INSERT INTO account_tokens(id,token_hash,kind,inquiry_id,email,expires_at)"
        + " VALUES(?,?,'HIRE_CONFIRMATION',?,'client@example.test',clock_timestamp()+interval '1 hour')",
        UUID.randomUUID(), Support.hash(token), inquiry);
  }

  @AfterEach void clear() { db.execute("TRUNCATE users CASCADE"); db.execute("TRUNCATE rate_buckets"); }

  @Test void liveCapabilityStillReturnsLegitimateContextThroughThePublicController() throws Exception {
    mvc.perform(post("/api/v1/auth/confirmation").header("Origin", "http://localhost:3000")
        .header("X-Requested-With", "getlancer").contentType("application/json")
        .content("{\"token\":\"" + token + "\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.used").value(false))
        .andExpect(jsonPath("$.inquiry.description").value("Confidential inquiry requirements"));
  }

  @Test void usedCapabilityRetainsRecordedMetadataAndCannotReadInquiryDetails() {
    db.update("UPDATE account_tokens SET used_at=clock_timestamp() WHERE inquiry_id=?", inquiry);
    var context = auth.confirmationContext(Map.of("token", token));
    assertEquals("HIRE_CONFIRMATION", context.get("kind")); assertEquals(true, context.get("used"));
    assertFalse(context.containsKey("inquiry"));
  }

  @Test void quarantineBlockAndRestoreCannotReactivateRevokedPrivateContext() {
    for (String state : new String[] {"QUARANTINED", "BLOCKED", "CLEAR"}) {
      db.update("UPDATE inquiries SET moderation_status=? WHERE id=?", state, inquiry);
      db.update("UPDATE account_tokens SET used_at=clock_timestamp() WHERE inquiry_id=?", inquiry);
      assertFalse(auth.confirmationContext(Map.of("token", token)).containsKey("inquiry"));
    }
  }

  @Test void activeTokenCannotReadRestrictedInquiryOrSuspendedSender() {
    db.update("UPDATE inquiries SET moderation_status='QUARANTINED' WHERE id=?", inquiry);
    assertFalse(auth.confirmationContext(Map.of("token", token)).containsKey("inquiry"));
    db.update("UPDATE inquiries SET moderation_status='CLEAR' WHERE id=?", inquiry);
    db.update("INSERT INTO users(id,email,password_hash,account_status) VALUES(?,'client@example.test','unused','SUSPENDED')", UUID.randomUUID());
    assertFalse(auth.confirmationContext(Map.of("token", token)).containsKey("inquiry"));
  }

  @Test void expiredContextsIncludingConsumedTokensStayExpiredAndUnknownTokensStayInvalid() {
    db.update("UPDATE account_tokens SET expires_at=clock_timestamp()-interval '1 second' WHERE inquiry_id=?", inquiry);
    assertEquals("TOKEN_EXPIRED", assertThrows(ApiError.class,
        () -> auth.confirmationContext(Map.of("token", token))).code);
    db.update("UPDATE account_tokens SET used_at=clock_timestamp() WHERE inquiry_id=?", inquiry);
    assertEquals("TOKEN_EXPIRED", assertThrows(ApiError.class,
        () -> auth.confirmationContext(Map.of("token", token))).code);
    assertEquals("INVALID_TOKEN", assertThrows(ApiError.class,
        () -> auth.confirmationContext(Map.of("token", "unknown-synthetic-capability"))).code);
  }

  @Test void consumedMutationKeepsItsExistingReplayContract() {
    db.update("UPDATE account_tokens SET used_at=clock_timestamp() WHERE inquiry_id=?", inquiry);
    var request = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/v1/auth/confirm");
    assertTrue(Boolean.TRUE.equals(auth.confirm(Map.of("token", token), request).get("alreadyRecorded")));
  }
}
