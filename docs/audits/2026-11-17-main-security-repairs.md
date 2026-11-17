# Main security repairs — 17 November 2026

Base: `2db4a757cc8a5c5da7d5b79e94aac446b1fb11dd` (PR 71 merge).

- Privileged template, component and college reviews, private inspection, slot pricing and recovered-order binding recheck current session, active account, ADMIN role and database-clock recent MFA after resource waits and final transactional audit/actor writes. Recovered-order responses recheck authority after provider reconciliation. Provider facts remain system-authoritative.
- Report suspension leaves PENDING without changing the frozen submission, then installs the exact reported published snapshot in the same transaction under its row lock. Applied migrations and freeze triggers are unchanged. Public denial, enforcement revision and exact-artifact appeal restore are retained.
- Retained component/catalogue saves and publishing/payment controls use the existing account assertion. It compares the original actor with the real authenticated session and never selects an account. Dependent reads, purchase consent, verification callbacks and operator recovery forms retain that actor. Denial clears private state and invalidates pending refreshes.
- Node defaults, engine floor, CI, Docker and source downloads require patched Node 24.22.3. Dependencies use Next 16.4.6, Vinext 1.2.0, plugin-rsc 0.5.37 and Undici 7.30.2. Source-download hashes and native framework previews are regenerated.
- npm 11.19.1 is pinned. Root dependency lifecycle scripts are disabled. The existing hash-checked lint adapter runs through ESLint configuration. Development prerequisite/build commands run explicitly rather than relying on ignored npm hooks.
- Preserve HTTP 307 for all eleven connected preview-to-workspace redirects before Vinext streams a 200 shell. The built-Worker regression derives destinations from every preview page declaration and checks GET and HEAD, preventing later routes from falling outside a manually curated test list.
- Resolve historical college-project bookmarks through one shared resolver before the Worker streams a response. Wrong-project, missing, malformed, empty and duplicate release bindings return uncached HTTP 404; backend outages return 503 without sample data. The page uses the same binding rule. Built-Worker HTTP tests cover valid frozen hashes, invalid bindings, HEAD responses and connected-mode outages.
- Existing typed-profile and late-repository-write fixtures control the DNS resolver on the request thread. Their former InetAddress mock missed the resolver worker, while another test required public DNS. Production URL/address checks are unchanged. Surefire loads the existing Mockito jar as a test-only agent; runtime packaging excludes it.

## Verification

Earlier local execution demonstrated seven failures on old main with the new PostgreSQL regressions: five natural MFA expiry cases during observed lock waits, logout during a commerce review, and report suspension of a submitted private replacement. Controls with current authority or a private draft passed. The repaired implementation passed 39 focused backend checks, all 757 frontend Node tests, build, TypeScript and changed-file lint. GitHub also passed all 1,017 backend tests on the unchanged backend sources. npm audit and frontend OSV found no known matches; registry signatures verified for 665 installed packages.

This branch must be requalified on its exact GitHub revision before merging. New tests cover later actor/audit FK waits, binding response revocation, purchase-preflight handoff, retained recovery evidence and native two-tab component-save handoff. Local Chromium is blocked by the workspace's Unix-socket restriction; no local browser pass is claimed. A valid binding committed before logout remains bound when only its later private response is denied; no paid slot is granted.

## Assessment limits

Existing Spring MVC assessments are narrowly conditional on absence of XSLT view rendering and SSE fragment rendering, current runtime proof and source inspection. Raw findings remain visible. The three GET/CSRF assessments retain their exact finding pins, required regressions, same-run binding, one-hour evidence freshness and **18 November 2026 00:00 UTC** expiry. No scan threshold or required check is relaxed.

The rejected team-invitation hypothesis is untouched. This is not production host/isolation certification or a claim that all undiscovered vulnerabilities are absent.
