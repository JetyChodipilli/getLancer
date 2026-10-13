<?php
declare(strict_types=1);

// Copyright (c) 2026 getLancer contributors. SPDX-License-Identifier: MIT
// Native regression tests: real CLI HTTP process, not fabricated execution evidence.
require __DIR__ . '/server.php';

$checks = 0;
function check(bool $condition, string $message): void
{
    global $checks;
    ++$checks;
    if (!$condition) {
        throw new RuntimeException('Regression failed: ' . $message);
    }
}

function expectProtocol(Closure $operation, int $status): void
{
    try {
        $operation();
    } catch (ProtocolError $error) {
        check($error->status === $status, 'exact protocol status');
        return;
    }
    throw new RuntimeException('Expected a protocol denial.');
}

function operation(Lab $lab, string $pattern, string $operation, array $extra = [], int $expected = 200): array
{
    static $sequences = [];
    [$status, $response] = $lab->execute(array_merge(['runId' => $lab->runId, 'pattern' => $pattern, 'operationId' => $operation], $extra));
    check($status === $expected && $response['status'] === $expected, 'execution status');
    check($response['sourceHash'] === hash_file('sha256', __DIR__ . '/server.php'), 'actual primary source hash');
    check(count($response['events']) >= 1 && count($response['events']) <= 4, 'bounded observed events');
    check(strlen(json_encode($response, JSON_THROW_ON_ERROR)) <= RESPONSE_LIMIT, 'bounded serialized response');
    foreach ($response['events'] as $event) {
        $previous = $sequences[$lab->runId] ?? 0;
        check($event['sequence'] > $previous, 'monotonic events through resets');
        $sequences[$lab->runId] = $event['sequence'];
        check($event['requestId'] === $response['requestId'] && $event['runId'] === $lab->runId, 'event identity binding');
    }
    return $response;
}

function signed(string $body): array
{
    return ['eventBody' => $body, 'signature' => hash_hmac('sha256', $body, PAYMENT_KEY)];
}

function fixturePort(): int
{
    $socket = stream_socket_server('tcp://127.0.0.1:0', $error, $message);
    if ($socket === false) {
        throw new RuntimeException('Test port could not be reserved.');
    }
    $address = stream_socket_get_name($socket, false);
    fclose($socket);
    return (int) substr($address, strrpos($address, ':') + 1);
}

function rawRequest(int $port, string $wire, bool $halfClose = false): array
{
    $socket = stream_socket_client('tcp://127.0.0.1:' . $port, $error, $message, 1.0);
    if ($socket === false) {
        throw new RuntimeException('Native HTTP fixture unavailable.');
    }
    stream_set_blocking($socket, false);
    $started = monotonicSeconds();
    try {
        writeAll($socket, $wire, $started + 1.0);
        if ($halfClose) {
            stream_socket_shutdown($socket, STREAM_SHUT_WR);
        }
        $reply = '';
        while (!feof($socket)) {
            socketReady($socket, $started + 3.0, false);
            $chunk = fread($socket, 4096);
            if ($chunk === false) {
                throw new RuntimeException('Native HTTP response incomplete.');
            }
            $reply .= $chunk;
            if (strlen($reply) > 8192) {
                throw new RuntimeException('Native HTTP response exceeded its test bound.');
            }
        }
    } finally {
        fclose($socket);
    }
    check(preg_match('/\AHTTP\/1\.1 ([0-9]{3}) /', $reply, $match) === 1, 'real HTTP response');
    $split = strpos($reply, "\r\n\r\n");
    check($split !== false, 'real HTTP header completion');
    $json = substr($reply, $split + 4);
    check(strlen($json) <= RESPONSE_LIMIT, 'HTTP projection byte limit');
    check(str_contains(substr($reply, 0, $split), 'Connection: close'), 'no retained HTTP connection');
    return [(int) $match[1], json_decode($json, true, 32, JSON_THROW_ON_ERROR), monotonicSeconds() - $started];
}

