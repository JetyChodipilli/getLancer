# API authorization matrix

This source-derived matrix covers **256 API method/route entries and 3 health entries** in [api-authorization-policy.txt](../backend/src/main/resources/api-authorization-policy.txt). It describes the current V1–V4.5 backend contract. It is an inventory and source review, not evidence that CI or production verification passed. Every row has the eleven fields required by security-plan section 6; the last two columns identify domain-state gates and their implementation.

## Shared contract and legend

[AuthorizationService] selects the most specific explicit route; unknown routes deny by default. `HEAD` inherits the corresponding `GET` policy. `OPTIONS` is preflight only: exact configured origin, known requested method/route, and only Content-Type, X-Requested-With and Idempotency-Key headers; it returns no domain data. All controller classes use the same declarative route policy. The Public/private column includes the outer policy; stronger service conditions appear in the other columns.

**Authentication:** `A` permits anonymous entry; `S` is the opaque `gl_session` cookie, unexpired and bound to an ACTIVE account; `SV` additionally requires verified email. `C` is a hashed, expiring email capability bound to its subject/action, not a session. `W` is provider HMAC over exact raw request bytes, verified before parsing. `G` is the constant-time checked X-GetLancer-Demo-Gateway secret. OAuth callbacks require the one-use, unexpired state and matching browser cookie as well as provider identity validation and PKCE. A public entry with a capability requirement does not expose its protected data to arbitrary callers.

**Roles and ownership:** `AD` means DEVELOPER plus verified email and APPROVED builder profile. `—` means no additional gate in that column, not unrestricted access to the entire row. IDs alone never satisfy an owner/party gate. `PP` means APPROVED, ACTIVE, PUBLIC product, ACTIVE account and APPROVED profile ([ProductRepository]); private grants bind the verified principal email and must not be expired. Owner checks refer to current database identity, not client-supplied owner fields.

**Business/team membership:** `BM` is current OWNER/HIRING_MANAGER membership with active verified account ([BusinessRepository]). `BO` requires OWNER. `TM` is current unexpired team membership with active verified account and approved profile ([TeamRepository]); `TO` is TM OWNER; `TR` is OWNER/RECRUITER; `TC` is OWNER/BUSINESS_MANAGER; `TP` is OWNER/PROJECT_MANAGER ([TeamPolicy]). Team `ACTIVE` status is a separate gate where the service requires it; membership alone does not imply it.

**Engagement parties:** `EB` is the named CLIENT buyer or current verified business OWNER/HIRING_MANAGER. `ES` is the named approved verified DEVELOPER seller or approved verified current team OWNER/BUSINESS_MANAGER in an ACTIVE team. `EP` is EB or ES, never both. [DeliveryRepository] rechecks these authorities under party/engagement locks; ordinary team MEMBER/RECRUITER/PROJECT_MANAGER membership grants no commercial party authority. `TS` means the ES team authority. `ME` additionally requires completed fully paid work, current eligible consent parties and no financial holds ([MaintenanceAccess], [MaintenanceRepository]); frozen recurring-billing consents are rechecked separately. `HS` is the current hosting servability predicate ([HostingRepository]).

**MFA:** `M` means ADMIN with an MFA-verified session; `M15` also requires its session issuance within 15 minutes. Every unsafe ADMIN_MFA operation and administrator package download uses M15. The login/MFA row describes the challenge before a full administrator session exists. Private media accessed through an ADMIN fallback requires M but has no additional recent-MFA gate.

**CSRF:** `B` requires exact configured Origin **and** X-Requested-With: getlancer for every unsafe browser operation, including anonymous auth/capability operations and multipart upload. CORS permits credentials only for that origin. Safe GET/HEAD are `—`; OAuth state/browser binding is still enforced. `W-exempt` is only the four explicit signed webhook routes. [BrowserSecurityFilter] enforces these controls before authentication and bounds mutation bodies to 6 MiB (webhooks 64 KiB); domain upload limits may be smaller.

**Rate classes:** all counters are durable hashed PostgreSQL buckets in [RateLimits], with a one-minute window unless specified. Limits below are configurable defaults, not hardcoded deployment assertions. `D` shares a discovery bucket per trusted client IP for GET/HEAD under products/builders/teams/templates/components/college-projects (300/min). `R` shares a separate security-read IP bucket for other API GET/HEAD (300/min), including OAuth callbacks and failed gateway authentication. `MUT` has an IP + classified route-template bucket and, when a session cookie is supplied, a cookie-hash + route-template bucket (30/min each); path UUIDs cannot create new template buckets. `LOGIN` adds email-identity 10/min; `TOTP` adds eligible ADMIN challenge-user 10/min plus the challenge’s five-attempt ceiling; `EMAIL` adds identity 3/min for reset, verification resend or closure request. `INQUIRY` adds the sender-email five-inquiries/hour + duplicate guard. Successful `G` gateway requests bypass R and have no dedicated application quota (`G-none`); health paths have none (`H-none`). IP means the socket peer unless the constant-time authenticated reverse proxy supplies the permitted client-IP header.

**Audit:** every row inherits `DENY`: 401/403 outcomes reaching the filter and invalid-origin/preflight denials produce AUTHORIZATION_DENIED. Anonymous authorization/login denials share a 1,000/min audit budget; rejected-over-quota requests do not automatically create a success/event record. [SecurityAudit] accepts bounded event names/targets/request UUIDs and excludes request bodies, credentials, tokens, emails and IP addresses. `N` means no success audit at this handler; persisted business objects/analytics are not silently counted as security audit. `*` means conditional on an actual transition/new reservation/fact/identified cookie, with harmless repeats not necessarily emitting another event.

| Audit code | Current success/failure behavior |
|---|---|
| ADMIN | Filter ADMIN_ACTION for unsafe administrator routes after domain completion, with SUCCESS/FAILURE status; no blanket successful administrator-read event. |
| LOGIN / CHALLENGE / MFA | Deferred authentication LOGIN_SUCCESS/LOGIN_FAILURE, MFA_CHALLENGE, MFA_SUCCESS/MFA_FAILURE/MFA_LOCKED at their actual branches. |
| REVOKE | SESSION_REVOKED when logout resolves a supplied session/challenge actor. |
| TOKEN | PASSWORD_RESET or ACCOUNT_DELETED security event for those capability kinds; inquiry decisions use their domain events; verification itself has no universal security success event. |
| EX | Filter PRIVATE_EXPORT for account export, the four owner/admin source-template/hosting package routes, and buyer source download (SUCCESS/FAILURE for completed responses unless 401/403 takes DENY precedence). |
| WH | Filter WEBHOOK_FAILURE for error responses; additional WEBHOOK_SIGNATURE_FAILURE for recognized signature error codes; successful provider event dedup record is domain-specific. |
| RECON / BIND / SET / ROLE / SUSPEND / BM_REMOVE | Selective filter PAYMENT_RECONCILIATION / PAYMENT_MANUAL_BIND / SECURITY_SETTING_CHANGE / ROLE_CHANGED / ACCOUNT_SUSPENDED / BUSINESS_MEMBER_REMOVED on the matching successful route/action. |
| B / T / DV / MN / H / C / CO / MOD | Domain business_activity / team_activity / delivery_activity / maintenance_audit / hosting_audit / commerce_audit / component_audit / moderation_actions at the linked service branch. These are distinct from security_audit_events. |
| I / LED | Inquiry lifecycle events / idempotent financial ledger entries when corresponding facts or transitions are recorded. |

## Route inventory

### AccountService (2 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| GET | `/api/v1/me/export` | Private (SESSION) | SV | — | Self; scoped account export | Own business/team records only | — | — | R | DENY; EX | — | [AccountService.export][AccountService] |
| PATCH | `/api/v1/notifications/{id}/read` | Private (SESSION) | S | — | Notification.user_id = actor | — | — | B | MUT | DENY; N | — | [AccountService.read][AccountService] |

### AdminService (16 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| GET | `/api/v1/admin/accounts` | Private (ADMIN_MFA) | S | ADMIN | — | — | M | — | R | DENY; N | Operator projections; scoped target/filter parameters; bounded pages | [AdminService.accounts][AdminService] |
| GET | `/api/v1/admin/audit` | Private (ADMIN_MFA) | S | ADMIN | — | — | M | — | R | DENY; N | Operator projections; scoped target/filter parameters; bounded pages | [AdminService.history][AdminService] |
| GET | `/api/v1/admin/metrics` | Private (ADMIN_MFA) | S | ADMIN | — | — | M | — | R | DENY; N | Operator projections; scoped target/filter parameters; bounded pages | [AdminService.metrics][AdminService] |
| GET | `/api/v1/admin/products/pending` | Private (ADMIN_MFA) | S | ADMIN | — | — | M | — | R | DENY; N | Operator projections; scoped target/filter parameters; bounded pages | [AdminService.pending][AdminService] |
| GET | `/api/v1/admin/profiles/pending` | Private (ADMIN_MFA) | S | ADMIN | — | — | M | — | R | DENY; N | Operator projections; scoped target/filter parameters; bounded pages | [AdminService.profiles][AdminService] |
| GET | `/api/v1/admin/reports` | Private (ADMIN_MFA) | S | ADMIN | — | — | M | — | R | DENY; N | Operator projections; scoped target/filter parameters; bounded pages | [AdminService.reports][AdminService] |
| GET | `/api/v1/admin/reports/{id}` | Private (ADMIN_MFA) | S | ADMIN | — | — | M | — | R | DENY; MOD* (inquiry VIEW_EVIDENCE) | Report exists; target-specific evidence projection | [AdminService.reportDetail][AdminService] |
| GET | `/api/v1/admin/reviews` | Private (ADMIN_MFA) | S | ADMIN | — | — | M | — | R | DENY; N | Operator projections; scoped target/filter parameters; bounded pages | [AdminService.reviews][AdminService] |
| GET | `/api/v1/admin/{kind:categories\|technologies}` | Private (ADMIN_MFA) | S | ADMIN | — | — | M | — | R | DENY; N | Operator projections; scoped target/filter parameters; bounded pages | [AdminService.allTaxonomy][AdminService] |
| POST | `/api/v1/admin/accounts/{id}/{action}` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; MOD / SUSPEND* | suspend/restore; ADMIN protected; DELETED cannot restore; revokes sessions on suspend | [AdminService.accountAction][AdminService] |
| POST | `/api/v1/admin/inquiries/{id}/{action}` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; MOD / I | quarantine/block/restore; target inquiry exists; non-CLEAR revokes unused inquiry capabilities | [AdminService.inquiryAction][AdminService] |
| POST | `/api/v1/admin/products/{id}/{action}` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; MOD | approve/reject/request-changes/suspend state gates; public activation rechecks owner/profile/capacity | [AdminService.productAction][AdminService] |
| POST | `/api/v1/admin/profiles/{id}/{action}` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; MOD | Profile action/state gates; approve requires active verified account | [AdminService.profileAction][AdminService] |
| POST | `/api/v1/admin/reports/{id}/resolve` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; MOD | Unresolved report; enforcement delegated to target action allowlists | [AdminService.resolve][AdminService] |
| POST | `/api/v1/admin/reviews/{id}/{action}` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; MOD | publish/hide; target review exists; public read visibility separately requires clear confirmed completion | [AdminService.reviewAction][AdminService] |
| POST | `/api/v1/admin/{kind:categories\|technologies}` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; MOD | categories/technologies only; valid taxonomy fields | [AdminService.taxonomy][AdminService] |

