package com.getlancer.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import com.getlancer.hosting.HostingConfiguration;
import com.getlancer.security.AuthorizationService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class SecurityArchitectureTest {
  @Test void everyPublishedApplicationRouteHasOneExplicitPolicyIncludingBareMappings() throws Exception {
    var authorization = new AuthorizationService(mock(HostingConfiguration.class));
    var actual = new TreeSet<String>();
    for (var route : authorization.routes()) {
      if (!route.path().startsWith("/api/v1/")) continue;
      boolean added = actual.add(route.method() + " " + route.path());
      assertFalse(!added, "Duplicate authorization policy for " + route.path());
    }
    try (var input = getClass().getResourceAsStream("/api-route-contract.txt")) {
      assertNotNull(input);
      var expected = new TreeSet<>(new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().toList());
      assertEquals(expected, actual);
      assertFalse(!actual.contains("GET /api/v1/me/inquiries"));
    }
  }

  @Test void productionSourceCannotReintroduceWildcardImportsRawSqlProjectionsOrFieldInjection() throws Exception {
    try (var files = Files.walk(Path.of("src/main/java/com/getlancer"))) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        String source = Files.readString(file);
        assertFalse(source.matches("(?s).*import\\s+(?:static\\s+)?[^;]+\\.\\*;.*"), file.toString());
        assertFalse(source.matches("(?is).*SELECT\\s+(?:[a-z][a-z0-9_]*\\.)?\\*.*"), file.toString());
        assertFalse(source.matches("(?s).*@(Autowired|Value\\([^)]*\\))\\s+(?:(?:private|public|protected)\\s+)?(?:final\\s+)?[\\w<>]+\\s+\\w+\\s*(?:=[^;]*)?;.*"), file.toString());
        assertFalse(source.contains("@org.springframework."), file.toString());
      }
    }
  }
}
