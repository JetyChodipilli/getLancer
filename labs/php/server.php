<?php
declare(strict_types=1);

// Copyright (c) 2026 getLancer contributors. SPDX-License-Identifier: MIT
// Original disposable PHP 8.3 CLI fixture. No framework, account tokens or payment gateway.

const BODY_LIMIT = 16 * 1024;
const HEADER_LIMIT = 8192;
const RESPONSE_LIMIT = 4096;
const REDIS_BUDGET_SECONDS = 0.250;
const HTTP_BUDGET_SECONDS = 1.0;
const PAYMENT_KEY = 'getlancer-payment-fixture-only';
const IDENTITY_KEY = 'getlancer-identity-fixture-only';
const COUNTER_LIMIT = 1000000;
const OPERATIONS = [
    'cache' => ['read' => [], 'expire' => [], 'read-unavailable' => [], 'read-timeout' => [], 'reset' => []],
    'security' => ['authorize' => ['identity'], 'revoke' => [], 'reset' => []],
    'payment' => ['deliver' => ['eventBody', 'signature'], 'timeout' => [], 'reset' => []],
];

final class ProtocolError extends RuntimeException
{
    public function __construct(public readonly int $status, public readonly string $errorCode, string $message)
    {
        parent::__construct($message);
    }
}

final class RedisError extends RuntimeException {}

function monotonicSeconds(): float
{
    return hrtime(true) / 1000000000;
}

function uuid(): string
{
    $bytes = random_bytes(16);
    $bytes[6] = chr((ord($bytes[6]) & 0x0f) | 0x40);
    $bytes[8] = chr((ord($bytes[8]) & 0x3f) | 0x80);
    $hex = bin2hex($bytes);
    return substr($hex, 0, 8) . '-' . substr($hex, 8, 4) . '-' . substr($hex, 12, 4) . '-' . substr($hex, 16, 4) . '-' . substr($hex, 20);
}

function canonicalUuid(string $value): bool
{
    return preg_match('/\A[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\z/', $value) === 1;
}

function configuredInteger(string $name, int $fallback, int $maximum): int
{
    $environment = getenv($name);
    $value = $environment === false ? (string) $fallback : $environment;
    if (preg_match('/\A[0-9]{1,6}\z/', $value) !== 1 || (int) $value < 1 || (int) $value > $maximum) {
        throw new RuntimeException($name . ' is outside its startup configuration bounds.');
    }
    return (int) $value;
}

function parseForm(string $raw): array
{
    if ($raw === '' || preg_match('//u', $raw) !== 1 || preg_match('/%(?![0-9a-fA-F]{2})/', $raw) === 1) {
        throw new ProtocolError(400, 'INVALID_FORM', 'Form fields must be unique and valid UTF-8.');
    }
    $fields = [];
    foreach (explode('&', $raw) as $pair) {
        $equals = strpos($pair, '=');
        if ($equals === false || $equals === 0) {
            throw new ProtocolError(400, 'INVALID_FORM', 'Each form field needs a name and value.');
        }
        $name = rawurldecode(str_replace('+', ' ', substr($pair, 0, $equals)));
        $value = rawurldecode(str_replace('+', ' ', substr($pair, $equals + 1)));
        if ($name === '' || preg_match('//u', $name) !== 1 || preg_match('//u', $value) !== 1 || array_key_exists($name, $fields)) {
            throw new ProtocolError(400, 'INVALID_FORM', 'Form fields must be unique and valid UTF-8.');
        }
        $fields[$name] = $value;
        if (count($fields) > 5) {
            throw new ProtocolError(400, 'INVALID_FIELDS', 'Only the exact operation fields are accepted.');
        }
    }
    return $fields;
}

/** Wait against an absolute deadline, so trickled bytes cannot prolong a request. */
function socketReady($socket, float $deadline, bool $writing): void
{
    $remaining = $deadline - monotonicSeconds();
    if ($remaining <= 0) {
        throw new RuntimeException('Socket deadline expired.');
    }
    $seconds = (int) floor($remaining);
    $microseconds = min(999999, max(1, (int) (($remaining - $seconds) * 1000000)));
    $read = $writing ? [] : [$socket];
    $write = $writing ? [$socket] : [];
    $except = [];
    if (@stream_select($read, $write, $except, $seconds, $microseconds) !== 1) {
        throw new RuntimeException('Socket could not complete within its deadline.');
    }
}

