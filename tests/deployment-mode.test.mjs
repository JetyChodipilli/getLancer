import assert from 'node:assert/strict';
import test from 'node:test';
import {resolveBackendOrigin} from '../lib/deployment-mode.ts';

test('a configured real backend takes priority over a leftover demo flag', () => {
  assert.equal(resolveBackendOrigin({DEMO_MODE:'true', BACKEND_URL:'https://api.example.test'}), 'https://api.example.test');
});

test('real deployments keep their configured backend origin', () => {
  assert.equal(resolveBackendOrigin({DEMO_MODE:'false', BACKEND_URL:'https://api.example.test/'}), 'https://api.example.test');
});

test('an unconfigured deployment uses the isolated preview', () => {
  assert.equal(resolveBackendOrigin({}), undefined);
  assert.equal(resolveBackendOrigin({BACKEND_URL:''}), undefined);
  assert.equal(resolveBackendOrigin({DEMO_MODE:'true'}), undefined);
});

test('explicit real mode refuses to silently show demo data without a backend', () => {
  assert.throws(()=>resolveBackendOrigin({DEMO_MODE:'false'}), /BACKEND_URL is required/);
  assert.throws(()=>resolveBackendOrigin({DEMO_MODE:'false',BACKEND_URL:'  '}), /BACKEND_URL is required/);
});

test('invalid backend settings fail rather than selecting the sample catalog', () => {
  for(const BACKEND_URL of ['not-a-url','file:///tmp/data','https://user:password@api.example.test','https://api.example.test/api/v1','https://api.example.test?demo=true','https://api.example.test/#preview']){
    assert.throws(()=>resolveBackendOrigin({DEMO_MODE:'true',BACKEND_URL}));
  }
});
