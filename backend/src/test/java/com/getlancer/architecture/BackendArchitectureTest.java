package com.getlancer.architecture;

import static org.junit.jupiter.api.Assertions.*;

import com.getlancer.Application;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

class BackendArchitectureTest {
  private List<Class<?>> productionClasses() throws Exception {
    Path root =
        Path.of(Application.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    try (var files = Files.walk(root.resolve("com/getlancer"))) {
      var classes = new ArrayList<Class<?>>();
      for (Path file :
          files
              .filter(
                  p -> p.toString().endsWith(".class") && !p.getFileName().toString().contains("$"))
              .toList()) {
        String name =
            root.relativize(file)
                .toString()
                .replace('/', '.')
                .replace('\\', '.')
                .replaceAll("\\.class$", "");
        classes.add(Class.forName(name, false, Application.class.getClassLoader()));
      }
      return classes;
    }
  }

  @Test
  void featureClassesDoNotAccumulateInTheRootPackage() throws Exception {
    assertEquals(
        List.of(Application.class),
        productionClasses().stream()
            .filter(c -> c.getPackageName().equals("com.getlancer"))
            .toList());
  }

  @Test
  void controllersDependOnServicesInsteadOfPersistenceOrOtherControllers() throws Exception {
    for (Class<?> type : productionClasses()) {
      if (!type.isAnnotationPresent(RestController.class)) continue;
      var fields =
          Arrays.stream(type.getDeclaredFields())
              .filter(f -> !Modifier.isStatic(f.getModifiers()))
              .toList();
      assertEquals(1, fields.size(), type.getName());
      for (var field : fields) {
        assertFalse(JdbcTemplate.class.isAssignableFrom(field.getType()), type.getName());
        assertFalse(field.getType().isAnnotationPresent(RestController.class), type.getName());
        assertTrue(field.getType().isAnnotationPresent(Service.class), type.getName());
      }
    }
  }

  @Test
  void applicationServicesDoNotRegisterHttpEndpoints() throws Exception {
    for (Class<?> type : productionClasses()) {
      if (!type.isAnnotationPresent(Service.class)) continue;
      assertNull(
          AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class), type.getName());
      for (var method : type.getDeclaredMethods()) {
        assertNull(
            AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class),
            type.getName() + "." + method.getName());
      }
    }
  }

  @Test
  void refactorPreservesThePublishedHttpRouteContract() throws Exception {
    var actual = new TreeSet<String>();
    for (Class<?> type : productionClasses()) {
      if (!type.isAnnotationPresent(RestController.class)) continue;
      var prefix = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
      String[] roots =
          prefix == null || prefix.path().length == 0 ? new String[] {""} : prefix.path();
      for (var method : type.getDeclaredMethods()) {
        var route = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (route == null) continue;
        for (String root : roots)
          for (String path : route.path().length == 0 ? new String[] {""} : route.path())
            for (var verb : route.method()) actual.add(verb.name() + " " + root + path);
      }
    }
    try (var input =
        Objects.requireNonNull(getClass().getResourceAsStream("/api-route-contract.txt"))) {
      var expected =
          new TreeSet<>(
              new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                  .lines()
                  .filter(s -> !s.isBlank())
                  .toList());
      assertEquals(expected, actual);
    }
  }
}
