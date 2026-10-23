/** Read-only production acceptance verifier. Supplied operator evidence never enables runtime or payments. */
import assert from 'node:assert/strict';
import {closeSync, constants, fstatSync, openSync, readSync, statfsSync, readFileSync} from 'node:fs';
import {dirname, resolve, isAbsolute} from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
import {isIP} from 'node:net';
import {Resolver} from 'node:dns/promises';
import {connect} from 'node:tls';
import {request} from 'node:http';
import {boundedFile, canonical, exact, hash, integer, openReport, parse, readJournal, sourceTargets, timestamp, verifyReportFile} from '../v52/evidence.mjs';
import {verifiedPayload, verifyBundle, verifyManifestBundle} from '../labs/certify.mjs';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const DAY = 86400000, HASH = /^[a-f0-9]{64}$/, REVISION = /^[a-f0-9]{40}$/, UUID = /^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/;
const SOURCES = {java: 'labs/java/LabServer.java', typescript: 'labs/typescript/server.ts', python: 'labs/python/server.py', go: 'labs/go/server.go', dotnet: 'labs/dotnet/Program.cs', php: 'labs/php/server.php'};
const RUNTIMES = {java: 'jdk', typescript: 'node', python: 'python', go: 'go', dotnet: 'dotnet', php: 'php'};
const COMMON = ['version', 'kind', 'purpose', 'environment', 'revision', 'provider', 'operatorEpoch', 'hostIdSha256', 'observedAt'];
const FILES = ['admission', 'inventory', 'preflight', 'startup', 'loadComponents', 'loadProjects', 'soak', 'handover', 'operations'];
const TRACES = ['isolation', 'query-components', 'query-college-projects', 'recovery', 'merchant', 'human', ...Object.keys(SOURCES).flatMap(language => ['licence-' + language, 'reproduction-' + language])];
const CHECKS = ['linux', 'kvmReadWrite', 'cgroupV2', 'systemd250', 'hostIdentity', 'firecracker', 'jailer', 'kernel'];
const digest = value => assert(typeof value === 'string' && HASH.test(value) && value !== '0'.repeat(64), 'Supply a real nonzero SHA256');
const text = value => assert(typeof value === 'string' && value.length >= 3 && value.length <= 100 && /^[\x20-\x7e]+$/.test(value) && !/^(unknown|unassigned|pending)$/i.test(value), 'Name a responsible owner or provider');
const fresh = (value, now) => {const at = timestamp(value); assert(at <= now && at >= now - DAY, 'Production observations must be current'); return at;};
const normalizeLanguage = value => ['c#', '.net', 'c# / .net'].includes(String(value).toLowerCase()) ? 'dotnet' : String(value).toLowerCase();
export const sourceDigests = () => Object.fromEntries(Object.entries(SOURCES).map(([language, path]) => [language, hash(boundedFile(resolve(ROOT, path), 1024 * 1024))]));

