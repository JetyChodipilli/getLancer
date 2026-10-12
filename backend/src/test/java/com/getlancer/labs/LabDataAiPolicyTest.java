package com.getlancer.labs;

import static org.junit.jupiter.api.Assertions.*;
import com.getlancer.shared.ApiError;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LabDataAiPolicyTest {
  private LabManifest variant(LabProtocolFixture fixture,String id,String scenario,String kind,int memory){
    var node=fixture.json.valueToTree(fixture.manifest(UUID.randomUUID()));
    ((com.fasterxml.jackson.databind.node.ObjectNode)node).put("id",id).put("memoryMiB",memory);
    if(kind!=null)((com.fasterxml.jackson.databind.node.ObjectNode)node).put("resourceClass",kind);
    ((com.fasterxml.jackson.databind.node.ObjectNode)node.path("scenarios").get(0)).put("id",scenario);
    return fixture.json.convertValue(node,LabManifest.class);
  }
  @Test void disabledPolicyRecognizesNamespaceCuratedIdentityAndSignedRenamedClass(){try(var fixture=new LabProtocolFixture()){
    var policy=new LabDataAiPolicy(false);var ordinary=fixture.manifest(UUID.randomUUID());
    assertEquals("GENERAL",policy.require(ordinary,"request",true));assertTrue(policy.visible(ordinary));
    for(var manifest:java.util.List.of(variant(fixture,"renamed","revenue-summary",null,256),variant(fixture,"data-ai-future","renamed",null,256),variant(fixture,"operator-alias","renamed","AI_ML",256))){
      assertFalse(policy.visible(manifest));assertEquals("DATA_AI_DISABLED",assertThrows(ApiError.class,()->policy.require(manifest,manifest.scenarios().get(0).id(),true)).code);
      assertEquals(403,assertThrows(ApiError.class,()->policy.require(manifest,manifest.scenarios().get(0).id(),false)).status);
    }
  }}
  @Test void enabledPolicyPreservesClassCeilingsAndRejectsMisclassification(){try(var fixture=new LabProtocolFixture()){
    var policy=new LabDataAiPolicy(true);
    assertEquals("DATA_ANALYTICS",policy.require(variant(fixture,"renamed","sensor-quality",null,256),"sensor-quality",true));
    assertEquals("AI_ML",policy.require(variant(fixture,"renamed","custom-inference","AI_ML",256),"custom-inference",true));
    assertEquals("DATA_AI_RESOURCE_LIMIT",assertThrows(ApiError.class,()->policy.require(variant(fixture,"data-ai-wide","sentiment-inference",null,512),"sentiment-inference",true)).code);
    assertEquals("INVALID_DATA_AI_CLASS",assertThrows(ApiError.class,()->policy.require(variant(fixture,"renamed","revenue-summary","AI_ML",256),"revenue-summary",true)).code);
    assertEquals("INVALID_DATA_AI_CLASS",assertThrows(ApiError.class,()->policy.require(variant(fixture,"data-ai-unknown","unknown",null,256),"unknown",true)).code);
    assertThrows(IllegalArgumentException.class,()->variant(fixture,"renamed","request","GPU",256));
  }}
  @Test void historicalOperatorManifestStillRoundTripsWithoutResourceClass()throws Exception{try(var fixture=new LabProtocolFixture()){
    var node=(com.fasterxml.jackson.databind.node.ObjectNode)fixture.json.valueToTree(fixture.manifest(UUID.randomUUID()));node.remove("resourceClass");
    var parsed=fixture.json.readValue(node.toString(),LabManifest.class);assertNull(parsed.resourceClass());assertEquals("GENERAL",new LabDataAiPolicy(false).require(parsed,"request",true));
  }}
}
