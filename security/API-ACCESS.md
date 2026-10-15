# API access controls

The baseline declares **289 method/path policies**: **286 application routes** and **3 loopback-only health routes** in `backend/src/main/resources/api-authorization-policy.txt`.

| Policy | Count | Required authority |
| --- | ---: | --- |
| PUBLIC | 45 | Public handler validation and abuse controls |
| SESSION | 102 | Current active session; handler-specific ownership/membership |
| DEVELOPER | 61 | Developer role plus domain ownership/approval rules |
| ADMIN_MFA | 71 | Administrator MFA; recent MFA where required |
| SERVICE | 1 | Independent demo gateway secret |
| SIGNED_WEBHOOK | 4 | Domain raw-body signature verification |
| CAPABILITY | 2 | Matching inquiry/action capability |
| HEALTH | 3 | Loopback caller |

`ApiPolicyCoverageIntegrationTest` compares the actual Spring MVC handler set with the complete application policy set. It sends real requests through the MVC security chain for every SESSION/DEVELOPER/ADMIN_MFA route using anonymous, expired, revoked and suspended sessions, and checks ordinary-user, missing-MFA and stale-MFA denials for the relevant elevated routes. Service authentication is checked separately. Tests raise rate budgets only inside the isolated test context so rate rejection cannot conceal an authorization failure.

This matrix proves those transport decisions. It does not replace positive behavior tests, object-level authorization checks, signed-webhook/capability tests or production access reviews. New routes must have explicit policy, matching handler inventory and meaningful domain tests.
