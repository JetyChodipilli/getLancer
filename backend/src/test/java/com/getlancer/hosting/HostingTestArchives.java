package com.getlancer.hosting;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
public final class HostingTestArchives {
  public static byte[] built(){return zip(Map.of("index.html","<!doctype html><html><body><h1>Reviewed demo</h1><script src=\"assets/app.js\"></script></body></html>".getBytes(StandardCharsets.UTF_8),"assets/app.js","document.body.dataset.ready='true';".getBytes(StandardCharsets.UTF_8),"assets/style.css","body { color: #123456; }".getBytes(StandardCharsets.UTF_8),"README.md","Built static frontend demo, with no server process or install step.".getBytes(StandardCharsets.UTF_8),"LICENSE","MIT licensed sample. Preserve third-party notices when publishing.".getBytes(StandardCharsets.UTF_8)));}
  public static byte[] zip(Map<String,byte[]> files){try{var bytes=new ByteArrayOutputStream();try(var stream=new ZipOutputStream(bytes)){for(var e:files.entrySet()){stream.putNextEntry(new ZipEntry(e.getKey()));stream.write(e.getValue());stream.closeEntry();}}return bytes.toByteArray();}catch(IOException e){throw new IllegalStateException(e);}}
}
