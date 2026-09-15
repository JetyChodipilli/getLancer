-- Defense in depth if this database is hosted on Supabase. Application sessions
-- are enforced by Spring; anonymous/authenticated Data API roles get no grants.
-- Limit changes to this app's explicit tables, never Supabase-managed schemas.
DO $$
DECLARE t text; r text;
BEGIN
 FOREACH t IN ARRAY ARRAY['users','user_roles','developer_profiles','showcase_entitlements','products','product_media','saved_products','inquiries','inquiry_events','reviews','sessions','account_tokens','email_outbox','reports','moderation_actions','notifications','analytics_events','rate_buckets','categories','technologies','legal_acceptances','deletion_requests','oauth_pending','login_challenges','storage_deletions','product_access_grants','media_uploads','moderation_appeals','product_categories','product_technologies','oauth_identities'] LOOP
  IF to_regclass(format('%I.%I',current_schema(),t)) IS NOT NULL THEN
   EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY',current_schema(),t);
   EXECUTE format('REVOKE ALL ON TABLE %I.%I FROM PUBLIC',current_schema(),t);
   FOREACH r IN ARRAY ARRAY['anon','authenticated'] LOOP
    IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname=r) THEN
     EXECUTE format('REVOKE ALL ON TABLE %I.%I FROM %I',current_schema(),t,r);
    END IF;
   END LOOP;
  END IF;
 END LOOP;
END $$;
