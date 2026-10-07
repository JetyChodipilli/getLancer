package com.getlancer.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class SessionAuthenticationFilter extends OncePerRequestFilter {
  private final Security security;
  public SessionAuthenticationFilter(Security security) { this.security = security; }

  @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    var user = security.loadPrincipal(request);
    if (user != null) {
      var context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null,
          user.roles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList()));
      SecurityContextHolder.setContext(context);
    }
    chain.doFilter(request, response);
  }
}
