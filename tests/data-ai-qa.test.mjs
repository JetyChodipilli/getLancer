/** Independent QA: original Python/HTTP, direct fixture arithmetic and safe downloaded source. */
import test from 'node:test';
import assert from 'node:assert/strict';
import {spawn, spawnSync, execFileSync} from 'node:child_process';
import {createHash, randomUUID} from 'node:crypto';
import {readFile, writeFile, mkdir, mkdtemp, rm, symlink} from 'node:fs/promises';
import {request} from 'node:http';
import {createServer, connect} from 'node:net';
import {tmpdir} from 'node:os';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {parseDataAiIndex, parseDataAiRecording} from '../lib/data-ai.ts';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..'), base = join(root, 'labs/data-ai');
const readJson = async path => JSON.parse(await readFile(path, 'utf8'));
const catalogue = await readJson(join(base, 'catalogue.json'));
const sha = bytes => createHash('sha256').update(bytes).digest('hex');
const delay = ms => new Promise(done => setTimeout(done, ms));
const rounded = value => Number(value.toFixed(6));
const canonical = value => JSON.stringify(sorted(value));
function sorted(value) {
  if (typeof value === 'number') {assert(Number.isFinite(value)); return rounded(value);}
  if (Array.isArray(value)) return value.map(sorted);
  if (value && typeof value === 'object') return Object.fromEntries(Object.keys(value).sort().map(key => [key, sorted(value[key])]));
  return value;
}
const digest = value => sha(canonical(value));

async function freePort() {
  const server = createServer(); await new Promise((yes, no) => server.once('error', no).listen(0, '127.0.0.1', yes));
  const port = server.address().port; await new Promise(done => server.close(done)); return port;
}
async function start(t, options = {}) {
  const runId = randomUUID(), port = await freePort(); let log = '', problem;
  const child = spawn('python3', ['-I', options.source || join(base, 'runtime.py')], {cwd: options.cwd || root, env: {PATH: process.env.PATH, LAB_RUN_ID: runId, LAB_PORT: String(port), ...options.env}, stdio: ['ignore', 'pipe', 'pipe']});
  child.on('error', error => {problem = error;});
  for (const stream of [child.stdout, child.stderr]) stream.on('data', chunk => {log += chunk.toString(); if (log.length > 8192) child.kill();});
  t.after(async () => {
    if (!child.pid || child.exitCode !== null || child.signalCode !== null) return;
    const stopped = new Promise(done => child.once('exit', done)); child.kill('SIGTERM'); await Promise.race([stopped, delay(1000)]);
    if (child.exitCode === null && child.signalCode === null) {child.kill('SIGKILL'); await stopped;}
  });
  const deadline = Date.now() + 5000;
  while (!log.includes('"status": "READY"') && Date.now() < deadline) {
    if (problem || child.exitCode !== null) throw Error('Original local runtime startup failed: ' + (problem?.message || log)); await delay(15);
  }
  assert(log.includes('"status": "READY"'), 'Original runtime did not become ready');
  return {runId, port, child, log: () => log};
}
function send(app, fields, options = {}) {
  const method = options.method || 'POST', body = method === 'GET' ? null : Buffer.isBuffer(fields) ? fields : Buffer.from(new URLSearchParams(fields).toString());
  return new Promise((yes, no) => {
    const req = request({host: '127.0.0.1', port: app.port, path: options.path || '/request', method, headers: {...(body ? {'Content-Type': 'application/x-www-form-urlencoded', 'Content-Length': body.length} : {}), ...options.headers}}, response => {
      let bytes = Buffer.alloc(0); response.on('data', chunk => {bytes = Buffer.concat([bytes, chunk]); if (bytes.length > 4096) req.destroy(Error('Actual output exceeded 4 KiB'));});
      response.on('error', no); response.on('end', () => {try {yes({status: response.statusCode, headers: response.headers, bytes: bytes.length, text: bytes.toString(), value: JSON.parse(bytes)});} catch (error) {no(error);}});
    });
    req.on('error', no); req.setTimeout(2500, () => req.destroy(Error('Local socket exceeded bounded deadline'))); req.end(body);
  });
}
function raw(app, source) {
  return new Promise((yes, no) => {
    const socket = connect(app.port, '127.0.0.1'); let received = '';
    socket.setTimeout(2500, () => socket.destroy(Error('Raw socket exceeded absolute bound'))); socket.on('error', no);
    socket.once('connect', () => socket.write(source)); socket.on('data', chunk => {received += chunk.toString(); if (Buffer.byteLength(received) > 8192) socket.destroy(Error('Raw response exceeded bound'));});
    socket.once('close', () => yes(received));
  });
}
async function exited(child, timeout = 1500) {
  const deadline = Date.now() + timeout; while (child.exitCode === null && child.signalCode === null && Date.now() < deadline) await delay(20);
  assert.equal(child.exitCode, 0, 'A completed disposable run must clean up and exit');
}
const fields = (app, id, input) => ({runId: app.runId, scenarioId: id, operationId: 'execute', ...input});
async function execute(app, item, input) {
  const before = Date.now(), reply = await send(app, fields(app, item.id, input)), envelope = reply.value;
  assert.equal(reply.status, 200, reply.text); assert(reply.bytes <= 4096);
  assert.deepEqual(Object.keys(envelope).sort(), ['schemaVersion', 'mode', 'runId', 'scenarioId', 'sourceSha256', 'dataSha256', 'modelSha256', 'evaluationSha256', 'input', 'inputSha256', 'result', 'resultSha256', 'startedAt', 'elapsedMs'].sort());
  assert.equal(envelope.schemaVersion, 'getlancer-data-ai-result-v1'); assert.equal(envelope.mode, 'LOCAL'); assert.equal(envelope.runId, app.runId); assert.equal(envelope.scenarioId, item.id);
  assert.equal(envelope.sourceSha256, sha(await readFile(join(base, 'runtime.py'))));
  for (const key of ['dataSha256', 'modelSha256', 'evaluationSha256']) assert.equal(envelope[key], item[key]);
  assert.deepEqual(envelope.input, input); assert.equal(envelope.inputSha256, digest(input)); assert.equal(envelope.resultSha256, digest(envelope.result));
  const started = Date.parse(envelope.startedAt); assert.equal(new Date(started).toISOString(), envelope.startedAt); assert(started >= before - 1000 && started <= Date.now());
  assert(Number.isInteger(envelope.elapsedMs) && envelope.elapsedMs >= 0 && envelope.elapsedMs <= 1000);
  assert.equal(reply.headers['cache-control'], 'no-store'); assert.equal(Object.keys(reply.headers).some(key => key.startsWith('access-control-')), false);
  return envelope;
}

