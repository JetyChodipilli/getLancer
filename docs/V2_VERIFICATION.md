# V2 scope and verification

## Document traceability

| Source | Implemented behavior | Evidence |
|---|---|---|
| Vision §11, functional requirements §18 | Teams/studios, multi-team membership | TeamsController, team workspace, demo |
| Business rules §15 | Approved creator with approved product; five team roles | TeamPolicy, TeamsTest |
| Business rules §15 | Search/invite and open-role/apply; recipient consent | TeamsController, TeamsIntegrationTest, demo tests |
| Business rules §15 | Temporary contract membership and staffing expiry | TeamPolicy, workspace, demo expiry tests |
| Business rules §16 | Client interest, team stages, capacity/recruitment, proposal and client acknowledgment | Team leads + staffing + recruitment sections; original-client response test |
| UI UX §19 | Team profile, members, roles, combined products, availability, range, attributed reviews and hire CTA | TeamPublic |
| Security/privacy | Role-filtered CRM/recruitment records; owner product consent; moderation with MFA and audit | Backend + demo negative authorization tests |

## Release boundary

V2 contains collaboration and lead management. Legal agreements, payments, automated matching, subscriptions and template commerce are outside this phase. A team member remains independent; membership creates no employment relationship. WON is a reported outcome and never creates a verified completion or review.

The cloud site hosts the interactive isolated preview. Real account/database flows require the Java service, PostgreSQL, SMTP and object storage deployed with the repository manifests. Provider OAuth credentials are optional until configured.

## Checks

Verified 2026-09-30:
- TypeScript and Cloudflare-compatible frontend build passed.
- 44 frontend tests passed, including 16 isolated team-flow tests and V2 route rendering.
- 99 backend unit tests passed locally.
- GitHub CI passed 141 backend tests, including guarded PostgreSQL integration.
- GitHub frontend and Docker startup/recovery jobs passed.
- CI run: https://github.com/JetyChodipilli/getLancer/actions/runs/36685919485
- Successful private site publication: https://getlancer-v1.jety124050.chatgpt.site
- Direct team preview: https://getlancer-v1.jety124050.chatgpt.site/preview/teams

Browser visual QA was not performed in this release; route rendering and interaction state were tested programmatically. Real PostgreSQL verification ran in CI. PostgreSQL tests use only the guarded disposable getlancer_test database; normal application data is never reset by test fixtures.
