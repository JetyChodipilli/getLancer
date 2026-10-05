package com.getlancer.commerce;

import static com.getlancer.shared.Support.*;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CommerceService {
  private final CommerceRepository repo;private final Security security;private final CommerceStorage storage;private final TransactionTemplate transaction;private final com.getlancer.publishing.PublishingCapacity publishing;
  public CommerceService(CommerceRepository repo,Security security,CommerceStorage storage,PlatformTransactionManager manager,com.getlancer.publishing.PublishingCapacity publishing) {this.repo=repo;this.security=security;this.storage=storage;this.publishing=publishing;transaction=new TransactionTemplate(manager);}
  private <T> T tx(java.util.function.Supplier<T> work) {return transaction.execute(status->work.get());}
  public Map<String,Object> catalog(HttpServletRequest r,String q,String category,String technology) {return repo.catalog(r,q,category,technology);}
  public Map<String,Object> detail(String slug) {return repo.publicDetail(slug);}
  public Map<String,Object> own(HttpServletRequest r) {UUID owner=security.user(r);var result=repo.ownerPage(owner,r);result.put("capacity",publishing.capacity(owner,"TEMPLATE"));return result;}
  public Map<String,Object> admin(HttpServletRequest r) {security.admin(r);return repo.adminPage(r);}
  public Map<String,Object> create(Map<String,Object> b,HttpServletRequest r) {return tx(()->{
    UUID seller=security.developer(r,true),product=uuid(b.get("productId"));publishing.lock(seller);repo.sellerEligible(seller,product,true);
    String title=text(b,"title",3,100),summary=text(b,"summary",10,240),description=text(b,"description",20,12000),terms=text(b,"licenseTerms",40,8000);
    long price;try {price=Long.parseLong(Objects.toString(b.get("priceMinor"),""));} catch(NumberFormatException e) {throw new ApiError(400,"VALIDATION_ERROR","Enter the price in whole paise.",Map.of("priceMinor","Use a whole number of paise."));}
    if(price<100 || price>1000000000) throw new ApiError(400,"VALIDATION_ERROR","Price must be between 100 and 1000000000 paise.",Map.of("priceMinor","Use 100–1000000000 paise."));
    UUID id=UUID.randomUUID();String slug=title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("^-|-$","");if(slug.isBlank()) slug="source";slug=slug+"-"+id.toString().substring(0,8);
    terms+="\n\ngetLancer license floor: one commercial end product; modification is permitted; source redistribution or resale as a competing template is prohibited. Third-party license notices remain applicable.";
    repo.create(id,seller,product,slug,title,summary,description,price,terms);repo.audit(id,null,seller,"TEMPLATE_CREATED","Seller created a source listing tied to verified public proof.");return repo.ownerDetail(id,seller);
  });}
  private Map<String,Object> owner(UUID id,HttpServletRequest r,boolean approved) {UUID seller=security.user(r);publishing.lock(seller);var row=repo.template(id,true);if(!seller.equals(row.get("seller_id"))) throw new ApiError(404,"NOT_FOUND","Template not found.");if(approved) security.developer(r,true);return row;}
  public Map<String,Object> upload(UUID template,MultipartFile file,String version,String notes,boolean consent,HttpServletRequest r) throws IOException {
    if(!consent) throw new ApiError(400,"RIGHTS_CONSENT_REQUIRED","Confirm source ownership and third-party redistribution rights.");
    version=version.trim();notes=notes.trim();if(!version.matches("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,39}") || notes.length()<10 || notes.length()>4000) throw new ApiError(400,"VALIDATION_ERROR","Use a release version up to 40 characters and release notes of 10–4000 characters.");
    if(file.isEmpty() || file.getSize()>SourceArchive.MAX_COMPRESSED) throw new ApiError(413,"PAYLOAD_TOO_LARGE","Source ZIP must be 5 MiB or smaller.");
    var snapshot=tx(()->{var t=owner(template,r,true);repo.sellerEligible((UUID)t.get("seller_id"),(UUID)t.get("product_id"),true);if(Set.of("ARCHIVED","SUSPENDED").contains(t.get("status"))) throw new ApiError(409,"TEMPLATE_HELD","Archived or suspended listings cannot receive new releases.");if(repo.versions(template,true).size()>=50) throw new ApiError(409,"VERSION_LIMIT","Use up to 50 reviewed releases per listing.");return t;});
    byte[] bytes;try(var input=file.getInputStream()) {bytes=input.readNBytes(SourceArchive.MAX_COMPRESSED+1);}var inspection=SourceArchive.inspect(bytes);UUID id=UUID.randomUUID();String storageKey=storage.put(id,bytes,inspection.sha256());
    final String releaseVersion=version,releaseNotes=notes;
    try {return tx(()->{var t=owner(template,r,true);repo.sellerEligible((UUID)t.get("seller_id"),(UUID)t.get("product_id"),true);if(Set.of("ARCHIVED","SUSPENDED").contains(t.get("status"))) throw new ApiError(409,"TEMPLATE_HELD","This listing is now held.");var releases=repo.versions(template,true);if(releases.size()>=50) throw new ApiError(409,"VERSION_LIMIT","Use up to 50 releases per listing.");for(var v:releases) if(releaseVersion.equals(v.get("version"))) throw new ApiError(409,"VERSION_EXISTS","Use a new version identifier for an immutable release.");
      repo.addVersion(id,template,releaseVersion,releaseNotes,storageKey,inspection,Objects.toString(snapshot.get("license_terms")));repo.audit(template,null,(UUID)t.get("seller_id"),"SOURCE_UPLOADED","Immutable source SHA-256 "+inspection.sha256()+"; rights consent recorded; no source was executed.");return repo.versions(template,true).stream().filter(v->id.equals(v.get("id"))).findFirst().orElseThrow();});} catch(RuntimeException e) {storage.discardUnlinked(storageKey);throw e;}
  }
  public Map<String,Object> submit(UUID template,UUID version,Map<String,Object> b,HttpServletRequest r) {return tx(()->{var t=owner(template,r,true);repo.sellerEligible((UUID)t.get("seller_id"),(UUID)t.get("product_id"),true);if(!Boolean.TRUE.equals(b.get("rightsConsent"))) throw new ApiError(400,"RIGHTS_CONSENT_REQUIRED","Confirm package ownership and redistribution rights.");var v=repo.version(template,version,true);if(!Set.of("DRAFT","CHANGES_REQUESTED").contains(v.get("status")) || Set.of("ARCHIVED","SUSPENDED").contains(t.get("status"))) throw new ApiError(409,"INVALID_TRANSITION","This release cannot be submitted in its current state.");repo.submit(version);repo.audit(template,null,(UUID)t.get("seller_id"),"SOURCE_SUBMITTED","Seller confirmed package rights and requested operator review.");return repo.ownerDetail(template,(UUID)t.get("seller_id"));});}
  public Map<String,Object> archive(UUID template,HttpServletRequest r) {return tx(()->{var t=owner(template,r,false);if(!"SUSPENDED".equals(t.get("status"))) repo.templateStatus(template,"ARCHIVED");repo.audit(template,null,(UUID)t.get("seller_id"),"TEMPLATE_ARCHIVED","Seller removed the listing from new discovery and checkout; existing eligible purchases remain pinned.");return repo.ownerDetail(template,(UUID)t.get("seller_id"));});}
  public Map<String,Object> activate(UUID template,HttpServletRequest r) {return tx(()->{
    var t=owner(template,r,true);repo.sellerEligible((UUID)t.get("seller_id"),(UUID)t.get("product_id"),true);
    if(!Set.of("ACTIVE","ARCHIVED").contains(t.get("status")) || repo.versions(template,false).isEmpty())
      throw new ApiError(409,"INVALID_TRANSITION","Only an archived template with an approved release can be activated.");
    publishing.requireTemplate((UUID)t.get("seller_id"),template);repo.templateStatus(template,"ACTIVE");
    repo.audit(template,null,(UUID)t.get("seller_id"),"TEMPLATE_ACTIVATED","Seller reactivated an approved listing using available template capacity.");return repo.ownerDetail(template,(UUID)t.get("seller_id"));
  });}
  public Map<String,Object> review(UUID template,UUID version,Map<String,Object> b,HttpServletRequest r) {return tx(()->{UUID actor=security.admin(r);publishing.lock((UUID)repo.template(template,false).get("seller_id"));var t=repo.template(template,true);var v=repo.version(template,version,true);String action=text(b,"action",6,20),reason=text(b,"reason",10,2000);if(!Boolean.TRUE.equals(b.get("rightsReviewed")) || !Boolean.TRUE.equals(b.get("packageReviewed"))) throw new ApiError(400,"REVIEW_CONFIRMATION_REQUIRED","Review the actual source package, README/build manifest, license and third-party redistribution rights first.");String next=switch(action) {case "APPROVE"->"APPROVED";case "CHANGES_REQUESTED"->"CHANGES_REQUESTED";case "REJECT"->"REJECTED";case "SUSPEND"->"SUSPENDED";default->throw new ApiError(400,"VALIDATION_ERROR","Choose a valid review action.");};
    if(action.equals("SUSPEND")) {if(!Set.of("PENDING","APPROVED","SUSPENDED").contains(v.get("status"))) throw new ApiError(409,"INVALID_TRANSITION","Only a pending or approved release can be suspended.");repo.templateStatus(template,"SUSPENDED");}
    else {if(!Set.of("PENDING","SUSPENDED").contains(v.get("status"))) throw new ApiError(409,"INVALID_TRANSITION","Only a submitted or suspended release can be reviewed.");if(action.equals("APPROVE")) {repo.sellerEligible((UUID)t.get("seller_id"),(UUID)t.get("product_id"),true);if(!"ARCHIVED".equals(t.get("status"))) {publishing.requireTemplate((UUID)t.get("seller_id"),template);repo.templateStatus(template,"ACTIVE");}}}
    repo.review(version,next,reason,actor);repo.audit(template,null,actor,"SOURCE_"+action,"Release "+v.get("version")+": "+reason);return repo.detail(template);});}
  public ResponseEntity<byte[]> packageDownload(UUID template,UUID version,HttpServletRequest r,boolean admin) {
    var v=tx(()->{if(admin) security.admin(r);else owner(template,r,false);return repo.version(template,version,false);});return archiveResponse(v);
  }
  public ResponseEntity<byte[]> archiveResponse(Map<String,Object> version) {byte[] bytes=storage.read(Objects.toString(version.get("storage_key")),Objects.toString(version.get("sha256")),((Number)version.get("size_bytes")).intValue());return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip")).header("Content-Disposition","attachment; filename=\"getlancer-source-"+version.get("id")+".zip\"").header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff").body(bytes);}
}
