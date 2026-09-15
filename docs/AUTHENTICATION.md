# getLancer authentication

Updated 8 September 2026 at the user's request. This adds Google OpenID Connect to the existing marketplace account model; it does not replace the private Site's separate viewer access policy.

## Experience

- `/login` and `/signup` share an original Slate Atelier split layout: public project cards on the left, a focused form on the right. The compact header retains the exact transparent company logo. Mobile prioritizes the form.
- Cards load from public discovery when connected, with a 2.5-second timeout so a catalog outage cannot block authentication. Only the disconnected design preview uses explicitly labelled sample projects.
- Password visibility, password-manager autocomplete, paste support, field-level errors, pending states, reset-email feedback and consent are included. Reduced motion disables card entrance animation.
- One account supports CLIENT and DEVELOPER roles. Signup does not grant ADMIN or approve a builder profile.
- Account services remain disconnected in the current private preview. A disabled Google button and availability text are deliberate; no fake sign-in or synthetic account is created.

## Administrator code

The code is the administrator's six-digit time-based authenticator code (TOTP), not a registration code, static password, email code or client/developer requirement.

1. `POST /api/v1/auth/login` checks email/password without disclosing administrator status on a wrong password.
2. Ordinary members receive their existing one-day session. For administrators, the server creates a five-minute challenge and returns `{ "mfaRequired": true }` without issuing a new session.
3. A Secure/HttpOnly/SameSite=Lax `gl_mfa` cookie binds the challenge to the browser. The form replaces the password step with the six-digit input; the password is cleared from React state.
4. `POST /api/v1/auth/login/mfa` locks the challenge and user, verifies ACTIVE status and ADMIN role, checks expiry and TOTP, and atomically consumes the challenge before issuing a one-hour MFA-verified session.
5. Five failed attempts exhaust the challenge. Failure updates intentionally commit; a rollback must not reset the attempt count. Password reset and logout invalidate applicable pending challenges.

`ADMIN_TOTP_SECRET` must be provisioned securely into the administrator's authenticator during initial bootstrap. Existing administrator credentials are not overwritten on startup. Google cannot bypass administrator MFA: administrator Google sign-in returns to password login.

## Google configuration

Create an OAuth client of type **Web application** in the Google Cloud project owned by getLancer. Configure its consent-screen branding and authorized test users while the app remains in Google's testing mode.

Set these values only in the Spring backend environment:

```text
GOOGLE_CLIENT_ID=<Google Web application client ID>
GOOGLE_CLIENT_SECRET=<Google client secret>
APP_BASE_URL=<the exact public frontend origin>
SECURE_COOKIES=true
```

Register this exact authorized redirect URI for the current Site:

```text
https://getlancer-v1.jety124050.chatgpt.site/api/v1/auth/google/callback
```

For the existing local frontend configuration, register this separately:

```text
http://localhost:3000/api/v1/auth/google/callback
```

The scheme, host, port and path must match `APP_BASE_URL`; do not register the backend's internal hostname. This implementation uses a server authorization-code flow and no browser Google SDK, so it does not require a JavaScript-origin entry solely for this flow. Do not put the client secret in `NEXT_PUBLIC_*`, browser storage, the public asset bundle or Git.

Apply migration `V5__google_and_login_challenges.sql`, connect PostgreSQL/email as already documented, start the backend and set the frontend's `BACKEND_URL`. `GET /api/v1/auth/providers` returns only whether Google is configured, never its secret. Both Google values must be nonblank to enable the button. The current owner-private Site remains accessible only to its authorized viewer; private publishing does not open the marketplace to the public.

## Protocol and identity rules

