/** Real wall-clock observations; the command never fabricates or compresses a soak. */
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {argumentsFor, monitor, numberArg} from '../ops/v52/collector.mjs';
import {boundedFile, LIMITS, parse} from '../ops/v52/evidence.mjs';
export {monitor} from '../ops/v52/collector.mjs';
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    const args = argumentsFor(process.argv.slice(2), ['--origin', '--environment', '--revision', '--out', '--duration-ms', '--cadence-ms', '--timeout-ms', '--admin', '--profile']);
    if (args['--admin'] !== undefined && args['--admin'] !== 'configured') throw Error('Explicit admin configuration required');
    if (!args['--out'] || !args['--profile'] || (args['--admin'] === 'configured' && !process.env.V52_ADMIN_COOKIE_FILE)) throw Error('Output, declared profile and configured cookie file required');
    const result = await monitor({origin: args['--origin'], environment: args['--environment'], revision: args['--revision'], out: args['--out'], serverProfile: parse(boundedFile(args['--profile'], 4096)), durationMs: numberArg(args['--duration-ms'], 100, LIMITS.sevenDaysMs + LIMITS.cadenceMs), cadenceMs: numberArg(args['--cadence-ms'] ?? '60000', 100, LIMITS.cadenceMs), timeoutMs: numberArg(args['--timeout-ms'] ?? '5000', 1, LIMITS.timeoutMs), ...(args['--admin'] === 'configured' ? {adminCookieFile: process.env.V52_ADMIN_COOKIE_FILE, adminConfigured: true} : {})});
    process.stdout.write(JSON.stringify(result) + '\n');
  } catch {process.stderr.write('V5.2 monitor rejected. Supply bounded duration, deployed revision, environment, origin and unused output path. Authentication and response details are never printed.\n'); process.exitCode = 1;}
}
