import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {MATERIAL_BYTE_LIMIT, RECORDING_BYTE_LIMIT, parseMaterialIndex, parseScenarioMaterials, parseScenarioRecording, readScenarioFile} from '../lib/scenario-evidence.ts';

// Parser protocol fixtures only. These values are never published or claimed as execution evidence.
const runId = '11111111-1111-4111-8111-111111111111';
const requestId = '22222222-2222-4222-8222-222222222222';
const secondRequest = '33333333-3333-4333-8333-333333333333';
const sourceHash = 'a'.repeat(64), recordedAt = '2026-10-09T10:00:00.123Z';
const catalogue = JSON.parse(await readFile(new URL('../labs/catalogue.json', import.meta.url), 'utf8'));
const material = (language = 'java', pattern = 'cache') => ({id: `${language}-${pattern}`, language, pattern, title: 'Parser fixture source', summary: 'Synthetic parser protocol fixture only.', sourceHash, archiveHash: 'b'.repeat(64), sourceUrl: `/labs/sources/${language}.tar.gz`, recordingUrl: `/labs/recordings/${language}-${pattern}.json`, setup: 'Read the local source README.', scenarios: catalogue.patterns[pattern].scenarios, edges: catalogue.patterns[pattern].edges});
const states = {cache: {cache: 'MISS', storeReads: 1, ttlMs: 5000}, security: {decision: 'ALLOW', reason: 'ALLOW', currentRole: 'editor'}, payment: {payment: 'PAID', entitlements: 1, processedEvents: 1}};
const eventTypes = {cache: 'CACHE_MISS', security: 'AUTH_ALLOWED', payment: 'PAYMENT_APPLIED'};
const eventEdges = {cache: 'service-redis', security: 'service-policy', payment: 'emulator-ledger'};
function fixture(pattern = 'cache', language = 'java') {
  const event = {runId, sourceHash, requestId, sequence: 7, type: eventTypes[pattern], edge: eventEdges[pattern], recordedAt, summary: 'Parser protocol fixture observation.'};
  return {mode: 'REPLAY', labId: `${language}-${pattern}`, language, pattern, runId, sourceHash, recordedAt, responses: [{runId, sourceHash, requestId, pattern, status: 200, durationMs: 2, state: structuredClone(states[pattern]), events: [event]}]};
}
const clone = value => structuredClone(value);
function rejected(change, pattern = 'cache') {const value = fixture(pattern); change(value); assert.throws(() => parseScenarioRecording(value, material('java', pattern)));}