function httpWire(int $port, string $body, string $path = '/request', string $method = 'POST', string $additional = ''): string
{
    return $method . ' ' . $path . " HTTP/1.1\r\nHost: 127.0.0.1:" . $port . "\r\nContent-Type: application/x-www-form-urlencoded\r\nContent-Length: " . strlen($body) .
        "\r\n" . $additional . "Connection: close\r\n\r\n" . $body;
}

function launchFixture(int $port, string $runId, int $lifetime = 60): array
{
    $process = proc_open([PHP_BINARY, __DIR__ . '/server.php'], [['pipe', 'r'], ['pipe', 'w'], ['pipe', 'w']], $pipes, null,
        ['PATH' => (string) getenv('PATH'), 'LAB_RUN_ID' => $runId, 'LAB_PORT' => (string) $port,
            'LAB_REDIS_FAILURE_PORT' => (string) fixturePort(), 'LAB_LIFETIME_SECONDS' => (string) $lifetime]);
    if (!is_resource($process)) {
        throw new RuntimeException('Native fixture process could not start.');
    }
    fclose($pipes[0]);
    stream_set_blocking($pipes[1], false);
    stream_set_blocking($pipes[2], false);
    $ready = false;
    for ($attempt = 0; $attempt < 100; ++$attempt) {
        if (!proc_get_status($process)['running']) {
            break;
        }
        $socket = @stream_socket_client('tcp://127.0.0.1:' . $port, $error, $message, 0.05);
        if ($socket !== false) {
            fclose($socket);
            $ready = true;
            break;
        }
        usleep(20000);
    }
    if (!$ready) {
        $diagnostic = stream_get_contents($pipes[2]);
        stopFixture([$process, $pipes]);
        throw new RuntimeException('Native fixture startup failed: ' . $diagnostic);
    }
    return [$process, $pipes];
}

function stopFixture(array $handle): void
{
    [$process, $pipes] = $handle;
    if (proc_get_status($process)['running']) {
        proc_terminate($process);
        $deadline = monotonicSeconds() + 1.0;
        while (proc_get_status($process)['running'] && monotonicSeconds() < $deadline) {
            usleep(10000);
        }
        if (proc_get_status($process)['running']) {
            proc_terminate($process, 9);
        }
    }
    fclose($pipes[1]);
    fclose($pipes[2]);
    proc_close($process);
}

