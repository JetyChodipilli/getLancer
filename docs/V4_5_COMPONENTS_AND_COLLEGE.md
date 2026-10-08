Current slot policy: see [V4_5_PUBLISHING_SLOTS.md](V4_5_PUBLISHING_SLOTS.md). This supersedes the original shared three-showcase allowance: regular and college projects now each receive three free active places.

Historical V4.5 baseline. V4.6 adds creator ZIP uploads, source-changing releases and controlled free previews; see [the current implementation](v46/IMPLEMENTATION.md). The later-phase statements below describe the V4.5 checkpoint.

# V4.5: components, college discovery and publishing capacity

The 4 October amendment supersedes the planning pack's proposed component capacity: each approved builder receives **three free active component slots**. Buying one additional slot increases reusable publishing capacity by one. Component previews and licensed source remain free to visitors without login. Full-project showcase capacity remains independent.

V4.5 includes six original MIT-licensed HTML/CSS/JavaScript recipes, searchable discovery, versioned source files, a working opaque-origin preview, curated recipe remixes with creator attribution, component review, educational metadata on existing projects, and admin-priced capacity purchases. Uploading arbitrary component source, backend live labs and paid college source listings retain their later sprint milestones. The backend tab explicitly says source-only coverage; it does not offer invented live labs.

## Publishing and payment policy

- Drafts and pending reviews do not consume slots. Approved active entries do. Archiving releases capacity. Paid slots remain reusable; there is no recurring subscription.
- Admins with current MFA configure a positive INR price in minor units. Checkout is disabled until both pricing and the existing merchant/policy gate are enabled. No runtime price is seeded.
- Each order buys one slot. The price is frozen before the external request. A changed price requires renewed user intent. The client supplies a UUID idempotency key; one unresolved order per account/mode blocks another charge even under a different key.
- The shared Razorpay client creates a platform order without a seller Route transfer. Existing provider credentials, bounded requests, signature checks and collection approval are reused. Test captures are labelled and never grant live publishing capacity.
- Capacity derives from captured **live**, unrefunded, undisputed purchases. Capture is confirmed by an authoritative provider GET, never by the browser success callback alone. Partial or full refund removes that purchase's slot; an open/lost dispute holds it. Older refund observations cannot restore capacity.
- A capacity reduction archives the newest excess active remixes under the account lock. It preserves the source and licence of previously published, unsuspended entries. Suspensions and account/profile restrictions still revoke public access.
- Ambiguous provider creation becomes UNKNOWN. MFA operators bind a recovered provider order only after receipt, amount and currency verification. Reconciliation uses the same reservation. Provider errors preserve stored facts.
- Signed webhook endpoint: `/api/v1/components/razorpay/webhook`. Configure capture, refund and dispute events. Signed raw bytes and event-ID hashes prevent forged or conflicting redelivery. Failed processing remains retryable.

## Preview and content boundary

Each preview uses `sandbox="allow-scripts allow-forms"` without same-origin, navigation or popup permissions. Native form events work, while the child CSP denies form navigation. Its unique opaque origin cannot access application cookies/storage. An embedded CSP denies network connections, external assets and form actions. Preview source is curated and bundled with the application; submitted source is never executed. Six recipes contain synthetic interactions and no external dependencies. Source downloads contain `index.html`, `README.md` and `LICENSE` with version and rights information.

College metadata belongs to existing product IDs and retains existing showcase slot, profile, visibility and account checks. Metadata changes return its independent educational review to PENDING. Academic institution/year/branch are private unless the creator opts in. Contribution is disclosed without claiming university certification. V4.5 supports showcase discovery in any declared language; it does not imply a hosted runtime or source sale.

## Operations and rollback

Apply Flyway V24 and V25 with the normal backend migration process. Configure the slot price through the admin workspace and the existing payment settings separately. Disable pricing to stop new orders without preventing reconciliation. Suspend a recipe remix or educational record through MFA review. Frontend preview mode offers clearly labelled sample records and never opens a payment gateway or returns a successful purchase. Connected backend errors never fall back to sample success.

Rollback the frontend/backend release together; retain V24 and payment/audit records. Do not erase reservations or downgrade the database after orders exist. The source-only language and future-runtime boundaries remain visible.

Published component manifests and their MIT source are stored as immutable first-publication snapshots. Edited context stays private until approved; public archived, pending or draft versions retain the last approved context. Operator review and recovery lists are paginated, with pending/unresolved records first. Signed events can recover an ambiguous order by matching its authoritative receipt and frozen amount before applying a dispute hold. This phase does not extend account retention or anonymization policy.
