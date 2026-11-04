import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';

const workflow = readFileSync('.github/workflows/ci.yml', 'utf8');
const fixtureClasses = [
  'com.getlancer.integration.Round3FinanceIntegrationTest',
  'com.getlancer.integration.Round3MarketplaceIntegrationTest',
  'com.getlancer.integration.Round3OperationsIntegrationTest',
  'com.getlancer.integration.Round3RuntimeStorageIntegrationTest',
  'com.getlancer.config.DatabaseRoleGuardIntegrationTest',
];
const requiredJobs = ['lab-scenarios', 'backend', 'frontend', 'docker-startup', 'secrets-and-sast', 'container-security'];

function job(name) {
  const match = workflow.match(new RegExp(`^  ${name}:\\n[\\s\\S]*?(?=^  [a-z][a-z-]*:|$(?![\\s\\S]))`, 'm'));
  assert(match, `Missing ${name} job`);
  return match[0];
}

function step(block, name) {
  const marker = `      - name: ${name}\n`;
  const start = block.indexOf(marker);
  assert(start >= 0, `Missing ${name} step`);
  const end = block.indexOf('\n      - ', start + marker.length);
  return block.slice(start, end < 0 ? undefined : end);
}

function python(block) {
  const match = block.match(/          python3 - <<'PY'\n([\s\S]*?)          PY(?:\n|$)/);
  assert(match, 'Expected an embedded Python command');
  return match[1].replace(/^ {10}/gm, '');
}

function temporary(t) {
  const root = mkdtempSync(join(tmpdir(), 'getlancer-ci-'));
  t.after(() => rmSync(root, {recursive: true, force: true}));
  return root;
}

function executePython(code, root, env = {}) {
  return spawnSync('python3', ['-c', code], {cwd: root, env: {...process.env, ...env}, encoding: 'utf8'});
}

test('serial performance and parallel browser commands preserve every collected test exactly once', () => {
  const frontend = job('frontend');
  const performance = step(frontend, 'Verify mobile performance budgets without competing browser workers');
  const general = step(frontend, 'Verify desktop, phone and tablet workspace journeys');
  assert.match(performance, /--workers=1\b/);
  assert.doesNotMatch(performance, /--project|--grep|--retries/);
  assert.match(performance, /PLAYWRIGHT_JSON_OUTPUT_FILE: test-results-performance\/results.json/);
  assert(frontend.includes('name: performance-browser-evidence-${{ github.sha }}'));
  assert.match(general, /--workers=2\b/);
  function collect(block) {
    const command = block.match(/run: npx playwright test([^\n]*)/);
    assert(command, 'Expected the actual workflow browser command');
    const args = command[1].trim().split(/\s+/).filter(Boolean);
    const result = spawnSync(process.execPath, ['node_modules/@playwright/test/cli.js', 'test', ...args, '--list', '--reporter=json'], {encoding: 'utf8', timeout: 60000, maxBuffer: 8 * 1024 * 1024});
    assert.equal(result.status, 0, result.stderr);
    const records = [];
    function visit(suites) {
      for (const suite of suites) {
        for (const spec of suite.specs ?? []) for (const item of spec.tests) records.push({key: `${item.projectName}:${spec.file}:${spec.line}:${spec.title}`, file: spec.file, project: item.projectName});
        visit(suite.suites ?? []);
      }
    }
    visit(JSON.parse(result.stdout).suites);
    assert(records.length > 0, 'Collection must not be empty');
    return records;
  }
  const all = collect('run: npx playwright test');
  const isolated = collect(performance), parallel = collect(general);
  assert(isolated.every(item => item.file.endsWith('v46-performance.spec.ts')));
  assert.deepEqual(isolated.map(item => item.project).sort(), ['desktop', 'phone', 'tablet']);
  assert(parallel.every(item => !item.file.endsWith('v46-performance.spec.ts')));
  const combined = [...isolated, ...parallel].map(item => item.key).sort();
  assert.equal(new Set(combined).size, combined.length, 'No duplicate execution across the partition');
  assert.deepEqual(combined, all.map(item => item.key).sort(), 'No browser test may disappear from CI');
});

function report(name, body = `<testcase classname="${name}" name="passes"/>`) {
  return `<testsuite name="${name}">${body}</testsuite>`;
}

function fixtureReports(root) {
  const reports = join(root, 'backend/target/surefire-reports');
  mkdirSync(reports, {recursive: true});
  for (const name of fixtureClasses) writeFileSync(join(reports, `TEST-${name}.xml`), report(name));
  return reports;
}

const backend = job('backend');
const fixtureStep = step(backend, 'Verify expected backend fixtures in complete-suite reports');
const validator = python(fixtureStep);
const status = job('validation-status');
const summary = python(step(status, 'Require every validation job to succeed'));

test('backend runs the full serial suite once within a bounded job budget', () => {
  assert.match(backend, /timeout-minutes: 30\n/);
  const commands = backend.match(/mvn [^\n]+/g);
  const tests = commands.filter(command => /\b(?:test|verify)$/.test(command));
  assert.deepEqual(tests, ['mvn -B -f backend/pom.xml -Djunit.jupiter.execution.timeout.default=60s -DfailIfNoTests=true verify']);
  assert.doesNotMatch(backend, /-D(?:test=|skipTests|maven\.test\.skip|junit\.jupiter\.execution\.parallel|forkCount)/);
  assert.match(backend, /TEST_DATABASE_RESET: 'true'/);
  assert.match(backend, /getlancer_test\?socketTimeout=20&options=-c%20statement_timeout%3D15000/);
  assert.match(backend, /DB_POOL_MAX: '4'/);
  const clear = backend.indexOf('rm -rf backend/target/surefire-reports backend/target/round3-smoke-reports');
  assert(clear >= 0 && clear < backend.indexOf(tests[0]), 'Reports must be fresh before the complete suite');
  assert(backend.indexOf(tests[0]) < backend.indexOf('Verify expected backend fixtures in complete-suite reports'));
  assert.match(fixtureStep, /if: always\(\)/);
});

