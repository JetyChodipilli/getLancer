import assert from 'node:assert/strict';
import { once } from 'node:events';
import { mkdtemp, readFile, rm, writeFile, symlink } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { request } from 'node:http';
import { createPlannerServer } from './server.mjs';
const directory = await mkdtemp(join(tmpdir(), 'study-planner-')), file = join(directory, 'tasks.json');
let server;
async function start() { server = await createPlannerServer(file); server.listen(0, '127.0.0.1'); await once(server, 'listening'); return `http://127.0.0.1:${server.address().port}`; }
async function stop() { await new Promise(resolve => server.close(resolve)); }
// Exact handler chunks make UTF-8 boundary coverage independent of TCP packet coalescing.
function streamedPost(chunks) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(Error('Streamed request timed out')), 2000);
    server.emit('request', { method: 'POST', url: '/api/tasks', headers: { host: '127.0.0.1:' + server.address().port, 'content-type': 'application/json' }, async *[Symbol.asyncIterator]() { for (const chunk of chunks) yield chunk; } }, {
      writeHead(status) { this.status = status; },
      end(body) { clearTimeout(timer); try { resolve({ status: this.status, value: JSON.parse(body) }); } catch (error) { reject(error); } },
    });
  });
}
function wirePost(url, chunks) {
  return new Promise((resolve, reject) => {
    const req = request(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, timeout: 2500 }, response => {
      let body = ''; response.setEncoding('utf8'); response.on('data', chunk => { body += chunk; }); response.on('error', reject);
      response.on('end', () => { try { resolve({ status: response.statusCode, value: JSON.parse(body) }); } catch (error) { reject(error); } });
    });
    req.on('error', reject); req.on('timeout', () => req.destroy(Error('HTTP request timed out')));
    let index = 0;
    const write = () => { if (index === chunks.length) return req.end(); req.write(chunks[index++], error => { if (error) reject(error); else setImmediate(write); }); };
    write();
  });
}
try {
  let base = await start();
  const send = (path, body, method = 'POST', headers = {}) => fetch(base + path, { method, headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(body) });
  const page = await fetch(base); assert.equal(page.status, 200); assert.match(page.headers.get('content-security-policy'), /frame-ancestors 'none'/); assert.match(await page.text(), /<label>/);
  assert.deepEqual(await (await fetch(base + '/api/tasks')).json(), { tasks: [], remainingMinutes: 0 });
  const results = await Promise.all([send('/api/tasks', { title: '<script>synthetic</script>', minutes: 25 }), send('/api/tasks', { title: 'Review notes', minutes: 10 })]); assert.ok(results.every(result => result.status === 201));
  assert.equal((await (await fetch(base + '/api/tasks')).json()).remainingMinutes, 35);
  assert.equal((await send('/api/tasks/1', { done: true }, 'PATCH')).status, 200);
  await stop(); base = await start(); assert.equal((await (await fetch(base + '/api/tasks')).json()).remainingMinutes, 10);
  for (const bad of [{ title: '', minutes: 1 }, { title: 'x', minutes: 0 }, { title: 'x', minutes: 1.5 }, { title: 'x', minutes: 1, private: 'excluded' }, null]) assert.equal((await send('/api/tasks', bad)).status, 400);
  assert.equal((await send('/api/tasks', { title: 'x', minutes: 1 }, 'POST', { Origin: 'https://other.invalid' })).status, 403);
  assert.equal((await send('/api/tasks/999', { done: true }, 'PATCH')).status, 404);
  assert.equal((await send('/api/tasks/1', { done: 'true' }, 'PATCH')).status, 400);
  assert.equal((await fetch(base + '/api/tasks', { method: 'POST', body: '{}' })).status, 415);
  assert.equal((await fetch(base + '/api/tasks', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{bad' })).status, 400);
  assert.equal((await fetch(base + '/api/tasks', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: 'x'.repeat(4097) })).status, 413);
  assert.equal((await fetch(base + '/tasks.json')).status, 404);
  const title = 'Café 🧪', body = Buffer.from(JSON.stringify({ title, minutes: 1 }));
  for (const character of ['é', '🧪']) {
    const encoded = Buffer.from(character), start = body.indexOf(encoded);
    for (let split = 1; split < encoded.length; split++) {
      const chunks = [body.subarray(0, start + split), body.subarray(start + split)];
      const response = await streamedPost(chunks);
      assert.equal(response.status, 201); assert.equal(response.value.title, title);
      const transported = await wirePost(base + '/api/tasks', chunks);
      assert.equal(transported.status, 201); assert.equal(transported.value.title, title);
    }
  }
  const malformed = Buffer.concat([Buffer.from('{"title":"'), Buffer.from([0xc3, 0x28]), Buffer.from('","minutes":1}')]);
  assert.equal((await streamedPost([malformed])).status, 400);
  const padded = bytes => Buffer.concat([body, Buffer.alloc(bytes - body.length, 0x20)]);
  assert.equal((await streamedPost([padded(4096)])).status, 201);
  assert.equal((await streamedPost([padded(4097)])).status, 413);
  await stop(); const stored = JSON.parse(await readFile(file, 'utf8'));
  assert.equal(stored.length, 11); assert.equal(stored.filter(task => task.title === title).length, 9);
  base = await start(); assert.deepEqual((await (await fetch(base + '/api/tasks')).json()).tasks, stored); await stop();
  const existing = { id: 100, title: 'Existing', minutes: 1, done: false };
  await writeFile(file, JSON.stringify([existing])); base = await start();
  const added = await send('/api/tasks', { title: 'Next', minutes: 1 }); assert.equal(added.status, 201);
  const nextTask = await added.json(); assert.equal(nextTask.id, 1);
  await stop(); base = await start();
  assert.deepEqual(await (await fetch(base + '/api/tasks')).json(), { tasks: [existing, nextTask], remainingMinutes: 2 }); await stop();
  await writeFile(file, '[{"id":1}]'); await assert.rejects(createPlannerServer(file), /Invalid stored tasks/);
  await writeFile(file, ' '.repeat(262145)); await assert.rejects(createPlannerServer(file), /256 KiB/);
  await writeFile(file, JSON.stringify(Array.from({ length: 100 }, (_, index) => ({ id: index + 1, title: 'Synthetic', minutes: 1, done: false }))));
  const fullFile = await readFile(file);
  base = await start(); assert.equal((await send('/api/tasks', { title: 'Over limit', minutes: 1 })).status, 409); await stop();
  assert.deepEqual(await readFile(file), fullFile);
  server = await createPlannerServer(join(directory, 'missing', 'tasks.json')); server.listen(0, '127.0.0.1'); await once(server, 'listening'); base = `http://127.0.0.1:${server.address().port}`;
  assert.equal((await send('/api/tasks', { title: 'Must not commit', minutes: 1 })).status, 500);
  assert.equal((await (await fetch(base + '/api/tasks')).json()).tasks.length, 0); await stop();
  const linked = join(directory, 'linked.json');
  if (process.platform !== 'win32') {
  await writeFile(join(directory, 'safe.json'), '[]'); await symlink(join(directory, 'safe.json'), linked);
  await assert.rejects(createPlannerServer(linked), /ELOOP/);
  }
  await assert.rejects(createPlannerServer(directory), /regular file|EISDIR/);
  console.log('Persistent local study planner regression passed.');
} finally { if (server?.listening) await stop(); await rm(directory, { recursive: true, force: true }); }