### AnalyticsService (1 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| POST | `/api/v1/analytics/events` | Public entry (PUBLIC) | A | — | — | — | — | B | MUT | DENY; N (analytics record only) | Feature enabled; public event allowlist; eligible entity; bounded sanitized context + time; deduplicated event ID | [AnalyticsService.record][AnalyticsService] |

### AuthProviderService (1 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| GET | `/api/v1/auth/providers` | Public entry (PUBLIC) | A | — | — | — | — | — | R | DENY; N | Configured provider projection only | [AuthProviderService.providers][AuthProviderService] |

### AuthService (13 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| DELETE | `/api/v1/me` | Private (SESSION) | S | — | Self | — | — | B | MUT + EMAIL | DENY; N | ADMIN protected; creates emailed closure capability; closure occurs on confirmation | [AuthService.deletion][AuthService] |
| GET | `/api/v1/me` | Private (SESSION) | S | — | Self | — | — | — | R | DENY; N | — | [AuthService.me][AuthService] |
| POST | `/api/v1/auth/confirm` | Public entry (PUBLIC) | C | — | Token-bound user/inquiry; endpoint kind match | — | — | B | MUT | DENY; TOKEN* | Kind-bound token consume; active account for reset/verification; closure protects ADMIN + recurring billing/hosting; inquiry outcomes obey CLEAR/state gates | [AuthService.confirm][AuthService] |
| POST | `/api/v1/auth/confirmation` | Public entry (PUBLIC) | C | — | Token-bound user/inquiry | — | — | B | MUT | DENY; N | Hashed confirmation token; expiry; limited context | [AuthService.confirmationContext][AuthService] |
| POST | `/api/v1/auth/login` | Public entry (PUBLIC) | Password | — | — | — | — | B | MUT + LOGIN | DENY; LOGIN / CHALLENGE | Active account; ADMIN branches to challenge instead of full session | [AuthService.login][AuthService] |
| POST | `/api/v1/auth/login/mfa` | Public entry (PUBLIC) | Challenge + TOTP | ADMIN | Challenge user; bound browser cookie | — | Challenge TOTP | B | MUT + TOTP | DENY; MFA / LOGIN | Unexpired challenge; ≤5 attempts; locked ADMIN; replay-resistant TOTP | [AuthService.mfa][AuthService] |
| POST | `/api/v1/auth/logout` | Public entry (PUBLIC) | A; cookies if present | — | Session/challenge matching supplied cookie | — | — | B | MUT | DENY; REVOKE* | — | [AuthService.logout][AuthService] |
| POST | `/api/v1/auth/password-reset/confirm` | Public entry (PUBLIC) | C | — | Token-bound user/inquiry; endpoint kind match | — | — | B | MUT | DENY; TOKEN | PASSWORD_RESET token; ACTIVE account; one-use; revokes sessions/challenges | [AuthService.confirm][AuthService] |
| POST | `/api/v1/auth/password-reset/request` | Public entry (PUBLIC) | A | — | — | — | — | B | MUT + EMAIL | DENY; N | Generic response; reset token only for eligible account | [AuthService.reset][AuthService] |
| POST | `/api/v1/auth/resend-verification` | Private (SESSION) | S | — | Self; principal email | — | — | B | MUT + EMAIL | DENY; N | Email verification delivery | [AuthService.resend][AuthService] |
| POST | `/api/v1/auth/signup` | Public entry (PUBLIC) | A | — | — | — | — | B | MUT | DENY; N (signup analytics only) | Reserved admin email excluded; explicit legal consent; new CLIENT + DEVELOPER roles | [AuthService.signup][AuthService] |
| POST | `/api/v1/auth/verify-email` | Public entry (PUBLIC) | C | — | Token-bound user/inquiry; endpoint kind match | — | — | B | MUT | DENY; N | EMAIL_VERIFICATION token; ACTIVE account; atomic consume; repeat harmless | [AuthService.confirm][AuthService] |
| POST | `/api/v1/inquiries/confirm-email` | Public entry (PUBLIC) | C | — | Token-bound user/inquiry; endpoint kind match | — | — | B | MUT | DENY; I* / analytics | CLIENT_INQUIRY_CONFIRMATION token; CLEAR eligible sender; unverified inquiry state; atomic consume | [AuthService.confirm][AuthService] |

### BusinessService (7 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| DELETE | `/api/v1/businesses/{id}/members/{userId}` | Private (SESSION) | SV | — | — | BO to remove another; BM to leave | — | B | MUT | DENY; B / BM_REMOVE | Target belongs to business; OWNER cannot be removed | [BusinessService.remove][BusinessService] |
| GET | `/api/v1/businesses` | Private (SESSION) | SV | — | Self invitations | BM: own workspaces | — | — | R | DENY; N | — | [BusinessService.mine][BusinessService] |
| GET | `/api/v1/businesses/{id}` | Private (SESSION) | SV | — | — | BM; invitations shown only to OWNER | — | — | R | DENY; N | — | [BusinessService.workspace][BusinessService] |
| POST | `/api/v1/business-invitations/{id}/respond` | Private (SESSION) | SV | — | Invitation.user_id = actor | — | — | B | MUT | DENY; B | PENDING, unexpired; ACCEPT/DECLINE; adds HIRING_MANAGER only after consent | [BusinessService.respond][BusinessService] |
| POST | `/api/v1/businesses` | Private (SESSION) | SV | — | Actor becomes business OWNER | — | — | B | MUT | DENY; B | — | [BusinessService.create][BusinessService] |
| POST | `/api/v1/businesses/{id}/invitations` | Private (SESSION) | SV | — | — | BO | — | B | MUT | DENY; B | Invite target active + verified; pending/duplicate checks | [BusinessService.invite][BusinessService] |
| PUT | `/api/v1/businesses/{id}` | Private (SESSION) | SV | — | — | BO | — | B | MUT | DENY; B | Invite target active + verified; pending/duplicate checks | [BusinessService.edit][BusinessService] |

### ClientInquiryService (6 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| GET | `/api/v1/me/inquiries` | Private (SESSION) | SV | — | Inquiry.client_email = principal email | — | — | — | R | DENY; N | — | [ClientInquiryService.list][ClientInquiryService] |
| GET | `/api/v1/me/inquiries/{id}` | Private (SESSION) | SV | — | Inquiry.client_email = principal email | — | — | — | R | DENY; N | — | [ClientInquiryService.detail][ClientInquiryService] |
| POST | `/api/v1/me/inquiries/{id}/confirmation-link` | Private (SESSION) | SV | — | Inquiry.client_email = principal email | — | — | B | MUT | DENY; N | CLEAR; sender eligible; only states awaiting confirmation/review | [ClientInquiryService.resend][ClientInquiryService] |
| POST | `/api/v1/me/inquiries/{id}/decision` | Private (SESSION) | SV | — | Inquiry.client_email = principal email | — | — | B | MUT | DENY; I* | CLEAR; hire/completion ACCEPT/REJECT; correct pending state; accepted repeat harmless | [ClientInquiryService.decision][ClientInquiryService] |
| POST | `/api/v1/me/inquiries/{id}/not-hired` | Private (SESSION) | SV | — | Inquiry.client_email = principal email | — | — | B | MUT | DENY; I* | CLEAR; not-hired transition; repeat harmless | [ClientInquiryService.close][ClientInquiryService] |
| POST | `/api/v1/me/inquiries/{id}/review` | Private (SESSION) | SV | — | Inquiry.client_email = principal email | — | — | B | MUT | DENY; N (review + analytics) | CLEAR, client-confirmed COMPLETED; eligible sender; one held review | [ClientInquiryService.review][ClientInquiryService] |

### CommercePaymentService (11 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| GET | `/api/v1/admin/template-purchases/attention` | Private (ADMIN_MFA) | S | ADMIN | — | — | M | — | R | DENY; N | — | [CommercePaymentService.attention][CommercePaymentService] |
| GET | `/api/v1/me/template-purchases` | Private (SESSION) | S | — | Purchase.buyer_id = actor | — | — | — | R | DENY; N | — | [CommercePaymentService.purchases][CommercePaymentService] |
| GET | `/api/v1/template-purchases/{id}/download` | Private (SESSION) | S | — | Purchase.buyer_id = actor | — | — | — | R | DENY; EX / C (SOURCE_DOWNLOADED) | Live CAPTURED; no refund/local/provider hold; approved pinned version; eligible current seller/proof/transfer | [CommercePaymentService.download][CommercePaymentService] |
| POST | `/api/v1/admin/template-purchases/{id}/bind-order` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; C / BIND | Unbound CREATING/UNKNOWN; exact reserved order facts | [CommercePaymentService.bind][CommercePaymentService] |
| POST | `/api/v1/admin/template-purchases/{id}/reconcile` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; LED* / RECON | Original provider mode; authoritative frozen provider facts | [CommercePaymentService.adminReconcile][CommercePaymentService] |
| POST | `/api/v1/admin/template-purchases/{id}/resolve` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; C | RESUME/REVOKE; local decision cannot clear provider refunds/disputes | [CommercePaymentService.resolve][CommercePaymentService] |
| POST | `/api/v1/commerce/razorpay/webhook` | Provider only (SIGNED_WEBHOOK) | W | SIGNED_WEBHOOK | — | — | — | W-exempt | MUT | DENY; WH / LED* | Raw HMAC before parse; event ID/hash dedup; matched order/payment reconciled with provider facts | [CommercePaymentService.webhook][CommercePaymentService] |
| POST | `/api/v1/template-purchases/{id}/disputes` | Private (SESSION) | S | — | Purchase.buyer_id = actor | — | — | B | MUT | DENY; C | Captured payment; one recorded source dispute | [CommercePaymentService.dispute][CommercePaymentService] |
| POST | `/api/v1/template-purchases/{id}/reconcile` | Private (SESSION) | S | — | Purchase.buyer_id = actor | — | — | B | MUT | DENY; LED* / RECON | Original provider mode; verify checkout HMAC; authoritative receipt/payee/amount; no trust in client amount | [CommercePaymentService.reconcile][CommercePaymentService] |
| POST | `/api/v1/template-purchases/{id}/verify` | Private (SESSION) | S | — | Purchase.buyer_id = actor | — | — | B | MUT | DENY; LED* | Original provider mode; verify checkout HMAC; authoritative receipt/payee/amount; no trust in client amount | [CommercePaymentService.verify][CommercePaymentService] |
| POST | `/api/v1/templates/{id}/orders` | Private (SESSION) | S | — | Actor becomes buyer; not template seller | — | — | B | MUT | DENY; C* | Approved current version; eligible verified seller; license consent; frozen server price/payee; idempotency | [CommercePaymentService.order][CommercePaymentService] |