function httpsOrigin(value) {
  const url = new URL(value);
  assert(url.origin === value && url.protocol === 'https:' && !url.username && !url.password && !url.port && !url.search && !url.hash && url.pathname === '/', 'Use an exact public HTTPS origin');
  assert(!isIP(url.hostname) && /^[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?$/.test(url.hostname) && url.hostname.includes('.') && !/(^|\.)(localhost|local|internal|invalid|test|example)$/.test(url.hostname) && !/(^|\.)example\.(com|net|org)$/.test(url.hostname), 'Select an actual production DNS host');
  return value;
}

export function targetPolicy(value) {
  exact(value, ['version', 'environment', 'revision', 'provider', 'operatorEpoch', 'hostIdSha256', 'appOrigin', 'gatewayOrigin', 'providerFiles', 'adapters', 'sources']);
  assert.equal(value.version, 1); assert.equal(value.environment, 'production'); assert(REVISION.test(value.revision)); assert(UUID.test(value.operatorEpoch)); text(value.provider); digest(value.hostIdSha256);
  httpsOrigin(value.appOrigin); httpsOrigin(value.gatewayOrigin); assert.notEqual(value.appOrigin, value.gatewayOrigin, 'Keep the runtime gateway separate');
  exact(value.providerFiles, ['firecracker', 'jailer', 'kernel']);
  for (const pin of Object.values(value.providerFiles)) {exact(pin, ['path', 'sha256']); assert(typeof pin.path === 'string' && isAbsolute(pin.path) && !pin.path.includes('\0') && pin.path.length <= 500); digest(pin.sha256);}
  assert(Array.isArray(value.adapters) && value.adapters.length === 6); const seen = new Set(), current = sourceDigests();
  for (const adapter of value.adapters) {
    exact(adapter, ['language', 'runtime', 'version', 'framework', 'sourceSha256', 'imageDigest']); assert(Object.hasOwn(SOURCES, adapter.language) && !seen.has(adapter.language)); seen.add(adapter.language);
    assert.equal(adapter.runtime, RUNTIMES[adapter.language]); assert(/^\d{1,4}\.\d{1,4}\.\d{1,4}$/.test(adapter.version)); assert.equal(adapter.framework, 'Standard library');
    assert.equal(adapter.sourceSha256, current[adapter.language], 'Runtime source pin differs from this checkout'); assert(/^sha256:[a-f0-9]{64}$/.test(adapter.imageDigest)); digest(adapter.imageDigest.slice(7));
  }
  sourceTargets(value.sources); assert.equal(value.sources.length, 2, 'Pin both production source services'); return value;
}

function common(proof, target, kind, now) {
  assert.equal(proof.version, 1); assert.equal(proof.kind, kind); assert.equal(proof.purpose, 'MEASUREMENT', 'TEST data cannot certify production');
  for (const field of ['environment', 'revision', 'provider', 'operatorEpoch', 'hostIdSha256']) assert.equal(proof[field], target[field], 'Production provenance differs');
  return fresh(proof.observedAt, now);
}

export function reviewPreflight(proof, target, now = Date.now()) {
  exact(proof, [...COMMON, 'targetSha256', 'checks', 'tls']); common(proof, target, 'PRODUCTION_PREFLIGHT', now); digest(proof.targetSha256); exact(proof.checks, CHECKS);
  for (const value of Object.values(proof.checks)) assert.equal(value, true, 'Host preflight check is UNMET');
  assert(Array.isArray(proof.tls) && proof.tls.length === 2); const origins = new Set();
  for (const tls of proof.tls) {
    exact(tls, ['origin', 'authorized', 'protocol', 'certificateSha256', 'validUntil', 'httpsOnly']); assert([target.appOrigin, target.gatewayOrigin].includes(tls.origin) && !origins.has(tls.origin)); origins.add(tls.origin);
    assert.equal(tls.authorized, true); assert.equal(tls.httpsOnly, true); assert(['TLSv1.2', 'TLSv1.3'].includes(tls.protocol)); digest(tls.certificateSha256); assert(timestamp(tls.validUntil) > now, 'TLS certificate expired');
  }
  return true;
}

const percentile = values => [...values].sort((a, b) => a - b)[Math.ceil(values.length * 0.95) - 1];
export function reviewStartup(proof, target, now = Date.now()) {
  exact(proof, [...COMMON, 'adapters']); const observed = common(proof, target, 'HOSTED_STARTUP', now); assert(Array.isArray(proof.adapters) && proof.adapters.length === 6);
  const seen = new Set(), runs = new Set(), events = [], summary = [];
  for (const adapter of proof.adapters) {
    exact(adapter, ['language', 'runtime', 'version', 'framework', 'sourceSha256', 'imageDigest', 'maintainer', 'licenceEvidenceSha256', 'reproductionEvidenceSha256', 'samples']);
    const policy = target.adapters.find(row => row.language === adapter.language); assert(policy && !seen.has(adapter.language)); seen.add(adapter.language); for (const [key, value] of Object.entries(policy)) assert.equal(adapter[key], value, 'Hosted run adapter differs from the pinned image');
    assert(/^[A-Za-z0-9][A-Za-z0-9._@+-]{2,99}$/.test(adapter.maintainer)); digest(adapter.licenceEvidenceSha256); digest(adapter.reproductionEvidenceSha256); assert(Array.isArray(adapter.samples)); integer(adapter.samples.length, 100, 1000); const warm = [], cold = [];
    for (const sample of adapter.samples) {
      exact(sample, ['runId', 'kind', 'queuedAtMs', 'admittedAtMs', 'readyAtMs', 'cleanedAtMs']); assert(UUID.test(sample.runId) && !runs.has(sample.runId), 'Use unique fresh hosted run identities'); runs.add(sample.runId); assert(['WARM', 'COLD'].includes(sample.kind), 'Replay is not a hosted start');
      for (const key of ['queuedAtMs', 'admittedAtMs', 'readyAtMs', 'cleanedAtMs']) integer(sample[key], observed - 31 * DAY, observed);
      assert(sample.queuedAtMs <= sample.admittedAtMs && sample.admittedAtMs <= sample.readyAtMs && sample.readyAtMs < sample.cleanedAtMs && sample.cleanedAtMs - sample.admittedAtMs <= 300000, 'Startup chronology or hard lease is invalid');
      (sample.kind === 'WARM' ? warm : cold).push(sample.readyAtMs - sample.admittedAtMs); events.push([sample.admittedAtMs, 1], [sample.cleanedAtMs, -1]);
    }
    assert(warm.length && cold.length, 'Measure warm and cold starts'); const warmP95Ms = percentile(warm), coldP95Ms = percentile(cold); assert(warmP95Ms <= 5000 && coldP95Ms <= 30000, 'Hosted startup performance is UNMET'); summary.push({language: adapter.language, freshRuns: adapter.samples.length, warmP95Ms, coldP95Ms});
  }
  let active = 0, peak = 0; for (const [, delta] of events.sort((a, b) => a[0] - b[0] || a[1] - b[1])) {active += delta; peak = Math.max(peak, active); assert(active >= 0 && peak <= 10, 'Limited cohort capacity exceeded');} assert.equal(active, 0);
  return {adapters: summary, peakAdmittedRuns: peak};
}

function startupDocument(bytes, baseDir) {
  const document = parse(bytes); assert(Array.isArray(document.adapters));
  document.adapters = document.adapters.map(adapter => {
    assert(!Object.hasOwn(adapter, 'samples'), 'Keep bounded startup samples in separate NDJSON files'); const {samplesFile, ...metadata} = adapter, captured = reference(baseDir, samplesFile, 1024 * 1024);
    const text = new TextDecoder('utf-8', {fatal: true}).decode(captured.bytes); assert(text.endsWith('\n')); const lines = text.slice(0, -1).split('\n'); integer(lines.length, 100, 1000);
    return {...metadata, samples: lines.map(line => {assert(Buffer.byteLength(line) <= 2048); return parse(Buffer.from(line));})};
  }); return document;
}

export function reviewOperations(proof, target, now = Date.now()) {
  exact(proof, [...COMMON, 'infrastructure', 'queryPlans', 'recovery', 'merchant', 'human']); common(proof, target, 'PRODUCTION_OPERATIONS', now);
  const infra = proof.infrastructure; exact(infra, ['isolationTraceSha256', 'kernelSha256', 'firecrackerSha256', 'jailerSha256', 'networkRejected', 'cgroupLimitsObserved', 'hardLeaseObserved', 'crashCleanupObserved', 'orphanAdmissionDenied']); digest(infra.isolationTraceSha256);
  for (const name of ['kernel', 'firecracker', 'jailer']) assert.equal(infra[name + 'Sha256'], target.providerFiles[name].sha256); for (const field of ['networkRejected', 'cgroupLimitsObserved', 'hardLeaseObserved', 'crashCleanupObserved', 'orphanAdmissionDenied']) assert.equal(infra[field], true);
  assert(Array.isArray(proof.queryPlans) && proof.queryPlans.length === 2); const catalogues = new Set();
  for (const plan of proof.queryPlans) {exact(plan, ['catalogue', 'eligibleRows', 'planSha256', 'observedAt']); assert(['components', 'college-projects'].includes(plan.catalogue) && !catalogues.has(plan.catalogue)); catalogues.add(plan.catalogue); integer(plan.eligibleRows, 10000, 1000000000); digest(plan.planSha256); fresh(plan.observedAt, now);}
  const recovery = proof.recovery; exact(recovery, ['traceSha256', 'backupAt', 'incidentAt', 'recoveredAt', 'encryptedArchive', 'separateRestoreTarget', 'sourceBindings', 'outboxBindings', 'purchaseBindings', 'oldEpoch', 'newEpoch', 'oldCapabilitiesDenied', 'rollbackRevision', 'compatibleMigrations', 'retainedFinancialRecords']); digest(recovery.traceSha256);
  const backup = timestamp(recovery.backupAt), incident = fresh(recovery.incidentAt, now), recovered = fresh(recovery.recoveredAt, now); assert(backup <= incident && incident <= recovered && incident - backup <= 900000 && recovered - incident <= 14400000, 'Production RPO/RTO is UNMET');
  assert(UUID.test(recovery.oldEpoch) && recovery.oldEpoch !== target.operatorEpoch && recovery.newEpoch === target.operatorEpoch); assert(REVISION.test(recovery.rollbackRevision) && recovery.rollbackRevision !== target.revision);
  for (const field of ['encryptedArchive', 'separateRestoreTarget', 'sourceBindings', 'outboxBindings', 'purchaseBindings', 'oldCapabilitiesDenied', 'compatibleMigrations', 'retainedFinancialRecords']) assert.equal(recovery[field], true);
  const merchant = proof.merchant; exact(merchant, ['provider', 'mode', 'traceSha256', 'settledPaymentVerified', 'refundReconciled', 'signedWebhookVerified']); assert.equal(merchant.provider, 'RAZORPAY'); assert.equal(merchant.mode, 'LIVE'); digest(merchant.traceSha256); for (const field of ['settledPaymentVerified', 'refundReconciled', 'signedWebhookVerified']) assert.equal(merchant[field], true);
  const human = proof.human; exact(human, ['owner', 'traceSha256', 'reviewedAt', 'keyboard', 'screenReader', 'zoomPercent', 'widths', 'taskSessions', 'blockingFindings', 'ownersAcknowledged']); assert(/^[a-z][a-z0-9_.-]{1,63}$/.test(human.owner) && !['unknown', 'pending', 'unassigned'].includes(human.owner)); digest(human.traceSha256); fresh(human.reviewedAt, now); for (const field of ['keyboard', 'screenReader', 'ownersAcknowledged']) assert.equal(human[field], true); assert.equal(human.zoomPercent, 200); assert.deepEqual([...human.widths].sort((a, b) => a - b), [320, 390, 768, 1024, 1440]); integer(human.taskSessions, 5, 1000); assert.equal(human.blockingFindings, 0); return true;
}

function reference(baseDir, ref, maximum = 2 * 1024 * 1024) {
  exact(ref, ['file', 'sha256']); assert(typeof ref.file === 'string' && /^[A-Za-z0-9][A-Za-z0-9_.-]{0,159}$/.test(ref.file) && !ref.file.includes('..'), 'Evidence must be a sibling regular file'); digest(ref.sha256);
  const path = resolve(baseDir, ref.file), bytes = boundedFile(path, maximum); assert.equal(hash(bytes), ref.sha256, 'Pinned evidence changed'); return {path, bytes};
}

export function verifyProduction({targetPath, bundlePath, publicKeyPath, revision, now = Date.now()}) {
  const targetBytes = boundedFile(targetPath, 65536), target = targetPolicy(parse(targetBytes)); assert.equal(target.revision, revision, 'Supply the exact deployed revision');
  const key = boundedFile(publicKeyPath, 16384), bundleBytes = boundedFile(bundlePath, 131072), bundle = openReport(bundleBytes, key); exact(bundle, [...COMMON, 'targetSha256', 'files', 'manifests', 'traces']); common(bundle, target, 'PRODUCTION_QUALIFICATION', now); assert.equal(bundle.targetSha256, hash(targetBytes), 'External target policy changed');
  const baseDir = dirname(resolve(bundlePath)); exact(bundle.files, FILES); assert.equal(new Set(Object.values(bundle.files).map(ref => ref.file)).size, FILES.length, 'Each measurement lane needs its own evidence'); const files = Object.fromEntries(FILES.map(name => [name, reference(baseDir, bundle.files[name])]));
  exact(bundle.traces, TRACES); const traces = Object.fromEntries(TRACES.map(name => [name, reference(baseDir, bundle.traces[name])]));
  const hostOptions = {admissionPath: files.admission.path, inventoryPath: files.inventory.path, expectedAdmissionSha256: bundle.files.admission.sha256, expectedInventorySha256: bundle.files.inventory.sha256, publicKeyBytes: key, operatorEpoch: target.operatorEpoch, now};
  const admission = verifyBundle(hostOptions); assert.equal(admission.provider, target.provider); const inventory = parse(files.inventory.bytes);
  const preflight = parse(files.preflight.bytes); assert.equal(preflight.targetSha256, hash(targetBytes)); reviewPreflight(preflight, target, now);
  const startup = startupDocument(files.startup.bytes, baseDir), startupSummary = reviewStartup(startup, target, now); assert(Array.isArray(bundle.manifests) && bundle.manifests.length === 6); const seen = new Set();
  for (const item of bundle.manifests) {
    exact(item, ['language', 'file', 'sha256']); assert(!seen.has(item.language)); seen.add(item.language); const policy = target.adapters.find(row => row.language === item.language); assert(policy);
    const captured = reference(baseDir, {file: item.file, sha256: item.sha256}), native = verifyManifestBundle({...hostOptions, manifestPath: captured.path, expectedManifestSha256: item.sha256}); const manifest = verifiedPayload(captured.bytes, key);
    assert.equal(normalizeLanguage(native.language), policy.language); for (const field of ['sourceSha256', 'imageDigest']) assert.equal(native[field], policy[field]); assert.equal(native.runtime, policy.runtime); assert.equal(native.runtimeVersion, policy.version); assert.equal(manifest.framework, policy.framework);
    const row = startup.adapters.find(adapter => adapter.language === item.language); assert.equal(row.maintainer, native.certification.maintainer); assert.equal(row.reproductionEvidenceSha256, native.certification.reproductionSha256); assert.equal(row.licenceEvidenceSha256, inventory.records.find(record => record.control === 'licence').artifactSha256);
    for (const type of ['licence', 'reproduction']) assert.equal(row[type + 'EvidenceSha256'], hash(traces[type + '-' + item.language].bytes));
  }
  const operations = parse(files.operations.bytes); reviewOperations(operations, target, now); assert.equal(operations.infrastructure.isolationTraceSha256, hash(traces.isolation.bytes)); for (const plan of operations.queryPlans) assert.equal(plan.planSha256, hash(traces['query-' + plan.catalogue].bytes)); for (const name of ['recovery', 'merchant', 'human']) assert.equal(operations[name].traceSha256, hash(traces[name].bytes));
  const options = {environment: target.environment, revision, publicKeyBytes: key, now};
  const reports = {};
  for (const name of ['loadComponents', 'loadProjects', 'soak', 'handover']) {reports[name] = verifyReportFile(files[name].path, {...options, expectedSha256: bundle.files[name].sha256}); assert.equal(reports[name].kind, name === 'soak' ? 'SOAK' : name === 'handover' ? 'HANDOVER' : 'LOAD');}
  assert.equal(reports.loadComponents.catalogue, 'components'); assert.equal(reports.loadProjects.catalogue, 'college-projects');
  const handover = openReport(files.handover.bytes, key); for (const [kind, name] of [['LOAD', 'loadComponents'], ['SOAK', 'soak']]) {const dependency = handover.reports.find(row => row.kind === kind); assert.equal(dependency?.file, bundle.files[name].file); assert.equal(dependency.sha256, bundle.files[name].sha256, 'Handover measured another cohort');}
  for (const name of ['loadComponents', 'loadProjects', 'soak']) {
    const report = openReport(files[name].bytes, key);
    for (const log of report.logs) {const captured = reference(baseDir, log, 32 * 1024 * 1024), journal = readJournal(captured.path); assert.equal(journal.sha256, log.sha256, 'Captured journal changed'); assert.equal(journal.header.origin, target.appOrigin); if (name === 'soak') {assert.equal(canonical(journal.header.config.sources), canonical(target.sources), 'Soak source pins differ from the external target'); for (const observation of journal.observations) if (observation.runtimeProbe?.snapshot) assert.equal(observation.runtimeProbe.snapshot.operatorEpoch, target.operatorEpoch);}}
  }
  return {result: 'PRODUCTION_QUALIFICATION_VERIFIED', environment: target.environment, revision, operatorEpoch: target.operatorEpoch, evidenceSha256: hash(bundleBytes), gates: {signedAdmission: true, hostAndTls: true, sixAdapterStartup: true, newCataloguesLoad: true, sevenDayRuntimeAndSources: true, productionRecoveryAndMerchant: true, humanAcceptance: true, signedHandover: true}, startup: startupSummary, changesActivationFlags: false};
}

/** Absolute, cancellable DNS bound prevents timed-out sockets retaining an OS resolver request. */
function boundedLookup(hostname, options, callback) {
  const resolver = new Resolver({timeout: 1500, tries: 1}); let finished = false;
  const finish = (error, addresses = []) => {if (finished) return; finished = true; clearTimeout(timer); resolver.cancel(); if (error) callback(error); else if (options.all) callback(null, addresses); else callback(null, addresses[0].address, addresses[0].family);};
  const timer = setTimeout(() => finish(Object.assign(new Error('DNS deadline'), {code: 'ETIMEDOUT'})), 3000);
  Promise.allSettled([resolver.resolve4(hostname), resolver.resolve6(hostname)]).then(results => {const addresses = results.flatMap((result, index) => result.status === 'fulfilled' ? result.value.map(address => ({address, family: index ? 6 : 4})) : []); finish(addresses.length ? null : Object.assign(new Error('DNS unavailable'), {code: 'ENOTFOUND'}), addresses);}).catch(() => finish(new Error('DNS unavailable')));
}

function cleartextClosed(origin) {
  return new Promise(resolveResult => {
    let settled = false; const finish = value => {if (settled) return; settled = true; clearTimeout(timer); req.destroy(); resolveResult(value);};
    const req = request({hostname: new URL(origin).hostname, port: 80, path: '/', method: 'HEAD', maxHeaderSize: 16384, lookup: boundedLookup}, response => {let valid = false; try {valid = [301, 302, 307, 308].includes(response.statusCode) && new URL(response.headers.location, 'http://' + new URL(origin).hostname).origin === origin;} catch { /* Invalid redirects cannot demonstrate HTTPS-only. */ } response.destroy(); finish(valid);});
    const timer = setTimeout(() => finish(false), 5000); req.on('error', error => finish(error.code === 'ECONNREFUSED')); req.end();
  });
}

export async function observeTls(origin) {
  httpsOrigin(origin);
  const tls = await new Promise(resolveResult => {
    let settled = false; const finish = value => {if (settled) return; settled = true; clearTimeout(timer); socket.destroy(); resolveResult(value);};
    const socket = connect({host: new URL(origin).hostname, port: 443, servername: new URL(origin).hostname, rejectUnauthorized: true, minVersion: 'TLSv1.2', lookup: boundedLookup}, () => {try {const cert = socket.getPeerCertificate(); finish({authorized: socket.authorized === true, protocol: socket.getProtocol(), certificateSha256: hash(cert.raw), validUntil: new Date(cert.valid_to).toISOString()});} catch {finish({authorized: false, protocol: null, certificateSha256: null, validUntil: null});}});
    const timer = setTimeout(() => finish({authorized: false, protocol: null, certificateSha256: null, validUntil: null}), 5000); socket.on('error', () => finish({authorized: false, protocol: null, certificateSha256: null, validUntil: null}));
  }); return {origin, ...tls, httpsOnly: await cleartextClosed(origin)};
}

function rootPinnedFile(pin, executable) {
  let descriptor;
  try {
    assert.equal(process.platform, 'linux'); const parts = resolve(pin.path).split('/').filter(Boolean), flags = constants.O_RDONLY | constants.O_NOFOLLOW | constants.O_NONBLOCK; descriptor = openSync('/', flags | constants.O_DIRECTORY);
    for (let i = 0; i < parts.length; i++) {const parent = fstatSync(descriptor); assert(parent.uid === 0 && !(parent.mode & 0o022)); const next = openSync('/proc/self/fd/' + descriptor + '/' + parts[i], flags | (i < parts.length - 1 ? constants.O_DIRECTORY : 0)); closeSync(descriptor); descriptor = next;}
    const info = fstatSync(descriptor); assert(info.isFile() && info.uid === 0 && !(info.mode & 0o222) && (!executable || info.mode & 0o111) && info.size > 0 && info.size <= 64 * 1024 * 1024);
    const bytes = Buffer.alloc(info.size + 1); let size = 0; while (size < bytes.length) {const read = readSync(descriptor, bytes, size, bytes.length - size, null); if (!read) break; size += read;} assert.equal(size, info.size); return hash(bytes.subarray(0, size)) === pin.sha256;
  } catch {return false;} finally {if (descriptor !== undefined) closeSync(descriptor);}
}

function localChecks(target) {
  const checks = Object.fromEntries(CHECKS.map(name => [name, false])); checks.linux = process.platform === 'linux'; let device;
  try {device = openSync('/dev/kvm', constants.O_RDWR | constants.O_NOFOLLOW | constants.O_NONBLOCK); checks.kvmReadWrite = fstatSync(device).isCharacterDevice();} catch { /* Unavailable device stays UNMET; never run a VM during discovery. */ } finally {if (device !== undefined) closeSync(device);}
  try {checks.cgroupV2 = statfsSync('/sys/fs/cgroup').type === 0x63677270 && readFileSync('/sys/fs/cgroup/cgroup.controllers', 'utf8').trim().length > 0;} catch { /* Unavailable controller stays UNMET. */ }
  try {const result = spawnSync('/usr/bin/systemctl', ['--version'], {encoding: 'utf8', timeout: 3000, maxBuffer: 4096, env: {PATH: '/usr/bin:/bin', LC_ALL: 'C'}}); checks.systemd250 = result.status === 0 && Number(/^systemd (\d+)/.exec(result.stdout)?.[1]) >= 250;} catch { /* Missing executable stays UNMET. */ }
  try {checks.hostIdentity = hash(boundedFile('/etc/machine-id', 1024)) === target.hostIdSha256;} catch { /* Never output machine identity. */ }
  for (const name of ['firecracker', 'jailer', 'kernel']) checks[name] = rootPinnedFile(target.providerFiles[name], name !== 'kernel'); return checks;
}

export async function collectPreflight({targetPath, now = Date.now()}) {
  const bytes = boundedFile(targetPath, 65536), target = targetPolicy(parse(bytes)), tls = [];
  for (const origin of [target.appOrigin, target.gatewayOrigin]) tls.push(await observeTls(origin));
  return {...Object.fromEntries(['environment', 'revision', 'provider', 'operatorEpoch', 'hostIdSha256'].map(field => [field, target[field]])), version: 1, kind: 'PRODUCTION_PREFLIGHT', purpose: 'MEASUREMENT', observedAt: new Date(now).toISOString(), targetSha256: hash(bytes), checks: localChecks(target), tls};
}

export function checkoutRevision() {
  const options = {cwd: ROOT, encoding: 'utf8', timeout: 3000, maxBuffer: 65536}; const head = spawnSync('git', ['rev-parse', 'HEAD'], options), status = spawnSync('git', ['status', '--porcelain', '--untracked-files=no'], options);
  assert(head.status === 0 && REVISION.test(head.stdout.trim()) && status.status === 0 && !status.stdout.trim(), 'Use a clean exact-revision checkout'); return head.stdout.trim();
}
