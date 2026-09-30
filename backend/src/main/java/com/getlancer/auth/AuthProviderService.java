package com.getlancer.auth;

import static com.getlancer.shared.Support.*;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.*;
import java.net.*;
import java.net.http.*;
import java.util.*;

@org.springframework.stereotype.Service
public class AuthProviderService {
  final GoogleAuthService google;
  final GitHubAuthService github;

  public AuthProviderService(GoogleAuthService google, GitHubAuthService github) {
    this.google = google;
    this.github = github;
  }

  public Map<String, Object> providers() {
    return Map.of("google", google.enabled(), "github", github.enabled());
  }
}
