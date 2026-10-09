import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Three disposable local fixtures. JDK 17 source launcher; no external libraries. */
public class LabServer {
    private static final int MAX_BODY = 16 * 1024;
    private static final int MAX_RESPONSE = 4096;
    private static final int REDIS_BUDGET_MS = 250;
    private static final int CACHE_TTL_MS = 5000;
    private static final String FIXTURE_VALUE = "fixture-item";
    private static final byte[] PAYMENT_KEY = "getlancer-payment-fixture-only".getBytes(StandardCharsets.UTF_8);
    private static final Pattern UUID_TEXT = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern PAYMENT_BODY = Pattern.compile("evt-[1-9][0-9]{0,3}\\|order-1\\|(payment|refund)\\.succeeded");
    private static final Pattern SIGNATURE = Pattern.compile("[0-9a-f]{64}");
    private static final List<String> EDGES = List.of("client-service", "service-redis", "service-store", "service-policy", "policy-store", "service-emulator", "emulator-ledger");

    private final String runId;
    private final String sourceHash;
    private final String cacheKey;
    private final int redisPort;
    private final int failurePort;
    private final int timeoutPort;
    private final byte[] identityKey = new byte[32];
    private long eventSequence;
    private long storeReads;
    private String currentRole = "editor";
    private final Map<String, String> paymentEvents = new LinkedHashMap<>();
    private boolean paymentSeen;
    private boolean refundSeen;

