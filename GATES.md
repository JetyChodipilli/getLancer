# Gates: V2.5 connected acceptance

OWNS: GATES.md, .github/workflows/ci.yml, .gitignore, playwright.connected.config.ts, scripts/smoke-docker.mjs, scripts/verify-staging.mjs, tests/connected/**, tests/staging-check.test.mjs, app/components/business-workspace.tsx, app/components/business.css, docs/V2_5_ACCEPTANCE.md, ops/STAGING.md

Scope: close connected browser and deployment-verification gaps while retaining real Java services and the frontend-only cloud release.

- [x] G1: frontend types and production bundle are valid
  CHECK: npx tsc --noEmit && npm run build
  EXPECT: built in
  EVIDENCE: automatic-evidence=v1; definition-sha256=9205bbf8d859cf86692f3049bd81b99d1f281b18a73b6681b9829a0781466773; exit=0; EXPECT=matched; output-sha256=5f6f6877250b541aaf2fde669c6125c8f51d6c2e0cc2955784448b4371c93be0; output-bytes=2342; shell=/bin/sh; cwd=/workspace/scratch/70ced75f2e62/.sites-checkout; path=88718e024ae0/13 entries

- [x] G2: frontend regressions and staging-check controls pass
  CHECK: node --experimental-strip-types --test tests/*.test.mjs
  EXPECT: fail 0
  EVIDENCE: automatic-evidence=v1; definition-sha256=eb49e8610746070f4f7e3d9c3bb4a2e9f8fd7603a362fa92f1d1fcffcd2882c2; exit=0; EXPECT=matched; output-sha256=30e38b931c8a8dde7cb53a2f26fa2714834c347c5d03380cf97be5c4b10795f3; output-bytes=5826; shell=/bin/sh; cwd=/workspace/scratch/70ced75f2e62/.sites-checkout; path=88718e024ae0/13 entries

- [ ] G3: responsive preview interactions pass with zero failures
  CHECK: npx playwright test
  EXPECT: /\d+ passed \(/
  EVIDENCE: pending

- [ ] G4: connected browser journeys exercise persisted V2.5 behavior and negative permissions through the real frontend proxy
  EVIDENCE: pending; requires disposable Docker CI, not the owner's database

- [ ] G5: reviewed branch passes Java/PostgreSQL, connected services and database recovery in GitHub Actions
  EVIDENCE: pending

- [ ] G6: changes are reviewed with gstack/Ponytail and the frontend-only release is available
  EVIDENCE: pending

- [ ] G7: the owner's hosted Java/database/email/storage providers pass production acceptance
  EVIDENCE: pending; no backend hosting target or production provider credentials configured; existing authorization deploys only the frontend demo
