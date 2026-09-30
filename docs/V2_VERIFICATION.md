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

Verification results are recorded after the final build and GitHub CI. PostgreSQL tests use only the guarded disposable getlancer_test database; normal application data is never reset by test fixtures.
