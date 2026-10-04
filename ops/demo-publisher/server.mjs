import http from 'node:http';
import https from 'node:https';
import { constants } from 'node:fs';
import * as fs from 'node:fs/promises';
import path from 'node:path';
import { createHash, randomUUID, timingSafeEqual } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';

export const HARD_LIMITS = Object.freeze({ fileBytes: 5 * 1024 * 1024, expandedBytes: 10 * 1024 * 1024,
  files: 256, storageBytes: 500 * 1024 * 1024, identities: 1000, requestsPerMinute: 240,
  expiryMs: 30 * 86400_000, bodyBytes: 14 * 1024 * 1024 });
export const CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; connect-src 'self'; object-src 'none'; frame-src 'none'; worker-src 'none'; base-uri 'none'; form-action 'none'; sandbox allow-scripts allow-same-origin; frame-ancestors 'none'";
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const HASH = /^[0-9a-f]{64}$/;
const SECRET_MARKER = /-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----|\bAKIA[A-Z0-9]{16}\b|\b(?:ghp_|github_pat_)[A-Za-z0-9_]{20,}\b|\brzp_live_[A-Za-z0-9]{8,}\b/i;
const TEXT_DECODER = new TextDecoder('utf-8', { fatal: true });
const TYPES = Object.freeze({ html: 'text/html; charset=utf-8', css: 'text/css; charset=utf-8', js: 'text/javascript; charset=utf-8',
  json: 'application/json', png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', webp: 'image/webp', gif: 'image/gif',
  svg: 'image/svg+xml', ico: 'image/vnd.microsoft.icon', woff: 'font/woff', woff2: 'font/woff2', ttf: 'font/ttf',
  txt: 'text/plain; charset=utf-8', webmanifest: 'application/manifest+json' });
const openedDirectories = new Set();
const sha = (bytes) => createHash('sha256').update(bytes).digest('hex');
class HttpError extends Error { constructor(status, message) { super(message); this.status = status; } }
function requireValue(condition, message, status = 400) { if (!condition) throw new HttpError(status, message); }
function exactKeys(object, keys) { return object && typeof object === 'object' && !Array.isArray(object)
  && Object.keys(object).length === keys.length && keys.every((key) => Object.hasOwn(object, key)); }