function writeAll($socket, string $bytes, float $deadline): void
{
    $offset = 0;
    while ($offset < strlen($bytes)) {
        socketReady($socket, $deadline, true);
        $written = @fwrite($socket, substr($bytes, $offset));
        if ($written === false || $written === 0) {
            throw new RuntimeException('Socket write did not complete.');
        }
        $offset += $written;
    }
}

/** One loopback Redis connection and one 250 ms budget for a complete cache action. */
final class RedisConnection
{
    private $socket;
    private string $buffer = '';
    private float $deadline;

    public function __construct(int $port)
    {
        $this->deadline = monotonicSeconds() + REDIS_BUDGET_SECONDS;
        $this->socket = @stream_socket_client('tcp://127.0.0.1:' . $port, $error, $message, REDIS_BUDGET_SECONDS, STREAM_CLIENT_CONNECT);
        if ($this->socket === false) {
            throw new RedisError('Redis fixture unavailable.');
        }
        stream_set_blocking($this->socket, false);
    }

    public function close(): void
    {
        if (is_resource($this->socket)) {
            fclose($this->socket);
        }
    }

    private function receive(): void
    {
        socketReady($this->socket, $this->deadline, false);
        $bytes = @fread($this->socket, 1024);
        if ($bytes === false || $bytes === '') {
            throw new RedisError('Redis fixture response incomplete.');
        }
        $this->buffer .= $bytes;
        if (strlen($this->buffer) > 2048) {
            throw new RedisError('Redis response exceeded its bound.');
        }
    }

    public function command(string ...$parts): string|int|null
    {
        $wire = '*' . count($parts) . "\r\n";
        foreach ($parts as $part) {
            $wire .= '$' . strlen($part) . "\r\n" . $part . "\r\n";
        }
        writeAll($this->socket, $wire, $this->deadline);
        while (($end = strpos($this->buffer, "\r\n")) === false) {
            $this->receive();
        }
        $line = substr($this->buffer, 0, $end);
        $this->buffer = substr($this->buffer, $end + 2);
        $marker = $line[0] ?? '';
        $value = substr($line, 1);
        if ($marker === '+') {
            return $value;
        }
        if (($marker === ':' || $marker === '$') && preg_match('/\A-?[0-9]{1,10}\z/', $value) === 1) {
            $number = (int) $value;
            if ($marker === ':') {
                return $number;
            }
            if ($number === -1) {
                return null;
            }
            if ($number >= 0 && $number <= 1024) {
                while (strlen($this->buffer) < $number + 2) {
                    $this->receive();
                }
                if (substr($this->buffer, $number, 2) !== "\r\n") {
                    throw new RedisError('Redis bulk reply was malformed.');
                }
                $result = substr($this->buffer, 0, $number);
                $this->buffer = substr($this->buffer, $number + 2);
                return $result;
            }
        }
        throw new RedisError('Redis reply was outside the reviewed subset.');
    }
}

final class Lab
{
    public readonly string $runId;
    public readonly string $sourceHash;
    public readonly float $deadline;
    private readonly int $issuedAt;
    private readonly int $expiresAt;
    private readonly int $redisPort;
    private readonly int $failurePort;
    private readonly int $timeoutPort;
    private readonly string $cacheKey;
    private int $sequence = 0;
    private int $storeReads = 0;
    private string $currentRole = 'editor';
    private array $ledger = [];
    private bool $paid = false;
    private bool $refunded = false;

    public function __construct()
    {
        $configuredRun = getenv('LAB_RUN_ID');
        $this->runId = $configuredRun === false ? uuid() : $configuredRun;
        if (!canonicalUuid($this->runId)) {
            throw new RuntimeException('LAB_RUN_ID must be a canonical lowercase UUID.');
        }
        $hash = hash_file('sha256', __FILE__);
        if ($hash === false) {
            throw new RuntimeException('Primary source hash unavailable.');
        }
        $this->sourceHash = $hash;
        $lifetime = configuredInteger('LAB_LIFETIME_SECONDS', 3600, 86400);
        $this->deadline = monotonicSeconds() + $lifetime;
        $this->issuedAt = time();
        $this->expiresAt = $this->issuedAt + $lifetime;
        $this->redisPort = configuredInteger('LAB_REDIS_PORT', 6379, 65535);
        $this->failurePort = configuredInteger('LAB_REDIS_FAILURE_PORT', 6380, 65535);
        $this->timeoutPort = configuredInteger('LAB_REDIS_TIMEOUT_PORT', 6381, 65535);
        $this->cacheKey = 'getlancer:' . $this->runId . ':item';
    }