async function oracle(item, input) {
  const assets = catalogue.assets[item.id], dataBytes = await readFile(join(base, assets.dataPath)), data = JSON.parse(dataBytes);
  assert.equal(sha(dataBytes), item.dataSha256);
  let rows, values, prediction = null, evaluation = null;
  const table = structuredClone(catalogue.contracts[item.id].tables[0]);
  const metricUnits = {'revenue-summary': ['INR', 'orders', 'INR'], 'sensor-quality': ['readings', 'readings', 'percent'], 'sentiment-inference': ['probability', 'ratio'], 'equipment-inference': ['probability', 'ratio']};
  assert.deepEqual(catalogue.contracts[item.id].metrics.map(metric => metric.unit), metricUnits[item.id]);
  if (item.id === 'revenue-summary') {
    const selected = data.filter(row => input.region === 'all' || row.region === input.region), adjustment = (100 + Number(input.upliftPercent)) / 100;
    const revenue = Math.round(selected.reduce((sum, row) => sum + row.revenue, 0) * adjustment);
    rows = ['Jan', 'Feb', 'Mar'].map(month => {const orders = selected.filter(row => row.month === month); return {month, revenue: Math.round(orders.reduce((sum, row) => sum + row.revenue, 0) * adjustment), orders: orders.length};});
    values = [revenue, selected.length, Math.round(revenue / selected.length)];
    assert.deepEqual(table.columns.map(column => [column.key, column.type, column.unit]), [['month', 'string', 'none'], ['revenue', 'number', 'INR'], ['orders', 'number', 'orders']]);
  } else if (item.id === 'sensor-quality') {
    const selected = data.filter(row => input.channel === 'all' || row.channel === input.channel), valid = row => Math.abs(row.deviation) <= Number(input.tolerance);
    rows = (input.channel === 'all' ? ['temperature', 'vibration'] : [input.channel]).map(channel => {const group = selected.filter(row => row.channel === channel), accepted = group.filter(valid).length; return {channel, accepted, rejected: group.length - accepted, total: group.length};});
    const accepted = selected.filter(valid).length; values = [accepted, selected.length - accepted, rounded(accepted / selected.length * 100)];
    assert.deepEqual(table.columns.map(column => column.unit), ['none', 'readings', 'readings', 'readings']);
  } else {
    const modelBytes = await readFile(join(base, assets.modelPath)), heldBytes = await readFile(join(base, assets.evaluationPath)), model = JSON.parse(modelBytes), held = JSON.parse(heldBytes);
    assert.equal(sha(modelBytes), item.modelSha256); assert.equal(sha(heldBytes), item.evaluationSha256); assert.notEqual(item.dataSha256, item.evaluationSha256);
    assert.equal(model.format, 'getlancer-linear-json-v1'); assert.equal(model.id, item.id);
    function probabilities(value) {
      const score = model.bias + (item.id === 'sentiment-inference'
        ? (value.text.toLowerCase().match(/[a-z]+/g) || []).reduce((sum, token) => sum + (model.weights[token] || 0), 0)
        : Object.entries(model.weights).reduce((sum, [key, weight]) => sum + weight * Number(value[key]), 0));
      const second = rounded(1 / (1 + Math.exp(-Math.max(-30, Math.min(30, score))))); return [rounded(1 - second), second];
    }
    const probabilitiesForInput = probabilities(input), winner = probabilitiesForInput[1] > probabilitiesForInput[0] ? 1 : 0;
    prediction = {label: model.classes[winner], probability: probabilitiesForInput[winner]};
    rows = model.classes.map((label, index) => ({class: label, probability: probabilitiesForInput[index]}));
    assert.equal(held.length, 8); const identities = new Set(); let correct = 0;
    for (const {label, ...sample} of held) {
      const identity = canonical(sample); assert.equal(identities.has(identity), false); identities.add(identity);
      assert.notEqual(identity, canonical(data.default)); assert.notEqual(identity, canonical(data.changed));
      const p = probabilities(sample); if (model.classes[p[1] > p[0] ? 1 : 0] === label) correct++;
    }
    evaluation = {split: 'held-out-synthetic', protocol: 'frozen-model-no-training', samples: 8, correct, accuracy: rounded(correct / 8), limitations: item.limitations};
    values = [prediction.probability, evaluation.accuracy];
    assert.deepEqual(table.columns.map(column => [column.key, column.unit]), [['class', 'none'], ['probability', 'probability']]);
  }
  return {tables: [{...table, rows}], metrics: catalogue.contracts[item.id].metrics.map((metric, index) => ({...metric, value: values[index]})), prediction, evaluation};
}

