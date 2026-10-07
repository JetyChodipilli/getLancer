package com.getlancer.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.getlancer.Application;
import com.getlancer.auth.AuthRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class RequestValidationTest {
  private List<Class<?>> controllers() throws Exception {
    Path root = Path.of(Application.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    var result = new ArrayList<Class<?>>();
    try (var files = Files.walk(root.resolve("com/getlancer"))) {
      for (Path file : files.filter(p -> p.toString().endsWith("Controller.class")).toList()) {
        String name = root.relativize(file).toString().replace('/', '.').replace('\\', '.')
            .replaceAll("\\.class$", "");
        Class<?> type = Class.forName(name, false, Application.class.getClassLoader());
        if (type.isAnnotationPresent(RestController.class)) result.add(type);
      }
    }
    assertTrue(result.size() >= 31, "Inspect the complete production controller inventory.");
    return result;
  }

  @Test
  void everyControllerHasTheCentralDeclarativeRoutePolicy() throws Exception {
    for (Class<?> type : controllers()) {
      var policy = AnnotatedElementUtils.findMergedAnnotation(type, PreAuthorize.class);
      assertTrue(policy != null, type.getName());
      assertEquals("@authorization.routeAllowed(authentication)", policy.value(), type.getName());
    }
  }

  @Test
  void everyJsonBodyIsValidatedAndHasConcreteDomainFields() throws Exception {
    int bodies = 0;
    for (Class<?> controller : controllers()) {
      for (var method : controller.getDeclaredMethods()) {
        for (var parameter : method.getParameters()) {
          if (!parameter.isAnnotationPresent(RequestBody.class)) continue;
          bodies++;
          String label = controller.getSimpleName() + "." + method.getName();
          assertTrue(parameter.isAnnotationPresent(Valid.class), label);
          assertFalse(Map.class.isAssignableFrom(parameter.getType()), label);
          assertTrue(parameter.getType().getPackageName().equals("com.getlancer.dto")
              || parameter.getType().getName().startsWith("com.getlancer.auth.AuthRequests$"), label);
          assertConcrete(parameter.getParameterizedType(), new HashSet<>());
        }
      }
    }
    assertTrue(bodies >= 108, "Guard the complete JSON mutation inventory, including authentication.");
  }

  private void assertConcrete(Type type, Set<Type> seen) {
    if (!seen.add(type)) return;
    if (type instanceof ParameterizedType generic) {
      assertFalse(generic.getRawType().equals(Map.class), type.toString());
      for (Type argument : generic.getActualTypeArguments()) assertConcrete(argument, seen);
      return;
    }
    assertTrue(type instanceof Class<?>, type.toString());
    Class<?> concrete = (Class<?>) type;
    assertFalse(concrete.equals(Object.class) || Map.class.isAssignableFrom(concrete)
        || concrete.getName().startsWith("com.fasterxml.jackson.databind."), concrete.getName());
    if (!concrete.getPackageName().startsWith("com.getlancer.")) return;
    for (var field : concrete.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers())) continue;
      assertFalse(Map.class.isAssignableFrom(field.getType()) || field.getType().equals(Object.class),
          concrete.getName() + "." + field.getName());
      assertFalse(field.isAnnotationPresent(JsonAnySetter.class), field.toString());
      if (field.getType().equals(String.class)) {
        assertTrue(field.isAnnotationPresent(Size.class) || field.isAnnotationPresent(Pattern.class)
                || field.isAnnotationPresent(AuthRequests.Password.class),
            "Request strings need length bounds or a finite allowlist: " + field);
      }
    }
    for (var method : concrete.getDeclaredMethods()) {
      assertFalse(method.isAnnotationPresent(JsonAnySetter.class), concrete.getName());
    }
    if (concrete.isRecord()) {
      assertTrue(concrete.getRecordComponents().length > 0, concrete.getName());
      for (var field : concrete.getRecordComponents()) assertConcrete(field.getGenericType(), seen);
    }
  }

  @Test
  void signedWebhookHandlersKeepRawBytesOutsideJsonBinding() throws Exception {
    int webhooks = 0;
    for (Class<?> controller : controllers()) {
      for (var method : controller.getDeclaredMethods()) {
        var route = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (route == null || List.of(route.path()).stream().noneMatch(p -> p.endsWith("/webhook"))) continue;
        webhooks++;
        assertTrue(List.of(method.getParameterTypes()).contains(HttpServletRequest.class), method.toString());
        for (var parameter : method.getParameters()) {
          assertFalse(parameter.isAnnotationPresent(RequestBody.class), method.toString());
        }
      }
    }
    assertEquals(4, webhooks);
  }
}
