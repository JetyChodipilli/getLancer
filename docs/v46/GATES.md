# Gates: fresh V4.6

OWNS: app/**, lib/**, backend/**, tests/**, docs/**, GATES.md

Scope: rebuild V4.6 from current main with creator contributions, reviewed immutable source, saved components, 24 distinct seeds, compact forms and visible demo roles.

- [x] G1: Implementation starts on a fresh branch from current main
  EVIDENCE: codex/v4.6-fresh-20261008 created from b672b584c6eee83e33d9de1db7863b8a38fd074b with a clean tree; no previous V4.6 branch restored.

- [x] G2: Frontend source type-checks and builds
  CHECK: npx tsc --noEmit && npm run build
  EXPECT: Build complete
  EVIDENCE: automatic-evidence=v1; definition-sha256=b5fe8e770e5cf77c8fd954363417bfa5573c957a1d3199d03d62b2335539d686; exit=0; EXPECT=matched; output-sha256=79540accb5499c01d67331648548fcf1eb9f7504762c6f84e8a0fc6049366f91; output-bytes=50401; shell=/bin/sh; cwd=/workspace/scratch/65fc1757838b/getLancer; path=cf3727fc71b6/14 entries

- [x] G3: Component catalogue, archives and saved state pass regression checks
  CHECK: node --test tests/components.test.mjs tests/component-saves.test.mjs
  EXPECT: fail 0
  EVIDENCE: automatic-evidence=v1; definition-sha256=31b9695397a7e4aaee8c9e15412f52dc7d6a9616f55ac32b59b7b48397000429; exit=0; EXPECT=matched; output-sha256=c07041f4e8ba72518a5bcbfd46bcc31d0d6882b6009b58d38ab30e44f2495fca; output-bytes=727; shell=/bin/sh; cwd=/workspace/scratch/65fc1757838b/getLancer; path=cf3727fc71b6/14 entries

- [x] G4: Java architecture and bounded-archive regression checks pass
  CHECK: mvn -B -f backend/pom.xml -Dtest='com.getlancer.architecture.*Test,com.getlancer.hosting.StaticArchiveTest' test
  EXPECT: BUILD SUCCESS
  EVIDENCE: automatic-evidence=v1; definition-sha256=9527845a8923b3efa3cf1c489f08406538962897b5aad6a185330c1ee571d0f7; exit=0; EXPECT=matched; output-sha256=678c8b3fdac7b5d30cb0c774aaf9d5e0a7bc10a2b45ed0c8b7a71afd08f51920; output-bytes=2629; shell=/bin/sh; cwd=/workspace/scratch/65fc1757838b/getLancer; path=cf3727fc71b6/14 entries

- [ ] G5: Responsive contribution flows and all seed interactions work in the browser
  EVIDENCE: pending

- [x] G6: Ponytail whole-repository audit and change review are documented
  EVIDENCE: docs/v46/REVIEW.md records measured unused dependency consumers, 1398 source lines and one possible dependency removal; final contribution diff reviewed without unrelated deletions.

- [ ] G7: Verified source is committed and the existing preview is updated
  EVIDENCE: local source committed; user explicitly authorized publication and merge on 2026-10-08. Remote checks and preview publication pending.

- [ ] G8: Disposable-database lifecycle, bookmark isolation and withdrawal regressions pass in CI
  EVIDENCE: tests implemented; remote CI pending. Local database integration was not executed.

- [ ] G9: Required creator archives, source-changing versions and controlled preview revocation are implemented
  EVIDENCE: pending; remaining S02-B02/B03/B04 work is explicitly recorded in IMPLEMENTATION.md.
