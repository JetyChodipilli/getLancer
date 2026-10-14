package com.getlancer.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/** Request-local application time, including metadata serialization, never a network-latency estimate. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public final class CatalogueTimingFilter extends OncePerRequestFilter {
  private static final Set<String> CATALOGUES=Set.of("/api/v1/components","/api/v1/college-projects","/api/v1/products");
  @Override protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getMethod().equals("GET") || !CATALOGUES.contains(request.getRequestURI());
  }
  @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
      throws ServletException,IOException {
    // These three paginated metadata handlers cap item counts and remove source bytes. No stream/private package is buffered.
    var buffered=new ContentCachingResponseWrapper(response);
    long began=System.nanoTime();
    chain.doFilter(request,buffered);
    if(buffered.getStatus()==200)buffered.setHeader("Server-Timing",String.format(Locale.ROOT,"app;dur=%.3f",(System.nanoTime()-began)/1_000_000.0));
    buffered.copyBodyToResponse();
  }
}
