package com.getlancer.media;

import static com.getlancer.shared.Support.*;

import com.getlancer.products.PrivateProjectService;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import com.getlancer.shared.Rules;
import jakarta.servlet.http.HttpServletRequest;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@org.springframework.stereotype.Service
public class MediaService {
  @Value("${app.jobs-enabled:true}")
  boolean jobsEnabled;

  final JdbcTemplate db;
  final Security security;
  final S3Client s3;
  final software.amazon.awssdk.services.s3.presigner.S3Presigner presigner;
  final String bucket;

  public MediaService(
      JdbcTemplate db,
      Security security,
      @Value("${app.storage.endpoint}") String endpoint,
      @Value("${app.storage.region}") String region,
      @Value("${app.storage.access-key}") String key,
      @Value("${app.storage.secret-key}") String secret,
      @Value("${app.storage.bucket}") String bucket,
      @Value("${app.storage.upload-endpoint:${app.storage.endpoint}}") String uploadEndpoint) {
    this.db = db;
    this.security = security;
    this.bucket = bucket;
    s3 =
        key.isBlank()
            ? null
            : S3Client.builder()
                .endpointOverride(java.net.URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(key, secret)))
                .forcePathStyle(true)
                .overrideConfiguration(
                    c ->
                        c.apiCallTimeout(java.time.Duration.ofSeconds(15))
                            .apiCallAttemptTimeout(java.time.Duration.ofSeconds(10)))
                .build();
    presigner =
        key.isBlank()
            ? null
            : software.amazon.awssdk.services.s3.presigner.S3Presigner.builder()
                .endpointOverride(java.net.URI.create(uploadEndpoint))
                .region(Region.of(region))
                .credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(key, secret)))
                .serviceConfiguration(
                    software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
  }

  @Transactional
  public Map<String, Object> upload(
      UUID product, MultipartFile file, String altText, HttpServletRequest r) throws IOException {
    editable(product, r);
    capacity(product);
    storage();
    return storeVerified(product, file.getBytes(), altText);
  }

  void storage() {
    if (s3 == null)
      throw new ApiError(503, "STORAGE_UNAVAILABLE", "Media storage is not configured.");
  }

  void capacity(UUID product) {
    if (db.queryForObject(
            "SELECT count(*) FROM product_media WHERE product_id=?", Integer.class, product)
        >= 6) throw new ApiError(409, "MEDIA_LIMIT", "Use up to six proof images.");
  }

  public static byte[] sanitize(byte[] bytes) throws IOException {
    if (bytes.length == 0 || bytes.length > 5242880)
      throw new ApiError(400, "VALIDATION_ERROR", "Choose an image smaller than 5 MB.");
    boolean png =
        bytes.length > 8
            && bytes[0] == (byte) 137
            && bytes[1] == 80
            && bytes[2] == 78
            && bytes[3] == 71;
    boolean jpg =
        bytes.length > 3
            && bytes[0] == (byte) 255
            && bytes[1] == (byte) 216
            && bytes[2] == (byte) 255;
    if (!png && !jpg) throw new ApiError(400, "VALIDATION_ERROR", "Use a PNG or JPEG image.");
    BufferedImage image;
    try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(stream);
      if (!readers.hasNext()) throw new IOException();
      var reader = readers.next();
      try {
        reader.setInput(stream);
        long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
        if (pixels > 20000000)
          throw new ApiError(400, "VALIDATION_ERROR", "Image dimensions are too large.");
        image = reader.read(0);
      } finally {
        reader.dispose();
      }
    } catch (IOException e) {
      throw new ApiError(400, "INVALID_IMAGE", "This image could not be decoded.");
    }
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, "png", output);
    byte[] clean = output.toByteArray();
    if (clean.length > 5242880)
      throw new ApiError(
          400, "VALIDATION_ERROR", "The decoded image is too large. Upload a smaller image.");
    return clean;
  }

  Map<String, Object> storeVerified(UUID product, byte[] bytes, String alt) throws IOException {
    if (alt.length() > 300)
      throw new ApiError(
          400, "VALIDATION_ERROR", "Keep the image description under 300 characters.");
    byte[] clean = sanitize(bytes);
    UUID media = id();
    String object = "products/" + product + "/" + media + ".png";
    s3.putObject(
        PutObjectRequest.builder().bucket(bucket).key(object).contentType("image/png").build(),
        RequestBody.fromBytes(clean));
    db.update(
        "INSERT INTO"
            + " product_media(id,product_id,storage_key,content_type,size_bytes,verified,alt_text,sort_order)"
            + " VALUES(?,?,?,'image/png',?,true,?,(SELECT COALESCE(max(sort_order),-1)+1 FROM"
            + " product_media WHERE product_id=?))",
        media,
        product,
        object,
        clean.length,
        alt.isBlank() ? "Project screenshot" : alt,
        product);
    var decoded = ImageIO.read(new ByteArrayInputStream(clean));
    String thumbKey = object.replace(".png", "-640.png");
    byte[] thumbnail = thumbnail(decoded);
    s3.putObject(
        PutObjectRequest.builder().bucket(bucket).key(thumbKey).contentType("image/png").build(),
        RequestBody.fromBytes(thumbnail));
    db.update(
        "UPDATE product_media SET thumbnail_key=?,width=?,height=? WHERE id=?",
        thumbKey,
        decoded.getWidth(),
        decoded.getHeight(),
        media);
    return Map.of("id", media, "url", "/api/v1/media/" + media);
  }

  public static byte[] thumbnail(BufferedImage image) throws IOException {
    int width = Math.min(640, image.getWidth()),
        height =
            Math.max(1, (int) Math.round((double) image.getHeight() * width / image.getWidth()));
    var resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    var g = resized.createGraphics();
    try {
      g.setRenderingHint(
          java.awt.RenderingHints.KEY_INTERPOLATION,
          java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.drawImage(image, 0, 0, width, height, null);
    } finally {
      g.dispose();
    }
    var out = new ByteArrayOutputStream();
    ImageIO.write(resized, "png", out);
    return out.toByteArray();
  }

  @Transactional
  public Map<String, Object> requestUpload(
      UUID product, Map<String, Object> body, HttpServletRequest r) {
    editable(product, r);
    capacity(product);
    storage();
    text(body, "filename", 1, 255);
    String type = text(body, "contentType", 1, 50);
    long size;
    try {
      size = Long.parseLong(Objects.toString(body.get("sizeBytes"), ""));
    } catch (NumberFormatException e) {
      throw new ApiError(400, "VALIDATION_ERROR", "Provide the image size.");
    }
    if (!Set.of("image/png", "image/jpeg").contains(type) || size < 1 || size > 5242880)
      throw new ApiError(400, "VALIDATION_ERROR", "Use a PNG or JPEG image up to 5 MB.");
    int reserved =
        db.queryForObject(
            "SELECT count(*) FROM media_uploads WHERE product_id=? AND completed_media_id IS NULL"
                + " AND expires_at>now()",
            Integer.class,
            product);
    if (reserved
            + db.queryForObject(
                "SELECT count(*) FROM product_media WHERE product_id=?", Integer.class, product)
        >= 6)
      throw new ApiError(409, "MEDIA_LIMIT", "Complete pending uploads before adding more images.");
    UUID upload = id();
    String key = "pending/" + product + "/" + upload;
    var request =
        PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(type)
            .contentLength(size)
            .build();
    var signed =
        presigner.presignPutObject(
            software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.builder()
                .signatureDuration(java.time.Duration.ofMinutes(10))
                .putObjectRequest(request)
                .build());
    db.update(
        "INSERT INTO media_uploads(id,product_id,storage_key,content_type,size_bytes,expires_at)"
            + " VALUES(?,?,?,?,?,now()+interval '10 minutes')",
        upload,
        product,
        key,
        type,
        size);
    return Map.of(
        "uploadId",
        upload,
        "url",
        signed.url().toString(),
        "method",
        "PUT",
        "headers",
        Map.of("Content-Type", type),
        "expiresInSeconds",
        600);
  }

  @Transactional
  public Map<String, Object> complete(UUID product, Map<String, Object> body, HttpServletRequest r)
      throws IOException {
    editable(product, r);
    storage();
    UUID upload = uuid(body.get("uploadId"));
    var rows =
        db.queryForList(
            "SELECT * FROM media_uploads WHERE id=? AND product_id=? FOR UPDATE", upload, product);
    if (rows.isEmpty()) throw new ApiError(404, "NOT_FOUND", "Upload not found.");
    var pending = rows.get(0);
    if (pending.get("completed_media_id") != null)
      return Map.of("id", pending.get("completed_media_id"));
    if (((java.sql.Timestamp) pending.get("expires_at"))
        .toInstant()
        .isBefore(java.time.Instant.now()))
      throw new ApiError(410, "UPLOAD_EXPIRED", "Start a new upload.");
    capacity(product);
    String key = (String) pending.get("storage_key");
    byte[] bytes;
    try (var response = s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())) {
      if (response.response().contentLength() != ((Number) pending.get("size_bytes")).longValue()
          || !pending.get("content_type").equals(response.response().contentType()))
        throw new ApiError(400, "INVALID_IMAGE", "Uploaded metadata does not match the request.");
      bytes = response.readNBytes(5242881);
    } catch (NoSuchKeyException e) {
      throw new ApiError(409, "UPLOAD_NOT_READY", "Upload the image before completing it.");
    }
    var media = storeVerified(product, bytes, text(body, "altText", 1, 300));
    db.update("UPDATE media_uploads SET completed_media_id=? WHERE id=?", media.get("id"), upload);
    // The signed URL can be replayed until expiry. Keep staging cleanup after its expiry;
    // the verified copy has a different key and can never be overwritten by the client.
    return media;
  }

  public Map<String, Object> proof(UUID product, HttpServletRequest r) {
    UUID owner = security.developer(r, false);
    if (db.queryForObject(
            "SELECT count(*) FROM products WHERE id=? AND owner_user_id=?",
            Integer.class,
            product,
            owner)
        == 0) throw new ApiError(404, "NOT_FOUND", "Project not found.");
    return Map.of(
        "items",
        db
            .queryForList(
                "SELECT id,alt_text AS alt FROM product_media WHERE product_id=? ORDER BY"
                    + " sort_order,created_at,id",
                product)
            .stream()
            .map(
                m ->
                    Map.of(
                        "id",
                        m.get("id"),
                        "alt",
                        m.get("alt"),
                        "url",
                        "/api/v1/media/" + m.get("id")))
            .toList());
  }

  void editable(UUID product, HttpServletRequest r) {
    UUID owner = security.developer(r, true);
    if (db.queryForList(
            "SELECT id FROM products WHERE id=? AND owner_user_id=? AND approval_status IN"
                + " ('DRAFT','CHANGES_REQUESTED') FOR UPDATE",
            product,
            owner)
        .isEmpty()) throw new ApiError(404, "NOT_FOUND", "Editable project not found.");
  }

  @Transactional
  public Map<String, Object> order(UUID product, Map<String, Object> b, HttpServletRequest r) {
    editable(product, r);
    if (!(b.get("items") instanceof List<?> items) || items.size() > 6)
      throw new ApiError(400, "VALIDATION_ERROR", "Supply all proof images in order.");
    var current =
        db.queryForList("SELECT id FROM product_media WHERE product_id=?", UUID.class, product);
    Set<UUID> seen = new HashSet<>();
    int position = 0;
    for (Object item : items) {
      if (!(item instanceof Map<?, ?> raw))
        throw new ApiError(400, "VALIDATION_ERROR", "Invalid image description.");
      UUID id = uuid(raw.get("id"));
      String alt = Objects.toString(raw.get("alt"), "").trim();
      if (!current.contains(id) || !seen.add(id) || alt.isEmpty() || alt.length() > 300)
        throw new ApiError(
            400,
            "VALIDATION_ERROR",
            "Each image needs a unique ID and a description under 300 characters.");
      db.update(
          "UPDATE product_media SET sort_order=?,alt_text=? WHERE id=? AND product_id=?",
          position++,
          alt,
          id,
          product);
    }
    if (seen.size() != current.size())
      throw new ApiError(
          409, "MEDIA_CHANGED", "Reload the current proof images before reordering.");
    return Map.of("ok", true);
  }

  @Transactional
  public Map<String, Object> remove(UUID product, UUID id, HttpServletRequest r) {
    editable(product, r);
    var rows =
        db.queryForList(
            "SELECT storage_key,thumbnail_key FROM product_media WHERE id=? AND product_id=?",
            id,
            product);
    if (rows.isEmpty()) throw new ApiError(404, "NOT_FOUND", "Image not found.");
    db.update(
        "INSERT INTO storage_deletions(storage_key) VALUES(?) ON CONFLICT DO NOTHING",
        rows.get(0).get("storage_key"));
    if (rows.get(0).get("thumbnail_key") != null)
      db.update(
          "INSERT INTO storage_deletions(storage_key) VALUES(?) ON CONFLICT DO NOTHING",
          rows.get(0).get("thumbnail_key"));
    db.update("DELETE FROM product_media WHERE id=?", id);
    return Map.of("ok", true);
  }

  @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000)
  public void removeObjects() {
    if (!jobsEnabled || s3 == null) return;
    for (var row :
        db.queryForList("SELECT storage_key FROM storage_deletions ORDER BY created_at LIMIT 20")) {
      String key = (String) row.get("storage_key");
      try {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        db.update("DELETE FROM storage_deletions WHERE storage_key=?", key);
      } catch (Exception e) {
        org.slf4j.LoggerFactory.getLogger(MediaService.class).warn("storage_cleanup_failed");
      }
    }
  }

  public ResponseEntity<byte[]> get(UUID id, String variant, HttpServletRequest r) {
    var rows =
        db.queryForList(
            "SELECT m.storage_key,m.thumbnail_key,p.id AS"
                + " product_id,p.owner_user_id,p.approval_status,p.lifecycle_status,p.visibility,u.account_status,d.approval_status"
                + " AS profile_status FROM product_media m JOIN products p ON p.id=m.product_id"
                + " JOIN users u ON u.id=p.owner_user_id JOIN developer_profiles d ON"
                + " d.user_id=u.id WHERE m.id=? AND m.verified=true",
            id);
    if (rows.isEmpty() || s3 == null) throw new ApiError(404, "NOT_FOUND", "Image unavailable.");
    var p = rows.get(0);
    boolean pub =
        Rules.publicProduct(
            (String) p.get("approval_status"),
            (String) p.get("lifecycle_status"),
            (String) p.get("visibility"),
            (String) p.get("account_status"),
            (String) p.get("profile_status"));
    if (!pub) {
      var viewer = security.principal(r);
      UUID u = (UUID) viewer.get("id");
      boolean clientAccess =
          !p.get("visibility").equals("PUBLIC")
              && p.get("approval_status").equals("APPROVED")
              && p.get("lifecycle_status").equals("ACTIVE")
              && p.get("account_status").equals("ACTIVE")
              && p.get("profile_status").equals("APPROVED")
              && PrivateProjectService.granted(db, (UUID) p.get("product_id"), viewer);
      if (!u.equals(p.get("owner_user_id")) && !clientAccess) security.admin(r);
    }
    if (!Set.of("full", "thumbnail").contains(variant))
      throw new ApiError(400, "VALIDATION_ERROR", "Unknown image size.");
    String storageKey =
        variant.equals("thumbnail") && p.get("thumbnail_key") != null
            ? (String) p.get("thumbnail_key")
            : (String) p.get("storage_key");
    byte[] body =
        s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(storageKey).build())
            .asByteArray();
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .header("Cache-Control", "private, no-store")
        .body(body);
  }
}
