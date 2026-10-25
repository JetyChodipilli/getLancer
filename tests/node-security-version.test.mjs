import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, readdirSync} from 'node:fs';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {supportedNodeVersion, requireSupportedNodeVersion} from '../scripts/node-version.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const read = path => readFileSync(join(root, path), 'utf8');

test('local Node guard accepts patched stable LTS families at their security boundaries', () => {
  for (const version of ['22.23.4', 'v22.23.4', '22.23.5', '22.24.0', '24.22.1', 'v24.22.1', '24.22.2', '24.23.0']) {
    assert.equal(supportedNodeVersion(version), true, version);
    assert.doesNotThrow(() => requireSupportedNodeVersion(version), version);
  }
});

test('local Node guard rejects vulnerable patches, unsupported majors and prereleases', () => {
  for (const version of ['20.20.0', '22.13.0', '22.18.0', '22.22.99', '22.23.3', '23.0.0', '24.0.0', '24.21.99', '24.22.0', '25.0.0', '26.12.1', '22.23.4-rc.1', '24.22.1-nightly', '22.23.4+custom', '022.23.4', '22.23', '22.23.4.1', '22.23.4\n', '', null, 22]) {
    assert.equal(supportedNodeVersion(version), false, String(version));
    assert.throws(() => requireSupportedNodeVersion(version), /Use stable Node 22\.23\.4/, String(version));
  }
});

test('developer defaults, package engines, every CI Node action and Docker stage agree on patched Node', () => {
  const selected = read('.nvmrc').trim();
  assert.equal(selected, '22.23.4');
  assert.equal(read('.node-version').trim(), selected);
  assert.equal(supportedNodeVersion(selected), true);
  const pkg = JSON.parse(read('package.json')), lock = JSON.parse(read('package-lock.json'));
  assert.equal(pkg.engines.node, '>=22.23.4 <23 || >=24.22.1 <25');
  assert.deepEqual(lock.packages[''].engines, pkg.engines);
  for (const framework of ['vue', 'svelte', 'angular']) {
    const example = JSON.parse(read(`examples/frameworks/${framework}/package.json`));
    const exampleLock = JSON.parse(read(`examples/frameworks/${framework}/package-lock.json`));
    assert.equal(example.engines.node, pkg.engines.node, framework + ': source download must require a patched runtime');
    assert.deepEqual(exampleLock.packages[''].engines, example.engines, framework + ': lock root must match the source prerequisite');
  }
  let actions = 0;
  for (const file of readdirSync(join(root, '.github/workflows')).filter(name => /\.ya?ml$/.test(name))) {
    const source = read('.github/workflows/' + file);
    for (const step of source.split(/\n\s*-(?=\s)/).filter(step => /uses:\s*actions\/setup-node@/.test(step))) {
      const pin = /node-version:\s*['"]([^'"]+)['"]/.exec(step)?.[1];
      assert.equal(pin, selected, file + ': setup-node must pin the reviewed patch');
      assert.equal(supportedNodeVersion(pin), true, file);
      actions++;
    }
  }
  assert.ok(actions > 0, 'CI must exercise the pinned runtime');
  function dockerfiles(directory) {
    return readdirSync(join(root, directory), {withFileTypes: true}).flatMap(entry => entry.isDirectory()
      ? dockerfiles(join(directory, entry.name))
      : entry.name.startsWith('Dockerfile') ? [join(directory, entry.name)] : []);
  }
  const stages = dockerfiles('ops').flatMap(file => [...read(file).matchAll(/^FROM\s+node:([^\s]+).*$/gm)].map(match => ({file, tag: match[1]})));
  assert.ok(stages.length > 0, 'Docker must exercise the pinned runtime');
  for (const {file, tag} of stages) {
    const version = tag.split('-')[0];
    assert.equal(version, selected, file + ': Node image must pin the reviewed patch');
    assert.equal(supportedNodeVersion(version), true, file);
  }
});
