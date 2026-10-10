import test from 'node:test';
import assert from 'node:assert/strict';
import {summarizeBenchmark} from '../ops/labs/check-v48-benchmark.mjs';

// Protocol fixtures only: no hosted run occurs and no benchmark report is published.
const now = Date.parse('2026-10-09T12:00:00Z');
const admission = {provider: 'Protocol fixture', operatorEpoch: '11111111-1111-4111-8111-111111111111', evidenceSha256: 'a'.repeat(64)};
const hashes = {java: 'b'.repeat(64), typescript: 'c'.repeat(64), python: 'd'.repeat(64)};
function fixture() {
  let cursor = now - 86400000, id = 0;
  return {version: 1, mode: 'HOSTED_EXECUTION', ...admission, measuredAt: '2026-10-09T12:00:00Z', hardware: 'Protocol fixture hardware only', adapters: Object.keys(hashes).map(language => ({language, sourceSha256: hashes[language], imageDigest: 'sha256:' + 'e'.repeat(64), samples: Array.from({length: 100}, (_, i) => {
    const queuedAtMs = cursor; cursor += 60000;
    return {runId: (++id).toString(16).padStart(8, '0') + '-1111-4111-8111-111111111111', kind: i % 2 ? 'COLD' : 'WARM', queuedAtMs, admittedAtMs: queuedAtMs + 100, readyAtMs: queuedAtMs + (i % 2 ? 25100 : 2100), cleanedAtMs: queuedAtMs + 30000};
  })}))};
}

test('raw protocol cohorts independently produce nearest-rank p95 and queue/concurrency counts', () => {
  const result = summarizeBenchmark(fixture(), admission, hashes, now);
  assert.equal(result.peakAdmittedRuns, 1);
  for (const adapter of result.adapters) assert.deepEqual([adapter.freshRuns, adapter.warmSamples, adapter.coldSamples, adapter.warmP95Ms, adapter.coldP95Ms, adapter.queueP95Ms], [100, 50, 50, 2000, 25000, 100]);
});
test('replay, foreign certification, stale source, missing classes, undersampling and reused runs reject', () => {
  for (const change of [r => {r.mode = 'REPLAY';}, r => {r.provider = 'Other';}, r => {r.operatorEpoch = 'other';}, r => {r.evidenceSha256 = 'f'.repeat(64);}, r => {r.adapters[0].sourceSha256 = 'f'.repeat(64);}, r => {r.adapters[0].imageDigest = 'latest';}, r => {r.adapters.pop();}, r => {r.adapters[1].language = 'java';}, r => {r.adapters[0].samples.pop();}, r => {r.adapters[0].samples.forEach(s => s.kind = 'WARM');}, r => {r.adapters[0].samples[1].runId = r.adapters[0].samples[0].runId;}, r => {r.measuredAt = '2026-11-01T12:00:00Z';}, r => {r.measuredAt = '2026-02-31T12:00:00Z';}, r => {r.secret = 'extra';}]) {
    const report = fixture(); change(report); assert.throws(() => summarizeBenchmark(report, admission, hashes, now));
  }
  const stale = fixture(); stale.measuredAt = '2026-10-08T23:00:00Z';
  const start = now - 31 * 86400000 - 60000;
  Object.assign(stale.adapters[0].samples[0], {queuedAtMs: start, admittedAtMs: start + 100, readyAtMs: start + 2100, cleanedAtMs: start + 30000});
  assert.throws(() => summarizeBenchmark(stale, admission, hashes, now));
});
test('raw startup times, failed targets, invalid leases and actual overlapping reservations reject', () => {
  for (const change of [r => {r.adapters[0].samples.forEach(s => {s.readyAtMs = s.admittedAtMs + (s.kind === 'WARM' ? 5001 : 30001); s.cleanedAtMs = s.readyAtMs + 1;});}, r => {r.adapters[0].samples[0].readyAtMs = r.adapters[0].samples[0].admittedAtMs - 1;}, r => {r.adapters[0].samples[0].cleanedAtMs = r.adapters[0].samples[0].admittedAtMs + 300001;}, r => {r.adapters[0].samples[0].queuedAtMs = 1.5;}, r => {const first = r.adapters[0].samples[0]; for (const sample of r.adapters[0].samples.slice(0, 11)) Object.assign(sample, {queuedAtMs: first.queuedAtMs, admittedAtMs: first.admittedAtMs, readyAtMs: first.admittedAtMs + 2000, cleanedAtMs: first.admittedAtMs + 3000});}]) {
    const report = fixture(); change(report); assert.throws(() => summarizeBenchmark(report, admission, hashes, now));
  }
});