### CommerceService (12 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| GET | `/api/v1/admin/templates` | Private (ADMIN_MFA) | S | ADMIN | — | — | M | — | R | DENY; N | — | [CommerceService.admin][CommerceService] |
| GET | `/api/v1/admin/templates/{id}/versions/{version}/package` | Private (ADMIN_MFA) | S | ADMIN | Administrative access; package belongs to target record | — | M15 | — | R | DENY; EX | Immutable ZIP integrity; central private-export audit after completed response | [CommerceService.packageDownload][CommerceService] |
| GET | `/api/v1/me/templates` | Private (SESSION) | S | — | Template.seller_id = actor | — | — | — | R | DENY; N | — | [CommerceService.own][CommerceService] |
| GET | `/api/v1/me/templates/{id}/versions/{version}/package` | Private (DEVELOPER) | S | DEVELOPER | Template.seller_id = actor; version belongs to template | — | — | — | R | DENY; EX | Immutable ZIP integrity; central private-export audit after completed response | [CommerceService.packageDownload][CommerceService] |
| GET | `/api/v1/templates` | Public entry (PUBLIC) | A | — | — | — | — | — | D | DENY; N | ACTIVE listing; approved release; eligible seller + approved active PUBLIC proof | [CommerceService.catalog][CommerceService] |
| GET | `/api/v1/templates/{slug}` | Public entry (PUBLIC) | A | — | — | — | — | — | D | DENY; N | ACTIVE listing; approved release; eligible seller + approved active PUBLIC proof | [CommerceService.detail][CommerceService] |
| POST | `/api/v1/admin/templates/{id}/versions/{version}/review` | Private (ADMIN_MFA) | S | ADMIN | Version.template_id = route template | — | M15 | B | MUT | DENY; ADMIN; C | Review state; package/rights confirmations; seller eligibility and capacity on approve | [CommerceService.review][CommerceService] |
| POST | `/api/v1/me/templates` | Private (DEVELOPER) | SV | AD | Actor owns linked product and new listing | — | — | B | MUT | DENY; C | Current verified repository + PP; frozen license floor and server price | [CommerceService.create][CommerceService] |
| POST | `/api/v1/me/templates/{id}/activate` | Private (DEVELOPER) | SV | AD | Template.seller_id = actor; version belongs to template | — | — | B | MUT | DENY; C | Verified repository + PP; permitted listing/release state; rights; immutable ZIP; activation capacity | [CommerceService.activate][CommerceService] |
| POST | `/api/v1/me/templates/{id}/archive` | Private (DEVELOPER) | S | DEVELOPER | Template.seller_id = actor | — | — | B | MUT | DENY; C | Suspension preserved; existing eligible purchases remain pinned | [CommerceService.archive][CommerceService] |
| POST | `/api/v1/me/templates/{id}/versions` | Private (DEVELOPER) | SV | AD | Template.seller_id = actor; version belongs to template | — | — | B | MUT | DENY; C | Verified repository + PP; permitted listing/release state; rights; immutable ZIP; activation capacity | [CommerceService.upload][CommerceService] |
| POST | `/api/v1/me/templates/{id}/versions/{version}/submit` | Private (DEVELOPER) | SV | AD | Template.seller_id = actor; version belongs to template | — | — | B | MUT | DENY; C | Verified repository + PP; permitted listing/release state; rights; immutable ZIP; activation capacity | [CommerceService.submit][CommerceService] |

### ComponentService (14 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| GET | `/api/v1/admin/components/review` | Private (ADMIN_MFA) | S | ADMIN | — | — | M | — | R | DENY; N | — | [ComponentService.queue][ComponentService] |
| GET | `/api/v1/admin/components/{id}` | Private (ADMIN_MFA) | S | ADMIN | — | — | M | — | R | DENY; N | — | [ComponentService.adminDetail][ComponentService] |
| GET | `/api/v1/college-projects` | Public entry (PUBLIC) | A | — | — | — | — | — | D | DENY; N | PP + APPROVED college metadata; academic fields honor sharing consent | [ComponentService.college][ComponentService] |
| GET | `/api/v1/college-projects/{slug}` | Public entry (PUBLIC) | A | — | — | — | — | — | D | DENY; N | PP + APPROVED college metadata; academic fields honor sharing consent | [ComponentService.collegeDetail][ComponentService] |
| GET | `/api/v1/components` | Public entry (PUBLIC) | A | — | — | — | — | — | D | DENY; N | Curated seed recipes + ACTIVE eligible builder remixes; BACKEND source-only catalog empty | [ComponentService.catalog][ComponentService] |
| GET | `/api/v1/components/{slug}` | Public entry (PUBLIC) | A | — | — | — | — | — | D | DENY; N | Seed recipe OR published snapshot in ACTIVE/ARCHIVED/DRAFT/PENDING with eligible builder | [ComponentService.detail][ComponentService] |
| GET | `/api/v1/me/college-projects` | Private (DEVELOPER) | S | DEVELOPER | Component owner / product owner = actor | — | — | — | R | DENY; N | — | [ComponentService.ownCollege][ComponentService] |
| GET | `/api/v1/me/components` | Private (DEVELOPER) | S | DEVELOPER | Component owner / product owner = actor | — | — | — | R | DENY; N | — | [ComponentService.own][ComponentService] |
| PATCH | `/api/v1/me/components/{id}` | Private (DEVELOPER) | SV | AD | Component.owner_id = actor | — | — | B | MUT | DENY; CO | Action/edit state allowlist; pinned published source; rights consent for edits | [ComponentService.edit][ComponentService] |
| POST | `/api/v1/admin/college-projects/{id}/review` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; CO | Scoped college metadata; exact revision; APPROVED/CHANGES_REQUESTED/SUSPENDED decision allowlist | [ComponentService.reviewCollege][ComponentService] |
| POST | `/api/v1/admin/components/{id}/review` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; CO | Scoped entry; exact revision; decision/state allowlist; eligible builder + publication capacity on APPROVE; pinned snapshot | [ComponentService.review][ComponentService] |
| POST | `/api/v1/me/components` | Private (DEVELOPER) | SV | AD | Actor owns new entry | — | — | B | MUT | DENY; CO | Curated recipe; rights consent; component publishing capacity | [ComponentService.create][ComponentService] |
| POST | `/api/v1/me/components/{id}/{action}` | Private (DEVELOPER) | SV | AD | Component.owner_id = actor | — | — | B | MUT | DENY; CO | Action/edit state allowlist; pinned published source; rights consent for edits | [ComponentService.action][ComponentService] |
| PUT | `/api/v1/me/college-projects/{id}` | Private (DEVELOPER) | SV | AD | Product.owner_user_id = actor | — | — | B | MUT | DENY; CO | Rights consent; category allowlist; publishing college capacity; new PENDING metadata | [ComponentService.saveCollege][ComponentService] |