test('fixture evidence is a byte-identical subset of the current complete suite', t => {
  const root = temporary(t);
  const reports = fixtureReports(root);
  writeFileSync(join(reports, 'TEST-unrelated.xml'), report('unrelated'));
  const result = executePython(validator, root);
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /nonzero passing cases in the complete suite/);
  const smoke = join(root, 'backend/target/round3-smoke-reports');
  assert.deepEqual(readdirSync(smoke).sort(), fixtureClasses.map(name => `TEST-${name}.xml`).sort());
  for (const file of readdirSync(smoke)) assert.deepEqual(readFileSync(join(smoke, file)), readFileSync(join(reports, file)));
});

test('fixture verification rejects every missing required class', t => {
  const root = temporary(t);
  for (const name of fixtureClasses) {
    const reports = fixtureReports(root);
    rmSync(join(reports, `TEST-${name}.xml`));
    const result = executePython(validator, root);
    assert.notEqual(result.status, 0, `${name} cannot be absent`);
  }
});

test('fixture verification rejects empty, failed, errored, skipped, malformed and mislabelled reports', t => {
  const root = temporary(t);
  const reports = fixtureReports(root);
  const name = fixtureClasses[0];
  const invalid = [
    report(name, ''),
    ...['failure', 'error', 'skipped'].map(tag => report(name, `<testcase classname="${name}" name="fails"><${tag}/></testcase>`)),
    '<testsuite',
    report('another.Class'),
    report(name, '<testcase classname="another.Class" name="wrong-class"/>'),
  ];
  for (const xml of invalid) {
    writeFileSync(join(reports, `TEST-${name}.xml`), xml);
    const result = executePython(validator, root);
    assert.notEqual(result.status, 0, xml);
    assert.equal(readFileSync(join(root, 'backend/target/round3-smoke-reports', `TEST-${name}.xml`), 'utf8'), xml, 'Preserve failure diagnostics');
  }
});

test('smoke and complete evidence retain SHA names and scan dependencies', () => {
  const source = '${{ github.event.pull_request.head.sha || github.sha }}';
  assert(backend.includes(`name: backend-round3-smoke-${source}\n          path: backend/target/round3-smoke-reports`));
  assert(backend.includes(`name: backend-verification-${source}\n          path: backend/target/surefire-reports`));
  assert(backend.includes(`SECURITY_SOURCE_SHA: ${source}`));
  assert(backend.includes(`PHASE_SOURCE_SHA: ${source}`));
  assert(backend.indexOf('Verify expected backend fixtures in complete-suite reports') < backend.indexOf('node scripts/sast-dispositions.mjs --write-evidence'));
  for (const name of ['secrets-and-sast', 'container-security']) {
    const block = job(name);
    assert.match(block, /^    needs: backend$/m);
    assert(block.includes(`name: backend-verification-${source}`));
    assert.doesNotMatch(block.split('    steps:')[0], /if: always\(\)/);
  }
});

test('final status always observes every required job, including the security matrix', () => {
  const allJobs = [...workflow.matchAll(/^  ([a-z][a-z-]*):$/gm)].map(match => match[1]).filter(name => name !== 'validation-status');
  assert.deepEqual(allJobs, requiredJobs, 'New jobs must be added to the final gate');
  assert(status.includes(`needs: [${requiredJobs.join(', ')}]`));
  assert.match(status, /^    if: always\(\)$/m);
  assert.match(status, /timeout-minutes: 2\n/);
  assert(status.includes('CI_JOB_RESULTS: ${{ toJSON(needs) }}'));
});

test('final status passes only all-success results and writes a readable summary', t => {
  const root = temporary(t);
  const path = join(root, 'summary.md');
  const jobs = Object.fromEntries(requiredJobs.map(name => [name, {result: 'success'}]));
  const result = executePython(summary, root, {CI_JOB_RESULTS: JSON.stringify(jobs), GITHUB_STEP_SUMMARY: path});
  assert.equal(result.status, 0, result.stderr);
  const markdown = readFileSync(path, 'utf8');
  for (const name of requiredJobs) assert(markdown.includes(`| ${name} | success |`));
});

test('final status fails visibly for any failed, cancelled, skipped or missing required job', t => {
  const root = temporary(t);
  for (const name of requiredJobs) {
    for (const outcome of ['failure', 'cancelled', 'skipped', 'missing', 'missing-result']) {
      const jobs = Object.fromEntries(requiredJobs.map(jobName => [jobName, {result: 'success'}]));
      if (outcome === 'missing') delete jobs[name];
      else jobs[name] = outcome === 'missing-result' ? {} : {result: outcome};
      const path = join(root, 'summary.md');
      writeFileSync(path, '');
      const result = executePython(summary, root, {CI_JOB_RESULTS: JSON.stringify(jobs), GITHUB_STEP_SUMMARY: path});
      assert.notEqual(result.status, 0, `${name}: ${outcome}`);
      assert(result.stderr.includes(`Required validation jobs did not pass: ${name}`), result.stderr);
      assert(readFileSync(path, 'utf8').includes(`| ${name} | ${outcome.startsWith('missing') ? 'missing' : outcome} |`));
    }
  }
});
