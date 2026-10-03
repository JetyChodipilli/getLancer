package com.getlancer.hosting;

import static com.getlancer.shared.Support.*;
import static com.getlancer.hosting.HostingRepository.*;
import com.getlancer.commerce.CommerceStorage;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class HostingService {
  private final HostingRepository repo;private final Security security;private final CommerceStorage storage;private final HostingConfiguration config;private final DemoPublisherClient publisher;private final TransactionTemplate transaction;
  public HostingService(HostingRepository repo,Security security,CommerceStorage storage,HostingConfiguration config,DemoPublisherClient publisher,PlatformTransactionManager manager){this.repo=repo;this.security=security;this.storage=storage;this.config=config;this.publisher=publisher;transaction=new TransactionTemplate(manager);}
  private <T>T tx(java.util.function.Supplier<T> work){return transaction.execute(status->work.get());}
  public Map<String,Object> configuration(){return config.projection();}
  public Map<String,Object> sources(HttpServletRequest r){return repo.sources(security.user(r),r);}
  public Map<String,Object> list(HttpServletRequest r,boolean admin){UUID actor=admin?security.admin(r):security.user(r);return repo.list(actor,r,admin);}
  public Map<String,Object> detail(UUID id,HttpServletRequest r){repo.owned(id,security.user(r),false);return repo.detail(id);}
  private UUID actor(HttpServletRequest r,boolean admin,boolean approved){UUID user=admin?security.admin(r):security.user(r);if(admin)repo.lockActor(user);else repo.lockOwner(user);UUID fresh=admin?security.admin(r):approved?security.developer(r,true):security.user(r);if(!user.equals(fresh))throw new ApiError(401,"UNAUTHENTICATED","Please log in again.");return fresh;}
  private Map<String,Object> access(UUID id,UUID actor,boolean admin){if(admin){repo.lockOwner(repo.owner(id));return repo.record(id,true);}return repo.owned(id,actor,true);}
  private static void consent(boolean consent){if(!consent)throw new ApiError(400,"RIGHTS_CONSENT_REQUIRED","Confirm ownership and permission to publish this package and its third-party assets.");}
  public Map<String,Object> create(MultipartFile file,UUID product,String title,String version,boolean consent,HttpServletRequest r)throws IOException {
    config.ready();consent(consent);String name=text(Map.of("title",title),"title",3,100),release=version.trim();if(!release.matches("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,39}"))throw new ApiError(400,"VALIDATION_ERROR","Use a version up to 40 ASCII letters, digits, dots, underscores, plus or minus signs.");
    if(file.isEmpty()||file.getSize()>StaticArchive.MAX_COMPRESSED||file.getOriginalFilename()==null||!file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".zip"))throw new ApiError(400,"INVALID_STATIC_PACKAGE","Choose a nonempty ZIP file up to 5 MiB.");
    UUID owner=tx(()->{UUID user=actor(r,false,true);repo.eligible(user,product);repo.capacity(user,false);return user;});
    byte[] bytes;try(var input=file.getInputStream()){bytes=input.readNBytes(StaticArchive.MAX_COMPRESSED+1);}var archive=StaticArchive.inspect(bytes);UUID id=UUID.randomUUID();String key=storage.put(id,bytes,archive.archiveSha256());
    try{return tx(()->{UUID user=actor(r,false,true);if(!owner.equals(user))throw new ApiError(401,"UNAUTHENTICATED","Please log in again.");repo.eligible(user,product);repo.capacity(user,false);if(repo.versionExists(user,product,release))throw new ApiError(409,"HOSTING_VERSION_EXISTS","Use a new immutable version for this proof product.");repo.insert(id,user,product,name,release,key,archive);repo.audit(id,user,"PACKAGE_UPLOADED","Immutable built package SHA-256 "+archive.archiveSha256()+"; publication rights consent recorded; source was never executed.");return repo.detail(id);});}catch(RuntimeException e){storage.discardUnlinked(key);throw e;}
  }
  public Map<String,Object> submit(UUID id,Map<String,Object>b,HttpServletRequest r){consent(Boolean.TRUE.equals(b.get("rightsConsent")));return tx(()->{UUID user=actor(r,false,true);var h=access(id,user,false);repo.eligible(user,(UUID)h.get("product_id"));if(!Set.of("DRAFT","CHANGES_REQUESTED").contains(h.get("status"))||!h.get("desired_state").equals("PUBLISHED")||!h.get("deployment_state").equals("NOT_CREATED"))throw transition();repo.submit(id);repo.audit(id,user,"PACKAGE_SUBMITTED","Owner reconfirmed publication rights for the immutable package and requested operator review.");return repo.detail(id);});}
  public ResponseEntity<byte[]> packageDownload(UUID id,HttpServletRequest r,boolean admin){var snapshot=tx(()->{UUID user=actor(r,admin,false);return access(id,user,admin);});byte[] bytes=read(snapshot);tx(()->{UUID user=actor(r,admin,false);var h=access(id,user,admin);if(!h.get("archive_sha256").equals(snapshot.get("archive_sha256")))throw integrity();repo.audit(id,user,admin?"PACKAGE_DOWNLOADED":"OWNER_PACKAGE_DOWNLOADED","Verified immutable archive SHA-256 "+h.get("archive_sha256"));return true;});return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip")).header("Content-Disposition","attachment; filename=\"getlancer-static-"+id+".zip\"").header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff").body(bytes);}
  public Map<String,Object> review(UUID id,Map<String,Object>b,HttpServletRequest r){String action=text(b,"action",6,20),reason=text(b,"reason",10,2000);if(!Boolean.TRUE.equals(b.get("rightsReviewed"))||!Boolean.TRUE.equals(b.get("packageReviewed")))throw new ApiError(400,"REVIEW_CONFIRMATION_REQUIRED","Review the actual built package and its ownership and third-party publication rights first.");String next=switch(action){case "APPROVE"->"APPROVED";case "CHANGES_REQUESTED"->"CHANGES_REQUESTED";case "REJECT"->"REJECTED";case "SUSPEND"->"SUSPENDED";default->throw new ApiError(400,"VALIDATION_ERROR","Choose a valid review action.");};return tx(()->{UUID user=actor(r,true,false);var h=access(id,user,true);if(action.equals("SUSPEND")){if(!Set.of("PENDING","APPROVED","SUSPENDED").contains(h.get("status")))throw transition();}else if(!Set.of("PENDING","SUSPENDED").contains(h.get("status")))throw transition();
      if(action.equals("APPROVE")){repo.eligible((UUID)h.get("owner_id"),(UUID)h.get("product_id"));if(!repo.downloaded(id,user,Objects.toString(h.get("archive_sha256"))))throw new ApiError(409,"PACKAGE_REVIEW_REQUIRED","Download this exact immutable package with MFA before approving it.");if(h.get("desired_state").equals("WITHDRAWN"))throw transition();}
      repo.review(id,next,reason,user);repo.audit(id,user,"REVIEW_"+action,reason+"; package and rights review confirmed for SHA-256 "+h.get("archive_sha256"));return repo.detail(id);});}
  private byte[] read(Map<String,Object> h){byte[] bytes=storage.read(Objects.toString(h.get("storage_key")),Objects.toString(h.get("archive_sha256")),integer(h,"size_bytes"));var archive=StaticArchive.inspect(bytes);if(!archive.archiveSha256().equals(h.get("archive_sha256"))||!archive.manifestSha256().equals(h.get("manifest_sha256"))||archive.files().size()!=integer(h,"file_count")||archive.expandedBytes()!=integer(h,"expanded_bytes"))throw integrity();return bytes;}
  public Map<String,Object> deploy(UUID id,HttpServletRequest r){config.ready();var before=tx(()->{UUID user=actor(r,false,true);var h=access(id,user,false);repo.eligible(user,(UUID)h.get("product_id"));if(!h.get("status").equals("APPROVED")||!h.get("desired_state").equals("PUBLISHED"))throw transition();if(!Set.of("NOT_CREATED","READY").contains(h.get("deployment_state")))throw new ApiError(409,"DEPLOYMENT_UNCONFIRMED","Reconcile or withdraw the existing reserved deployment before any further action.");return h;});
    if(before.get("deployment_state").equals("READY"))return detail(id,r);
    var archive=StaticArchive.inspect(read(before));var reserved=tx(()->{UUID user=actor(r,false,true);var h=access(id,user,false);repo.eligible(user,(UUID)h.get("product_id"));if(!h.get("status").equals("APPROVED")||!h.get("desired_state").equals("PUBLISHED")||!h.get("deployment_state").equals("NOT_CREATED"))throw transition();repo.capacity(user,true);Instant expiry=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).plus(config.expiryDays,java.time.temporal.ChronoUnit.DAYS);repo.reserve(id,UUID.randomUUID(),expiry);repo.audit(id,user,"PUBLISH_INTENT","Reserved immutable deployment identity and fixed expiry before contacting the publisher.");return repo.record(id,false);});
    UUID deployment=(UUID)reserved.get("deployment_id"),owner=(UUID)reserved.get("owner_id");
    try{var facts=publisher.publish(deployment,archive,instant(reserved,"expires_at"));complete(id,owner,facts,"PUBLISH_CONFIRMED");}catch(ApiError e){tx(()->{repo.lockOwner(owner);repo.record(id,true);repo.unknown(id);repo.audit(id,owner,"PUBLISH_UNCONFIRMED","Exact reserved deployment requires reconciliation; no new identity was allocated.");return true;});throw e;}return detail(id,r);
  }
  private void complete(UUID id,UUID actor,DemoPublisherClient.Metadata facts,String action){tx(()->{repo.lockOwner(repo.owner(id));repo.record(id,true);if(repo.observed(id,facts))repo.audit(id,actor,action,"Authenticated publisher confirmed "+facts.state()+" for the fixed deployment identity.");return true;});}
  public Map<String,Object> withdraw(UUID id,Map<String,Object>b,HttpServletRequest r,boolean admin){String reason=admin?text(b,"reason",10,2000):"Owner withdrew public hosting.";var h=tx(()->{UUID user=actor(r,admin,false);var row=access(id,user,admin);repo.withdraw(id);repo.audit(id,user,"WITHDRAW_INTENT",reason+" Public access is denied immediately; publisher deletion follows the committed intent.");return repo.record(id,false);});
    if(h.get("deployment_id")!=null&&!h.get("deployment_state").equals("DELETED")){config.ready();UUID actor=admin?security.admin(r):security.user(r);try{complete(id,actor,publisher.delete((UUID)h.get("deployment_id"),Objects.toString(h.get("archive_sha256")),Objects.toString(h.get("manifest_sha256")),instant(h,"expires_at")),"DELETE_CONFIRMED");}catch(ApiError e){tx(()->{repo.lockOwner((UUID)h.get("owner_id"));repo.record(id,true);repo.audit(id,actor,"DELETE_UNCONFIRMED","Withdrawal remains committed; exact publisher deletion must be reconciled.");return true;});throw e;}}
    return admin?repo.detail(id):detail(id,r);
  }
  public Map<String,Object> reconcile(UUID id,Map<String,Object>b,HttpServletRequest r,boolean admin){config.ready();String reason=admin?text(b,"reason",10,2000):"Owner requested exact deployment reconciliation.";var h=tx(()->{UUID user=actor(r,admin,false);var row=access(id,user,admin);if(row.get("deployment_id")==null)throw new ApiError(409,"NO_DEPLOYMENT","This package has no publisher deployment to reconcile.");repo.audit(id,user,"RECONCILE_REQUESTED",reason);return row;});
    UUID actor=admin?security.admin(r):security.user(r);UUID deployment=(UUID)h.get("deployment_id");if(!h.get("deployment_state").equals("DELETED")){DemoPublisherClient.Metadata facts;
      if(h.get("desired_state").equals("WITHDRAWN"))facts=publisher.delete(deployment,Objects.toString(h.get("archive_sha256")),Objects.toString(h.get("manifest_sha256")),instant(h,"expires_at"));
      else {try{facts=publisher.get(deployment,Objects.toString(h.get("archive_sha256")),Objects.toString(h.get("manifest_sha256")),instant(h,"expires_at"));}catch(ApiError e){if(!e.code.equals("DEPLOYMENT_UNCONFIRMED"))throw e;facts=retryExact(id,h,r,admin);}}
      complete(id,actor,facts,"RECONCILE_CONFIRMED");}
    return admin?repo.detail(id):detail(id,r);
  }
  /** A proven absent reservation can be retried only with the same immutable id, bytes and expiry. */
  private DemoPublisherClient.Metadata retryExact(UUID id,Map<String,Object> snapshot,HttpServletRequest r,boolean admin){
    tx(()->{retryAllowed(id,snapshot,r,admin);return true;});
    var archive=StaticArchive.inspect(read(snapshot));
    UUID user=tx(()->{UUID actor=retryAllowed(id,snapshot,r,admin);repo.retryIntent(id);repo.audit(id,actor,"PUBLISH_RETRY_INTENT","Authenticated GET found no deployment; committed retry of the same id, archive manifest and fixed expiry.");return actor;});
    try{return publisher.publish((UUID)snapshot.get("deployment_id"),archive,instant(snapshot,"expires_at"));}
    catch(ApiError e){tx(()->{repo.lockOwner((UUID)snapshot.get("owner_id"));repo.record(id,true);repo.unknown(id);repo.audit(id,user,"PUBLISH_UNCONFIRMED","Same-id publication remains unconfirmed; withdrawal or exact reconciliation is required.");return true;});throw e;}
  }
  private UUID retryAllowed(UUID id,Map<String,Object> snapshot,HttpServletRequest r,boolean admin){
    UUID user=actor(r,admin,!admin);var current=access(id,user,admin);repo.eligible((UUID)current.get("owner_id"),(UUID)current.get("product_id"));
    if(!current.get("status").equals("APPROVED")||!current.get("desired_state").equals("PUBLISHED")||!Set.of("CREATING","UNKNOWN").contains(current.get("deployment_state"))||!Objects.equals(current.get("deployment_id"),snapshot.get("deployment_id"))||!instant(current,"expires_at").equals(instant(snapshot,"expires_at"))||!instant(current,"expires_at").isAfter(Instant.now()))throw transition();return user;
  }
  public Map<String,Object> publicProduct(UUID product){return repo.publicProduct(product);}
  public Map<String,Object> gateway(UUID deployment,String secret){if(!config.gateway(secret))throw new ApiError(403,"FORBIDDEN","Gateway authentication is required.");return Map.of("allowed",repo.allowed(deployment));}
  private static ApiError transition(){return new ApiError(409,"INVALID_TRANSITION","This package cannot perform that action in its current state.");}
  private static ApiError integrity(){return new ApiError(409,"ARCHIVE_INTEGRITY_ERROR","The private package does not match its immutable manifest.");}
}
