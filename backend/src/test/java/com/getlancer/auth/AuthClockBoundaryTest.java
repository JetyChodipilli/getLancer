package com.getlancer.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.getlancer.inquiries.InquiryOutcomeService;
import com.getlancer.notifications.Mail;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import com.getlancer.shared.Support;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AuthClockBoundaryTest {
  final JdbcTemplate db = mock(JdbcTemplate.class);
  final Mail mail = mock(Mail.class);
  final AuthService auth = new AuthService(db, mock(Security.class), mail, false, "",
      new InquiryOutcomeService(db, mail));

  Map<String, Object> token(String expiry) {
    var row = new LinkedHashMap<String, Object>();
    row.put("expires_at", Timestamp.from(Instant.parse(expiry))); row.put("used_at", null);
    when(db.queryForList(anyString(), eq(Support.hash("synthetic-capability")))).thenReturn(List.of(row));
    return row;
  }

  @Test void databaseExpiredTokenCannotBeRevivedByAnEarlierJvmClock() {
    token("2098-01-01T00:00:00Z");
    when(db.queryForObject("SELECT clock_timestamp()", Timestamp.class))
        .thenReturn(Timestamp.from(Instant.parse("2099-01-01T00:00:00Z")));
    assertEquals("TOKEN_EXPIRED", assertThrows(ApiError.class,
        () -> auth.confirmationToken("synthetic-capability", false)).code);
  }

  @Test void liveDatabaseTokenIsNotRejectedByAnAheadJvmClock() {
    var row = token("1991-01-01T00:00:00Z");
    when(db.queryForObject("SELECT clock_timestamp()", Timestamp.class))
        .thenReturn(Timestamp.from(Instant.parse("1990-01-01T00:00:00Z")));
    assertSame(row, auth.confirmationToken("synthetic-capability", false));
  }

  @Test void expiryIsReobservedAfterCredentialLockAndExactDeadlineIsExpired() {
    token("2098-01-01T00:00:00Z");
    when(db.queryForObject("SELECT clock_timestamp()", Timestamp.class))
        .thenReturn(Timestamp.from(Instant.parse("2097-01-01T00:00:00Z")),
            Timestamp.from(Instant.parse("2098-01-01T00:00:00Z")));
    auth.confirmationToken("synthetic-capability", false);
    assertEquals("TOKEN_EXPIRED", assertThrows(ApiError.class,
        () -> auth.confirmationToken("synthetic-capability", true)).code);
  }

  @Test void consumedMutationRemainsIdempotentEvenAfterExpiry() {
    var row = token("1991-01-01T00:00:00Z"); row.put("used_at", Timestamp.from(Instant.EPOCH));
    assertSame(row, auth.confirmationToken("synthetic-capability", true));
  }
}
