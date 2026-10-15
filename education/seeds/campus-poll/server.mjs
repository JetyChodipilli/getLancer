import { createServer } from 'node:http';
import { readFile, writeFile, rename, open } from 'node:fs/promises';
import { constants } from 'node:fs';
import { fileURLToPath } from 'node:url';
export const choices = ['Library workshop', 'Garden cleanup', 'Coding circle'];
const valid = vote => vote && typeof vote.ballotId === 'string' && /^[a-zA-Z0-9_-]{8,64}$/.test(vote.ballotId) && Number.isInteger(vote.choice) && vote.choice >= 0 && vote.choice < choices.length && Object.keys(vote).length === 2;
/** Persistent synthetic ballots, bounded to 100. This is not a trustworthy election. */
export async function createPollServer(dataFile) {
  let votes = [];
  try {
    // Inspect and read the same descriptor; path replacement cannot redirect a checked read.
    const file = await open(dataFile, constants.O_RDONLY | constants.O_NOFOLLOW | constants.O_NONBLOCK);
    try {
      const info = await file.stat();
      if (!info.isFile()) throw Error('Stored ballots must be a regular file');
      const bytes = Buffer.alloc(65537); let length = 0;
      while (length < bytes.length) {
        const { bytesRead } = await file.read(bytes, length, bytes.length - length, length);
        if (bytesRead === 0) break; length += bytesRead;
      }
      if (length > 65536) throw Error('Stored ballots exceed 64 KiB');
      votes = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes.subarray(0, length)));
    } finally { await file.close(); }
  } catch (error) { if (error.code !== 'ENOENT') throw error; }
  if (!Array.isArray(votes) || votes.length > 100 || votes.some(vote => !valid(vote)) || new Set(votes.map(vote => vote.ballotId)).size !== votes.length) throw Error('Invalid stored ballots');
  let queue = Promise.resolve();
  const totals = () => ({ choices, counts: choices.map((_, index) => votes.filter(vote => vote.choice === index).length), total: votes.length, synthetic: true });
  return createServer({ requestTimeout: 5000, headersTimeout: 5000, maxHeaderSize: 8192 }, async (request, response) => {
    const send = (status, value) => { response.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' }); response.end(JSON.stringify(value)); };
    const origin = `http://${request.headers.host}`;
    if (!/^127\.0\.0\.1:\d+$/.test(request.headers.host || '') || (request.headers.origin && request.headers.origin !== origin)) return send(403, { error: 'Local origin required' });
    let path;
    try { path = new URL(request.url, origin).pathname; } catch { return send(400, { error: 'Invalid route' }); }
    if (request.method === 'GET' && ['/', '/client.mjs', '/style.css'].includes(path)) {
      const name = path === '/' ? 'index.html' : path.slice(1);
      response.writeHead(200, { 'Content-Type': path === '/' ? 'text/html' : path.endsWith('mjs') ? 'text/javascript' : 'text/css', 'Content-Security-Policy': "default-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'", 'X-Content-Type-Options': 'nosniff' }); response.end(await readFile(new URL(name, import.meta.url))); return;
    }
    if (request.method === 'GET' && path === '/api/poll') return send(200, totals());
    if (request.method !== 'POST' || path !== '/api/ballots') return send(404, { error: 'Route not found' });
    if (request.headers['content-type'] !== 'application/json') return send(415, { error: 'JSON required' });
    try {
      let body = '', bytes = 0;
      for await (const chunk of request) { bytes += chunk.length; if (bytes > 2048) return send(413, { error: 'Body too large' }); body += chunk; }
      const input = JSON.parse(body); if (!valid(input)) return send(400, { error: 'A valid synthetic ballot and choice are required' });
      const mutate = async () => {
        const old = votes.find(vote => vote.ballotId === input.ballotId);
        if (old) return old.choice === input.choice ? send(200, { ...totals(), replay: true }) : send(409, { error: 'A ballot cannot change choice' });
        if (votes.length >= 100) return send(409, { error: 'The 100-ballot exercise limit was reached' });
        const next = [...votes, input]; await writeFile(dataFile + '.tmp', JSON.stringify(next), { mode: 0o600 }); await rename(dataFile + '.tmp', dataFile); votes = next; send(201, { ...totals(), replay: false });
      };
      const operation = queue.then(mutate); queue = operation.catch(() => {}); await operation;
    } catch (error) { send(error instanceof SyntaxError ? 400 : 500, { error: error instanceof SyntaxError ? 'Invalid JSON' : 'Storage unavailable; ballot was not committed' }); }
  });
}
if (process.argv[1] === fileURLToPath(import.meta.url)) { const server = await createPollServer(process.argv[2] || 'poll-data.json'); server.listen(3102, '127.0.0.1', () => console.log('Local synthetic poll: http://127.0.0.1:3102')); }