### ComponentSlotService (20 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| GET | `/api/v1/admin/component-slot-purchases` | Private (ADMIN_MFA) | S | ADMIN | — | — | M | — | R | DENY; N | Pool-scoped operator purchase queue | [ComponentSlotService.attention][ComponentSlotService] |
| GET | `/api/v1/admin/publishing-slot-purchases` | Private (ADMIN_MFA) | S | ADMIN | — | — | M | — | R | DENY; N | Pool-scoped operator purchase queue | [ComponentSlotService.attention][ComponentSlotService] |
| GET | `/api/v1/components/slots/pricing` | Public entry (PUBLIC) | A | — | — | — | — | — | D | DENY; N | Pool allowlist PROJECT/TEMPLATE/COMPONENT; configured prices | [ComponentSlotService.pricing][ComponentSlotService] |
| GET | `/api/v1/me/component-slot-purchases` | Private (DEVELOPER) | S | DEVELOPER | Purchase.owner_id = actor; requested pool scoped | — | — | — | R | DENY; N | — | [ComponentSlotService.history][ComponentSlotService] |
| GET | `/api/v1/me/publishing-slot-purchases` | Private (DEVELOPER) | S | DEVELOPER | Purchase.owner_id = actor; requested pool scoped | — | — | — | R | DENY; N | — | [ComponentSlotService.history][ComponentSlotService] |
| GET | `/api/v1/me/publishing-slots` | Private (DEVELOPER) | S | DEVELOPER | Purchase.owner_id = actor; requested pool scoped | — | — | — | R | DENY; N | — | [ComponentSlotService.overview][ComponentSlotService] |
| GET | `/api/v1/publishing-slots/pricing` | Public entry (PUBLIC) | A | — | — | — | — | — | R | DENY; N | Pool allowlist PROJECT/TEMPLATE/COMPONENT; configured prices | [ComponentSlotService.allPricing][ComponentSlotService] |
| POST | `/api/v1/admin/component-slot-purchases/{id}/bind-order` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; CO / BIND | Unresolved reservation; provider order matches frozen receipt/amount; no conflicting bind | [ComponentSlotService.bind][ComponentSlotService] |
| POST | `/api/v1/admin/component-slot-purchases/{id}/reconcile` | Private (ADMIN_MFA) | S | ADMIN | Administrative purchase; pool/mode scoped | — | M15 | B | MUT | DENY; ADMIN; CO* / LED* / RECON | Original mode; checkout HMAC on verify; authoritative receipt/amount/provider reconciliation | [ComponentSlotService.reconcile][ComponentSlotService] |
| POST | `/api/v1/admin/publishing-slot-purchases/{id}/bind-order` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; CO / BIND | Unresolved reservation; provider order matches frozen receipt/amount; no conflicting bind | [ComponentSlotService.bind][ComponentSlotService] |
| POST | `/api/v1/admin/publishing-slot-purchases/{id}/reconcile` | Private (ADMIN_MFA) | S | ADMIN | Administrative purchase; pool/mode scoped | — | M15 | B | MUT | DENY; ADMIN; CO* / LED* / RECON | Original mode; checkout HMAC on verify; authoritative receipt/amount/provider reconciliation | [ComponentSlotService.reconcile][ComponentSlotService] |
| POST | `/api/v1/components/razorpay/webhook` | Provider only (SIGNED_WEBHOOK) | W | SIGNED_WEBHOOK | — | — | — | W-exempt | MUT | DENY; WH / CO* / LED* | Exact raw bytes HMAC first; bounded event ID + payload hash dedup; provider facts; pool purchase scope | [ComponentSlotService.webhook][ComponentSlotService] |
| POST | `/api/v1/me/component-slot-purchases` | Private (DEVELOPER) | SV | AD | Actor owns reservation | — | — | B | MUT | DENY; CO* | Pool + idempotency; consent; current server price comparison; enabled provider collection | [ComponentSlotService.order][ComponentSlotService] |
| POST | `/api/v1/me/component-slot-purchases/{id}/reconcile` | Private (DEVELOPER) | S | DEVELOPER | Purchase.owner_id = actor | — | — | B | MUT | DENY; CO* / LED* / RECON | Original mode; checkout HMAC on verify; authoritative receipt/amount/provider reconciliation | [ComponentSlotService.reconcile][ComponentSlotService] |
| POST | `/api/v1/me/component-slot-purchases/{id}/verify` | Private (DEVELOPER) | S | DEVELOPER | Purchase.owner_id = actor | — | — | B | MUT | DENY; CO* / LED* | Original mode; checkout HMAC on verify; authoritative receipt/amount/provider reconciliation | [ComponentSlotService.verify][ComponentSlotService] |
| POST | `/api/v1/me/publishing-slot-purchases` | Private (DEVELOPER) | SV | AD | Actor owns reservation | — | — | B | MUT | DENY; CO* | Pool + idempotency; consent; current server price comparison; enabled provider collection | [ComponentSlotService.order][ComponentSlotService] |
| POST | `/api/v1/me/publishing-slot-purchases/{id}/reconcile` | Private (DEVELOPER) | S | DEVELOPER | Purchase.owner_id = actor | — | — | B | MUT | DENY; CO* / LED* / RECON | Original mode; checkout HMAC on verify; authoritative receipt/amount/provider reconciliation | [ComponentSlotService.reconcile][ComponentSlotService] |
| POST | `/api/v1/me/publishing-slot-purchases/{id}/verify` | Private (DEVELOPER) | S | DEVELOPER | Purchase.owner_id = actor | — | — | B | MUT | DENY; CO* / LED* | Original mode; checkout HMAC on verify; authoritative receipt/amount/provider reconciliation | [ComponentSlotService.verify][ComponentSlotService] |
| PUT | `/api/v1/admin/component-slot-pricing` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; CO / SET | Valid pool + bounded whole minor-unit amount; new reservations only | [ComponentSlotService.setPrice][ComponentSlotService] |
| PUT | `/api/v1/admin/publishing-slots/{pool}/pricing` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; CO / SET | Valid pool + bounded whole minor-unit amount; new reservations only | [ComponentSlotService.setPrice][ComponentSlotService] |

### ConciergeService (4 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| GET | `/api/v1/admin/concierge` | Private (ADMIN_MFA) | S | ADMIN | — | — | M | — | R | DENY; N | Open business requests; requested/in-progress/fulfilled concierge | [ConciergeService.queue][ConciergeService] |
| GET | `/api/v1/admin/concierge/{id}/matches` | Private (ADMIN_MFA) | S | ADMIN | — | — | M | — | R | DENY; N | OPEN linked brief; eligible candidate proof | [ConciergeService.matches][ConciergeService] |
| PATCH | `/api/v1/admin/concierge/{id}` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; B | REQUESTED→IN_PROGRESS→FULFILLED; fulfillment requires eligible concierge recommendation | [ConciergeService.status][ConciergeService] |
| POST | `/api/v1/admin/concierge/{id}/shortlist` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; B | OPEN brief; IN_PROGRESS concierge; candidate eligibility | [ConciergeService.save][ConciergeService] |

### DeliveryService (10 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| GET | `/api/v1/admin/delivery/disputes` | Private (ADMIN_MFA) | S | ADMIN | — | — | M | — | R | DENY; N | — | [DeliveryService.adminDisputes][DeliveryService] |
| GET | `/api/v1/engagements` | Private (SESSION) | SV | EP | Named buyer CLIENT or approved builder seller | EP business/team authority; current party checks | — | — | R | DENY; N | — | [DeliveryService.mine][DeliveryService] |
| GET | `/api/v1/engagements/{id}` | Private (SESSION) | SV | EP | Named buyer CLIENT or approved builder seller | EP business/team authority; current party checks | — | — | R | DENY; N | — | [DeliveryService.detail][DeliveryService] |
| POST | `/api/v1/admin/delivery/disputes/{id}/resolve` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; DV | OPEN dispute; RESUME/CANCEL; stale completion cannot resume after refund | [DeliveryService.resolve][DeliveryService] |
| POST | `/api/v1/engagements` | Private (SESSION) | SV | EP | Source inquiry party OR qualified candidate | BM buyer / TS seller as applicable | — | B | MUT | DENY; DV* | Clear confirmed inquiry OR OPEN business brief; eligible matching proof; seller must be shortlisted; distinct parties | [DeliveryService.create][DeliveryService] |
| POST | `/api/v1/engagements/{id}/completion` | Private (SESSION) | SV | EP | Seller request then buyer acknowledgement | BM buyer / TS seller | — | B | MUT | DENY; DV | ACTIVE→COMPLETION_PENDING→COMPLETED; all accepted, live paid, unrefunded milestones | [DeliveryService.completion][DeliveryService] |
| POST | `/api/v1/engagements/{id}/disputes` | Private (SESSION) | SV | EP | Either current engagement party | BM buyer / TS seller | — | B | MUT | DENY; DV | OPEN/ACTIVE/COMPLETION_PENDING/COMPLETED; holds checkout + completion | [DeliveryService.dispute][DeliveryService] |
| POST | `/api/v1/engagements/{id}/milestones/{milestoneId}/{action}` | Private (SESSION) | SV | EP, action-specific | SELLER start/submit; BUYER revision/accept; milestone belongs to route engagement | BM buyer / TS seller | — | B | MUT | DENY; DV | ACTIVE; milestone/action state; safe bounded delivery URL/note | [DeliveryService.milestoneAction][DeliveryService] |
| POST | `/api/v1/engagements/{id}/proposals` | Private (SESSION) | SV | ES | Seller party | TS for team seller | — | B | MUT | DENY; DV | OPEN; no SENT/ACCEPTED proposal; bounded milestone total + INR | [DeliveryService.draft][DeliveryService] |
| POST | `/api/v1/engagements/{id}/proposals/{proposalId}/{action}` | Private (SESSION) | SV | EP, action-specific | SELLER send/withdraw; BUYER accept/reject; proposal engagement matches | BM buyer / TS seller | — | B | MUT | DENY; DV | OPEN; action/state allowlist; explicit consent on send/accept; immutable accepted terms | [DeliveryService.proposalAction][DeliveryService] |

### GitHubAuthService (2 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| GET | `/api/v1/auth/github/callback` | Public entry (PUBLIC) | OAuth state + browser cookie | — | — | — | — | — | R | DENY; LOGIN / OAuth failure* | Atomic unexpired one-use state; provider code + PKCE; verified provider identity; ADMIN login excluded | [GitHubAuthService.callback][GitHubAuthService] |
| POST | `/api/v1/auth/github/start` | Public entry (PUBLIC) | A | — | — | — | — | B | MUT | DENY; N | Provider enabled; login/signup intent; signup legal consent; 10-minute browser/state binding + PKCE | [GitHubAuthService.start][GitHubAuthService] |

### GoogleAuthService (2 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| GET | `/api/v1/auth/google/callback` | Public entry (PUBLIC) | OAuth state + browser cookie | — | — | — | — | — | R | DENY; LOGIN / OAuth failure* | Atomic unexpired one-use state; provider code + PKCE; verified provider identity; ADMIN login excluded | [GoogleAuthService.callback][GoogleAuthService] |
| POST | `/api/v1/auth/google/start` | Public entry (PUBLIC) | A | — | — | — | — | B | MUT | DENY; N | Provider enabled; login/signup intent; signup legal consent; 10-minute browser/state binding + PKCE | [GoogleAuthService.start][GoogleAuthService] |

### Health (3 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| GET | `/actuator/health` | Private (HEALTH) | Loopback socket peer | INTERNAL_SERVICE | — | — | — | — | H-none | DENY; N | Exact IPv4/IPv6 loopback address; no X-Forwarded-For trust | [AuthorizationService] |
| GET | `/actuator/health/liveness` | Private (HEALTH) | Loopback socket peer | INTERNAL_SERVICE | — | — | — | — | H-none | DENY; N | Exact IPv4/IPv6 loopback address; no X-Forwarded-For trust | [AuthorizationService] |
| GET | `/actuator/health/readiness` | Private (HEALTH) | Loopback socket peer | INTERNAL_SERVICE | — | — | — | — | H-none | DENY; N | Exact IPv4/IPv6 loopback address; no X-Forwarded-For trust | [AuthorizationService] |

