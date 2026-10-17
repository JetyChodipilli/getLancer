-- A consumed upload remains consumed after its media row is removed.
ALTER TABLE media_uploads ADD COLUMN completed_at timestamptz;
UPDATE media_uploads SET completed_at=created_at WHERE completed_media_id IS NOT NULL;

-- Commit deterministic final-object cleanup intent before external writes.
-- No upload FK: expiry of staging metadata must not erase final-object recovery.
CREATE TABLE media_write_intents (
 id uuid PRIMARY KEY,
 product_id uuid NOT NULL REFERENCES products,
 upload_id uuid UNIQUE,
 expires_at timestamptz NOT NULL,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX media_write_intents_expiry ON media_write_intents(expires_at,id);
CREATE INDEX media_write_intents_product ON media_write_intents(product_id);
ALTER TABLE media_write_intents ENABLE ROW LEVEL SECURITY;
