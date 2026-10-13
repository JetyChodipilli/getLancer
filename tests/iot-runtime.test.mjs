import {test} from 'node:test';
import assert from 'node:assert/strict';
import {connect} from 'node:net';
import {randomUUID} from 'node:crypto';
import {spawn} from 'node:child_process';
import {startIotRuntime, strictJson, validateReading} from '../labs/iot/runtime.mjs';
import {send, verifyIot} from '../scripts/verify-iot.mjs';

// Independent MQTT byte construction keeps wire assertions separate from production encoders.
const str = value => {const bytes = Buffer.from(value); return Buffer.concat([Buffer.from([bytes.length >> 8, bytes.length & 255]), bytes]);};
function frame(header, body = Buffer.alloc(0)) {const bytes = []; let n = body.length; do {let b = n % 128; n = Math.floor(n / 128); if (n) b |= 128; bytes.push(b);} while (n); return Buffer.concat([Buffer.from([header, ...bytes]), body]);}
const handshake = id => frame(0x10, Buffer.concat([str('MQTT'), Buffer.from([4, 2, 0, 30]), str('getlancer-publisher-' + id)]));
const telemetry = (topic, payload = {temperatureC: 35, humidityPercent: 65, thresholdC: 30}, header = 0x30) => frame(header, Buffer.concat([str(topic), Buffer.from(typeof payload === 'string' ? payload : JSON.stringify(payload))]));
async function peer(runtime, connectBytes = handshake(runtime.runId)) {
  const socket = connect(runtime.mqttPort, '127.0.0.1'); socket.on('error', () => {});
  const result = await new Promise((done, reject) => {const timer = setTimeout(() => {socket.destroy(); reject(Error('Wire handshake deadline'));}, 1600); socket.once('connect', () => socket.write(connectBytes)); socket.once('data', bytes => {clearTimeout(timer); done(bytes);}); socket.once('close', () => {clearTimeout(timer); done(null);});});
  return {socket, result};
}
async function closedAfter(socket, bytes) {return await new Promise((done, reject) => {const timer = setTimeout(() => {socket.destroy(); reject(Error('Rejected wire remained open'));}, 1600); socket.once('close', () => {clearTimeout(timer); done();}); socket.write(bytes);});}
async function until(predicate, timeout = 1200) {const start = performance.now(); while (!predicate()) {if (performance.now() - start > timeout) throw Error('State observation deadline'); await new Promise(done => setTimeout(done, 5));}}
async function rawHttp(runtime, bytes) {return await new Promise((done, reject) => {const socket = connect(runtime.httpPort, '127.0.0.1'), chunks = []; socket.on('connect', () => socket.write(bytes)); socket.on('data', bytes => chunks.push(bytes)); socket.on('error', error => {if (error.code !== 'ECONNRESET') reject(error);}); socket.setTimeout(1800, () => socket.destroy()); socket.on('close', () => done(Buffer.concat(chunks).toString()));});}
const fields = (runtime, operationId, extra = {}) => ({runId: runtime.runId, operationId, ...extra});

test('actual independent MQTT bytes update subscribed device and threshold equality; fragments/coalescing work', async t => {
  const runtime = await startIotRuntime(); t.after(() => runtime.close()); const {socket, result} = await peer(runtime); t.after(() => socket.destroy()); assert.deepEqual(result, Buffer.from([0x20, 2, 0, 0]));
  const bytes = telemetry(runtime.topic, {temperatureC: 30, humidityPercent: 0, thresholdC: 30}); socket.write(bytes.subarray(0, 1)); socket.write(bytes.subarray(1, 4)); socket.write(bytes.subarray(4)); await until(() => runtime.snapshot().sequence === 1); let state = runtime.snapshot(); assert.equal(state.actuatorOn, true); assert.equal(state.temperatureC, 30); assert.equal(state.humidityPercent, 0);
  socket.write(Buffer.concat([telemetry(runtime.topic, {temperatureC: -20, humidityPercent: 100, thresholdC: 60}), telemetry(runtime.topic, {temperatureC: 80, humidityPercent: 75, thresholdC: 0})])); await until(() => runtime.snapshot().sequence === 3); state = runtime.snapshot(); assert.deepEqual(state.messages.map(message => [message.temperatureC, message.actuatorOn]), [[30, true], [-20, false], [80, true]]); assert.equal(runtime.logs().filter(log => log.code === 'DEVICE_APPLIED').length, 3);
});

