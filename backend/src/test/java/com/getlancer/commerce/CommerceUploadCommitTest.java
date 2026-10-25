package com.getlancer.commerce;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.getlancer.publishing.PublishingCapacity;
import com.getlancer.security.Security;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class CommerceUploadCommitTest {
  enum Outcome { COMMITTED, UNKNOWN, ROLLED_BACK, BEGIN_FAILURE }

  /** Exercises Spring's real completion callbacks, including STATUS_UNKNOWN after a failed commit. */
  static final class Manager extends AbstractPlatformTransactionManager {
    final Outcome outcome;
    final List<Map<String, Object>> committed = new ArrayList<>(), pending = new ArrayList<>();
    int begun;
    Manager(Outcome outcome) { this.outcome = outcome; }
    @Override protected Object doGetTransaction() { return new Object(); }
    @Override protected void doBegin(Object transaction, TransactionDefinition definition) {
      begun++;
      if (begun == 2 && outcome == Outcome.BEGIN_FAILURE)
        throw new CannotCreateTransactionException("Synthetic connection failure before upload transaction");
      pending.clear();
    }
    @Override protected void doCommit(DefaultTransactionStatus status) {
      committed.addAll(pending); pending.clear();
      if (begun == 2 && outcome == Outcome.UNKNOWN)
        throw new TransactionSystemException("Synthetic durable COMMIT with lost acknowledgement");
    }
    @Override protected void doRollback(DefaultTransactionStatus status) { pending.clear(); }
  }

  static final class Fixture {
    final UUID template = UUID.randomUUID(), seller = UUID.randomUUID(), product = UUID.randomUUID(), paid = UUID.randomUUID();
    final String paidKey = "commerce/" + paid + "/" + UUID.randomUUID() + ".zip";
    final byte[] paidBytes = "Existing purchase remains pinned to these exact bytes".getBytes(StandardCharsets.UTF_8);
    final Map<String, byte[]> objects = new HashMap<>();
    final CommerceRepository repo = mock(CommerceRepository.class);
    final CommerceStorage storage = mock(CommerceStorage.class);
    final Manager manager;
    final CommerceService service;
    final MockHttpServletRequest request = new MockHttpServletRequest();
    String uploadedKey;
    Fixture(Outcome outcome) {
      manager = new Manager(outcome);
      manager.committed.add(Map.of("id", paid, "version", "1.0.0", "storage_key", paidKey));
      objects.put(paidKey, paidBytes.clone());
      var security = mock(Security.class);
      when(security.user(request)).thenReturn(seller);
      when(security.developer(request, true)).thenReturn(seller);
      when(repo.template(template, true)).thenReturn(Map.of("seller_id", seller, "product_id", product, "status", "ACTIVE", "license_terms", "Existing immutable license snapshot"));
      when(repo.versions(template, true)).thenAnswer(call -> {
        var rows = new ArrayList<>(manager.committed); rows.addAll(manager.pending); return rows;
      });
      when(storage.put(any(UUID.class), any(byte[].class), anyString())).thenAnswer(call -> {
        uploadedKey = "commerce/" + call.getArgument(0) + "/" + UUID.randomUUID() + ".zip";
        objects.put(uploadedKey, ((byte[]) call.getArgument(1)).clone()); return uploadedKey;
      });
      doAnswer(call -> { objects.remove(call.getArgument(0)); return null; }).when(storage).discardUnlinked(anyString());
      doAnswer(call -> {
        manager.pending.add(Map.of("id", call.getArgument(0), "version", call.getArgument(2), "storage_key", call.getArgument(4)));
        if (outcome == Outcome.ROLLED_BACK) throw new IllegalStateException("Synthetic metadata failure after insert");
        return null;
      }).when(repo).addVersion(any(), eq(template), anyString(), anyString(), anyString(), any(), anyString());
      service = new CommerceService(repo, security, storage, manager, mock(PublishingCapacity.class));
    }
    Map<String, Object> upload() throws Exception {
      return service.upload(template, new MockMultipartFile("file", "source.zip", "application/zip", archive()), "2.0.0", "Complete immutable release notes.", true, request);
    }
    void existingPurchaseUnchanged() {
      assertArrayEquals(paidBytes, objects.get(paidKey));
      assertEquals(paidKey, manager.committed.get(0).get("storage_key"));
      verify(storage, never()).discardUnlinked(paidKey);
    }
  }

  @Test void unknownCompletionRetainsTheDurablyCommittedReleaseAndExistingPaidBytes() throws Exception {
    var f = new Fixture(Outcome.UNKNOWN);
    assertThrows(TransactionSystemException.class, f::upload);
    assertEquals(2, f.manager.committed.size());
    assertEquals(f.uploadedKey, f.manager.committed.get(1).get("storage_key"));
    assertArrayEquals(archive(), f.objects.get(f.uploadedKey));
    verify(f.storage, never()).discardUnlinked(anyString());
    f.existingPurchaseUnchanged();
  }

  @Test void confirmedRollbackRemovesOnlyTheNewUnlinkedObject() throws Exception {
    var f = new Fixture(Outcome.ROLLED_BACK);
    assertThrows(IllegalStateException.class, f::upload);
    assertEquals(1, f.manager.committed.size()); assertTrue(f.manager.pending.isEmpty());
    assertFalse(f.objects.containsKey(f.uploadedKey)); verify(f.storage).discardUnlinked(f.uploadedKey);
    f.existingPurchaseUnchanged();
  }

  @Test void failureBeforeTransactionEntryRemovesOnlyTheNewUnlinkedObject() throws Exception {
    var f = new Fixture(Outcome.BEGIN_FAILURE);
    assertThrows(CannotCreateTransactionException.class, f::upload);
    assertEquals(1, f.manager.committed.size()); assertFalse(f.objects.containsKey(f.uploadedKey));
    verify(f.storage).discardUnlinked(f.uploadedKey); f.existingPurchaseUnchanged();
  }

  @Test void confirmedCommitKeepsBothImmutableReleases() throws Exception {
    var f = new Fixture(Outcome.COMMITTED); var result = f.upload();
    assertEquals("2.0.0", result.get("version")); assertEquals(2, f.manager.committed.size());
    assertNotNull(f.objects.get(f.uploadedKey)); verify(f.storage, never()).discardUnlinked(anyString());
    f.existingPurchaseUnchanged();
  }

  private static byte[] archive() throws Exception {
    var bytes = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(bytes)) {
      for (var entry : Map.of("README.md", "Build the project using npm install and npm run build.", "LICENSE", "One commercial product; preserve all third-party license notices.", "package.json", "{\"name\":\"source-fixture\"}").entrySet()) {
        var file = new ZipEntry(entry.getKey()); file.setTime(0); zip.putNextEntry(file);
        zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
      }
    }
    return bytes.toByteArray();
  }
}
