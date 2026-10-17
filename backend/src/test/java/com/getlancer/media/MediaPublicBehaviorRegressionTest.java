package com.getlancer.media;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.getlancer.shared.ApiError;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/** Uses preexisting public APIs/schema so the same tests can exercise the pristine service. */
@ContextConfiguration(initializers = TestDatabaseGuard.class)
@SpringBootTest(properties = {
    "app.environment=local", "app.jobs-enabled=false", "app.admin-email=operator@example.test",
    "app.mfa.active-key-id=v1", "app.mfa.keyring=v1:AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
    "app.admin-password=", "app.admin-totp=", "spring.config.import=",
    "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}",
    "spring.datasource.username=${TEST_DB_USERNAME:postgres}", "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.hikari.schema=getlancer_test", "spring.flyway.default-schema=getlancer_test",
    "spring.flyway.schemas=getlancer_test", "app.origin=http://localhost:3000",
    "app.secure-cookie=false", "app.storage.access-key=", "app.storage.secret-key="
})
class MediaPublicBehaviorRegressionTest {
  @Autowired JdbcTemplate db;
  @Autowired MediaService service;
  Object target, previousStorage, previousPresigner;
  UUID product;
  MockHttpServletRequest request;
  byte[] png;
  final Map<String, byte[]> objects = new ConcurrentHashMap<>();

  @BeforeEach void fixtures() throws Exception {
    db.execute("TRUNCATE users CASCADE"); db.execute("TRUNCATE storage_deletions");
    UUID owner = UUID.randomUUID(); product = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())", owner, owner + "@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER')", owner);
    db.update("INSERT INTO developer_profiles(user_id,slug,approval_status) VALUES(?,?,'APPROVED')", owner, "builder-" + owner);
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,rights_confirmed)"
        + " VALUES(?,?,?,'Proof project','Proof summary','Synthetic proof project','SAAS','Inventory','Java','Built the project',true)", product, owner, "proof-" + product);
    String token = UUID.randomUUID().toString();
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')", Support.hash(token), owner);
    request = new MockHttpServletRequest(); request.setCookies(new Cookie("gl_session", token));
    var output = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(16, 8, BufferedImage.TYPE_INT_RGB), "png", output); png = output.toByteArray();
    var storage = mock(S3Client.class);
    when(storage.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenAnswer(call -> {
      try (var stream = call.getArgument(1, RequestBody.class).contentStreamProvider().newStream()) {
        objects.put(call.getArgument(0, PutObjectRequest.class).key(), stream.readAllBytes());
      }
      return PutObjectResponse.builder().build();
    });
    when(storage.getObject(any(GetObjectRequest.class))).thenAnswer(call -> {
      byte[] bytes = Objects.requireNonNull(objects.get(call.getArgument(0, GetObjectRequest.class).key()));
      return new ResponseInputStream<>(GetObjectResponse.builder().contentLength((long) bytes.length).contentType("image/png").build(),
          AbortableInputStream.create(new ByteArrayInputStream(bytes)));
    });
    var presigner = mock(S3Presigner.class); var signed = mock(PresignedPutObjectRequest.class);
    when(signed.url()).thenReturn(URI.create("https://storage.example.test/pending").toURL());
    when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(signed);
    target = AopTestUtils.getUltimateTargetObject(service);
    previousStorage = ReflectionTestUtils.getField(target, "s3");
    previousPresigner = ReflectionTestUtils.getField(target, "presigner");
    ReflectionTestUtils.setField(target, "s3", storage);
    ReflectionTestUtils.setField(target, "presigner", presigner);
  }

  @AfterEach void restoreStorage() {
    if (target != null) {
      ReflectionTestUtils.setField(target, "s3", previousStorage);
      ReflectionTestUtils.setField(target, "presigner", previousPresigner);
    }
  }

  UUID pending() {
    var signed = service.requestUpload(product,
        Map.of("filename", "proof.png", "contentType", "image/png", "sizeBytes", png.length), request);
    UUID upload = (UUID) signed.get("uploadId");
    objects.put("pending/" + product + "/" + upload, png);
    return upload;
  }

  Map<String, Object> completion(UUID upload) { return Map.of("uploadId", upload, "altText", "Proof screenshot"); }

  @Test void deletedImageCannotBeRecreatedByReplayingItsSuccessfulCompletion() throws Exception {
    UUID upload = pending();
    UUID media = (UUID) service.complete(product, completion(upload), request).get("id");
    assertEquals(media, service.complete(product, completion(upload), request).get("id"));
    service.remove(product, media, request);
    var rejected = assertThrows(ApiError.class, () -> service.complete(product, completion(upload), request));
    assertEquals("UPLOAD_CONSUMED", rejected.code);
    assertEquals(0, db.queryForObject("SELECT count(*) FROM product_media WHERE product_id=?", Integer.class, product));
  }

  @Test void multipartCannotDisplaceSixAcceptedSignedReservationsAndThoseReservationsCanComplete() throws Exception {
    var uploads = new ArrayList<UUID>();
    for (int i = 0; i < 6; i++) uploads.add(pending());
    var rejected = assertThrows(ApiError.class, () -> service.upload(product,
        new MockMultipartFile("file", "proof.png", "image/png", png), "Extra proof", request));
    assertEquals("MEDIA_LIMIT", rejected.code);
    for (UUID upload : uploads) assertNotNull(service.complete(product, completion(upload), request).get("id"));
    assertEquals(6, db.queryForObject("SELECT count(*) FROM product_media WHERE product_id=?", Integer.class, product));
  }
}
