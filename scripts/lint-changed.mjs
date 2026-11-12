import {existsSync} from 'node:fs';
import {dirname, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const git = (...args) => {
  const result = spawnSync('git', args, {cwd: root, encoding: 'utf8'});
  if (result.error || result.status !== 0) {
    throw new Error(`Changed-file lint could not inspect Git: ${result.error?.message || result.stderr.trim()}`);
  }
  return result.stdout;
};
const paths = output => output.split('\0').filter(Boolean);
const sha = (value, name) => {
  if (!/^(?:[a-f0-9]{40}|[a-f0-9]{64})$/i.test(value)) throw new Error(`${name} must be a full Git commit SHA.`);
  return value;
};
const commitExists = (value, name) => {
  const result = spawnSync('git', ['cat-file', '--batch-check=%(objecttype)'], {cwd: root, encoding: 'utf8', input: `${value}\n`});
  if (result.error || result.status !== 0 || result.stderr.trim()) {
    throw new Error(`Changed-file lint could not inspect Git: ${result.error?.message || result.stderr.trim() || 'commit inspection failed'}`);
  }
  const type = result.stdout.trim();
  if (type === `${value} missing`) return false;
  if (type !== 'commit') throw new Error(`${name} must identify a Git commit.`);
  return true;
};

try {
  const committed = process.env.CI === 'true' || Boolean(process.env.LINT_HEAD_SHA || process.env.LINT_BASE_SHA);
  let changed;
  if (committed) {
    const head = sha(process.env.LINT_HEAD_SHA || git('rev-parse', 'HEAD').trim(), 'LINT_HEAD_SHA');
    const base = process.env.LINT_BASE_SHA ? sha(process.env.LINT_BASE_SHA, 'LINT_BASE_SHA') : undefined;
    if (!commitExists(head, 'LINT_HEAD_SHA')) throw new Error('LINT_HEAD_SHA must identify an available Git commit.');
    if (base && !/^0+$/.test(base) && commitExists(base, 'LINT_BASE_SHA')) {
      changed = paths(git('diff', '--name-only', '--diff-filter=ACMR', '-z', base, head, '--'));
    } else {
      // First pushes and amended/force-pushed siblings may have no available
      // before commit. Cover the complete branch against the default branch.
      const originMain = spawnSync('git', ['merge-base', 'origin/main', head], {cwd: root, encoding: 'utf8'});
      if (originMain.status === 0 && originMain.stdout.trim().toLowerCase() !== head.toLowerCase()) {
        changed = paths(git('diff', '--name-only', '--diff-filter=ACMR', '-z', originMain.stdout.trim(), head, '--'));
      } else {
        // A missing or HEAD-equal anchor cannot bound branch changes. Lint every
        // tracked HEAD source rather than silently checking only its last commit.
        changed = paths(git('ls-tree', '-r', '--name-only', '-z', head, '--'));
      }
    }
  } else {
    changed = [...paths(git('diff', '--name-only', '--diff-filter=ACMR', '-z', 'HEAD', '--')),
      ...paths(git('ls-files', '--others', '--exclude-standard', '-z'))];
  }
  const files = [...new Set(changed)].filter(path => /\.(?:[cm]?js|jsx|tsx?)$/.test(path) && existsSync(resolve(root, path))).sort();
  if (!files.length) {
    console.log('Changed-file ESLint passed: no modified JavaScript or TypeScript files.');
  } else {
    console.log(`Checking ${files.length} changed source/test files with the repository ESLint rules.`);
    // Argument arrays preserve spaces and shell metacharacters in filenames.
    for (let index = 0; index < files.length; index += 80) {
      const result = spawnSync(process.execPath, [resolve(root, 'node_modules/eslint/bin/eslint.js'), '--no-warn-ignored', '--', ...files.slice(index, index + 80)], {cwd: root, stdio: 'inherit'});
      if (result.error) throw result.error;
      if (result.status !== 0) process.exit(result.status || 1);
    }
    console.log(`Changed-file ESLint passed: ${files.length} modified source/test files.`);
  }
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exitCode = 1;
}
