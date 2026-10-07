package com.getlancer.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;



public final class Support {
  public static UUID id() {
    return UUID.randomUUID();
  }

  public static String text(Map<String, Object> b, String k, int min, int max) {
    String v = Objects.toString(b.get(k), "").trim();
    if (v.length() < min || v.length() > max)
      throw new ApiError(
          400,
          "VALIDATION_ERROR",
          "Check the " + k + " field.",
          Map.of(k, "Use between " + min + " and " + max + " characters."));
    return v;
  }

  public static String email(Map<String, Object> b, String k) {
    String e = text(b, k, 3, 254).toLowerCase(Locale.ROOT);
    if (!e.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
      throw new ApiError(400, "VALIDATION_ERROR", "Enter a valid email address.");
    return e;
  }

  public static UUID uuid(Object v) {
    try {
      return UUID.fromString(v.toString());
    } catch (Exception e) {
      throw new ApiError(400, "VALIDATION_ERROR", "Invalid identifier.");
    }
  }

  public static String hash(String s) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static String randomToken() {
    byte[] b = new byte[32];
    new SecureRandom().nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  public static boolean totp(String secret, String code) {
    if (secret == null || code == null || !code.matches("[0-9]{6}")) return false;
    try {
      String a = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
      int bits = 0, value = 0;
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      for (char c : secret.toUpperCase().replace("=", "").toCharArray()) {
        int x = a.indexOf(c);
        if (x < 0) return false;
        value = (value << 5) | x;
        bits += 5;
        if (bits >= 8) {
          out.write((value >> (bits - 8)) & 255);
          bits -= 8;
        }
      }
      long now = Instant.now().getEpochSecond() / 30;
      for (long n = now - 1; n <= now + 1; n++) {
        byte[] counter = java.nio.ByteBuffer.allocate(8).putLong(n).array();
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(out.toByteArray(), "HmacSHA1"));
        byte[] h = mac.doFinal(counter);
        int p = h[h.length - 1] & 15;
        int number =
            ((h[p] & 127) << 24)
                | ((h[p + 1] & 255) << 16)
                | ((h[p + 2] & 255) << 8)
                | (h[p + 3] & 255);
        String expected = String.format("%06d", number % 1000000);
        if (MessageDigest.isEqual(
            code.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8)))
          return true;
      }
      return false;
    } catch (Exception e) {
      return false;
    }
  }
}
