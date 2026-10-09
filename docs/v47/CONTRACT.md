# V4.7 lab execution platform

Source: getLancer V4.5–V5.2 plan, S03-B01–B08, pp.31–41 and 66. Baseline: main c5bc9eba16a360d9ad092ab78b617450e59ebb76; post-merge workflow 37833045907 passed all six jobs.

## Delivery and admission

Implement the separate lab control plane and workbench. Reuse Java session/CSRF/MFA, PostgreSQL, typed requests, source publication authority and the existing Spectral Studio tokens. No payment, new showcase slots, contributor-code execution in the app, or changes to V1–V30. The public runtime is off by default. Tests of protocol fixtures establish control-plane behavior; they do not establish KVM isolation or real hosted Redis.

S03-B01 requires a chosen isolated provider, real host/network/cleanup measurements, Redis licence assessment and an approved dated budget. S03-B08 requires runtime admission evidence. Neither gate may be inferred from a configuration flag, shared-kernel Docker, frontend animation or a mock test. New source remains freely available when runtime access is disabled or quota is exhausted.

## HTTP contract

All routes remain under `/api/v1`; JSON camelCase, UUID run ids, certified manifest slugs, UTC timestamps, errors `{error:{code,message,requestId,fieldErrors}}`. Mutations use the existing exact Origin and X-Requested-With checks. All run operations recheck current account/session and current manifest/source authority. No worker URLs, credentials, command strings or arbitrary methods/paths are accepted from the browser.

- `GET /lab-manifests`: public `{items: LabManifest[], runtime: {enabled:boolean,reason:string}}`; only currently eligible manifests. Each manifest has `id,title,summary,language,framework,sourceUrl,setup,executionMode,scenarios`. Each scenario has `id,title,description,inputs` (bounded named field definitions), and `operations` (id,label). No infrastructure fields in public projections.
- `GET /lab-quota`: current actor `{dailyLimit,remaining,resetAt,activeRunId,runtime:{enabled,reason}}`. Initial limits: five admitted runs per UTC day, one active account run, ten globally; five-minute lifetime, 90-second idle cleanup. Run reservations are separate from the three publishing-slot pools.
- `POST /lab-runs`: current actor, Idempotency-Key (bounded), `{manifestId,scenarioId,inputs}`. Returns 202 safe `LabRun`. Same key/body returns the same run; changed request returns 409. Disabled runtime 503 LAB_DISABLED; quota/capacity 429; invalid scenario/inputs 400.
- `GET /lab-runs/{id}`: owner/admin, safe `LabRun`: `id,manifestId,scenarioId,status,executionMode,verified,requestedAt,expiresAt,reason,eventsUrl`. Queued, Starting, Running, Cancelling, Cancelled, Succeeded, Failed, Expired use uppercase API values. No terminal state becomes Running again. No verification badge before actual healthy isolated execution.
- `POST /lab-runs/{id}/stop`: owner, idempotent; routes close before cleanup. Unacknowledged cleanup keeps global/memory/cost reservations and operator attention.
- `POST /lab-runs/{id}/requests`: owner, Idempotency-Key, `{operationId,inputs}`. Only certified operation/input schemas; <=64KiB request, bounded safe JSON response. Source or account revocation, expiry and stale lease deny before forwarding.
- `GET /lab-runs/{id}/events`: owner-authorized bounded SSE, Last-Event-ID cursor; no bearer query tokens. Envelope includes runId, sequence, type, recordedAt and allowlisted data. Current authority is checked for every replay; no secrets, account tokens or raw worker headers.
- `GET /admin/labs`: current admin/MFA, truthful admission/queue/resource/cleanup counts.
- `POST /admin/labs/pause-admissions`: current recent admin/MFA, `{paused,reason}`. Pause is durable and audited; unpause cannot bypass missing certification or isolation evidence.

## Durable execution

Atomic reservation and transactional outbox use PostgreSQL locks, conservative memory and daily/monthly cost reservations. Infrastructure failure before healthy startup releases the daily unit; Running consumes it. Stable command id and lease generation fence retries, cancellation, lost responses and restore. Scheduler/watchdog bounds queue/start/expiry work and retains quarantined capacity until cleanup is confirmed. A restore rotates an operator epoch outside restored metadata before any route or admission can reopen.

Only an operator-certified immutable manifest with pinned source/image/protocol and resource/network limits can run. Separate build service has no app database/merchant secrets. Fixed operator gateway origin, no redirects, bounded response, exact run/lease/command/image binding. Provider feasibility, secret-free isolated builds, tenant/network/resource attacks, orphan cleanup and restore need independent admission evidence before public execution.

## UI

Add `/labs` workbench and operator workspace using existing 16px fields, 44px controls, associated labels, compact readable cards and responsive layouts. Preserve inputs through denial/start failures. Deliberate Run only; no reservation on mount/tab change. Lost responses retain the same idempotency key. Queued/Starting permit cancel; terminal/expired/revoked runs cannot send requests. Free source/setup fallback has no payment upsell. Connected API failure is an error, never successful demo data. Browser tests may supply labelled fixtures; product routes cannot manufacture execution evidence.

## Release evidence

Record source-tree tests, typed route/database grants, concurrent quota races, stale cancellation/lease/restore, gateway negative controls, privacy, responsive keyboard/error behavior and published CI separately. Nine educational scenario labs and event-driven teaching diagrams belong to V4.8. Deployment/provider approval and seven-day soak remain explicit external evidence, not local test results.
