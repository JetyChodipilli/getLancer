import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
test('separate provider executable lifecycle and hostile transport regressions', () => {
  const run = spawnSync('python3', ['-m', 'unittest', 'discover', '-s', 'ops/lab-provider', '-p', 'test_*.py'], {
    cwd: root, encoding: 'utf8', timeout: 30000,
  });
  assert.equal(run.status, 0, run.stdout + run.stderr);
  assert.match(run.stderr, /Ran \d+ tests[\s\S]*\bOK\b/);
});
test('provider command and response fields remain aligned with Java records', () => {
  const java = readFileSync(path.join(root, 'backend/src/main/java/com/getlancer/labs/LabProviderClient.java'), 'utf8');
  const names = record => java.match(new RegExp(`record ${record}\\(([\\s\\S]*?)\\) \\{\\}`))[1]
    .split(/,(?![^<]*>)/).map(field => field.trim().split(/\s+/).at(-1)).sort();
  const run = spawnSync('python3', ['-c', "import sys,json;sys.path.insert(0,'ops/lab-provider');from protocol import FIELDS,result; c={k:None for k in FIELDS}; print(json.dumps({'command':sorted(FIELDS),'result':sorted(result(c,'CLEANED'))}))"], { cwd: root, encoding: 'utf8' });
  assert.equal(run.status, 0, run.stderr);
  const python = JSON.parse(run.stdout);
  assert.deepEqual(python.command, names('Command'));
  assert.deepEqual(python.result, names('Result'));
});
