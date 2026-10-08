package com.getlancer.security;

import com.getlancer.hosting.HostingConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.http.server.PathContainer;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/** The explicit route catalogue is shared by filter authorization and controller method security. */
@Service("authorization")
public class AuthorizationService {
  public enum Policy { PUBLIC, SESSION, DEVELOPER, ADMIN_MFA, CAPABILITY, SIGNED_WEBHOOK, SERVICE, HEALTH }
  public record Route(String method, Policy policy, String path, PathPattern pattern) {}
  private final List<Route> routes;
  private final HostingConfiguration hosting;

  public AuthorizationService(HostingConfiguration hosting) {
    this.hosting = hosting;
    try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/api-authorization-policy.txt"))) {
      var parsed = new ArrayList<Route>();
      for (String line : new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().toList()) {
        if (line.isBlank() || line.startsWith("#")) continue;
        String[] parts = line.split(" ", 3);
        parsed.add(new Route(parts[0], Policy.valueOf(parts[1]), parts[2],
            PathPatternParser.defaultInstance.parse(parts[2])));
      }
      parsed.sort((left, right) -> PathPattern.SPECIFICITY_COMPARATOR.compare(left.pattern(), right.pattern()));
      routes = List.copyOf(parsed);
    } catch (IOException exception) { throw new IllegalStateException("Cannot read authorization policy", exception); }
  }

  public List<Route> routes() { return routes; }

  public Route route(String method, String path) {
    String verb = method.equals("HEAD") ? "GET" : method;
    PathContainer parsed = PathContainer.parsePath(path);
    return routes.stream().filter(route -> route.method().equals(verb) && route.pattern().matches(parsed))
        .findFirst().orElse(null);
  }

  public boolean routeAllowed(Authentication authentication) {
    var attributes = RequestContextHolder.getRequestAttributes();
    return attributes instanceof ServletRequestAttributes servlet
        && allowed(authentication, servlet.getRequest());
  }

  public boolean allowed(Authentication authentication, HttpServletRequest request) {
    var route = route(request.getMethod(), request.getRequestURI());
    if (route == null) return false;
    GetLancerPrincipal user = authentication != null && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof GetLancerPrincipal principal ? principal : null;
    return switch (route.policy()) {
      case PUBLIC, CAPABILITY, SIGNED_WEBHOOK -> true; // Domain handlers validate capabilities/signatures.
      case SESSION -> user != null;
      case DEVELOPER -> user != null && user.roles().contains("DEVELOPER");
      case ADMIN_MFA -> user != null && user.roles().contains("ADMIN") && user.mfaVerified()
          && (!recentMfaRequired(route) || user.recentMfa());
      case SERVICE -> hosting.gateway(request.getHeader("X-GetLancer-Demo-Gateway"));
      case HEALTH -> List.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1").contains(request.getRemoteAddr());
    };
  }

  public boolean signedWebhook(HttpServletRequest request) {
    var route = route(request.getMethod(), request.getRequestURI());
    return route != null && route.policy() == Policy.SIGNED_WEBHOOK;
  }

  /** Privileged writes and private package exports require MFA within the past fifteen minutes. */
  public static boolean recentMfaRequired(Route route) {
    return route.policy() == Policy.ADMIN_MFA
        && (!List.of("GET", "HEAD", "OPTIONS").contains(route.method()) || route.path().endsWith("/package")
            || route.path().equals("/api/v1/admin/components/{id}"));
  }
}
