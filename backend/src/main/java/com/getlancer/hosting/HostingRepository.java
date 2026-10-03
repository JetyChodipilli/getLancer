package com.getlancer.hosting;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.shared.*;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HostingRepository {
  private final JdbcTemplate db;private final HostingConfiguration config;private final ObjectMapper json;
  private static final String ELIGIBLE="u.account_status='ACTIVE' AND u.email_verified_at IS NOT NULL AND d.approval_status='APPROVED' AND EXISTS(SELECT 1 FROM user_roles r WHERE r.user_id=u.id AND r.role='DEVELOPER') AND p.owner_user_id=u.id AND p.approval_status='APPROVED' AND p.lifecycle_status='ACTIVE' AND p.visibility='PUBLIC' AND EXISTS(SELECT 1 FROM repository_verifications rv WHERE rv.product_id=p.id AND rv.status='VERIFIED' AND rv.repository_url=p.repository_url)";
  private static final String CURRENT="EXISTS(SELECT 1 FROM products p JOIN users u ON u.id=h.owner_id JOIN developer_profiles d ON d.user_id=u.id WHERE p.id=h.product_id AND "+ELIGIBLE+")";
  private static final String SERVABLE="h.status='APPROVED' AND h.deployment_state='READY' AND h.desired_state='PUBLISHED' AND h.expires_at>now() AND "+CURRENT;
  private static final String VIEW="SELECT h.*,p.title AS product_title,("+SERVABLE+") AS servable FROM hosted_demos h JOIN products p ON p.id=h.product_id ";
  public HostingRepository(JdbcTemplate db,HostingConfiguration config,ObjectMapper json){this.db=db;this.config=config;this.json=json;}
  private static ApiError missing(){return new ApiError(404,"NOT_FOUND","Hosted demo not found.");}
  public void lockOwner(UUID owner){db.queryForList("SELECT id FROM users WHERE id=? FOR SHARE",owner);db.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"hosting-owner:"+owner);lockActor(owner);}
  public void lockActor(UUID actor){db.queryForList("SELECT id FROM users WHERE id=? FOR SHARE",actor);db.queryForList("SELECT user_id FROM developer_profiles WHERE user_id=? FOR SHARE",actor);db.queryForList("SELECT user_id FROM user_roles WHERE user_id=? ORDER BY role FOR SHARE",actor);db.queryForList("SELECT token_hash FROM sessions WHERE user_id=? ORDER BY token_hash FOR SHARE",actor);}
  public void eligible(UUID owner,UUID product){
    db.queryForList("SELECT id FROM products WHERE id=? FOR SHARE",product);db.queryForList("SELECT product_id FROM repository_verifications WHERE product_id=? FOR SHARE",product);
    if(db.queryForObject("SELECT count(*) FROM products p JOIN users u ON u.id=p.owner_user_id JOIN developer_profiles d ON d.user_id=u.id WHERE p.id=? AND u.id=? AND "+ELIGIBLE,Integer.class,product,owner)==0)throw new ApiError(409,"HOSTING_SOURCE_NOT_ELIGIBLE","A current approved public proof product with verified repository ownership and an approved builder profile is required.");
  }
  public void capacity(UUID owner,boolean reservation){int count=db.queryForObject(reservation?"SELECT count(*) FROM hosted_demos WHERE owner_id=? AND deployment_state IN ('CREATING','UNKNOWN','READY','DELETE_PENDING')":"SELECT count(*) FROM hosted_demos WHERE owner_id=?",Integer.class,owner);if(count>=(reservation?3:10))throw new ApiError(409,reservation?"HOSTING_ACTIVE_LIMIT":"HOSTING_RECORD_LIMIT",reservation?"Withdraw and confirm a deployment before creating another; at most three unresolved deployments are allowed.":"At most ten immutable hosted package records are retained per builder.");}
  public boolean versionExists(UUID owner,UUID product,String version){return db.queryForObject("SELECT count(*) FROM hosted_demos WHERE owner_id=? AND product_id=? AND version=?",Integer.class,owner,product,version)>0;}
  public Map<String,Object> record(UUID id,boolean lock){var rows=db.queryForList("SELECT * FROM hosted_demos WHERE id=?"+(lock?" FOR UPDATE":""),id);if(rows.isEmpty())throw missing();return rows.get(0);}
  public UUID owner(UUID id){return (UUID)record(id,false).get("owner_id");}
  public Map<String,Object> owned(UUID id,UUID owner,boolean lock){var row=record(id,lock);if(!owner.equals(row.get("owner_id")))throw missing();return row;}
  public void insert(UUID id,UUID owner,UUID product,String title,String version,String storage,StaticArchive.Inspection archive){try{db.update("INSERT INTO hosted_demos(id,owner_id,product_id,title,version,storage_key,archive_sha256,manifest_sha256,size_bytes,expanded_bytes,file_count,files) VALUES(?,?,?,?,?,?,?,?,?,?,?,?::jsonb)",id,owner,product,title,version,storage,archive.archiveSha256(),archive.manifestSha256(),archive.sizeBytes(),archive.expandedBytes(),archive.files().size(),json.writeValueAsString(archive.manifest()));}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}}
  public void audit(UUID id,UUID actor,String action,String detail){db.update("INSERT INTO hosting_audit(id,demo_id,actor_id,action,detail) VALUES(?,?,?,?,?)",UUID.randomUUID(),id,actor,action,detail);}
  public void submit(UUID id){db.update("UPDATE hosted_demos SET status='PENDING',submitted_at=now(),review_reason=null,updated_at=now() WHERE id=?",id);}
  public boolean downloaded(UUID id,UUID actor,String hash){return db.queryForObject("SELECT count(*) FROM hosting_audit WHERE demo_id=? AND actor_id=? AND action='PACKAGE_DOWNLOADED' AND detail=?",Integer.class,id,actor,"Verified immutable archive SHA-256 "+hash)>0;}
  public void review(UUID id,String status,String reason,UUID actor){db.update("UPDATE hosted_demos SET status=?,review_reason=?,reviewed_by=?,reviewed_at=now(),updated_at=now() WHERE id=?",status,reason,actor,id);}
  public void reserve(UUID id,UUID deployment,Instant expiry){db.update("UPDATE hosted_demos SET deployment_id=?,expires_at=?,deployment_state='CREATING',updated_at=now() WHERE id=? AND deployment_state='NOT_CREATED' AND desired_state='PUBLISHED'",deployment,Timestamp.from(expiry),id);}
  public void retryIntent(UUID id){db.update("UPDATE hosted_demos SET deployment_state='CREATING',updated_at=now() WHERE id=? AND desired_state='PUBLISHED' AND deployment_state IN ('CREATING','UNKNOWN')",id);}
  public void unknown(UUID id){db.update("UPDATE hosted_demos SET deployment_state='UNKNOWN',updated_at=now() WHERE id=? AND deployment_state='CREATING' AND desired_state='PUBLISHED'",id);}
  public void withdraw(UUID id){db.update("UPDATE hosted_demos SET desired_state='WITHDRAWN',deployment_state=CASE WHEN deployment_id IS NULL OR deployment_state='DELETED' THEN 'DELETED' ELSE 'DELETE_PENDING' END,updated_at=now() WHERE id=?",id);}
  public boolean observed(UUID id,DemoPublisherClient.Metadata facts){if(facts.state().equals("DELETED"))return db.update("UPDATE hosted_demos SET desired_state='WITHDRAWN',deployment_state='DELETED',updated_at=now() WHERE id=? AND deployment_id=? AND deployment_state<>'DELETED'",id,facts.id())==1;return db.update("UPDATE hosted_demos SET deployment_state='READY',updated_at=now() WHERE id=? AND deployment_id=? AND desired_state='PUBLISHED' AND deployment_state IN ('CREATING','UNKNOWN','READY')",id,facts.id())==1;}
  public Map<String,Object> sources(UUID actor,HttpServletRequest r){return Pages.query(db,r,"SELECT p.id,p.title FROM products p JOIN users u ON u.id=p.owner_user_id JOIN developer_profiles d ON d.user_id=u.id WHERE u.id=? AND "+ELIGIBLE+" ORDER BY p.created_at DESC,p.id",actor);}
  public Map<String,Object> list(UUID actor,HttpServletRequest r,boolean admin){var page=admin?Pages.query(db,r,VIEW+"ORDER BY h.created_at DESC,h.id"):Pages.query(db,r,VIEW+"WHERE h.owner_id=? ORDER BY h.created_at DESC,h.id",actor);page.put("items",Pages.items(page).stream().map(this::projection).toList());return page;}
  public Map<String,Object> detail(UUID id){var rows=db.queryForList(VIEW+"WHERE h.id=?",id);if(rows.isEmpty())throw missing();return projection(rows.get(0));}
  private Map<String,Object> projection(Map<String,Object> h){
    var out=new LinkedHashMap<String,Object>();for(String key:List.of("id","product_id","product_title","title","version","status","deployment_state","desired_state","archive_sha256","manifest_sha256","size_bytes","expanded_bytes","file_count","deployment_id","review_reason","rights_consent_at","expires_at","created_at","updated_at")){String camel=camel(key);Object value=h.get(key);out.put(camel,value instanceof Timestamp t?t.toInstant().toString():value);}
    out.put("url",config.configured&&Boolean.TRUE.equals(h.get("servable"))&&h.get("deployment_id")!=null?config.publicUrl((UUID)h.get("deployment_id")):null);
    try{out.put("files",json.readValue(h.get("files").toString(),new TypeReference<List<Map<String,Object>>>(){}));}catch(Exception e){throw new ApiError(409,"ARCHIVE_INTEGRITY_ERROR","Stored package manifest could not be verified.");}
    out.put("audit",db.queryForList("SELECT id,action,detail,created_at AS \"createdAt\" FROM hosting_audit WHERE demo_id=? ORDER BY created_at,id",h.get("id")).stream().map(row->{Object t=row.get("createdAt");if(t instanceof Timestamp timestamp)row.put("createdAt",timestamp.toInstant().toString());return row;}).toList());return out;
  }
  private static String camel(String snake){var result=new StringBuilder();boolean uppercase=false;for(char ch:snake.toCharArray()){if(ch=='_')uppercase=true;else{result.append(uppercase?Character.toUpperCase(ch):ch);uppercase=false;}}return result.toString();}
  public boolean allowed(UUID deployment){return config.configured&&db.queryForObject("SELECT count(*) FROM hosted_demos h WHERE h.deployment_id=? AND "+SERVABLE,Integer.class,deployment)>0;}
  public Map<String,Object> publicProduct(UUID product){if(!config.configured)return Map.of("items",List.of());var rows=db.queryForList("SELECT h.id,h.title,h.version,h.deployment_id,h.expires_at FROM hosted_demos h WHERE h.product_id=? AND "+SERVABLE+" ORDER BY h.created_at DESC,h.id",product);return Map.of("items",rows.stream().map(h->Map.of("id",h.get("id"),"title",h.get("title"),"version",h.get("version"),"url",config.publicUrl((UUID)h.get("deployment_id")),"expiresAt",instant(h,"expires_at").toString())).toList());}
  /** Account deletion must not erase an uncertain provider identity, even after local withdrawal. */
  public boolean blocksAccountDeletion(UUID owner){return db.queryForObject("SELECT count(*) FROM hosted_demos WHERE owner_id=? AND deployment_id IS NOT NULL AND deployment_state<>'DELETED'",Integer.class,owner)>0;}
  /** Explicit safe projection; never exports S3 locators, credentials, or archive/base64 bytes. */
  public Map<String,Object> export(UUID owner){return Map.of("hosting",db.queryForList(VIEW+"WHERE h.owner_id=? ORDER BY h.created_at,h.id",owner).stream().map(this::projection).toList());}
  public static Instant instant(Map<String,Object> row,String key){return ((Timestamp)row.get(key)).toInstant();}
  public static int integer(Map<String,Object> row,String key){return ((Number)row.get(key)).intValue();}
}
