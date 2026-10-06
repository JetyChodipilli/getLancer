package com.getlancer.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.getlancer.security.AuthorizationService;
import com.getlancer.security.BrowserSecurityFilter;
import com.getlancer.security.Security;
import com.getlancer.security.SecurityAudit;
import com.getlancer.security.SessionAuthenticationFilter;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
  @Bean
  SecurityFilterChain apiSecurity(HttpSecurity http, Security security,
      AuthorizationService authorization, SecurityAudit audit) throws Exception {
    var authentication = new SessionAuthenticationFilter(security);
    var browser = new BrowserSecurityFilter(security, authorization, audit);
    // Exact Origin + non-simple header + exact CORS form the documented browser CSRF equivalent.
    // Standard Spring CSRF protection remains for any mutation outside those verified exceptions.
    http.csrf(csrf -> csrf.ignoringRequestMatchers(browser::hasBrowserMutationProtection, authorization::signedWebhook))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.disable())
        .formLogin(login -> login.disable()).httpBasic(basic -> basic.disable()).logout(logout -> logout.disable())
        .authorizeHttpRequests(requests -> requests.anyRequest().access((current, context) ->
            new AuthorizationDecision(authorization.allowed(current.get(), context.getRequest()))))
        .exceptionHandling(errors -> errors
            .authenticationEntryPoint((request, response, exception) -> {
              var route = authorization.route(request.getMethod(), request.getRequestURI());
              boolean internal = route != null && (route.policy() == AuthorizationService.Policy.SERVICE
                  || route.policy() == AuthorizationService.Policy.HEALTH);
              BrowserSecurityFilter.writeError(request, response, internal ? 403 : 401,
                  internal ? "FORBIDDEN" : "UNAUTHENTICATED",
                  internal ? "You cannot access this resource." : "Please log in to continue.");
            })
            .accessDeniedHandler((request, response, exception) -> BrowserSecurityFilter.writeError(
                request, response, 403, "FORBIDDEN", "You cannot access this resource.")))
        .addFilterBefore(authentication, AnonymousAuthenticationFilter.class)
        .addFilterBefore(browser, CsrfFilter.class);
    return http.build();
  }

  @Bean
  Jackson2ObjectMapperBuilderCustomizer strictJsonRequests() {
    return builder -> builder.featuresToEnable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
        DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .featuresToDisable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        .postConfigurer(mapper -> {
          mapper.coercionConfigFor(LogicalType.Boolean)
              .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
              .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
          mapper.coercionConfigFor(LogicalType.Textual)
              .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
              .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
              .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        });
  }
}
