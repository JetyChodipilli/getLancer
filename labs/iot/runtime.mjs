/** Original MIT loopback MQTT 3.1.1 QoS0 teaching runtime. No physical or hosted execution. */
import {createServer as tcpServer, connect} from 'node:net';
import {createServer as httpServer} from 'node:http';
import {createHash, randomUUID} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {resolve} from 'node:path';

export const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
export const LIMITS = Object.freeze({packetBytes: 1024, bodyBytes: 1024, actions: 100, attempts: 200, history: 20, sockets: 4, packetDeadlineMs: 1000});
const DEFAULTS = {temperatureC: 24, humidityPercent: 45, thresholdC: 30};
export function strictJson(raw) {
  if (typeof raw !== 'string' || Buffer.byteLength(raw) > 512) throw Error('PAYLOAD_SIZE');
  let at = 0, nodes = 0;
  const ws = () => {while (/[ \n\r\t]/.test(raw[at] ?? '!')) at++;};
  function string() {const start = at++; while (at < raw.length) {if (raw[at] === '\\') {at += 2; continue;} if (raw[at++] === '"') return JSON.parse(raw.slice(start, at));} throw Error('JSON_STRING');}
  function value(depth) {
    if (depth > 3 || ++nodes > 20) throw Error('JSON_LIMIT'); ws();
    if (raw[at] === '"') return string();
    if (raw[at] === '{') {at++; ws(); const out = {}, keys = new Set(); if (raw[at] === '}') {at++; return out;} while (true) {ws(); if (raw[at] !== '"') throw Error('JSON_KEY'); const key = string(); if (keys.has(key)) throw Error('JSON_DUPLICATE'); keys.add(key); ws(); if (raw[at++] !== ':') throw Error('JSON_COLON'); Object.defineProperty(out, key, {value: value(depth + 1), enumerable: true}); ws(); const end = raw[at++]; if (end === '}') return out; if (end !== ',') throw Error('JSON_OBJECT');}}
    const token = /^(?:-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?|true|false|null)/.exec(raw.slice(at)); if (!token) throw Error('JSON_VALUE'); at += token[0].length; const parsed = JSON.parse(token[0]); if (typeof parsed === 'number' && !Number.isFinite(parsed)) throw Error('JSON_NUMBER'); return parsed;
  }
  const result = value(0); ws(); if (at !== raw.length) throw Error('JSON_TRAILING'); return result;
}
export function validateReading(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value) || Object.keys(value).sort().join(',') !== 'humidityPercent,temperatureC,thresholdC') throw Error('READING_FIELDS');
  for (const [name, min, max] of [['temperatureC', -20, 80], ['humidityPercent', 0, 100], ['thresholdC', 0, 60]]) {const n = value[name]; if (typeof n !== 'number' || !Number.isFinite(n) || n < min || n > max || Number(n.toFixed(6)) !== n) throw Error('READING_BOUNDS');}
  return {...value};
}
export const utf8 = value => {const bytes = Buffer.from(value); if (bytes.length > 256 || !bytes.length || /[\u0000-\u001f\u007f]/u.test(value)) throw Error('MQTT_STRING'); return Buffer.concat([Buffer.from([bytes.length >> 8, bytes.length & 255]), bytes]);};
export function packet(header, body = Buffer.alloc(0)) {if (body.length > LIMITS.packetBytes) throw Error('PACKET_SIZE'); const length = []; let n = body.length; do {let byte = n % 128; n = Math.floor(n / 128); if (n) byte |= 128; length.push(byte);} while (n); return Buffer.concat([Buffer.from([header, ...length]), body]);}
export function connectPacket(runId, role = 'publisher') {return packet(0x10, Buffer.concat([utf8('MQTT'), Buffer.from([4, 2, 0, 30]), utf8(`getlancer-${role}-${runId}`)]));}
export function publishPacket(topic, value) {return packet(0x30, Buffer.concat([utf8(topic), Buffer.from(typeof value === 'string' ? value : JSON.stringify(value))]));}
function decodeString(body, offset) {if (offset + 2 > body.length) throw Error('MQTT_STRING'); const length = body.readUInt16BE(offset); if (!length || length > 256 || offset + 2 + length > body.length) throw Error('MQTT_STRING'); const bytes = body.subarray(offset + 2, offset + 2 + length), value = new TextDecoder('utf-8', {fatal: true}).decode(bytes); if (/[\u0000-\u001f\u007f]/u.test(value)) throw Error('MQTT_STRING'); return {value, end: offset + 2 + length};}
/** One bounded decoder per socket, including fragmented and coalesced packets. */
export function attachDecoder(socket, receive, reject = () => {}) {
  let pending = Buffer.alloc(0), deadline;
  const deny = error => {clearTimeout(deadline); reject(error); socket.destroy();};
  socket.on('data', chunk => {
    if (pending.length + chunk.length > 4096) return deny(Error('WIRE_BUFFER_SIZE'));
    pending = Buffer.concat([pending, chunk]); if (!deadline) deadline = setTimeout(() => deny(Error('PACKET_TIMEOUT')), LIMITS.packetDeadlineMs);
    try {while (pending.length >= 2) {let n = 0, multiplier = 1, at = 1, complete = false; for (; at < pending.length && at <= 4; at++) {const byte = pending[at]; n += (byte & 127) * multiplier; if (!(byte & 128)) {complete = true; at++; break;} multiplier *= 128;}
      if (!complete) {if (at > 4) throw Error('REMAINING_LENGTH'); break;} if (n > LIMITS.packetBytes || (at > 2 && n < 128 ** (at - 2))) throw Error('PACKET_SIZE'); if (pending.length < at + n) break;
      const header = pending[0], body = pending.subarray(at, at + n); pending = pending.subarray(at + n); receive(header, body);
    } if (!pending.length) {clearTimeout(deadline); deadline = undefined;}} catch (error) {deny(error);}
  });
  socket.on('close', () => clearTimeout(deadline)); socket.on('error', () => {});
}

