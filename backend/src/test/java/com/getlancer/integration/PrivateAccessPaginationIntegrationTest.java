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
    "app.environment=local", "app.jobs-enabled=false", "app.admin-password=", "app.admin-totp=",
    "spring.config.import=", "app.secure-cookie=false", "app.origin=http://localhost:3000",
    "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}",
    "spring.datasource.username=${TEST_DB_USERNAME:postgres}", "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.hikari.schema=getlancer_test", "spring.flyway.default-schema=getlancer_test",
    "spring.flyway.schemas=getlancer_test", "app.storage.access-key=", "app.storage.secret-key="
})
@AutoConfigureMockMvc
class PrivateAccessPaginationIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  UUID owner, outsider, product;

  UUID user(String name, boolean developer) {
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())", id, name + "@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT')", id);
    if (developer) {
      db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER')", id);
      db.update("INSERT INTO developer_profiles(user_id,slug,display_name,approval_status) VALUES(?,?,?,'APPROVED')", id, name, name);
    }
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')", Support.hash(name), id);
    return id;
  }

  @BeforeEach void seed() {
    clear(); owner = user("grant-owner", true); outsider = user("grant-outsider", true); user("client054", false);
    product = UUID.randomUUID();
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,visibility,approval_status,lifecycle_status)"
        + " VALUES(?,?,'private-continuation','Private fixture','Private fixture summary','Working private project','SAAS','CRM','Java','Built this project','PRIVATE_CASE_STUDY','APPROVED','ACTIVE')", product, owner);
    // All fifty-five grants are current; this boundary does not rely on expired rows surviving maintenance.
    for (int index = 0; index < 55; index++) db.update("INSERT INTO product_access_grants(product_id,client_email,expires_at) VALUES(?,?,now()+interval '7 days')", product, String.format("client%03d@example.test", index));
  }

  @AfterEach void clear() { db.execute("TRUNCATE users CASCADE"); db.execute("TRUNCATE rate_buckets"); }
  String access() { return "/api/v1/developer/products/" + product + "/access"; }
  MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) { return request.cookie(new Cookie("gl_session", token)); }
  MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, String token, String value) {
    return as(request, token).header("Origin", "http://localhost:3000").header("X-Requested-With", "getlancer").contentType("application/json").content(value);
  }
  JsonNode ok(MockHttpServletRequestBuilder request) throws Exception { return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); }

  @Test void ownerCanContinueCurrentGrantsAndRevokeTheClientOnPageTwo() throws Exception {
    JsonNode first = ok(as(get(access()), "grant-owner"));
    assertEquals(50, first.path("items").size()); assertTrue(first.path("hasMore").asBoolean()); assertEquals(50, first.path("size").asInt());
    assertEquals("client000@example.test", first.path("items").get(0).path("client_email").asText());
    JsonNode second = ok(as(get(access()).param("page", "1"), "grant-owner"));
    assertEquals(5, second.path("items").size()); assertEquals(1, second.path("page").asInt()); assertFalse(second.path("hasMore").asBoolean()); assertEquals(55, second.path("totalItems").asInt());
    Set<String> identities = new HashSet<>();
    for (JsonNode page : new JsonNode[] {first, second}) for (JsonNode grant : page.path("items")) {
      assertTrue(identities.add(grant.path("client_email").asText()));
      Set<String> fields = new HashSet<>(); grant.fieldNames().forEachRemaining(fields::add); assertEquals(Set.of("client_email", "expires_at"), fields);
    }
    assertEquals("client054@example.test", second.path("items").get(4).path("client_email").asText());
    mvc.perform(as(get("/api/v1/private/products/" + product), "client054")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/products/private-continuation")).andExpect(status().isNotFound());
    JsonNode publicProjects = ok(get("/api/v1/products"));
    assertFalse(publicProjects.toString().contains("client050@example.test")); assertFalse(publicProjects.toString().contains("client_email"));
    mvc.perform(body(delete(access()), "grant-owner", "{\"email\":\"client054@example.test\"}")).andExpect(status().isOk());
    assertEquals(0, db.queryForObject("SELECT count(*) FROM product_access_grants WHERE product_id=? AND client_email='client054@example.test'", Integer.class, product));
    mvc.perform(as(get("/api/v1/private/products/" + product), "client054")).andExpect(status().isNotFound());
    JsonNode remaining = ok(as(get(access()).param("page", "1"), "grant-owner")); assertEquals(4, remaining.path("items").size()); assertFalse(remaining.toString().contains("client054@example.test"));
  }

  @Test void everyPageAndRevokeRequireTheCurrentApprovedOwner() throws Exception {
    ok(as(get(access()), "grant-owner"));
    mvc.perform(get(access()).param("page", "1")).andExpect(status().isUnauthorized());
    mvc.perform(as(get(access()).param("page", "1"), "grant-outsider")).andExpect(status().isNotFound());
    mvc.perform(body(delete(access()), "grant-outsider", "{\"email\":\"client054@example.test\"}")).andExpect(status().isNotFound());
    db.update("UPDATE products SET owner_user_id=? WHERE id=?", outsider, product);
    mvc.perform(as(get(access()).param("page", "1"), "grant-owner")).andExpect(status().isNotFound());
    mvc.perform(body(delete(access()), "grant-owner", "{\"email\":\"client054@example.test\"}")).andExpect(status().isNotFound());
    assertEquals(5, ok(as(get(access()).param("page", "1"), "grant-outsider")).path("items").size());
    db.update("UPDATE developer_profiles SET approval_status='SUSPENDED' WHERE user_id=?", outsider);
    mvc.perform(as(get(access()).param("page", "1"), "grant-outsider")).andExpect(status().isForbidden());
    assertEquals(55, db.queryForObject("SELECT count(*) FROM product_access_grants WHERE product_id=?", Integer.class, product));
  }
}
