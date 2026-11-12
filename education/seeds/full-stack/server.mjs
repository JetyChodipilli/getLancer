import {createServer} from 'node:http';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

export function createTaskServer() {
  const tasks = []; let nextId = 1;
  return createServer(async (request, response) => {
    const send = (status, body) => { response.writeHead(status, {'Content-Type': 'application/json'}); response.end(JSON.stringify(body)); };
    const pathname = new URL(request.url, 'http://localhost').pathname;
    try {
      if (request.method === 'GET' && pathname === '/tasks') return send(200, {items: tasks});
      if (request.method === 'POST' && pathname === '/tasks') {
        const chunks = []; let bytes = 0;
        for await (const chunk of request) {
          bytes += chunk.length;
          if (bytes > 4096) return send(413, {error: 'Body too large'});
          chunks.push(chunk);
        }
        // A network chunk can end inside a UTF-8 character. Decode only the complete, bounded body.
        const value = JSON.parse(new TextDecoder('utf-8', {fatal: true}).decode(Buffer.concat(chunks, bytes)));
        if (typeof value.title !== 'string' || !value.title.trim() || value.title.length > 100) return send(400, {error: 'A short title is required'});
        const task = {id: nextId++, title: value.title.trim(), completed: false}; tasks.push(task); return send(201, task);
      }
      if (request.method === 'PATCH' && /^\/tasks\/\d+$/.test(pathname)) {
        const task = tasks.find(t => t.id === Number(pathname.split('/')[2]));
        if (!task) return send(404, {error: 'Task not found'});
        task.completed = true; return send(200, task);
      }
      send(404, {error: 'Route not found'});
    } catch { send(400, {error: 'Invalid JSON'}); }
  });
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const configuredPort = process.env.PORT ?? '3100';
  if (!/^(?:0|[1-9][0-9]{0,4})$/.test(configuredPort) || Number(configuredPort) > 65535) throw Error('PORT must be an integer from 0 to 65535');
  const server = createTaskServer();
  server.listen(Number(configuredPort), '127.0.0.1', () => console.log(`Local demonstration at http://127.0.0.1:${server.address().port}/tasks`));
}
