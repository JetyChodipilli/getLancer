package com.getlancer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import com.getlancer.Application;
import com.getlancer.security.AuthorizationService;
import com.getlancer.security.BrowserSecurityFilter;
import com.getlancer.security.Security;
import com.getlancer.security.SecurityAudit;
import com.getlancer.security.SessionAuthenticationFilter;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;

class DefaultSecurityBootstrapIntegrationTest {
  @Test void applicationOmitsFallbackAccountAndPasswordLogButKeepsCustomSecurity() {
    var passwordLogged = new AtomicBoolean();
    var logging = (LoggerContext) LoggerFactory.getILoggerFactory();
    var observer = new TurboFilter() {
      @Override public FilterReply decide(Marker marker, Logger logger, Level level, String format,
          Object[] arguments, Throwable error) {
        if (logger.getName().equals(UserDetailsServiceAutoConfiguration.class.getName())
            && format != null && format.contains("Using generated security password")) {
          passwordLogged.set(true);
          // Neither a failing regression nor the positive control may publish a credential.
          return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
      }
    };
    observer.start();
    logging.addTurboFilter(observer);
    try {
      var runner = new WebApplicationContextRunner()
          .withUserConfiguration(Application.class)
          .withBean(SecurityBootstrapSlice.class, SecurityBootstrapSlice::new)
          .withBean(Security.class, () -> mock(Security.class))
          .withBean(AuthorizationService.class, () -> mock(AuthorizationService.class))
          .withBean(SecurityAudit.class, () -> mock(SecurityAudit.class))
          .withPropertyValues("spring.autoconfigure.exclude=" + DataSourceAutoConfiguration.class.getName());
      runner.run(context -> {
        assertNull(context.getStartupFailure(), "Application security bootstrap must succeed");
        assertFalse(passwordLogged.get(), "Application must not generate a development password");
        assertTrue(context.getBeansOfType(UserDetailsService.class).isEmpty(),
            "Database session authentication must not create a fallback user store");
        var chain = context.getBean("apiSecurity", SecurityFilterChain.class);
        var proxy = context.getBean(FilterChainProxy.class);
        assertEquals(1, proxy.getFilterChains().size());
        assertSame(chain, proxy.getFilterChains().get(0));
        assertTrue(chain.getFilters().stream().anyMatch(SessionAuthenticationFilter.class::isInstance));
        assertTrue(chain.getFilters().stream().anyMatch(BrowserSecurityFilter.class::isInstance));
        assertTrue(chain.getFilters().stream().anyMatch(CsrfFilter.class::isInstance));
        assertTrue(chain.getFilters().stream().anyMatch(AuthorizationFilter.class::isInstance));
        assertFalse(chain.getFilters().stream().anyMatch(BasicAuthenticationFilter.class::isInstance));
        assertFalse(chain.getFilters().stream().anyMatch(UsernamePasswordAuthenticationFilter.class::isInstance));
      });
      // Register the fallback directly to verify the log oracle independently of imported exclusions.
      runner.withConfiguration(AutoConfigurations.of(UserDetailsServiceAutoConfiguration.class))
          .run(context -> {
            assertNull(context.getStartupFailure(), "Fallback positive-control bootstrap must succeed");
            assertEquals(1, context.getBeansOfType(UserDetailsService.class).size(),
                "The positive control must create the unused fallback user store");
            assertTrue(passwordLogged.get(), "The observer must detect the real generated-password warning");
            var chain = context.getBean("apiSecurity", SecurityFilterChain.class);
            var proxy = context.getBean(FilterChainProxy.class);
            assertEquals(1, proxy.getFilterChains().size());
            assertSame(chain, proxy.getFilterChains().get(0));
          });
    } finally {
      logging.getTurboFilterList().remove(observer);
      observer.stop();
    }
  }

  /** Use the real Application auto-configuration and security chain without a database context. */
  static final class SecurityBootstrapSlice extends TypeExcludeFilter {
    @Override public boolean match(MetadataReader reader, MetadataReaderFactory factory) {
      return !reader.getClassMetadata().getClassName().equals(SecurityConfiguration.class.getName());
    }

    @Override public boolean equals(Object other) { return other instanceof SecurityBootstrapSlice; }
    @Override public int hashCode() { return SecurityBootstrapSlice.class.hashCode(); }
  }
}
