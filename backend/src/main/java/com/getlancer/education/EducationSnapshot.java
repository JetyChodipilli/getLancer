package com.getlancer.education;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.shared.ApiError;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** One bounded content schema and canonical digest for immutable education agreements. */
final class EducationSnapshot {
  static final Map<String,List<String>> EVIDENCE=Map.of(
      "FULL_STACK",List.of("modules","schemaApi","demoAccounts","setupMigrations","versions","tests","deployment"),
      "DATA_ANALYTICS",List.of("dataSourceLicense","sampleSchema","transformations","notebook","charts","interpretation","reproducibility"),
      "AI_ML",List.of("task","datasetModelLicense","splits","evaluation","metrics","inference","limitations"),
      "IOT",List.of("boardFirmware","billOfMaterials","wiring","powerConnectivity","topics","evidence"));
  static final Set<String> DIFFICULTIES=Set.of("BEGINNER","INTERMEDIATE","ADVANCED");
  private static final Set<String> FIELDS=Set.of("revision","category","mode","difficulty","demoMode","demoUrl","package","categoryEvidence","componentLinks","versionId","rightsConsent");
  private static final List<String> ARRAYS=List.of("includedAssets","excludedAssets","setupSteps","prerequisites","limitations");
  private final ObjectMapper json;
  EducationSnapshot(ObjectMapper json){this.json=json;}
  Map<String,Object> draft(Map<String,Object> input){
    if(!FIELDS.containsAll(input.keySet()))throw invalid("Use the publishing fields shown in the form.");
    var out=new LinkedHashMap<String,Object>();
    for(String field:List.of("category","mode","difficulty","demoMode","demoUrl"))out.put(field,text(input.get(field),field.equals("demoUrl")?1000:30));
    allowed(out,"category",EVIDENCE.keySet());allowed(out,"mode",Set.of("SHOWCASE","FREE","PAID"));
    if(Objects.toString(out.get("mode")).isEmpty())throw invalid("Choose an access mode.");
    allowed(out,"difficulty",DIFFICULTIES);allowed(out,"demoMode",Set.of("SOURCE_ONLY","EXTERNAL","HOSTED"));
    var evidence=object(input.get("categoryEvidence"));var keys=EVIDENCE.values().stream().flatMap(List::stream).collect(java.util.stream.Collectors.toSet());
    if(evidence.size()>12||!keys.containsAll(evidence.keySet()))throw invalid("Choose supported category evidence fields.");
    var bounded=new LinkedHashMap<String,Object>();evidence.forEach((key,value)->bounded.put(key,text(value,4000)));out.put("categoryEvidence",bounded);
    var details=object(input.get("package"));var detailKeys=new java.util.HashSet<>(ARRAYS);detailKeys.add("supportTerms");detailKeys.add("licenseTerms");
    if(!detailKeys.containsAll(details.keySet()))throw invalid("Use supported package disclosures.");
    var pack=new LinkedHashMap<String,Object>();for(String field:ARRAYS)pack.put(field,strings(details.get(field)));
    pack.put("supportTerms",text(details.get("supportTerms"),4000));pack.put("licenseTerms",text(details.get("licenseTerms"),8000));out.put("package",pack);
    Object raw=input.get("componentLinks");if(raw!=null&&!(raw instanceof List<?>))throw invalid("Component links must be a list.");
    var links=raw==null?List.of():(List<?>)raw;if(links.size()>20)throw invalid("Link at most twenty immutable component versions.");
    var normalized=new ArrayList<Map<String,Object>>();var ids=new java.util.HashSet<String>();
    for(Object value:links){var link=object(value);if(!Set.of("componentId","revision","license","attribution").equals(link.keySet()))throw invalid("Each component link needs its exact identity, license and attribution.");
      UUID component;try{component=UUID.fromString(link.get("componentId").toString());}catch(Exception e){throw invalid("Choose a valid component release.");}
      if(!(link.get("revision") instanceof Number revision)||revision.longValue()<1)throw invalid("Choose a valid component revision.");
      String identity=component+":"+revision.longValue(),license=text(link.get("license"),200),attribution=text(link.get("attribution"),1000);
      if(license.isBlank()||attribution.length()<3||!ids.add(identity))throw invalid("Each component needs one immutable release and original attribution.");
      normalized.add(Map.of("componentId",component.toString(),"revision",revision.longValue(),"license",license,"attribution",attribution));
    }
    out.put("componentLinks",normalized);return out;
  }
  void complete(Map<String,Object> draft){
    String category=Objects.toString(draft.get("category"),"");if(!EVIDENCE.containsKey(category))throw invalid("Choose a supported college project category.");
    required(draft,"difficulty",1);required(draft,"demoMode",1);var evidence=object(draft.get("categoryEvidence"));
    if(!EVIDENCE.get(category).containsAll(evidence.keySet()))throw invalid("Remove fields belonging to another category.");
    for(String key:EVIDENCE.get(category))required(evidence,key,10);
    var pack=object(draft.get("package"));for(String field:ARRAYS){var values=(List<?>)pack.get(field);if(values.isEmpty()||values.stream().anyMatch(v->v.toString().trim().length()<3))throw invalid("Complete "+field+"; state none explicitly when appropriate.");}
    required(pack,"supportTerms",10);required(pack,"licenseTerms",20);
    if("SOURCE_ONLY".equals(draft.get("demoMode"))&&!Objects.toString(draft.get("demoUrl"),"").isBlank())throw invalid("Source-only projects cannot claim a demo URL.");
  }
  Map<String,Object> read(Object value){try{return json.readValue(value.toString(),new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception e){throw new ApiError(409,"EDUCATION_INTEGRITY_ERROR","Stored education content could not be verified.");}}
  List<?> list(Object value){try{return json.readValue(value.toString(),List.class);}catch(Exception e){throw new ApiError(409,"EDUCATION_INTEGRITY_ERROR","Stored source manifest could not be verified.");}}
  String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
  String hash(Map<String,Object> value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(canonical(value)).getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
  private Object canonical(Object value){if(value instanceof Map<?,?> map){var out=new TreeMap<String,Object>();map.forEach((key,item)->out.put(key.toString(),canonical(item)));return out;}if(value instanceof List<?> list)return list.stream().map(this::canonical).toList();return value;}
  static Map<String,Object> object(Object value){if(value==null)return Map.of();if(!(value instanceof Map<?,?> map)||map.keySet().stream().anyMatch(k->!(k instanceof String)))throw invalid("Use structured education objects.");var out=new LinkedHashMap<String,Object>();map.forEach((key,item)->out.put(key.toString(),item));return out;}
  private static List<String> strings(Object value){if(value==null)return List.of();if(!(value instanceof List<?> list)||list.size()>30)throw invalid("Use at most thirty disclosure items.");return list.stream().map(v->text(v,2000)).toList();}
  private static String text(Object value,int max){if(value==null)return "";if(!(value instanceof String text)||text.length()>max)throw invalid("Check the length and text value of the field.");return text.trim();}
  private static void required(Map<String,Object> value,String key,int minimum){if(!(value.get(key) instanceof String text)||text.trim().length()<minimum)throw invalid("Complete "+key+" before submitting for review.");}
  private static void allowed(Map<String,Object> value,String key,Set<String> options){if(!value.get(key).equals("")&&!options.contains(value.get(key)))throw invalid("Choose a supported "+key+".");}
  static ApiError invalid(String message){return new ApiError(400,"VALIDATION_ERROR",message);}
}
