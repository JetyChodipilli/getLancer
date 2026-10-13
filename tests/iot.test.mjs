import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp, rm, readFile, writeFile, cp, mkdir, lstat} from 'node:fs/promises';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {pathToFileURL} from 'node:url';
import {spawnSync} from 'node:child_process';
import {createIotSession, iotAction} from '../lib/iot.ts';
import {inventory, sourceFiles, archiveFor, unpackArchive, materialIndex, buildMaterial, base} from '../scripts/build-iot-material.mjs';

const runId = '00000000-0000-4000-8000-000000000001';
const publish = (temperatureC, humidityPercent = 45, thresholdC = 30) => ({type: 'publish', temperatureC, humidityPercent, thresholdC});

test('browser-local pure state handles changed telemetry, threshold equality and immutable reset/reconnect', () => {
  let state = createIotSession(runId, 100); assert.equal(state.topic, `getlancer/${runId}/telemetry`); assert.equal(state.connected, true);
  const original = structuredClone(state); state = iotAction(state, publish(20), 101); assert.deepEqual(original, createIotSession(runId, 100)); assert.equal(state.actuatorOn, false); assert.equal(state.sequence, 1);
  state = iotAction(state, publish(30, 70), 102); assert.equal(state.actuatorOn, true); assert.equal(state.humidityPercent, 70); assert.deepEqual(state.messages.map(message => message.temperatureC), [20, 30]);
  state = iotAction(state, {type: 'disconnect'}, 103); assert.equal(state.connected, false); assert.throws(() => iotAction(state, publish(40), 104), /Connect/); assert.equal(state.sequence, 3);
  state = iotAction(state, {type: 'connect'}, 104); assert.equal(state.temperatureC, 30);
  state = iotAction(state, {type: 'reset'}, 105); assert.equal(state.runId, runId); assert.equal(state.sequence, 5); assert.equal(state.connected, true); assert.equal(state.temperatureC, 24); assert.equal(state.humidityPercent, 45); assert.equal(state.thresholdC, 30); assert.equal(state.actuatorOn, false); assert.equal(state.messages.length, 0); assert.deepEqual(state.events.map(event => event.type), ['publish', 'publish', 'disconnect', 'connect', 'reset']);
});

test('browser-local bounds, foreign topic, malformed action/state and timestamps fail without mutation', () => {
  const state = createIotSession(runId, 100), before = structuredClone(state);
  for (const action of [publish(-21), publish(81), publish(NaN), publish(Infinity), publish(1, -1), publish(1, 101), publish(1, 40, -1), publish(1, 40, 61), publish(1.0000001), {...publish(20), topic: 'getlancer/foreign/telemetry'}, {...publish(20), privateKey: 'secret'}, {type: 'unsupported'}, {type: 'reset', network: 'anything'}]) assert.throws(() => iotAction(state, action, 101));
  for (const broken of [{...state, topic: 'foreign'}, {...state, sequence: -1}, {...state, actuatorOn: 'yes'}, {...state, privateCredential: 'secret'}]) assert.throws(() => iotAction(broken, publish(20), 101));
  assert.throws(() => createIotSession('invalid')); assert.throws(() => createIotSession(runId, NaN)); assert.throws(() => iotAction(state, publish(20), Infinity)); assert.deepEqual(state, before);
  const changed = iotAction(state, publish(20), 101); assert.throws(() => iotAction(changed, publish(21), 100)); changed.messages[0].actuatorOn = true; assert.throws(() => iotAction(changed, publish(21), 102));
});

test('browser history stays at twenty and one hundred actions cannot be extended by reset', () => {
  let state = createIotSession(runId, 0); for (let i = 1; i <= 99; i++) state = iotAction(state, publish(i % 60, i % 100, 30), i);
  assert.equal(state.messages.length, 20); assert.equal(state.events.length, 20); assert.equal(state.messages[0].sequence, 80); state = iotAction(state, {type: 'reset'}, 100); assert.equal(state.sequence, 100); assert.throws(() => iotAction(state, {type: 'reset'}, 101), /100-action/); assert.notEqual(createIotSession().runId, createIotSession().runId);
});

test('fixed eleven-file MIT source archive is deterministic, source-bound and rejects unsafe names/omissions', async () => {
  const {files} = await sourceFiles(), archive = archiveFor(files), index = materialIndex(files, archive); assert.equal(files.length, 11); assert.deepEqual(files.map(([name]) => name), inventory); assert(archive.equals(archiveFor(files))); assert.equal(unpackArchive(archive, files).size, 11); assert.equal(index.physicalProof, null); assert(index.firmware.every(item => item.physicalExecutionVerified === false));
  for (const name of ['../secret', '/etc/passwd', 'firmware/../../env']) assert.throws(() => archiveFor([[name, Buffer.from('denied')]])); assert.throws(() => unpackArchive(archiveFor(files.slice(1)), files)); const corrupt = Buffer.from(archive); corrupt[corrupt.length / 2 | 0] ^= 64; assert.throws(() => unpackArchive(corrupt, files));
});

