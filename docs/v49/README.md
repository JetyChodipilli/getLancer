# V4.9 — college-project service

This implements S05 from the reviewed V4.5–V5.2 plan on the V4.8 baseline. Code supports reviewed SHOWCASE, FREE and PAID college releases, with independent student privacy annotations and the existing commerce authority. Production paid activation remains pending; `APP_EDUCATION_PAID_ENABLED=false` is the default. A successful test payment does not establish merchant readiness or grant production source entitlement.

## Implemented acceptance

| Requirement | Implementation | Verification |
|---|---|---|
| FR-029 / four-category contracts | Bounded full-stack, data analytics, AI/ML and IoT evidence; package includes/exclusions, setup, prerequisites, limitations and license | Education integration and four original seed reproduction |
| FR-031 / access modes | Immutable submitted release; showcase has no package, FREE uses private statically inspected ZIP, PAID binds existing approved source version | Positive free download, historical free release, prohibited paid-to-free paths |
| FR-033 / contribution | Existing owner/team consent and current approved profile; source licensing conveys no authorship | Current ownership, team/component visibility and suspension tests |
| FR-034 / existing commerce | Exact version/product/owner/hash/file/license/price binding inside existing purchase reservation; frozen support and package snapshot | PostgreSQL/provider-fixture tests plus existing commerce regressions |
| FR-035 / support | Purchase history keeps accepted license/package/support; pending/refund/dispute/download states remain existing server authority | Purchase/entitlement/refund/dispute negative cases |
| FR-041 / component reuse | Exact immutable component revision, license and attribution; current visibility overlay never changes retained source rights | Wrong revision/license, withdrawal and immutable snapshot checks |
| FR-042 / artifact reports | Existing reports pin canonical reviewed release/hash/snapshot; private MFA moderator triage and existing suspension | Artifact mismatch, immutable evidence, private notes and immediate access revocation |
| NFR-004/005/008/017–020 | Explicit route/DTO/CSRF/MFA policy, additive migrations, private source storage, export/deletion, license provenance and layered tests | [Validation](VALIDATION.md) and [review](REVIEW.md) |

## Creator and reviewer workflow

An approved builder selects an owned project, saves an incomplete draft, fills category evidence and package disclosures, chooses the access/demo mode and accepts source-rights consent. Draft saving does not require a complete submission. Optimistic revisions reject stale saves while the UI retains edits.

FREE mode accepts a bounded private ZIP through the existing static archive inspector: 5 MiB compressed, 25 MiB expanded, 500 entries, README, LICENSE and a recognized build manifest. Uploads are never executed. PAID mode selects an approved source version for that same owned project. Submission freezes content, license, source files/hash, price and support terms. Changing these requires a new release.

A reviewer uses current administrator MFA, checks exact revision/hash and rights/package evidence, and inspects FREE bytes through a CSRF-protected POST before approval. Existing college capacity and current product/owner eligibility still apply. A historical approved FREE release remains downloadable by its identity after a newer showcase or paid release, subject to current suspension/visibility authority. Paid source stays on the existing protected purchase path.

Academic institution/year/branch are private by default and kept outside the immutable agreement. The creator can revoke sharing without changing source terms. Approved anonymization removes these annotations and legacy academic fields while retaining the documented source/financial/audit agreement history. Account export selects only actor-owned records and explicit safe snapshot fields.

## Laptop testing

Use the existing [account-free laptop guide](../../ops/LAPTOP_TESTING.md). No cloud provider is needed for browsing, Java/PostgreSQL testing, original source examples or the Docker lab provider. Build example ZIPs with `npm run education:build` and reproduce them with `npm run education:verify`. Run `npm run build`, `npm run test` and the checked-in Playwright configuration after installing its Chromium browser.

The four original MIT samples are deliberately small: a Node task API, synthetic Decimal analytics, tiny stdlib Naive Bayes evaluation and deterministic IoT simulation. Their README/tests/manifests disclose absent authentication/database/UI, synthetic data, limited metrics, and no physical hardware. Preview records are illustrative. A configured backend failure is shown as a failure and never replaced with those records.

SOURCE_ONLY means no hosted runtime. EXTERNAL links are unverified external evidence. HOSTED requires a current eligible existing hosted deployment; revocation clears its usable link and shows unavailable. V4.9 does not execute arbitrary college uploads, add GPU training, provide physical hardware or introduce team payouts.

## Operator handoff

Production paid activation is an unmet external gate owned by the merchant/operator: approve the existing Razorpay merchant/collection/payee configuration, complete the existing live-commerce acceptance, review the exact college offer and enable `APP_EDUCATION_PAID_ENABLED` deliberately. Leave it false until evidence exists. Runtime admission and static-hosting configuration remain separate existing controls. Test mode cannot substitute for this gate.

See the [API/data contract](CONTRACT.md), [review](REVIEW.md) and [measured validation](VALIDATION.md). No production provider, deployment, PR or merge is implied by local coding checks.
