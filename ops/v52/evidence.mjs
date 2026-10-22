/** V5.2 operator evidence. No provider actions, secrets, request inputs or user identities. */
import assert from 'node:assert/strict';
import {createHash, createPrivateKey, createPublicKey, sign, verify} from 'node:crypto';
import {closeSync, constants, fstatSync, openSync, readSync, writeFileSync} from 'node:fs';
import {basename, dirname, resolve} from 'node:path';
import {parseStrictJson} from '../../scripts/build-data-ai-material.mjs';

export const LIMITS = Object.freeze({fileBytes: 32 * 1024 * 1024, lineBytes: 65536, observations: 25000, timeoutMs: 10000, cadenceMs: 60000, sevenDaysMs: 7 * 86400000, freshnessMs: 86400000, loadRate: 20, loadConcurrency: 10, loadDurationMs: 60000, eligibleRows: 10000});
export const hash = bytes => createHash('sha256').update(bytes).digest('hex');
export const canonical = value => JSON.stringify(value);
export function exact(value, keys) {assert(value && typeof value === 'object' && !Array.isArray(value), 'Object required'); assert.deepEqual(Object.keys(value).sort(), [...keys].sort(), 'Unsupported evidence fields');}
export function integer(value, min, max) {assert(Number.isSafeInteger(value) && value >= min && value <= max, 'Integer outside evidence bounds');}
export function timestamp(value) {assert(typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/.test(value), 'Use canonical UTC timestamps'); const at = Date.parse(value); assert(Number.isFinite(at) && new Date(at).toISOString() === value, 'Invalid calendar timestamp'); return at;}
export function origin(value) {const url = new URL(value); assert(['http:', 'https:'].includes(url.protocol) && !url.username && !url.password && !url.search && !url.hash && url.pathname === '/', 'Use an origin without credentials or paths'); assert(url.protocol === 'https:' || ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname), 'Plain HTTP is restricted to loopback'); return url.origin;}
export function serverProfile(value) {exact(value, ['cpuCores', 'memoryMiB', 'postgresMajor', 'replicas', 'storage', 'clientLocation']); integer(value.cpuCores, 1, 256); integer(value.memoryMiB, 512, 1048576); integer(value.postgresMajor, 16, 30); integer(value.replicas, 1, 100); assert(['SSD', 'HDD', 'MANAGED'].includes(value.storage)); assert(['SAME_REGION', 'OTHER_REGION', 'LOOPBACK'].includes(value.clientLocation)); return value;}
const digest = value => assert(typeof value === 'string' && /^[a-f0-9]{64}$/.test(value), 'Invalid evidence hash');
const owner = value => assert(typeof value === 'string' && /^[a-z][a-z0-9_.-]{1,63}$/.test(value) && !['pending', 'unassigned', 'unknown'].includes(value), 'Name an accountable operator handle');
const uuid = value => assert(typeof value === 'string' && /^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/.test(value), 'Invalid collector segment identity');
const safeName = value => assert(typeof value === 'string' && /^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,159}$/.test(value) && !value.includes('..'), 'Evidence references must be sibling regular files');
export function boundedFile(path, max = LIMITS.fileBytes) {
  // Descriptor-relative traversal refuses every linked parent, not only the final file.
  assert(process.platform === 'linux', 'Operator evidence verification requires Linux descriptor-relative reads');
  const parts = resolve(path).split('/').filter(Boolean), flags = constants.O_RDONLY | constants.O_NOFOLLOW | constants.O_NONBLOCK;
  let descriptor = openSync('/', flags | constants.O_DIRECTORY);
  try {
    for (let i = 0; i < parts.length; i++) {const next = openSync('/proc/self/fd/' + descriptor + '/' + parts[i], flags | (i < parts.length - 1 ? constants.O_DIRECTORY : 0)); closeSync(descriptor); descriptor = next;}
    const stat = fstatSync(descriptor); assert(stat.isFile() && stat.size > 0 && stat.size <= max, 'Use a bounded regular evidence file');
    const bytes = Buffer.alloc(Math.min(stat.size + 1, max + 1)); let size = 0;
    while (size < bytes.length) {const length = readSync(descriptor, bytes, size, bytes.length - size, null); if (!length) break; size += length;}
    assert(size === stat.size && size <= max, 'Evidence changed or exceeded its byte budget'); return bytes.subarray(0, size);
  } finally {closeSync(descriptor);}
}
export const parse = bytes => parseStrictJson(new TextDecoder('utf-8', {fatal: true}).decode(bytes));
export function writeExclusive(path, bytes) {writeFileSync(path, bytes, {flag: 'wx', mode: 0o600});}
export function envelope(report, privateKeyBytes) {const payload = Buffer.from(canonical(report)), key = createPrivateKey(privateKeyBytes); assert.equal(key.asymmetricKeyType, 'ed25519'); return {algorithm: 'Ed25519', payload: payload.toString('base64'), signature: sign(null, payload, key).toString('base64')};}
export function openReport(bytes, publicKeyBytes, requireSignature = true) {
  const input = parse(bytes); if (input.algorithm === undefined) {assert(!requireSignature, 'Release evidence requires a trusted operator signature'); return input;}
  exact(input, ['algorithm', 'payload', 'signature']); assert.equal(input.algorithm, 'Ed25519');
  assert(typeof input.payload === 'string' && /^[A-Za-z0-9+/]+={0,2}$/.test(input.payload) && input.payload.length <= 87000, 'Bounded canonical signature payload required');
  assert(typeof input.signature === 'string' && /^[A-Za-z0-9+/]{86}==$/.test(input.signature), 'Canonical Ed25519 signature required');
  const payload = Buffer.from(input.payload, 'base64'); assert.equal(payload.toString('base64'), input.payload); const key = createPublicKey(publicKeyBytes); assert.equal(key.asymmetricKeyType, 'ed25519');
  assert(verify(null, payload, key, Buffer.from(input.signature, 'base64')), 'Operator signature rejected'); return parse(payload);
}
function header(value) {
  exact(value, ['version', 'kind', 'purpose', 'environment', 'revision', 'origin', 'startedAt', 'segmentId', 'serverProfile', 'config']);
  assert.equal(value.version, 1); assert(['LOAD', 'SOAK'].includes(value.kind)); assert(['MEASUREMENT', 'TEST'].includes(value.purpose));
  assert(typeof value.environment === 'string' && /^[a-z][a-z0-9-]{1,63}$/.test(value.environment)); assert(/^[a-f0-9]{40}$/.test(value.revision)); origin(value.origin); timestamp(value.startedAt); uuid(value.segmentId); serverProfile(value.serverProfile);
  const configKeys = value.kind === 'SOAK' ? ['cadenceMs', 'timeoutMs'] : ['rate', 'concurrency', 'durationMs', 'timeoutMs']; exact(value.config, [...configKeys, ...(value.config.catalogue === undefined ? [] : ['catalogue'])]);
  if(value.config.catalogue !== undefined) assert(['products','components','college-projects'].includes(value.config.catalogue));
  integer(value.config.timeoutMs, 1, LIMITS.timeoutMs);
  if (value.kind === 'SOAK') integer(value.config.cadenceMs, 100, LIMITS.cadenceMs);
  else {integer(value.config.rate, 1, LIMITS.loadRate); integer(value.config.concurrency, 1, LIMITS.loadConcurrency); integer(value.config.durationMs, 100, 300000);}
}
function probe(value) {
  exact(value, ['status', 'outcome', 'durationMs', 'bodySha256', 'serverDurationMs', 'eligibleRows']);
  integer(value.status, 0, 599); assert(['OK', 'HTTP_ERROR', 'TIMEOUT', 'INVALID_RESPONSE', 'CAPACITY_SKIP'].includes(value.outcome));
  assert(Number.isFinite(value.durationMs) && value.durationMs >= 0 && value.durationMs <= LIMITS.timeoutMs + 3000, 'Implausible probe latency');
  assert(value.serverDurationMs === null || (Number.isFinite(value.serverDurationMs) && value.serverDurationMs >= 0 && value.serverDurationMs <= value.durationMs + 2), 'Server latency cannot exceed measured round trip');
  if (value.eligibleRows !== null) integer(value.eligibleRows, 0, 1000000000);
  if (value.bodySha256 !== null) digest(value.bodySha256);
  if (value.outcome === 'OK') {assert.equal(value.status, 200); digest(value.bodySha256); integer(value.eligibleRows, 0, 1000000000);}
  else {assert.equal(value.bodySha256, null); assert.equal(value.eligibleRows, null); assert.equal(value.serverDurationMs, null);}
  if (value.outcome === 'CAPACITY_SKIP') assert.equal(value.status, 0);
  if (value.outcome === 'HTTP_ERROR') assert(value.status >= 100 && value.status !== 200, 'HTTP failure must retain a non-success status');
}
export function runtimeSnapshot(value) {
  exact(value, ['observedAt', 'counterEpoch', 'operatorEpoch', 'measuredFrom', 'admitted', 'healthy', 'pending', 'completed', 'infraFailures', 'scenarioFailures', 'quotaDenials', 'validationDenials']);
  if (value.operatorEpoch !== null) uuid(value.operatorEpoch);
  const at = timestamp(value.observedAt), epoch = timestamp(value.counterEpoch); assert.equal(value.measuredFrom, value.counterEpoch); assert(epoch <= at);
  for (const key of ['admitted', 'healthy', 'pending', 'completed', 'infraFailures', 'scenarioFailures', 'quotaDenials', 'validationDenials']) integer(value[key], 0, 1000000000);
  assert.equal(value.completed + value.pending, value.admitted); assert.equal(value.healthy + value.infraFailures, value.completed); return value;
}
function runtimeProbe(value) {if (value === null) return; exact(value, ['status', 'outcome', 'durationMs', 'snapshot']); integer(value.status, 0, 599); assert(['OK', 'HTTP_ERROR', 'TIMEOUT', 'INVALID_RESPONSE'].includes(value.outcome)); assert(Number.isFinite(value.durationMs) && value.durationMs >= 0 && value.durationMs <= LIMITS.timeoutMs + 3000); if (value.outcome === 'OK') {assert.equal(value.status, 200); runtimeSnapshot(value.snapshot);} else assert.equal(value.snapshot, null);}
export function readJournal(path) {
  const bytes = boundedFile(path), raw = new TextDecoder('utf-8', {fatal: true}).decode(bytes); assert(raw.endsWith('\n'), 'Truncated observation journal'); const lines = raw.slice(0, -1).split('\n');
  assert(lines.length >= 2 && lines.length <= LIMITS.observations + 1, 'Empty or oversized observation cohort'); const first = parse(Buffer.from(lines[0])); header(first); let previous = hash(lines[0]), lastAt = timestamp(first.startedAt), lastElapsed = 0;
  const observations = [];
  for (let i = 1; i < lines.length; i++) {
    const value = parse(Buffer.from(lines[i])); exact(value, ['sequence', 'sampledAt', 'elapsedMs', 'previousSha256', 'sha256', ...(first.kind === 'SOAK' ? ['publicProbe', 'runtimeProbe'] : ['scheduledElapsedMs', 'response'])]);
    assert.equal(value.sequence, i); digest(value.sha256); assert.equal(value.previousSha256, previous, 'Broken observation hash chain'); const {sha256, ...signed} = value; assert.equal(hash(canonical(signed)), sha256, 'Observation content was changed');
    const at = timestamp(value.sampledAt); integer(value.elapsedMs, 0, LIMITS.sevenDaysMs + 86400000);
    assert(at >= lastAt && value.elapsedMs >= lastElapsed, 'Observation clocks moved backwards'); assert(Math.abs((at - timestamp(first.startedAt)) - value.elapsedMs) <= 2000, 'Wall time and monotonic elapsed time disagree');
    if (first.kind === 'SOAK') {probe(value.publicProbe); runtimeProbe(value.runtimeProbe); assert(value.publicProbe.outcome !== 'CAPACITY_SKIP'); assert(at - lastAt <= first.config.cadenceMs + first.config.timeoutMs * 2 + 3000, 'Missing soak interval'); if (value.runtimeProbe?.snapshot) {const observedAt = timestamp(value.runtimeProbe.snapshot.observedAt); assert(observedAt <= at + 2000 && observedAt >= at - first.config.timeoutMs - 2000, 'Stale backend observation');}}
    else {integer(value.scheduledElapsedMs, 0, first.config.durationMs); probe(value.response); assert(value.elapsedMs >= value.scheduledElapsedMs, 'A load observation predates its scheduled request'); if (value.response.outcome !== 'CAPACITY_SKIP') {const requestStart = value.elapsedMs - value.response.durationMs; assert(requestStart >= value.scheduledElapsedMs - 2 && requestStart <= value.scheduledElapsedMs + 1000, 'Load request did not follow its declared cadence');}}
    previous = sha256; lastAt = at; lastElapsed = value.elapsedMs; observations.push(value);
  }
  return {header: first, observations, sha256: hash(bytes), lastAt, file: basename(path), byteLength: bytes.length};
}
export function appendObservation(writer, value) {const record = {...value, previousSha256: writer.previous}; const signed = {...record, sha256: hash(canonical(record))}; writer.append(canonical(signed) + '\n'); writer.previous = signed.sha256; return signed;}
export function journalReport(journals, kind = journals[0]?.header.kind) {
  assert(journals.length > 0 && journals.length <= 100, 'Provide bounded collector segments'); const first = journals[0].header;
  for (const journal of journals) {assert.equal(journal.header.kind, kind); for (const key of ['purpose', 'environment', 'revision', 'origin']) assert.equal(journal.header[key], first[key], 'Mixed collector provenance'); assert.equal(journal.header.config.catalogue ?? 'products', first.config.catalogue ?? 'products', 'Mixed catalogue targets');}
  return {version: 1, kind, purpose: first.purpose, environment: first.environment, revision: first.revision, startedAt: first.startedAt, endedAt: new Date(journals.at(-1).lastAt).toISOString(), collector: 'getlancer-v52-ops-1', logs: journals.map(journal => ({file: journal.file, sha256: journal.sha256}))};
}
const percentile = values => [...values].sort((a, b) => a - b)[Math.ceil(values.length * .95) - 1];
function soakSummary(journals) {
  const observations = [], segments = new Set(); let previous;
  for (const journal of journals) {
    assert(!segments.has(journal.header.segmentId), 'Duplicate collector segment'); segments.add(journal.header.segmentId);
    assert.equal(journal.header.origin, journals[0].header.origin, 'Soak deployment origin changed');
    if (previous) {const gap = timestamp(journal.header.startedAt) - previous.lastAt; assert(gap >= 0 && gap <= LIMITS.cadenceMs + 3000, 'Unobserved gap between collector segments');}
    assert.equal(canonical(journal.header.config), canonical(journals[0].header.config), 'Soak sampling profile changed'); assert.equal(canonical(journal.header.serverProfile), canonical(journals[0].header.serverProfile), 'Declared deployment profile changed'); observations.push(...journal.observations); previous = journal;
  }
  const started = timestamp(journals[0].header.startedAt), ended = journals.at(-1).lastAt, elapsedMs = ended - started;
  const success = observations.filter(o => o.publicProbe.outcome === 'OK').length;
  // Sampling establishes a conservative observed interval SLI, not a monthly uptime certification.
  let outageMs = 0, previousAt = started, previousFailed = false;
  for (const observation of observations) {const at = timestamp(observation.sampledAt), failed = observation.publicProbe.outcome !== 'OK'; if (failed || previousFailed) outageMs += at - previousAt; previousAt = at; previousFailed = failed;}
  const runtime = observations.filter(o => o.runtimeProbe?.outcome === 'OK'); const baseline = runtime[0]?.runtimeProbe.snapshot, ending = runtime.at(-1)?.runtimeProbe.snapshot;
  let admitted = null, healthy = null, infraFailures = null, scenarioFailures = null, quotaDenials = null, validationDenials = null;
  if (baseline && ending) {
    for (const observation of runtime) {assert.equal(observation.runtimeProbe.snapshot.counterEpoch, baseline.counterEpoch, 'Runtime counter epoch changed: restart the soak'); assert.equal(observation.runtimeProbe.snapshot.operatorEpoch, baseline.operatorEpoch, 'Runtime authority epoch changed: restart the soak after restore'); for (const field of ['admitted', 'healthy', 'completed', 'infraFailures', 'scenarioFailures', 'quotaDenials', 'validationDenials']) assert(observation.runtimeProbe.snapshot[field] >= baseline[field], 'Runtime counters decreased');}
    for (let i = 1; i < runtime.length; i++) for (const field of ['admitted', 'healthy', 'completed', 'infraFailures', 'scenarioFailures', 'quotaDenials', 'validationDenials']) assert(runtime[i].runtimeProbe.snapshot[field] >= runtime[i - 1].runtimeProbe.snapshot[field], 'Runtime cohort counters decreased');
    admitted = ending.admitted - baseline.admitted; healthy = ending.healthy - baseline.healthy; infraFailures = ending.infraFailures - baseline.infraFailures; scenarioFailures = ending.scenarioFailures - baseline.scenarioFailures; quotaDenials = ending.quotaDenials - baseline.quotaDenials; validationDenials = ending.validationDenials - baseline.validationDenials;
  }
  const runtimeElapsedMs = baseline && ending ? timestamp(ending.observedAt) - timestamp(baseline.observedAt) : null;
  const endpointCoverage = Boolean(baseline && ending && timestamp(baseline.observedAt) - started <= LIMITS.cadenceMs && ended - timestamp(ending.observedAt) <= LIMITS.cadenceMs);
  const settled = Boolean(baseline && ending && baseline.pending === 0 && ending.pending === 0 && healthy + infraFailures === admitted);
  const infraSuccessPercent = admitted > 0 && settled ? healthy / admitted * 100 : null;
  return {elapsedMs, runtimeElapsedMs, samples: observations.length, successfulPublicProbes: success, failedPublicProbes: observations.length - success, observedOutageMs: outageMs, observedAvailabilityPercent: elapsedMs > 0 ? (elapsedMs - outageMs) / elapsedMs * 100 : null, monthlyAvailabilityCertified: false, admitted, healthy, infraFailures, scenarioFailures, quotaDenials, validationDenials, infraSuccessPercent, gates: {sevenElapsedDays: elapsedMs >= LIMITS.sevenDaysMs, sevenRuntimeElapsedDays: runtimeElapsedMs !== null && runtimeElapsedMs >= LIMITS.sevenDaysMs, continuousPublicCoverage: observations.length >= Math.floor(elapsedMs / LIMITS.cadenceMs), authoritativeRuntimeEndpoints: endpointCoverage, verifiedRuntimeAuthorityEpoch: Boolean(baseline?.operatorEpoch), allAdmittedSettled: settled, infrastructureSuccess99: infraSuccessPercent !== null && infraSuccessPercent >= 99}};
}
function loadSummary(journals) {
  assert.equal(journals.length, 1, 'Load profile must run as one collector segment'); const journal = journals[0], config = journal.header.config, observations = journal.observations, scheduled = new Set();
  for (const observation of observations) {assert(!scheduled.has(observation.scheduledElapsedMs), 'Duplicate scheduled load request'); scheduled.add(observation.scheduledElapsedMs);}
  const expected = Math.floor(config.durationMs * config.rate / 1000); assert.equal(observations.length, expected, 'Every scheduled load request needs an outcome');
  const occupancy = [];
  for (const observation of observations) if (observation.response.outcome !== 'CAPACITY_SKIP') {occupancy.push([observation.elapsedMs - observation.response.durationMs, 1], [observation.elapsedMs, -1]);}
  let active = 0, peak = 0; for (const [, delta] of occupancy.sort((a, b) => a[0] - b[0] || a[1] - b[1])) {active += delta; peak = Math.max(peak, active);} assert(peak <= config.concurrency, 'Captured requests exceeded declared concurrency');
  const successful = observations.filter(o => o.response.outcome === 'OK'), server = successful.filter(o => o.response.serverDurationMs !== null), rows = successful.map(o => o.response.eligibleRows);
  for (let i = 0; i < expected; i++) assert(scheduled.has(Math.floor(i * 1000 / config.rate)), 'Load schedule does not match its declared rate');
  const clientP95Ms = successful.length ? percentile(successful.map(o => o.response.durationMs)) : null, serverP95Ms = server.length === successful.length && server.length ? percentile(server.map(o => o.response.serverDurationMs)) : null;
  const eligibleRows = rows.length ? Math.min(...rows) : null;
  return {catalogue: config.catalogue ?? 'products', requests: expected, successfulRequests: successful.length, failedRequests: expected - successful.length, declaredRate: config.rate, durationMs: config.durationMs, peakMeasuredConcurrency: peak, eligibleRows, clientP95Ms, serverP95Ms, latencyScope: 'Client round trip and request-local Server-Timing app duration are separate measurements', gates: {newCatalogueMeasured: ['components','college-projects'].includes(config.catalogue), declared20Rps: config.rate === LIMITS.loadRate, boundedConcurrency10: config.concurrency === LIMITS.loadConcurrency, fullMinute: config.durationMs >= LIMITS.loadDurationMs && journal.lastAt - timestamp(journal.header.startedAt) >= config.durationMs - 1000 / config.rate, catalogue10000Eligible: eligibleRows !== null && eligibleRows >= LIMITS.eligibleRows, allRequestsSuccessful: successful.length === expected, serverP95AtMost300Ms: serverP95Ms !== null && serverP95Ms <= 300}};
}
export const DRILL_CONTROLS = ['BUDGET_STOP', 'ADMISSIONS_PAUSE', 'WITHDRAWAL', 'RESTORE', 'CAPABILITY_REVOCATION', 'ROLLBACK', 'CLEANUP', 'FINANCIAL_SOURCE_RETENTION'];
export const ACTIVATION_CONTROLS = ['DOMAIN', 'TLS', 'RUNTIME_ISOLATION', 'RUNTIME_PROVIDER', 'MERCHANT_LIVE', 'PAYMENT_CALLBACKS'];
export const HANDOVER_CONTROLS = ['CONTENT_RIGHTS_REPRODUCTION', 'SECURITY_CI', 'MANUAL_ACCESSIBILITY', 'TASK_COMPREHENSION', 'ALERT_DELIVERY', 'RUNBOOK_OWNERSHIP', 'LIMITED_COHORT_APPROVAL'];
// Proof attachments contain fixed facts and a hash of the restricted operator trace, never the trace's private payload.
export const CONTROL_FACTS = Object.freeze({
  BUDGET_STOP: {reservationDenied: true, budgetExceeded: false, retainedFinancialRecords: true},
  ADMISSIONS_PAUSE: {newAdmissionDenied: true, cleanupContinues: true},
  WITHDRAWAL: {newSaleDenied: true, publicListingHidden: true, historicalEntitlementsRetained: true},
  RESTORE: {authenticatedArchive: true, restoredCountsMatch: true, rpoMs: [0, 900000], rtoMs: [0, 14400000], oldCapabilitiesDenied: true},
  CAPABILITY_REVOCATION: {oldCapabilityDenied: true, freshEpoch: true, protectedBytesDenied: true},
  ROLLBACK: {buildPinned: true, migrationsCompatible: true, admissionsPaused: true, financialSourceRecordsRetained: true},
  CLEANUP: {orphanReservations: [0, 0], staleWorkers: [0, 0], cleanupConfirmed: true},
  FINANCIAL_SOURCE_RETENTION: {ledgerImmutable: true, sourceVersionsRetained: true, entitlementsCorrect: true},
  DOMAIN: {dnsVerified: true}, TLS: {validCertificate: true, httpsOnly: true},
  RUNTIME_ISOLATION: {separateAccount: true, noHostPrivileges: true, networkRestricted: true, hardLease: true},
  RUNTIME_PROVIDER: {liveProvider: true, signedAdmission: true, sourceHashesPinned: true},
  MERCHANT_LIVE: {merchantLive: true, providerLive: true},
  PAYMENT_CALLBACKS: {signedCallbackVerified: true, idempotent: true, retainedLedger: true},
  CONTENT_RIGHTS_REPRODUCTION: {components: [24, 24], labs: [18, 18], projects: [12, 12], rightsReviewed: true, cleanReproduction: true},
  SECURITY_CI: {blockingFindings: [0, 0], finalHeadCiPass: true},
  MANUAL_ACCESSIBILITY: {viewportsAssessed: true, keyboardAssessed: true, contrastAssessed: true, blockingFindings: [0, 0]},
  TASK_COMPREHENSION: {sessions: [5, 1000], medianFirstUsefulInteractionMs: [1, 120000], remediationComplete: true},
  ALERT_DELIVERY: {quotaAlert: true, staleWorkerAlert: true, orphanAlert: true, deliveryConfirmed: true},
  RUNBOOK_OWNERSHIP: {runbookCurrent: true, ownersAcknowledged: true},
  LIMITED_COHORT_APPROVAL: {runtimeApproved: true, commerceApproved: true, signedInCohortLimit: [1, 10]},
});
function controlSummary(report, baseDir) {
  const required = report.kind === 'DRILL' ? DRILL_CONTROLS : report.kind === 'ACTIVATION' ? ACTIVATION_CONTROLS : HANDOVER_CONTROLS;
  assert(Array.isArray(report.controls) && report.controls.length === required.length, 'All required control outcomes must be recorded'); const seen = new Set();
  for (const control of report.controls) {
    exact(control, ['control', 'outcome', 'owner', 'observedAt', 'file', 'sha256']); assert(required.includes(control.control) && !seen.has(control.control), 'Foreign or duplicate gate'); seen.add(control.control); assert(['PASS', 'UNMET'].includes(control.outcome)); owner(control.owner); const at = timestamp(control.observedAt); assert(at >= timestamp(report.startedAt) && at <= timestamp(report.endedAt)); safeName(control.file); digest(control.sha256);
    const bytes = boundedFile(resolve(baseDir, control.file), 65536); assert.equal(hash(bytes), control.sha256, 'Referenced control evidence changed'); const proof = parse(bytes);
    exact(proof, ['version', 'kind', 'purpose', 'environment', 'revision', 'control', 'observedAt', 'outcome', 'method', 'evidenceSha256', 'facts']); assert.equal(proof.version, 1); assert.equal(proof.kind, 'CONTROL_OBSERVATION');
    for (const field of ['purpose', 'environment', 'revision']) assert.equal(proof[field], report[field]); for (const field of ['control', 'observedAt', 'outcome']) assert.equal(proof[field], control[field]); assert(['CI', 'OPERATOR'].includes(proof.method)); if (proof.purpose === 'MEASUREMENT' && !['SECURITY_CI', 'CONTENT_RIGHTS_REPRODUCTION'].includes(proof.control)) assert.equal(proof.method, 'OPERATOR', 'Actual deployment and manual gates require operator observation'); digest(proof.evidenceSha256);
    const targets = CONTROL_FACTS[control.control]; exact(proof.facts, Object.keys(targets)); let passed = true;
    for (const [field, target] of Object.entries(targets)) {const fact = proof.facts[field]; if (Array.isArray(target)) {integer(fact, 0, 1000000000); passed &&= fact >= target[0] && fact <= target[1];} else {assert.equal(typeof fact, 'boolean'); passed &&= fact === target;}}
    if (control.outcome === 'PASS') assert(passed, 'A PASS label contradicts the measured control facts');
  }
  return {controls: report.controls.map(({control, outcome, owner}) => ({control, outcome, owner})), gates: Object.fromEntries(report.controls.map(c => [c.control, c.outcome === 'PASS'])), ...(report.kind === 'ACTIVATION' ? {runtimeAndCommerceIndependent: true, changesActivationFlags: false} : {})};
}
export function validateReport(report, {baseDir, environment, revision, now = Date.now(), release = true, publicKeyBytes} = {}) {
  exact(report, ['version', 'kind', 'purpose', 'environment', 'revision', 'startedAt', 'endedAt', 'collector', ...(['SOAK', 'LOAD'].includes(report.kind) ? ['logs'] : ['controls']), ...(report.kind === 'HANDOVER' ? ['reports'] : [])]);
  assert.equal(report.version, 1); assert(['SOAK', 'LOAD', 'DRILL', 'ACTIVATION', 'HANDOVER'].includes(report.kind)); assert(['MEASUREMENT', 'TEST'].includes(report.purpose)); assert.equal(report.collector, 'getlancer-v52-ops-1');
  assert(typeof environment === 'string' && environment === report.environment, 'Supply the expected deployment environment'); assert(/^[a-f0-9]{40}$/.test(revision) && revision === report.revision, 'Supply the exact reviewed source revision');
  const started = timestamp(report.startedAt), ended = timestamp(report.endedAt); assert(started <= ended && ended <= now, 'Future or inverted evidence period'); assert(ended >= now - LIMITS.freshnessMs, 'Evidence is stale'); if (release) assert.equal(report.purpose, 'MEASUREMENT', 'Synthetic TEST fixtures cannot certify a release');
  let summary;
  if (['SOAK', 'LOAD'].includes(report.kind)) {
    assert(Array.isArray(report.logs) && report.logs.length > 0 && report.logs.length <= 100); const names = new Set(), journals = []; let totalBytes = 0, totalObservations = 0;
    for (const log of report.logs) {exact(log, ['file', 'sha256']); safeName(log.file); assert(!names.has(log.file), 'Duplicate evidence file'); names.add(log.file); digest(log.sha256); const journal = readJournal(resolve(baseDir, log.file)); totalBytes += journal.byteLength; totalObservations += journal.observations.length; assert(totalBytes <= LIMITS.fileBytes && totalObservations <= LIMITS.observations, 'Combined collector cohort exceeded its bounded budget'); assert.equal(journal.sha256, log.sha256, 'Journal hash rejected'); for (const key of ['kind', 'purpose', 'environment', 'revision']) assert.equal(journal.header[key], report[key], 'Journal provenance differs'); journals.push(journal);}
    assert.equal(journals[0].header.startedAt, report.startedAt); assert.equal(journals.at(-1).lastAt, ended); summary = report.kind === 'SOAK' ? soakSummary(journals) : loadSummary(journals);
  } else {
    summary = controlSummary(report, baseDir);
    if (report.kind === 'HANDOVER') {
      assert(Array.isArray(report.reports) && report.reports.length === 4, 'Handover must reference each independent launch lane'); const seen = new Set();
      const dependencies = report.reports.map(dependency => {exact(dependency, ['kind', 'file', 'sha256']); assert(['LOAD', 'SOAK', 'DRILL', 'ACTIVATION'].includes(dependency.kind) && !seen.has(dependency.kind)); seen.add(dependency.kind); safeName(dependency.file); digest(dependency.sha256); const path = resolve(baseDir, dependency.file); assert.equal(hash(boundedFile(path, 131072)), dependency.sha256); const result = verifyReportFile(path, {environment, revision, now, release, publicKeyBytes}); assert.equal(result.kind, dependency.kind); return result;});
      summary.dependencies = dependencies.map(({kind, releaseEligible}) => ({kind, releaseEligible})); summary.gates.dependentLaunchGates = dependencies.every(result => Object.values(result.gates).every(Boolean));
    }
  }
  const met = Object.values(summary.gates).every(Boolean); if (release) assert(met, 'Required release evidence gates are UNMET');
  return {result: release ? 'RELEASE_FACTS_VALIDATED' : 'MEASUREMENT_ONLY', kind: report.kind, purpose: report.purpose, environment, revision, startedAt: report.startedAt, endedAt: report.endedAt, releaseEligible: false, ...summary};
}
export function verifyReportFile(path, options) {const release = options.release !== false, report = openReport(boundedFile(path, 131072), options.publicKeyBytes, release), summary = validateReport(report, {...options, baseDir: dirname(resolve(path))}); return {...summary, result: release ? 'SIGNED_OPERATOR_EVIDENCE_VERIFIED' : 'MEASUREMENT_ONLY', releaseEligible: release};}
