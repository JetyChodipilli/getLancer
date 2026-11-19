# getLancer Search First

Search First is the approved public direction. The landing page opens with a centered project search, then three project previews from the current catalog. Components, college projects and learning follow below.

## Identity and references
Google’s search emphasis and Microsoft’s product previews informed the composition. Use the getLancer marks and catalog previews. Frosted surfaces belong on public demo cards and navigation. Small rounded rims and a directional highlight carry the treatment into primary actions, including form buttons.

## Semantic tokens
Canvas #f3f5ff, surface #ffffff, ink #17213d, muted ink #464f6d, and action #263ca3. The action highlight is #435cde, with visible focus at #354bc7. Keep established success, warning and danger colors. Use the existing [Joinery marks](docs/brand/JOINERY.md).

## Typography
Use the bundled Geist for the interface and hero, and getLancer Serif for editorial display. Original OFL licenses are bundled. Set body text at 16px with 1.55 line height and metadata at 13–14px. Keep the centered hero heading at 650 weight and scale it with the viewport. Use the existing Joinery mark and wordmark.

## Geometry
4px spacing scale: 4/8/12/16/24/32/48/64/96. Public max width 1440px with 24–48px gutters. Tasks max width 1320px. Form line length 480–640px. 6px radius for controls, 10px previews, 999px for the primary CTA and the selected account menu's trigger and Edit profile action. Border and surface hierarchy; dialogs and the selected account dropdown may have modest functional elevation.

## Components and states
The header stays keyboard reachable. Primary links remain available in the overflow while the search is docked. Search keeps the same query in the hero and dock. Submitting synchronises the query with the URL and browser history. On a narrow phone the dock sits immediately below the main nav row. Project cards show the preview, title, builder, and stack. Use an opaque surface when the browser lacks backdrop blur or the visitor requests reduced transparency.
Forms always show labels and help, field-specific errors and focusable summary, password affordances, retained input after failure. Mutations expose pending/disabled/success/failure states; show success only after server acknowledgement. Never auto-retry writes. Error messages explain next action and preserve support reference IDs. Toast/status cannot be color-only. Dialogs retain Radix focus management. Tables have a responsive scroll wrapper and visible heading.
Workspaces use a light canvas with four top-level destinations: Work, Clients, Trust, and Teams. All tools provides direct access to individual features. Searchable directory rows make room for more tools. Use compact headings and readable record panels. Preserve authorization checks and preview labels.

## Responsive and motion
Breakpoints 640/768/1024/1280. Phone navigation uses menu; content becomes one column; controls wrap; minimum target 44px. Desktop featured exhibition 2fr 1fr 1fr; tablet two columns; phone one. No fixed screen-sized widths or hidden important controls. Test at 375,768,1440 plus narrow320.
Use 160–220ms color/opacity/transform transitions, a small entrance and meaningful hover feedback; no parallax, continuous loops or content hidden waiting for JS. prefers-reduced-motion removes nonessential transforms/animations. Retain predictable browser back navigation.

## Accessibility and integrity
Normal text contrast >=4.5:1, visible focus, semantic landmarks, labelled icon buttons, honest live regions, no hover-only actions. User content stays escaped; existing safe URL/iframe contracts remain. Session/CSRF behavior and backend API payloads remain authoritative. Sample data is labelled; no invented testimonials, usage counts or claims. New assets are local and do not introduce third-party tracking.

Legacy route styles are retained for functional geometry. `paper.css` contains one intentional important backdrop reset on site-owned outer surface classes so older, more-specific glass selectors cannot reintroduce blur. Embedded preview documents remain isolated. The navigation bar has a stable ID to prevent CSS bundler grouped-selector specificity changing its flex layout.

## Selected account menu A

Confirmed 17 November 2026: compact 280px account dropdown, centered 40px initials avatar, name/email/role, rounded **Edit profile** action, workspace/settings rows and separated sign-out. The trigger uses a 32px avatar in a 44px target. The dropdown alone uses neutral translucent white with a subtle 16px blur and soft gray shadow; the canvas is pure white, with opaque fallbacks for reduced transparency. Editing opens the existing authenticated profile editor for members and administrators; the account role remains informational. Demo account switching remains confined to the labelled demo.
