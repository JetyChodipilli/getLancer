# Media recovery and migration

Uploads reserve capacity under the product lock. Verified media, unexpired signed reservations and unexpired direct-write intents share the existing six-slot limit. A signed completion uses its own reservation once. Provider I/O runs outside database transactions; final attachment rechecks current owner, editable state, session, reservation expiry and the exact write intent.

## Recovery protocol

V42 adds `media_write_intents` and durable `media_uploads.completed_at`. Each attempt commits a deterministic final-key intent before storage reads/writes. The successful metadata transaction attaches independently verified final objects, consumes the signed reservation and removes the journal together. An uncertain commit must not trigger deletion of possibly committed media. A provider error retains recoverable intent; an early failure before any final write may release it for a legitimate retry.

The two-minute attempt deadline plus five-minute recovery margin bounds application finalization. Cleanup locks expired intents with SKIP LOCKED in batches of twenty, checks media by identity and final/thumbnail keys, then atomically queues unreferenced keys and removes the intent. Actual storage deletion runs outside that transaction and retries through the existing queue. Staging cleanup retains its original signed expiry plus five-minute margin; completing or retiring an upload does not shorten a still-valid PUT signature.

This is an application recovery protocol, not a physical bucket capacity guarantee. Very late provider writes, outages, cleanup backlog, deletion throughput and storage lifecycle rules still require deployed provider verification, lifecycle policy and monitoring. Do not configure lifecycle deletion of active final objects.

## Rollout

1. Quiesce old application writers and pending upload completion traffic.
2. Apply V41, V42 and V43 as the migration owner, then apply reviewed runtime grants in ops/database/10_permissions.sql. Keep the normal application role separate from the migration owner; its startup privilege guard must pass.
3. Deploy the new application before reopening writes. V43 retires unresolved pre-upgrade reservations because V8 erased the evidence needed to distinguish unused uploads from deleted completions. Users with a pending old reservation must start a new upload. Existing completed references remain available for idempotent retries.
4. Verify new signed completion/retry, direct upload, delete, capacity, failed-provider recovery and cleanup under the real storage role. Monitor recovery age, queue/backlog, storage errors and bytes retained.

Do not operate mixed old writers during this rollout: an old binary does not understand the durable consumption marker. V43 deliberately preserves original expires_at values and does not discard staging deletion work.

## Regression scope

MediaPublicBehaviorRegressionTest verifies deleted-completion replay rejection and reserved-slot protection against direct uploads. MediaLifecycleIntegrationTest exercises external I/O without a transaction, concurrent completion, provider/read failures, expired/unauthorized finalization, quota and cleanup, legitimate retry and ambiguous commit controls. MediaUploadUpgradeIntegrationTest runs real Flyway V41→V42→V43 in a guarded private test schema, including actual FK erasure, surviving completed references, ambiguous legacy reservations, unchanged expiry and a fresh successful completion/retry. DatabaseRoleGuardIntegrationTest separately exercises real restricted-role privileges. Provider behavior in these media regressions is mocked; real storage operations still require deployment validation.