test('wire topics and client identities isolate two real concurrently running UUID sessions', async t => {
  const left = await startIotRuntime(), right = await startIotRuntime(); t.after(() => left.close()); t.after(() => right.close()); assert.notEqual(left.topic, right.topic);
  const foreign = await peer(left, handshake(right.runId)); assert.equal(foreign.result, null); assert.equal(left.snapshot().sequence, 0);
  const accepted = await peer(left); await closedAfter(accepted.socket, telemetry(right.topic)); assert.equal(left.snapshot().sequence, 0); assert.equal(right.snapshot().sequence, 0); assert(left.logs().some(log => log.code === 'FOREIGN_TOPIC'));
  assert.equal((await send(right, fields(left, 'state'))).status, 403); const valid = await send(right, fields(right, 'publish', {temperatureC: '10', humidityPercent: '20', thresholdC: '30'})); assert.equal(valid.status, 200); assert.equal(valid.value.state.actuatorOn, false); assert.equal(left.snapshot().sequence, 0);
});

test('malformed MQTT, QoS/retain/duplicate flags, oversize, bad JSON/numbers and unknown fields reject on real TCP', async t => {
  const runtime = await startIotRuntime(); t.after(() => runtime.close());
  const bad = [telemetry(runtime.topic, undefined, 0x31), telemetry(runtime.topic, undefined, 0x32), telemetry(runtime.topic, undefined, 0x38), telemetry(runtime.topic, '{"temperatureC":20,"humidityPercent":30,"thresholdC":40,"thresholdC":30}'), telemetry(runtime.topic, '{"temperatureC":20,"humidityPercent":30,"thresholdC":40,"\\u0074hresholdC":30}'), telemetry(runtime.topic, {temperatureC: 81, humidityPercent: 0, thresholdC: 0}), telemetry(runtime.topic, {temperatureC: 20, humidityPercent: 101, thresholdC: 30}), telemetry(runtime.topic, {temperatureC: 20, humidityPercent: 30, thresholdC: -1}), telemetry(runtime.topic, {temperatureC: 1.0000001, humidityPercent: 30, thresholdC: 30}), telemetry(runtime.topic, {temperatureC: 20, humidityPercent: 30, thresholdC: 30, secret: 'not permitted'}), Buffer.from([0x30, 0x81, 0x08]), Buffer.from([0x30, 0x80, 0]), Buffer.from([0x30, 0xff, 0xff, 0xff, 0xff]), frame(0x30, Buffer.concat([str(runtime.topic), Buffer.from([255])])), frame(0x82, Buffer.concat([Buffer.from([0, 1]), str('#'), Buffer.from([0])])), frame(0x50), frame(0xc0, Buffer.from([1]))];
  for (const bytes of bad) {const {socket, result} = await peer(runtime); assert.deepEqual(result, Buffer.from([0x20, 2, 0, 0])); await closedAfter(socket, bytes); assert.equal(runtime.snapshot().sequence, 0);}
  const noConnect = connect(runtime.mqttPort, '127.0.0.1'); noConnect.on('error', () => {}); await closedAfter(noConnect, telemetry(runtime.topic)); assert.equal(runtime.snapshot().sequence, 0);
  const malformed = await peer(runtime); await closedAfter(malformed.socket, telemetry(runtime.topic, '{"private-user-input\\n":0}')); assert(!JSON.stringify(runtime.logs()).includes('private-user-input'));
  const valid = await send(runtime, fields(runtime, 'publish', {temperatureC: '31', humidityPercent: '40', thresholdC: '30'})); assert.equal(valid.status, 200); assert.equal(valid.value.state.actuatorOn, true);
});

