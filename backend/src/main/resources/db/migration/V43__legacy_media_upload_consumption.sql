-- V8 cleared completed_media_id when media was deleted. Such rows cannot be
-- distinguished from unused pre-upgrade reservations. Retire all unresolved
-- legacy reservations, retaining their original signed expiry for staging cleanup.
-- In-flight legacy clients must start a new upload after this rollout.
UPDATE media_uploads SET completed_at=created_at WHERE completed_at IS NULL;

-- Capacity and expiry work remain bounded to indexed product/retention lookups.
CREATE INDEX media_uploads_pending_product ON media_uploads(product_id,expires_at) WHERE completed_at IS NULL;
CREATE INDEX media_uploads_expiry ON media_uploads(expires_at,id);
