/** Review and sign actual captured evidence; signing does not activate either deployment lane. */
import assert from 'node:assert/strict';
import {createPublicKey} from 'node:crypto';
import {dirname, resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {argumentsFor} from '../ops/v52/collector.mjs';
import {boundedFile, canonical, envelope, journalReport, LIMITS, openReport, readJournal, validateReport, verifyReportFile, writeExclusive} from '../ops/v52/evidence.mjs';
export {validateReport, verifyReportFile} from '../ops/v52/evidence.mjs';
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    const [command, ...argv] = process.argv.slice(2), args = argumentsFor(argv, ['--file', '--public-key', '--private-key', '--environment', '--revision', '--logs', '--out']);
    if (command === 'verify' || command === 'inspect') {
      assert(args['--file']); assert(command !== 'verify' || args['--public-key']); assert(!args['--logs'] && !args['--out'] && !args['--private-key']);
      const summary = verifyReportFile(args['--file'], {environment: args['--environment'], revision: args['--revision'], release: command === 'verify', ...(args['--public-key'] ? {publicKeyBytes: boundedFile(args['--public-key'], 4096)} : {})}); process.stdout.write(JSON.stringify(summary) + '\n');
    } else if (command === 'collect' || command === 'sign') {
      assert(args['--out'] && !args['--public-key']); let report;
      if (command === 'collect') {assert(args['--logs'] && !args['--file']); const files = args['--logs'].split(','); assert(files.length > 0 && files.length <= 100); assert(files.every(file => dirname(resolve(file)) === dirname(resolve(args['--out']))), 'Collected logs must be beside their report'); const journals = []; let bytes = 0, observations = 0; for (const file of files) {const journal = readJournal(file); bytes += journal.byteLength; observations += journal.observations.length; assert(bytes <= LIMITS.fileBytes && observations <= LIMITS.observations); journals.push(journal);} report = journalReport(journals);}
      else {assert(args['--file'] && args['--private-key'] && !args['--logs']); assert.equal(dirname(resolve(args['--file'])), dirname(resolve(args['--out']))); report = openReport(boundedFile(args['--file'], 131072), undefined, false);}
      const privateKeyBytes = args['--private-key'] ? boundedFile(args['--private-key'], 4096) : null;
      const summary = validateReport(report, {baseDir: dirname(resolve(args['--out'])), environment: args['--environment'], revision: args['--revision'], release: false, ...(privateKeyBytes ? {publicKeyBytes: createPublicKey(privateKeyBytes).export({format: 'pem', type: 'spki'})} : {})});
      // A signature authenticates provenance. The verify command separately rejects every UNMET gate.
      writeExclusive(args['--out'], canonical(privateKeyBytes ? envelope(report, privateKeyBytes) : report) + '\n'); process.stdout.write(JSON.stringify({...summary, signed: Boolean(privateKeyBytes), releaseEligible: false}) + '\n');
    } else throw Error('Unknown command');
  } catch {process.stderr.write('V5.2 evidence rejected. Require current immutable evidence, exact deployment/revision, bounded regular files and trusted operator signatures. Synthetic, incomplete or altered evidence cannot certify release.\n'); process.exitCode = 1;}
}
