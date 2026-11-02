# getLancer / Joinery

Selected direction B, recovered from the approved identity board. The name remains `getLancer`. Two offset joints share a clear diagonal opening: an identifier for the relationship between working software and its independent builder. This interpretation is a design judgement, not a business claim.

## Logo

Primary: `/brand/joinery/getlancer-horizontal.svg`. Header width 168px; minimum 132px. Symbol-only artwork starts at 24px; the tested favicon exception is 16px. Use supplied paths, never recreate the wordmark by typing. It is IBM Plex Sans 600, normal width, outlined with HarfBuzz and optically spaced. The `tL` boundary is opened and the `La` pair tightened; corrections are in the construction audit. The symbol is resolved with the native brand identity helper and skia-pathops, including real subtraction of the diagonal.

Clear space: half the symbol's ink width around the lockup. This is a derived production rule, to be reviewed after use. Never close the diagonal, move one joint independently, rotate, stretch, outline or add effects. Use graphite on white/canvas, white on graphite, or black for single-color reproduction. Reversed artwork uses the same inspected silhouette; an artificial inset was not needed at 24/32px.

Compatibility URLs under `/brand/paper-*.svg` and `/brand/getlancer-logo.svg` now contain Joinery artwork. Google and GitHub provider artwork is unchanged. The former Cutframe directory is removed.

## Color

| Role | HEX | Use |
| --- | --- | --- |
| Ink / dark | #233746 | Text, primary action, exhibition ground |
| Canvas | #f5f8fa | Reading and workspace background |
| Surface | #ffffff | Task surface |
| Soft | #edf2f5 | Quiet grouping |
| Muted | #536572 | Secondary text |
| Line | #d3dce2 | Decorative separators |
| Control | #7d8b94 | Interactive boundaries |
| Focus | #2d5d85 | Focus on light |
| Inverse focus | #bad8f1 | Focus on dark |
| Dark raised | #304959 | Raised dark surface |
| Dark muted | #c1d0da | Secondary text on dark |
| Dark line | #5a6e7b | Decorative dark separators |

Measured pairs are in `audit/asset-audit.json`; role names do not grant blanket accessibility. Decorative line colors are not sole control boundaries. Print color needs the printer's actual profile; no unverified CMYK/Pantone equivalent is specified.

## Typography and licensing

UI and logo source: IBM Plex Sans. Editorial display: IBM Plex Serif 400. Deployed Latin subsets are modified fonts named **getLancer Sans** and **getLancer Serif** internally and in CSS, respecting OFL reserved name "Plex". Original copyright and license records are retained. Public filenames identify source families for stable URLs; CSS uses renamed families.

Sans `/fonts/ibm-plex-sans-latin.woff2`: normal variable weight 100-700, width pinned to 100. Serif `/fonts/ibm-plex-serif-latin-400.woff2`: real normal 400 only. Sans 400 body, 500-600 labels/headings; serif 400 display. No synthetic serif bold. English, Latin accents, punctuation and ₹ € £ ¥ ₿ are included. Only source-supported mathematics/arrows are included; see exact coverage. Other scripts use system fallback.

The source Sans has no `tnum` feature. At weight 600 its default digits all measure 600 font units. This is measured equal advance, not a claimed OpenType feature.

Official source: Google Fonts repository revision `6c1dbe27dcf7d817d4b62cec033ba77fa8e2a5ec`, `ofl/ibmplexsans` and `ofl/ibmplexserif`. Original OFLs are in `public/fonts/IBMPlexSans-OFL.txt` and `IBMPlexSerif-OFL.txt`. Source URLs and checksums are in the kit README.

## Delivery and evidence

The eight-page guide, variants, web icons, CSS/JSON tokens, font licenses, source recipe, checksums and small-size sheet are in `.tooling/joinery-kit/`. The final ZIP is the distributable package. React Brand has filled outlined paths, inherits currentColor, one accessible getLancer label, and a decorative nested SVG.

Four recovery passes: approved-board/source comparison; native geometry/font/SVG integrity audit; actual-size light/reversed and PDF-page visual review; changed-file ESLint and final path/checksum review. This evidence covers assets and component source. Integrated browser/build evidence belongs to the root rollout and is not implied here.
