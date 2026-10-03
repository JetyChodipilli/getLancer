import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import * as fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import { createHash, randomUUID } from 'node:crypto';
import { spawn } from 'node:child_process';
import { createPublisher, configFromEnv, manifestHash, HARD_LIMITS, CONTENT_SECURITY_POLICY } from '../ops/demo-publisher/server.mjs';

const secret = 'publisher-test-secret-01234567890123456789';
const gatewaySecret = 'gateway-test-secret-01234567890123456789';
const adminHost = 'demo-publisher:8090';
const hash = (value) => createHash('sha256').update(value).digest('hex');
function payload(id = randomUUID(), files = { 'index.html': '<h1>Approved static frontend</h1>', 'assets/main.js': 'throw new Error("Server must never execute this");' }, expiresAt = new Date(Date.now() + 86400_000).toISOString()) {
  const mapped = Object.entries(files).map(([filename, value]) => { const bytes = Buffer.isBuffer(value) ? value : Buffer.from(value); return {
    path: filename, sha256: hash(bytes), sizeBytes: bytes.length, contentBase64: bytes.toString('base64') }; }).sort((a, b) => a.path < b.path ? -1 : 1);
  return { id, archiveSha256: hash('verified ZIP checked by Java'), manifestSha256: manifestHash(mapped), expiresAt, files: mapped };
}
function request(port, { method = 'GET', host = adminHost, target = '/health', body, raw, headers = {}, admin = false } = {}) {
  return new Promise((resolve, reject) => {
    const bytes = raw === undefined ? (body === undefined ? undefined : Buffer.from(JSON.stringify(body))) : Buffer.from(raw);
    const outgoing = { Host: host, ...(admin ? { Authorization: `Bearer ${secret}` } : {}), ...(bytes ? { 'Content-Type': 'application/json', 'Content-Length': bytes.length } : {}), ...headers };
    const req = http.request({ host: 'localhost', port, method, path: target, headers: outgoing, agent: false }, (res) => {
      const chunks = []; res.on('data', (chunk) => chunks.push(chunk)); res.on('end', () => {
        const content = Buffer.concat(chunks); let json; try { json = JSON.parse(content.toString()); } catch {}
        resolve({ status: res.statusCode, headers: res.headers, bytes: content, json });
      }); res.on('error', reject);
    }); req.on('error', reject); req.end(bytes);
  });
}
async function harness(t, overrides = {}) {
  const dataDir = await fs.mkdtemp(path.join(os.tmpdir(), 'demo-publisher-'));
  const calls = []; let mode = 'allow'; let hold;
  const gateway = http.createServer((req, res) => {
    calls.push({ path: req.url, headers: req.headers });
    assert.equal(req.headers['x-getlancer-demo-gateway'], gatewaySecret);
    if (mode === 'hang') return;
    if (mode === 'hold') { hold = () => { res.writeHead(200, { 'Content-Type': 'application/json' }); res.end('{"allowed":true}'); }; return; }
    if (mode === 'redirect') { res.writeHead(302, { Location: 'http://localhost:1/unapproved' }); res.end(); return; }
    if (mode === 'huge') { res.writeHead(200, { 'Content-Type': 'application/json' }); res.end(' '.repeat(4097)); return; }
    if (mode === 'malformed') { res.writeHead(200, { 'Content-Type': 'application/json' }); res.end('{'); return; }
    if (mode === 'wrong-type') { res.writeHead(200, { 'Content-Type': 'text/plain' }); res.end('{"allowed":true}'); return; }
    res.writeHead(200, { 'Content-Type': 'application/json' }); res.end(JSON.stringify({ allowed: mode === 'allow' }));
  });
  await new Promise((resolve) => gateway.listen(0, 'localhost', resolve));
  const config = { dataDir, secret, adminHost, publicUrlTemplate: 'http://{id}.demo.localhost:8090',
    gatewayUrl: `http://localhost:${gateway.address().port}/api/v1/hosting/gateway`, gatewaySecret, ...overrides };
  let publisher; let port;
  t.after(async () => { if (publisher) await publisher.close(); gateway.closeAllConnections();
    if (gateway.listening) await new Promise((resolve) => gateway.close(resolve)); await fs.rm(dataDir, { recursive: true, force: true }); });
  publisher = await createPublisher(config); port = (await publisher.listen()).port;
  return { calls, config, dataDir, get port() { return port; }, setMode(value) { mode = value; }, release() { assert.ok(hold); hold(); },
    async offline() { gateway.closeAllConnections(); await new Promise((resolve) => gateway.close(resolve)); },
    admin(method, id, body, options = {}) { return request(port, { method, target: `/deployments/${id}`, body, admin: true, ...options }); },
    public(id, target = '/', options = {}) { return request(port, { host: `${id}.demo.localhost:8090`, target, ...options }); },
    async restart() { await publisher.close(); publisher = await createPublisher(config); port = (await publisher.listen()).port; },
    async stop() { await publisher.close(); },
  };
}