function boundedInteger(value, fallback, max, name) {
  const number = value === undefined || value === '' ? fallback : Number(value);
  if (!Number.isSafeInteger(number) || number < 1 || number > max) throw new Error(`${name} must be an integer in 1..${max}`);
  return number;
}
function validTime(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?Z$/.test(value)) return false;
  const time = Date.parse(value);
  return Number.isFinite(time) && new Date(time).toISOString().replace(/\.000Z$/, 'Z')
    === value.replace(/\.(\d{1,3})Z$/, (_, fraction) => `.${fraction.padEnd(3, '0')}Z`).replace(/\.000Z$/, 'Z');
}
export function validateFilePath(value) {
  requireValue(typeof value === 'string' && value.length > 0 && value.length <= 240 && /^[A-Za-z0-9._/-]+$/.test(value), 'Invalid file path');
  const parts = value.split('/');
  requireValue(parts.every((part) => part.length > 0 && !part.startsWith('.') && part !== '..'), 'Invalid file path');
  requireValue(!parts.some((part) => /(?:^|[._-])(?:secrets?|credentials?|private|passwords?|api[_-]?keys?)(?:[._-]|$)/i.test(part)), 'Private file name');
  requireValue(!parts.some((part, index) => /(?:^|[._-])tokens?(?:[._-]|$)/i.test(part)
    && !(index === parts.length - 1 && /\.css$/i.test(part))), 'Private token file name');
  requireValue(!/(?:credentials|secret-key|private-key|service-account|id_rsa|id_ed25519|node_modules|package-lock|yarn.lock|pnpm-lock)/i.test(value)
    && parts.at(-1).toLowerCase() !== 'package.json', 'Private or source package file');
  const extension = value.split('.').at(-1).toLowerCase();
  const notice = /^(?:README|LICEN[CS]E)(?:\.(?:md|txt))?$/i.test(parts.at(-1));
  requireValue(Object.hasOwn(TYPES, extension) || notice, 'Unsupported file type');
  return notice ? 'text/plain; charset=utf-8' : TYPES[extension];
}
function containsSecret(text) {
  if (SECRET_MARKER.test(text)) return true;
  // Bound key length and require a token boundary: no quadratic search over a 5 MiB minified asset.
  const assignments = /(?:^|[\s;{,])["']?([A-Za-z0-9_]{1,128})["']?[ \t]*[:=][ \t]*["']([A-Za-z0-9+/=_-]{16,})["']/g;
  for (const match of text.matchAll(assignments)) if (/SECRET|PRIVATE_?KEY|PASSWORD|ACCESS_?TOKEN|API_?KEY/i.test(match[1])) return true;
  return false;
}
function inspectBytes(filename, bytes) {
  const header = bytes.subarray(0, 8).toString('hex');
  requireValue(!['4d5a', '7f454c46', '504b0304', '504b0506', '504b0708', '1f8b', '377abcaf271c', '526172211a07',
    '425a68', 'fd377a585a00', 'cafebabe', 'bebafeca', 'feedface', 'cefaedfe', 'feedfacf', 'cffaedfe'].some((magic) => header.startsWith(magic)), 'Executable or nested archive content');
  requireValue(!(bytes.length >= 262 && bytes.subarray(257, 262).toString('ascii') === 'ustar'), 'Nested TAR archive content');
  requireValue(!containsSecret(bytes.toString('latin1')), 'Credential marker in content');
  const notice = /^(?:README|LICEN[CS]E)(?:\.(?:md|txt))?$/i.test(filename.split('/').at(-1));
  if (notice || /\.(?:html|css|js|json|svg|txt|webmanifest)$/i.test(filename)) {
    let text; try { text = TEXT_DECODER.decode(bytes); } catch { throw new HttpError(400, 'Static text must be valid UTF-8'); }
    requireValue(!text.includes('\0') && !containsSecret(text), 'Invalid static text or credential marker');
    if (notice) requireValue(text.trim().length >= 20, 'A useful notice is required');
  }
}
export function manifestHash(files) { return sha(Buffer.from(files.map((file) => `${file.path}\0${file.sha256}\0${file.sizeBytes}\n`).join(''), 'utf8')); }
function validateFiles(files, content) {
  requireValue(Array.isArray(files) && files.length > 0 && files.length <= HARD_LIMITS.files, 'Invalid file count');
  const cases = new Set(); const prefixCases = new Map(); let previous = ''; let size = 0;
  const result = files.map((file) => {
    requireValue(exactKeys(file, content ? ['path', 'sha256', 'sizeBytes', 'contentBase64'] : ['path', 'sha256', 'sizeBytes']), 'Invalid file fields');
    validateFilePath(file.path);
    requireValue(file.path > previous && !cases.has(file.path.toLowerCase()), 'Files must be sorted and unique');
    previous = file.path; cases.add(file.path.toLowerCase());
    const parts = file.path.split('/');
    for (let index = 1; index <= parts.length; index++) {
      const prefix = parts.slice(0, index).join('/'); const prior = prefixCases.get(prefix.toLowerCase());
      requireValue(prior === undefined || prior === prefix, 'Case-insensitive path collision'); prefixCases.set(prefix.toLowerCase(), prefix);
    }
    requireValue(typeof file.sha256 === 'string' && HASH.test(file.sha256), 'Invalid file hash');
    requireValue(Number.isSafeInteger(file.sizeBytes) && file.sizeBytes >= 0 && file.sizeBytes <= HARD_LIMITS.fileBytes, 'Invalid file size', 413);
    size += file.sizeBytes; requireValue(size <= HARD_LIMITS.expandedBytes, 'Expanded package too large', 413);
    if (!content) return { ...file };
    requireValue(typeof file.contentBase64 === 'string' && file.contentBase64.length === Math.ceil(file.sizeBytes / 3) * 4
      && /^[A-Za-z0-9+/]*={0,2}$/.test(file.contentBase64), 'Invalid base64');
    const bytes = Buffer.from(file.contentBase64, 'base64');
    requireValue(bytes.length === file.sizeBytes && bytes.toString('base64') === file.contentBase64 && sha(bytes) === file.sha256, 'File hash or length mismatch');
    inspectBytes(file.path, bytes);
    return { path: file.path, sha256: file.sha256, sizeBytes: file.sizeBytes, bytes };
  });
  requireValue(cases.has('index.html') && files.some((file) => file.path === 'index.html' && file.sizeBytes > 0), 'Nonempty root index.html required');
  // A file cannot also be a directory, including case-insensitive parent collisions.
  for (const file of result) {
    const segments = file.path.toLowerCase().split('/');
    for (let index = 1; index < segments.length; index++) requireValue(!cases.has(segments.slice(0, index).join('/')), 'File/directory collision');
  }
  return { files: result, size };
}
function validatePayload(body, id) {
  requireValue(exactKeys(body, ['id', 'archiveSha256', 'manifestSha256', 'expiresAt', 'files']), 'Invalid publication fields');
  requireValue(body.id === id && UUID.test(body.id), 'Deployment id mismatch');
  requireValue(typeof body.archiveSha256 === 'string' && HASH.test(body.archiveSha256)
    && typeof body.manifestSha256 === 'string' && HASH.test(body.manifestSha256), 'Invalid package hashes');
  requireValue(validTime(body.expiresAt), 'Invalid expiry');
  const result = validateFiles(body.files, true);
  requireValue(manifestHash(result.files) === body.manifestSha256, 'Manifest hash mismatch');
  return { ...result, archiveSha256: body.archiveSha256, manifestSha256: body.manifestSha256, expiresAt: body.expiresAt };
}
function localHttp(hostname, internal) {
  return hostname === 'localhost' || hostname.endsWith('.localhost') || (internal && /^[a-z][a-z0-9-]*$/.test(hostname));
}
function configuredUrl(value, name, internal) {
  let url; try { url = new URL(value); } catch { throw new Error(`${name} must be a fixed URL`); }
  if (url.username || url.password || url.search || url.hash || !['http:', 'https:'].includes(url.protocol)
    || (url.protocol === 'http:' && !localHttp(url.hostname, internal))) throw new Error(`${name} requires HTTPS (HTTP only localhost or internal Docker)`);
  return url;
}
export function configFromEnv(env = process.env) {
  return { dataDir: env.DEMO_PUBLISHER_DATA_DIR, secret: env.DEMO_PUBLISHER_SECRET,
    adminHost: env.DEMO_PUBLISHER_ADMIN_HOST, publicUrlTemplate: env.DEMO_PUBLIC_URL_TEMPLATE,
    gatewayUrl: env.DEMO_GATEWAY_URL, gatewaySecret: env.DEMO_GATEWAY_SECRET,
    host: env.DEMO_PUBLISHER_HOST || '0.0.0.0', port: boundedInteger(env.DEMO_PUBLISHER_PORT, 8081, 65535, 'port'),
    maxBytes: env.DEMO_PUBLISHER_MAX_BYTES, maxIdentities: env.DEMO_PUBLISHER_MAX_IDENTITIES,
    requestsPerMinute: env.DEMO_PUBLISHER_REQUESTS_PER_MINUTE };
}
function configure(input) {
  if (typeof input.secret !== 'string' || input.secret.length < 32 || /[^\x21-\x7e]/.test(input.secret)
    || typeof input.gatewaySecret !== 'string' || input.gatewaySecret.length < 32 || /[^\x21-\x7e]/.test(input.gatewaySecret)) throw new Error('Independent printable publisher and gateway secrets (at least 32 characters) required');
  if (input.secret === input.gatewaySecret) throw new Error('Publisher and gateway secrets must differ');
  if (typeof input.adminHost !== 'string' || !/^[a-z0-9][a-z0-9.-]*(?::[1-9][0-9]{0,4})?$/.test(input.adminHost)) throw new Error('Exact lowercase adminHost required');
  if (typeof input.dataDir !== 'string' || !path.isAbsolute(input.dataDir)) throw new Error('Absolute dataDir required');
  if (typeof input.publicUrlTemplate !== 'string' || !/^https?:\/\/\{id\}\./.test(input.publicUrlTemplate)
    || input.publicUrlTemplate.split('{id}').length !== 2) throw new Error('publicUrlTemplate must have one {id} as the first hostname label');
  const sample = '11111111-1111-4111-8111-111111111111';
  const publicUrl = configuredUrl(input.publicUrlTemplate.replace('{id}', sample), 'publicUrlTemplate', false);
  const publicSuffix = publicUrl.host.slice(sample.length);
  if (publicUrl.pathname !== '/' || (input.adminHost.endsWith(publicSuffix)
    && UUID.test(input.adminHost.slice(0, -publicSuffix.length)))) throw new Error('Admin Host must never match a public deployment origin');
  const gateway = configuredUrl(input.gatewayUrl, 'gatewayUrl', true);
  if (!gateway.pathname.endsWith('/api/v1/hosting/gateway')) throw new Error('gatewayUrl must end with /api/v1/hosting/gateway');
  return { ...input, gateway, publicSuffix, publicUrlTemplate: publicUrl.origin.replace(sample, '{id}'),
    maxBytes: boundedInteger(input.maxBytes, HARD_LIMITS.storageBytes, HARD_LIMITS.storageBytes, 'maxBytes'),
    maxIdentities: boundedInteger(input.maxIdentities, HARD_LIMITS.identities, HARD_LIMITS.identities, 'maxIdentities'),
    requestsPerMinute: boundedInteger(input.requestsPerMinute, HARD_LIMITS.requestsPerMinute, HARD_LIMITS.requestsPerMinute, 'requestsPerMinute'),
    now: input.now || Date.now };
}
async function syncDirectory(directory) { const file = await fs.open(directory, 'r'); try { await file.sync(); } finally { await file.close(); } }
async function writeSynced(filename, value) {
  const file = await fs.open(filename, 'wx', 0o600);
  try { await file.writeFile(value); await file.sync(); } finally { await file.close(); }
}
async function plainDirectory(directory) {
  const info = await fs.lstat(directory); if (!info.isDirectory() || info.isSymbolicLink()) throw new Error('Unsafe publisher directory');
}
async function processIdentity(pid) {
  // Linux Docker deployment: the kernel start tick distinguishes PID reuse after a crash/restart.
  try {
    const stat = await fs.readFile(`/proc/${pid}/stat`, 'utf8');
    return `${(await fs.readFile('/proc/sys/kernel/random/boot_id', 'utf8')).trim()}:${stat.slice(stat.lastIndexOf(')') + 2).split(' ')[19]}`;
  } catch (error) {
    if (error.code === 'ENOENT') return null;
    throw error;
  }
}
async function acquireKernelWriter(root) {
  // ponytail: Linux flock covers one local volume; replicas need a different storage architecture.
  const file = await fs.open(path.join(root, 'writer.flock'), constants.O_RDWR | constants.O_CREAT | constants.O_NOFOLLOW, 0o600);
  let child; let exited;
  try {
    if (!(await file.stat()).isFile()) throw new Error('Invalid publisher kernel lock file');
    // flock locks the inherited open file description. Node retains it after the utility exits.
    // Never unlink this inode: close or parent process death releases the kernel lock.
    child = spawn('flock', ['-n', '-E', '75', '3'], { stdio: ['ignore', 'ignore', 'ignore', file.fd] });
    exited = new Promise(resolve => child.once('close', code => resolve(code)));
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => { child.kill('SIGKILL'); reject(new Error('Publisher kernel lock startup timeout')); }, 5000);
      child.once('error', error => { clearTimeout(timer); reject(error); });
      exited.then(code => {
        clearTimeout(timer);
        if (code === 0) resolve();
        else reject(new Error(code === 75 ? 'Publisher writer already running' : 'Publisher kernel lock unavailable'));
      });
    });
    return () => file.close();
  } catch (error) { if (child) { child.kill('SIGKILL'); await exited; } await file.close(); throw error; }
}
async function acquireWriter(root) {
  const releaseKernel = await acquireKernelWriter(root);
  try {
    const filename = path.join(root, 'writer.lock');
    const selfPid = Number((await fs.readFile('/proc/self/stat', 'utf8')).split(' ')[0]);
    const owner = { pid: selfPid, identity: await processIdentity(selfPid), token: randomUUID() };
    if (!owner.identity) throw new Error('Linux /proc publisher process identity required');
    try { await writeSynced(filename, JSON.stringify(owner)); }
    catch (error) {
      if (error.code !== 'EEXIST') throw error;
      const previous = JSON.parse((await plainRead(root, 'writer.lock', 4096)).toString('utf8'));
      if (!exactKeys(previous, ['pid', 'identity', 'token']) || !Number.isSafeInteger(previous.pid) || previous.pid < 1
        || typeof previous.identity !== 'string' || typeof previous.token !== 'string') throw new Error('Invalid publisher writer lease');
      if (await processIdentity(previous.pid) === previous.identity) throw new Error('Publisher writer already running');
      await fs.unlink(filename); await writeSynced(filename, JSON.stringify(owner));
    }
    await syncDirectory(root);
    return async () => {
      try {
        const saved = JSON.parse((await plainRead(root, 'writer.lock', 4096)).toString('utf8'));
        if (saved.token !== owner.token) throw new Error('Publisher writer lease changed');
        await fs.unlink(filename); await syncDirectory(root);
      } finally { await releaseKernel(); }
    };
  } catch (error) { await releaseKernel(); throw error; }
}
async function plainRead(root, relative, maximum) {
  let current = root;
  for (const part of relative.split('/').slice(0, -1)) { current = path.join(current, part); await plainDirectory(current); }
  const handle = await fs.open(path.join(root, relative), constants.O_RDONLY | constants.O_NOFOLLOW);
  try {
    const stat = await handle.stat(); if (!stat.isFile() || stat.size > maximum) throw new Error('Invalid stored file');
    const bytes = await handle.readFile(); if (bytes.length > maximum) throw new Error('Invalid stored file'); return bytes;
  } finally { await handle.close(); }
}
function publicHeaders(response) {
  response.setHeader('Content-Security-Policy', CONTENT_SECURITY_POLICY);
  response.setHeader('X-Content-Type-Options', 'nosniff'); response.setHeader('Referrer-Policy', 'no-referrer');
  response.setHeader('Permissions-Policy', 'camera=(), microphone=(), geolocation=(), accelerometer=(), gyroscope=(), magnetometer=(), usb=(), payment=()');
  response.setHeader('Cache-Control', 'no-store'); response.setHeader('Cross-Origin-Opener-Policy', 'same-origin');
  response.setHeader('Cross-Origin-Resource-Policy', 'same-origin'); response.setHeader('X-Frame-Options', 'DENY');
  response.setHeader('Origin-Agent-Cluster', '?1');
}
function json(response, status, value, head = false) {
  const bytes = Buffer.from(JSON.stringify(value)); response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': bytes.length });
  response.end(head ? undefined : bytes);
}
function matchesSecret(value, secret) {
  const actual = Buffer.from(typeof value === 'string' ? value : ''); const expected = Buffer.from(`Bearer ${secret}`);
  return actual.length === expected.length && timingSafeEqual(actual, expected);
}
async function readBody(request) {
  requireValue(request.headers['content-type']?.split(';')[0].trim().toLowerCase() === 'application/json', 'JSON required', 415);
  requireValue(!request.headers['content-encoding'], 'Encoded request body unsupported', 415);
  const length = request.headers['content-length'];
  requireValue(length === undefined || (/^\d+$/.test(length) && Number(length) <= HARD_LIMITS.bodyBytes), 'Request body too large', 413);
  return new Promise((resolve, reject) => {
    let size = 0; let finished = false; const chunks = [];
    const done = (error, value) => {
      if (finished) return; finished = true; clearTimeout(timer);
      request.removeListener('data', data); request.removeListener('end', end);
      if (error) { request.pause(); reject(error); } else resolve(value);
    };
    const data = (chunk) => {
      size += chunk.length;
      if (size > HARD_LIMITS.bodyBytes) done(new HttpError(413, 'Request body too large')); else chunks.push(chunk);
    };
    const end = () => { try { done(null, JSON.parse(Buffer.concat(chunks).toString('utf8'))); } catch { done(new HttpError(400, 'Malformed JSON')); } };
    const timer = setTimeout(() => done(new HttpError(408, 'Upload timeout')), 10_000);
    request.on('data', data); request.once('end', end); request.once('error', () => done(new HttpError(400, 'Incomplete request body')));
    request.once('aborted', () => done(new HttpError(400, 'Incomplete request body')));
  });
}
function gatewayAllows(config, id, signal) {
  return new Promise((resolve) => {
    let finished = false; const done = (value) => {
      if (finished) return; finished = true; clearTimeout(timer); signal.removeEventListener('abort', cancel); request.destroy(); resolve(value);
    };
    const cancel = () => done(false);
    const url = new URL(`${config.gateway.href}/${id}`);
    const client = url.protocol === 'https:' ? https : http;
    const request = client.request(url, { method: 'GET', headers: { 'X-GetLancer-Demo-Gateway': config.gatewaySecret, Accept: 'application/json' }, agent: false }, (response) => {
      response.on('error', () => done(false));
      if (response.statusCode !== 200 || response.headers['content-type']?.split(';')[0].trim().toLowerCase() !== 'application/json') { done(false); return; }
      let size = 0; const chunks = [];
      response.on('data', (chunk) => { size += chunk.length; if (size > 4096) done(false); else chunks.push(chunk); });
      response.on('end', () => { try { const body = JSON.parse(Buffer.concat(chunks).toString('utf8')); done(exactKeys(body, ['allowed']) && body.allowed === true); } catch { done(false); } });
    });
    const timer = setTimeout(() => done(false), 2000);
    request.on('error', () => done(false)); signal.addEventListener('abort', cancel, { once: true });
    if (signal.aborted) cancel(); else request.end();
  });
}

