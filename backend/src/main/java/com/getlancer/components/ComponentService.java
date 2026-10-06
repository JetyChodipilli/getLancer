package com.getlancer.components;

import static com.getlancer.shared.Support.id;
import static com.getlancer.shared.Support.text;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.products.ProductRepository;
import com.getlancer.products.ProductService;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComponentService {
  final JdbcTemplate db;
  final ObjectMapper json;
  final Security security;
  final ComponentSlotService slots;
  final com.getlancer.publishing.PublishingCapacity publishing;
  final ProductService products;
  final List<Map<String,Object>> recipes;
  static final Set<String> CATEGORIES=Set.of("NAVBAR","SIDEBAR","FORM","CARD","AUTH","DASHBOARD");
  static final Set<String> COLLEGE=Set.of("FULL_STACK","DATA_ANALYTICS","AI_ML","IOT");
  static final String JOIN=" FROM component_entries c JOIN users u ON u.id=c.owner_id JOIN developer_profiles d ON d.user_id=u.id";
  static final String ELIGIBLE="u.account_status='ACTIVE' AND u.email_verified_at IS NOT NULL AND d.approval_status='APPROVED'";
  public ComponentService(JdbcTemplate db,Security security,ComponentSlotService slots,ProductService products,ObjectMapper json,com.getlancer.publishing.PublishingCapacity publishing) throws java.io.IOException {
    this.db=db;
    this.json=json;
    this.security=security;
    this.slots=slots;
    this.publishing=publishing;
    this.products=products;
    try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/catalog/components.json"))) {
      recipes=json.readValue(input,new TypeReference<List<Map<String,Object>>>() {
      }
       ).stream().map(ComponentService::sourceProjection).toList();
      if(recipes.size()>100)throw new IllegalStateException("Catalogue contains too many recipes");
    }
  }
  Map<String,Object> recipe(String slug) {
    return recipes.stream().filter(x->slug.equals(x.get("slug"))).findFirst().orElseThrow(()->new ApiError(404,"NOT_FOUND","Component not found."));
  }
  Map<String,Object> entry(Map<String,Object> c) {
    var out=new LinkedHashMap<>(c.get("published_source")==null?recipe(Objects.toString(c.get("recipe_slug"))):sourceProjection(snapshot(c.get("published_source"))));
    out.put("published",c.get("published_at")!=null);
    out.put("revision",c.get("revision"));
    out.put("id",c.get("id"));
    out.put("recipeSlug",c.get("recipe_slug"));
    out.put("slug",c.get("slug"));
    out.put("title",c.get("title"));
    out.put("summary",c.get("summary"));
    out.put("contribution",c.get("contribution"));
    out.put("creator",c.get("creator"));
    out.put("builderSlug",c.get("builder_slug"));
    out.put("status",c.get("status"));
    out.put("reviewReason",c.get("review_reason"));
    return out;
  }
  /** Catalogue metadata is a deliberate dynamic exception; pin metadata keys and bound source files. */
  static Map<String,Object> sourceProjection(Map<String,Object> source) {
    var projected=new LinkedHashMap<String,Object>();
    for(String key:List.of("slug","category","title","summary","version","framework","kind","executionMode","creator","license","scenario","sha256"))
      if(source.containsKey(key)) {
        if(!(source.get(key) instanceof String value)||value.length()>20000)
          throw new IllegalStateException("Invalid catalogue metadata");
        projected.put(key,value);
      }
    if(source.containsKey("files")) {
      if(!(source.get("files") instanceof Map<?,?> files)||files.size()>100)
        throw new IllegalStateException("Invalid catalogue source files");
      var output=new LinkedHashMap<String,String>();
      long characters=0;
      for(var file:files.entrySet()) {
        if(!(file.getKey() instanceof String path)||path.length()>256||!(file.getValue() instanceof String content)||content.length()>1048576)
          throw new IllegalStateException("Invalid catalogue source file");
        characters+=content.length();
        if(characters>5242880)throw new IllegalStateException("Catalogue source exceeds the supported size");
        output.put(path,content);
      }
      projected.put("files",output);
    }
    return projected;
  }
  Map<String,Object> snapshot(Object value){
    try{
      return json.readValue(Objects.toString(value),new TypeReference<Map<String,Object>>(){
      }
      );
    }
    catch(java.io.IOException ex){
      throw new IllegalStateException("Invalid published component snapshot",ex);
    }
  }
  String encode(Object value){
    try{
      return json.writeValueAsString(value);
    }
    catch(java.io.IOException ex){
      throw new IllegalStateException("Cannot snapshot component",ex);
    }
  }
  Map<String,Object> publicEntry(Map<String,Object> row){
    var out=entry(row);
    if(!"ACTIVE".equals(row.get("status"))){
      var context=snapshot(row.get("published_context"));
      for(String key:List.of("title","summary","contribution"))if(context.containsKey(key))out.put(key,context.get(key));
    }
    out.remove("reviewReason");
    return out;
  }
  static Map<String,Object> card(Map<String,Object> item) {
    var out=new LinkedHashMap<>(item);
    out.remove("files");
    return out;
  }
  public Map<String,Object> catalog(String q,String category,String kind,String builder,int page) {
    if(q.length()>200||page<0||page>10000||!category.isBlank()&&!CATEGORIES.contains(category)||!Set.of("FRONTEND","BACKEND").contains(kind)) throw new ApiError(400,"VALIDATION_ERROR","Choose valid component filters.");
    if(kind.equals("BACKEND"))return Map.of("items",List.of(),"totalItems",0,"hasMore",false,"page",page,"executionMode","Source only");
    var all=new ArrayList<Map<String,Object>>();
    if(builder.isBlank())all.addAll(recipes);
    // ponytail: bounded recipe catalogue plus paginated database remixes; no separate search infrastructure.
    String sql="SELECT c.id,c.recipe_slug,c.slug,c.title,c.summary,c.contribution,c.revision,c.status,c.review_reason,c.published_at,c.published_source,c.published_context,d.display_name AS creator,d.slug AS builder_slug"+JOIN+" WHERE c.status='ACTIVE' AND "+ELIGIBLE;
    var args=new ArrayList<Object>();
    if(!builder.isBlank()){
      sql+=" AND d.slug=?";
      args.add(builder);
    }
    if(!q.isBlank()){
      sql+=" AND (c.title ILIKE ? OR c.summary ILIKE ?)";
      String term="%"+q.replace("%","\\%").replace("_","\\_")+"%";
      args.add(term);
      args.add(term);
    }
    if(!category.isBlank()){
      var slugs=recipes.stream().filter(x->category.equals(x.get("category"))).map(x->x.get("slug")).toList();
      sql+=" AND c.recipe_slug IN ("+String.join(",",Collections.nCopies(slugs.size(),"?"))+")";
      args.addAll(slugs);
    }
    long count=db.queryForObject("SELECT count(*) FROM ("+sql+") matches",Long.class,args.toArray());
    all.removeIf(x->!matches(x,q,category));
    int seedCount=all.size(),offset=page*12;
    var result=new ArrayList<Map<String,Object>>();
    if(offset<seedCount)result.addAll(all.subList(offset,Math.min(seedCount,offset+12)));
    int take=12-result.size();
    if(take>0){
      args.add(take);
      args.add(Math.max(0,offset-seedCount));
      result.addAll(db.queryForList(sql+" ORDER BY c.created_at DESC,c.id LIMIT ? OFFSET ?",args.toArray()).stream().map(this::publicEntry).toList());
    }
    return Map.of("items",result.stream().map(ComponentService::card).toList(),"totalItems",seedCount+count,"hasMore",offset+12<seedCount+count,"page",page);
  }
  static boolean matches(Map<String,Object> x,String q,String category) {
    return (category.isBlank()||category.equals(x.get("category")))&&(q.isBlank()||(x.get("title")+" "+x.get("summary")).toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT)));
  }
  public Map<String,Object> detail(String slug) {
    var seed=recipes.stream().filter(x->slug.equals(x.get("slug"))).findFirst();
    if(seed.isPresent())return seed.get();
    var rows=db.queryForList("SELECT c.id,c.recipe_slug,c.slug,c.title,c.summary,c.contribution,c.revision,c.status,c.review_reason,c.published_at,c.published_source,c.published_context,d.display_name AS creator,d.slug AS builder_slug"+JOIN+" WHERE c.slug=? AND c.published_at IS NOT NULL AND c.status IN ('ACTIVE','ARCHIVED','DRAFT','PENDING') AND "+ELIGIBLE,slug);
    if(rows.isEmpty())throw new ApiError(404,"NOT_FOUND","Component not found.");
    return publicEntry(rows.get(0));
  }
  public Map<String,Object> own(HttpServletRequest r) {
    UUID owner=security.developer(r,false);
    return Map.of("items",db.queryForList("SELECT c.id,c.recipe_slug,c.slug,c.title,c.summary,c.contribution,c.revision,c.status,c.review_reason,c.published_at,c.published_source,c.published_context,d.display_name AS creator,d.slug AS builder_slug"+JOIN+" WHERE c.owner_id=? ORDER BY c.created_at DESC LIMIT 100",owner).stream().map(this::entry).map(ComponentService::card).toList(),"capacity",slots.capacity(owner),"pricing",slots.pricing(),"recipes",recipes.stream().map(ComponentService::card).toList());
  }
  @Transactional public Object create(Map<String,Object> b,HttpServletRequest r) {
    UUID owner=security.developer(r,true);
    slots.lock(owner);
    security.developer(r,true);
    if(db.queryForObject("SELECT count(*) FROM component_entries WHERE owner_id=?",Integer.class,owner)>=100)throw new ApiError(409,"DRAFT_LIMIT","Manage your existing 100 component records before adding more.");
    String recipe=text(b,"recipeSlug",3,100);
    recipe(recipe);
    if(!Boolean.TRUE.equals(b.get("rightsConsent")))throw new ApiError(400,"RIGHTS_CONSENT_REQUIRED","Confirm the MIT licence and your attribution.");
    UUID id=id();
    String title=text(b,"title",3,100),summary=text(b,"summary",10,240),contribution=text(b,"contribution",20,2000);
    db.update("INSERT INTO component_entries(id,owner_id,recipe_slug,slug,title,summary,contribution) VALUES(?,?,?,?,?,?,?)",id,owner,recipe,"remix-"+id,title,summary,contribution);
    slots.audit(owner,id,"DRAFT_CREATED","Curated recipe remix with explicit attribution and rights consent.");
    return Map.of("id",id);
  }
  @Transactional public Object action(UUID id,String action,HttpServletRequest r) {
    UUID owner=security.developer(r,true);
    slots.lock(owner);
    security.developer(r,true);
    var rows=db.queryForList("SELECT id,owner_id,recipe_slug,slug,title,summary,contribution,revision,status,review_reason,published_at,published_source,published_context,created_at,updated_at FROM component_entries WHERE id=? AND owner_id=? FOR UPDATE",id,owner);
    if(rows.isEmpty())throw new ApiError(404,"NOT_FOUND","Component not found.");
    var c=rows.get(0);
    String next;
    if(action.equals("submit")&&Set.of("DRAFT","ARCHIVED").contains(c.get("status")))next="PENDING";
    else if(action.equals("archive")&&!"SUSPENDED".equals(c.get("status")))next="ARCHIVED";
    else throw new ApiError(409,"INVALID_COMPONENT_STATE","This component cannot take that action.");
    db.update("UPDATE component_entries SET status=?,revision=revision+1,updated_at=now() WHERE id=?",next,id);
    slots.audit(owner,id,"COMPONENT_"+next,"Creator changed publication state.");
    return Map.of("status",next);
  }
  @Transactional public Object edit(UUID id,Map<String,Object>b,HttpServletRequest r){
    UUID owner=security.developer(r,true);
    slots.lock(owner);
    security.developer(r,true);
    var rows=db.queryForList("SELECT id,owner_id,recipe_slug,slug,title,summary,contribution,revision,status,review_reason,published_at,published_source,published_context,created_at,updated_at FROM component_entries WHERE id=? AND owner_id=? FOR UPDATE",id,owner);
    if(rows.isEmpty())throw new ApiError(404,"NOT_FOUND","Component not found.");
    var c=rows.get(0);
    if(!Set.of("DRAFT","ARCHIVED").contains(c.get("status")))throw new ApiError(409,"INVALID_COMPONENT_STATE","Archive an active component before editing its context.");
    String recipe=text(b,"recipeSlug",3,100);
    recipe(recipe);
    if(c.get("published_at")!=null&&!recipe.equals(c.get("recipe_slug")))throw new ApiError(409,"PUBLISHED_SOURCE_IMMUTABLE","A published recipe stays pinned. Create a separate component to use another source.");
    if(!Boolean.TRUE.equals(b.get("rightsConsent")))throw new ApiError(400,"RIGHTS_CONSENT_REQUIRED","Confirm the original licence and contribution.");
    db.update("UPDATE component_entries SET recipe_slug=?,title=?,summary=?,contribution=?,status='DRAFT',revision=revision+1,updated_at=now() WHERE id=?",recipe,text(b,"title",3,100),text(b,"summary",10,240),text(b,"contribution",20,2000),id);
    slots.audit(owner,id,"DRAFT_EDITED","Curated recipe context edited; fresh publication review required.");
    return Map.of("id",id);
  }
  @Transactional public Object review(UUID id,Map<String,Object> b,HttpServletRequest r) {
    UUID admin=security.admin(r);
    var rows=db.queryForList("SELECT owner_id FROM component_entries WHERE id=?",id);
    if(rows.isEmpty())throw new ApiError(404,"NOT_FOUND","Component not found.");
    UUID owner=(UUID)rows.get(0).get("owner_id");
    slots.lock(owner);
    var c=db.queryForMap("SELECT id,owner_id,recipe_slug,slug,title,summary,contribution,revision,status,review_reason,published_at,published_source,published_context,created_at,updated_at FROM component_entries WHERE id=? FOR UPDATE",id);
    security.admin(r);
    checkRevision(b,c);
    String decision=text(b,"decision",3,30),reason=text(b,"reason",20,2000),next;
    if(decision.equals("APPROVE")&&Set.of("PENDING","SUSPENDED").contains(c.get("status"))){
      if(db.queryForObject("SELECT count(*) FROM users u JOIN developer_profiles d ON d.user_id=u.id WHERE u.id=? AND "+ELIGIBLE,Integer.class,owner)==0)throw new ApiError(409,"BUILDER_NOT_ELIGIBLE","The builder must have an active account and approved profile.");
      var capacity=slots.capacity(owner);
      if(((Number)capacity.get("used")).intValue()>=((Number)capacity.get("limit")).intValue())throw new ApiError(409,"COMPONENT_CAPACITY_REACHED","Archive a component or purchase another publishing slot before approval.");
      next="ACTIVE";
    }
    else if(decision.equals("CHANGES_REQUESTED")&&"PENDING".equals(c.get("status")))next="DRAFT";
    else if(decision.equals("SUSPEND")&&Set.of("ACTIVE","PENDING","ARCHIVED").contains(c.get("status")))next="SUSPENDED";
    else throw new ApiError(409,"INVALID_COMPONENT_STATE","Choose a valid review decision.");
    db.update("UPDATE component_entries SET status=?,revision=revision+1,review_reason=?,published_at=CASE WHEN ?='ACTIVE' THEN coalesce(published_at,now()) ELSE published_at END,published_source=CASE WHEN ?='ACTIVE' THEN coalesce(published_source,?::jsonb) ELSE published_source END,published_context=CASE WHEN ?='ACTIVE' THEN ?::jsonb ELSE published_context END,updated_at=now() WHERE id=?",next,reason,next,next,encode(recipe(Objects.toString(c.get("recipe_slug")))),next,encode(Map.of("title",c.get("title"),"summary",c.get("summary"),"contribution",c.get("contribution"))),id);
    slots.audit(admin,id,"REVIEW_"+decision,reason);
    return Map.of("status",next);
  }
  static void checkRevision(Map<String,Object>b,Map<String,Object>row){
    Object value=b.get("revision");
    if(!(value instanceof Integer||value instanceof Long)||((Number)value).longValue()!=((Number)row.get("revision")).longValue())throw new ApiError(409,"REVIEW_CONTENT_CHANGED","This content changed after you loaded it. Reload and review the current revision.");
  }
  public Object adminDetail(UUID id,HttpServletRequest r){security.admin(r);var rows=db.queryForList("SELECT c.id,c.recipe_slug,c.slug,c.title,c.summary,c.contribution,c.revision,c.status,c.review_reason,c.published_at,c.published_source,c.published_context,d.display_name AS creator,d.slug AS builder_slug"+JOIN+" WHERE c.id=?",id);if(rows.isEmpty())throw new ApiError(404,"NOT_FOUND","Component not found.");return entry(rows.get(0));}
  public Object queue(HttpServletRequest r) {
    security.admin(r);
    int cp=com.getlancer.shared.Pages.number(r,"componentPage",0,100000),ep=com.getlancer.shared.Pages.number(r,"collegePage",0,100000);
    var components=db.queryForList("SELECT c.id,c.recipe_slug,c.slug,c.title,c.summary,c.contribution,c.revision,c.status,c.review_reason,c.published_at,c.published_source,c.published_context,d.display_name AS creator,d.slug AS builder_slug"+JOIN+" WHERE c.status IN ('PENDING','ACTIVE','SUSPENDED') ORDER BY CASE WHEN c.status='PENDING' THEN 0 ELSE 1 END,c.id LIMIT 51 OFFSET ?",cp*50);
    var college=db.queryForList("SELECT e.product_id,e.category,e.language,e.problem,e.outcome,e.prerequisites,e.contribution,e.institution,e.academic_year,e.branch,e.share_academic_details,e.revision,e.status,e.review_reason,e.updated_at,p.title,p.slug FROM college_project_metadata e JOIN products p ON p.id=e.product_id ORDER BY CASE WHEN e.status='PENDING' THEN 0 ELSE 1 END,e.updated_at,e.product_id LIMIT 51 OFFSET ?",ep*50);
    return Map.of("components",components.subList(0,Math.min(50,components.size())).stream().map(this::entry).map(ComponentService::card).toList(),"collegeProjects",college.subList(0,Math.min(50,college.size())),"componentHasMore",components.size()>50,"collegeHasMore",college.size()>50,"componentPage",cp,"collegePage",ep);
  }
  private Map<String,Object> education(Map<String,Object> e,boolean privateView) {
    var out=new LinkedHashMap<String,Object>();
    for(String k:List.of("category","language","problem","outcome","prerequisites","contribution","status","revision"))out.put(k,e.get(k));
    boolean share=Boolean.TRUE.equals(e.get("share_academic_details"));
    out.put("shareAcademicDetails",share);
    if(privateView||share){
      out.put("institution",e.get("institution"));
      out.put("academicYear",e.get("academic_year"));
      out.put("branch",e.get("branch"));
    }
    if(privateView)out.put("reviewReason",e.get("review_reason"));
    out.put("executionMode","Source only");
    out.put("mode","Showcase only");
    return out;
  }
  public Object college(String q,String category,String language,String builder,int page) {
    if(q.length()>200||language.length()>100||page<0||page>10000||!category.isBlank()&&!COLLEGE.contains(category))throw new ApiError(400,"VALIDATION_ERROR","Choose valid college project filters.");
    String where=" WHERE "+ProductRepository.PUBLIC+" AND e.status='APPROVED'";
    var args=new ArrayList<Object>();
    for(var pair:List.of(new String[]{
      "e.category",category
    }
    ,new String[]{
      "d.slug",builder
    }
    )){
      if(!pair[1].isBlank()){
        where+=" AND "+pair[0]+"=?";
        args.add(pair[1]);
      }
    }
    if(!language.isBlank()){
      where+=" AND e.language ILIKE ?";
      args.add("%"+language.replace("%","\\%").replace("_","\\_")+"%");
    }
    if(!q.isBlank()){
      where+=" AND (p.title ILIKE ? OR e.problem ILIKE ?)";
      String term="%"+q.replace("%","\\%").replace("_","\\_")+"%";
      args.add(term);
      args.add(term);
    }
    String join=" JOIN college_project_metadata e ON e.product_id=p.id";
    long count=db.queryForObject("SELECT count(*) FROM products p JOIN users u ON u.id=p.owner_user_id JOIN developer_profiles d ON d.user_id=u.id"+join+where,Long.class,args.toArray());
    args.add(12);
    args.add(page*12);
    var rows=db.queryForList(ProductRepository.SELECT.replace(" FROM products p",",jsonb_build_object('category',e.category,'language',e.language,'problem',e.problem,'outcome',e.outcome,'prerequisites',e.prerequisites,'contribution',e.contribution,'institution',e.institution,'academic_year',e.academic_year,'branch',e.branch,'share_academic_details',e.share_academic_details,'revision',e.revision,'status',e.status,'review_reason',e.review_reason,'updated_at',e.updated_at,'product_id',e.product_id) AS education_snapshot FROM products p")+join+where+" ORDER BY p.updated_at DESC,p.id LIMIT ? OFFSET ?",args.toArray());
    var items=products.dtos(rows);
    for(int n=0;n<rows.size();n++)items.get(n).put("education",education(snapshot(rows.get(n).get("education_snapshot")),false));
    return Map.of("items",items,"page",page,"totalItems",count,"hasMore",(page+1)*12<count);
  }
  public Object collegeDetail(String slug) {
    var p=products.detail(slug);
    var rows=db.queryForList("SELECT product_id,category,language,problem,outcome,prerequisites,contribution,institution,academic_year,branch,share_academic_details,revision,status,review_reason,updated_at FROM college_project_metadata WHERE product_id=? AND status='APPROVED'",p.get("id"));
    if(rows.isEmpty())throw new ApiError(404,"NOT_FOUND","College project not found.");
    p.put("education",education(rows.get(0),false));
    return p;
  }
  public Object ownCollege(HttpServletRequest r) {
    UUID owner=security.developer(r,false);
    return Map.of("items",db.queryForList("SELECT e.product_id,e.category,e.language,e.problem,e.outcome,e.prerequisites,e.contribution,e.institution,e.academic_year,e.branch,e.share_academic_details,e.revision,e.status,e.review_reason,e.updated_at,p.title,p.slug FROM college_project_metadata e JOIN products p ON p.id=e.product_id WHERE p.owner_user_id=? ORDER BY e.updated_at LIMIT 100",owner).stream().map(e->{
      var out=education(e,true);out.put("productId",e.get("product_id"));out.put("title",e.get("title"));return out;
    }
    ).toList());
  }
  @Transactional public Object saveCollege(UUID product,Map<String,Object> b,HttpServletRequest r) {
    UUID owner=security.developer(r,true);
    slots.lock(owner);
    security.developer(r,true);
    var rows=db.queryForList("SELECT id,owner_user_id,slug,title,summary,description,project_type,category,technology,visibility,contribution_text,available_for_similar_work,approval_status,lifecycle_status,live_url,video_url,moderation_reason,rights_confirmed,created_at,updated_at,repository_url,pricing_note,demo_health,demo_checked_at,demo_checked_url,pricing_mode,price_min_minor,price_max_minor,currency_code FROM products WHERE id=? AND owner_user_id=? FOR UPDATE",product,owner);
    if(rows.isEmpty())throw new ApiError(404,"NOT_FOUND","Showcase not found.");
    if(!Boolean.TRUE.equals(b.get("rightsConsent")))throw new ApiError(400,"RIGHTS_CONSENT_REQUIRED","Confirm your contribution and permission to showcase this work.");
    String category=text(b,"category",3,20);
    if(!COLLEGE.contains(category))throw new ApiError(400,"VALIDATION_ERROR","Choose a college category.");
    publishing.requireCollegeMove(owner,product);
    db.update("INSERT INTO college_project_metadata(product_id,category,language,problem,outcome,prerequisites,contribution,institution,academic_year,branch,share_academic_details) VALUES(?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(product_id) DO UPDATE SET category=excluded.category,language=excluded.language,problem=excluded.problem,outcome=excluded.outcome,prerequisites=excluded.prerequisites,contribution=excluded.contribution,institution=excluded.institution,academic_year=excluded.academic_year,branch=excluded.branch,share_academic_details=excluded.share_academic_details,status='PENDING',revision=college_project_metadata.revision+1,review_reason=NULL,updated_at=now()",product,category,text(b,"language",1,100),text(b,"problem",20,2000),text(b,"outcome",20,2000),text(b,"prerequisites",10,2000),text(b,"contribution",20,2000),text(b,"institution",0,160),text(b,"academicYear",0,40),text(b,"branch",0,100),Boolean.TRUE.equals(b.get("shareAcademicDetails")));
    slots.audit(owner,product,"COLLEGE_SUBMITTED","Educational metadata queued for independent review; academic visibility consent recorded.");
    return Map.of("status","PENDING");
  }
  @Transactional public Object reviewCollege(UUID product,Map<String,Object> b,HttpServletRequest r) {
    UUID admin=security.admin(r);
    var rows=db.queryForList("SELECT product_id,category,language,problem,outcome,prerequisites,contribution,institution,academic_year,branch,share_academic_details,revision,status,review_reason,updated_at FROM college_project_metadata WHERE product_id=? FOR UPDATE",product);
    if(rows.isEmpty())throw new ApiError(404,"NOT_FOUND","College metadata not found.");
    security.admin(r);
    checkRevision(b,rows.get(0));
    String decision=text(b,"decision",3,30),reason=text(b,"reason",20,2000);
    if(!Set.of("APPROVED","CHANGES_REQUESTED","SUSPENDED").contains(decision))throw new ApiError(400,"VALIDATION_ERROR","Choose a college review decision.");
    if(db.update("UPDATE college_project_metadata SET status=?,revision=revision+1,review_reason=?,updated_at=now() WHERE product_id=?",decision,reason,product)!=1)throw new ApiError(404,"NOT_FOUND","College metadata not found.");
    slots.audit(admin,product,"COLLEGE_"+decision,reason);
    return Map.of("status",decision);
  }
}
