package com.getlancer.inquiries;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.getlancer.notifications.Mail;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import com.getlancer.shared.Support;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

class PublicReportTest {
  final JdbcTemplate db = mock(JdbcTemplate.class);
  final Security security = new Security(db, "http://localhost:3000", 60);
  final InquiryService controller = new InquiryService(db, security, mock(Mail.class));
  final Map<String, Object> body =
      Map.of(
          "targetType",
          "PRODUCT",
          "targetId",
          UUID.randomUUID().toString(),
          "reason",
          "SPAM",
          "detail",
          "This showcase contains repeated spam.");
  final String insert =
      "INSERT INTO reports(id,target_type,target_id,reason,detail,reporter_id,severity)"
          + " VALUES(?,?,?,?,?,?,?)";

  @Test
  void anonymousReportDoesNotRequireSession() {
    assertNotNull(controller.report(body, new MockHttpServletRequest()).get("reference"));
    verify(db)
        .update(
            eq(insert),
            any(UUID.class),
            eq("PRODUCT"),
            any(UUID.class),
            eq("SPAM"),
            anyString(),
            isNull(),
            eq("MEDIUM"));
  }

  @Test
  void staleSessionCanReportButCannotAccessProtectedRoutes() {
    var request = new MockHttpServletRequest();
    request.setCookies(new Cookie("gl_session", "expired-or-revoked"));
    when(db.queryForList(anyString(), eq(Support.hash("expired-or-revoked"))))
        .thenReturn(List.of());
    assertNotNull(controller.report(body, request).get("reference"));
    verify(db)
        .update(
            eq(insert),
            any(UUID.class),
            eq("PRODUCT"),
            any(UUID.class),
            eq("SPAM"),
            anyString(),
            isNull(),
            eq("MEDIUM"));
    assertEquals(401, assertThrows(ApiError.class, () -> security.user(request)).status);
  }

  @Test
  void validSessionAssociatesVerifiedReporter() {
    var request = new MockHttpServletRequest();
    request.setCookies(new Cookie("gl_session", "active-session"));
    UUID user = UUID.randomUUID();
    when(db.queryForList(anyString(), eq(Support.hash("active-session"))))
        .thenReturn(List.of(Map.of("id", user)));
    controller.report(body, request);
    verify(db)
        .update(
            eq(insert),
            any(UUID.class),
            eq("PRODUCT"),
            any(UUID.class),
            eq("SPAM"),
            anyString(),
            eq(user),
            eq("MEDIUM"));
  }

  @Test
  void databaseFailureIsNotTreatedAsAnonymousAccess() {
    var request = new MockHttpServletRequest();
    request.setCookies(new Cookie("gl_session", "active-session"));
    when(db.queryForList(anyString(), eq(Support.hash("active-session"))))
        .thenThrow(new DataAccessResourceFailureException("Unavailable"));
    assertThrows(DataAccessResourceFailureException.class, () -> controller.report(body, request));
    verify(db, never()).update(eq(insert), any(Object[].class));
  }
}