/** One process, one private persistent volume; production supplies no test clock. */
export async function createPublisher(input) {
  const config = configure(input); await fs.mkdir(config.dataDir, { recursive: true, mode: 0o700 }); await plainDirectory(config.dataDir);
  const root = await fs.realpath(config.dataDir);
  if (openedDirectories.has(root)) throw new Error('Publisher data directory already open');
  openedDirectories.add(root);
  let releaseWriter; let unhealthy = false;
  try { releaseWriter = await acquireWriter(root); } catch (error) { openedDirectories.delete(root); throw error; }
  const identities = path.join(root, 'identities'); const staging = path.join(root, 'staging');
  const records = new Map(); let totalBytes = 0; let pending = Promise.resolve(); let activeUploads = 0; let activePublic = 0; let admissionClosed = false;
  const serialized = (fn) => { const result = pending.then(() => {
    requireValue(!unhealthy, 'Publisher storage unavailable', 503); return fn();
  }).catch((error) => {
    // A persistence failure may occur after an atomic rename. Stop serving/mutating until a verified restart.
    if (!(error instanceof HttpError)) unhealthy = true;
    throw error;
  }); pending = result.catch(() => {}); return result; };
  const urlFor = (id) => config.publicUrlTemplate.replace('{id}', id);
  const metadata = (record) => ({ id: record.id, state: record.state, ...(record.archiveSha256 ? {
    archiveSha256: record.archiveSha256, manifestSha256: record.manifestSha256, expiresAt: record.expiresAt, url: record.url } : {}) });
  const unknownTombstone = (id) => ({ id, state: 'DELETED', sizeBytes: 0, files: [] });
  async function closeAdmission() {
    if (admissionClosed) return;
    const temporary = path.join(root, `admission-${randomUUID()}.tmp`);
    await writeSynced(temporary, JSON.stringify({ state: 'CLOSED' }));
    await fs.rename(temporary, path.join(root, 'admission-closed.json')); await syncDirectory(root);
    admissionClosed = true;
  }
  async function commitNew(record, files = []) {
    const temporary = path.join(staging, randomUUID()); await fs.mkdir(temporary, { mode: 0o700 });
    try {
      if (files.length) {
        const bundle = path.join(temporary, 'bundle'); await fs.mkdir(bundle, { mode: 0o700 }); const directories = new Set([bundle]);
        for (const file of files) {
          const filename = path.join(bundle, file.path); await fs.mkdir(path.dirname(filename), { recursive: true, mode: 0o700 });
          await writeSynced(filename, file.bytes);
          let directory = path.dirname(filename); while (directory.startsWith(bundle)) { directories.add(directory); if (directory === bundle) break; directory = path.dirname(directory); }
        }
        for (const directory of [...directories].sort((a, b) => b.length - a.length)) await syncDirectory(directory);
      }
      await writeSynced(path.join(temporary, 'record.json'), JSON.stringify(record)); await syncDirectory(temporary);
      await fs.rename(temporary, path.join(identities, record.id)); await syncDirectory(identities);
      records.set(record.id, record); totalBytes += record.sizeBytes;
    } finally { await fs.rm(temporary, { recursive: true, force: true }); }
  }
  async function remove(record) {
    if (record.state === 'DELETED') return record;
    const deleted = { ...record, state: 'DELETED', sizeBytes: 0, files: [] };
    const directory = path.join(identities, record.id); const temporary = path.join(directory, `record-${randomUUID()}.tmp`);
    await writeSynced(temporary, JSON.stringify(deleted)); await fs.rename(temporary, path.join(directory, 'record.json')); await syncDirectory(directory);
    records.set(record.id, deleted); totalBytes -= record.sizeBytes;
    await fs.rm(path.join(directory, 'bundle'), { recursive: true, force: true }); await syncDirectory(directory);
    return deleted;
  }
  async function expire() { for (const record of records.values()) if (record.state === 'READY' && Date.parse(record.expiresAt) <= config.now()) await remove(record); }
  try {
    await fs.mkdir(identities, { mode: 0o700 });
  } catch (error) { if (error.code !== 'EEXIST') { await releaseWriter(); openedDirectories.delete(root); throw error; } }
  try {
    await plainDirectory(identities); await fs.mkdir(staging, { recursive: true, mode: 0o700 }); await plainDirectory(staging);
    try {
      const fence = JSON.parse((await plainRead(root, 'admission-closed.json', 4096)).toString('utf8'));
      if (!exactKeys(fence, ['state']) || fence.state !== 'CLOSED') throw new Error('Invalid permanent admission fence');
      admissionClosed = true;
    } catch (error) { if (error.code !== 'ENOENT') throw error; }
    for (const name of await fs.readdir(root)) if (/^admission-[0-9a-f-]+\.tmp$/.test(name)) await fs.rm(path.join(root, name), { force: true });
    // Private single-writer volume: orphan unpublished staging trees are safe to discard.
    for (const name of await fs.readdir(staging)) await fs.rm(path.join(staging, name), { recursive: true, force: true });
    for (const id of await fs.readdir(identities)) {
      if (!UUID.test(id)) throw new Error('Invalid stored identity'); const directory = path.join(identities, id); await plainDirectory(directory);
      const record = JSON.parse((await plainRead(directory, 'record.json', 256 * 1024)).toString('utf8'));
      if (!exactKeys(record, record.archiveSha256 ? ['id', 'state', 'archiveSha256', 'manifestSha256', 'expiresAt', 'url', 'sizeBytes', 'files'] : ['id', 'state', 'sizeBytes', 'files'])
        || record.id !== id || !['READY', 'DELETED'].includes(record.state)) throw new Error('Invalid stored metadata');
      if (record.archiveSha256 && (!HASH.test(record.archiveSha256) || !HASH.test(record.manifestSha256) || !validTime(record.expiresAt) || record.url !== urlFor(id))) throw new Error('Stored configuration or identity mismatch');
      if (record.state === 'READY') {
        if (!record.archiveSha256) throw new Error('Missing stored identity'); const checked = validateFiles(record.files, false);
        if (checked.size !== record.sizeBytes || manifestHash(checked.files) !== record.manifestSha256) throw new Error('Invalid stored manifest');
        await plainDirectory(path.join(directory, 'bundle'));
        for (const file of record.files) { const bytes = await plainRead(path.join(directory, 'bundle'), file.path, HARD_LIMITS.fileBytes);
          if (bytes.length !== file.sizeBytes || sha(bytes) !== file.sha256) throw new Error('Stored content integrity failure'); inspectBytes(file.path, bytes); }
        totalBytes += record.sizeBytes;
      } else {
        if (record.sizeBytes !== 0 || !Array.isArray(record.files) || record.files.length) throw new Error('Invalid tombstone');
        await fs.rm(path.join(directory, 'bundle'), { recursive: true, force: true });
      }
      records.set(id, record);
    }
    await expire(); if (records.size > config.maxIdentities || totalBytes > config.maxBytes) throw new Error('Stored data exceeds configured capacity');
    if (records.size === config.maxIdentities) await closeAdmission();
  } catch (error) { await releaseWriter(); openedDirectories.delete(root); throw error; }
  const traffic = new Map();
  const server = http.createServer({ maxHeaderSize: 8192, requestTimeout: 10_000, headersTimeout: 5000, keepAliveTimeout: 1000 }, async (request, response) => {
    publicHeaders(response); const head = request.method === 'HEAD';
    try {
      requireValue(!unhealthy, 'Publisher storage unavailable', 503);
      const hostHeaders = request.rawHeaders.filter((_, index) => index % 2 === 0).filter((name) => name.toLowerCase() === 'host');
      requireValue(hostHeaders.length === 1 && typeof request.headers.host === 'string', 'Invalid Host', 403);
      const host = request.headers.host.toLowerCase();
      requireValue(request.url.startsWith('/') && !request.url.startsWith('//') && request.url.length <= 2048, 'Invalid request target');
      const target = request.url.split('?')[0];
      if (host === config.adminHost) {
        if (target === '/health' && request.method === 'GET') { json(response, 200, { status: 'ok' }); return; }
        requireValue(matchesSecret(request.headers.authorization, config.secret), 'Unauthorized', 401);
        const match = /^\/deployments\/([^/]+)$/.exec(target);
        requireValue(match && UUID.test(match[1]), 'Not found', 404); const id = match[1];
        if (request.method === 'PUT') {
          requireValue(activeUploads < 1, 'Upload concurrency limit', 503); activeUploads++;
          try {
            const body = await readBody(request); const checked = validatePayload(body, id);
            const result = await serialized(async () => {
              await expire(); const existing = records.get(id);
              if (existing) {
                requireValue(existing.state !== 'DELETED', 'Deployment permanently withdrawn', 409);
                requireValue(existing.archiveSha256 === checked.archiveSha256 && existing.manifestSha256 === checked.manifestSha256
                  && existing.expiresAt === checked.expiresAt, 'Deployment identity conflict', 409); return existing;
              }
              requireValue(!admissionClosed, 'New deployment admission permanently closed', 409);
              const remaining = Date.parse(checked.expiresAt) - config.now();
              requireValue(remaining > 0 && remaining <= HARD_LIMITS.expiryMs, 'Expiry outside permitted lifetime');
              requireValue(records.size < config.maxIdentities && totalBytes + checked.size <= config.maxBytes, 'Publisher capacity reached', 507);
              const record = { id, state: 'READY', archiveSha256: checked.archiveSha256, manifestSha256: checked.manifestSha256,
                expiresAt: checked.expiresAt, url: urlFor(id), sizeBytes: checked.size, files: checked.files.map(({ path: filename, sha256, sizeBytes }) => ({ path: filename, sha256, sizeBytes })) };
              await commitNew(record, checked.files); return record;
            });
            json(response, 200, metadata(result)); return;
          } finally { activeUploads--; }
        }
        if (request.method === 'GET') {
          const result = await serialized(async () => { await expire(); return records.get(id) || (admissionClosed ? unknownTombstone(id) : undefined); });
          requireValue(result, 'Not found', 404); json(response, 200, metadata(result)); return;
        }
        if (request.method === 'DELETE') {
          const result = await serialized(async () => {
            const existing = records.get(id); if (existing) return remove(existing);
            if (admissionClosed) return unknownTombstone(id);
            if (records.size >= config.maxIdentities) { await closeAdmission(); return unknownTombstone(id); }
            const record = { id, state: 'DELETED', sizeBytes: 0, files: [] }; await commitNew(record); return record;
          }); json(response, 200, metadata(result)); return;
        }
        throw new HttpError(405, 'Method not allowed');
      }
      requireValue(!target.startsWith('/deployments') && target !== '/health', 'Administrative endpoint unavailable', 403);
      requireValue(host.endsWith(config.publicSuffix), 'Unknown public host', 403);
      const id = host.slice(0, -config.publicSuffix.length); requireValue(UUID.test(id), 'Unknown public host', 403);
      requireValue(['GET', 'HEAD'].includes(request.method), 'Method not allowed', 405);
      const record = records.get(id); requireValue(record && record.state === 'READY' && Date.parse(record.expiresAt) > config.now(), 'Not found', 404);
      const now = config.now(); let hits = traffic.get(id);
      if (!hits || now - hits.start >= 60_000 || now < hits.start) { hits = { start: now, count: 0 }; traffic.set(id, hits); }
      requireValue(++hits.count <= config.requestsPerMinute, 'Demo request limit reached', 429);
      requireValue(activePublic < 8, 'Public concurrency limit', 503); activePublic++;
      const gatewayCancellation = new AbortController();
      let released = false; let responseDone = false; let workDone = false;
      const deadline = setTimeout(() => response.destroy(), 10_000); deadline.unref();
      const releasePublic = () => {
        if (released || !responseDone || !workDone) return; released = true; activePublic--;
      };
      const responseSettled = () => {
        if (responseDone) return; responseDone = true; gatewayCancellation.abort(); clearTimeout(deadline);
        response.removeListener('finish', responseSettled); response.removeListener('close', responseSettled); releasePublic();
      };
      response.once('finish', responseSettled); response.once('close', responseSettled);
      try {
        // Every recognized static GET/HEAD checks the authority, including missing paths. No forwarded user headers or query.
        requireValue(await gatewayAllows(config, id, gatewayCancellation.signal), 'Demo unavailable', 403);
        if (response.destroyed) return;
        requireValue(!unhealthy, 'Publisher storage unavailable', 503);
        requireValue(records.get(id) === record && record.state === 'READY' && Date.parse(record.expiresAt) > config.now(), 'Not found', 404);
        let filename; try { filename = decodeURIComponent(target); } catch { throw new HttpError(400, 'Invalid encoded path'); }
        requireValue(!/%(?:2f|5c)/i.test(target), 'Encoded separator rejected');
        filename = filename === '/' ? 'index.html' : filename.slice(1); if (filename.endsWith('/')) filename += 'index.html';
        validateFilePath(filename); const file = record.files.find((candidate) => candidate.path === filename); requireValue(file, 'Not found', 404);
        const bytes = await plainRead(path.join(identities, id, 'bundle'), filename, HARD_LIMITS.fileBytes);
        if (response.destroyed) return;
        requireValue(bytes.length === file.sizeBytes && sha(bytes) === file.sha256, 'Stored content unavailable', 503);
        requireValue(records.get(id) === record && Date.parse(record.expiresAt) > config.now(), 'Not found', 404);
        requireValue(!unhealthy, 'Publisher storage unavailable', 503);
        response.writeHead(200, { 'Content-Type': validateFilePath(filename), 'Content-Length': bytes.length }); response.end(head ? undefined : bytes);
      } finally { workDone = true; releasePublic(); }
    } catch (error) {
      if (!response.headersSent && !response.destroyed) {
        if (!request.complete) response.setHeader('Connection', 'close');
        json(response, error instanceof HttpError ? error.status : 503, { error: error instanceof HttpError ? error.message : 'Publisher unavailable' }, head);
      }
    }
  });
  server.maxConnections = 128; server.maxRequestsPerSocket = 100;
  server.on('clientError', (_, socket) => socket.end('HTTP/1.1 400 Bad Request\r\nConnection: close\r\nContent-Length: 0\r\n\r\n'));
  const expiryTimer = setInterval(() => { serialized(expire).catch(() => { unhealthy = true; }); }, 30_000); expiryTimer.unref();
  let closed = false;
  return { server, async listen(port = config.port ?? 0, host = config.host ?? 'localhost') {
    if (closed) throw new Error('Publisher closed');
    await new Promise((resolve, reject) => { server.once('error', reject); server.listen(port, host, () => { server.removeListener('error', reject); resolve(); }); }); return server.address();
  }, async close() { if (closed) return; closed = true; clearInterval(expiryTimer); server.closeAllConnections();
    if (server.listening) await new Promise((resolve) => server.close(resolve)); await pending; await releaseWriter(); openedDirectories.delete(root); } };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const publisher = await createPublisher(configFromEnv()); const address = await publisher.listen();
    process.stdout.write(`Static demo publisher listening on ${address.port}\n`);
    for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => { publisher.close().then(() => process.exit(0), () => process.exit(1)); });
  } catch (error) { process.stderr.write(`Static demo publisher startup failed: ${error.message}\n`); process.exitCode = 1; }
}
