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
