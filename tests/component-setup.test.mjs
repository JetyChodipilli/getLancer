import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {setupBaselineLine, validateSetupCatalog} from '../scripts/verify-component-setup.mjs';

const seeds = JSON.parse(readFileSync('backend/src/main/resources/catalog/components.json', 'utf8'));
test('all24 new immutable packages document exact runtime and complete reproducible setup', () => {
  assert.equal(validateSetupCatalog(seeds).length, 24);
  assert.deepEqual(JSON.parse(readFileSync('backend/src/main/resources/catalog/components.metadata.json', 'utf8')), seeds.map(({files, ...metadata}) => metadata));
});
test('setup verification rejects missing runtime evidence, edited source and reused versions', () => {
  for (const mutate of [item => { item.files['README.md'] = item.files['README.md'].replace(setupBaselineLine, 'Modern browser'); },
    item => { item.files['index.html'] += '<!-- altered -->'; }, item => { item.version = '1.0.0'; }]) {
    const corrupted = structuredClone(seeds); mutate(corrupted[0]); assert.throws(() => validateSetupCatalog(corrupted));
  }
});