test('real HTTP: immutable publish, exact retries, file/HEAD gateway checks, Host/admin isolation and defense headers', async (t) => {
  const h = await harness(t); const p = payload();
  assert.equal((await request(h.port)).status, 200);
  assert.equal((await h.admin('GET', p.id)).status, 404);
  assert.equal((await h.admin('PUT', p.id, p, { admin: false })).status, 401);
  const created = await h.admin('PUT', p.id, p); assert.equal(created.status, 200);
  assert.deepEqual(created.json, { id: p.id, state: 'READY', archiveSha256: p.archiveSha256, manifestSha256: p.manifestSha256,
    expiresAt: p.expiresAt, url: `http://${p.id}.demo.localhost:8090` });
  assert.deepEqual((await h.admin('PUT', p.id, p)).json, created.json);
  assert.deepEqual((await h.admin('GET', p.id)).json, created.json);
  const page = await h.public(p.id, '/?token=private&url=https://attacker.example', { headers: { Cookie: 'session=private', Authorization: 'Bearer private', 'X-GetLancer-Demo-Gateway': 'attacker' } });
  assert.equal(page.status, 200); assert.equal(page.bytes.toString(), '<h1>Approved static frontend</h1>');
  assert.equal(page.headers['content-security-policy'], CONTENT_SECURITY_POLICY);
  assert.equal(page.headers['cache-control'], 'no-store'); assert.equal(page.headers['x-content-type-options'], 'nosniff');
  assert.equal(page.headers['referrer-policy'], 'no-referrer'); assert.equal(page.headers['x-frame-options'], 'DENY');
  assert.match(page.headers['permissions-policy'], /camera=\(\)/); assert.equal(page.headers['cross-origin-resource-policy'], 'same-origin');
  assert.equal(page.headers['cross-origin-opener-policy'], 'same-origin'); assert.equal(page.headers['set-cookie'], undefined);
  const js = await h.public(p.id, '/assets/main.js'); assert.equal(js.status, 200); assert.match(js.headers['content-type'], /^text\/javascript/);
  const head = await h.public(p.id, '/', { method: 'HEAD' }); assert.equal(head.status, 200); assert.equal(head.bytes.length, 0);
  assert.equal(Number(head.headers['content-length']), page.bytes.length);
  assert.equal(h.calls.length, 3);
  for (const call of h.calls) { assert.equal(call.path, `/api/v1/hosting/gateway/${p.id}`); assert.equal(call.headers.cookie, undefined); assert.equal(call.headers.authorization, undefined); }
  assert.equal((await h.public(p.id, '/missing.txt')).status, 404); assert.equal(h.calls.length, 4);
  for (const method of ['GET', 'PUT', 'DELETE']) assert.equal((await h.public(p.id, `/deployments/${p.id}`, { method, body: method === 'PUT' ? p : undefined, headers: { Authorization: `Bearer ${secret}` } })).status, 403);
  assert.equal((await h.public(p.id, '/health')).status, 403);
  assert.equal((await request(h.port, { host: 'attacker.example', target: '/' })).status, 403);
  assert.equal((await request(h.port, { host: `${p.id}.demo.localhost:8091`, target: '/' })).status, 403);
  assert.equal((await request(h.port, { host: adminHost, target: '/' })).status, 401);
  assert.equal((await h.public(p.id, '/', { method: 'POST' })).status, 405);
});

test('live gateway denial, revocation, invalid/offline/redirect/oversized responses all fail closed without caching', async (t) => {
  const h = await harness(t); const p = payload(); await h.admin('PUT', p.id, p);
  assert.equal((await h.public(p.id)).status, 200);
  for (const mode of ['deny', 'malformed', 'wrong-type', 'redirect', 'huge']) { h.setMode(mode); assert.equal((await h.public(p.id)).status, 403, mode); }
  h.setMode('allow'); assert.equal((await h.public(p.id)).status, 200);
  h.setMode('hang'); const before = Date.now(); assert.equal((await h.public(p.id)).status, 403); assert.ok(Date.now() - before < 2800);
  await h.offline(); assert.equal((await h.public(p.id)).status, 403);
});

