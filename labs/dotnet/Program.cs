using System.Diagnostics;
using System.Globalization;
using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;
using Microsoft.AspNetCore.Http.Features;
using Microsoft.AspNetCore.Server.Kestrel.Core;
using Microsoft.AspNetCore.Server.Kestrel.Transport.Sockets;

// Original disposable .NET 8 fixtures. ASP.NET Core and BCL only; no application credentials.
try
{
    await FixtureServer.RunAsync(args);
    return 0;
}
catch (Exception)
{
    Console.Error.WriteLine("Local fixture could not start; check the documented LAB_* configuration and .NET 8 SDK.");
    return 1;
}

internal sealed class FixtureServer
{
    private const int MaxBody = 16 * 1024;
    private const int MaxResponse = 4096;
    private const int CacheTtlMs = 5000;
    private const string FixtureValue = "fixture-item";
    private static readonly UTF8Encoding Utf8 = new(false, true);
    private static readonly byte[] PaymentKey = Utf8.GetBytes("getlancer-payment-fixture-only");
    private static readonly Regex Uuid = Pattern(@"\A[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\z");
    private static readonly Regex Signature = Pattern(@"\A[0-9a-f]{64}\z");
    private static readonly Regex PaymentBody = Pattern(@"\Aevt-[1-9][0-9]{0,3}\|order-1\|(payment|refund)\.succeeded\z");
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        Converters = { new JsonStringEnumConverter<FixtureEventType>() }
    };
    private static readonly HashSet<string> Edges = new(StringComparer.Ordinal)
    {
        "client-service", "service-redis", "service-store", "service-policy", "policy-store", "service-emulator", "emulator-ledger"
    };

    private readonly string runId;
    private readonly string sourceHash;
    private readonly string cacheKey;
    private readonly int redisPort;
    private readonly int failurePort;
    private readonly int timeoutPort;
    private readonly byte[] identityKey = RandomNumberGenerator.GetBytes(32);
    private readonly SemaphoreSlim effects = new(1, 1);
    private readonly Dictionary<string, string> paymentEvents = new(StringComparer.Ordinal);
    private long eventSequence;
    private long storeReads;
    private string currentRole = "editor";
    private bool paymentSeen;
    private bool refundSeen;

    private FixtureServer(string runId, string sourceHash)
    {
        this.runId = runId;
        this.sourceHash = sourceHash;
        cacheKey = "getlancer:" + runId + ":item";
        redisPort = Port("LAB_REDIS_PORT", 6379);
        failurePort = Port("LAB_REDIS_FAILURE_PORT", 6380);
        timeoutPort = Port("LAB_REDIS_TIMEOUT_PORT", 6381);
    }

    public static async Task RunAsync(string[] arguments)
    {
        if (arguments.Length != 0) throw new InvalidOperationException("Use only LAB_* configuration.");
        string? configuredRun = Environment.GetEnvironmentVariable("LAB_RUN_ID");
        if (configuredRun is null || !Uuid.IsMatch(configuredRun)) throw new InvalidOperationException("A lowercase run UUID is required.");
        string? configuredSource = Environment.GetEnvironmentVariable("LAB_SOURCE_PATH");
        string source = string.IsNullOrWhiteSpace(configuredSource) ? "labs/dotnet/Program.cs" : configuredSource;
        if (string.IsNullOrWhiteSpace(configuredSource) && !File.Exists(source)) source = "Program.cs";
        source = Path.GetFullPath(source);
        if (Path.GetFileName(source) != "Program.cs") throw new InvalidOperationException("Use the primary Program.cs source.");
        // Read the opened file once, within a bound, rather than checking and reopening its bytes.
        await using var sourceStream = new FileStream(source, FileMode.Open, FileAccess.Read, FileShare.Read, 4096, true);
        if (sourceStream.Length > 128 * 1024) throw new InvalidOperationException("Primary source exceeds its bound.");
        byte[] sourceBytes = new byte[checked((int)sourceStream.Length)];
        await sourceStream.ReadExactlyAsync(sourceBytes);
        if (sourceStream.ReadByte() != -1) throw new InvalidOperationException("Primary source changed during startup.");
        var fixture = new FixtureServer(configuredRun, Convert.ToHexString(SHA256.HashData(sourceBytes)).ToLowerInvariant());
        int httpPort = Port("LAB_PORT", 8100);
        int lifetimeSeconds = IntegerEnvironment("LAB_LIFETIME_SECONDS", 600, 1, 600);

        var builder = WebApplication.CreateSlimBuilder(new WebApplicationOptions { Args = [] });
        builder.Configuration.Sources.Clear();
        builder.Logging.ClearProviders();
        builder.WebHost.ConfigureKestrel(options =>
        {
            options.AddServerHeader = false;
            options.Listen(IPAddress.Loopback, httpPort, listen => listen.Protocols = HttpProtocols.Http1);
            options.Limits.MaxConcurrentConnections = 16;
            options.Limits.MaxConcurrentUpgradedConnections = 0;
            options.Limits.MaxRequestBodySize = MaxBody;
            options.Limits.MaxRequestHeaderCount = 32;
            options.Limits.MaxRequestHeadersTotalSize = 8192;
            options.Limits.MaxRequestLineSize = 2048;
            options.Limits.RequestHeadersTimeout = TimeSpan.FromSeconds(2);
            options.Limits.KeepAliveTimeout = TimeSpan.FromSeconds(2);
            options.Limits.MinRequestBodyDataRate = new MinDataRate(256, TimeSpan.FromSeconds(2));
            options.Limits.MinResponseDataRate = new MinDataRate(256, TimeSpan.FromSeconds(2));
        });
        builder.Services.Configure<SocketTransportOptions>(options => options.Backlog = 16);
        builder.Services.Configure<HostOptions>(options => options.ShutdownTimeout = TimeSpan.FromSeconds(2));
        await using var app = builder.Build();
        app.Run(fixture.HandleAsync);
        using var lifetime = new CancellationTokenSource(TimeSpan.FromSeconds(lifetimeSeconds));
        Console.WriteLine("Local execution: .NET fixture listening on 127.0.0.1:" + httpPort);
        await app.RunAsync(lifetime.Token);
    }

    private static Regex Pattern(string value) => new(value, RegexOptions.CultureInvariant | RegexOptions.NonBacktracking, TimeSpan.FromMilliseconds(50));

    private static int Port(string name, int fallback) => IntegerEnvironment(name, fallback, 1, 65535);

    private static int IntegerEnvironment(string name, int fallback, int minimum, int maximum)
    {
        string? value = Environment.GetEnvironmentVariable(name);
        if (value is null) return fallback;
        if (value.Length is < 1 or > 5 || value.Any(character => character is < '0' or > '9') ||
            !int.TryParse(value, NumberStyles.None, CultureInfo.InvariantCulture, out int number) || number < minimum || number > maximum)
            throw new InvalidOperationException("Invalid bounded operator setting.");
        return number;
    }

    private async Task HandleAsync(HttpContext context)
    {
        try
        {
            if (context.Request.QueryString.HasValue) throw new Denial(400, "QUERY", "Query strings are not accepted.");
            string path = context.Features.Get<IHttpRequestFeature>()?.RawTarget ?? context.Request.Path.Value ?? "";
            if (path is not "/health" and not "/request") throw new Denial(404, "PATH", "Unknown local fixture path.");
            string expectedMethod = path == "/health" ? "GET" : "POST";
            if (context.Request.Method != expectedMethod) throw new Denial(405, "METHOD", "Unsupported request method.");
            if (path == "/health")
            {
                if ((await ReadBodyAsync(context)).Length != 0) throw new Denial(400, "INPUT", "Health requests cannot carry a body.");
                await SendAsync(context, 200, new HealthResponse("Local execution", "dotnet", runId, sourceHash));
                return;
            }
            var contentTypes = context.Request.Headers.ContentType;
            if (contentTypes.Count != 1 || contentTypes[0] is not string contentType ||
                !Regex.IsMatch(contentType, @"\Aapplication/x-www-form-urlencoded(?:\s*;\s*charset=UTF-8)?\z", RegexOptions.IgnoreCase | RegexOptions.CultureInvariant | RegexOptions.NonBacktracking))
                throw new Denial(415, "CONTENT_TYPE", "Use a UTF-8 form request.");
            byte[] bytes = await ReadBodyAsync(context);
            Dictionary<string, string> form = ParseForm(bytes);
            Validate(form);
            var elapsed = Stopwatch.StartNew();
            using var waitDeadline = CancellationTokenSource.CreateLinkedTokenSource(context.RequestAborted);
            waitDeadline.CancelAfter(TimeSpan.FromSeconds(2));
            bool locked;
            try { locked = await effects.WaitAsync(TimeSpan.FromSeconds(1), waitDeadline.Token); }
            catch (OperationCanceledException) { throw new Denial(503, "BUSY", "The bounded fixture queue is unavailable."); }
            if (!locked) throw new Denial(503, "BUSY", "The bounded fixture queue is unavailable.");
            try
            {
                string requestId = Guid.NewGuid().ToString("D");
                var events = new List<FixtureEvent>(4);
                Result result = form["pattern"] switch
                {
                    "cache" => await CacheAsync(form["operationId"], requestId, events),
                    "security" => Security(form, requestId, events),
                    "payment" => Payment(form, requestId, events),
                    _ => throw new InvalidOperationException("Validated pattern missing.")
                };
                await SendAsync(context, result.Status, new ExecutionResponse(runId, sourceHash, requestId,
                    form["pattern"], result.Status, elapsed.ElapsedMilliseconds, result.State, events));
            }
            finally { effects.Release(); }
        }
        catch (Denial denial)
        {
            await SendErrorAsync(context, denial.Status, denial.Code, denial.Message);
        }
        catch (Microsoft.AspNetCore.Http.BadHttpRequestException invalid)
        {
            await SendErrorAsync(context, invalid.StatusCode == 413 ? 413 : 400,
                invalid.StatusCode == 413 ? "TOO_LARGE" : "INPUT", "The bounded request could not be read.");
        }
        catch (OperationCanceledException)
        {
            if (!context.RequestAborted.IsCancellationRequested) await SendErrorAsync(context, 408, "TIMEOUT", "The request read deadline expired.");
        }
        catch (IOException) { context.Abort(); }
        catch (Exception)
        {
            await SendErrorAsync(context, 500, "INTERNAL", "The local fixture could not complete this request.");
        }
    }

    private static async Task<byte[]> ReadBodyAsync(HttpContext context)
    {
        if (context.Request.ContentLength > MaxBody) throw new Denial(413, "TOO_LARGE", "Request exceeds 16 KiB.");
        using var deadline = CancellationTokenSource.CreateLinkedTokenSource(context.RequestAborted);
        deadline.CancelAfter(TimeSpan.FromSeconds(2));
        byte[] buffer = new byte[MaxBody + 1];
        int offset = 0;
        while (offset < buffer.Length)
        {
            int count = await context.Request.Body.ReadAsync(buffer.AsMemory(offset), deadline.Token);
            if (count == 0) return buffer[..offset];
            offset += count;
        }
        throw new Denial(413, "TOO_LARGE", "Request exceeds 16 KiB.");
    }

    private static Dictionary<string, string> ParseForm(byte[] bytes)
    {
        DecodeUtf8(bytes); // Reject malformed literal UTF-8 before interpreting form escaping.
        var form = new Dictionary<string, string>(StringComparer.Ordinal);
        int start = 0;
        for (int end = 0; end <= bytes.Length; end++)
        {
            if (end != bytes.Length && bytes[end] != (byte)'&') continue;
            int equal = start;
            while (equal < end && bytes[equal] != (byte)'=') equal++;
            if (equal == start || equal == end || form.Count >= 5) throw new Denial(400, "INPUT", "Malformed form fields.");
            string name = DecodeForm(bytes.AsSpan(start, equal - start));
            string value = DecodeForm(bytes.AsSpan(equal + 1, end - equal - 1));
            if (!form.TryAdd(name, value)) throw new Denial(400, "INPUT", "Duplicate form fields are not accepted.");
            start = end + 1;
        }
        return form;
    }

    private static string DecodeForm(ReadOnlySpan<byte> source)
    {
        byte[] output = new byte[source.Length];
        int offset = 0;
        for (int index = 0; index < source.Length; index++)
        {
            byte value = source[index];
            if (value == (byte)'+') output[offset++] = (byte)' ';
            else if (value == (byte)'%')
            {
                if (index + 2 >= source.Length) throw new Denial(400, "INPUT", "Invalid form escaping.");
                int high = HexDigit(source[++index]);
                int low = HexDigit(source[++index]);
                if (high < 0 || low < 0) throw new Denial(400, "INPUT", "Invalid form escaping.");
                output[offset++] = (byte)((high << 4) | low);
            }
            else output[offset++] = value;
        }
        return DecodeUtf8(output.AsSpan(0, offset));
    }

    private static int HexDigit(byte value) => value switch
    {
        >= (byte)'0' and <= (byte)'9' => value - '0',
        >= (byte)'a' and <= (byte)'f' => value - 'a' + 10,
        >= (byte)'A' and <= (byte)'F' => value - 'A' + 10,
        _ => -1
    };

    private static string DecodeUtf8(ReadOnlySpan<byte> bytes)
    {
        try { return Utf8.GetString(bytes); }
        catch (DecoderFallbackException) { throw new Denial(400, "INPUT", "Request must contain valid UTF-8."); }
    }

    private void Validate(Dictionary<string, string> form)
    {
        if (!form.TryGetValue("runId", out string? suppliedRun) || !Uuid.IsMatch(suppliedRun)) throw new Denial(400, "INPUT", "A valid run UUID is required.");
        if (suppliedRun != runId) throw new Denial(403, "FOREIGN_RUN", "This process accepts only its own run.");
        if (!form.TryGetValue("pattern", out string? pattern) || !form.TryGetValue("operationId", out string? operation))
            throw new Denial(400, "INPUT", "Pattern and operation are required.");
        var allowed = new HashSet<string>(["runId", "pattern", "operationId"], StringComparer.Ordinal);
        bool valid;
        switch (pattern)
        {
            case "cache":
                valid = operation is "read" or "expire" or "read-unavailable" or "read-timeout" or "reset";
                break;
            case "security":
                valid = operation is "authorize" or "revoke" or "reset";
                if (operation == "authorize")
                {
                    allowed.Add("identity");
                    if (!form.TryGetValue("identity", out string? identity) || identity is not ("editor" or "viewer" or "other-tenant" or "expired" or "tampered"))
                        throw new Denial(400, "INPUT", "Choose an allowed synthetic identity.");
                }
                break;
            case "payment":
                valid = operation is "deliver" or "timeout" or "reset";
                if (operation == "deliver")
                {
                    allowed.UnionWith(["eventBody", "signature"]);
                    if (!form.TryGetValue("eventBody", out string? eventBody) || Utf8.GetByteCount(eventBody) > 256 ||
                        !form.TryGetValue("signature", out string? signature) || !Signature.IsMatch(signature))
                        throw new Denial(400, "INPUT", "A bounded event body and lowercase SHA-256 signature are required.");
                }
                break;
            default: valid = false; break;
        }
        if (!valid) throw new Denial(400, "INPUT", "Unknown fixture pattern or operation.");
        if (!allowed.SetEquals(form.Keys)) throw new Denial(400, "INPUT", "Only the operation's required fields are accepted.");
    }

    private async Task<Result> CacheAsync(string operation, string requestId, List<FixtureEvent> events)
    {
        int destination = operation == "read-unavailable" ? failurePort : operation == "read-timeout" ? timeoutPort : redisPort;
        bool storeRead = false;
        try
        {
            await using var redis = await RedisConnection.ConnectAsync(destination);
            if (operation == "reset")
            {
                long removed = await redis.IntegerAsync("DEL", cacheKey);
                if (removed is < 0 or > 1) throw new IOException("Unexpected Redis deletion reply.");
                storeReads = 0;
                Event(events, requestId, FixtureEventType.CACHE_RESET, "service-redis", "Redis deleted only this run's fixture key.");
                return CacheResult("RESET", 0);
            }
            if (operation == "expire")
            {
                long expired = await redis.IntegerAsync("PEXPIRE", cacheKey, "0");
                if (expired is < 0 or > 1) throw new IOException("Unexpected Redis expiry reply.");
                Event(events, requestId, FixtureEventType.CACHE_EXPIRED, "service-redis", "Redis completed immediate expiry of this run's fixture key.");
                return CacheResult("EXPIRED", 0);
            }
            string? cached = await redis.BulkAsync("GET", cacheKey);
            if (cached is null)
            {
                Event(events, requestId, FixtureEventType.CACHE_MISS, "service-redis", "Redis missed the current run's fixture key.");
                ReadStore(requestId, events);
                storeRead = true;
                await redis.OkAsync("SET", cacheKey, FixtureValue, "PX", CacheTtlMs.ToString(CultureInfo.InvariantCulture));
                long ttl = await redis.IntegerAsync("PTTL", cacheKey);
                if (ttl is < 0 or > CacheTtlMs) throw new IOException("Unexpected Redis TTL reply.");
                return CacheResult("MISS", ttl);
            }
            if (cached != FixtureValue) throw new IOException("Unexpected cache value.");
            long hitTtl = await redis.IntegerAsync("PTTL", cacheKey);
            if (hitTtl is < 0 or > CacheTtlMs) throw new IOException("Unexpected Redis TTL reply.");
            Event(events, requestId, FixtureEventType.CACHE_HIT, "service-redis", "Redis returned the current run's cached fixture value.");
            return CacheResult("HIT", hitTtl);
        }
        catch (Exception error) when (error is IOException or SocketException or OperationCanceledException)
        {
            if (operation is "reset" or "expire") throw new Denial(503, "REDIS_UNAVAILABLE", "Redis could not complete this fixture change.");
            Event(events, requestId, FixtureEventType.CACHE_FALLBACK, "service-redis", "The bounded Redis attempt failed; the fixture store supplied the result.");
            if (!storeRead) ReadStore(requestId, events);
            return CacheResult("FALLBACK", -1);
        }
    }

    private void ReadStore(string requestId, List<FixtureEvent> events)
    {
        storeReads++;
        Event(events, requestId, FixtureEventType.STORE_READ, "service-store", "The synthetic store returned its fixed fixture item.");
    }

    private Result CacheResult(string outcome, long ttl) => new(200, new CacheState(outcome, storeReads, ttl));

    private Result Security(Dictionary<string, string> form, string requestId, List<FixtureEvent> events)
    {
        string operation = form["operationId"];
        if (operation is "reset" or "revoke")
        {
            bool reset = operation == "reset";
            currentRole = reset ? "editor" : "viewer";
            Event(events, requestId, reset ? FixtureEventType.AUTH_RESET : FixtureEventType.AUTH_REVOKED, "policy-store",
                reset ? "The synthetic current-role policy was reset." : "The synthetic current-role policy now denies editor authority.");
            return new Result(200, new AuthorizationState(reset ? "RESET" : "DENY", reset ? "RESET" : "REVOKED", currentRole));
        }
        string kind = form["identity"];
        string tenant = kind == "other-tenant" ? "tenant-2" : "tenant-1";
        string role = kind == "viewer" ? "viewer" : "editor";
        long expires = DateTimeOffset.UtcNow.ToUnixTimeSeconds() + (kind == "expired" ? -60 : 60);
        byte[] claims = Utf8.GetBytes(tenant + "|" + role + "|" + expires.ToString(CultureInfo.InvariantCulture));
        byte[] issuedSignature = HMACSHA256.HashData(identityKey, claims);
        if (kind == "tampered") issuedSignature[0] ^= 1;
        string observedCurrentRole = currentRole; // Consult current authority for every request, including denied fixtures.
        string reason = !CryptographicOperations.FixedTimeEquals(HMACSHA256.HashData(identityKey, claims), issuedSignature) ? "SIGNATURE"
            : expires <= DateTimeOffset.UtcNow.ToUnixTimeSeconds() ? "EXPIRED"
            : tenant != "tenant-1" ? "TENANT" : role != "editor" ? "ROLE" : observedCurrentRole != "editor" ? "REVOKED" : "ALLOW";
        bool allowed = reason == "ALLOW";
        int status = allowed ? 200 : reason is "SIGNATURE" or "EXPIRED" ? 401 : 403;
        Event(events, requestId, allowed ? FixtureEventType.AUTH_ALLOWED : FixtureEventType.AUTH_DENIED, "service-policy",
            allowed ? "Synthetic identity and current-role policy allowed the action." : "Synthetic identity or current-role policy denied the action.");
        return new Result(status, new AuthorizationState(allowed ? "ALLOW" : "DENY", reason, observedCurrentRole));
    }

    private Result Payment(Dictionary<string, string> form, string requestId, List<FixtureEvent> events)
    {
        string operation = form["operationId"];
        if (operation == "reset")
        {
            paymentEvents.Clear();
            paymentSeen = refundSeen = false;
            Event(events, requestId, FixtureEventType.PAYMENT_RESET, "emulator-ledger", "This run's synthetic payment ledger was cleared.");
            return PaymentResult(200);
        }
        if (operation == "timeout")
        {
            Event(events, requestId, FixtureEventType.PAYMENT_PENDING, "service-emulator", "No new payment outcome was confirmed; existing ledger facts were retained.");
            return PaymentResult(paymentSeen ? 200 : 202);
        }
        string body = form["eventBody"];
        if (!CryptographicOperations.FixedTimeEquals(HMACSHA256.HashData(PaymentKey, Utf8.GetBytes(body)), Convert.FromHexString(form["signature"])))
        {
            Event(events, requestId, FixtureEventType.PAYMENT_SIGNATURE_DENIED, "service-emulator", "The exact UTF-8 event body failed synthetic signature verification.");
            return PaymentResult(401);
        }
        if (!PaymentBody.IsMatch(body)) throw new Denial(400, "INPUT", "Signed event does not match the synthetic fixture grammar.");
        string eventId = body[..body.IndexOf('|')];
        if (paymentEvents.TryGetValue(eventId, out string? prior))
        {
            bool duplicate = prior == body;
            Event(events, requestId, duplicate ? FixtureEventType.PAYMENT_DUPLICATE : FixtureEventType.PAYMENT_CONFLICT, "emulator-ledger",
                duplicate ? "The ledger already contains this identical event; no effect was added." : "The ledger already binds this event ID to a different body.");
            return PaymentResult(duplicate ? 200 : 409);
        }
        if (paymentEvents.Count >= 100)
        {
            Event(events, requestId, FixtureEventType.PAYMENT_CONFLICT, "emulator-ledger", "The bounded fixture ledger cannot accept another distinct event.");
            return PaymentResult(409);
        }
        paymentEvents.Add(eventId, body);
        if (body.EndsWith("payment.succeeded", StringComparison.Ordinal))
        {
            paymentSeen = true;
            Event(events, requestId, FixtureEventType.PAYMENT_APPLIED, "emulator-ledger", "The verified payment event was recorded once in the fixture ledger.");
            if (refundSeen) Event(events, requestId, FixtureEventType.REFUND_APPLIED, "emulator-ledger", "The recorded refund and payment converged without granting an entitlement.");
        }
        else
        {
            refundSeen = true;
            Event(events, requestId, paymentSeen ? FixtureEventType.REFUND_APPLIED : FixtureEventType.REFUND_PENDING, "emulator-ledger",
                paymentSeen ? "The ledger confirms a refund; the fixture entitlement is absent." : "The refund was recorded pending its payment; no entitlement was granted.");
        }
        return PaymentResult(refundSeen && !paymentSeen ? 202 : 200);
    }

    private Result PaymentResult(int status) => new(status,
        new PaymentState(paymentSeen ? refundSeen ? "REFUNDED" : "PAID" : "PENDING", paymentSeen && !refundSeen ? 1 : 0, paymentEvents.Count));

    private void Event(List<FixtureEvent> events, string requestId, FixtureEventType type, string edge, string summary)
    {
        if (events.Count >= 4 || !Edges.Contains(edge) || summary.Length is < 1 or > 200) throw new InvalidOperationException("Fixture event bound.");
        events.Add(new FixtureEvent(runId, sourceHash, requestId, ++eventSequence, type, edge,
            DateTime.UtcNow.ToString("O", CultureInfo.InvariantCulture), summary));
    }

    private static async Task SendErrorAsync(HttpContext context, int status, string code, string message)
    {
        if (context.Response.HasStarted || context.RequestAborted.IsCancellationRequested) { context.Abort(); return; }
        try { await SendAsync(context, status, new ErrorResponse(new ErrorDetail(code, message))); }
        catch (Exception) { context.Abort(); }
    }

    private static async Task SendAsync(HttpContext context, int status, object response)
    {
        byte[] bytes = JsonSerializer.SerializeToUtf8Bytes(response, JsonOptions);
        if (bytes.Length > MaxResponse) throw new IOException("Response bound.");
        context.Response.StatusCode = status;
        context.Response.ContentType = "application/json; charset=utf-8";
        context.Response.ContentLength = bytes.Length;
        context.Response.Headers.CacheControl = "no-store";
        context.Response.Headers.XContentTypeOptions = "nosniff";
        context.Response.Headers.Connection = "close";
        using var deadline = CancellationTokenSource.CreateLinkedTokenSource(context.RequestAborted);
        deadline.CancelAfter(TimeSpan.FromSeconds(2));
        await context.Response.Body.WriteAsync(bytes, deadline.Token);
    }

    private sealed record HealthResponse(string Mode, string Language, string RunId, string SourceHash);
    private sealed record ExecutionResponse(string RunId, string SourceHash, string RequestId, string Pattern, int Status, long DurationMs, object State, IReadOnlyList<FixtureEvent> Events);
    private sealed record CacheState(string Cache, long StoreReads, long TtlMs);
    private sealed record AuthorizationState(string Decision, string Reason, string CurrentRole);
    private sealed record PaymentState(string Payment, int Entitlements, int ProcessedEvents);
    private sealed record FixtureEvent(string RunId, string SourceHash, string RequestId, long Sequence, FixtureEventType Type, string Edge, string RecordedAt, string Summary);
    private sealed record Result(int Status, object State);
    private sealed record ErrorResponse(ErrorDetail Error);
    private sealed record ErrorDetail(string Code, string Message);
    private sealed class Denial(int status, string code, string message) : Exception(message)
    {
        public int Status { get; } = status;
        public string Code { get; } = code;
    }
    private enum FixtureEventType
    {
        CACHE_MISS, CACHE_HIT, CACHE_EXPIRED, CACHE_FALLBACK, CACHE_RESET, STORE_READ,
        AUTH_ALLOWED, AUTH_DENIED, AUTH_REVOKED, AUTH_RESET,
        PAYMENT_APPLIED, PAYMENT_DUPLICATE, PAYMENT_SIGNATURE_DENIED, PAYMENT_CONFLICT,
        PAYMENT_PENDING, PAYMENT_RESET, REFUND_PENDING, REFUND_APPLIED
    }

    // Only fixed RESP2 commands and operator-selected loopback ports. One 250 ms budget spans all I/O.
    private sealed class RedisConnection : IAsyncDisposable
    {
        private readonly TcpClient socket = new(AddressFamily.InterNetwork);
        private readonly CancellationTokenSource deadline = new(TimeSpan.FromMilliseconds(250));
        private readonly byte[] oneByte = new byte[1];
        private NetworkStream stream = null!;
        private sealed record Simple(string Value);
        private sealed record Bulk(string? Value);

        public static async Task<RedisConnection> ConnectAsync(int port)
        {
            var connection = new RedisConnection();
            try
            {
                await connection.socket.ConnectAsync(IPAddress.Loopback, port, connection.deadline.Token);
                connection.stream = connection.socket.GetStream();
                return connection;
            }
            catch { await connection.DisposeAsync(); throw; }
        }

        private async Task<byte> ReadAsync()
        {
            int count = await stream.ReadAsync(oneByte, deadline.Token);
            if (count != 1) throw new IOException("Redis closed its response.");
            return oneByte[0];
        }

        private async Task<string> LineAsync()
        {
            byte[] line = new byte[1024];
            int count = 0;
            while (true)
            {
                byte next = await ReadAsync();
                if (next == '\r')
                {
                    if (await ReadAsync() != '\n') throw new IOException("Invalid Redis framing.");
                    return Encoding.ASCII.GetString(line, 0, count);
                }
                if (count == line.Length || next == '\n' || next > 127) throw new IOException("Redis line bound.");
                line[count++] = next;
            }
        }

        private static long Number(string line)
        {
            int start = line.StartsWith('-') ? 1 : 0;
            if (line.Length - start is < 1 or > 12 || line.AsSpan(start).ContainsAnyExceptInRange('0', '9') ||
                !long.TryParse(line, NumberStyles.AllowLeadingSign, CultureInfo.InvariantCulture, out long result))
                throw new IOException("Invalid Redis number.");
            return result;
        }

        private async Task<object> CommandAsync(string[] parts)
        {
            using var command = new MemoryStream();
            void Append(string text) => command.Write(Utf8.GetBytes(text));
            Append("*" + parts.Length.ToString(CultureInfo.InvariantCulture) + "\r\n");
            foreach (string part in parts)
            {
                byte[] bytes = Utf8.GetBytes(part);
                Append("$" + bytes.Length.ToString(CultureInfo.InvariantCulture) + "\r\n");
                command.Write(bytes);
                Append("\r\n");
            }
            await stream.WriteAsync(command.ToArray(), deadline.Token);
            byte type = await ReadAsync();
            if (type == '+') return new Simple(await LineAsync());
            if (type == ':') return Number(await LineAsync());
            if (type != '$') throw new IOException("Unsupported Redis reply.");
            long size = Number(await LineAsync());
            if (size == -1) return new Bulk(null);
            if (size is < 0 or > 1024) throw new IOException("Redis bulk bound.");
            byte[] value = new byte[checked((int)size)];
            await stream.ReadExactlyAsync(value, deadline.Token);
            if (await ReadAsync() != '\r' || await ReadAsync() != '\n') throw new IOException("Invalid Redis bulk framing.");
            try { return new Bulk(Utf8.GetString(value)); }
            catch (DecoderFallbackException) { throw new IOException("Invalid Redis UTF-8."); }
        }

        public async Task<string?> BulkAsync(params string[] parts) => await CommandAsync(parts) is Bulk bulk ? bulk.Value : throw new IOException("Expected Redis bulk reply.");
        public async Task<long> IntegerAsync(params string[] parts) => await CommandAsync(parts) is long number ? number : throw new IOException("Expected Redis integer reply.");
        public async Task OkAsync(params string[] parts)
        {
            if (await CommandAsync(parts) is not Simple { Value: "OK" }) throw new IOException("Expected Redis success reply.");
        }

        public ValueTask DisposeAsync()
        {
            socket.Dispose();
            deadline.Dispose();
            return ValueTask.CompletedTask;
        }
    }
}
