package com.getlancer.inquiries;

import static com.getlancer.shared.Support.id;
import static com.getlancer.shared.Support.text;

import com.getlancer.notifications.Mail;
import com.getlancer.shared.ApiError;
import com.getlancer.shared.Rules;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class InquiryOutcomeService {
  private final JdbcTemplate db;
  private final Mail mail;

  public InquiryOutcomeService(JdbcTemplate db, Mail mail) {
    this.db = db;
    this.mail = mail;
  }

  public String applyDecision(
      Map<String, Object> inquiry, String kind, String decision, String actor, UUID actorId) {
    InquiryPolicy.requireClear(inquiry);
    InquiryPolicy.requireSender(db, (String) inquiry.get("client_email"));
    if (!Set.of("", "ACCEPT", "REJECT").contains(decision))
      throw new ApiError(400, "VALIDATION_ERROR", "Choose confirm or not yet.");
    String next =
        Rules.clientDecision(
            kind, (String) inquiry.get("current_status"), !decision.equals("REJECT"));
    UUID inquiryId = (UUID) inquiry.get("id"), builder = (UUID) inquiry.get("developer_user_id");
    db.update("UPDATE inquiries SET current_status=?,updated_at=now() WHERE id=?", next, inquiryId);
    db.update(
        "INSERT INTO inquiry_events(id,inquiry_id,event_type,actor_type,actor_id)"
            + " VALUES(?,?,?,?,?)",
        id(),
        inquiryId,
        next,
        actor,
        actorId);
    db.update(
        "UPDATE account_tokens SET used_at=now() WHERE inquiry_id=? AND kind=? AND used_at IS NULL",
        inquiryId,
        kind);
    mail.notify(
        builder,
        decision.equals("REJECT")
            ? "Your client requested further discussion."
            : "Your client confirmed an engagement update.");
    if (next.equals("HIRED")) event("hire_confirmed", inquiryId);
    if (next.equals("COMPLETED")) {
      event("project_completed", inquiryId);
      mail.token("REVIEW", null, inquiryId, (String) inquiry.get("client_email"));
    }
    return next;
  }

  public void submitReview(Map<String, Object> inquiry, Map<String, Object> body) {
    InquiryPolicy.requireClear(inquiry);
    InquiryPolicy.requireSender(db, (String) inquiry.get("client_email"));
    UUID inquiryId = (UUID) inquiry.get("id"), builder = (UUID) inquiry.get("developer_user_id");
    if (!Rules.reviewEligible(
        (String) inquiry.get("current_status"),
        db.queryForObject(
                "SELECT count(*) FROM inquiry_events WHERE inquiry_id=? AND event_type='COMPLETED'",
                Integer.class,
                inquiryId)
            > 0,
        false))
      throw new ApiError(
          409, "REVIEW_NOT_ELIGIBLE", "Completion must be confirmed before reviewing.");
    int rating;
    try {
      rating = Integer.parseInt(Objects.toString(body.get("rating"), ""));
    } catch (NumberFormatException e) {
      throw new ApiError(400, "VALIDATION_ERROR", "Choose a rating from 1 to 5.");
    }
    if (rating < 1 || rating > 5)
      throw new ApiError(400, "VALIDATION_ERROR", "Choose a rating from 1 to 5.");
    String review = text(body, "reviewText", 10, 2000);
    String visibility = Objects.toString(body.getOrDefault("visibility", "ANONYMOUS"), "");
    if (!Set.of("ANONYMOUS", "NAMED").contains(visibility))
      throw new ApiError(
          400,
          "VALIDATION_ERROR",
          "Choose whether to display your name.",
          Map.of("visibility", "Choose anonymous or named."));
    var prior =
        db.queryForList(
            "SELECT rating,review_text,visibility FROM reviews WHERE inquiry_id=?", inquiryId);
    if (!prior.isEmpty()) {
      if (((Number) prior.get(0).get("rating")).intValue() == rating
          && prior.get(0).get("review_text").equals(review)
          && prior.get(0).get("visibility").equals(visibility)) return;
      throw new ApiError(
          409, "REVIEW_EXISTS", "A review has already been submitted for this engagement.");
    }
    db.update(
        "INSERT INTO reviews(id,inquiry_id,developer_user_id,rating,review_text,visibility)"
            + " VALUES(?,?,?,?,?,?)",
        id(),
        inquiryId,
        builder,
        rating,
        review,
        visibility);
    db.update(
        "UPDATE account_tokens SET used_at=now() WHERE inquiry_id=? AND kind='REVIEW'", inquiryId);
    event("review_submitted", inquiryId);
  }

  void event(String name, UUID entity) {
    db.update(
        "INSERT INTO analytics_events(id,event_name,entity_id) VALUES(?,?,?)", id(), name, entity);
  }
}
