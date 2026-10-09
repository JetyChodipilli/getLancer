import {createServer} from 'node:net';

// Closed ephemeral ports can be selected again. Never reuse a fixture's identity.
const allocated = new Set();
export async function fixturePort() {
  for (let attempt = 0; attempt < 100; attempt++) {
    const server = createServer();
    await new Promise((yes, no) => server.once('error', no).listen(0, '127.0.0.1', yes));
    const value = server.address().port;
    await new Promise(done => server.close(done));
    if (allocated.has(value)) continue;
    allocated.add(value);
    return value;
  }
  throw new Error('Distinct fixture port allocation exhausted.');
}
