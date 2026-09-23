# getLancer — Slate Atelier, refined

## Reference and deliberate adaptation
Primary reference: VoltAgent Awesome Design MD, `linear.app.md` (MIT). Adopt compact hierarchy, a four-pixel spacing rhythm, restrained borders, and product-first composition. Do not copy Linear's assets, copy, custom fonts, lavender, or dark palette. getLancer retains its exact supplied logo, blue identity, a light canvas, and glass only on persistent navigation. Four desktop columns intentionally replace the reference's three. UI UX Pro Max informs visible focus, 44px targets, responsive density, and interruptible motion; its generic marketplace palette is not used because it conflicts with the brand.

## Semantic tokens
Canvas #F7F8FA; surface #FFFFFF; subtle surface #F0F3F7; ink #17212F; muted ink #566478; border #DCE2E9; primary/focus #245BDD; hover #1948B8; primary foreground #FFFFFF; success #18734D; warning #8A5700; danger #B42318; disabled ink #687487. Normal text aims for 4.5:1 contrast. Muted never means translucent text.

## Typography and geometry
System UI, -apple-system, Segoe UI, sans-serif; no proprietary font or external font request. Body 16/24, labels 14/21, metadata 12/18. Display 56/60 weight 600 tracking -2px; mobile 38/42. Workspace title 32/38 weight 600 tracking -1px. Card titles 18/24 weight 600. Four-pixel spacing scale: 4,8,12,16,24,32,48,64. Radii: controls 8, cards 12, feature media 16, avatars full. Hairline borders define cards; shadows only for popovers and sticky chrome.

## Composition
Maximum content width 1512px, desktop inset 32px, mobile 18px. Persistent 76px masthead: exact logo, primary navigation or scroll-search in the same slot, overflow navigation, avatar menu. Search crossfades with navigation without changing header geometry. Workspace sections are a horizontal sticky tab bar above filters, with local sideways scrolling on small screens. Four card columns at >=1280px, two at 581–1279px, one <=580px. Four desktop KPI columns, two smaller-screen columns. Preserve the three-active-showcase rule. Data is never invented to fill a fourth card.

## Components and states
Avatar menu identifies account and role, provides workspace/profile/exit and explicitly labeled sample account switching in demo. Glass belongs to the header and sticky tabs; content cards are opaque. Cards: preview, title/status, concise summary, builder/actions. Active tabs have blue text and a quiet blue fill; hover is subtle, focus a 2px blue outline with offset. Inputs have persistent labels; invalid state uses text plus border, never color alone. Disabled controls retain readable labels. Existing dialogs, permission checks, validation, toast semantics, and empty states stay intact. Tables retain headers and horizontal scrolling only within their own region. Footer has real internal links under Explore, Workspace, and Trust headings; no fake social links or claims.

## Motion and accessibility
Opacity/transform only, 180–280ms, cubic-bezier(.22,1,.36,1). Search stays mounted and reverses naturally; hidden search is inert. Reduced motion removes transitions. No width/height animation, no arbitrary delay for typing, no result collapse. Keyboard tabs use horizontal arrow keys. All touch targets >=44px, focus stays visible below sticky chrome. On small screens only the tab strip scrolls horizontally, never the page. Product carousel pauses on focus/hover and honors reduced motion.

## Guardrails
No decorative metric charts, invented activity, oversized gradient panels, nested frosted cards, or generic promotional claims. Keep auth artwork and logo unchanged. Preview remains clearly sample data; never imply real authentication or hosted backend connectivity.
