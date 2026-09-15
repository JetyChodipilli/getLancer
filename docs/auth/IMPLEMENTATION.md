# Icy Wind authentication implementation

Implemented locally on 9 September 2026 from CODEX_CONTEXT.md and icy-wind-auth-reference.png. No deployment was requested or performed.

## Changes

- `/login` and `/signup` use one reusable auth route layout and form component. The marketplace navigation and footer no longer compete with the auth composition.
- Original transparent stacked getLancer logo is reused unchanged. Separate generated icy arrow artwork supplies the left visual; no reference screenshot is used as page content.
- Pearl/ice surfaces, translucent form panel, blue CTA, Google/GitHub controls, leading field icons, password visibility, signup terms links, and mobile single-column layout.
- Arrow float, path highlight, glint, fine-pointer parallax, with automatic playback and no motion control. Motion pauses while inputs are focused, the art is offscreen, the document is hidden, or reduced motion is requested.
- Existing email/password, signup verification, password reset, and administrator MFA paths retained. No administrator-code field appears during ordinary login.
- The unchecked remember option uses a browser-session cookie by default. Checked, it preserves the cookie for the existing 24-hour maximum; administrator sessions remain one hour.
- GitHub authorization-code OAuth uses S256 PKCE, hashed state and browser binding, single-use provider-scoped pending records, verified primary email, immutable GitHub numeric user ID, and app-owned redirects. Tokens and client secrets stay on the backend. Existing emails are never automatically merged across providers. Reserved/privileged administrator accounts cannot use OAuth.

## Relevant files

- `app/(auth)/layout.tsx`, `app/(auth)/login`, `app/(auth)/signup`
- `app/components/auth-form.tsx`, `app/components/icy-auth-shell.tsx`, `app/globals.css`
- `backend/src/main/java/com/getlancer/GitHubAuthController.java`, `GitHubAccounts.java`, and existing Google/session handlers
- `backend/src/main/resources/db/migration/V7__github_oauth.sql`
- `backend/src/test/java/com/getlancer/GitHubAuthTest.java`
- `.env.example` and `backend/src/main/resources/application.properties`

## Required service configuration

The preview backend is disconnected. The OAuth buttons intentionally remain unavailable until `/api/v1/auth/providers` confirms configuration. No live Google/GitHub sign-in or delivery of verification/reset email was demonstrated.

Set backend-only `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET` using an OAuth app owned by the project. Set its callback to `<APP_ORIGIN>/api/v1/auth/github/callback`. Google keeps `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, and callback `<APP_ORIGIN>/api/v1/auth/google/callback`. Keep `APP_ORIGIN` equal to the actual frontend origin, connect the frontend backend URL, apply Flyway migration V7 on the intended PostgreSQL database, and configure SMTP. No provider secret belongs in a browser-exposed variable or committed file.

Official implementation reference: https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps (PKCE, state, token exchange, and identity revalidation).

## Verification

- Production build passed; TypeScript passed.
- 15 frontend render tests passed.
- 49 targeted backend unit tests passed, including 12 new GitHub/session tests.
- Auth components and auth routes: zero ESLint errors; three existing-pattern `img` optimization warnings. Repository-wide lint still has unrelated existing errors and is not a clean launch gate.
- Browser: rendered both routes, verified first-invalid-field focus on empty submission, password visibility toggle, motion pause on field focus, manual pause persistence across login/signup, and non-fabricated backend-unavailable state.
- Responsive static render of production HTML/CSS: 360, 375, 768, 1024 pixels without horizontal overflow. This was a separate inert QA document because the real application blocks iframe embedding; the security policy was preserved. It does not test mobile keyboard or live mobile authentication.
- Console inspection: browser-extension metadata errors; no app-origin exceptions in the inspected log.
- PostgreSQL integration tests and live OAuth are not verified in this environment.

## Explicit fidelity and testing limits

The supplied image is a two-screen design board; the implementation is two independent responsive pages. Artwork follows its composition/material but is newly generated rather than pixel-identical. The original provided logo is unchanged, including its original colors. Existing Arial/Helvetica pairing is retained. Theme toggle is omitted because the app does not have a working theme flow. Copy adds the actual 12-character minimum and honest missing-service state. The shell enters with a 420ms fade and the form with a 220ms entry. Login/signup switches now use ordinary browser navigation, without a timer or exit-state lock. Reduced-motion mode skips decorative animations. No motion-recording capability is exposed; still screenshots are supplied. OS reduced-motion switching, real-device keyboard/zoom behavior, and end-to-end authenticated sessions remain manual/live environment checks.

## Navigation and motion correction — 9 September 2026

Removed the visible motion control and stored pause preference at the user’s request. Artwork starts automatically in the server-rendered markup, so hydration cannot strand it in the initial paused state. Operating-system reduced-motion preferences remain respected without displaying a button. Replaced the intercepted, timer-delayed account link with native links in both directions; the signup route and form do not depend on the client router or backend availability. Browser checks verified automatic animation and navigation in both directions.
