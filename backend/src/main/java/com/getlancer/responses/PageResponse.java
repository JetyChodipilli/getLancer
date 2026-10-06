package com.getlancer.responses;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Fixed pagination contract with concretely typed items. */
public record PageResponse<T>(List<T> items, int page, int size, boolean hasMore, long totalItems, long totalPages) {
  public static <T> PageResponse<T> from(Map<String, Object> page, Function<Map<String, Object>, T> projection) {
    return new PageResponse<>(ResponseRows.rows(page, "items", projection),
        ResponseRows.integer32(page, "page"), ResponseRows.integer32(page, "size"),
        ResponseRows.bool(page, "hasMore"), ResponseRows.integer64(page, "totalItems"),
        ResponseRows.integer64(page, "totalPages"));
  }
}
