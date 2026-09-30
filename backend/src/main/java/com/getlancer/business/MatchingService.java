package com.getlancer.business;

import static com.getlancer.shared.Support.*;

import com.getlancer.products.ProductRepository;
import com.getlancer.shared.ApiError;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class MatchingService {
  final BusinessRepository repo;

  public MatchingService(BusinessRepository repo) {
    this.repo = repo;
  }

  private static final String ELIGIBLE =
      ProductRepository.PUBLIC
          + " AND u.email_verified_at IS NOT NULL AND p.available_for_similar_work=true ";

  private String sql(boolean teams) {
    String projection =
        teams
            ? "t.id AS \"targetId\",t.name,t.summary,'/teams/'||t.id AS url,t.availability"
            : "u.id AS \"targetId\",d.display_name AS name,d.headline AS"
                + " summary,'/builders/'||d.slug AS url,d.availability_status AS availability";
    String join =
        teams
            ? " JOIN team_projects tp ON tp.product_id=p.id AND tp.consented_by=p.owner_user_id"
                + " JOIN teams t ON t.id=tp.team_id JOIN team_members m ON m.team_id=t.id AND"
                + " m.user_id=p.owner_user_id AND (m.expires_at IS NULL OR m.expires_at>now()) "
            : " ";
    return "SELECT "
        + projection
        + ",count(DISTINCT p.id) AS \"evidenceCount\",bool_or(lower(p.category)=lower(?)) AS"
        + " \"categoryMatch\",bool_or(lower(?)=ANY(regexp_split_to_array(lower(p.technology),'\\s*,\\s*')))"
        + " AS \"technologyMatch\",bool_or(v.status='VERIFIED' AND"
        + " v.repository_url=p.repository_url) AS \"repositoryVerified\" FROM products p JOIN users"
        + " u ON u.id=p.owner_user_id JOIN developer_profiles d ON d.user_id=u.id LEFT JOIN"
        + " repository_verifications v ON v.product_id=p.id"
        + join
        + " WHERE "
        + ELIGIBLE
        + (teams ? " AND t.status='ACTIVE' " : "");
  }

  private List<Map<String, Object>> candidates(
      boolean teams, Map<String, Object> request, UUID target) {
    String category = Objects.toString(request.get("category"), ""),
        technology = Objects.toString(request.get("technology"), "");
    var args = new ArrayList<Object>(List.of(category, technology));
    String query = sql(teams);
    if (target != null) {
      query += " AND " + (teams ? "t.id" : "u.id") + "=? ";
      args.add(target);
    }
    query += " GROUP BY " + (teams ? "t.id" : "u.id,d.user_id");
    if (!request.isEmpty()) {
      query +=
          " HAVING (bool_or(lower(p.category)=lower(?)) OR"
              + " bool_or(lower(?)=ANY(regexp_split_to_array(lower(p.technology),'\\s*,\\s*'))))";
      args.add(category);
      args.add(technology);
      if (Boolean.TRUE.equals(request.get("availableOnly")))
        query +=
            " AND " + (teams ? "t.availability" : "d.availability_status") + "='AVAILABLE_NOW'";
      if (Boolean.TRUE.equals(request.get("repositoryVerifiedOnly")))
        query += " AND bool_or(v.status='VERIFIED' AND v.repository_url=p.repository_url)";
    }
    query +=
        " ORDER BY (CASE WHEN bool_or(lower(p.category)=lower(?)) THEN 4 ELSE 0 END + CASE WHEN"
            + " bool_or(lower(?)=ANY(regexp_split_to_array(lower(p.technology),'\\s*,\\s*'))) THEN"
            + " 3 ELSE 0 END) DESC,"
            + (teams ? "t.id" : "u.id")
            + " LIMIT 30";
    args.add(category);
    args.add(technology);
    var result = repo.jdbc().queryForList(query, args.toArray());
    for (var c : result) {
      var reasons = new ArrayList<String>();
      if (Boolean.TRUE.equals(c.remove("categoryMatch")))
        reasons.add("Category evidence: " + category);
      if (Boolean.TRUE.equals(c.remove("technologyMatch")))
        reasons.add("Technology evidence: " + technology);
      if ("AVAILABLE_NOW".equals(c.get("availability")))
        reasons.add("Available now (self-reported)");
      if (Boolean.TRUE.equals(c.remove("repositoryVerified")))
        reasons.add("Manually reviewed repository evidence");
      c.put("reasons", reasons);
      c.put("kind", teams ? "TEAM" : "BUILDER");
    }
    return result;
  }

  public Map<String, Object> matches(Map<String, Object> request) {
    var result = new ArrayList<Map<String, Object>>();
    result.addAll(candidates(false, request, null));
    result.addAll(candidates(true, request, null));
    result.sort(
        Comparator.<Map<String, Object>>comparingInt(c -> ((List<?>) c.get("reasons")).size())
            .reversed()
            .thenComparing(c -> c.get("kind") + ":" + c.get("targetId")));
    return Map.of("items", result.stream().limit(30).toList());
  }

  public Map<String, Object> candidate(String kind, UUID target) {
    if (!Set.of("BUILDER", "TEAM").contains(kind))
      throw new ApiError(400, "VALIDATION_ERROR", "Choose a builder or team.");
    var rows = candidates(kind.equals("TEAM"), Map.of(), target);
    if (rows.isEmpty())
      throw new ApiError(404, "NOT_FOUND", "This candidate no longer has eligible public proof.");
    return rows.get(0);
  }

  List<Map<String, Object>> entries(String table, String foreignKey, UUID id) {
    var rows =
        repo.jdbc()
            .queryForList(
                "SELECT id,kind,coalesce(builder_id,team_id) AS \"targetId\""
                    + (table.equals("request_shortlist") ? ",reason,source" : "")
                    + " FROM "
                    + table
                    + " WHERE "
                    + foreignKey
                    + "=? ORDER BY created_at DESC,id LIMIT 100",
                id);
    var result = new ArrayList<Map<String, Object>>();
    for (var entry : rows) {
      try {
        var c = candidate((String) entry.get("kind"), (UUID) entry.get("targetId"));
        entry.put("candidate", c);
        result.add(entry);
      } catch (ApiError e) {
        if (e.status != 404) throw e;
        entry.put("unavailable", true);
        result.add(entry);
      }
    }
    return result;
  }

  void insert(
      String table,
      String foreignKey,
      UUID parent,
      Map<String, Object> b,
      UUID actor,
      String source) {
    String kind = BusinessRepository.choice(b, "kind", Set.of("BUILDER", "TEAM"));
    UUID target = uuid(b.get("targetId"));
    candidate(kind, target);
    String reason = table.equals("request_shortlist") ? text(b, "reason", 3, 2000) : "";
    String extra = table.equals("request_shortlist") ? ",reason,source" : "";
    String placeholders = table.equals("request_shortlist") ? ",?,?" : "";
    var args =
        new ArrayList<Object>(
            Arrays.asList(
                id(),
                parent,
                kind,
                kind.equals("BUILDER") ? target : null,
                kind.equals("TEAM") ? target : null,
                actor));
    if (table.equals("request_shortlist")) {
      args.add(reason);
      args.add(source);
    }
    repo.jdbc()
        .update(
            "INSERT INTO "
                + table
                + "(id,"
                + foreignKey
                + ",kind,builder_id,team_id,created_by"
                + extra
                + ") VALUES(?,?,?,?,?,?"
                + placeholders
                + ") ON CONFLICT"
                + (source.equals("CONCIERGE")
                    ? "(request_id,"
                        + (kind.equals("BUILDER") ? "builder_id" : "team_id")
                        + ") DO UPDATE SET"
                        + " reason=excluded.reason,source=excluded.source,created_by=excluded.created_by"
                    : " DO NOTHING"),
            args.toArray());
  }
}
