# getLancer — Open Marketplace

## Source and visual target
Primary system: Awesome Design MD `cursor.md`, read in full; MIT VoltAgent collection. Use its restrained hierarchy, hairline surfaces and deliberate spacing with getLancer's own white/cobalt identity and self-hosted Inter. Deliberate deviations: white rather than cream canvas, cobalt rather than orange actions, and 600-weight workspace headings for scanability. Preserve the approved marketplace gallery and original logo. Airbnb remains the secondary influence for discovery search and image-led marketplace spacing. Original miniature interfaces are illustrations, never represented as customer products.

## Workspace redesign — 30 September 2026
The UI/UX Pro Max component guidance and Shadcn Dashboard Free/Template layout conventions inform a single workspace system. Adapt the existing Next-compatible React components; retain the real Java API and the isolated frontend preview adapters. Do not import template mock APIs, route engines or configuration. The local template asset snapshot is unavailable; its upstream layout documentation is used as a reference, with original implementation using the existing shadcn primitives.

### Page anatomy
Desktop: a 216px workspace rail and a flexible content area capped at 1180px. The rail links Personal workspace, Trust & reliability and Teams & studios. Public discovery remains in the global header. A compact page header contains breadcrumb, one H1, description and one main action. Preview disclosure sits below it, with actor/reset controls in an expandable panel. Related sections use shadcn Tabs with keyboard navigation. Metrics, section heading and records each occupy a distinct row. Never repeat the page title in an oversized second hero.

At 1000px the rail becomes a horizontal navigation strip. At 640px the strip and section tabs scroll within their own bounds; forms and split sections become one column. Desktop gutters 32px, tablet 24px, mobile 16px. Containers, grids, forms and flex children use min-width:0. No horizontal document scrolling.

### Components
Metric cards: 16–20px padding, label 13/20, value 28/34 at 600, explanatory note 12/18, one muted icon. Values must come from the current API response or explicitly disclosed preview state. No invented growth deltas, charts or online indicators. Overview uses a main record list plus contextual aside, not a wall of interchangeable cards.
Record lists: clear header, 16px row padding, 1px separators, consistent avatar/thumbnail, text block, status badge and trailing action. Empty states explain the next useful action. Project library cards separate proof media, title/status, description and actions; never stack arbitrary panels inside panels.
Forms: grouped fieldsets with a short heading and helper copy, label above control, 44px controls, 16px gaps. Short related fields share two columns; descriptions and primary actions span the full width. Dialogs use a bounded scrolling body, sticky action where useful, 24px desktop/18px mobile padding. Progressive disclosure hides optional management forms until requested. Keep validation, submission feedback, disabled states and authorization intact.
Status colors: success #17634D on #EAF6EF, warning #805300 on #FFF6E6, danger #B42318 on #FFF1F0, neutral #526174 on #F2F5F9. Status must include readable text. Focus is 3px cobalt with 3px offset. Border #E1E6EE; controls #D5DDE8; canvas #F6F8FB; cards #FFFFFF; ink #18212B; muted #526174; primary #175CD3.

### Geometry and motion
Spacing 4/8/12/16/20/24/32/48px. Controls 8px radius, cards 12px, dialogs 16px. Shadows only for overlays. Heading 32/40, section title 20/28, body 15/24, form labels 14/20. Transitions 160ms for color/border; no floating dashboard panels or automatic chart motion. Respect reduced-motion settings.

### Verification
Check personal, trust, team and public profile screens; open long forms and switch roles/tabs. Check keyboard focus, empty/error/loading states, desktop/tablet/mobile density and no document overflow. Run typecheck, production build and existing frontend/connected-mode tests before release. Configuring BACKEND_URL must continue to disable every preview route and sample fallback.

### Business hiring — V2.5
Add Business hiring to the same workspace rail. The page has one primary action, a business switcher, four metrics derived from workspace data and four sections: Requests, Saved talent, Hiring team and Activity. Requests use a compact brief chooser beside the selected private brief; evidence and shortlist follow in reading order. At 900px the chooser moves above the brief; at 640px all fields and request cards become one column. Candidate cards show actual evidence reasons and availability wording, with distinct shortlist and named-list actions. No synthetic score, growth chart or implied hiring outcome.

