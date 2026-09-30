package com.getlancer.business;

import static com.getlancer.shared.Support.*;

import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TalentListService {
  final BusinessRepository repo;
  final MatchingService matching;

  public TalentListService(BusinessRepository repo, MatchingService matching) {
    this.repo = repo;
    this.matching = matching;
  }

  private UUID access(UUID b, HttpServletRequest r) {
    UUID u = repo.verified(r);
    repo.access(b, u, false);
    return u;
  }

  private void list(UUID business, UUID list) {
    repo.one("SELECT id FROM talent_lists WHERE business_id=? AND id=?", business, list);
  }

  @Transactional
  public Map<String, Object> lists(UUID business, HttpServletRequest r) {
    access(business, r);
    return Map.of(
        "items",
        repo.db.queryForList(
            "SELECT id,name FROM talent_lists WHERE business_id=? ORDER BY created_at DESC,id LIMIT"
                + " 100",
            business));
  }

  @Transactional
  public Map<String, Object> create(UUID business, Map<String, Object> b, HttpServletRequest r) {
    UUID u = access(business, r), list = id();
    repo.db.update(
        "INSERT INTO talent_lists(id,business_id,name,created_by) VALUES(?,?,?,?)",
        list,
        business,
        text(b, "name", 2, 120),
        u);
    repo.audit(business, u, "TALENT_LIST_CREATED", "Shared talent list created");
    return Map.of("id", list, "name", text(b, "name", 2, 120));
  }

  @Transactional
  public Map<String, Object> entries(UUID business, UUID list, HttpServletRequest r) {
    access(business, r);
    list(business, list);
    return Map.of("items", matching.entries("talent_entries", "list_id", list));
  }

  @Transactional
  public Map<String, Object> save(
      UUID business, UUID list, Map<String, Object> b, HttpServletRequest r) {
    UUID u = access(business, r);
    list(business, list);
    matching.insert("talent_entries", "list_id", list, b, u, "");
    repo.audit(business, u, "TALENT_SAVED", "Eligible candidate saved to a talent list");
    return Map.of("ok", true);
  }

  @Transactional
  public Map<String, Object> remove(UUID business, UUID list, UUID entry, HttpServletRequest r) {
    UUID u = access(business, r);
    list(business, list);
    if (repo.db.update("DELETE FROM talent_entries WHERE list_id=? AND id=?", list, entry) == 0)
      throw new ApiError(404, "NOT_FOUND", "Talent entry not found.");
    repo.audit(business, u, "TALENT_REMOVED", "Talent entry removed");
    return Map.of("ok", true);
  }
}
