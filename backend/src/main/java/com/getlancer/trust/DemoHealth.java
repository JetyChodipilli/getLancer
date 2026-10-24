package com.getlancer.trust;

import com.getlancer.products.ProductRepository;
import com.getlancer.shared.Rules;
import com.getlancer.shared.BoundedDns;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.time.Instant;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DemoHealth {
  final JdbcTemplate db;
  final ThreadPoolExecutor dns =
      new ThreadPoolExecutor(
          0,
          1,
          30,
          TimeUnit.SECONDS,
          new SynchronousQueue<>(),
          task -> {
            Thread t = new Thread(task, "demo-health-dns");
            t.setDaemon(true);
            return t;
          },
          new ThreadPoolExecutor.AbortPolicy());

  @PreDestroy
  void close() {
    dns.shutdownNow();
  }

  static InetAddress[] resolve(
      ThreadPoolExecutor executor, Callable<InetAddress[]> lookup, long timeoutMillis)
      throws IOException {
    return BoundedDns.resolve(executor, lookup, timeoutMillis);
  }

  final boolean enabled;

  public DemoHealth(JdbcTemplate db) { this(db, false); }

  @Autowired
  public DemoHealth(JdbcTemplate db, @Value("${app.demo-health-enabled:false}") boolean enabled) {
    this.db = db; this.enabled = enabled;
  }

  public static URI target(String raw) {
    try {
      URI u = URI.create(raw);
      String h = u.getHost();
      if (h != null) h = h.toLowerCase(Locale.ROOT).replaceFirst("\\.$", "");
      if (!"https".equalsIgnoreCase(u.getScheme())
          || h == null
          || u.getRawUserInfo() != null
          || (u.getPort() != -1 && u.getPort() != 443)
          || h.contains(":")
          || h.matches("[0-9.]+")
          || !h.contains(".")
          || h.endsWith(".local")
          || h.endsWith(".internal")
          || h.endsWith(".localhost")
          || raw.length() > 2000) throw new IllegalArgumentException();
      return u;
    } catch (Exception e) {
      throw new IllegalArgumentException("UNSAFE_EXTERNAL_URL");
    }
  }

  static void validateAddresses(InetAddress[] addresses) {
    if (addresses.length == 0) throw new IllegalArgumentException("UNSAFE_EXTERNAL_URL");
    for (var ip : addresses)
      if (!Rules.publicAddress(ip)) throw new IllegalArgumentException("UNSAFE_EXTERNAL_URL");
  }

  static String classify(int status) {
    if (status >= 200 && status < 400) return "REACHABLE";
    if (Set.of(401, 403, 405, 429).contains(status)) return "BLOCKED";
    return "UNREACHABLE";
  }

  /** Transport seams keep tests on the real redirect/resolution policy without external traffic. */
  InetAddress[] lookup(String host) throws IOException {
    return resolve(dns, () -> InetAddress.getAllByName(host), 3000);
  }

  String head(URI u, InetAddress pinned) throws IOException {
    try (Socket tcp = new Socket()) {
      // Connect to the already validated IP, never resolve the host again in the transport.
      tcp.connect(new InetSocketAddress(pinned, 443), 3000);
      tcp.setSoTimeout(3000);
      try (SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
          .createSocket(tcp, u.getHost(), 443, true)) {
        ssl.setSoTimeout(3000);
        var parameters = ssl.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        parameters.setServerNames(List.of(new SNIHostName(u.getHost())));
        ssl.setSSLParameters(parameters);
        ssl.startHandshake();
        String path = u.getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        if (u.getRawQuery() != null) path += "?" + u.getRawQuery();
        ssl.getOutputStream().write(("HEAD " + path + " HTTP/1.1\r\nHost: " + u.getHost()
            + "\r\nUser-Agent: getLancer-DemoHealth/1.5\r\nConnection: close\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII));
        return readHeaders(ssl.getInputStream());
      }
    }
  }

  protected String probe(String raw) {
    try {
      URI u = target(raw);
      for (int hop = 0; hop < 3; hop++) {
        var addresses = lookup(u.getHost());
        validateAddresses(addresses);
        String[] lines = head(u, addresses[0]).split("\r\n");
        if (!lines[0].matches("HTTP/1\\.[01] [0-9]{3}.*")) return "UNKNOWN";
        int status = Integer.parseInt(lines[0].substring(9, 12));
        if (Set.of(301, 302, 303, 307, 308).contains(status)) {
          String location = null;
          for (String line : lines)
            if (line.toLowerCase(Locale.ROOT).startsWith("location:"))
              location = line.substring(9).trim();
          if (location == null || hop == 2) return "UNKNOWN";
          u = target(u.resolve(location).toString());
          continue;
        }
        return classify(status);
      }
      return "UNKNOWN";
    } catch (IllegalArgumentException e) {
      return "BLOCKED";
    } catch (IOException e) {
      return "UNREACHABLE";
    }
  }

  private static Instant instant(Object value) {
    if (value instanceof Timestamp timestamp) return timestamp.toInstant();
    if (value instanceof OffsetDateTime timestamp) return timestamp.toInstant();
    if (value instanceof Instant timestamp) return timestamp;
    return null;
  }

  /** Observations expire; they never become part of the frozen source/license agreement. */
  public static String current(Map<String,Object> row, String url, String sourceHash) {
    return current(row, url, sourceHash, Instant.now());
  }

  static String current(Map<String,Object> row, String url, String sourceHash, Instant now) {
    Instant checked = instant(row.get("demo_checked_at"));
    String status = Objects.toString(row.get("demo_health"), "UNKNOWN");
    if (url == null || url.isBlank() || !url.equals(row.get("demo_checked_url"))
        || !Objects.equals(sourceHash, row.get("demo_checked_source_hash"))
        || checked == null || checked.isAfter(now) || !checked.isAfter(now.minusSeconds(86400))
        || !Set.of("REACHABLE", "UNREACHABLE", "BLOCKED").contains(status)) return "UNKNOWN";
    return status;
  }

  static String readHeaders(InputStream in) throws IOException {
    var bytes = new ByteArrayOutputStream();
    long deadline = System.nanoTime() + 3_000_000_000L;
    int tail = 0;
    while (bytes.size() < 16384 && System.nanoTime() < deadline) {
      int b = in.read();
      if (b < 0) throw new EOFException();
      bytes.write(b);
      tail = (tail << 8) | b;
      if (tail == 0x0d0a0d0a) return bytes.toString(StandardCharsets.ISO_8859_1);
    }
    throw new IOException("Header limit exceeded");
  }

  @Scheduled(fixedDelayString = "${app.demo-health-interval-ms:3600000}")
  public void check() {
    if (!enabled) return;
    // Small bounded batches; no network request holds a database transaction open.
    var rows =
        db.queryForList(
            ProductRepository.SELECT
                + " WHERE "
                + ProductRepository.PUBLIC
                + " AND p.live_url IS NOT NULL AND p.live_url<>'' AND (p.demo_checked_at IS NULL OR"
                + " p.demo_checked_url IS DISTINCT FROM p.live_url OR p.demo_checked_at>now() OR"
                + " p.demo_checked_at<=now()-interval '24 hours') ORDER BY p.demo_checked_at NULLS"
                + " FIRST,p.id LIMIT 10");
    for (var row : rows) {
      String url = (String) row.get("live_url");
      String status = probe(url);
      db.update(
          "UPDATE products SET demo_health=?,demo_checked_at=now(),demo_checked_url=? WHERE id=?"
              + " AND live_url=? AND approval_status='APPROVED' AND lifecycle_status='ACTIVE' AND"
              + " visibility='PUBLIC'",
          status,
          url,
          row.get("id"),
          url);
    }
    checkEducation();
  }

  private static final String EDUCATION_PUBLIC = ProductRepository.PUBLIC
      + " AND u.email_verified_at IS NOT NULL"
      + " AND EXISTS(SELECT 1 FROM user_roles ur WHERE ur.user_id=u.id AND ur.role='DEVELOPER')"
      + " AND EXISTS(SELECT 1 FROM college_project_metadata cm WHERE cm.product_id=p.id AND cm.status='APPROVED')"
      + " AND e.product_id=p.id AND e.owner_id=p.owner_user_id AND e.status='APPROVED'"
      + " AND e.snapshot->>'demoMode'='EXTERNAL' AND e.source_hash IS NOT NULL"
      + " AND e.id=(SELECT latest.id FROM education_releases latest WHERE latest.product_id=p.id"
      + " AND latest.status='APPROVED' ORDER BY latest.reviewed_at DESC,latest.created_at DESC,latest.id DESC LIMIT 1)";

  void checkEducation() {
    var rows = db.queryForList("SELECT e.id,e.source_hash,e.snapshot->>'demoUrl' AS demo_url,"
        + "p.live_url,p.demo_health AS product_demo_health,p.demo_checked_at AS product_demo_checked_at,"
        + "p.demo_checked_url AS product_demo_checked_url"
        + ProductRepository.FROM + " JOIN education_releases e ON e.product_id=p.id WHERE "
        + EDUCATION_PUBLIC + " AND (e.demo_checked_at IS NULL OR e.demo_checked_at>now()"
        + " OR e.demo_checked_at<=now()-interval '24 hours'"
        + " OR e.demo_checked_url IS DISTINCT FROM e.snapshot->>'demoUrl'"
        + " OR e.demo_checked_source_hash IS DISTINCT FROM e.source_hash)"
        + " ORDER BY e.demo_checked_at NULLS FIRST,e.id LIMIT 10");
    for (var row : rows) {
      String url = (String) row.get("demo_url"), hash = (String) row.get("source_hash");
      // Exact-URL product observations may be reused, retaining their original clock.
      var productFact = new java.util.HashMap<String,Object>();
      productFact.put("demo_health", row.get("product_demo_health"));
      productFact.put("demo_checked_at", row.get("product_demo_checked_at"));
      productFact.put("demo_checked_url", row.get("product_demo_checked_url"));
      String status = current(productFact, url, null);
      Object checkedAt = row.get("product_demo_checked_at");
      if (status.equals("UNKNOWN")) {
        status = probe(url);
        checkedAt = Timestamp.from(Instant.now());
      }
      // A URL edit, new release, suspension or owner revocation during I/O invalidates this result.
      db.update("UPDATE education_releases e SET demo_health=?,demo_checked_at=?,demo_checked_url=?,"
          + "demo_checked_source_hash=? WHERE e.id=? AND e.source_hash=? AND e.snapshot->>'demoUrl'=?"
          + " AND EXISTS(SELECT 1" + ProductRepository.FROM + " WHERE " + EDUCATION_PUBLIC + ")",
          status, checkedAt, url, hash, row.get("id"), hash, url);
    }
  }
}
