/** Validate actual signed operator measurements; never create samples or start a provider. */
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {dirname, resolve} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {readEvidence, verifiedPayload, verifyBundle} from './certify.mjs';

const languages = ['java', 'typescript', 'python'];
const hash = /^[a-f0-9]{64}$/;
const uuid = /^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/;
function exact(value, names) {
  assert(value && typeof value === 'object' && !Array.isArray(value), 'Expected an evidence object');
  assert.deepEqual(Object.keys(value).sort(), [...names].sort(), 'Unsupported evidence fields');
}
function boundedText(value, max) {assert(typeof value === 'string' && value.length > 0 && value.length <= max && !/[\u0000-\u001f\u007f]/.test(value), 'Invalid evidence text');}
// Nearest-rank percentile, calculated independently from each raw cohort.
const p95 = values => [...values].sort((a, b) => a - b)[Math.ceil(values.length * .95) - 1];

export function summarizeBenchmark(report, admission, sourceHashes, now = Date.now()) {
  exact(report, ['version', 'mode', 'provider', 'operatorEpoch', 'evidenceSha256', 'measuredAt', 'hardware', 'adapters']);
  assert.equal(report.version, 1); assert.equal(report.mode, 'HOSTED_EXECUTION');
  assert.equal(report.provider, admission.provider); assert.equal(report.operatorEpoch, admission.operatorEpoch);
  assert.equal(report.evidenceSha256, admission.evidenceSha256);
  boundedText(report.hardware, 500); boundedText(report.measuredAt, 40);
  const measured = Date.parse(report.measuredAt);
  assert(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{3})?Z$/.test(report.measuredAt) && Number.isFinite(measured) && new Date(measured).toISOString().slice(0, 19) === report.measuredAt.slice(0, 19));
  assert(measured <= now && measured >= now - 31 * 86400000, 'Measurements must be current');
  assert(Array.isArray(report.adapters) && report.adapters.length === 3);
  const seenLanguages = new Set(), runIds = new Set(), occupancy = [];
  const adapters = report.adapters.map(adapter => {
    exact(adapter, ['language', 'sourceSha256', 'imageDigest', 'samples']);
    assert(languages.includes(adapter.language) && !seenLanguages.has(adapter.language)); seenLanguages.add(adapter.language);
    assert(hash.test(adapter.sourceSha256) && adapter.sourceSha256.length === 64 && adapter.sourceSha256 === sourceHashes[adapter.language], 'Source version changed');
    assert(typeof adapter.imageDigest === 'string' && /^sha256:[a-f0-9]{64}$/.test(adapter.imageDigest) && adapter.imageDigest.length === 71, 'Pin an immutable image');
    assert(Array.isArray(adapter.samples) && adapter.samples.length >= 100 && adapter.samples.length <= 1000, 'Require 100 fresh runs per adapter');
    const warm = [], cold = [], queue = [];
    for (const sample of adapter.samples) {
      exact(sample, ['runId', 'kind', 'queuedAtMs', 'admittedAtMs', 'readyAtMs', 'cleanedAtMs']);
      assert(typeof sample.runId === 'string' && sample.runId.length === 36 && uuid.test(sample.runId) && !runIds.has(sample.runId), 'Fresh run identities must be unique'); runIds.add(sample.runId);
      assert(['WARM', 'COLD'].includes(sample.kind));
      for (const field of ['queuedAtMs', 'admittedAtMs', 'readyAtMs', 'cleanedAtMs']) assert(Number.isSafeInteger(sample[field]) && sample[field] >= now - 31 * 86400000 && sample[field] <= measured);
      assert(sample.queuedAtMs <= sample.admittedAtMs && sample.admittedAtMs <= sample.readyAtMs && sample.readyAtMs < sample.cleanedAtMs);
      assert(sample.cleanedAtMs - sample.admittedAtMs <= 300000, 'A run exceeded the hard lease');
      (sample.kind === 'WARM' ? warm : cold).push(sample.readyAtMs - sample.admittedAtMs);
      queue.push(sample.admittedAtMs - sample.queuedAtMs);
      occupancy.push([sample.admittedAtMs, 1], [sample.cleanedAtMs, -1]);
    }
    assert(warm.length && cold.length, 'Report both startup classes');
    const warmP95Ms = p95(warm), coldP95Ms = p95(cold);
    assert(warmP95Ms <= 5000 && coldP95Ms <= 30000, 'Startup target exceeded');
    return {language: adapter.language, sourceSha256: adapter.sourceSha256, imageDigest: adapter.imageDigest, freshRuns: adapter.samples.length, warmSamples: warm.length, coldSamples: cold.length, warmP95Ms, coldP95Ms, queueP95Ms: p95(queue)};
  });
  let active = 0, peak = 0;
  for (const [, delta] of occupancy.sort((a, b) => a[0] - b[0] || a[1] - b[1])) {active += delta; peak = Math.max(peak, active);}
  assert(peak <= 10 && active === 0, 'More than ten admitted runs overlap');
  return {provider: report.provider, operatorEpoch: report.operatorEpoch, evidenceSha256: report.evidenceSha256, measuredAt: report.measuredAt, hardware: report.hardware, peakAdmittedRuns: peak, adapters};
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    const [reportPath, admissionPath, inventoryPath, publicKeyPath, operatorEpoch] = process.argv.slice(2);
    assert(process.argv.length === 7, 'Supply signed report, admission, inventory, public key and epoch');
    // Both envelopes must verify against the same captured key, even if its path is replaced later.
    const publicKeyBytes = readEvidence(publicKeyPath);
    const admission = verifyBundle({admissionPath, inventoryPath, publicKeyBytes, operatorEpoch});
    const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
    const sources = {java: 'labs/java/LabServer.java', typescript: 'labs/typescript/server.ts', python: 'labs/python/server.py'};
    const hashes = Object.fromEntries(languages.map(language => [language, createHash('sha256').update(readFileSync(resolve(root, sources[language]))).digest('hex')]));
    const report = verifiedPayload(readEvidence(reportPath), publicKeyBytes);
    const summary = summarizeBenchmark(report, admission, hashes);
    process.stdout.write(JSON.stringify({result: 'HOSTED_BENCHMARK_ARTIFACTS_VERIFIED', ...summary}) + '\n');
  } catch {process.stderr.write('Benchmark evidence rejected. Require current signed provider evidence, source pins, fresh raw runs, both startup classes and passing concurrency/latency limits.\n'); process.exitCode = 1;}
}
