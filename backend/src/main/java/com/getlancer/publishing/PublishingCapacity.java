package com.getlancer.publishing;

import com.getlancer.shared.ApiError;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PublishingCapacity {
  private final JdbcTemplate db;
  public PublishingCapacity(JdbcTemplate db) { this.db=db; }

  public static String pool(String value) {
    String name=Objects.toString(value,"").toUpperCase(Locale.ROOT);
    if(!Set.of("PROJECT","TEMPLATE","COMPONENT").contains(name))
      throw new ApiError(400,"VALIDATION_ERROR","Choose project, template or component publishing slots.");
    return name;
  }
  public void lock(UUID owner) {
    // Serialize capacity changes without taking an exclusive account lock ahead of commerce row locks.
    db.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"publishing:"+owner);
    db.queryForMap("SELECT id FROM users WHERE id=? FOR SHARE",owner);
  }
  int count(String sql,Object...args) { return db.queryForObject(sql,Integer.class,args); }
  int paid(UUID owner,String pool) {
    return count("SELECT count(*) FROM component_slot_purchases WHERE owner_id=? AND pool=? AND mode='live' AND status='CAPTURED' AND refunded_minor=0 AND (dispute_status IS NULL OR dispute_status='won')",owner,pool);
  }
  int earned(UUID owner) {
    var rows=db.queryForList("SELECT greatest(active_slot_limit-3,0) AS extra FROM showcase_entitlements WHERE user_id=?",owner);
    return rows.isEmpty()?0:((Number)rows.get(0).get("extra")).intValue();
  }
  int legacy(UUID owner) {
    return count("SELECT coalesce(sum(slots),0)::integer FROM publishing_capacity_grants WHERE owner_id=? AND pool='TEMPLATE'",owner);
  }
  public static int excess(int regular,int college) { return Math.max(regular-3,0)+Math.max(college-3,0); }
  public static boolean projectsFit(int regular,int college,int extra) { return excess(regular,college)<=extra; }
  public Map<String,Object> capacity(UUID owner,String requested) {
    String pool=pool(requested);int purchased=paid(owner,pool);
    var out=new LinkedHashMap<String,Object>();out.put("pool",pool);out.put("purchased",purchased);
    if(pool.equals("PROJECT")) {
      var counts=db.queryForMap("SELECT count(*) FILTER(WHERE e.product_id IS NULL)::integer AS regular,count(*) FILTER(WHERE e.product_id IS NOT NULL)::integer AS college FROM products p LEFT JOIN college_project_metadata e ON e.product_id=p.id WHERE p.owner_user_id=? AND p.lifecycle_status='ACTIVE'",owner);
      int regular=((Number)counts.get("regular")).intValue(),college=((Number)counts.get("college")).intValue(),earned=earned(owner),extra=earned+purchased;
      out.put("free",6);out.put("used",regular+college);out.put("limit",6+extra);out.put("earned",earned);
      out.put("regular",Map.of("free",3,"used",regular));out.put("college",Map.of("free",3,"used",college));
      out.put("extraUsed",excess(regular,college));out.put("extraLimit",extra);
      out.put("availableRegular",Math.max(3-regular,0)+Math.max(extra-excess(regular,college),0));
      out.put("availableCollege",Math.max(3-college,0)+Math.max(extra-excess(regular,college),0));
    } else {
      int grant=pool.equals("TEMPLATE")?legacy(owner):0;
      int used=pool.equals("TEMPLATE")?count("SELECT count(*) FROM source_templates WHERE seller_id=? AND status='ACTIVE'",owner):count("SELECT count(*) FROM component_entries WHERE owner_id=? AND status='ACTIVE'",owner);
      out.put("free",3);out.put("used",used);out.put("limit",3+purchased+grant);out.put("legacy",grant);
    }
    return out;
  }
  public Map<String,Object> all(UUID owner) {
    return Map.of("PROJECT",capacity(owner,"PROJECT"),"TEMPLATE",capacity(owner,"TEMPLATE"),"COMPONENT",capacity(owner,"COMPONENT"));
  }
  @SuppressWarnings("unchecked")
  public boolean projectFits(UUID owner,UUID product,boolean becomingCollege) {
    var cap=capacity(owner,"PROJECT");
    int regular=((Number)((Map<String,Object>)cap.get("regular")).get("used")).intValue();
    int college=((Number)((Map<String,Object>)cap.get("college")).get("used")).intValue();
    var row=db.queryForMap("SELECT p.lifecycle_status,EXISTS(SELECT 1 FROM college_project_metadata e WHERE e.product_id=p.id) AS college FROM products p WHERE p.id=? AND p.owner_user_id=?",product,owner);
    boolean active="ACTIVE".equals(row.get("lifecycle_status")),wasCollege=Boolean.TRUE.equals(row.get("college"));
    if(becomingCollege&&active&&!wasCollege){regular--;college++;}
    else if(!active){if(becomingCollege||wasCollege)college++;else regular++;}
    return projectsFit(regular,college,((Number)cap.get("extraLimit")).intValue());
  }
  public void requireCollegeMove(UUID owner,UUID product) {
    // Draft context does not reserve active capacity; activation performs the quota check.
    var row=db.queryForMap("SELECT lifecycle_status FROM products WHERE id=? AND owner_user_id=?",product,owner);
    if(!"ACTIVE".equals(row.get("lifecycle_status")))return;
    if(!projectFits(owner,product,true))throw new ApiError(409,"SLOT_LIMIT_REACHED","All three free college project slots and shared extra project slots are in use. Archive a college project or buy another project slot.");
  }
  public void requireTemplate(UUID owner,UUID template) {
    var row=db.queryForMap("SELECT status FROM source_templates WHERE id=? AND seller_id=?",template,owner);
    if("ACTIVE".equals(row.get("status")))return;
    var cap=capacity(owner,"TEMPLATE");
    if(((Number)cap.get("used")).intValue()>=((Number)cap.get("limit")).intValue())
      throw new ApiError(409,"TEMPLATE_SLOT_LIMIT_REACHED","All three free template slots and extra template slots are in use. Archive a template or buy another template slot.");
  }
  void audit(UUID owner,UUID target,String detail) {
    db.update("INSERT INTO component_audit(id,actor_id,target_id,kind,detail) VALUES(?,?,?,'CAPACITY_ARCHIVED',?)",UUID.randomUUID(),owner,target,detail);
  }
  @SuppressWarnings("unchecked")
  public void trim(UUID owner,String pool) {
    var cap=capacity(owner,pool);
    if(pool.equals("PROJECT")) {
      int regular=((Number)((Map<String,Object>)cap.get("regular")).get("used")).intValue(),college=((Number)((Map<String,Object>)cap.get("college")).get("used")).intValue(),extra=((Number)cap.get("extraLimit")).intValue();
      var rows=db.queryForList("SELECT p.id,EXISTS(SELECT 1 FROM college_project_metadata e WHERE e.product_id=p.id) AS college FROM products p WHERE p.owner_user_id=? AND p.lifecycle_status='ACTIVE' ORDER BY p.updated_at DESC,p.id DESC",owner);
      for(var row:rows) {
        if(projectsFit(regular,college,extra))break;
        boolean isCollege=Boolean.TRUE.equals(row.get("college"));
        if(isCollege?college<=3:regular<=3)continue;
        db.update("UPDATE products SET lifecycle_status='ARCHIVED',updated_at=now() WHERE id=?",row.get("id"));
        if(isCollege)college--;else regular--;
        db.update("INSERT INTO analytics_events(id,event_name,entity_id,context) VALUES(?,'product_archived',?,jsonb_build_object('builderId',?::text,'activeCount',?::integer))",UUID.randomUUID(),row.get("id"),owner.toString(),regular+college);
        audit(owner,(UUID)row.get("id"),"Shared project capacity reduced after refund or dispute. Existing source purchases remain pinned.");
      }
    } else {
      int limit=((Number)cap.get("limit")).intValue();
      var rows=pool.equals("TEMPLATE")?db.queryForList("SELECT id FROM source_templates WHERE seller_id=? AND status='ACTIVE' ORDER BY created_at,id OFFSET ?",owner,limit):db.queryForList("SELECT id FROM component_entries WHERE owner_id=? AND status='ACTIVE' ORDER BY published_at,id OFFSET ?",owner,limit);
      for(var row:rows) {
        if(pool.equals("TEMPLATE"))db.update("UPDATE source_templates SET status='ARCHIVED',updated_at=now() WHERE id=?",row.get("id"));
        else db.update("UPDATE component_entries SET status='ARCHIVED',revision=revision+1,updated_at=now() WHERE id=?",row.get("id"));
        audit(owner,(UUID)row.get("id"),pool+" capacity reduced after refund or dispute. Published source rights remain preserved.");
      }
    }
  }
}