### HostingService (17 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| GET | `/api/v1/admin/hosting` | Private (ADMIN_MFA) | S | ADMIN | Administrative target; owner row locked | — | M | — | R | DENY; N | — | [HostingService.list][HostingService] |
| GET | `/api/v1/admin/hosting/{id}/package` | Private (ADMIN_MFA) | S | ADMIN | Administrative access; package belongs to target record | — | M15 | — | R | DENY; EX / H (package read) | Immutable archive hash verified; authority rechecked after storage read | [HostingService.packageDownload][HostingService] |
| GET | `/api/v1/hosting/config` | Public entry (PUBLIC) | A | — | — | — | — | — | R | DENY; N | Public hosting readiness/projection | [HostingService.configuration][HostingService] |
| GET | `/api/v1/hosting/gateway/{deploymentId}` | Service only (SERVICE) | G | INTERNAL_SERVICE | — | — | — | — | G-none; R if denied | DENY; N | Constant-time gateway secret; allowed boolean rechecks HS | [HostingService.gateway][HostingService] |
| GET | `/api/v1/hosting/products/{productId}` | Public entry (PUBLIC) | A | — | — | — | — | — | R | DENY; N | HS: configured; approved READY PUBLISHED unexpired deployment; current verified PP | [HostingService.publicProduct][HostingService] |
| GET | `/api/v1/me/hosting` | Private (DEVELOPER) | S | DEVELOPER | HostedDemo.owner_id = actor | — | — | — | R | DENY; N | — | [HostingService.list][HostingService] |
| GET | `/api/v1/me/hosting/sources` | Private (DEVELOPER) | S | DEVELOPER | Product.owner_user_id = actor | — | — | — | R | DENY; N | Current approved verified public hosting sources | [HostingService.sources][HostingService] |
| GET | `/api/v1/me/hosting/{id}` | Private (DEVELOPER) | S | DEVELOPER | HostedDemo.owner_id = actor | — | — | — | R | DENY; N | — | [HostingService.detail][HostingService] |
| GET | `/api/v1/me/hosting/{id}/package` | Private (DEVELOPER) | S | DEVELOPER | HostedDemo.owner_id = actor | — | — | — | R | DENY; EX / H (package read) | Immutable archive hash verified; authority rechecked after storage read | [HostingService.packageDownload][HostingService] |
| POST | `/api/v1/admin/hosting/{id}/reconcile` | Private (ADMIN_MFA) | S | ADMIN | Administrative target; owner row locked | — | M15 | B | MUT | DENY; ADMIN; H* | Existing deployment; exact ID/hash/expiry; retry of absent deployment requires eligible approved owner | [HostingService.reconcile][HostingService] |
| POST | `/api/v1/admin/hosting/{id}/review` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; H | Package/rights confirmations; allowed state; APPROVE requires eligible product + this admin’s exact-hash package download; no withdrawn intent | [HostingService.review][HostingService] |
| POST | `/api/v1/admin/hosting/{id}/withdraw` | Private (ADMIN_MFA) | S | ADMIN | Administrative target; owner row locked | — | M15 | B | MUT | DENY; ADMIN; H* | Committed withdrawal immediately denies public access; exact deletion reconciliation follows | [HostingService.withdraw][HostingService] |
| POST | `/api/v1/me/hosting` | Private (DEVELOPER) | SV | AD | Actor owns linked eligible product | — | — | B | MUT | DENY; H | Current verified PP; capacity; explicit rights; inspected bounded static ZIP + immutable hashes | [HostingService.create][HostingService] |
| POST | `/api/v1/me/hosting/{id}/deploy` | Private (DEVELOPER) | SV | AD | HostedDemo.owner_id = actor | — | — | B | MUT | DENY; H* | Current eligible PP; APPROVED + PUBLISHED; fixed deployment identity/expiry; capacity | [HostingService.deploy][HostingService] |
| POST | `/api/v1/me/hosting/{id}/reconcile` | Private (DEVELOPER) | S | DEVELOPER | HostedDemo.owner_id = actor | — | — | B | MUT | DENY; H* | Existing deployment; exact ID/hash/expiry; retry of absent deployment requires eligible approved owner | [HostingService.reconcile][HostingService] |
| POST | `/api/v1/me/hosting/{id}/submit` | Private (DEVELOPER) | SV | AD | HostedDemo.owner_id = actor | — | — | B | MUT | DENY; H | Eligible current product; DRAFT/CHANGES_REQUESTED + PUBLISHED intent + NOT_CREATED; rights consent | [HostingService.submit][HostingService] |
| POST | `/api/v1/me/hosting/{id}/withdraw` | Private (DEVELOPER) | S | DEVELOPER | HostedDemo.owner_id = actor | — | — | B | MUT | DENY; H* | Committed withdrawal immediately denies public access; exact deletion reconciliation follows | [HostingService.withdraw][HostingService] |

### InquiryContractService (4 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| POST | `/api/v1/inquiries/{id}/confirm-completion` | Capability/client party (CAPABILITY) | C or SV | — | Token.inquiry_id/kind = route action OR principal email matches client | — | — | B | MUT | DENY; I* | CLEAR; correct hire/completion state; token expiry/consume or verified client decision | [InquiryContractService.confirm][InquiryContractService] |
| POST | `/api/v1/inquiries/{id}/confirm-hire` | Capability/client party (CAPABILITY) | C or SV | — | Token.inquiry_id/kind = route action OR principal email matches client | — | — | B | MUT | DENY; I* | CLEAR; correct hire/completion state; token expiry/consume or verified client decision | [InquiryContractService.confirm][InquiryContractService] |
| POST | `/api/v1/inquiries/{id}/not-hired` | Private (SESSION) | SV | Client or DEVELOPER for builder branch | Client email OR Inquiry.developer_user_id = actor | — | — | B | MUT | DENY; I* | CLEAR; not-hired transition | [InquiryContractService.close][InquiryContractService] |
| POST | `/api/v1/inquiries/{id}/review` | Private (SESSION) | SV | — | Inquiry.client_email = principal email | — | — | B | MUT | DENY; N (review + analytics) | CLEAR, client-confirmed COMPLETED; one held review | [InquiryContractService.review][InquiryContractService] |

### InquiryService (7 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| GET | `/api/v1/developer/analytics` | Private (DEVELOPER) | S | DEVELOPER | Actor’s inquiries/products/analytics | — | — | — | R | DENY; N | — | [InquiryService.analytics][InquiryService] |
| GET | `/api/v1/developer/inquiries` | Private (DEVELOPER) | S | DEVELOPER | Inquiry.developer_user_id = actor | — | — | — | R | DENY; N | Email-confirmed + CLEAR inquiries | [InquiryService.list][InquiryService] |
| GET | `/api/v1/developer/inquiries/{id}` | Private (DEVELOPER) | S | DEVELOPER | Inquiry.developer_user_id = actor | — | — | — | R | DENY; N | Email-confirmed + CLEAR inquiries | [InquiryService.detail][InquiryService] |
| GET | `/api/v1/notifications` | Private (SESSION) | S | — | Notification.user_id = actor | — | — | — | R | DENY; N | — | [InquiryService.notifications][InquiryService] |
| POST | `/api/v1/inquiries` | Public entry (PUBLIC) | A | — | Not builder’s own email | — | — | B | MUT + INQUIRY | DENY; I / analytics | PP available-for-work; sender not suspended/deleted; honeypot; idempotency + duplicate checks | [InquiryService.create][InquiryService] |
| POST | `/api/v1/inquiries/{id}/{action}` | Private (SESSION) | S | DEVELOPER | Inquiry.developer_user_id = actor | — | — | B | MUT | DENY; I | Email-confirmed + CLEAR; Rules.transition action/state allowlist | [InquiryService.transition][InquiryService] |
| POST | `/api/v1/reports` | Public entry (PUBLIC) | A; optional S | — | — | — | — | B | MUT | DENY; N | Bounded report target/reason allowlists; records report, no success security event | [InquiryService.report][InquiryService] |

### MaintenanceService (15 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| GET | `/api/v1/admin/maintenance/attention` | Private (ADMIN_MFA) | S | ADMIN | — | — | M | — | R | DENY; N | — | [MaintenanceService.attention][MaintenanceService] |
| GET | `/api/v1/maintenance` | Private (SESSION) | SV | EP | Current offer engagement party | BM buyer / TS seller; rechecked | — | — | R | DENY; N | — | [MaintenanceService.list][MaintenanceService] |
| GET | `/api/v1/maintenance/config` | Private (SESSION) | SV | — | — | — | — | — | R | DENY; N | Verified session; provider maintenance configuration projection | [MaintenanceService.configuration][MaintenanceService] |
| GET | `/api/v1/maintenance/sources` | Private (SESSION) | SV | EP | Current completed engagement party | BM buyer / TS seller | — | — | R | DENY; N | ME: completed, fully paid, eligible distinct parties, no financial holds | [MaintenanceService.sources][MaintenanceService] |
| GET | `/api/v1/maintenance/{id}` | Private (SESSION) | SV | EP | Current offer engagement party | BM buyer / TS seller; rechecked | — | — | R | DENY; N | — | [MaintenanceService.detail][MaintenanceService] |
| POST | `/api/v1/admin/maintenance/{id}/{action}` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; MN* | Operator action allowlist; original mode; exact provider IDs/period binding; bounded reason; frozen facts | [MaintenanceService.operator][MaintenanceService] |
| POST | `/api/v1/maintenance` | Private (SESSION) | SV | ES | Current engagement seller | TS for team seller | — | B | MUT | DENY; MN | ME; no open offer/enrollment; immutable bounded monthly terms | [MaintenanceService.create][MaintenanceService] |
| POST | `/api/v1/maintenance/razorpay/webhook` | Provider only (SIGNED_WEBHOOK) | W | SIGNED_WEBHOOK | — | — | — | W-exempt | MUT | DENY; WH / LED* | Raw HMAC before parse; event/hash dedup; subscription/payment facts matched; no client entitlement assertions | [MaintenanceService.webhook][MaintenanceService] |
| POST | `/api/v1/maintenance/{id}/billing` | Private (SESSION) | SV | EB | Current offer buyer; actor becomes payer | BM for business buyer | — | B | MUT | DENY; MN* | ME; accepted immutable consent/digest; idempotency; authoritative payee + bounded cycles | [MaintenanceService.start][MaintenanceService] |
| POST | `/api/v1/maintenance/{id}/billing/cancel` | Private (SESSION) | SV | EB | Current offer buyer | BM for business buyer | — | B | MUT | DENY; MN* | Subscription exists; intent before provider reads; provider-confirmed future billing stop | [MaintenanceService.cancel][MaintenanceService] |
| POST | `/api/v1/maintenance/{id}/billing/confirm` | Private (SESSION) | SV | EB | Current offer buyer | BM for business buyer | — | B | MUT | DENY; MN* / LED* | Original mode; provider subscription ready; checkout HMAC; facts + consent reconciliation | [MaintenanceService.confirm][MaintenanceService] |
| POST | `/api/v1/maintenance/{id}/billing/refresh` | Private (SESSION) | SV | EP | Current offer engagement party | BM buyer / TS seller | — | B | MUT | DENY; MN* / LED* | Subscription exists; authoritative provider facts; party rechecked | [MaintenanceService.refresh][MaintenanceService] |
| POST | `/api/v1/maintenance/{id}/requests` | Private (SESSION) | SV | EB | Current offer buyer; actor-scoped request key | BM for business buyer | — | B | MUT | DENY; MN* | ME; live current paid/fresh/clear invoice; no local hold; request quota; idempotency | [MaintenanceService.request][MaintenanceService] |
| POST | `/api/v1/maintenance/{id}/requests/{requestId}/{action}` | Private (SESSION) | SV | EP, action-specific | SELLER start/submit; BUYER revision/accept/cancel; request belongs to offer subscription | BM buyer / TS seller | — | B | MUT | DENY; MN | ME; original paid period fresh/clear; no local hold; action/state allowlist | [MaintenanceService.requestAction][MaintenanceService] |
| POST | `/api/v1/maintenance/{id}/{action}` | Private (SESSION) | SV | EP, action-specific | SELLER send/withdraw; BUYER accept/reject | BM buyer / TS seller | — | B | MUT | DENY; MN | ME for send/accept; DRAFT/OFFERED states; consent + exact digest on accept | [MaintenanceService.offerAction][MaintenanceService] |