test('builder executes current and clean extracted MQTT source before deterministic write and verify', async t => {
  const temporary = await mkdtemp(join(tmpdir(), 'getlancer-iot-build-')); t.after(() => rm(temporary, {recursive: true, force: true}));
  const first = await buildMaterial(false, temporary), checked = await buildMaterial(true, temporary); assert.equal(first.sourceSha256, checked.sourceSha256); assert.equal(first.archiveSha256, checked.archiveSha256); assert.equal(first.currentActions, 5); assert.equal(first.cleanExtractedActions, 5); assert.equal(first.cleanupEmpty, true); assert(first.maxResponseBytes < 20000);
  await writeFile(join(temporary, 'material-index.json'), '{}'); await assert.rejects(() => buildMaterial(true, temporary), /material drift/);
});

test('actual builder CLI creates checked material and --verify succeeds in a clean copied tree', async t => {
  const temporary = await mkdtemp(join(tmpdir(), 'getlancer-iot-cli-build-')); t.after(() => rm(temporary, {recursive: true, force: true}));
  await cp(base, join(temporary, 'labs/iot'), {recursive: true}); await mkdir(join(temporary, 'scripts'), {recursive: true});
  for (const name of ['build-iot-material.mjs', 'build-data-ai-material.mjs', 'verify-iot.mjs']) await writeFile(join(temporary, 'scripts', name), await readFile(new URL('../scripts/' + name, import.meta.url)));
  for (const args of [[], ['--verify']]) {
    const result = spawnSync(process.execPath, ['scripts/build-iot-material.mjs', ...args], {cwd: temporary, encoding: 'utf8', timeout: 10000, maxBuffer: 16384}); assert.equal(result.status, 0, result.stderr); assert.equal(result.stderr, ''); assert.match(result.stdout, /^IOT_MATERIAL_OK /); const checked = JSON.parse(result.stdout.slice('IOT_MATERIAL_OK '.length)); assert.equal(checked.currentActions, 5); assert.equal(checked.cleanExtractedActions, 5); assert.equal(checked.cleanupEmpty, true);
  }
  const index = JSON.parse(await readFile(join(temporary, 'public/iot/material-index.json'), 'utf8')); assert.equal(index.sourceLicense, 'MIT'); assert.equal(index.physicalProof, null); assert(index.setup.startsWith('Node 22.18+')); const archive = await readFile(join(temporary, 'public/iot/source.tar.gz')); const {files} = await sourceFiles(); assert.equal(unpackArchive(archive, files).size, 11);
});

test('source drift during checking refuses every artifact write', async t => {
  const temporary = await mkdtemp(join(tmpdir(), 'getlancer-iot-drift-')); t.after(() => rm(temporary, {recursive: true, force: true}));
  await cp(base, join(temporary, 'labs/iot'), {recursive: true}); await mkdir(join(temporary, 'scripts'), {recursive: true});
  for (const name of ['build-iot-material.mjs', 'build-data-ai-material.mjs']) await writeFile(join(temporary, 'scripts', name), await readFile(new URL('../scripts/' + name, import.meta.url)));
  await writeFile(join(temporary, 'scripts/verify-iot.mjs'), `import {sourceFiles,archiveFor,sha,base} from './build-iot-material.mjs';import {appendFile} from 'node:fs/promises';import {join} from 'node:path';export async function verifyIot(){const {files}=await sourceFiles();const result={sourceSha256:sha(files.find(([name])=>name==='runtime.mjs')[1]),archiveSha256:sha(archiveFor(files)),measurements:{}};await appendFile(join(base,'README.md'),'\\nconcurrent change\\n');return result;}`);
  const copy = await import(pathToFileURL(join(temporary, 'scripts/build-iot-material.mjs')).href); await assert.rejects(() => copy.buildMaterial(), /archive changed during verification/); await assert.rejects(() => lstat(join(temporary, 'public/iot')), {code: 'ENOENT'});
});

test('MicroPython original source parses as Python without executing hardware imports', async () => {
  const source = join(base, 'firmware/micropython/main.py'); const result = spawnSync('python3', ['-c', 'import sys; compile(open(sys.argv[1], encoding="utf-8").read(), sys.argv[1], "exec")', source], {encoding: 'utf8', timeout: 3000, maxBuffer: 4096}); assert.equal(result.status, 0, result.stderr);
});
