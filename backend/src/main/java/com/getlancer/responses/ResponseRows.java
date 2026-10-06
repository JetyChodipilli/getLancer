package com.getlancer.responses;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Typed value conversion for explicit response projections; never copies arbitrary row keys. */
final class ResponseRows {
  private ResponseRows() {}

  static String string(Map<String, Object> row, String key) { return (String) row.get(key); }
  static UUID uuid(Map<String, Object> row, String key) { return (UUID) row.get(key); }
  static Boolean bool(Map<String, Object> row, String key) { return (Boolean) row.get(key); }
  static Timestamp timestamp(Map<String, Object> row, String key) { return (Timestamp) row.get(key); }
  static Date date(Map<String, Object> row, String key) { return (Date) row.get(key); }
  static Long integer64(Map<String, Object> row, String key) {
    Number value = (Number) row.get(key); return value == null ? null : value.longValue();
  }
  static Integer integer32(Map<String, Object> row, String key) {
    Number value = (Number) row.get(key); return value == null ? null : value.intValue();
  }
  static BigDecimal decimal(Map<String, Object> row, String key) { return (BigDecimal) row.get(key); }
  static JsonNode json(Map<String, Object> row, String key, ObjectMapper mapper) {
    return mapper.valueToTree(row.get(key));
  }
  @SuppressWarnings("unchecked")
  static Map<String, Object> row(Map<String, Object> source, String key) {
    return (Map<String, Object>) source.get(key);
  }
  @SuppressWarnings("unchecked")
  static <T> List<T> rows(Map<String, Object> source, String key, Function<Map<String, Object>, T> projection) {
    return ((List<Map<String, Object>>) source.get(key)).stream().map(projection).toList();
  }
}
