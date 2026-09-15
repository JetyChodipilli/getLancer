# Icy Wind authentication QA — 9 September 2026

**Findings**

No remaining actionable P0/P1/P2 visual findings in the reviewed layouts. This is local visual/interaction acceptance, not production authentication acceptance.

Expected differences: the original transparent logo retains its supplied colors; the separate generated arrow matches the material/composition but is not pixel-identical; the existing Arial/Helvetica stack is retained as allowed by the brief. Fields use the specified 48px height. The unavailable theme toggle is omitted. OAuth controls and backend notice reflect actual disconnected services. Signup adds the actual password minimum.

**Evidence**

Source: `docs/auth/icy-wind-auth-reference.png`, 1254 × 1254 board with two approx. 1195 × 590 screen regions.

Implementation:
- `docs/auth/screenshots/icy-login-final.jpg`, 1363 × 936, CSS viewport 1363 × 936, density 1.
- `docs/auth/screenshots/icy-signup-final.jpg`, same dimensions.
- `docs/auth/screenshots/icy-signup-mobile-final.jpg`, 375 × 936, density 1, clipped static production-HTML/CSS iframe render.

State: logged out, empty fields, disconnected backend. Source and final signup were opened together after refinement. Compare each source screen region, excluding board framing, against the corresponding app panel; absolute heights differ because the real page uses responsive fields and an actual unavailable-service message. No density differences were classified as design defects.

Focused inspection used the readable full-resolution regions: logo silhouette/aspect ratio, form typography, provider order, field labels/icons, CTA, and legal links. Additional crop files were unnecessary because these regions were readable at 1×.

Required fidelity surfaces:
- Typography: compact heavy headings, blue third headline line, plain sans-serif labels and muted supporting copy. Existing font stack is intentional.
- Layout: art/form split, inset rounded card, compact browse link, regular field rhythm, single-column mobile. No competing global auth header/footer.
- Colors: pearl/ice surfaces, blue borders, cobalt CTA, ink text. Disabled states reflect missing configuration.
- Assets: full-resolution standalone arrow and byte-unchanged transparent logo. No reference screenshot used as UI.
- Content: requested titles/subtitles, full name only on signup, remembered session only on login, actual terms/privacy links, no role picker or initial administrator-code field.

Comparison history:
1. Initial `icy-login-desktop.jpg`/`icy-signup-desktop.jpg`: [P2] saturated artwork and tight signup arrow tip. Corrected opacity to 0.86 and right-centered fit; final screenshots show the fix.
2. [P2] small motion hit target: raised minimum height to 44px.
3. Disabled providers were too faded: increased scoped opacity, preserving unavailable semantics.
4. Added 150ms route exit and 220ms entry. Clicking Create account reached the signup heading and final rendered screen.

**Verification**

- Empty login submission: associated errors and first-invalid-field focus.
- Password visibility control changed type/accessible state; no credential values entered or captured.
- Input focus pauses artwork; leaving the input resumes it.
- Manual pause persists across login/signup.
- Forgot password shows reset screen.
- Static CSS widths/scroll widths matched at 360, 375, 768, 1024px.
- Inspected console contained browser-extension metadata errors, no app-origin exceptions.
- Production build, TypeScript, 15 frontend tests, and 49 targeted backend unit tests passed.
- Scoped auth lint: zero errors, three image optimization warnings. Global lint retains unrelated pre-existing errors.

**Open checks**

Real OAuth/database/email workflows require configured services and migration V7. PostgreSQL integration tests were not executed. The app blocks iframe embedding; responsive evidence uses a separate inert copy of server-rendered HTML/CSS, without weakening that policy. It does not establish mobile hydration or real-device keyboard behavior. OS reduced-motion switching, hidden-tab switching, 1440+ widths and short-height devices remain manual checks; the corresponding handlers/styles were inspected. No motion recording capability is exposed.

**Checklist**

- [x] Two responsive auth routes and original logo.
- [x] Independent artwork motion, pause, reduced-motion guards.
- [x] Existing email/reset/MFA behavior retained.
- [x] GitHub PKCE/provider-isolated OAuth implementation.
- [x] Local build/tests and reference comparison.
- [ ] Configure services and run live OAuth/email/PostgreSQL tests.

**Final result:** passed

## Follow-up correction — 9 September 2026

User requested removal of the motion control and reported signup navigation stuck. Removed all motion-button markup, styling, and saved pause-state handling. Server-rendered artwork defaults to playing; OS reduced-motion CSS remains supported. Replaced delayed client-router navigation with native anchors to /signup and /login, removing the exit state and timer. Browser confirmed the running icy-float animation, signup heading after clicking Create account, and return navigation. Earlier pause-button and timed-exit observations above describe the superseded revision.
