> Historical checkpoint. Current status: [15 September V1 verification](V1_VERIFICATION_2026-09-15.md). PostgreSQL is the selected database; earlier Supabase references and unexecuted-test counts below are superseded.

# V1 fixes and release gates

Current follow-up: [9 September release checkpoint](V1_RELEASE_CHECKPOINT.md) documents the latest code changes, executed checks and credential-dependent launch gates.

8 September 2026. This is the current follow-up to the original completeness audit. **V1 is not yet fully complete or ready for public launch.** This change implements substantial repairs; it does not manufacture a live backend, a seeded administrator, or evidence for tests that cannot run.

## Implemented in this checkpoint

| Area | Change |
|---|---|
| Export privacy (P1) | Builders can export only email-confirmed, unrestricted inquiries. Clients retain access to their own submissions. |
| Sole administrator (P1) | Self-service deletion and administrator suspension are rejected before mutation. The account page suppresses the admin closure form. |
| Account enforcement (P1) | MFA-protected suspend/restore actions change account status, revoke sessions/challenges/tokens and record reasons. Deleted accounts require operational recovery. |
| Inquiry abuse (P1) | Quarantine/block/restore actions, recipient disclosure restrictions, public sender-status checks, blocked confirmation/outcome/review actions, and exclusion from outcome counts and published reviews. Reports support inquiry restriction actions. |
| Restricted showcases (F03) | Owner workspace includes a Restricted tab, moderation reason and a review-request link. Restricted content cannot be edited around enforcement. |
| Pagination (F04) | Bounded page/size/hasMore responses for builder projects, builder/client inquiries, saves, notifications, public reviews and moderation queues; load-more controls. Builder active capacity is counted separately from loaded records. Inquiry status, project ID and date filters are available; status/date controls are in the workspace. |
| Expiry (F05) | Batched, locked expiry of abandoned unverified inquiries; valid renewed tokens prevent expiry. Verified clients can request renewal. Expired sessions, challenges, OAuth state, rate buckets and old tokens are cleaned up. Terminal failed email token bodies are scrubbed after seven days. Engagement records are not automatically purged without an approved retention policy. |
| Proof and project data (F06) | Repository URL, optional pricing context, proof reorder/remove, per-image descriptions, upload progress and durable object-deletion queue. Supported YouTube/Vimeo demos load on demand under an explicit iframe allow-list. External demos retain their own access rules. |
| Private previews (F07) | Owner-issued, email-bound seven-day grants; verified client login required; revoke/expiry; approved/active and owner-status checks on preview and media. No public visibility is granted as a side effect. Grants authorize review only, not purchase, redistribution or access to external-host credentials. |
| Taxonomy (F02) | Active server options feed discovery and editing. Admin can add/rename/deactivate options; mapped product names follow renames. Illustrative constants are used only when the backend explicitly reports the disconnected-preview state. |
| Moderation (F08) | Account and inquiry controls, paginated audit history, complete submitted image evidence, external video evidence, report types for project/user/review/inquiry and an Appeal reason. Appeals enter manual report review; they do not automatically reverse decisions. |
| Measurement (F01) | Public builder-profile event; save/unsave server events; client event ID deduplication; product view/demo/save/similar-build counts; private response rate/time with a 48-hour observation boundary; weekly founder qualified/hire/completion counts and failed-email count. |
| Notifications (F09) | Client inquiry progress produces email and in-app notification for an active, verified matching account. Review moderation sends results to affected parties. |
| Errors/accessibility (F10) | API retains code, request ID and field errors; malformed requests return 400, invalid transitions 409. Inquiry/showcase validation summaries focus on failure. Report submission has pending protection. Existing focus/reduced-motion styles preserved. |
| Metadata/performance (F11/F12) | Builder metadata uses the actual approved profile; product and builder Twitter metadata; filtered home URLs noindex. Gallery proof reads are batched instead of N+1. |
| Configuration (F13/F14) | Mutation buckets normalize UUID target paths. Production startup rejects missing credentials, local mail defaults and insecure email/storage configuration. Background jobs and SMTP SSL are explicit environment settings. |

Schema migration: `V6__moderation_and_proof.sql`. Run with Flyway only on an isolated backend database before testing. The private frontend deployment does not apply Spring migrations.

The existing exact transparent logo, Slate Atelier design, three active slots, combined builder/client account, external agreements/payments, manual approval and client-confirmed engagement rules remain the applicable V1 foundations. Templates, subscriptions, teams, platform checkout and hosted demos are not introduced.

## Executed verification

- Full Java main and test source compilation.
- 37 unit tests: 12 new V1 safeguard tests and 25 existing authentication tests, zero failures/errors.
- TypeScript checking and production frontend build.
- 15 existing frontend rendering/contract checks. These exercise the disconnected preview, not authenticated marketplace E2E journeys.
- Whitespace check and secret exclusion scan of source/build output.

20 PostgreSQL integration tests now exist and compile. Four added scenarios cover export disclosure, reaching records beyond 100, private grant revocation and expiry versus renewal. **They have not run against PostgreSQL here.** Docker/PostgreSQL are absent; the package installer cannot change users/groups under the environment's permissions. The existing CI PostgreSQL service is the next executable integration gate; no successful CI result is claimed.

No browser visual, screen-reader, cross-browser, load or backup/restore test is claimed.

## Work still required before V1 can be declared complete

1. Connect the requested Supabase project and choose/configure the backend database. Keep Spring-managed marketplace tables private from any public data API; verify grants/RLS before migration. Supply a running HTTPS Spring service as `BACKEND_URL`, sandbox email and private object storage. No provider or credentials were invented.
2. Provision the administrator authenticator secret, seed the sole administrator in that connected database, and test password plus TOTP. The admin email and password references are already configured; the local ignored password is not committed. Real Google sign-in also needs registered provider credentials and callback configuration.
3. Run all 20 PostgreSQL tests and six documented acceptance journeys, including concurrent slot activation, inquiry confirmation/replay, hire/completion, moderated reviews, private media revocation and account enforcement. Fix any discovered defects before activation.
4. Reconcile remaining document-06 contract differences: signed two-step media protocol versus the existing validated multipart flow; confirmation/review aliases and 201/204 status semantics; optional reported value/currency fields. The current UI uses the implemented endpoints; this does not establish strict API-contract parity.
5. Complete analytics attribution/context and an approved public reliability threshold; public reliability scores remain withheld. Finish queue triage/severity workflows and any additional appeal case lifecycle beyond the implemented manual report channel.
6. Finish validation coverage across all forms and run keyboard, screen-reader, zoom/reflow and contrast checks. Add structured data and curated taxonomy landing pages when real approved listings exist; public indexing remains disabled.
7. Validate actual proxy identity forwarding and endpoint/account/email rate limits. UUID normalization alone does not distinguish guests sharing a proxy. Measure media delivery and implement derivatives/caching only with tested confidentiality and immediate-revocation semantics.
8. Approve operator/legal contacts and policies, retention/deletion processing and consent versions. Configure monitoring and alerts; perform an encrypted backup/restore drill and verify failed-mail recovery. Do not automatically purge engagement/legal records based on an invented retention period.

These remaining items include both implementation and operational work. A successful frontend publication must not be presented as completion of all six V1 phases.
