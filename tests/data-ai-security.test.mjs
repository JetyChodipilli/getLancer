/** Independent security acceptance: original processes, raw HTTP and original-byte oracles. */
import test from 'node:test';
import assert from 'node:assert/strict';
import {spawn, spawnSync} from 'node:child_process';
import {createHash, randomUUID} from 'node:crypto';
import {readFile, writeFile, mkdir, mkdtemp, rm, symlink} from 'node:fs/promises';
import {request} from 'node:http';
import {createServer, connect} from 'node:net';
import {tmpdir} from 'node:os';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {gunzipSync} from 'node:zlib';
import {parseDataAiIndex, parseDataAiRecording, readDataAiFile} from '../lib/data-ai.ts';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const base = join(root, 'labs/data-ai'), sha = value => createHash('sha256').update(value).digest('hex');
const catalogue = JSON.parse(await readFile(join(base, 'catalogue.json'), 'utf8'));
const sourceHash = sha(await readFile(join(base, 'runtime.py')));
const delay = ms => new Promise(done => setTimeout(done, ms));
function canonical(value) {return JSON.stringify(sort(value));}
function sort(value) {return Array.isArray(value) ? value.map(sort) : value && typeof value === 'object' ? Object.fromEntries(Object.keys(value).sort().map(key => [key, sort(value[key])])) : value;}
async function freePort() {const server = createServer(); await new Promise((yes, no) => server.once('error', no).listen(0, '127.0.0.1', yes)); const value = server.address().port; await new Promise(done => server.close(done)); return value;}
async function start(t, env = {}, cwd = base) {
  const port = await freePort(), runId = randomUUID();
  const child = spawn('python3', ['runtime.py'], {cwd, env: {PATH: process.env.PATH, PYTHONDONTWRITEBYTECODE: '1', LAB_RUN_ID: runId, LAB_PORT: String(port), ...env}, stdio: ['ignore', 'pipe', 'pipe']});
  let output = '', failure;
  child.on('error', error => {failure = error;});
  for (const stream of [child.stdout, child.stderr]) stream.on('data', chunk => {output += chunk.toString();});
  t.after(async () => {if (child.exitCode !== null || child.signalCode !== null || !child.pid) return; const stopped = new Promise(done => child.once('exit', done)); child.kill('SIGTERM'); await Promise.race([stopped, delay(1500)]); if (child.exitCode === null && child.signalCode === null) {child.kill('SIGKILL'); await stopped;}});
  const until = Date.now() + 5000;
  while (Date.now() < until) {if (failure || child.exitCode !== null) throw Error('Runtime startup rejected: ' + (failure?.message || output)); if (output.includes('"READY"')) {assert.deepEqual(JSON.parse(output.trim()), {status: 'READY', runId, port}); return {port, runId, child, output: () => output};} await delay(10);}
  throw Error('Original runtime did not reach readiness');
}
function form(app, id = 'revenue-summary', values = catalogue.items.find(item => item.id === id).defaultInputs) {return new URLSearchParams({runId: app.runId, scenarioId: id, operationId: 'execute', ...values}).toString();}
function send(app, body, options = {}) {
  const bytes = body === undefined ? undefined : Buffer.from(body);
  return new Promise((yes, no) => {
    const req = request({host: '127.0.0.1', port: app.port, path: options.path || '/request', method: options.method || 'POST', headers: {...(bytes ? {'Content-Type': 'application/x-www-form-urlencoded', 'Content-Length': bytes.length} : {}), ...options.headers}}, response => {
      let data = Buffer.alloc(0); response.on('data', chunk => {data = Buffer.concat([data, chunk]); if (data.length > 4096) req.destroy(Error('Response byte budget exceeded'));});
      response.on('end', () => {try {yes({status: response.statusCode, headers: response.headers, text: data.toString('utf8'), value: JSON.parse(data.toString('utf8')), bytes: data.length});} catch (error) {no(error);}}); response.on('error', no);
    });
    req.setTimeout(2500, () => req.destroy(Error('Request deadline exceeded'))); req.on('error', no); req.end(bytes);
  });
}
function raw(app, bytes) {
  return new Promise((yes, no) => {const socket = connect(app.port, '127.0.0.1'); let data = Buffer.alloc(0);
    socket.setTimeout(2500, () => socket.destroy(Error('Raw response deadline exceeded'))); socket.on('error', no); socket.once('connect', () => socket.end(bytes));
    socket.on('data', chunk => {data = Buffer.concat([data, chunk]); if (data.length > 8192) socket.destroy(Error('Raw response exceeded budget'));});
    socket.on('end', () => {try {const text = data.toString(), offset = text.indexOf('\r\n\r\n'); assert(offset > 0); yes({status: Number(text.match(/^HTTP\/1\.[01] (\d+)/)?.[1]), text: text.slice(offset + 4), value: JSON.parse(text.slice(offset + 4))});} catch (error) {no(error);}});
  });
}
const post = (app, headers, body = '', path = '/request') => `POST ${path} HTTP/1.1\r\nHost: 127.0.0.1:${app.port}\r\nContent-Type: application/x-www-form-urlencoded\r\n${headers}\r\n\r\n${body}`;
function denial(reply, marker = '') {assert(reply.status >= 400 && reply.status < 500, reply.text); assert.deepEqual(Object.keys(reply.value).sort(), ['error', 'message']); assert.equal(typeof reply.value.error, 'string'); assert.equal(typeof reply.value.message, 'string'); if (marker) assert(!reply.text.includes(marker)); assert(!/Traceback|\/workspace|sourceBytes/.test(reply.text));}
async function control(app) {const reply = await send(app, form(app)); assert.equal(reply.status, 200); assert.equal(reply.value.mode, 'LOCAL'); assert.equal(reply.value.runId, app.runId); return reply;}
async function exited(child, maximum = 1800) {if (child.exitCode !== null) {assert.equal(child.exitCode, 0); return;} await Promise.race([new Promise((yes, no) => child.once('exit', code => code === 0 ? yes() : no(Error('Unexpected child exit ' + code)))), delay(maximum).then(() => {throw Error('Disposable authority remained alive');})]);}

