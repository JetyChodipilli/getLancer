package com.getlancer.inquiries;

import static com.getlancer.shared.Support.id;
import static com.getlancer.shared.Support.text;

import com.getlancer.auth.AuthService;
import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InquiryContractService {
  final ClientInquiryService clients;
  final AuthService auth;
  final InquiryService inquiries;

  public InquiryContractService(
      ClientInquiryService clients, AuthService auth, InquiryService inquiries) {
    this.clients = clients;
    this.auth = auth;
    this.inquiries = inquiries;
  }

  @Transactional
  public Map<String, Object> confirm(UUID id, Map<String, Object> raw, HttpServletRequest request) {
    Map<String, Object> body = raw == null ? new HashMap<>() : new HashMap<>(raw);
    String kind =
        request.getRequestURI().endsWith("confirm-hire")
            ? "HIRE_CONFIRMATION"
            : "COMPLETION_CONFIRMATION";
    body.put("kind", kind);
    body.putIfAbsent("decision", "ACCEPT");
    if (body.containsKey("token")) {
      var token = auth.confirmationToken(text(body, "token", 20, 200), false);
      if (!id.equals(token.get("inquiry_id")) || !kind.equals(token.get("kind")))
        throw new ApiError(400, "INVALID_TOKEN", "This link is for a different action.");
      return auth.confirm(body, request);
    }
    return clients.decision(id, body, request);
  }

  @Transactional
  public Map<String, Object> close(UUID id, HttpServletRequest request) {
    var user = clients.client(request);
    if (clients.requestedBy(id, user)) return clients.close(id, request);
    return inquiries.transition(id, "not-hired", Map.of(), request);
  }

  @Transactional
  public Map<String, Object> review(UUID id, Map<String, Object> body, HttpServletRequest request) {
    return clients.review(id, body, request);
  }
}
