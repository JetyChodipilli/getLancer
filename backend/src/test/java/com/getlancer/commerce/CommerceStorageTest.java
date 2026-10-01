package com.getlancer.commerce;

import static org.junit.jupiter.api.Assertions.*;
import com.getlancer.shared.*;
import com.sun.net.httpserver.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class CommerceStorageTest {
  @Test void realAwsClientKeepsRandomizedObjectsPrivateAndRejectsModifiedBytes() throws Exception {
    var objects=new ConcurrentHashMap<String,byte[]>();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var executor=Executors.newCachedThreadPool();server.setExecutor(executor);server.createContext("/",exchange->{String path=exchange.getRequestURI().getPath();if(exchange.getRequestMethod().equals("PUT")) {assertNotNull(exchange.getRequestHeaders().getFirst("Authorization"));assertNull(exchange.getRequestHeaders().getFirst("x-amz-acl"));byte[] bytes=exchange.getRequestBody().readAllBytes();if(Objects.toString(exchange.getRequestHeaders().getFirst("Content-Encoding"),"").contains("aws-chunked")) bytes=decodeChunks(bytes);objects.put(path,bytes);exchange.sendResponseHeaders(200,-1);}else {byte[] bytes=objects.get(path);exchange.getResponseHeaders().set("Content-Type","application/zip");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);}exchange.close();});server.start();
    try {var storage=new CommerceStorage("http://127.0.0.1:"+server.getAddress().getPort(),"us-east-1","fixture-access","fixture-private-secret","private-proof");byte[] source="immutable archive".getBytes();String hash=Support.hash("immutable archive");UUID release=UUID.randomUUID();String first=storage.put(release,source,hash),second=storage.put(release,source,hash);assertNotEquals(first,second);assertArrayEquals(source,storage.read(first,hash,source.length));objects.put("/private-proof/"+first,"modified archive".getBytes());assertThrows(ApiError.class,()->storage.read(first,hash,source.length));assertThrows(ApiError.class,()->storage.read("https://attacker.invalid/source.zip",hash,source.length));}
    finally {server.stop(0);executor.shutdownNow();}
  }
  @Test void unconfiguredPrivateStorageReturnsExplicitError() {var storage=new CommerceStorage("http://127.0.0.1:1","us-east-1","","","private-proof");ApiError e=assertThrows(ApiError.class,()->storage.put(UUID.randomUUID(),new byte[]{1},"a".repeat(64)));assertEquals("STORAGE_UNAVAILABLE",e.code);}
  private static byte[] decodeChunks(byte[] bytes) throws java.io.IOException {var input=new java.io.ByteArrayInputStream(bytes);var output=new java.io.ByteArrayOutputStream();while(true) {var line=new StringBuilder();int ch;while((ch=input.read())!=-1) {if(ch=='\r') {if(input.read()!='\n') throw new java.io.IOException();break;}line.append((char)ch);}int length=Integer.parseInt(line.toString().split(";",2)[0],16);if(length==0) break;output.write(input.readNBytes(length));if(input.read()!='\r' || input.read()!='\n') throw new java.io.IOException();}return output.toByteArray();}
}
