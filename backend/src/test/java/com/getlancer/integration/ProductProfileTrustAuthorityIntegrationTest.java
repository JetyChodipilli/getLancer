package com.getlancer.integration;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@org.springframework.test.context.ContextConfiguration(initializers = TestDatabaseGuard.class)
@SpringBootTest(properties = {
    "app.environment=local", "app.jobs-enabled=false", "app.admin-password=", "app.admin-totp=",
    "spring.config.import=", "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}",
    "spring.datasource.username=${TEST_DB_USERNAME:postgres}", "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.hikari.schema=getlancer_test", "spring.flyway.default-schema=getlancer_test",
    "spring.flyway.schemas=getlancer_test", "app.origin=http://localhost:3000", "app.secure-cookie=false",
    "app.storage.access-key=", "app.storage.secret-key="
})
@AutoConfigureMockMvc
class ProductProfileTrustAuthorityIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  UUID builder, operator, product, inquiry;
  Map<String, Object> originalProduct, originalProfile, originalProof, originalEntitlement, originalInquiry;
  List<Map<String, Object>> originalCategories, originalInquiryEvents;
  int analyticsCount;

  UUID user(String token) {
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",
        id, token + "@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT'),(?,'DEVELOPER')", id, id);
    db.update("INSERT INTO developer_profiles(user_id,slug,display_name,headline,bio,technology,category,approval_status)"
        + " VALUES(?,?,?,'Existing builder headline','Existing builder biography with retained public evidence.','Java','Authority boundary category','APPROVED')",
        id, token, token);
    db.update("INSERT INTO showcase_entitlements(user_id) VALUES(?)", id);
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')",
        Support.hash(token), id);
    return id;
  }

  @BeforeEach
  void prepare() {
    db.execute("TRUNCATE users CASCADE");
    db.execute("TRUNCATE rate_buckets");
    builder = user("product-authority-builder");
    operator = user("product-authority-admin");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ADMIN')", operator);
    db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?", operator);
    db.update("INSERT INTO categories(slug,name,active) VALUES('product-authority-category','Authority boundary category',true)"
        + " ON CONFLICT(slug) DO UPDATE SET name=excluded.name,active=true");
    product = UUID.randomUUID();
    inquiry = UUID.randomUUID();
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,approval_status,lifecycle_status,rights_confirmed,repository_url)"
        + " VALUES(?,?,?,'Existing authority project','Existing project summary','Existing project description with retained public evidence.','SAAS','Authority boundary category','','Built the existing public evidence','APPROVED','ACTIVE',true,'https://github.com/example/existing-authority-project')",
        product, builder, product.toString());
    db.update("INSERT INTO product_categories(product_id,category_slug) VALUES(?,'product-authority-category')", product);
    db.update("INSERT INTO repository_verifications(product_id,repository_url,challenge)"
        + " VALUES(?,'https://github.com/example/existing-authority-project','getlancer-proof-existing-authority')", product);
    db.update("INSERT INTO inquiries(id,reference_product_id,developer_user_id,client_email,client_name,request_type,description,budget_band,timeline_band,current_status,email_confirmed_at,idempotency_key,request_hash)"
        + " VALUES(?,?,?,'separate-client@example.test','Separate client','SIMILAR_BUILD','Retained client-confirmed engagement evidence','USD_3K_10K','ONE_TO_THREE_MONTHS','COMPLETED',now(),?,'authority-test')",
        inquiry, product, builder, UUID.randomUUID());
    db.update("INSERT INTO inquiry_events(id,inquiry_id,event_type,actor_type) VALUES(?,?,'COMPLETED','CLIENT_TOKEN')",
        UUID.randomUUID(), inquiry);
    originalProduct = db.queryForMap("SELECT * FROM products WHERE id=?", product);
    originalProfile = db.queryForMap("SELECT * FROM developer_profiles WHERE user_id=?", builder);
    originalProof = db.queryForMap("SELECT * FROM repository_verifications WHERE product_id=?", product);
    originalEntitlement = db.queryForMap("SELECT * FROM showcase_entitlements WHERE user_id=?", builder);
    originalInquiry = db.queryForMap("SELECT * FROM inquiries WHERE id=?", inquiry);
    originalCategories = db.queryForList("SELECT * FROM product_categories WHERE product_id=?", product);
    originalInquiryEvents = db.queryForList("SELECT * FROM inquiry_events WHERE inquiry_id=? ORDER BY id", inquiry);
    analyticsCount = db.queryForObject("SELECT count(*) FROM analytics_events", Integer.class);
  }

  MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder call, String token) {
    return call.cookie(new Cookie("gl_session", token)).header("Origin", "http://localhost:3000")
        .header("X-Requested-With", "getlancer").contentType("application/json");
  }

  @Test
  void profilePictureUsesCurrentAccountAndPublishedBuilderAuthority() throws Exception {
    String owner = "product-authority-builder", stranger = "product-authority-admin";
    String upload = "/api/v1/me/avatar", publicProfile = "/api/v1/builders/" + owner;
    var png = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(720, 300,
        java.awt.image.BufferedImage.TYPE_INT_RGB), "png", png);
    var file = new MockMultipartFile("file", "profile.png", "image/png", png.toByteArray());
    var malformed = new MockMultipartFile("file", "wrong.png", "image/png", new byte[]{1,2,3});
    mvc.perform(multipart(HttpMethod.PUT, upload).file(file).header("Origin", "http://localhost:3000")
        .header("X-Requested-With", "getlancer")).andExpect(status().isUnauthorized());
    mvc.perform(multipart(HttpMethod.PUT, upload).file(file).cookie(new Cookie("gl_session", owner))
        .header("Origin", "https://example.com").header("X-Requested-With", "getlancer"))
        .andExpect(status().isForbidden());
    mvc.perform(avatarRequest(upload, owner, file).header("X-GetLancer-Account", operator))
        .andExpect(status().isUnauthorized());
    mvc.perform(avatarRequest(upload, owner, malformed)).andExpect(status().isBadRequest());

    var before = mvc.perform(avatarRequest(upload, owner, file)).andExpect(status().isOk()).andReturn();
    String first = json.readTree(before.getResponse().getContentAsString()).path("avatarUrl").asText();
    assertTrue(first.matches("/api/v1/profile-avatars/[0-9a-f-]{36}"));
    mvc.perform(as(get("/api/v1/me"), owner)).andExpect(status().isOk())
        .andExpect(jsonPath("$.avatarUrl").value(first)).andExpect(jsonPath("$.profile.image").doesNotExist());
    mvc.perform(get(publicProfile)).andExpect(status().isOk())
        .andExpect(jsonPath("$.avatarUrl").value(first)).andExpect(jsonPath("$.avatar_id").doesNotExist());
    var publicBytes = mvc.perform(get(first)).andExpect(status().isOk())
        .andExpect(content().contentType("image/jpeg")).andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Cache-Control", containsString("no-store"))).andReturn().getResponse().getContentAsByteArray();
    assertEquals(320, javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(publicBytes)).getWidth());

    db.update("UPDATE developer_profiles SET approval_status='PROFILE_PENDING' WHERE user_id=?", builder);
    mvc.perform(get(first)).andExpect(status().isNotFound());
    mvc.perform(as(get(first), stranger)).andExpect(status().isNotFound());
    mvc.perform(as(get(first), owner)).andExpect(status().isOk());
    db.update("UPDATE developer_profiles SET approval_status='APPROVED' WHERE user_id=?", builder);
    db.update("DELETE FROM user_roles WHERE user_id=? AND role='DEVELOPER'", builder);
    mvc.perform(get(first)).andExpect(status().isNotFound());
    mvc.perform(get(publicProfile)).andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").doesNotExist());
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER')", builder);

    var after = mvc.perform(avatarRequest(upload, owner, file)).andExpect(status().isOk()).andReturn();
    String replacement = json.readTree(after.getResponse().getContentAsString()).path("avatarUrl").asText();
    assertNotEquals(first, replacement);
    mvc.perform(get(first)).andExpect(status().isNotFound());
    mvc.perform(get(replacement)).andExpect(status().isOk());
    mvc.perform(as(delete(upload), stranger).header("X-GetLancer-Account", builder))
        .andExpect(status().isUnauthorized());
    mvc.perform(as(delete(upload), owner)).andExpect(status().isNoContent());
    mvc.perform(get(replacement)).andExpect(status().isNotFound());
    mvc.perform(as(get("/api/v1/me"), owner)).andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(builder.toString())).andExpect(jsonPath("$.avatarUrl").doesNotExist());
    mvc.perform(get(publicProfile)).andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").doesNotExist());
  }

  private MockHttpServletRequestBuilder avatarRequest(String path, String actor, MockMultipartFile file) {
    return multipart(HttpMethod.PUT, path).file(file).cookie(new Cookie("gl_session", actor))
        .header("Origin", "http://localhost:3000").header("X-Requested-With", "getlancer");
  }

  void revokeDuringObservedLockWait(String lockSql, UUID key, MockHttpServletRequestBuilder call,
      Runnable revoke, int expectedStatus) throws Exception {
    var executor = Executors.newSingleThreadExecutor();
    try (var holder = db.getDataSource().getConnection()) {
      holder.setAutoCommit(false);
      int holderPid;
      try (var statement = holder.createStatement();
          var result = statement.executeQuery("SELECT pg_backend_pid()")) {
        assertTrue(result.next());
        holderPid = result.getInt(1);
      }
      try (var statement = holder.prepareStatement(lockSql)) {
        statement.setObject(1, key);
        try (var result = statement.executeQuery()) {
          assertTrue(result.next(), "The row held by the test must already exist");
        }
      }
      var pending = executor.submit(() -> {
        // Keep the authority/SQL regression independent of public DNS.
        var address=java.net.InetAddress.getByAddress(new byte[]{8,8,8,8});
        try(var dns=org.mockito.Mockito.mockStatic(com.getlancer.shared.BoundedDns.class,
            org.mockito.Mockito.withSettings().mockMaker(org.mockito.MockMakers.INLINE))){
          dns.when(()->com.getlancer.shared.BoundedDns.resolve("github.com"))
              .thenReturn(new java.net.InetAddress[]{address});
          return mvc.perform(call).andReturn().getResponse().getStatus();
        }
      });
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      boolean waiting = false;
      while (System.nanoTime() < deadline && !pending.isDone()) {
        if (db.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))",
            Integer.class, holderPid) > 0) {
          waiting = true;
          break;
        }
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
      }
      assertTrue(waiting, "The request must be observed waiting on the actual PostgreSQL lock before revocation");
      revoke.run();
      holder.commit();
      assertEquals(expectedStatus, pending.get(10, TimeUnit.SECONDS).intValue());
    } finally {
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
  }

  void assertNoCommittedEffects() {
    assertEquals(originalProduct, db.queryForMap("SELECT * FROM products WHERE id=?", product));
    assertEquals(originalProfile, db.queryForMap("SELECT * FROM developer_profiles WHERE user_id=?", builder));
    assertEquals(originalProof, db.queryForMap("SELECT * FROM repository_verifications WHERE product_id=?", product));
    assertEquals(originalEntitlement, db.queryForMap("SELECT * FROM showcase_entitlements WHERE user_id=?", builder));
    assertEquals(originalInquiry, db.queryForMap("SELECT * FROM inquiries WHERE id=?", inquiry));
    assertEquals(originalCategories, db.queryForList("SELECT * FROM product_categories WHERE product_id=?", product));
    assertEquals(originalInquiryEvents, db.queryForList("SELECT * FROM inquiry_events WHERE inquiry_id=? ORDER BY id", inquiry));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM earned_capacity_awards", Integer.class));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM moderation_actions", Integer.class));
    assertEquals(analyticsCount, db.queryForObject("SELECT count(*) FROM analytics_events", Integer.class));
  }

  @Test
  void projectEditRollsBackWhenSessionRevokedDuringLaterRepositoryProofWrite() throws Exception {
    // In-place writes still apply to unpublished drafts.
    db.update("UPDATE products SET approval_status='DRAFT',lifecycle_status='DRAFT' WHERE id=?", product);
    originalProduct = db.queryForMap("SELECT * FROM products WHERE id=?", product);
    var call = as(patch("/api/v1/developer/products/" + product), "product-authority-builder")
        .content("""
            {"title":"Changed authority project","summary":"Changed project summary",
             "description":"Changed project description with new public evidence.","projectType":"SAAS",
             "category":"Authority boundary category","technology":"","visibility":"PUBLIC",
             "contribution":"Built changed public evidence","liveUrl":"","videoUrl":"",
             "repositoryUrl":"https://github.com/example/changed-authority-project","pricingNote":"",
             "rightsConfirmed":true,"availableForSimilarWork":true}
            """);
    revokeDuringObservedLockWait("SELECT product_id FROM repository_verifications WHERE product_id=? FOR UPDATE",
        product, call, () -> db.update("DELETE FROM sessions WHERE user_id=?", builder), 401);
    assertNoCommittedEffects();
  }

  void assertProjectReviewCounts(int expected) throws Exception {
    String admin = "product-authority-admin";
    mvc.perform(as(get("/api/v1/admin/metrics").param("section", "moderation"), admin))
        .andExpect(status().isOk()).andExpect(jsonPath("$.projects").value(expected));
    mvc.perform(as(get("/api/v1/admin/dashboard"), admin))
        .andExpect(status().isOk()).andExpect(jsonPath("$.queues[?(@.key == 'projects')].count", contains(expected)));
    mvc.perform(as(get("/api/v1/admin/products/pending").param("status", "PENDING_REVIEW"), admin))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(expected));
  }

  @Test
  void firstUploadBecomesDiscoverableToAnIndependentViewerAfterApproval() throws Exception {
    String owner = "product-authority-builder", reader = "project-catalogue-reader";
    UUID client = user(reader);
    db.update("DELETE FROM user_roles WHERE user_id=? AND role='DEVELOPER'", client);
    String catalogue = "/api/v1/products", title = "First review booking showcase";
    var created = mvc.perform(as(post("/api/v1/developer/products"), owner).content("""
        {"title":"First review booking showcase","summary":"A booking workflow for small providers",
         "description":"An original hosted booking workflow with a locally uploaded cover.",
         "projectType":"SAAS","category":"Authority boundary category","technology":"",
         "visibility":"PUBLIC","contribution":"I designed and built the booking flow","rightsConfirmed":true}
        """)).andExpect(status().isCreated()).andReturn().getResponse();
    String id = json.readTree(created.getContentAsString()).path("id").asText();
    String projectPath = "/api/v1/developer/products/" + id;
    String coverUrl = "/api/v1/media/" + id + "?variant=cover";
    var png = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(16,9,java.awt.image.BufferedImage.TYPE_INT_RGB),
        "png", png);
    var file = new org.springframework.mock.web.MockMultipartFile("file", "cover.png", "image/png", png.toByteArray());
    mvc.perform(as(multipart(projectPath + "/cover").file(file), owner).contentType("multipart/form-data"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.imageUrl").value(coverUrl));
    mvc.perform(as(get(catalogue).param("q", title), reader)).andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(0));

    mvc.perform(as(post(projectPath + "/submit"), owner)).andExpect(status().isNoContent());
    assertProjectReviewCounts(1);
    mvc.perform(as(get(catalogue).param("q", title), reader)).andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(0));
    mvc.perform(as(post("/api/v1/admin/products/" + id + "/approve"), "product-authority-admin")
        .content("{\"reason\":\"Reviewed the uploaded booking example\"}")).andExpect(status().isOk());
    assertProjectReviewCounts(0);

    mvc.perform(get(catalogue)).andExpect(status().isOk()).andExpect(jsonPath("$.items[*].id", hasItem(id)));
    var result = mvc.perform(as(get(catalogue).param("q", title), reader)).andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].id").value(id))
        .andExpect(jsonPath("$.items[0].imageUrl").value(coverUrl))
        .andExpect(jsonPath("$.items[0].lifecycleStatus").value("ACTIVE"))
        .andReturn().getResponse();
    String slug = json.readTree(result.getContentAsString()).path("items").get(0).path("slug").asText();
    mvc.perform(as(get(catalogue + "/" + slug), reader)).andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value(title));
  }

  @Test
  void materialProfileEditHidesSearchResultUntilReapprovalWithoutEndingSession() throws Exception {
    String search = "/api/v1/products";
    String title = "Existing authority project";
    String owner = "product-authority-builder";
    mvc.perform(get(search).param("q", title)).andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(product.toString()));
    mvc.perform(as(put("/api/v1/developer/profile"), owner).content("""
        {"displayName":"product-authority-builder","headline":"Updated builder headline",
         "bio":"Existing builder biography with retained public evidence.","technology":"Java",
         "category":"Authority boundary category","availabilityStatus":"AVAILABLE_NOW",
         "githubUrl":"","linkedinUrl":""}
        """)).andExpect(status().isOk());
    mvc.perform(get(search).param("q", title)).andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(0));
    mvc.perform(as(post("/api/v1/developer/products/" + product + "/submit"), owner))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("BUILDER_APPROVAL_REQUIRED"));
    mvc.perform(as(get("/api/v1/me"), owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(builder.toString()));
    mvc.perform(as(post("/api/v1/developer/profile/submit"), owner)).andExpect(status().isOk());
    mvc.perform(get(search).param("q", title)).andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(0));
    mvc.perform(as(post("/api/v1/admin/profiles/" + builder + "/approve"), "product-authority-admin")
        .content("{\"reason\":\"Reviewed the updated builder headline\"}")).andExpect(status().isOk());
    mvc.perform(get(search).param("q", title)).andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(product.toString()));
  }

  @Test
  void publishedPreviewRemainsVisibleUntilTheExactUpdateIsApproved() throws Exception {
    String owner = "product-authority-builder", admin = "product-authority-admin";
    String editUrl = "/api/v1/developer/products/" + product;
    String listing = "/api/v1/products/" + product, image = "/api/v1/media/" + product;
    var png = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(16,9,java.awt.image.BufferedImage.TYPE_INT_RGB),
        "png", png);
    var publishedCover = com.getlancer.media.MediaService.previewUpload(
        new org.springframework.mock.web.MockMultipartFile("file","cover.png","image/png",png.toByteArray()));
    db.update("UPDATE products SET cover_image=?,cover_type=?,live_url='https://example.com/original' WHERE id=?",
        publishedCover.bytes(),publishedCover.type(),product);
    mvc.perform(as(patch(editUrl),owner).content("""
        {"title":"Updated project title","summary":"A reviewed update for the project",
         "description":"The replacement preview awaits an independent review.","projectType":"SAAS",
         "category":"Authority boundary category","technology":"","visibility":"PUBLIC",
         "contribution":"Updated the project preview","liveUrl":"https://example.com/updated",
         "rightsConfirmed":true,"availableForSimilarWork":true}
        """)).andExpect(status().isOk());

    // Minimal MP4 container fixture. Playback is verified separately in a browser.
    byte[] mp4 = java.nio.ByteBuffer.allocate(34)
        .putInt(16).putInt(0x66747970).putInt(0x69736f6d).putInt(0)
        .putInt(9).putInt(0x6d6f6f76).put((byte) 1)
        .putInt(9).putInt(0x6d646174).put((byte) 1).array();
    var file = new org.springframework.mock.web.MockMultipartFile("file","preview.mp4","video/mp4",mp4);
    mvc.perform(as(multipart(editUrl+"/cover").file(file),owner).contentType("multipart/form-data")).andExpect(status().isOk())
        .andExpect(jsonPath("$.previewVideoUrl").value(image+"?variant=draft-cover"));
    mvc.perform(get(image).param("variant","draft-cover")).andExpect(status().isUnauthorized());
    assertProjectReviewCounts(0);
    mvc.perform(as(post(editUrl+"/submit"),owner)).andExpect(status().isNoContent());
    assertProjectReviewCounts(1);
    mvc.perform(as(multipart(editUrl+"/cover").file(file),owner).contentType("multipart/form-data")).andExpect(status().isConflict());
    mvc.perform(get(listing)).andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Existing authority project"))
        .andExpect(jsonPath("$.liveUrl").value("https://example.com/original"))
        .andExpect(jsonPath("$.imageUrl").value(image+"?variant=cover"))
        .andExpect(jsonPath("$.previewVideoUrl").doesNotExist());
    mvc.perform(get("/api/v1/products").param("q", "Existing authority project"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(product.toString()))
        .andExpect(jsonPath("$.items[0].imageUrl").value(image + "?variant=cover"));
    mvc.perform(get("/api/v1/products").param("q", "Updated project title"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
    mvc.perform(get(image).param("variant","cover")).andExpect(content().bytes(publishedCover.bytes()));
    mvc.perform(as(get("/api/v1/admin/products/pending"),admin))
        .andExpect(jsonPath("$.items[0].previewVideoUrl").value(image+"?variant=draft-cover"))
        .andExpect(jsonPath("$.items[0].title").value("Updated project title"));
    UUID token = db.queryForObject("SELECT draft_review_id FROM products WHERE id=?",UUID.class,product);
    var decision = "/api/v1/admin/products/"+product+"/approve";
    mvc.perform(as(post(decision).param("draftReviewId",UUID.randomUUID().toString()),admin)
        .content("{\"reason\":\"Stale review token\"}")).andExpect(status().isConflict());
    mvc.perform(as(post(decision).param("draftReviewId",token.toString()),admin)
        .content("{\"reason\":\"Reviewed the submitted preview\"}")).andExpect(status().isOk());
    assertProjectReviewCounts(0);
    mvc.perform(get(listing)).andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Updated project title"))
        .andExpect(jsonPath("$.liveUrl").value("https://example.com/updated"))
        .andExpect(jsonPath("$.lifecycleStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.previewVideoUrl").value(image+"?variant=cover"));
    mvc.perform(get("/api/v1/products").param("q", "Updated project title"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(product.toString()))
        .andExpect(jsonPath("$.items[0].previewVideoUrl").value(image + "?variant=cover"));
    mvc.perform(get("/api/v1/products").param("q", "Existing authority project"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
    mvc.perform(get(image).param("variant","cover").header("Range","bytes=0-15"))
        .andExpect(status().isPartialContent()).andExpect(content().contentType("video/mp4"))
        .andExpect(header().string("Content-Range","bytes 0-15/34"))
        .andExpect(content().bytes(java.util.Arrays.copyOf(mp4,16)));
  }

  @Test
  void profileEditRejectsDeveloperRoleRevokedDuringProfileLockWait() throws Exception {
    var call = as(put("/api/v1/developer/profile"), "product-authority-builder")
        .content("""
            {"displayName":"Changed builder name","headline":"Changed builder headline",
             "bio":"Changed builder biography with new public evidence.","technology":"Java",
             "category":"Authority boundary category","availabilityStatus":"AVAILABLE_NOW",
             "githubUrl":"","linkedinUrl":""}
            """);
    revokeDuringObservedLockWait("SELECT user_id FROM developer_profiles WHERE user_id=? FOR UPDATE",
        builder, call, () -> db.update("DELETE FROM user_roles WHERE user_id=? AND role='DEVELOPER'", builder), 403);
    assertNoCommittedEffects();
  }

  @Test
  void repositoryApprovalRejectsMfaRevokedDuringVerificationLockWait() throws Exception {
    var call = as(post("/api/v1/admin/verifications/" + product + "/approve"), "product-authority-admin")
        .content("{\"reason\":\"Independently checked the repository proof\"}");
    revokeDuringObservedLockWait("SELECT product_id FROM repository_verifications WHERE product_id=? FOR UPDATE",
        product, call, () -> db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?", operator), 403);
    assertNoCommittedEffects();
  }

  @Test
  void earnedCapacityRejectsAdminRoleRevokedDuringEntitlementLockWait() throws Exception {
    var call = as(post("/api/v1/admin/earned-capacity/" + inquiry), "product-authority-admin")
        .content("{\"reason\":\"Independently checked the completed client outcome\"}");
    revokeDuringObservedLockWait("SELECT user_id FROM showcase_entitlements WHERE user_id=? FOR UPDATE",
        builder, call, () -> db.update("DELETE FROM user_roles WHERE user_id=? AND role='ADMIN'", operator), 403);
    assertNoCommittedEffects();
  }
}
