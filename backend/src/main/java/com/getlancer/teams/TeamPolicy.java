package com.getlancer.teams;

import com.getlancer.shared.ApiError;
import com.getlancer.shared.Support;
import java.time.*;
import java.util.*;

public final class TeamPolicy {
  static final Set<String> ROLES =
      Set.of("OWNER", "BUSINESS_MANAGER", "RECRUITER", "PROJECT_MANAGER", "MEMBER");

  static boolean permits(String role, String capability) {
    return "OWNER".equals(role)
        || switch (capability) {
          case "recruit" -> "RECRUITER".equals(role);
          case "commercial" -> "BUSINESS_MANAGER".equals(role);
          case "staff" -> "PROJECT_MANAGER".equals(role);
          default -> false;
        };
  }

  static void require(String role, String capability) {
    if (!permits(role, capability))
      throw new ApiError(403, "FORBIDDEN", "Your team role cannot perform this action.");
  }

  static String choice(Map<String, Object> b, String key, Set<String> values) {
    String v = Support.text(b, key, 1, 40);
    if (!values.contains(v))
      throw new ApiError(400, "VALIDATION_ERROR", "Choose a valid " + key + ".");
    return v;
  }

  static Instant future(Object value, boolean required) {
    if (value == null || value.toString().isBlank()) {
      if (required) throw new ApiError(400, "VALIDATION_ERROR", "An expiry is required.");
      return null;
    }
    try {
      Instant t = Instant.parse(value.toString());
      if (!t.isAfter(Instant.now())) throw new IllegalArgumentException();
      return t;
    } catch (Exception e) {
      throw new ApiError(400, "VALIDATION_ERROR", "Use a future ISO timestamp.");
    }
  }

  static void invitationRole(String actor, String requested) {
    require(actor, "recruit");
    if (!ROLES.contains(requested)
        || requested.equals("OWNER")
        || (!actor.equals("OWNER") && !requested.equals("MEMBER")))
      throw new ApiError(403, "FORBIDDEN", "This invitation role is not permitted.");
  }

  static void leadTransition(String from, String to) {
    if (from.equals(to)) return;
    Set<String> next =
        switch (from) {
          case "NEW" -> Set.of("INTERESTED", "NEEDS_INFORMATION", "DECLINED");
          case "NEEDS_INFORMATION" -> Set.of("INTERESTED", "DECLINED");
          case "INTERESTED" -> Set.of("NEEDS_INFORMATION", "DECLINED", "PROPOSAL_SENT", "LOST");
          case "PROPOSAL_SENT" -> Set.of("NEEDS_INFORMATION", "WON", "LOST");
          default -> Set.of();
        };
    if (!next.contains(to))
      throw new ApiError(409, "INVALID_STATE_TRANSITION", "This lead transition is not allowed.");
  }

  static void applicationTransition(String from, String to) {
    if (!((from.equals("APPLIED") && Set.of("SHORTLISTED", "REJECTED", "INVITED").contains(to))
        || (from.equals("SHORTLISTED") && Set.of("REJECTED", "INVITED").contains(to))))
      throw new ApiError(
          409, "INVALID_STATE_TRANSITION", "This application decision is not allowed.");
  }
}
