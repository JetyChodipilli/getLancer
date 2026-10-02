# Authentication layout refinement

The latest reference showed an oversized account form and a ribbon with straight cropped ends. The revision gives the artwork a larger portion while making login and signup compact and readable.

## Research and decisions

Reviewed [Webflow's login page](https://webflow.com/login) and its [split-screen login example](https://splitscreen-login.webflow.io/) before implementation. Their concise account hierarchy informed the restrained heading, narrow form, grouped provider controls and consistent field spacing. The 65:35 desktop ratio follows the user's requested composition.

- Use 65:35 columns above 1000px, 55:45 on smaller tablets, and a single compact column on phones.
- Cap the form at 320px. Use 26px headings, 14px labels, 16px input text, 44px controls and 10px field spacing.
- Fill the desktop viewport edge to edge. Remove the nested form card, outer browser-like gaps and excessive padding.
- Keep the unavailable-account notice truthful and shorten its copy. Group Google and GitHub actions in one row and use a compact demo/check-again row.
- Preserve the supplied charcoal/orange vector logo at 220px on desktop and 185px on phones.
- Keep the cool silver story background (#EEF0F4) and quiet outside surface (#F4F5F7).
- Preserve the laptop, four cards, 72-second orbit, pause control, typing pause and reduced-motion behavior. Use equally sized cards with quarter-turn spacing on an ellipse so their bounds never overlap.
- Replace the inherently cropped ribbon with a complete transparent oval. Render it with `object-fit: contain` inside an inset canvas; keep the entire animated card bounds within that canvas.
- Keep story copy in normal flow so it cannot collide with or crop artwork. Allow natural vertical scrolling for long signup and service states.

## Asset generation

Mode: edit of the original ribbon with the image-generation skill. The generated image was converted to WebP for delivery without changing its contents.

Source output: `/workspace/scratch/3216c26b08e9/auth-ribbon-output/pearlescent-oval-frame.png`.

Delivered asset: `public/auth/pearlescent-frame-complete.webp` (1536×1024, transparent background and center).

Prompt: Complete the cropped pearlescent liquid-glass ribbon into one self-contained, closed, wide oval frame with matching glass droplets. Preserve silver/pearl identity, translucent folds, pale blue refractions and thin gold highlights. Use a large open transparent center for the existing laptop and cards, and transparent outer margins. Keep every edge naturally curved and fully visible; no cropped ends, straight crop lines, orange wash, opaque backdrop, UI, text, logo or watermark.

## Verification

Login and signup are checked at 1920×1080, 1440×1000, 1366×768, 1024×768, 768×1024, 720×900, 375×812 and 320×812. Checks cover the loaded vector logo, exact responsive split, maximum form width, readable controls, no horizontal overflow, contained artwork, password visibility and reachable submit buttons. A full 72-second virtual animation cycle checks containment and pairwise separation of the moving card bounds while confirming the laptop stays fixed. Existing authentication tests cover provider behavior and navigation.

Styling does not activate an unconfigured backend or OAuth provider. The published preview continues to disclose unavailable account services.
