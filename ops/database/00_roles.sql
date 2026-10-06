-- Run as the database administrator in the target database. No passwords are embedded.
-- SET getlancer.app_schema = 'getlancer'; before executing this script.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
DO $roles$
DECLARE schema_name text := current_setting('getlancer.app_schema', true); role_name text;
BEGIN
  IF schema_name IS NULL OR schema_name !~ '^[a-z][a-z0-9_]{0,62}$' OR schema_name='public' THEN
    RAISE EXCEPTION 'Set a safe private getlancer.app_schema';
  END IF;
  FOREACH role_name IN ARRAY ARRAY['getlancer_migration','getlancer_runtime','getlancer_backup','getlancer_readonly'] LOOP
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname=role_name) THEN
      EXECUTE format('CREATE ROLE %I NOLOGIN', role_name);
    END IF;
    EXECUTE format('ALTER ROLE %I NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT', role_name);
    IF EXISTS(SELECT FROM pg_auth_members WHERE member=(SELECT oid FROM pg_roles WHERE rolname=role_name) OR roleid=(SELECT oid FROM pg_roles WHERE rolname=role_name)) THEN
      RAISE EXCEPTION 'getLancer roles must not inherit or SET ROLE into other roles';
    END IF;
  END LOOP;
  EXECUTE format('REVOKE CREATE, TEMPORARY ON DATABASE %I FROM PUBLIC', current_database());
  EXECUTE format('GRANT CONNECT ON DATABASE %I TO getlancer_migration,getlancer_runtime,getlancer_backup,getlancer_readonly', current_database());
  REVOKE CREATE ON SCHEMA public FROM PUBLIC;
  EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I AUTHORIZATION getlancer_migration', schema_name);
  EXECUTE format('ALTER SCHEMA %I OWNER TO getlancer_migration', schema_name);
  EXECUTE format('REVOKE ALL ON SCHEMA %I FROM PUBLIC,getlancer_runtime,getlancer_backup,getlancer_readonly', schema_name);
  EXECUTE format('GRANT USAGE ON SCHEMA %I TO getlancer_runtime,getlancer_backup,getlancer_readonly', schema_name);
  -- Future objects have no service privileges until the reviewed provisioning script is rerun.
  EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE getlancer_migration IN SCHEMA %I REVOKE ALL ON TABLES FROM PUBLIC,getlancer_runtime,getlancer_backup,getlancer_readonly',schema_name);
  EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE getlancer_migration IN SCHEMA %I REVOKE ALL ON SEQUENCES FROM PUBLIC,getlancer_runtime,getlancer_backup,getlancer_readonly',schema_name);
  -- Function EXECUTE defaults come from the global setting; a per-schema REVOKE cannot remove them.
  ALTER DEFAULT PRIVILEGES FOR ROLE getlancer_migration REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC,getlancer_runtime,getlancer_backup,getlancer_readonly;
  -- Also undo any previously added per-schema function grants.
  EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE getlancer_migration IN SCHEMA %I REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC,getlancer_runtime,getlancer_backup,getlancer_readonly',schema_name);
END $roles$;
COMMIT;
