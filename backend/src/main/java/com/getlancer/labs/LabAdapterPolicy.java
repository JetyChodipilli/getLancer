package com.getlancer.labs;

import com.getlancer.shared.ApiError;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Reviewed native coverage is narrower than a valid signature: signing never enables an unsupported adapter. */
@Component
public final class LabAdapterPolicy {
  private static final Set<String> PATTERNS=Set.of("cache","security","payment");
  private static final Map<String,String> RUNTIMES=Map.ofEntries(Map.entry("java","jdk"),Map.entry("typescript","node"),Map.entry("javascript","node"),
      Map.entry("python","python"),Map.entry("go","go"),Map.entry("c#","dotnet"),Map.entry(".net","dotnet"),Map.entry("c# / .net","dotnet"),
      Map.entry("dotnet","dotnet"),Map.entry("php","php"));
  void require(LabManifest manifest,String scenario,boolean admission){
    manifest.scenario(scenario);
    String language=manifest.language().toLowerCase(Locale.ROOT),runtime=RUNTIMES.get(language);
    var adapter=manifest.adapter();
    // Existing separately signed protocol fixtures and V5.0 data/CPU manifests keep their historical contract.
    if(adapter==null&&!LabIotPolicy.applies(manifest)&&((language.equals("protocol")&&manifest.framework().equals("Fixture"))||(language.equals("python")&&LabDataAiPolicy.applies(manifest))))return;
    if(runtime==null)throw unavailable(admission,"LAB_ADAPTER_UNSUPPORTED","This language has no reviewed native adapter. Free source remains available.");
    boolean iot=LabIotPolicy.applies(manifest);
    if(!iot&&PATTERNS.contains(scenario)&&manifest.resourceClass()!=null&&!manifest.resourceClass().equals("GENERAL"))
      throw unavailable(admission,"LAB_RESOURCE_CLASS_UNSUPPORTED","This native backend pattern requires its general execution class. Free source remains available.");
    if(manifest.scenarios().stream().anyMatch(item->iot?!item.id().equals(LabIotPolicy.SCENARIO):!PATTERNS.contains(item.id())))
      throw unavailable(admission,"LAB_SCENARIO_UNSUPPORTED","This adapter has no reviewed coverage for the signed scenario combination. Free source remains available.");
    if(adapter==null){
      // Pre-V5.1 Java, TypeScript and Python certificates remain authoritative only for their reviewed three patterns.
      if(!iot&&Set.of("jdk","node","python").contains(runtime))return;
      throw unavailable(admission,"LAB_ADAPTER_UNCERTIFIED","This native adapter needs an explicit signed runtime and version. Free source remains available.");
    }
    if(!adapter.state().equals("ACTIVE"))throw unavailable(admission,"LAB_ADAPTER_"+adapter.state(),
        "This native adapter is "+adapter.state().toLowerCase(Locale.ROOT)+". Free source remains available.");
    if(!runtime.equals(adapter.runtime())||!supportedVersion(runtime,adapter.version())||iot&&!runtime.equals("node"))
      throw unavailable(admission,"LAB_ADAPTER_UNSUPPORTED","This language and runtime version have no reviewed adapter coverage. Free source remains available.");
  }
  static boolean supportedVersion(String runtime,String version){
    String[] parts=version.split("\\.");int major=Integer.parseInt(parts[0]),minor=Integer.parseInt(parts[1]);
    return switch(runtime){case "jdk"->major==17;case "node"->major==22&&minor>=18;
      case "python"->major==3&&minor==12;case "go"->major==1&&minor==24;case "dotnet"->major==8;case "php"->major==8&&minor==3;default->false;};
  }
  private static ApiError unavailable(boolean admission,String code,String reason){return new ApiError(admission?503:403,code,reason);}
}
