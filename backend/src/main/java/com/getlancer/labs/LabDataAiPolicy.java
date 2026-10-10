package com.getlancer.labs;

import com.getlancer.shared.ApiError;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Additional admission barrier. It never substitutes for signed provider/source authority. */
@Component
public final class LabDataAiPolicy {
  static final Set<String> DATA=Set.of("revenue-summary","sensor-quality");
  static final Set<String> AI=Set.of("sentiment-inference","equipment-inference");
  static final int MAX_RUN_MEMORY_MIB=256,CLASS_MEMORY_MIB=1024,CLASS_RUNS=4;
  private volatile boolean enabled;
  public LabDataAiPolicy(@Value("${app.data-ai.enabled:false}") boolean enabled){this.enabled=enabled;}
  static boolean applies(LabManifest manifest){return "DATA_ANALYTICS".equals(manifest.resourceClass())||"AI_ML".equals(manifest.resourceClass())
      ||manifest.id().startsWith("data-ai-")||manifest.scenarios().stream().anyMatch(s->DATA.contains(s.id())||AI.contains(s.id()));}
  static String resourceClass(LabManifest manifest,String scenario){
    String known=DATA.contains(scenario)?"DATA_ANALYTICS":AI.contains(scenario)?"AI_ML":null;
    String declared=manifest.resourceClass();
    if(known!=null&&declared!=null&&!declared.equals("GENERAL")&&!declared.equals(known))throw invalid();
    if(known!=null)return known;
    if("DATA_ANALYTICS".equals(declared)||"AI_ML".equals(declared))return declared;
    if(applies(manifest))throw invalid();
    return "GENERAL";
  }
  boolean visible(LabManifest manifest){return !applies(manifest)||enabled;}
  String require(LabManifest manifest,String scenario,boolean admission){
    if(!applies(manifest))return "GENERAL";
    if(!enabled)throw new ApiError(admission?503:403,"DATA_AI_DISABLED","Data and AI execution is disabled. Free source and recorded local evidence remain available.");
    String kind=resourceClass(manifest,scenario);
    if(manifest.memoryMiB()>MAX_RUN_MEMORY_MIB)throw new ApiError(400,"DATA_AI_RESOURCE_LIMIT","Curated data and CPU inference runs reserve at most 256 MiB.");
    return kind;
  }
  private static ApiError invalid(){return new ApiError(400,"INVALID_DATA_AI_CLASS","Certify the scenario with its correct data or CPU inference resource class.");}
}
