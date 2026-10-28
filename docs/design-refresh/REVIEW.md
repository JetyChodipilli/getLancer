# Workboard review — 28 October 2026

## Scope and material

Reviewed the current main baseline `2f1ea580336ce2cf26c655a25d51b0aed080cc5f`, including merged PR #56. The REST API attachment is a question-bank validation report: 100 questions, not endpoint/transport failure evidence. It does not authorize changing API semantics. The frontend retains the Java/PostgreSQL boundary, real authorization, provider truthfulness and isolated previews.

Subject inventory: real software paired with its named builder; active showcase limits; reusable free components; paid immutable source releases; practical college projects; consent-based teams; hiring briefs; delivery milestones and support; separate sample workspaces; original two-color vector wordmark. The page's claim is that software demonstrates what a builder can do. The evidence is each actual catalogue object and its working detail route. The live element is URL-backed project discovery, filters and no-match recovery.

Primary Awesome Design MD reference: Airbnb marketplace hierarchy. Secondary: Linear product framing. UI/UX Pro Max: verified Vibrant & Block-based result for talent/software marketplace, plus React semantics/forms. Its testimonial layout and purple palette were rejected as unsuitable. Taste page direction: The well-kept board, seed 1793452197. App: Achromatic ledger, seed 650972335, retaining the brand typeface. No external photos, borrowed logos, proprietary fonts or invented testimonials were added.

Logo equity audit: original SVG, original two colors and exact proportions retained. Audit returned 91/100 heuristic, with a wide-lockup/small-size caveat and high path count. This refresh improves display and clear space; it does not claim a new symbol, app-icon kit or trademark clearance.

## Visual critique

ROUND 1
Seen in one second: a software discovery invitation beside project evidence.
Idea (one sentence): getLancer's software and its builder become the objects on a board of independent work.
Live thing: query/category/technology discovery with real detail navigation.
Layout words by section: hero=work board / catalogue=searchable objects / next steps=editorial links / footer=directory.
Scores: design 6 · usability 6.5 · creativity 7 · content 7 → overall 6.5.
Caps applied: legacy shadows/card rotation and low-contrast caption from inherited rules.
Top three problems:
1. Desktop and tablet featured media were reduced and rotated by old selectors; isolate the new component classes.
2. The main feature caption inherited a pale background while using white text; correct selector scope and remeasure contrast.
3. The phone's long workspace navigation consumed the task area; replace the horizontal strip with a native disclosure and retain keyboard access to every destination.

ROUND 2
Seen in one second: software and its creator, with a clear explore action.
Idea (one sentence): independent work is the evidence, with the creator one click away.
Live thing: catalogue state persists in the URL; empty results have a clearing action.
Layout words by section: hero=asymmetric board / catalogue=three-column desktop and single-column phone / next steps=three text rows / footer=quiet directory.
Scores: design 7.5 · usability 8 · creativity 7 · content 8 → overall 7.6.
Caps applied: none on the final homepage inspector; original sample product UI remains an illustrative interface fragment, not real business proof.
Top three problems:
1. Small featured previews are necessarily less legible than the dominant project; labels and genuine detail links identify them.
2. The wide original wordmark has limited symbol strength at favicon scale; no new logo concept is substituted without a brand decision.
3. Existing feature modules carry older CSS and full-repository lint debt; this release unifies shared surfaces and adds a strict lint gate for the changed shared UI modules. It does not claim to have rewritten every legacy feature implementation.

## Browser and tool boundaries

Playwright CLI was attempted; this executor rejects its Unix-domain control socket (`listen EPERM`). The actual Playwright test runner works through its browser pipe, with a Chrome headless shell and a server launched in the same process environment. The real-browser design inspector also ran against the production build. No CLI interaction success is claimed.

An automatic review stopped the initial Node suite on the synthetic `getlancer-qualification.dev` hostname. Source inspection proved that unsafe origins throw before connection and the remaining case mocks both DNS resolver methods. The test was then split to reject unsafe origins before transport and exercise the DNS deadline directly, without creating HTTP/TLS requests. All seven cases passed; no external qualification action or production activation occurred.

Full baseline `npm run lint` reports 190 existing errors and 110 warnings across unrelated feature code and fixtures. Retain that diagnostic command. The new `lint:ui` quality gate checks every shared TypeScript/React file changed by this redesign without disabling rules. One existing native-image warning remains appropriate: uploaded images use their original safe URL and natural proportions, with no new image proxy trust surface.

## Verification ledger

The task ledger records current source-bound outcomes; final CI evidence is retained in the pull request. Browser and CI results are recorded after completion, not inferred from the design inspector. Real production providers, payment activation and deployment qualification remain separate from this frontend refresh.

## Fresh catalogue implementation

The renewed brief explicitly requires a fresh interface. Component and college catalogue introductions are rebuilt as purposeful editorial panels, workspace destinations are grouped by job, and template releases use actual release metadata. Twenty-four component cover images are captured from the original MIT recipe HTML in a browser with every network request blocked. Each cover is pinned to both the exact recipe source SHA-256 and its own JPEG SHA-256; the frontend uses it only for the matching original source, with a truthful fallback for uploaded or different work. `scripts/verify-component-covers.mjs` verifies every pin in CI. This replaces generic wireframe artwork with the component's actual rendered UI.