test('original loopback process executes all four scenarios; byte hashes and independent arithmetic/model oracles agree', async t => {
  const app = await start(t), health = await send(app, undefined, {method: 'GET', path: '/health'});
  assert.equal(health.status, 200); assert.equal(health.value.sourceSha256, sourceHash); assert.equal(health.value.limits.maxAttempts, 100); assert.equal(health.value.limits.absoluteRequestSeconds, 1);
  for (const item of catalogue.items) {
    const data = await readFile(join(base, catalogue.assets[item.id].dataPath)); assert.equal(sha(data), item.dataSha256);
    const results = [];
    for (const input of [item.defaultInputs, item.changedInputs]) {
      const reply = await send(app, form(app, item.id, input)); assert.equal(reply.status, 200); assert(reply.bytes <= 4096); const envelope = reply.value;
      assert.deepEqual(Object.keys(envelope).sort(), ['schemaVersion', 'mode', 'runId', 'scenarioId', 'sourceSha256', 'dataSha256', 'modelSha256', 'evaluationSha256', 'input', 'inputSha256', 'result', 'resultSha256', 'startedAt', 'elapsedMs'].sort());
      assert.equal(envelope.mode, 'LOCAL'); assert.equal(envelope.runId, app.runId); assert.equal(envelope.sourceSha256, sourceHash); assert.deepEqual(envelope.input, input);
      assert.equal(envelope.inputSha256, sha(canonical(input))); assert.equal(envelope.resultSha256, sha(canonical(envelope.result))); assert(Number.isInteger(envelope.elapsedMs) && envelope.elapsedMs >= 0 && envelope.elapsedMs <= 1000); assert(Date.parse(envelope.startedAt) <= Date.now());
      assert.equal(reply.headers['cache-control'], 'no-store'); assert.equal(reply.headers['x-content-type-options'], 'nosniff'); assert.equal(reply.headers['access-control-allow-origin'], undefined);
      const metrics = Object.fromEntries(envelope.result.metrics.map(metric => [metric.key, metric.value])), rows = JSON.parse(data);
      if (item.id === 'revenue-summary') {const filtered = rows.filter(row => input.region === 'all' || row.region === input.region), amount = filtered.reduce((sum, row) => sum + row.revenue, 0), revenue = Math.floor((amount * (100 + Number(input.upliftPercent)) + 50) / 100); assert.equal(metrics.revenue, revenue); assert.equal(metrics.orders, filtered.length); assert.equal(metrics.averageOrder, Math.floor((revenue + Math.floor(filtered.length / 2)) / filtered.length));}
      else if (item.id === 'sensor-quality') {const filtered = rows.filter(row => input.channel === 'all' || row.channel === input.channel), accepted = filtered.filter(row => Math.abs(row.deviation) <= Number(input.tolerance)).length; assert.equal(metrics.accepted, accepted); assert.equal(metrics.rejected, filtered.length - accepted); assert.equal(metrics.acceptance, accepted / filtered.length * 100);}
      else {const modelBytes = await readFile(join(base, catalogue.assets[item.id].modelPath)), model = JSON.parse(modelBytes), evaluationBytes = await readFile(join(base, catalogue.assets[item.id].evaluationPath)), samples = JSON.parse(evaluationBytes); assert.equal(sha(modelBytes), envelope.modelSha256); assert.equal(sha(evaluationBytes), envelope.evaluationSha256); assert.notEqual(envelope.dataSha256, envelope.evaluationSha256);
        function prediction(values) {const score = item.id === 'sentiment-inference' ? model.bias + (values.text.toLowerCase().match(/[a-z]+/g) || []).reduce((sum, token) => sum + (model.weights[token] || 0), 0) : model.bias + model.weights.temperature * Number(values.temperature) + model.weights.vibration * Number(values.vibration); const p = Math.round(1 / (1 + Math.exp(-Math.max(-30, Math.min(30, score)))) * 1e6) / 1e6; return {label: model.classes[p > .5 ? 1 : 0], probability: p > .5 ? p : Math.round((1 - p) * 1e6) / 1e6};}
        assert.deepEqual(envelope.result.prediction, prediction(input)); const correct = samples.filter(({label, ...values}) => prediction(values).label === label).length; assert.equal(envelope.result.evaluation.samples, 8); assert.equal(envelope.result.evaluation.correct, correct); assert.equal(envelope.result.evaluation.accuracy, correct / 8);
      }
      results.push(envelope.resultSha256);
    }
    assert.notEqual(results[0], results[1]);
  }
  const repeated = await control(app); assert.equal(repeated.value.resultSha256, (await control(app)).value.resultSha256);
});