test('independent id, hashes, manifest, base64, times, schema and file bounds validation', async (t) => {
  const h = await harness(t); const p = payload(); const mutate = async (fn, expected = 400) => { const bad = structuredClone(p); fn(bad); assert.equal((await h.admin('PUT', p.id, bad)).status, expected); };
  await mutate((bad) => { bad.id = randomUUID(); });
  await mutate((bad) => { bad.archiveSha256 = 'not-a-hash'; });
  await mutate((bad) => { bad.manifestSha256 = '0'.repeat(64); });
  await mutate((bad) => { bad.files[0].sha256 = '0'.repeat(64); });
  await mutate((bad) => { bad.files[0].contentBase64 = 'aGVsbG8'; });
  await mutate((bad) => { bad.files[0].sizeBytes++; });
  await mutate((bad) => { bad.files[0].sizeBytes = -1; }, 413);
  await mutate((bad) => { bad.files[0].sizeBytes = HARD_LIMITS.fileBytes + 1; }, 413);
  await mutate((bad) => { bad.files[0].sizeBytes = 1.5; }, 413);
  await mutate((bad) => { bad.expiresAt = new Date(Date.now() - 1000).toISOString(); });
  await mutate((bad) => { bad.expiresAt = new Date(Date.now() + HARD_LIMITS.expiryMs + 10_000).toISOString(); });
  await mutate((bad) => { bad.expiresAt = '2027-02-30T12:00:00Z'; });
  await mutate((bad) => { bad.expiresAt = '2026-10-03T12:00:00+00:00'; });
  await mutate((bad) => { bad.extra = 'private'; });
  await mutate((bad) => { bad.files[0].extra = true; });
  await mutate((bad) => { bad.files.reverse(); });
  await mutate((bad) => { bad.files = []; });
  assert.equal((await h.admin('PUT', p.id, undefined, { raw: '{broken' })).status, 400);
  assert.equal((await h.admin('PUT', p.id, p, { headers: { 'Content-Type': 'text/plain' } })).status, 415);
  assert.equal((await h.admin('PUT', p.id, p, { headers: { 'Content-Encoding': 'gzip' } })).status, 415);
  assert.equal((await h.admin('PUT', p.id, p, { headers: { 'Content-Length': HARD_LIMITS.bodyBytes + 1 } })).status, 413);
  const tooMany = payload(randomUUID(), Object.fromEntries([['index.html', ''], ...Array.from({ length: 256 }, (_, i) => [`file-${i}.txt`, ''])]));
  assert.equal((await h.admin('PUT', tooMany.id, tooMany)).status, 400);
  const expanded = payload(randomUUID(), { 'index.html': Buffer.alloc(HARD_LIMITS.fileBytes, 97), 'one.txt': Buffer.alloc(HARD_LIMITS.fileBytes, 97), 'two.txt': 'x' });
  assert.equal((await h.admin('PUT', expanded.id, expanded)).status, 413);
  const normal = await h.admin('PUT', p.id, p); assert.equal(normal.status, 200);
});

test('path allowlist denies traversal, dot/private/source/archive names, duplicates, case and parent collisions', async (t) => {
  const h = await harness(t);
  for (const invalid of ['../outside.txt', '/outside.txt', 'a//b.txt', '.env', 'a/.private.txt', 'a\\b.txt', 'a%2fb.txt', 'a/../b.txt', 'file.js.map', 'file.zip', 'file.exe', 'source.ts', 'credentials.json', 'secret.json', 'private-key.txt',
    'tokens.json', 'assets/design-tokens.js', 'tokens/theme.css', 'é.txt']) {
    const p = payload(randomUUID(), { 'index.html': '', [invalid]: 'bad' }); assert.equal((await h.admin('PUT', p.id, p)).status, 400, invalid);
  }
  const noRoot = payload(randomUUID(), { 'nested/index.html': '' }); assert.equal((await h.admin('PUT', noRoot.id, noRoot)).status, 400);
  for (const files of [{ 'index.html': '', 'INDEX.html': '' }, { 'index.html': '', 'assets.txt': '', 'assets.txt/main.js': '' },
    { 'index.html': '', 'Assets/one.js': '', 'assets/two.js': '' }]) {
    const p = payload(randomUUID(), files); assert.equal((await h.admin('PUT', p.id, p)).status, 400);
  }
  const duplicate = payload(); duplicate.files.push(duplicate.files.at(-1)); assert.equal((await h.admin('PUT', duplicate.id, duplicate)).status, 400);
  const p = payload(randomUUID(), { 'index.html': 'ok', 'README.md': 'Useful frontend sample notice.', 'LICENSE': 'Useful frontend license notice.',
    'nested/LICENCE': 'Useful frontend license notice.', 'tokens.css': ':root { --color: red; }', 'assets/design-tokens.css': ':root { --space: 1rem; }' });
  assert.equal((await h.admin('PUT', p.id, p)).status, 200);
  assert.equal((await h.public(p.id, '/README.md')).headers['content-type'], 'text/plain; charset=utf-8');
  for (const target of ['/../record.json', '/%2e%2e/record.json', '/assets%2fmain.js', '/%2findex.html', '//index.html', '/index.html%00', '/a\\b.txt', '/%ZZ']) assert.equal((await h.public(p.id, target)).status, 400, target);
  assert.equal(await fs.access(path.join(h.dataDir, 'outside.txt')).then(() => true, () => false), false);
});

