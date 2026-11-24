package com.getlancer.admin;

import com.getlancer.payments.PaymentRepository;
import com.getlancer.responses.AdminDashboardResponse;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminDashboard {
  private final JdbcTemplate db;
  private final Security security;
  private final MarketplaceMetrics marketplace;
  private final OperationsMetrics operations;
  private final PaymentRepository paymentRepository;

  public AdminDashboard(JdbcTemplate db, Security security, MarketplaceMetrics marketplace,
      OperationsMetrics operations, PaymentRepository paymentRepository) {
    this.db = db;
    this.security = security;
    this.marketplace = marketplace;
    this.operations = operations;
    this.paymentRepository = paymentRepository;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public AdminDashboardResponse snapshot(String period, HttpServletRequest request) {
    var actor = security.admin(request);
    int weeks = switch (period) {
      case "4" -> 4;
      case "12" -> 12;
      default -> throw new ApiError(400, "VALIDATION_ERROR", "Select 4 or 12 weeks.");
    };
    var principal = security.loadPrincipal(request);
    if (principal == null || !actor.equals(principal.userId())) {
      throw new ApiError(401, "UNAUTHENTICATED", "Please log in to continue.");
    }

    Instant asOf = db.queryForObject("SELECT transaction_timestamp()", Timestamp.class).toInstant();
    LocalDate monday = asOf.atOffset(ZoneOffset.UTC).toLocalDate().with(DayOfWeek.MONDAY);
    LocalDate first = monday.minusWeeks(weeks - 1L);
    Instant from = first.atStartOfDay().toInstant(ZoneOffset.UTC);
    Timestamp start = Timestamp.from(from), end = Timestamp.from(asOf);
    Map<String, Object> m = marketplace.outcomes(start, end);
    var outcomes = new AdminDashboardResponse.Outcomes(number(m, "qualifiedInquiries"), number(m, "confirmedHires"),
        number(m, "completedHires"), number(m, "respondedEligibleInquiries"), number(m, "responseEligibleInquiries"),
        (BigDecimal) m.get("responseRatePercent"), decimal(m, "medianResponseHours"),
        (BigDecimal) m.get("inquiryToHirePercent"), (BigDecimal) m.get("hireToCompletionPercent"));

    var trend = db.query("""
        WITH counts AS (
          SELECT date_trunc('week', email_confirmed_at AT TIME ZONE 'UTC')::date AS week, count(*) AS qualified
          FROM inquiries WHERE email_confirmed_at BETWEEN ? AND ? AND moderation_status = 'CLEAR'
          GROUP BY 1
        )
        SELECT week_start::date AS week, coalesce(counts.qualified, 0) AS qualified
        FROM generate_series(?::timestamp, ?::timestamp, interval '1 week') week_start
        LEFT JOIN counts ON counts.week = week_start::date
        ORDER BY week_start
        """, (rs, row) -> new AdminDashboardResponse.Week(rs.getObject("week", LocalDate.class), rs.getLong("qualified")),
        start, end, first, monday);

    var queues = db.query("""
        SELECT 'projects' AS key, count(*) AS count, min(updated_at) AS oldest, NULL AS severity
        FROM products WHERE approval_status = 'PENDING_REVIEW'
        UNION ALL
        SELECT 'reports', count(*), min(created_at),
          CASE WHEN bool_or(severity = 'CRITICAL') THEN 'CRITICAL' WHEN bool_or(severity = 'HIGH') THEN 'HIGH' END
        FROM reports WHERE status <> 'RESOLVED'
        UNION ALL
        SELECT 'disputes', count(*), min(created_at), NULL FROM delivery_disputes WHERE status = 'OPEN'
        UNION ALL
        SELECT 'email', count(*), min(created_at), NULL FROM email_outbox WHERE sent_at IS NULL AND attempts >= 8
        """, (rs, row) -> new AdminDashboardResponse.Queue(rs.getString("key"), rs.getLong("count"),
        instant(rs, "oldest"), rs.getString("severity")));
    var payment = paymentRepository.attentionSummary();
    queues.add(new AdminDashboardResponse.Queue("payments", payment.count(), payment.oldest(), null));

    var catalog = db.queryForObject("""
        SELECT count(*) AS active, count(*) FILTER(WHERE p.demo_health = 'UNREACHABLE'
          AND p.demo_checked_url = p.live_url AND p.demo_checked_at <= transaction_timestamp()
          AND p.demo_checked_at > transaction_timestamp() - interval '24 hours') AS unreachable
        FROM products p JOIN developer_profiles d ON d.user_id = p.owner_user_id
        JOIN users u ON u.id = p.owner_user_id
        WHERE p.approval_status = 'APPROVED' AND p.lifecycle_status = 'ACTIVE' AND p.visibility = 'PUBLIC'
          AND d.approval_status = 'APPROVED' AND u.account_status = 'ACTIVE' AND u.email_verified_at IS NOT NULL
        """, (rs, row) -> new AdminDashboardResponse.Catalog(rs.getLong("active"), rs.getLong("unreachable")));

    var commerce = db.query("""
        WITH entries AS (
          SELECT 'Milestone payments' AS product, p.mode, l.currency, l.kind, l.amount_minor
          FROM payment_ledger l JOIN payment_attempts p ON p.id = l.attempt_id WHERE l.created_at BETWEEN ? AND ?
          UNION ALL
          SELECT 'Source purchases', p.mode, l.currency, l.kind, l.amount_minor
          FROM commerce_ledger l JOIN template_purchases p ON p.id = l.purchase_id WHERE l.created_at BETWEEN ? AND ?
        )
        SELECT product, mode, currency,
          coalesce(sum(amount_minor) FILTER (WHERE kind = 'CAPTURE'), 0)::text AS captured,
          coalesce(sum(amount_minor) FILTER (WHERE kind = 'REFUND'), 0)::text AS refunded
        FROM entries GROUP BY product, mode, currency ORDER BY product, mode, currency
        """, (rs, row) -> new AdminDashboardResponse.Commerce(rs.getString("product"), rs.getString("mode"),
        rs.getString("currency"), rs.getString("captured"), rs.getString("refunded")), start, end, start, end);

    AdminDashboardResponse.Readiness readiness = null;
    if (principal.recentMfa()) {
      var current = operations.readiness();
      readiness = new AdminDashboardResponse.Readiness(current.runtime().enabled(),
          current.commerce().enabled() && "live".equalsIgnoreCase(current.commerce().mode()),
          current.productionCertified());
    }
    return new AdminDashboardResponse(new AdminDashboardResponse.Window(weeks, from, asOf),
        outcomes, trend, List.copyOf(queues), catalog, commerce, readiness);
  }

  private static long number(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).longValue();
  }

  private static BigDecimal decimal(Map<String, Object> row, String key) {
    return row.get(key) instanceof Number value ? new BigDecimal(value.toString()) : null;
  }

  private static Instant instant(ResultSet row, String name) throws SQLException {
    Timestamp timestamp = row.getTimestamp(name);
    return timestamp == null ? null : timestamp.toInstant();
  }
}
