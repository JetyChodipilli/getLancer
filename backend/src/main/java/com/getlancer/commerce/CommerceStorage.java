package com.getlancer.commerce;

import com.getlancer.shared.ApiError;
import java.net.URI;
import java.security.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

/** Private server-only archive access. No public URL or upload capability is issued. */
@Component
public class CommerceStorage {
  private final S3Client s3; private final String bucket;
  public CommerceStorage(@Value("${app.storage.endpoint}") String endpoint,@Value("${app.storage.region}") String region,
      @Value("${app.storage.access-key}") String key,@Value("${app.storage.secret-key}") String secret,@Value("${app.storage.bucket}") String bucket) {
    this.bucket=bucket;this.s3=key.isBlank()?null:S3Client.builder().endpointOverride(URI.create(endpoint)).region(Region.of(region))
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(key,secret))).forcePathStyle(true)
      .overrideConfiguration(c->c.apiCallTimeout(java.time.Duration.ofSeconds(15)).apiCallAttemptTimeout(java.time.Duration.ofSeconds(10))).build();
  }
  public String put(UUID version,byte[] bytes,String sha) {
    ready();String key="commerce/"+version+"/"+UUID.randomUUID()+".zip";
    try {s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType("application/zip").metadata(Map.of("sha256",sha)).build(),RequestBody.fromBytes(bytes));return key;}
    catch(RuntimeException e) {discardUnlinked(key);throw new ApiError(503,"STORAGE_UNAVAILABLE","Private source storage could not confirm the upload. Try again later.");}
  }
  public byte[] read(String key,String expectedHash,int expectedSize) {
    ready();if(!key.matches("commerce/[0-9a-f-]{36}/[0-9a-f-]{36}\\.zip")) throw new ApiError(409,"ARCHIVE_INTEGRITY_ERROR","Invalid source archive location.");
    try(var stream=s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())) {
      if(stream.response().contentLength()!=expectedSize || expectedSize>SourceArchive.MAX_COMPRESSED) throw new ApiError(409,"ARCHIVE_INTEGRITY_ERROR","Source archive integrity could not be confirmed.");
      byte[] bytes=stream.readNBytes(expectedSize+1);String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
      if(bytes.length!=expectedSize || !MessageDigest.isEqual(sha.getBytes(java.nio.charset.StandardCharsets.US_ASCII),expectedHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) throw new ApiError(409,"ARCHIVE_INTEGRITY_ERROR","Source archive integrity could not be confirmed.");
      return bytes;
    } catch(ApiError e) {throw e;} catch(Exception e) {throw new ApiError(503,"STORAGE_UNAVAILABLE","Private source storage is unavailable. Try again later.");}
  }
  /** Only the upload transaction invokes this for its own uncommitted random object. */
  public void discardUnlinked(String key) {
    if(s3==null || !key.matches("commerce/[0-9a-f-]{36}/[0-9a-f-]{36}\\.zip")) return;
    try {s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());}
    catch(RuntimeException e) {org.slf4j.LoggerFactory.getLogger(CommerceStorage.class).warn("An unlinked source upload could not be removed; storage cleanup is required.");}
  }
  private void ready() {if(s3==null) throw new ApiError(503,"STORAGE_UNAVAILABLE","Private source storage is not configured.");}
}
