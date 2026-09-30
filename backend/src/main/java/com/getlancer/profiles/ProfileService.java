package com.getlancer.profiles;

import static com.getlancer.shared.Support.*;

import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import com.getlancer.shared.Rules;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
  private final JdbcTemplate db;
  private final Security security;

  public ProfileService(JdbcTemplate db, Security security) {
    this.db = db;
    this.security = security;
  }

  @Transactional
  public Map<String, Object> profile(Map<String, Object> b, HttpServletRequest r) {
    UUID u = security.developer(r, false);
    var previous = db.queryForMap("SELECT * FROM developer_profiles WHERE user_id=? FOR UPDATE", u);
    if (previous.get("approval_status").equals("SUSPENDED"))
      throw new ApiError(403, "FORBIDDEN", "This profile is suspended.");
    String availability = text(b, "availabilityStatus", 1, 30);
    if (!Set.of("AVAILABLE_NOW", "ONE_SLOT_LEFT", "LIMITED", "BOOKED_UNTIL", "NOT_ACCEPTING")
        .contains(availability))
      throw new ApiError(400, "VALIDATION_ERROR", "Choose a valid availability.");
    var details = ProfileDetails.read(b, previous);
    java.sql.Date date = details.bookedUntil(b, availability);
    String github = text(b, "githubUrl", 0, 500), linkedin = text(b, "linkedinUrl", 0, 500);
    Rules.safeUrl(github);
    Rules.safeUrl(linkedin);
    db.update(
        "UPDATE developer_profiles SET"
            + " display_name=?,headline=?,bio=?,technology=?,category=?,availability_status=?,booked_until=?,github_url=?,linkedin_url=?,updated_at=now()"
            + " WHERE user_id=?",
        text(b, "displayName", 2, 100),
        text(b, "headline", 5, 160),
        text(b, "bio", 20, 3000),
        text(b, "technology", 1, 300),
        text(b, "category", 1, 200),
        availability,
        date,
        github,
        linkedin,
        u);
    db.update(
        "UPDATE developer_profiles SET website_url=?,country=?,time_zone=?,languages=? WHERE"
            + " user_id=?",
        details.websiteUrl(),
        details.country(),
        details.timeZone(),
        details.languages(),
        u);
    boolean material =
        details.changed(previous)
            || !text(b, "displayName", 2, 100).equals(previous.get("display_name"))
            || !text(b, "headline", 5, 160).equals(previous.get("headline"))
            || !text(b, "bio", 20, 3000).equals(previous.get("bio"))
            || !text(b, "technology", 1, 300).equals(previous.get("technology"))
            || !text(b, "category", 1, 200).equals(previous.get("category"))
            || !github.equals(Objects.toString(previous.get("github_url"), ""))
            || !linkedin.equals(Objects.toString(previous.get("linkedin_url"), ""));
    if (material && Set.of("APPROVED", "PROFILE_PENDING").contains(previous.get("approval_status")))
      db.update("UPDATE developer_profiles SET approval_status='DRAFT' WHERE user_id=?", u);
    return Map.of("ok", true);
  }

  @Transactional
  public Map<String, Object> submitProfile(HttpServletRequest r) {
    UUID u = security.developer(r, false);
    var p =
        db.queryForMap(
            "SELECT d.*,u.email_verified_at FROM developer_profiles d JOIN users u ON"
                + " u.id=d.user_id WHERE d.user_id=? FOR UPDATE",
            u);
    if (p.get("email_verified_at") == null)
      throw new ApiError(409, "EMAIL_NOT_VERIFIED", "Confirm your email first.");
    if (!Set.of("DRAFT", "CHANGES_REQUESTED").contains(p.get("approval_status")))
      throw new ApiError(409, "INVALID_STATE_TRANSITION", "Profile cannot be submitted now.");
    for (String k : List.of("display_name", "headline", "bio", "technology", "category"))
      if (Objects.toString(p.get(k), "").isBlank())
        throw new ApiError(400, "VALIDATION_ERROR", "Complete your profile first.");
    db.update("UPDATE developer_profiles SET approval_status='PROFILE_PENDING' WHERE user_id=?", u);
    return Map.of("ok", true);
  }
}
