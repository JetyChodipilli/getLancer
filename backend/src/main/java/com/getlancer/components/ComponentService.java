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
  final ComponentPreviewService previews;
  final JdbcTemplate db;
  final ObjectMapper json;
  final Security security;
  final ComponentSlotService slots;
  final com.getlancer.publishing.PublishingCapacity publishing;
  final ProductService products;
  final List<Map<String,Object>> recipes;
  final String catalogSources;
  final String reportSources;
  static final Set<String> CATEGORIES=Set.of("NAVBAR","SIDEBAR","FORM","CARD","AUTH","DASHBOARD");
  static final Set<String> COLLEGE=Set.of("FULL_STACK","DATA_ANALYTICS","AI_ML","IOT");
  static final String JOIN=" FROM component_entries c JOIN users u ON u.id=c.owner_id JOIN developer_profiles d ON d.user_id=u.id";
  static final String ELIGIBLE="u.account_status='ACTIVE' AND u.email_verified_at IS NOT NULL AND d.approval_status='APPROVED' AND EXISTS(SELECT 1 FROM user_roles role WHERE role.user_id=u.id AND role.role='DEVELOPER')";
  private static final String COLLEGE_FROM=ProductRepository.FROM+" JOIN college_project_metadata e ON e.product_id=p.id";
  private static final String COLLEGE_FILTERS=" WHERE "+ProductRepository.PUBLIC+" AND e.status='APPROVED' AND (? OR e.category=?) AND (? OR d.slug=?) AND (? OR e.language ILIKE ?) AND (? OR p.title ILIKE ? OR e.problem ILIKE ?)";
  private static final String COLLEGE_COUNT="SELECT count(*)"+COLLEGE_FROM+COLLEGE_FILTERS;
  private static final String COLLEGE_SEARCH=ProductRepository.PROJECTION+",jsonb_build_object('category',e.category,'language',e.language,'problem',e.problem,'outcome',e.outcome,'prerequisites',e.prerequisites,'contribution',e.contribution,'institution',e.institution,'academic_year',e.academic_year,'branch',e.branch,'share_academic_details',e.share_academic_details,'revision',e.revision,'status',e.status,'review_reason',e.review_reason,'updated_at',e.updated_at,'product_id',e.product_id) AS education_snapshot"+COLLEGE_FROM+COLLEGE_FILTERS+" ORDER BY p.updated_at DESC,p.id LIMIT ? OFFSET ?";
  public ComponentService(JdbcTemplate db,Security security,ComponentSlotService slots,ProductService products,ObjectMapper json,com.getlancer.publishing.PublishingCapacity publishing,ComponentPreviewService previews) throws java.io.IOException {
    this.previews=previews;this.db=db;
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
      var metadata=new LinkedHashMap<String,Object>();
      for(var recipe:recipes){
        var fields=new LinkedHashMap<String,Object>();
        for(String key:List.of("category","kind","language","framework","license","executionMode"))if(recipe.containsKey(key))fields.put(key,recipe.get(key));
        metadata.put(recipe.get("slug").toString(),fields);
      }
      catalogSources=encode(metadata);
      reportSources=encode(recipes.stream().map(recipe->Map.of("recipe_slug",recipe.get("slug"),"source_sha256",recipe.get("sha256"))).toList());
    }
  }
  Map<String,Object> recipe(String slug) {
    return recipes.stream().filter(x->slug.equals(x.get("slug"))).findFirst().orElseThrow(()->new ApiError(404,"NOT_FOUND","Component not found."));
  }
  Map<String,Object> publishedSource(Map<String,Object> c) {
    var releases=db.queryForList("SELECT source FROM component_releases WHERE component_id=? ORDER BY revision DESC LIMIT 1",c.get("id"));
    if(!releases.isEmpty())return snapshot(releases.get(0).get("source"));
    return c.get("published_source")==null?recipe(Objects.toString(c.get("recipe_slug"))):snapshot(c.get("published_source"));
  }
  Map<String,Object> reviewSource(Map<String,Object> c){
    if(Set.of("ACTIVE","ARCHIVED").contains(c.get("status"))&&c.get("published_at")!=null)return publishedSource(c);
    if(c.get("submitted_source")!=null&&Set.of("PENDING","SUSPENDED").contains(c.get("status")))return snapshot(c.get("submitted_source"));
    return c.get("draft_source")!=null?snapshot(c.get("draft_source")):publishedSource(c);
  }
  Map<String,Object> reviewContext(Map<String,Object> c){
    if(Set.of("ACTIVE","ARCHIVED").contains(c.get("status"))&&c.get("published_at")!=null)return snapshot(c.get("published_context"));
    if(Set.of("PENDING","SUSPENDED").contains(c.get("status"))&&c.get("submitted_context")!=null)return snapshot(c.get("submitted_context"));
    return Map.of("title",c.get("title"),"summary",c.get("summary"),"contribution",c.get("contribution"));
  }
  Map<String,Object> privateEntry(Map<String,Object> row){
    if("ARCHIVED".equals(row.get("status"))){var draft=new LinkedHashMap<>(row);draft.put("status","DRAFT");var out=entry(draft);out.put("status",row.get("status"));return out;}
    return entry(row);
  }
  Map<String,Object> entry(Map<String,Object> c) {
    var source=reviewSource(c);
    var out=new LinkedHashMap<>(sourceProjection(source));
    out.put("published",c.get("published_at")!=null);
    out.put("revision",c.get("revision"));
    var submitted=c.get("submitted_source");
    out.put("sourceHash",reviewHash(c,source));
    out.put("uploaded",source.containsKey("archiveBase64"));
    out.put("withdrawn",c.get("withdrawn_at")!=null);
    out.put("id",c.get("id"));
    out.put("recipeSlug",c.get("recipe_slug"));
    out.put("slug",c.get("slug"));
    var context=reviewContext(c);
    out.put("title",context.get("title"));
    out.put("summary",context.get("summary"));
    out.put("contribution",context.get("contribution"));
    out.put("creator",c.get("creator"));
    out.put("builderSlug",c.get("builder_slug"));
    out.put("status",c.get("status"));
    out.put("reviewReason",c.get("review_reason"));
    return out;
  }
  /** Catalogue metadata is a deliberate dynamic exception; pin metadata keys and bound source files. */
  static Map<String,Object> sourceProjection(Map<String,Object> source) {
    var projected=new LinkedHashMap<String,Object>();
    for(String key:List.of("slug","category","title","summary","version","language","framework","kind","executionMode","creator","license","scenario","sha256","archiveSha256","manifestSha256"))
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
  private Object canonical(Object value){
    if(value instanceof Map<?,?> map){var sorted=new java.util.TreeMap<String,Object>();map.forEach((key,item)->sorted.put(key.toString(),canonical(item)));return sorted;}
    return value;
  }
  String reviewHash(Map<String,Object> row,Map<String,Object> source){
    return com.getlancer.hosting.StaticArchive.sha(encode(canonical(Map.of("source",source,"context",reviewContext(row),"rightsConsent",true))).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
  Map<String,Object> publicEntry(Map<String,Object> row){
    var published=new LinkedHashMap<>(row);
    published.put("submitted_source",null);
    published.put("draft_source",null);
    var out=entry(published);
    if(!"ACTIVE".equals(row.get("status"))){
      var context=snapshot(row.get("published_context"));
      for(String key:List.of("title","summary","contribution"))if(context.containsKey(key))out.put(key,context.get(key));
    }
    out.remove("reviewReason");
    out.remove("sourceHash");
    out.remove("withdrawn");
    return out;
  }
  static Map<String,Object> card(Map<String,Object> item) {
    var out=new LinkedHashMap<>(item);
    out.remove("files");
    return out;
  }
  public Map<String,Object> catalog(String q,String category,String kind,String builder,int page) {
    return catalog(q,category,kind,builder,page,"","","","");
  }
  public Map<String,Object> catalog(String q,String category,String kind,String builder,int page,String language,String framework,String license,String mode) {
    if(q.length()>200)throw new ApiError(400,"VALIDATION_ERROR","Choose valid component filters.");
    for(String value:List.of(category,kind,builder,language,framework,license,mode))if(value.length()>100)throw new ApiError(400,"VALIDATION_ERROR","Choose valid component filters.");
    for(String value:List.of(q,category,kind,builder,language,framework,license,mode))if(value.chars().anyMatch(c->c<32||c==127))throw new ApiError(400,"VALIDATION_ERROR","Choose valid component filters.");
    q=q.strip();category=category.strip();kind=kind.strip();builder=builder.strip();language=language.strip();framework=framework.strip();license=license.strip();mode=mode.strip();
    if(page<0||page>10000||!category.isBlank()&&!CATEGORIES.contains(category)||!Set.of("FRONTEND","BACKEND").contains(kind)) throw new ApiError(400,"VALIDATION_ERROR","Choose valid component filters.");
    if(kind.equals("BACKEND"))return Map.of("items",List.of(),"totalItems",0,"totalPages",0,"size",12,"hasMore",false,"page",page,"executionMode","Source only");
    var all=new ArrayList<Map<String,Object>>();
    if(builder.isBlank())all.addAll(availableRecipes());
    // ponytail: bounded recipe catalogue plus paginated database remixes; no separate search infrastructure.
    // Match the same released source shown by detail; private drafts never influence counts.
    String source="COALESCE(released.filter_metadata,c.published_filter_metadata,seed.value)";
    String sql=JOIN+" LEFT JOIN LATERAL (SELECT r.filter_metadata FROM component_releases r WHERE r.component_id=c.id ORDER BY r.revision DESC LIMIT 1) released ON true LEFT JOIN jsonb_each(?::jsonb) seed ON seed.key=c.recipe_slug WHERE c.status='ACTIVE' AND c.withdrawn_at IS NULL AND "+ELIGIBLE+" AND "+source+"->>'kind'=?";
    var args=new ArrayList<Object>();
    args.add(catalogSources);args.add(kind);
    if(!builder.isBlank()){
      sql+=" AND d.slug=?";
      args.add(builder);
    }
    if(!q.isBlank()){
      sql+=" AND concat_ws(' ',c.published_context->>'title',c.published_context->>'summary') ILIKE ?";
      String term="%"+q.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
      args.add(term);
    }
    if(!category.isBlank()){
      sql+=" AND "+source+"->>'category'=?";args.add(category);
    }
    if(!language.isBlank()){
      sql+=" AND EXISTS(SELECT 1 FROM regexp_split_to_table(lower(COALESCE("+source+"->>'language',"+source+"->>'framework','')),'[[:space:]/,]+') AS languages(token) WHERE (CASE token WHEN 'javascript' THEN 'js' ELSE token END)=?)";args.add(languageKey(language));
    }
    if(!framework.isBlank()){
      String value="COALESCE("+source+"->>'framework','')";
      sql+=" AND (CASE WHEN lower(regexp_replace("+value+",'[[:space:]]','','g')) IN ('html/css/javascript','html/css/js','vanilla') THEN 'vanilla' ELSE lower(btrim("+value+")) END)=?";args.add(frameworkKey(framework));
    }
    if(!license.isBlank()){sql+=" AND lower("+source+"->>'license')=?";args.add(license.toLowerCase(Locale.ROOT));}
    if(!mode.isBlank()){sql+=" AND lower("+source+"->>'executionMode')=?";args.add(mode.toLowerCase(Locale.ROOT));}
    long count=db.queryForObject("SELECT count(*)"+sql,Long.class,args.toArray());
    var filtered=new ArrayList<Map<String,Object>>();
    for(var recipe:all)if(matches(recipe,q,category,language,framework,license,mode))filtered.add(recipe);
    all=filtered;
    int seedCount=all.size(),offset=page*12;
    var result=new ArrayList<Map<String,Object>>();
    if(offset<seedCount)result.addAll(all.subList(offset,Math.min(seedCount,offset+12)));
    int take=12-result.size();
    if(take>0){
      args.add(take);
      args.add(Math.max(0,offset-seedCount));
      result.addAll(db.queryForList("SELECT c.id,c.recipe_slug,c.slug,c.title,c.summary,c.contribution,c.revision,c.status,c.review_reason,c.published_at,c.published_source,c.published_context,c.draft_source,c.submitted_source,c.submitted_context,c.withdrawn_at,d.display_name AS creator,d.slug AS builder_slug"+sql+" ORDER BY c.created_at DESC,c.id LIMIT ? OFFSET ?",args.toArray()).stream().map(this::publicEntry).toList());
    }
    return Map.of("items",result.stream().map(ComponentService::card).toList(),"totalItems",seedCount+count,"totalPages",(seedCount+count+11)/12,"size",12,"hasMore",offset+12<seedCount+count,"page",page);
  }
  static String languageKey(String value){return value.strip().toLowerCase(Locale.ROOT).replaceAll("^javascript$","js");}
  static String frameworkKey(String value){String key=value.toLowerCase(Locale.ROOT).replaceAll("\\s","");return Set.of("html/css/javascript","html/css/js","vanilla").contains(key)?"vanilla":value.strip().toLowerCase(Locale.ROOT);}
  static boolean matches(Map<String,Object> x,String q,String category,String language,String framework,String license,String mode) {
    return (category.isBlank()||category.equals(x.get("category")))&&(q.isBlank()||(x.get("title")+" "+x.get("summary")).toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT)))
      &&(language.isBlank()||java.util.Arrays.stream(Objects.toString(x.getOrDefault("language",x.get("framework")),"").split("[\\s/,]+")).anyMatch(token->languageKey(token).equals(languageKey(language))))
      &&(framework.isBlank()||frameworkKey(Objects.toString(x.get("framework"),"")).equals(frameworkKey(framework)))
      &&(license.isBlank()||license.equalsIgnoreCase(Objects.toString(x.get("license"),"")))
      &&(mode.isBlank()||mode.equalsIgnoreCase(Objects.toString(x.get("executionMode"),"")));
  }
  public Map<String,Object> detail(String slug) {
    var seed=recipes.stream().filter(x->slug.equals(x.get("slug"))).findFirst();
    if(seed.isPresent()){
      if(recipeSuspended(seed.get()))throw new ApiError(404,"NOT_FOUND","Component not found.");
      var out=new LinkedHashMap<>(seed.get());out.put("reportTargetId",recipeReportId(slug));out.put("reportRevision",1L);out.put("related",relatedRecipes(slug));return out;
    }
    var rows=db.queryForList("SELECT c.id,c.recipe_slug,c.slug,c.title,c.summary,c.contribution,c.revision,c.status,c.review_reason,c.published_at,c.published_source,c.published_context,c.draft_source,c.submitted_source,c.submitted_context,c.withdrawn_at,d.display_name AS creator,d.slug AS builder_slug"+JOIN+" WHERE c.slug=? AND c.withdrawn_at IS NULL AND c.published_at IS NOT NULL AND c.status IN ('ACTIVE','ARCHIVED','DRAFT','PENDING') AND "+ELIGIBLE,slug);
    if(rows.isEmpty())throw new ApiError(404,"NOT_FOUND","Component not found.");
    var out=publicEntry(rows.get(0));out.put("reportTargetId",out.get("id"));out.put("reportRevision",out.get("revision"));out.put("related",relatedRecipes(Objects.toString(out.get("recipeSlug"),slug)));return out;
  }
  private List<Map<String,Object>> availableRecipes(){
    var blocked=db.queryForList("SELECT e.recipe_slug FROM component_recipe_enforcement e JOIN jsonb_to_recordset(?::jsonb) AS candidate(recipe_slug text,source_sha256 text) ON candidate.recipe_slug=e.recipe_slug AND candidate.source_sha256=e.source_sha256 WHERE e.suspended",String.class,reportSources);
    return recipes.stream().filter(recipe->!blocked.contains(recipe.get("slug"))).toList();
  }
  private List<Map<String,Object>> relatedRecipes(String slug){return availableRecipes().stream().filter(recipe->!slug.equals(recipe.get("slug"))).limit(3).map(ComponentService::card).toList();}
  private static UUID recipeReportId(String slug){return UUID.nameUUIDFromBytes(("getlancer:curated-component:"+slug).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
  private boolean recipeSuspended(Map<String,Object> recipe){return db.queryForObject("SELECT count(*) FROM component_recipe_enforcement WHERE recipe_slug=? AND source_sha256=? AND suspended",Integer.class,recipe.get("slug"),recipe.get("sha256"))>0;}
  private void lockRecipeReport(String slug){db.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"component-recipe-report:"+slug);}
  private Map<String,Object> reportSnapshot(UUID id,long revision,Map<String,Object> source,Map<String,Object> context){
    var metadata=new LinkedHashMap<String,Object>(sourceProjection(source));metadata.remove("files");
    return Map.of("componentId",id,"revision",revision,"version",source.get("version"),"sourceHash",source.get("sha256"),"source",metadata,"context",context);
  }
  /** Freeze server-derived published facts, not caller-supplied source or moderator fields. */
  @Transactional public Map<String,Object> reportArtifact(UUID id,long expectedRevision,String expectedSha){
    var curated=recipes.stream().filter(recipe->recipeReportId(recipe.get("slug").toString()).equals(id)).findFirst();
    if(curated.isPresent()){
      var source=curated.get();lockRecipeReport(source.get("slug").toString());
      if(recipeSuspended(source))throw new ApiError(404,"NOT_FOUND","Component not found.");
      if(expectedRevision!=1||!Objects.equals(source.get("sha256"),expectedSha))throw new ApiError(409,"REPORT_ARTIFACT_CHANGED","Refresh the component before reporting this source version.");
      return Map.of("componentId",id,"revision",1L,"version",source.get("version"),"sourceHash",source.get("sha256"),"snapshot",reportSnapshot(id,1L,source,Map.of("title",source.get("title"),"summary",source.get("summary"),"curated",true)));
    }
    var owners=db.queryForList("SELECT owner_id FROM component_entries WHERE id=?",UUID.class,id);
    if(owners.isEmpty())throw new ApiError(404,"NOT_FOUND","Component not found.");
    slots.lockAuthority(owners.get(0));
    var rows=db.queryForList("SELECT c.id,c.recipe_slug,c.revision,c.published_source,c.published_context"+JOIN+" WHERE c.id=? AND c.withdrawn_at IS NULL AND c.published_at IS NOT NULL AND c.status IN ('ACTIVE','ARCHIVED','DRAFT','PENDING') AND "+ELIGIBLE+" FOR SHARE OF c",id);
    if(rows.isEmpty())throw new ApiError(404,"NOT_FOUND","Component not found.");
    var row=rows.get(0);var source=publishedSource(row);long revision=((Number)row.get("revision")).longValue();
    if(revision!=expectedRevision||!Objects.equals(source.get("sha256"),expectedSha))throw new ApiError(409,"REPORT_ARTIFACT_CHANGED","Refresh the component before reporting this source version.");
    return Map.of("componentId",id,"revision",revision,"version",source.get("version"),"sourceHash",source.get("sha256"),"snapshot",reportSnapshot(id,revision,source,snapshot(row.get("published_context"))));
  }
  /** Current source authority closes fresh admissions and current run routes after this commits. */
  @Transactional public Map<String,Object> suspendReported(UUID id,long expectedRevision,String expectedSourceHash,String expectedVersion,String reason,HttpServletRequest request){
    UUID admin=security.admin(request);slots.lockAuthority(admin);security.admin(request);
    if(reason==null||reason.strip().length()<3||reason.length()>2000)throw new ApiError(400,"VALIDATION_ERROR","Record an enforcement reason.");
    var curated=recipes.stream().filter(recipe->recipeReportId(recipe.get("slug").toString()).equals(id)).findFirst();
    if(curated.isPresent()){
      var source=curated.get();lockRecipeReport(source.get("slug").toString());
      if(expectedRevision!=1||!Objects.equals(source.get("sha256"),expectedSourceHash)||!Objects.equals(source.get("version"),expectedVersion))throw new ApiError(409,"REPORTED_COMPONENT_CHANGED","The published component source changed. Review the current version before enforcing this report.");
      db.update("INSERT INTO component_recipe_enforcement(recipe_slug,source_sha256,suspended,actor_id,reason) VALUES(?,?,true,?,?) ON CONFLICT(recipe_slug,source_sha256) DO UPDATE SET suspended=true,actor_id=EXCLUDED.actor_id,reason=EXCLUDED.reason,updated_at=now()",source.get("slug"),expectedSourceHash,admin,reason);
    }else{
      var owners=db.queryForList("SELECT owner_id FROM component_entries WHERE id=?",UUID.class,id);if(owners.isEmpty())throw new ApiError(404,"NOT_FOUND","Component not found.");
      slots.lock(owners.get(0));
      var row=db.queryForMap("SELECT id,owner_id,recipe_slug,revision,status,published_at,published_source,published_context FROM component_entries WHERE id=? FOR UPDATE",id);security.admin(request);
      if(row.get("published_at")==null)throw new ApiError(404,"NOT_FOUND","Published component not found.");
      var source=publishedSource(row);
      if(((Number)row.get("revision")).longValue()!=expectedRevision||!Objects.equals(source.get("sha256"),expectedSourceHash)||!Objects.equals(source.get("version"),expectedVersion))throw new ApiError(409,"REPORTED_COMPONENT_CHANGED","The published component source changed. Review the current version before enforcing this report.");
      if(!"SUSPENDED".equals(row.get("status")))db.update("UPDATE component_entries SET status='SUSPENDED',revision=revision+1,submitted_source=?::jsonb,submitted_context=?::jsonb,review_reason=?,updated_at=now() WHERE id=?",encode(source),Objects.toString(row.get("published_context")),reason,id);
    }
    slots.audit(admin,id,"REPORT_SUSPEND",reason);
    db.update("INSERT INTO moderation_actions(id,admin_id,target_type,target_id,action,reason) VALUES(?,?,'COMPONENT',?,'suspend',?)",id(),admin,id,reason);
    return Map.of("status","SUSPENDED");
  }
  @Transactional(readOnly=true)
  public org.springframework.http.ResponseEntity<byte[]> source(String slug,String sha256) {
    var item=detail(slug); // Reuse current publication, withdrawal and creator authority.
    if(item.get("id")==null||!Boolean.TRUE.equals(item.get("uploaded")))throw new ApiError(404,"NOT_FOUND","Reviewed upload not found.");
    var source=publishedSource(Map.of("id",item.get("id")));
    var inspected=ComponentArchive.reviewed(source);
    if(!inspected.archiveSha256().equals(sha256))throw new ApiError(409,"SOURCE_VERSION_CHANGED","Refresh to download the current reviewed archive.");
    byte[] bytes=java.util.Base64.getDecoder().decode(source.get("archiveBase64").toString());
    return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType("application/zip"))
      .header("Content-Disposition",org.springframework.http.ContentDisposition.attachment().filename(slug+"-"+source.get("version")+".zip").build().toString())
      .header("Cache-Control","no-store").body(bytes);
  }
  public Map<String,Object> own(HttpServletRequest r) {
    UUID owner=security.developer(r,false);
    return Map.of("items",db.queryForList("SELECT c.id,c.recipe_slug,c.slug,c.title,c.summary,c.contribution,c.revision,c.status,c.review_reason,c.published_at,c.published_source,c.published_context,c.draft_source,c.submitted_source,c.submitted_context,c.withdrawn_at,d.display_name AS creator,d.slug AS builder_slug"+JOIN+" WHERE c.owner_id=? ORDER BY c.created_at DESC LIMIT 100",owner).stream().map(this::privateEntry).map(ComponentService::card).toList(),"capacity",slots.capacity(owner),"pricing",slots.pricing(),"recipes",availableRecipes().stream().map(ComponentService::card).toList());
  }
  public Object preview(String slug){return previews.publicPreview(slug);}
  public Object publishPreview(UUID id,HttpServletRequest r){return previews.publish(id,r);}
  @Transactional public Object ownDetail(UUID id,HttpServletRequest r){
    UUID owner=security.developer(r,false);slots.lockAuthority(owner);security.developer(r,false);
    var rows=db.queryForList("SELECT c.id,c.recipe_slug,c.slug,c.title,c.summary,c.contribution,c.revision,c.status,c.review_reason,c.published_at,c.published_source,c.published_context,c.draft_source,c.submitted_source,c.submitted_context,c.withdrawn_at,d.display_name AS creator,d.slug AS builder_slug"+JOIN+" WHERE c.id=? AND c.owner_id=?",id,owner);
    if(rows.isEmpty())throw new ApiError(404,"NOT_FOUND","Component not found.");return privateEntry(rows.get(0));
  }
  @Transactional public Object create(Map<String,Object> b,HttpServletRequest r) {
    UUID owner=security.developer(r,true);
    slots.lock(owner);
    security.developer(r,true);
    if(db.queryForObject("SELECT count(*) FROM component_entries WHERE owner_id=?",Integer.class,owner)>=100)throw new ApiError(409,"DRAFT_LIMIT","Manage your existing 100 component records before adding more.");
    String recipe=text(b,"recipeSlug",3,100);
    if(recipeSuspended(recipe(recipe)))throw new ApiError(409,"COMPONENT_RECIPE_UNAVAILABLE","This recipe is unavailable for new submissions. Choose another reviewed source.");
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
    var rows=db.queryForList("SELECT id,owner_id,recipe_slug,slug,title,summary,contribution,revision,status,review_reason,published_at,published_source,published_context,draft_source,submitted_source,submitted_context,withdrawn_at,created_at,updated_at FROM component_entries WHERE id=? AND owner_id=? FOR UPDATE",id,owner);
    if(rows.isEmpty())throw new ApiError(404,"NOT_FOUND","Component not found.");
    var c=rows.get(0);
    String next;
    if(action.equals("submit")&&Set.of("DRAFT","ARCHIVED").contains(c.get("status")))next="PENDING";
    else if(action.equals("new-version")&&"ACTIVE".equals(c.get("status")))next="DRAFT";
    else if(Set.of("archive","withdraw").contains(action)&&!"SUSPENDED".equals(c.get("status")))next="ARCHIVED";
    else throw new ApiError(409,"INVALID_COMPONENT_STATE","This component cannot take that action.");
    var candidate=c.get("draft_source")==null?publishedSource(c):snapshot(c.get("draft_source"));
    if(next.equals("PENDING")&&db.queryForObject("SELECT count(*) FROM component_releases WHERE component_id=?",Integer.class,id)>=100&&!canonical(candidate).equals(canonical(publishedSource(c))))throw new ApiError(409,"VERSION_LIMIT","A component supports up to 100 reviewed releases.");
    if(next.equals("PENDING")&&db.queryForObject("SELECT count(*) FROM component_entries WHERE owner_id=? AND status='PENDING'",Integer.class,owner)>=3)throw new ApiError(409,"PENDING_LIMIT","Wait for review of your three pending submissions.");
    db.update("UPDATE component_entries SET status=?,revision=revision+1,submitted_source=CASE WHEN ?='PENDING' THEN ?::jsonb ELSE submitted_source END,submitted_context=CASE WHEN ?='PENDING' THEN ?::jsonb ELSE submitted_context END,withdrawn_at=CASE WHEN ?='withdraw' THEN now() ELSE withdrawn_at END,updated_at=now() WHERE id=?",next,next,encode(c.get("draft_source")==null?publishedSource(c):snapshot(c.get("draft_source"))),next,encode(Map.of("title",c.get("title"),"summary",c.get("summary"),"contribution",c.get("contribution"))),action,id);
    if(action.equals("withdraw"))db.update("UPDATE component_previews SET state='REVOKED' WHERE component_id=?",id);
    slots.audit(owner,id,"COMPONENT_"+next,"Creator changed publication state.");
    return Map.of("status",next);
  }
  @Transactional public Object edit(UUID id,Map<String,Object>b,HttpServletRequest r){
    UUID owner=security.developer(r,true);
    slots.lock(owner);
    security.developer(r,true);
    var rows=db.queryForList("SELECT id,owner_id,recipe_slug,slug,title,summary,contribution,revision,status,review_reason,published_at,published_source,published_context,draft_source,submitted_source,submitted_context,withdrawn_at,created_at,updated_at FROM component_entries WHERE id=? AND owner_id=? FOR UPDATE",id,owner);
    if(rows.isEmpty())throw new ApiError(404,"NOT_FOUND","Component not found.");
    var c=rows.get(0);
    if(!Set.of("DRAFT","ARCHIVED").contains(c.get("status")))throw new ApiError(409,"INVALID_COMPONENT_STATE","Archive an active component before editing its context.");
    String recipe=text(b,"recipeSlug",3,100);
    if(recipeSuspended(recipe(recipe)))throw new ApiError(409,"COMPONENT_RECIPE_UNAVAILABLE","This recipe is unavailable for new submissions. Choose another reviewed source.");
    if((c.get("published_at")!=null||c.get("draft_source")!=null)&&!recipe.equals(c.get("recipe_slug")))throw new ApiError(409,"PUBLISHED_SOURCE_IMMUTABLE","A published recipe stays pinned. Create a separate component to use another source.");
    if(!Boolean.TRUE.equals(b.get("rightsConsent")))throw new ApiError(400,"RIGHTS_CONSENT_REQUIRED","Confirm the original licence and contribution.");
    db.update("UPDATE component_entries SET draft_source=CASE WHEN recipe_slug<>? THEN NULL ELSE draft_source END,recipe_slug=?,title=?,summary=?,contribution=?,status='DRAFT',revision=revision+1,updated_at=now() WHERE id=?",recipe,recipe,text(b,"title",3,100),text(b,"summary",10,240),text(b,"contribution",20,2000),id);
    slots.audit(owner,id,"DRAFT_EDITED","Curated recipe context edited; fresh publication review required.");
    return Map.of("id",id);
  }
  @Transactional public Object upload(UUID id,org.springframework.web.multipart.MultipartFile file,String version,String scenario,boolean consent,HttpServletRequest r) throws java.io.IOException {
    UUID owner=security.developer(r,true);
    slots.lock(owner);security.developer(r,true);
    var rows=db.queryForList("SELECT id,recipe_slug,revision,status FROM component_entries WHERE id=? AND owner_id=? FOR UPDATE",id,owner);
    if(rows.isEmpty())throw new ApiError(404,"NOT_FOUND","Component not found.");
    var c=rows.get(0);
    if(!Set.of("DRAFT","ARCHIVED").contains(c.get("status")))throw new ApiError(409,"INVALID_COMPONENT_STATE","Archive the component before uploading a new version.");
    if(db.queryForObject("SELECT count(*) FROM component_releases WHERE component_id=?",Integer.class,id)>=100)throw new ApiError(409,"VERSION_LIMIT","A component supports up to 100 reviewed releases.");
    if(scenario==null||scenario.isBlank()||scenario.length()>500)throw new ApiError(400,"VALIDATION_ERROR","Describe a synthetic preview interaction in up to 500 characters.");
    var source=ComponentArchive.inspect(file,version,consent,recipe(Objects.toString(c.get("recipe_slug"))));
    source.put("scenario",scenario);
    if(db.queryForObject("SELECT count(*) FROM component_releases WHERE component_id=? AND source->>'version'=?",Integer.class,id,version)>0)throw new ApiError(409,"VERSION_EXISTS","Choose a new version; published versions are immutable.");
    db.update("UPDATE component_entries SET draft_source=?::jsonb,status='DRAFT',revision=revision+1,updated_at=now() WHERE id=?",encode(source),id);
    slots.audit(owner,id,"SOURCE_UPLOADED","Bounded MIT source archive pinned for fresh review; SHA-256 "+source.get("archiveSha256"));
    return Map.of("id",id,"version",version,"sourceHash",source.get("sha256"),"archiveSha256",source.get("archiveSha256"));
  }
  @Transactional public Object review(UUID id,Map<String,Object> b,HttpServletRequest r) {
    UUID admin=security.admin(r);slots.lockAuthority(admin);security.admin(r);
    var rows=db.queryForList("SELECT owner_id FROM component_entries WHERE id=?",id);
    if(rows.isEmpty())throw new ApiError(404,"NOT_FOUND","Component not found.");
    UUID owner=(UUID)rows.get(0).get("owner_id");
    slots.lock(owner);
    var c=db.queryForMap("SELECT id,owner_id,recipe_slug,slug,title,summary,contribution,revision,status,review_reason,published_at,published_source,published_context,draft_source,submitted_source,submitted_context,withdrawn_at,created_at,updated_at FROM component_entries WHERE id=? FOR UPDATE",id);
    security.admin(r);
    checkRevision(b,c);
    var submitted=reviewSource(c);var context=reviewContext(c);
    if(!reviewHash(c,submitted).equals(b.get("sourceHash")))throw new ApiError(409,"REVIEW_SOURCE_CHANGED","The reviewed source hash does not match this submission.");
    String decision=text(b,"decision",3,30),reason=text(b,"reason",20,2000),next;
    if(decision.equals("APPROVE")&&Set.of("PENDING","SUSPENDED").contains(c.get("status"))){
      if(db.queryForObject("SELECT count(*) FROM users u JOIN developer_profiles d ON d.user_id=u.id WHERE u.id=? AND "+ELIGIBLE,Integer.class,owner)==0)throw new ApiError(409,"BUILDER_NOT_ELIGIBLE","The builder must have an active account and approved profile.");
      var capacity=slots.capacity(owner);
      if(((Number)capacity.get("used")).intValue()>=((Number)capacity.get("limit")).intValue())throw new ApiError(409,"COMPONENT_CAPACITY_REACHED","Archive a component or purchase another publishing slot before approval.");
      next="ACTIVE";
    }
    else if(decision.equals("CHANGES_REQUESTED")&&"PENDING".equals(c.get("status")))next="DRAFT";
    else if(decision.equals("SUSPEND")&&(Set.of("ACTIVE","PENDING").contains(c.get("status"))||"ARCHIVED".equals(c.get("status"))&&c.get("published_at")!=null&&c.get("withdrawn_at")==null))next="SUSPENDED";
    else throw new ApiError(409,"INVALID_COMPONENT_STATE","Choose a valid review decision.");
    if(next.equals("ACTIVE")&&"SUSPENDED".equals(c.get("status"))&&c.get("withdrawn_at")!=null)throw new ApiError(409,"COMPONENT_WITHDRAWN","Only a fresh owner submission can renew withdrawn publication.");
    boolean newRelease=false;
    if(next.equals("ACTIVE")){
      var versions=db.queryForList("SELECT revision,source FROM component_releases WHERE component_id=? AND source->>'version'=? ORDER BY revision DESC LIMIT 1",id,submitted.get("version"));
      newRelease=versions.isEmpty();
      if(!newRelease&&(!canonical(snapshot(versions.get(0).get("source"))).equals(canonical(submitted))||!versions.get(0).get("revision").equals(db.queryForObject("SELECT max(revision) FROM component_releases WHERE component_id=?",Long.class,id))))throw new ApiError(409,"VERSION_EXISTS","Use a new version for different source; reviewed versions are immutable.");
    }
    db.update("UPDATE component_entries SET status=?,revision=revision+1,submitted_source=CASE WHEN ?='SUSPENDED' THEN ?::jsonb ELSE submitted_source END,submitted_context=CASE WHEN ?='SUSPENDED' THEN ?::jsonb ELSE submitted_context END,review_reason=?,published_at=CASE WHEN ?='ACTIVE' THEN coalesce(published_at,now()) ELSE published_at END,published_source=CASE WHEN ?='ACTIVE' THEN coalesce(published_source,?::jsonb) ELSE published_source END,published_context=CASE WHEN ?='ACTIVE' THEN ?::jsonb ELSE published_context END,withdrawn_at=CASE WHEN ?='ACTIVE' THEN NULL ELSE withdrawn_at END,updated_at=now() WHERE id=?",next,next,encode(submitted),next,encode(context),reason,next,next,encode(submitted),next,encode(context),next,id);
    if(next.equals("ACTIVE")&&newRelease)db.update("INSERT INTO component_releases(component_id,revision,source,context,source_sha256) VALUES(?,?,?::jsonb,?::jsonb,?)",id,((Number)c.get("revision")).longValue(),encode(submitted),encode(context),submitted.get("sha256"));
    slots.audit(admin,id,"REVIEW_"+decision,reason);
    return Map.of("status",next);
  }
  static void checkRevision(Map<String,Object>b,Map<String,Object>row){
    Object value=b.get("revision");
    if(!(value instanceof Integer||value instanceof Long)||((Number)value).longValue()!=((Number)row.get("revision")).longValue())throw new ApiError(409,"REVIEW_CONTENT_CHANGED","This content changed after you loaded it. Reload and review the current revision.");
  }
  public Object history(String slug) {
    var component=detail(slug); // Recheck current account, publication and withdrawal authority.
    if(component.get("id")==null)return Map.of("items",List.of(Map.of("version",component.get("version"),"sha256",component.get("sha256"))));
    return Map.of("items",db.queryForList("SELECT revision,source->>'version' AS version,source_sha256 AS sha256,source->>'archiveSha256' AS archive_sha256,source->>'manifestSha256' AS manifest_sha256,published_at FROM component_releases WHERE component_id=? ORDER BY revision DESC LIMIT 100",component.get("id")));
  }
  @Transactional public Object adminDetail(UUID id,HttpServletRequest r){UUID admin=security.admin(r);slots.lockAuthority(admin);security.admin(r);var rows=db.queryForList("SELECT c.id,c.recipe_slug,c.slug,c.title,c.summary,c.contribution,c.revision,c.status,c.review_reason,c.published_at,c.published_source,c.published_context,c.draft_source,c.submitted_source,c.submitted_context,c.withdrawn_at,d.display_name AS creator,d.slug AS builder_slug"+JOIN+" WHERE c.id=?",id);if(rows.isEmpty())throw new ApiError(404,"NOT_FOUND","Component not found.");return entry(rows.get(0));}
  public Object queue(HttpServletRequest r) {
    security.admin(r);
    int cp=com.getlancer.shared.Pages.number(r,"componentPage",0,100000),ep=com.getlancer.shared.Pages.number(r,"collegePage",0,100000);
    var components=db.queryForList("SELECT c.id,c.recipe_slug,c.slug,c.title,c.summary,c.contribution,c.revision,c.status,c.review_reason,c.published_at,c.published_source,c.published_context,c.draft_source,c.submitted_source,c.submitted_context,c.withdrawn_at,d.display_name AS creator,d.slug AS builder_slug"+JOIN+" WHERE c.status IN ('PENDING','ACTIVE','SUSPENDED') ORDER BY CASE WHEN c.status='PENDING' THEN 0 ELSE 1 END,c.id LIMIT 51 OFFSET ?",cp*50);
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
    var args=new ArrayList<Object>();
    String term="%"+q.replace("%","\\%").replace("_","\\_")+"%";
    String languageTerm="%"+language.replace("%","\\%").replace("_","\\_")+"%";
    Collections.addAll(args,category.isBlank(),category,builder.isBlank(),builder,
        language.isBlank(),languageTerm,q.isBlank(),term,term);
    long count=db.queryForObject(COLLEGE_COUNT,Long.class,args.toArray());
    args.add(12);
    args.add(page*12);
    var rows=db.queryForList(COLLEGE_SEARCH,args.toArray());
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
    UUID admin=security.admin(r);slots.lockAuthority(admin);security.admin(r);
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
