package com.getlancer.business;

import static com.getlancer.business.BusinessRepository.*;
import static com.getlancer.shared.Support.*;

import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectRequestService {
  final BusinessRepository repo;
  final MatchingService matching;

  public ProjectRequestService(BusinessRepository repo, MatchingService matching) {
    this.repo = repo;
    this.matching = matching;
  }

  private UUID access(UUID b, HttpServletRequest r) {
    UUID u = repo.verified(r);
    repo.access(b, u, false);
    return u;
  }

  @Transactional
  public Map<String, Object> list(UUID b, HttpServletRequest r) {
    access(b, r);
    return Map.of(
        "items",
        repo.jdbc()
            .queryForList(
                REQUEST + "WHERE r.business_id=? ORDER BY r.created_at DESC,r.id LIMIT 100", b));
  }

  private Object[] fields(Map<String, Object> b) {
    String category = text(b, "category", 0, 100), technology = text(b, "technology", 0, 300);
    if (category.isEmpty() && technology.isEmpty())
      throw new ApiError(400, "VALIDATION_ERROR", "Choose a category or technology for matching.");
    for (String field : List.of("availableOnly", "repositoryVerifiedOnly"))
      if (b.containsKey(field) && !(b.get(field) instanceof Boolean))
        throw new ApiError(400, "VALIDATION_ERROR", "Use a boolean for " + field + ".");
    return new Object[] {
      text(b, "title", 3, 120),
      text(b, "description", 20, 5000),
      category,
      technology,
      text(b, "budget", 1, 200),
      text(b, "timeline", 1, 200),
      Boolean.TRUE.equals(b.get("availableOnly")),
      Boolean.TRUE.equals(b.get("repositoryVerifiedOnly")),
      choice(b, "status", Set.of("DRAFT", "OPEN", "CLOSED"))
    };
  }

  @Transactional
  public Map<String, Object> create(UUID business, Map<String, Object> b, HttpServletRequest r) {
    UUID u = access(business, r), request = id();
    var fields = fields(b);
    if (fields[8].equals("CLOSED"))
      throw new ApiError(400, "VALIDATION_ERROR", "Create a draft or open request.");
    var args = new ArrayList<Object>(List.of(request, business, u));
    args.addAll(Arrays.asList(fields));
    repo.jdbc()
        .update(
            "INSERT INTO"
                + " business_requests(id,business_id,created_by,title,description,category,technology,budget,timeline,available_only,repository_verified_only,status)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
            args.toArray());
    repo.audit(business, u, "REQUEST_CREATED", "Private project request created");
    return repo.request(business, request);
  }

  @Transactional
  public Map<String, Object> edit(
      UUID business, UUID request, Map<String, Object> b, HttpServletRequest r) {
    UUID u = access(business, r);
    var previous = repo.request(business, request);
    if (previous.get("status").equals("CLOSED"))
      throw new ApiError(409, "INVALID_STATE_TRANSITION", "Closed requests are read-only.");
    var fields = fields(b);
    if (previous.get("status").equals("OPEN") && fields[8].equals("DRAFT"))
      throw new ApiError(
          409, "INVALID_STATE_TRANSITION", "An open request cannot return to draft.");
    var args = new ArrayList<Object>(Arrays.asList(fields));
    args.add(request);
    args.add(business);
    repo.jdbc()
        .update(
            "UPDATE business_requests SET"
                + " title=?,description=?,category=?,technology=?,budget=?,timeline=?,available_only=?,repository_verified_only=?,status=?,updated_at=now()"
                + " WHERE id=? AND business_id=?",
            args.toArray());
    if (fields[8].equals("CLOSED"))
      repo.jdbc()
          .update(
              "UPDATE concierge_requests SET status='CANCELLED',updated_at=now() WHERE request_id=?"
                  + " AND status IN ('REQUESTED','IN_PROGRESS')",
              request);
    repo.audit(business, u, "REQUEST_UPDATED", "Private request updated to " + fields[8]);
    return repo.request(business, request);
  }

  @Transactional
  public Map<String, Object> matches(UUID business, UUID request, HttpServletRequest r) {
    access(business, r);
    var brief = repo.request(business, request);
    repo.open(brief);
    return matching.matches(brief);
  }

  @Transactional
  public Map<String, Object> shortlist(UUID business, UUID request, HttpServletRequest r) {
    access(business, r);
    repo.request(business, request);
    return Map.of("items", matching.entries("request_shortlist", "request_id", request));
  }

  @Transactional
  public Map<String, Object> save(
      UUID business, UUID request, Map<String, Object> b, HttpServletRequest r) {
    UUID u = access(business, r);
    repo.open(repo.request(business, request));
    matching.insert("request_shortlist", "request_id", request, b, u, "BUSINESS");
    repo.audit(business, u, "CANDIDATE_SHORTLISTED", "Candidate added to the request shortlist");
    return Map.of("ok", true);
  }

  @Transactional
  public Map<String, Object> remove(UUID business, UUID request, UUID entry, HttpServletRequest r) {
    UUID u = access(business, r);
    repo.open(repo.request(business, request));
    if (repo.jdbc()
            .update("DELETE FROM request_shortlist WHERE id=? AND request_id=?", entry, request)
        == 0) throw new ApiError(404, "NOT_FOUND", "Shortlist entry not found.");
    repo.audit(business, u, "SHORTLIST_REMOVED", "Shortlist entry removed");
    return Map.of("ok", true);
  }

  @Transactional
  public Map<String, Object> concierge(UUID business, UUID request, HttpServletRequest r) {
    UUID u = access(business, r);
    repo.open(repo.request(business, request));
    repo.jdbc()
        .update(
            "INSERT INTO concierge_requests(id,request_id,requested_by) VALUES(?,?,?) ON"
                + " CONFLICT(request_id) DO NOTHING",
            id(),
            request,
            u);
    repo.audit(
        business,
        u,
        "CONCIERGE_REQUESTED",
        "Brief shared with the platform administrator for manual sourcing");
    return Map.of("ok", true);
  }
}
