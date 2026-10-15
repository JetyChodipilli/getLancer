import { createServer } from 'node:http';
import { readFile, writeFile, rename, open } from 'node:fs/promises';
import { constants } from 'node:fs';
import { fileURLToPath } from 'node:url';

function validTask(task) {
  return task && Object.keys(task).length === 4 && Number.isSafeInteger(task.id) && task.id > 0 && task.id <= 100 && typeof task.title === 'string' &&
    task.title.trim().length > 0 && task.title.length <= 100 && Number.isInteger(task.minutes) &&
    task.minutes >= 1 && task.minutes <= 600 && typeof task.done === 'boolean';
}
/** A single-process local exercise. The caller supplies a private data-file path. */
export async function createPlannerServer(dataFile) {
  let tasks = [];
  try {
    // Inspect and read the same descriptor; path replacement cannot redirect a checked read.
    const file = await open(dataFile, constants.O_RDONLY | constants.O_NOFOLLOW | constants.O_NONBLOCK);
    try {
      const info = await file.stat();
      if (!info.isFile()) throw Error('Stored tasks must be a regular file');
      const bytes = Buffer.alloc(262145); let length = 0;
      while (length < bytes.length) {
        const { bytesRead } = await file.read(bytes, length, bytes.length - length, length);
        if (bytesRead === 0) break; length += bytesRead;
      }
      if (length > 262144) throw Error('Stored tasks exceed 256 KiB');
      tasks = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes.subarray(0, length)));
    } finally { await file.close(); }
  } catch (error) { if (error.code !== 'ENOENT') throw error; }
  if (!Array.isArray(tasks) || tasks.length > 100 || tasks.some(task => !validTask(task)) ||
      new Set(tasks.map(task => task.id)).size !== tasks.length) throw Error('Invalid stored tasks');
  let queue = Promise.resolve();
  return createServer({ requestTimeout: 5000, headersTimeout: 5000, maxHeaderSize: 8192 }, async (request, response) => {
    const send = (status, value) => {
      response.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store',
        'X-Content-Type-Options': 'nosniff' }); response.end(JSON.stringify(value));
    };
    const origin = `http://${request.headers.host}`;
    if (!/^127\.0\.0\.1:\d+$/.test(request.headers.host || '') ||
        (request.headers.origin && request.headers.origin !== origin)) return send(403, { error: 'Local origin required' });
    let path;
    try { path = new URL(request.url, origin).pathname; } catch { return send(400, { error: 'Invalid route' }); }
    if (request.method === 'GET' && ['/', '/client.mjs', '/style.css'].includes(path)) {
      const name = path === '/' ? 'index.html' : path.slice(1);
      response.writeHead(200, { 'Content-Type': path === '/' ? 'text/html' : path.endsWith('mjs') ? 'text/javascript' : 'text/css',
        'Content-Security-Policy': "default-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'",
        'X-Content-Type-Options': 'nosniff', 'Cache-Control': 'no-store' });
      response.end(await readFile(new URL(name, import.meta.url))); return;
    }
    if (request.method === 'GET' && path === '/api/tasks') return send(200, { tasks, remainingMinutes: tasks.filter(task => !task.done).reduce((sum, task) => sum + task.minutes, 0) });
    if (!['POST', 'PATCH'].includes(request.method) || (path !== '/api/tasks' && !/^\/api\/tasks\/\d+$/.test(path))) return send(404, { error: 'Route not found' });
    if (request.headers['content-type'] !== 'application/json') return send(415, { error: 'JSON required' });
    let bytes = 0, body = '';
    try {
      for await (const chunk of request) { bytes += chunk.length; if (bytes > 4096) return send(413, { error: 'Body too large' }); body += chunk; }
      const input = JSON.parse(body);
      if (!input || Array.isArray(input) || typeof input !== 'object') return send(400, { error: 'Invalid input' });
      const mutate = async () => {
        const next = structuredClone(tasks); let result;
        if (request.method === 'POST' && path === '/api/tasks') {
          if (Object.keys(input).some(key => !['title', 'minutes'].includes(key)) || typeof input.title !== 'string' || !input.title.trim() || input.title.length > 100 || !Number.isInteger(input.minutes) || input.minutes < 1 || input.minutes > 600) return send(400, { error: 'Title and minutes from 1 to 600 are required' });
          if (next.length >= 100) return send(409, { error: 'The 100-task exercise limit was reached' });
          result = { id: Math.max(0, ...next.map(task => task.id)) + 1, title: input.title.trim(), minutes: input.minutes, done: false }; next.push(result);
        } else if (request.method === 'PATCH' && /^\/api\/tasks\/\d+$/.test(path)) {
          if (Object.keys(input).length !== 1 || typeof input.done !== 'boolean') return send(400, { error: 'A boolean done field is required' });
          result = next.find(task => task.id === Number(path.split('/').at(-1)));
          if (!result) return send(404, { error: 'Task not found' }); result.done = input.done;
        } else return send(404, { error: 'Route not found' });
        await writeFile(dataFile + '.tmp', JSON.stringify(next), { mode: 0o600 }); await rename(dataFile + '.tmp', dataFile);
        tasks = next; send(request.method === 'POST' ? 201 : 200, result);
      };
      const operation = queue.then(mutate); queue = operation.catch(() => {}); await operation;
    } catch (error) { send(error instanceof SyntaxError ? 400 : 500, { error: error instanceof SyntaxError ? 'Invalid JSON' : 'Storage unavailable; mutation was not committed' }); }
  });
}
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const server = await createPlannerServer(process.argv[2] || 'planner-data.json');
  server.listen(3101, '127.0.0.1', () => console.log('Local study planner: http://127.0.0.1:3101'));
}