test('conflicting immutable PUT, repeatable withdrawal, unknown tombstone, retries/restarts cannot resurrect', async (t) => {
  const h = await harness(t); const p = payload(); await h.admin('PUT', p.id, p);
  const altered = payload(p.id, { 'index.html': 'different' }, p.expiresAt); assert.equal((await h.admin('PUT', p.id, altered)).status, 409);
  const differentArchive = { ...p, archiveSha256: hash('different zip') }; assert.equal((await h.admin('PUT', p.id, differentArchive)).status, 409);
  const differentExpiry = { ...p, expiresAt: new Date(Date.now() + 200_000).toISOString() }; assert.equal((await h.admin('PUT', p.id, differentExpiry)).status, 409);
  await h.restart(); assert.equal((await h.public(p.id)).status, 200); assert.equal((await h.admin('PUT', p.id, p)).status, 200);
  const deleted = await h.admin('DELETE', p.id); assert.equal(deleted.status, 200); assert.equal(deleted.json.state, 'DELETED');
  assert.equal(deleted.json.archiveSha256, p.archiveSha256); assert.deepEqual((await h.admin('DELETE', p.id)).json, deleted.json);
  assert.equal((await h.public(p.id)).status, 404); assert.equal((await h.admin('PUT', p.id, p)).status, 409);
  assert.equal(await fs.access(path.join(h.dataDir, 'identities', p.id, 'bundle')).then(() => true, () => false), false);
  const unknown = payload(); const tombstone = await h.admin('DELETE', unknown.id); assert.deepEqual(tombstone.json, { id: unknown.id, state: 'DELETED' });
  assert.equal((await h.admin('PUT', unknown.id, unknown)).status, 409);
  await h.restart(); assert.deepEqual((await h.admin('GET', p.id)).json, deleted.json); assert.deepEqual((await h.admin('DELETE', unknown.id)).json, tombstone.json);
  assert.equal((await h.admin('PUT', p.id, p)).status, 409); assert.equal((await h.admin('PUT', unknown.id, unknown)).status, 409);
});

test('atomic serialized same-id publication and withdrawal races retain permanent tombstones', async (t) => {
  const h = await harness(t); const p = payload();
  const first = await Promise.all([h.admin('PUT', p.id, p), h.admin('PUT', p.id, p)]);
  assert.ok(first.some((result) => result.status === 200)); assert.ok(first.every((result) => [200, 503].includes(result.status)));
  const retry = await h.admin('PUT', p.id, p); assert.deepEqual(retry.json, first.find((result) => result.status === 200).json);
  const id = randomUUID(); const candidate = payload(id);
  const race = await Promise.all([h.admin('PUT', id, candidate), h.admin('DELETE', id), h.admin('PUT', id, candidate)]);
  assert.ok(race.every((result) => [200, 409, 503].includes(result.status)));
  assert.equal((await h.admin('GET', id)).json.state, 'DELETED'); assert.equal((await h.admin('PUT', id, candidate)).status, 409);
  h.setMode('hold'); const pending = h.public(p.id);
  for (let i = 0; i < 100 && h.calls.length === 0; i++) await new Promise((resolve) => setTimeout(resolve, 5));
  assert.equal((await h.admin('DELETE', p.id)).json.state, 'DELETED'); h.release(); assert.equal((await pending).status, 404);
  await h.restart(); assert.equal((await h.admin('GET', id)).json.state, 'DELETED');
});

