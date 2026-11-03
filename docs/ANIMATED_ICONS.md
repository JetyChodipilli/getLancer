# Animated navigation icons

getLancer uses a small local selection from both requested animated icon libraries.
The product keeps Lucide line shapes, 18px navigation symbols, fixed labels and
44px control targets. Interaction plays once; there is no idle loop or route-load
autoplay. Reduced-motion preferences keep the symbols in their complete static
state and stop any animation already playing.

## Source provenance

| Library | Pinned source commit | License |
| --- | --- | --- |
| [lucide-animated](https://lucide-animated.com/) / [pqoqubbw/icons](https://github.com/pqoqubbw/icons) | `4662fff2b60fdcf4751b32013606756caf7c4c26` | MIT, pqoqubbw |
| [AnimateIcons](https://github.com/Avijit07x/animateicons) | `49b3c297b4fa3364e9c24f5208ce6c22aa35bac8` | MIT, Avijit Dey |

Local adaptations retain the selected SVG path shapes and source animation
patterns, with quieter amplitudes/timing and one shared typed interaction adapter.

| Use | Upstream file | Selected motion |
| --- | --- | --- |
| Home / Projects | pqo `icons/home.tsx` | Doorway stroke redraw |
| Components | pqo `icons/blocks.tsx` | Detached block shifts into place |
| College projects | pqo `icons/graduation-cap.tsx` | Small cap lift and tassel movement |
| Publish | pqo `icons/upload.tsx` | Upload arrow lifts |
| Teams | pqo `icons/users.tsx` | Second person shifts |
| Help | pqo `icons/circle-help.tsx` | Question mark tilts |
| Learning | AnimateIcons `icons/lucide/flask-conical-icon.tsx` | Flask and liquid counter-rotate |
| Saved projects | AnimateIcons `icons/lucide/bookmark-icon.tsx` | Bookmark lifts and settles |
| Search | AnimateIcons `icons/lucide/search-icon.tsx` | Lens scans gently |
| Menu | AnimateIcons `icons/lucide/menu-icon.tsx` | Three bars pulse in sequence |
| Source templates | AnimateIcons `icons/lucide/code-xml-icon.tsx` | Code marks separate and settle |
| Workspace | AnimateIcons `icons/lucide/layout-dashboard-icon.tsx` | Dashboard tiles pulse in sequence |
| Sign in | AnimateIcons `icons/lucide/log-in-icon.tsx` | Entry arrow moves inward |

All sequences finish within 560ms and leave their SVG box in place.
Full upstream MIT and underlying Lucide ISC / Feather MIT notices ship in
`public/third-party/animated-icons.txt`. Hugeicons are not included.

The copied icon code is reviewed application source; icons do not fetch code or
assets from an external service at runtime. The pinned Motion dependency is
locked by package-lock.json. LazyMotion and its smaller component surface keep
unused animation features out of the product bundle.

## Interaction contract

Hovering the whole navigation control, keyboard focus and activating the control
can trigger the same finite path/group animation. Touch receives activation
feedback without requiring hover. Visual labels, hit targets and focus outlines
stay stationary. Decorative SVGs stay aria-hidden and cannot take tab focus.
There are no additional API calls, navigation delays or event cancellation.

Browser coverage includes label/destination preservation, menu dismissal,
320–1440px layout, path motion, keyboard/pointer activation and live changes to
reduced-motion preference. Existing auth, permission and business-flow suites
remain enabled.

## Bundle measurement

A standalone esbuild browser/ESM comparison with React external, minification and
gzip measures the previous static navigation renderer at 2,387 bytes and this
13-icon renderer at 29,973 bytes: an increase of 27,586 compressed bytes. This
is a bounded renderer comparison, not a total page transfer, loading-time or
Core Web Vitals measurement. The shared Motion runtime accounts for most of
the increase; reducing the number of selected path shapes would save little.