test('independent fixture/model/evaluation arithmetic matches all eight actual default and changed jobs', async t => {
  const app = await start(t);
  for (const item of catalogue.items) {
    const observed = [];
    for (const input of [item.defaultInputs, item.changedInputs]) {const result = await execute(app, item, input); assert.deepEqual(result.result, await oracle(item, input)); observed.push(result);}
    assert.notEqual(observed[0].inputSha256, observed[1].inputSha256); assert.notEqual(observed[0].resultSha256, observed[1].resultSha256);
    const repeated = await execute(app, item, item.defaultInputs); assert.equal(repeated.resultSha256, observed[0].resultSha256);
  }
});

test('boundary analytics, ties, tokenization and tiny logistic probabilities retain exact units and hashes', async t => {
  const app = await start(t), cases = [
    ['revenue-summary', {region: 'north', upliftPercent: '-20'}], ['revenue-summary', {region: 'all', upliftPercent: '20'}],
    ['sensor-quality', {channel: 'vibration', tolerance: '1'}], ['sensor-quality', {channel: 'all', tolerance: '10'}],
    ['sentiment-inference', {text: 'unseen neutral words'}], ['sentiment-inference', {text: 'EXCELLENT, reliable!'}], ['sentiment-inference', {text: 'terrible '.repeat(7).trim()}],
    ['equipment-inference', {temperature: '0', vibration: '0'}], ['equipment-inference', {temperature: '100', vibration: '20'}],
  ];
  for (const [id, input] of cases) {const item = catalogue.items.find(value => value.id === id), result = await execute(app, item, input); assert.deepEqual(result.result, await oracle(item, input));}
  assert.equal(app.log().includes('terrible'), false, 'Local logs must not retain submitted text');
});

