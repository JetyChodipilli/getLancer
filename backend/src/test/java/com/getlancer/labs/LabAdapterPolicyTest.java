package com.getlancer.labs;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.getlancer.shared.ApiError;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LabAdapterPolicyTest {
  private LabManifest manifest(LabProtocolFixture fixture,String language,String runtime,String version,String state,String scenario){
    var node=(ObjectNode)fixture.json.valueToTree(fixture.manifest(UUID.randomUUID()));node.put("language",language).put("framework","Standard library");
    ((ObjectNode)node.path("scenarios").get(0)).put("id",scenario);
    if(runtime!=null)node.set("adapter",fixture.json.valueToTree(new LabManifest.Adapter(runtime,version,state)));
    return fixture.json.convertValue(node,LabManifest.class);
  }
  @Test void exactSignedFamilyVersionsCoverAllEighteenNativePatternCombinations(){try(var fixture=new LabProtocolFixture()){
    var policy=new LabAdapterPolicy();
    for(var family:List.of(new String[]{"Java","jdk","17.0.16"},new String[]{"TypeScript","node","22.18.0"},new String[]{"Python","python","3.12.8"},
        new String[]{"Go","go","1.24.13"},new String[]{"C# / .NET","dotnet","8.0.22"},new String[]{"PHP","php","8.3.6"})){
      for(String scenario:List.of("cache","security","payment"))assertDoesNotThrow(()->policy.require(manifest(fixture,family[0],family[1],family[2],"ACTIVE",scenario),scenario,true));
    }
  }}
  @Test void signedStateAndVersionNeverEnableUnsupportedCombinations(){try(var fixture=new LabProtocolFixture()){
    var policy=new LabAdapterPolicy();
    for(var combination:List.of(new String[]{"Go","php","8.3.6"},new String[]{"Go","go","1.25.0"},new String[]{"C#","dotnet","9.0.0"},new String[]{"PHP","php","8.2.29"},new String[]{"TypeScript","node","22.17.0"},new String[]{"Java","jdk","21.0.0"})){
      var manifest=manifest(fixture,combination[0],combination[1],combination[2],"ACTIVE","cache");
      assertEquals("LAB_ADAPTER_UNSUPPORTED",assertThrows(ApiError.class,()->policy.require(manifest,"cache",true)).code);
    }
    for(String state:List.of("DISABLED","RETIRED")){
      var manifest=manifest(fixture,"Go","go","1.24.13",state,"cache");
      assertEquals("LAB_ADAPTER_"+state,assertThrows(ApiError.class,()->policy.require(manifest,"cache",true)).code);
      assertEquals(403,assertThrows(ApiError.class,()->policy.require(manifest,"cache",false)).status);
    }
    assertEquals("LAB_SCENARIO_UNSUPPORTED",assertThrows(ApiError.class,()->policy.require(manifest(fixture,"Go","go","1.24.13","ACTIVE","shell"),"shell",true)).code);
    assertEquals("LAB_ADAPTER_UNCERTIFIED",assertThrows(ApiError.class,()->policy.require(manifest(fixture,"Go",null,null,null,"cache"),"cache",true)).code);
    assertEquals("LAB_ADAPTER_UNSUPPORTED",assertThrows(ApiError.class,()->policy.require(manifest(fixture,"Ruby",null,null,null,"cache"),"cache",true)).code);
  }}
  @Test void historicalCertificatesKeepTheirBoundedProtocolAndVersionedDataHasCoverage(){try(var fixture=new LabProtocolFixture()){
    var policy=new LabAdapterPolicy();assertDoesNotThrow(()->policy.require(fixture.manifest(UUID.randomUUID()),"request",true));
    for(String language:List.of("Java","TypeScript","Python"))assertDoesNotThrow(()->policy.require(manifest(fixture,language,null,null,null,"cache"),"cache",true));
    var data=(ObjectNode)fixture.json.valueToTree(fixture.manifest(UUID.randomUUID()));data.put("id","data-ai-original");
    assertDoesNotThrow(()->policy.require(fixture.json.convertValue(data,LabManifest.class),"request",true));
    data.set("adapter",fixture.json.valueToTree(new LabManifest.Adapter("go","1.24.13","ACTIVE")));data.put("language","Go");
    assertEquals("LAB_SCENARIO_UNSUPPORTED",assertThrows(ApiError.class,()->policy.require(fixture.json.convertValue(data,LabManifest.class),"request",true)).code);
    data.remove("adapter");assertEquals("LAB_SCENARIO_UNSUPPORTED",assertThrows(ApiError.class,()->policy.require(fixture.json.convertValue(data,LabManifest.class),"request",true)).code);
    var misclassified=(ObjectNode)fixture.json.valueToTree(manifest(fixture,"Go","go","1.24.13","ACTIVE","cache"));misclassified.put("resourceClass","AI_ML");
    assertEquals("LAB_RESOURCE_CLASS_UNSUPPORTED",assertThrows(ApiError.class,()->policy.require(fixture.json.convertValue(misclassified,LabManifest.class),"cache",true)).code);
    var iot=(ObjectNode)fixture.json.valueToTree(fixture.iotManifest(UUID.randomUUID()));iot.remove("adapter");iot.put("language","Protocol").put("framework","Fixture");
    assertEquals("LAB_ADAPTER_UNSUPPORTED",assertThrows(ApiError.class,()->policy.require(fixture.json.convertValue(iot,LabManifest.class),"greenhouse-monitor",true)).code);
    iot.put("language","Python").set("adapter",fixture.json.valueToTree(new LabManifest.Adapter("python","3.12.8","ACTIVE")));
    assertEquals("LAB_ADAPTER_UNSUPPORTED",assertThrows(ApiError.class,()->policy.require(fixture.json.convertValue(iot,LabManifest.class),"greenhouse-monitor",true)).code);
  }}
  @Test void signedAdapterIdentityCannotBeEditedWithoutInvalidatingManifestProof()throws Exception{try(var fixture=new LabProtocolFixture()){
    var manifest=manifest(fixture,"Go","go","1.24.13","ACTIVE","cache");String payload=fixture.json.writeValueAsString(manifest),signature=fixture.sign(payload);
    assertEquals(manifest,fixture.config().manifest(payload,LabConfiguration.hex(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)),signature,fixture.json));
    String changed=payload.replace("1.24.13","1.25.0");assertThrows(ApiError.class,()->fixture.config().manifest(changed,LabConfiguration.hex(changed.getBytes(java.nio.charset.StandardCharsets.UTF_8)),signature,fixture.json));
    var expired=(ObjectNode)fixture.json.valueToTree(manifest);expired.put("approvedUntil",java.time.Instant.now().minusSeconds(1).toString());String old=expired.toString();
    assertEquals("Go",fixture.config().sourceManifest(old,LabConfiguration.hex(old.getBytes(java.nio.charset.StandardCharsets.UTF_8)),fixture.sign(old),fixture.json).language());
    assertThrows(ApiError.class,()->fixture.config().manifest(old,LabConfiguration.hex(old.getBytes(java.nio.charset.StandardCharsets.UTF_8)),fixture.sign(old),fixture.json));
  }}
}