test('HTTP rejects foreign identity, origins, unknown/duplicate fields, bad UTF8/framing, invalid numeric bounds', async t => {
  const runtime = await startIotRuntime(); t.after(() => runtime.close()); const original = fields(runtime, 'publish', {temperatureC: '24', humidityPercent: '45', thresholdC: '30'}); assert.equal((await send(runtime, original)).status, 200);
  for (const [input, options, status] of [[{...original, runId: randomUUID()}, {}, 403], [{...original, topic: 'other'}, {}, 400], [Buffer.from(new URLSearchParams(original) + '&thresholdC=30'), {}, 400], [Buffer.from('runId=%ff'), {}, 400], [Buffer.from('runId=%zz'), {}, 400], [Buffer.from([255]), {}, 400], [original, {headers: {Origin: ''}}, 403], [original, {headers: {Origin: 'https://outside.invalid'}}, 403], [original, {headers: {'Content-Type': 'application/json'}}, 415], [original, {path: '/request?topic=foreign'}, 404], [original, {method: 'PUT'}, 405], [Buffer.alloc(1025, 65), {}, 413], [{...original, temperatureC: '1e3'}, {}, 400], [{...original, temperatureC: 'NaN'}, {}, 400], [{...original, temperatureC: '81'}, {}, 400], [{...original, humidityPercent: '-1'}, {}, 400], [{...original, thresholdC: '61'}, {}, 400]]) {const reply = await send(runtime, input, options); assert.equal(reply.status, status); assert.deepEqual(Object.keys(reply.value), ['error']); assert.equal(runtime.snapshot().sequence, 1);}
  const prefix = `POST /request HTTP/1.1\r\nHost: 127.0.0.1:${runtime.httpPort}\r\nContent-Type: application/x-www-form-urlencoded\r\n`;
  for (const headers of ['Content-Length: 0\r\nContent-Length: 0\r\n', 'Transfer-Encoding: chunked\r\nContent-Length: 0\r\n', 'Transfer-Encoding: chunked\r\n']) assert.match(await rawHttp(runtime, prefix + headers + '\r\n'), /HTTP\/1\.1 400/);
  // fetch normalizes Host, so inspect the actual raw TCP header rather than a false negative.
  assert.match(await rawHttp(runtime, `GET /health HTTP/1.1\r\nHost: localhost:${runtime.httpPort}\r\n\r\n`), /HTTP\/1\.1 403/);
  assert(!JSON.stringify(runtime.logs()).includes('outside.invalid'));
});

test('real disconnect/reconnect/reset preserve run isolation and monotonic history', async t => {
  const runtime = await startIotRuntime(); t.after(() => runtime.close()); const publish = fields(runtime, 'publish', {temperatureC: '55', humidityPercent: '80', thresholdC: '30'});
  assert.equal((await send(runtime, publish)).value.state.sequence, 1); assert.equal((await send(runtime, fields(runtime, 'disconnect'))).value.state.connected, false); const rejected = await peer(runtime); await closedAfter(rejected.socket, telemetry(runtime.topic)); assert.equal(runtime.snapshot().sequence, 2); assert.equal((await send(runtime, publish)).status, 409);
  const connected = await send(runtime, fields(runtime, 'connect')); assert.equal(connected.value.state.sequence, 3); assert.equal(connected.value.state.temperatureC, 55);
  for (let i = 0; i < 3; i++) {const reset = await send(runtime, fields(runtime, 'reset')); assert.equal(reset.status, 200); assert.equal(reset.value.state.sequence, 4 + i); assert.equal(reset.value.state.runId, runtime.runId); assert.equal(reset.value.state.temperatureC, 24); assert.equal(reset.value.state.messages.length, 0); assert.equal(reset.value.state.connected, true);}
  assert.equal((await send(runtime, publish)).value.state.sequence, 7);
});

