package com.getlancer;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/** Runs before bean creation, Flyway, bootstrap, or destructive fixtures. */
public class TestDatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
 @Override public void initialize(ConfigurableApplicationContext context) { validate(context.getEnvironment()); }
 static void validate(Environment env) {
  String url=env.getProperty("spring.datasource.url", "");
  if (!env.getProperty("TEST_DATABASE_RESET", "false").equals("true")
      || !url.matches("jdbc:postgresql://[^/?#]+/getlancer_test(?:\\?[^#]*)?")
      || !env.getProperty("spring.flyway.default-schema", "").equals("getlancer_test"))
   throw new IllegalStateException("Integration tests require TEST_DATABASE_RESET=true and a dedicated getlancer_test database/schema; normal DB_URL is never used.");
 }
}
