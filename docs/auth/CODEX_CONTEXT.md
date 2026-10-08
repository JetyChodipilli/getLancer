# getLancer — authentication implementation context

Current repository phase: V4.6 reusable frontend contributions. The authentication guidance below remains scoped to its feature. Use [V4.6 implementation](../v46/IMPLEMENTATION.md) and [verification status](../v46/STATUS.md) for the current phase handoff.

## Task and visual reference

Implement the selected getLancer Icy Wind login and create-account experience, including a genuinely moving glass arrow, transitions, hover feedback, and Google/GitHub authentication entry points. Deliver a working responsive implementation in the existing project.

First inspect `icy-wind-auth-reference.png`, `getlancer-original-logo.png`, the repository instructions, and the existing application. The reference image contains two alternative routes stacked for comparison: LOGIN above and CREATE ACCOUNT below. Build them as separate screens; do not render both forms together on one page. The reference is a static design, not an existing working application. Do not reproduce its outer presentation-board title, resolution labels, or “Static motion concept” text in the product.

Use the **UI/UX Pro Max** and **Kiranism shadcn Dashboard** skills. Apply their form anatomy, spacing, theme, interaction, and accessibility guidance. Follow repository-compatible conventions; the Kiranism skill does not require replacing the application's stack or authentication provider.

Scope is authentication UI, supporting motion assets, and integration with available authentication services. The full marketplace, dashboard, subscriptions, payments, and project editor are outside this implementation task.

## Product context

The company is **getLancer**, inspired by Java's `get()` method. Its core idea is proof before pitch: developers showcase working software, clients explore demos and hire the people who built them. Brand message: **Build. Show. Get hired.**

Public visitors can browse projects and demos without an account. Account creation starts free. Builder profile completion, email verification, and approval are separate steps; creating an account must not imply approval. Approved builders initially have three active showcase slots. Subscription plans may later increase capacity and add tools. Authentication must not require choosing a subscription, entering payment information, or completing a long profile.

The supplied requirements recommend Next.js/React + TypeScript, Spring Boot, Spring Security/OAuth2, and PostgreSQL. Inspect what is actually implemented before choosing libraries or changing routes. Preserve an existing compatible stack. Reuse existing form, session, theme, and animation utilities. Do not introduce Clerk or another replacement auth system merely because a dashboard example uses it. Do not add multiple animation libraries for this screen.

## Visual direction

Match the selected Icy Wind composition closely while improving execution:

- Desktop: approximately 56% brand/art area and 44% form area, with the form comfortably readable at a maximum content width of roughly 400–440 CSS pixels. Adapt these proportions to the available viewport rather than squeezing the form.
- Pearl background `#F8FAFC`, pale ice `#EAF4FF`, white form/input surfaces `#FFFFFF`, cobalt primary `#2563EB`, dark ink `#17212B`, muted text around `#526174`, and subtle cool borders around `#D4DFEB`. These are design targets; validate the actual contrast of every text and control pairing.
- Typography: Plus Jakarta Sans for headings and Inter for controls/body, or the repository's closest established pairing. Load fonts efficiently. Use roughly 30–34px desktop form headings, 16px body/input text, and 14px labels. Retain readable legal text.
- Keep the original circular `get();` crest **above** the getLancer wordmark, as one compact stacked brand lockup in the art area. Preserve exact capitalization. Use the supplied source asset and a correctly prepared transparent derivative if necessary. Do not recreate the logo as plain text or omit the circle.
- The form panel has a near-white surface, a delicate border, about 20px corner radius, and a restrained cool shadow. Inputs have opaque backgrounds, approximately 48px height, 10px radius, and consistent padding. Buttons follow the same vertical rhythm.
- Apply minimal glassmorphism to the arrow and limited outer decorative surfaces. Keep input text and labels on calm, readable surfaces. Do not animate backdrop blur.
- Preserve the large upward glass ribbon arrow, the blue/white identity, and the headline “Build. Show. Get hired.” Keep small decorative text subordinate and hide it when space is limited.
- Keep “Browse projects” visible. Connect an existing theme toggle to the application's real theme system. If there is no dark theme yet, report that gap rather than leaving a fake toggle.

## Login screen

Preserve this hierarchy:

1. “Welcome back”
2. “Your next opportunity starts here.”
3. Full-width “Continue with Google” button with recognizable Google icon.
4. Full-width “Continue with GitHub” button with recognizable GitHub icon.
5. Divider: “or continue with email”.
6. Visible “Email address” label and email input; placeholder `you@example.com`.
7. Visible “Password” label, masked input, and accessible show/hide button.
8. Unchecked “Keep me signed in” and “Forgot password?” link, on one row when space permits.
9. Primary “Log in” button.
10. “New to getLancer? Create account” with working navigation.

