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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/** Real disposable PostgreSQL transactions; object storage is an in-process mock. */
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
class MediaLifecycleIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired Security security;
  @Autowired PlatformTransactionManager manager;
  MediaService service;
  S3Client s3;
  S3Presigner presigner;
  UUID owner, product;
  MockHttpServletRequest request, outsider;
  byte[] png;
  final Map<String, byte[]> objects = new ConcurrentHashMap<>();
  final Set<String> deleted = ConcurrentHashMap.newKeySet();
  Consumer<String> beforeGet = key -> {};
  Consumer<String> afterPut = key -> {};
  boolean failFull;
  boolean failThumbnail;

  @BeforeEach void fixtures() throws Exception {
    db.execute("TRUNCATE users CASCADE");
    db.execute("TRUNCATE storage_deletions");
    objects.clear(); deleted.clear();
    owner = UUID.randomUUID();
    request = actor(owner);
    outsider = actor(UUID.randomUUID());
    product = UUID.randomUUID();
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,"
        + "category,technology,contribution_text,rights_confirmed) VALUES(?,?,?,'Proof project',"
        + "'A proof project','A working synthetic proof project','SAAS','Inventory','Java',"
        + "'Built the project',true)", product, owner, "proof-" + product);
    var output = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(16, 8, BufferedImage.TYPE_INT_RGB), "png", output);
    png = output.toByteArray();
    s3 = mock(S3Client.class);
    presigner = mock(S3Presigner.class);
    var signed = mock(PresignedPutObjectRequest.class);
    when(signed.url()).thenReturn(URI.create("https://storage.example.test/pending").toURL());
    when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(signed);
    when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenAnswer(call -> {
      assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "PUT must release DB transaction");
      var put = call.getArgument(0, PutObjectRequest.class);
      String key = put.key();
      UUID media = UUID.fromString(key.substring(key.lastIndexOf('/') + 1).replace("-640.png", "").replace(".png", ""));
      assertEquals(1, count("SELECT count(*) FROM media_write_intents WHERE id=?", media), "Journal must commit before PUT");
      if ((failFull && !key.endsWith("-640.png")) || (failThumbnail && key.endsWith("-640.png")))
        throw S3Exception.builder().statusCode(503).message("Synthetic storage failure").build();
      try (var stream = call.getArgument(1, RequestBody.class).contentStreamProvider().newStream()) {
        objects.put(key, stream.readAllBytes());
      }
      afterPut.accept(key);
      return PutObjectResponse.builder().build();
    });
    when(s3.getObject(any(GetObjectRequest.class))).thenAnswer(call -> {
      assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "GET must release DB transaction");
      String key = call.getArgument(0, GetObjectRequest.class).key();
      UUID upload = UUID.fromString(key.substring(key.lastIndexOf('/') + 1));
      assertEquals(1, count("SELECT count(*) FROM media_write_intents WHERE upload_id=?", upload));
      beforeGet.accept(key);
      byte[] bytes = objects.get(key);
      if (bytes == null) throw NoSuchKeyException.builder().message("Synthetic missing object").build();
      return new ResponseInputStream<>(GetObjectResponse.builder().contentLength((long) bytes.length)
          .contentType("image/png").build(), AbortableInputStream.create(new ByteArrayInputStream(bytes)));
    });
    when(s3.deleteObject(any(DeleteObjectRequest.class))).thenAnswer(call -> {
      assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "DELETE must release DB transaction");
      String key = call.getArgument(0, DeleteObjectRequest.class).key();
      objects.remove(key); deleted.add(key);
      return DeleteObjectResponse.builder().build();
    });
    service = service(db, manager);
  }

  MediaService service(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
    return new MediaService(jdbc, security, s3, presigner, "private-proof", true, transactions);
  }

  MockHttpServletRequest actor(UUID user) {
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())",
        user, user + "@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER')", user);
    db.update("INSERT INTO developer_profiles(user_id,slug,approval_status) VALUES(?,?,'APPROVED')", user, "builder-" + user);
    String token = UUID.randomUUID().toString();
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')", Support.hash(token), user);
    var r = new MockHttpServletRequest(); r.setCookies(new Cookie("gl_session", token)); return r;
  }

  int count(String sql, Object... args) { return db.queryForObject(sql, Integer.class, args); }
  <T> T tx(java.util.function.Supplier<T> work) { return new TransactionTemplate(manager).execute(status -> work.get()); }
  MockMultipartFile file() { return new MockMultipartFile("file", "proof.png", "image/png", png); }
  Map<String, Object> completion(UUID upload) { return Map.of("uploadId", upload, "altText", "Synthetic proof screenshot"); }
  UUID pending() {
    var signed = tx(() -> service.requestUpload(product,
        Map.of("filename", "proof.png", "contentType", "image/png", "sizeBytes", png.length), request));
    UUID upload = (UUID) signed.get("uploadId");
    objects.put("pending/" + product + "/" + upload, png);
    return upload;
  }
  void ageWrites() { db.update("UPDATE media_write_intents SET expires_at=clock_timestamp()-interval '6 minutes'"); }
  ApiError error(String code, org.junit.jupiter.api.function.Executable work) {
    var error = assertThrows(ApiError.class, work); assertEquals(code, error.code); return error;
  }

  @Test void directUploadCommitsCompleteMetadataAfterBothProviderWrites() throws Exception {
    var media = service.upload(product, file(), "Project screenshot", request);
    UUID id = (UUID) media.get("id");
    var row = db.queryForMap("SELECT * FROM product_media WHERE id=?", id);
    assertEquals(16, row.get("width")); assertEquals(8, row.get("height"));
    assertEquals(true, row.get("verified"));
    assertTrue(objects.containsKey((String) row.get("storage_key")));
    assertTrue(objects.containsKey((String) row.get("thumbnail_key")));
    assertEquals(0, count("SELECT count(*) FROM media_write_intents"));
  }

  @Test void signedReservationsCannotBeDisplacedAndCompletionDoesNotCountItsOwnSlotTwice() throws Exception {
    var uploads = new ArrayList<UUID>();
    for (int i = 0; i < 6; i++) uploads.add(pending());
    error("MEDIA_LIMIT", () -> service.upload(product, file(), "Proof", request));
    error("MEDIA_LIMIT", this::pending);
    for (UUID upload : uploads) service.complete(product, completion(upload), request);
    assertEquals(6, count("SELECT count(*) FROM product_media WHERE product_id=?", product));
    assertEquals(6, count("SELECT count(*) FROM media_uploads WHERE completed_at IS NOT NULL"));
    assertEquals(0, count("SELECT count(*) FROM media_write_intents"));
  }

  @Test void directWriteReservesCapacityWhileProviderRunsWithoutHoldingProductLock() throws Exception {
    for (int i = 0; i < 5; i++) service.upload(product, file(), "Proof", request);
    var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
    afterPut = key -> { if (!key.endsWith("-640.png")) { entered.countDown(); await(release); } };
    var executor = Executors.newSingleThreadExecutor();
    try {
      var future = executor.submit(() -> service.upload(product, file(), "Last proof", request));
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      assertEquals(1, count("SELECT count(*) FROM media_write_intents"));
      error("MEDIA_LIMIT", this::pending);
      error("MEDIA_LIMIT", () -> service.upload(product, file(), "Excess", request));
      release.countDown(); future.get(5, TimeUnit.SECONDS);
      assertEquals(6, count("SELECT count(*) FROM product_media"));
    } finally { release.countDown(); executor.shutdownNow(); }
  }

  @Test void simultaneousCompletionIsBusyThenRetryIsIdempotentAndDeletionConsumesUpload() throws Exception {
    UUID upload = pending();
    var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
    beforeGet = key -> { entered.countDown(); await(release); };
    var executor = Executors.newSingleThreadExecutor();
    try {
      var future = executor.submit(() -> service.complete(product, completion(upload), request));
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      error("UPLOAD_BUSY", () -> service.complete(product, completion(upload), request));
      assertEquals(1, count("SELECT count(*) FROM media_write_intents WHERE upload_id=?", upload));
      release.countDown(); UUID media = (UUID) future.get(5, TimeUnit.SECONDS).get("id");
      assertEquals(media, service.complete(product, completion(upload), request).get("id"));
      verify(s3, times(1)).getObject(any(GetObjectRequest.class));
      tx(() -> service.remove(product, media, request));
      error("UPLOAD_CONSUMED", () -> service.complete(product, completion(upload), request));
      assertEquals(0, count("SELECT count(*) FROM product_media"));
      assertEquals(1, count("SELECT count(*) FROM media_uploads WHERE completed_at IS NOT NULL AND completed_media_id IS NULL"));
      assertEquals(2, count("SELECT count(*) FROM storage_deletions"));
    } finally { release.countDown(); executor.shutdownNow(); }
  }

  @Test void thumbnailFailureRetainsJournalAndDelayedCleanupRemovesBothPossibleKeys() {
    failThumbnail = true;
    assertThrows(S3Exception.class, () -> service.upload(product, file(), "Proof", request));
    assertEquals(0, count("SELECT count(*) FROM product_media"));
    assertEquals(1, count("SELECT count(*) FROM media_write_intents"));
    service.removeObjects(); assertEquals(1, objects.size()); assertTrue(deleted.isEmpty());
    ageWrites(); service.removeObjects();
    assertTrue(objects.isEmpty()); assertEquals(2, deleted.size());
    assertEquals(0, count("SELECT count(*) FROM media_write_intents"));
    assertEquals(0, count("SELECT count(*) FROM storage_deletions"));
  }

  @Test void failedFullPutStillJournalsBothPossibleKeysBeforeAnyProviderCall() {
    failFull = true;
    assertThrows(S3Exception.class, () -> service.upload(product, file(), "Proof", request));
    assertEquals(0, count("SELECT count(*) FROM product_media"));
    assertEquals(1, count("SELECT count(*) FROM media_write_intents"));
    ageWrites(); service.removeObjects();
    assertTrue(objects.isEmpty()); assertEquals(2, deleted.size());
  }

  @Test void missingOrMismatchedStagingIsRejectedAndUnwrittenUploadCanRetryImmediately() throws Exception {
    UUID upload = pending(); String key = "pending/" + product + "/" + upload;
    objects.remove(key);
    error("UPLOAD_NOT_READY", () -> service.complete(product, completion(upload), request));
    assertEquals(0, count("SELECT count(*) FROM product_media"));
    assertEquals(0, count("SELECT count(*) FROM media_write_intents"));
    objects.put(key, png);
    db.update("UPDATE media_uploads SET size_bytes=size_bytes+1 WHERE id=?", upload);
    error("INVALID_IMAGE", () -> service.complete(product, completion(upload), request));
    assertEquals(0, count("SELECT count(*) FROM product_media"));
    assertEquals(0, count("SELECT count(*) FROM media_write_intents"));
    db.update("UPDATE media_uploads SET size_bytes=? WHERE id=?", png.length, upload);
    assertNotNull(service.complete(product, completion(upload), request).get("id"));
    assertEquals(1, count("SELECT count(*) FROM product_media"));
    assertTrue(objects.containsKey(key), "Original staging expiry is retained across completion");
  }

  @Test void abandonedJournalRecoversAfterRestartButLinkedMetadataIsNeverDeleted() throws Exception {
    UUID id = UUID.randomUUID();
    var abandoned = new MediaService.WriteIntent(id, product, null, owner);
    db.update("INSERT INTO media_write_intents(id,product_id,expires_at) VALUES(?,?,clock_timestamp()-interval '6 minutes')", id, product);
    objects.put(abandoned.key(), png); objects.put(abandoned.thumbnailKey(), png);
    service.removeObjects(); assertFalse(objects.containsKey(abandoned.key()));
    UUID linked = (UUID) service.upload(product, file(), "Proof", request).get("id");
    var safe = new MediaService.WriteIntent(linked, product, null, owner);
    db.update("INSERT INTO media_write_intents(id,product_id,expires_at) VALUES(?,?,clock_timestamp()-interval '6 minutes')", linked, product);
    service.removeObjects();
    assertTrue(objects.containsKey(safe.key())); assertTrue(objects.containsKey(safe.thumbnailKey()));
    assertFalse(deleted.contains(safe.key()));
    assertEquals(1, count("SELECT count(*) FROM product_media WHERE id=?", linked));
  }

  @Test void sessionRevocationDuringProviderIoCannotAttachProof() {
    afterPut = key -> { if (!key.endsWith("-640.png")) db.update("DELETE FROM sessions WHERE user_id=?", owner); };
    error("UNAUTHENTICATED", () -> service.upload(product, file(), "Proof", request));
    assertEquals(0, count("SELECT count(*) FROM product_media"));
    assertEquals(1, count("SELECT count(*) FROM media_write_intents"));
    ageWrites(); service.removeObjects(); assertTrue(objects.isEmpty());
  }

  @Test void changedProductOrExpiredWriteCannotAttachProof() throws Exception {
    afterPut = key -> db.update("UPDATE products SET approval_status='PENDING_REVIEW' WHERE id=?", product);
    error("NOT_FOUND", () -> service.upload(product, file(), "Proof", request));
    assertEquals(0, count("SELECT count(*) FROM product_media"));
    ageWrites(); service.removeObjects();
    db.update("UPDATE products SET approval_status='DRAFT' WHERE id=?", product);
    afterPut = key -> db.update("UPDATE media_write_intents SET expires_at=clock_timestamp()-interval '1 second'");
    error("UPLOAD_EXPIRED", () -> service.upload(product, file(), "Proof", request));
    assertEquals(0, count("SELECT count(*) FROM product_media"));
  }

  @Test void pendingExpiryIsCheckedAfterWaitingForItsRowLock() throws Exception {
    UUID upload = pending();
    var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
    var selecting = new CountDownLatch(1); var executor = Executors.newFixedThreadPool(2);
    var observing = new JdbcTemplate(Objects.requireNonNull(db.getDataSource())) {
      @Override public List<Map<String, Object>> queryForList(String sql, Object... args) {
        if (sql.contains("FROM media_uploads") && sql.contains("FOR UPDATE")) selecting.countDown();
        return super.queryForList(sql, args);
      }
    };
    try {
      var holder = executor.submit(() -> tx(() -> {
        db.queryForList("SELECT id FROM media_uploads WHERE id=? FOR UPDATE", upload);
        locked.countDown(); await(release);
        db.update("UPDATE media_uploads SET expires_at=clock_timestamp()-interval '1 second' WHERE id=?", upload);
        return true;
      }));
      assertTrue(locked.await(5, TimeUnit.SECONDS));
      var completing = executor.submit(() -> service(observing, manager).complete(product, completion(upload), request));
      assertTrue(selecting.await(5, TimeUnit.SECONDS)); release.countDown(); holder.get(5, TimeUnit.SECONDS);
      var failure = assertThrows(ExecutionException.class, () -> completing.get(5, TimeUnit.SECONDS));
      assertEquals("UPLOAD_EXPIRED", ((ApiError) failure.getCause()).code);
      verify(s3, never()).getObject(any(GetObjectRequest.class));
      assertEquals(0, count("SELECT count(*) FROM media_write_intents"));
    } finally { release.countDown(); executor.shutdownNow(); }
  }

  @Test void writeDeadlineIsCheckedAfterWaitingForIntentLock() throws Exception {
    var stored = new CountDownLatch(1); var providerRelease = new CountDownLatch(1);
    var locked = new CountDownLatch(1); var lockRelease = new CountDownLatch(1);
    var selecting = new CountDownLatch(1); var executor = Executors.newFixedThreadPool(2);
    afterPut = key -> { if (key.endsWith("-640.png")) { stored.countDown(); await(providerRelease); } };
    var observing = new JdbcTemplate(Objects.requireNonNull(db.getDataSource())) {
      @Override public List<Map<String, Object>> queryForList(String sql, Object... args) {
        if (sql.contains("FROM media_write_intents") && sql.contains("FOR UPDATE")) selecting.countDown();
        return super.queryForList(sql, args);
      }
    };
    try {
      var uploading = executor.submit(() -> service(observing, manager).upload(product, file(), "Proof", request));
      assertTrue(stored.await(5, TimeUnit.SECONDS));
      UUID id = db.queryForObject("SELECT id FROM media_write_intents", UUID.class);
      var holder = executor.submit(() -> tx(() -> {
        db.queryForList("SELECT id FROM media_write_intents WHERE id=? FOR UPDATE", id);
        locked.countDown(); await(lockRelease);
        db.update("UPDATE media_write_intents SET expires_at=clock_timestamp()-interval '1 second' WHERE id=?", id);
        return true;
      }));
      assertTrue(locked.await(5, TimeUnit.SECONDS)); providerRelease.countDown();
      assertTrue(selecting.await(5, TimeUnit.SECONDS)); lockRelease.countDown(); holder.get(5, TimeUnit.SECONDS);
      var failure = assertThrows(ExecutionException.class, () -> uploading.get(5, TimeUnit.SECONDS));
      assertEquals("UPLOAD_EXPIRED", ((ApiError) failure.getCause()).code);
      assertEquals(0, count("SELECT count(*) FROM product_media"));
      assertEquals(1, count("SELECT count(*) FROM media_write_intents"));
    } finally { providerRelease.countDown(); lockRelease.countDown(); executor.shutdownNow(); }
  }

  @Test void failedMetadataInsertRollsBackAndKeepsDurableCleanup() {
    var failing = new JdbcTemplate(Objects.requireNonNull(db.getDataSource())) {
      @Override public int update(String sql, Object... args) {
        int result = super.update(sql, args);
        if (sql.startsWith("INSERT INTO product_media"))
          throw new org.springframework.dao.DataAccessResourceFailureException("Synthetic metadata failure");
        return result;
      }
    };
    var failingService = service(failing, manager);
    assertThrows(org.springframework.dao.DataAccessException.class, () -> failingService.upload(product, file(), "Proof", request));
    assertEquals(0, count("SELECT count(*) FROM product_media"));
    assertEquals(1, count("SELECT count(*) FROM media_write_intents"));
    ageWrites(); service.removeObjects(); assertTrue(objects.isEmpty());
  }

  @Test void successfulButReportedFailedCommitKeepsMetadataAndObjects() throws Exception {
    UUID upload = pending(); var injected = new AtomicBoolean();
    PlatformTransactionManager uncertain = new PlatformTransactionManager() {
      public TransactionStatus getTransaction(TransactionDefinition definition) { return manager.getTransaction(definition); }
      public void rollback(TransactionStatus status) { manager.rollback(status); }
      public void commit(TransactionStatus status) {
        manager.commit(status);
        if (count("SELECT count(*) FROM product_media") > 0 && injected.compareAndSet(false, true))
          throw new org.springframework.transaction.TransactionSystemException("Synthetic lost commit acknowledgement");
      }
    };
    var uncertainService = service(db, uncertain);
    assertThrows(org.springframework.transaction.TransactionSystemException.class,
        () -> uncertainService.complete(product, completion(upload), request));
    UUID media = (UUID) service.complete(product, completion(upload), request).get("id");
    var safe = new MediaService.WriteIntent(media, product, upload, owner);
    service.removeObjects();
    assertTrue(objects.containsKey(safe.key())); assertTrue(objects.containsKey(safe.thumbnailKey()));
    assertEquals(0, count("SELECT count(*) FROM media_write_intents"));
    assertEquals(0, count("SELECT count(*) FROM storage_deletions"));
  }

  @Test void ownedReorderIsAtomicAndForeignMutationsCannotChangeMedia() throws Exception {
    UUID first = (UUID) service.upload(product, file(), "First", request).get("id");
    UUID second = (UUID) service.upload(product, file(), "Second", request).get("id");
    error("NOT_FOUND", () -> service.upload(product, file(), "Foreign", outsider));
    error("NOT_FOUND", () -> tx(() -> service.remove(product, first, outsider)));
    error("NOT_FOUND", () -> tx(() -> service.order(product, Map.of("items", List.of()), outsider)));
    error("MEDIA_CHANGED", () -> tx(() -> service.order(product,
        Map.of("items", List.of(Map.of("id", first, "alt", "Must roll back"))), request)));
    assertEquals("First", db.queryForObject("SELECT alt_text FROM product_media WHERE id=?", String.class, first));
    error("VALIDATION_ERROR", () -> tx(() -> service.order(product,
        Map.of("items", List.of(Map.of("id", first, "alt", "One"), Map.of("id", first, "alt", "Duplicate"))), request)));
    tx(() -> service.order(product, Map.of("items", List.of(Map.of("id", second, "alt", "Second proof"),
        Map.of("id", first, "alt", "First proof"))), request));
    assertEquals(List.of(second, first), db.queryForList("SELECT id FROM product_media ORDER BY sort_order", UUID.class));
    tx(() -> service.remove(product, first, request));
    assertEquals(1, count("SELECT count(*) FROM product_media"));
    assertEquals(2, count("SELECT count(*) FROM storage_deletions"));
  }

  static void await(CountDownLatch latch) {
    try { if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Synthetic storage wait timed out"); }
    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
  }
}
