import { createHash, createHmac, randomUUID, timingSafeEqual } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { createServer } from 'node:http';
import type { IncomingMessage, ServerResponse } from 'node:http';
import { createConnection } from 'node:net';

// Disposable, synthetic local fixtures. This program never accepts a destination or token.
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
function matches(value: string, pattern: RegExp): boolean {
  // JavaScript's $ can match before a final line terminator; require the whole value.
  return pattern.exec(value)?.[0] === value;
}
const runId = process.env.LAB_RUN_ID ?? '';
if (!matches(runId, UUID)) throw new Error('LAB_RUN_ID must be a UUID.');
function port(name: string, fallback: number): number {
  const value = process.env[name] ?? String(fallback);
  if (!matches(value, /^[0-9]{1,5}$/) || Number(value) < 1 || Number(value) > 65535) {
    throw new Error(`${name} must be a port from 1 to 65535.`);
  }
  return Number(value);
}
const httpPort = port('LAB_PORT', 8100);
const redisPort = port('LAB_REDIS_PORT', 6379);
const failurePort = port('LAB_REDIS_FAILURE_PORT', 6380);
const timeoutPort = port('LAB_REDIS_TIMEOUT_PORT', 6381);
const sourceHash = createHash('sha256').update(readFileSync(new URL(import.meta.url))).digest('hex');
const cacheKey = `getlancer:${runId}:item`;
const CACHE_VALUE = 'fixture-item';
const TTL_MS = 5000;
const BODY_LIMIT = 16 * 1024;
const PAYMENT_KEY = 'getlancer-payment-fixture-only';
const AUTH_KEY = 'getlancer-identity-fixture-only';
const MAX_EVENTS = 100;
const MAX_SEQUENCE = 10_000_000;
const MAX_STORE_READS = 1_000_000;
const decoder = new TextDecoder('utf-8', { fatal: true, ignoreBOM: true });
const fixtureIssuedAt = Date.now();

type Pattern = 'cache' | 'security' | 'payment';
type Edge = 'client-service' | 'service-redis' | 'service-store' | 'service-policy' |
  'policy-store' | 'service-emulator' | 'emulator-ledger';
type EventType = 'CACHE_MISS' | 'CACHE_HIT' | 'CACHE_EXPIRED' | 'CACHE_FALLBACK' |
  'CACHE_RESET' | 'STORE_READ' | 'AUTH_ALLOWED' | 'AUTH_DENIED' | 'AUTH_REVOKED' |
  'AUTH_RESET' | 'PAYMENT_APPLIED' | 'PAYMENT_DUPLICATE' | 'PAYMENT_SIGNATURE_DENIED' |
  'PAYMENT_CONFLICT' | 'PAYMENT_PENDING' | 'PAYMENT_RESET' | 'REFUND_PENDING' | 'REFUND_APPLIED';
type State = Record<string, string | number>;
type LabEvent = {
  runId: string; sourceHash: string; requestId: string; sequence: number;
  type: EventType; edge: Edge; recordedAt: string; summary: string;
};
type Outcome = { status: number; state: State; events: LabEvent[] };
type CommandReply = string | number | null;
type ProtocolError = Error & { status: number; code: string };