test('strict material index preserves all nine source identities and nullable recording URLs', () => {
  const items = Object.keys(catalogue.languages).flatMap(language => Object.keys(catalogue.patterns).map(pattern => material(language, pattern)));
  items[0].recordingUrl = null;
  items[0].setup = 'Set LAB_RUN_ID to a UUID.\n\tRun the local command.';
  assert.deepEqual(parseScenarioMaterials(JSON.stringify({items})), items);
  assert.deepEqual(parseMaterialIndex({items: []}), {items: []});
});
test('material index rejects foreign identities, duplicate labs, hashes and unsafe download destinations', () => {
  for (const patch of [{id: 'python-cache'}, {language: 'go'}, {pattern: 'unknown'}, {sourceHash: 'A'.repeat(64)}, {archiveHash: 'bad'}, {sourceUrl: '//foreign.example/archive.tar.gz'}, {sourceUrl: '/api/v1/private'}, {recordingUrl: '/labs/recordings/python-cache.json'}, {recordingUrl: 'https://foreign.example/recording'}]) assert.throws(() => parseMaterialIndex({items: [{...material(), ...patch}]}));
  assert.throws(() => parseMaterialIndex({items: [material(), material()]}));
});
test('material index rejects unsupported fields, architectures, controls and oversized metadata', () => {
  for (const change of [item => {item.token = 'private';}, item => {delete item.setup;}, item => {item.setup = 'x'.repeat(2001);}, item => {item.title = 'bad\ncontrol';}, item => {item.scenarios = [];}, item => {item.scenarios = [1];}, item => {item.edges[0].id = 'emulator-ledger';}, item => {item.edges[1].id = item.edges[0].id;}, item => {item.edges[0].endpoint = 'http://private';}, item => {item.edges = [];}]) {
    const item = clone(material()); change(item); assert.throws(() => parseMaterialIndex({items: [item]}));
  }
  assert.throws(() => parseMaterialIndex({items: [], runtime: {enabled: true}}));
});
test('valid parser fixtures retain exact recorded timestamps, hashes, statuses and original process cursor', () => {
  for (const language of Object.keys(catalogue.languages)) for (const pattern of Object.keys(catalogue.patterns)) {
    const value = fixture(pattern, language);
    assert.deepEqual(parseScenarioRecording(JSON.stringify(value), material(language, pattern)), value);
    assert.equal(parseScenarioRecording(value, material(language, pattern)).responses[0].events[0].sequence, 7);
  }
});
test('replay cannot accept local-live or hosted modes and admission-like fields', () => {
  for (const mode of ['Local execution', 'ISOLATED', 'LIVE', 'replay', null]) rejected(value => {value.mode = mode;});
  for (const key of ['verified', 'runtime', 'endpoint', 'data']) rejected(value => {value[key] = true;});
});
test('stale source hash and foreign root lab, language, pattern and run identities are rejected', () => {
  for (const patch of [{sourceHash: 'c'.repeat(64)}, {labId: 'python-cache'}, {language: 'python'}, {pattern: 'security'}, {runId: secondRequest}, {runId: 'not-a-uuid'}]) rejected(value => Object.assign(value, patch));
  assert.throws(() => parseScenarioRecording(fixture(), {...material(), sourceHash: 'c'.repeat(64)}), /stale/);
});
test('response run, source, pattern and request identities bind every event and cannot repeat', () => {
  for (const patch of [{runId: secondRequest}, {sourceHash: 'c'.repeat(64)}, {pattern: 'security'}, {requestId: secondRequest}, {requestId: 'invalid'}]) rejected(value => Object.assign(value.responses[0], patch));
  rejected(value => value.responses.push(clone(value.responses[0])));
  for (const patch of [{runId: secondRequest}, {sourceHash: 'c'.repeat(64)}, {requestId: secondRequest}]) rejected(value => Object.assign(value.responses[0].events[0], patch));
});
test('event sequence increases across response boundaries, permitting genuine gaps', () => {
  const value = fixture(); const next = clone(value.responses[0]); next.requestId = secondRequest; next.events[0].requestId = secondRequest; next.events[0].sequence = 20; value.responses.push(next);
  assert.equal(parseScenarioRecording(value, material()).responses[1].events[0].sequence, 20);
  for (const sequence of [7, 6, 0, -1, 1.2, '8', Number.MAX_SAFE_INTEGER + 1]) {const bad = clone(value); bad.responses[1].events[0].sequence = sequence; assert.throws(() => parseScenarioRecording(bad, material()));}
  rejected(item => item.responses[0].events.push(clone(item.responses[0].events[0])));
});
test('event types, edges and projections reject arbitrary payloads and cross-pattern actions', () => {
  for (const patch of [{type: 'STATE'}, {type: 'AUTH_ALLOWED'}, {edge: 'emulator-ledger'}, {edge: 'service-store'}, {edge: 'client-service'}, {edge: 'redis-private'}, {summary: ''}, {summary: 'x'.repeat(301)}, {summary: 'unsafe\u0000'}, {data: {token: 'private'}}, {signature: 'private'}, {endpoint: 'http://private'}]) rejected(value => Object.assign(value.responses[0].events[0], patch));
  rejected(value => {delete value.responses[0].events[0].summary;});
  rejected(value => {value.responses[0].events = [];});
  rejected(value => {value.responses[0].events = Array.from({length: 5}, (_, index) => ({...value.responses[0].events[0], sequence: index + 1}));});
});
test('observed action binds its edge, returned status and authorization decision', () => {
  rejected(value => {value.responses[0].events[0].type = 'STORE_READ';});
  for (const [type, edge, status] of [['PAYMENT_SIGNATURE_DENIED', 'service-emulator', 200], ['PAYMENT_CONFLICT', 'emulator-ledger', 200], ['PAYMENT_DUPLICATE', 'emulator-ledger', 401], ['REFUND_PENDING', 'emulator-ledger', 200]]) rejected(value => {value.responses[0].status = status; Object.assign(value.responses[0].events[0], {type, edge});}, 'payment');
  rejected(value => {value.responses[0].events[0].type = 'AUTH_DENIED';}, 'security');
  rejected(value => {Object.assign(value.responses[0].events[0], {type: 'AUTH_RESET', edge: 'policy-store'});}, 'security');
  const roleObservation = fixture('security'); roleObservation.responses[0].events[0].edge = 'policy-store'; assert.equal(parseScenarioRecording(roleObservation, material('java', 'security')).responses[0].events[0].edge, 'policy-store');
});
test('only exact bounded pattern states are accepted', () => {
  for (const patch of [{cache: 'UNKNOWN'}, {storeReads: -1}, {storeReads: 1.5}, {ttlMs: 5001}, {ttlMs: -3}, {cache: 'MISS', signature: 'raw'}, {entitlements: 1}]) rejected(value => Object.assign(value.responses[0].state, patch));
  for (const patch of [{decision: 'ALLOW', reason: 'ROLE'}, {decision: 'DENY', reason: 'ALLOW'}, {decision: 'ALLOW', currentRole: 'viewer'}, {currentRole: 'owner'}, {reason: 'UNAVAILABLE'}]) rejected(value => Object.assign(value.responses[0].state, patch), 'security');
  for (const patch of [{entitlements: 0}, {payment: 'PENDING'}, {payment: 'REFUNDED'}, {processedEvents: 101}, {processedEvents: -1}, {secret: 'raw'}]) rejected(value => Object.assign(value.responses[0].state, patch), 'payment');
});
test('security denial status preserves signature, expiry, tenant, role and revoke meaning', () => {
  for (const [reason, status] of [['SIGNATURE', 401], ['EXPIRED', 401], ['TENANT', 403], ['ROLE', 403], ['REVOKED', 403], ['REVOKED', 200]]) {
    const value = fixture('security'); Object.assign(value.responses[0], {status, state: {decision: 'DENY', reason, currentRole: 'viewer'}}); value.responses[0].events[0].type = reason === 'REVOKED' && status === 200 ? 'AUTH_REVOKED' : 'AUTH_DENIED'; value.responses[0].events[0].edge = reason === 'REVOKED' ? 'policy-store' : 'service-policy';
    assert.equal(parseScenarioRecording(value, material('java', 'security')).responses[0].status, status);
  }
  rejected(value => {value.responses[0].status = 401;}, 'security');
  rejected(value => {value.responses[0].status = 403;}, 'security');
});
test('payment pending, signature denial and conflict retain bounded state without inventing entitlement', () => {
  for (const [type, status] of [['PAYMENT_PENDING', 202], ['PAYMENT_SIGNATURE_DENIED', 401], ['PAYMENT_CONFLICT', 409], ['REFUND_PENDING', 202]]) {
    const value = fixture('payment'); Object.assign(value.responses[0], {status, state: {payment: 'PENDING', entitlements: 0, processedEvents: type === 'REFUND_PENDING' ? 1 : 0}}); value.responses[0].events[0].type = type; value.responses[0].events[0].edge = ['PAYMENT_PENDING', 'PAYMENT_SIGNATURE_DENIED'].includes(type) ? 'service-emulator' : 'emulator-ledger';
    assert.equal(parseScenarioRecording(value, material('java', 'payment')).responses[0].state.entitlements, 0);
  }
  rejected(value => {value.responses[0].status = 202;}, 'payment');
});
test('observations cannot misrepresent final cache, authorization or payment facts', () => {
  rejected(value => {value.responses[0].events[0].type = 'CACHE_HIT';});
  rejected(value => {value.responses[0].state.cache = 'HIT';});
  rejected(value => {Object.assign(value.responses[0].state, {decision: 'DENY', reason: 'REVOKED', currentRole: 'viewer'}); value.responses[0].events[0].type = 'AUTH_DENIED';}, 'security');
  rejected(value => {Object.assign(value.responses[0], {status: 403, state: {decision: 'DENY', reason: 'REVOKED', currentRole: 'viewer'}}); Object.assign(value.responses[0].events[0], {type: 'AUTH_REVOKED', edge: 'policy-store'});}, 'security');
  rejected(value => {Object.assign(value.responses[0].state, {payment: 'PENDING', entitlements: 0});}, 'payment');
  rejected(value => {value.responses[0].events[0].type = 'REFUND_APPLIED';}, 'payment');
  rejected(value => {value.responses[0].events[0].type = 'PAYMENT_RESET';}, 'payment');
  rejected(value => {Object.assign(value.responses[0], {status: 202, state: {payment: 'PENDING', entitlements: 0, processedEvents: 0}}); value.responses[0].events[0].type = 'REFUND_PENDING';}, 'payment');
  for (const payment of ['PAID', 'REFUNDED']) {
    const value = fixture('payment'); Object.assign(value.responses[0].state, {payment, entitlements: payment === 'PAID' ? 1 : 0}); Object.assign(value.responses[0].events[0], {type: 'PAYMENT_PENDING', edge: 'service-emulator'});
    assert.equal(parseScenarioRecording(value, material('java', 'payment')).responses[0].status, 200);
  }
});
test('invalid response status, duration, fields and empty or oversized recordings reject', () => {
  for (const patch of [{status: 500}, {status: '200'}, {durationMs: -1}, {durationMs: 1.2}, {durationMs: 30001}, {token: 'raw'}]) rejected(value => Object.assign(value.responses[0], patch));
  rejected(value => {value.responses = [];}); rejected(value => {value.responses = {};});
  rejected(value => {value.responses = Array.from({length: 129}, () => clone(value.responses[0]));});
  rejected(value => {delete value.responses[0].status;});
});
test('UTC timestamps are original values and invalid calendar dates, offsets and controls reject', () => {
  const value = fixture(); value.recordedAt = '2026-10-09T10:00:00.123456789Z'; value.responses[0].events[0].recordedAt = value.recordedAt;
  assert.equal(parseScenarioRecording(value, material()).recordedAt, value.recordedAt);
  for (const recordedAt of ['invalid', '2026-02-31T10:00:00Z', '2026-10-09T24:00:00Z', '2026-10-09T10:00:00+00:00', '2026-10-09', '2026-10-09T10:00:00Z\n']) {
    rejected(value => {value.recordedAt = recordedAt;}); rejected(value => {value.responses[0].events[0].recordedAt = recordedAt;});
  }
});
test('the recording timestamp matches its first observation and event times never move backwards', () => {
  rejected(value => {value.recordedAt = '2026-10-09T10:00:01Z';});
  rejected(value => {value.responses[0].events.push({...value.responses[0].events[0], sequence: 8, recordedAt: '2026-10-09T09:59:59Z'});});
});
test('raw JSON rejects duplicate and escape-equivalent fields before projection', () => {
  const source = JSON.stringify(fixture());
  assert.throws(() => parseScenarioRecording(source.replace('"mode":"REPLAY"', '"mode":"LIVE","mode":"REPLAY"'), material()), /duplicate/);
  assert.throws(() => parseScenarioRecording(source.replace('"status":200', '"status":500,"stat\\u0075s":200'), material()), /duplicate/);
  assert.throws(() => parseMaterialIndex('{"items":[],"items":[]}'), /duplicate/);
});
test('JSON bounds count UTF-8 bytes, reject malformed values and deeply nested input', () => {
  assert.throws(() => parseScenarioRecording(' '.repeat(RECORDING_BYTE_LIMIT + 1), material()), /size limit/);
  assert.throws(() => parseMaterialIndex(' '.repeat(MATERIAL_BYTE_LIMIT + 1)), /size limit/);
  assert.throws(() => parseMaterialIndex('"' + 'é'.repeat(MATERIAL_BYTE_LIMIT / 2) + '"'), /size limit/);
  for (const source of ['', '{', 'null', '[]', '1', 'true']) assert.throws(() => parseScenarioRecording(source, material()));
  assert.throws(() => parseMaterialIndex('['.repeat(18) + '0' + ']'.repeat(18)));
  const circular = {}; circular.loop = circular; assert.throws(() => parseMaterialIndex(circular));
});
test('a response projection has its own 4096 byte limit inside the recording bound', () => {
  rejected(value => {value.responses[0].events = Array.from({length: 4}, (_, index) => ({...value.responses[0].events[0], sequence: index + 1, summary: '漢'.repeat(300)}));});
});
test('streamed reader refuses HTTP failures, non-JSON and oversized bytes', async () => {
  await assert.rejects(readScenarioFile(new Response('{}', {status: 503, headers: {'Content-Type': 'application/json'}}), 10), /HTTP 503/);
  await assert.rejects(readScenarioFile(new Response('{}', {headers: {'Content-Type': 'text/html'}}), 10), /JSON/);
  await assert.rejects(readScenarioFile(new Response('ééé', {headers: {'Content-Type': 'application/json', 'Content-Length': '1'}}), 5), /size limit/);
  const body = '{"items":[]}'; assert.equal(await readScenarioFile(new Response(body, {headers: {'Content-Type': 'application/json'}}), 100), body);
});
test('streamed reader detects malformed UTF-8 even when JSON would replace its bytes', async () => {
  await assert.rejects(readScenarioFile(new Response(new Uint8Array([0x7b, 0xc3, 0x28, 0x7d]), {headers: {'Content-Type': 'application/json'}}), 100));
});
