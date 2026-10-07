package com.getlancer.shared;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

public final class Pages {
  public static int number(HttpServletRequest r, String key, int fallback, int max) {
    try {
      String raw = r.getParameter(key);
      int n = raw == null ? fallback : Integer.parseInt(raw);
      if (n < 0 || n > max || (key.equals("size") && n == 0)) throw new NumberFormatException();
      return n;
    } catch (NumberFormatException e) {
      throw new ApiError(400, "VALIDATION_ERROR", "Invalid pagination.");
    }
  }

  public static Map<String, Object> query(
      JdbcTemplate db, HttpServletRequest r, String sql, Object... args) {
    int page = number(r, "page", 0, 100000), size = number(r, "size", 50, 100);
    List<Object> values = new ArrayList<>(Arrays.asList(args));
    values.add(size + 1);
    values.add(page * size);
    long total =
        db.queryForObject("SELECT count(*) FROM (" + sql + ") page_count", Long.class, args);
    var rows = db.queryForList(sql + " LIMIT ? OFFSET ?", values.toArray());
    boolean more = rows.size() > size;
    return new LinkedHashMap<>(
        Map.of(
            "items",
            new ArrayList<>(rows.subList(0, Math.min(size, rows.size()))),
            "page",
            page,
            "size",
            size,
            "hasMore",
            more,
            "totalItems",
            total,
            "totalPages",
            (total + size - 1) / size));
  }

  @SuppressWarnings("unchecked")
  public static List<Map<String, Object>> items(Map<String, Object> page) {
    return (List<Map<String, Object>>) page.get("items");
  }
}
