package com.getlancer.testing;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/** Runs before bean creation, Flyway, bootstrap, or destructive fixtures. */
public class TestDatabaseGuard
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  @Override
  public void initialize(ConfigurableApplicationContext context) {
    Environment env = context.getEnvironment();
    validate(env);
    // Several application contexts share this disposable database but use different
    // synthetic administrator settings. Remove prior fixtures before bootstrap runs.
    try (var connection = DriverManager.getConnection(env.getProperty("spring.datasource.url"),
        env.getProperty("spring.datasource.username"), env.getProperty("spring.datasource.password"));
        var statement = connection.createStatement();
        var tables = statement.executeQuery("SELECT to_regclass('getlancer_test.users') IS NOT NULL")) {
      tables.next();
      boolean initialized = tables.getBoolean(1);
      tables.close();
      if (initialized) statement.execute("TRUNCATE getlancer_test.users CASCADE");
    } catch (SQLException exception) {
      throw new IllegalStateException("Cannot reset the disposable integration test fixtures", exception);
    }
  }

  public static void validate(Environment env) {
    String url = env.getProperty("spring.datasource.url", "");
    if (!env.getProperty("TEST_DATABASE_RESET", "false").equals("true")
        || !url.matches("jdbc:postgresql://[^/?#]+/getlancer_test(?:\\?[^#]*)?")
        || !env.getProperty("spring.flyway.default-schema", "").equals("getlancer_test"))
      throw new IllegalStateException(
          "Integration tests require TEST_DATABASE_RESET=true and a dedicated getlancer_test"
              + " database/schema; normal DB_URL is never used.");
  }
}
