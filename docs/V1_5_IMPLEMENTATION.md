# V1.5 implementation contract
Source: 01_VISION section 11, MASTER_PRD deferred items, business rules section 5, OPS section 18.

- Availability: dedicated update/reconfirmation without re-review of material profile claims; dated freshness exposed.
- Response reliability: reuse the existing minimum-sample public response statistics; no invented score.
- Repository evidence: builder publishes a one-time challenge in the stated GitHub repository README and submits it for manual administrator inspection. Approval is scoped to that exact repository URL and is not an identity, security, or code-quality guarantee. URL edits invalidate evidence.
- Demo health: scheduled public approved demos only; HTTPS, public DNS destinations pinned to the connection, certificate/hostname checks, three-hop maximum, 16KiB headers, HEAD requests only. Never execute JavaScript. Health is observational and must not change approval or availability.
- Similar builders: public approved projects by different approved available builders; shared category or technology, one result per builder, deterministic ordering, at most four, no paid ranking.
- Earned capacity: documents supply no automated threshold. Administrator may grant one audited slot per distinct client-confirmed completed engagement, with a required reason; the same engagement cannot be awarded twice. No automatic awards, no purchase mechanism, no trust badge from capacity.
- Existing 3-slot defaults remain; atomic activation reads the existing entitlement.
- Hosted frontend remains isolated demo until a hosted Java backend is configured. Local PostgreSQL is not remotely reachable from the frontend.

## Verification and operational boundaries
The Docker smoke journey compares the exact applied Flyway scripts with the repository migrations (not a fixed count). It exercises V1.5 availability, admin isolation, evidence approval/revocation, unknown demo health and an idempotent capacity award through HTTP.

Remaining activation work: deploy the Java API/PostgreSQL and configure the hosted frontend BACKEND_URL; configure email and optional OAuth providers. Demo monitoring is off by default (DEMO_HEALTH_ENABLED=false). Enable it only with restricted public outbound HTTPS and operational DNS timeouts; it performs bounded HEAD reads, but system DNS lookup has a three-second caller timeout and a single daemon worker with no queued requests, so an uninterruptible resolver cannot consume unbounded threads. The administrator screen lists pending and verified evidence and supports reasoned revocation through the protected API. This is manual repository evidence review, not automated GitHub ownership verification.
