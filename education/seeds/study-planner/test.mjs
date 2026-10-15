import assert from 'node:assert/strict';
import { once } from 'node:events';
import { mkdtemp, readFile, rm, writeFile, symlink } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { createPlannerServer } from './server.mjs';
const directory = await mkdtemp(join(tmpdir(), 'study-planner-')), file = join(directory, 'tasks.json');
let server;
async function start() { server = await createPlannerServer(file); server.listen(0, '127.0.0.1'); await once(server, 'listening'); return `http://127.0.0.1:${server.address().port}`; }
async function stop() { await new Promise(resolve => server.close(resolve)); }
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
  await stop(); const stored = JSON.parse(await readFile(file, 'utf8')); assert.equal(stored.length, 2);
  await writeFile(file, '[{"id":1}]'); await assert.rejects(createPlannerServer(file), /Invalid stored tasks/);
  await writeFile(file, ' '.repeat(262145)); await assert.rejects(createPlannerServer(file), /256 KiB/);
  await writeFile(file, JSON.stringify(Array.from({ length: 100 }, (_, index) => ({ id: index + 1, title: 'Synthetic', minutes: 1, done: false }))));
  base = await start(); assert.equal((await send('/api/tasks', { title: 'Over limit', minutes: 1 })).status, 409); await stop();
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
