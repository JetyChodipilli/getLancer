/** Read-only catalogue load with an explicit finite profile. */
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {argumentsFor, load, numberArg} from '../ops/v52/collector.mjs';
import {boundedFile, parse} from '../ops/v52/evidence.mjs';
export {load} from '../ops/v52/collector.mjs';
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    const args = argumentsFor(process.argv.slice(2), ['--origin', '--environment', '--revision', '--out', '--duration-ms', '--rate', '--concurrency', '--timeout-ms', '--profile']);
    if (!args['--out'] || !args['--profile']) throw Error('Output and declared server profile required');
    const result = await load({origin: args['--origin'], environment: args['--environment'], revision: args['--revision'], out: args['--out'], serverProfile: parse(boundedFile(args['--profile'], 4096)), durationMs: numberArg(args['--duration-ms'] ?? '60000', 100, 300000), rate: numberArg(args['--rate'] ?? '20', 1, 20), concurrency: numberArg(args['--concurrency'] ?? '10', 1, 10), timeoutMs: numberArg(args['--timeout-ms'] ?? '5000', 1, 10000)});
    process.stdout.write(JSON.stringify(result) + '\n');
  } catch {process.stderr.write('V5.2 load rejected. Supply a deployed revision, environment, origin and unused output path; the finite profile is limited to 20 requests/second and 10 concurrent GETs.\n'); process.exitCode = 1;}
}
