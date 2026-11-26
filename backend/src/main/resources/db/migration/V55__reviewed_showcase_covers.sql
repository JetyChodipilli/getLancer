ALTER TABLE products
  ADD COLUMN cover_image bytea,
  ADD COLUMN cover_type text,
  ADD COLUMN draft_details jsonb,
  ADD COLUMN draft_cover_image bytea,
  ADD COLUMN draft_cover_type text,
  ADD COLUMN draft_status text CHECK (draft_status IN ('DRAFT','PENDING_REVIEW','CHANGES_REQUESTED','REJECTED')),
  ADD COLUMN draft_review_id uuid;

CREATE INDEX products_draft_review ON products(updated_at,id) WHERE draft_status='PENDING_REVIEW';

ALTER TABLE component_entries
  ADD COLUMN draft_cover_image bytea,
  ADD COLUMN draft_cover_type text,
  ADD COLUMN submitted_cover_image bytea,
  ADD COLUMN submitted_cover_type text,
  ADD COLUMN published_cover_image bytea,
  ADD COLUMN published_cover_type text;
