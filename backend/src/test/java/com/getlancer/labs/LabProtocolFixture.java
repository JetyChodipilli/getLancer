package com.getlancer.labs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Controlled HTTP protocol fixture only: does not create a VM, Redis instance or isolation evidence. */
public final class LabProtocolFixture implements AutoCloseable {
  public static final String SECRET="fixture-secret-never-production-0123456789";
  public final ObjectMapper json=new ObjectMapper().findAndRegisterModules();public final KeyPair key;public final UUID epoch=UUID.randomUUID();public final String evidenceHash="e".repeat(64);
  public final Path evidence;private final HttpServer server;public final Map<UUID,LabProviderClient.Command> commands=new ConcurrentHashMap<>();
  private final Map<UUID,Long> leases=new ConcurrentHashMap<>();private final java.util.Set<UUID> healthyRuns=ConcurrentHashMap.newKeySet();
  public volatile boolean failStart,failStop,failRequest,wrongBinding,redirect,oversized,notIsolated,slowBody;
  public LabProtocolFixture(){try{
    key=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();evidence=Files.createTempFile("lab-protocol-evidence-",".json");
    writeEvidence(new LabConfiguration.Evidence("Controlled HTTP protocol fixture; not deployment evidence","KVM",true,true,true,true,true,Instant.now().plusSeconds(3600),evidenceHash,epoch));
    server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/v1/lab-commands",exchange->{
      try{
        if(!exchange.getRequestMethod().equals("POST")||!("Bearer "+SECRET).equals(exchange.getRequestHeaders().getFirst("Authorization"))){exchange.sendResponseHeaders(403,-1);return;}
        byte[] bytes=exchange.getRequestBody().readNBytes(65537);if(bytes.length>65536){exchange.sendResponseHeaders(413,-1);return;}
        var command=json.readValue(bytes,LabProviderClient.Command.class);commands.put(command.commandId(),command);leases.merge(command.runId(),command.leaseGeneration(),Math::max);
        if(command.leaseGeneration()<leases.get(command.runId())){exchange.sendResponseHeaders(409,-1);return;}
        if(redirect){exchange.getResponseHeaders().set("Location","http://127.0.0.1:1/secret");exchange.sendResponseHeaders(302,-1);return;}
        if(command.action().equals("START")&&failStart||command.action().equals("STOP")&&failStop||command.action().equals("REQUEST")&&failRequest){exchange.sendResponseHeaders(503,-1);return;}
        if(command.action().equals("START"))healthyRuns.add(command.runId());String state=command.action().equals("STOP")?"CLEANED":command.action().equals("REQUEST")?"RESULT":"RUNNING";
        var result=new LabProviderClient.Result(wrongBinding?UUID.randomUUID():command.runId(),command.leaseGeneration(),command.operatorEpoch(),command.commandId(),command.manifestSha256(),command.imageDigest(),command.expiresAt(),state,state.equals("RUNNING")||state.equals("RESULT"),!notIsolated,healthyRuns.contains(command.runId()),command.action().equals("REQUEST")?"Fixture operation completed":"","Controlled protocol fixture");
        byte[] output=oversized?"x".repeat(20000).getBytes(java.nio.charset.StandardCharsets.UTF_8):json.writeValueAsBytes(result);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,output.length);if(slowBody)try{Thread.sleep(3000);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}exchange.getResponseBody().write(output);
      }catch(Exception error){exchange.sendResponseHeaders(500,-1);}finally{exchange.close();}
    });server.start();
  }catch(Exception failure){throw new IllegalStateException(failure);}}
  public String origin(){return "http://127.0.0.1:"+server.getAddress().getPort();}
  public String publicKey(){return Base64.getEncoder().encodeToString(key.getPublic().getEncoded());}
  public String sign(String payload){try{var signer=Signature.getInstance("Ed25519");signer.initSign(key.getPrivate());signer.update(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));return Base64.getEncoder().encodeToString(signer.sign());}catch(Exception failure){throw new IllegalStateException(failure);}}
  public void writeEvidence(LabConfiguration.Evidence value){try{String payload=json.writeValueAsString(value);Files.writeString(evidence,json.writeValueAsString(Map.of("payload",Base64.getEncoder().encodeToString(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)),"signature",sign(payload))));}catch(Exception failure){throw new IllegalStateException(failure);}}
  public LabConfiguration config(){return new LabConfiguration(true,origin(),SECRET,publicKey(),evidence.toString(),epoch.toString(),1000,json);}
  public LabManifest manifest(UUID component){return new LabManifest("protocol-fixture",component,1,"a".repeat(64),"sha256:"+"b".repeat(64),"c".repeat(64),"d".repeat(64),"c".repeat(64),"getlancer-lab-v1","Protocol fixture","A test-only source certificate for the bounded HTTP protocol.","Protocol","Fixture","This is a fixture document, not executable Redis source or KVM evidence.",64,1000,Instant.now().plusSeconds(1800),evidenceHash,List.of(new LabManifest.Scenario("request","Bounded request","Submit a labelled fixture operation.",List.of(new LabManifest.Input("message","Message","TEXT",true,120,0,0,List.of()),new LabManifest.Input("count","Count","INTEGER",false,4,1,10,List.of())),List.of(new LabManifest.Operation("echo","Echo fixture input")))));}
  public void reset(){failStart=failStop=failRequest=wrongBinding=redirect=oversized=notIsolated=slowBody=false;commands.clear();leases.clear();healthyRuns.clear();}
  @Override public void close(){server.stop(0);try{Files.deleteIfExists(evidence);}catch(Exception ignored){}}
}