    private LabServer(String runId, String sourceHash, int redisPort, int failurePort, int timeoutPort) {
        this.runId = runId;
        this.sourceHash = sourceHash;
        this.cacheKey = "getlancer:" + runId + ":item";
        this.redisPort = redisPort;
        this.failurePort = failurePort;
        this.timeoutPort = timeoutPort;
        new SecureRandom().nextBytes(identityKey);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new IllegalArgumentException("Configure this local fixture through LAB_* environment variables.");
        String runId = System.getenv("LAB_RUN_ID");
        if (runId == null || !UUID_TEXT.matcher(runId).matches()) {
            throw new IllegalArgumentException("LAB_RUN_ID must be a lowercase UUID.");
        }
        String configuredSource = System.getenv("LAB_SOURCE_PATH");
        boolean explicitSource = configuredSource != null && !configuredSource.isBlank();
        Path source = !explicitSource
                ? Path.of("labs/java/LabServer.java") : Path.of(configuredSource);
        if (!explicitSource && !Files.isRegularFile(source)) source = Path.of("LabServer.java");
        source = source.toRealPath();
        if (!source.getFileName().toString().equals("LabServer.java") || !Files.isRegularFile(source)
                || Files.size(source) > 128 * 1024) {
            throw new IllegalArgumentException("LAB_SOURCE_PATH must identify the primary LabServer.java source file.");
        }
        String sourceHash = hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source)));
        LabServer fixture = new LabServer(runId, sourceHash, port("LAB_REDIS_PORT", 6379),
                port("LAB_REDIS_FAILURE_PORT", 6380), port("LAB_REDIS_TIMEOUT_PORT", 6381));
        // HTTP headers, pending connections and slow request reads have process-wide bounds.
        System.setProperty("sun.net.httpserver.maxReqHeaders", "32");
        System.setProperty("sun.net.httpserver.maxReqTime", "2");
        System.setProperty("sun.net.httpserver.maxRspTime", "2");
        System.setProperty("sun.net.httpserver.maxIdleConnections", "8");
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port("LAB_PORT", 8100)), 16);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(16), new ThreadPoolExecutor.AbortPolicy());
        server.setExecutor(executor);
        server.createContext("/", fixture::handle);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(0);
            executor.shutdownNow();
        }));
        server.start();
        System.out.println("Local execution: Java fixture listening on 127.0.0.1:" + server.getAddress().getPort());
    }

    private static int port(String name, int fallback) {
        String value = System.getenv(name);
        if (value == null) return fallback;
        if (!value.matches("[0-9]{1,5}")) throw new IllegalArgumentException(name + " must be a port number.");
        int parsed = Integer.parseInt(value);
        if (parsed < 1 || parsed > 65535) throw new IllegalArgumentException(name + " must be a port number.");
        return parsed;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (exchange.getRequestURI().getRawQuery() != null) throw new Denial(400, "QUERY", "Query strings are not accepted.");
            String path = exchange.getRequestURI().getRawPath();
            if (!path.equals("/health") && !path.equals("/request")) throw new Denial(404, "PATH", "Unknown local fixture path.");
            String expectedMethod = path.equals("/health") ? "GET" : "POST";
            if (!exchange.getRequestMethod().equals(expectedMethod)) throw new Denial(405, "METHOD", "Unsupported request method.");
            if (path.equals("/health")) {
                if (exchange.getRequestBody().read() != -1) throw new Denial(400, "INPUT", "Health requests cannot carry a body.");
                send(exchange, 200, map("mode", "Local execution", "language", "java", "runId", runId, "sourceHash", sourceHash));
                return;
            }
            List<String> contentTypes = exchange.getRequestHeaders().get("Content-Type");
            if (contentTypes == null || contentTypes.size() != 1
                    || !contentTypes.get(0).matches("(?i)application/x-www-form-urlencoded(?:\\s*;\\s*charset=UTF-8)?")) {
                throw new Denial(415, "CONTENT_TYPE", "Use a UTF-8 form request.");
            }
            List<String> lengths = exchange.getRequestHeaders().get("Content-Length");
            if (lengths != null) {
                if (lengths.size() != 1 || !lengths.get(0).matches("[0-9]{1,10}")) throw new Denial(400, "INPUT", "Invalid request length.");
                if (Long.parseLong(lengths.get(0)) > MAX_BODY) throw new Denial(413, "TOO_LARGE", "Request exceeds 16 KiB.");
            }
            byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
            if (body.length > MAX_BODY) throw new Denial(413, "TOO_LARGE", "Request exceeds 16 KiB.");
            Map<String, String> form = parseForm(body);
            validate(form);
            long started = System.nanoTime();
            synchronized (this) {
                // Serialize fixture changes and the monotonic process event sequence.
                String requestId = UUID.randomUUID().toString();
                List<Map<String, Object>> events = new ArrayList<>(4);
                Result result = switch (form.get("pattern")) {
                    case "cache" -> cache(form.get("operationId"), requestId, events);
                    case "security" -> security(form, requestId, events);
                    case "payment" -> payment(form, requestId, events);
                    default -> throw new IllegalStateException("Validated pattern missing.");
                };
                send(exchange, result.status, map("runId", runId, "sourceHash", sourceHash,
                        "requestId", requestId, "pattern", form.get("pattern"), "status", result.status,
                        "durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                        "state", result.state, "events", events));
            }
        } catch (Denial denial) {
            send(exchange, denial.status, map("error", map("code", denial.code, "message", denial.getMessage())));
        } catch (IOException failure) {
            // A disconnected HTTP peer gets no projections, and raw exceptions are never returned.
        } catch (Exception failure) {
            send(exchange, 500, map("error", map("code", "INTERNAL", "message", "The local fixture could not complete this request.")));
        } finally {
            exchange.close();
        }
    }

    private void validate(Map<String, String> form) throws Denial {
        String suppliedRun = form.get("runId");
        if (suppliedRun == null || !UUID_TEXT.matcher(suppliedRun).matches()) throw new Denial(400, "INPUT", "A valid run UUID is required.");
        if (!runId.equals(suppliedRun)) throw new Denial(403, "FOREIGN_RUN", "This process accepts only its own run.");
        String pattern = form.get("pattern");
        String operation = form.get("operationId");
        if (pattern == null || operation == null) throw new Denial(400, "INPUT", "Pattern and operation are required.");
        List<String> extra = List.of();
        boolean valid;
        switch (pattern) {
            case "cache" -> valid = List.of("read", "expire", "read-unavailable", "read-timeout", "reset").contains(operation);
            case "security" -> {
                valid = List.of("authorize", "revoke", "reset").contains(operation);
                if (operation.equals("authorize")) {
                    extra = List.of("identity");
                    String identity = form.get("identity");
                    if (identity == null || !List.of("editor", "viewer", "other-tenant", "expired", "tampered").contains(identity)) {
                        throw new Denial(400, "INPUT", "Choose an allowed synthetic identity.");
                    }
                }
            }
            case "payment" -> {
                valid = List.of("deliver", "timeout", "reset").contains(operation);
                if (operation.equals("deliver")) {
                    extra = List.of("eventBody", "signature");
                    String eventBody = form.get("eventBody");
                    String signature = form.get("signature");
                    if (eventBody == null || eventBody.getBytes(StandardCharsets.UTF_8).length > 256
                            || signature == null || !SIGNATURE.matcher(signature).matches()) {
                        throw new Denial(400, "INPUT", "A bounded event body and lowercase SHA-256 signature are required.");
                    }
                }
            }
            default -> valid = false;
        }
        if (!valid) throw new Denial(400, "INPUT", "Unknown fixture pattern or operation.");
        List<String> allowed = new ArrayList<>(List.of("runId", "pattern", "operationId"));
        allowed.addAll(extra);
        if (form.size() != allowed.size() || !allowed.containsAll(form.keySet())) throw new Denial(400, "INPUT", "Only the operation's required fields are accepted.");
    }

    private static Map<String, String> parseForm(byte[] bytes) throws Denial {
        decodeUtf8(bytes); // Also reject malformed literal UTF-8, before percent decoding.
        Map<String, String> result = new LinkedHashMap<>();
        int start = 0;
        for (int end = 0; end <= bytes.length; end++) {
            if (end != bytes.length && bytes[end] != '&') continue;
            int equals = start;
            while (equals < end && bytes[equals] != '=') equals++;
            if (equals == start || equals == end || result.size() >= 5) throw new Denial(400, "INPUT", "Malformed form fields.");
            String name = decodeForm(bytes, start, equals);
            String value = decodeForm(bytes, equals + 1, end);
            if (result.putIfAbsent(name, value) != null) throw new Denial(400, "INPUT", "Duplicate form fields are not accepted.");
            start = end + 1;
        }
        return result;
    }

    private static String decodeForm(byte[] bytes, int start, int end) throws Denial {
        ByteArrayOutputStream decoded = new ByteArrayOutputStream(end - start);
        for (int i = start; i < end; i++) {
            int next = bytes[i] & 255;
            if (next == '+') decoded.write(' ');
            else if (next == '%') {
                if (i + 2 >= end) throw new Denial(400, "INPUT", "Invalid form escaping.");
                int high = Character.digit((char) bytes[++i], 16);
                int low = Character.digit((char) bytes[++i], 16);
                if (high < 0 || low < 0) throw new Denial(400, "INPUT", "Invalid form escaping.");
                decoded.write((high << 4) | low);
            } else decoded.write(next);
        }
        return decodeUtf8(decoded.toByteArray());
    }

    private static String decodeUtf8(byte[] bytes) throws Denial {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException malformed) {
            throw new Denial(400, "INPUT", "Request must contain valid UTF-8.");
        }
    }

    private Result cache(String operation, String requestId, List<Map<String, Object>> events) throws Denial {
        int destination = operation.equals("read-unavailable") ? failurePort : operation.equals("read-timeout") ? timeoutPort : redisPort;
        boolean storeRead = false;
        try (Redis redis = new Redis(destination)) {
            if (operation.equals("reset")) {
                long removed = redis.integer("DEL", cacheKey);
                if (removed < 0 || removed > 1) throw new IOException("Unexpected DEL reply.");
                storeReads = 0;
                event(events, requestId, "CACHE_RESET", "service-redis", "Redis deleted only this run's fixture key.");
                return cacheState("RESET", 0);
            }
            if (operation.equals("expire")) {
                long expired = redis.integer("PEXPIRE", cacheKey, "0");
                if (expired < 0 || expired > 1) throw new IOException("Unexpected expiry reply.");
                event(events, requestId, "CACHE_EXPIRED", "service-redis", "Redis completed immediate expiry of this run's fixture key.");
                return cacheState("EXPIRED", 0);
            }
            String cached = redis.bulk("GET", cacheKey);
            if (cached == null) {
                event(events, requestId, "CACHE_MISS", "service-redis", "Redis missed the current run's fixture key.");
                readStore(requestId, events);
                storeRead = true;
                redis.ok("SET", cacheKey, FIXTURE_VALUE, "PX", String.valueOf(CACHE_TTL_MS));
                long ttl = redis.integer("PTTL", cacheKey);
                if (ttl < 0 || ttl > CACHE_TTL_MS) throw new IOException("Unexpected TTL reply.");
                return cacheState("MISS", ttl);
            }
            if (!cached.equals(FIXTURE_VALUE)) throw new IOException("Unexpected cache value.");
            long ttl = redis.integer("PTTL", cacheKey);
            if (ttl < 0 || ttl > CACHE_TTL_MS) throw new IOException("Unexpected TTL reply.");
            event(events, requestId, "CACHE_HIT", "service-redis", "Redis returned the current run's cached fixture value.");
            return cacheState("HIT", ttl);
        } catch (IOException unavailable) {
            if (operation.equals("reset") || operation.equals("expire")) throw new Denial(503, "REDIS_UNAVAILABLE", "Redis could not complete this fixture change.");
            event(events, requestId, "CACHE_FALLBACK", "service-redis", "The bounded Redis attempt failed; the fixture store supplied the result.");
            if (!storeRead) readStore(requestId, events);
            return cacheState("FALLBACK", -1);
        }
    }

    private void readStore(String requestId, List<Map<String, Object>> events) {
        storeReads++;
        event(events, requestId, "STORE_READ", "service-store", "The synthetic store returned its fixed fixture item.");
    }

    private Result cacheState(String outcome, long ttl) {
        return new Result(200, map("cache", outcome, "storeReads", storeReads, "ttlMs", ttl));
    }

    private Result security(Map<String, String> form, String requestId, List<Map<String, Object>> events) throws Exception {
        String operation = form.get("operationId");
        if (operation.equals("reset") || operation.equals("revoke")) {
            currentRole = operation.equals("reset") ? "editor" : "viewer";
            String reason = operation.equals("reset") ? "RESET" : "REVOKED";
            event(events, requestId, "AUTH_" + reason, "policy-store",
                    operation.equals("reset") ? "The synthetic current-role policy was reset." : "The synthetic current-role policy now denies editor authority.");
            return new Result(200, map("decision", operation.equals("reset") ? "RESET" : "DENY", "reason", reason, "currentRole", currentRole));
        }
        String kind = form.get("identity");
        String tenant = kind.equals("other-tenant") ? "tenant-2" : "tenant-1";
        String role = kind.equals("viewer") ? "viewer" : "editor";
        long expires = Instant.now().getEpochSecond() + (kind.equals("expired") ? -60 : 60);
        byte[] claims = (tenant + "|" + role + "|" + expires).getBytes(StandardCharsets.UTF_8);
        byte[] issuedSignature = hmac(identityKey, claims);
        if (kind.equals("tampered")) issuedSignature[0] ^= 1;
        String observedCurrentRole = currentRole; // Read authority for every authorization, including invalid identities.
        String reason;
        if (!MessageDigest.isEqual(hmac(identityKey, claims), issuedSignature)) reason = "SIGNATURE";
        else if (expires <= Instant.now().getEpochSecond()) reason = "EXPIRED";
        else if (!tenant.equals("tenant-1")) reason = "TENANT";
        else if (!role.equals("editor")) reason = "ROLE";
        else if (!observedCurrentRole.equals("editor")) reason = "REVOKED";
        else reason = "ALLOW";
        boolean allowed = reason.equals("ALLOW");
        int status = allowed ? 200 : List.of("SIGNATURE", "EXPIRED").contains(reason) ? 401 : 403;
        event(events, requestId, allowed ? "AUTH_ALLOWED" : "AUTH_DENIED", "service-policy",
                allowed ? "Synthetic identity and current-role policy allowed the action." : "Synthetic identity or current-role policy denied the action.");
        return new Result(status, map("decision", allowed ? "ALLOW" : "DENY", "reason", reason, "currentRole", observedCurrentRole));
    }

    private Result payment(Map<String, String> form, String requestId, List<Map<String, Object>> events) throws Exception {
        String operation = form.get("operationId");
        if (operation.equals("reset")) {
            paymentEvents.clear();
            paymentSeen = false;
            refundSeen = false;
            event(events, requestId, "PAYMENT_RESET", "emulator-ledger", "This run's synthetic payment ledger was cleared.");
            return paymentState(200);
        }
        if (operation.equals("timeout")) {
            event(events, requestId, "PAYMENT_PENDING", "service-emulator", "No new payment outcome was confirmed; existing ledger facts were retained.");
            return paymentState(paymentSeen ? 200 : 202);
        }
        String body = form.get("eventBody");
        byte[] receivedSignature = unhex(form.get("signature"));
        if (!MessageDigest.isEqual(hmac(PAYMENT_KEY, body.getBytes(StandardCharsets.UTF_8)), receivedSignature)) {
            event(events, requestId, "PAYMENT_SIGNATURE_DENIED", "service-emulator", "The exact UTF-8 event body failed synthetic signature verification.");
            return paymentState(401);
        }
        if (!PAYMENT_BODY.matcher(body).matches()) throw new Denial(400, "INPUT", "Signed event does not match the synthetic fixture grammar.");
        String eventId = body.substring(0, body.indexOf('|'));
        String prior = paymentEvents.get(eventId);
        if (prior != null) {
            boolean duplicate = prior.equals(body);
            event(events, requestId, duplicate ? "PAYMENT_DUPLICATE" : "PAYMENT_CONFLICT", "emulator-ledger",
                    duplicate ? "The ledger already contains this identical event; no effect was added." : "The ledger already binds this event ID to a different body.");
            return paymentState(duplicate ? 200 : 409);
        }
        if (paymentEvents.size() >= 100) {
            event(events, requestId, "PAYMENT_CONFLICT", "emulator-ledger", "The bounded fixture ledger cannot accept another distinct event.");
            return paymentState(409);
        }
        paymentEvents.put(eventId, body);
        if (body.endsWith("payment.succeeded")) {
            paymentSeen = true;
            event(events, requestId, "PAYMENT_APPLIED", "emulator-ledger", "The verified payment event was recorded once in the fixture ledger.");
            if (refundSeen) event(events, requestId, "REFUND_APPLIED", "emulator-ledger", "The recorded refund and payment converged without granting an entitlement.");
        } else {
            refundSeen = true;
            event(events, requestId, paymentSeen ? "REFUND_APPLIED" : "REFUND_PENDING", "emulator-ledger",
                    paymentSeen ? "The ledger confirms a refund; the fixture entitlement is absent." : "The refund was recorded pending its payment; no entitlement was granted.");
        }
        return paymentState(refundSeen && !paymentSeen ? 202 : 200);
    }

    private Result paymentState(int status) {
        return new Result(status, map("payment", paymentSeen ? refundSeen ? "REFUNDED" : "PAID" : "PENDING",
                "entitlements", paymentSeen && !refundSeen ? 1 : 0, "processedEvents", paymentEvents.size()));
    }

    private void event(List<Map<String, Object>> events, String requestId, String type, String edge, String summary) {
        if (events.size() >= 4 || !EDGES.contains(edge)) throw new IllegalStateException("Fixture event bound.");
        events.add(map("runId", runId, "sourceHash", sourceHash, "requestId", requestId,
                "sequence", ++eventSequence, "type", type, "edge", edge, "recordedAt", Instant.now().toString(), "summary", summary));
    }

    private static byte[] hmac(byte[] key, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(body);
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format("%02x", value & 255));
        return out.toString();
    }

    private static byte[] unhex(String text) {
        byte[] result = new byte[text.length() / 2];
        for (int i = 0; i < result.length; i++) result[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
        return result;
    }

    private static Map<String, Object> map(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) result.put((String) entries[i], entries[i + 1]);
        return result;
    }

    /** Output-only JSON serializer; request data never enters an execution projection. */
    private static String json(Object value) {
        if (value instanceof String text) {
            StringBuilder out = new StringBuilder("\"");
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '"' || c == '\\') out.append('\\').append(c);
                else if (c < 32) out.append(String.format("\\u%04x", (int) c));
                else out.append(c);
            }
            return out.append('"').toString();
        }
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> values) {
            List<String> fields = new ArrayList<>();
            for (Map.Entry<?, ?> entry : values.entrySet()) fields.add(json(entry.getKey()) + ":" + json(entry.getValue()));
            return "{" + String.join(",", fields) + "}";
        }
        if (value instanceof List<?> values) {
            List<String> items = new ArrayList<>();
            for (Object item : values) items.add(json(item));
            return "[" + String.join(",", items) + "]";
        }
        if (value == null) return "null";
        throw new IllegalArgumentException("Unsupported response value.");
    }

    private static void send(HttpExchange exchange, int status, Map<String, Object> response) throws IOException {
        byte[] bytes = json(response).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_RESPONSE) throw new IOException("Response bound.");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Connection", "close");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private record Result(int status, Map<String, Object> state) {}

    private static final class Denial extends Exception {
        final int status;
        final String code;
        Denial(int status, String code, String message) { super(message); this.status = status; this.code = code; }
    }

    /** Small bounded RESP2 client for fixed commands, on a fixed loopback destination. */
    private static final class Redis implements AutoCloseable {
        private record Simple(String value) {}
        private record Bulk(String value) {}
        private final Socket socket = new Socket();
        private final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(REDIS_BUDGET_MS);
        private final InputStream input;
        private final OutputStream output;

        Redis(int port) throws IOException {
            try {
                socket.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), remaining());
                input = socket.getInputStream();
                output = socket.getOutputStream();
            } catch (IOException failure) {
                socket.close();
                throw failure;
            }
        }

        private int remaining() throws IOException {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new IOException("Redis budget exhausted.");
            return (int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining));
        }

        private int read() throws IOException {
            socket.setSoTimeout(remaining());
            int next = input.read();
            if (next < 0) throw new IOException("Redis closed its response.");
            return next;
        }

        private String line() throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            while (bytes.size() <= 1024) {
                int next = read();
                if (next == '\r') {
                    if (read() != '\n') throw new IOException("Invalid RESP framing.");
                    return bytes.toString(StandardCharsets.US_ASCII);
                }
                if (next == '\n' || next > 127) throw new IOException("Invalid RESP line.");
                bytes.write(next);
            }
            throw new IOException("RESP line bound.");
        }

        private long number(String line) throws IOException {
            if (!line.matches("-?[0-9]{1,12}")) throw new IOException("Invalid RESP integer.");
            return Long.parseLong(line);
        }

        private Object command(String... parts) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bytes.write(("*" + parts.length + "\r\n").getBytes(StandardCharsets.US_ASCII));
            for (String part : parts) {
                byte[] value = part.getBytes(StandardCharsets.UTF_8);
                bytes.write(("$" + value.length + "\r\n").getBytes(StandardCharsets.US_ASCII));
                bytes.write(value);
                bytes.write("\r\n".getBytes(StandardCharsets.US_ASCII));
            }
            remaining();
            output.write(bytes.toByteArray());
            output.flush();
            int type = read();
            if (type == '+') return new Simple(line());
            if (type == ':') return number(line());
            if (type == '$') {
                long size = number(line());
                if (size == -1) return new Bulk(null);
                if (size < 0 || size > 1024) throw new IOException("RESP bulk bound.");
                int length = (int) size;
                ByteArrayOutputStream value = new ByteArrayOutputStream(length);
                for (int i = 0; i < length; i++) value.write(read());
                if (read() != '\r' || read() != '\n') throw new IOException("Invalid RESP bulk framing.");
                return new Bulk(value.toString(StandardCharsets.UTF_8));
            }
            throw new IOException("Unsupported RESP reply.");
        }

        String bulk(String... command) throws IOException {
            Object result = command(command);
            if (result instanceof Bulk bulk) return bulk.value;
            throw new IOException("Expected RESP bulk reply.");
        }

        long integer(String... command) throws IOException {
            Object result = command(command);
            if (result instanceof Long) return (Long) result;
            throw new IOException("Expected RESP integer reply.");
        }

        void ok(String... command) throws IOException {
            Object result = command(command);
            if (!(result instanceof Simple simple) || !simple.value.equals("OK")) throw new IOException("Expected RESP success.");
        }

        public void close() throws IOException { socket.close(); }
    }
}