test('original recorded evidence passes the browser parser and hostile identity/hash/schema/mode cases reject', async () => {
  const index = parseDataAiIndex(await readFile(join(root, 'public/data-ai/material-index.json'), 'utf8'));
  for (const item of index.items) {
    const text = await readFile(join(root, 'public', item.recordingUrl), 'utf8'), recording = JSON.parse(text);
    assert.deepEqual(await parseDataAiRecording(text, index, item), recording);
    for (const run of recording.runs) {assert.deepEqual(run.result, await oracle(item, run.input)); assert.equal(run.resultSha256, digest(run.result));}
    const changes = [r => {r.mode = 'LOCAL';}, r => {r.runs[0].mode = 'HOSTED';}, r => {r.sourceSha256 = '0'.repeat(64);}, r => {r.runs[1].runId = randomUUID();}, r => {r.runs[0].resultSha256 = 'f'.repeat(64);}, r => {r.runs[0].inputSha256 = 'e'.repeat(64);}, r => {r.recordedAt = '2099-01-01T00:00:00.000Z';}, r => {r.privateStorageKey = 'QA_PRIVATE_CANARY';}, r => {r.runs[0].result.tables[0].columns[0].unit = 'wrong unit'; r.runs[0].resultSha256 = digest(r.runs[0].result);}];
    for (const change of changes) {const bad = structuredClone(recording); change(bad); await assert.rejects(parseDataAiRecording(bad, index, item));}
    await assert.rejects(parseDataAiRecording(text.replace('"mode": "REPLAY"', '"mode":"REPLAY","\\u006dode":"REPLAY"'), index, item));
    if (item.kind === 'AI_ML') {const bad = structuredClone(recording); bad.runs[1].result = structuredClone(bad.runs[0].result); bad.runs[1].resultSha256 = digest(bad.runs[1].result); await assert.rejects(parseDataAiRecording(bad, index, item));}
  }
  for (const key of ['sourceSha256', 'archiveSha256']) {const bad = structuredClone(index); bad[key] = 'a'.repeat(64); assert.throws(() => parseDataAiIndex(bad));}
});

test('actual HTTP rejects malformed allowed text, identities, loaders, paths and framing before a successful control', async t => {
  const app = await start(t), item = catalogue.items[0], sample = fields(app, item.id, item.defaultInputs), basic = new URLSearchParams(sample).toString();
  const denials = [
    [{...sample, runId: randomUUID()}, {}], [{...sample, operationId: 'reset'}, {}], [{...sample, url: 'file:///QA_PRIVATE_CANARY'}, {}], [{...sample, loader: 'pickle'}, {}],
    [Buffer.from(basic + '&%72unId=' + app.runId), {}], [Buffer.from(new URLSearchParams(fields(app, 'sentiment-inference', {text: 'good%Q0text'})).toString().replace('%25Q0', '%Q0')), {}],
    [Buffer.from([0xc3, 0x28]), {}], [Buffer.alloc(8193, 65), {}], [sample, {path: '//request'}], [sample, {path: '/request?model=remote'}],
    [sample, {method: 'PUT'}], [sample, {headers: {Origin: ''}}], [sample, {headers: {'Transfer-Encoding': ''}}], [sample, {headers: {Host: 'foreign.invalid'}}], [sample, {headers: {'Content-Type': 'application/json'}}],
  ];
  for (const [input, options] of denials) {const reply = await send(app, input, options); assert(reply.status >= 400 && reply.status < 500, reply.text); assert.deepEqual(Object.keys(reply.value).sort(), ['error', 'message']); assert.equal(reply.text.includes('QA_PRIVATE_CANARY'), false); assert.equal(Object.keys(reply.headers).some(key => key.startsWith('access-control-')), false);}
  assert.deepEqual((await execute(app, item, item.defaultInputs)).result, await oracle(item, item.defaultInputs)); assert.equal(app.log().includes('QA_PRIVATE_CANARY'), false);
});

