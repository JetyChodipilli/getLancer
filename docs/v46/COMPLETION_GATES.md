# Gates: V4.6 completion

OWNS: app/**, lib/**, backend/**, ops/**, tests/**, docs/**

Scope: Finish creator archive contributions, immutable source-changing releases, current-authority controlled previews, and verify existing V4.6 flows.

- [ ] G1: Creator uploads and reviewed source-changing releases enforce archive and ownership boundaries
  EVIDENCE: pending; real database integration checks required

- [ ] G2: Controlled previews use the V4 publisher and deny withdrawn, suspended and obsolete releases
  EVIDENCE: pending; publisher and real database regressions required

- [x] G3: Frontend type-check and production build pass
  CHECK: npx tsc --noEmit && npm run build
  EXPECT: Build complete
  EVIDENCE: 2026-10-08 local TypeScript exit 0 and production build completed.

- [x] G4: Frontend and publisher regression suites pass
  CHECK: node --test tests/*.test.mjs
  EXPECT: fail 0
  EVIDENCE: 2026-10-08 local 165 tests passed, zero failures/skips.

- [ ] G5: Complete backend, source contracts and permissions regressions pass
  EVIDENCE: pending; complete CI required

- [ ] G6: Responsive and connected contribution flows pass browser checks
  EVIDENCE: pending; actual browser execution required

- [ ] G7: Final reviewed source is published with passing CI
  EVIDENCE: pending
