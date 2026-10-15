/** Real native .NET HTTP/TCP regressions beyond the shared pinned-Redis matrix. */
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {createHash, createHmac, randomUUID} from 'node:crypto';
import {mkdtemp, readFile, rm, stat} from 'node:fs/promises';
import {request as httpRequest} from 'node:http';
import {createServer, connect} from 'node:net';
import {tmpdir} from 'node:os';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
assert(process.argv.slice(2).every(argument => argument === '--sdk'), 'Only --sdk is supported');
const sdkAcceptance = process.argv.includes('--sdk');
const sourceHash = createHash('sha256').update(await readFile(join(root, 'labs/dotnet/Program.cs'))).digest('hex');
const workspace = await mkdtemp(join(tmpdir(), 'getlancer-dotnet-native-'));
const handles = [];
const delay = milliseconds => new Promise(done => setTimeout(done, milliseconds));
const sign = body => createHmac('sha256', 'getlancer-payment-fixture-only').update(body).digest('hex');
let checks = 0;

async function sdkCommand(arguments_, operation) {
  const child = spawn(process.env.LAB_DOTNET || 'dotnet', arguments_, {cwd: root, env: {
    PATH: process.env.PATH, XDG_RUNTIME_DIR: workspace, XDG_DATA_HOME: join(workspace, 'sdk-data'), NUGET_PACKAGES: join(workspace, 'packages'), DOTNET_CLI_HOME: join(workspace, 'sdk-home'),
    DOTNET_SKIP_FIRST_TIME_EXPERIENCE: '1', DOTNET_CLI_TELEMETRY_OPTOUT: '1', DOTNET_NOLOGO: '1', DOTNET_CLI_USE_MSBUILD_SERVER: '0'
  }, stdio: ['ignore', 'pipe', 'pipe']});
  let diagnostic = '';
  for (const stream of [child.stdout, child.stderr]) stream.on('data', data => { diagnostic = (diagnostic + data).slice(-4000); });
  handles.push({child});
  await new Promise((yes, no) => {
    const timeout = setTimeout(() => { child.kill('SIGKILL'); no(Error(operation + ' exceeded 90 seconds')); }, 90000);
    child.once('error', error => { clearTimeout(timeout); no(error); });
    child.once('exit', code => { clearTimeout(timeout); code === 0 ? yes() : no(Error(operation + ' failed: ' + diagnostic)); });
  });
}

async function buildNative() {
  const output = join(workspace, 'native');
  await sdkCommand([
    'build', 'labs/dotnet/LabServer.csproj', '--configuration', 'Release', '--output', output, '--disable-build-servers', '--nologo'
  ], 'Native build');
  return join(output, 'GetLancer.NativeFixtures.dll');
}

let assembly;

async function port() {
  const server = createServer();
  await new Promise((yes, no) => server.once('error', no).listen(0, '127.0.0.1', yes));
  const result = server.address().port;
  await new Promise(done => server.close(done));
  return result;
}

async function start(lifetime = 600, launch = [assembly]) {
  const runId = randomUUID(), httpPort = await port();
  const started = performance.now();
  const child = spawn(process.env.LAB_DOTNET || 'dotnet', launch, {
    cwd: root,
    env: {
      PATH: process.env.PATH,
      ...(process.env.DOTNET_ROOT ? {DOTNET_ROOT: process.env.DOTNET_ROOT} : {}),
      XDG_RUNTIME_DIR: workspace, XDG_DATA_HOME: join(workspace, 'sdk-data'), NUGET_PACKAGES: join(workspace, 'packages'),
      DOTNET_CLI_HOME: join(workspace, runId),
      DOTNET_SKIP_FIRST_TIME_EXPERIENCE: '1', DOTNET_CLI_TELEMETRY_OPTOUT: '1', DOTNET_NOLOGO: '1', DOTNET_CLI_USE_MSBUILD_SERVER: '0',
      LAB_RUN_ID: runId, LAB_PORT: String(httpPort), LAB_LIFETIME_SECONDS: String(lifetime)
    },
    stdio: ['ignore', 'pipe', 'pipe']
  });
  const app = {child, runId, httpPort, origin: 'http://127.0.0.1:' + httpPort, output: '', error: null, native: launch.length === 1};
  child.on('error', error => { app.error = error; });
  for (const stream of [child.stdout, child.stderr]) stream.on('data', data => { app.output = (app.output + data).slice(-4000); });
  handles.push(app);
  const deadline = Date.now() + 90000;
  while (Date.now() < deadline) {
    if (app.error || child.exitCode !== null) throw Error('Native startup failed: ' + (app.error?.message || app.output));
    try {
      const response = await fetch(app.origin + '/health', {signal: AbortSignal.timeout(500)});
      if (response.ok) {
        assert.deepEqual(await response.json(), {mode: 'Local execution', language: 'dotnet', runId, sourceHash});
        app.startupMs = Math.ceil(performance.now() - started);
        await observeMemory(app);
        checks++;
        return app;
      }
    } catch (error) { if (error instanceof assert.AssertionError) throw error; }
    await delay(50);
  }
  throw Error('Native readiness deadline: ' + app.output);
}