test('expiry blocks serving, removes bytes and retains identity across restart; storage/identity caps serialize', async (t) => {
  let now = Date.now(); const h = await harness(t, { now: () => now, maxBytes: 16, maxIdentities: 3 });
  const p = payload(randomUUID(), { 'index.html': '12345678' }, new Date(now + 1000).toISOString()); assert.equal((await h.admin('PUT', p.id, p)).status, 200);
  const candidates = [payload(randomUUID(), { 'index.html': '12345678' }), payload(randomUUID(), { 'index.html': '12345678' })];
  const both = await Promise.all(candidates.map((candidate) => h.admin('PUT', candidate.id, candidate)));
  for (let index = 0; index < both.length; index++) if (both[index].status === 503) both[index] = await h.admin('PUT', candidates[index].id, candidates[index]);
  assert.deepEqual(both.map((result) => result.status).sort(), [200, 507]);
  now += 2000; assert.equal((await h.public(p.id)).status, 404); assert.equal((await h.admin('GET', p.id)).json.state, 'DELETED');
  assert.equal(await fs.access(path.join(h.dataDir, 'identities', p.id, 'bundle')).then(() => true, () => false), false);
  const third = payload(randomUUID(), { 'index.html': 'a' }); assert.equal((await h.admin('PUT', third.id, third)).status, 200);
  const neverCreated = randomUUID(); assert.deepEqual((await h.admin('DELETE', neverCreated)).json, { id: neverCreated, state: 'DELETED' });
  const fourth = payload(randomUUID(), { 'index.html': 'a' }); assert.equal((await h.admin('PUT', fourth.id, fourth)).status, 409);
  await h.restart(); assert.equal((await h.admin('PUT', p.id, p)).status, 409); assert.equal((await h.admin('GET', p.id)).json.state, 'DELETED');
});

test('traffic caps include HEAD/missing/denied attempts, reset after minute and are bounded per identity', async (t) => {
  let now = Date.now(); const h = await harness(t, { now: () => now, requestsPerMinute: 2 }); const p = payload(); await h.admin('PUT', p.id, p);
  assert.equal((await h.public(p.id)).status, 200); assert.equal((await h.public(p.id, '/', { method: 'HEAD' })).status, 200);
  assert.equal((await h.public(p.id)).status, 429); assert.equal(h.calls.length, 2);
  now += 60_001; h.setMode('deny'); assert.equal((await h.public(p.id)).status, 403); h.setMode('allow');
  assert.equal((await h.public(p.id, '/missing.txt')).status, 404); assert.equal((await h.public(p.id)).status, 429);
});

test('filesystem integrity rechecked per file and startup; symlink content and corrupt metadata fail closed', async (t) => {
  const h = await harness(t); const p = payload(); await h.admin('PUT', p.id, p);
  const bundle = path.join(h.dataDir, 'identities', p.id, 'bundle'); const index = path.join(bundle, 'index.html');
  await fs.writeFile(index, 'corrupt'); assert.equal((await h.public(p.id)).status, 503);
  await h.stop(); await assert.rejects(createPublisher(h.config), /integrity/);
  const bytes = Buffer.from(p.files.find((file) => file.path === 'index.html').contentBase64, 'base64'); await fs.writeFile(index, bytes);
  await h.restart(); await fs.rm(index); await fs.symlink('/etc/passwd', index); assert.equal((await h.public(p.id)).status, 503);
  await h.stop(); await assert.rejects(createPublisher(h.config));
  await fs.rm(index); await fs.writeFile(index, bytes); const recordFile = path.join(h.dataDir, 'identities', p.id, 'record.json');
  const record = JSON.parse(await fs.readFile(recordFile, 'utf8')); record.url = 'https://attacker.example'; await fs.writeFile(recordFile, JSON.stringify(record));
  await assert.rejects(createPublisher(h.config), /mismatch/);
});

test('startup cleans orphan staging and withdrawn bytes without dropping tombstones; duplicate local writer rejected', async (t) => {
  const h = await harness(t); const p = payload(); await h.admin('PUT', p.id, p);
  await assert.rejects(createPublisher(h.config), /already open/);
  await h.admin('DELETE', p.id); await h.stop();
  await fs.mkdir(path.join(h.dataDir, 'staging', 'crash-staging')); await fs.writeFile(path.join(h.dataDir, 'staging', 'crash-staging', 'temporary.txt'), 'unpublished');
  await fs.mkdir(path.join(h.dataDir, 'identities', p.id, 'bundle')); await fs.writeFile(path.join(h.dataDir, 'identities', p.id, 'bundle', 'index.html'), 'orphan withdrawn content');
  await h.restart(); assert.deepEqual(await fs.readdir(path.join(h.dataDir, 'staging')), []);
  assert.equal((await h.admin('GET', p.id)).json.state, 'DELETED'); assert.equal((await h.admin('PUT', p.id, p)).status, 409);
});

