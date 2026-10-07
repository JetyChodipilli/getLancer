package com.getlancer.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

public final class SessionCookies {
  public static final String SECURE_ATTRIBUTE = SessionCookies.class.getName() + ".secure";
  private SessionCookies() {}

  public static String name(String logicalName, boolean secure) {
    if (!logicalName.matches("gl_[a-z_]+")) throw new IllegalArgumentException("Invalid cookie name");
    return secure ? "__Host-" + logicalName : logicalName;
  }

  public static String read(HttpServletRequest req, String logicalName) {
    Object configured = req.getAttribute(SECURE_ATTRIBUTE);
    String canonical = name(logicalName, Boolean.TRUE.equals(configured));
    String value = "";
    int found = 0;
    if (req.getCookies() == null) return value;
    for (Cookie cookie : req.getCookies()) {
      if (configured != null && !cookie.getName().equals(canonical)) continue;
      if (!cookie.getName().equals(logicalName)
          && !cookie.getName().equals(name(logicalName, true))) continue;
      if (++found > 1) return "";
      if (configured == null || cookie.getName().equals(canonical)) value = cookie.getValue();
    }
    // Tokens are opaque base64url values. Never let oversized cookie data reach SQL/hash.
    return value != null && value.matches("[A-Za-z0-9_-]{1,128}") ? value : "";
  }
}
