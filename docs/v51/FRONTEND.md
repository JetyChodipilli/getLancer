# V5.1 frontend: IoT and native framework components

## Delivered scope

`/labs/iot` is an account-free, browser-local device/protocol model driven by the pure reviewed `lib/iot.ts` state machine. It publishes changed synthetic temperature, humidity and threshold, renders the returned actuator state, exposes the exact per-run topic and raw messages, and shows bounded session events. Disconnect rejects publication; reconnect permits recovery; reset clears telemetry while preserving the run's monotonic sequence and topic. A new session has a distinct UUID and no previous telemetry. No app API mutation or physical control occurs.

Inputs retain visible labels, units, 16 px text, 44 px controls, linked inline errors and a focused error summary. The exact assigned topic is validated before publication. Finite/range validation is followed by the state machine's precision and action-budget checks. Messages and events show the latest 20; a session permits 100 actions. The page labels simulation, browser state and supplied hardware proof separately. Physical proof is absent, with no invented date. Source, BOM, wiring, protocol and setup are linked to the original IoT source material. The example wiring has no physical actuator. Hardware, power, USB, computer and internet costs are excluded.

`/components/frameworks` contains three original components compiled by their real framework toolchains:

| Framework | Original component | Compilation |
| --- | --- | --- |
| Vue 3.5.43 | Project brief with validation/local save | `@vue/compiler-sfc` 3.5.43 `compileScript` with inline precompiled template; runtime-only Vue bundle |
| Svelte 5.57.2 | Filtered build queue with completion state | `svelte/compiler` 5.57.2 client compilation |
| Angular 20.3.33 | Release checklist with signals | `@angular/compiler-cli` 20.3.33 `ngc` AOT, zoneless `bootstrapApplication`; JIT and dev mode disabled |

Each isolated package has exact direct dependencies, an integrity-pinned lockfile, original MIT license, framework-specific setup, notices and full bundled runtime license texts. Full runtime licenses also travel in inert comments inside each generated preview document. npm lifecycle scripts are disabled during installation. Sources remain anonymous/free. Framework compilers are absent from the getLancer application package. Static previews bundle their runtime locally; they are not React emulations or remote CDN embeds.

## Build and security contract

The trusted fixed builder is `scripts/build-framework-material.mjs`. `--native` installs the three reviewed lockfiles with `npm ci --ignore-scripts`, runs only their reviewed build scripts, and packages current source and native outputs. `--native --verify` clears generated compiler output, reinstalls locked dependencies and reproduces compilation and compares exact generated output with the committed material. The normal application build uses `--verify` without executing compilers. This code never accepts caller-selected paths, packages, commands or uploads.

Source reads use one no-follow descriptor, bounded reads and pre/post descriptor identity/size/time checks. Parent directories are checked, symlinks/special files are rejected, inventory counts are bounded, and source hashes must remain unchanged throughout compilation. Source archives are deterministic tar/gzip bytes. The independent verifier unpacks every member and compares the current source, including complete lockfiles and licenses. Generated material binds source, archive and compiled preview SHA-256 checksums.

GET-only preview routes are `/components/frameworks/preview/{vue,svelte,angular}`. Their exact generated script and stylesheet hashes are the only execution/style allowlist. `default-src`, network, forms, frames, workers, images, fonts, objects and base URL are denied; CSP `sandbox allow-scripts` and iframe `sandbox="allow-scripts"` retain an opaque origin, with no same-origin permission. The root Worker compares the exact material-derived policy, preserves it only on those reviewed routes, and bypasses app nonce injection there. Unknown frameworks are 404; POST is 405. GET source routes return only the fixed archive bytes and checksums.

The iframe cannot read app cookies, storage or parent DOM, and cannot initiate network, popups, downloads, form submission or top navigation. Its initial same-origin document request serves only public immutable reviewed bytes and performs no account operation. Reset reloads local synthetic state. Source downloads outside the iframe are ordinary user-initiated free downloads. Checksums establish byte binding, not hosted certification or arbitrary-upload approval.

## Design decisions

The existing Spectral Studio design system remains authoritative: pearl/silver canvas, readable opaque controls, restrained borders, compact type and the existing action color. UI/UX Pro Max searches informed focused error summary/inline errors, readable controls, responsive layout and keyboard behavior. Its generic new-page dark dashboard recommendation was rejected because it conflicted with the user's approved design. No remote font, new application dependency, decoration that hides controls, invented metric or live hardware badge was added.

## Validation and limits

Owned Node tests check current-source native material/archives, exact CSP hash policy, forbidden dynamic loading, symlink/oversized source rejection, archive drift and isolated dependencies. Browser cases exercise actual native interactions under production response CSP, independent IoT sessions, changed readings, recovery/reset, validation/topic rejection, six-decimal precision rejection, bounded histories, source downloads, 404/405 boundaries, cookies/storage/parent/network/popup/untrusted-script denial, responsive widths, readable/touchable controls, keyboard order and automated WCAG scans of each native frame and app page.

Four-pass review: implementation completeness; source/rights/accessibility reread; hostile input/source/CSP defect hunt; compact-copy/responsive polish. Current-source native compilation and owned Node tests have been observed locally. Browser acceptance and current-head CI are recorded by the parent phase acceptance after they actually run. No physical ESP32/DHT22 test, screen-reader session, Firefox/Safari acceptance or live hosted IoT certification is claimed.