async function observeMemory(app) {
  try {
    const status = await readFile('/proc/' + app.child.pid + '/status', 'utf8');
    const rss = /^VmRSS:\s+(\d+)\s+kB$/m.exec(status);
    if (rss) app.rssKiB = Math.max(app.rssKiB || 0, Number(rss[1]));
  } catch { /* /proc is not available on every supported .NET host; no fabricated metric. */ }
}

async function response(app, body, expected, options = {}) {
  const result = await fetch(app.origin + (options.path || '/request'), {
    method: options.method || 'POST',
    headers: {'Content-Type': options.contentType || 'application/x-www-form-urlencoded'},
    ...(options.method === 'GET' ? {} : {body}), signal: AbortSignal.timeout(3500)
  });
  const text = await result.text();
  assert.equal(result.status, expected, text);
  assert(Buffer.byteLength(text) <= 4096);
  assert.equal(result.headers.get('cache-control'), 'no-store');
  assert.equal(result.headers.get('x-content-type-options'), 'nosniff');
  assert.equal(result.headers.get('access-control-allow-origin'), null);
  const value = JSON.parse(text);
  if (value.error) assert(!Object.hasOwn(value, 'events'));
  else {
    assert.equal(value.runId, app.runId); assert.equal(value.sourceHash, sourceHash);
    for (const event of value.events) {
      assert.match(event.recordedAt, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{1,9}Z$/, 'Native event must use canonical fractional UTC Z');
      assert(Number.isFinite(Date.parse(event.recordedAt)));
    }
  }
  checks++;
  return value;
}

function form(app, pattern, operationId, input = {}) {
  return new URLSearchParams({runId: app.runId, pattern, operationId, ...input}).toString();
}

async function stop(app) {
  if (!app.child.pid || app.child.exitCode !== null || app.child.signalCode !== null) return;
  const exited = new Promise(done => app.child.once('exit', done));
  app.child.kill('SIGTERM');
  await Promise.race([exited, delay(2500)]);
  if (app.child.exitCode === null && app.child.signalCode === null) { app.child.kill('SIGKILL'); await exited; }
}

async function expectLifetime(app) {
  if (app.child.exitCode === null) {
    const stopped = new Promise(done => app.child.once('exit', done));
    await Promise.race([stopped, delay(5000)]);
  }
  assert.equal(app.child.exitCode, 0, 'Native process lifetime did not terminate cleanly'); checks++;
}

