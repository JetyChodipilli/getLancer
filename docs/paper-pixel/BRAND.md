# getLancer identity — Cutframe

The Paper & Pixel rollout uses a new original Cutframe mark. Its chamfered gallery frame closes into a lowercase **g**, then returns as an open lower shelf. This is a designer interpretation of getLancer's public software exhibition, not a claim about trademark availability or user behavior.

## Approved context and decisions

The user selected the P4 charcoal exhibition / white editorial template and authorized a complete frontend rollout with a new logo. The identity therefore keeps that palette and typographic atmosphere. Geist Sans is already bundled under the SIL Open Font License; its wordmark is outlined at weight 550. Georgia supplies the occasional editorial chapter headline. The software's own work stays the visual focus.

The brand mechanism is one cut frame, not a set of decorative symbols. The mark is the expressive element; the type and palette remain quiet. It is contemporary, restrained and angular without introducing a loud accent.

Reference study of the bundled developer-tools library found circles, blue primaries, containment, and code brackets common. Those are category conventions, not proof of real direct-competitor ownership. This mark avoids an infinity/link device, sparkles, a generic initial inside a tile, brackets, globes and node diagrams. A rectangular gallery study was rejected for similarity to an existing reference; the final chamfered g has a different contour and returned tail. No library artwork was traced into the delivered mark.

## Exploration and refinement

Ten quick directions were considered: an open frame g; chamfered frame g; folded double-L monogram; offset gallery doorway; specimen-tab wordmark; proof-stamp wordmark; shared baseline; cropped exhibit; aperture pair; and a returning page corner. The framed g, double-L and doorway were rendered in monochrome. The final g was chosen for direct name recognition and the clearest small-size silhouette.

Round 1 found the rounded g too generic, the double-L too ambiguous and the rectangular doorway too close to a known library convention. Round 2 gave the g a cut left aperture and a broad returned shelf, centered its silhouette by 4 grid units, and rounded all outlined glyph coordinates to two decimals. No gradients, masks, filters, rasters, strokes or live text occur in final logo masters.

## Files and usage

| File | Use |
|---|---|
| `public/brand/paper-logo.svg` | Horizontal dark logo on a light background |
| `public/brand/paper-logo-inverse.svg` | Horizontal ivory logo on charcoal |
| `public/brand/paper-symbol.svg` | Standalone dark mark |
| `public/brand/paper-symbol-inverse.svg` | Standalone ivory mark |
| `public/brand/paper-wordmark.svg` | Outlined type alone when the symbol already appears nearby |
| `public/brand/paper-stacked.svg` | Stacked lockup for spacious square uses |
| `public/brand/paper-icon.svg` | Brand icon with a charcoal tile |
| `public/brand/cutframe/` | Black, white, mono, favicon, app icons, webmanifest, and identity tokens |
| `app/components/brand.tsx` | Font-independent inline SVG inheriting currentColor |
| `public/favicon.ico`, `public/icon.png`, `public/apple-icon.png` | Application/browser assets |

The historic `getlancer-logo.svg` remains as an archived source asset; the current component uses the new paths directly. External SVG masters contain an accessible title. In the UI, the containing Brand span is named “getLancer” and its SVG is decorative to avoid duplicate announcements.

## Size and spacing

The symbol uses a 256-unit canvas, a 32-unit band, 45° cut faces, and one curved returned tail. Its bounds are x=28–228 and y=24–232. Use at least one band (32 units, one eighth of the symbol canvas) as clear space. This spacing rule is derived from the construction and should be checked in new physical applications.

Minimum digital sizes after rendered inspection: **16px symbol**, **144px horizontal logo**. The UI lockup is normally 168px wide. Use the symbol alone for browser tabs; never squeeze the full wordmark into a favicon. Avoid the wordmark below 144px. A conservative print minimum is 6mm for the symbol, subject to a print proof.

Keep the g's open lower shelf clear. Do not fill the horizontal white gap, square off the lower curved return, replace the cut faces with arbitrary angles, stretch the lockup, reorder its parts, add shadows, or apply the logo over busy project imagery. Choose the reversed mark on charcoal and the dark mark on paper. A solid tile is needed over photographs.

## Color and type

| Role | Value | Use |
|---|---|---|
| Ink | `#202321` | Logo and main type on light surfaces |
| Paper | `#f8f8f6` | Main light canvas |
| White | `#ffffff` | Product/form surfaces |
| Charcoal | `#171a18` | Exhibition and identity ground |
| Ivory | `#f8faf7` | Reversed logo and main text on charcoal |

Independent WCAG relative-luminance calculations give **15.86:1** for ink on white, **14.92:1** for ink on paper, and **16.71:1** for ivory on charcoal. These are measured color-pair results, not an accessibility certification for every screen. The flat mark remains distinguishable in grayscale.

Geist's supplied font has a real 100–900 weight axis, tabular figures, and full English alphabet/digit coverage. The selected Latin subset does **not** contain the Indian rupee sign; UI prices must retain an appropriate fallback for that glyph. Typeface source and OFL terms are retained in `public/fonts/OFL.txt`. No modified font is distributed: final wordmark graphics are path outlines.

## Validation

Two render/review rounds used the Logo Design skill's Inkscape renderer. The final symbol was inspected at 16/32px and the horizontal logo on light/dark grounds. Its broad band remains approximately 2px at a 16px canvas. The final `svg_audit.py` result is **100 for the symbol, no findings**. The outlined horizontal masters score 99 with one informational complexity note for their nine actual glyph outlines. Scores are structural heuristics, not a substitute for visual judgement. The letter, gap, junction and monochrome checks were reviewed manually.

The original concept image was used for exploration only. All shipped logo masters are deliberately constructed SVG geometry. The new mark is original to this work; professional trademark clearance has not been performed.