### MediaService (7 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| DELETE | `/api/v1/developer/products/{product}/media/{id}` | Private (DEVELOPER) | SV | AD | Product + media/upload capability scoped to actor product | — | — | B | MUT | DENY; N | Editable DRAFT/CHANGES_REQUESTED; ≤6 images; bounded verified/sanitized image or ordered owned IDs | [MediaService.remove][MediaService] |
| GET | `/api/v1/developer/products/{product}/proof` | Private (DEVELOPER) | S | DEVELOPER | Product.owner_user_id = actor | — | — | — | R | DENY; N | — | [MediaService.proof][MediaService] |
| GET | `/api/v1/media/{id}` | Public entry (PUBLIC) | A for PP; S for owner/grant/ADMIN | Owner / grant / ADMIN for private | PP OR product owner OR verified grant OR ADMIN | — | M for ADMIN fallback | — | R | DENY; N | Verified media; nonpublic access rechecked; grant requires approved active private product | [MediaService.get][MediaService] |
| PATCH | `/api/v1/developer/products/{product}/media` | Private (DEVELOPER) | SV | AD | Product + media/upload capability scoped to actor product | — | — | B | MUT | DENY; N | Editable DRAFT/CHANGES_REQUESTED; ≤6 images; bounded verified/sanitized image or ordered owned IDs | [MediaService.order][MediaService] |
| POST | `/api/v1/developer/products/{product}/media` | Private (DEVELOPER) | SV | AD | Product + media/upload capability scoped to actor product | — | — | B | MUT | DENY; N | Editable DRAFT/CHANGES_REQUESTED; ≤6 images; bounded verified/sanitized image or ordered owned IDs | [MediaService.upload][MediaService] |
| POST | `/api/v1/developer/products/{product}/media/complete` | Private (DEVELOPER) | SV | AD | Product + media/upload capability scoped to actor product | — | — | B | MUT | DENY; N | Editable DRAFT/CHANGES_REQUESTED; ≤6 images; bounded verified/sanitized image or ordered owned IDs | [MediaService.complete][MediaService] |
| POST | `/api/v1/developer/products/{product}/media/upload-request` | Private (DEVELOPER) | SV | AD | Product + media/upload capability scoped to actor product | — | — | B | MUT | DENY; N | Editable DRAFT/CHANGES_REQUESTED; ≤6 images; bounded verified/sanitized image or ordered owned IDs | [MediaService.requestUpload][MediaService] |

### ModerationService (8 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| GET | `/api/v1/admin/appeals` | Private (ADMIN_MFA) | S | ADMIN | — | — | M | — | R | DENY; N | — | [ModerationService.appeals][ModerationService] |
| GET | `/api/v1/admin/email-jobs` | Private (ADMIN_MFA) | S | ADMIN | — | — | M | — | R | DENY; N | — | [ModerationService.failedMail][ModerationService] |
| GET | `/api/v1/me/appeals` | Private (SESSION) | S | — | Appeal.appellant_id = actor | — | — | — | R | DENY; N | — | [ModerationService.ownAppeals][ModerationService] |
| GET | `/api/v1/me/moderation-decisions` | Private (SESSION) | S | — | Actor affected by account/profile/product/review/inquiry decision | — | — | — | R | DENY; N | — | [ModerationService.decisions][ModerationService] |
| POST | `/api/v1/admin/appeals/{id}/decision` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; MOD | Unresolved appeal; UNDER_REVIEW/UPHELD/OVERTURNED; restoration delegates recheck domain eligibility | [ModerationService.appealDecision][ModerationService] |
| POST | `/api/v1/admin/email-jobs/{id}/retry` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; MOD | Failed unsent job ≥8 attempts; unexpired body; <24h; never revive consumed links | [ModerationService.retry][ModerationService] |
| POST | `/api/v1/admin/reports/{id}/triage` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; MOD | Report not RESOLVED; bounded severity/status allowlists | [ModerationService.triage][ModerationService] |
| POST | `/api/v1/appeals` | Private (SESSION) | S | — | Actor affected by selected moderation decision | — | — | B | MUT | DENY; N | Bounded statement; safe evidence URL; creates appeal, no success audit | [ModerationService.appeal][ModerationService] |

### PaymentService (11 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| GET | `/api/v1/admin/payments/accounts` | Private (ADMIN_MFA) | S | ADMIN | — | — | M | — | R | DENY; N | — | [PaymentService.accounts][PaymentService] |
| GET | `/api/v1/admin/payments/attention` | Private (ADMIN_MFA) | S | ADMIN | — | — | M | — | R | DENY; N | — | [PaymentService.attention][PaymentService] |
| GET | `/api/v1/engagements/{id}/payments` | Private (SESSION) | SV | EP | Current engagement party | BM buyer / TS seller | — | — | R | DENY; N | — | [PaymentService.summaries][PaymentService] |
| GET | `/api/v1/payments/config` | Public entry (PUBLIC) | A | — | — | — | — | — | R | DENY; N | Public provider configuration projection | [PaymentService.configuration][PaymentService] |
| POST | `/api/v1/admin/payments/accounts` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; N | Verified Razorpay linked account mapped to builder OR team; original mode | [PaymentService.account][PaymentService] |
| POST | `/api/v1/admin/payments/{id}/bind-order` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; DV / BIND | Unbound unresolved reservation; exact receipt/amount/payee/order | [PaymentService.bind][PaymentService] |
| POST | `/api/v1/admin/payments/{id}/reconcile` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; DV* / LED* / RECON | Reserved milestone payment; provider facts | [PaymentService.adminReconcile][PaymentService] |
| POST | `/api/v1/milestones/{id}/payment-order` | Private (SESSION) | SV | EB | Current milestone buyer | BM for business buyer | — | B | MUT | DENY; DV* | ACCEPTED milestone + ACTIVE undisputed engagement; idempotency; frozen amount/payee | [PaymentService.order][PaymentService] |
| POST | `/api/v1/payments/razorpay/webhook` | Provider only (SIGNED_WEBHOOK) | W | SIGNED_WEBHOOK | — | — | — | W-exempt | MUT | DENY; WH / DV* / LED* | Raw HMAC before JSON; event/hash dedup; exact matched payment and provider facts | [PaymentService.webhook][PaymentService] |
| POST | `/api/v1/payments/{id}/reconcile` | Private (SESSION) | SV | EB | Payment milestone buyer authority (not arbitrary payment ID) | BM for business buyer | — | B | MUT | DENY; DV* / LED* / RECON | Original mode; verify HMAC; authoritative amount/currency/payee reconciliation | [PaymentService.reconcile][PaymentService] |
| POST | `/api/v1/payments/{id}/verify` | Private (SESSION) | SV | EB | Payment milestone buyer authority (not arbitrary payment ID) | BM for business buyer | — | B | MUT | DENY; DV* / LED* | Original mode; verify HMAC; authoritative amount/currency/payee reconciliation | [PaymentService.verify][PaymentService] |

### PrivacyService (3 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| GET | `/api/v1/admin/deletion-requests` | Private (ADMIN_MFA) | S | ADMIN | — | — | M | — | R | DENY; N | — | [PrivacyService.requests][PrivacyService] |
| GET | `/api/v1/policies/config` | Public entry (PUBLIC) | A | — | — | — | — | — | R | DENY; N | Published legal-version/contact/approval projection | [PrivacyService.config][PrivacyService] |
| POST | `/api/v1/admin/deletion-requests/{id}/review` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; MOD | DELETED account + request; ADMIN protected; HOLD/ANONYMIZE_PROFILE; approved retention policy for anonymization | [PrivacyService.review][PrivacyService] |

### PrivateProjectService (4 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| DELETE | `/api/v1/developer/products/{id}/access` | Private (DEVELOPER) | SV | AD | Product.owner_user_id = actor | — | — | B | MUT | DENY; N | Non-PUBLIC, not SUSPENDED; email-bound expiring grants | [PrivateProjectService.revoke][PrivateProjectService] |
| GET | `/api/v1/developer/products/{id}/access` | Private (DEVELOPER) | SV | AD | Product.owner_user_id = actor | — | — | — | R | DENY; N | Non-PUBLIC, not SUSPENDED; email-bound expiring grants | [PrivateProjectService.list][PrivateProjectService] |
| GET | `/api/v1/private/products/{id}` | Private (SESSION) | S | — | Product owner OR verified-email unexpired grant | — | — | — | R | DENY; N | Non-PUBLIC; approved ACTIVE product; active owner + approved profile | [PrivateProjectService.preview][PrivateProjectService] |
| POST | `/api/v1/developer/products/{id}/access` | Private (DEVELOPER) | SV | AD | Product.owner_user_id = actor | — | — | B | MUT | DENY; N | Non-PUBLIC, not SUSPENDED; email-bound expiring grants | [PrivateProjectService.grant][PrivateProjectService] |

