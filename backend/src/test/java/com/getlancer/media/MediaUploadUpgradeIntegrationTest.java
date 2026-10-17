package com.getlancer.media;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.*;
import javax.imageio.ImageIO;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/** Exercises the actual 41 -> 42 -> 43 Flyway rollout in its own disposable schema. */
class MediaUploadUpgradeIntegrationTest {
  @Test void legacyDeletedAndAmbiguousUploadsAreRetiredWithoutChangingSignedExpiry() throws Exception {
    String url = System.getenv().getOrDefault("TEST_DB_URL", "jdbc:postgresql://localhost:5432/getlancer_test");
    String user = System.getenv().getOrDefault("TEST_DB_USERNAME", "postgres");
    String password = System.getenv().getOrDefault("TEST_DB_PASSWORD", "");
    TestDatabaseGuard.validate(new MockEnvironment()
        .withProperty("TEST_DATABASE_RESET", System.getenv().getOrDefault("TEST_DATABASE_RESET", "false"))
        .withProperty("spring.datasource.url", url)
        .withProperty("spring.flyway.default-schema", "getlancer_test"));
    String schema = "getlancer_media_upgrade_" + UUID.randomUUID().toString().replace("-", "");
    assertTrue(schema.matches("getlancer_media_upgrade_[a-f0-9]{32}"));
    var admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
    admin.execute("CREATE SCHEMA " + schema);
    try {
      var source = new DriverManagerDataSource(url, user, password);
      var properties = new Properties(); properties.setProperty("currentSchema", schema);
      source.setConnectionProperties(properties);
      var db = new JdbcTemplate(source);
      migrate(source, schema, "41");
      UUID owner = UUID.randomUUID(), product = UUID.randomUUID();
      UUID survivingMedia = UUID.randomUUID(), deletedMedia = UUID.randomUUID();
      UUID survivingUpload = UUID.randomUUID(), deletedUpload = UUID.randomUUID(), unusedUpload = UUID.randomUUID();
      db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())", owner, owner + "@example.test");
      db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER')", owner);
      db.update("INSERT INTO developer_profiles(user_id,slug,approval_status) VALUES(?,?,'APPROVED')", owner, "builder-" + owner);
      db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,rights_confirmed)"
          + " VALUES(?,?,?,'Upgrade proof','Proof summary','Synthetic migration proof','SAAS','Inventory','Java','Built the project',true)", product, owner, "proof-" + product);
      for (UUID media : List.of(survivingMedia, deletedMedia))
        db.update("INSERT INTO product_media(id,product_id,storage_key,content_type,size_bytes,verified,alt_text)"
            + " VALUES(?,?,?,'image/png',100,true,'Legacy proof')", media, product, "products/" + product + "/" + media + ".png");
      legacyUpload(db, product, survivingUpload, survivingMedia);
      legacyUpload(db, product, deletedUpload, deletedMedia);
      legacyUpload(db, product, unusedUpload, null);
      db.update("DELETE FROM product_media WHERE id=?", deletedMedia);
      assertNull(db.queryForMap("SELECT completed_media_id FROM media_uploads WHERE id=?", deletedUpload).get("completed_media_id"),
          "The actual legacy FK erases the completed binding on deletion");
      var expiries = new HashMap<UUID, Object>();
      for (var row : db.queryForList("SELECT id,expires_at FROM media_uploads"))
        expiries.put((UUID) row.get("id"), row.get("expires_at"));

      migrate(source, schema, "42");
      assertNotNull(db.queryForMap("SELECT completed_at FROM media_uploads WHERE id=?", survivingUpload).get("completed_at"));
      assertEquals(2, db.queryForObject("SELECT count(*) FROM media_uploads WHERE completed_at IS NULL", Integer.class),
          "V42 alone cannot distinguish deleted completions from unused reservations");
      migrate(source, schema, "43");
      assertEquals("43", db.queryForObject("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1", String.class));
      assertEquals(3, db.queryForObject("SELECT count(*) FROM media_uploads WHERE completed_at IS NOT NULL", Integer.class));
      for (var row : db.queryForList("SELECT id,expires_at FROM media_uploads"))
        assertEquals(expiries.get(row.get("id")), row.get("expires_at"), "Staging cleanup must retain each original signed expiry");
      assertEquals(2, db.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname=? AND indexname IN"
          + " ('media_uploads_pending_product','media_uploads_expiry')", Integer.class, schema));

      String token = UUID.randomUUID().toString();
      db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')", Support.hash(token), owner);
      var request = new MockHttpServletRequest(); request.setCookies(new Cookie("gl_session", token));
      var objects = new HashMap<String, byte[]>();
      var s3 = mock(S3Client.class);
      when(s3.getObject(any(GetObjectRequest.class))).thenAnswer(call -> {
        byte[] bytes = Objects.requireNonNull(objects.get(call.getArgument(0, GetObjectRequest.class).key()));
        return new ResponseInputStream<>(GetObjectResponse.builder().contentLength((long) bytes.length).contentType("image/png").build(),
            AbortableInputStream.create(new ByteArrayInputStream(bytes)));
      });
      when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenAnswer(call -> {
        try (var stream = call.getArgument(1, RequestBody.class).contentStreamProvider().newStream()) {
          objects.put(call.getArgument(0, PutObjectRequest.class).key(), stream.readAllBytes());
        }
        return PutObjectResponse.builder().build();
      });
      var presigner = mock(S3Presigner.class); var signed = mock(PresignedPutObjectRequest.class);
      when(signed.url()).thenReturn(URI.create("https://storage.example.test/pending").toURL());
      when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(signed);
      var manager = new DataSourceTransactionManager(source);
      var service = new MediaService(db, new Security(db, "http://localhost:3000", 30), s3, presigner, "private-proof", false, manager);
      assertEquals(survivingMedia, service.complete(product, completion(survivingUpload), request).get("id"));
      for (UUID consumed : List.of(deletedUpload, unusedUpload)) {
        var rejected = assertThrows(ApiError.class, () -> service.complete(product, completion(consumed), request));
        assertEquals("UPLOAD_CONSUMED", rejected.code);
        assertEquals("This upload can no longer be used. Start a new upload.", rejected.getMessage());
      }
      verifyNoInteractions(s3);
      var output = new ByteArrayOutputStream();
      ImageIO.write(new BufferedImage(16, 8, BufferedImage.TYPE_INT_RGB), "png", output);
      byte[] png = output.toByteArray();
      var fresh = new TransactionTemplate(manager).execute(status -> service.requestUpload(product,
          Map.of("filename", "fresh.png", "contentType", "image/png", "sizeBytes", png.length), request));
      UUID freshUpload = (UUID) Objects.requireNonNull(fresh).get("uploadId");
      assertNull(db.queryForMap("SELECT completed_at FROM media_uploads WHERE id=?", freshUpload).get("completed_at"));
      objects.put("pending/" + product + "/" + freshUpload, png);
      UUID freshMedia = (UUID) service.complete(product, completion(freshUpload), request).get("id");
      assertNotEquals(survivingMedia, freshMedia);
      assertEquals(2, db.queryForObject("SELECT count(*) FROM product_media WHERE product_id=? AND verified", Integer.class, product));
      assertEquals(freshMedia, service.complete(product, completion(freshUpload), request).get("id"));
    } finally {
      admin.execute("DROP SCHEMA " + schema + " CASCADE");
    }
  }

  static void migrate(DriverManagerDataSource source, String schema, String version) {
    Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).createSchemas(false)
        .target(MigrationVersion.fromVersion(version)).load().migrate();
  }

  static void legacyUpload(JdbcTemplate db, UUID product, UUID upload, UUID completed) {
    db.update("INSERT INTO media_uploads(id,product_id,storage_key,content_type,size_bytes,expires_at,completed_media_id)"
        + " VALUES(?,?,?,'image/png',100,clock_timestamp()+interval '10 minutes',?)",
        upload, product, "pending/" + product + "/" + upload, completed);
  }

  static Map<String, Object> completion(UUID upload) { return Map.of("uploadId", upload, "altText", "Upgrade proof screenshot"); }
}