try {
    check(parseForm('identity=other-tenant&empty=&text=hello+world')['text'] === 'hello world', 'form decoding');
    foreach (['', 'x', '=x', 'x=1&x=2', 'x=1&%78=2', 'x=%FF', 'x=%Q0', 'x=%', 'x=%C0%AF', "x=\xff", 'x=1&'] as $raw) {
        expectProtocol(fn() => parseForm($raw), 400);
    }
    $runId = uuid();
    putenv('LAB_RUN_ID=' . $runId);
    $lab = new Lab();
    $base = ['runId' => $runId, 'pattern' => 'security', 'operationId' => 'authorize', 'identity' => 'editor'];
    expectProtocol(fn() => $lab->execute(array_merge($base, ['runId' => uuid()])), 403);
    expectProtocol(fn() => $lab->execute(array_merge($base, ['token' => 'platform-positive-control'])), 400);
    check(operation($lab, 'security', 'authorize', ['identity' => 'editor'])['state']['decision'] === 'ALLOW', 'editor current authority');
    foreach ([['viewer', 'ROLE', 403], ['other-tenant', 'TENANT', 403], ['expired', 'EXPIRED', 401], ['tampered', 'SIGNATURE', 401]] as [$identity, $reason, $status]) {
        check(operation($lab, 'security', 'authorize', ['identity' => $identity], $status)['state']['reason'] === $reason, 'identity failure reason');
    }
    operation($lab, 'security', 'revoke');
    check(operation($lab, 'security', 'authorize', ['identity' => 'editor'], 403)['state']['reason'] === 'REVOKED', 'signed editor reads current revoked policy');
    operation($lab, 'security', 'reset');
    check(operation($lab, 'security', 'authorize', ['identity' => 'editor'])['state']['decision'] === 'ALLOW', 'reset restores only current role');

    $payment = 'evt-1|order-1|payment.succeeded';
    $refund = 'evt-2|order-1|refund.succeeded';
    $pending = operation($lab, 'payment', 'timeout', [], 202)['state'];
    check($pending === ['payment' => 'PENDING', 'entitlements' => 0, 'processedEvents' => 0], 'timeout does not invent payment');
    check(operation($lab, 'payment', 'deliver', signed($refund), 202)['state']['entitlements'] === 0, 'early refund does not grant access');
    $refunded = operation($lab, 'payment', 'deliver', signed($payment))['state'];
    check($refunded === ['payment' => 'REFUNDED', 'entitlements' => 0, 'processedEvents' => 2], 'late payment reconciles refund');
    check(operation($lab, 'payment', 'timeout')['state'] === $refunded, 'timeout cannot resurrect refunded access');
    check(operation($lab, 'payment', 'deliver', signed($payment))['state'] === $refunded, 'retry does not reapply entitlement');
    $wrongSignature = ['eventBody' => 'evt-3|order-1|payment.succeeded', 'signature' => signed($payment)['signature']];
    check(operation($lab, 'payment', 'deliver', $wrongSignature, 401)['state'] === $refunded, 'exact raw bytes govern signature');
    check(operation($lab, 'payment', 'deliver', signed('evt-1|order-1|refund.succeeded'), 409)['state'] === $refunded, 'conflicting event leaves facts intact');
    foreach (['malformed', $payment . "\n", $payment . "\r\n", 'evt-0001|order-1|payment.succeeded'] as $body) {
        expectProtocol(fn() => $lab->execute(array_merge(['runId' => $runId, 'pattern' => 'payment', 'operationId' => 'deliver'], signed($body))), 400);
    }
    expectProtocol(fn() => $lab->execute(['runId' => $runId, 'pattern' => 'payment', 'operationId' => 'deliver', 'eventBody' => $payment, 'signature' => signed($payment)['signature'] . "\n"]), 400);
    operation($lab, 'payment', 'reset');
    for ($index = 1; $index <= 100; ++$index) {
        check(operation($lab, 'payment', 'deliver', signed('evt-' . $index . '|order-1|payment.succeeded'))['state']['processedEvents'] === $index, 'ledger distinct-event count');
    }
    $full = operation($lab, 'payment', 'deliver', signed($payment))['state'];
    expectProtocol(fn() => $lab->execute(array_merge(['runId' => $runId, 'pattern' => 'payment', 'operationId' => 'deliver'], signed('evt-101|order-1|payment.succeeded'))), 409);
    check(operation($lab, 'payment', 'deliver', signed($payment))['state'] === $full, 'full-ledger retry retains exactly one entitlement');

    $port = fixturePort();
    $handle = launchFixture($port, $runId);
    try {
        [$status, $health] = rawRequest($port, "GET /health HTTP/1.1\r\nHost: 127.0.0.1:" . $port . "\r\n\r\n");
        check($status === 200 && $health === ['mode' => 'Local execution', 'language' => 'php', 'runId' => $runId, 'sourceHash' => hash_file('sha256', __DIR__ . '/server.php')], 'actual native health identity');
        $encoded = http_build_query($base, '', '&', PHP_QUERY_RFC3986);
        foreach ([[$encoded . '&identity=viewer', 400, '/request', 'POST', ''], [$encoded . '&x=%FF', 400, '/request', 'POST', ''],
            [$encoded, 400, '/request?target=other', 'POST', ''], [$encoded, 404, '/elsewhere', 'POST', ''],
            [$encoded, 405, '/request', 'PUT', ''], [$encoded, 400, '/request', 'POST', "Content-Length: 1\r\n"],
            [$encoded, 400, '/request', 'POST', "Transfer-Encoding: chunked\r\n"], [$encoded, 415, '/request', 'POST', "Content-Type: application/json\r\n"],
            ['x=' . str_repeat('x', BODY_LIMIT + 1), 413, '/request', 'POST', '']] as [$body, $expected, $path, $method, $additional]) {
            [$status, $denied] = rawRequest($port, httpWire($port, $body, $path, $method, $additional));
            check($status === $expected && isset($denied['error']) && !isset($denied['events']), 'HTTP denial has no fabricated events');
        }
        [$status] = rawRequest($port, "POST /request HTTP/1.1\r\nHost: 127.0.0.1:" . $port . "\r\nContent-Type: application/x-www-form-urlencoded\r\nContent-Length: 100\r\n\r\n", true);
        check($status === 400, 'incomplete body rejected before effects');
        [$status, , $elapsed] = rawRequest($port, "POST /request HTTP/1.1\r\nHost: 127.0.0.1:" . $port . "\r\nContent-Type: application/x-www-form-urlencoded\r\nContent-Length: 100\r\n\r\n");
        check($status === 408 && $elapsed >= 0.8 && $elapsed < 2.0, 'stalled actual HTTP socket expires');
        [$status] = rawRequest($port, httpWire($port, $encoded) . 'extra');
        check($status === 400, 'extra/pipelined bytes rejected');
        [$status] = rawRequest($port, "GET /health HTTP/1.1\r\nHost: foreign.example\r\n\r\n");
        check($status === 400, 'foreign host rejected');
        [$status] = rawRequest($port, "GET /health HTTP/1.1\r\nHost: 127.0.0.1:" . $port . "\r\nX-Padding: " . str_repeat('x', HEADER_LIMIT) . "\r\n\r\n");
        check($status === 431, 'oversized actual headers rejected');
        [$status, $allowed] = rawRequest($port, httpWire($port, $encoded));
        check($status === 200 && $allowed['state']['decision'] === 'ALLOW', 'HTTP parser rejections did not mutate role');
        $revokeBody = http_build_query(['runId' => $runId, 'pattern' => 'security', 'operationId' => 'revoke']);
        [$status, $revoked] = rawRequest($port, httpWire($port, $revokeBody));
        check($status === 200 && $revoked['state']['currentRole'] === 'viewer', 'native role mutation');
        [$status, $denied] = rawRequest($port, httpWire($port, $encoded));
        check($status === 403 && $denied['state']['reason'] === 'REVOKED' && $denied['events'][0]['sequence'] > $revoked['events'][0]['sequence'], 'state and sequence persist across native requests');
        $paidBody = http_build_query(array_merge(['runId' => $runId, 'pattern' => 'payment', 'operationId' => 'deliver'], signed($payment)));
        [$status, $paid] = rawRequest($port, httpWire($port, $paidBody));
        [$retryStatus, $retry] = rawRequest($port, httpWire($port, $paidBody));
        check($status === 200 && $retryStatus === 200 && $paid['state'] === $retry['state'] && $retry['state']['entitlements'] === 1 && $retry['events'][0]['type'] === 'PAYMENT_DUPLICATE', 'native payment idempotency persists');
    } finally {
        stopFixture($handle);
    }
    $port = fixturePort();
    $handle = launchFixture($port, uuid(), 1);
    try {
        $deadline = monotonicSeconds() + 2.0;
        while (proc_get_status($handle[0])['running'] && monotonicSeconds() < $deadline) {
            usleep(20000);
        }
        check(!proc_get_status($handle[0])['running'], 'native process exits at bounded run lifetime');
    } finally {
        stopFixture($handle);
    }
    fwrite(STDOUT, 'PHP_NATIVE_TESTS_OK checks=' . $checks . " actual_cli_http=true redis_matrix=separate\n");
} catch (Throwable $error) {
    fwrite(STDERR, $error->getMessage() . "\n");
    exit(1);
}