    public function health(): array
    {
        return ['mode' => 'Local execution', 'language' => 'php', 'runId' => $this->runId, 'sourceHash' => $this->sourceHash];
    }

    private function validate(array $fields): void
    {
        foreach (['runId', 'pattern', 'operationId'] as $name) {
            if (!array_key_exists($name, $fields)) {
                throw new ProtocolError(400, 'INVALID_FIELDS', 'Required operation fields are missing.');
            }
        }
        if (!canonicalUuid($fields['runId'])) {
            throw new ProtocolError(400, 'INVALID_RUN', 'The run ID must be a canonical lowercase UUID.');
        }
        if ($fields['runId'] !== $this->runId) {
            throw new ProtocolError(403, 'FOREIGN_RUN', 'This request belongs to a different local run.');
        }
        $pattern = $fields['pattern'];
        $operation = $fields['operationId'];
        if (!isset(OPERATIONS[$pattern][$operation])) {
            throw new ProtocolError(400, 'INVALID_OPERATION', 'Choose a supported pattern and operation.');
        }
        $allowed = array_merge(['runId', 'pattern', 'operationId'], OPERATIONS[$pattern][$operation]);
        if (count($allowed) !== count($fields) || array_diff($allowed, array_keys($fields)) !== []) {
            throw new ProtocolError(400, 'INVALID_FIELDS', 'This operation requires its exact allowed fields.');
        }
        if ($pattern === 'security' && $operation === 'authorize' && !in_array($fields['identity'], ['editor', 'viewer', 'other-tenant', 'expired', 'tampered'], true)) {
            throw new ProtocolError(400, 'INVALID_IDENTITY', 'Choose a supported synthetic identity.');
        }
        if ($pattern === 'payment' && $operation === 'deliver' && preg_match('/\A[0-9a-f]{64}\z/', $fields['signature']) !== 1) {
            throw new ProtocolError(400, 'INVALID_SIGNATURE', 'The signature must be 64 lowercase hexadecimal characters.');
        }
        if (monotonicSeconds() >= $this->deadline || $this->sequence > COUNTER_LIMIT - 4 || $this->storeReads >= COUNTER_LIMIT) {
            throw new ProtocolError(503, 'RUN_LIMIT', 'Restart this bounded disposable local run.');
        }
    }

    public function execute(array $fields): array
    {
        $this->validate($fields);
        $started = monotonicSeconds();
        $requestId = uuid();
        $events = [];
        $emit = function (string $type, string $edge, string $summary) use ($requestId, &$events): void {
            $events[] = ['runId' => $this->runId, 'sourceHash' => $this->sourceHash, 'requestId' => $requestId,
                'sequence' => ++$this->sequence, 'type' => $type, 'edge' => $edge,
                'recordedAt' => (new DateTimeImmutable('now', new DateTimeZone('UTC')))->format('Y-m-d\TH:i:s.v\Z'), 'summary' => $summary];
        };
        $pattern = $fields['pattern'];
        [$status, $state] = match ($pattern) {
            'cache' => $this->cache($fields['operationId'], $emit),
            'security' => $this->security($fields['operationId'], $fields, $emit),
            'payment' => $this->payment($fields['operationId'], $fields, $emit),
        };
        return [$status, ['runId' => $this->runId, 'sourceHash' => $this->sourceHash, 'requestId' => $requestId,
            'pattern' => $pattern, 'status' => $status, 'durationMs' => max(0, (int) ((monotonicSeconds() - $started) * 1000)),
            'state' => $state, 'events' => $events]];
    }

    private function storeRead(Closure $emit): string
    {
        ++$this->storeReads;
        $emit('STORE_READ', 'service-store', 'The synthetic store returned its fixed fixture item.');
        return 'fixture-item';
    }

