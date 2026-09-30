import assert from 'node:assert/strict';
import test from 'node:test';
import {resolveBackendOrigin} from '../lib/deployment-mode.ts';

test('cloud demo mode prevents connecting to a configured real backend', () => {
  assert.equal(resolveBackendOrigin({DEMO_MODE:'true', BACKEND_URL:'https://api.example.test'}), undefined);
});

test('real deployments keep their configured backend origin', () => {
  assert.equal(resolveBackendOrigin({DEMO_MODE:'false', BACKEND_URL:'https://api.example.test/'}), 'https://api.example.test');
});

test('an unconfigured deployment uses the isolated preview', () => {
  assert.equal(resolveBackendOrigin({}), undefined);
  assert.equal(resolveBackendOrigin({BACKEND_URL:''}), undefined);
});