test('real health and isolated OS measurements report enforceable local bounds without hosted authority', async t => {
  const app = await start(t), health = await send(app, null, {method: 'GET', path: '/health'});
  assert.equal(health.status, 200); assert.equal(health.value.mode, 'LOCAL'); assert.equal(health.value.runId, app.runId);
  assert.deepEqual(health.value.limits, {requestBytes: 8192, responseBytes: 4096, maxAttempts: 100, lifetimeSeconds: 300, idleSeconds: 90, absoluteRequestSeconds: 1, memoryMiB: 256, cpuSeconds: 10, osLimits: process.platform !== 'win32'});
  if (process.platform !== 'win32') {
    const source = `import importlib.util,json,resource\ns=importlib.util.spec_from_file_location('r',${JSON.stringify(join(base, 'runtime.py'))});m=importlib.util.module_from_spec(s);s.loader.exec_module(m)\na=m.apply_limits();print(json.dumps({'active':a,'memory':resource.getrlimit(resource.RLIMIT_AS),'cpu':resource.getrlimit(resource.RLIMIT_CPU),'files':resource.getrlimit(resource.RLIMIT_NOFILE),'core':resource.getrlimit(resource.RLIMIT_CORE)}))`;
    const measured = JSON.parse(execFileSync('python3', ['-I', '-c', source], {encoding: 'utf8', timeout: 4000}));
    assert.equal(measured.active, true); assert.deepEqual(measured.memory, [268435456, 268435456]); assert.deepEqual(measured.cpu, [10, 10]); assert.deepEqual(measured.files, [64, 64]); assert.deepEqual(measured.core, [0, 0]);
  }
});

test('malformed pre-parser attempts exhaust capacity and continually dripped headers cannot extend expiry', async t => {
  const app = await start(t), item = catalogue.items[0];
  for (let attempt = 0; attempt < 100; attempt++) {
    if (attempt % 2 === 0) assert.match(await raw(app, 'MALFORMED\r\n\r\n'), /^HTTP\/1\.0 400 /);
    else assert.equal((await send(app, fields(app, item.id, item.defaultInputs), {path: '/request?denied=true'})).status, 400);
  }
  const full = await send(app, fields(app, item.id, item.defaultInputs)); assert.equal(full.status, 429); assert.equal(full.value.error, 'ATTEMPT_LIMIT'); await exited(app.child);
  const short = await start(t, {env: {LAB_LIFETIME_SECONDS: '1'}}), before = performance.now(); let timer;
  const response = await new Promise((yes, no) => {
    const socket = connect(short.port, '127.0.0.1'); let source = '';
    t.after(() => {clearInterval(timer); socket.destroy();}); socket.setTimeout(2500, () => socket.destroy(Error('Dribbled headers escaped absolute expiry'))); socket.on('error', no);
    socket.once('connect', () => {socket.write(`POST /request HTTP/1.1\r\nHost: 127.0.0.1:${short.port}\r\nX-Dribble: `); timer = setInterval(() => {if (!socket.destroyed) socket.write('x');}, 80);});
    socket.on('data', chunk => {source += chunk.toString();}); socket.once('close', () => {clearInterval(timer); yes(source);});
  });
  assert.match(response, /^HTTP\/1\.0 408 /); assert.match(response, /REQUEST_TIMEOUT/); assert(performance.now() - before < 1800); await exited(short.child);
  const idle = await start(t, {env: {LAB_IDLE_SECONDS: '1'}}); await exited(idle.child, 1800);
});

