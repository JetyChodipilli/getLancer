# Original V1 connected browser journeys

`tests/connected/v1-journeys.spec.ts` implements the six journeys in `docs/10_TESTING_QA.txt`, each with a separate synthetic actor and data for desktop, phone and tablet. These 18 cases use the connected frontend, Java API, PostgreSQL, Mailpit and private S3-compatible storage. Browser clicks and forms perform every transition named below. There are no route mocks, skipped tests, demo-state substitutions, direct domain-table writes or application test endpoints in this suite.

| Original journey | Initial fixture | Browser acceptance |
| --- | --- | --- |
| E2E-01 Developer onboarding | Generated credentials only; the account does not exist | Signup, delivered email link, explicit confirmation, login, profile save/submission, separate administrator password/MFA login and approval, enabled developer dashboard |
| E2E-02 Product publication | Verified approved builder with no products | New draft and live URL, actual PNG upload through signed S3 PUT and completion, saved alt text, review submission, absence from public discovery while pending, administrator inspection of rendered uploaded bytes and approval, owner activation and public discovery/detail |
| E2E-03 Showcase slots | Approved builder, three active products and one approved draft | Real fourth activation returns `409 SLOT_LIMIT_REACHED` and visible error, archive one active product, activate the fourth, persist exactly three active products, archived item absent and replacement present in search |
| E2E-04 Client conversion | Approved builder/public product; unique anonymous client address | Public search, detail, real external-demo navigation request, Build something similar, inquiry form, absence from builder queue before confirmation, delivered client email link and confirmation, delivered developer notification and qualified inquiry |
| E2E-05 Marketplace outcome | Approved builder/public product, verified client and one email-confirmed inquiry in `INQUIRY_RECEIVED` | Review form unavailable before completion; responded, discussion, reported proposal value/currency, hire request, delivered hire link and client confirmation, in progress, completion request, delivered completion link and client confirmation, eligible anonymous review submission, held review absent publicly, administrator publication and anonymous verified public review |
| E2E-06 Moderation | Approved builder/public product | Public report, administrator MFA, priority/investigation triage, review-stage persistence, report resolution with suspension, disappearance from search and detail, restricted owner view with the exact reason and appeal link |

Approval currently activates a product automatically when capacity is available. E2E-02 asserts that behavior, then archives and explicitly activates the same approved product through the owner UI. It does not silently treat an API fixture as the publication transition. E2E-03 independently tests the capacity-blocked activation path.

## Disposable CI integration

Keep the existing `docker-startup` stack and `tests/connected/test.ts` automatic counter isolation. After `node scripts/smoke-docker.mjs` has created `.ci-connected.json`, run:

```sh
node scripts/ci-v1-fixture.mjs
npx playwright test --config playwright.connected.config.ts
```

The preparer verifies `CI=true`, the `getlancer-ci-<digits>` Compose project, the fixture's project binding and the configured synthetic administrator. Its reused `resetCiRateBudgets` guard additionally checks the concrete database container, Compose labels, exclusively owned Docker volume, database identity and synthetic-only email addresses. It refuses an already prepared V1 fixture. It uses ordinary authenticated APIs and delivered Mailpit email to create only the initial states listed above. In particular it does not create the E2E-01 account, an E2E-02 product, E2E-04 inquiry or E2E-06 report, nor does it perform any E2E-05 outcome transition.

Setup groups reset rate counters between independent groups using that existing guard. Browser tests reset counters only through the existing automatic fixture before each journey; all actual limits remain active throughout each journey. Keep `workers: 1`, no retries and separate actors/data for each width. Do not reset counters during a test or raise service rate limits to make it pass.

The preparer extends the existing ignored, owner-readable `.ci-connected.json` with `.v1.version`, `.v1.runId` and `.v1.devices[desktop|phone|tablet][journey]`. It preserves existing connected fixture fields. Credentials use a random run identifier, viewport and scenario in a `ci-...@example.test` address. Delivered setup-message IDs are recorded so browser tests cannot accidentally consume an old signup/inquiry token as a later hire or completion token. Tokens are read only from Mailpit's delivered SMTP messages, never database tokens or the outbox. Existing CI cleanup of `.ci-connected.json` and `docker compose down --volumes` covers all new data. Do not upload the credential fixture as an artifact.

Keep the existing connected screenshots/traces artifact collection. The new screenshots use `v1-*.png` within each case's normal output directory. Setup adds SMTP delivery waits and multiple real API accounts, so retain enough CI job time for the complete connected suite. Browser tests retain the existing 180-second per-case timeout; Mailpit waits are bounded at 60 seconds per expected delivered message.

## Validation and review

The implementation was reviewed in four passes:

1. Implementation: mapped every original transition to a visible UI action and isolated initial fixture state.
2. Expert reread: checked frontend selectors, controller status codes, state-machine behavior, S3 upload completion and operator MFA against the actual sources. Confirmed that approval autoactivation requires explicit coverage.
3. Defect hunt: corrected profile `PROFILE_PENDING` labeling, the app's actual unavailable-project heading, Node Fetch's boolean `Response.ok` and environment typing. Checked Mailpit token freshness, read-only discovery mode, mutation ownership, rate isolation and restore compatibility.
4. Polish: documented exactly which states are fixtures, actual CI integration, evidence boundaries and output handling; kept all six journeys independent and assertions tied to persisted UI state.

Local focused checks:

```sh
node --check scripts/ci-v1-fixture.mjs
CI=true COMPOSE_PROJECT_NAME=getlancer-ci-123 npx playwright test --config playwright.connected.config.ts --list tests/connected/v1*.spec.ts
npx tsc --noEmit --incremental false
```

Discovery reports **18 tests in 1 file**. Syntax and TypeScript checks validate the definitions; they do not prove connected execution. The actual Java/PostgreSQL/Mailpit/S3 browser run requires the disposable Docker stack and remains the root integration gate. Docker is unavailable in the implementation workspace, so no actual connected pass is claimed here.

The external demo step proves the real click and outgoing browser navigation to the synthetic `example.com` URL; it does not claim that an external production demo is reachable. Chromium width coverage is not the manual Safari/Firefox, assistive-technology, production email or launch-admission gate.
