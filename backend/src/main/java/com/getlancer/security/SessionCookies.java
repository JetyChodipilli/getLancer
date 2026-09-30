package com.getlancer.security;

import jakarta.servlet.http.*;

public final class SessionCookies {
  private SessionCookies() {}

  public static String read(HttpServletRequest req, String name) {
    if (req.getCookies() != null)
      for (Cookie c : req.getCookies()) if (c.getName().equals(name)) return c.getValue();
    return "";
  }
}
