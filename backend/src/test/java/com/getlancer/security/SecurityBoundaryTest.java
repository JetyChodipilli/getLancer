package com.getlancer.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.getlancer.hosting.HostingConfiguration;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class SecurityBoundaryTest {
  @Test void hostedCookiesIgnoreUntrustedLegacyNamesButRejectDuplicateCanonicalNames() {
    var request = new MockHttpServletRequest();
    request.setAttribute(SessionCookies.SECURE_ATTRIBUTE, true);
    request.setCookies(new Cookie("__Host-gl_session", "valid"), new Cookie("gl_session", "attacker"));
    assertEquals("valid", SessionCookies.read(request, "gl_session"));
    request.setCookies(new Cookie("gl_session", "attacker"));
    assertEquals("", SessionCookies.read(request, "gl_session"));
    request.setCookies(new Cookie("__Host-gl_session", "valid"), new Cookie("__Host-gl_session", "valid"));
    assertEquals("", SessionCookies.read(request, "gl_session"));
    request.setAttribute(SessionCookies.SECURE_ATTRIBUTE, false);
    request.setCookies(new Cookie("gl_session", "valid"), new Cookie("gl_session", "attacker"));
    assertEquals("", SessionCookies.read(request, "gl_session"));
    assertEquals("__Host-gl_mfa", SessionCookies.name("gl_mfa", true));
    assertEquals("gl_github_oauth", SessionCookies.name("gl_github_oauth", false));
  }

  @Test void streamedBodyCountsBytesIndependentlyOfLengthAndReadShape() throws Exception {
    for (boolean singleByte : new boolean[] {true, false}) {
      var request = new MockHttpServletRequest("POST", "/api/v1/auth/signup") {
        @Override public long getContentLengthLong() { return -1; }
      };
      request.setContent(new byte[12]);
      var input = new BoundedRequest(request, 10).getInputStream();
      assertThrows(BoundedRequest.TooLarge.class, () -> {
        if (singleByte) while (input.read() >= 0) { /* Count each byte. */ }
        else input.readAllBytes();
      });
    }
    var request = new MockHttpServletRequest(); request.setContent(new byte[10]);
    assertEquals(10, new BoundedRequest(request, 10).getInputStream().readAllBytes().length);
  }

  @Test void routeSpecificityAndDefaultDenyCannotBeBypassedWithAnAnonymousPrincipal() {
    var authorization = new AuthorizationService(mock(HostingConfiguration.class));
    assertEquals(AuthorizationService.Policy.CAPABILITY,
        authorization.route("POST", "/api/v1/inquiries/" + UUID.randomUUID() + "/confirm-hire").policy());
    assertEquals(AuthorizationService.Policy.SIGNED_WEBHOOK,
        authorization.route("POST", "/api/v1/payments/razorpay/webhook").policy());
    assertFalse(authorization.allowed(null, new MockHttpServletRequest("GET", "/api/v1/admin/new-endpoint")));
    assertFalse(authorization.allowed(null, new MockHttpServletRequest("POST", "/api/v1/products")));
    assertTrue(authorization.allowed(null, new MockHttpServletRequest("GET", "/api/v1/products")));
    var user = new GetLancerPrincipal(UUID.randomUUID(), "operator@example.test", Instant.now(), false, Set.of("ADMIN"));
    var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, java.util.List.of());
    assertFalse(authorization.allowed(authentication, new MockHttpServletRequest("GET", "/api/v1/admin/accounts")));
  }

  @Test void oauthRedirectFailureHasBoundedCredentialFreeAuditAndServerGeneratedRequestId() throws Exception {
    var rates = mock(RateLimits.class); when(rates.allow(anyString(), anyInt())).thenReturn(true);
    var security = new Security(mock(JdbcTemplate.class), "https://app.example.test", 30, rates, "", 10, 3, 300, true);
    var audit = mock(SecurityAudit.class);
    var filter = new BrowserSecurityFilter(security, new AuthorizationService(mock(HostingConfiguration.class)), audit);
    var request = new MockHttpServletRequest("GET", "/api/v1/auth/github/callback");
    request.addHeader("X-Request-ID", "attacker-controlled-sensitive-text");
    var response = new MockHttpServletResponse();
    filter.doFilterInternal(request, response, (req, res) -> {
      response.setStatus(303); response.setHeader("Location", "/login?auth_error=expired&provider=github");
    });
    String id = response.getHeader("X-Request-ID"); UUID.fromString(id);
    assertNotEquals("attacker-controlled-sensitive-text", id);
    verify(audit).record(eq(null), eq("LOGIN_FAILURE"), eq("OAUTH_GITHUB"), eq("FAILURE"), eq(id));
  }

  @Test void exactOriginAndNonSimpleHeaderAreBothRequiredAndWebhookExceptionIsMethodSpecific() throws IOException {
    var rates = mock(RateLimits.class);
    var security = new Security(mock(JdbcTemplate.class), "https://app.example.test", 30, rates, "", 10, 3, 300, true);
    var filter = new BrowserSecurityFilter(security, new AuthorizationService(mock(HostingConfiguration.class)), mock(SecurityAudit.class));
    var request = new MockHttpServletRequest("POST", "/api/v1/auth/signup");
    request.addHeader("Origin", "https://app.example.test");
    assertFalse(filter.hasBrowserMutationProtection(request));
    request.addHeader("X-Requested-With", "getlancer"); assertTrue(filter.hasBrowserMutationProtection(request));
    request.removeHeader("Origin"); request.addHeader("Origin", "https://sibling.example.test");
    assertFalse(filter.hasBrowserMutationProtection(request));
    assertFalse(new AuthorizationService(mock(HostingConfiguration.class))
        .signedWebhook(new MockHttpServletRequest("PATCH", "/api/v1/payments/razorpay/webhook")));
  }
}
