package com.getlancer.responses;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AccountExportResponseTest {
  @Test void educationExportKeepsAgreementsAndAcademicAnnotationsWithoutPrivateOrFutureFields() throws Exception {
    var mapper=new ObjectMapper();
    String snapshot="{\"mode\":\"FREE\",\"package\":{\"includedAssets\":[\"source\"],\"licenseTerms\":\"MIT\",\"providerToken\":\"secret-canary\"},\"sourceBinding\":{\"sha256\":\"reviewed-hash\",\"files\":[\"README.md\"],\"storageKey\":\"secret-canary\"},\"categoryEvidence\":{\"modules\":\"API\",\"futureSecret\":\"secret-canary\"},\"componentLinks\":[{\"revision\":2,\"attribution\":\"Original author\",\"privateNotes\":\"secret-canary\"}],\"academicDetails\":\"secret-canary\",\"providerToken\":\"secret-canary\"}";
    var row=Map.<String,Object>of("draft",snapshot,"snapshot",snapshot,"institution","Owner private university","share_academic_details",false,"storage_key","secret-canary");
    var exported=mapper.valueToTree(AccountExportResponse.EducationRelease.from(row,mapper));
    assertEquals("Owner private university",exported.path("institution").asText());
    assertEquals("MIT",exported.path("snapshot").path("package").path("licenseTerms").asText());
    assertEquals("README.md",exported.path("draft").path("sourceBinding").path("files").get(0).asText());
    assertEquals(2,exported.path("snapshot").path("componentLinks").get(0).path("revision").asInt());
    assertFalse(exported.toString().contains("secret-canary"));
    var purchase=mapper.valueToTree(AccountExportResponse.SourcePurchases.from(Map.of("education_snapshot",snapshot),mapper));
    assertEquals("MIT",purchase.path("education_snapshot").path("package").path("licenseTerms").asText());
    assertFalse(purchase.toString().contains("secret-canary"));
  }
  @Test void labExportRetainsUsefulInputsAndOnlyDocumentedResultFields() throws Exception {
    var mapper=new ObjectMapper();
    var row=Map.<String,Object>of("operation_id","inspect","inputs","{\"count\":\"2\"}",
        "response","{\"output\":\"two records\",\"summary\":\"done\",\"providerToken\":\"secret-canary\"}",
        "gateway_secret","secret-canary");
    var exported=mapper.valueToTree(AccountExportResponse.LabRequest.from(row,mapper));
    assertEquals("2",exported.path("inputs").path("count").asText());
    assertEquals("two records",exported.path("response").path("output").asText());
    assertFalse(exported.toString().contains("secret-canary"));
    assertTrue(mapper.valueToTree(AccountExportResponse.LabRequest.from(Map.of(),mapper)).path("response").isNull());
  }
  @Test void componentExportRetainsDraftAndSubmittedSourceAsUsableJsonWithoutDuplicateArchiveBytes() throws Exception {
    var mapper=new ObjectMapper();
    String source=mapper.writeValueAsString(Map.of("version","2.0.0","files",Map.of("index.html","Original creator source","README.md","Local setup","LICENSE","MIT notice"),"archiveBase64","duplicate archive bytes"));
    var exported=mapper.valueToTree(AccountExportResponse.Components.from(Map.of("draft_source",source,"submitted_source",source,"submitted_context","{\"title\":\"Submitted title\"}"),mapper));
    for(String key:java.util.List.of("draft_source","submitted_source")){
      assertEquals("2.0.0",exported.path(key).path("version").asText());
      assertEquals("Original creator source",exported.path(key).path("files").path("index.html").asText());
      assertFalse(exported.path(key).has("archiveBase64"));
    }
    assertEquals("Submitted title",exported.path("submitted_context").path("title").asText());
  }
}
