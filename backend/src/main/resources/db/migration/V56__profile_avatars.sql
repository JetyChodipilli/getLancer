CREATE TABLE profile_avatars (
  user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  id uuid NOT NULL UNIQUE,
  image bytea NOT NULL CHECK (octet_length(image) BETWEEN 1 AND 1048576)
);

ALTER TABLE profile_avatars ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON profile_avatars FROM PUBLIC;
DO $$
BEGIN
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN
    REVOKE ALL ON profile_avatars FROM anon;
  END IF;
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN
    REVOKE ALL ON profile_avatars FROM authenticated;
  END IF;
END $$;