test('downloaded archive has fourteen exact licensed originals and clean extraction reproduces all eight recorded outputs', async t => {
  const index = parseDataAiIndex(await readFile(join(root, 'public/data-ai/material-index.json'), 'utf8')), archivePath = join(root, 'public/data-ai/source.tar.gz');
  assert.equal(sha(await readFile(archivePath)), index.archiveSha256); assert.equal(index.sourceSha256, sha(await readFile(join(base, 'runtime.py'))));
  const directory = await mkdtemp(join(tmpdir(), 'getlancer-independent-qa-')); t.after(() => rm(directory, {recursive: true, force: true}));
  // Python tarfile is an independent standard-library archive reader, not the application builder.
  const source = `import json,sys,tarfile,pathlib\na,d,b=map(pathlib.Path,sys.argv[1:]);expected=json.loads((b/'catalogue.json').read_text())['inventory']\nwith tarfile.open(a,'r:gz') as archive:\n members=archive.getmembers();assert [m.name for m in members]==expected\n for m in members:\n  assert m.isfile() and m.mode==0o644 and m.uid==m.gid==m.mtime==0 and m.size<=65536\n  data=archive.extractfile(m).read();assert data==(b/m.name).read_bytes();target=d/m.name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)\nfor n in ['License.md','DATA_LICENSE.md','MODEL_LICENSE.md']: assert 'MIT License' in (d/n).read_text()\nprint(json.dumps({'files':len(members),'safe':True}))`;
  assert.deepEqual(JSON.parse(execFileSync('python3', ['-I', '-c', source, archivePath, directory, base], {encoding: 'utf8', timeout: 5000})), {files: 14, safe: true});
  const app = await start(t, {source: join(directory, 'runtime.py'), cwd: directory});
  for (const item of index.items) {
    const recording = await readJson(join(root, 'public', item.recordingUrl));
    for (const [position, input] of [item.defaultInputs, item.changedInputs].entries()) {const actual = await execute(app, item, input); assert.deepEqual(actual.result, await oracle(item, input)); assert.equal(actual.resultSha256, recording.runs[position].resultSha256);}
  }
});

test('altered downloaded sources reject loader schemas, repinned executable models, stale data and escaping filesystem paths before readiness', async t => {
  const directory = await mkdtemp(join(tmpdir(), 'getlancer-independent-source-negatives-')); t.after(() => rm(directory, {recursive: true, force: true}));
  for (const attack of ['stale-data', 'remote-loader', 'executable-format', 'absolute-path', 'foreign-inventory', 'deep-catalogue', ...(process.platform === 'win32' ? [] : ['linked-models'])]) {
    const clean = join(directory, attack), changed = structuredClone(catalogue);
    for (const name of catalogue.inventory) {await mkdir(dirname(join(clean, name)), {recursive: true}); await writeFile(join(clean, name), await readFile(join(base, name)));}
    const item = changed.items.find(item => item.id === 'sentiment-inference');
    if (attack === 'stale-data') await writeFile(join(clean, 'fixtures/revenue.json'), '[]');
    if (['remote-loader', 'executable-format'].includes(attack)) {
      const model = await readJson(join(clean, 'models/sentiment-inference.json'));
      if (attack === 'remote-loader') model.loader = 'https://private.invalid/QA_MODEL_CANARY'; else model.format = 'pickle';
      const bytes = Buffer.from(JSON.stringify(model)); item.modelSha256 = sha(bytes); await writeFile(join(clean, 'models/sentiment-inference.json'), bytes);
    }
    if (attack === 'absolute-path') changed.assets['sentiment-inference'].modelPath = join(clean, 'models/sentiment-inference.json');
    if (attack === 'foreign-inventory') changed.inventory.push('private-environment.json');
    if (attack === 'linked-models') {
      const external = join(directory, 'external-models'); await mkdir(external, {recursive: true});
      for (const name of ['equipment-inference.json', 'sentiment-inference.json']) await writeFile(join(external, name), await readFile(join(base, 'models', name)));
      await rm(join(clean, 'models'), {recursive: true}); await symlink(external, join(clean, 'models'), 'dir');
    }
    await writeFile(join(clean, 'catalogue.json'), attack === 'deep-catalogue' ? '['.repeat(10000) + '0' + ']'.repeat(10000) : JSON.stringify(changed));
    const result = spawnSync('python3', ['-I', join(clean, 'runtime.py')], {cwd: clean, env: {PATH: process.env.PATH, LAB_RUN_ID: randomUUID(), LAB_PORT: String(await freePort()), LAB_LIFETIME_SECONDS: '1'}, encoding: 'utf8', timeout: 3000, maxBuffer: 8192});
    assert.equal(result.error, undefined, 'Source rejection exceeded its deadline: ' + attack); assert.equal(result.status, 1, 'Unsafe source reached execution: ' + attack);
    assert.equal(result.stdout, ''); assert.equal(result.stderr.trim(), 'Local configuration or pinned assets rejected.'); assert.equal(/READY|Traceback|QA_MODEL_CANARY/.test(result.stderr), false);
  }
});
