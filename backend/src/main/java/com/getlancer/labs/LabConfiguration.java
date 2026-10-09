package com.getlancer.labs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.shared.ApiError;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Admission is fail closed: operator configuration is insufficient without independently signed evidence. */
@Component
public final class LabConfiguration {
  public record Runtime(boolean enabled,String reason) {}
  public record Evidence(String provider,String isolation,boolean licenceReviewed,boolean budgetApproved,
      boolean networkVerified,boolean cleanupVerified,boolean restoreVerified,Instant approvedUntil,
      String evidenceSha256,UUID operatorEpoch) {}
  final URI origin;final String secret;final UUID epoch;final int timeoutMs;private final PublicKey key;
  private final boolean requested;private final Evidence evidence;private final String invalidReason;
  public LabConfiguration(@Value("${app.labs.enabled:false}") boolean enabled,@Value("${app.labs.gateway-origin:}") String gatewayOrigin,
      @Value("${app.labs.gateway-secret:}") String secret,@Value("${app.labs.operator-public-key:}") String publicKey,
      @Value("${app.labs.admission-evidence:}") String evidencePath,@Value("${app.labs.operator-epoch:}") String epoch,
      @Value("${app.labs.timeout-ms:3000}") int timeoutMs,ObjectMapper json){
    this.requested=enabled;this.secret=secret;this.timeoutMs=timeoutMs;URI parsed=null;PublicKey parsedKey=null;UUID parsedEpoch=null;Evidence approved=null;
    String failure="Lab execution requires independent provider, isolation, licence, budget, cleanup and restore admission evidence.";
    try{
      parsed=URI.create(gatewayOrigin);parsedEpoch=UUID.fromString(epoch);
      if(!validOrigin(parsed)||secret==null||!secret.matches("[!-~]{32,256}")||timeoutMs<500||timeoutMs>5000)throw new IllegalArgumentException();
      parsedKey=KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)));
      Path file=Path.of(evidencePath);if(Files.isSymbolicLink(file)||!Files.isRegularFile(file,java.nio.file.LinkOption.NOFOLLOW_LINKS)||Files.size(file)>65536)throw new IllegalArgumentException();
      var envelope=json.readTree(Files.readAllBytes(file));if(!envelope.isObject()||envelope.size()!=2||!envelope.has("payload")||!envelope.has("signature")||!envelope.get("payload").isTextual()||!envelope.get("signature").isTextual())throw new IllegalArgumentException();byte[] payload=Base64.getDecoder().decode(envelope.path("payload").asText());if(payload.length>32768||!signature(parsedKey,payload,envelope.path("signature").asText()))throw new IllegalArgumentException();
      approved=json.readValue(payload,Evidence.class);
      if(approved.provider()==null||approved.provider().isBlank()||!"KVM".equals(approved.isolation())||!approved.licenceReviewed()||!approved.budgetApproved()||!approved.networkVerified()||!approved.cleanupVerified()||!approved.restoreVerified()
          ||approved.approvedUntil()==null||(!approved.approvedUntil().isAfter(Instant.now())||approved.approvedUntil().isAfter(Instant.now().plusSeconds(31L*86400)))||approved.evidenceSha256()==null||!approved.evidenceSha256().matches("[a-f0-9]{64}")||!parsedEpoch.equals(approved.operatorEpoch()))throw new IllegalArgumentException();
    }catch(Exception error){approved=null;}
    this.origin=parsed;this.key=parsedKey;this.epoch=parsedEpoch;this.evidence=approved;this.invalidReason=failure;
  }
  public Runtime projection(){boolean ready=requested&&evidence!=null&&evidence.approvedUntil().isAfter(Instant.now());return new Runtime(ready,ready?"Operator admission evidence is current.":requested?invalidReason:"Lab execution is disabled. Reviewed source and setup remain free.");}
  void ready(){if(!projection().enabled())throw new ApiError(503,"LAB_DISABLED",projection().reason());}
  LabManifest manifest(String payload,String sha,String detached,ObjectMapper json){
    try{byte[] bytes=payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);if(bytes.length>65536||!hex(bytes).equals(sha)||key==null||!signature(key,bytes,detached))throw new IllegalArgumentException();
      LabManifest manifest=json.readValue(bytes,LabManifest.class);
      if(!manifest.approvedUntil().isAfter(Instant.now())||evidence==null||!manifest.evidenceSha256().equals(evidence.evidenceSha256()))throw new IllegalArgumentException();return manifest;
    }catch(Exception error){throw new ApiError(503,"LAB_MANIFEST_UNAVAILABLE","This immutable lab manifest has no current operator certification.");}
  }
  static boolean validOrigin(URI uri){String host=uri.getHost(),path=uri.getRawPath();return host!=null&&uri.getRawUserInfo()==null&&uri.getRawQuery()==null&&uri.getRawFragment()==null&&(path==null||path.isEmpty()||path.equals("/"))
      &&("https".equals(uri.getScheme())||"http".equals(uri.getScheme())&&(host.equals("localhost")||host.equals("127.0.0.1")||host.equals("[::1]")));}
  static boolean signature(PublicKey key,byte[] payload,String detached){try{byte[] bytes=Base64.getDecoder().decode(detached);if(bytes.length!=64)return false;Signature verifier=Signature.getInstance("Ed25519");verifier.initVerify(key);verifier.update(payload);return verifier.verify(bytes);}catch(Exception error){return false;}}
  static String hex(byte[] bytes){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception impossible){throw new IllegalStateException(impossible);}}
}