test('unknown loader/code/data inputs, foreign runs and malformed UTF-8 or percent bytes deny without echo or logs', async t => {
  const app = await start(t), basic = form(app), marker = 'SEC_PRIVATE_CANARY_51229';
  const attacks = [basic + '&runId=' + randomUUID(), basic + '&%72unId=' + app.runId, basic + '&loader=pickle', basic + '&modelUrl=https://private.invalid/' + marker, basic + '&command=' + marker, basic + '&path=/etc/passwd', basic + '&sourceBytes=' + marker, basic + '&extra=%ZZ', basic.replace('operationId=execute', 'operationId=reset'), basic.replace(app.runId, randomUUID()), basic.replace('revenue-summary', 'arbitrary-loader'), Buffer.concat([Buffer.from(basic + '&extra='), Buffer.from([0xff])]), form(app, 'sentiment-inference', {text: '\n' + marker}), form(app, 'sentiment-inference', {text: 'é' + marker})];
  attacks.push(`runId=${app.runId}&scenarioId=sentiment-inference&operationId=execute&text=hello%GG${marker}`);
  for (const attack of attacks) denial(await send(app, attack), marker);
  await control(app); assert(!app.output().includes(marker)); assert(!/Traceback|PRIVATE/.test(app.output()));
});

test('raw duplicate/conflicting framing, origins, folded headers and ambiguous paths cannot authorize work', async t => {
  const app = await start(t), body = form(app), length = Buffer.byteLength(body);
  const headers = [`Content-Length: ${length}\r\nContent-Length: ${length}`, `Content-Length: ${length}\r\nContent-Length: 0`, `Transfer-Encoding: chunked\r\nContent-Length: ${length}`, 'Transfer-Encoding:', `Content-Length: ${length}\r\nOrigin:`, `Content-Length: ${length}\r\nOrigin: https://evil.invalid`, `Content-Length: ${length}\r\nHost: 127.0.0.1:${app.port}`, `Content-Length: ${length}\r\nContent-Type: application/json`, 'Content-Length: +10', 'Content-Length: -1', 'Content-Length: 0010', `Content-Length: ${length}\r\n Folded: value`, `Content-Length: ${length}\r\nBad_header: value`];
  for (const header of headers) denial(await raw(app, post(app, header, body)));
  for (const path of ['//request', '/request?x=1', '/%72equest', '/request/../request', '/health']) denial(await raw(app, post(app, 'Content-Length: ' + length, body, path)));
  denial(await raw(app, `GET /health HTTP/1.1\r\nHost: 127.0.0.1:${app.port}\r\nContent-Length: 0\r\n\r\n`)); await control(app);
});

