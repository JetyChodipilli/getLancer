// Run after the trusted native build has installed pinned example dependencies.
import test from 'node:test';
import assert from 'node:assert/strict';
import {cp, mkdtemp, readFile, rm, symlink} from 'node:fs/promises';
import {execFileSync} from 'node:child_process';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';

for (const framework of ['vue', 'svelte']) {
  test(`${framework} compiles the same bytes from an extraction path containing spaces and escaped characters`, async t => {
    const source = fileURLToPath(new URL(`../examples/frameworks/${framework}/`, import.meta.url));
    const directory = await mkdtemp(join(tmpdir(), 'getlancer framework é%-'));
    t.after(() => rm(directory, {recursive: true, force: true}));
    for (const name of ['build.mjs', 'package.json', 'package-lock.json', 'src']) await cp(join(source, name), join(directory, name), {recursive: true});
    // Reuse already-installed pinned compilers; this regression performs no install or network request.
    await symlink(join(source, 'node_modules'), join(directory, 'node_modules'), process.platform === 'win32' ? 'junction' : 'dir');
    execFileSync(process.execPath, ['build.mjs'], {cwd: directory, encoding: 'utf8', timeout: 120000, maxBuffer: 1048576});
    for (const name of ['native.js', 'native.css']) {
      const output = await readFile(join(directory, 'dist', name)); assert.ok(output.length > 0);
      assert.deepEqual(output, await readFile(join(source, 'dist', name)), `${framework} ${name} changed with the extraction path`);
    }
  });
}