    private function cache(string $operation, Closure $emit): array
    {
        $connection = null;
        if ($operation === 'reset' || $operation === 'expire') {
            try {
                $connection = new RedisConnection($this->redisPort);
                $reply = $operation === 'reset' ? $connection->command('DEL', $this->cacheKey) : $connection->command('PEXPIRE', $this->cacheKey, '0');
                if ($reply !== 0 && $reply !== 1) {
                    throw new RedisError('Unexpected Redis fixture reply.');
                }
            } catch (RuntimeException $error) {
                throw new ProtocolError(503, 'REDIS_UNAVAILABLE', 'The Redis fixture action could not complete.');
            } finally {
                $connection?->close();
            }
            if ($operation === 'reset') {
                $this->storeReads = 0;
                $emit('CACHE_RESET', 'service-redis', 'Redis cleared only the current run fixture key.');
            } else {
                $emit('CACHE_EXPIRED', 'service-redis', 'Redis completed expiry of the current run fixture key.');
            }
            return [200, ['cache' => $operation === 'reset' ? 'RESET' : 'EXPIRED', 'storeReads' => $this->storeReads, 'ttlMs' => 0]];
        }
        $port = match ($operation) { 'read-unavailable' => $this->failurePort, 'read-timeout' => $this->timeoutPort, default => $this->redisPort };
        $readStore = false;
        try {
            $connection = new RedisConnection($port);
            $value = $connection->command('GET', $this->cacheKey);
            if ($value === null) {
                $emit('CACHE_MISS', 'service-redis', 'Redis missed the current run fixture key.');
                $value = $this->storeRead($emit);
                $readStore = true;
                if ($connection->command('SET', $this->cacheKey, $value, 'PX', '5000') !== 'OK') {
                    throw new RedisError('Unexpected Redis set reply.');
                }
                $state = 'MISS';
            } elseif ($value === 'fixture-item') {
                $state = 'HIT';
            } else {
                throw new RedisError('Unexpected Redis fixture value.');
            }
            $ttl = $connection->command('PTTL', $this->cacheKey);
            if (!is_int($ttl) || $ttl < -2 || $ttl > 5000) {
                throw new RedisError('Unexpected Redis fixture TTL.');
            }
            if ($state === 'HIT') {
                $emit('CACHE_HIT', 'service-redis', 'Redis returned the current run cached fixture item.');
            }
            return [200, ['cache' => $state, 'storeReads' => $this->storeReads, 'ttlMs' => max(0, $ttl)]];
        } catch (RuntimeException $error) {
            $emit('CACHE_FALLBACK', 'service-redis', 'The bounded Redis action failed; cache fallback was selected.');
            if (!$readStore) {
                $this->storeRead($emit);
            }
            return [200, ['cache' => 'FALLBACK', 'storeReads' => $this->storeReads, 'ttlMs' => 0]];
        } finally {
            $connection?->close();
        }
    }

    private function security(string $operation, array $fields, Closure $emit): array
    {
        if ($operation === 'reset' || $operation === 'revoke') {
            $this->currentRole = $operation === 'reset' ? 'editor' : 'viewer';
            $emit($operation === 'reset' ? 'AUTH_RESET' : 'AUTH_REVOKED', 'policy-store',
                $operation === 'reset' ? 'The current run editor policy was restored.' : 'The current role policy revoked editor authority.');
            return [200, ['decision' => $operation === 'reset' ? 'RESET' : 'DENY', 'reason' => $operation === 'reset' ? 'RESET' : 'REVOKED', 'currentRole' => $this->currentRole]];
        }
        $identity = $fields['identity'];
        $claims = ['tenant' => $identity === 'other-tenant' ? 'other' : 'fixture', 'role' => $identity === 'viewer' ? 'viewer' : 'editor',
            'expires' => $identity === 'expired' ? $this->issuedAt - 60 : $this->expiresAt];
        $encoded = json_encode($claims, JSON_THROW_ON_ERROR);
        $signature = hash_hmac('sha256', $encoded, IDENTITY_KEY, true);
        if ($identity === 'tampered') {
            $signature[0] = chr(ord($signature[0]) ^ 1);
        }
        $verified = hash_equals(hash_hmac('sha256', $encoded, IDENTITY_KEY, true), $signature);
        // Signed fixture claims never replace the current run role and tenant policy.
        $role = $this->currentRole;
        $reason = !$verified ? 'SIGNATURE' : ($claims['expires'] <= time() ? 'EXPIRED' :
            ($claims['tenant'] !== 'fixture' ? 'TENANT' : ($claims['role'] !== 'editor' ? 'ROLE' : ($role !== 'editor' ? 'REVOKED' : 'ALLOW'))));
        $status = $reason === 'ALLOW' ? 200 : (in_array($reason, ['SIGNATURE', 'EXPIRED'], true) ? 401 : 403);
        $summaries = ['ALLOW' => 'The verified synthetic editor has current authority.', 'SIGNATURE' => 'The synthetic identity signature did not verify.',
            'EXPIRED' => 'The synthetic identity expired.', 'TENANT' => 'The synthetic identity belongs to another tenant.',
            'ROLE' => 'The synthetic identity lacks the editor role.', 'REVOKED' => 'The current role policy revoked editor authority.'];
        $emit($status === 200 ? 'AUTH_ALLOWED' : 'AUTH_DENIED', in_array($reason, ['ALLOW', 'ROLE', 'REVOKED'], true) ? 'policy-store' : 'service-policy', $summaries[$reason]);
        return [$status, ['decision' => $status === 200 ? 'ALLOW' : 'DENY', 'reason' => $reason, 'currentRole' => $role]];
    }

