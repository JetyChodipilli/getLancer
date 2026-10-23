import assert from 'node:assert/strict';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {argumentsFor} from '../ops/v52/collector.mjs';
import {boundedFile, canonical, parse, writeExclusive} from '../ops/v52/evidence.mjs';
import {checkoutRevision, collectPreflight, reviewPreflight, targetPolicy, verifyProduction} from '../ops/production/qualification.mjs';
export {collectPreflight, verifyProduction};

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    const [command, ...argv] = process.argv.slice(2), args = argumentsFor(argv, ['target', 'bundle', 'public-key', 'revision', 'out']);
    assert(args.target && args.revision); assert.equal(checkoutRevision(), args.revision);
    if (command === 'preflight') {
      assert(args.out && !args.bundle && !args['public-key']); const proof = await collectPreflight({targetPath: args.target}); writeExclusive(args.out, canonical(proof) + '\n');
      try {reviewPreflight(proof, targetPolicy(parse(boundedFile(args.target, 65536)))); process.stdout.write(canonical({result: 'HOST_PREFLIGHT_OBSERVED', revision: args.revision, releaseEligible: false, requiresActualKvmDrills: true}) + '\n');}
      catch {process.stdout.write(canonical({result: 'HOST_PREFLIGHT_UNMET', revision: args.revision, releaseEligible: false, unmetChecks: Object.entries(proof.checks).filter(([, met]) => !met).map(([name]) => name), tlsUnmet: true}) + '\n'); process.exitCode = 1;}
    } else {
      assert.equal(command, 'verify'); assert(args.bundle && args['public-key'] && !args.out);
      process.stdout.write(canonical(verifyProduction({targetPath: args.target, bundlePath: args.bundle, publicKeyPath: args['public-key'], revision: args.revision})) + '\n');
    }
  } catch {process.stderr.write('Production qualification is UNMET. Require a clean exact-revision checkout, configured production target, current trusted signed admission/measurements and actual host, soak, recovery, merchant and human evidence. No activation flags were changed.\n'); process.exitCode = 1;}
}
