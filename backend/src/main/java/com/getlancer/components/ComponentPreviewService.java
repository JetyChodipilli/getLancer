package com.getlancer.components;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.hosting.DemoPublisherClient;
import com.getlancer.hosting.HostingConfiguration;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Free reviewed component previews reuse V4's bounded publisher and per-request authority. */
@Service
public final class ComponentPreviewService {
  final JdbcTemplate db;final Security security;final ComponentSlotService slots;final HostingConfiguration config;
  final DemoPublisherClient publisher;final ObjectMapper json;final TransactionTemplate tx;
  static final String AUTHORITY=" FROM component_previews p JOIN component_entries c ON c.id=p.component_id JOIN users u ON u.id=c.owner_id JOIN developer_profiles d ON d.user_id=u.id WHERE p.deployment_id=? AND p.state='READY' AND p.expires_at>now() AND c.status='ACTIVE' AND c.withdrawn_at IS NULL AND u.account_status='ACTIVE' AND u.email_verified_at IS NOT NULL AND d.approval_status='APPROVED' AND EXISTS(SELECT 1 FROM user_roles role WHERE role.user_id=u.id AND role.role='DEVELOPER') AND p.revision=(SELECT max(revision) FROM component_releases WHERE component_id=c.id)";
  public ComponentPreviewService(JdbcTemplate db,Security security,ComponentSlotService slots,HostingConfiguration config,DemoPublisherClient publisher,ObjectMapper json,org.springframework.transaction.PlatformTransactionManager manager){this.db=db;this.security=security;this.slots=slots;this.config=config;this.publisher=publisher;this.json=json;tx=new TransactionTemplate(manager);}
  public boolean allowed(UUID deployment){return config.projection().get("enabled").equals(true)&&db.queryForObject("SELECT count(*)"+AUTHORITY,Integer.class,deployment)==1;}
  public Map<String,Object> publicPreview(String slug){
    var rows=db.queryForList("SELECT p.deployment_id,p.expires_at FROM component_previews p JOIN component_entries c ON c.id=p.component_id WHERE c.slug=? ORDER BY p.revision DESC LIMIT 1",slug);
    if(rows.isEmpty()||!allowed((UUID)rows.get(0).get("deployment_id")))return Map.of("available",false);
    var row=rows.get(0);return Map.of("available",true,"url",config.publicUrl((UUID)row.get("deployment_id")),"expiresAt",row.get("expires_at").toString());
  }
  private Map<String,Object> current(UUID id,UUID owner){
    slots.lock(owner);
    var rows=db.queryForList("SELECT c.id,c.status,c.withdrawn_at,r.revision,r.source FROM component_entries c JOIN users u ON u.id=c.owner_id JOIN developer_profiles d ON d.user_id=u.id JOIN component_releases r ON r.component_id=c.id WHERE c.id=? AND c.owner_id=? AND c.status='ACTIVE' AND c.withdrawn_at IS NULL AND u.account_status='ACTIVE' AND u.email_verified_at IS NOT NULL AND d.approval_status='APPROVED' AND EXISTS(SELECT 1 FROM user_roles role WHERE role.user_id=u.id AND role.role='DEVELOPER') ORDER BY r.revision DESC LIMIT 1 FOR UPDATE OF c",id,owner);
    if(rows.isEmpty())throw new ApiError(409,"PREVIEW_NOT_APPROVED","Publish an approved component before creating its free preview.");return rows.get(0);
  }
  public Map<String,Object> publish(UUID id,HttpServletRequest request){
    config.ready();UUID owner=security.developer(request,true);
    var reserved=tx.execute(status->{
      var release=current(id,owner);security.developer(request,true);
      Map<String,Object> source;try{source=json.readValue(release.get("source").toString(),new TypeReference<Map<String,Object>>(){});}catch(Exception e){throw new IllegalStateException("Invalid reviewed component snapshot",e);}
      var archive=ComponentArchive.reviewed(source);
      var rows=db.queryForList("SELECT deployment_id,revision,state,expires_at,archive_sha256,manifest_sha256 FROM component_previews WHERE component_id=? AND revision=? FOR UPDATE",id,release.get("revision"));
      Map<String,Object> preview;
      if(rows.isEmpty()){
        UUID deployment=UUID.randomUUID();Instant expiry=Instant.now().plus(((Number)config.projection().get("expiryDays")).intValue(),ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        db.update("INSERT INTO component_previews(deployment_id,component_id,revision,expires_at,archive_sha256,manifest_sha256) VALUES(?,?,?,?,?,?)",deployment,id,release.get("revision"),java.sql.Timestamp.from(expiry),archive.archiveSha256(),archive.manifestSha256());
        preview=Map.of("deployment_id",deployment,"revision",release.get("revision"),"state","PENDING","expires_at",java.sql.Timestamp.from(expiry));
      }else{
        preview=rows.get(0);
        if(!archive.archiveSha256().equals(preview.get("archive_sha256"))||!archive.manifestSha256().equals(preview.get("manifest_sha256")))throw new ApiError(409,"PREVIEW_SOURCE_MISMATCH","Reserved preview hashes differ from the reviewed package.");
      }
      if("REVOKED".equals(preview.get("state")))throw new ApiError(409,"PREVIEW_WITHDRAWN","This preview identity was permanently withdrawn. Submit a distinct source version for a new preview.");
      if(!((java.sql.Timestamp)preview.get("expires_at")).toInstant().isAfter(Instant.now()))throw new ApiError(409,"PREVIEW_EXPIRED","Submit a new reviewed version to create a new preview lifetime.");
      slots.audit(owner,id,"PREVIEW_RESERVED","Exact immutable reviewed deployment and fixed expiry reserved.");
      return Map.of("preview",preview,"archive",archive);
    });
    var preview=(Map<String,Object>)reserved.get("preview");UUID deployment=(UUID)preview.get("deployment_id");
    if(!"READY".equals(preview.get("state"))) {
      var facts=publisher.publish(deployment,(com.getlancer.hosting.StaticArchive.Inspection)reserved.get("archive"),((java.sql.Timestamp)preview.get("expires_at")).toInstant());
      if(!facts.state().equals("READY"))throw new ApiError(409,"PREVIEW_UNAVAILABLE","The exact preview identity is permanently unavailable.");
      tx.execute(status->{var release=current(id,owner);security.developer(request,true);if(!release.get("revision").equals(preview.get("revision")))throw new ApiError(409,"PREVIEW_OBSOLETE","A newer reviewed version replaced this preview.");db.update("UPDATE component_previews SET state='READY' WHERE deployment_id=? AND state='PENDING'",deployment);if(!allowed(deployment))throw new ApiError(409,"PREVIEW_WITHDRAWN","This preview was withdrawn before confirmation.");slots.audit(owner,id,"PREVIEW_READY","Publisher confirmed the exact source, manifest and fixed expiry.");return true;});
    }
    if(!allowed(deployment))throw new ApiError(409,"PREVIEW_UNAVAILABLE","Current publication authority no longer permits this preview.");
    return Map.of("available",true,"url",config.publicUrl(deployment),"expiresAt",preview.get("expires_at").toString());
  }
}