### ProductService (13 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| DELETE | `/api/v1/products/{id}/save` | Private (SESSION) | S | — | SavedProduct.user_id = actor | — | — | B | MUT | DENY; N | — | [ProductService.unsave][ProductService] |
| GET | `/api/v1/builders/{slug}` | Public entry (PUBLIC) | A | — | — | — | — | — | D | DENY; N | Approved builder profile; active account; published product projection | [ProductService.builder][ProductService] |
| GET | `/api/v1/builders/{slug}/reviews` | Public entry (PUBLIC) | A | — | — | — | — | — | D | DENY; N | Approved builder; active account; published reviews of clear confirmed completions | [ProductService.reviews][ProductService] |
| GET | `/api/v1/categories` | Public entry (PUBLIC) | A | — | — | — | — | — | R | DENY; N | Active taxonomy only | [ProductService.categories][ProductService] |
| GET | `/api/v1/developer/products` | Private (DEVELOPER) | S | DEVELOPER | Product.owner_user_id = actor | — | — | — | R | DENY; N | — | [ProductService.own][ProductService] |
| GET | `/api/v1/me/saved-products` | Private (SESSION) | S | — | SavedProduct.user_id = actor | — | — | — | R | DENY; N | Current availability reflected in returned projection | [ProductService.saved][ProductService] |
| GET | `/api/v1/products` | Public entry (PUBLIC) | A | — | — | — | — | — | D | DENY; N | PP: approved active PUBLIC product; active owner; approved profile | [ProductService.search][ProductService] |
| GET | `/api/v1/products/{slug}` | Public entry (PUBLIC) | A | — | — | — | — | — | D | DENY; N | PP: approved active PUBLIC product; active owner; approved profile | [ProductService.detail][ProductService] |
| GET | `/api/v1/technologies` | Public entry (PUBLIC) | A | — | — | — | — | — | R | DENY; N | Active taxonomy only | [ProductService.technologies][ProductService] |
| PATCH | `/api/v1/developer/products/{id}` | Private (DEVELOPER) | SV | AD | Product.owner_user_id = actor | — | — | B | MUT | DENY; N | Save resets draft/review state; active taxonomy + public-safe URLs + rights/pricing checks | [ProductService.edit][ProductService] |
| POST | `/api/v1/developer/products` | Private (DEVELOPER) | SV | AD | Actor owns new product | — | — | B | MUT | DENY; N | Publishing capacity; active taxonomy; URLs/rights/pricing validation | [ProductService.create][ProductService] |
| POST | `/api/v1/developer/products/{id}/{action}` | Private (DEVELOPER) | SV | AD | Product.owner_user_id = actor | — | — | B | MUT | DENY; N (archive/capacity analytics*) | submit/archive/activate state allowlist; proof + rights on submit; approval and capacity on activation | [ProductService.action][ProductService] |
| POST | `/api/v1/products/{id}/save` | Private (SESSION) | S | — | Actor owns saved-product relation | — | — | B | MUT | DENY; N | PP required at save | [ProductService.saveProduct][ProductService] |

### ProfileService (2 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| POST | `/api/v1/developer/profile/submit` | Private (DEVELOPER) | SV | DEVELOPER | Profile.user_id = actor | — | — | B | MUT | DENY; N | DRAFT/CHANGES_REQUESTED; complete required profile fields | [ProfileService.submitProfile][ProfileService] |
| PUT | `/api/v1/developer/profile` | Private (DEVELOPER) | S | DEVELOPER | Profile.user_id = actor | — | — | B | MUT | DENY; N | Material changes reset approved/pending profile to DRAFT | [ProfileService.profile][ProfileService] |

### ProjectRequestService (8 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| DELETE | `/api/v1/businesses/{id}/requests/{requestId}/shortlist/{entryId}` | Private (SESSION) | SV | — | Request + entry scoped to route business/request | BM | — | B | MUT | DENY; B | OPEN; save candidates rechecked for eligibility | [ProjectRequestService.remove][ProjectRequestService] |
| GET | `/api/v1/businesses/{id}/requests` | Private (SESSION) | SV | — | — | BM | — | — | R | DENY; N | — | [ProjectRequestService.list][ProjectRequestService] |
| GET | `/api/v1/businesses/{id}/requests/{requestId}/matches` | Private (SESSION) | SV | — | Request.business_id = route business | BM | — | — | R | DENY; N | OPEN; approved public eligible proof from MatchingService | [ProjectRequestService.matches][ProjectRequestService] |
| GET | `/api/v1/businesses/{id}/requests/{requestId}/shortlist` | Private (SESSION) | SV | — | Request.business_id = route business | BM | — | — | R | DENY; N | — | [ProjectRequestService.shortlist][ProjectRequestService] |
| POST | `/api/v1/businesses/{id}/requests` | Private (SESSION) | SV | — | — | BM | — | B | MUT | DENY; B | New DRAFT/OPEN; eligible matching criteria | [ProjectRequestService.create][ProjectRequestService] |
| POST | `/api/v1/businesses/{id}/requests/{requestId}/concierge` | Private (SESSION) | SV | — | Request + entry scoped to route business/request | BM | — | B | MUT | DENY; B | OPEN; save candidates rechecked for eligibility | [ProjectRequestService.concierge][ProjectRequestService] |
| POST | `/api/v1/businesses/{id}/requests/{requestId}/shortlist` | Private (SESSION) | SV | — | Request + entry scoped to route business/request | BM | — | B | MUT | DENY; B | OPEN; save candidates rechecked for eligibility | [ProjectRequestService.save][ProjectRequestService] |
| PUT | `/api/v1/businesses/{id}/requests/{requestId}` | Private (SESSION) | SV | — | Request.business_id = route business | BM | — | B | MUT | DENY; B | Not CLOSED; OPEN cannot return to DRAFT | [ProjectRequestService.edit][ProjectRequestService] |

### TalentListService (5 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| DELETE | `/api/v1/businesses/{id}/talent-lists/{listId}/entries/{entryId}` | Private (SESSION) | SV | — | List + entry scoped to route business/list | BM | — | B | MUT | DENY; B | Save candidates rechecked for eligible public proof | [TalentListService.remove][TalentListService] |
| GET | `/api/v1/businesses/{id}/talent-lists` | Private (SESSION) | SV | — | — | BM | — | — | R | DENY; N | — | [TalentListService.lists][TalentListService] |
| GET | `/api/v1/businesses/{id}/talent-lists/{listId}/entries` | Private (SESSION) | SV | — | List.business_id = route business | BM | — | — | R | DENY; N | — | [TalentListService.entries][TalentListService] |
| POST | `/api/v1/businesses/{id}/talent-lists` | Private (SESSION) | SV | — | — | BM | — | B | MUT | DENY; B | — | [TalentListService.create][TalentListService] |
| POST | `/api/v1/businesses/{id}/talent-lists/{listId}/entries` | Private (SESSION) | SV | — | List + entry scoped to route business/list | BM | — | B | MUT | DENY; B | Save candidates rechecked for eligible public proof | [TalentListService.save][TalentListService] |

### TeamService (24 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| DELETE | `/api/v1/teams/{id}/members/{userId}` | Private (SESSION) | S | — | Self leave OR TO removing another | Current TM; ownership transfer only to eligible remaining OWNER | — | B | MUT | DENY; T | Cannot remove another OWNER; last active OWNER cannot leave | [TeamService.remove][TeamService] |
| DELETE | `/api/v1/teams/{id}/projects/{productId}` | Private (SESSION) | S | — | Consenting product owner OR TO | TO needed only if not original consenting owner | — | B | MUT | DENY; T | — | [TeamService.removeProject][TeamService] |
| GET | `/api/v1/admin/teams` | Private (ADMIN_MFA) | S | ADMIN | — | — | M | — | R | DENY; N | — | [TeamService.admin][TeamService] |
| GET | `/api/v1/me/teams` | Private (SESSION) | S | — | Own memberships/invitations/applications/leads; section-scoped projections | — | — | — | R | DENY; N | — | [TeamService.mine][TeamService] |
| GET | `/api/v1/teams` | Public entry (PUBLIC) | A | — | — | — | — | — | D | DENY; N | ACTIVE team; eligible public members + consented projects + open roles | [TeamService.directory][TeamService] |
| GET | `/api/v1/teams/{id}` | Public entry (PUBLIC) | A | — | — | — | — | — | D | DENY; N | ACTIVE team; eligible public members + consented projects + open roles | [TeamService.detail][TeamService] |
| GET | `/api/v1/teams/{id}/candidates` | Private (SESSION) | S | — | — | TR | — | — | D | DENY; N | Eligible approved verified target builders | [TeamService.candidates][TeamService] |
| GET | `/api/v1/teams/{id}/workspace` | Private (SESSION) | S | — | — | TM; role-scoped recruit/commercial/staff sections | — | — | D | DENY; N | — | [TeamService.workspace][TeamService] |
| PATCH | `/api/v1/teams/{id}/leads/{leadId}` | Private (SESSION) | S | — | Lead.team_id = route team | TC; assignee must be current TM | — | B | MUT | DENY; T | ACTIVE team; leadTransition; optional PATCH assignment/follow-up semantics | [TeamService.updateLead][TeamService] |
| PATCH | `/api/v1/teams/{id}/members/{userId}` | Private (SESSION) | S | — | — | TO; target current TM | — | B | MUT | DENY; T / ROLE | Ownership cannot change through member roles | [TeamService.changeRole][TeamService] |
| PATCH | `/api/v1/teams/{id}/roles/{roleId}` | Private (SESSION) | S | — | Role/application scoped to team | TR | — | B | MUT | DENY; T | ACTIVE team; role belongs to team; OPEN/CLOSED status allowlist | [TeamService.roleStatus][TeamService] |
| PATCH | `/api/v1/teams/{id}/staffing/{staffingId}` | Private (SESSION) | S | — | Staffing.team_id = route team | TP; staffing target current TM | — | B | MUT | DENY; T | ACTIVE team; future assignment end cannot outlast membership; scoped state | [TeamService.updateStaffing][TeamService] |
| POST | `/api/v1/admin/teams/{id}/moderate` | Private (ADMIN_MFA) | S | ADMIN | — | — | M15 | B | MUT | DENY; ADMIN; T | ACTIVE/SUSPENDED allowlist; bounded reason | [TeamService.moderate][TeamService] |
| POST | `/api/v1/team-invitations/{id}/respond` | Private (SESSION) | S | — | Invitation.user_id = actor | — | — | B | MUT | DENY; T | PENDING + respond_by/expiry; ACCEPT additionally requires ACTIVE team + approved verified target; DECLINE does not | [TeamService.respond][TeamService] |
| POST | `/api/v1/teams` | Private (SESSION) | SV | AD | Actor becomes team OWNER | — | — | B | MUT | DENY; T | Approved nonsuspended showcase required | [TeamService.create][TeamService] |
| POST | `/api/v1/teams/{id}/applications/{applicationId}/decision` | Private (SESSION) | S | — | Role/application scoped to team | TR | — | B | MUT | DENY; T | ACTIVE team; application belongs to OPEN team role; application transition; INVITED still requires applicant acceptance | [TeamService.decision][TeamService] |
| POST | `/api/v1/teams/{id}/invitations` | Private (SESSION) | S | — | — | TR; recruiter may invite MEMBER only; OWNER may assign non-owner roles | — | B | MUT | DENY; T | ACTIVE team; eligible target; bounded contract expiry; invitation consent | [TeamService.invite][TeamService] |
| POST | `/api/v1/teams/{id}/leads` | Private (SESSION) | SV | — | Actor becomes lead client | Actor must not be an unexpired member | — | B | MUT | DENY; T | ACTIVE team | [TeamService.lead][TeamService] |
| POST | `/api/v1/teams/{id}/leads/{leadId}/respond` | Private (SESSION) | SV | — | Lead.client_id = actor; lead team matches | — | — | B | MUT | DENY; T | ACTIVE team; PROPOSAL_SENT; ACCEPT/DECLINE | [TeamService.clientDecision][TeamService] |
| POST | `/api/v1/teams/{id}/projects` | Private (SESSION) | SV | AD | Product.owner_user_id = actor; explicit owner sharing consent | TM | — | B | MUT | DENY; T | ACTIVE team; eligible approved active public project | [TeamService.addProject][TeamService] |
| POST | `/api/v1/teams/{id}/roles` | Private (SESSION) | S | — | Role/application scoped to team | TR | — | B | MUT | DENY; T | ACTIVE team; bounded new recruitment role | [TeamService.createRole][TeamService] |
| POST | `/api/v1/teams/{id}/roles/{roleId}/applications` | Private (SESSION) | SV | AD | Actor owns new application | Must not already be TM | — | B | MUT | DENY; T | ACTIVE team; OPEN scoped role | [TeamService.apply][TeamService] |
| POST | `/api/v1/teams/{id}/staffing` | Private (SESSION) | S | — | Staffing.team_id = route team | TP; staffing target current TM | — | B | MUT | DENY; T | ACTIVE team; future assignment end cannot outlast membership; scoped state | [TeamService.addStaffing][TeamService] |
| PUT | `/api/v1/teams/{id}` | Private (SESSION) | S | — | — | TO | — | B | MUT | DENY; T | — | [TeamService.edit][TeamService] |

