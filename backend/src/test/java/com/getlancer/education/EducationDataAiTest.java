package com.getlancer.education;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.config.SecurityConfiguration;
import com.getlancer.dto.EducationRequests;
import com.getlancer.responses.AccountExportResponse;
import com.getlancer.shared.ApiError;
import com.getlancer.shared.TypedInputs;
import jakarta.validation.Validation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.util.ReflectionTestUtils;

public class EducationDataAiTest {
  final ObjectMapper json=new ObjectMapper();
  final EducationSnapshot schema=new EducationSnapshot(json);
  public static Map<String,Object> evidence(boolean ai){
    var out=new LinkedHashMap<String,Object>();out.put("codeLicense","MIT: original code notice retained");out.put("dataLicense","MIT: original synthetic data notice");out.put("dataProvenance","Original synthetic fixtures with no personal records");out.put("dataSha256","a".repeat(64));out.put("outputSchema","class:string; probability:number; totals:integer");out.put("limitations","Tiny synthetic results do not establish real-world accuracy");out.put("redistributionAllowed",true);out.put("syntheticData",true);out.put("noRemoteCode",true);
    if(ai){out.put("modelLicense","MIT: original frozen model notice");out.put("modelProvenance","Original fixed JSON coefficients without remote loaders");out.put("modelSha256","b".repeat(64));out.put("modelFormat","JSON");out.put("evaluationSplit","Separate held-out synthetic evaluation fixture");out.put("evaluationProtocol","Deterministic comparison of predictions with original synthetic labels");}return out;
  }
  Map<String,Object> draft(String category,Map<String,Object> evidence){
    var raw=new LinkedHashMap<String,Object>();raw.put("category",category);raw.put("mode","SHOWCASE");raw.put("difficulty","BEGINNER");raw.put("demoMode","SOURCE_ONLY");
    var categoryEvidence=new LinkedHashMap<String,Object>();for(String key:EducationSnapshot.EVIDENCE.get(category))categoryEvidence.put(key,"Original documented category evidence with realistic limitations");raw.put("categoryEvidence",categoryEvidence);
    raw.put("package",Map.of("includedAssets",List.of("Original source"),"excludedAssets",List.of("Production data"),"setupSteps",List.of("Run original local setup"),"prerequisites",List.of("Standard library only"),"limitations",List.of("Synthetic demonstration only"),"supportTerms","Self-guided local setup support","licenseTerms","Original MIT code and separate synthetic data/model notices"));if(evidence!=null)raw.put("dataAiEvidence",evidence);return schema.draft(raw);
  }
  @Test void draftsPreserveTypedPartialEvidenceAndOtherCategoriesRejectIt(){
    assertFalse(schema.draft(Map.of("mode","SHOWCASE")).containsKey("dataAiEvidence"));
    var saved=schema.draft(Map.of("mode","SHOWCASE","category","AI_ML","dataAiEvidence",Map.of("codeLicense"," MIT ","syntheticData",false,"dataSha256","")));
    assertEquals(Map.of("codeLicense","MIT","syntheticData",false,"dataSha256",""),saved.get("dataAiEvidence"));
    for(String category:List.of("FULL_STACK","IOT"))assertThrows(ApiError.class,()->draft(category,Map.of()));
  }
  @Test void unknownKeysUnsafeTypesBoundsHashesAndModelLoadersRejectBeforeSubmission(){
    for(Object unsafe:List.<Object>of("text",List.of(),Map.of("sourceBytes","secret"),Map.of("codeLicense",42),Map.of("limitations",Map.of("private","secret")),Map.of("syntheticData","true"),Map.of("noRemoteCode",1),Map.of("limitations","x".repeat(4001)),Map.of("dataSha256","A".repeat(64)),Map.of("modelSha256","b".repeat(63)),Map.of("modelFormat","pickle"),Map.of("modelFormat","joblib"),Map.of("modelFormat","https://remote.invalid/model")))assertThrows(ApiError.class,()->schema.draft(Map.of("mode","SHOWCASE","category","AI_ML","dataAiEvidence",unsafe)));
    assertDoesNotThrow(()->schema.complete(draft("AI_ML",evidence(true))));
  }
  @Test void publicationRequiresEachRelevantFieldAndAffirmativeRightsButAnalyticsNeedsNoModel(){
    for(String category:List.of("DATA_ANALYTICS","AI_ML")){
      var valid=evidence(category.equals("AI_ML"));assertDoesNotThrow(()->schema.complete(draft(category,valid)));assertThrows(ApiError.class,()->schema.complete(draft(category,null)));
      for(String field:valid.keySet()){var incomplete=new LinkedHashMap<>(valid);incomplete.remove(field);assertThrows(ApiError.class,()->schema.complete(draft(category,incomplete)),category+" missing "+field);}
      for(String flag:List.of("redistributionAllowed","syntheticData","noRemoteCode")){var unsafe=new LinkedHashMap<>(valid);unsafe.put(flag,false);assertThrows(ApiError.class,()->schema.complete(draft(category,unsafe)));}
    }
    var analytics=evidence(false);for(String key:List.of("modelLicense","modelProvenance","modelSha256","modelFormat","evaluationSplit","evaluationProtocol"))analytics.put(key,"");assertDoesNotThrow(()->schema.complete(draft("DATA_ANALYTICS",analytics)));
  }
  @Test void historicalAbsenceSurvivesReviewAndNewEvidenceChangesTheCanonicalAgreementHash(){
    var historical=draft("AI_ML",null);String digest=schema.hash(historical);assertDoesNotThrow(()->schema.completeReview(historical));assertEquals(digest,schema.hash(historical));
    var current=draft("AI_ML",evidence(true));assertEquals(schema.hash(current),schema.hash(schema.read(schema.encode(current))));assertNotEquals(digest,schema.hash(current));
    var changed=evidence(true);changed.put("modelSha256","c".repeat(64));assertNotEquals(schema.hash(current),schema.hash(draft("AI_ML",changed)));
    current.put("dataAiEvidence",Map.of("modelFormat","JSON"));assertThrows(ApiError.class,()->schema.completeReview(current));
  }
  @Test void publicAndExportProjectionsRetainSafeEvidenceButCannotLeakNestedInternalFields(){
    var current=draft("AI_ML",evidence(true));var poisoned=new LinkedHashMap<>(evidence(true));poisoned.put("storageKey","private-canary");poisoned.put("sourceBytes",List.of("private-canary"));current.put("dataAiEvidence",poisoned);current.put("institution","private-canary");current.put("sourceBinding",Map.of("sha256","d".repeat(64),"files",List.of("README.md"),"manifestFiles",List.of("pyproject.toml"),"storage_key","private-canary"));current.put("package",Map.of("licenseTerms","MIT notice retained","private","private-canary"));
    String stored=schema.encode(current);assertTrue(stored.contains("private-canary"));var publicView=schema.publicView(current);assertEquals(evidence(true),publicView.get("dataAiEvidence"));assertFalse(schema.encode(publicView).contains("private-canary"));assertEquals(stored,schema.encode(current));
    var export=json.valueToTree(AccountExportResponse.EducationRelease.from(Map.of("snapshot",stored,"draft",stored,"institution","Owner private annotation"),json));assertEquals("Owner private annotation",export.path("institution").asText());assertEquals(json.valueToTree(evidence(true)),export.path("snapshot").path("dataAiEvidence"));assertFalse(export.toString().contains("private-canary"));
    var malformed=new LinkedHashMap<>(poisoned);malformed.put("noRemoteCode","private-canary");malformed.put("modelFormat","pickle");malformed.put("codeLicense",Map.of("private","private-canary"));current.put("dataAiEvidence",malformed);var safe=schema.publicView(current);assertFalse(schema.encode(safe).contains("private-canary"));assertFalse(EducationSnapshot.object(safe.get("dataAiEvidence")).containsKey("modelFormat"));
  }
  @Test void concreteDtoUsesActualStrictApplicationMapperAndNestedBeanValidation()throws Exception{
    var builder=new Jackson2ObjectMapperBuilder();Jackson2ObjectMapperBuilderCustomizer policy=ReflectionTestUtils.invokeMethod(new SecurityConfiguration(),"strictJsonRequests");policy.customize(builder);ObjectMapper strict=builder.build();
    var valid=strict.readValue(json.writeValueAsBytes(Map.of("mode","SHOWCASE","category","AI_ML","dataAiEvidence",evidence(true))),EducationRequests.Draft.class);assertEquals(evidence(true),TypedInputs.map(valid).get("dataAiEvidence"));
    for(Map<String,Object> invalid:List.of(Map.of("unknownField","private"),Map.of("syntheticData","true"),Map.of("noRemoteCode",1),Map.of("codeLicense",42),Map.of("outputSchema",List.of("private"))))assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,()->strict.readValue(json.writeValueAsBytes(Map.of("mode","SHOWCASE","dataAiEvidence",invalid)),EducationRequests.Draft.class));
    try(var factory=Validation.buildDefaultValidatorFactory()){
      assertTrue(factory.getValidator().validate(valid).isEmpty());var bad=new LinkedHashMap<>(evidence(true));bad.put("limitations","x".repeat(4001));bad.put("dataSha256","A".repeat(64));bad.put("modelFormat","joblib");var invalid=strict.readValue(json.writeValueAsBytes(Map.of("mode","SHOWCASE","dataAiEvidence",bad)),EducationRequests.Draft.class);assertEquals(3,factory.getValidator().validate(invalid).size());
    }
  }
}
