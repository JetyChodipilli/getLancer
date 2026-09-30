package com.getlancer.shared;

import java.net.*;
import java.util.*;

public class RulesSmoke {
  static int checks;

  static void check(boolean b) {
    checks++;
    if (!b) throw new AssertionError("Check " + checks + " failed");
  }

  public static void main(String[] args) throws Exception {
    check(Rules.canActivate(true, true, 2, 3));
    check(!Rules.canActivate(true, true, 3, 3));
    check(!Rules.canActivate(false, true, 0, 3));
    check(!Rules.canActivate(true, false, 0, 3));
    check(Rules.publicProduct("APPROVED", "ACTIVE", "PUBLIC", "ACTIVE", "APPROVED"));
    for (String status : List.of("DRAFT", "ARCHIVED", "SUSPENDED"))
      check(!Rules.publicProduct("APPROVED", status, "PUBLIC", "ACTIVE", "APPROVED"));
    check(!Rules.publicProduct("APPROVED", "ACTIVE", "NDA_SAFE", "ACTIVE", "APPROVED"));
    check(!Rules.publicProduct("APPROVED", "ACTIVE", "PUBLIC", "ACTIVE", "SUSPENDED"));
    for (var entry : Rules.TRANSITIONS.entrySet()) {
      String[] pair = entry.getValue().split(":");
      check(Rules.transition(pair[0], entry.getKey()).equals(pair[1]));
      try {
        Rules.transition("CREATED_UNVERIFIED", entry.getKey());
        throw new AssertionError();
      } catch (IllegalArgumentException ok) {
        checks++;
      }
    }
    try {
      Rules.transition("INQUIRY_RECEIVED", "confirm-completion");
      throw new AssertionError();
    } catch (IllegalArgumentException ok) {
      checks++;
    }
    check(Rules.reviewEligible("COMPLETED", true, false));
    check(!Rules.reviewEligible("COMPLETED", false, false));
    check(!Rules.reviewEligible("COMPLETED", true, true));
    check(!Rules.reviewEligible("HIRED", true, false));
    for (String u :
        List.of(
            "http://example.com",
            "https://localhost",
            "https://127.0.0.1",
            "https://169.254.169.254",
            "https://192.168.0.1",
            "https://[::1]",
            "https://metadata.google.internal",
            "file:///etc/passwd",
            "javascript:alert(1)",
            "https://user:password@example.com",
            "https://example.com:8443")) {
      try {
        Rules.safeUrl(u);
        throw new AssertionError(u);
      } catch (IllegalArgumentException ok) {
        checks++;
      }
    }
    for (String ip :
        List.of(
            "127.0.0.1",
            "10.0.0.1",
            "172.16.0.1",
            "192.168.1.1",
            "169.254.169.254",
            "100.64.0.1",
            "198.18.0.1",
            "::1",
            "fc00::1",
            "fe80::1")) check(!Rules.publicAddress(InetAddress.getByName(ip)));
    check(Rules.publicAddress(InetAddress.getByName("8.8.8.8")));
    for (String kind : List.of("HIRE_CONFIRMATION", "COMPLETION_CONFIRMATION")) {
      String pending =
          kind.equals("HIRE_CONFIRMATION")
              ? "HIRE_PENDING_CONFIRMATION"
              : "COMPLETION_PENDING_CONFIRMATION";
      check(
          Rules.clientDecision(kind, pending, true)
              .equals(kind.equals("HIRE_CONFIRMATION") ? "HIRED" : "COMPLETED"));
      check(
          Rules.clientDecision(kind, pending, false)
              .equals(kind.equals("HIRE_CONFIRMATION") ? "DISCUSSION" : "IN_PROGRESS"));
      for (String invalid : List.of("CREATED_UNVERIFIED", "INQUIRY_RECEIVED", "NOT_HIRED")) {
        try {
          Rules.clientDecision(kind, invalid, true);
          throw new AssertionError();
        } catch (IllegalArgumentException ok) {
          checks++;
        }
      }
    }
    System.out.println(checks + " business-rule and URL-security checks passed");
  }
}
