-- Project published filters once, when source is written, rather than detoasting
-- every component's source files/archive for each catalogue count or filter.
-- Stored generated columns also backfill existing snapshots during this migration.
CREATE FUNCTION component_filter_metadata(source jsonb) RETURNS jsonb
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE SET search_path=pg_catalog AS $$
  SELECT jsonb_strip_nulls(jsonb_build_object(
    'category',source->'category','kind',source->'kind',
    'language',source->'language','framework',source->'framework',
    'license',source->'license','executionMode',source->'executionMode'));
$$;
REVOKE EXECUTE ON FUNCTION component_filter_metadata(jsonb) FROM PUBLIC;

ALTER TABLE component_entries ADD COLUMN published_filter_metadata jsonb
  GENERATED ALWAYS AS (component_filter_metadata(published_source)) STORED;
ALTER TABLE component_releases ADD COLUMN filter_metadata jsonb
  GENERATED ALWAYS AS (component_filter_metadata(source)) STORED;