function denial(status: number, code: string, message: string): ProtocolError {
  return Object.assign(new Error(message), { status, code });
}
function send(response: ServerResponse, status: number, value: unknown): void {
  const body = JSON.stringify(value);
  if (Buffer.byteLength(body) > 4096) {
    sendError(response, denial(500, 'RESPONSE_LIMIT', 'The bounded response could not be produced.'));
    return;
  }
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
    'Cache-Control': 'no-store',
  });
  response.end(body);
}
function sendError(response: ServerResponse, error: ProtocolError): void {
  send(response, error.status, { error: { code: error.code, message: error.message } });
}
function readBody(request: IncomingMessage): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    let size = 0;
    let settled = false;
    const fail = (error: ProtocolError) => {
      if (settled) return;
      settled = true;
      clearTimeout(deadline);
      chunks.length = 0;
      reject(error);
    };
    const deadline = setTimeout(() => fail(denial(408, 'BODY_TIMEOUT', 'The request body did not complete within five seconds.')), 5000);
    request.on('data', (chunk: Buffer) => {
      if (settled) return;
      size += chunk.length;
      if (size > BODY_LIMIT) {
        fail(denial(413, 'BODY_TOO_LARGE', 'The request exceeds 16 KiB.'));
        return;
      }
      chunks.push(chunk);
    });
    request.on('end', () => {
      if (settled) return;
      settled = true;
      clearTimeout(deadline);
      resolve(Buffer.concat(chunks));
    });
    request.on('aborted', () => fail(denial(400, 'INCOMPLETE_BODY', 'The request body is incomplete.')));
    request.on('error', () => fail(denial(400, 'INCOMPLETE_BODY', 'The request body is incomplete.')));
  });
}
function parseForm(bytes: Buffer): Record<string, string> {
  let body: string;
  try { body = decoder.decode(bytes); }
  catch { throw denial(400, 'INVALID_ENCODING', 'The request must contain valid UTF-8.'); }
  const fields: Record<string, string> = Object.create(null) as Record<string, string>;
  if (!body) throw denial(400, 'INVALID_FIELDS', 'Required form fields are missing.');
  for (const part of body.split('&')) {
    const equals = part.indexOf('=');
    if (equals < 1) throw denial(400, 'INVALID_FIELDS', 'Each form field must have a name and value.');
    let name: string;
    let value: string;
    try {
      name = decodeURIComponent(part.slice(0, equals).replace(/\+/g, ' '));
      value = decodeURIComponent(part.slice(equals + 1).replace(/\+/g, ' '));
    } catch { throw denial(400, 'INVALID_ENCODING', 'Form escapes must encode valid UTF-8.'); }
    if (Object.hasOwn(fields, name)) throw denial(400, 'DUPLICATE_FIELD', 'Duplicate form fields are not accepted.');
    fields[name] = value;
  }
  return fields;
}
function validate(fields: Record<string, string>): Pattern {
  if (!matches(fields.runId ?? '', UUID)) throw denial(400, 'INVALID_RUN_ID', 'runId must be a UUID.');
  if (fields.runId !== runId) throw denial(403, 'FOREIGN_RUN', 'This process only serves its configured run.');
  const pattern = fields.pattern;
  if (pattern !== 'cache' && pattern !== 'security' && pattern !== 'payment') {
    throw denial(400, 'INVALID_PATTERN', 'The pattern is not supported.');
  }
  const operation = fields.operationId;
  const operations = {
    cache: ['read', 'expire', 'read-unavailable', 'read-timeout', 'reset'],
    security: ['authorize', 'revoke', 'reset'],
    payment: ['deliver', 'timeout', 'reset'],
  };
  if (!operations[pattern].includes(operation)) throw denial(400, 'INVALID_OPERATION', 'The operation is not supported.');
  const allowed = ['runId', 'pattern', 'operationId'];
  if (pattern === 'security' && operation === 'authorize') allowed.push('identity');
  if (pattern === 'payment' && operation === 'deliver') allowed.push('eventBody', 'signature');
  if (Object.keys(fields).length !== allowed.length || allowed.some(name => !Object.hasOwn(fields, name)) ||
    Object.keys(fields).some(name => !allowed.includes(name))) {
    throw denial(400, 'INVALID_FIELDS', 'Only the exact fields for this operation are accepted.');
  }
  if (pattern === 'security' && operation === 'authorize' &&
    !['editor', 'viewer', 'other-tenant', 'expired', 'tampered'].includes(fields.identity)) {
    throw denial(400, 'INVALID_IDENTITY', 'The synthetic identity is not supported.');
  }
  if (pattern === 'payment' && operation === 'deliver' &&
    (fields.signature.length !== 64 || !matches(fields.signature, /^[0-9a-f]{64}$/))) {
    throw denial(400, 'INVALID_SIGNATURE', 'signature must be 64 lowercase hexadecimal characters.');
  }
  return pattern;
}