Private-brief forms use labelled 44px controls, bounded scrolling dialogs and native validation. Owners see settings and access management; hiring managers see collaboration actions. Closed briefs are read-only. Unavailable saved candidates display a withdrawal notice and retain their removal action. Concierge is a separate opted-in administrator section, with a required client-visible recommendation reason. Browser acceptance checks device viewport width, rather than an overflow-expanded `window.innerWidth`. Mobile header rules cover both discovery states so CSS selector minification cannot restore desktop navigation on phones.

## Semantic tokens
Canvas/surface #FFFFFF; subtle #F6F8FB; ink #18212B; display #101828; muted #657080; border #E1E6EE; primary #175CD3; hover #124AA9; on-primary #FFFFFF; focus #175CD3; success #17634D; warning #805300; danger #B42318. Decorative miniature plates: mint #E0F1EB, peach #FAE9DF, lavender #E4E5FA, sage #DEEBDE. Opaque readable controls; shadows only on search, overlays and hero miniatures. Body text targets 4.5:1 contrast.

## Typography
Inter variable, self-hosted, OFL license; fallback system-ui. Display 600–700, clamp(38px,4.5vw,64px), 1.06 line-height, -0.045em tracking. H2 30/38 at 650; cards 18/25 at 650; body 16/25 at 400; UI 14/20 at 500; captions 12/18. Miniature screenshots intentionally use smaller illustrative text and never hold required user instructions.

## Layout
Content max 1440px, 48px desktop / 20px mobile gutters. Header 76px, sticky. Hero 460px desktop: 44% copy / 56% compact overlapping two-card scene. Hero search is below the scene, 84px tall with query, category and technology segments. Filters and sort beside a horizontally scrollable category strip. Four catalog columns >=1100px; two 641–1099px; one <=640px. Card media 4:3, metadata directly on white canvas, save button over media. No permanent sidebar. Footer is light with compact genuine navigation.

## Geometry and states
4px spacing scale: 4,8,12,16,24,32,48,64. Buttons/fields 8–10px radius, media 12px, search 18px. Controls minimum 44px. Hairline borders; visible 3px focus ring. Selected category blue underline; pressed save button blue. Filters preserve Apply/Clear workflow and keyboard focus. Account menus, forms and workspaces share tokens and type. Loading, no-results and connection states remain explicit. Never fabricate account or trust data.

## Motion and accessibility
Remove the visible hero pause/play UI and perpetual cycling. Two hero cards enter once within 700ms and settle at gentle opposing angles. Reduced-motion disables entrance transforms. Search dock crossfades within the fixed header grid; no animated width or height. Hover transitions 180–240ms. Keyboard reachable controls, proper labels, semantic navigation and no horizontal document overflow. Keep sample-data disclosure and access to the UAT workspace below discovery, outside primary navigation.

## Gallery update — mixed-format demos
Reference: https://godly.design/ (observed 28 September 2026). Equal-width masonry columns, variable-height media, compact metadata. Gallery only: existing brand, hero, search and navigation stay unchanged. Four columns on desktop, two on tablet and one on mobile. Source screenshots render at their native aspect ratio without cropping; portrait source images remain vertical. Illustrative Learnspace demonstrates a mobile interface. Measured masonry row spans preserve DOM/source order and adapt after images load. CSS columns provide the no-JavaScript fallback. Filtering/sorting retain their existing result order. No third-party demo assets copied.

### Staggered placement correction
Four independent desktop columns begin at offsets 0/64/24/96px, then each next card occupies the shortest column. Cards use measured content height rather than equal rows. The unfiltered illustrative collection places its mobile preview second so the format mix is visible immediately. Real results and explicit filters/sorts retain source order. ResizeObserver handles width changes and late image loads; mobile becomes one unshifted column.


## V3 delivery and payments

The V3 workspace extends Slate Atelier with Quiet Craft principles from the Cursor reference: calm editorial hierarchy, compact readable rows, restrained hairlines and consistent four-pixel spacing. Keep the existing white canvas, blue actions and self-hosted Inter; the reference does not replace brand tokens. Reuse the shared rail and shadcn/Radix primitives.

Use one engagement list and one focused detail panel. Show agreement consent, milestone progression, payable amount and provider-confirmed payment status in the order a party needs them. Avoid decorative charts and duplicated status cards. On phones, list and detail stack with natural scrolling; tables that need horizontal scroll have labelled regions. Errors and pending actions use text with icon cues, never colour alone. Preview simulation and real payment state remain visibly distinct. Razorpay opens only from an enabled accepted milestone's explicit Pay action. Do not show a wallet, escrow balance or settled badge from payment capture alone.