test('configuration requires isolated origins, exact gateway, independent secrets and downward-only limits', async (t) => {
  const h = await harness(t);
  for (const override of [{ secret: 'short' }, { gatewaySecret: secret }, { adminHost: 'admin/path' },
    { adminHost: `${randomUUID()}.demo.localhost:8090` }, { publicUrlTemplate: 'https://demo.example/{id}' },
    { publicUrlTemplate: 'http://{id}.example.com' }, { publicUrlTemplate: 'https://{id}.demo.example/a' }, { gatewayUrl: 'https://api.example/arbitrary' },
    { gatewayUrl: 'http://public.example/api/v1/hosting/gateway' }, { maxBytes: HARD_LIMITS.storageBytes + 1 }, { maxIdentities: 1001 },
    { requestsPerMinute: 241 }, { dataDir: 'relative' }]) await assert.rejects(createPublisher({ ...h.config, ...override }));
  const env = configFromEnv({ DEMO_PUBLISHER_DATA_DIR: '/data', DEMO_PUBLISHER_SECRET: secret, DEMO_PUBLISHER_ADMIN_HOST: adminHost,
    DEMO_PUBLIC_URL_TEMPLATE: h.config.publicUrlTemplate, DEMO_GATEWAY_URL: h.config.gatewayUrl, DEMO_GATEWAY_SECRET: gatewaySecret, DEMO_PUBLISHER_PORT: '8090' });
  assert.equal(env.port, 8090); assert.equal(env.host, '0.0.0.0'); assert.throws(() => configFromEnv({ DEMO_PUBLISHER_PORT: 'NaN' }));
});

test('maximum valid 10 MiB and 256 files publish; credential, invalid text and disguised archive/executable content rejected', async (t) => {
  const h = await harness(t);
  const maximum = payload(randomUUID(), { 'index.html': Buffer.alloc(HARD_LIMITS.fileBytes, 97), 'assets/main.js': Buffer.alloc(HARD_LIMITS.fileBytes, 97) });
  const before = Date.now(); assert.equal((await h.admin('PUT', maximum.id, maximum)).status, 200); assert.ok(Date.now() - before < 5000);
  const files = payload(randomUUID(), Object.fromEntries([['index.html', 'ok'], ...Array.from({ length: 255 }, (_, i) => [`file-${i}.txt`, ''])]));
  assert.equal((await h.admin('PUT', files.id, files)).status, 200);
  for (const content of ['-----BEGIN PRIVATE KEY-----', 'AKIAABCDEFGHIJKLMNOP', 'const API_KEY="abcdefghijklmnopq123";',
    '{"PASSWORD":"long-private-value-123"}', Buffer.from([0xff, 0xfe, 0xfa]), 'binary\0text']) {
    const candidate = payload(randomUUID(), { 'index.html': content }); assert.equal((await h.admin('PUT', candidate.id, candidate)).status, 400);
  }
  for (const magic of ['4d5a', '7f454c46', '504b0304', '504b0506', '504b0708', '1f8b', '377abcaf271c', '526172211a07',
    '425a68', 'fd377a585a00', 'cafebabe', 'bebafeca', 'feedface', 'cefaedfe', 'feedfacf', 'cffaedfe']) {
    const candidate = payload(randomUUID(), { 'index.html': 'valid', 'image.png': Buffer.from(`${magic}000102030405`, 'hex') });
    assert.equal((await h.admin('PUT', candidate.id, candidate)).status, 400, magic);
  }
  const tar = Buffer.alloc(512); tar.write('ustar', 257, 'ascii');
  const renamedTar = payload(randomUUID(), { 'index.html': 'valid', 'image.png': tar }); assert.equal((await h.admin('PUT', renamedTar.id, renamedTar)).status, 400);
  for (const name of ['package.json', 'node_modules/a.js', 'lock/package-lock.json', 'service-account.json']) {
    const candidate = payload(randomUUID(), { 'index.html': 'valid', [name]: 'x' }); assert.equal((await h.admin('PUT', candidate.id, candidate)).status, 400, name);
  }
  const empty = payload(randomUUID(), { 'index.html': '' }); assert.equal((await h.admin('PUT', empty.id, empty)).status, 400);
  const unhelpful = payload(randomUUID(), { 'index.html': 'valid', 'LICENSE': 'short' }); assert.equal((await h.admin('PUT', unhelpful.id, unhelpful)).status, 400);
});

