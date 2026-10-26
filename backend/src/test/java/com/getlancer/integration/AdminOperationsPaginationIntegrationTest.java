package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.util.HashSet;
import java.util.Set;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@ContextConfiguration(initializers = TestDatabaseGuard.class)
@SpringBootTest(properties = {
    "app.environment=local", "app.jobs-enabled=false", "app.admin-email=operator@example.test", "app.admin-password=", "app.admin-totp=",
    "spring.config.import=", "app.secure-cookie=false", "app.origin=http://localhost:3000", "app.rate-limit=1000",
    "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}",
    "spring.datasource.username=${TEST_DB_USERNAME:postgres}", "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.hikari.schema=getlancer_test", "spring.flyway.default-schema=getlancer_test",
    "spring.flyway.schemas=getlancer_test", "app.storage.access-key=", "app.storage.secret-key="
})
@AutoConfigureMockMvc
class AdminOperationsPaginationIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  UUID admin, buyer, seller;

  UUID user(String name) {
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())", id, name + "@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT')", id);
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')", Support.hash(name), id);
    return id;
  }

  @BeforeEach void seed() {
    clear(); admin = user("operator"); buyer = user("operations-buyer"); seller = user("operations-seller");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')", admin);
    db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?", admin);
    UUID product = UUID.randomUUID();
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,approval_status,lifecycle_status,visibility) VALUES(?,?,'operations-seed','Operations seed','Pagination fixture','Synthetic retained operations fixture','SAAS','CRM','Java','Built this fixture','APPROVED','ACTIVE','PUBLIC')", product, seller);
    for (int index = 1; index <= 206; index++) {
      UUID inquiry = new UUID(1, index), engagement = new UUID(2, index), dispute = new UUID(3, index);
      db.update("INSERT INTO inquiries(id,reference_product_id,developer_user_id,client_email,client_name,request_type,description,budget_band,timeline_band,idempotency_key,request_hash,current_status,email_confirmed_at) VALUES(?,?,?,'operations-buyer@example.test','Buyer','CUSTOMIZE','Retained pagination fixture','NEED_ESTIMATE','FLEXIBLE',?,'fixture','DISCUSSION',now())", inquiry, product, seller, UUID.randomUUID());
      db.update("INSERT INTO delivery_engagements(id,source_inquiry_id,buyer_user_id,builder_user_id,title,status,created_by) VALUES(?,?,?,?,?,'DISPUTED',?)", engagement, inquiry, buyer, seller, "Retained dispute " + index, buyer);
      db.update("INSERT INTO delivery_disputes(id,engagement_id,opened_by,reason,previous_status,created_at) VALUES(?,?,?,'A retained open dispute for pagination verification','ACTIVE',timestamp '2026-01-01 00:00:00')", dispute, engagement, buyer);
      if (index <= 55) {
        UUID accountOwner = user("operations-account-" + index), milestone = new UUID(4, index);
        db.update("INSERT INTO payment_accounts(id,builder_user_id,account_id,mode,provider_status,activation_confirmed,verified_by,verified_at) VALUES(?,?,?,'test','created',true,?,timestamp '2026-01-01 00:00:00')", new UUID(5, index), accountOwner, "acc_operations" + index, admin);
        db.update("INSERT INTO delivery_milestones(id,engagement_id,ordinal,title,description,amount_minor,due_date) VALUES(?,?,1,'Retained milestone','Synthetic payment attention fixture',100,'2030-12-31')", milestone, engagement);
        db.update("INSERT INTO payment_attempts(id,milestone_id,payer_user_id,idempotency_key,amount_minor,account_id,mode,status,created_at) VALUES(?,?,?,?,100,?,'test','UNKNOWN',timestamp '2026-01-01 00:00:00')", new UUID(6, index), milestone, buyer, UUID.randomUUID(), "acc_operations" + index);
      }
    }
  }

  @AfterEach void clear() { db.execute("TRUNCATE users CASCADE"); db.execute("TRUNCATE rate_buckets"); }
  MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) { return request.cookie(new Cookie("gl_session", token)); }
  JsonNode ok(MockHttpServletRequestBuilder request) throws Exception { return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); }

  @Test void boundedPagesReachEveryOpenDisputeAndOlderCaseCanBeResolved() throws Exception {
    Set<String> ids = new HashSet<>();
    for (int page = 0; page < 5; page++) {
      JsonNode response = ok(as(get("/api/v1/admin/delivery/disputes").param("page", String.valueOf(page)), "operator"));
      assertEquals(page, response.path("page").asInt()); assertEquals(50, response.path("size").asInt()); assertEquals(206, response.path("totalItems").asInt());
      assertEquals(page == 4 ? 6 : 50, response.path("items").size()); assertEquals(page < 4, response.path("hasMore").asBoolean());
      for (JsonNode row : response.path("items")) { assertTrue(ids.add(row.path("id").asText())); assertEquals("OPEN", row.path("status").asText()); }
    }
    assertEquals(206, ids.size()); assertTrue(ids.contains(new UUID(3, 206).toString()));
    mvc.perform(as(post("/api/v1/admin/delivery/disputes/" + new UUID(3, 206) + "/resolve"), "operator").header("Origin", "http://localhost:3000").header("X-Requested-With", "getlancer").contentType("application/json").content("{\"resolution\":\"RESUME\",\"reason\":\"Independent retained case reviewed by the operator.\"}"))
        .andExpect(status().isOk());
    assertEquals("RESOLVED", db.queryForObject("SELECT status FROM delivery_disputes WHERE id=?", String.class, new UUID(3, 206)));
    assertEquals("ACTIVE", db.queryForObject("SELECT status FROM delivery_engagements WHERE id=?", String.class, new UUID(2, 206)));
    JsonNode last = ok(as(get("/api/v1/admin/delivery/disputes").param("page", "4"), "operator"));
    assertEquals(new UUID(3, 206).toString(), last.path("items").get(5).path("id").asText()); assertEquals("RESOLVED", last.path("items").get(5).path("status").asText());
  }

  @Test void accountAndAttentionCollectionsAreIndependentlyBoundedTypedAndStable() throws Exception {
    for (String collection : new String[] {"accounts", "attention"}) {
      String path = "/api/v1/admin/payments/" + collection;
      JsonNode first = ok(as(get(path), "operator")), second = ok(as(get(path).param("page", "1"), "operator"));
      assertEquals(50, first.path("items").size()); assertTrue(first.path("hasMore").asBoolean()); assertEquals(5, second.path("items").size()); assertFalse(second.path("hasMore").asBoolean()); assertEquals(55, second.path("totalItems").asInt());
      Set<String> ids = new HashSet<>();
      for (JsonNode response : new JsonNode[] {first, second}) for (JsonNode row : response.path("items")) { assertTrue(ids.add(row.path("id").asText())); assertFalse(row.has("password_hash")); assertFalse(row.has("verified_by")); }
      assertEquals(55, ids.size()); assertEquals(new UUID(collection.equals("accounts") ? 5 : 6, 55).toString(), second.path("items").get(4).path("id").asText());
      assertEquals(0, ok(as(get(path).param("page", "2"), "operator")).path("items").size());
    }
  }

  @Test void everyPageRequiresCurrentAdminRoleMfaAndValidBounds() throws Exception {
    for (String path : new String[] {"/api/v1/admin/delivery/disputes", "/api/v1/admin/payments/accounts", "/api/v1/admin/payments/attention"}) {
      mvc.perform(get(path).param("page", "1")).andExpect(status().isUnauthorized());
      mvc.perform(as(get(path).param("page", "1"), "operations-buyer")).andExpect(status().isForbidden());
      for (String[] params : new String[][] {{"page", "-1"}, {"page", "100001"}, {"size", "0"}, {"size", "101"}}) mvc.perform(as(get(path).param(params[0], params[1]), "operator")).andExpect(status().isBadRequest());
      db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?", admin);
      mvc.perform(as(get(path).param("page", "1"), "operator")).andExpect(status().isForbidden());
      db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?", admin);
      db.update("DELETE FROM user_roles WHERE user_id=? AND role='ADMIN'", admin);
      mvc.perform(as(get(path).param("page", "1"), "operator")).andExpect(status().isForbidden());
      db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')", admin);
    }
  }
}
