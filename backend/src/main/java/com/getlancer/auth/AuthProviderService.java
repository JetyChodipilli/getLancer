package com.getlancer.auth;

import com.getlancer.responses.AuthProvidersResponse;
import org.springframework.stereotype.Service;

@Service
public class AuthProviderService {
  final GoogleAuthService google;
  final GitHubAuthService github;

  public AuthProviderService(GoogleAuthService google, GitHubAuthService github) {
    this.google = google;
    this.github = github;
  }

  public AuthProvidersResponse providers() {
    return new AuthProvidersResponse(google.enabled(), github.enabled());
  }
}
