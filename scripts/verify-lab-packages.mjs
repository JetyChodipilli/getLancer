/** Execute the downloaded source archives from fresh directories, without repository dependencies. */
import {spawn} from 'node:child_process';
import {mkdtemp, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
async function run(command, args, cwd) {
  await new Promise((yes, no) => {
    const child = spawn(command, args, {cwd, stdio: 'inherit'});
    child.on('error', no);
    child.on('exit', code => code === 0 ? yes() : no(Error('Package reproduction failed: ' + command + ' (exit ' + code + ')')));
  });
}
// This verifies every archive byte and its exact bounded regular-file inventory before extraction.
await run(process.execPath, ['--experimental-strip-types', join(root, 'scripts/build-lab-material.mjs'), '--verify'], root);
for (const language of ['java', 'typescript', 'python']) {
  const extracted = await mkdtemp(join(tmpdir(), 'getlancer-source-'));
  try {
    await run('tar', ['--extract', '--gzip', '--file', join(root, 'public/labs/sources', language + '.tar.gz'), '--directory', extracted, '--no-same-owner', '--no-same-permissions'], root);
    await run(process.execPath, ['scripts/verify-lab-scenarios.mjs', '--language', language], extracted);
    console.log('SOURCE_PACKAGE_OK language=' + language);
  } finally {
    await rm(extracted, {recursive: true, force: true});
  }
}
console.log('SOURCE_PACKAGES_OK languages=3');
