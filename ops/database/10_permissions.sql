-- Execute after EVERY migration with the database administrator; bounded to the reviewed app schema.
-- SET getlancer.app_schema = 'getlancer'; before executing this script.
-- This fixed backend role is trusted for all rows. User/tenant authorization remains in the API.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
DO $permissions$
DECLARE
  schema_name text := current_setting('getlancer.app_schema', true);
  known_tables text[] := ARRAY['account_tokens','analytics_events','analytics_instrumentation','business_activity','business_invitations','business_members','business_requests','businesses','categories','college_project_metadata','commerce_audit','commerce_disputes','commerce_ledger','commerce_provider_disputes','commerce_webhook_events','component_audit','component_entries','component_slot_events','component_slot_ledger','component_slot_pricing','component_slot_purchases','concierge_requests','deletion_requests','delivery_activity','delivery_agreements','delivery_disputes','delivery_engagements','delivery_milestones','delivery_proposals','developer_profiles','earned_capacity_awards','email_outbox','hosted_demos','hosting_audit','inquiries','inquiry_events','legal_acceptances','login_challenges','maintenance_audit','maintenance_ledger','maintenance_offers','maintenance_periods','maintenance_provider_disputes','maintenance_refunds','maintenance_requests','maintenance_subscriptions','maintenance_webhook_events','media_uploads','moderation_actions','moderation_appeals','notifications','oauth_identities','oauth_pending','payment_account_audit','payment_accounts','payment_attempts','payment_ledger','payment_provider_disputes','payment_webhook_events','product_access_grants','product_categories','product_media','product_technologies','products','publishing_capacity_grants','rate_buckets','reports','repository_verifications','request_shortlist','reviews','saved_products','security_audit_events','sessions','showcase_entitlements','source_templates','source_versions','storage_deletions','talent_entries','talent_lists','team_activity','team_applications','team_invitations','team_lead_notes','team_leads','team_members','team_projects','team_roles','team_staffing','teams','technologies','template_purchases','user_roles','users'];
  read_only_tables text[] := ARRAY['analytics_instrumentation','publishing_capacity_grants'];
  update_tables text[] := ARRAY['categories','technologies','product_access_grants','request_shortlist','account_tokens','business_invitations','business_requests','businesses','college_project_metadata','commerce_disputes','commerce_provider_disputes','component_entries','component_slot_pricing','component_slot_purchases','concierge_requests','deletion_requests','delivery_disputes','delivery_engagements','delivery_milestones','delivery_proposals','developer_profiles','email_outbox','hosted_demos','inquiries','login_challenges','maintenance_offers','maintenance_periods','maintenance_provider_disputes','maintenance_refunds','maintenance_requests','maintenance_subscriptions','maintenance_webhook_events','media_uploads','moderation_appeals','notifications','oauth_pending','payment_accounts','payment_attempts','payment_provider_disputes','product_media','products','rate_buckets','reports','repository_verifications','reviews','showcase_entitlements','source_templates','source_versions','team_applications','team_invitations','team_leads','team_members','team_roles','team_staffing','teams','template_purchases','users'];
  delete_tables text[] := ARRAY['account_tokens','analytics_events','business_members','login_challenges','media_uploads','notifications','oauth_identities','oauth_pending','product_access_grants','product_categories','product_media','product_technologies','rate_buckets','repository_verifications','request_shortlist','saved_products','sessions','storage_deletions','talent_entries','team_members','team_projects'];
  -- PostgreSQL row locking needs UPDATE on at least one column, even for SELECT FOR SHARE.
  lock_tables text[] := ARRAY['user_roles','sessions','business_members'];
  lock_columns text[] := ARRAY['user_id','token_hash','business_id'];
  object record; table_name text; operation text; role_name text; lock_column text;