- Start with a same-origin, CSRF-checked POST to `/api/v1/auth/google/start`. The body has `intent: login | signup`; signup additionally requires `acceptedTerms: true`.
- Request only `openid email profile`. No offline access, refresh token or Google API permissions are requested.
- Generate independent cryptographically random state, browser binding, nonce and PKCE verifier. Store hashed state/binding and temporary nonce/verifier in PostgreSQL for ten minutes. The browser gets only a short-lived HttpOnly binding cookie.
- The GET callback atomically deletes matching, unexpired state before any exchange. It verifies the browser binding, exchanges the code with the fixed Google HTTPS token endpoint and sends the PKCE verifier.
- Spring Security's Nimbus decoder validates the Google signature against the fixed Google JWK endpoint. Additional validation covers allowed issuer, audience, authorized party, issued/expiry time, nonce, nonempty subject and verified email claim. Tokens, codes, client secrets and Google provider error descriptions are never logged or displayed.
- `(provider, subject)` is the stable identity key. A matching email address never automatically links an existing password account. That user must use the existing password route; an explicit authenticated account-linking flow is not included in this change.
- A new Google account is created only through the consented signup intent. It receives the same dual roles, unapproved builder profile and three showcase slots as email signup. A normal Google login for an unknown identity goes to signup first.
- For Gmail and a matching verified Workspace `hd` domain, Google's email assertion is authoritative. Other Google email addresses require getLancer's separate email confirmation before email-dependent privileges are granted.
- Suspended/deleted accounts cannot authenticate. A changed Google email does not silently rewrite the marketplace email or acquire someone else's inquiry history; it requires account recovery. The reserved admin email and any linked ADMIN role use password + TOTP only.
- The callback's redirect is restricted by the frontend proxy to `/workspace` or the login/signup page with a fixed error code. Client-supplied return URLs are not accepted.
- Successful Google login creates the same HttpOnly application session as ordinary login. Google access/ID tokens are not persisted. Cookies use Secure in production and SameSite=Lax for the top-level OAuth return.
- Password reset can establish an email/password credential for a Google-created account; it still requires possession of the getLancer email link.

## Contract updates

| Endpoint | Behavior |
|---|---|
| `POST /auth/signup` | Accepts optional `displayName`; requires `acceptedTerms: true`; stores the supplied name |
| `POST /auth/login` | Ordinary success `{id}`; valid administrator credentials return `{mfaRequired:true}` and challenge cookie |
| `POST /auth/login/mfa` | `{totp}` plus challenge cookie; success `{id}`; `MFA_INVALID` or `MFA_EXPIRED` errors |
| `GET /auth/providers` | `{google:boolean}` |
| `POST /auth/google/start` | `{intent,acceptedTerms?}`; returns `{authorizationUrl}` and browser-binding cookie |
| `GET /auth/google/callback` | One-time provider response; 303 to approved local destination |

All paths above are prefixed by `/api/v1`. Old clients must handle the administrator challenge response instead of sending TOTP alongside the first password request. User-facing errors preserve their structured error code via `ApiError`.

## Validation and limits

- Frontend build and TypeScript checks passed; 15 frontend tests passed in total; authentication render tests cover both forms, autocomplete, consent, hidden initial MFA and unconfigured-provider behavior.
- Full Java main/test source compilation passed with Maven and Java 17. **25 focused unit tests passed**: 11 authentication-flow checks, 11 Google token-claim checks and 3 account-collision/enforcement checks. Test doubles use Mockito's subclass maker, avoiding dependence on JVM self-attachment.
- These tests use mocked persistence for controller behavior. They do not establish PostgreSQL transaction/concurrency behavior, successful real Google account consent, email delivery, or live end-to-end login.
- Before activation, exercise first Google signup, returning Google login, cancelled consent, replayed/expired state, wrong browser binding, existing-password-email collision, external-email confirmation, suspended accounts and administrator MFA in connected staging.
- The previous V1 audit remains applicable beyond these authentication changes. This delivery does not close its account-export privacy issue, sole-admin deletion issue or other marketplace launch gaps.

## Research and asset provenance

- [Dribbble authentication](https://dribbble.com/session/new): public page content inspected for concise entry, signup/login switching and consent placement. No private account was accessed.
- [shadcn login blocks](https://ui.shadcn.com/blocks/login) and [signup blocks](https://ui.shadcn.com/blocks/signup): public source/structure inspected for split-form composition. Implementation uses the existing local Checkbox and InputOTP primitives; no commercial component code was copied.
- UI/UX Pro Max guidance: accessible authentication, password-manager/paste support, inline validation and labelled controls. The existing Slate Atelier direction remains authoritative.
- [Google OpenID Connect](https://developers.google.com/identity/openid-connect/openid-connect): protocol, identity claims, server exchange and email authority.
- [Google branding guidelines](https://developers.google.com/identity/branding-guidelines): Google mark sourced from the official SVG icon. Only the outer button chrome was removed and the viewBox framed around the original mark; colors and paths were preserved.
- [Spring JWT documentation](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html): Nimbus decoder and claim validation.