// One RESP command per connection, with an absolute deadline, even if a peer trickles bytes.
function redis(command: string[], destination: number = redisPort): Promise<CommandReply> {
  return new Promise((resolve, reject) => {
    const socket = createConnection({ host: '127.0.0.1', port: destination });
    let bytes: Buffer = Buffer.alloc(0);
    let settled = false;
    const finish = (error: boolean, reply: CommandReply = null) => {
      if (settled) return;
      settled = true;
      clearTimeout(deadline);
      socket.destroy();
      if (error) reject(new Error('Redis fixture unavailable.'));
      else resolve(reply);
    };
    const deadline = setTimeout(() => finish(true), 250);
    socket.setNoDelay(true);
    socket.once('connect', () => {
      const parts = [`*${command.length}\r\n`];
      for (const part of command) parts.push(`$${Buffer.byteLength(part)}\r\n${part}\r\n`);
      socket.write(parts.join(''));
    });
    socket.on('data', (chunk: Buffer) => {
      bytes = Buffer.concat([bytes, chunk]);
      if (bytes.length > 1024) { finish(true); return; }
      const lineEnd = bytes.indexOf('\r\n');
      if (lineEnd < 0) return;
      const marker = String.fromCharCode(bytes[0]);
      const line = bytes.toString('ascii', 1, lineEnd);
      const afterLine = lineEnd + 2;
      if (marker === '+' && bytes.length === afterLine) { finish(false, line); return; }
      if (marker === ':' && matches(line, /^-?\d+$/) && bytes.length === afterLine && Number.isSafeInteger(Number(line))) {
        finish(false, Number(line)); return;
      }
      if (marker === '$' && matches(line, /^-?\d+$/)) {
        const length = Number(line);
        if (length === -1 && bytes.length === afterLine) { finish(false, null); return; }
        if (!Number.isSafeInteger(length) || length < 0 || length > 256) { finish(true); return; }
        const end = afterLine + length;
        if (bytes.length < end + 2) return;
        if (bytes.length !== end + 2 || bytes.toString('ascii', end) !== '\r\n') { finish(true); return; }
        try { finish(false, decoder.decode(bytes.subarray(afterLine, end))); }
        catch { finish(true); }
        return;
      }
      finish(true);
    });
    socket.once('error', () => finish(true));
    socket.once('end', () => finish(true));
    socket.once('close', () => finish(true));
  });
}