BEGIN
  IF schema_name IS NULL OR schema_name !~ '^[a-z][a-z0-9_]{0,62}$' OR schema_name='public' THEN
    RAISE EXCEPTION 'Set a safe private getlancer.app_schema';
  END IF;
  IF NOT EXISTS(SELECT FROM pg_namespace WHERE nspname=schema_name AND nspowner=(SELECT oid FROM pg_roles WHERE rolname='getlancer_migration')) THEN
    RAISE EXCEPTION 'Provision roles/schema before permissions';
  END IF;
  IF EXISTS(SELECT FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname=schema_name
      AND c.relkind IN ('r','p') AND c.relname<>'flyway_schema_history' AND NOT c.relname=ANY(known_tables)) THEN
    RAISE EXCEPTION 'Unclassified application table: review and extend the explicit permission list before provisioning';
  END IF;
  IF (SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname=schema_name AND c.relkind IN ('r','p')) > 128 THEN
    RAISE EXCEPTION 'Application table bound exceeded';
  END IF;
  FOREACH role_name IN ARRAY ARRAY['anon','authenticated'] LOOP
    IF EXISTS(SELECT FROM pg_roles WHERE rolname=role_name) THEN
      EXECUTE format('REVOKE ALL ON SCHEMA %I FROM %I',schema_name,role_name);
    END IF;
  END LOOP;
  -- Explicitly transfer legacy owner-created tables/sequences; never REASSIGN OWNED across schemas.
  FOR object IN SELECT c.relname,c.relkind FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
      WHERE n.nspname=schema_name AND c.relkind IN ('r','p','S') LOOP
    EXECUTE format('ALTER %s %I.%I OWNER TO getlancer_migration', CASE WHEN object.relkind='S' THEN 'SEQUENCE' ELSE 'TABLE' END,schema_name,object.relname);
    -- Table REVOKE also resets corresponding column privileges before the narrow lock grants are rebuilt.
    EXECUTE format('REVOKE ALL ON %I.%I FROM PUBLIC,getlancer_runtime,getlancer_backup,getlancer_readonly',schema_name,object.relname);
    FOREACH role_name IN ARRAY ARRAY['anon','authenticated'] LOOP
      IF EXISTS(SELECT FROM pg_roles WHERE rolname=role_name) THEN
        EXECUTE format('REVOKE ALL ON %I.%I FROM %I',schema_name,object.relname,role_name);
      END IF;
    END LOOP;
  END LOOP;
  FOR object IN SELECT p.oid::regprocedure AS signature FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname=schema_name LOOP
    EXECUTE format('ALTER FUNCTION %s OWNER TO getlancer_migration',object.signature);
    EXECUTE format('REVOKE EXECUTE ON FUNCTION %s FROM PUBLIC,getlancer_runtime,getlancer_backup,getlancer_readonly',object.signature);
  END LOOP;
  FOREACH table_name IN ARRAY known_tables LOOP
    IF NOT EXISTS(SELECT FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname=schema_name AND c.relname=table_name AND c.relkind IN ('r','p')) THEN CONTINUE; END IF;
    EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY',schema_name,table_name);
    FOREACH operation IN ARRAY ARRAY['select','insert','update','delete'] LOOP
      EXECUTE format('DROP POLICY IF EXISTS %I ON %I.%I','getlancer_service_'||operation,schema_name,table_name);
    END LOOP;
    EXECUTE format('GRANT SELECT ON %I.%I TO getlancer_runtime,getlancer_backup,getlancer_readonly',schema_name,table_name);
    EXECUTE format('CREATE POLICY getlancer_service_select ON %I.%I FOR SELECT TO getlancer_runtime,getlancer_backup,getlancer_readonly USING (true)',schema_name,table_name);
    IF NOT table_name=ANY(read_only_tables) THEN
      EXECUTE format('GRANT INSERT ON %I.%I TO getlancer_runtime',schema_name,table_name);
      EXECUTE format('CREATE POLICY getlancer_service_insert ON %I.%I FOR INSERT TO getlancer_runtime WITH CHECK (true)',schema_name,table_name);
    END IF;
    IF table_name=ANY(update_tables) THEN
      EXECUTE format('GRANT UPDATE ON %I.%I TO getlancer_runtime',schema_name,table_name);
      EXECUTE format('CREATE POLICY getlancer_service_update ON %I.%I FOR UPDATE TO getlancer_runtime USING (true) WITH CHECK (true)',schema_name,table_name);
    ELSIF table_name=ANY(lock_tables) THEN
      lock_column := lock_columns[array_position(lock_tables,table_name)];
      EXECUTE format('GRANT UPDATE (%I) ON %I.%I TO getlancer_runtime',lock_column,schema_name,table_name);
      -- USING permits locking the existing row. WITH CHECK false rejects every actual UPDATE.
      EXECUTE format('CREATE POLICY getlancer_service_update ON %I.%I FOR UPDATE TO getlancer_runtime USING (true) WITH CHECK (false)',schema_name,table_name);
    END IF;
    IF table_name=ANY(delete_tables) THEN
      EXECUTE format('GRANT DELETE ON %I.%I TO getlancer_runtime',schema_name,table_name);
      EXECUTE format('CREATE POLICY getlancer_service_delete ON %I.%I FOR DELETE TO getlancer_runtime USING (true)',schema_name,table_name);
    END IF;
  END LOOP;
  IF to_regclass(format('%I.flyway_schema_history',schema_name)) IS NOT NULL THEN
    EXECUTE format('GRANT SELECT ON %I.flyway_schema_history TO getlancer_backup,getlancer_readonly',schema_name);
  END IF;
  -- Application IDs are UUIDs; no runtime sequence mutation or function execution is needed.
  -- PostgreSQL trigger functions execute through their triggers; callers need no EXECUTE grant.
END $permissions$;
COMMIT;
