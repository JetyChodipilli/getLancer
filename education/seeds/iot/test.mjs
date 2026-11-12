import assert from 'node:assert/strict';
import {copyFile, mkdtemp, rm} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {simulate} from './simulate.mjs';

const readings = simulate();
assert.equal(readings.length, 5); assert.equal(readings.filter(row => row.alert).length, 2);
assert.ok(readings.every(row => row.simulated)); assert.deepEqual(readings, simulate());
const directory = await mkdtemp(join(tmpdir(), 'simulated sensors é%-'));
try {
  const entry = join(directory, 'simulate.mjs'); await copyFile(new URL('./simulate.mjs', import.meta.url), entry);
  const result = spawnSync(process.execPath, [entry], {cwd: directory, encoding: 'utf8', timeout: 5000, maxBuffer: 4096});
  assert.equal(result.error, undefined); assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(JSON.parse(result.stdout), readings);
} finally { await rm(directory, {recursive: true, force: true}); }
console.log('Deterministic sensor simulation regression passed; no hardware was exercised.');
