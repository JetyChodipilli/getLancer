/** Actual TCP MQTT/HTTP acceptance and independent numeric/state oracle. */
import assert from 'node:assert/strict';
import {mkdtemp, mkdir, writeFile, readdir, rm} from 'node:fs/promises';
import {join, dirname, resolve} from 'node:path';
import {tmpdir} from 'node:os';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {randomUUID} from 'node:crypto';
import {sourceFiles, archiveFor, unpackArchive, sha, base} from './build-iot-material.mjs';

export async function send(runtime, fields, options = {}) {
  const body = fields instanceof Uint8Array ? fields : new URLSearchParams(fields);
  const response = await fetch(`http://127.0.0.1:${runtime.httpPort}${options.path ?? '/request'}`, {method: options.method ?? 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded', ...options.headers}, body: (options.method ?? 'POST') === 'GET' ? undefined : body, signal: AbortSignal.timeout(2500)});
  const text = await response.text(); assert(Buffer.byteLength(text) <= 20000, 'IoT response budget exceeded'); return {status: response.status, value: text ? JSON.parse(text) : null, bytes: Buffer.byteLength(text)};
}
function stateExpected(state, expected) {
  assert.deepEqual(Object.keys(state).sort(), ['runId', 'connected', 'temperatureC', 'humidityPercent', 'thresholdC', 'actuatorOn', 'sequence', 'topic', 'messages', 'events'].sort());
  for (const [name, value] of Object.entries(expected)) assert.equal(state[name], value, 'Independent state mismatch: ' + name);
  assert.equal(state.topic, `getlancer/${state.runId}/telemetry`); assert(state.messages.length <= 20 && state.events.length <= 20);
}
export async function exerciseRuntime(startIotRuntime, expectedHash) {
  const runId = randomUUID(), started = performance.now(), runtime = await startIotRuntime({runId}); let maxResponseBytes = 0;
  const request = async (operationId, input = {}) => {const response = await send(runtime, {runId, operationId, ...input}); maxResponseBytes = Math.max(maxResponseBytes, response.bytes); return response;};
  try {
    const health = await send(runtime, {}, {method: 'GET', path: '/health'}); assert.equal(health.status, 200); assert.equal(health.value.sourceSha256, expectedHash); assert.equal(health.value.physicalProof, null); assert.equal(health.value.mode, 'LOCAL_MQTT');
    let response = await request('state'); assert.equal(response.status, 200); stateExpected(response.value.state, {sequence: 0, connected: true, temperatureC: 24, humidityPercent: 45, thresholdC: 30, actuatorOn: false});
    response = await request('publish', {temperatureC: '24', humidityPercent: '45', thresholdC: '30'}); assert.equal(response.status, 200); stateExpected(response.value.state, {sequence: 1, temperatureC: 24, humidityPercent: 45, thresholdC: 30, actuatorOn: false});
    response = await request('publish', {temperatureC: '40', humidityPercent: '70', thresholdC: '30'}); assert.equal(response.status, 200); stateExpected(response.value.state, {sequence: 2, temperatureC: 40, humidityPercent: 70, thresholdC: 30, actuatorOn: true}); assert.deepEqual(response.value.logs.map(log => log.code), ['BROKER_FORWARDED', 'DEVICE_APPLIED', 'BROKER_FORWARDED', 'DEVICE_APPLIED']);
    response = await request('disconnect'); assert.equal(response.status, 200); stateExpected(response.value.state, {sequence: 3, connected: false, temperatureC: 40}); assert.equal((await request('publish', {temperatureC: '20', humidityPercent: '40', thresholdC: '30'})).status, 409); assert.equal(runtime.snapshot().sequence, 3);
    response = await request('connect'); assert.equal(response.status, 200); stateExpected(response.value.state, {sequence: 4, connected: true, temperatureC: 40});
    response = await request('reset'); assert.equal(response.status, 200); stateExpected(response.value.state, {sequence: 5, connected: true, temperatureC: 24, humidityPercent: 45, thresholdC: 30, actuatorOn: false}); assert.equal(response.value.state.messages.length, 0); assert.deepEqual(response.value.state.events.map(event => event.type), ['publish', 'publish', 'disconnect', 'connect', 'reset']);
    assert.equal((await send(runtime, {runId: randomUUID(), operationId: 'state'})).status, 403);
    assert.equal((await request('publish', {temperatureC: '81', humidityPercent: '40', thresholdC: '30'})).status, 400); assert.equal(runtime.snapshot().sequence, 5);
    return {maxResponseBytes, actions: 5, elapsedMs: Math.ceil(performance.now() - started)};
  } finally {await runtime.close(); assert.equal(runtime.closed(), true); assert.equal(runtime.socketCount(), 0); assert.deepEqual(runtime.logs(), []); assert.equal(runtime.snapshot().connected, false); assert.equal(runtime.snapshot().events.length, 0);}
}
export async function verifyIot() {
  const {files} = await sourceFiles(), archive = archiveFor(files), sourceSha256 = sha(files.find(([name]) => name === 'runtime.mjs')[1]), archiveSha256 = sha(archive), {startIotRuntime} = await import(pathToFileURL(join(base, 'runtime.mjs')).href);
  const current = await exerciseRuntime(startIotRuntime, sourceSha256), temporary = await mkdtemp(join(tmpdir(), 'getlancer-iot-source-'));
  let clean;
  try {
    for (const [name, bytes] of unpackArchive(archive, files)) {const path = join(temporary, name); await mkdir(dirname(path), {recursive: true}); await writeFile(path, bytes);}
    const extracted = await import(pathToFileURL(join(temporary, 'runtime.mjs')).href); clean = await exerciseRuntime(extracted.startIotRuntime, sourceSha256);
    assert.deepEqual((await readdir(temporary)).sort(), ['BOM.md', 'License.md', 'PROTOCOL.md', 'README.md', 'WIRING.md', 'client.mjs', 'firmware', 'runtime.mjs'].sort(), 'Runtime wrote unexpected files');
    const after = await sourceFiles(); assert.equal(sha(archiveFor(after.files)), archiveSha256, 'IoT source archive changed during verification');
  } finally {await rm(temporary, {recursive: true, force: true});}
  return {sourceSha256, archiveSha256, measurements: {currentActions: current.actions, cleanExtractedActions: clean.actions, maxResponseBytes: Math.max(current.maxResponseBytes, clean.maxResponseBytes), currentElapsedMs: current.elapsedMs, cleanElapsedMs: clean.elapsedMs, cleanupEmpty: true, physicalProof: null}};
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {assert.equal(process.argv.length, 2, 'Unknown IoT verifier argument'); console.log('IOT_PROTOCOL_OK ' + JSON.stringify(await verifyIot()));} catch (error) {console.error('IOT_PROTOCOL_REJECTED ' + error.message); process.exitCode = 1;}
}
