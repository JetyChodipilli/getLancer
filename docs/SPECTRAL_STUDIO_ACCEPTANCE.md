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
