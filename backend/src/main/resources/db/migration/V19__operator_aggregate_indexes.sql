CREATE INDEX analytics_operator_cohort ON analytics_events(source,event_name,created_at);
CREATE INDEX inquiries_qualified_cohort ON inquiries(email_confirmed_at) WHERE email_confirmed_at IS NOT NULL;
CREATE INDEX reports_operator_window ON reports(created_at);
CREATE INDEX appeals_operator_window ON moderation_appeals(created_at);
CREATE INDEX moderation_operator_window ON moderation_actions(created_at);
