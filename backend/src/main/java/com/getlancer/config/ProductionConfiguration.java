package com.getlancer.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProductionConfiguration implements ApplicationRunner {
  final Environment env;

  public ProductionConfiguration(Environment env) {
    this.env = env;
  }

  public void run(ApplicationArguments args) {
    int rate = env.getProperty("app.rate-limit", Integer.class, 30),
        pool = env.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class, 10);
    if (rate < 1 || rate > 10000 || pool < 1 || pool > 100)
      throw new IllegalStateException("Rate limit or pool size outside supported range");
    int retention = env.getProperty("app.analytics-retention-days", Integer.class, 90),
        sample = env.getProperty("app.reliability-min-sample", Integer.class, 10);
    if (retention < 1 || retention > 365 || sample < 0 || sample > 10000)
      throw new IllegalStateException("Invalid analytics retention or reliability threshold");
    String schema = env.getProperty("spring.flyway.default-schema", "public");
    if (!schema.matches("[a-z][a-z0-9_]{0,62}"))
      throw new IllegalStateException("DB_SCHEMA must be a safe lowercase schema name");
    String stage = env.getProperty("app.environment", "local");
    if (!java.util.Set.of("local", "staging", "production").contains(stage))
      throw new IllegalStateException("APP_ENV must be local, staging or production");
    boolean hosted = java.util.Set.of("staging", "production").contains(stage);
    if (!hosted) return;
    if (schema.equals("public"))
      throw new IllegalStateException("Use a private application schema for staging/production");
    if (!env.getProperty("app.secure-cookie", Boolean.class, true)
        || !required("app.origin").startsWith("https://"))
      throw new IllegalStateException("Hosted environments require HTTPS and secure cookies");
    if (required("app.proxy-secret").length() < 32)
      throw new IllegalStateException("BACKEND_PROXY_SECRET must contain at least 32 characters");
    String db = required("spring.datasource.url");
    if (!db.contains("sslmode=verify-full"))
      throw new IllegalStateException("Hosted database connections must validate TLS certificates");
    for (String provider : new String[] {"google", "github"}) {
      boolean client = env.getProperty("app." + provider + ".client-id", "").isBlank(),
          secret = env.getProperty("app." + provider + ".client-secret", "").isBlank();
      if (client != secret)
        throw new IllegalStateException("Configure both OAuth values for " + provider);
    }
    if (env.getProperty("app.analytics-enabled", Boolean.class, false)
        && required("app.analytics-salt").length() < 32)
      throw new IllegalStateException("Analytics requires a private hashing salt");
    if (stage.equals("production")) {
      if (!env.getProperty("app.policies-approved", Boolean.class, false)
          || required("app.legal-version").contains("draft"))
        throw new IllegalStateException("Approve and version public policies before launch");
      for (String key :
          new String[] {"app.support-email", "app.privacy-email", "app.copyright-email"})
        if (!required(key).matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))
          throw new IllegalStateException("Configure a valid operator contact: " + key);
    }
    for (String key :
        new String[] {
          "spring.datasource.password",
          "app.storage.access-key",
          "app.storage.secret-key",
          "app.storage.bucket",
          "app.email-from"
        }) required(key);
    if (!required("app.storage.upload-endpoint").startsWith("https://"))
      throw new IllegalStateException("Hosted upload URLs must use HTTPS");
    String smtp = required("spring.mail.host"),
        endpoint = required("app.storage.endpoint"),
        from = required("app.email-from");
    if (smtp.equals("localhost")
        || smtp.equals("mailpit")
        || smtp.endsWith(".local")
        || from.endsWith(".local")
        || !endpoint.startsWith("https://"))
      throw new IllegalStateException(
          "Production requires configured email and HTTPS storage services");
    if (!env.getProperty("spring.mail.properties.mail.smtp.starttls.enable", Boolean.class, false)
        && !env.getProperty("spring.mail.properties.mail.smtp.ssl.enable", Boolean.class, false))
      throw new IllegalStateException("Production email must use TLS");
  }

  String required(String key) {
    String value = env.getProperty(key, "");
    if (value.isBlank()
        || value.startsWith("REPLACE_")
        || value.contains("YOUR_")
        || value.equals("CHANGE_ME"))
      throw new IllegalStateException("Missing production configuration: " + key);
    return value;
  }
}