test('exact request-byte and scenario bounds fail closed; valid tiny-probability extremes still bind canonical hashes', async t => {
  const app = await start(t), prefix = form(app) + '&extra=';
  const within = prefix + 'x'.repeat(8192 - Buffer.byteLength(prefix)), over = within + 'x'; denial(await send(app, within)); const reply = await send(app, over); denial(reply); assert.equal(reply.status, 413);
  for (const input of [{region: 'all', upliftPercent: '-21'}, {region: 'all', upliftPercent: '020'}, {region: 'all', upliftPercent: '1e1'}, {region: 'all', upliftPercent: '-0'}]) denial(await send(app, form(app, 'revenue-summary', input)));
  denial(await send(app, form(app, 'sentiment-inference', {text: 'x'.repeat(161)})));
  for (const text of ['terrible '.repeat(7), 'excellent '.repeat(16)]) {const good = await send(app, form(app, 'sentiment-inference', {text})); assert.equal(good.status, 200); assert.equal(good.value.resultSha256, sha(canonical(good.value.result))); assert(!canonical(good.value.result).includes(text));}
  await control(app);
});

test('all malformed attempts count before parse; request101 returns429 then disposable authority exits', async t => {
  const app = await start(t);
  for (let attempt = 0; attempt < 100; attempt++) {const reply = attempt % 2 ? await send(app, 'invalid') : await raw(app, `POST /request HTTP/1.1\r\nMalformed header\r\n\r\n`); denial(reply);}
  const exhausted = await send(app, form(app)); denial(exhausted); assert.equal(exhausted.status, 429); assert.equal(exhausted.value.error, 'ATTEMPT_LIMIT'); await exited(app.child);
});

async function dribble(app, prefix) {const startTime = Date.now(); return new Promise((yes, no) => {const socket = connect(app.port, '127.0.0.1'); let response = '', interval; socket.on('error', no); socket.once('connect', () => {socket.write(prefix); interval = setInterval(() => socket.write('x'), 80);}); socket.on('data', chunk => {response += chunk;}); socket.on('end', () => {clearInterval(interval); yes({response, elapsed: Date.now() - startTime});}); socket.setTimeout(2000, () => {clearInterval(interval); socket.destroy(Error('Dribble survived absolute deadline'));}); socket.on('close', () => clearInterval(interval));});}
test('continuously dripped headers and body cannot extend one-second absolute lifetime', async t => {
  for (const body of [false, true]) {const app = await start(t, {LAB_LIFETIME_SECONDS: '1'}), prefix = body ? post(app, 'Content-Length: 8192').replace(/\r\n$/, '') + '\r\n' : `POST /request HTTP/1.1\r\nHost: 127.0.0.1:${app.port}\r\nX-Slow: `;
    const result = await dribble(app, prefix); assert.match(result.response, /^HTTP\/1\.0 408 /); assert.match(result.response, /REQUEST_TIMEOUT/); assert(result.elapsed <= 1600); await exited(app.child);}
});

