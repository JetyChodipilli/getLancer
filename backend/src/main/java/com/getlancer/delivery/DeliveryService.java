package com.getlancer.delivery;

import static com.getlancer.shared.Support.hash;
import static com.getlancer.shared.Support.id;
import static com.getlancer.shared.Support.text;
import static com.getlancer.shared.Support.uuid;

import com.getlancer.business.MatchingService;
import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryService {
  private final DeliveryRepository repo;
  private final MatchingService matching;
  private static final long MAX_AMOUNT = 1_000_000_000L;

  public DeliveryService(DeliveryRepository repo, MatchingService matching) {
    this.repo = repo;
    this.matching = matching;
  }

  public Map<String, Object> mine(HttpServletRequest request) {
    return Map.of("items", repo.list(repo.verified(request)));
  }

  @Transactional
  public Map<String, Object> create(Map<String, Object> body, HttpServletRequest request) {
    UUID actor = repo.verified(request);
    boolean inquirySource = body.get("inquiryId") != null, businessSource = body.get("businessRequestId") != null;
    if (inquirySource == businessSource)
      throw new ApiError(400, "VALIDATION_ERROR", "Choose exactly one inquiry or business request.");
    UUID inquiry = null, brief = null, buyer = null, business = null, builder = null, team = null;
    String title;
    if (inquirySource) {
      if (body.get("candidateKind") != null || body.get("candidateId") != null)
        throw new ApiError(400, "VALIDATION_ERROR", "An inquiry already identifies its builder.");
      inquiry = uuid(body.get("inquiryId"));
      var source = repo.inquiry(inquiry);
      buyer = (UUID) source.get("buyerUserId");
      builder = (UUID) source.get("builderUserId");
      if (!actor.equals(buyer) && !repo.sellerForCandidate("BUILDER", builder, actor))
        throw new ApiError(404, "NOT_FOUND", "Inquiry not found.");
      matching.candidate("BUILDER", builder);
      if (buyer.equals(builder)) throw new ApiError(409, "CONFLICT", "Both agreement parties must be different.");
      title = (String) source.get("title");
    } else {
      brief = uuid(body.get("businessRequestId"));
      String kind = text(body, "candidateKind", 1, 10);
      if (!Set.of("BUILDER", "TEAM").contains(kind))
        throw new ApiError(400, "VALIDATION_ERROR", "Choose a builder or team.");
      UUID candidate = uuid(body.get("candidateId"));
      var source = repo.businessSource(brief);
      business = (UUID) source.get("businessId");
      boolean isBuyer = repo.buyerForBusiness(business, actor);
      boolean isSeller = repo.sellerForCandidate(kind, candidate, actor);
      if (!isBuyer && !(isSeller && repo.shortlisted(brief, kind, candidate)))
        throw new ApiError(404, "NOT_FOUND", "Business request not found.");
      if (isBuyer && isSeller)
        throw new ApiError(409, "CONFLICT", "The same account cannot represent both agreement parties.");
      requireState(source, "OPEN");
      @SuppressWarnings("unchecked")
      var candidates = (List<Map<String, Object>>) matching.matches(source).get("items");
      if (candidates.stream().noneMatch(c -> kind.equals(c.get("kind")) && candidate.equals(c.get("targetId"))))
        throw new ApiError(404, "NOT_FOUND", "The selected candidate no longer matches the request's eligible proof.");
      if (kind.equals("BUILDER")) builder = candidate; else team = candidate;
      title = (String) source.get("title");
    }
    UUID existing = repo.existing(inquiry, brief, builder, team);
    if (existing != null) return Map.of("id", existing);
    UUID engagement = id();
    repo.create(engagement, inquiry, brief, buyer, business, builder, team, title, actor);
    repo.event(engagement, actor, "ENGAGEMENT_CREATED", "Qualified source linked to its agreement parties");
    return Map.of("id", engagement);
  }

  @Transactional
  public Map<String, Object> detail(UUID engagement, HttpServletRequest request) {
    return repo.detail(repo.lockEngagement(engagement, request, "ANY"));
  }

  @Transactional
  public Map<String, Object> draft(UUID engagement, Map<String, Object> body, HttpServletRequest request) {
    var e = repo.lockEngagement(engagement, request, "SELLER");
    requireState(e, "OPEN");
    String latest = repo.latestProposalStatus(engagement);
    if ("SENT".equals(latest) || "ACCEPTED".equals(latest))
      throw new ApiError(409, "INVALID_STATE_TRANSITION", "Withdraw the sent proposal before creating a revision.");
    String scope = text(body, "scope", 20, 10000), terms = text(body, "terms", 10, 5000);
    if (body.containsKey("currency") && !"INR".equals(body.get("currency")))
      throw new ApiError(400, "VALIDATION_ERROR", "V3 agreements use INR.");
    var milestones = validateMilestones(body.get("milestones"));
    long amount = milestones.stream().mapToLong(m -> ((Number) m.get("amountMinor")).longValue()).sum();
    if (amount > MAX_AMOUNT) throw amountError();
    if (body.containsKey("amountMinor") && amount(body.get("amountMinor")) != amount)
      throw new ApiError(400, "VALIDATION_ERROR", "The proposal total must equal its milestone amounts.");
    UUID proposal = id();
    int revision = repo.nextRevision(engagement);
    repo.draft(engagement, proposal, revision, scope, terms, amount, repo.json(milestones));
    repo.event(engagement, repo.verified(request), "PROPOSAL_DRAFTED", "Proposal revision " + revision + " drafted");
    return Map.of("id", proposal, "revision", revision, "status", "DRAFT");
  }

  @Transactional
  public Map<String, Object> proposalAction(UUID engagement, UUID proposal, String action, Map<String, Object> body, HttpServletRequest request) {
    String side = switch (action) {
      case "send", "withdraw" -> "SELLER";
      case "accept", "reject" -> "BUYER";
      default -> throw new ApiError(400, "VALIDATION_ERROR", "Choose a valid proposal action.");
    };
    var e = repo.lockEngagement(engagement, request, side);
    requireState(e, "OPEN");
    var p = repo.proposal(engagement, proposal);
    UUID actor = repo.verified(request);
    switch (action) {
      case "send" -> {
        requireState(p, "DRAFT");
        consent(body);
        repo.proposalState(proposal, "SENT", actor);
      }
      case "withdraw" -> {
        requireState(p, "DRAFT", "SENT");
        repo.proposalState(proposal, "WITHDRAWN", actor);
      }
      case "reject" -> {
        requireState(p, "SENT");
        repo.proposalState(proposal, "REJECTED", actor);
      }
      case "accept" -> {
        requireState(p, "SENT");
        consent(body);
        var milestones = repo.milestonesJson((String) p.get("milestonesJson"));
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("engagementId", engagement.toString());
        snapshot.put("proposalId", proposal.toString());
        snapshot.put("scope", p.get("scope")); snapshot.put("terms", p.get("terms"));
        snapshot.put("amountMinor", p.get("amount_minor")); snapshot.put("currency", p.get("currency"));
        snapshot.put("milestones", milestones);
        repo.accept(engagement, proposal, actor, hash(repo.json(snapshot)), milestones);
      }
    }
    repo.event(engagement, actor, "PROPOSAL_" + action.toUpperCase(Locale.ROOT), "Proposal revision " + p.get("revision") + " " + action + " recorded");
    return Map.of("ok", true);
  }

  @Transactional
  public Map<String, Object> milestoneAction(UUID engagement, UUID milestone, String action, Map<String, Object> body, HttpServletRequest request) {
    String side = switch (action) {
      case "start", "submit" -> "SELLER";
      case "request-revision", "accept" -> "BUYER";
      default -> throw new ApiError(400, "VALIDATION_ERROR", "Choose a valid milestone action.");
    };
    // Authorize the route engagement first: a valid milestone from another tenant must not be actionable.
    var e = repo.lockEngagement(engagement, request, side);
    requireState(e, "ACTIVE");
    repo.requireMilestone(engagement, milestone);
    var m = repo.lockMilestone(milestone, request, side);
    if (!engagement.equals(m.get("engagementId")))
      throw new ApiError(404, "NOT_FOUND", "Milestone not found.");
    UUID actor = repo.verified(request);
    String next, note = null, url = null, detail;
    switch (action) {
      case "start" -> { requireState(m, "PLANNED", "REVISION_REQUESTED"); next = "IN_PROGRESS"; detail = "Work started on " + m.get("title"); }
      case "submit" -> {
        requireState(m, "IN_PROGRESS");
        note = text(body, "deliveryNote", 10, 5000); url = deliveryUrl(body);
        next = "SUBMITTED"; detail = "Delivery submitted for " + m.get("title");
      }
      case "request-revision" -> {
        requireState(m, "SUBMITTED"); next = "REVISION_REQUESTED";
        detail = "Revision requested for " + m.get("title") + ": " + text(body, "reason", 10, 2000);
      }
      case "accept" -> { requireState(m, "SUBMITTED"); next = "ACCEPTED"; detail = "Deliverable accepted for " + m.get("title"); }
      default -> throw new IllegalStateException();
    }
    repo.milestoneState(milestone, next, note, url, actor);
    repo.event(engagement, actor, "MILESTONE_" + next, detail);
    return Map.of("status", next);
  }

  @Transactional
  public Map<String, Object> completion(UUID engagement, HttpServletRequest request) {
    var e = repo.lockEngagement(engagement, request, "ANY");
    boolean seller = "SELLER".equals(e.get("side"));
    requireState(e, seller ? "ACTIVE" : "COMPLETION_PENDING");
    if (!repo.allPaid(engagement))
      throw new ApiError(409, "UNPAID_MILESTONES", "Every milestone must be accepted and provider-confirmed paid without refunds before completion.");
    String next = seller ? "COMPLETION_PENDING" : "COMPLETED";
    repo.state(engagement, next);
    repo.event(engagement, repo.verified(request), next, seller ? "Seller requested final completion acknowledgement" : "Buyer confirmed final completion");
    return Map.of("status", next);
  }

  @Transactional
  public Map<String, Object> dispute(UUID engagement, Map<String, Object> body, HttpServletRequest request) {
    var e = repo.lockEngagement(engagement, request, "ANY");
    requireState(e, "OPEN", "ACTIVE", "COMPLETION_PENDING", "COMPLETED");
    UUID actor = repo.verified(request);
    UUID dispute = repo.dispute(engagement, actor, text(body, "reason", 10, 3000), (String) e.get("status"));
    repo.event(engagement, actor, "DISPUTE_OPENED", "Dispute opened; checkout and completion paused. No refund was issued.");
    return Map.of("id", dispute, "status", "OPEN");
  }

  public Map<String, Object> adminDisputes(HttpServletRequest request) {
    repo.admin(request);
    return Map.of("items", repo.disputes());
  }

  @Transactional
  public Map<String, Object> resolve(UUID dispute, Map<String, Object> body, HttpServletRequest request) {
    UUID actor = repo.admin(request);
    String resolution = text(body, "resolution", 1, 10);
    if (!Set.of("RESUME", "CANCEL").contains(resolution))
      throw new ApiError(400, "VALIDATION_ERROR", "Choose RESUME or CANCEL.");
    String reason = text(body, "reason", 10, 3000);
    var d = repo.lockDispute(dispute);
    requireState(d, "OPEN");
    UUID engagement = (UUID) d.get("engagement_id");
    String previous = (String) d.get("previous_status");
    // Refunded work must be reviewed again; do not restore a stale completed acknowledgement.
    if (resolution.equals("RESUME") && Set.of("COMPLETED", "COMPLETION_PENDING").contains(previous) && !repo.allPaid(engagement)) previous = "ACTIVE";
    repo.resolve(dispute, actor, resolution, reason);
    String next = resolution.equals("CANCEL") ? "CANCELLED" : previous;
    repo.state(engagement, next);
    repo.event(engagement, actor, "DISPUTE_" + resolution, reason);
    return Map.of("status", next);
  }

  private static void requireState(Map<String, Object> record, String... allowed) {
    if (!Arrays.asList(allowed).contains(record.get("status")))
      throw new ApiError(409, "INVALID_STATE_TRANSITION", "This action is not available in the current state.");
  }
  private static void consent(Map<String, Object> body) {
    if (!Boolean.TRUE.equals(body.get("consent")))
      throw new ApiError(400, "CONSENT_REQUIRED", "Explicit agreement consent is required.");
  }
  private static ApiError amountError() {
    return new ApiError(400, "VALIDATION_ERROR", "Use integer paise amounts of at least 100 with a total no greater than 1,000,000,000.");
  }
  private static long amount(Object value) {
    if (!(value instanceof Integer || value instanceof Long || value instanceof BigInteger)) throw amountError();
    long amount;
    try { amount = value instanceof BigInteger b ? b.longValueExact() : ((Number) value).longValue(); }
    catch (ArithmeticException e) { throw amountError(); }
    if (amount < 100 || amount > MAX_AMOUNT) throw amountError();
    return amount;
  }
  private static List<Map<String, Object>> validateMilestones(Object value) {
    if (!(value instanceof List<?> list) || list.isEmpty() || list.size() > 20)
      throw new ApiError(400, "VALIDATION_ERROR", "Use between one and twenty milestones.");
    var result = new ArrayList<Map<String, Object>>();
    for (Object entry : list) {
      if (!(entry instanceof Map<?, ?> fields)) throw new ApiError(400, "VALIDATION_ERROR", "Check milestone fields.");
      var b = new HashMap<String, Object>();
      for (var field : fields.entrySet()) if (field.getKey() instanceof String key) b.put(key, field.getValue());
      String date = text(b, "dueDate", 10, 10);
      try {
        LocalDate parsed = LocalDate.parse(date);
        if (parsed.getYear() < 2000 || parsed.getYear() > 2100) throw new IllegalArgumentException();
      } catch (Exception e) { throw new ApiError(400, "VALIDATION_ERROR", "Use a valid ISO milestone due date."); }
      var m = new LinkedHashMap<String, Object>();
      m.put("title", text(b, "title", 2, 120)); m.put("description", text(b, "description", 10, 3000));
      m.put("amountMinor", amount(b.get("amountMinor"))); m.put("dueDate", date);
      result.add(m);
    }
    return result;
  }
  private static String deliveryUrl(Map<String, Object> body) {
    String value = text(body, "deliveryUrl", 0, 2000);
    if (value.isEmpty()) return null;
    try {
      URI uri = URI.create(value);
      if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) throw new IllegalArgumentException();
      String host = uri.getHost().toLowerCase(Locale.ROOT);
      if (!host.contains(".") || host.contains(":") || host.matches("[0-9.]+") || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal")) throw new IllegalArgumentException();
      return value;
    } catch (IllegalArgumentException e) { throw new ApiError(400, "VALIDATION_ERROR", "Use an HTTPS delivery URL without embedded credentials."); }
  }
}
