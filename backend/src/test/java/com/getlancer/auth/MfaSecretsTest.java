package com.getlancer.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.getlancer.config.MfaStorageUpgrade;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class MfaSecretsTest {
  static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
  static String key(int value) {
    byte[] bytes = new byte[32];
    Arrays.fill(bytes, (byte) value);
    return Base64.getEncoder().encodeToString(bytes);
  }
  static MfaSecrets keys() { return new MfaSecrets("v1", "v1:" + key(1), "local"); }

  @Test
  void aes256AuthenticatedEnvelopeRoundTripsWithFresh96BitNonces() {
    UUID user = UUID.randomUUID();
    var secrets = keys();
    var nonces = new HashSet<String>();
    for (int i = 0; i < 64; i++) {
      var encrypted = secrets.encrypt(user, SECRET);
      assertEquals("v1", encrypted.keyId());
      assertEquals(12, encrypted.nonce().length);
      assertEquals(SECRET.length() + 16, encrypted.ciphertext().length);
      assertEquals(SECRET, secrets.decrypt(user, encrypted.keyId(), encrypted.nonce(), encrypted.ciphertext()));
      assertTrue(nonces.add(Base64.getEncoder().encodeToString(encrypted.nonce())));
      assertFalse(new String(encrypted.ciphertext(), StandardCharsets.US_ASCII).contains(SECRET));
      assertFalse(encrypted.toString().contains(SECRET));
    }
  }

  @Test
  void ciphertextNonceUserAndVersionTamperingCannotAuthenticate() {
    UUID user = UUID.randomUUID();
    var secrets = new MfaSecrets("v1", "v1:" + key(1) + ",v2:" + key(2), "local");
    var encrypted = secrets.encrypt(user, SECRET);
    byte[] changed = encrypted.ciphertext();
    changed[0] ^= 1;
    assertThrows(MfaSecrets.SecretUnavailable.class,
        () -> secrets.decrypt(user, "v1", encrypted.nonce(), changed));
    byte[] nonce = encrypted.nonce();
    nonce[0] ^= 1;
    assertThrows(MfaSecrets.SecretUnavailable.class,
        () -> secrets.decrypt(user, "v1", nonce, encrypted.ciphertext()));
    assertThrows(MfaSecrets.SecretUnavailable.class,
        () -> secrets.decrypt(UUID.randomUUID(), "v1", encrypted.nonce(), encrypted.ciphertext()));
    assertThrows(MfaSecrets.SecretUnavailable.class,
        () -> secrets.decrypt(user, "v2", encrypted.nonce(), encrypted.ciphertext()));
  }

  @Test
  void wrongOrMissingHistoricalKeyAndPlaintextNeverFallBack() {
    UUID user = UUID.randomUUID();
    var encrypted = keys().encrypt(user, SECRET);
    var wrong = new MfaSecrets("v1", "v1:" + key(9), "local");
    assertThrows(MfaSecrets.SecretUnavailable.class,
        () -> wrong.decrypt(user, "v1", encrypted.nonce(), encrypted.ciphertext()));
    assertThrows(MfaSecrets.SecretUnavailable.class,
        () -> wrong.decrypt(user, "missing", encrypted.nonce(), encrypted.ciphertext()));
    assertThrows(MfaSecrets.SecretUnavailable.class,
        () -> keys().decrypt(user, "v1", encrypted.nonce(), SECRET.getBytes(StandardCharsets.US_ASCII)));
    assertThrows(MfaSecrets.SecretUnavailable.class,
        () -> keys().decrypt(user, null, null, null));
  }

  @Test
  void rotationReadsRetainedKeyAndWritesOnlyNewActiveVersion() {
    UUID user = UUID.randomUUID();
    var old = keys().encrypt(user, SECRET);
    var rotated = new MfaSecrets("v2", "v1:" + key(1) + ",v2:" + key(2), "production");
    var replacement = rotated.encrypt(user,
        rotated.decrypt(user, old.keyId(), old.nonce(), old.ciphertext()));
    assertEquals("v2", replacement.keyId());
    var retired = new MfaSecrets("v2", "v2:" + key(2), "production");
    assertEquals(SECRET, retired.decrypt(user, "v2", replacement.nonce(), replacement.ciphertext()));
    assertThrows(MfaSecrets.SecretUnavailable.class,
        () -> retired.decrypt(user, "v1", old.nonce(), old.ciphertext()));
  }

  @Test
  void invalidKeyringsFailWithoutEchoingSecretsAndLocalHasNoEncryptionFallback() {
    for (String invalid : List.of("broken", "v1:!!!", "v1:" + key(1) + ",v1:" + key(2),
        "v1:" + key(1) + ",v2:" + key(1), "v1:" + Base64.getEncoder().encodeToString(new byte[16]))) {
      var failure = assertThrows(IllegalStateException.class,
          () -> new MfaSecrets("v1", invalid, "production"));
      assertFalse(failure.getMessage().contains(invalid));
      assertEquals(null, failure.getCause());
    }
    assertThrows(IllegalStateException.class, () -> new MfaSecrets("", "", "production"));
    assertThrows(IllegalStateException.class, () -> new MfaSecrets("", "", "staging"));
    assertThrows(IllegalStateException.class, () -> new MfaSecrets("missing", "v1:" + key(1), "local"));
    var local = new MfaSecrets("", "", "local");
    assertFalse(local.configured());
    assertThrows(IllegalStateException.class, () -> local.encrypt(UUID.randomUUID(), SECRET));
  }

  @Test
  void envelopeArraysCannotBeMutatedThroughAccessors() {
    UUID user = UUID.randomUUID();
    var encrypted = keys().encrypt(user, SECRET);
    byte[] original = encrypted.ciphertext();
    encrypted.ciphertext()[0] ^= 1;
    encrypted.nonce()[0] ^= 1;
    assertArrayEquals(original, encrypted.ciphertext());
    assertEquals(SECRET, keys().decrypt(user, "v1", encrypted.nonce(), encrypted.ciphertext()));
  }

  @Test
  void boundedLegacyUpgradeEncryptsAndNullsPlaintextAndRevokesCredentials() {
    var db = mock(JdbcTemplate.class);
    UUID user = UUID.randomUUID();
    when(db.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
    when(db.queryForList(anyString(), eq(MfaStorageUpgrade.MAX_CREDENTIALS + 1)))
        .thenReturn(List.of(Map.of("id", user, "admin_totp", SECRET)));
    new MfaStorageUpgrade(db, keys()).run(null);
    var version = ArgumentCaptor.forClass(String.class);
    var nonce = ArgumentCaptor.forClass(byte[].class);
    var ciphertext = ArgumentCaptor.forClass(byte[].class);
    verify(db).update(eq("UPDATE users SET admin_totp=NULL,admin_totp_key_version=?,admin_totp_nonce=?,admin_totp_ciphertext=? WHERE id=?"),
        version.capture(), nonce.capture(), ciphertext.capture(), eq(user));
    assertEquals(SECRET, keys().decrypt(user, version.getValue(), nonce.getValue(), ciphertext.getValue()));
    verify(db).update("DELETE FROM sessions WHERE user_id=?", user);
    verify(db).update("DELETE FROM login_challenges WHERE user_id=?", user);
  }

  @Test
  void upgradeBoundAndMissingKeyAbortBeforeAnyWrite() {
    var db = mock(JdbcTemplate.class);
    when(db.queryForObject(anyString(), eq(Integer.class))).thenReturn(MfaStorageUpgrade.MAX_CREDENTIALS + 1);
    assertThrows(IllegalStateException.class, () -> new MfaStorageUpgrade(db, keys()).run(null));
    verify(db, never()).queryForList(anyString(), any(Object[].class));
    verify(db, never()).update(anyString(), any(Object[].class));
    when(db.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
    assertThrows(IllegalStateException.class,
        () -> new MfaStorageUpgrade(db, new MfaSecrets("", "", "local")).run(null));
    verify(db, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void upgradeAuthenticatesUnchangedCiphertextAndRotatesOldVersion() {
    var db = mock(JdbcTemplate.class);
    UUID user = UUID.randomUUID();
    var old = keys().encrypt(user, SECRET);
    when(db.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
    when(db.queryForList(anyString(), eq(MfaStorageUpgrade.MAX_CREDENTIALS + 1)))
        .thenReturn(List.of(Map.of("id", user, "admin_totp_key_version", "v1",
            "admin_totp_nonce", old.nonce(), "admin_totp_ciphertext", old.ciphertext())));
    new MfaStorageUpgrade(db, keys()).run(null);
    verify(db, never()).update(anyString(), any(Object[].class));
    new MfaStorageUpgrade(db,
        new MfaSecrets("v2", "v1:" + key(1) + ",v2:" + key(2), "local")).run(null);
    verify(db).update(eq("UPDATE users SET admin_totp=NULL,admin_totp_key_version=?,admin_totp_nonce=?,admin_totp_ciphertext=? WHERE id=?"),
        eq("v2"), any(byte[].class), any(byte[].class), eq(user));
  }
}