test('lowered idle expiry closes a previously healthy process and actual Unix process limits are enforced', async t => {
  const app = await start(t, {LAB_IDLE_SECONDS: '1'}); await control(app); await exited(app.child, 2000);
  if (process.platform === 'win32') return;
  const probe = spawnSync('python3', ['-c', 'import importlib.util,json,resource; s=importlib.util.spec_from_file_location("fixed","runtime.py"); m=importlib.util.module_from_spec(s); s.loader.exec_module(m); assert m.apply_limits(); print(json.dumps([resource.getrlimit(k) for k in [resource.RLIMIT_AS,resource.RLIMIT_CPU,resource.RLIMIT_NOFILE,resource.RLIMIT_CORE,resource.RLIMIT_FSIZE]]))'], {cwd: base, env: {PATH: process.env.PATH, PYTHONDONTWRITEBYTECODE: '1'}, encoding: 'utf8', timeout: 3000}); assert.equal(probe.status, 0, probe.stderr);
  const values = JSON.parse(probe.stdout); for (const [index, maximum] of [268435456, 10, 64, 0, 524288].entries()) {assert(values[index][0] <= maximum); assert(values[index][1] <= maximum);}
});

test('original public tar has fourteen exact licensed regular files, deterministic metadata and no private extras', async () => {
  const archive = await readFile(join(root, 'public/data-ai/source.tar.gz')), index = JSON.parse(await readFile(join(root, 'public/data-ai/material-index.json'))); assert.equal(sha(archive), index.archiveSha256);
  const bytes = gunzipSync(archive, {maxOutputLength: 1048576}), found = new Set(); let at = 0;
  while (at + 512 <= bytes.length) {const header = bytes.subarray(at, at + 512); if (header.every(value => value === 0)) break; const name = header.subarray(0, 100).toString().split('\0')[0], size = parseInt(header.subarray(124, 136).toString(), 8); assert(catalogue.inventory.includes(name) && !found.has(name)); found.add(name); assert.equal(header[156], 48); assert.equal(header.subarray(100, 108).toString(), '0000644\0'); assert.equal(parseInt(header.subarray(108, 116).toString(), 8), 0); assert.equal(parseInt(header.subarray(136, 148).toString(), 8), 0); assert(Number.isSafeInteger(size) && size <= 65536); assert((await readFile(join(base, name))).equals(bytes.subarray(at + 512, at + 512 + size))); at += 512 + Math.ceil(size / 512) * 512;}
  assert.equal(found.size, 14); assert(bytes.subarray(at).every(value => value === 0)); for (const name of ['License.md', 'DATA_LICENSE.md', 'MODEL_LICENSE.md']) assert.match(await readFile(join(base, name), 'utf8'), /MIT License/);
});

test('unsafe source models, duplicate JSON, pinned data and absolute/symlink destinations reject before readiness', async t => {
  const temporary = await mkdtemp(join(tmpdir(), 'getlancer-security-')); t.after(() => rm(temporary, {recursive: true, force: true}));
  const positiveDirectory = join(temporary, 'positive');
  for (const name of catalogue.inventory) {await mkdir(dirname(join(positiveDirectory, name)), {recursive: true}); await writeFile(join(positiveDirectory, name), await readFile(join(base, name)));}
  const positive = await start(t, {LAB_LIFETIME_SECONDS: '1'}, positiveDirectory); await control(positive); await exited(positive.child);
  for (const attack of ['loader', 'pickle', 'duplicate-key', 'deep-json', 'changed-data', 'absolute-path', 'foreign-inventory', ...(process.platform === 'win32' ? [] : ['parent-symlink'])]) {
    const cwd = join(temporary, attack); for (const name of catalogue.inventory) {await mkdir(dirname(join(cwd, name)), {recursive: true}); await writeFile(join(cwd, name), await readFile(join(base, name)));}
    const changed = structuredClone(catalogue), item = changed.items.find(value => value.id === 'sentiment-inference'), modelPath = changed.assets[item.id].modelPath;
    if (['loader', 'pickle', 'duplicate-key', 'deep-json'].includes(attack)) {const model = JSON.parse(await readFile(join(cwd, modelPath))); let data; if (attack === 'loader') {model.loader = 'https://private.invalid/SEC_SOURCE_CANARY'; data = Buffer.from(JSON.stringify(model));} else if (attack === 'pickle') data = Buffer.from([128, 4, 149, 0]); else if (attack === 'deep-json') data = Buffer.from('['.repeat(10000) + '0' + ']'.repeat(10000)); else data = Buffer.from(JSON.stringify(model).replace('"format":', '"format":"pickle","format":')); item.modelSha256 = sha(data); await writeFile(join(cwd, modelPath), data);}
    else if (attack === 'changed-data') await writeFile(join(cwd, 'fixtures/revenue.json'), '[]');
    else if (attack === 'absolute-path') changed.assets[item.id].modelPath = join(cwd, modelPath);
    else if (attack === 'foreign-inventory') changed.inventory.push('private-environment.json');
    else {const external = join(temporary, 'external'); await mkdir(external); for (const name of ['sentiment-inference.json', 'equipment-inference.json']) await writeFile(join(external, name), await readFile(join(base, 'models', name))); await rm(join(cwd, 'models'), {recursive: true}); await symlink(external, join(cwd, 'models'));}
    await writeFile(join(cwd, 'catalogue.json'), JSON.stringify(changed)); const stopped = spawnSync('python3', ['runtime.py'], {cwd, env: {PATH: process.env.PATH, PYTHONDONTWRITEBYTECODE: '1', LAB_RUN_ID: randomUUID(), LAB_PORT: String(await freePort()), LAB_LIFETIME_SECONDS: '1'}, encoding: 'utf8', timeout: 2500}); assert.equal(stopped.error, undefined); assert.equal(stopped.status, 1, attack); assert.equal(stopped.stdout, ''); assert.equal(stopped.stderr.trim(), 'Local configuration or pinned assets rejected.');
  }
  const good = await start(t); await control(good);
});

