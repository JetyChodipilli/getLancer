# Invitation acceptance expiry

Reviewed on 2026-10-16 against main `3e1da3603a0678edb5819de55dc8066ff9023d41`.

## Boundary and impact

`POST /api/v1/business-invitations/{id}/respond` and `POST /api/v1/team-invitations/{id}/respond` require an authenticated recipient and a pending invitation. Business acceptance grants the hiring-manager role; team acceptance grants the stored offered role and membership type. Neither endpoint accepts a caller-supplied recipient or role.

Both handlers formerly checked invitation deadlines against PostgreSQL `now()` inside a transaction that first locks the business/team and then its invitation. `now()` is fixed at transaction start. A request starting before expiry but waiting for either lock until after expiry could consequently record acceptance and grant membership. A permanent business or team invitation can grant durable access in that situation. Accepting an already-expired contract can instead create an expired membership; that case alone does not establish durable access.

The prerequisite is a pending invitation and contention across its deadline. An invitee-controlled way to keep the blocking transaction open indefinitely has not been demonstrated. This is an expiry enforcement defect, not evidence of arbitrary invitation theft or role escalation.

## Implemented control

Both services now read the raw invitation row under the existing locks and then issue a separate `SELECT clock_timestamp()`. All invitation deadlines must be strictly later than that observation. The separate statement runs after both locking reads return; merely changing the clock function inside a locking query does not establish that ordering. This follows the existing account-token verification pattern in `AuthService`.

Business invitations require `expires_at` to be future. Team invitations require `respond_by` and any non-null `expires_at` to be future, including optional expiry on a permanent invitation. Existing recipient binding, pending-state checks, team/builder eligibility, offered roles, valid acceptance/decline behavior and 409 `INVALID_STATE_TRANSITION` semantics are retained. No schema or deadline duration changes are needed.

PostgreSQL documents the difference between transaction time and wall time in [Current Date/Time](https://www.postgresql.org/docs/current/functions-datetime.html#FUNCTIONS-DATETIME-CURRENT).

## Regression evidence

The unchanged baseline failed all six original concurrent expiry cases: business expiry, permanent team response deadline and team contract end, each while blocked on either its parent or invitation row. Every response was 200 instead of 409. Six corresponding valid controls passed.

`InvitationExpiryIntegrationTest` extends that matrix to sixteen cases with optional expiry on permanent team invitations. It creates synthetic accounts in the guarded `getlancer_test` database, arms the real database deadline before the request, confirms the request is blocked by the intended connection before expiry, and releases the lock after the deadline. It does not change status or deadlines while the request waits. Expired responses must leave membership, acceptance status and acceptance audit unwritten. The valid controls must preserve the offered role and stored expiry. Existing team unit tests now model stored deadlines and the database clock instead of a projected validity flag.

## Scope limits

This change covers invitation response deadlines. It does not establish that every other expiring capability or membership check has been manually reviewed, nor does it prove capacity for millions of concurrent clients. Production contention, infrastructure controls and load/failover testing remain separate work. See the [audit limits](AUDIT-2026-10-15.md).
