# V2 implementation contract
Source: docs/01_VISION section 11; 02_FUNCTIONAL_REQUIREMENTS section 18; 04_BUSINESS_RULES_STATE_MACHINES sections 15–16; 07_DATA_MODEL future tables; 08_UI_UX section 19.

## Scope
Teams/studios, membership permissions, search/invite, open roles/applications, temporary staffing, team client interest and shared lead CRM. One account can act as builder and client and belong to multiple teams. No payments, legal acceptance, contracts, template commerce or V2.5 matching. Existing V1 rules and individual showcase capacity remain unchanged.

## Security and lifecycle
Creation requires approved, email-verified builder with at least one approved product. Team starts ACTIVE, owner is creator. Team owner may edit profile and manage membership roles; cannot remove last owner. Roles OWNER, BUSINESS_MANAGER, RECRUITER, PROJECT_MANAGER, MEMBER. Owner alone changes roles. OWNER/BUSINESS_MANAGER manage commercial leads; OWNER/RECRUITER recruit; OWNER/PROJECT_MANAGER manage staffing. Invitations and applications require recipient/applicant consent; no invitation automatically joins anyone. Contract membership requires expiry and optional project label; expired members lose access at query time. Membership is not employment. Public views contain only active approved builders and public approved products; never private lead notes, emails or applications. Team showcase aggregation requires explicit product owner consent, no ownership takeover. Moderated/suspended teams are not public and cannot recruit or receive new leads. Client interest is recorded, never a legally binding acceptance. Authenticated email-verified clients can submit team interest; team members cannot self-inquire. Review display is aggregated existing verified member reviews and explicitly attributed, not new team-verified reviews.

## API (all prefixed /api/v1; errors follow Support.ApiError)
JSON is camelCase; UUID ids; lists {items:[...]}; dates ISO strings or null. Mutations return object or {ok:true}. Read/mutation authorization enforced server-side.
- GET /teams?q= => public teams. POST /teams {name,summary,availability,projectRange} => team.
- GET /teams/{id} => public team detail: team + members[], projects[], roles[], reviews[].
- GET /me/teams => {items:team[], invitations:invite[], applications:application[], requests:lead[]}.
- GET /teams/{id}/workspace => {team,myRole,members,invitations,roles,applications,leads,projects,staffing,activity} (private arrays role-filtered).
- PUT /teams/{id} {name,summary,availability,projectRange} owner.
- GET /teams/{id}/candidates?q= => {items:[{id,name,headline,skills}]} recruiter scoped; approved public builders.
- POST /teams/{id}/invitations {userId,role,membershipType,expiresAt,projectLabel} -> invitation. Owner can invite any non-OWNER role; recruiter only MEMBER.
- POST /team-invitations/{id}/respond {action:ACCEPT|DECLINE}; only recipient, expiry enforced.
- PATCH /teams/{id}/members/{userId} {role} owner; no owner escalation through generic patch. DELETE same: owner removes other non-owner member, member may leave except last owner.
- POST /teams/{id}/roles {title,description,skills,contractType,compensationBand} owner/recruiter -> open role.
- PATCH /teams/{id}/roles/{roleId} {status:OPEN|CLOSED} owner/recruiter.
- POST /teams/{id}/roles/{roleId}/applications {message} approved builder -> application.
- POST /teams/{id}/applications/{applicationId}/decision {status:SHORTLISTED|REJECTED|INVITED} owner/recruiter. INVITED creates MEMBER invitation; never automatic membership.
- POST /teams/{id}/leads {title,description,budget,timeline} verified non-member client -> lead.
- PATCH /teams/{id}/leads/{leadId} {status:NEW|INTERESTED|NEEDS_INFORMATION|DECLINED|PROPOSAL_SENT|WON|LOST,assigneeId,followUpAt,note} owner/business manager. Notes append to audit activity; WON is externally reported outcome, not verified completion/payment. Validate allowed transitions and active assignee. No synthetic verified reviews.
- POST /teams/{id}/leads/{leadId}/respond {action:ACCEPT|DECLINE} authenticated verified original client only, from PROPOSAL_SENT; records reported WON/LOST without legal acceptance or verified completion.
- POST /teams/{id}/projects {productId} active member who owns approved public product; DELETE /teams/{id}/projects/{productId} original owner or team owner.
- POST /teams/{id}/staffing {userId,projectLabel,skills,endsAt} owner/project manager; active member required; expiry required. PATCH /teams/{id}/staffing/{staffingId} {status:ACTIVE|COMPLETED} same role.
- GET /admin/teams => {items}; POST /admin/teams/{id}/moderate {status:ACTIVE|SUSPENDED,reason} MFA admin with audit.

Team: {id,slug,name,summary,availability,projectRange,ownerId,status,memberCount,roleCount,myRole?}.
Member: {userId,name,role,membershipType,expiresAt,projectLabel}.
Invitation: {id,teamId,teamName,userId,name,role,membershipType,expiresAt,projectLabel,status}.
OpenRole: {id,teamId,title,description,skills,contractType,compensationBand,status}.
Application: {id,teamId,roleId,roleTitle,userId,name,message,status}.
Lead: {id,teamId,teamName,clientId,clientName,title,description,budget,timeline,status,assigneeId,followUpAt,notes:[{text,createdAt,actorName}]}.
Project: existing Product DTO. Staffing: {id,userId,name,projectLabel,skills,endsAt,status}.
Activity: {id,action,actorName,detail,createdAt}; never secrets.

## Frontend and demo
Routes /teams (public directory), /teams/[id] (public team/recruitment/interest), /workspace/teams (real workspace), /preview/teams (isolated interactive UAT).
Shared component uses async request(path,init) with normal api() in real mode and local demo adapter in preview. Demo adapter lib/team-demo.ts exports createTeamDemo(): {request:(path:string,init?:RequestInit)=>Promise<any>, reset:()=>void, setActor:(id:string)=>void, actors:{id:string,name:string,role:string}[]}. Demo in memory only; no real API, mail or account writes. Demo ids can be readable strings. Demo must implement above API and permission failures, with owner/recruiter/member/client roles and clear sample disclosure.
Design preserves white/cobalt Inter system, horizontal workspace sections, labeled forms, visible pending/errors, keyboard-operable actions. No changes to homepage masonry or auth.
