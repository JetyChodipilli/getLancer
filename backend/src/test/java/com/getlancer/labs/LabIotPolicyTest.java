package com.getlancer.labs;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.getlancer.shared.ApiError;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LabIotPolicyTest {
  static LabManifest manifest(LabProtocolFixture fixture){
    return fixture.iotManifest(UUID.randomUUID());
  }
  @Test void defaultDenialCannotBeBypassedByRenamingSourceOrClass(){try(var fixture=new LabProtocolFixture()){
    var policy=new LabIotPolicy(false);var original=manifest(fixture);
    assertEquals("IOT_DISABLED",assertThrows(ApiError.class,()->policy.require(original,"greenhouse-monitor",true)).code);
    assertEquals(403,assertThrows(ApiError.class,()->policy.require(original,"greenhouse-monitor",false)).status);
    var renamed=(ObjectNode)fixture.json.valueToTree(original);renamed.put("id","operator-renamed").remove("resourceClass");
    assertEquals("IOT_DISABLED",assertThrows(ApiError.class,()->policy.require(fixture.json.convertValue(renamed,LabManifest.class),"greenhouse-monitor",true)).code);
    ((ObjectNode)renamed.path("scenarios").get(0)).put("id","request");renamed.put("resourceClass","IOT_SIMULATION");
    assertEquals("IOT_DISABLED",assertThrows(ApiError.class,()->policy.require(fixture.json.convertValue(renamed,LabManifest.class),"request",true)).code);
    assertEquals("GENERAL",policy.require(fixture.manifest(UUID.randomUUID()),"request",true));
  }}
  @Test void curatedSchemaAndClassCeilingsRejectSignedBroaderAuthority(){try(var fixture=new LabProtocolFixture()){
    var policy=new LabIotPolicy(true);assertEquals("IOT_SIMULATION",policy.require(manifest(fixture),"greenhouse-monitor",true));
    var broad=(ObjectNode)fixture.json.valueToTree(manifest(fixture));broad.put("memoryMiB",256);
    assertEquals("IOT_RESOURCE_LIMIT",assertThrows(ApiError.class,()->policy.require(fixture.json.convertValue(broad,LabManifest.class),"greenhouse-monitor",true)).code);
    for(String alteration:List.of("namespace","class","bound","operation")){
      var node=(ObjectNode)fixture.json.valueToTree(manifest(fixture));
      switch(alteration){case "namespace"->((ObjectNode)node.path("scenarios").get(0)).put("id","custom-simulator");case "class"->node.put("resourceClass","AI_ML");
        case "bound"->((ObjectNode)node.path("scenarios").get(0).path("inputs").get(0)).put("max",10000);case "operation"->((ObjectNode)node.path("scenarios").get(0).path("operations").get(0)).put("id","control-physical-pump");}
      assertEquals("INVALID_IOT_CONTRACT",assertThrows(ApiError.class,()->policy.require(fixture.json.convertValue(node,LabManifest.class),node.path("scenarios").get(0).path("id").asText(),true)).code);
    }
  }}
  @Test void serverRunTopicAndActualInputBoundsCannotBeBroadened(){try(var fixture=new LabProtocolFixture()){
    var policy=new LabIotPolicy(true);var manifest=manifest(fixture);UUID run=UUID.randomUUID();var values=Map.of("temperatureC","-20","humidityPercent","100","thresholdC","60");
    var result=policy.inputs(manifest,manifest.inputs(manifest.scenario("greenhouse-monitor"),values),run);assertEquals("getlancer/"+run+"/telemetry",result.get("topic"));
    for(var invalid:List.of(Map.of("temperatureC","81","humidityPercent","50","thresholdC","30"),Map.of("temperatureC","20.5","humidityPercent","50","thresholdC","30"),
        Map.of("temperatureC","20","humidityPercent","101","thresholdC","30"),Map.of("temperatureC","20","humidityPercent","50","thresholdC","61"),
        Map.of("temperatureC","20","humidityPercent","50","thresholdC","30","topic","getlancer/"+UUID.randomUUID()+"/telemetry"),Map.of("temperatureC","20","humidityPercent","50","thresholdC","30","broker","https://foreign.example")))
      assertThrows(ApiError.class,()->policy.inputs(manifest,invalid,run));
    assertEquals(result,policy.inputs(manifest,result,run));
  }}
}
