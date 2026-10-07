package com.getlancer.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionConfigurationTest {
  private MockEnvironment hosted() {
    MockEnvironment env = new MockEnvironment();
    Map.ofEntries(
        Map.entry("app.environment", "staging"), Map.entry("spring.flyway.default-schema", "getlancer"),
        Map.entry("spring.datasource.hikari.schema", "getlancer"), Map.entry("spring.flyway.enabled", "false"),
        Map.entry("spring.datasource.username", "getlancer_runtime"), Map.entry("spring.datasource.password", "synthetic-only-database-password"),
        Map.entry("spring.datasource.url", "jdbc:postgresql://db.example.test/getlancer?sslmode=verify-full&sslrootcert=/run/secrets/ca.crt"),
        Map.entry("app.secure-cookie", "true"), Map.entry("app.origin", "https://getlancer.example.test"),
        Map.entry("app.backend-url", "https://api.example.test"), Map.entry("app.proxy-secret", "synthetic-only-proxy-secret-with-32-characters"),
        Map.entry("app.storage.endpoint", "https://objects.example.test"), Map.entry("app.storage.upload-endpoint", "https://uploads.example.test"),
        Map.entry("app.storage.access-key", "synthetic-only-access"), Map.entry("app.storage.secret-key", "synthetic-only-storage"),
        Map.entry("app.storage.bucket", "private-fixture"), Map.entry("app.email-from", "operator@example.test"),
        Map.entry("spring.mail.host", "smtp.example.test"), Map.entry("spring.mail.properties.mail.smtp.starttls.enable", "true"),
        Map.entry("spring.mail.properties.mail.smtp.starttls.required", "true"), Map.entry("spring.mail.properties.mail.smtp.ssl.checkserveridentity", "true"),
        Map.entry("app.mfa.keyring", "test:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="), Map.entry("app.mfa.active-key-id", "test"))
        .forEach(env::setProperty);
    env.setActiveProfiles("staging");
    return env;
  }

  @Test void validHostedAndExplicitLocalFixturesPass() {
    assertDoesNotThrow(() -> new ProductionConfiguration(hosted()).run(null));
    assertDoesNotThrow(() -> new ProductionConfiguration(new MockEnvironment().withProperty("app.environment", "local")).run(null));
    assertThrows(IllegalStateException.class, () -> new ProductionConfiguration(new MockEnvironment()).run(null));
  }

  @Test void localProfileIsTheOnlyPackagedFileImporterAndPersonalAdminDefaultIsAbsent() throws IOException {
    for (String resource : new String[] {"application.properties", "application-staging.properties", "application-production.properties"}) {
      String source = new String(getClass().getClassLoader().getResourceAsStream(resource).readAllBytes(), StandardCharsets.UTF_8);
      assertFalse(source.contains("spring.config.import="), resource);
      assertFalse(source.contains("jetychodipilli"), resource);
    }
    String local = new String(getClass().getClassLoader().getResourceAsStream("application-local.properties").readAllBytes(), StandardCharsets.UTF_8);
    assertTrue(local.contains("spring.config.import=optional:file:"));
  }

  @Test void realConfigLoadingImportsPoisonedEnvOnlyInExplicitLocalProfile(@TempDir Path directory) throws Exception {
    Files.writeString(directory.resolve(".env"), "GETLANCER_POISON_IMPORT=true\nDB_PASSWORD=sentinel-file-secret\nADMIN_EMAIL=sentinel-file-identity@example.test\n");
    String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    for (String profile : new String[] {"staging", "production", "local"}) {
      Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
          "-cp", classpath, getClass().getName(), profile).directory(directory.toFile()).redirectErrorStream(true).start();
      boolean exited = process.waitFor(20, TimeUnit.SECONDS);
      if (!exited) process.destroyForcibly();
      assertTrue(exited, "Config loading subprocess timed out");
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      assertEquals(0, process.exitValue(), "Config subprocess failed for " + profile + ": " + output);
      assertTrue(output.contains("CONFIG_IMPORT_OK"), profile);
      assertFalse(output.contains("sentinel-file-secret"));
    }
  }

  /** Subprocess changes working directory so optional .env imports can be tested without touching user files. */
  public static void main(String[] args) {
    String profile = args[0];
    StandardEnvironment environment = new StandardEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("injected-fixture", Map.ofEntries(
        Map.entry("spring.profiles.active", profile), Map.entry("APP_ENV", profile),
        Map.entry("DB_URL", "jdbc:postgresql://db.example.test/getlancer?sslmode=verify-full"),
        Map.entry("DB_USERNAME", "getlancer_runtime"), Map.entry("DB_PASSWORD", "injected-fixture-secret"),
        Map.entry("DB_SCHEMA", "getlancer"), Map.entry("APP_BASE_URL", "https://app.example.test"),
        Map.entry("BACKEND_URL", "https://api.example.test"))));
    ConfigDataEnvironmentPostProcessor.applyTo(environment);
    boolean imported = "true".equals(environment.getProperty("GETLANCER_POISON_IMPORT"));
    if (imported != profile.equals("local")
        || !"injected-fixture-secret".equals(environment.getProperty("spring.datasource.password")))
      throw new IllegalStateException("Profile import boundary failed");
    System.out.println("CONFIG_IMPORT_OK");
  }

  @Test void hostedRejectsMissingConfigurationAndUnsafeOverridesWithoutPrintingValues() {
    Map.ofEntries(
        Map.entry("app.backend-url", "http://api.example.test"), Map.entry("app.origin", "https://example.test@localhost"),
        Map.entry("app.storage.endpoint", "http://objects.example.test"), Map.entry("app.storage.upload-endpoint", "https://localhost"),
        Map.entry("spring.datasource.username", "postgres"), Map.entry("spring.flyway.enabled", "true"),
        Map.entry("spring.flyway.password", "sentinel-sensitive-value"), Map.entry("spring.datasource.hikari.schema", "public"),
        Map.entry("spring.config.import", "optional:file:.env[.properties]"), Map.entry("spring.config.additional-location", "file:/secrets/"),
        Map.entry("app.demo-mode", "true"), Map.entry("server.forward-headers-strategy", "framework"),
        Map.entry("app.secure-cookie", "false"), Map.entry("app.proxy-secret", "short"),
        Map.entry("spring.mail.properties.mail.smtp.starttls.required", "false"), Map.entry("spring.mail.properties.mail.smtp.ssl.checkserveridentity", "false"),
        Map.entry("app.mfa.keyring", ""), Map.entry("app.mfa.active-key-id", ""))
        .forEach((key, value) -> {
          MockEnvironment env = hosted().withProperty(key, value);
          IllegalStateException error = assertThrows(IllegalStateException.class, () -> new ProductionConfiguration(env).run(null), key);
          assertFalse(error.getMessage().contains("sentinel-sensitive-value"));
        });
    MockEnvironment mixed = hosted(); mixed.setActiveProfiles("local", "staging");
    assertThrows(IllegalStateException.class, () -> new ProductionConfiguration(mixed).run(null));
    MockEnvironment mismatch = hosted().withProperty("app.environment", "local");
    assertThrows(IllegalStateException.class, () -> new ProductionConfiguration(mismatch).run(null));
  }

  @Test void hostedOriginsRejectTrailingSlashAndOtherNoncanonicalSpellings() {
    for (String key : new String[] {"app.origin", "app.backend-url"})
      for (String value : new String[] {"https://app.example.test/", "https://app.example.test?", "https://APP.example.test", "https://app.example.test:443"})
        assertThrows(IllegalStateException.class, () -> new ProductionConfiguration(hosted().withProperty(key, value)).run(null), key);
  }

  @Test void databaseTlsCannotBeSpoofedBySubstringOrDuplicatedAndPrivilegedParameters() {
    for (String query : new String[] {"x=sslmode=verify-full", "sslmode=verify-full&host=localhost", "sslmode=verify-full&PGHOST=localhost", "sslmode=verify-full&currentSchema=public", "sslmode=verify-full&gssEncMode=prefer", "sslmode=verify-full&service=external", "sslmode=verify-full&authenticationPluginClassName=override", "SSLMODE=verify-full", "%73slmode=verify-full", "sslmode=verify-full&SSL=false", "sslmode=disable&sslmode=verify-full", "sslmode=verify-full&sslmode=disable", "sslmode=verify-full&ssl=false", "sslmode=verify-full&sslfactory=untrusted", "sslmode=verify-full&options=-c%20role%3Dpostgres", "sslmode=verify-full&user=postgres", "sslmode=verify-full#sslmode=verify-full"})
      assertThrows(IllegalStateException.class, () -> ProductionConfiguration.validateDatabaseTls("jdbc:postgresql://db.example.test/getlancer?" + query), query);
    assertThrows(IllegalStateException.class, () -> ProductionConfiguration.validateDatabaseTls("jdbc:postgresql://user:secret@db.example.test/getlancer?sslmode=verify-full"));
    assertDoesNotThrow(() -> ProductionConfiguration.validateDatabaseTls("jdbc:postgresql://db.example.test/getlancer?sslmode=verify-full"));
  }

  @Test void partialBootstrapAndUnapprovedProductionFailClosed() {
    assertThrows(IllegalStateException.class, () -> new ProductionConfiguration(hosted().withProperty("app.admin-password", "strong-synthetic-password")).run(null));
    MockEnvironment production = hosted().withProperty("app.environment", "production"); production.setActiveProfiles("production");
    assertThrows(IllegalStateException.class, () -> new ProductionConfiguration(production).run(null));
    production.withProperty("app.policies-approved", "true").withProperty("app.legal-version", "v1");
    for (String name : new String[] {"app.support-email", "app.privacy-email", "app.copyright-email"}) production.setProperty(name, "operator@example.test");
    assertDoesNotThrow(() -> new ProductionConfiguration(production).run(null));
  }
}