### TrustService (7 entries)

| HTTP method | Route | Public/private | Authentication | Required role | Ownership requirement | Membership requirement | MFA requirement | CSRF requirement | Rate-limit class | Audit requirement | Domain-state gate | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| GET | `/api/v1/admin/trust` | Private (ADMIN_MFA) | S | ADMIN | — | — | M | — | R | DENY; N | Current pending repository proof + eligible completed outcomes | [TrustService.queue][TrustService] |
| GET | `/api/v1/developer/trust` | Private (DEVELOPER) | S | DEVELOPER | Own profile/products/capacity/awards | — | — | — | R | DENY; N | — | [TrustService.overview][TrustService] |
| GET | `/api/v1/products/{slug}/similar-builders` | Public entry (PUBLIC) | A | — | — | — | — | — | D | DENY; N | PP source + distinct available eligible public builder proof | [TrustService.similar][TrustService] |
| POST | `/api/v1/admin/earned-capacity/{id}` | Private (ADMIN_MFA) | S | ADMIN | Admin must not be awarded builder | — | M15 | B | MUT | DENY; ADMIN; MOD* | Clear client-confirmed completed distinct-party outcome; one award per inquiry | [TrustService.award][TrustService] |
| POST | `/api/v1/admin/verifications/{id}/{action}` | Private (ADMIN_MFA) | S | ADMIN | Admin must not own target product | — | M15 | B | MUT | DENY; ADMIN; MOD | approve/reject/revoke; current URL + unexpired pending proof or verified revoke | [TrustService.review][TrustService] |
| POST | `/api/v1/developer/products/{id}/verification` | Private (DEVELOPER) | SV | AD | Product.owner_user_id = actor | — | — | B | MUT | DENY; N | Exact GitHub repository URL; current challenge reuse; 7-day proof | [TrustService.request][TrustService] |
| PUT | `/api/v1/developer/availability` | Private (DEVELOPER) | SV | AD | Profile.user_id = actor | — | — | B | MUT | DENY; N | Allowed availability; bounded future BOOKED_UNTIL | [TrustService.availability][TrustService] |

## Source reconciliation and remaining limits

The inventory contains **65 ADMIN_MFA**, **2 CAPABILITY**, **51 DEVELOPER**, **3 HEALTH**, **40 PUBLIC**, **1 SERVICE**, **93 SESSION**, **4 SIGNED_WEBHOOK**. The API entries map to **256 controller method/route combinations**; aliases are counted separately, and the three health entries are Spring Actuator routes. Source links name the exact handler delegate, with shared helper sources below.

GET `/maintenance/config` uses SESSION policy and additionally requires verified email in MaintenanceService.configuration; this matches the actual domain contract. Public media intentionally has conditional session/grant/ADMIN checks. Capability inquiry actions intentionally accept a matching token or verified client session. Hosted demo gateway access is service-secret protected; its returned allowed flag separately enforces current product/owner/deployment eligibility. None of those are unconditional public data access.

Successful ordinary reads and several profile/product/grant/report/appeal/availability mutations have no dedicated security success event; their `N` cells make that limitation visible. The account export and five private package/source-download routes emit central PRIVATE_EXPORT events; buyer source downloads and hosting package downloads also have domain read events. Administrator package routes require recent MFA. CommerceService.packageDownload relies on the central event and does not separately write a domain package-read event. Dedicated per-endpoint quotas are limited to the listed auth/inquiry additions; most public writes share the route-template mutation class. Authenticated gateway requests and loopback health have no application rate bucket. They remain service-secret and loopback protected respectively; they are not anonymous bypasses. Downstream gateway/proxy ingress quotas, private API exposure and probe routing are operator-managed deployment controls in [SECURITY_RELEASE](../ops/SECURITY_RELEASE.md), not application buckets claimed here. These are current implementation facts, not additional controls claimed by this document.

This document does not certify database RLS, reverse-proxy isolation, production secrets/MFA rotation, legal approval, provider configuration, or a passing exact-source CI run. Those release checks are tracked separately.

[AccountService]: ../backend/src/main/java/com/getlancer/accounts/AccountService.java
[AdminService]: ../backend/src/main/java/com/getlancer/admin/AdminService.java
[AnalyticsService]: ../backend/src/main/java/com/getlancer/analytics/AnalyticsService.java
[AuthProviderService]: ../backend/src/main/java/com/getlancer/auth/AuthProviderService.java
[AuthService]: ../backend/src/main/java/com/getlancer/auth/AuthService.java
[AuthorizationService]: ../backend/src/main/java/com/getlancer/security/AuthorizationService.java
[BrowserSecurityFilter]: ../backend/src/main/java/com/getlancer/security/BrowserSecurityFilter.java
[BusinessRepository]: ../backend/src/main/java/com/getlancer/business/BusinessRepository.java
[BusinessService]: ../backend/src/main/java/com/getlancer/business/BusinessService.java
[ClientInquiryService]: ../backend/src/main/java/com/getlancer/inquiries/ClientInquiryService.java
[CommercePaymentService]: ../backend/src/main/java/com/getlancer/commerce/CommercePaymentService.java
[CommerceService]: ../backend/src/main/java/com/getlancer/commerce/CommerceService.java
[ComponentService]: ../backend/src/main/java/com/getlancer/components/ComponentService.java
[ComponentSlotService]: ../backend/src/main/java/com/getlancer/components/ComponentSlotService.java
[ConciergeService]: ../backend/src/main/java/com/getlancer/business/ConciergeService.java
[DeliveryRepository]: ../backend/src/main/java/com/getlancer/delivery/DeliveryRepository.java
[DeliveryService]: ../backend/src/main/java/com/getlancer/delivery/DeliveryService.java
[GitHubAuthService]: ../backend/src/main/java/com/getlancer/auth/GitHubAuthService.java
[GoogleAuthService]: ../backend/src/main/java/com/getlancer/auth/GoogleAuthService.java
[HostingRepository]: ../backend/src/main/java/com/getlancer/hosting/HostingRepository.java
[HostingService]: ../backend/src/main/java/com/getlancer/hosting/HostingService.java
[InquiryContractService]: ../backend/src/main/java/com/getlancer/inquiries/InquiryContractService.java
[InquiryService]: ../backend/src/main/java/com/getlancer/inquiries/InquiryService.java
[MaintenanceAccess]: ../backend/src/main/java/com/getlancer/maintenance/MaintenanceAccess.java
[MaintenanceRepository]: ../backend/src/main/java/com/getlancer/maintenance/MaintenanceRepository.java
[MaintenanceService]: ../backend/src/main/java/com/getlancer/maintenance/MaintenanceService.java
[MediaService]: ../backend/src/main/java/com/getlancer/media/MediaService.java
[ModerationService]: ../backend/src/main/java/com/getlancer/moderation/ModerationService.java
[PaymentService]: ../backend/src/main/java/com/getlancer/payments/PaymentService.java
[PrivacyService]: ../backend/src/main/java/com/getlancer/accounts/PrivacyService.java
[PrivateProjectService]: ../backend/src/main/java/com/getlancer/products/PrivateProjectService.java
[ProductRepository]: ../backend/src/main/java/com/getlancer/products/ProductRepository.java
[ProductService]: ../backend/src/main/java/com/getlancer/products/ProductService.java
[ProfileService]: ../backend/src/main/java/com/getlancer/profiles/ProfileService.java
[ProjectRequestService]: ../backend/src/main/java/com/getlancer/business/ProjectRequestService.java
[RateLimits]: ../backend/src/main/java/com/getlancer/security/RateLimits.java
[SecurityAudit]: ../backend/src/main/java/com/getlancer/security/SecurityAudit.java
[TalentListService]: ../backend/src/main/java/com/getlancer/business/TalentListService.java
[TeamPolicy]: ../backend/src/main/java/com/getlancer/teams/TeamPolicy.java
[TeamRepository]: ../backend/src/main/java/com/getlancer/teams/TeamRepository.java
[TeamService]: ../backend/src/main/java/com/getlancer/teams/TeamService.java
[TrustService]: ../backend/src/main/java/com/getlancer/trust/TrustService.java
