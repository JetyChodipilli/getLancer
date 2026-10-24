package com.getlancer.labs;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.getlancer.shared.ApiError;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class LabObservationsTest {
  private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
  private final UUID run=UUID.fromString("11111111-1111-4111-8111-111111111111"),request=UUID.fromString("22222222-2222-4222-8222-222222222222");private final String source="a".repeat(64);
  private ObjectNode observation(String pattern,String type,String edge,Map<String,Object> state,int status){return json.valueToTree(Map.of("runId",run,"sourceHash",source,"requestId",request,"pattern",pattern,"status",status,"durationMs",3,"state",state,"events",List.of(Map.of("runId",run,"sourceHash",source,"requestId",request,"sequence",1,"type",type,"edge",edge,"recordedAt","2026-10-22T00:00:00Z","summary","Observed fixture transition."))));}
  private ObjectNode cache(){return observation("cache","CACHE_MISS","service-redis",Map.of("cache","MISS","storeReads",1,"ttlMs",2000),200);}
  private LabObservations.Observation parse(ObjectNode value,long cursor){return LabObservations.parse(value.toString(),run,source,value.path("pattern").asText(),cursor,json);}
  @Test void actualNativeStateAndEdgesSurviveValidation(){assertEquals("MISS",parse(cache(),0).state().get("cache"));assertEquals("service-policy",parse(observation("security","AUTH_DENIED","service-policy",Map.of("decision","DENY","reason","REVOKED","currentRole","viewer"),403),0).events().get(0).edge());assertEquals(202,parse(observation("payment","REFUND_PENDING","emulator-ledger",Map.of("payment","PENDING","entitlements",0,"processedEvents",1),202),0).status());assertNull(LabObservations.parse("Opaque protocol result",run,source,"request",0,json));}
  @Test void malformedForeignAndReplayedEvidenceCannotProduceAcceptedObservation(){
    List<Consumer<ObjectNode>> mutations=List.of(node->node.put("runId",UUID.randomUUID().toString()),node->node.put("sourceHash","b".repeat(64)),node->node.put("requestId",UUID.randomUUID().toString()),node->node.put("status",401),node->node.put("durationMs",30001),node->((ObjectNode)node.path("state")).put("cache","HIT"),node->((ObjectNode)node.path("state")).put("privateExtra","secret"),node->((ObjectNode)node.path("events").get(0)).put("edge","emulator-ledger"),node->((ObjectNode)node.path("events").get(0)).put("sequence",true),node->((ObjectNode)node.path("events").get(0)).put("recordedAt","not-an-instant"));
    for(var mutation:mutations){var node=cache();mutation.accept(node);assertEquals("LAB_EVIDENCE_UNCONFIRMED",assertThrows(ApiError.class,()->parse(node,0)).code);}assertThrows(ApiError.class,()->parse(cache(),1));assertThrows(ApiError.class,()->LabObservations.parse(cache().toString().replace("\"status\":200","\"status\":200,\"status\":200"),run,source,"cache",0,json));
  }
  @Test void aLaterInvalidTransitionRejectsTheWholeBatch(){var node=cache();var events=(com.fasterxml.jackson.databind.node.ArrayNode)node.path("events");var second=((ObjectNode)events.get(0)).deepCopy();second.put("sequence",2).put("type","STORE_READ").put("edge","service-store");events.add(second);assertEquals(2,parse(node,0).events().size());second.put("sourceHash","b".repeat(64));assertThrows(ApiError.class,()->parse(node,0));second.put("sourceHash",source).put("recordedAt","2026-10-21T23:59:59Z");assertThrows(ApiError.class,()->parse(node,0));}
}
