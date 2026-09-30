# Gates: V2.5 connected acceptance

OWNS: GATES.md, .github/workflows/ci.yml, .gitignore, playwright.connected.config.ts, scripts/smoke-docker.mjs, scripts/verify-staging.mjs, tests/connected/**, tests/staging-check.test.mjs, app/components/business-workspace.tsx, app/components/business.css, docs/V2_5_ACCEPTANCE.md, ops/STAGING.md

Scope: close connected browser and deployment-verification gaps while retaining real Java services and the frontend-only cloud release.

- [x] G1: frontend types and production bundle are valid
  CHECK: npx tsc --noEmit && npm run build
  EXPECT: built in
  EVIDENCE: automatic-evidence=v1; definition-sha256=9205bbf8d859cf86692f3049bd81b99d1f281b18a73b6681b9829a0781466773; exit=0; EXPECT=matched; output-sha256=989060d116709fcecd034fd15c15e20d5aa2b805df0a4a226154e411fb7226ad; output-bytes=2342; shell=/bin/sh; cwd=/workspace/scratch/70ced75f2e62/.sites-checkout; path=88718e024ae0/13 entries

- [x] G2: frontend regressions and staging-check controls pass
  CHECK: node --experimental-strip-types --test tests/*.test.mjs
  EXPECT: fail 0
  EVIDENCE: automatic-evidence=v1; definition-sha256=eb49e8610746070f4f7e3d9c3bb4a2e9f8fd7603a362fa92f1d1fcffcd2882c2; exit=0; EXPECT=matched; output-sha256=f745c9d27fc5af92ebcbbb51e0e0cadc948875715fa25334c528b98bfb2adb7c; output-bytes=5912; shell=/bin/sh; cwd=/workspace/scratch/70ced75f2e62/.sites-checkout; path=88718e024ae0/13 entries

- [x] G3: responsive preview interactions pass with zero failures
  EVIDENCE: External CI review: head 7d964402092a0a288c7a06376901dca12d69b1f1, run 36752780563, frontend job 110015334806, 15 Chromium tests passed on desktop/phone/tablet. Local browser download was unavailable; this gate is explicitly reviewed from GitHub logs/artifacts, not claimed as local automatic evidence.

- [x] G4: connected browser journeys exercise persisted V2.5 behavior and negative permissions through the real frontend proxy
  EVIDENCE: Run 36752780563, docker-startup job 110015974735, all three connected desktop/phone/tablet journeys passed in 50.3 seconds. No intercepted network responses. Real SMTP-verified accounts and Java/PostgreSQL records; login/MFA, consent, negative permissions, focus recovery, reload persistence, current matches, shared lists, concierge, revocation and closed-request checks passed.

- [x] G5: reviewed branch passes Java/PostgreSQL, connected services and database recovery in GitHub Actions
  EVIDENCE: Run 36752780563 passed all three jobs on head 7d964402092a0a288c7a06376901dca12d69b1f1: 157 Java tests, 66 frontend tests, 15 preview and three connected browser tests; actual SMTP/storage service journeys; PostgreSQL outage recovery; encrypted restore with application/migration/admin row checks.

- [x] G6: changes are reviewed with gstack/Ponytail and the frontend-only release is available
  EVIDENCE: Reviewed CI guards, synthetic credential isolation, negative authorization, persistence/reload checks, proxy boundary, HTTPS validation and bounded read-only measurements. Existing libraries/stdlib only; no runtime package changes. Connected desktop/phone/tablet screenshots reviewed with no overflow. Frontend-only private publication succeeded on 30 September 2026 at https://getlancer-v1.jety124050.chatgpt.site, source 1d393cf4e35df44c49bb161e9cef5424fefbb830, deployment appgdep_6abd4c033cec8191951232fc9b883fdd. Owner-only audience preserved.

- [ ] G7: the owner's hosted Java/database/email/storage providers pass production acceptance
  EVIDENCE: pending; no backend hosting target or production provider credentials configured; existing authorization deploys only the frontend demo

ABANDON: G7 Owner handoff required: choose a Java HTTPS hosting target and configure owner-controlled PostgreSQL, SMTP and S3 providers before deployed acceptance. The existing frontend has no BACKEND_URL; standing authorization publishes only the frontend demo.
