-- Optional, public builder details. Existing approvals and content are preserved.
ALTER TABLE developer_profiles ADD COLUMN website_url text NOT NULL DEFAULT '';
ALTER TABLE developer_profiles ADD COLUMN country varchar(2) NOT NULL DEFAULT '';
ALTER TABLE developer_profiles ADD COLUMN time_zone varchar(100) NOT NULL DEFAULT '';
ALTER TABLE developer_profiles ADD COLUMN languages varchar(200) NOT NULL DEFAULT '';

-- Reviews remain anonymous unless the client explicitly chooses their name.
ALTER TABLE reviews ADD CONSTRAINT review_identity_choice
 CHECK (visibility IN ('ANONYMOUS', 'NAMED'));