test('chunked oversized upload returns 413 and concurrent upload limit rejects without buffering', async (t) => {
  const h = await harness(t); const id = randomUUID();
  const oversized = await new Promise((resolve, reject) => {
    const req = http.request({ host: 'localhost', port: h.port, method: 'PUT', path: `/deployments/${id}`, agent: false,
      headers: { Host: adminHost, Authorization: `Bearer ${secret}`, 'Content-Type': 'application/json', 'Transfer-Encoding': 'chunked' } }, (res) => {
      res.resume(); res.on('end', () => resolve(res.statusCode));
    }); req.on('error', reject); const chunk = Buffer.alloc(1024 * 1024, 32);
    for (let i = 0; i < 15; i++) req.write(chunk); req.end();
  }); assert.equal(oversized, 413); assert.equal((await h.admin('GET', id)).status, 404);
  const pending = http.request({ host: 'localhost', port: h.port, method: 'PUT', path: `/deployments/${id}`, agent: false,
    headers: { Host: adminHost, Authorization: `Bearer ${secret}`, 'Content-Type': 'application/json', 'Transfer-Encoding': 'chunked' } });
  pending.on('error', () => {}); pending.write('{');
  t.after(() => pending.destroy()); await new Promise((resolve) => setTimeout(resolve, 20));
  const candidate = payload(); assert.equal((await h.admin('PUT', candidate.id, candidate)).status, 503); pending.destroy();
});

test('standalone process rejects a second writer and recovers abrupt crash with persistent identity/tombstone', async (t) => {
  const h = await harness(t); await h.stop();
  const probe = http.createServer(); await new Promise((resolve) => probe.listen(0, 'localhost', resolve)); const port = probe.address().port;
  await new Promise((resolve) => probe.close(resolve));
  const env = { ...process.env, DEMO_PUBLISHER_DATA_DIR: h.dataDir, DEMO_PUBLISHER_SECRET: secret, DEMO_PUBLISHER_ADMIN_HOST: adminHost,
    DEMO_PUBLIC_URL_TEMPLATE: h.config.publicUrlTemplate, DEMO_GATEWAY_URL: h.config.gatewayUrl, DEMO_GATEWAY_SECRET: gatewaySecret,
    DEMO_PUBLISHER_HOST: 'localhost', DEMO_PUBLISHER_PORT: String(port) };
  const children = []; const launch = () => { const child = spawn(process.execPath, ['ops/demo-publisher/server.mjs'], { cwd: process.cwd(), env, stdio: ['ignore', 'pipe', 'pipe'] }); children.push(child); return child; };
  t.after(async () => { for (const child of children) if (child.exitCode === null && child.signalCode === null) { child.kill('SIGKILL'); await new Promise((resolve) => child.once('exit', resolve)); } });
  const ready = (child) => new Promise((resolve, reject) => {
    let output = ''; const timer = setTimeout(() => reject(new Error('Standalone startup timeout')), 5000);
    child.stderr.on('data', (chunk) => { output += chunk; }); child.on('error', reject);
    child.once('exit', () => { clearTimeout(timer); reject(new Error(`Standalone exited: ${output}`)); });
    child.stdout.on('data', (chunk) => { if (String(chunk).includes('listening on')) { clearTimeout(timer); resolve(); } });
  });
  const stop = async (child, signal) => { const exited = new Promise((resolve) => child.once('exit', resolve)); child.kill(signal); await exited; };
  const first = launch(); await ready(first); assert.equal((await request(port)).status, 200);
  const p = payload(); assert.equal((await request(port, { method: 'PUT', target: `/deployments/${p.id}`, admin: true, body: p })).status, 200);
  const duplicate = launch(); let duplicateError = ''; duplicate.stderr.on('data', (chunk) => { duplicateError += chunk; });
  const duplicateExit = await new Promise((resolve) => duplicate.once('exit', resolve)); assert.equal(duplicateExit, 1); assert.match(duplicateError, /already running/);
  await stop(first, 'SIGKILL'); const second = launch(); await ready(second);
  assert.equal((await request(port, { target: `/deployments/${p.id}`, admin: true })).json.state, 'READY');
  assert.equal((await request(port, { host: `${p.id}.demo.localhost:8090`, target: '/' })).status, 200);
  assert.equal((await request(port, { method: 'DELETE', target: `/deployments/${p.id}`, admin: true })).json.state, 'DELETED');
  await stop(second, 'SIGTERM'); const third = launch(); await ready(third);
  assert.equal((await request(port, { method: 'PUT', target: `/deployments/${p.id}`, admin: true, body: p })).status, 409);
  assert.equal((await request(port, { host: `${p.id}.demo.localhost:8090`, target: '/' })).status, 404); await stop(third, 'SIGTERM');
});