Only retain “Keep me signed in” if the authentication service supports the corresponding session behavior. Do not imply persistence by storing credentials in the browser.

## Create-account screen

Preserve this hierarchy:

1. “Create your account”
2. “Show your work. Find your people.”
3. The same Google and GitHub buttons, in the same order.
4. Divider: “or continue with email”.
5. Full name, email address, and password fields, with persistent labels.
6. Password helper: “Use a long, unique password.” Match actual backend password requirements; do not invent an arbitrary composition policy.
7. Primary “Create account” button.
8. “By creating an account, you agree to the Terms and Privacy Policy.” Both destinations must be real routes.
9. “Already a member? Log in”.

Do not add phone, confirm-password, card/payment fields, a forced role selector, or preselected marketing consent. After actual registration succeeds, show the appropriate email-verification/next-step state from the authentication service.

## Moving glass arrow

The arrow must actually animate. Moving the entire reference screenshot is not an acceptable implementation. Build the UI with semantic components and keep the arrow as a separate visual asset/layer. Prefer lightweight SVG masks, gradients, a transparent artwork layer, and compositor-friendly transforms. Use WebGL only if an existing project setup and a demonstrated visual need justify it.

Implement these coordinated movements:

| Effect | Specification |
| --- | --- |
| Idle float | Seamless 6.5-second cycle; vertical movement up to 8px and rotation up to 0.6 degrees. Use gentle sine-like easing. Maintain the arrow's silhouette and anchor. |
| Traveling highlight | A narrow light streak follows the ribbon curve toward the tip. Roughly 4.5-second cycle, with a soft fade in/out and a quiet interval before repetition. Use an SVG path/mask or equivalent so it follows the ribbon instead of sweeping across the whole page. |
| Tip glint | A subtle 600–800ms brightening as the traveling highlight reaches the tip. No flashing or sudden bloom. |
| Pointer response | On fine-pointer desktop devices only, move the art layer at most ±6px and rotate at most ±1 degree relative to the art panel's center. Smooth the response and return to neutral over about 250ms on pointer exit. |
| Pause/resume | A real, keyboard-accessible “Pause animation”/“Play animation” button in the art area. Update its icon and accessible name to match its state. |

Keep logo, headlines, form, and all controls spatially stable. Avoid constant particles, large zooms, bouncy motion, and pointer-following form cards. If highlights require an animated stroke offset, keep the SVG small and profile its cost; do not animate layout dimensions or large filters.

Pause decorative motion while a form field has focus, while the art is offscreen, and when the tab is hidden. Resume only when those conditions clear AND the user has not manually paused it. Keep manual pause preference across login/signup navigation in the current session. Clean up every observer, frame callback, and event listener on unmount.

For `prefers-reduced-motion: reduce`, render a beautiful static arrow, disable looping motion and parallax, and remove spatial entry/hover effects. Keep essential state feedback visible. Do not automatically restart motion when the preference changes back if the user explicitly paused it.

## Transitions and dynamic hover effects

| Element/state | Motion target |
| --- | --- |
| Initial screen entrance | Form opacity 0→1 and y 12px→0 over about 420ms; ease `cubic-bezier(0.22, 1, 0.36, 1)`. Optional 35ms stagger between a few major groups, with the entire entrance complete within 650ms. Start after the initial layout is ready; render immediately without spatial motion for reduced-motion users. |
| Login/signup navigation | Short 150ms exit and 220ms entry fade with no more than 6px movement. Keep the brand/art shell stable when the router allows it. Prevent height jumps and unexpected scroll changes. Move focus to the new heading after navigation. Never retain passwords between forms. |
| Primary button hover | At most 1px upward movement, a slightly deeper cobalt fill, and a small shadow change over 150–180ms. Keep width, text, and hit target stable. |
| Primary button press | Brief scale around 0.985 for about 90ms, returning smoothly on release. Disable scale for reduced motion. |
| OAuth button hover | Slight cool background tint and border emphasis over 150ms. No rotation, oversized glow, or brand-icon recoloring. |
| Input hover | Subtle border emphasis over roughly 120ms. |
| Input focus | Clear cobalt focus ring and border. Use a quick 120–150ms transition; preserve an immediate, easily visible keyboard focus indicator. Labels remain visible. |
| Text links | Underline/color feedback over 150ms. “Browse projects” may move only its arrow icon by 2px on hover. Provide equally clear focus feedback. |
| Form panel hover | Very subtle shadow/border adjustment over 180ms. No card tilt or movement beneath the pointer while typing. |
| Validation | Associated error text appears beside/below the relevant field with a short opacity transition. Avoid shaking fields. Reserve enough space to prevent buttons jumping unexpectedly. |
| Submitting | Button retains its width and shows a spinner plus “Logging in…” or “Creating account…”. Disable duplicate submission, preserve user input on failure, and announce the result. |
| Theme switch | If supported, transition surface and text colors over about 180ms with no white flash. Avoid global `transition: all`. |

