package com.getlancer.media;

import static com.getlancer.shared.Support.id;
import static com.getlancer.shared.Support.text;
import static com.getlancer.shared.Support.uuid;

import com.getlancer.products.PrivateProjectService;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import com.getlancer.shared.Rules;
import jakarta.servlet.http.HttpServletRequest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class MediaService {
  final boolean jobsEnabled;

  final JdbcTemplate db;
  final Security security;
  final S3Client s3;
  final software.amazon.awssdk.services.s3.presigner.S3Presigner presigner;
  final String bucket;
  final TransactionTemplate transaction;

  public MediaService(JdbcTemplate db, Security security, String endpoint, String region, String key, String secret, String bucket, String uploadEndpoint) {
    this(db, security, endpoint, region, key, secret, bucket, uploadEndpoint, false);
  }

  public MediaService(JdbcTemplate db, Security security, String endpoint, String region,
      String key, String secret, String bucket, String uploadEndpoint, boolean jobsEnabled) {
    this(db, security, endpoint, region, key, secret, bucket, uploadEndpoint, jobsEnabled,
        new org.springframework.jdbc.datasource.DataSourceTransactionManager(
            Objects.requireNonNull(db.getDataSource())));
  }

  @Autowired
  public MediaService(JdbcTemplate db, Security security, @Value("${app.storage.endpoint}") String endpoint,
      @Value("${app.storage.region}") String region, @Value("${app.storage.access-key}") String key,
      @Value("${app.storage.secret-key}") String secret, @Value("${app.storage.bucket}") String bucket,
      @Value("${app.storage.upload-endpoint:${app.storage.endpoint}}") String uploadEndpoint,
      @Value("${app.jobs-enabled:true}") boolean jobsEnabled, PlatformTransactionManager manager) {
    this.db = db;
    this.security = security;
    this.bucket = bucket;
    this.jobsEnabled = jobsEnabled;
    this.transaction = new TransactionTemplate(manager);
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

  MediaService(JdbcTemplate db, Security security, S3Client s3,
      software.amazon.awssdk.services.s3.presigner.S3Presigner presigner, String bucket,
      boolean jobsEnabled, PlatformTransactionManager manager) {
    this.db = db;
    this.security = security;
    this.s3 = s3;
    this.presigner = presigner;
    this.bucket = bucket;
    this.jobsEnabled = jobsEnabled;
    this.transaction = new TransactionTemplate(manager);
  }

  private <T> T tx(java.util.function.Supplier<T> work) {
    return transaction.execute(status -> work.get());
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public Map<String, Object> upload(
      UUID product, MultipartFile file, String altText, HttpServletRequest r) throws IOException {
    var write = tx(() -> {
      UUID owner = editable(product, r);
      capacity(product);
      storage();
      return reserveWrite(product, null, owner);
    });
    boolean writesStarted = false;
    try {
      var image = prepare(file.getBytes(), altText);
      writesStarted = true;
      writeImage(write, image);
      return finishWrite(write, image, altText, r);
    } catch (IOException | RuntimeException e) {
      abandonWrite(write, writesStarted);
      throw e;
    }
  }

  void storage() {
    if (s3 == null)
      throw new ApiError(503, "STORAGE_UNAVAILABLE", "Media storage is not configured.");
  }

  void capacity(UUID product) {
    capacity(product, null, null);
  }

  void capacity(UUID product, UUID ownWrite, UUID ownUpload) {
    if (db.queryForObject(
            "SELECT (SELECT count(*) FROM product_media WHERE product_id=?)"
                + " + (SELECT count(*) FROM media_uploads WHERE product_id=? AND completed_at IS NULL"
                + " AND expires_at>clock_timestamp() AND (?::uuid IS NULL OR id<>?))"
                + " + (SELECT count(*) FROM media_write_intents WHERE product_id=? AND upload_id IS NULL"
                + " AND expires_at>clock_timestamp() AND (?::uuid IS NULL OR id<>?))",
            Integer.class, product, product, ownUpload, ownUpload, product, ownWrite, ownWrite)
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

  record WriteIntent(UUID id, UUID product, UUID upload, UUID owner) {
    String key() { return "products/" + product + "/" + id + ".png"; }
    String thumbnailKey() { return "products/" + product + "/" + id + "-640.png"; }
  }

  record PreparedImage(byte[] full, byte[] thumbnail, int width, int height) {}

  record PendingWrite(WriteIntent intent, Map<String, Object> upload) {}

  WriteIntent reserveWrite(UUID product, UUID upload, UUID owner) {
    var write = new WriteIntent(id(), product, upload, owner);
    db.update(
        "INSERT INTO media_write_intents(id,product_id,upload_id,expires_at)"
            + " VALUES(?,?,?,clock_timestamp()+interval '2 minutes')",
        write.id(), product, upload);
    return write;
  }

  PreparedImage prepare(byte[] bytes, String alt) throws IOException {
    if (alt.length() > 300)
      throw new ApiError(
          400, "VALIDATION_ERROR", "Keep the image description under 300 characters.");
    byte[] clean = sanitize(bytes);
    var decoded = ImageIO.read(new ByteArrayInputStream(clean));
    return new PreparedImage(clean, thumbnail(decoded), decoded.getWidth(), decoded.getHeight());
  }

  void writeImage(WriteIntent write, PreparedImage image) {
    s3.putObject(
        PutObjectRequest.builder().bucket(bucket).key(write.key()).contentType("image/png").build(),
        RequestBody.fromBytes(image.full()));
    s3.putObject(
        PutObjectRequest.builder().bucket(bucket).key(write.thumbnailKey()).contentType("image/png").build(),
        RequestBody.fromBytes(image.thumbnail()));
  }

  Map<String, Object> pendingUpload(UUID product, UUID upload) {
    var rows = db.queryForList(
        "SELECT id,product_id,storage_key,content_type,size_bytes,completed_media_id,completed_at,"
            + "expires_at FROM media_uploads"
            + " WHERE id=? AND product_id=? FOR UPDATE", upload, product);
    if (rows.isEmpty()) throw new ApiError(404, "NOT_FOUND", "Upload not found.");
    var pending = rows.get(0);
    // Evaluate expiry after any row-lock wait, rather than in the locking SELECT.
    pending.put("active", db.queryForObject(
        "SELECT expires_at>clock_timestamp() FROM media_uploads WHERE id=?", Boolean.class, upload));
    return pending;
  }

  UUID completedMedia(Map<String, Object> pending) {
    if (pending.get("completed_at") == null) return null;
    if (pending.get("completed_media_id") == null)
      throw new ApiError(410, "UPLOAD_CONSUMED", "This upload can no longer be used. Start a new upload.");
    return (UUID) pending.get("completed_media_id");
  }

  void unexpired(Map<String, Object> pending) {
    if (!Boolean.TRUE.equals(pending.get("active")))
      throw new ApiError(410, "UPLOAD_EXPIRED", "Start a new upload.");
  }

  Map<String, Object> finishWrite(
      WriteIntent write, PreparedImage image, String alt, HttpServletRequest r) {
    return tx(() -> {
      UUID owner = editable(write.product(), r);
      if (!owner.equals(write.owner()))
        throw new ApiError(401, "UNAUTHENTICATED", "Please log in again.");
      if (write.upload() != null) {
        var pending = pendingUpload(write.product(), write.upload());
        UUID completed = completedMedia(pending);
        if (completed != null) {
          db.update("UPDATE media_write_intents SET expires_at=clock_timestamp() WHERE id=?", write.id());
          return Map.of("id", completed, "url", "/api/v1/media/" + completed);
        }
        unexpired(pending);
      }
      var intents = db.queryForList(
          "SELECT id FROM media_write_intents"
              + " WHERE id=? AND product_id=? FOR UPDATE", write.id(), write.product());
      if (intents.isEmpty() || !Boolean.TRUE.equals(db.queryForObject(
          "SELECT expires_at>clock_timestamp() FROM media_write_intents WHERE id=?",
          Boolean.class, write.id())))
        throw new ApiError(410, "UPLOAD_EXPIRED", "Start a new upload.");
      capacity(write.product(), write.id(), write.upload());
      db.update(
          "INSERT INTO product_media(id,product_id,storage_key,content_type,size_bytes,verified,"
              + "alt_text,sort_order,thumbnail_key,width,height)"
              + " VALUES(?,?,?,'image/png',?,true,?,(SELECT COALESCE(max(sort_order),-1)+1"
              + " FROM product_media WHERE product_id=?),?,?,?)",
          write.id(), write.product(), write.key(), image.full().length,
          alt.isBlank() ? "Project screenshot" : alt, write.product(), write.thumbnailKey(),
          image.width(), image.height());
      if (write.upload() != null)
        db.update("UPDATE media_uploads SET completed_media_id=?,completed_at=clock_timestamp() WHERE id=?",
            write.id(), write.upload());
      db.update("DELETE FROM media_write_intents WHERE id=?", write.id());
      return Map.of("id", write.id(), "url", "/api/v1/media/" + write.id());
    });
  }

  void abandonWrite(WriteIntent write, boolean writesStarted) {
    try {
      // A committed finalization has removed its journal. An uncertain commit retains bytes
      // until cleanup can check the database, rather than deleting them in this catch path.
      tx(() -> writesStarted
          ? db.update("UPDATE media_write_intents SET expires_at=LEAST(expires_at,clock_timestamp()) WHERE id=?", write.id())
          : db.update("DELETE FROM media_write_intents WHERE id=?", write.id()));
    } catch (RuntimeException e) {
      org.slf4j.LoggerFactory.getLogger(MediaService.class).warn("media_write_reconciliation_pending");
    }
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
            + " VALUES(?,?,?,?,?,clock_timestamp()+interval '10 minutes')",
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

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public Map<String, Object> complete(UUID product, Map<String, Object> body, HttpServletRequest r)
      throws IOException {
    UUID upload = uuid(body.get("uploadId"));
    var start = tx(() -> {
      UUID owner = editable(product, r);
      storage();
      var pending = pendingUpload(product, upload);
      if (completedMedia(pending) != null) return new PendingWrite(null, pending);
      unexpired(pending);
      if (!db.queryForList("SELECT id FROM media_write_intents WHERE upload_id=?", upload).isEmpty())
        throw new ApiError(409, "UPLOAD_BUSY", "This upload is processing. Retry after cleanup.");
      capacity(product, null, upload);
      return new PendingWrite(reserveWrite(product, upload, owner), pending);
    });
    if (start.intent() == null) return Map.of("id", completedMedia(start.upload()));
    var write = start.intent();
    boolean writesStarted = false;
    try {
      String alt = text(body, "altText", 1, 300);
      byte[] bytes;
      var pending = start.upload();
      try (var response = s3.getObject(GetObjectRequest.builder().bucket(bucket)
          .key((String) pending.get("storage_key")).build())) {
        if (response.response().contentLength() != ((Number) pending.get("size_bytes")).longValue()
            || !pending.get("content_type").equals(response.response().contentType()))
          throw new ApiError(400, "INVALID_IMAGE", "Uploaded metadata does not match the request.");
        bytes = response.readNBytes(5242881);
      } catch (NoSuchKeyException e) {
        throw new ApiError(409, "UPLOAD_NOT_READY", "Upload the image before completing it.");
      }
      var image = prepare(bytes, alt);
      writesStarted = true;
      writeImage(write, image);
      // Retain the pending object until its original signature expires; replay can only
      // replace staging bytes, never the independently verified immutable media keys.
      return finishWrite(write, image, alt, r);
    } catch (IOException | RuntimeException e) {
      abandonWrite(write, writesStarted);
      throw e;
    }
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

  UUID editable(UUID product, HttpServletRequest r) {
    UUID owner = security.developer(r, true);
    if (db.queryForList(
            "SELECT id FROM products WHERE id=? AND owner_user_id=? AND approval_status IN"
                + " ('DRAFT','CHANGES_REQUESTED') FOR UPDATE",
            product,
            owner)
        .isEmpty()) throw new ApiError(404, "NOT_FOUND", "Editable project not found.");
    if (!owner.equals(security.developer(r, true)))
      throw new ApiError(401, "UNAUTHENTICATED", "Please log in again.");
    return owner;
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

  @Scheduled(fixedDelay = 60000)
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void removeObjects() {
    if (!jobsEnabled) return;
    expireWrites();
    if (s3 == null) return;
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

  void expireWrites() {
    tx(() -> {
      // The two-minute write deadline bounds finalization. Wait another five minutes
      // before deleting to accommodate bounded SDK calls already in flight. Provider
      // behavior beyond that margin still needs the operator's object lifecycle/sweep.
      for (var row : db.queryForList(
          "SELECT id,product_id FROM media_write_intents WHERE expires_at<clock_timestamp()"
              + "-interval '5 minutes' ORDER BY expires_at,id LIMIT 20 FOR UPDATE SKIP LOCKED")) {
        var write = new WriteIntent((UUID) row.get("id"), (UUID) row.get("product_id"), null, null);
        int linked = db.queryForObject(
            "SELECT count(*) FROM product_media WHERE id=? OR storage_key IN (?,?)"
                + " OR thumbnail_key IN (?,?)", Integer.class, write.id(), write.key(),
            write.thumbnailKey(), write.key(), write.thumbnailKey());
        if (linked == 0) {
          db.update("INSERT INTO storage_deletions(storage_key) VALUES(?) ON CONFLICT DO NOTHING", write.key());
          db.update("INSERT INTO storage_deletions(storage_key) VALUES(?) ON CONFLICT DO NOTHING", write.thumbnailKey());
        }
        db.update("DELETE FROM media_write_intents WHERE id=?", write.id());
      }
      return true;
    });
  }

  public ResponseEntity<byte[]> get(UUID id, String variant, HttpServletRequest r) {
    var before = readable(id, r);
    if (!Set.of("full", "thumbnail").contains(variant))
      throw new ApiError(400, "VALIDATION_ERROR", "Unknown image size.");
    String storageKey = storageKey(before, variant);
    byte[] body =
        s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(storageKey).build())
            .asByteArray();
    // Storage I/O must not keep a database transaction open. Recheck deletion,
    // publication and viewer authority before releasing the fetched bytes.
    if (!storageKey.equals(storageKey(readable(id, r), variant)))
      throw new ApiError(404, "NOT_FOUND", "Image unavailable.");
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .header("Cache-Control", "private, no-store")
        .body(body);
  }

  private String storageKey(Map<String, Object> media, String variant) {
    return variant.equals("thumbnail") && media.get("thumbnail_key") != null
        ? (String) media.get("thumbnail_key")
        : (String) media.get("storage_key");
  }

  private Map<String, Object> readable(UUID id, HttpServletRequest r) {
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
    return p;
  }
}
