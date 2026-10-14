import assert from 'node:assert/strict';
import { once } from 'node:events';
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { join } from 'node:path'; import { tmpdir } from 'node:os';
import { createPollServer } from './server.mjs';
const directory = await mkdtemp(join(tmpdir(), 'campus-poll-')), file = join(directory, 'ballots.json'); let server;
async function start() { server = await createPollServer(file); server.listen(0, '127.0.0.1'); await once(server, 'listening'); return `http://127.0.0.1:${server.address().port}`; }
async function stop() { await new Promise(resolve => server.close(resolve)); }
try {
  let base = await start(); const send = (body, headers = {}) => fetch(base + '/api/ballots', { method: 'POST', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(body) });
  const initial = await (await fetch(base + '/api/poll')).json(); assert.deepEqual(initial.counts, [0, 0, 0]); assert.equal(initial.synthetic, true);
  const vote = { ballotId: 'synthetic-ballot-01', choice: 1 };
  const simultaneous = await Promise.all([send(vote), send(vote)]); assert.deepEqual(simultaneous.map(result => result.status).sort(), [200, 201]);
  assert.equal((await send({ ...vote, choice: 2 })).status, 409); assert.equal((await send({ ballotId: 'synthetic-ballot-02', choice: 0 })).status, 201);
  await stop(); base = await start(); assert.deepEqual((await (await fetch(base + '/api/poll')).json()).counts, [1, 1, 0]); assert.equal((await send(vote)).status, 200);
  for (const bad of [null, [], { ballotId: 'short', choice: 0 }, { ballotId: 'synthetic-03', choice: 3 }, { ballotId: 'synthetic-03', choice: '1' }, { ballotId: 'synthetic-03', choice: 1, email: 'excluded' }]) assert.equal((await send(bad)).status, 400);
  assert.equal((await send(vote, { Origin: 'https://other.invalid' })).status, 403);
  assert.equal((await fetch(base + '/api/ballots', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{bad' })).status, 400);
  assert.equal((await fetch(base + '/api/ballots', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: 'x'.repeat(2049) })).status, 413);
  const page = await fetch(base); assert.match(await page.text(), /fieldset/); assert.match(page.headers.get('content-security-policy'), /frame-ancestors 'none'/);
  assert.equal((await fetch(base + '/ballots.json')).status, 404);
  await stop(); await writeFile(file, JSON.stringify(Array.from({ length: 100 }, (_, index) => ({ ballotId: 'synthetic-' + index, choice: 0 })))); base = await start(); assert.equal((await send({ ballotId: 'synthetic-overflow', choice: 0 })).status, 409);
  await stop(); await writeFile(file, '[{"ballotId":"synthetic-1","choice":9}]'); await assert.rejects(createPollServer(file), /Invalid stored ballots/);
  await writeFile(file, ' '.repeat(65537)); await assert.rejects(createPollServer(file), /64 KiB/);
  server = await createPollServer(join(directory, 'missing', 'ballots.json')); server.listen(0, '127.0.0.1'); await once(server, 'listening'); base = `http://127.0.0.1:${server.address().port}`;
  assert.equal((await send(vote)).status, 500); assert.equal((await (await fetch(base + '/api/poll')).json()).total, 0); await stop();
  console.log('Persistent synthetic poll regression passed.');
} finally { if (server?.listening) await stop(); await rm(directory, { recursive: true, force: true }); }