let sequence = 0;
let storeReads = 0;
let currentRole: 'editor' | 'viewer' = 'editor';
const ledger = new Map<string, string>();
let paymentSeen = false;
let refundSeen = false;
function event(requestId: string, type: EventType, edge: Edge, summary: string): LabEvent {
  return { runId, sourceHash, requestId, sequence: ++sequence, type, edge, recordedAt: new Date().toISOString(), summary };
}
function storeRead(requestId: string, events: LabEvent[]): string {
  storeReads++;
  events.push(event(requestId, 'STORE_READ', 'service-store', 'The synthetic fixture store completed one read.'));
  return CACHE_VALUE;
}
async function cache(operation: string, requestId: string): Promise<Outcome> {
  const events: LabEvent[] = [];
  if (operation === 'reset' || operation === 'expire') {
    try {
      const reply = await redis([operation === 'reset' ? 'DEL' : 'PEXPIRE', cacheKey, ...(operation === 'expire' ? ['0'] : [])]);
      if (reply !== 0 && reply !== 1) throw new Error('Unexpected Redis reply.');
    } catch { throw denial(503, 'REDIS_UNAVAILABLE', 'The Redis fixture action could not complete.'); }
    if (operation === 'reset') storeReads = 0;
    events.push(event(requestId, operation === 'reset' ? 'CACHE_RESET' : 'CACHE_EXPIRED', 'service-redis',
      operation === 'reset' ? 'Redis deleted only the current run fixture key.' : 'Redis completed immediate expiry of the current run fixture key.'));
    return { status: 200, state: { cache: operation === 'reset' ? 'RESET' : 'EXPIRED', storeReads, ttlMs: 0 }, events };
  }
  let retrieved = false;
  try {
    const destination = operation === 'read-unavailable' ? failurePort : operation === 'read-timeout' ? timeoutPort : redisPort;
    const value = await redis(['GET', cacheKey], destination);
    if (value !== null && value !== CACHE_VALUE) throw new Error('Unexpected Redis fixture response.');
    if (value !== null) {
      const ttl = await redis(['PTTL', cacheKey], destination);
      if (typeof ttl !== 'number' || ttl < -2 || ttl > TTL_MS) throw new Error('Unexpected Redis TTL.');
      events.push(event(requestId, 'CACHE_HIT', 'service-redis', 'Redis returned the current run fixture value.'));
      return { status: 200, state: { cache: 'HIT', storeReads, ttlMs: Math.max(0, ttl) }, events };
    }
    events.push(event(requestId, 'CACHE_MISS', 'service-redis', 'Redis missed the current run fixture key.'));
    const fixtureValue = storeRead(requestId, events);
    retrieved = true;
    if (await redis(['SET', cacheKey, fixtureValue, 'PX', String(TTL_MS)], destination) !== 'OK') throw new Error('Unexpected Redis set reply.');
    const ttl = await redis(['PTTL', cacheKey], destination);
    if (typeof ttl !== 'number' || ttl < -2 || ttl > TTL_MS) throw new Error('Unexpected Redis TTL.');
    return { status: 200, state: { cache: 'MISS', storeReads, ttlMs: Math.max(0, ttl) }, events };
  } catch {
    events.push(event(requestId, 'CACHE_FALLBACK', 'service-redis', 'The configured Redis fixture did not complete a usable cache read.'));
    if (!retrieved) storeRead(requestId, events);
    return { status: 200, state: { cache: 'FALLBACK', storeReads, ttlMs: 0 }, events };
  }
}
function security(operation: string, identity: string, requestId: string): Outcome {
  if (operation === 'reset' || operation === 'revoke') {
    currentRole = operation === 'reset' ? 'editor' : 'viewer';
    return { status: 200, state: { decision: operation === 'reset' ? 'RESET' : 'DENY', reason: operation === 'reset' ? 'RESET' : 'REVOKED', currentRole },
      events: [event(requestId, operation === 'reset' ? 'AUTH_RESET' : 'AUTH_REVOKED', 'policy-store',
        operation === 'reset' ? 'The current run editor role policy was restored.' : 'The current run editor role policy was revoked.')] };
  }
  const now = Date.now();
  const claims = {
    subject: 'fixture-user', tenant: identity === 'other-tenant' ? 'fixture-other-tenant' : 'fixture-tenant',
    role: identity === 'viewer' ? 'viewer' : 'editor',
    expiresAt: identity === 'expired' ? fixtureIssuedAt - 1000 : fixtureIssuedAt + 24 * 60 * 60 * 1000,
  };
  const body = JSON.stringify(claims);
  const issued = createHmac('sha256', AUTH_KEY).update(body).digest();
  const presented = Buffer.from(issued);
  if (identity === 'tampered') presented[0] ^= 1;
  const expected = createHmac('sha256', AUTH_KEY).update(body).digest();
  // The current authority is read for every authorization, independently of issued claims.
  const observedRole = currentRole;
  let reason = 'ALLOW';
  if (!timingSafeEqual(expected, presented)) reason = 'SIGNATURE';
  else if (claims.expiresAt <= now) reason = 'EXPIRED';
  else if (claims.tenant !== 'fixture-tenant') reason = 'TENANT';
  else if (claims.role !== 'editor') reason = 'ROLE';
  else if (observedRole !== 'editor') reason = 'REVOKED';
  const allowed = reason === 'ALLOW';
  const explanations: Record<string, string> = {
    ALLOW: 'Synthetic identity verified and the current role policy permits the action.',
    SIGNATURE: 'The synthetic identity signature did not verify.', EXPIRED: 'The synthetic identity has expired.',
    TENANT: 'The synthetic identity belongs to another tenant.', ROLE: 'The synthetic identity has the wrong role.',
    REVOKED: 'The current role policy denies the formerly permitted editor identity.',
  };
  return { status: allowed ? 200 : ['SIGNATURE', 'EXPIRED'].includes(reason) ? 401 : 403,
    state: { decision: allowed ? 'ALLOW' : 'DENY', reason, currentRole: observedRole },
    events: [event(requestId, allowed ? 'AUTH_ALLOWED' : 'AUTH_DENIED', reason === 'REVOKED' || allowed ? 'policy-store' : 'service-policy', explanations[reason])] };
}
function paymentState(): State {
  return { payment: paymentSeen ? refundSeen ? 'REFUNDED' : 'PAID' : 'PENDING',
    entitlements: paymentSeen && !refundSeen ? 1 : 0, processedEvents: ledger.size };
}
function payment(operation: string, fields: Record<string, string>, requestId: string): Outcome {
  if (operation === 'reset') {
    ledger.clear(); paymentSeen = false; refundSeen = false;
    return { status: 200, state: paymentState(), events: [event(requestId, 'PAYMENT_RESET', 'emulator-ledger', 'The current run payment ledger was cleared.')] };
  }
  if (operation === 'timeout') {
    return { status: paymentSeen ? 200 : 202, state: paymentState(), events: [event(requestId, 'PAYMENT_PENDING', 'service-emulator',
      paymentSeen ? 'The emulator returned an ambiguous timeout; the confirmed ledger state was preserved.' :
        'The emulator returned an ambiguous timeout; the ledger grants no new entitlement.')] };
  }
  const body = fields.eventBody;
  const expected = createHmac('sha256', PAYMENT_KEY).update(Buffer.from(body, 'utf8')).digest();
  if (!timingSafeEqual(expected, Buffer.from(fields.signature, 'hex'))) {
    return { status: 401, state: paymentState(), events: [event(requestId, 'PAYMENT_SIGNATURE_DENIED', 'service-emulator', 'The exact event body signature did not verify; the ledger was unchanged.')] };
  }
  const match = /^(evt-[1-9][0-9]{0,3})\|order-1\|(payment|refund)\.succeeded$/.exec(body);
  if (!match || match[0] !== body) throw denial(400, 'INVALID_EVENT', 'The signed event must follow the synthetic fixture grammar.');
  const previous = ledger.get(match[1]);
  if (previous !== undefined) {
    const identical = previous === body;
    return { status: identical ? 200 : 409, state: paymentState(), events: [event(requestId,
      identical ? 'PAYMENT_DUPLICATE' : 'PAYMENT_CONFLICT', 'emulator-ledger',
      identical ? 'The ledger observed an identical event retry without another effect.' : 'The ledger rejected a reused event identifier with different content.')] };
  }
  if (ledger.size >= MAX_EVENTS) {
    return { status: 409, state: paymentState(), events: [event(requestId, 'PAYMENT_CONFLICT', 'emulator-ledger', 'The bounded fixture ledger is full; no event was added.')] };
  }
  ledger.set(match[1], body);
  const events: LabEvent[] = [];
  if (match[2] === 'refund') {
    refundSeen = true;
    events.push(event(requestId, paymentSeen ? 'REFUND_APPLIED' : 'REFUND_PENDING', 'emulator-ledger',
      paymentSeen ? 'The ledger recorded a refund; the order has no entitlement.' : 'The ledger recorded a refund awaiting payment, without granting an entitlement.'));
  } else {
    paymentSeen = true;
    events.push(event(requestId, 'PAYMENT_APPLIED', 'emulator-ledger', refundSeen ?
      'The ledger reconciled payment with the pending refund without granting an entitlement.' : 'The ledger recorded payment; the order has one entitlement.'));
    if (refundSeen) events.push(event(requestId, 'REFUND_APPLIED', 'emulator-ledger', 'The ledger reconciled the pending refund; the order has no entitlement.'));
  }
  return { status: !paymentSeen && refundSeen ? 202 : 200, state: paymentState(), events };
}

