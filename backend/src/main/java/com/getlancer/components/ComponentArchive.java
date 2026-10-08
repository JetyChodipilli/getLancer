package com.getlancer.components;

import com.getlancer.hosting.StaticArchive;
import com.getlancer.shared.ApiError;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

/** Free, self-contained frontend contributions; never extracts or executes creator code. */
public final class ComponentArchive {
  private ComponentArchive() {}
  private static final String MIT_BODY="Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions: The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software. THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.";
  static Map<String,Object> inspect(MultipartFile file,String version,boolean consent,Map<String,Object> base) throws IOException {
    if(!consent)throw new ApiError(400,"RIGHTS_CONSENT_REQUIRED","Confirm your rights and preserve the MIT attribution.");
    if(version==null||!version.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,39}"))throw new ApiError(400,"VALIDATION_ERROR","Choose an ASCII version of at most 40 characters.");
    if(file.isEmpty()||file.getSize()>StaticArchive.MAX_COMPRESSED||file.getOriginalFilename()==null||!file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT).endsWith(".zip"))throw new ApiError(400,"INVALID_COMPONENT_ARCHIVE","Choose a ZIP up to 5 MiB.");
    byte[] bytes;try(var input=file.getInputStream()){bytes=input.readNBytes(StaticArchive.MAX_COMPRESSED+1);}
    var inspected=StaticArchive.inspect(bytes);
    var files=new LinkedHashMap<String,String>();
    for(var item:inspected.files()){
      if(!Set.of("index.html","README.md","LICENSE").contains(item.path())||item.sizeBytes()>100000)throw new ApiError(400,"INVALID_COMPONENT_ARCHIVE","Include only index.html, README.md and LICENSE, each up to 100 kB. Inline your CSS and JavaScript.");
      files.put(item.path(),new String(item.content(),StandardCharsets.UTF_8));
    }
    if(files.size()!=3||!files.keySet().equals(Set.of("index.html","README.md","LICENSE")))throw new ApiError(400,"INVALID_COMPONENT_ARCHIVE","Include source, setup instructions and the MIT licence.");
    String licence=files.get("LICENSE");
    int grant=licence.indexOf("Permission is hereby granted");
    if(grant<0||!licence.substring(0,grant).contains("Copyright")||!licence.substring(grant).strip().replaceAll("\\s+"," ").equals(MIT_BODY))throw new ApiError(400,"MIT_LICENSE_REQUIRED","Include the full unmodified MIT licence and copyright attribution.");
    var source=new LinkedHashMap<>(base);
    source.put("version",version);source.put("files",files);source.put("sha256",StaticArchive.sha(files.get("index.html").getBytes(StandardCharsets.UTF_8)));
    source.put("archiveSha256",inspected.archiveSha256());source.put("manifestSha256",inspected.manifestSha256());
    source.put("archiveBase64",Base64.getEncoder().encodeToString(bytes));
    return source;
  }
  /** Reviewed snapshots, including setup and licence files, must match the published package. */
  static StaticArchive.Inspection reviewed(Map<String,Object> source) {
    try {
      byte[] bytes;
      if(source.get("archiveBase64") instanceof String encoded) bytes=Base64.getDecoder().decode(encoded);
      else {
        var output=new java.io.ByteArrayOutputStream();
        try(var zip=new java.util.zip.ZipOutputStream(output,StandardCharsets.UTF_8)) {
          for(var item:new java.util.TreeMap<>((Map<String,String>)source.get("files")).entrySet()) {
            var entry=new java.util.zip.ZipEntry(item.getKey());entry.setTime(0);zip.putNextEntry(entry);
            zip.write(item.getValue().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
          }
        }
        bytes=output.toByteArray();
      }
      var inspected=StaticArchive.inspect(bytes);
      var files=new LinkedHashMap<String,String>();
      for(var file:inspected.files()) files.put(file.path(),new String(file.content(),StandardCharsets.UTF_8));
      if(!files.equals(source.get("files"))||!StaticArchive.sha(files.get("index.html").getBytes(StandardCharsets.UTF_8)).equals(source.get("sha256"))
        ||source.containsKey("archiveSha256")&&!inspected.archiveSha256().equals(source.get("archiveSha256"))
        ||source.containsKey("manifestSha256")&&!inspected.manifestSha256().equals(source.get("manifestSha256")))
        throw new ApiError(409,"PREVIEW_SOURCE_MISMATCH","The preview package differs from the reviewed source.");
      return inspected;
    }catch(ApiError e){throw e;}catch(Exception e){throw new ApiError(409,"PREVIEW_SOURCE_MISMATCH","The reviewed source package is unavailable.");}
  }
}
