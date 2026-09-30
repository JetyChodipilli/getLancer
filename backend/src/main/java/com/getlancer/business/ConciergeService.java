package com.getlancer.business;

import static com.getlancer.shared.Support.*;

import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConciergeService {
  final BusinessRepository repo;
  final MatchingService matching;

  public ConciergeService(BusinessRepository repo, MatchingService matching) {
    this.repo = repo;
    this.matching = matching;
  }

  private Map<String, Object> record(UUID id) {
    var initial =
        repo.one(
            "SELECT r.business_id,c.request_id FROM concierge_requests c JOIN business_requests r"
                + " ON r.id=c.request_id WHERE c.id=?",
            id);
    repo.one("SELECT id FROM businesses WHERE id=? FOR UPDATE", initial.get("business_id"));
    return repo.one(
        "SELECT c.id,c.request_id AS \"requestId\",r.business_id AS \"businessId\",c.status FROM"
            + " concierge_requests c JOIN business_requests r ON r.id=c.request_id WHERE c.id=? FOR"
            + " UPDATE OF c",
        id);
  }

  private Map<String, Object> brief(Map<String, Object> c) {
    var r = repo.request((UUID) c.get("businessId"), (UUID) c.get("requestId"));
    repo.open(r);
    return r;
  }

  @Transactional
  public Map<String, Object> queue(HttpServletRequest r) {
    repo.security.admin(r);
    return Map.of(
        "items",
        repo.db.queryForList(
            "SELECT c.id,c.status,r.id AS \"requestId\",r.business_id AS \"businessId\",b.name AS"
                + " \"businessName\",r.title,r.description,r.category,r.technology,r.budget,r.timeline,c.created_at"
                + " AS \"createdAt\" FROM concierge_requests c JOIN business_requests r ON"
                + " r.id=c.request_id JOIN businesses b ON b.id=r.business_id WHERE r.status='OPEN'"
                + " AND c.status IN ('REQUESTED','IN_PROGRESS','FULFILLED') ORDER BY"
                + " c.created_at,c.id LIMIT 100"));
  }

  @Transactional
  public Map<String, Object> matches(UUID id, HttpServletRequest r) {
    repo.security.admin(r);
    return matching.matches(brief(record(id)));
  }

  @Transactional
  public Map<String, Object> status(UUID id, Map<String, Object> b, HttpServletRequest r) {
    UUID u = repo.security.admin(r);
    var c = record(id);
    brief(c);
    String next = BusinessRepository.choice(b, "status", Set.of("IN_PROGRESS", "FULFILLED"));
    boolean valid =
        c.get("status").equals("REQUESTED") && next.equals("IN_PROGRESS")
            || c.get("status").equals("IN_PROGRESS") && next.equals("FULFILLED");
    if (!valid)
      throw new ApiError(409, "INVALID_STATE_TRANSITION", "Invalid concierge transition.");
    if (next.equals("FULFILLED")
        && matching.entries("request_shortlist", "request_id", (UUID) c.get("requestId")).stream()
            .noneMatch(e -> e.get("source").equals("CONCIERGE")))
      throw new ApiError(409, "EMPTY_SHORTLIST", "Add an eligible concierge recommendation first.");
    repo.db.update("UPDATE concierge_requests SET status=?,updated_at=now() WHERE id=?", next, id);
    repo.audit(
        (UUID) c.get("businessId"),
        u,
        "CONCIERGE_" + next,
        "Manual sourcing " + next.toLowerCase().replace('_', ' '));
    return Map.of("ok", true);
  }

  @Transactional
  public Map<String, Object> save(UUID id, Map<String, Object> b, HttpServletRequest r) {
    UUID u = repo.security.admin(r);
    var c = record(id);
    brief(c);
    if (!c.get("status").equals("IN_PROGRESS"))
      throw new ApiError(
          409, "INVALID_STATE_TRANSITION", "Start sourcing before recommending candidates.");
    matching.insert(
        "request_shortlist", "request_id", (UUID) c.get("requestId"), b, u, "CONCIERGE");
    repo.audit(
        (UUID) c.get("businessId"),
        u,
        "CONCIERGE_RECOMMENDATION",
        "Manual recommendation added with a client-visible reason");
    return Map.of("ok", true);
  }
}
