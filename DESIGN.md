# getLancer — Open Marketplace

## Source and visual target
Primary system: Awesome Design MD `airbnb.md`, read in full; MIT VoltAgent collection. Implement the user-approved white/cobalt reference (530f504b-3193-4dab-a56c-a7a02beb7488.png). Adapt its marketplace spacing, image-led cards and segmented search, not Airbnb identity. Use the original getLancer logo without redrawing it. Generated reference miniature screenshots are interpreted as original HTML illustrations, not represented as real customer products.

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
