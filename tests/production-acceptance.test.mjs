/** Disposable validator keys and invented observations are TEST FIXTURES, never production evidence. */
import assert from 'node:assert/strict';
import test from 'node:test';
import {generateKeyPairSync, randomUUID, sign} from 'node:crypto';
import {mkdtempSync, rmSync, writeFileSync, symlinkSync, readFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import {spawnSync} from 'node:child_process';
import {Resolver} from 'node:dns/promises';
import {ACTIVATION_CONTROLS, appendObservation, canonical, CONTROL_FACTS, DRILL_CONTROLS, envelope, HANDOVER_CONTROLS, hash, journalReport, openReport, readJournal} from '../ops/v52/evidence.mjs';
import {boundedLookup, observeTls, reviewOperations, reviewPreflight, reviewStartup, sourceDigests, targetPolicy, verifyProduction} from '../ops/production/qualification.mjs';

const NOW = Date.parse('2026-10-22T12:00:00.000Z'), ISO = value => new Date(value).toISOString(), REVISION = '1'.repeat(40), EPOCH = '11111111-1111-4111-8111-111111111111', OTHER = '22222222-2222-4222-8222-222222222222';
const clone = value => structuredClone(value), sources = () => [{id: 'component-source', path: '/api/v1/components/qualification/source?sha256=' + hash('component'), sha256: hash('component'), sizeBytes: 50}, {id: 'education-source', path: '/api/v1/education-releases/' + OTHER + '/source', sha256: hash('education'), sizeBytes: 50}];
function policy() {
  const versions = {java: ['jdk', '17.0.13'], typescript: ['node', '22.23.4'], python: ['python', '3.12.8'], go: ['go', '1.24.13'], dotnet: ['dotnet', '8.0.15'], php: ['php', '8.3.14']};
  return {version: 1, environment: 'production', revision: REVISION, provider: 'synthetic-provider', operatorEpoch: EPOCH, hostIdSha256: hash('synthetic-host'), appOrigin: 'https://getlancer-qualification.dev', gatewayOrigin: 'https://runtime-qualification.dev', providerFiles: Object.fromEntries(['firecracker', 'jailer', 'kernel'].map(name => [name, {path: '/opt/qualification/' + name, sha256: hash(name)}])), adapters: Object.entries(sourceDigests()).map(([language, sourceSha256]) => ({language, runtime: versions[language][0], version: versions[language][1], framework: 'Standard library', sourceSha256, imageDigest: 'sha256:' + hash(language)})), sources: sources()};
}
function common(target, kind) {return {version: 1, kind, purpose: 'MEASUREMENT', environment: target.environment, revision: target.revision, provider: target.provider, operatorEpoch: target.operatorEpoch, hostIdSha256: target.hostIdSha256, observedAt: ISO(NOW)};}
function preflight(target) {return {...common(target, 'PRODUCTION_PREFLIGHT'), targetSha256: hash(canonical(target)), checks: Object.fromEntries(['linux', 'kvmReadWrite', 'cgroupV2', 'systemd250', 'hostIdentity', 'firecracker', 'jailer', 'kernel'].map(name => [name, true])), tls: [target.appOrigin, target.gatewayOrigin].map(origin => ({origin, authorized: true, protocol: 'TLSv1.3', certificateSha256: hash(origin), validUntil: ISO(NOW + 86400000), httpsOnly: true}))};}
function startup(target, licence = hash('licence'), reproduction = hash('build')) {
  let count = 0;
  return {...common(target, 'HOSTED_STARTUP'), adapters: target.adapters.map(adapter => ({...adapter, maintainer: 'qualification-owner', licenceEvidenceSha256: licence, reproductionEvidenceSha256: reproduction, samples: Array.from({length: 100}, (_, i) => {const admittedAtMs = NOW - (600 - count++) * 40000, readyAtMs = admittedAtMs + (i % 2 ? 10000 : 1000); return {runId: randomUUID(), kind: i % 2 ? 'COLD' : 'WARM', queuedAtMs: admittedAtMs - 50, admittedAtMs, readyAtMs, cleanedAtMs: readyAtMs + 100};})}))};
}
function operations(target, traces = Object.fromEntries(['isolation', 'query-components', 'query-college-projects', 'recovery', 'merchant', 'human'].map(name => [name, {sha256: hash(name)}]))) {
  return {...common(target, 'PRODUCTION_OPERATIONS'), infrastructure: {isolationTraceSha256: traces.isolation.sha256, ...Object.fromEntries(['kernel', 'firecracker', 'jailer'].map(name => [name + 'Sha256', target.providerFiles[name].sha256])), networkRejected: true, cgroupLimitsObserved: true, hardLeaseObserved: true, crashCleanupObserved: true, orphanAdmissionDenied: true}, queryPlans: ['components', 'college-projects'].map(catalogue => ({catalogue, eligibleRows: 10000, planSha256: traces['query-' + catalogue].sha256, observedAt: ISO(NOW)})), recovery: {traceSha256: traces.recovery.sha256, backupAt: ISO(NOW - 100000), incidentAt: ISO(NOW - 50000), recoveredAt: ISO(NOW), encryptedArchive: true, separateRestoreTarget: true, sourceBindings: true, outboxBindings: true, purchaseBindings: true, oldEpoch: OTHER, newEpoch: EPOCH, oldCapabilitiesDenied: true, rollbackRevision: '2'.repeat(40), compatibleMigrations: true, retainedFinancialRecords: true}, merchant: {provider: 'RAZORPAY', mode: 'LIVE', traceSha256: traces.merchant.sha256, settledPaymentVerified: true, refundReconciled: true, signedWebhookVerified: true}, human: {owner: 'qualification-owner', traceSha256: traces.human.sha256, reviewedAt: ISO(NOW), keyboard: true, screenReader: true, zoomPercent: 200, widths: [320, 390, 768, 1024, 1440], taskSessions: 5, blockingFindings: 0, ownersAcknowledged: true}};
}

function fixture(t) {
  const directory = mkdtempSync(resolve(tmpdir(), 'production-qualification-test-')); t.after(() => rmSync(directory, {recursive: true, force: true}));
  const pair = generateKeyPairSync('ed25519'), privateKey = pair.privateKey.export({format: 'pem', type: 'pkcs8'}), publicKey = pair.publicKey.export({format: 'pem', type: 'spki'}), target = policy();
  const put = (file, value) => {const bytes = typeof value === 'string' ? value : canonical(value); writeFileSync(resolve(directory, file), bytes); return {file, sha256: hash(bytes)};};
  const signed = (file, value) => put(file, envelope(value, privateKey)); const legacy = (file, value) => {const bytes = Buffer.from(canonical(value)); return put(file, {payload: bytes.toString('base64'), signature: sign(null, bytes, pair.privateKey).toString('base64')});};
  const targetRef = put('target.json', target); put('public.pem', publicKey);
  const records = ['isolation', 'network', 'cleanup', 'restore', 'licence', 'cost', 'build'].map(control => {const artifact = put(control + '.trace', 'Synthetic validator control ' + control); return {control, result: 'PASS', artifactSha256: artifact.sha256, recordedAt: ISO(NOW - 1000), evidencePath: artifact.file};});
  const inventory = put('inventory.json', {version: 1, mode: 'PRODUCTION', operatorEpoch: EPOCH, provider: target.provider, records});
  const admission = legacy('admission.json', {provider: target.provider, isolation: 'KVM', licenceReviewed: true, budgetApproved: true, networkVerified: true, cleanupVerified: true, restoreVerified: true, approvedUntil: ISO(NOW + 86400000), evidenceSha256: inventory.sha256, operatorEpoch: EPOCH});
  const traces = Object.fromEntries(['isolation', 'query-components', 'query-college-projects', 'recovery', 'merchant', 'human'].map(name => [name, put(name + '-operator.trace', 'Synthetic restricted observation ' + name)]));
  for (const language of Object.keys(sourceDigests())) for (const type of ['licence', 'reproduction']) {const record = records.find(row => row.control === (type === 'licence' ? 'licence' : 'build')); traces[type + '-' + language] = {file: record.evidencePath, sha256: record.artifactSha256};}
  const pre = preflight(target), start = startup(target, traces['licence-java'].sha256, traces['reproduction-java'].sha256), ops = operations(target, traces);
  const manifestDocuments = target.adapters.map(adapter => ({id: 'native-' + adapter.language, componentId: randomUUID(), revision: 1, title: 'Synthetic native ' + adapter.language, summary: 'Disposable complete native manifest for production verifier unit tests.', setup: 'Unit fixture only; this manifest does not certify a real provider or runtime image.', language: adapter.language === 'dotnet' ? 'C# / .NET' : adapter.language, framework: adapter.framework, resourceClass: 'GENERAL', memoryMiB: 128, maxCostMicros: 1000, protocolVersion: 'getlancer-lab-v1', sourceSha256: adapter.sourceSha256, archiveSha256: hash('archive-' + adapter.language), buildSourceSha256: hash('archive-' + adapter.language), manifestSha256: hash('manifest-' + adapter.language), imageDigest: adapter.imageDigest, evidenceSha256: inventory.sha256, approvedUntil: ISO(NOW + 86400000), adapter: {runtime: adapter.runtime, version: adapter.version, state: 'ACTIVE'}, certification: {frameworkVersion: adapter.version, maintainer: 'qualification-owner', reviewedAt: ISO(NOW), licenceReviewed: true, reproductionVerified: true, resourcesVerified: true, isolationVerified: true, reproductionSha256: traces['reproduction-' + adapter.language].sha256}, scenarios: [
    {id: 'cache', title: 'Native cache fixture', description: 'Exercise a bounded isolated cache read and reset operation.', inputs: [], operations: [{id: 'read', label: 'Read cache fixture'}, {id: 'reset', label: 'Reset cache fixture'}]},
    {id: 'security', title: 'Native authorization fixture', description: 'Exercise an explicitly selected synthetic authorization identity.', inputs: [{name: 'identity', label: 'Synthetic identity', type: 'ENUM', required: true, maxLength: 20, min: 0, max: 0, choices: ['editor', 'viewer', 'other-tenant', 'expired', 'tampered']}], operations: [{id: 'authorize', label: 'Authorize fixture identity'}]},
  ]}));
  const manifests = manifestDocuments.map((document, index) => ({language: target.adapters[index].language, ...legacy('manifest-' + target.adapters[index].language + '.json', document)}));
  const diskStart = {...start, adapters: start.adapters.map(({samples, ...metadata}) => ({...metadata, samplesFile: put('samples-' + metadata.language + '.jsonl', samples.map(canonical).join('\n') + '\n')}))};
  function measurement(kind, catalogue) {
    const begin = kind === 'SOAK' ? NOW - 7 * 86400000 : NOW - 60000, header = {version: 1, kind, purpose: 'MEASUREMENT', environment: 'production', revision: REVISION, origin: target.appOrigin, startedAt: ISO(begin), segmentId: randomUUID(), serverProfile: {cpuCores: 4, memoryMiB: 8192, postgresMajor: 16, replicas: 1, storage: 'SSD', clientLocation: 'SAME_REGION'}, config: kind === 'SOAK' ? {catalogue, sources: target.sources, cadenceMs: 60000, timeoutMs: 5000} : {catalogue, rate: 20, concurrency: 10, durationMs: 60000, timeoutMs: 5000}};
    const lines = [canonical(header)], writer = {previous: hash(lines[0]), append: line => lines.push(line.trimEnd())}, response = {status: 200, outcome: 'OK', durationMs: 10, bodySha256: hash('synthetic-response'), serverDurationMs: 2, eligibleRows: 10000};
    for (let i = 0, length = kind === 'SOAK' ? 10081 : 1200; i < length; i++) {
      const elapsedMs = kind === 'SOAK' ? i * 60000 : i * 50 + 10, sampledAt = ISO(begin + elapsedMs), value = {sequence: i + 1, sampledAt, elapsedMs};
      if (kind === 'SOAK') {const admitted = Math.floor(i * 100 / 10080), epoch = ISO(begin - 86400000); Object.assign(value, {publicProbe: response, sourceProbes: target.sources.map(source => ({id: source.id, status: 200, outcome: 'OK', durationMs: 10, bodySha256: source.sha256, sizeBytes: source.sizeBytes})), runtimeProbe: {status: 200, outcome: 'OK', durationMs: 10, snapshot: {observedAt: sampledAt, counterEpoch: epoch, operatorEpoch: EPOCH, measuredFrom: epoch, admitted, healthy: admitted, pending: 0, completed: admitted, infraFailures: 0, scenarioFailures: 0, quotaDenials: 0, validationDenials: 0}}});} else Object.assign(value, {scheduledElapsedMs: i * 50, response}); appendObservation(writer, value);
    }
    const journal = put(kind.toLowerCase() + '-' + catalogue + '.jsonl', lines.join('\n') + '\n'); return signed(kind.toLowerCase() + '-' + catalogue + '.json', journalReport([readJournal(resolve(directory, journal.file))]));
  }
  const loadComponents = measurement('LOAD', 'components'), loadProjects = measurement('LOAD', 'college-projects'), soak = measurement('SOAK', 'components');
  function controlReport(kind, controls, reports) {
    return signed(kind.toLowerCase() + '.json', {version: 1, kind, purpose: 'MEASUREMENT', environment: 'production', revision: REVISION, startedAt: ISO(NOW - 1000), endedAt: ISO(NOW), collector: 'getlancer-v52-ops-1', controls: controls.map(control => {const proof = put(control.toLowerCase() + '.json', {version: 1, kind: 'CONTROL_OBSERVATION', purpose: 'MEASUREMENT', environment: 'production', revision: REVISION, control, observedAt: ISO(NOW), outcome: 'PASS', method: 'OPERATOR', evidenceSha256: hash('synthetic-control-' + control), facts: Object.fromEntries(Object.entries(CONTROL_FACTS[control]).map(([key, value]) => [key, Array.isArray(value) ? value[0] : value]))}); return {control, outcome: 'PASS', owner: 'qualification-owner', observedAt: ISO(NOW), ...proof};}), ...(reports ? {reports} : {})});
  }
  const drill = controlReport('DRILL', DRILL_CONTROLS), activation = controlReport('ACTIVATION', ACTIVATION_CONTROLS), handover = controlReport('HANDOVER', HANDOVER_CONTROLS, [{kind: 'LOAD', ...loadComponents}, {kind: 'SOAK', ...soak}, {kind: 'DRILL', ...drill}, {kind: 'ACTIVATION', ...activation}]);
  const bundle = {...common(target, 'PRODUCTION_QUALIFICATION'), targetSha256: targetRef.sha256, files: {admission, inventory, preflight: put('preflight.json', pre), startup: put('startup.json', diskStart), loadComponents, loadProjects, soak, handover, operations: put('operations.json', ops)}, manifests, traces};
  const save = value => signed('bundle.json', value); save(bundle); const readReport = file => openReport(readFileSync(resolve(directory, file)), publicKey);
  return {target, pre, start, ops, bundle, manifestDocuments, put, signed, legacy, save, readReport, directory, args: {targetPath: resolve(directory, targetRef.file), bundlePath: resolve(directory, 'bundle.json'), publicKeyPath: resolve(directory, 'public.pem'), revision: REVISION, now: NOW}};
}

test('production policy requires all six current runtime sources and both immutable source services', () => {
  const target = policy(); assert.equal(targetPolicy(target), target);
  for (const change of [row => {row.environment = 'staging';}, row => {row.appOrigin = 'http://getlancer-qualification.dev';}, row => {row.appOrigin = 'https://localhost';}, row => {row.adapters.pop();}, row => {row.adapters[0].sourceSha256 = hash('different');}, row => {row.operatorEpoch = 'unknown';}, row => {row.gatewayOrigin = row.appOrigin;}, row => {row.sources.pop();}]) {const bad = clone(target); change(bad); assert.throws(() => targetPolicy(bad));}
});

test('host, six fresh hosted startup cohorts, recovery, live merchant and human gates fail closed', () => {
  const target = policy(), pre = preflight(target), start = startup(target), ops = operations(target); assert(reviewPreflight(pre, target, NOW)); assert.equal(reviewStartup(start, target, NOW).adapters.reduce((sum, row) => sum + row.freshRuns, 0), 600); assert(reviewOperations(ops, target, NOW));
  for (const change of [row => {row.checks.kvmReadWrite = false;}, row => {row.tls[0].authorized = false;}, row => {row.tls[0].httpsOnly = false;}, row => {row.tls[0].validUntil = ISO(NOW);}, row => {row.purpose = 'TEST';}, row => {row.observedAt = ISO(NOW + 1);}]) {const bad = clone(pre); change(bad); assert.throws(() => reviewPreflight(bad, target, NOW));}
  for (const change of [row => {row.adapters[0].samples.pop();}, row => {row.adapters[1].samples[0].runId = row.adapters[0].samples[0].runId;}, row => {row.adapters[0].samples[0].kind = 'REPLAY';}, row => {row.adapters[0].imageDigest = 'sha256:' + hash('different');}, row => {row.adapters[0].samples[0].cleanedAtMs = row.adapters[0].samples[0].admittedAtMs + 300001;}, row => {row.adapters[0].samples.forEach(sample => {sample.readyAtMs = sample.admittedAtMs + 31000; sample.cleanedAtMs = sample.readyAtMs + 1;});}, row => {row.adapters.forEach(adapter => adapter.samples.forEach(sample => {sample.queuedAtMs = NOW - 20000; sample.admittedAtMs = NOW - 19999; sample.readyAtMs = sample.admittedAtMs + (sample.kind === 'WARM' ? 1000 : 10000); sample.cleanedAtMs = NOW - 9998;}));}]) {const bad = clone(start); change(bad); assert.throws(() => reviewStartup(bad, target, NOW));}
  for (const change of [row => {row.merchant.mode = 'TEST';}, row => {row.merchant.refundReconciled = false;}, row => {row.recovery.backupAt = ISO(NOW - 2000000);}, row => {row.recovery.newEpoch = OTHER;}, row => {row.recovery.purchaseBindings = false;}, row => {row.queryPlans[0].eligibleRows = 9999;}, row => {row.human.zoomPercent = 100;}, row => {row.human.screenReader = false;}, row => {row.human.widths.pop();}, row => {row.human.taskSessions = 4;}, row => {row.infrastructure.crashCleanupObserved = false;}]) {const bad = clone(ops); change(bad); assert.throws(() => reviewOperations(bad, target, NOW));}
});

test('signed production composition binds target, native certifications, traces and actual collector lanes', t => {
  const f = fixture(t), result = verifyProduction(f.args); assert.equal(result.result, 'PRODUCTION_QUALIFICATION_VERIFIED'); assert.equal(result.changesActivationFlags, false); assert(Object.values(result.gates).every(Boolean)); assert.equal(result.startup.adapters.reduce((sum, row) => sum + row.freshRuns, 0), 600);
  for (const change of [row => {row.purpose = 'TEST';}, row => {row.environment = 'staging';}, row => {row.operatorEpoch = OTHER;}, row => {row.observedAt = ISO(NOW - 86400001);}, row => {row.targetSha256 = hash('different');}, row => {row.files.startup.sha256 = hash('different');}, row => {row.files.loadProjects = row.files.loadComponents;}, row => {row.manifests.pop();}, row => {row.traces.merchant.sha256 = hash('different');}, row => {row.files.operations.file = '../outside.json';}]) {const bad = clone(f.bundle); change(bad); f.save(bad); assert.throws(() => verifyProduction(f.args));} f.save(f.bundle);
  const different = generateKeyPairSync('ed25519').publicKey.export({type: 'spki', format: 'pem'}); f.put('other.pem', different); assert.throws(() => verifyProduction({...f.args, publicKeyPath: resolve(f.directory, 'other.pem')})); symlinkSync(f.args.targetPath, resolve(f.directory, 'linked.json')); assert.throws(() => verifyProduction({...f.args, targetPath: resolve(f.directory, 'linked.json')}));
  for (const change of [row => {row.resourceClass = 'STANDARD';}, row => {delete row.title;}, row => {row.memoryMiB = 63;}, row => {row.maxCostMicros = 0;}, row => {row.scenarios = [];}, row => {row.scenarios[0].id = 'unreviewed';}, row => {row.scenarios[0].operations = ['read'];}, row => {row.scenarios[1].inputs[0].type = 'UNREVIEWED';}]) {const document = clone(f.manifestDocuments[0]); change(document); const bad = clone(f.bundle), pin = f.legacy(bad.manifests[0].file, document); Object.assign(bad.manifests[0], pin); f.save(bad); assert.throws(() => verifyProduction(f.args), 'The full backend-native manifest contract must reject signed invalid fields');}
  f.legacy(f.bundle.manifests[0].file, f.manifestDocuments[0]); f.save(f.bundle);
  const badOperations = clone(f.ops); badOperations.merchant.mode = 'TEST'; const badBundle = clone(f.bundle); badBundle.files.operations = f.put('operations.json', badOperations); f.save(badBundle); assert.throws(() => verifyProduction(f.args), /LIVE/);
});

test('actual report time, final handover cohort and externally pinned source cohort cannot be substituted', t => {
  const f = fixture(t), originalSoak = f.readReport(f.bundle.files.soak.file), handover = f.readReport(f.bundle.files.handover.file);
  const short = clone(originalSoak); short.startedAt = ISO(NOW - 1000); const shortBundle = clone(f.bundle); shortBundle.files.soak = f.signed('short-soak.json', short); f.save(shortBundle); assert.throws(() => verifyProduction(f.args));
  const changedHandover = clone(handover); changedHandover.reports.find(row => row.kind === 'LOAD').file = f.bundle.files.loadProjects.file; changedHandover.reports.find(row => row.kind === 'LOAD').sha256 = f.bundle.files.loadProjects.sha256; const alternate = clone(f.bundle); alternate.files.handover = f.signed('alternate-handover.json', changedHandover); f.save(alternate); assert.throws(() => verifyProduction(f.args));
  const target = clone(f.target); target.sources[1].sha256 = hash('other-actual-archive'); const policyRef = f.put('different-target.json', target), pre = clone(f.pre); pre.targetSha256 = policyRef.sha256; const different = clone(f.bundle); different.targetSha256 = policyRef.sha256; different.files.preflight = f.put('different-preflight.json', pre); f.save(different); assert.throws(() => verifyProduction({...f.args, targetPath: resolve(f.directory, policyRef.file)}), /Soak source pins/);
});

test('CLI reports actionable unmet status without leaking configuration or activating anything', () => {
  const result = spawnSync(process.execPath, ['scripts/qualify-production.mjs', 'verify'], {encoding: 'utf8', timeout: 5000}); assert.notEqual(result.status, 0); assert.match(result.stderr, /qualification is UNMET/); assert.equal(result.stdout, ''); assert.doesNotMatch(result.stderr, /password=|token=|cookie=|secret=/i);
});

test('TLS discovery rejects unsafe origins before creating transports', async () => {
  for (const origin of ['http://localhost', 'https://127.0.0.1', 'https://user:pass@localhost', 'https://example.net']) await assert.rejects(observeTls(origin));
});

test('DNS deadline cancels a nonresponding resolver without HTTP or TLS transport', {timeout: 10000}, async t => {
  const queried = [], cancelled = [];
  t.mock.method(Resolver.prototype, 'resolve4', hostname => {queried.push(hostname); return new Promise(() => {});});
  t.mock.method(Resolver.prototype, 'resolve6', hostname => {queried.push(hostname); return new Promise(() => {});});
  t.mock.method(Resolver.prototype, 'cancel', function () {cancelled.push(true);});
  const result = await new Promise(resolveResult => boundedLookup('dns-deadline.invalid', {all: true}, (error, addresses) => resolveResult({error, addresses})));
  assert.equal(result.error.code, 'ETIMEDOUT');
  assert.equal(result.addresses, undefined);
  assert.deepEqual(queried, ['dns-deadline.invalid', 'dns-deadline.invalid']);
  assert.equal(cancelled.length, 1);
});
