# Gates: V4.6 PR bug fixes

OWNS: app/components/**, lib/**, backend/src/**, tests/**, docs/v46/**

Scope: Reproduce and fix V4.6 PR #40 defects, retain publication and authorization boundaries, and publish tested fixes to its existing branch.

- [x] G1: Every reported defect has a failing reproduction and a passing regression after its fix
  EVIDENCE: Published-context PostgreSQL-compatible predicate: failed before, all 3 cases passed after. AccountExportResponseTest: failed before, passed after within 10 passing Java tests. Empty-ZIP browser regression: failed before, passed after on desktop/phone/tablet. See BUGFIX_REVIEW.md for scope and integration limitations.

- [x] G2: Frontend types and production bundle remain valid
  CHECK: npx tsc --noEmit && npm run build
  EXPECT: built in
  EVIDENCE: automatic-evidence=v1; definition-sha256=9205bbf8d859cf86692f3049bd81b99d1f281b18a73b6681b9829a0781466773; exit=0; EXPECT=matched; output-sha256=4dc9d819cde1a0a4da9cf5ee86a14d70a935bfe77fc52707eddcd779cb7ea041; output-bytes=52154; shell=/bin/sh; cwd=/workspace/scratch/74bc6691ecf3/getLancer; path=3de439d84dd2/13 entries

- [x] G3: Node and publisher regressions pass without failures
  CHECK: node --experimental-strip-types --test tests/*.test.mjs
  EXPECT: fail 0
  EVIDENCE: automatic-evidence=v1; definition-sha256=eb49e8610746070f4f7e3d9c3bb4a2e9f8fd7603a362fa92f1d1fcffcd2882c2; exit=0; EXPECT=matched; output-sha256=4d0dfb81e537dbc4036f170b77aca004ce5202dec8c78565ce4fafb5c333ed84; output-bytes=16074; shell=/bin/sh; cwd=/workspace/scratch/74bc6691ecf3/getLancer; path=3de439d84dd2/13 entries

- [x] G4: Authorization and database privilege source contracts remain valid
  CHECK: node scripts/check-security-contract.mjs && node ops/database/check-permissions.mjs
  EXPECT: Database permission source coverage passed
  EVIDENCE: automatic-evidence=v1; definition-sha256=5a7d89f7ce0604fe61869d8d41aad423c95a166a8949fbe1c2b72299004a63f8; exit=0; EXPECT=matched; output-sha256=f7875c151186fc8af8788fb5ed5063fe0c78f06510b5d7f5402c821e329d8f78; output-bytes=289; shell=/bin/sh; cwd=/workspace/scratch/74bc6691ecf3/getLancer; path=3de439d84dd2/13 entries

- [ ] G5: Affected responsive and connected browser flows pass on the published revision
  EVIDENCE: pending

- [ ] G6: The existing PR contains the reviewed fixes and all six required CI jobs pass
  EVIDENCE: pending
