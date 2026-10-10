import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync, mkdirSync, cpSync, copyFileSync, readFileSync, writeFileSync, rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join, dirname} from 'node:path';
import {spawnSync} from 'node:child_process';

test('stale source archive fails with a bounded diagnostic instead of expanding a binary diff', t => {
  const dir = mkdtempSync(join(tmpdir(), 'getlancer-material-'));
  t.after(() => rmSync(dir, {recursive: true, force: true}));
  cpSync(new URL('../labs', import.meta.url), join(dir, 'labs'), {recursive: true});
  for (const file of ['scripts/build-lab-material.mjs', 'scripts/verify-lab-scenarios.mjs', 'scripts/lab-fixture-ports.mjs', 'lib/scenario-evidence.ts', 'docs/v48/CONTRACT.md', 'docs/v48/License.md']) {
    mkdirSync(dirname(join(dir, file)), {recursive: true});
    copyFileSync(new URL('../' + file, import.meta.url), join(dir, file));
  }
  writeFileSync(join(dir, 'package.json'), '{"type":"module"}');
  const run = (...args) => spawnSync(process.execPath, ['--max-old-space-size=64', '--experimental-strip-types', 'scripts/build-lab-material.mjs', ...args], {cwd: dir, encoding: 'utf8', timeout: 15000});
  const built = run();
  assert.equal(built.status, 0, built.stderr);
  assert.equal(run('--verify').status, 0);
  const path = join(dir, 'public/labs/sources/java.tar.gz');
  const archive = readFileSync(path);
  archive.reverse();
  writeFileSync(path, archive);
  const result = run('--verify');
  assert.equal(result.status, 1);
  assert.equal(result.signal, null);
  assert.match(result.stderr, /Material drift:/);
  assert(Buffer.byteLength(result.stderr) < 4096);
});
