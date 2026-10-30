import assert from 'node:assert/strict';
import {test} from 'node:test';
import {queryOsv} from '../scripts/osv-client.mjs';

const queries = [{package: {name: 'example-public-package', ecosystem: 'npm'}, version: '1.2.3'}];

function harness(responses) {
  const calls = [], waits = [], logs = [], cancelled = [];
  return {
    calls, waits, logs, cancelled,
    options: {
      fetchImpl: async (url, init) => {
        calls.push({url, init});
        const response = responses[calls.length - 1];
        assert.ok(response, 'An unexpected extra request exceeded the supplied responses.');
        if (response instanceof Error) throw response;
        return {
          ok: response.status >= 200 && response.status < 300,
          status: response.status,
          body: {cancel: async () => {cancelled.push(response.status);}},
          json: async () => {
            if (response.jsonError) throw response.jsonError;
            return response.json;
          },
        };
      },
      wait: async milliseconds => {waits.push(milliseconds);},
      log: message => {logs.push(message);},
    },
  };
}

test('OSV temporary service recovery repeats only the exact public query payload', async () => {
  const result = {results: [{}]}, h = harness([{status: 503}, {status: 200, json: result}]);
  assert.deepEqual(await queryOsv(queries, h.options), result);
  assert.equal(h.calls.length, 2);
  for (const {url, init} of h.calls) {
    assert.equal(url, 'https://api.osv.dev/v1/querybatch');
    assert.equal(init.method, 'POST');
    assert.deepEqual(init.headers, {'Content-Type': 'application/json'});
    assert.equal(init.body, JSON.stringify({queries}));
    assert.ok(init.signal instanceof AbortSignal);
    assert.equal(init.signal.aborted, false);
    assert.equal(Object.hasOwn(init, 'credentials'), false);
  }
  assert.deepEqual(h.cancelled, [503]);
  assert.deepEqual(h.waits, [1000]);
  assert.equal(h.logs.length, 1);
  assert.match(h.logs[0], /503.*1000ms.*2\/3/);
});

for (const status of [502, 503, 504]) {
  test(`OSV repeated HTTP ${status} fails closed after three attempts`, async () => {
    const h = harness([{status}, {status}, {status}]);
    await assert.rejects(queryOsv(queries, h.options), {message: `OSV returned ${status}; scan incomplete.`});
    assert.equal(h.calls.length, 3);
    assert.deepEqual(h.cancelled, [status, status, status]);
    assert.deepEqual(h.waits, [1000, 2000]);
    assert.equal(h.logs.length, 2);
    assert.match(h.logs[1], /2000ms.*3\/3/);
  });
}

for (const status of [400, 401, 403, 429, 500]) {
  test(`OSV non-retryable HTTP ${status} fails immediately`, async () => {
    const h = harness([{status}]);
    await assert.rejects(queryOsv(queries, h.options), {message: `OSV returned ${status}; scan incomplete.`});
    assert.equal(h.calls.length, 1);
    assert.deepEqual(h.cancelled, [status]);
    assert.deepEqual(h.waits, []);
    assert.deepEqual(h.logs, []);
  });
}

test('OSV transport failures are preserved without retry', async () => {
  const error = new TypeError('controlled connection failure'), h = harness([error]);
  await assert.rejects(queryOsv(queries, h.options), caught => caught === error);
  assert.equal(h.calls.length, 1);
  assert.deepEqual(h.waits, []);
  assert.deepEqual(h.logs, []);
});

test('OSV invalid successful JSON fails immediately instead of becoming a clean scan', async () => {
  const error = new SyntaxError('controlled invalid JSON'), h = harness([{status: 200, jsonError: error}]);
  await assert.rejects(queryOsv(queries, h.options), caught => caught === error);
  assert.equal(h.calls.length, 1);
  assert.deepEqual(h.waits, []);
  assert.deepEqual(h.logs, []);
});

test('OSV advisory results survive transient recovery unchanged for the existing audit gates', async () => {
  const result = {results: [{vulns: [{id: 'GHSA-controlled-evidence'}]}]}, h = harness([{status: 502}, {status: 504}, {status: 200, json: result}]);
  assert.deepEqual(await queryOsv(queries, h.options), result);
  assert.deepEqual(h.waits, [1000, 2000]);
  assert.equal(h.calls.length, 3);
  assert.deepEqual(h.calls.map(({init}) => JSON.parse(init.body)), [{queries}, {queries}, {queries}]);
});
