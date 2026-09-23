# getLancer — Quiet Craft

## Reference and deliberate adaptation
Primary reference: VoltAgent Awesome Design MD, `cursor.md` (MIT): warm cream canvas, warm ink, 400-weight editorial display type, restrained blue actions, hairline depth, generous section rhythm. Per the user's requested combination, `airbnb.md` supplies the secondary search/filter and marketplace card patterns; `figma.md` supplies hero presentation only (pastel preview framing, community-gallery composition). Canva's Growing Freelancer / Beige Black Minimal Professional portfolio is a supplementary presentation reference, not an imported template. Replace proprietary fonts with system UI and retain the exact getLancer logo and blue brand. No Cursor orange, competitor logos or copied product text. UI UX Pro Max informs accessible filtering, 44px targets, visible focus, reduced motion and meaningful empty states.

Figma inspiration: https://www.figma.com/templates/landing-page-design-inspiration/ ; the linked Community file 1222060007934600841 could not be fetched. Canva reference: https://www.canva.com/templates/EAG5xs7AU9U-growing-freelancer-website-in-beige-black-minimal-professional-style/ . Hero previews remain original getLancer illustrative project interfaces, never represented as imported Community files.

## Semantic tokens
Canvas #F7F7F4; surface #FFFFFF; subtle surface #EFEEE8; ink #26251E; muted ink #5A5852; border #E6E5E0; primary/focus #245BDD; hover #1948B8; primary foreground #FFFFFF; success #18734D; warning #8A5700; danger #B42318; disabled ink #687487. Normal text aims for 4.5:1 contrast. Muted never means translucent text.

## Typography and geometry
System UI, -apple-system, Segoe UI, sans-serif; no proprietary font or external font request. Body 16/24, labels 14/21, metadata 12/18. Display 60/65 weight 400 tracking -2px; mobile 38/42. Workspace title 32/38 weight 400 tracking -1px. Card titles 18/24 weight 600. Four-pixel spacing scale: 4,8,12,16,24,32,48,64. Radii: controls 8, cards 12, feature media 16, avatars full. Hairline borders define cards; shadows only for popovers and sticky chrome.

## Composition
Maximum content width 1512px, desktop inset 32px, mobile 18px. Persistent 76px masthead: exact logo, primary navigation or scroll-search in the same slot, overflow navigation, avatar menu. Search crossfades with navigation without changing header geometry. Workspace sections are a horizontal sticky tab bar above filters, with local sideways scrolling on small screens. Four card columns at >=1280px, two at 581–1279px, one <=580px. Four desktop KPI columns, two smaller-screen columns. Preserve the three-active-showcase rule. Data is never invented to fill a fourth card.

## Components and states
Avatar menu identifies account and role, provides workspace/profile/exit and explicitly labeled sample account switching in demo. Glass belongs to the header and sticky tabs; content cards are opaque. Cards: preview, title/status, concise summary, builder/actions. Active tabs have blue text and a quiet blue fill; hover is subtle, focus a 2px blue outline with offset. Inputs have persistent labels; invalid state uses text plus border, never color alone. Disabled controls retain readable labels. Existing dialogs, permission checks, validation, toast semantics, and empty states stay intact. Tables retain headers and horizontal scrolling only within their own region. Footer has real internal links under Explore, Workspace, and Trust headings; no fake social links or claims.

## Motion and accessibility
Opacity/transform only, 180–280ms, cubic-bezier(.22,1,.36,1). Search stays mounted and reverses naturally; hidden search is inert. Reduced motion removes transitions. No width/height animation, no arbitrary delay for typing, no result collapse. Keyboard tabs use horizontal arrow keys. All touch targets >=44px, focus stays visible below sticky chrome. On small screens only the tab strip scrolls horizontally, never the page. Hero uses native sticky cards with optional view-timeline scale enhancement. There is no autoplay or scroll interception. A visible Explore projects anchor bypasses the stack. On mobile, landscape previews form a horizontal snap strip; reduced motion disables stacking and animation.

## Guardrails
No decorative metric charts, invented activity, oversized gradient panels, nested frosted cards, or generic promotional claims. Keep auth artwork and logo unchanged. Preview remains clearly sample data; never imply real authentication or hosted backend connectivity.


## Marketplace refinements — 2026-09-23
Media occupies a 4:3 frame with 14px corners and a 2% hover scale; card information sits below without a nested panel. The title row includes a 44px save target. Reserve two summary lines, use at most two taxonomy chips, and align builder/action footers. Do not clip full software screenshots into square photographs. Preview mockups use a compact media layout so they remain legible at four columns.
Search and sorting occupy one compact row above results. In workspaces it stays below the sticky tab strip; filter chips wrap below it. A labeled Filters button opens an accessible dialog; on <=760px it is a bottom sheet with a scrollable body and persistent Apply/Clear actions. Changes in the sheet are staged until Apply. Results announce counts; no-match states distinguish an empty shortlist from restrictive filters. While loading, four skeletons reserve card geometry without an artificial delay. Demo-only account switching is a separate submenu. Show real pending work above metrics, with a direct next action; never fabricate work to fill a card.

## Quiet Craft hero
Two-column desktop composition: sticky editorial copy with search and an immediate collection anchor; three original project previews stack naturally in the right column as the page scrolls. The first card is visible without scrolling. Soft blue, sand and sage distinguish preview canvases only; UI chrome remains cream and blue. Native sticky is the baseline; supported browsers progressively enhance with scroll-driven transform animation. Links stay accessible by keyboard. Filters do not remove the hero: use stable featured projects separately from result items.