try {
  // Build actual native source once, then supervise each assembly directly so no CLI parent can orphan it.
  assembly = await buildNative();
  // Concurrent disposable processes have independent policy, ledger and event state.
  const [app, other] = await Promise.all([start(), start()]);
  const basic = form(app, 'security', 'authorize', {identity: 'editor'});
  await response(app, basic + '&%70attern=security', 400);
  await response(app, basic + '&x=%C0%AF', 400);
  await response(app, Buffer.concat([Buffer.from(basic + '&x='), Buffer.from([0xc0, 0xaf])]), 400);
  await response(app, basic + '&', 400);
  await response(app, basic + '&=empty', 400);
  await response(app, basic, 404, {path: '/re%71uest'});
  await response(app, basic, 415, {contentType: 'application/x-www-form-urlencoded; charset=latin1'});
  await response(app, form(app, 'security', 'authorize', {identity: 'editor\n'}), 400);
  const eventBody = 'evt-1|order-1|payment.succeeded';
  const delivery = form(app, 'payment', 'deliver', {eventBody, signature: sign(eventBody)});
  const retries = await Promise.all(Array.from({length: 16}, () => response(app, delivery, 200)));
  assert(retries.every(value => value.state.processedEvents === 1 && value.state.entitlements === 1));
  assert.equal(retries.flatMap(value => value.events).filter(event => event.type === 'PAYMENT_APPLIED').length, 1);
  assert.equal(new Set(retries.flatMap(value => value.events.map(event => event.sequence))).size, 16);
  await observeMemory(app);
  checks++;
  await response(app, form(app, 'payment', 'deliver', {eventBody: eventBody + '\0', signature: sign(eventBody + '\0')}), 400);
  await response(app, form(app, 'payment', 'deliver', {eventBody: eventBody + '\n', signature: sign(eventBody)}), 401);
  await response(app, form(app, 'payment', 'deliver', {eventBody: 'x'.repeat(257), signature: sign('x'.repeat(257))}), 400);
  await response(app, form(app, 'security', 'revoke'), 200);
  assert.equal((await response(other, form(other, 'security', 'authorize', {identity: 'editor'}), 200)).state.reason, 'ALLOW');
  assert.equal((await response(app, basic, 403)).state.reason, 'REVOKED');
  checks++;
  const healthBody = await new Promise((yes, no) => {
    const request = httpRequest(app.origin + '/health', {method: 'GET', headers: {'Content-Length': '1'}}, result => {
      let text = ''; result.setEncoding('utf8'); result.on('data', part => { text += part; });
      result.once('end', () => yes({status: result.statusCode, text}));
    });
    request.setTimeout(3500, () => request.destroy(Error('Health body deadline'))); request.once('error', no); request.end('x');
  });
  assert.equal(healthBody.status, 400); assert(JSON.parse(healthBody.text).error); checks++;
  const slowStarted = performance.now();
  const slow = await new Promise((yes, no) => {
    const socket = connect({host: '127.0.0.1', port: app.httpPort});
    let bytes = '';
    socket.setTimeout(3500, () => socket.destroy(Error('Native request deadline was not enforced')));
    socket.once('error', no);
    socket.once('connect', () => socket.write('POST /request HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/x-www-form-urlencoded\r\nContent-Length: 100\r\n\r\nx'));
    socket.on('data', part => { bytes += part; });
    socket.once('close', () => yes(bytes));
  });
  assert.match(slow, /^HTTP\/1\.1 408 /); assert(performance.now() - slowStarted < 3300); checks++;
  const shortLived = await start(2);
  await expectLifetime(shortLived);
  if (sdkAcceptance) {
    const published = join(workspace, 'published');
    await sdkCommand([
      'publish', 'labs/dotnet/LabServer.csproj', '--configuration', 'Release', '-p:PublishProfile=Local',
      '--output', published, '--disable-build-servers', '--nologo'
    ], 'Native Release publish');
    await expectLifetime(await start(2, [join(published, 'GetLancer.NativeFixtures.dll')]));
    // Exercise the exact documented source command using a two-second child lifetime.
    const sdkRun = await start(2, ['run', '--project', 'labs/dotnet/LabServer.csproj', '--no-launch-profile']);
    assert((await stat(join(root, 'labs/dotnet/artifacts', sdkRun.runId, 'obj/project.assets.json'))).size > 0);
    assert((await stat(join(root, 'labs/dotnet/artifacts', sdkRun.runId, 'bin/Debug/net8.0/GetLancer.NativeFixtures.dll'))).size > 0);
    checks++;
    await expectLifetime(sdkRun);
  }
  const observed = handles.filter(handle => handle.native && Number.isFinite(handle.startupMs));
  const rss = observed.map(handle => handle.rssKiB).filter(Number.isFinite);
  console.log('DOTNET_NATIVE_REGRESSIONS_OK checks=' + checks + ' sourceHash=' + sourceHash +
    ' nativeStarts=' + observed.length + ' startupMaxMs=' + Math.max(...observed.map(handle => handle.startupMs)) +
    ' observedRssMaxKiB=' + (rss.length ? Math.max(...rss) : 'unavailable') + ' sdkAcceptance=' + sdkAcceptance);
} finally {
  await Promise.all(handles.map(stop));
  await rm(workspace, {recursive: true, force: true});
}
