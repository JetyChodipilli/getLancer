package com.getlancer.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Hosted deployments use environment injection and an independently migrated database. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProductionConfiguration implements ApplicationRunner {
  final Environment env;

  public ProductionConfiguration(Environment env) { this.env = env; }

  @PostConstruct
  void beforeServing() { run(null); }

  public void run(ApplicationArguments args) {
    bounded("app.rate-limit", 30, 1, 10000);
    bounded("app.discovery-rate-limit", 300, 1, 10000);
    bounded("spring.datasource.hikari.maximum-pool-size", 10, 1, 100);
    bounded("app.analytics-retention-days", 90, 1, 365);
    bounded("app.reliability-min-sample", 10, 0, 10000);
    String schema = env.getProperty("spring.flyway.default-schema", "public");
    if (!schema.matches("[a-z][a-z0-9_]{0,62}")) fail("DB_SCHEMA must be a safe lowercase schema name");
    String stage = env.getProperty("app.environment", "");
    if (!Set.of("local", "staging", "production").contains(stage))
      fail("Explicit APP_ENV must be local, staging or production");
    Set<String> profiles = Set.of(env.getActiveProfiles());
    if (profiles.contains("staging") && !stage.equals("staging")
        || profiles.contains("production") && !stage.equals("production"))
      fail("Spring profile must match APP_ENV");
    if (stage.equals("local")) return; // Local fixtures intentionally retain migration/owner access.
    if (profiles.contains("local")) fail("Hosted environments cannot activate the local profile");
    for (String key : new String[] {"spring.config.import", "spring.config.location", "spring.config.additional-location"})
      if (!env.getProperty(key, "").isBlank()) fail("Hosted configuration cannot import external configuration files");
    if (schema.equals("public")) fail("Use a private application schema for staging/production");
    if (!schema.equals(env.getProperty("spring.datasource.hikari.schema", ""))) fail("Runtime and migration schemas must match");
    if (env.getProperty("spring.flyway.enabled", Boolean.class, true))
      fail("Hosted runtime must disable Flyway; run migrations with separate credentials before deployment");
    if (!required("spring.datasource.username").equals("getlancer_runtime"))
      fail("Hosted runtime must use getlancer_runtime, never a migration/admin role");
    for (String key : new String[] {"spring.flyway.user", "spring.flyway.password", "DB_MIGRATION_PASSWORD"})
      if (!env.getProperty(key, "").isBlank()) fail("Migration credentials must not be supplied to the runtime");
    if (!env.getProperty("app.secure-cookie", Boolean.class, false)) fail("Hosted environments require secure cookies");
    https("app.origin", true);
    https("app.backend-url", true);
    if (env.getProperty("app.demo-mode", Boolean.class, false)) fail("Hosted deployments cannot enable demo mode");
    if (!env.getProperty("server.forward-headers-strategy", "none").equals("none"))
      fail("Forwarded headers must be disabled; use the authenticated application proxy");
    if (required("app.proxy-secret").length() < 32) fail("BACKEND_PROXY_SECRET must contain at least 32 characters");
    validateDatabaseTls(required("spring.datasource.url"));
    for (String provider : new String[] {"google", "github"}) {
      boolean client = env.getProperty("app." + provider + ".client-id", "").isBlank();
      boolean secret = env.getProperty("app." + provider + ".client-secret", "").isBlank();
      if (client != secret) fail("Configure both OAuth values for " + provider);
      if (!client) { required("app." + provider + ".client-id"); required("app." + provider + ".client-secret"); }
    }
    if (env.getProperty("app.analytics-enabled", Boolean.class, false)
        && required("app.analytics-salt").length() < 32) fail("Analytics requires a private hashing salt");
    for (String key : new String[] {"spring.datasource.password", "app.storage.access-key", "app.storage.secret-key", "app.storage.bucket", "app.email-from"}) required(key);
    https("app.storage.endpoint", false);
    https("app.storage.upload-endpoint", false);
    String smtp = required("spring.mail.host"), from = required("app.email-from");
    if (localHost(smtp) || smtp.equals("mail") || smtp.equals("mailpit") || from.endsWith(".local"))
      fail("Hosted environments require configured mail services");
    if (!env.getProperty("spring.mail.properties.mail.smtp.starttls.enable", Boolean.class, false)
        && !env.getProperty("spring.mail.properties.mail.smtp.ssl.enable", Boolean.class, false)) fail("Hosted email must use TLS");
    if (env.getProperty("spring.mail.properties.mail.smtp.starttls.enable", Boolean.class, false)
        && !env.getProperty("spring.mail.properties.mail.smtp.starttls.required", Boolean.class, false)) fail("SMTP STARTTLS must be required");
    if (!env.getProperty("spring.mail.properties.mail.smtp.ssl.checkserveridentity", Boolean.class, false)) fail("SMTP TLS must verify server identity");
    required("app.mfa.keyring"); required("app.mfa.active-key-id"); // MfaSecrets validates key sizes and keyring format.
    String password = env.getProperty("app.admin-password", ""), totp = env.getProperty("app.admin-totp", "");
    if (!password.isBlank() || !totp.isBlank()) {
      if (!required("app.admin-email").matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
          || password.length() < 16 || password.getBytes(StandardCharsets.UTF_8).length > 72
          || !totp.matches("[A-Z2-7]{32,}")) fail("Administrator bootstrap requires explicit identity and strong credentials");
    } // Bootstrap verifies the existing administrator, or fails closed if no administrator exists.
    if (stage.equals("production")) {
      if (!env.getProperty("app.policies-approved", Boolean.class, false) || required("app.legal-version").contains("draft"))
        fail("Approve and version public policies before launch");
      for (String key : new String[] {"app.support-email", "app.privacy-email", "app.copyright-email"})
        if (!required(key).matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) fail("Configure a valid operator contact: " + key);
    }
  }

  void bounded(String key, int fallback, int minimum, int maximum) {
    int value = env.getProperty(key, Integer.class, fallback);
    if (value < minimum || value > maximum) fail("Configuration outside supported range: " + key);
  }

  void https(String key, boolean origin) {
    try {
      URI uri = URI.create(required(key));
      if (!"https".equals(uri.getScheme()) || uri.getHost() == null || localHost(uri.getHost())
          || uri.getRawUserInfo() != null || uri.getRawFragment() != null || uri.getPort() == 0
          || (origin && (uri.getRawQuery() != null || !"".equals(uri.getRawPath()))))
        fail("Hosted configuration requires a valid HTTPS URL: " + key);
      if (origin) {
        String canonical = "https://" + uri.getHost().toLowerCase(java.util.Locale.ROOT)
            + (uri.getPort() == -1 || uri.getPort() == 443 ? "" : ":" + uri.getPort());
        if (!required(key).equals(canonical)) fail("Hosted origin must use canonical origin spelling: " + key);
      }
    } catch (IllegalArgumentException e) { fail("Hosted configuration requires a valid HTTPS URL: " + key); }
  }

  static void validateDatabaseTls(String value) {
    try {
      if (!value.startsWith("jdbc:postgresql://")) fail("Hosted DB_URL must use PostgreSQL TLS");
      URI uri = URI.create(value.substring(5));
      if (uri.getHost() == null || localHost(uri.getHost()) || uri.getRawUserInfo() != null
          || uri.getRawFragment() != null || uri.getPath() == null || !uri.getPath().matches("/[^/]+"))
        fail("Hosted DB_URL must identify a remote PostgreSQL database without embedded credentials");
      Map<String, String> parameters = new HashMap<>();
      for (String entry : (uri.getRawQuery() == null ? "" : uri.getRawQuery()).split("&")) {
        if (entry.isBlank()) continue;
        String[] parts = entry.split("=", 2);
        String key = parts[0];
        if (!key.matches("[A-Za-z][A-Za-z0-9]*")) fail("DB_URL parameter names must be literal JDBC property names");
        String normalized = key.toLowerCase(java.util.Locale.ROOT);
        if (Set.of("gssencmode", "authenticationpluginclassname", "service", "pghost", "pgport", "pgdbname", "host", "port", "dbname", "currentschema").contains(normalized))
          fail("DB_URL contains an unsupported connection override");
        if (Set.of("sslmode", "ssl", "sslfactory", "sslhostnameverifier", "sslpasswordcallback", "socketfactory", "user", "password", "options").contains(normalized)
            && !key.equals(normalized)) fail("DB_URL security parameter names must use their exact JDBC spelling");
        String setting = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
        if (parameters.putIfAbsent(key, setting) != null) fail("DB_URL must not repeat connection parameters");
      }
      if (!"verify-full".equals(parameters.get("sslmode"))) fail("Hosted DB_URL must use sslmode=verify-full");
      if (parameters.containsKey("ssl") && !"true".equals(parameters.get("ssl"))) fail("DB_URL cannot disable TLS");
      for (String key : new String[] {"sslfactory", "sslhostnameverifier", "sslpasswordcallback", "socketfactory", "user", "password", "options"})
        if (parameters.containsKey(key)) fail("DB_URL contains an unsupported connection override");
    } catch (IllegalArgumentException e) { fail("Hosted DB_URL is invalid"); }
  }

  static boolean localHost(String host) {
    String name = host.toLowerCase(java.util.Locale.ROOT);
    return name.equals("localhost") || name.endsWith(".localhost") || name.endsWith(".local")
        || name.equals("[::1]") || name.equals("::1") || name.equals("0.0.0.0") || name.startsWith("127.");
  }

  String required(String key) {
    String value = env.getProperty(key, "");
    if (value.isBlank() || value.contains("REPLACE_") || value.contains("YOUR_") || value.equals("CHANGE_ME"))
      fail("Missing hosted configuration: " + key);
    return value;
  }
  static void fail(String message) { throw new IllegalStateException(message); }
}
