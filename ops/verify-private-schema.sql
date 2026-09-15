-- Run after Flyway in the dedicated staging schema. No user data is selected.
SELECT current_schema() AS application_schema;
SELECT tablename,rowsecurity FROM pg_tables WHERE schemaname=current_schema() ORDER BY tablename;
SELECT grantee,table_name,privilege_type FROM information_schema.role_table_grants
WHERE table_schema=current_schema() AND grantee IN ('anon','authenticated','PUBLIC');
-- The grants query must return zero application-table grants. The Flyway
-- history table is operational metadata, not a browser-facing resource.
