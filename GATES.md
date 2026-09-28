# Gates: Reference marketplace design
OWNS: DESIGN.md, GATES.md, app/globals.css, app/components/explore.tsx, app/components/project-stack.tsx, app/components/preview.tsx, app/components/header.tsx, app/components/footer.tsx, app/components/browse-toolbar.tsx, public/fonts, tests/marketplace-ui.test.mjs
Scope: implement the supplied white/blue marketplace reference while preserving existing account and project flows.

- [x] G1: Frontend type checking succeeds.
  CHECK: npx tsc --noEmit && node -e "console.log('TYPECHECK_COMPLETE')"
  EXPECT: TYPECHECK_COMPLETE
  EVIDENCE: automatic-evidence=v1; definition-sha256=0e4f122b58c6a386b10f565f641e210b8928f6f2a481a0bb59db706b263fd76a; exit=0; EXPECT=matched; output-sha256=65e99d73e5e1e0b5856b1d44f518de639d94647c6ca46979c302a789bda8dd29; output-bytes=119; shell=/bin/sh; cwd=/workspace/sites/getlancer; path=ef3568d3b391/13 entries
- [x] G2: Existing frontend behavior passes regression checks.
  CHECK: node /root/.codex/plugins/cache/openai-curated-remote/sites/0.1.71/scripts/build-site.mjs && node --test tests/*.test.mjs
  EXPECT: fail 0
  EVIDENCE: automatic-evidence=v1; definition-sha256=850624211b403bd5bb32fc6d2f7747836590ac031dcb8f916f89fa4c2fa1273f; exit=0; EXPECT=matched; output-sha256=cfe9665e87bddd69a459449e1c582214df34d5a25f954e21b8d3b971224f7baf; output-bytes=4606; shell=/bin/sh; cwd=/workspace/sites/getlancer; path=ef3568d3b391/13 entries
- [x] G3: Browser review confirms the reference layout, four desktop columns, functional search/filter controls, responsive layout and no hero pause button.
  EVIDENCE: Desktop browser review: four 298px columns, Inter font, no horizontal overflow; Inventory + React search returns Stockroom; filters open; sticky header stays at top and search dock appears on scroll. Static rendered-DOM layout at 390px: one column and no overflow (mobile interaction not tested). No hero pause/play control.
- [ ] G4: Reviewed code is pushed to GitHub and the updated cloud site deploys successfully.
  EVIDENCE: pending push and deployment.
