# V2.5 — business hiring and sourcing

Source: Vision §11 and Master PRD §11. Business accounts, private client requests, saved talent and concierge sourcing. This is a complete first implementation of the roadmap item, not platform payments, contracts or AI matching.

## Invariants
- A verified active account can create a business. Its creator is OWNER; invited HIRING_MANAGER accounts must accept before gaining access. Only owners manage business settings/membership. The sole owner cannot leave or be removed. Suspended/unverified users lose access at query time.
- Every private request, talent list, shortlist and activity record is scoped to business membership. Private briefs never become public marketplace records or get sent to candidates automatically.
- Requests follow DRAFT → OPEN → CLOSED. Drafts may be edited; open requests may be edited or closed. Closed requests are read-only. Matching is available only for open requests.
- Matches come from approved active public projects owned by approved, verified active builders. Team matches additionally require active approved members, explicit project-owner consent and an active team. Matching uses category and technology evidence with optional AVAILABLE_NOW and manually reviewed repository evidence filters. Reasons describe criteria, never imply identity/security verification or a trust score. No paid ranking. A candidate must match category or technology.
- Named talent lists are shared within a business. Saving/shortlisting never contacts, joins or hires a candidate. Eligibility is rechecked on every read; withdrawn/suspended proof disappears. Entries may be removed even after eligibility is lost.
- Concierge is opt-in per open request. Only the existing MFA administrator may inspect submitted briefs and curate candidates, with a required client-visible reason and audit entry. Status REQUESTED → IN_PROGRESS → FULFILLED; closing a request cancels unfinished sourcing. The administrator cannot browse unrelated draft/private requests.
- No production Java fixtures, seeds, sample candidates or simulated adapters. Frontend preview is isolated in memory. BACKEND_URL redirects all preview routes to their real counterparts; failures/empty responses never substitute samples.

## API, under /api/v1
GET/POST /businesses; GET/PUT /businesses/{id}; POST /businesses/{id}/invitations {email}; POST /business-invitations/{id}/respond {action}; DELETE /businesses/{id}/members/{userId}.
GET/POST /businesses/{id}/requests; PUT /businesses/{id}/requests/{requestId}; GET /businesses/{id}/requests/{requestId}/matches; GET/POST /businesses/{id}/requests/{requestId}/shortlist; DELETE /businesses/{id}/requests/{requestId}/shortlist/{entryId}; POST /businesses/{id}/requests/{requestId}/concierge.
GET/POST /businesses/{id}/talent-lists; GET/POST /businesses/{id}/talent-lists/{listId}/entries; DELETE /businesses/{id}/talent-lists/{listId}/entries/{entryId}.
GET /admin/concierge; GET /admin/concierge/{id}/matches; PATCH /admin/concierge/{id} {status}; POST /admin/concierge/{id}/shortlist {kind,targetId,reason}.

Request fields: title,description,category,technology,budget,timeline,availableOnly,repositoryVerifiedOnly,status. Candidate: kind BUILDER|TEAM, targetId,name,summary,url,reasons[],evidenceCount. JSON camelCase; UUID IDs; list envelopes {items:[]}; errors use existing ApiError. Collections are bounded to 100; matching top 30 is deterministic with category/technology/availability reasons and ID tie-break.

## Delivery and acceptance
Migration V13 follows existing private-schema RLS/revocation policy. Feature package com.getlancer.business contains thin controllers, services and a repository; existing security/session/CSRF and PostgreSQL transactions are reused. Verify tenant isolation, consent, expired invitations, removed users, current candidate eligibility, closed-request immutability, admin MFA, private export projections, empty matches and no demo fallback. Extend Docker HTTP acceptance for V2 and V2.5. Check desktop and mobile reflow/keyboard forms before frontend-only publication. Backend hosting/provider credentials and public-launch operational acceptance remain separate, as requested.
