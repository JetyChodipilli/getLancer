ALTER TABLE developer_profiles ADD COLUMN availability_confirmed_at timestamptz;
ALTER TABLE products ADD COLUMN demo_health varchar(20) NOT NULL DEFAULT 'UNKNOWN' CHECK(demo_health IN ('UNKNOWN','REACHABLE','UNREACHABLE','BLOCKED'));
ALTER TABLE products ADD COLUMN demo_checked_at timestamptz;
ALTER TABLE products ADD COLUMN demo_checked_url text;
CREATE TABLE repository_verifications (
 product_id uuid PRIMARY KEY REFERENCES products,
 repository_url text NOT NULL,
 challenge varchar(100) NOT NULL,
 status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','VERIFIED','REJECTED')),
 requested_at timestamptz NOT NULL DEFAULT now(),
 expires_at timestamptz NOT NULL DEFAULT now()+interval '7 days',
 reviewed_at timestamptz,
 reviewer_id uuid REFERENCES users,
 reason text
);
CREATE TABLE earned_capacity_awards (
 inquiry_id uuid PRIMARY KEY REFERENCES inquiries,
 user_id uuid NOT NULL REFERENCES users,
 admin_id uuid NOT NULL REFERENCES users,
 reason text NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX demo_health_due ON products(demo_checked_at) WHERE approval_status='APPROVED' AND lifecycle_status='ACTIVE' AND visibility='PUBLIC';
