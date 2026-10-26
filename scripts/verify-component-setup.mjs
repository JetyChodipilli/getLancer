import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {execFileSync, spawn} from 'node:child_process';
import {mkdtemp, readFile, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join, resolve} from 'node:path';
import {createServer} from 'node:net';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {componentZip} from '../lib/components.ts';

const root = fileURLToPath(new URL('../', import.meta.url));
export const setupBaseline = {browser: '145.0.7632.6', python: '3.12'};
export const setupBaselineLine = `Verification baseline: Chromium ${setupBaseline.browser}; Python ${setupBaseline.python} (optional loopback HTTP server).`;

export function validateSetupCatalog(seeds) {
  assert.equal(seeds.length, 24);
  assert.equal(new Set(seeds.map(item => item.slug)).size, seeds.length);
  for (const item of seeds) {
    assert.equal(item.version, '1.0.1', `${item.slug}: use a new immutable release version`);
    assert.equal(item.sha256, createHash('sha256').update(item.files['index.html']).digest('hex'));
    assert.deepEqual(Object.keys(item.files).sort(), ['LICENSE', 'README.md', 'index.html']);
    assert(item.files['README.md'].includes(setupBaselineLine), `${item.slug}: missing exact verified runtime baseline`);
    assert(item.files['README.md'].includes('python3 -m http.server 8000 --bind 127.0.0.1'));
    assert(item.files['README.md'].includes('http://127.0.0.1:8000/index.html'));
    assert(item.files['README.md'].includes(item.scenario));
    assert.match(item.files['README.md'], /Version: 1\.0\.1/);
    assert.match(item.files['README.md'], /Ctrl\+C/);
  }
  return seeds;
}

async function freePort() {
  const socket = createServer();
  await new Promise((resolve, reject) => { socket.once('error', reject); socket.listen(0, '127.0.0.1', resolve); });
  const port = socket.address().port;
  await new Promise((resolve, reject) => socket.close(error => error ? reject(error) : resolve()));
  return port;
}

async function waitForServer(url, child) {
  for (let attempt = 0; attempt < 100; attempt++) {
    if (child.exitCode !== null) throw Error('The documented Python HTTP server exited before serving the extracted source.');
    try { const response = await fetch(url, {signal: AbortSignal.timeout(200)}); if (response.ok) return; } catch {}
    await new Promise(resolve => setTimeout(resolve, 30));
  }
  throw Error('The documented Python HTTP server did not become ready.');
}

async function inspect(browser, item, url) {
  const context = await browser.newContext({viewport: {width: 1280, height: 900}});
  const page = await context.newPage(), failures = [], origin = new URL(url).origin;
  page.on('pageerror', error => failures.push(error.message));
  await context.route('**/*', route => {
    const requested = new URL(route.request().url());
    if (requested.href === url || (origin !== 'null' && requested.origin === origin)) return route.continue();
    failures.push(`Unexpected network dependency: ${requested.protocol}//${requested.host}`);
    return route.abort();
  });
  try {
    await page.goto(url, {waitUntil: 'load'});
    assert.equal(await page.title(), item.title, `${item.slug}: extracted document title`);
    assert(await page.locator('button,a,input,select,textarea,summary').count() > 0, `${item.slug}: no working controls`);
    assert(await page.locator('body').innerText(), `${item.slug}: empty document`);
    assert.deepEqual(failures, [], `${item.slug}: setup browser errors`);
  } finally { await context.close(); }
}

/** Independently decode the actual download ZIP, then follow both documented startup paths. */
export async function verifyComponentSetup({browser, reportPath} = {}) {
  assert(browser, 'A real Playwright browser is required.');
  const seeds = validateSetupCatalog(JSON.parse(await readFile(join(root, 'backend/src/main/resources/catalog/components.json'), 'utf8')));
  const pythonVersion = execFileSync('python3', ['--version'], {encoding: 'utf8'}).trim().replace(/^Python /, '');
  assert.equal(browser.version(), setupBaseline.browser, 'The browser differs from the package verified baseline. Record and review the new baseline before changing this assertion.');
  assert(pythonVersion.startsWith(setupBaseline.python + '.'), 'The Python runtime differs from the package verification family.');
  const temporary = await mkdtemp(join(tmpdir(), 'getlancer-component-setup-')), records = [];
  try {
    for (const item of seeds) {
      const directory = join(temporary, item.slug), archive = componentZip(item.files);
      execFileSync('python3', ['-c', 'import sys,io,zipfile,pathlib; z=zipfile.ZipFile(io.BytesIO(sys.stdin.buffer.read())); assert sorted(z.namelist()) == ["LICENSE","README.md","index.html"]; assert z.testzip() is None; p=pathlib.Path(sys.argv[1]); p.mkdir(); z.extractall(p)', directory], {input: archive});
      for (const [name, contents] of Object.entries(item.files)) assert.equal(await readFile(join(directory, name), 'utf8'), contents);
      await inspect(browser, item, pathToFileURL(join(directory, 'index.html')).href);
      const port = await freePort(), url = `http://127.0.0.1:${port}/index.html`;
      const server = spawn('python3', ['-m', 'http.server', String(port), '--bind', '127.0.0.1'], {cwd: directory, stdio: 'ignore'});
      let serverError; server.once('error', error => { serverError = error; });
      try {
        await waitForServer(url, server); if (serverError) throw serverError;
        assert.equal(await (await fetch(url)).text(), item.files['index.html']);
        await inspect(browser, item, url);
      } finally {
        if (server.exitCode === null) { const closed = new Promise(resolve => server.once('close', resolve)); server.kill('SIGTERM'); await closed; }
      }
      records.push({slug: item.slug, version: item.version, htmlSha256: item.sha256,
        archiveSha256: createHash('sha256').update(archive).digest('hex'), fileStartup: 'passed', pythonHttpStartup: 'passed'});
    }
  } finally { await rm(temporary, {recursive: true, force: true}); }
  const report = {schema: 1, browser: browser.version(), python: pythonVersion, packages: records,
    scope: 'Clean ZIP extraction, README startup by file URL and loopback Python HTTP, no browser errors or external dependencies. Interaction-state and human accessibility acceptance are separate.'};
  if (reportPath) await writeFile(reportPath, JSON.stringify(report, null, 2) + '\n');
  console.log(`COMPONENT_SETUP_VERIFIED ${records.length} packages; Chromium ${report.browser}; Python ${report.python}`);
  return report;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const {chromium} = await import('@playwright/test');
  const executablePath = process.env.COMPONENT_SETUP_BROWSER_EXECUTABLE;
  const browser = await chromium.launch({headless: true, ...(executablePath ? {executablePath} : {})});
  try { await verifyComponentSetup({browser, reportPath: process.env.COMPONENT_SETUP_REPORT}); }
  finally { await browser.close(); }
}