    private function paymentState(): array
    {
        return ['payment' => $this->paid ? ($this->refunded ? 'REFUNDED' : 'PAID') : 'PENDING',
            'entitlements' => (int) ($this->paid && !$this->refunded), 'processedEvents' => count($this->ledger)];
    }

    private function payment(string $operation, array $fields, Closure $emit): array
    {
        if ($operation === 'reset') {
            $this->ledger = [];
            $this->paid = $this->refunded = false;
            $emit('PAYMENT_RESET', 'emulator-ledger', 'The current run synthetic payment ledger was cleared.');
            return [200, $this->paymentState()];
        }
        if ($operation === 'timeout') {
            usleep(25000); // Synthetic emulator deadline, never a real payment request.
            $emit('PAYMENT_PENDING', 'service-emulator', 'The emulator timed out; no new ledger effect was applied.');
            return [$this->paid ? 200 : 202, $this->paymentState()];
        }
        $body = $fields['eventBody'];
        if (!hash_equals(hash_hmac('sha256', $body, PAYMENT_KEY), $fields['signature'])) {
            $emit('PAYMENT_SIGNATURE_DENIED', 'service-emulator', 'The exact event body signature did not verify.');
            return [401, $this->paymentState()];
        }
        if (preg_match('/\A(evt-[1-9][0-9]{0,3})\|order-1\|(payment|refund)\.succeeded\z/', $body, $match) !== 1) {
            throw new ProtocolError(400, 'INVALID_EVENT', 'The signed event must use the fixed fixture grammar.');
        }
        $eventId = $match[1];
        if (isset($this->ledger[$eventId])) {
            $same = $this->ledger[$eventId] === $body;
            $emit($same ? 'PAYMENT_DUPLICATE' : 'PAYMENT_CONFLICT', 'emulator-ledger', $same ?
                'The verified event was already applied without another effect.' : 'The event identifier already belongs to a different verified body.');
            return [$same ? 200 : 409, $this->paymentState()];
        }
        if (count($this->ledger) >= 100) {
            throw new ProtocolError(409, 'EVENT_LIMIT', 'The current run reached its 100 event limit.');
        }
        $this->ledger[$eventId] = $body;
        if ($match[2] === 'payment') {
            $this->paid = true;
            $emit('PAYMENT_APPLIED', 'emulator-ledger', 'The verified payment was recorded once in the synthetic ledger.');
            if ($this->refunded) {
                $emit('REFUND_APPLIED', 'emulator-ledger', 'The recorded refund reconciled without granting an entitlement.');
            }
        } else {
            $this->refunded = true;
            $emit($this->paid ? 'REFUND_APPLIED' : 'REFUND_PENDING', 'emulator-ledger', $this->paid ?
                'The verified refund was recorded; the synthetic entitlement is absent.' : 'The verified refund awaits payment without granting an entitlement.');
        }
        return [$this->paid ? 200 : 202, $this->paymentState()];
    }
}