Hover enhancements apply only on hover-capable pointers. Touch users must receive clear press/focus feedback without sticky hover states. Form entry animations should not replay during ordinary typing, validation, or React rerenders.

## Form and authentication behavior

- Use semantic forms, persistent labels, sensible autocomplete values (`name`, `email`, `current-password`, `new-password`), appropriate input types, and logical keyboard order.
- Allow password managers and paste. The password visibility button is `type="button"`, has an accessible name/state, and must not submit the form.
- Validate with the existing form system and align client rules with the server. Do not show errors for untouched fields immediately. Revalidate corrected fields sensibly. Associate errors using the appropriate accessible attributes and focus the first invalid field on submit.
- Distinguish invalid form input, server failure, expired sessions, provider cancellation, and network failure. Use neutral authentication/reset messages where account existence could otherwise be revealed.
- Both OAuth buttons must initiate their actual configured provider flows. Reuse the Spring Security or existing auth backend flow and its state/nonce/callback protections. Provider configuration and secrets stay server-side.
- If an endpoint or provider configuration is missing, make that limitation explicit in the delivery notes and handle it honestly in the UI. Do not simulate OAuth success, invent API routes, or claim a connection is live.
- Respect the existing verified session and redirect behavior. “Browse projects” remains public. Do not infer identity from local flags or hardcoded users.
- Keep passwords and OAuth tokens out of logs, analytics, committed files, and ad hoc localStorage/sessionStorage persistence. Follow the project's established secure session design.

## Responsive behavior and accessibility

Target a usable layout at 360/375px, 768px, 1024px, 1440px, and wide desktop sizes, including a short laptop viewport. Desktop may use the split art/form composition. On tablet, reduce decorative density. On mobile, use a compact stacked logo followed by a single-column form; hide or substantially simplify the large art panel.

Let pages scroll naturally when necessary. Signup fields, legal links, and the primary CTA must remain reachable with the on-screen keyboard open and at enlarged text/zoom settings. Keep input text at least 16px and interactive targets approximately 44px or larger. Check keyboard navigation, focus visibility, contrast, screen-reader labels, reduced motion, and pause/resume behavior.

## Implementation and review expectations

Use reusable auth layout, provider button, form field, password field, and animated-arrow components. Use the existing motion library if present; CSS/SVG may be sufficient. Avoid a heavy animation dependency for simple hover effects. Preserve SVG aspect ratio and reserve artwork dimensions to prevent layout shifts.

Validate both routes in light mode and any supported existing dark mode. Check long validation messages, provider cancellation/failure, missing backend configuration, small/short screens, keyboard-only use, reduced motion, manual pause persistence, and hidden-tab behavior. Run the repository's relevant build/type/lint checks and existing integration tests. Add focused tests only for meaningful new behavior such as validation/navigation, pause-state logic, and auth failure handling.

Deliver the implementation, a concise description of changed files, desktop/mobile screenshots, and a short motion recording if the available tools support it. Report which authentication paths are verified and which need environment configuration. Do not deploy or publish as part of this task.

## Research provenance

The design direction was informed by inspection of Dribbble's compact form with an artwork panel, Framer's focused account entry, and shadcn's form layouts. These are sources of principles, not layouts or artwork to copy:

- https://dribbble.com/session/new
- https://dribbble.com/signup
- https://www.framer.com/help/articles/creating-a-framer-account/
- https://ui.shadcn.com/blocks/login

The supplied getLancer requirements, selected reference image, and this brief define product behavior. Framer's passwordless implementation is not a requirement to change getLancer's email/password plus Google/GitHub approach.


## V1 follow-up — 9 September 2026
See `docs/V1_RELEASE_CHECKPOINT.md` and `ops/README.md`. The pending-work patch adds migrations V8/V9, signed proof uploads and thumbnails, moderation/appeal/privacy operations, API aliases, proposal reporting, protected analytics attribution, schema/proxy configuration and staging connection instructions. Preserve the Icy Wind login fixes. Supabase/SMTP/OAuth/backend credentials still require connection and real acceptance testing; never claim the disconnected preview is live authentication.


## 14 September 2026 continuation
User selected local PostgreSQL, database getLancer, username postgres. Private .env contains the current requested password; never commit or print it. Supabase was explicitly declined. Optional profile data and review identity choice were completed with migration V10. Local setup/doctor/frontend/backend commands and a guarded dedicated database-test path were added; see ops/LOCAL_POSTGRESQL.md and docs/V1_RELEASE_CHECKPOINT.md. Do not claim the user's database has been seeded without an actual successful connection and committed insertion.
