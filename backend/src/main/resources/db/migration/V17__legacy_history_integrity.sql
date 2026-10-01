-- History predating the delivery/finance ledgers must be append-only too.
CREATE FUNCTION protect_legacy_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'Marketplace history is append-only';
END;
$$;
CREATE TRIGGER inquiry_events_append_only
  BEFORE UPDATE OR DELETE ON inquiry_events
  FOR EACH ROW EXECUTE FUNCTION protect_legacy_history();
CREATE TRIGGER moderation_actions_append_only
  BEFORE UPDATE OR DELETE ON moderation_actions
  FOR EACH ROW EXECUTE FUNCTION protect_legacy_history();
