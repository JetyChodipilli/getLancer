package com.getlancer.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** External versioned AES-256 keyring. No plaintext read or fallback is supported. */
@Component
public final class MfaSecrets {
  private final String activeKeyId;
  private final Map<String, SecretKeySpec> keys;
  private final SecureRandom random = new SecureRandom();

  public MfaSecrets(
      @Value("${app.mfa.active-key-id:}") String activeKeyId,
      @Value("${app.mfa.keyring:}") String keyring,
      @Value("${app.environment:local}") String environment) {
    this.activeKeyId = activeKeyId.trim();
    var parsed = new LinkedHashMap<String, SecretKeySpec>();
    try {
      if (!keyring.isBlank()) {
        String[] entries = keyring.split(",", -1);
        if (entries.length > 16) throw new IllegalArgumentException();
        for (String entry : entries) {
          String[] parts = entry.trim().split(":", -1);
          if (parts.length != 2 || !parts[0].matches("[A-Za-z0-9_-]{1,32}"))
            throw new IllegalArgumentException();
          byte[] decoded = Base64.getDecoder().decode(parts[1]);
          try {
            if (decoded.length != 32 || parsed.containsKey(parts[0]))
              throw new IllegalArgumentException();
            var key = new SecretKeySpec(decoded, "AES");
            if (parsed.containsValue(key)) throw new IllegalArgumentException();
            parsed.put(parts[0], key);
          } finally {
            Arrays.fill(decoded, (byte) 0);
          }
        }
      }
      if (!parsed.isEmpty() && !parsed.containsKey(this.activeKeyId))
        throw new IllegalArgumentException();
      if (parsed.isEmpty() && (!this.activeKeyId.isBlank() || !"local".equals(environment)))
        throw new IllegalArgumentException();
    } catch (IllegalArgumentException failure) {
      // Never include supplied key material, or a decoding exception, in diagnostics.
      throw new IllegalStateException("MFA encryption keyring is missing or invalid.");
    }
    this.keys = Map.copyOf(parsed);
  }

  public boolean configured() {
    return !keys.isEmpty();
  }

  public String activeKeyId() {
    requireConfigured();
    return activeKeyId;
  }

  public void requireConfigured() {
    if (!configured()) throw new IllegalStateException("MFA encryption keyring is required.");
  }

  public Encrypted encrypt(UUID user, String secret) {
    requireConfigured();
    if (user == null || !validSecret(secret)) throw new SecretUnavailable();
    byte[] nonce = new byte[12];
    random.nextBytes(nonce);
    byte[] plaintext = secret.getBytes(StandardCharsets.US_ASCII);
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, keys.get(activeKeyId), new GCMParameterSpec(128, nonce));
      cipher.updateAAD(aad(user, activeKeyId));
      return new Encrypted(activeKeyId, nonce, cipher.doFinal(plaintext));
    } catch (GeneralSecurityException failure) {
      throw new SecretUnavailable();
    } finally {
      Arrays.fill(plaintext, (byte) 0);
    }
  }

  public String decrypt(UUID user, String keyId, byte[] nonce, byte[] ciphertext) {
    if (user == null || keyId == null || !keys.containsKey(keyId)
        || nonce == null || nonce.length != 12 || ciphertext == null
        || ciphertext.length < 32 || ciphertext.length > 144) throw new SecretUnavailable();
    byte[] plaintext = null;
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, keys.get(keyId), new GCMParameterSpec(128, nonce));
      cipher.updateAAD(aad(user, keyId));
      plaintext = cipher.doFinal(ciphertext);
      String secret = new String(plaintext, StandardCharsets.US_ASCII);
      if (!validSecret(secret)) throw new SecretUnavailable();
      return secret;
    } catch (GeneralSecurityException failure) {
      throw new SecretUnavailable();
    } finally {
      if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
    }
  }

  private static byte[] aad(UUID user, String keyId) {
    return ("getlancer:admin-totp:v1:" + user + ":" + keyId).getBytes(StandardCharsets.UTF_8);
  }

  private static boolean validSecret(String secret) {
    return secret != null && secret.matches("[A-Z2-7]{16,128}");
  }

  public record Encrypted(String keyId, byte[] nonce, byte[] ciphertext) {
    public Encrypted {
      nonce = nonce.clone();
      ciphertext = ciphertext.clone();
    }
    @Override public byte[] nonce() { return nonce.clone(); }
    @Override public byte[] ciphertext() { return ciphertext.clone(); }
    @Override public String toString() { return "Encrypted[keyId=" + keyId + "]"; }
  }

  public static final class SecretUnavailable extends IllegalStateException {
    public SecretUnavailable() { super("MFA credential is unavailable; operator recovery is required."); }
  }
}
