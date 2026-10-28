package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
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
class AuthAuditRegressionTest {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  UUID owner, other;

  UUID user(String name) {
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())", id, name + "@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT'),(?,'DEVELOPER')", id, id);
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')", Support.hash(name), id);
    db.update("INSERT INTO developer_profiles(user_id,slug,display_name,headline,bio,technology,category,approval_status,availability_status,availability_confirmed_at,country,time_zone,languages) VALUES(?,?,?,?,?,'Java','Business','APPROVED','AVAILABLE_NOW',timestamp '2020-01-01 00:00:00','IN','Asia/Kolkata','English')", id, name, "Original builder", "A useful builder headline", "A detailed description of the original work and experience.");
    db.update("INSERT INTO showcase_entitlements(user_id) VALUES(?)", id);
    return id;
  }

  @BeforeEach void seed() { clear(); owner=user("auth-owner"); other=user("auth-other"); }
  @AfterEach void clear() { db.execute("TRUNCATE users CASCADE"); db.execute("TRUNCATE rate_buckets"); }

  MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) { return request.cookie(new Cookie("gl_session", token)); }
  MockHttpServletRequestBuilder mutation(MockHttpServletRequestBuilder request, String token, Map<String,Object> body) throws Exception {
    return as(request,token).header("Origin","http://localhost:3000").header("X-Requested-With","getlancer").contentType("application/json").content(json.writeValueAsString(body));
  }
  Map<String,Object> profile() {
    var body=new LinkedHashMap<String,Object>();
    body.put("displayName","Original builder");body.put("headline","A useful builder headline");body.put("bio","A detailed description of the original work and experience.");body.put("technology","Java");body.put("category","Business");body.put("availabilityStatus","LIMITED");body.put("githubUrl","");body.put("linkedinUrl","");body.put("websiteUrl","");body.put("country","IN");body.put("timeZone","Asia/Kolkata");body.put("languages","English");return body;
  }

  @Test void profileAvailabilityIsConfirmedWithoutInvalidatingApprovalAndMaterialEditsNeedReview() throws Exception {
    var body=profile();
    mvc.perform(mutation(put("/api/v1/developer/profile"),"auth-owner",body)).andExpect(status().isOk());
    assertEquals("LIMITED",db.queryForObject("SELECT availability_status FROM developer_profiles WHERE user_id=?",String.class,owner));
    assertEquals("APPROVED",db.queryForObject("SELECT approval_status FROM developer_profiles WHERE user_id=?",String.class,owner));
    assertTrue(db.queryForObject("SELECT availability_confirmed_at FROM developer_profiles WHERE user_id=?",Timestamp.class,owner).after(Timestamp.valueOf("2020-01-01 00:00:00")));
    assertEquals("AVAILABLE_NOW",db.queryForObject("SELECT availability_status FROM developer_profiles WHERE user_id=?",String.class,other));
    body.put("headline","Materially changed builder headline");
    mvc.perform(mutation(put("/api/v1/developer/profile"),"auth-owner",body)).andExpect(status().isOk());
    assertEquals("DRAFT",db.queryForObject("SELECT approval_status FROM developer_profiles WHERE user_id=?",String.class,owner));
    mvc.perform(mutation(post("/api/v1/developer/profile/submit"),"auth-owner",Map.of())).andExpect(status().isOk());
    assertEquals("PROFILE_PENDING",db.queryForObject("SELECT approval_status FROM developer_profiles WHERE user_id=?",String.class,owner));
    mvc.perform(mutation(post("/api/v1/developer/profile/submit"),"auth-owner",Map.of())).andExpect(status().isConflict());
  }

  @Test void failedProfileCommandsPreserveExistingDataAndSuspendedOrUnverifiedProfilesCannotAdvance() throws Exception {
    Timestamp confirmationBefore=db.queryForObject("SELECT availability_confirmed_at FROM developer_profiles WHERE user_id=?",Timestamp.class,owner);
    var body=profile();body.put("displayName","Must not be saved");body.put("availabilityStatus","BOOKED_UNTIL");body.put("bookedUntil","2000-01-01");
    mvc.perform(mutation(put("/api/v1/developer/profile"),"auth-owner",body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fieldErrors.bookedUntil").exists());
    assertEquals("Original builder",db.queryForObject("SELECT display_name FROM developer_profiles WHERE user_id=?",String.class,owner));
    assertEquals(confirmationBefore,db.queryForObject("SELECT availability_confirmed_at FROM developer_profiles WHERE user_id=?",Timestamp.class,owner));
    db.update("UPDATE developer_profiles SET approval_status='SUSPENDED' WHERE user_id=?",owner);
    mvc.perform(mutation(put("/api/v1/developer/profile"),"auth-owner",profile())).andExpect(status().isForbidden());
    mvc.perform(mutation(post("/api/v1/developer/profile/submit"),"auth-owner",Map.of())).andExpect(status().isConflict());
    db.update("UPDATE developer_profiles SET approval_status='DRAFT' WHERE user_id=?",owner);db.update("UPDATE users SET email_verified_at=NULL WHERE id=?",owner);
    mvc.perform(mutation(post("/api/v1/developer/profile/submit"),"auth-owner",Map.of())).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMAIL_NOT_VERIFIED"));
    mvc.perform(as(get("/api/v1/me/export"),"auth-owner")).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("EMAIL_NOT_VERIFIED"));
    mvc.perform(put("/api/v1/developer/profile").header("Origin","http://localhost:3000").header("X-Requested-With","getlancer").contentType("application/json").content(json.writeValueAsString(profile()))).andExpect(status().isUnauthorized());
  }

  @Test void notificationPagesAndReadMutationsStayInsideTheCurrentAccountAndReadsAreRetryable() throws Exception {
    UUID first=new UUID(1,1),second=new UUID(1,2),foreign=new UUID(1,3);
    for(UUID id:new UUID[]{first,second,foreign})db.update("INSERT INTO notifications(id,user_id,title,created_at) VALUES(?,?,?,timestamp '2026-01-01 00:00:00')",id,id.equals(foreign)?other:owner,id.equals(foreign)?"Foreign private notification":"Owned notification");
    for(int page=0;page<2;page++) {
      JsonNode result=json.readTree(mvc.perform(as(get("/api/v1/notifications").param("page",String.valueOf(page)).param("size","1"),"auth-owner")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
      assertEquals(2,result.path("totalItems").asInt());assertEquals(1,result.path("items").size());assertEquals(page==0,result.path("hasMore").asBoolean());assertEquals((page==0?first:second).toString(),result.path("items").get(0).path("id").asText());
    }
    mvc.perform(mutation(patch("/api/v1/notifications/"+foreign+"/read"),"auth-owner",Map.of())).andExpect(status().isNotFound());
    for(int attempt=0;attempt<2;attempt++)mvc.perform(mutation(patch("/api/v1/notifications/"+first+"/read"),"auth-owner",Map.of())).andExpect(status().isOk());
    assertNotNull(db.queryForObject("SELECT read_at FROM notifications WHERE id=?",Timestamp.class,first));
    assertNull(db.queryForObject("SELECT read_at FROM notifications WHERE id=?",Timestamp.class,foreign));assertNull(db.queryForObject("SELECT read_at FROM notifications WHERE id=?",Timestamp.class,second));
    mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
  }
}
