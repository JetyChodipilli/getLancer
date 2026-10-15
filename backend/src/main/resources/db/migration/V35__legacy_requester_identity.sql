-- Earlier anonymization erased users.email without clearing email-bound authority.
-- There is no immutable requester ID from which to recover those historical links.
-- Preserve ambiguous contact data for operator review, never assign it to a new holder.
ALTER TABLE inquiries ADD COLUMN IF NOT EXISTS legacy_client_email varchar(254);

UPDATE inquiries i
SET legacy_client_email=i.client_email,
    client_email='closed-legacy-' || i.id::text || '@example.invalid',
    moderation_status='QUARANTINED',
    moderation_reason='Legacy requester identity requires operator verification before access can be restored.',
    updated_at=now()
WHERE i.legacy_client_email IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM users u WHERE u.email=i.client_email
      AND u.account_status='ACTIVE' AND u.created_at<=i.created_at
  );

DELETE FROM account_tokens t USING inquiries i
WHERE t.inquiry_id=i.id AND i.legacy_client_email IS NOT NULL;

-- The only application grant issuer sets/renews expires_at to now()+7 days.
-- Retain only accounts predating that issue time; uncertain guest grants must be reissued.
DELETE FROM product_access_grants g
WHERE NOT EXISTS (
  SELECT 1 FROM users u WHERE u.email=g.client_email AND u.account_status='ACTIVE'
    AND u.created_at<=g.expires_at-interval '7 days'
);
