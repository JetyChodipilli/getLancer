# Patched lint glob compatibility

The full dependency audit includes development tools. The minimatch advisory
[GHSA-g3hv-75wg-m249](https://github.com/advisories/GHSA-g3hv-75wg-m249)
requires a patched matcher. This repository pins the genuine `minimatch@11.0.4`
through npm overrides and records its registry integrity in the lockfile.

The installed import, React and accessibility plugins still use older matcher
APIs. An override alone breaks actual lint rules. The root `postinstall` runs
[the checked adaptation](../scripts/patch-lint-globs.mjs):

| Existing consumer | Fixed upstream API |
| --- | --- |
| Callable CommonJS matcher | Named `minimatch` function |
| Internal-module regex generation and testing | `Minimatch` instance and `match` |

The script checks plugin versions, source locations, full original/patched
SHA-256 hashes and replacement counts before writing any of the 14 reviewed
files. Repeated runs verify the already-patched hashes. Unexpected versions or
source changes fail before pending writes. The matching algorithm remains in
the patched upstream package; no old matcher is copied, version disguised,
rule disabled or audit exception added.

Normal `npm ci` applies the adaptation automatically. An intentional manual
`npm ci --ignore-scripts` requires `node scripts/patch-lint-globs.mjs` before
linting. A non-CI production install may omit all development plugins; partial
or expected development installations fail if they are missing. CI never
accepts the production omission shortcut.

Run `node --test tests/lint-glob-compat.test.mjs` to exercise the actual ESLint
and plugin APIs. Both accepted and rejected cases cover file/ignore globs,
namespace exceptions, internal-module allow/forbid patterns, React prop
patterns, and accessibility label/control patterns. Run the unchanged full
audit with `npm audit --audit-level=moderate`.

This is a temporary compatibility repair. When all three upstream plugins
support the patched matcher API, review their changes and remove the obsolete
adaptations. Version/hash guards deliberately require that review when a
plugin changes. Keep the behavioral regressions and full dependency audit.
