# Selected direction: Slate Atelier

The user's selected blue glassmorphism concept takes precedence over the generic generated recommendation in getlancer/MASTER.md.

- Discovery is the primary activity: product spotlight hero with prominent search, followed by filters and three-column project grid on desktop, one column on mobile.
- Primary: #2563EB with white text. Text: #142238. Background: #F3F8FF. Secondary text: #51647B. No orange calls to action. Deep navy welcome panels provide contrast; ice-blue glass is reserved for cards and navigation.
- Glass belongs on navigation and panels: translucent white/ice-blue surfaces at approximately 56–64% opacity, bright fine borders, blue ambient gradients and 18–24px backdrop blur. Inputs and dense filter menus retain stronger backgrounds for legibility. Provide opaque fallbacks where blur is unsupported.
- Use the existing system sans-serif stack; no required external font requests.
- One account supports both browsing and building. The workspace separates Showcases, Received inquiries, and My requests. Do not require a role switch for Explore.
- Visible V1 inventory is software projects. Templates, components, hero sections, purchases, paid memberships, and hosting are future releases.
- Use truthful approval and availability labels; active showcase capacity is independent of capacity for client work.
- Use installed Radix/shadcn primitives for focus management, tabs, dialogs, select and checkbox controls.

## September 7 refinement
- Dribbble-inspired image-led discovery and clear search/category browsing, without copying its pink branding, checkout, or marketplace scope. Reference: https://dribbble.com/
- Product spotlight rotates at six-second intervals with pause, previous/next controls. Pause on focus/hover and document hiding; honor reduced motion. Search results hide the carousel when filters are active.
- Workspace uses a navy welcome panel, three truthful outcome summaries, and vertical Radix tabs. Mobile tabs remain a compact vertical reading-order grid; no role switch is added.
- Use the exact supplied background-removed logo at public/brand/getlancer-transparent.png, including its original stacked symbol, lettering and tagline. The header uses a CSS viewport that trims only the surrounding transparent canvas. Never reconstruct its lettering with HTML or separate the mark from its wordmark.

## Persistent navigation refinement
- Keep the header sticky at the top: an 80px desktop bar, compact original logo, account links and primary navigation. Scrolling never hides navigation.
- After the hero search passes the header, show a compact search field and Filters button inline. Both search fields share the same query; the popover uses the existing URL-backed category, technology, project type, availability, demo and sort state.
- Keep focused search controls and open filters mounted when the user scrolls back. Use reduced-motion-aware transitions and Radix focus/escape behavior.
- On intermediate widths, group navigation links into a menu. On small screens, place the persistent search/filter row beneath the main bar. Offset content focus targets for the sticky controls without changing document flow during the handoff.
- Preserve the source logo pixels, colors, layout and tagline; only its displayed size changes.

## Authentication refinement
- Use a spacious split surface with public project cards on the left and a 412px form on the right. Use ice-blue glass around the product wall and a stronger white surface for inputs. Mobile shows the form first without the decorative discovery panel.
- Retain the exact logo in a compact header with Explore projects. Keep signup/login switching, recovery, consent, password visibility, inline validation and restrained motion consistent.
- Google appears above email/password. Show honest provider availability, and show administrator TOTP only after successful administrator password verification. One account remains sufficient to browse and build.
- Research, protocol decisions and activation steps are recorded in docs/AUTHENTICATION.md.
