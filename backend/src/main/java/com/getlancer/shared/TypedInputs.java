package com.getlancer.shared;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Map;

/** Adapter for validated, concrete request DTOs while domain rules are migrated incrementally. */
public final class TypedInputs {
  private static final ObjectMapper MAPPER = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .setSerializationInclusion(JsonInclude.Include.NON_NULL);
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private TypedInputs() {}

  public static Map<String, Object> map(Object input) {
    return input == null ? Map.of() : MAPPER.convertValue(input, MAP);
  }
}
