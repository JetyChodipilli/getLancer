# Spectral Studio frontend acceptance — 1 October 2026

## Scope and design sources
The selected Spectral Studio reference replaces the previous Slate Atelier/Icy Wind visual direction. Pearl canvas, silver glass chrome, black headings and contrast-safe orange actions apply to discovery, public details, authentication, business hiring, personal/trust/team workspaces, delivery and source-template purchases. Existing shadcn primitives remain in place. Awesome Design MD Apple hierarchy and the Shadcn Dashboard Free/Template conventions guide the original implementation; no template mock API or router was imported. UI/UX Pro Max informed material, interaction and accessibility decisions, with off-topic automated palette suggestions rejected. DESIGN.md and design-system/getlancer/MASTER.md contain the active rules; older dated audits describe historical releases.

The transparent glass ribbon is original image-generated artwork, converted to a 273,570-byte WebP and served locally. Its optical refraction is pre-rendered. Actual project cards, builder identity, profile links, forms and actions are React UI using real records or explicitly disclosed frontend preview records. No generated screenshot is used as an interactive interface. No new runtime dependency, remote font, analytics provider or external design asset was added.

## Security repairs
All untrusted public evidence/profile/repository/deliverable/video links share an HTTPS-only validator that rejects executable schemes, relative hosts, URL credentials, control characters and excessive length. New-tab links enforce noopener, noreferrer and no-referrer. Video embeds remain opt-in. Loom's exact embed origin is added only to frame-src, fixing a previously blocked supported video host without enabling its scripts or network access in the parent page.

HTML, API and image-optimizer responses now share the existing CSP, nosniff, referrer, permissions and HTTPS HSTS protections. Provider checkout hosts remain connected-only. Existing same-origin proxy checks, HttpOnly authentication cookies, server-side permissions, private cache rules and redirect allowlist remain intact. No server secret or API credential is passed to the frontend. The asset scanner is part of CI and reports only paths/types of findings, never values.

## Validation and release gates
- Production build and TypeScript checks pass.
- 77 Node tests pass, including executable URL rejection and response/header preservation.
- 42 responsive browser cases pass across desktop, phone and tablet. Expanded checks cover real project links, search/filter navigation, public details, auth forms, narrow 320px navigation, the scroll-search dock and reduced motion. Existing cases cover hiring-manager consent, private requests, concierge, delivery/payment state, source licenses, dialogs and keyboard focus restoration.
- Source and public-build scans find no private credential signatures or public source maps. The built scan checks 111 public text assets and compares supplied server-secret values without printing them.
- Scoped lint for the shared/new components and security helpers has no errors; two informational img warnings refer to the deliberately pre-optimized local WebP. Repository-wide lint has pre-existing debt in older files and is not claimed clean.
- GitHub CI must pass Java/PostgreSQL tests, frontend checks, responsive Chromium cases, connected browser cases and real service/restore journeys before main is merged.

## Boundaries
The Java/PostgreSQL backend is unchanged. Connected mode has no demo fallback; preview routes stay unavailable when configured with a backend. Cloud publication is the demo frontend only, with unchanged audience. Live-provider configuration, owner acceptance and connected production rollout remain pending under the existing release boundaries. This frontend check is not a claim of a completed production penetration test or external-provider acceptance.

## Hero reference correction — 2 October 2026
The hero now follows the selected reference composition: full-width architectural studio artwork, compact glass capsule navigation, centered headline, category pills above the angled wide previews, a dark portrait preview in the middle, and glass builder/inquiry panels below. The new original background is a locally served 1536 × 1024 WebP (120,910 bytes) with no text or interface. Cards retain genuine catalogue identity, routes and connected approved records. No photographic builder identity or public engagement count is invented.

Motion uses a shared decelerating curve, staggered entrance, restrained floating windows, smooth pointer depth and hover feedback. Animation pauses offscreen, in hidden tabs and during keyboard/form focus. Pointer depth is limited to fine hover devices. Reduced motion removes hero animations, transitions and smooth scrolling; reduced transparency and forced-color fallbacks remain available. The search dock now handles jumping past a below-viewport search bar on a tall mobile layout.

The inquiry card passes an email and idea to the existing review form in tab-scoped session storage, with a 30-minute expiry and removal on consumption. Personal details are absent from URLs. Server-rendered product selection uses the existing Java catalogue adapter in connected mode. Demo inquiry submission is explicitly disabled; required budget, timeline, name, backend validation and email confirmation remain in the real flow. The business project-request link is preserved.

Local validation: production build and TypeScript pass; 77 Node tests and 45 responsive browser cases pass. The final scroll-dock adjustment is rechecked at 320px in all three browser projects. Hero motion, offscreen pausing and reduced-motion behavior are checked in the built application. Public asset scans find no credentials or source maps. New motion/inquiry helpers and the modified dock pass scoped lint. GitHub's existing Java/PostgreSQL, connected browser and service/restore CI gates remain required before merge. This change does not complete connected production or live-provider owner acceptance.
