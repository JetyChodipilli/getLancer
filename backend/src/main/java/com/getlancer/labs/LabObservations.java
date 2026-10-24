package com.getlancer.labs;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.shared.ApiError;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Native evidence is bound and validated as a whole before any durable cursor advances. */
public final class LabObservations {
  private LabObservations() {}
  public record Transition(UUID runId,String sourceHash,UUID requestId,long sequence,String type,String edge,Instant recordedAt,String summary) {}
  public record Observation(UUID runId,String sourceHash,UUID requestId,String pattern,int status,int durationMs,Map<String,Object> state,List<Transition> events) {}
  private static final Map<String,Set<String>> TYPES=Map.of(
      "cache",Set.of("CACHE_MISS","CACHE_HIT","CACHE_EXPIRED","CACHE_FALLBACK","CACHE_RESET","STORE_READ"),
      "security",Set.of("AUTH_ALLOWED","AUTH_DENIED","AUTH_REVOKED","AUTH_RESET"),
      "payment",Set.of("PAYMENT_APPLIED","PAYMENT_DUPLICATE","PAYMENT_SIGNATURE_DENIED","PAYMENT_CONFLICT","PAYMENT_PENDING","PAYMENT_RESET","REFUND_PENDING","REFUND_APPLIED"));
  static Observation parse(String output,UUID runId,String sourceHash,String pattern,long lastSequence,ObjectMapper mapper) {
    if(!TYPES.containsKey(pattern))return null;
    try {
      if(output==null||output.length()>4096)throw new IllegalArgumentException();
      JsonNode root=mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readTree(output);
      exact(root,"runId","sourceHash","requestId","pattern","status","durationMs","state","events");
      String requestText=text(root,"requestId",36);UUID request=UUID.fromString(requestText);if(!request.toString().equals(requestText))throw new IllegalArgumentException();
      if(!runId.toString().equals(text(root,"runId",36))||!sourceHash.equals(text(root,"sourceHash",64))||!pattern.equals(text(root,"pattern",20)))throw new IllegalArgumentException();
      int status=integer(root,"status",100,599),duration=integer(root,"durationMs",0,30000);
      if(!(pattern.equals("cache")?Set.of(200):pattern.equals("security")?Set.of(200,401,403):Set.of(200,202,401,409)).contains(status))throw new IllegalArgumentException();
      Map<String,Object> state=state(root.path("state"),pattern);
      if(pattern.equals("security")&&(status==401&&!Set.of("EXPIRED","SIGNATURE").contains(state.get("reason"))||status==403&&!Set.of("ROLE","TENANT","REVOKED").contains(state.get("reason"))||status==200&&state.get("decision").equals("DENY")&&!state.get("reason").equals("REVOKED")))throw new IllegalArgumentException();
      if(pattern.equals("payment")&&status==202&&!state.get("payment").equals("PENDING"))throw new IllegalArgumentException();
      JsonNode events=root.path("events");if(!events.isArray()||events.isEmpty()||events.size()>4)throw new IllegalArgumentException();
      List<Transition> transitions=new ArrayList<>();long sequence=lastSequence;Instant observed=null;
      for(JsonNode event:events){
        exact(event,"runId","sourceHash","requestId","sequence","type","edge","recordedAt","summary");
        if(!runId.toString().equals(text(event,"runId",36))||!sourceHash.equals(text(event,"sourceHash",64))||!request.toString().equals(text(event,"requestId",36)))throw new IllegalArgumentException();
        long next=integer(event,"sequence",1,1000000);String type=text(event,"type",40),edge=text(event,"edge",40);
        if(next<=sequence||!TYPES.get(pattern).contains(type)||!edge(type,edge))throw new IllegalArgumentException();
        Instant at=Instant.parse(text(event,"recordedAt",40));if(observed!=null&&at.isBefore(observed))throw new IllegalArgumentException();
        coherent(type,state,status);transitions.add(new Transition(runId,sourceHash,request,next,type,edge,at,text(event,"summary",240)));sequence=next;observed=at;
      }
      return new Observation(runId,sourceHash,request,pattern,status,duration,state,List.copyOf(transitions));
    }catch(Exception invalid){throw new ApiError(502,"LAB_EVIDENCE_UNCONFIRMED","Observed native activity could not be bound to this run and source. No activity cursor was advanced.");}
  }
  private static Map<String,Object> state(JsonNode value,String pattern){
    if(pattern.equals("cache")){
      exact(value,"cache","storeReads","ttlMs");String cache=text(value,"cache",10);if(!Set.of("MISS","HIT","FALLBACK","EXPIRED","RESET").contains(cache))throw new IllegalArgumentException();
      return Map.of("cache",cache,"storeReads",integer(value,"storeReads",0,1000000),"ttlMs",integer(value,"ttlMs",-2,5000));
    }
    if(pattern.equals("security")){
      exact(value,"decision","reason","currentRole");String decision=text(value,"decision",10),reason=text(value,"reason",12),role=text(value,"currentRole",10);
      if(!Set.of("ALLOW","DENY","RESET").contains(decision)||!Set.of("ALLOW","ROLE","TENANT","EXPIRED","SIGNATURE","REVOKED","RESET").contains(reason)||!Set.of("editor","viewer").contains(role))throw new IllegalArgumentException();
      if(decision.equals("ALLOW")!=reason.equals("ALLOW")||decision.equals("RESET")!=reason.equals("RESET")||decision.equals("ALLOW")&&!role.equals("editor"))throw new IllegalArgumentException();
      return Map.of("decision",decision,"reason",reason,"currentRole",role);
    }
    exact(value,"payment","entitlements","processedEvents");String payment=text(value,"payment",12);if(!Set.of("PENDING","PAID","REFUNDED").contains(payment))throw new IllegalArgumentException();
    int entitlements=integer(value,"entitlements",0,1);if(entitlements!=(payment.equals("PAID")?1:0))throw new IllegalArgumentException();
    return Map.of("payment",payment,"entitlements",entitlements,"processedEvents",integer(value,"processedEvents",0,100));
  }
  private static void coherent(String type,Map<String,Object> state,int status){
    if(state.containsKey("cache")){
      String cache=(String)state.get("cache");
      if(type.equals("CACHE_MISS")&&!Set.of("MISS","FALLBACK").contains(cache)||type.equals("STORE_READ")&&(!Set.of("MISS","FALLBACK").contains(cache)||(int)state.get("storeReads")==0)
          ||Set.of("CACHE_HIT","CACHE_EXPIRED","CACHE_FALLBACK","CACHE_RESET").contains(type)&&!type.substring(6).equals(cache))throw new IllegalArgumentException();
    }else if(state.containsKey("decision")){
      String decision=(String)state.get("decision"),reason=(String)state.get("reason");
      if(type.equals("AUTH_ALLOWED")&&!decision.equals("ALLOW")||type.equals("AUTH_DENIED")&&!decision.equals("DENY")||type.equals("AUTH_REVOKED")&&!reason.equals("REVOKED")||type.equals("AUTH_RESET")&&!decision.equals("RESET")
          ||status!=(type.equals("AUTH_DENIED")?(Set.of("EXPIRED","SIGNATURE").contains(reason)?401:403):200))throw new IllegalArgumentException();
    }else{
      String payment=(String)state.get("payment");int processed=(int)state.get("processedEvents");
      if(type.equals("PAYMENT_APPLIED")&&(status!=200||payment.equals("PENDING")||processed==0)||type.equals("REFUND_APPLIED")&&(status!=200||!payment.equals("REFUNDED")||processed==0)
          ||type.equals("REFUND_PENDING")&&(status!=202||!payment.equals("PENDING")||processed==0)||type.equals("PAYMENT_RESET")&&(status!=200||!payment.equals("PENDING")||processed!=0)
          ||type.equals("PAYMENT_DUPLICATE")&&(status!=200||processed==0)||type.equals("PAYMENT_SIGNATURE_DENIED")&&status!=401||type.equals("PAYMENT_CONFLICT")&&status!=409
          ||type.equals("PAYMENT_PENDING")&&status!=(payment.equals("PENDING")?202:200))throw new IllegalArgumentException();
    }
  }
  private static boolean edge(String type,String edge){
    return type.equals("STORE_READ")?edge.equals("service-store"):type.startsWith("CACHE_")?edge.equals("service-redis"):
      Set.of("AUTH_REVOKED","AUTH_RESET").contains(type)?edge.equals("policy-store"):type.startsWith("AUTH_")?Set.of("service-policy","policy-store").contains(edge):
      Set.of("PAYMENT_PENDING","PAYMENT_SIGNATURE_DENIED").contains(type)?edge.equals("service-emulator"):edge.equals("emulator-ledger");
  }
  private static String text(JsonNode node,String name,int max){JsonNode value=node.path(name);if(!value.isTextual()||value.asText().isEmpty()||value.asText().length()>max||value.asText().chars().anyMatch(c->c<32||c==127))throw new IllegalArgumentException();return value.asText();}
  private static int integer(JsonNode node,String name,int min,int max){JsonNode value=node.path(name);if(!value.isIntegralNumber()||!value.canConvertToInt()||value.asInt()<min||value.asInt()>max)throw new IllegalArgumentException();return value.asInt();}
  private static void exact(JsonNode value,String... keys){if(!value.isObject()||value.size()!=keys.length)throw new IllegalArgumentException();for(String key:keys)if(!value.has(key))throw new IllegalArgumentException();}
}
