package com.getlancer.auth;

import static com.getlancer.shared.Support.hash;
import static com.getlancer.shared.Support.randomToken;
import static com.getlancer.shared.Support.text;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.security.SessionCookies;
import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class GoogleAuthService {
  final JdbcTemplate db;
  final AuthService auth;
  final GoogleTokens tokens;
  final GoogleAccounts accounts;
  final ObjectMapper json;
  final String clientId, clientSecret, redirectUri;
  final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public GoogleAuthService(
      JdbcTemplate db,
      AuthService auth,
      GoogleTokens tokens,
      GoogleAccounts accounts,
      ObjectMapper json,
      @Value("${app.google.client-id:}") String clientId,
      @Value("${app.google.client-secret:}") String secret,
      @Value("${app.origin}") String origin) {
    this.db = db;
    this.auth = auth;
    this.tokens = tokens;
    this.accounts = accounts;
    this.json = json;
    this.clientId = clientId;
    this.clientSecret = secret;
    this.redirectUri = origin.replaceAll("/$", "") + "/api/v1/auth/google/callback";
  }

  boolean enabled() {
    return !clientId.isBlank() && !clientSecret.isBlank();
  }

  public Map<String, Object> start(Map<String, Object> body, HttpServletResponse response)
      throws Exception {
    if (!enabled())
      throw new ApiError(
          503,
          "GOOGLE_NOT_CONFIGURED",
          "Google sign-in is not available yet. Use email and password.");
    String intent = text(body, "intent", 1, 10);
    if (!Set.of("login", "signup").contains(intent))
      throw new ApiError(400, "VALIDATION_ERROR", "Choose login or signup.");
    if (intent.equals("signup") && !Boolean.TRUE.equals(body.get("acceptedTerms")))
      throw new ApiError(400, "TERMS_REQUIRED", "Accept the terms and privacy notice to continue.");
    String state = randomToken(),
        browser = randomToken(),
        nonce = randomToken(),
        verifier = randomToken();
    db.update("DELETE FROM oauth_pending WHERE expires_at<now()");
    db.update(
        "INSERT INTO oauth_pending(state_hash,browser_hash,nonce,code_verifier,intent,expires_at)"
            + " VALUES(?,?,?,?,?,now()+interval '10 minutes')",
        hash(state),
        hash(browser),
        nonce,
        verifier,
        intent);
    db.update(
        "UPDATE oauth_pending SET remember_me=? WHERE state_hash=?",
        Boolean.TRUE.equals(body.get("rememberMe")),
        hash(state));
    auth.authCookie(response, "gl_oauth", browser, 600);
    String challenge =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    var params = new LinkedHashMap<String, String>();
    params.put("client_id", clientId);
    params.put("redirect_uri", redirectUri);
    params.put("response_type", "code");
    params.put("scope", "openid email profile");
    params.put("state", state);
    params.put("nonce", nonce);
    params.put("code_challenge", challenge);
    params.put("code_challenge_method", "S256");
    params.put("prompt", "select_account");
    return Map.of(
        "authorizationUrl", "https://accounts.google.com/o/oauth2/v2/auth?" + form(params));
  }

  public ResponseEntity<Void> callback(
      String state, String code, String error, HttpServletRequest req, HttpServletResponse res) {
    auth.authCookie(res, "gl_oauth", "", 0);
    if (!enabled()) return back("login", "unavailable");
    String browser = SessionCookies.read(req, "gl_oauth");
    if (!state.matches("[A-Za-z0-9_-]{43}") || !browser.matches("[A-Za-z0-9_-]{43}"))
      return back("login", "expired");
    // Atomic consume in autocommit: failed exchange/verification must not resurrect state.
    var rows =
        db.queryForList(
            "DELETE FROM oauth_pending WHERE state_hash=? AND browser_hash=? AND provider='google'"
                + " AND expires_at>now() RETURNING nonce,code_verifier,intent,remember_me",
            hash(state),
            hash(browser));
    if (rows.isEmpty()) return back("login", "expired");
    var pending = rows.get(0);
    String intent = (String) pending.get("intent");
    if (!error.isBlank()) return back(intent, "cancelled");
    if (code.isBlank() || code.length() > 4096) return back(intent, "failed");
    try {
      var params = new LinkedHashMap<String, String>();
      params.put("code", code);
      params.put("client_id", clientId);
      params.put("client_secret", clientSecret);
      params.put("redirect_uri", redirectUri);
      params.put("grant_type", "authorization_code");
      params.put("code_verifier", (String) pending.get("code_verifier"));
      var request =
          HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
              .timeout(Duration.ofSeconds(10))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(HttpRequest.BodyPublishers.ofString(form(params)))
              .build();
      var response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) return back(intent, "failed");
      var token = json.readTree(response.body());
      var identity = tokens.verify(token.path("id_token").asText(), (String) pending.get("nonce"));
      UUID user = accounts.resolve(identity, intent);
      auth.issueSession(user, false, Boolean.TRUE.equals(pending.get("remember_me")), res);
      return ResponseEntity.status(303).location(URI.create("/workspace")).build();
    } catch (ApiError e) {
      return back(e.code.equals("signup_required") ? "signup" : intent, e.code);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return back(intent, "failed");
    } catch (Exception e) {
      return back(intent, "failed");
    }
  }

  static String form(Map<String, String> params) {
    return params.entrySet().stream()
        .map(
            e ->
                URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));
  }

  static ResponseEntity<Void> back(String intent, String code) {
    return ResponseEntity.status(303)
        .location(
            URI.create((intent.equals("signup") ? "/signup" : "/login") + "?auth_error=" + code))
        .build();
  }
}
