import assert from 'node:assert/strict';
import {setTimeout as delay} from 'node:timers/promises';

const transientStatuses = new Set([502, 503, 504]);

// Retry only temporary HTTP service failures. Incomplete scans still fail closed.
export async function queryOsv(queries, {fetchImpl = globalThis.fetch, wait = delay, log = console.warn} = {}) {
  for (let attempt = 0; attempt < 3; attempt++) {
    const response = await fetchImpl('https://api.osv.dev/v1/querybatch', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({queries}),
      signal: AbortSignal.timeout(30000),
    });
    if (response.ok) return response.json();
    await response.body?.cancel();
    assert.ok(transientStatuses.has(response.status) && attempt < 2,
      `OSV returned ${response.status}; scan incomplete.`);
    const milliseconds = 1000 * (attempt + 1);
    log(`OSV returned ${response.status}; retrying public dependency lookup in ${milliseconds}ms (${attempt + 2}/3).`);
    await wait(milliseconds);
  }
}
