package com.getlancer.components;

import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComponentBookmarkService {
  private final JdbcTemplate db;
  private final Security security;
  private final ComponentService components;
  public ComponentBookmarkService(JdbcTemplate db, Security security, ComponentService components) {
    this.db=db; this.security=security; this.components=components;
  }
  public Object list(HttpServletRequest request) {
    var owner=security.user(request);
    var items=db.queryForList("SELECT slug FROM saved_components WHERE user_id=? ORDER BY created_at DESC,slug LIMIT 200",owner).stream().map(row -> {
      String slug=(String)row.get("slug");
      try {
        var item=new LinkedHashMap<>(ComponentService.card(components.detail(slug)));
        item.put("available",true); return item;
      } catch(ApiError error) {
        if(error.status!=404)throw error;
        return Map.<String,Object>of("slug",slug,"available",false);
      }
    }).toList();
    return Map.of("items",items);
  }
  @Transactional
  public Object save(String slug,HttpServletRequest request) {
    var owner=security.user(request);
    // Serialize this actor's bounded saved list, including parallel idempotent saves.
    db.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",java.util.UUID.class,owner);
    security.user(request);
    components.detail(slug);
    if(db.queryForObject("SELECT count(*) FROM saved_components WHERE user_id=?",Integer.class,owner)>=200 &&
        db.queryForObject("SELECT count(*) FROM saved_components WHERE user_id=? AND slug=?",Integer.class,owner,slug)==0)
      throw new ApiError(409,"SAVED_LIMIT","Remove a saved component before adding more than 200.");
    db.update("INSERT INTO saved_components(user_id,slug) VALUES(?,?) ON CONFLICT DO NOTHING",owner,slug);
    return Map.of("saved",true);
  }
  public Object remove(String slug,HttpServletRequest request) {
    var owner=security.user(request);
    db.update("DELETE FROM saved_components WHERE user_id=? AND slug=?",owner,slug);
    return Map.of("saved",false);
  }
}
