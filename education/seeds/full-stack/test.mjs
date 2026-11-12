import assert from 'node:assert/strict';
import {once} from 'node:events';
import {copyFile, mkdtemp, rm} from 'node:fs/promises';
import {spawn, spawnSync} from 'node:child_process';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {request} from 'node:http';
import {createTaskServer} from './server.mjs';

// Feed the real request handler exact chunks: TCP is allowed to merge separate client writes.
function streamedPost(server, chunks) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(Error('Streamed request timed out')), 2000);
    server.emit('request', {method: 'POST', url: '/tasks', async *[Symbol.asyncIterator]() { for (const chunk of chunks) yield chunk; }}, {
      writeHead(status) { this.status = status; },
      end(body) { clearTimeout(timer); try { resolve({status: this.status, value: JSON.parse(body)}); } catch (error) { reject(error); } },
    });
  });
}

function wirePost(url, chunks) {
  return new Promise((resolve, reject) => {
    const req = request(url, {method: 'POST', timeout: 2500}, response => {
      let body = ''; response.setEncoding('utf8'); response.on('data', chunk => { body += chunk; }); response.on('error', reject);
      response.on('end', () => { try { resolve({status: response.statusCode, value: JSON.parse(body)}); } catch (error) { reject(error); } });
    });
    req.on('error', reject); req.on('timeout', () => req.destroy(Error('HTTP request timed out')));
    let index = 0;
    const write = () => { if (index === chunks.length) return req.end(); req.write(chunks[index++], error => { if (error) reject(error); else setImmediate(write); }); };
    write();
  });
}

const server = createTaskServer(); server.listen(0, '127.0.0.1'); await once(server, 'listening');
try {
  const base = 'http://127.0.0.1:' + server.address().port;
  assert.deepEqual(await (await fetch(base + '/tasks')).json(), {items: []});
  const created = await fetch(base + '/tasks', {method: 'POST', body: JSON.stringify({title: ' Original task '})});
  assert.equal(created.status, 201); assert.equal((await created.json()).title, 'Original task');
  assert.equal((await (await fetch(base + '/tasks/1', {method: 'PATCH'})).json()).completed, true);
  assert.equal((await fetch(base + '/tasks', {method: 'POST', body: '{bad'})).status, 400);
  assert.equal((await fetch(base + '/tasks/99', {method: 'PATCH'})).status, 404);

  const title = 'Café 🧪', body = Buffer.from(JSON.stringify({title}));
  for (const character of ['é', '🧪']) {
    const encoded = Buffer.from(character), start = body.indexOf(encoded);
    for (let split = 1; split < encoded.length; split++) {
      const chunks = [body.subarray(0, start + split), body.subarray(start + split)];
      const response = await streamedPost(server, chunks);
      assert.equal(response.status, 201); assert.equal(response.value.title, title);
      const transported = await wirePost(base + '/tasks', chunks);
      assert.equal(transported.status, 201); assert.equal(transported.value.title, title);
    }
  }
  const malformed = Buffer.concat([Buffer.from('{"title":"'), Buffer.from([0xc3, 0x28]), Buffer.from('"}')]);
  assert.equal((await streamedPost(server, [malformed])).status, 400);
  const padded = bytes => Buffer.concat([body, Buffer.alloc(bytes - body.length, 0x20)]);
  assert.equal((await streamedPost(server, [padded(4096)])).status, 201);
  assert.equal((await streamedPost(server, [padded(4097)])).status, 413);
  assert.equal((await fetch(base + '/tasks', {method: 'POST', body: padded(4097)})).status, 413);
  const tasks = (await (await fetch(base + '/tasks')).json()).items;
  assert.equal(tasks.length, 10); assert.ok(tasks.slice(1).every(task => task.title === title));
} finally { await new Promise(resolve => server.close(resolve)); }

// Exercise the documented entry point from an extracted directory with escaped URL characters.
const directory = await mkdtemp(join(tmpdir(), 'task API é%-'));
let child;
try {
  const entry = join(directory, 'server.mjs'); await copyFile(new URL('./server.mjs', import.meta.url), entry);
  for (const port of ['', '-1', '65536', '1.5', 'not-a-port']) {
    const rejected = spawnSync(process.execPath, [entry], {cwd: directory, env: {...process.env, PORT: port}, encoding: 'utf8', timeout: 2500, maxBuffer: 4096});
    assert.equal(rejected.error, undefined); assert.notEqual(rejected.status, 0); assert.match(rejected.stderr, /PORT must be an integer from 0 to 65535/);
  }
  child = spawn(process.execPath, [entry], {cwd: directory, env: {...process.env, PORT: '0'}, stdio: ['ignore', 'pipe', 'pipe']});
  const address = await new Promise((resolve, reject) => {
    let output = '', errors = '';
    const timer = setTimeout(() => { child.kill(); reject(Error('CLI startup timed out')); }, 5000);
    child.stderr.on('data', chunk => { errors += chunk; });
    child.stdout.on('data', chunk => {
      output += chunk;
      const match = /^Local demonstration at (http:\/\/127\.0\.0\.1:(\d+)\/tasks)\r?$/m.exec(output);
      if (match) { clearTimeout(timer); resolve(match[1]); }
    });
    child.once('error', error => { clearTimeout(timer); reject(error); });
    child.once('exit', code => { clearTimeout(timer); reject(Error('CLI exited before startup: ' + code + ' ' + errors)); });
  });
  assert.ok(Number(new URL(address).port) > 0);
  assert.deepEqual(await (await fetch(address)).json(), {items: []});
} finally {
  if (child && child.exitCode === null && child.signalCode === null) { child.kill(); await once(child, 'exit'); }
  await rm(directory, {recursive: true, force: true});
}
console.log('Task API regression passed.');
