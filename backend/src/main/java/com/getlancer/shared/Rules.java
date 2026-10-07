package com.getlancer.shared;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Rules {
  public static final Map<String, String> TRANSITIONS =
      Map.of(
          "responded",
          "INQUIRY_RECEIVED:RESPONDED",
          "discussion",
          "RESPONDED:DISCUSSION",
          "proposal-sent",
          "DISCUSSION:PROPOSAL_SENT",
          "request-hire-confirmation",
          "PROPOSAL_SENT:HIRE_PENDING_CONFIRMATION",
          "in-progress",
          "HIRED:IN_PROGRESS",
          "request-completion-confirmation",
          "IN_PROGRESS:COMPLETION_PENDING_CONFIRMATION");

  public static String transition(String state, String action) {
    if (action.equals("not-hired")
        && Set.of("INQUIRY_RECEIVED", "RESPONDED", "DISCUSSION", "PROPOSAL_SENT").contains(state))
      return "NOT_HIRED";
    String rule = TRANSITIONS.get(action);
    if (rule == null || !rule.split(":")[0].equals(state))
      throw new IllegalArgumentException("INVALID_STATE_TRANSITION");
    return rule.split(":")[1];
  }

  public static boolean canActivate(
      boolean approved, boolean ownerApproved, int active, int limit) {
    return approved && ownerApproved && active < limit;
  }

  public static boolean publicProduct(
      String approval, String lifecycle, String visibility, String account, String profile) {
    return approval.equals("APPROVED")
        && lifecycle.equals("ACTIVE")
        && visibility.equals("PUBLIC")
        && account.equals("ACTIVE")
        && profile.equals("APPROVED");
  }

  public static void safeUrl(String raw) {
    if (raw == null || raw.isBlank()) return;
    try {
      URI uri = new URI(raw);
      String host = uri.getHost();
      if (!"https".equalsIgnoreCase(uri.getScheme())
          || host == null
          || uri.getRawUserInfo() != null
          || uri.getPort() != -1 && uri.getPort() != 443) throw new Exception();
      host = host.toLowerCase(Locale.ROOT);
      if (host.equals("localhost")
          || host.endsWith(".localhost")
          || host.endsWith(".local")
          || host.endsWith(".internal")
          || !host.contains(".")
          || host.contains(":")
          || host.matches("[0-9.]+")) throw new Exception();
      for (InetAddress ip : InetAddress.getAllByName(host)) {
        if (!publicAddress(ip)) throw new Exception();
      }
    } catch (Exception e) {
      throw new IllegalArgumentException("UNSAFE_EXTERNAL_URL");
    }
  }

  public static boolean publicAddress(InetAddress ip) {
    if (ip.isAnyLocalAddress()
        || ip.isLoopbackAddress()
        || ip.isLinkLocalAddress()
        || ip.isSiteLocalAddress()
        || ip.isMulticastAddress()) return false;
    byte[] b = ip.getAddress();
    if (b.length == 16) return (b[0] & 0xe0) == 0x20;
    int a = b[0] & 255, c = b[1] & 255;
    return !(a == 0
        || a == 10
        || a == 127
        || a >= 224
        || a == 169 && c == 254
        || a == 172 && c >= 16 && c <= 31
        || a == 192 && (c == 168 || c == 0)
        || a == 100 && c >= 64 && c <= 127
        || a == 198 && (c == 18 || c == 19));
  }

  public static boolean reviewEligible(String state, boolean completionEvent, boolean self) {
    return state.equals("COMPLETED") && completionEvent && !self;
  }

  public static String clientDecision(String kind, String state, boolean accepted) {
    if (kind.equals("HIRE_CONFIRMATION") && state.equals("HIRE_PENDING_CONFIRMATION"))
      return accepted ? "HIRED" : "DISCUSSION";
    if (kind.equals("COMPLETION_CONFIRMATION") && state.equals("COMPLETION_PENDING_CONFIRMATION"))
      return accepted ? "COMPLETED" : "IN_PROGRESS";
    throw new IllegalArgumentException("INVALID_STATE_TRANSITION");
  }
}
