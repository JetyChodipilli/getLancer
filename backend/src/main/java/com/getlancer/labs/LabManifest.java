package com.getlancer.labs;

import com.getlancer.shared.ApiError;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Operator-signed immutable manifest. Infrastructure fields never enter the public projection. */
public record LabManifest(String id,UUID componentId,long revision,String sourceSha256,String imageDigest,
    String archiveSha256,String manifestSha256,String buildSourceSha256,String protocolVersion,String title,String summary,String language,String framework,String setup,
    int memoryMiB,long maxCostMicros,Instant approvedUntil,String evidenceSha256,List<Scenario> scenarios,String resourceClass) {
  /** Historical operator manifests remain GENERAL unless their curated identities require V5.0 policy. */
  public LabManifest(String id,UUID componentId,long revision,String sourceSha256,String imageDigest,
      String archiveSha256,String manifestSha256,String buildSourceSha256,String protocolVersion,String title,String summary,String language,String framework,String setup,
      int memoryMiB,long maxCostMicros,Instant approvedUntil,String evidenceSha256,List<Scenario> scenarios){
    this(id,componentId,revision,sourceSha256,imageDigest,archiveSha256,manifestSha256,buildSourceSha256,protocolVersion,title,summary,language,framework,setup,memoryMiB,maxCostMicros,approvedUntil,evidenceSha256,scenarios,null);
  }
  public record Input(String name,String label,String type,boolean required,int maxLength,long min,long max,List<String> choices) {public Input {choices=choices==null?null:List.copyOf(choices);}}
  public record Operation(String id,String label) {}
  public record Scenario(String id,String title,String description,List<Input> inputs,List<Operation> operations) {public Scenario {inputs=inputs==null?null:List.copyOf(inputs);operations=operations==null?null:List.copyOf(operations);}}
  public LabManifest {
    if(resourceClass!=null&&!Set.of("GENERAL","DATA_ANALYTICS","AI_ML").contains(resourceClass))throw invalid();
    if(id==null||!id.matches("[a-z0-9][a-z0-9-]{0,79}")||componentId==null||revision<1
        ||sourceSha256==null||!sourceSha256.matches("[a-f0-9]{64}")||imageDigest==null||!imageDigest.matches("sha256:[a-f0-9]{64}")
        ||archiveSha256==null||!archiveSha256.matches("[a-f0-9]{64}")||manifestSha256==null||!manifestSha256.matches("[a-f0-9]{64}")
        ||!archiveSha256.equals(buildSourceSha256)||!"getlancer-lab-v1".equals(protocolVersion)||memoryMiB<64||memoryMiB>512||maxCostMicros<1||maxCostMicros>1_000_000
        ||approvedUntil==null||!approvedUntil.isAfter(Instant.now())||approvedUntil.isAfter(Instant.now().plusSeconds(31L*86400))||evidenceSha256==null||!evidenceSha256.matches("[a-f0-9]{64}"))throw invalid();
    bounded(title,3,100);bounded(summary,10,500);bounded(language,1,50);bounded(framework,1,80);bounded(setup,10,6000);
    if(scenarios==null||scenarios.isEmpty()||scenarios.size()>20)throw invalid();scenarios=List.copyOf(scenarios);
    var ids=new java.util.HashSet<String>();
    for(var scenario:scenarios){slug(scenario.id());if(!ids.add(scenario.id()))throw invalid();bounded(scenario.title(),3,100);bounded(scenario.description(),10,1000);
      if(scenario.inputs()==null||scenario.inputs().size()>20||scenario.operations()==null||scenario.operations().isEmpty()||scenario.operations().size()>10)throw invalid();
      var names=new java.util.HashSet<String>();for(var input:scenario.inputs()){
        if(input.name()==null||!input.name().matches("[a-z][a-zA-Z0-9]{0,39}")||!names.add(input.name())||!Set.of("TEXT","INTEGER","BOOLEAN","ENUM").contains(input.type())
            ||input.maxLength()<1||input.maxLength()>4096||input.min()>input.max()||input.choices()==null||input.choices().size()>30)throw invalid();bounded(input.label(),1,80);
        for(String choice:input.choices())bounded(choice,1,input.maxLength());if(input.type().equals("ENUM")&&input.choices().isEmpty())throw invalid();
      }
      var operations=new java.util.HashSet<String>();for(var operation:scenario.operations()){slug(operation.id());if(!operations.add(operation.id()))throw invalid();bounded(operation.label(),1,80);}
    }
  }
  public Scenario scenario(String scenarioId){return scenarios.stream().filter(s->s.id().equals(scenarioId)).findFirst().orElseThrow(LabManifest::invalid);}
  public Map<String,String> inputs(Scenario scenario,Map<String,String> supplied){
    if(supplied==null||supplied.size()>20)throw invalid();var validated=new TreeMap<String,String>();var allowed=scenario.inputs().stream().map(Input::name).collect(java.util.stream.Collectors.toSet());
    if(!allowed.containsAll(supplied.keySet()))throw invalid();
    for(var definition:scenario.inputs()){
      String value=supplied.get(definition.name());if(value==null||value.isBlank()){if(definition.required())throw invalid();continue;}
      if(value.length()>definition.maxLength()||value.chars().anyMatch(c->c<32&&c!='\n'&&c!='\t')||value.indexOf(127)>=0)throw invalid();
      switch(definition.type()){
        case "INTEGER"->{try{if(!value.matches("-?[0-9]{1,18}"))throw invalid();long number=Long.parseLong(value);if(number<definition.min()||number>definition.max())throw invalid();}catch(NumberFormatException e){throw invalid();}}
        case "BOOLEAN"->{if(!Set.of("true","false").contains(value))throw invalid();}
        case "ENUM"->{if(!definition.choices().contains(value))throw invalid();}
        default->{}
      }validated.put(definition.name(),value);
    }return Map.copyOf(validated);
  }
  private static void slug(String text){if(text==null||!text.matches("[a-z0-9][a-z0-9-]{0,59}"))throw invalid();}
  private static void bounded(String value,int min,int max){if(value==null||value.length()<min||value.length()>max)throw invalid();}
  private static ApiError invalid(){return new ApiError(400,"INVALID_LAB_INPUT","Choose a supported scenario and check its input fields.");}
}
