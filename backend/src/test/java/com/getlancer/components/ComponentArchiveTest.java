package com.getlancer.components;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.shared.ApiError;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ComponentArchiveTest {
  Map<String,Object> seed()throws Exception{return new ObjectMapper().readValue(getClass().getResourceAsStream("/catalog/components.json"),new com.fasterxml.jackson.core.type.TypeReference<java.util.List<Map<String,Object>>>(){}).get(0);}
  byte[] zip(Map<String,String> files)throws Exception{var output=new java.io.ByteArrayOutputStream();try(var zip=new java.util.zip.ZipOutputStream(output)){for(var file:files.entrySet()){zip.putNextEntry(new java.util.zip.ZipEntry(file.getKey()));zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));zip.closeEntry();}}return output.toByteArray();}
  Map<String,Object> upload(Map<String,String> files)throws Exception{return ComponentArchive.inspect(new MockMultipartFile("file","source.zip","application/zip",zip(files)),"1.0.1",true,seed());}
  @Test void exactReviewedFilesAndDigestsAreRequired()throws Exception{
    var files=(Map<String,String>)seed().get("files");var source=upload(files);var archive=ComponentArchive.reviewed(source);
    assertEquals(source.get("archiveSha256"),archive.archiveSha256());assertEquals(source.get("manifestSha256"),archive.manifestSha256());
    for(String name:files.keySet()){var changed=new LinkedHashMap<>(files);changed.put(name,files.get(name)+" changed");var altered=new LinkedHashMap<>(source);altered.put("files",changed);assertEquals("PREVIEW_SOURCE_MISMATCH",assertThrows(ApiError.class,()->ComponentArchive.reviewed(altered)).code);}
    for(String key:java.util.List.of("archiveSha256","manifestSha256")){var changed=new LinkedHashMap<>(source);changed.put(key,"0".repeat(64));assertThrows(ApiError.class,()->ComponentArchive.reviewed(changed));}
  }
  @Test void rejectUnexpectedPathsOversizedFilesSecretsAndMissingLicence()throws Exception{
    var files=(Map<String,String>)seed().get("files");
    for(var extra:java.util.List.of("../index.html","nested/source.zip",".env","package.json","extra.html")){var changed=new LinkedHashMap<>(files);changed.put(extra,"unexpected source");assertThrows(ApiError.class,()->upload(changed));}
    var large=new LinkedHashMap<>(files);large.put("README.md","a".repeat(100001));assertThrows(ApiError.class,()->upload(large));
    var secret=new LinkedHashMap<>(files);secret.put("README.md","ghp_"+"a".repeat(32));assertThrows(ApiError.class,()->upload(secret));
    var licence=new LinkedHashMap<>(files);licence.put("LICENSE","Copyright with no permission to reuse this source.");assertThrows(ApiError.class,()->upload(licence));
  }
  @Test void consentAndVersionAreRequiredAndCuratedPackagesAreDeterministic()throws Exception{
    var seed=seed();var files=(Map<String,String>)seed.get("files");var file=new MockMultipartFile("file","source.zip","application/zip",zip(files));
    assertThrows(ApiError.class,()->ComponentArchive.inspect(file,"1.0.0",false,seed));assertThrows(ApiError.class,()->ComponentArchive.inspect(file,"bad version",true,seed));
    assertEquals(ComponentArchive.reviewed(seed).archiveSha256(),ComponentArchive.reviewed(seed).archiveSha256());
  }
}
