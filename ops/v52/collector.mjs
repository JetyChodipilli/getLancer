/** Bounded read-only probes. Authentication is explicitly configured and never serialized. */
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {openSync, closeSync, writeFileSync} from 'node:fs';
import {performance} from 'node:perf_hooks';
import {basename, dirname, resolve} from 'node:path';
import {appendObservation, boundedFile, canonical, hash, integer, journalReport, LIMITS, origin, parse, readJournal, runtimeSnapshot, serverProfile, writeExclusive} from './evidence.mjs';

export const pause = milliseconds => new Promise(done => setTimeout(done, milliseconds));
async function until(target) {for (let remaining = target - performance.now(); remaining > 0; remaining = target - performance.now()) await pause(Math.ceil(remaining));}
export function argumentsFor(argv, allowed) {assert(argv.length % 2 === 0, 'Arguments require explicit values'); const result = {}; for (let i = 0; i < argv.length; i += 2) {assert(allowed.includes(argv[i]) && result[argv[i]] === undefined, 'Unknown or repeated argument'); result[argv[i]] = argv[i + 1];} return result;}
export function numberArg(value, min, max) {assert(typeof value === 'string' && /^(?:0|[1-9][0-9]*)$/.test(value), 'Use an integer argument'); const number = Number(value); integer(number, min, max); return number;}
function catalogue(config) {const value=config.catalogue ?? "products"; assert(["products","components","college-projects"].includes(value), "Choose a supported catalogue"); return value;}
function configuration(config, kind) {
  catalogue(config);
  assert(/^[a-f0-9]{40}$/.test(config.revision), 'Supply the actual deployed commit'); assert(/^[a-z][a-z0-9-]{1,63}$/.test(config.environment), 'Name the deployment environment');
  origin(config.origin); serverProfile(config.serverProfile); integer(config.timeoutMs, 1, LIMITS.timeoutMs); integer(config.durationMs, 100, kind === 'SOAK' ? LIMITS.sevenDaysMs + LIMITS.cadenceMs : 300000);
  assert(['MEASUREMENT', 'TEST'].includes(config.purpose ?? 'MEASUREMENT')); if (kind === 'SOAK') {integer(config.cadenceMs, 100, LIMITS.cadenceMs); assert(Math.floor(config.durationMs / config.cadenceMs) + 1 <= LIMITS.observations, 'Bound the observation cohort');} else {integer(config.rate, 1, LIMITS.loadRate); integer(config.concurrency, 1, LIMITS.loadConcurrency);}
}
async function boundedBody(response) {assert(response.body, 'Body required'); const reader = response.body.getReader(), chunks = []; let count = 0; try {for (;;) {const {done, value} = await reader.read(); if (done) break; count += value.byteLength; assert(count <= LIMITS.lineBytes, 'Response exceeded byte budget'); chunks.push(value);} return Buffer.concat(chunks);} finally {await reader.cancel().catch(() => {});}}
const failure = (status, outcome, durationMs, admin) => admin ? {status, outcome, durationMs, snapshot: null} : {status, outcome, durationMs, bodySha256: null, serverDurationMs: null, eligibleRows: null};
function backendTime(value) {assert(typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.[0-9]{1,9})?Z$/.test(value)); const date = new Date(value); assert(Number.isFinite(date.getTime()) && date.toISOString().slice(0, 19) === value.slice(0, 19)); return date.toISOString();}
export const capacitySkip = () => failure(0, 'CAPACITY_SKIP', 0, false);
export async function requestProbe(url, {timeoutMs, adminCookie, fetchImpl = fetch} = {}) {
  integer(timeoutMs, 1, LIMITS.timeoutMs); const admin = adminCookie !== undefined, started = performance.now(), controller = new AbortController(), timer = setTimeout(() => controller.abort(), timeoutMs); let status = 0;
  try {
    const response = await fetchImpl(url, {method: 'GET', redirect: 'error', signal: controller.signal, headers: {Accept: 'application/json', ...(admin ? {Cookie: adminCookie} : {})}}); status = response.status;
    if (status !== 200) {await response.body?.cancel().catch(() => {}); return failure(status, 'HTTP_ERROR', performance.now() - started, admin);}
    const bytes = await boundedBody(response), body = parse(bytes), durationMs = performance.now() - started;
    if (admin) {
      assert(body && body.runtime && body.window && body.window.to); const raw = body.runtime;
      const snapshot = runtimeSnapshot({observedAt: backendTime(body.window.to), counterEpoch: backendTime(raw.counterEpoch), operatorEpoch: raw.operatorEpoch, measuredFrom: backendTime(raw.measuredFrom), admitted: raw.admitted, healthy: raw.healthy, pending: raw.pending, completed: raw.completed, infraFailures: raw.infraFailures, scenarioFailures: raw.scenarioFailures, quotaDenials: raw.quotaDenials, validationDenials: raw.validationDenials});
      return {status, outcome: 'OK', durationMs, snapshot};
    }
    assert(body && Array.isArray(body.items) && body.items.length <= 50); integer(body.totalItems, 0, 1000000000);
    const timing = response.headers.get('server-timing') ?? '', match = /(?:^|,)\s*app\s*;\s*dur=([0-9]+(?:\.[0-9]+)?)\s*(?=,|$)/.exec(timing); const serverDurationMs = match ? Number(match[1]) : null;
    assert(serverDurationMs === null || (Number.isFinite(serverDurationMs) && serverDurationMs >= 0 && serverDurationMs <= durationMs + 2));
    return {status, outcome: 'OK', durationMs, bodySha256: hash(bytes), serverDurationMs, eligibleRows: body.totalItems};
  } catch {return failure(status, controller.signal.aborted ? 'TIMEOUT' : 'INVALID_RESPONSE', performance.now() - started, admin);} finally {clearTimeout(timer);}
}
function startJournal(config, kind) {
  const startedAt = new Date().toISOString(), startedMono = performance.now();
  const header = {version: 1, kind, purpose: config.purpose ?? 'MEASUREMENT', environment: config.environment, revision: config.revision, origin: origin(config.origin), startedAt, segmentId: randomUUID(), serverProfile: config.serverProfile, config: kind === 'SOAK' ? {catalogue: catalogue(config), cadenceMs: config.cadenceMs, timeoutMs: config.timeoutMs} : {catalogue: catalogue(config), rate: config.rate, concurrency: config.concurrency, durationMs: config.durationMs, timeoutMs: config.timeoutMs}};
  const file = resolve(config.out + '.jsonl'); assert(/^[A-Za-z0-9][A-Za-z0-9_.-]{0,159}$/.test(basename(file)) && !basename(file).includes('..'), 'Use a safe output filename');
  const descriptor = openSync(file, 'wx', 0o600), first = canonical(header) + '\n'; writeFileSync(descriptor, first); let closed = false, size = Buffer.byteLength(first);
  const writer = {previous: hash(canonical(header)), append: line => {size += Buffer.byteLength(line); assert(size <= LIMITS.fileBytes, 'Collector journal exceeded its byte budget'); writeFileSync(descriptor, line);}};
  return {file, header, startedMono, writer, close: () => {if (!closed) {closeSync(descriptor); closed = true;}}};
}
function finishJournal(journal, config) {journal.close(); const read = readJournal(journal.file), report = journalReport([read]); const reportFile = resolve(config.out + '.json'); assert.equal(dirname(reportFile), dirname(journal.file)); writeExclusive(reportFile, canonical(report) + '\n'); return {reportFile, logFile: journal.file, observations: read.observations.length, purpose: report.purpose, releaseCertified: false};}
function cookieFromFile(path) {const value = new TextDecoder('utf-8', {fatal: true}).decode(boundedFile(path, 4096)).trim(); assert(value.length > 0 && value.length <= 4096 && !/[\u0000-\u001f\u007f]/.test(value), 'Invalid operator cookie'); return value;}
export async function monitor(config) {
  configuration(config, 'SOAK'); if (config.adminCookieFile) assert(config.adminConfigured === true, 'Explicit admin configuration required');
  const journal = startJournal(config, 'SOAK'); let sequence = 0;
  try {
    for (let scheduled = 0; scheduled <= config.durationMs; scheduled += config.cadenceMs) {
      await until(journal.startedMono + scheduled);
      const publicPromise = requestProbe(journal.header.origin + '/api/v1/' + catalogue(config) + '?page=0&size=1', {timeoutMs: config.timeoutMs, fetchImpl: config.fetchImpl});
      let runtimePromise = Promise.resolve(null);
      if (config.adminCookieFile) {
        // The operator may rotate a freshly authenticated cookie file; expiry is an observed failure.
        try {runtimePromise = requestProbe(journal.header.origin + '/api/v1/admin/operations', {timeoutMs: config.timeoutMs, adminCookie: cookieFromFile(config.adminCookieFile), fetchImpl: config.fetchImpl});} catch {runtimePromise = Promise.resolve({status: 0, outcome: 'INVALID_RESPONSE', durationMs: 0, snapshot: null});}
      }
      const [publicProbe, runtimeProbe] = await Promise.all([publicPromise, runtimePromise]);
      appendObservation(journal.writer, {sequence: ++sequence, sampledAt: new Date().toISOString(), elapsedMs: Math.round(performance.now() - journal.startedMono), publicProbe, runtimeProbe});
      if (performance.now() - journal.startedMono > config.durationMs + config.timeoutMs * 2) break;
    }
    return finishJournal(journal, config);
  } finally {journal.close();}
}
export async function load(config) {
  configuration(config, 'LOAD'); const journal = startJournal(config, 'LOAD'), pending = new Set(); let sequence = 0;
  const record = (scheduledElapsedMs, response) => appendObservation(journal.writer, {sequence: ++sequence, sampledAt: new Date().toISOString(), elapsedMs: Math.round(performance.now() - journal.startedMono), scheduledElapsedMs, response});
  try {
    const total = Math.floor(config.durationMs * config.rate / 1000); assert(total > 0 && total <= 6000, 'Bound the scheduled load cohort');
    for (let i = 0; i < total; i++) {
      const scheduled = Math.floor(i * 1000 / config.rate); await until(journal.startedMono + scheduled);
      if (pending.size >= config.concurrency || performance.now() - journal.startedMono - scheduled > 1000) {record(scheduled, capacitySkip()); continue;}
      const request = requestProbe(journal.header.origin + '/api/v1/' + catalogue(config) + '?page=' + i % 50 + '&size=20', {timeoutMs: config.timeoutMs, fetchImpl: config.fetchImpl}).then(response => record(scheduled, response)); pending.add(request); request.finally(() => pending.delete(request));
    }
    await Promise.all(pending); return finishJournal(journal, config);
  } finally {journal.close();}
}
