package com.getlancer.education;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.admin.AdminService;
import com.getlancer.commerce.CommerceEducationBinding;
import com.getlancer.commerce.CommerceStorage;
import com.getlancer.commerce.SourceArchive;
import com.getlancer.hosting.HostingRepository;
import com.getlancer.products.ProductRepository;
import com.getlancer.products.ProductService;
import com.getlancer.publishing.PublishingCapacity;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import com.getlancer.shared.Pages;
import com.getlancer.shared.Rules;
import com.getlancer.shared.Support;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/** Education publication reuses existing contribution, storage, capacity and purchase authorities. */
@Service
public class EducationService {
  private static final String COLUMNS="id,product_id,owner_id,revision,status,draft,snapshot,source_hash,free_package_id,source_version_id,review_reason,submitted_at,reviewed_at,created_at,updated_at";
  private static final String PACKAGE_COLUMNS="id,owner_id,storage_key,sha256,size_bytes,entry_count,files,created_at";
  private static final String ELIGIBLE=ProductRepository.PUBLIC+" AND u.email_verified_at IS NOT NULL AND EXISTS(SELECT 1 FROM user_roles ur WHERE ur.user_id=u.id AND ur.role='DEVELOPER')";
  private static final String COLLEGE_FROM=ProductRepository.FROM+" JOIN college_project_metadata e ON e.product_id=p.id LEFT JOIN LATERAL (SELECT r.id,r.snapshot,r.reviewed_at FROM education_releases r WHERE r.product_id=p.id AND r.status='APPROVED' ORDER BY r.reviewed_at DESC,r.created_at DESC,r.id DESC LIMIT 1) er ON true";
  private final JdbcTemplate db;
  private final Security security;
  private final ProductService products;
  private final PublishingCapacity capacity;
  private final CommerceStorage storage;
  private final CommerceEducationBinding commerce;
  private final HostingRepository hosting;
  private final AdminService admin;
  private final EducationSnapshot schema;
  private final TransactionTemplate tx;
  public EducationService(JdbcTemplate db,Security security,ProductService products,PublishingCapacity capacity,
      CommerceStorage storage,CommerceEducationBinding commerce,HostingRepository hosting,AdminService admin,
      ObjectMapper json,PlatformTransactionManager manager){
    this.db=db;this.security=security;this.products=products;this.capacity=capacity;this.storage=storage;
    this.commerce=commerce;this.hosting=hosting;this.admin=admin;this.schema=new EducationSnapshot(json);this.tx=new TransactionTemplate(manager);
  }
  private static ApiError missing(){return new ApiError(404,"NOT_FOUND","Education release not found.");}
  private Map<String,Object> row(UUID id,boolean lock){var rows=db.queryForList("SELECT "+COLUMNS+" FROM education_releases WHERE id=?"+(lock?" FOR UPDATE":""),id);if(rows.isEmpty())throw missing();return rows.get(0);}
  private UUID actor(HttpServletRequest request,boolean operator,boolean approved){
    UUID actor=operator?security.admin(request):security.developer(request,approved);
    db.queryForList("SELECT id FROM users WHERE id=? FOR SHARE",actor);
    db.queryForList("SELECT user_id FROM user_roles WHERE user_id=? ORDER BY role FOR SHARE",actor);
    db.queryForList("SELECT token_hash FROM sessions WHERE user_id=? ORDER BY token_hash FOR SHARE",actor);
    db.queryForList("SELECT user_id FROM developer_profiles WHERE user_id=? FOR SHARE",actor);
    UUID fresh=operator?security.admin(request):security.developer(request,approved);
    if(!actor.equals(fresh))throw new ApiError(401,"UNAUTHENTICATED","Please sign in again.");
    if(operator&&!security.loadPrincipal(request).recentMfa())throw new ApiError(403,"FORBIDDEN","Recent administrator MFA is required for source inspection.");
    return actor;
  }
  private Map<String,Object> ownerProduct(UUID product,UUID owner){var rows=db.queryForList(ProductRepository.SELECT+" WHERE p.id=? AND p.owner_user_id=? FOR UPDATE OF p",product,owner);if(rows.isEmpty())throw missing();return rows.get(0);}
  private Map<String,Object> eligible(UUID product,UUID owner,boolean metadata){
    db.queryForList("SELECT id FROM products WHERE id=? FOR SHARE",product);db.queryForList("SELECT id FROM users WHERE id=? FOR SHARE",owner);
    db.queryForList("SELECT user_id FROM developer_profiles WHERE user_id=? FOR SHARE",owner);db.queryForList("SELECT user_id FROM user_roles WHERE user_id=? ORDER BY role FOR SHARE",owner);
    if(metadata)db.queryForList("SELECT product_id FROM college_project_metadata WHERE product_id=? FOR SHARE",product);
    var rows=db.queryForList(ProductRepository.SELECT+" WHERE p.id=? AND p.owner_user_id=? AND "+ELIGIBLE+(metadata?" AND EXISTS(SELECT 1 FROM college_project_metadata e WHERE e.product_id=p.id AND e.status='APPROVED')":""),product,owner);
    if(rows.isEmpty())throw new ApiError(409,"EDUCATION_NOT_ELIGIBLE","The approved public product, builder and contribution must remain eligible.");return rows.get(0);
  }
  private Map<String,Object> owned(UUID id,UUID owner){var original=row(id,false);if(!owner.equals(original.get("owner_id")))throw missing();commerce.lockProduct((UUID)original.get("product_id"));capacity.lock(owner);ownerProduct((UUID)original.get("product_id"),owner);return row(id,true);}
  private static void revision(Map<String,Object> body,Map<String,Object> row){if(!(body.get("revision") instanceof Number value))throw EducationSnapshot.invalid("Provide the revision you last read.");if(value.longValue()!=((Number)row.get("revision")).longValue())throw new ApiError(409,"EDUCATION_REVISION_CONFLICT","This release changed. Reload it and compare your edits.");}
  private static void editable(Map<String,Object> row){if(!"DRAFT".equals(row.get("status"))||row.get("submitted_at")!=null)throw new ApiError(409,"EDUCATION_IMMUTABLE","Create a new release to change submitted content or terms.");}
  private void audit(UUID release,UUID actor,String action,String reason,String hash){db.update("INSERT INTO education_audit(id,release_id,actor_id,action,reason,source_hash) VALUES(?,?,?,?,?,?)",UUID.randomUUID(),release,actor,action,reason,hash);}
  private Map<String,Object> academic(UUID release,boolean privateView){var rows=db.queryForList("SELECT institution,academic_year,branch,share_academic_details,revision FROM education_private_details WHERE release_id=?",release);var out=new LinkedHashMap<String,Object>();var row=rows.isEmpty()?Map.<String,Object>of("institution","","academic_year","","branch","","share_academic_details",false,"revision",1L):rows.get(0);out.put("revision",row.get("revision"));out.put("shareAcademicDetails",row.get("share_academic_details"));if(privateView||Boolean.TRUE.equals(row.get("share_academic_details"))){out.put("institution",row.get("institution"));out.put("academicYear",row.get("academic_year"));out.put("branch",row.get("branch"));}return out;}
  private Map<String,Object> core(Map<String,Object> draft,Map<String,Object> product){
    var result=new LinkedHashMap<>(draft);result.put("productId",product.get("id").toString());result.put("slug",product.get("slug"));result.put("title",product.get("title"));result.put("summary",product.get("summary"));
    var context=db.queryForList("SELECT language,problem,outcome,contribution FROM college_project_metadata WHERE product_id=?",product.get("id"));
    result.put("language",context.isEmpty()?Objects.toString(product.get("technology"),""):context.get(0).get("language"));
    result.put("problem",context.isEmpty()?product.get("summary"):context.get(0).get("problem"));result.put("outcome",context.isEmpty()?product.get("description"):context.get(0).get("outcome"));
    String contribution=Objects.toString(context.isEmpty()?product.get("contribution_text"):context.get(0).get("contribution"),"");result.put("contribution",contribution);return result;
  }
  private Map<String,Object> dto(Map<String,Object> row,boolean privateView){
    var out=new LinkedHashMap<String,Object>();out.put("id",row.get("id"));out.put("productId",row.get("product_id"));out.put("revision",row.get("revision"));out.put("status",row.get("status"));out.put("sourceHash",row.get("source_hash"));
    Map<String,Object> snapshot;if(row.get("snapshot")!=null)snapshot=schema.read(row.get("snapshot"));else{var product=db.queryForMap(ProductRepository.SELECT+" WHERE p.id=?",row.get("product_id"));snapshot=core(schema.read(row.get("draft")),product);}
    out.put("snapshot",privateView?snapshot:schema.publicView(snapshot));out.put("academic",academic((UUID)row.get("id"),privateView));out.put("componentLinks",components(snapshot,false));out.putAll(demo(snapshot,(UUID)row.get("product_id"),false));out.put("teams",teams((UUID)row.get("product_id"),(UUID)row.get("owner_id")));
    if(privateView){
      out.put("reviewReason",row.get("review_reason"));out.put("freePackageId",row.get("free_package_id"));out.put("versionId",row.get("source_version_id"));
      if(row.get("snapshot")==null&&"FREE".equals(snapshot.get("mode"))&&row.get("free_package_id")!=null)out.put("sourceBinding",freeBinding(row,snapshot));
      if(row.get("snapshot")==null&&"PAID".equals(snapshot.get("mode"))&&row.get("source_version_id")!=null)out.putAll(draftPaidSource(row));
    }return out;
  }
  /** Private draft preview is read-only; submission performs the authoritative source freeze. */
  private Map<String,Object> draftPaidSource(Map<String,Object> release){
    var rows=db.queryForList("SELECT v.id,v.version,v.sha256,v.size_bytes,v.entry_count,v.manifest_files,v.safe_source_files,v.license_terms,v.status,t.id AS template_id,t.price_minor,t.currency,t.status AS template_status FROM source_versions v JOIN source_templates t ON t.id=v.template_id WHERE v.id=? AND t.product_id=? AND t.seller_id=?",release.get("source_version_id"),release.get("product_id"),release.get("owner_id"));
    if(rows.isEmpty())return Map.of("sourceStatus","Selected source is unavailable.");var row=rows.get(0);var binding=new LinkedHashMap<String,Object>();
    binding.put("versionId",row.get("id").toString());binding.put("templateId",row.get("template_id").toString());binding.put("version",row.get("version"));binding.put("sha256",row.get("sha256"));binding.put("sizeBytes",row.get("size_bytes"));binding.put("entryCount",row.get("entry_count"));binding.put("licenseTerms",row.get("license_terms"));binding.put("manifestFiles",List.of(Objects.toString(row.get("manifest_files"),"").split("\n")));binding.put("files",row.get("safe_source_files")==null?List.of():schema.list(row.get("safe_source_files")));
    return Map.of("sourceBinding",binding,"sourcePriceMinor",row.get("price_minor"),"sourceCurrency",row.get("currency"),"sourceStatus","APPROVED".equals(row.get("status"))&&"ACTIVE".equals(row.get("template_status"))?"Reviewed paid source selected; final eligibility is checked at submission.":"Selected source is awaiting current approval.");
  }
  @Transactional(readOnly=true)
  public Map<String,Object> own(HttpServletRequest request){UUID owner=security.developer(request,false);var page=Pages.query(db,request,"SELECT "+COLUMNS+" FROM education_releases WHERE owner_id=? ORDER BY created_at DESC,id",owner);page.put("items",Pages.items(page).stream().map(row->dto(row,true)).toList());return page;}
  @Transactional(readOnly=true)
  public Map<String,Object> ownDetail(UUID id,HttpServletRequest request){UUID owner=security.developer(request,false);var row=row(id,false);if(!owner.equals(row.get("owner_id")))throw missing();return dto(row,true);}
  private UUID version(Map<String,Object> body,String mode,UUID product,UUID owner){if(!"PAID".equals(mode)){if(body.get("versionId")!=null)throw EducationSnapshot.invalid("Only paid access binds a paid source version.");return null;}if(body.get("versionId")==null)return null;UUID version=Support.uuid(body.get("versionId"));if(db.queryForList("SELECT v.id FROM source_versions v JOIN source_templates t ON t.id=v.template_id WHERE v.id=? AND t.product_id=? AND t.seller_id=?",version,product,owner).isEmpty())throw missing();return version;}
  @Transactional
  public Map<String,Object> create(UUID product,Map<String,Object> body,HttpServletRequest request){
    commerce.lockProduct(product);UUID owner=actor(request,false,true);capacity.lock(owner);ownerProduct(product,owner);
    if(db.queryForObject("SELECT count(*) FROM education_releases WHERE product_id=?",Integer.class,product)>=100)throw new ApiError(409,"EDUCATION_RELEASE_LIMIT","At most one hundred release identities are retained per project.");
    var draft=schema.draft(body);UUID version=version(body,draft.get("mode").toString(),product,owner),id=UUID.randomUUID();
    db.update("INSERT INTO education_releases(id,product_id,owner_id,draft,source_version_id) VALUES(?,?,?,?::jsonb,?)",id,product,owner,schema.encode(draft),version);
    db.update("INSERT INTO education_private_details(release_id) VALUES(?)",id);audit(id,owner,"CREATED","Unfinished draft created; no source rights granted to buyers.",null);return dto(row(id,false),true);
  }
  @Transactional
  public Map<String,Object> save(UUID id,Map<String,Object> body,HttpServletRequest request){
    UUID owner=actor(request,false,true);var row=owned(id,owner);editable(row);revision(body,row);UUID product=(UUID)row.get("product_id");var draft=schema.draft(body);UUID version=version(body,draft.get("mode").toString(),product,owner);
    db.update("UPDATE education_releases SET draft=?::jsonb,source_version_id=?,free_package_id=CASE WHEN ?='FREE' THEN free_package_id ELSE NULL END,revision=revision+1,updated_at=now() WHERE id=?",schema.encode(draft),version,draft.get("mode"),id);return dto(row(id,false),true);
  }
  public Map<String,Object> upload(UUID id,MultipartFile file,long revision,boolean rights,HttpServletRequest request)throws IOException{
    if(!rights)throw new ApiError(400,"RIGHTS_CONSENT_REQUIRED","Confirm your permission to distribute this exact source.");
    if(file.getSize()>SourceArchive.MAX_COMPRESSED)throw new ApiError(413,"PAYLOAD_TOO_LARGE","Free source archives are limited to five MiB.");
    byte[] bytes=file.getBytes();var inspection=SourceArchive.inspect(bytes);UUID owner=tx.execute(status->{UUID actor=actor(request,false,true);var row=owned(id,actor);editable(row);revision(Map.of("revision",revision),row);if(!"FREE".equals(schema.read(row.get("draft")).get("mode")))throw EducationSnapshot.invalid("Only free drafts accept a free source upload.");return actor;});
    UUID pack=UUID.randomUUID();String key=storage.put(pack,bytes,inspection.sha256());var entered=new AtomicBoolean();var rolledBack=new AtomicBoolean();
    try{return tx.execute(status->{entered.set(true);TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int completion){if(completion==STATUS_ROLLED_BACK)rolledBack.set(true);}});
      UUID actor=actor(request,false,true);if(!owner.equals(actor))throw missing();var row=owned(id,owner);editable(row);revision(Map.of("revision",revision),row);if(!"FREE".equals(schema.read(row.get("draft")).get("mode")))throw EducationSnapshot.invalid("Only free drafts accept source uploads.");
      db.update("INSERT INTO education_free_packages(id,owner_id,storage_key,sha256,size_bytes,entry_count,files) VALUES(?,?,?,?,?,?,?::jsonb)",pack,owner,key,inspection.sha256(),inspection.sizeBytes(),inspection.entryCount(),schema.encode(inspection.files()));
      db.update("UPDATE education_releases SET free_package_id=?,revision=revision+1,updated_at=now() WHERE id=?",pack,id);audit(id,owner,"SOURCE_UPLOADED","Statically inspected source; uploaded code was never executed.",inspection.sha256());return dto(row(id,false),true);});
    }catch(RuntimeException error){if(!entered.get()||rolledBack.get())storage.discardUnlinked(key);throw error;}
  }
  private Map<String,Object> freeBinding(Map<String,Object> row,Map<String,Object> snapshot){if(row.get("free_package_id")==null)throw new ApiError(400,"EDUCATION_SOURCE_REQUIRED","Upload the exact free package before submission.");var rows=db.queryForList("SELECT "+PACKAGE_COLUMNS+" FROM education_free_packages WHERE id=? AND owner_id=?",row.get("free_package_id"),row.get("owner_id"));if(rows.isEmpty())throw missing();var pack=rows.get(0);var out=new LinkedHashMap<String,Object>();out.put("packageId",pack.get("id").toString());out.put("sha256",pack.get("sha256"));out.put("sizeBytes",pack.get("size_bytes"));out.put("entryCount",pack.get("entry_count"));out.put("files",schema.list(pack.get("files")));out.put("licenseTerms",EducationSnapshot.object(snapshot.get("package")).get("licenseTerms"));return out;}
  private Map<String,Object> paidBinding(Map<String,Object> row,Map<String,Object> snapshot){if(row.get("source_version_id")==null)throw new ApiError(400,"EDUCATION_SOURCE_REQUIRED","Choose the exact reviewed paid source version.");var frozen=new LinkedHashMap<>(commerce.freezePaid((UUID)row.get("source_version_id"),(UUID)row.get("product_id"),(UUID)row.get("owner_id")));snapshot.put("priceMinor",frozen.remove("priceMinor"));snapshot.put("currency",frozen.remove("currency"));if(!EducationSnapshot.object(snapshot.get("package")).get("licenseTerms").equals(frozen.get("licenseTerms")))throw new ApiError(409,"EDUCATION_LICENSE_MISMATCH","Disclose the exact approved paid source license.");return frozen;}
  private void metadata(UUID product,Map<String,Object> snapshot,boolean approved){
    String prerequisites=String.join("; ",((List<?>)EducationSnapshot.object(snapshot.get("package")).get("prerequisites")).stream().map(Object::toString).toList());
    String sql="INSERT INTO college_project_metadata(product_id,category,language,problem,outcome,prerequisites,contribution,status) VALUES(?,?,?,?,?,?,?,?)";
    if(approved)sql+=" ON CONFLICT(product_id) DO UPDATE SET category=excluded.category,language=excluded.language,problem=excluded.problem,outcome=excluded.outcome,prerequisites=excluded.prerequisites,contribution=excluded.contribution,status='APPROVED',revision=college_project_metadata.revision+1,review_reason=NULL,updated_at=now()";
    else sql+=" ON CONFLICT(product_id) DO NOTHING";
    db.update(sql,product,snapshot.get("category"),snapshot.get("language"),snapshot.get("problem"),snapshot.get("outcome"),prerequisites.substring(0,Math.min(2000,prerequisites.length())),snapshot.get("contribution"),approved?"APPROVED":"PENDING");
  }
  @Transactional
  public Map<String,Object> submit(UUID id,Map<String,Object> body,HttpServletRequest request){
    UUID owner=actor(request,false,true);var row=owned(id,owner);editable(row);revision(body,row);if(!Boolean.TRUE.equals(body.get("rightsConsent")))throw new ApiError(400,"RIGHTS_CONSENT_REQUIRED","Confirm current contribution and distribution rights.");
    UUID product=(UUID)row.get("product_id");var snapshot=core(schema.read(row.get("draft")),eligible(product,owner,false));schema.complete(snapshot);
    if(Objects.toString(snapshot.get("contribution"),"").length()<20)throw EducationSnapshot.invalid("Complete the existing approved product contribution before publishing.");
    capacity.requireCollegeMove(owner,product);snapshot.put("componentLinks",components(snapshot,true));var demo=demo(snapshot,product,true);if("HOSTED".equals(snapshot.get("demoMode"))){snapshot.put("demoUrl",demo.get("demoUrl"));snapshot.put("hostedDemoId",demo.get("hostedDemoId"));}
    String mode=snapshot.get("mode").toString();if(mode.equals("FREE"))snapshot.put("sourceBinding",freeBinding(row,snapshot));else if(mode.equals("PAID"))snapshot.put("sourceBinding",paidBinding(row,snapshot));
    metadata(product,snapshot,false);String hash=schema.hash(snapshot);db.update("UPDATE education_releases SET snapshot=?::jsonb,source_hash=?,submitted_at=now(),status='PENDING',revision=revision+1,updated_at=now() WHERE id=?",schema.encode(snapshot),hash,id);audit(id,owner,"SUBMITTED","Exact source and package license snapshot frozen for independent review.",hash);return dto(row(id,false),true);
  }
  @Transactional
  public Map<String,Object> academic(UUID id,Map<String,Object> body,HttpServletRequest request){
    UUID owner=actor(request,false,false);owned(id,owner);var rows=db.queryForList("SELECT revision,institution,academic_year,branch,share_academic_details FROM education_private_details WHERE release_id=? FOR UPDATE",id);
    if(rows.isEmpty())throw missing();var previous=rows.get(0);revision(body,previous);
    db.update("UPDATE education_private_details SET institution=?,academic_year=?,branch=?,share_academic_details=?,revision=revision+1 WHERE release_id=?",
        body.containsKey("institution")?Support.text(body,"institution",0,160):previous.get("institution"),
        body.containsKey("academicYear")?Support.text(body,"academicYear",0,40):previous.get("academic_year"),
        body.containsKey("branch")?Support.text(body,"branch",0,100):previous.get("branch"),
        body.containsKey("shareAcademicDetails")?Boolean.TRUE.equals(body.get("shareAcademicDetails")):previous.get("share_academic_details"),id);
    return dto(row(id,false),true);
  }
  @Transactional
  public Map<String,Object> queue(HttpServletRequest request){actor(request,true,false);var page=Pages.query(db,request,"SELECT "+COLUMNS+" FROM education_releases WHERE submitted_at IS NOT NULL ORDER BY CASE WHEN status='PENDING' THEN 0 ELSE 1 END,submitted_at,id");page.put("items",Pages.items(page).stream().map(row->dto(row,true)).toList());return page;}
  @Transactional
  public Map<String,Object> adminDetail(UUID id,HttpServletRequest request){actor(request,true,false);return dto(row(id,false),true);}
  @Transactional
  public Map<String,Object> review(UUID id,Map<String,Object> body,HttpServletRequest request){
    var original=row(id,false);commerce.lockProduct((UUID)original.get("product_id"));UUID reviewer=actor(request,true,false),owner=(UUID)original.get("owner_id"),product=(UUID)original.get("product_id");capacity.lock(owner);ownerProduct(product,owner);var row=row(id,true);revision(body,row);String hash=Support.text(body,"sourceHash",64,64);if(!hash.equals(row.get("source_hash"))||!schema.hash(schema.read(row.get("snapshot"))).equals(hash))throw new ApiError(409,"EDUCATION_HASH_CONFLICT","Review the exact currently submitted content hash.");
    String decision=Support.text(body,"decision",3,30),reason=Support.text(body,"reason",20,2000),next;
    if(decision.equals("APPROVE")){
      if(!"PENDING".equals(row.get("status")))throw new ApiError(409,"INVALID_STATE_TRANSITION","This release is not pending review.");
      if(!Boolean.TRUE.equals(body.get("rightsReviewed"))||!Boolean.TRUE.equals(body.get("packageReviewed")))throw new ApiError(400,"EDUCATION_REVIEW_REQUIRED","Review source evidence, package disclosures and rights explicitly.");
      eligible(product,owner,false);capacity.requireCollegeMove(owner,product);var snapshot=schema.read(row.get("snapshot"));schema.completeReview(snapshot);components(snapshot,true);demo(snapshot,product,true);
      if("FREE".equals(snapshot.get("mode"))){if(db.queryForObject("SELECT count(*) FROM education_audit WHERE release_id=? AND actor_id=? AND action='PACKAGE_REVIEW_DOWNLOAD' AND source_hash=?",Integer.class,id,reviewer,hash)==0)throw new ApiError(409,"EDUCATION_PACKAGE_REVIEW_REQUIRED","Inspect this exact source package with recent MFA before approval.");if(!schema.hash(freeBinding(row,snapshot)).equals(schema.hash(EducationSnapshot.object(snapshot.get("sourceBinding")))))throw new ApiError(409,"EDUCATION_SOURCE_CHANGED","The frozen source binding changed.");}
      if("PAID".equals(snapshot.get("mode"))){var checked=new LinkedHashMap<>(snapshot);var binding=paidBinding(row,checked);if(!schema.hash(binding).equals(schema.hash(EducationSnapshot.object(snapshot.get("sourceBinding"))))||!Objects.toString(checked.get("priceMinor")).equals(Objects.toString(snapshot.get("priceMinor")))||!Objects.equals(checked.get("currency"),snapshot.get("currency")))throw new ApiError(409,"EDUCATION_SOURCE_CHANGED","The approved paid offer changed; create a new release.");}
      metadata(product,snapshot,true);next="APPROVED";
    }else if(decision.equals("CHANGES_REQUESTED")){if(!"PENDING".equals(row.get("status")))throw new ApiError(409,"INVALID_STATE_TRANSITION","This release is not pending review.");next="CHANGES_REQUESTED";}
    else if(decision.equals("SUSPEND")){if(!Set.of("PENDING","APPROVED").contains(row.get("status")))throw new ApiError(409,"INVALID_STATE_TRANSITION","This release cannot be suspended.");admin.productAction(product,"suspend",Map.of("reason",reason),request);db.update("UPDATE college_project_metadata SET status='SUSPENDED',revision=revision+1,updated_at=now() WHERE product_id=?",product);next="SUSPENDED";}
    else throw EducationSnapshot.invalid("Choose a supported review decision.");
    actor(request,true,false);db.update("UPDATE education_releases SET status=?,review_reason=?,reviewed_at=clock_timestamp(),revision=revision+1,updated_at=clock_timestamp() WHERE id=?",next,reason,id);audit(id,reviewer,next,reason,hash);return dto(row(id,false),true);
  }
  private List<Map<String,Object>> teams(UUID product,UUID owner){return db.queryForList("SELECT t.id,t.slug,t.name FROM team_projects tp JOIN teams t ON t.id=tp.team_id JOIN team_members m ON m.team_id=t.id AND m.user_id=tp.consented_by JOIN users tu ON tu.id=t.owner_id JOIN developer_profiles td ON td.user_id=tu.id WHERE tp.product_id=? AND tp.consented_by=? AND t.status='ACTIVE' AND (m.membership_type='PERMANENT' OR m.expires_at>now()) AND tu.account_status='ACTIVE' AND tu.email_verified_at IS NOT NULL AND td.approval_status='APPROVED' AND EXISTS(SELECT 1 FROM user_roles tr WHERE tr.user_id=tu.id AND tr.role='DEVELOPER') ORDER BY t.id LIMIT 20",product,owner);}
  private List<Map<String,Object>> components(Map<String,Object> snapshot,boolean required){
    var result=new ArrayList<Map<String,Object>>();Object raw=snapshot.get("componentLinks");if(!(raw instanceof List<?> links))return result;
    for(Object value:links){var link=EducationSnapshot.object(value);UUID component=Support.uuid(link.get("componentId"));long revision=((Number)link.get("revision")).longValue();
      var rows=db.queryForList("SELECT c.slug,r.source,r.source_sha256,d.display_name AS creator,d.slug AS creator_slug FROM component_releases r JOIN component_entries c ON c.id=r.component_id JOIN users u ON u.id=c.owner_id JOIN developer_profiles d ON d.user_id=c.owner_id WHERE r.component_id=? AND r.revision=? AND c.status IN ('ACTIVE','ARCHIVED','DRAFT','PENDING') AND c.published_at IS NOT NULL AND c.withdrawn_at IS NULL AND u.account_status='ACTIVE' AND u.email_verified_at IS NOT NULL AND d.approval_status='APPROVED' AND EXISTS(SELECT 1 FROM user_roles ur WHERE ur.user_id=u.id AND ur.role='DEVELOPER')",component,revision);
      if(rows.isEmpty()){if(required)throw new ApiError(409,"EDUCATION_COMPONENT_UNAVAILABLE","A linked component version is no longer eligible.");continue;}
      var row=rows.get(0);String license=Objects.toString(schema.read(row.get("source")).get("license"),"");if(license.isBlank()||!license.equals(link.get("license"))||link.get("sha256")!=null&&!link.get("sha256").equals(row.get("source_sha256"))){if(required)throw new ApiError(409,"EDUCATION_COMPONENT_UNAVAILABLE","The exact component license or content could not be verified.");continue;}
      var safe=new LinkedHashMap<String,Object>();for(String field:List.of("componentId","revision","license","attribution"))safe.put(field,link.get(field));safe.put("sha256",row.get("source_sha256"));
      if(!required){safe.put("slug",row.get("slug"));safe.put("creator",row.get("creator"));safe.put("creatorSlug",row.get("creator_slug"));}result.add(safe);
    }return result;
  }
  private Map<String,Object> demo(Map<String,Object> snapshot,UUID product,boolean required){
    String mode=Objects.toString(snapshot.get("demoMode"),"SOURCE_ONLY"),url=Objects.toString(snapshot.get("demoUrl"),"");
    if(mode.equals("EXTERNAL")){try{if(url.isBlank())throw new IllegalArgumentException();Rules.safeUrl(url);}catch(IllegalArgumentException error){if(required)throw new ApiError(400,"UNSAFE_EXTERNAL_URL","Use a public HTTPS URL without private-network addresses or credentials.");return Map.of("demoAvailability","External link unavailable","demoUrl","");}return Map.of("demoAvailability","Unverified external link","demoUrl",url);}
    if(mode.equals("HOSTED")){Object raw=hosting.publicProduct(product).get("items");var candidates=raw instanceof List<?> list?list:List.of();for(Object value:candidates){var item=EducationSnapshot.object(value);if(snapshot.get("hostedDemoId")!=null?!snapshot.get("hostedDemoId").equals(item.get("id").toString()):!url.isBlank()&&!url.equals(item.get("url")))continue;return Map.of("demoAvailability","Hosted demo available","demoUrl",item.get("url"),"hostedDemoId",item.get("id").toString());}if(required)throw new ApiError(409,"EDUCATION_DEMO_UNAVAILABLE","Hosted mode requires an actually deployed, currently eligible hosted demo.");return Map.of("demoAvailability","Hosted demo unavailable","demoUrl","");}
    return Map.of("demoAvailability","Source only; no hosted demo","demoUrl","");
  }
  private Map<String,Object> publicRelease(UUID id){var row=row(id,false);if(!"APPROVED".equals(row.get("status")))throw missing();eligible((UUID)row.get("product_id"),(UUID)row.get("owner_id"),true);if(!schema.hash(schema.read(row.get("snapshot"))).equals(row.get("source_hash")))throw new ApiError(409,"EDUCATION_INTEGRITY_ERROR","The frozen education agreement could not be verified.");return row;}
  @Transactional
  public Map<String,Object> sourceOffer(UUID id){commerce.lockProduct((UUID)row(id,false).get("product_id"));var row=publicRelease(id);var snapshot=schema.read(row.get("snapshot"));var out=new LinkedHashMap<String,Object>();out.put("release",dto(row,false));out.put("package",EducationSnapshot.object(schema.publicView(snapshot).get("sourceBinding")));if("FREE".equals(snapshot.get("mode"))){out.put("available",true);out.put("checkoutAvailable",false);}else if("PAID".equals(snapshot.get("mode"))){out.putAll(commerce.offer(id));out.put("available",Boolean.TRUE.equals(out.get("checkoutAvailable")));}else{out.put("available",false);out.put("checkoutAvailable",false);out.put("reason","Showcase only; no source package is distributed.");}return out;}
  @Transactional
  public ResponseEntity<byte[]> source(UUID id){
    commerce.lockProduct((UUID)row(id,false).get("product_id"));var release=publicRelease(id);
    db.queryForList("SELECT id FROM education_releases WHERE id=? FOR SHARE",id);
    return sourcePackage(id,release);
  }
  @Transactional
  public ResponseEntity<byte[]> inspect(UUID id,HttpServletRequest request){
    commerce.lockProduct((UUID)row(id,false).get("product_id"));UUID reviewer=actor(request,true,false);var release=row(id,true);
    var response=sourcePackage(id,release);actor(request,true,false);
    audit(id,reviewer,"PACKAGE_REVIEW_DOWNLOAD","Downloaded checksum-verified source for exact package inspection.",(String)release.get("source_hash"));
    return response;
  }
  private ResponseEntity<byte[]> sourcePackage(UUID id,Map<String,Object> row){
    var content=schema.read(row.get("snapshot")!=null?row.get("snapshot"):row.get("draft"));if(!"FREE".equals(content.get("mode"))||row.get("free_package_id")==null)throw missing();
    var packs=db.queryForList("SELECT "+PACKAGE_COLUMNS+" FROM education_free_packages WHERE id=? AND owner_id=?",row.get("free_package_id"),row.get("owner_id"));if(packs.isEmpty())throw missing();var pack=packs.get(0);
    byte[] bytes=storage.read((String)pack.get("storage_key"),(String)pack.get("sha256"),((Number)pack.get("size_bytes")).intValue());
    return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip")).header(HttpHeaders.CACHE_CONTROL,"private, no-store").header("X-Content-Type-Options","nosniff").header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"education-source-"+id+".zip\"").body(bytes);
  }
  /** The root report adapter pins these canonical facts, never caller-supplied hashes or academics. */
  @Transactional
  public Map<String,Object> frozenReportArtifact(UUID id){commerce.lockProduct((UUID)row(id,false).get("product_id"));var row=publicRelease(id);return Map.of("releaseId",id,"productId",row.get("product_id"),"sourceHash",row.get("source_hash"),"snapshot",schema.read(row.get("snapshot")));}
  @Transactional(readOnly=true)
  public Map<String,Object> college(String q,String category,String language,String builder,String mode,String difficulty,int page){
    if(q.length()>200||language.length()>100||builder.length()>140||page<0||page>10000||!category.isBlank()&&!EducationSnapshot.EVIDENCE.containsKey(category)||!mode.isBlank()&&!Set.of("SHOWCASE","FREE","PAID").contains(mode)||!difficulty.isBlank()&&!EducationSnapshot.DIFFICULTIES.contains(difficulty))throw EducationSnapshot.invalid("Choose valid college filters.");
    String filters=" WHERE "+ELIGIBLE+" AND e.status='APPROVED' AND (? OR coalesce(er.snapshot->>'category',e.category)=?) AND (? OR d.slug=?) AND (? OR coalesce(er.snapshot->>'language',e.language) ILIKE ?) AND (? OR p.title ILIKE ? OR p.summary ILIKE ?) AND (? OR coalesce(er.snapshot->>'mode','SHOWCASE')=?) AND (? OR er.snapshot->>'difficulty'=?)";
    var args=new ArrayList<Object>();String term=like(q),lang=like(language);Collections.addAll(args,category.isBlank(),category,builder.isBlank(),builder,language.isBlank(),lang,q.isBlank(),term,term,mode.isBlank(),mode,difficulty.isBlank(),difficulty);
    long total=db.queryForObject("SELECT count(*)"+COLLEGE_FROM+filters,Long.class,args.toArray());args.add(page*12);var rows=db.queryForList(ProductRepository.PROJECTION+",e.product_id AS education_product_id,e.category AS education_category,e.language AS education_language,e.problem,e.outcome,e.prerequisites,e.contribution,e.institution,e.academic_year,e.branch,e.share_academic_details,e.revision AS metadata_revision,er.id AS education_release_id"+COLLEGE_FROM+filters+" ORDER BY p.updated_at DESC,p.id LIMIT 12 OFFSET ?",args.toArray());var items=products.dtos(rows);for(int index=0;index<rows.size();index++)items.get(index).put("education",education(rows.get(index)));return Map.of("items",items,"page",page,"totalItems",total,"hasMore",(page+1)*12<total);
  }
  private static String like(String value){return "%"+value.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";}
  @Transactional(readOnly=true)
  public Map<String,Object> detail(String slug){var rows=db.queryForList(ProductRepository.PROJECTION+",e.product_id AS education_product_id,e.category AS education_category,e.language AS education_language,e.problem,e.outcome,e.prerequisites,e.contribution,e.institution,e.academic_year,e.branch,e.share_academic_details,e.revision AS metadata_revision,er.id AS education_release_id"+COLLEGE_FROM+" WHERE p.slug=? AND "+ELIGIBLE+" AND e.status='APPROVED'",slug);if(rows.isEmpty())throw missing();var out=products.detail(slug);out.put("education",education(rows.get(0)));return out;}
  private Map<String,Object> education(Map<String,Object> product){if(product.get("education_release_id")!=null){var release=dto(row((UUID)product.get("education_release_id"),false),false);var out=new LinkedHashMap<>(EducationSnapshot.object(release.get("snapshot")));out.put("release",release);out.put("academic",release.get("academic"));out.put("componentLinks",release.get("componentLinks"));out.put("teams",release.get("teams"));out.put("demoAvailability",release.get("demoAvailability"));out.put("demoUrl",release.get("demoUrl"));out.put("executionMode","SOURCE_ONLY".equals(out.get("demoMode"))?"Source only":release.get("demoAvailability"));return out;}var legacy=new LinkedHashMap<String,Object>();legacy.put("category",product.get("education_category"));legacy.put("language",product.get("education_language"));for(String field:List.of("problem","outcome","prerequisites","contribution"))legacy.put(field,product.get(field));legacy.put("mode","SHOWCASE");legacy.put("demoMode","SOURCE_ONLY");legacy.put("executionMode","Source only");legacy.put("demoAvailability","Source only; no hosted demo");legacy.put("shareAcademicDetails",product.get("share_academic_details"));if(Boolean.TRUE.equals(product.get("share_academic_details"))){legacy.put("institution",product.get("institution"));legacy.put("academicYear",product.get("academic_year"));legacy.put("branch",product.get("branch"));}return legacy;}
}