function readHttp($socket, int $port, float $deadline): array
{
    $buffer = '';
    while (($end = strpos($buffer, "\r\n\r\n")) === false) {
        socketReady($socket, $deadline, false);
        $chunk = @fread($socket, 1024);
        if ($chunk === false || $chunk === '') {
            throw new ProtocolError(400, 'INCOMPLETE_HEADERS', 'HTTP headers did not complete.');
        }
        $buffer .= $chunk;
        if (strlen($buffer) > HEADER_LIMIT && strpos($buffer, "\r\n\r\n") === false) {
            throw new ProtocolError(431, 'HEADER_LIMIT', 'HTTP headers exceed their bound.');
        }
    }
    if ($end + 4 > HEADER_LIMIT) {
        throw new ProtocolError(431, 'HEADER_LIMIT', 'HTTP headers exceed their bound.');
    }
    $lines = explode("\r\n", substr($buffer, 0, $end));
    $requestLine = array_shift($lines);
    if (preg_match('/\A([A-Z]{1,16}) ([^\x00-\x20\x7f]{1,2048}) HTTP\/1\.[01]\z/', $requestLine, $request) !== 1 || count($lines) > 32) {
        throw new ProtocolError(400, 'INVALID_HTTP', 'The HTTP request is outside the bounded protocol.');
    }
    $method = $request[1];
    $target = $request[2];
    $headers = [];
    foreach ($lines as $line) {
        if (preg_match('/\A([A-Za-z0-9!#$%&\x27*+.^_`|~-]+):[ \t]*([^\x00-\x08\x0a-\x1f\x7f]*)\z/', $line, $header) !== 1) {
            throw new ProtocolError(400, 'INVALID_HEADERS', 'HTTP headers must use the reviewed syntax.');
        }
        $headers[strtolower($header[1])][] = rtrim($header[2], " \t");
    }
    if (isset($headers['host']) && (count($headers['host']) !== 1 || !in_array($headers['host'][0], ['127.0.0.1:' . $port, '127.0.0.1', 'localhost:' . $port, 'localhost'], true))) {
        throw new ProtocolError(400, 'INVALID_HOST', 'Only a local fixture host is accepted.');
    }
    if (str_contains($target, '?')) {
        throw new ProtocolError(400, 'QUERY_NOT_ALLOWED', 'Query strings are not accepted.');
    }
    if (!in_array($target, ['/health', '/request'], true)) {
        throw new ProtocolError(404, 'NOT_FOUND', 'Choose a supported local lab path.');
    }
    if (($target === '/health' && $method !== 'GET') || ($target === '/request' && $method !== 'POST')) {
        throw new ProtocolError(405, 'METHOD_NOT_ALLOWED', 'The method is not supported for this path.');
    }
    $body = substr($buffer, $end + 4);
    if ($target === '/health') {
        if (isset($headers['transfer-encoding']) || (isset($headers['content-length']) && $headers['content-length'] !== ['0']) || $body !== '') {
            throw new ProtocolError(400, 'INVALID_BODY', 'Health requests must not include a body.');
        }
        return [$target, []];
    }
    if (!isset($headers['content-type']) || count($headers['content-type']) !== 1 || preg_match('/\Aapplication\/x-www-form-urlencoded(?:[ \t]*;[ \t]*charset=utf-8)?\z/i', $headers['content-type'][0]) !== 1) {
        throw new ProtocolError(415, 'CONTENT_TYPE', 'Use application/x-www-form-urlencoded with UTF-8.');
    }
    if (isset($headers['transfer-encoding']) || !isset($headers['content-length']) || count($headers['content-length']) !== 1 || preg_match('/\A[0-9]{1,8}\z/', $headers['content-length'][0]) !== 1) {
        throw new ProtocolError(400, 'INVALID_LENGTH', 'One valid content length is required.');
    }
    $length = (int) $headers['content-length'][0];
    if ($length > BODY_LIMIT) {
        throw new ProtocolError(413, 'BODY_LIMIT', 'Requests are limited to 16 KiB.');
    }
    while (strlen($body) < $length) {
        socketReady($socket, $deadline, false);
        $chunk = @fread($socket, min(4096, $length - strlen($body)));
        if ($chunk === false || $chunk === '') {
            throw new ProtocolError(400, 'INCOMPLETE_BODY', 'The request body did not complete.');
        }
        $body .= $chunk;
    }
    if (strlen($body) !== $length) {
        throw new ProtocolError(400, 'INVALID_BODY', 'Pipelined or extra body bytes are not accepted.');
    }
    return [$target, parseForm($body)];
}