export async function startIotRuntime({runId = randomUUID(), mqttPort = 0, httpPort = 0, lifetimeMs = 60000, idleMs = 30000} = {}) {
  if (!UUID.test(runId) || ![mqttPort, httpPort].every(p => Number.isInteger(p) && p >= 0 && p <= 65535) || !Number.isInteger(lifetimeMs) || lifetimeMs < 500 || lifetimeMs > 120000 || !Number.isInteger(idleMs) || idleMs < 500 || idleMs > 60000) throw Error('STARTUP_BOUNDS');
  const sourceSha256 = createHash('sha256').update(await readFile(fileURLToPath(import.meta.url))).digest('hex');
  const topic = `getlancer/${runId}/telemetry`, sockets = new Set(), httpSockets = new Set(), clientSockets = new Set(), logs = []; let attempts = 0, stopped = false, closing, device, brokerDevice, deviceReady = false, idleTimer, lifetimeTimer, pendingReading, reservedPublishes = 0;
  let state = {runId, connected: false, ...DEFAULTS, actuatorOn: false, sequence: 0, topic, messages: [], events: []};
  const snapshot = () => structuredClone(state);
  const log = code => {logs.push({code: /^[A-Z_]{1,40}$/.test(code) ? code : 'MALFORMED_PACKET', at: Date.now()}); if (logs.length > 20) logs.shift();};
  const touch = () => {clearTimeout(idleTimer); if (!stopped) idleTimer = setTimeout(() => close(), idleMs);};
  const event = (type, detail) => {if (state.sequence >= LIMITS.actions) throw Error('ACTION_LIMIT'); state.sequence++; state.events.push({sequence: state.sequence, type, at: Date.now(), detail}); state.events = state.events.slice(-20); touch();};
  const broker = tcpServer(socket => {
    if (stopped || ++attempts > LIMITS.attempts) {socket.destroy(); if (!stopped) setImmediate(close); return;}
    if (sockets.size >= LIMITS.sockets) {socket.destroy(); return;}
    sockets.add(socket); socket.setTimeout(2000, () => socket.destroy()); let role;
    attachDecoder(socket, (header, body) => {
      if (++attempts > LIMITS.attempts) {setImmediate(close); throw Error('ATTEMPT_LIMIT');} touch();
      if (!role) {
        if (header !== 0x10) throw Error('CONNECT_REQUIRED'); const protocol = decodeString(body, 0); if (protocol.value !== 'MQTT' || body.length < protocol.end + 4 || body[protocol.end] !== 4 || body[protocol.end + 1] !== 2 || body.readUInt16BE(protocol.end + 2) > 60) throw Error('CONNECT_FLAGS'); const identity = decodeString(body, protocol.end + 4); if (identity.end !== body.length) throw Error('CONNECT_FIELDS');
        role = identity.value === `getlancer-device-${runId}` ? 'device' : identity.value === `getlancer-publisher-${runId}` ? 'publisher' : undefined; if (!role) throw Error('RUN_ID');
        if (role === 'device' && [...sockets].some(s => s !== socket && s.iotDevice && !s.destroyed)) throw Error('DEVICE_EXISTS'); socket.iotDevice = role === 'device'; if (socket.iotDevice) brokerDevice = socket; socket.setTimeout(0); socket.write(packet(0x20, Buffer.from([0, 0]))); return;
      }
      if (header === 0xe0 && body.length === 0) {socket.end(); return;}
      if (header === 0xc0 && body.length === 0) {socket.write(packet(0xd0)); return;}
      if (header === 0x82 && role === 'device') {if (body.length < 5 || body.readUInt16BE(0) === 0) throw Error('SUBSCRIBE_ID'); const filter = decodeString(body, 2); if (filter.value !== topic || filter.end + 1 !== body.length || body[filter.end] !== 0) throw Error('SUBSCRIBE_TOPIC'); socket.iotSubscribed = true; socket.write(packet(0x90, Buffer.from([body[0], body[1], 0]))); return;}
      if (header !== 0x30 || role !== 'publisher') throw Error('QOS0_ONLY'); const target = decodeString(body, 0); if (target.value !== topic) throw Error('FOREIGN_TOPIC'); const reading = validateReading(strictJson(new TextDecoder('utf-8', {fatal: true}).decode(body.subarray(target.end))));
      if (!state.connected || !deviceReady) throw Error('DEVICE_DISCONNECTED'); if (state.sequence + reservedPublishes >= LIMITS.actions) throw Error('ACTION_LIMIT');
      for (const peer of sockets) if (peer.iotDevice && peer.iotSubscribed && !peer.destroyed) {reservedPublishes++; peer.write(publishPacket(topic, reading)); log('BROKER_FORWARDED');}
    }, error => {log(error.message);});
    socket.on('close', () => {sockets.delete(socket); if (socket === brokerDevice) {state.connected = false; deviceReady = false; reservedPublishes = 0;}});
  });
  async function connectDevice() {
    if (device && !device.destroyed && deviceReady) return; deviceReady = false;
    await new Promise((done, reject) => {
      const client = connect(broker.address().port, '127.0.0.1'); device = client; clientSockets.add(client); client.on('close', () => clientSockets.delete(client)); const timer = setTimeout(() => {client.destroy(); reject(Error('DEVICE_TIMEOUT'));}, 1500);
      client.once('error', error => {clearTimeout(timer); reject(error);}); client.once('close', () => {if (!deviceReady) {clearTimeout(timer); reject(Error('DEVICE_CLOSED'));}});
      attachDecoder(client, (header, body) => {
        if (header === 0x20 && body.equals(Buffer.from([0, 0]))) client.write(packet(0x82, Buffer.concat([Buffer.from([0, 1]), utf8(topic), Buffer.from([0])])));
        else if (header === 0x90 && body.equals(Buffer.from([0, 1, 0]))) {deviceReady = true; state.connected = true; clearTimeout(timer); done();}
        else if (header === 0x30 && deviceReady) {const target = decodeString(body, 0); if (target.value !== topic) throw Error('DEVICE_TOPIC'); const reading = validateReading(strictJson(new TextDecoder('utf-8', {fatal: true}).decode(body.subarray(target.end)))); reservedPublishes = Math.max(0, reservedPublishes - 1); event('publish', `Telemetry accepted; simulated actuator ${reading.temperatureC >= reading.thresholdC ? 'on' : 'off'}.`); Object.assign(state, reading, {actuatorOn: reading.temperatureC >= reading.thresholdC}); state.messages.push({sequence: state.sequence, topic, ...reading, actuatorOn: state.actuatorOn, at: Date.now()}); state.messages = state.messages.slice(-20); log('DEVICE_APPLIED'); if (pendingReading && JSON.stringify(reading) === pendingReading.input) {const pending = pendingReading; pendingReading = undefined; pending.resolve(snapshot());}}
        else throw Error('DEVICE_PACKET');
      }, error => {clearTimeout(timer); reject(error);}); client.once('connect', () => client.write(connectPacket(runId, 'device')));
    });
  }
  async function disconnectDevice() {
    state.connected = false; deviceReady = false;
    const peers = [device, brokerDevice].filter(socket => socket && !socket.destroyed), endings = peers.map(socket => new Promise(done => socket.once('close', done))); for (const peer of peers) peer.destroy(); await Promise.all(endings); reservedPublishes = 0;
  }
  async function wirePublish(reading) {
    if (!state.connected || !deviceReady) throw Error('DEVICE_DISCONNECTED'); if (pendingReading) throw Error('BUSY');
    return await new Promise((done, reject) => {
      const publisher = connect(broker.address().port, '127.0.0.1'), timer = setTimeout(() => {pendingReading = undefined; publisher.destroy(); reject(Error('PUBLISH_TIMEOUT'));}, 1500); clientSockets.add(publisher); publisher.on('close', () => clientSockets.delete(publisher));
      const finish = value => {clearTimeout(timer); publisher.end(packet(0xe0)); done(value);}; pendingReading = {resolve: finish, input: JSON.stringify(reading), cancel: () => {clearTimeout(timer); publisher.destroy(); reject(Error('EXPIRED'));}};
      publisher.once('error', error => {clearTimeout(timer); pendingReading = undefined; reject(error);}); attachDecoder(publisher, (header, body) => {if (header !== 0x20 || !body.equals(Buffer.from([0, 0]))) throw Error('PUBLISH_CONNECT'); publisher.write(publishPacket(topic, reading));}, error => {clearTimeout(timer); pendingReading = undefined; reject(error);}); publisher.once('connect', () => publisher.write(connectPacket(runId)));
    });
  }
  let operationBusy = false;
  const http = httpServer({maxHeaderSize: 4096, requestTimeout: 2000, headersTimeout: 2000, keepAliveTimeout: 1}, async (req, res) => {
    clearTimeout(req.socket.iotHeaderTimer);
    const reply = (status, value) => {const bytes = JSON.stringify(value); res.writeHead(status, {'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(bytes), 'Connection': 'close', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff'}); res.end(bytes);};
    const deny = (status, error) => reply(status, {error});
    if (stopped) return deny(410, 'EXPIRED'); if (++attempts > LIMITS.attempts) {deny(429, 'ATTEMPT_LIMIT'); setImmediate(close); return;} touch();
    if (Object.hasOwn(req.headers, 'origin') || req.headers.host !== `127.0.0.1:${http.address().port}`) return deny(403, 'LOCAL_ORIGIN_ONLY');
    const names = req.rawHeaders.filter((_, i) => i % 2 === 0).map(h => h.toLowerCase()); if (new Set(names).size !== names.length || Object.hasOwn(req.headers, 'transfer-encoding')) return deny(400, 'FRAMING');
    if (req.method === 'GET' && req.url === '/health') return reply(200, {mode: 'LOCAL_MQTT', runId, sourceSha256, protocol: 'MQTT-3.1.1-QoS0', physicalProof: null});
    if (req.method !== 'POST') return deny(405, 'METHOD'); if (req.url !== '/request') return deny(404, 'PATH'); if (req.headers['content-type'] !== 'application/x-www-form-urlencoded') return deny(415, 'CONTENT_TYPE');
    if (!/^\d{1,4}$/.test(req.headers['content-length'] ?? '') || Number(req.headers['content-length']) > LIMITS.bodyBytes) return deny(413, 'BODY_SIZE');
    const chunks = []; let length = 0; const bodyTimer = setTimeout(() => {if (!res.headersSent) deny(408, 'REQUEST_TIMEOUT'); req.destroy();}, 1000);
    try {
      for await (const chunk of req) {length += chunk.length; if (length > LIMITS.bodyBytes) throw Error('BODY_SIZE'); chunks.push(chunk);} clearTimeout(bodyTimer);
      const raw = new TextDecoder('utf-8', {fatal: true}).decode(Buffer.concat(chunks)); if (/%(?![0-9a-f]{2})/i.test(raw)) throw Error('FORM_ENCODING'); for (const field of raw.split('&')) decodeURIComponent(field.replaceAll('+', ' ')); const entries = [...new URLSearchParams(raw)]; if (new Set(entries.map(([key]) => key)).size !== entries.length) throw Error('FORM_DUPLICATE'); const fields = Object.fromEntries(entries);
      if (fields.runId !== runId) return deny(403, 'RUN_ID'); const operation = fields.operationId; const expected = operation === 'publish' ? ['runId', 'operationId', 'temperatureC', 'humidityPercent', 'thresholdC'] : ['runId', 'operationId']; if (Object.keys(fields).sort().join(',') !== expected.sort().join(',') || !['publish', 'disconnect', 'connect', 'reset', 'state'].includes(operation)) throw Error('OPERATION_FIELDS');
      if (operationBusy) return deny(409, 'BUSY'); operationBusy = true;
      try {
        if (operation === 'publish') {const input = {}; for (const key of ['temperatureC', 'humidityPercent', 'thresholdC']) {if (!/^-?(?:0|[1-9]\d*)(?:\.\d{1,6})?$/.test(fields[key]) || fields[key].length > 12) throw Error('NUMERIC_FORMAT'); input[key] = Number(fields[key]);} await wirePublish(validateReading(input));}
        else if (operation === 'disconnect') {event('disconnect', 'Simulated device disconnected.'); await disconnectDevice();}
        else if (operation === 'connect') {if (state.sequence >= LIMITS.actions) throw Error('ACTION_LIMIT'); await connectDevice(); event('connect', 'Simulated device connected.');}
        else if (operation === 'reset') {if (state.sequence >= LIMITS.actions) throw Error('ACTION_LIMIT'); await disconnectDevice(); Object.assign(state, DEFAULTS, {actuatorOn: false, messages: []}); await connectDevice(); event('reset', 'Synthetic readings reset; the same isolated run is connected.');}
        reply(200, {mode: 'LOCAL_MQTT', sourceSha256, state: snapshot(), logs: [...logs]});
      } finally {operationBusy = false;}
    } catch (error) {clearTimeout(bodyTimer); if (!res.headersSent && !res.destroyed) deny(error.message === 'DEVICE_DISCONNECTED' ? 409 : 400, /^[A-Z_]+$/.test(error.message) ? error.message : 'INVALID_REQUEST');}
  });
  http.maxRequestsPerSocket = 1;
  http.on('connection', socket => {if (httpSockets.size >= 4) return socket.destroy(); httpSockets.add(socket); socket.iotHeaderTimer = setTimeout(() => socket.end('HTTP/1.1 408 Request Timeout\r\nConnection: close\r\nContent-Length: 0\r\n\r\n'), 1000); socket.setTimeout(2000, () => socket.destroy()); socket.on('close', () => {clearTimeout(socket.iotHeaderTimer); httpSockets.delete(socket);});});
  http.on('clientError', (_, socket) => socket.end('HTTP/1.1 400 Bad Request\r\nConnection: close\r\nContent-Length: 0\r\n\r\n'));
  function close() {if (closing) return closing; stopped = true; clearTimeout(idleTimer); clearTimeout(lifetimeTimer); if (pendingReading) {pendingReading.cancel(); pendingReading = undefined;} const peers = [...sockets, ...httpSockets, ...clientSockets].filter(socket => !socket.destroyed), endings = peers.map(socket => new Promise(done => socket.once('close', done))); for (const socket of peers) socket.destroy(); state = {runId, connected: false, ...DEFAULTS, actuatorOn: false, sequence: 0, topic, messages: [], events: []}; logs.length = 0; closing = Promise.all([...endings, new Promise(done => broker.close(done)), new Promise(done => http.close(done))]).then(() => {sockets.clear(); httpSockets.clear(); clientSockets.clear();}); return closing;}
  const listen = (server, port) => new Promise((done, reject) => {server.once('error', reject); server.listen(port, '127.0.0.1', done);});
  try {await listen(broker, mqttPort); await listen(http, httpPort); await connectDevice(); touch(); lifetimeTimer = setTimeout(() => close(), lifetimeMs);} catch (error) {await close(); throw error;}
  return {runId, topic, mqttPort: broker.address().port, httpPort: http.address().port, sourceSha256, snapshot, logs: () => structuredClone(logs), close, closed: () => stopped, socketCount: () => sockets.size + httpSockets.size};
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    if (process.argv.length !== 2) throw Error('ARGUMENTS');
    const allowedInteger = (key, fallback) => {if (!Object.hasOwn(process.env, key)) return fallback; if (!/^\d{1,6}$/.test(process.env[key])) throw Error('STARTUP_BOUNDS'); return Number(process.env[key]);};
    const runtime = await startIotRuntime({runId: process.env.LAB_RUN_ID ?? randomUUID(), mqttPort: allowedInteger('LAB_MQTT_PORT', 18830), httpPort: allowedInteger('LAB_HTTP_PORT', 8106), lifetimeMs: allowedInteger('LAB_LIFETIME_MS', 60000), idleMs: allowedInteger('LAB_IDLE_MS', 30000)});
    console.log('IOT_READY ' + JSON.stringify({runId: runtime.runId, mqttPort: runtime.mqttPort, httpPort: runtime.httpPort, sourceSha256: runtime.sourceSha256, mode: 'LOCAL_MQTT'}));
    process.once('SIGTERM', () => runtime.close()); process.once('SIGINT', () => runtime.close());
  } catch {console.error('IOT_STARTUP_REJECTED'); process.exitCode = 1;}
}