// A bounded serial queue makes fixture mutations and event ordering deterministic under concurrency.
let pending = 0;
let queue: Promise<void> = Promise.resolve();
const server = createServer(async (request, response) => {
  const startedAt = performance.now();
  try {
    if ((request.url ?? '').includes('?')) throw denial(400, 'QUERY_NOT_ALLOWED', 'Query strings are not accepted.');
    if (request.url !== '/health' && request.url !== '/request') throw denial(404, 'NOT_FOUND', 'The path is not supported.');
    if ((request.url === '/health' && request.method !== 'GET') || (request.url === '/request' && request.method !== 'POST')) {
      throw denial(405, 'METHOD_NOT_ALLOWED', 'The method is not supported for this path.');
    }
    if (request.url === '/health') {
      if (request.headers['transfer-encoding'] || (request.headers['content-length'] && request.headers['content-length'] !== '0')) {
        throw denial(400, 'INVALID_BODY', 'Health requests cannot have a body.');
      }
      send(response, 200, { mode: 'Local execution', language: 'typescript', runId, sourceHash });
      return;
    }
    const contentTypeCount = request.rawHeaders.filter((_, index) => index % 2 === 0 && request.rawHeaders[index].toLowerCase() === 'content-type').length;
    if (contentTypeCount !== 1 || !matches(request.headers['content-type'] ?? '', /^application\/x-www-form-urlencoded(?:[ \t]*;[ \t]*charset=utf-8)?$/i)) {
      throw denial(415, 'UNSUPPORTED_CONTENT_TYPE', 'Use application/x-www-form-urlencoded with UTF-8.');
    }
    if (Number(request.headers['content-length'] ?? 0) > BODY_LIMIT) throw denial(413, 'BODY_TOO_LARGE', 'The request exceeds 16 KiB.');
    if (pending >= 32) throw denial(503, 'RUN_BUSY', 'The bounded local request queue is full.');
    pending++;
    try {
      const fields = parseForm(await readBody(request));
      const pattern = validate(fields);
      const requestId = randomUUID();
      const previous = queue;
      let release: () => void = () => {};
      queue = new Promise<void>(resolve => { release = resolve; });
      await previous;
      try {
        if (sequence > MAX_SEQUENCE - 4 || (pattern === 'cache' && storeReads >= MAX_STORE_READS && fields.operationId !== 'reset')) {
          throw denial(503, 'RUN_LIMIT', 'This disposable run reached its execution bound.');
        }
        const result = pattern === 'cache' ? await cache(fields.operationId, requestId) : pattern === 'security' ?
          security(fields.operationId, fields.identity, requestId) : payment(fields.operationId, fields, requestId);
        send(response, result.status, { runId, sourceHash, requestId, pattern, status: result.status,
          durationMs: Math.max(0, Math.round(performance.now() - startedAt)), state: result.state, events: result.events });
      } finally { release(); }
    } finally { pending--; }
  } catch (error) {
    const safe = error instanceof Error && 'status' in error && 'code' in error ? error as ProtocolError :
      denial(500, 'LOCAL_EXECUTION_FAILED', 'The local fixture action could not complete.');
    if (!response.headersSent && !response.destroyed) sendError(response, safe);
    request.resume();
  }
});
server.requestTimeout = 5000;
server.headersTimeout = 5000;
server.maxHeadersCount = 32;
server.maxConnections = 64;
server.keepAliveTimeout = 1000;
server.listen(httpPort, '127.0.0.1', () => console.log('TypeScript local fixture server started.'));