test('packet deadline defeats dribbled MQTT and runtime idle/total lifetime clears state/logs/sockets', async t => {
  const runtime = await startIotRuntime({idleMs: 3000, lifetimeMs: 5000}); t.after(() => runtime.close()); const {socket} = await peer(runtime); let interval; const started = performance.now();
  const closing = new Promise(done => socket.once('close', done)); socket.write(Buffer.from([0x30, 100])); interval = setInterval(() => socket.write(Buffer.from([1])), 100); await closing; clearInterval(interval); assert(performance.now() - started < 1500); assert(runtime.logs().some(log => log.code === 'PACKET_TIMEOUT'));
  const idle = await startIotRuntime({idleMs: 500}); t.after(() => idle.close()); await until(() => idle.closed(), 1200); await idle.close(); await until(() => idle.socketCount() === 0); assert.deepEqual(idle.logs(), []); assert.equal(idle.snapshot().messages.length, 0);
  const total = await startIotRuntime({lifetimeMs: 500, idleMs: 1000}); t.after(() => total.close()); const keepAlive = setInterval(() => send(total, fields(total, 'state')).catch(() => {}), 50); await until(() => total.closed(), 1200); clearInterval(keepAlive); await until(() => total.socketCount() === 0); assert.equal(total.snapshot().sequence, 0);
});

test('absolute HTTP header and body deadlines stop dribbled bytes, independent of traffic refresh', async t => {
  const runtime = await startIotRuntime({idleMs: 5000, lifetimeMs: 10000}); t.after(() => runtime.close());
  async function dribble(initial) {
    const socket = connect(runtime.httpPort, '127.0.0.1'), parts = []; let interval; socket.on('error', () => {}); socket.on('data', bytes => parts.push(bytes)); const started = performance.now();
    await new Promise(done => {socket.once('connect', () => {socket.write(initial); interval = setInterval(() => socket.write('x'), 100);}); socket.once('close', done);}); clearInterval(interval); assert(performance.now() - started < 1500); return Buffer.concat(parts).toString();
  }
  assert.match(await dribble(`POST /request HTTP/1.1\r\nHost: 127.0.0.1:${runtime.httpPort}\r\nX-Dribble: `), /HTTP\/1\.1 408/);
  assert.match(await dribble(`POST /request HTTP/1.1\r\nHost: 127.0.0.1:${runtime.httpPort}\r\nContent-Type: application/x-www-form-urlencoded\r\nContent-Length: 100\r\n\r\n`), /HTTP\/1\.1 408/); assert.equal(runtime.snapshot().sequence, 0);
});

test('action/attempt/history/socket budgets are finite, burst admission cannot exceed action cap', async t => {
  const runtime = await startIotRuntime({idleMs: 10000}); t.after(() => runtime.close()); const {socket} = await peer(runtime); t.after(() => socket.destroy());
  for (let i = 0; i < 98; i++) {socket.write(telemetry(runtime.topic, {temperatureC: i % 60, humidityPercent: 40, thresholdC: 30})); await until(() => runtime.snapshot().sequence === i + 1);}
  const closing = new Promise(done => socket.once('close', done)); socket.write(Buffer.concat([telemetry(runtime.topic), telemetry(runtime.topic), telemetry(runtime.topic)])); await closing; await until(() => runtime.snapshot().sequence === 100);
  assert.equal(runtime.snapshot().messages.length, 20); assert.equal(runtime.snapshot().events.length, 20); assert.equal(runtime.snapshot().sequence, 100); const bounded = await send(runtime, fields(runtime, 'state')); assert.equal(bounded.status, 200); assert(bounded.bytes < 20000); assert.equal(bounded.value.state.events.length, 20); assert.equal((await send(runtime, fields(runtime, 'reset'))).status, 400);
  const attempts = await startIotRuntime(); t.after(() => attempts.close()); let last; for (let i = 0; i < 201 && !attempts.closed(); i++) last = await send(attempts, fields(attempts, 'state')); assert.equal(last.status, 429); await until(() => attempts.closed()); await until(() => attempts.socketCount() === 0);
});

test('four broker sockets include the device; excess connection is closed while admitted clients work', async t => {
  const runtime = await startIotRuntime(); t.after(() => runtime.close()); const peers = [];
  for (let i = 0; i < 3; i++) {const connected = await peer(runtime); assert.deepEqual(connected.result, Buffer.from([0x20, 2, 0, 0])); peers.push(connected.socket); t.after(() => connected.socket.destroy());}
  const excess = await peer(runtime); assert.equal(excess.result, null); peers[0].write(telemetry(runtime.topic)); await until(() => runtime.snapshot().sequence === 1); assert.equal(runtime.snapshot().actuatorOn, true);
});

