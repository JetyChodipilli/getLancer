package com.getlancer.commerce;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.education.EducationDataAiTest;
import com.getlancer.payments.RazorpayClient;
import com.getlancer.responses.AccountExportResponse;
import com.getlancer.shared.ApiError;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CommerceEducationEvidenceTest {
  final ObjectMapper json=new ObjectMapper();
  final JdbcTemplate db=mock(JdbcTemplate.class);
  final CommerceRepository repo=mock(CommerceRepository.class);
  final CommerceStorage storage=mock(CommerceStorage.class);
  final RazorpayClient provider=mock(RazorpayClient.class);
  final UUID release=UUID.randomUUID(),product=UUID.randomUUID(),seller=UUID.randomUUID(),template=UUID.randomUUID(),version=UUID.randomUUID();
  Map<String,Object> offer,source,snapshot;
  final CommerceEducationBinding binding=new CommerceEducationBinding(db,repo,storage,json,provider,true);
  @BeforeEach void setup()throws Exception{
    offer=Map.of("id",template,"product_id",product,"seller_id",seller,"price_minor",10000,"currency","INR");source=Map.of("id",version,"version","1.0.0","sha256","c".repeat(64),"size_bytes",100,"entry_count",2,"safe_source_files","[\"README.md\",\"model.json\"]","manifest_files","model.json","license_terms","MIT: retain original separate source/model notices");
    snapshot=new LinkedHashMap<>();snapshot.put("productId",product.toString());snapshot.put("category","AI_ML");snapshot.put("mode","PAID");snapshot.put("priceMinor",10000);snapshot.put("currency","INR");snapshot.put("sourceBinding",Map.of("templateId",template.toString(),"versionId",version.toString(),"version","1.0.0","sha256","c".repeat(64),"sizeBytes",100,"entryCount",2,"files",List.of("README.md","model.json"),"manifestFiles",List.of("model.json"),"licenseTerms",source.get("license_terms")));snapshot.put("dataAiEvidence",EducationDataAiTest.evidence(true));
    when(db.queryForList(startsWith("SELECT product_id,status FROM college_project_metadata"),eq(product))).thenReturn(List.of(Map.of("status","APPROVED")));
    when(db.queryForList(startsWith("SELECT id FROM education_releases WHERE product_id"),eq(product))).thenReturn(List.of(Map.of("id",release)));
    refresh();
  }
  void refresh()throws Exception{
    var row=Map.<String,Object>of("product_id",product,"owner_id",seller,"status","APPROVED","snapshot",json.writeValueAsString(snapshot),"source_hash","d".repeat(64),"source_version_id",version);
    when(db.queryForList(startsWith("SELECT product_id,owner_id,status,snapshot"),eq(release))).thenReturn(List.of(row));
  }
  @Test void acceptedPurchasePinsExactEvidenceAndStripsEveryNestedUnexpectedField()throws Exception{
    var evidence=new LinkedHashMap<>(EducationDataAiTest.evidence(true));evidence.put("storageKey","private-canary");evidence.put("sourceBytes",Map.of("raw","private-canary"));snapshot.put("dataAiEvidence",evidence);snapshot.put("institution","private-canary");snapshot.put("package",Map.of("licenseTerms","MIT source notice","includedAssets",List.of("Original source"),"providerToken","private-canary"));snapshot.put("categoryEvidence",Map.of("task","Original synthetic inference","privateNotes","private-canary"));snapshot.put("contribution",Map.of("text","Original creator contribution","privateEmail","private-canary"));refresh();String original=json.writeValueAsString(snapshot);
    var accepted=binding.bind(release,offer,source);assertEquals(EducationDataAiTest.evidence(true),accepted.get("dataAiEvidence"));assertEquals(snapshot.get("sourceBinding"),accepted.get("sourceBinding"));assertEquals(10000,accepted.get("priceMinor"));assertFalse(json.writeValueAsString(accepted).contains("private-canary"));assertTrue(original.contains("private-canary"));assertEquals(original,json.writeValueAsString(snapshot));
    var exported=json.valueToTree(AccountExportResponse.SourcePurchases.from(Map.of("education_snapshot",binding.encode(accepted)),json));assertEquals(json.valueToTree(EducationDataAiTest.evidence(true)),exported.path("education_snapshot").path("dataAiEvidence"));verifyNoInteractions(storage,provider);
  }
  @Test void historicalAgreementAbsenceRemainsAbsentAndCurrentPriceCannotRewriteIt()throws Exception{
    snapshot.remove("dataAiEvidence");refresh();var accepted=binding.bind(release,offer,source);assertFalse(accepted.containsKey("dataAiEvidence"));String frozen=binding.encode(accepted);
    var changed=new LinkedHashMap<>(offer);changed.put("price_minor",20000);assertThrows(ApiError.class,()->binding.bind(release,changed,source));assertEquals(frozen,binding.encode(accepted));assertEquals(10000,accepted.get("priceMinor"));
  }
  @Test void offerOnlyProjectsSafeSourceAndEvidenceEvenWhenCollectionIsDisabled()throws Exception{
    var unsafeSource=new LinkedHashMap<>((Map<String,Object>)snapshot.get("sourceBinding"));unsafeSource.put("storage_key","private-canary");unsafeSource.put("sourceBytes",List.of("private-canary"));snapshot.put("sourceBinding",unsafeSource);var evidence=new LinkedHashMap<>(EducationDataAiTest.evidence(true));evidence.put("noRemoteCode",Map.of("private","private-canary"));evidence.put("modelLoader","private-canary");snapshot.put("dataAiEvidence",evidence);refresh();when(provider.configuration()).thenReturn(Map.of("mode","disabled"));
    var disabled=new CommerceEducationBinding(db,repo,storage,json,provider,false);var publicOffer=disabled.offer(release);assertFalse(json.writeValueAsString(publicOffer).contains("private-canary"));assertEquals(false,publicOffer.get("checkoutAvailable"));assertEquals("c".repeat(64),publicOffer.get("sha256"));assertEquals("MIT: original frozen model notice",((Map<?,?>)publicOffer.get("dataAiEvidence")).get("modelLicense"));verifyNoInteractions(storage);
  }
}
