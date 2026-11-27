package com.getlancer.admin;

import com.getlancer.products.ProductRepository;
import com.getlancer.security.Security;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class QueueSummaryService {
  private final JdbcTemplate db;
  private final Security security;

  public QueueSummaryService(JdbcTemplate db, Security security) {
    this.db = db;
    this.security = security;
  }

  public record Totals(long projects, long profiles, long reports, long reviews) {}

  public Totals totals(HttpServletRequest request) {
    security.admin(request);
    return db.queryForObject("""
        SELECT (SELECT count(*) FROM products p WHERE %s) AS projects,
          (SELECT count(*) FROM developer_profiles WHERE approval_status IN ('PROFILE_PENDING', 'SUSPENDED')) AS profiles,
          (SELECT count(*) FROM reports WHERE status <> 'RESOLVED') AS reports,
          (SELECT count(*) FROM reviews WHERE moderation_status = 'HELD_FOR_REVIEW') AS reviews
        """.formatted(ProductRepository.MODERATION_QUEUE), (row, index) -> new Totals(row.getLong("projects"), row.getLong("profiles"),
        row.getLong("reports"), row.getLong("reviews")));
  }
}
