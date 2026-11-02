# getLancer — Paper & Pixel / Joinery

User-approved direction: P4 Paper & Pixel, 28 October 2026. The public homepage is a digital exhibition of working software. Dark projects lead into light components, college work and learning. Product screens retain quiet task-focused structure.

## Identity and references
Framer supplies the dark exhibition hierarchy; Wired supplies editorial light chapters. These are compositional references only. Use original getLancer marks, product copy and assets. Deliberate deviations: no neon, gradients, glass, floating blobs or proprietary typography. The approved template wins over any random style draw.

## Semantic tokens
Selected B / Joinery supplies the identity and graphite palette. Canvas #f5f8fa; surface #ffffff; raised #edf2f5; ink/dark #233746; muted #536572; decorative line #d3dce2; control boundary #7d8b94. Dark raised #304959; inverse ink #ffffff; dark muted #c1d0da; decorative dark line #5a6e7b. Focus #2d5d85 on light and #bad8f1 on dark. Success #166b45 / #edf8f0; warning #805710 / #fff8e8; danger #a52b34 / #fff1f2. Disabled controls remain legible; no opacity reduction on readable status text. Exact asset rules/evidence: [Joinery](docs/brand/JOINERY.md).

## Typography
Self-hosted getLancer Sans (renamed IBM Plex Sans Latin, normal variable 100-700, width 100) for UI and hero; getLancer Serif (renamed IBM Plex Serif Latin, real 400) for editorial display. Original OFL copyright/licenses bundled. Two families maximum; no synthetic serif bold. Body 16px/1.55. Metadata 13-14px/1.5; labels 14px/600. Headline clamp(2.5rem,5vw,4.75rem), 500, line 1.05, tracking -.04em. Editorial chapter 36-48px serif/400. Product headings 28-40px. No uppercased paragraphs. Joinery outlined horizontal logo: header 168px, minimum 132px; symbol minimum 24px with tested favicon exception at 16px.

## Geometry
4px spacing scale: 4/8/12/16/24/32/48/64/96. Public max width 1440px with 24–48px gutters. Tasks max width 1320px. Form line length 480–640px. 6px radius for controls, 10px previews, 999px primary CTA only. Border and surface hierarchy, no decorative shadows; dialogs may have modest functional elevation.

## Components and states
Header is solid, compact, keyboard reachable; logged-out primary links Projects, Components, College projects, Learning. Overflow retains every existing destination. Search is labelled, submit-driven, URL-synchronised, clearable; filters maintain backend semantics and browser history. Project cards show actual preview, title, builder, stack and accessible save state. Featured projects have uneven editorial scale; normal results are a readable grid without layout thrashing.
Forms always show labels and help, field-specific errors and focusable summary, password affordances, retained input after failure. Mutations expose pending/disabled/success/failure states; show success only after server acknowledgement. Never auto-retry writes. Error messages explain next action and preserve support reference IDs. Toast/status cannot be color-only. Dialogs retain Radix focus management. Tables have a responsive scroll wrapper and visible heading.
Workspaces: white canvas, charcoal navigation, useful compact headings, distinct toolbar, readable panels, no glass or nested decorative cards. Preserve all tabs, operations, authorization checks and preview labels.

## Responsive and motion
Breakpoints 640/768/1024/1280. Phone navigation uses menu; content becomes one column; controls wrap; minimum target 44px. Desktop featured exhibition 2fr 1fr 1fr; tablet two columns; phone one. No fixed screen-sized widths or hidden important controls. Test at 375,768,1440 plus narrow320.
Use 160–220ms color/opacity/transform transitions, a small entrance and meaningful hover feedback; no parallax, continuous loops or content hidden waiting for JS. prefers-reduced-motion removes nonessential transforms/animations. Retain predictable browser back navigation.

## Accessibility and integrity
Normal text contrast >=4.5:1, visible focus, semantic landmarks, labelled icon buttons, honest live regions, no hover-only actions. User content stays escaped; existing safe URL/iframe contracts remain. Session/CSRF behavior and backend API payloads remain authoritative. Sample data is labelled; no invented testimonials, usage counts or claims. New assets are local and do not introduce third-party tracking.

Legacy route styles are retained for functional geometry. `paper.css` contains one intentional important backdrop reset on site-owned outer surface classes so older, more-specific glass selectors cannot reintroduce blur. Embedded preview documents remain isolated. The navigation bar has a stable ID to prevent CSS bundler grouped-selector specificity changing its flex layout.
