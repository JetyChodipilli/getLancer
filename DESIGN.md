# getLancer — Studio

## Reference and adaptation
Primary: Awesome Design MD `linear.app.md` (MIT, VoltAgent collection), read in full. Adapt its measured typography, product-first composition, surface hierarchy, 12px panels and disciplined accent usage. Deliberate deviations: navy rather than near-black; getLancer blue rather than Linear lavender; light catalog and workspaces for longer browsing and form sessions; four desktop columns rather than three. Preserve the exact logo, original project previews and all workflow semantics.
UI UX Pro Max software-dashboard search supports restrained glass on navigation, explicit sample labels, motion controls, 44px targets and visible focus. Its generic palettes/fonts are recommendations, not copied wholesale.

## Tokens
Canvas #F5F7FA; surface #FFFFFF; subtle #EDF1F7; ink #142033; muted #526176; border #DBE2EC. Primary #245BDD; hover #1948B8; white primary text. Dark stage #111B2B; lifted dark #1C293D; stage text #F6F8FC; secondary stage text #BDC9DB. Success #17634D, warning #805300, danger #B42318, focus #245BDD. Body text minimum 4.5:1 intent. Disabled controls keep legible labels.

## Typography
Self-hosted Geist Sans (bundled WOFF2), fallback system-ui, -apple-system, Segoe UI, sans-serif. Display 600 at clamp(38px,4.4vw,68px), line-height 1.04, tracking -0.045em. Headings 32/38 at 600; cards 17/24 at 650; body 16/25 at 400; labels 14/20 at 600; captions 12/18. No external runtime font requests. Product miniatures retain their independent screenshot typography.

## Geometry
4px scale: 4,8,12,16,24,32,48,64,80. Max width 1512px; desktop gutters 32px, mobile 18px. Header 76px. Controls radius 10px, cards 14px, hero 24px. Shadows only on floating product previews and overlays. Four columns >=1280px, two 581–1279px, one <=580px; never invent projects to fill a row.

## Components
Dark bounded hero, eyebrow, clear display heading, one search field, compact category shortcuts. Tilted original demo windows stay inside their scene, pause on focus/hover and via explicit control. Light catalog with framed media, consistent card footer, restrained tags. Sticky header and horizontal workspace tabs stay visible; no permanent sidebar. Forms use visible labels, strong input borders, inline error text, readable disabled states. Popovers and dialogs use opaque white surfaces. Focus uses 3px blue outline and offset. Footer uses navy stage with real links and original logo on a light backing.

## Responsive, motion and accessibility
No document horizontal overflow. Navigation collapses using existing menu. Filter sheet retains staged Apply/Clear and focus management. 44px touch targets. Transitions 180–240ms opacity/transform/color; no animated layout dimensions. Existing reduced-motion setting disables movement. Empty/loading/error states remain explicit. Trust badges must describe evidence; demo data never implies real verification. Keep auth artwork, permission checks, confirmation flows, and backend behavior unchanged.