function sendJson($socket, int $status, array $value): void
{
    $payload = json_encode($value, JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES);
    if (strlen($payload) > RESPONSE_LIMIT) {
        $status = 500;
        $payload = '{"error":{"code":"RESPONSE_LIMIT","message":"The bounded response could not be produced."}}';
    }
    $reason = [200 => 'OK', 202 => 'Accepted', 400 => 'Bad Request', 401 => 'Unauthorized', 403 => 'Forbidden', 404 => 'Not Found',
        405 => 'Method Not Allowed', 408 => 'Request Timeout', 409 => 'Conflict', 413 => 'Content Too Large', 415 => 'Unsupported Media Type',
        431 => 'Request Header Fields Too Large', 500 => 'Internal Server Error', 503 => 'Service Unavailable'][$status] ?? 'Error';
    $wire = 'HTTP/1.1 ' . $status . ' ' . $reason . "\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: " . strlen($payload) .
        "\r\nCache-Control: no-store\r\nConnection: close\r\nX-Content-Type-Options: nosniff\r\n\r\n" . $payload;
    writeAll($socket, $wire, monotonicSeconds() + 0.250);
}

function main(): void
{
    if (PHP_SAPI !== 'cli' || PHP_VERSION_ID < 80300 || PHP_INT_SIZE < 8) {
        throw new RuntimeException('Run with 64-bit PHP CLI 8.3 or newer.');
    }
    ini_set('memory_limit', '32M');
    $lab = new Lab();
    $port = configuredInteger('LAB_PORT', 8100, 65535);
    $context = stream_context_create(['socket' => ['backlog' => 32]]);
    $listener = @stream_socket_server('tcp://127.0.0.1:' . $port, $error, $message, STREAM_SERVER_BIND | STREAM_SERVER_LISTEN, $context);
    if ($listener === false) {
        throw new RuntimeException('Local fixture listener could not start.');
    }
    stream_set_blocking($listener, false);
    fwrite(STDOUT, json_encode($lab->health(), JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES) . "\n");
    // One active connection serializes state and sequence; the operating-system backlog is capped.
    $requests = 0;
    try {
        while (monotonicSeconds() < $lab->deadline && $requests < 10000) {
            $read = [$listener]; $write = []; $except = [];
            $ready = @stream_select($read, $write, $except, 0, 200000);
            if ($ready === false) {
                break;
            }
            if ($ready === 0) {
                continue;
            }
            $socket = @stream_socket_accept($listener, 0);
            if ($socket === false) {
                continue;
            }
            ++$requests;
            stream_set_blocking($socket, false);
            try {
                try {
                    [$path, $fields] = readHttp($socket, $port, min($lab->deadline, monotonicSeconds() + HTTP_BUDGET_SECONDS));
                    [$status, $response] = $path === '/health' ? [200, $lab->health()] : $lab->execute($fields);
                } catch (ProtocolError $error) {
                    $status = $error->status;
                    $response = ['error' => ['code' => $error->errorCode, 'message' => $error->getMessage()]];
                } catch (RuntimeException $error) {
                    $status = 408;
                    $response = ['error' => ['code' => 'SOCKET_TIMEOUT', 'message' => 'The bounded local request could not complete.']];
                } catch (Throwable $error) {
                    $status = 500;
                    $response = ['error' => ['code' => 'LOCAL_EXECUTION_FAILED', 'message' => 'The local fixture action could not complete.']];
                }
                sendJson($socket, $status, $response);
            } catch (Throwable $error) {
                // A closed or stalled caller receives no response; never log untrusted bytes.
            } finally {
                fclose($socket);
            }
        }
    } finally {
        fclose($listener);
    }
}

if (isset($_SERVER['SCRIPT_FILENAME']) && realpath($_SERVER['SCRIPT_FILENAME']) === __FILE__) {
    try {
        main();
    } catch (Throwable $error) {
        fwrite(STDERR, "The bounded PHP fixture could not start; check native setup and startup configuration.\n");
        exit(1);
    }
}
