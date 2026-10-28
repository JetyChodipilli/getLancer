-- Transaction-start timestamps cannot order restrictions made after a row-lock wait.
-- Leave retained append-only history unnumbered: its causal order is unknown.
ALTER TABLE moderation_actions ADD COLUMN decision_order bigint CHECK (decision_order > 0);
CREATE SEQUENCE moderation_decision_order_seq AS bigint OWNED BY moderation_actions.decision_order;
ALTER TABLE moderation_actions ALTER COLUMN decision_order SET DEFAULT nextval('moderation_decision_order_seq');
CREATE UNIQUE INDEX moderation_decision_order_unique ON moderation_actions(decision_order);
REVOKE ALL ON SEQUENCE moderation_decision_order_seq FROM PUBLIC;
-- New decisions allocate their order while holding the target's existing row lock.
-- Ambiguous legacy appeals require manual recovery rather than a guessed backfill.
