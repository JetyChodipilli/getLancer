package com.getlancer.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class RequestIds {
  public static final String ATTRIBUTE = RequestIds.class.getName();
  private RequestIds() {}

  public static void initialize(HttpServletRequest request, Security security) {
    String supplied = request.getHeader("X-GetLancer-Request-ID");
    if (security.trustedProxy(request) && supplied != null
        && supplied.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
      request.setAttribute(ATTRIBUTE, supplied);
    else get(request);
  }

  public static String get(HttpServletRequest request) {
    Object existing = request.getAttribute(ATTRIBUTE);
    if (existing instanceof String id) return id;
    // The ID is server-generated. Untrusted request headers are never logged or echoed as IDs.
    String id = UUID.randomUUID().toString();
    request.setAttribute(ATTRIBUTE, id);
    return id;
  }

  public static String current() {
    var attributes = RequestContextHolder.getRequestAttributes();
    return attributes instanceof ServletRequestAttributes servlet
        ? get(servlet.getRequest()) : UUID.randomUUID().toString();
  }
}
