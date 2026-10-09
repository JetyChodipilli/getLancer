import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createServer, Server} from 'node:net';
import {fixturePort} from '../scripts/lab-fixture-ports.mjs';

test('fixture ports reject real reused sockets and bound allocation retries', async () => {
  const first = await fixturePort();
  // Positive control: closing a listening socket does allow the same port again.
  const control = createServer();
  await new Promise((yes, no) => control.once('error', no).listen(first, '127.0.0.1', yes));
  assert.equal(control.address().port, first);
  await new Promise(done => control.close(done));

  const listen = Server.prototype.listen;
  let forced = 2;
  Server.prototype.listen = function (...args) {
    if (args[0] === 0 && forced-- > 0) args[0] = first;
    return listen.apply(this, args);
  };
  try {
    const second = await fixturePort();
    assert.notEqual(second, first);
    assert(forced <= 0, 'The real duplicate candidates must be exercised.');
    forced = 100;
    await assert.rejects(fixturePort(), /Distinct fixture port allocation exhausted/);
    assert.equal(forced, 0);
  } finally {
    Server.prototype.listen = listen;
  }
});