test('strict duplicate JSON and number checks reject aliases/depth/nonfinite without prototype pollution', () => {
  const value = strictJson('{"temperatureC":24,"humidityPercent":45,"thresholdC":30}'); assert.deepEqual(validateReading(value), {temperatureC: 24, humidityPercent: 45, thresholdC: 30});
  for (const text of ['{"a":1,"\\u0061":2}', '{"n":1e999}', '{"a":{"b":{"c":{"d":1}}}}', '[1,2]', '{"a":1} trailing', 'x'.repeat(513)]) assert.throws(() => strictJson(text)); const safe = strictJson('{"__proto__":{"polluted":true}}'); assert.equal({}.polluted, undefined); assert.equal(Object.getPrototypeOf(safe), Object.prototype); assert.throws(() => validateReading(safe));
});

test('current source and clean extracted archive reproduce real broker/device state and clean shutdown', async () => {
  const result = await verifyIot(); assert.equal(result.measurements.currentActions, 5); assert.equal(result.measurements.cleanExtractedActions, 5); assert.equal(result.measurements.cleanupEmpty, true); assert.equal(result.measurements.physicalProof, null); assert(result.measurements.maxResponseBytes < 20000);
});

test('CLI binds requested ephemeral loopback ports, reports source identity and exits on expiry without files', async () => {
  const child = spawn(process.execPath, ['labs/iot/runtime.mjs'], {env: {...process.env, LAB_RUN_ID: randomUUID(), LAB_MQTT_PORT: '0', LAB_HTTP_PORT: '0', LAB_LIFETIME_MS: '500', LAB_IDLE_MS: '500'}, stdio: ['ignore', 'pipe', 'pipe']}); let stdout = '', stderr = ''; child.stdout.on('data', bytes => stdout += bytes); child.stderr.on('data', bytes => stderr += bytes); const code = await new Promise((done, reject) => {const timer = setTimeout(() => {child.kill('SIGKILL'); reject(Error('CLI expiry deadline'));}, 2200); child.once('exit', code => {clearTimeout(timer); done(code);});}); assert.equal(code, 0, stderr); assert.equal(stderr, ''); assert.match(stdout, /^IOT_READY /); const ready = JSON.parse(stdout.slice(10)); assert.equal(ready.mode, 'LOCAL_MQTT'); assert(ready.mqttPort > 0 && ready.httpPort > 0); assert.match(ready.sourceSha256, /^[a-f0-9]{64}$/);
});

test('free client command publishes changed readings against the actual runtime and rejects unsupported options', async t => {
  const runtime = await startIotRuntime(); t.after(() => runtime.close());
  async function command(args) {const child = spawn(process.execPath, ['labs/iot/client.mjs', ...args], {env: {...process.env, LAB_HTTP_PORT: String(runtime.httpPort)}, stdio: ['ignore', 'pipe', 'pipe']}); let stdout = '', stderr = ''; child.stdout.on('data', bytes => stdout += bytes); child.stderr.on('data', bytes => stderr += bytes); const code = await new Promise((done, reject) => {const timer = setTimeout(() => {child.kill('SIGKILL'); reject(Error('Client deadline'));}, 3000); child.once('exit', code => {clearTimeout(timer); done(code);});}); return {code, stdout, stderr};}
  const published = await command([runtime.runId, 'publish', '44', '63', '30']); assert.equal(published.code, 0, published.stderr); const state = JSON.parse(published.stdout).state; assert.equal(state.temperatureC, 44); assert.equal(state.humidityPercent, 63); assert.equal(state.actuatorOn, true); assert.equal(runtime.snapshot().sequence, 1);
  for (const args of [[randomUUID(), 'state'], [runtime.runId, 'publish', '81', '40', '30'], [runtime.runId, 'state', 'ignored-extra'], [runtime.runId, 'arbitrary-command']]) {const rejected = await command(args); assert.equal(rejected.code, 1); assert.match(rejected.stderr, /^IOT_CLIENT_REJECTED /); assert.equal(runtime.snapshot().sequence, 1);}
});
