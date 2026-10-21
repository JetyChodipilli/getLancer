-- Match team collection scopes and stable page ordering without truncating history.
CREATE INDEX team_directory_page ON teams(created_at DESC,id);
CREATE INDEX team_invitation_team_page ON team_invitations(team_id,created_at DESC,id);
CREATE INDEX team_invitation_user_page ON team_invitations(user_id,created_at DESC,id);
CREATE INDEX team_role_page ON team_roles(team_id,created_at DESC,id);
CREATE INDEX team_application_team_page ON team_applications(team_id,created_at DESC,id);
CREATE INDEX team_application_user_page ON team_applications(user_id,created_at DESC,id);
CREATE INDEX team_lead_client_page ON team_leads(client_id,created_at DESC,id);
CREATE INDEX team_note_page ON team_lead_notes(lead_id,created_at,id);
CREATE INDEX team_staffing_page ON team_staffing(team_id,created_at DESC,id);
CREATE INDEX team_activity_scope_page ON team_activity(team_id,scope,created_at DESC,id);
