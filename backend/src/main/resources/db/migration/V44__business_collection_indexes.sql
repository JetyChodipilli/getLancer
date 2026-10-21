-- Tenant and recipient prefixes keep retained business collections seekable in their stable order.
CREATE INDEX business_invitations_recipient_page ON business_invitations(user_id,created_at DESC,id);
CREATE INDEX business_invitations_workspace_page ON business_invitations(business_id,created_at DESC,id);
CREATE INDEX business_members_workspace_page ON business_members(business_id,role,user_id);
CREATE INDEX talent_lists_workspace_page ON talent_lists(business_id,created_at DESC,id);
CREATE INDEX talent_entries_list_page ON talent_entries(list_id,created_at DESC,id);
CREATE INDEX request_shortlist_request_page ON request_shortlist(request_id,created_at DESC,id);
CREATE INDEX request_shortlist_concierge_proof ON request_shortlist(request_id) WHERE source='CONCIERGE';
CREATE INDEX concierge_actionable_page ON concierge_requests((CASE WHEN status='REQUESTED' THEN 0 ELSE 1 END),created_at,id) WHERE status IN ('REQUESTED','IN_PROGRESS');
CREATE INDEX concierge_fulfilled_page ON concierge_requests(created_at DESC,id) WHERE status='FULFILLED';