test('full identity capacity has durable bounded absence settlement; in-flight PUT cannot bypass admission fence after restart/cap increase', async (t) => {
  const h = await harness(t, { maxIdentities: 1 }); const existing = payload(); const absent = payload();
  assert.equal((await h.admin('PUT', existing.id, existing)).status, 200);
  assert.equal((await h.admin('PUT', absent.id, absent)).status, 507); assert.equal((await h.admin('GET', absent.id)).status, 404);
  const bytes = Buffer.from(JSON.stringify(absent)); let pendingRequest;
  const inFlight = new Promise((resolve, reject) => {
    pendingRequest = http.request({ host: 'localhost', port: h.port, method: 'PUT', path: `/deployments/${absent.id}`, agent: false,
      headers: { Host: adminHost, Authorization: `Bearer ${secret}`, 'Content-Type': 'application/json', 'Content-Length': bytes.length } }, (res) => {
      res.resume(); res.on('end', () => resolve(res.statusCode));
    }); pendingRequest.on('error', reject); pendingRequest.write(bytes.subarray(0, 1));
  }); t.after(() => pendingRequest.destroy());
  await new Promise((resolve) => setTimeout(resolve, 20));
  const tombstone = { id: absent.id, state: 'DELETED' };
  assert.deepEqual((await h.admin('DELETE', absent.id)).json, tombstone);
  assert.deepEqual(JSON.parse(await fs.readFile(path.join(h.dataDir, 'admission-closed.json'), 'utf8')), { state: 'CLOSED' });
  assert.equal((await fs.readdir(path.join(h.dataDir, 'identities'))).length, 1);
  pendingRequest.end(bytes.subarray(1)); assert.equal(await inFlight, 409);
  assert.deepEqual((await h.admin('GET', absent.id)).json, tombstone); assert.deepEqual((await h.admin('DELETE', absent.id)).json, tombstone);
  h.config.maxIdentities = HARD_LIMITS.identities; await h.restart();
  assert.deepEqual((await h.admin('GET', absent.id)).json, tombstone); assert.equal((await h.admin('PUT', absent.id, absent)).status, 409);
  assert.equal((await h.admin('PUT', existing.id, existing)).status, 200); assert.equal((await h.public(existing.id)).status, 200);
  const randomAbsent = payload(); assert.equal((await h.admin('PUT', randomAbsent.id, randomAbsent)).status, 409);
  assert.deepEqual((await h.admin('DELETE', randomAbsent.id)).json, { id: randomAbsent.id, state: 'DELETED' });
  await h.admin('DELETE', existing.id); await h.restart(); assert.equal((await h.admin('PUT', randomAbsent.id, randomAbsent)).status, 409);
  assert.equal((await fs.readdir(path.join(h.dataDir, 'identities'))).length, 1);
  await h.stop(); await fs.writeFile(path.join(h.dataDir, 'admission-closed.json'), '{"state":"OPEN"}');
  await assert.rejects(createPublisher(h.config), /admission fence/);
  await fs.writeFile(path.join(h.dataDir, 'admission-closed.json'), '{"state":"CLOSED"}'); await h.restart();
});

test('startup seals full legacy identities before accepting requests without allocating extra tombstones', async (t) => {
  const h = await harness(t, { maxIdentities: 1 }); const existing = payload(); await h.admin('PUT', existing.id, existing);
  assert.equal(await fs.access(path.join(h.dataDir, 'admission-closed.json')).then(() => true, () => false), false);
  await h.restart(); const absent = payload(); assert.deepEqual((await h.admin('GET', absent.id)).json, { id: absent.id, state: 'DELETED' });
  assert.equal((await h.admin('PUT', absent.id, absent)).status, 409); assert.equal((await h.admin('PUT', existing.id, existing)).status, 200);
  assert.equal((await fs.readdir(path.join(h.dataDir, 'identities'))).length, 1);
});
