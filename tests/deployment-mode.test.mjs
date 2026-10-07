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

test('hosted deployments fail closed without a backend and require HTTPS', () => {
  for (const APP_ENV of ['staging', 'production']) {
    assert.throws(() => resolveBackendOrigin({APP_ENV}), /BACKEND_URL is required/);
    assert.throws(() => resolveBackendOrigin({APP_ENV, DEMO_MODE:'true'}), /BACKEND_URL is required/);
    for (const BACKEND_URL of ['http://api.example.test', 'http://localhost:8080', 'http://api:8080']) {
      assert.throws(() => resolveBackendOrigin({APP_ENV, BACKEND_URL}), /HTTPS/);
    }
    assert.equal(resolveBackendOrigin({APP_ENV, BACKEND_URL:'https://api.example.test'}), 'https://api.example.test');
  }
});

test('HTTP is limited to an explicit local/test environment and exact development hosts', () => {
  for (const APP_ENV of ['local', 'test']) {
    for (const host of ['localhost', '127.0.0.1', '[::1]', 'api']) {
      assert.equal(resolveBackendOrigin({APP_ENV, BACKEND_URL:`http://${host}:8080`}), `http://${host}:8080`);
    }
    for (const host of ['localhost.example.test', 'api.example.test', 'evil.localhost', '192.168.1.1', '0.0.0.0']) {
      assert.throws(() => resolveBackendOrigin({APP_ENV, BACKEND_URL:`http://${host}:8080`}), /HTTPS/);
    }
  }
  assert.throws(() => resolveBackendOrigin({BACKEND_URL:'http://localhost:8080'}), /HTTPS/);
  assert.throws(() => resolveBackendOrigin({APP_ENV:'prod', BACKEND_URL:'https://api.example.test'}), /APP_ENV/);
  assert.throws(() => resolveBackendOrigin({DEMO_MODE:'invalid', BACKEND_URL:'https://api.example.test'}), /DEMO_MODE/);
});
