package com.getlancer.commerce;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.payments.RazorpayClient;
import com.getlancer.shared.ApiError;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Reviewed college disclosures bind the existing source purchase; no separate payment authority. */
@Component
public class CommerceEducationBinding {
  private final JdbcTemplate db;
  private final CommerceRepository repo;
  private final CommerceStorage storage;
  private final ObjectMapper json;
  private final RazorpayClient provider;
  private final boolean paidEnabled;

  public CommerceEducationBinding(JdbcTemplate db,CommerceRepository repo,CommerceStorage storage,ObjectMapper json,
      RazorpayClient provider,@Value("${app.education.paid-enabled:false}") boolean paidEnabled) {
    this.db=db;this.repo=repo;this.storage=storage;this.json=json;this.provider=provider;this.paidEnabled=paidEnabled;
  }

  /** Release review and checkout acquire this before product/template/version/release row locks. */
  public void lockProduct(UUID product) {
    db.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"education:"+product);
  }
  public void lockTemplateProduct(UUID template) {lockProduct((UUID)repo.template(template,false).get("product_id"));}

  @Transactional
  public Map<String,Object> freezePaid(UUID version,UUID product,UUID owner) {
    lockProduct(product);
    var rows=db.queryForList("SELECT template_id FROM source_versions WHERE id=?",version);
    if(rows.isEmpty()) throw unavailable("Choose an approved source version for this project.");
    var t=repo.template((UUID)rows.get(0).get("template_id"),true);var v=repo.version((UUID)t.get("id"),version,true);
    if(!product.equals(t.get("product_id")) || !owner.equals(t.get("seller_id")))
      throw unavailable("The source must belong to this project and its current owner.");
    if(!"ACTIVE".equals(t.get("status")) || !"APPROVED".equals(v.get("status")))
      throw unavailable("The source listing and version must be approved and available.");
    repo.sellerEligible(owner,product,true);
    var frozen=source(t,v);frozen.put("priceMinor",t.get("price_minor"));frozen.put("currency",t.get("currency"));return frozen;
  }

  private Map<String,Object> source(Map<String,Object> t,Map<String,Object> v) {
    List<String> files;
    if(v.get("safe_source_files")==null) {
      // Legacy versions require their actual bounded, checksum-verified archive; never invent paths.
      var inspection=SourceArchive.inspect(storage.read(Objects.toString(v.get("storage_key")),
          Objects.toString(v.get("sha256")),((Number)v.get("size_bytes")).intValue()));
      if(!inspection.sha256().equals(v.get("sha256")) || inspection.entryCount()!=((Number)v.get("entry_count")).intValue())
        throw unavailable("The legacy source manifest cannot be confirmed.");
      files=inspection.files();
    } else files=files(v.get("safe_source_files"));
    var out=new LinkedHashMap<String,Object>();
    out.put("templateId",t.get("id").toString());out.put("versionId",v.get("id").toString());out.put("version",v.get("version"));
    out.put("sha256",v.get("sha256"));out.put("sizeBytes",v.get("size_bytes"));out.put("entryCount",v.get("entry_count"));
    out.put("manifestFiles",List.of(Objects.toString(v.get("manifest_files")).split("\n")));out.put("files",files);
    out.put("licenseTerms",v.get("license_terms"));return out;
  }

  private List<String> files(Object stored) {
    try {return json.readValue(stored.toString(),new TypeReference<List<String>>(){});}
    catch(java.io.IOException e) {throw new IllegalStateException("Stored source file manifest is invalid",e);}
  }

  /** Runs inside the existing reservation transaction after its source template/version locks. */
  public Map<String,Object> bind(UUID release,Map<String,Object> t,Map<String,Object> v) {
    UUID product=(UUID)t.get("product_id");
    var metadata=db.queryForList("SELECT product_id,status FROM college_project_metadata WHERE product_id=? FOR SHARE",product);
    if(metadata.isEmpty() && release==null) return null;
    if(!paidEnabled) throw new ApiError(503,"EDUCATION_PAID_UNAVAILABLE","Paid college-project collection awaits operator activation.");
    if(release==null) throw unavailable("Choose the exact reviewed college release before checkout.");
    var rows=db.queryForList("SELECT product_id,owner_id,status,snapshot,source_hash,source_version_id FROM education_releases WHERE id=? FOR SHARE",release);
    if(rows.isEmpty()) throw unavailable("This college release is unavailable.");
    var e=rows.get(0);
    if(metadata.isEmpty() || !"APPROVED".equals(metadata.get(0).get("status")) || !"APPROVED".equals(e.get("status"))
        || !product.equals(e.get("product_id"))
        || !t.get("seller_id").equals(e.get("owner_id")) || !v.get("id").equals(e.get("source_version_id")))
      throw unavailable("Checkout requires the current reviewed paid college release for this source.");
    var snapshot=snapshot(e);
    if(!"PAID".equals(snapshot.get("mode"))) throw unavailable("Showcase and free projects do not use checkout.");
    var latest=db.queryForList("SELECT id FROM education_releases WHERE product_id=? AND status='APPROVED' ORDER BY reviewed_at DESC NULLS LAST,created_at DESC,id DESC LIMIT 1",product);
    if(latest.isEmpty() || !release.equals(latest.get(0).get("id")))
      throw unavailable("The public college offer changed. Review the current package before checkout.");
    if(!equivalent(snapshot.get("sourceBinding"),source(t,v))
        || !Objects.toString(t.get("price_minor")).equals(Objects.toString(snapshot.get("priceMinor")))
        || !Objects.equals(t.get("currency"),snapshot.get("currency")))
      throw unavailable("The source price, license or package changed. A newly reviewed college release is required.");
    var accepted=new LinkedHashMap<String,Object>();
    for(String key:List.of("productId","slug","title","summary","category","mode","difficulty","demoMode","demoUrl",
        "package","categoryEvidence","contribution","componentLinks","sourceBinding","priceMinor","currency"))
      if(snapshot.containsKey(key)) accepted.put(key,snapshot.get(key));
    accepted.put("componentLinks",currentComponentLinks(db,snapshot.get("componentLinks")));
    accepted.put("releaseId",release.toString());accepted.put("sourceHash",e.get("source_hash"));return accepted;
  }

  @Transactional
  public Map<String,Object> offer(UUID release) {
    var rows=db.queryForList("SELECT product_id,owner_id,status,snapshot,source_version_id FROM education_releases WHERE id=?",release);
    if(rows.isEmpty() || !"APPROVED".equals(rows.get(0).get("status")) || rows.get(0).get("source_version_id")==null)
      throw new ApiError(404,"NOT_FOUND","Paid college release not found.");
    var e=rows.get(0);var snapshot=snapshot(e);var out=new LinkedHashMap<String,Object>();
    if(snapshot.get("sourceBinding") instanceof Map<?,?> source)
      for(var entry:source.entrySet()) out.put(Objects.toString(entry.getKey()),entry.getValue());
    out.put("priceMinor",snapshot.get("priceMinor"));out.put("currency",snapshot.get("currency"));
    boolean available=false;String reason="Paid college-project collection awaits operator activation.";
    if(paidEnabled) {
      try {
        lockProduct((UUID)e.get("product_id"));
        var frozen=freezePaid((UUID)e.get("source_version_id"),(UUID)e.get("product_id"),(UUID)e.get("owner_id"));
        var t=repo.template(UUID.fromString(Objects.toString(frozen.get("templateId"))),true);
        var v=repo.version((UUID)t.get("id"),(UUID)e.get("source_version_id"),true);bind(release,t,v);
        provider.requireCollection();repo.payee((UUID)t.get("seller_id"),provider.mode());available=true;reason="";
      } catch(ApiError error) {reason=error.getMessage();}
    }
    out.put("paidEnabled",paidEnabled);out.put("checkoutAvailable",available);out.put("checkoutReason",reason);
    out.put("paymentMode",Objects.toString(provider.configuration().get("mode"),"disabled"));return out;
  }

  public String encode(Map<String,Object> snapshot) {
    if(snapshot==null) return null;
    try {return json.writeValueAsString(snapshot);}catch(java.io.IOException e) {throw new IllegalStateException(e);}
  }

  /** Current component visibility overlays the accepted agreement without changing retained source licenses. */
  public static List<Map<String,Object>> currentComponentLinks(JdbcTemplate db,Object raw) {
    List<Map<String,Object>> out=new ArrayList<>();if(!(raw instanceof List<?> links)) return out;
    for(Object item:links) {
      if(!(item instanceof Map<?,?> link)) continue;UUID id;long revision;
      try {id=UUID.fromString(Objects.toString(link.get("componentId")));revision=Long.parseLong(Objects.toString(link.get("revision")));}
      catch(IllegalArgumentException e) {continue;}
      if(revision<1) continue;
      if(db.queryForObject("SELECT count(*) FROM component_releases cr JOIN component_entries c ON c.id=cr.component_id JOIN users u ON u.id=c.owner_id JOIN developer_profiles d ON d.user_id=u.id WHERE cr.component_id=? AND cr.revision=? AND c.published_at IS NOT NULL AND c.withdrawn_at IS NULL AND c.status IN ('ACTIVE','ARCHIVED','DRAFT','PENDING') AND u.account_status='ACTIVE' AND u.email_verified_at IS NOT NULL AND d.approval_status='APPROVED' AND EXISTS(SELECT 1 FROM user_roles r WHERE r.user_id=u.id AND r.role='DEVELOPER')",Integer.class,id,revision)==0) continue;
      var safe=new LinkedHashMap<String,Object>();
      for(String key:List.of("componentId","revision","license","attribution","sha256","slug")) if(link.containsKey(key)) safe.put(key,link.get(key));out.add(safe);
    }
    return out;
  }

  private Map<String,Object> snapshot(Map<String,Object> release) {
    try {return json.readValue(release.get("snapshot").toString(),new TypeReference<LinkedHashMap<String,Object>>(){});}
    catch(java.io.IOException e) {throw new IllegalStateException("Stored education release is invalid",e);}
  }
  private boolean equivalent(Object frozen,Object current) {
    try {return json.readTree(json.writeValueAsString(frozen)).equals(json.readTree(json.writeValueAsString(current)));}
    catch(java.io.IOException e) {throw new IllegalStateException(e);}
  }
  private static ApiError unavailable(String message) {return new ApiError(409,"EDUCATION_OFFER_CHANGED",message);}
}
