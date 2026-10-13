package com.getlancer.labs;

import com.getlancer.shared.ApiError;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Independent synthetic protocol gate; arbitrary signed simulations never gain execution authority. */
@Component
public final class LabIotPolicy {
  static final String SCENARIO="greenhouse-monitor",RESOURCE_CLASS="IOT_SIMULATION";
  static final int MAX_RUN_MEMORY_MIB=128,CLASS_MEMORY_MIB=512,CLASS_RUNS=4;
  private static final Map<String,long[]> BOUNDS=Map.of("temperatureC",new long[]{-20,80},"humidityPercent",new long[]{0,100},"thresholdC",new long[]{0,60});
  private static final Set<String> OPERATIONS=Set.of("publish","disconnect","connect","reset");
  private volatile boolean enabled;
  public LabIotPolicy(@Value("${app.iot.enabled:false}") boolean enabled){this.enabled=enabled;}
  static boolean applies(LabManifest manifest){return RESOURCE_CLASS.equals(manifest.resourceClass())||manifest.id().startsWith("iot-")
      ||manifest.scenarios().stream().anyMatch(item->SCENARIO.equals(item.id()));}
  String require(LabManifest manifest,String scenario,boolean admission){
    if(!applies(manifest))return "GENERAL";
    if(!enabled)throw new ApiError(admission?503:403,"IOT_DISABLED","IoT execution is disabled. Free firmware source and local protocol simulation remain available.");
    if((manifest.resourceClass()!=null&&!Set.of("GENERAL",RESOURCE_CLASS).contains(manifest.resourceClass()))||!SCENARIO.equals(scenario)
        ||manifest.scenarios().size()!=1||LabDataAiPolicy.applies(manifest))throw invalid();
    if(manifest.memoryMiB()>MAX_RUN_MEMORY_MIB)throw new ApiError(400,"IOT_RESOURCE_LIMIT","Curated protocol simulations reserve at most 128 MiB.");
    var definition=manifest.scenario(scenario);
    if(definition.inputs().size()<3||definition.inputs().size()>4)throw invalid();
    for(var name:BOUNDS.keySet()){
      var field=definition.inputs().stream().filter(item->name.equals(item.name())).findFirst().orElseThrow(LabIotPolicy::invalid);
      var range=BOUNDS.get(name);
      if(!field.required()||!field.type().equals("INTEGER")||field.min()!=range[0]||field.max()!=range[1]||field.maxLength()<(name.equals("thresholdC")?2:3)||field.maxLength()>4||!field.choices().isEmpty())throw invalid();
    }
    for(var field:definition.inputs())if(!BOUNDS.containsKey(field.name())&&(!field.name().equals("topic")||field.required()||!field.type().equals("TEXT")||field.maxLength()>80||!field.choices().isEmpty()))throw invalid();
    if(definition.operations().stream().anyMatch(item->!OPERATIONS.contains(item.id())))throw invalid();
    return RESOURCE_CLASS;
  }
  /** Canonical topic is supplied by the server and cannot select any other run, broker or physical device. */
  Map<String,String> inputs(LabManifest manifest,Map<String,String> values,UUID run){
    if(!applies(manifest))return values;
    if(values==null||values.size()>4||values.keySet().stream().anyMatch(name->!BOUNDS.containsKey(name)&&!name.equals("topic")))throw invalid();
    String expected="getlancer/"+run+"/telemetry",topic=values.get("topic");
    if(topic!=null&&!topic.equals(expected))throw new ApiError(400,"INVALID_IOT_TOPIC","Use only this run's synthetic telemetry topic.");
    for(var name:BOUNDS.keySet()){
      String value=values.get(name);var range=BOUNDS.get(name);
      try{if(value==null||!value.matches("-?[0-9]{1,3}"))throw invalid();long number=Long.parseLong(value);if(number<range[0]||number>range[1])throw invalid();}
      catch(NumberFormatException invalid){throw invalid();}
    }
    var bounded=new TreeMap<>(values);bounded.put("topic",expected);return Map.copyOf(bounded);
  }
  private static ApiError invalid(){return new ApiError(400,"INVALID_IOT_CONTRACT","Certify only the bounded greenhouse telemetry inputs and protocol operations.");}
}