test('actual published replay accepts; rehashed foreign/private/mode/schema/context/time/hash substitutions reject', async () => {
  const originalIndex = await readFile(join(root, 'public/data-ai/material-index.json'), 'utf8'), index = parseDataAiIndex(originalIndex);
  for (const item of index.items) {const original = JSON.parse(await readFile(join(root, 'public', item.recordingUrl), 'utf8')); await parseDataAiRecording(original, index, item);
    const mutations = [recording => {recording.mode = 'LOCAL';}, recording => {recording.runs[0].mode = 'HOSTED';}, recording => {recording.sourceSha256 = '0'.repeat(64);}, recording => {recording.runs[1].runId = randomUUID();}, recording => {recording.runs[0].startedAt = '2099-01-01T00:00:00.000Z';}, recording => {recording.runs[0].result.privateAcademic = 'SEC_REPLAY_CANARY';}, recording => {recording.runs[0].input.extra = 'SEC_REPLAY_CANARY';}, recording => {recording.runs[1].input = recording.runs[0].input; recording.runs[1].inputSha256 = recording.runs[0].inputSha256;}, recording => {recording.runs[0].result.tables[0].columns[0].unit = 'private-source';}, recording => {recording.runs[0].result.metrics[0].value += 1;}, recording => {recording.runs[0].result.tables[0].rows.push({private: 'SEC_REPLAY_CANARY'});}];
    for (const mutate of mutations) {const changed = structuredClone(original); mutate(changed); for (const run of changed.runs) {run.inputSha256 = sha(canonical(run.input)); run.resultSha256 = sha(canonical(run.result));} await assert.rejects(() => parseDataAiRecording(changed, index, item));}
    const raw = JSON.stringify(original).replace('"mode":', '"mode":"HOSTED","mo\\u0064e":'); await assert.rejects(() => parseDataAiRecording(raw, index, item));
  }
  const staleIndex = JSON.parse(originalIndex); staleIndex.sourceSha256 = '0'.repeat(64); assert.throws(() => parseDataAiIndex(staleIndex));
});

test('JSON transport bounds actual streamed bytes and fatal UTF-8 despite a lying Content-Length', async () => {
  const good = new Response('{"ok":true}', {headers: {'Content-Type': 'application/json'}}); assert.equal(await readDataAiFile(good, 100), '{"ok":true}');
  for (const reply of [new Response('x'.repeat(101), {headers: {'Content-Type': 'application/json', 'Content-Length': '1'}}), new Response(new Uint8Array([123, 255, 125]), {headers: {'Content-Type': 'application/json'}}), new Response('{}', {headers: {'Content-Type': 'text/html'}}), new Response('{}', {status: 403, headers: {'Content-Type': 'application/json'}})]) await assert.rejects(() => readDataAiFile(reply, 100));
});
