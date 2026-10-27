package com.getlancer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.commerce.CommerceStorage;
import com.getlancer.components.ComponentPreviewService;
import com.getlancer.hosting.DemoPublisherClient;
import com.getlancer.hosting.HostingConfiguration;
import com.getlancer.hosting.HostingRepository;
import com.getlancer.hosting.HostingService;
import com.getlancer.media.MediaService;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import com.getlancer.shared.Support;
import com.getlancer.testing.TestDatabaseGuard;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/** Real PostgreSQL authority and journals, with deterministic in-process storage/publisher I/O. */
@ContextConfiguration(initializers = TestDatabaseGuard.class)
@SpringBootTest(properties = {
    "app.environment=local", "app.jobs-enabled=false", "app.admin-email=operator@example.test",
    "app.admin-password=", "app.admin-totp=", "spring.config.import=",
    "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/getlancer_test}",
    "spring.datasource.username=${TEST_DB_USERNAME:postgres}", "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.datasource.hikari.schema=getlancer_test", "spring.flyway.default-schema=getlancer_test",
    "spring.flyway.schemas=getlancer_test", "app.origin=http://localhost:3000",
    "app.secure-cookie=false", "app.storage.access-key=", "app.storage.secret-key="
})
class Round2RuntimeStorageIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired Security security;
  @Autowired PlatformTransactionManager manager;
  UUID owner, admin, client, product, media, hosted, deployment;
  MockHttpServletRequest ownerRequest, adminRequest, clientRequest;
  MediaService images;
  HostingService hosting;
  S3Client storage;
  DemoPublisherClient publisher;
  Runnable storageRead = () -> {};
  Runnable publisherRead = () -> {};
  final byte[] png = new byte[] {1, 2, 3};
  final String archiveHash = "a".repeat(64), manifestHash = "b".repeat(64);
  Instant expiry;

  @BeforeEach void fixtures() {
    db.execute("TRUNCATE users CASCADE");
    owner = UUID.randomUUID(); admin = UUID.randomUUID(); client = UUID.randomUUID();
    ownerRequest = actor(owner, "DEVELOPER", false);
    adminRequest = actor(admin, "ADMIN", true);
    clientRequest = actor(client, "CLIENT", false);
    db.update("INSERT INTO developer_profiles(user_id,slug,approval_status) VALUES(?,?,'APPROVED')", owner, "builder-" + owner);
    product = UUID.randomUUID(); media = UUID.randomUUID(); hosted = UUID.randomUUID(); deployment = UUID.randomUUID();
    db.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,rights_confirmed,approval_status,lifecycle_status)"
        + " VALUES(?,?,?,'Authority fixture','Synthetic proof','Read authority around external I/O','SAAS','Inventory','Java','Built the fixture',true,'APPROVED','ACTIVE')", product, owner, "proof-" + product);
    db.update("INSERT INTO product_media(id,product_id,storage_key,thumbnail_key,content_type,size_bytes,alt_text,verified)"
        + " VALUES(?,?,'proof/full.png','proof/thumb.png','image/png',3,'Synthetic proof',true)", media, product);
    storage = mock(S3Client.class);
    when(storage.getObjectAsBytes(any(GetObjectRequest.class))).thenAnswer(call -> {
      assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "No product lock across storage I/O");
      storageRead.run();
      return ResponseBytes.fromByteArray(GetObjectResponse.builder().contentType("image/png").contentLength(3L).build(), png);
    });
    images = new MediaService(db, security, "http://storage.localhost", "us-east-1", "", "", "private-proof", "http://storage.localhost", false);
    ReflectionTestUtils.setField(images, "s3", storage);
    var configuration = new HostingConfiguration(true, "http://publisher:8090", "p".repeat(32),
        "http://{id}.demo.localhost:8090", "g".repeat(32), 7, 500, "http://localhost:3000");
    publisher = mock(DemoPublisherClient.class);
    when(publisher.get(any(), anyString(), anyString(), any())).thenAnswer(call -> publisherFact("READY"));
    when(publisher.delete(any(), anyString(), anyString(), any())).thenAnswer(call -> publisherFact("DELETED"));
    hosting = new HostingService(new HostingRepository(db, configuration, new ObjectMapper()), security,
        mock(CommerceStorage.class), configuration, publisher, manager, mock(ComponentPreviewService.class));
    expiry = Instant.now().plusSeconds(600).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    seedHosted(hosted, deployment, "1.0.0");
  }

  MockHttpServletRequest actor(UUID id, String role, boolean mfa) {
    db.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,'unused',now())", id, id + "@example.test");
    db.update("INSERT INTO user_roles(user_id,role) VALUES(?,?)", id, role);
    String token = id.toString();
    db.update("INSERT INTO sessions(token_hash,user_id,expires_at,mfa_verified,issued_at) VALUES(?,?,now()+interval '1 hour',?,now())", Support.hash(token), id, mfa);
    var request = new MockHttpServletRequest(); request.setCookies(new Cookie("gl_session", token)); return request;
  }

  void seedHosted(UUID id, UUID fixedDeployment, String version) {
    db.update("INSERT INTO hosted_demos(id,owner_id,product_id,title,version,storage_key,archive_sha256,manifest_sha256,size_bytes,expanded_bytes,file_count,files,status,deployment_state,deployment_id,expires_at)"
        + " VALUES(?,?,?,'Private package',?,'private/archive.zip',?,?,22,1,1,'[]'::jsonb,'APPROVED','READY',?,?)", id, owner, product, version, archiveHash, manifestHash, fixedDeployment, Timestamp.from(expiry));
  }

  DemoPublisherClient.Metadata publisherFact(String state) {
    assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "Publisher I/O releases authority locks");
    publisherRead.run();
    return new DemoPublisherClient.Metadata(deployment, state, archiveHash, manifestHash, expiry,
        state.equals("READY") ? "http://" + deployment + ".demo.localhost:8090" : null);
  }

  void denied(int status, org.junit.jupiter.api.function.Executable action) {
    assertEquals(status, assertThrows(ApiError.class, action).status);
  }
  void privateProduct() { db.update("UPDATE products SET visibility='PRIVATE_CASE_STUDY' WHERE id=?", product); }
  void expired(UUID user) { db.update("UPDATE sessions SET expires_at=clock_timestamp()-interval '1 second' WHERE user_id=?", user); }

  @Test void unchangedPublicImageAndThumbnailRemainReadableWithoutLogin() {
    var anonymous = new MockHttpServletRequest();
    assertArrayEquals(png, images.get(media, "full", anonymous).getBody());
    assertArrayEquals(png, images.get(media, "thumbnail", anonymous).getBody());
    verify(storage).getObjectAsBytes(argThat((GetObjectRequest request) -> request.key().equals("proof/full.png")));
    verify(storage).getObjectAsBytes(argThat((GetObjectRequest request) -> request.key().equals("proof/thumb.png")));
  }

  @Test void newlyPrivateImageDoesNotEscapeAfterStorageReadStartedPublicly() {
    storageRead = this::privateProduct;
    denied(401, () -> images.get(media, "full", new MockHttpServletRequest()));
    verify(storage).getObjectAsBytes(any(GetObjectRequest.class));
  }

  @Test void unchangedPrivateImageRemainsReadableByOwnerAndCurrentlyGrantedVerifiedClient() {
    privateProduct();
    db.update("INSERT INTO product_access_grants(product_id,client_email,expires_at) VALUES(?,?,now()+interval '1 hour')", product, client + "@example.test");
    assertArrayEquals(png, images.get(media, "full", ownerRequest).getBody());
    assertArrayEquals(png, images.get(media, "thumbnail", clientRequest).getBody());
  }

  @Test void privateOwnerSessionExpiryDuringReadRejectsFetchedBytes() {
    privateProduct(); storageRead = () -> expired(owner);
    denied(401, () -> images.get(media, "thumbnail", ownerRequest));
  }

  @Test void privateGrantRevocationDuringReadRejectsFetchedBytes() {
    privateProduct();
    db.update("INSERT INTO product_access_grants(product_id,client_email,expires_at) VALUES(?,?,now()+interval '1 hour')", product, client + "@example.test");
    storageRead = () -> db.update("DELETE FROM product_access_grants WHERE product_id=?", product);
    denied(403, () -> images.get(media, "full", clientRequest));
  }

  @Test void deletedImageCannotBeReturnedFromAnAlreadyRunningStorageRead() {
    storageRead = () -> db.update("DELETE FROM product_media WHERE id=?", media);
    denied(404, () -> images.get(media, "full", new MockHttpServletRequest()));
  }

  @Test void replacedStorageKeyDoesNotAuthorizePreviouslyFetchedBytes() {
    storageRead = () -> db.update("UPDATE product_media SET storage_key='proof/replacement.png' WHERE id=?", media);
    denied(404, () -> images.get(media, "full", new MockHttpServletRequest()));
  }

  @Test void replacedThumbnailKeyDoesNotAuthorizePreviouslyFetchedThumbnail() {
    storageRead = () -> db.update("UPDATE product_media SET thumbnail_key='proof/replacement-thumb.png' WHERE id=?", media);
    denied(404, () -> images.get(media, "thumbnail", new MockHttpServletRequest()));
  }

  @Test void unauthorizedAndInvalidVariantReadsNeverReachStorage() {
    privateProduct(); denied(403, () -> images.get(media, "full", clientRequest));
    denied(400, () -> images.get(media, "unknown", ownerRequest));
    verifyNoInteractions(storage);
  }

  @Test void withdrawnPublisherFactSurvivesMfaRevocationButPrivateProjectionIsDenied() {
    publisherRead = () -> db.update("UPDATE sessions SET mfa_verified=false WHERE user_id=?", admin);
    denied(403, () -> hosting.withdraw(hosted, Map.of("reason", "Operator verified this exact deployment"), adminRequest, true));
    assertEquals("DELETED", db.queryForObject("SELECT deployment_state FROM hosted_demos WHERE id=?", String.class, hosted));
    assertEquals("WITHDRAWN", db.queryForObject("SELECT desired_state FROM hosted_demos WHERE id=?", String.class, hosted));
    assertEquals(1, db.queryForObject("SELECT count(*) FROM hosting_audit WHERE demo_id=? AND action='DELETE_CONFIRMED'", Integer.class, hosted));
    db.update("UPDATE sessions SET mfa_verified=true WHERE user_id=?", admin);
    assertEquals("DELETED", hosting.withdraw(hosted, Map.of("reason", "Recheck confirmed withdrawal without republishing"), adminRequest, true).get("deploymentState"));
    verify(publisher, times(1)).delete(any(), anyString(), anyString(), any());
  }

  @Test void reconciledPublisherFactSurvivesRoleRevocationButPrivateProjectionIsDenied() {
    publisherRead = () -> db.update("DELETE FROM user_roles WHERE user_id=? AND role='ADMIN'", admin);
    denied(403, () -> hosting.reconcile(hosted, Map.of("reason", "Operator verified this exact deployment"), adminRequest, true));
    assertEquals("READY", db.queryForObject("SELECT deployment_state FROM hosted_demos WHERE id=?", String.class, hosted));
    assertEquals(1, db.queryForObject("SELECT count(*) FROM hosting_audit WHERE demo_id=? AND action='RECONCILE_CONFIRMED'", Integer.class, hosted));
  }

  @Test void sessionExpiryAfterPublisherObservationAlsoDeniesAdministratorProjection() {
    publisherRead = () -> expired(admin);
    denied(401, () -> hosting.reconcile(hosted, Map.of("reason", "Operator verified this exact deployment"), adminRequest, true));
    assertEquals(1, db.queryForObject("SELECT count(*) FROM hosting_audit WHERE demo_id=? AND action='RECONCILE_CONFIRMED'", Integer.class, hosted));
  }

  @Test void ownerSessionExpiryDoesNotDiscardConfirmedDeletion() {
    publisherRead = () -> expired(owner);
    denied(401, () -> hosting.withdraw(hosted, Map.of(), ownerRequest, false));
    assertEquals("DELETED", db.queryForObject("SELECT deployment_state FROM hosted_demos WHERE id=?", String.class, hosted));
    assertEquals(1, db.queryForObject("SELECT count(*) FROM hosting_audit WHERE demo_id=? AND action='DELETE_CONFIRMED'", Integer.class, hosted));
  }

  @Test void currentAdministratorStillReceivesBothOperationProjections() {
    assertEquals("READY", hosting.reconcile(hosted, Map.of("reason", "Operator verified this exact deployment"), adminRequest, true).get("deploymentState"));
    assertEquals("DELETED", hosting.withdraw(hosted, Map.of("reason", "Operator verified this exact deployment"), adminRequest, true).get("deploymentState"));
  }
}
