package com.getlancer.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.getlancer.admin.AdminService;
import com.getlancer.analytics.AnalyticsService;
import com.getlancer.config.ProductionConfiguration;
import com.getlancer.inquiries.InquiryService;
import com.getlancer.media.MediaService;
import com.getlancer.moderation.ModerationService;
import com.getlancer.notifications.Mail;
import com.getlancer.shared.ApiError;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

class ReleaseWorkflowTest {
  @Test
  void forgedProxyHeadersDoNotChangeRateIdentity() {
    var security = new Security(mock(JdbcTemplate.class), "https://example.com", 30, mock(RateLimits.class), "a-long-private-proxy-token-value", 10, 3, 300, true);
    var r = new MockHttpServletRequest();
    r.setRemoteAddr("192.0.2.1");
    r.addHeader("X-GetLancer-Client-IP", "203.0.113.5");
    r.addHeader("X-GetLancer-Proxy", "wrong");
    assertEquals("192.0.2.1", security.clientAddress(r));
  }

  @Test
  void trustedProxyIdentityIsUsedAndMalformedInputRejected() {
    var security = new Security(mock(JdbcTemplate.class), "https://example.com", 30, mock(RateLimits.class), "a-long-private-proxy-token-value", 10, 3, 300, true);
    var r = new MockHttpServletRequest();
    r.setRemoteAddr("192.0.2.1");
    r.addHeader("X-GetLancer-Proxy", security.proxySecret);
    r.addHeader("X-GetLancer-Client-IP", "203.0.113.5");
    assertEquals("203.0.113.5", security.clientAddress(r));
    r.removeHeader("X-GetLancer-Client-IP");
    r.addHeader("X-GetLancer-Client-IP", "anything,203.0.113.5");
    assertEquals("192.0.2.1", security.clientAddress(r));
  }

  @Test
  void analyticsDropsConfidentialProperties() {
    var cleaned =
        AnalyticsService.cleanContext(
            Map.of(
                "query",
                "client@example.com",
                "token",
                "secret",
                "description",
                "private inquiry",
                "sourcePage",
                "product",
                "acquisitionSource",
                "social",
                "resultCount",
                12,
                "arbitrary",
                true));
    assertEquals(
        Map.of("sourcePage", "product", "acquisitionSource", "social", "resultCount", 12), cleaned);
  }

  @Test
  void analyticsRejectsUnboundedOrUnknownContext() {
    assertTrue(
        AnalyticsService.cleanContext(
                Map.of(
                    "rankPosition",
                    -1,
                    "sourcePage",
                    "https://private.example/token",
                    "acquisitionSource",
                    "secret",
                    "resultCount",
                    0.5))
            .isEmpty());
  }

  @Test
  void invalidProposalValuesRejected() {
    for (String value : List.of("-1", "NaN", "1.001", "1000000000000", ""))
      assertThrows(
          ApiError.class, () -> InquiryService.proposalValue(Map.of("reportedValue", value)));
    assertEquals(
        "1999.99", InquiryService.proposalValue(Map.of("reportedValue", "1999.99")).toString());
  }

  @Test
  void invalidImageAndOversizeImageRejected() {
    assertThrows(
        ApiError.class, () -> MediaService.sanitize("<svg onload='alert(1)'/>".getBytes()));
    assertThrows(ApiError.class, () -> MediaService.sanitize(new byte[5242881]));
    assertThrows(
        ApiError.class,
        () -> MediaService.sanitize(new byte[] {(byte) 137, 80, 78, 71, 0, 0, 0, 0, 0}));
  }

  @Test
  void proofImageReencodedAndThumbnailPreservesAspectRatio() throws Exception {
    var source = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
    var out = new ByteArrayOutputStream();
    ImageIO.write(source, "png", out);
    var clean = ImageIO.read(new ByteArrayInputStream(MediaService.sanitize(out.toByteArray())));
    assertEquals(1280, clean.getWidth());
    var thumb = ImageIO.read(new ByteArrayInputStream(MediaService.thumbnail(clean)));
    assertEquals(640, thumb.getWidth());
    assertEquals(360, thumb.getHeight());
  }

  @Test
  void schemaNamesAreValidated() {
    assertThrows(
        IllegalStateException.class,
        () ->
            new ProductionConfiguration(
                    new MockEnvironment()
                        .withProperty("spring.flyway.default-schema", "bad;schema"))
                .run(null));
  }

  @Test
  void disabledAnalyticsDoesNotWrite() {
    var db = mock(JdbcTemplate.class);
    assertEquals(false, new AnalyticsService(db).record(Map.of()).get("recorded"));
    verifyNoInteractions(db);
  }

  @Test
  void cannotAppealAnotherUsersDecision() {
    var db = mock(JdbcTemplate.class);
    var security = mock(Security.class);
    var r = new MockHttpServletRequest();
    UUID user = UUID.randomUUID(), decision = UUID.randomUUID();
    when(security.user(r)).thenReturn(user);
    when(db.queryForList("SELECT id,admin_id,target_type,target_id,action,reason,created_at FROM moderation_actions WHERE id=?", decision))
        .thenReturn(List.of(Map.of("target_id", UUID.randomUUID(), "target_type", "ACCOUNT")));
    var workflow = new ModerationService(db, security, mock(AdminService.class), mock(Mail.class));
    assertEquals(
        "NOT_FOUND",
        assertThrows(
                ApiError.class,
                () ->
                    workflow.appeal(
                        Map.of("decisionId", decision, "statement", "Please review this decision"),
                        r))
            .code);
    verify(db).queryForList("SELECT id,admin_id,target_type,target_id,action,reason,created_at FROM moderation_actions WHERE id=?", decision);
    verify(db, never()).update(anyString(), any(Object[].class));
  }
}
