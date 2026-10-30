# Frontend and API contract map

Paper & Pixel changes presentation while retaining the existing `/api/v1` backend contract. Browser JSON calls use `lib/api.ts`; server catalogue calls use the existing `lib/server.ts` and component/education/template server adapters. Source downloads and event streams keep their dedicated download/stream handling rather than passing binary content through the JSON helper.

This is a source contract review. The browser and backend test results in `VALIDATION.md` are the evidence for runtime coverage; this document does not claim that every privileged production mutation was executed.

## Interaction map

All paths below are relative to `/api/v1`. `{id}` and `{slug}` are existing server resource identifiers. Action placeholders are the existing allowlisted backend transitions, not arbitrary new actions.

| UI surface and action | Method and route | Frontend source | Backend contract and authority |
|---|---|---|---|
| Project search, category, technology, pagination | GET `/products`, `/categories`, `/technologies` | `explore.tsx`, `browse-toolbar.tsx`, `lib/server.ts` | `ProductsController`; URL query filters are retained |
| Open project and builder; read reviews | GET `/products/{slug}`, `/builders/{slug}`, `/builders/{slug}/reviews` | product and builder pages, `public-reviews.tsx` | `ProductsController`; public approved content |
| Save or unsave a project; saved collection | POST/DELETE `/products/{id}/save`; GET `/me/saved-products` | `explore.tsx`, saved page | `ProductsController`; authenticated ownership, optimistic state only after confirmation |
| Discover components and view version/source evidence | GET `/components`, `/components/{slug}`, `/components/{slug}/versions`, `/components/{slug}/source` | `component-library.tsx`, `component-version-history.tsx`, component detail | `ComponentsController`; pinned source hash and preview restrictions retained |
| Save or unsave a component | POST/DELETE `/components/{slug}/save`; GET `/me/saved-components` | `component-save-button.tsx`, `saved-components.tsx` | `ComponentBookmarksController`; server account owns bookmarks |
| Discover college work; inspect frozen release | GET `/college-projects`, `/college-projects/{slug}`, `/education-releases/{id}/source-offer` | college pages, `education-source.tsx` | `EducationController`; public release and academic visibility rules retained |
| Learning labs, quota, run/stop, requests and event stream | GET `/lab-manifests`, `/lab-quota`, `/lab-runs/{id}`, `/lab-runs/{id}/events`; POST `/lab-runs`, `/lab-runs/{id}/stop`, `/lab-runs/{id}/requests` | `education-workspace.tsx`, labs components | `LabController`; admitted runs and request idempotency remain server-owned |
| Sign up, sign in, MFA, password reset | POST `/auth/signup`, `/auth/login`, `/auth/login/mfa`, `/auth/password-reset/request` | `auth-form.tsx` | `AuthController`; session cookies and server validation unchanged |
| Provider availability and OAuth start | GET `/auth/providers`; POST `/auth/google/start`, `/auth/github/start` | `auth-form.tsx` | provider controllers; redirect origin checks and unavailable-provider state retained |
| Confirm email, inquiry and sensitive decisions | POST `/auth/confirmation`, `/auth/confirm`, `/auth/password-reset/confirm` | `confirmation.tsx` | `AuthController`; token decisions remain server-validated |
| Account/session read, logout, close, export, notifications | GET `/me`, `/me/export`, `/notifications`; POST `/auth/logout`; DELETE `/me`; PATCH `/notifications/{id}/read` | header, account menu/settings, workspace | auth/account controllers; signed-in session and authorization preserved |
| Save and submit builder profile | PUT `/developer/profile`; POST `/developer/profile/submit` | `profile-form.tsx` | `ProfileRequests.Profile`, `ProfileController`; approval and refresh-only retry retained |
| Create/edit/submit/archive project showcase | GET/POST `/developer/products`; PATCH `/developer/products/{id}`; POST `/developer/products/{id}/{action}` | workspace page, proof editor | `ProductsController`; revision and lifecycle rules retained |
| Create inquiry | POST `/inquiries` | `forms.tsx` | `InquiryRequests.Create`, `InquiriesController`; stable per-intent idempotency key, email confirmation, duplicate-submit guard |
| Review inquiry, transition, client response and contract | GET `/developer/inquiries`, `/developer/inquiries/{id}`, `/me/inquiries`; POST `/inquiries/{id}/{action}`, `/me/inquiries/{id}/{action}` and contract routes | workspace page, `client-requests.tsx`, delivery | inquiry/client/contract controllers; role and lifecycle permission remains backend-owned |
| Report concern and appeal | POST `/reports` | `forms.tsx`, component report panel | `InquiryRequests.Report`; component report panel retains exact revision/hash binding; generic report never invents artifact metadata |
| Component draft, ZIP upload, preview and submission | GET/POST `/me/components`; PATCH `/me/components/{id}`; POST `/me/components/{id}/source`, `/me/components/{id}/preview`, `/me/components/{id}/{action}` | component workspace and draft editor | `ComponentsController`; multipart boundaries, source consent, rights and validation unchanged |
| Publish college metadata/release, upload and submit | PUT `/me/college-projects/{id}`; POST `/me/college-projects/{productId}/releases`; PATCH `/me/education-releases/{id}`; POST source/submit | college and education workspaces | `ComponentsController`, `EducationController`; academic and frozen source approval retained |
| Publishing capacity and component slot purchase/recovery | GET pricing/history; POST publishing/component slot purchase, verify and reconcile | `publishing-slots.tsx`, component workspace | slot controllers; Razorpay acknowledgement and reconciliation remain required before capacity updates |
| Template catalogue, package purchase, verify, reconcile and download | GET `/templates`, `/templates/{slug}`, `/me/template-purchases`; POST `/templates/{id}/orders`, `/template-purchases/{id}/verify`, `/template-purchases/{id}/reconcile`; GET download | template marketplace/workspace, education purchase/source | commerce controllers; consent, pinned package, server receipts and download entitlement retained |
| Team profile, members, invitations, recruitment, leads, projects, staffing | GET/POST `/teams`; GET `/teams/{id}/workspace`; PUT/PATCH/POST/DELETE team subresources | `team-workspace.tsx`, `team-public.tsx` | `TeamsController`; membership/role/expiry rules remain on server |
| Business briefs, matches, shortlists, talent lists and concierge | GET/POST `/businesses`; PUT profile/request; GET/POST/DELETE request and talent-list entries; POST concierge | `business-workspace.tsx` | business/request/talent/concierge controllers; business membership and sourcing authority unchanged |
| Proposals, milestones, completion, disputes and payments | GET/POST `/engagements`; POST proposals/milestone/completion/dispute actions; POST payment order/verify/reconcile | `delivery-workspace.tsx`, delivery operations | delivery/payment controllers; provider facts and participant authority retained |
| Hosting source, upload, submit, deploy, withdraw and reconcile | GET `/hosting/config`, `/me/hosting`, `/me/hosting/sources`; POST hosting and lifecycle actions | hosting workspace/operations | `HostingController`; source entitlement, review and deployment evidence retained |
| Maintenance offer, support, billing confirmation/recovery | GET `/maintenance/config`, `/maintenance/sources`, `/maintenance`; POST maintenance/actions/billing/requests | maintenance workspace/operations | `MaintenanceController`; subscription/provider state remains server authority |
| Trust, availability, verification and private access | GET/PUT/POST trust, availability and verification routes; GET/POST/DELETE `/developer/products/{id}/access` | trust workspace, private access, similar builders | trust/private-project controllers; ownership and expiry validation unchanged |
| Moderation, reports, taxonomy, payments, release and account operations | GET/POST/PUT/PATCH existing `/admin/...` routes | admin/operations/review components | admin and domain controllers; authenticated administrator gates retained |

## Feedback contract and repairs

- `ApiError` retains `status`, `code`, `requestId`, `fieldErrors` and `Error.message` for existing consumers. HTTP error codes are preserved, including malformed 401/403 denials.
- The JSON helper preserves custom headers and stable idempotency keys, keeps multipart boundary generation in the browser, and forces same-origin credential behavior. It never retries a mutation.
- Network loss during a mutation reports an **unknown outcome** and asks the user to inspect the record before retrying. Intentional aborts remain cancellations. Invalid JSON, JSON primitives and `null` cannot confirm successful writes. Existing 204/205 acknowledgements remain supported.
- Unstructured 401/403/404/409/413/429 responses have specific next steps. Support references fall back to `X-Request-ID`. Unexpected/internal server failures keep the code/reference while withholding diagnostics, including internal `fieldErrors`. Typed provider-uncertainty errors retain their recovery instructions, such as reconciling a receipt before paying again.
- Error summaries receive focus, name affected fields, and move focus to a native control or a Radix picker inside its field wrapper. Profile errors now connect inline messages through `aria-describedby`.
- Inquiry/report/workspace forms guard concurrent submits with refs, retain values on failure, expose pending state, and announce confirmed outcomes. Inquiry creation keeps one idempotency key for an intent; it does not silently create a new key after an uncertain result.
- Reports keep source-bound component reporting in the component detail flow. Generic reporting does not fabricate component revisions or source hashes.
- Form copy no longer falsely says getLancer never handles payments; the existing delivery and commerce flows remain intact.

## Verification

`node --test tests/api-response.test.mjs tests/paper-api.test.mjs` exercises confirmed/unknown/malformed outcomes, abort, diagnostic suppression, authority status preservation, multipart boundary and idempotency headers. Existing browser and backend suites cover their existing domain routes. No backend schema, role policy, CSRF rule, or API route was removed for the visual rollout.

A separate source-mounted Chromium review of `TeamForm` verified one write for two synchronous submit attempts, disabled fields while pending, summary focus after rejection, dirty-value retention, and explicit movement from the error control to the invalid field with its inline message. This complements the full route-level browser suite; it does not execute privileged backend mutations.

## Backend route inventory

The following inventory is extracted from controller annotations at the current working tree. It lists contract locations for audit and is not an assertion that each endpoint was invoked in a browser.

Inventory: 290 annotated route patterns across 34 controllers.

| Method | Route | Controller |
|---|---|---|
| GET | `/api/v1/me/export` | `AccountController.java` |
| PATCH | `/api/v1/notifications/{id}/read` | `AccountController.java` |
| GET | `/api/v1/policies/config` | `PrivacyController.java` |
| GET | `/api/v1/admin/deletion-requests` | `PrivacyController.java` |
| POST | `/api/v1/admin/deletion-requests/{id}/review` | `PrivacyController.java` |
| GET | `/api/v1/admin/products/pending` | `AdminController.java` |
| GET | `/api/v1/admin/profiles/pending` | `AdminController.java` |
| GET | `/api/v1/admin/reports` | `AdminController.java` |
| GET | `/api/v1/admin/reviews` | `AdminController.java` |
| POST | `/api/v1/admin/products/{id}/{action}` | `AdminController.java` |
| POST | `/api/v1/admin/profiles/{id}/{action}` | `AdminController.java` |
| GET | `/api/v1/admin/reports/{id}` | `AdminController.java` |
| POST | `/api/v1/admin/reports/{id}/resolve` | `AdminController.java` |
| POST | `/api/v1/admin/reviews/{id}/{action}` | `AdminController.java` |
| POST | `/api/v1/admin/{kind:categories\|technologies}` | `AdminController.java` |
| GET | `/api/v1/admin/audit` | `AdminController.java` |
| GET | `/api/v1/admin/accounts` | `AdminController.java` |
| POST | `/api/v1/admin/accounts/{id}/{action}` | `AdminController.java` |
| POST | `/api/v1/admin/inquiries/{id}/{action}` | `AdminController.java` |
| GET | `/api/v1/admin/{kind:categories\|technologies}` | `AdminController.java` |
| GET | `/api/v1/admin/operations` | `AdminController.java` |
| GET | `/api/v1/admin/metrics` | `AdminController.java` |
| POST | `/api/v1/analytics/events` | `AnalyticsController.java` |
| POST | `/api/v1/auth/signup` | `AuthController.java` |
| POST | `/api/v1/auth/login` | `AuthController.java` |
| POST | `/api/v1/auth/login/mfa` | `AuthController.java` |
| POST | `/api/v1/auth/logout` | `AuthController.java` |
| GET | `/api/v1/me` | `AuthController.java` |
| POST | `/api/v1/auth/password-reset/request` | `AuthController.java` |
| POST | `/api/v1/auth/confirmation` | `AuthController.java` |
| POST | `/api/v1/auth/confirm` | `AuthController.java` |
| POST | `/api/v1/auth/verify-email` | `AuthController.java` |
| POST | `/api/v1/inquiries/confirm-email` | `AuthController.java` |
| POST | `/api/v1/auth/password-reset/confirm` | `AuthController.java` |
| POST | `/api/v1/auth/resend-verification` | `AuthController.java` |
| DELETE | `/api/v1/me` | `AuthController.java` |
| GET | `/api/v1/auth/providers` | `AuthProvidersController.java` |
| POST | `/api/v1/auth/github/start` | `GitHubAuthController.java` |
| GET | `/api/v1/auth/github/callback` | `GitHubAuthController.java` |
| POST | `/api/v1/auth/google/start` | `GoogleAuthController.java` |
| GET | `/api/v1/auth/google/callback` | `GoogleAuthController.java` |
| GET | `/api/v1/businesses` | `BusinessController.java` |
| POST | `/api/v1/businesses` | `BusinessController.java` |
| GET | `/api/v1/businesses/{id}` | `BusinessController.java` |
| PUT | `/api/v1/businesses/{id}` | `BusinessController.java` |
| POST | `/api/v1/businesses/{id}/invitations` | `BusinessController.java` |
| POST | `/api/v1/business-invitations/{id}/respond` | `BusinessController.java` |
| DELETE | `/api/v1/businesses/{id}/members/{userId}` | `BusinessController.java` |
| GET | `/api/v1/admin/concierge` | `ConciergeController.java` |
| GET | `/api/v1/admin/concierge/{id}/matches` | `ConciergeController.java` |
| PATCH | `/api/v1/admin/concierge/{id}` | `ConciergeController.java` |
| POST | `/api/v1/admin/concierge/{id}/shortlist` | `ConciergeController.java` |
| GET | `/api/v1/businesses/{id}/requests` | `ProjectRequestsController.java` |
| POST | `/api/v1/businesses/{id}/requests` | `ProjectRequestsController.java` |
| PUT | `/api/v1/businesses/{id}/requests/{requestId}` | `ProjectRequestsController.java` |
| GET | `/api/v1/businesses/{id}/requests/{requestId}/matches` | `ProjectRequestsController.java` |
| GET | `/api/v1/businesses/{id}/requests/{requestId}/shortlist` | `ProjectRequestsController.java` |
| POST | `/api/v1/businesses/{id}/requests/{requestId}/shortlist` | `ProjectRequestsController.java` |
| DELETE | `/api/v1/businesses/{id}/requests/{requestId}/shortlist/{entryId}` | `ProjectRequestsController.java` |
| POST | `/api/v1/businesses/{id}/requests/{requestId}/concierge` | `ProjectRequestsController.java` |
| GET | `/api/v1/businesses/{id}/talent-lists` | `TalentListsController.java` |
| POST | `/api/v1/businesses/{id}/talent-lists` | `TalentListsController.java` |
| GET | `/api/v1/businesses/{id}/talent-lists/{listId}/entries` | `TalentListsController.java` |
| POST | `/api/v1/businesses/{id}/talent-lists/{listId}/entries` | `TalentListsController.java` |
| DELETE | `/api/v1/businesses/{id}/talent-lists/{listId}/entries/{entryId}` | `TalentListsController.java` |
| GET | `/api/v1/templates` | `CommerceController.java` |
| GET | `/api/v1/templates/{slug}` | `CommerceController.java` |
| GET | `/api/v1/me/templates` | `CommerceController.java` |
| POST | `/api/v1/me/templates` | `CommerceController.java` |
| POST | `/api/v1/me/templates/{id}/versions` | `CommerceController.java` |
| POST | `/api/v1/me/templates/{id}/versions/{version}/submit` | `CommerceController.java` |
| POST | `/api/v1/me/templates/{id}/activate` | `CommerceController.java` |
| POST | `/api/v1/me/templates/{id}/archive` | `CommerceController.java` |
| GET | `/api/v1/me/templates/{id}/versions/{version}/package` | `CommerceController.java` |
| GET | `/api/v1/admin/templates` | `CommerceController.java` |
| POST | `/api/v1/admin/templates/{id}/versions/{version}/review` | `CommerceController.java` |
| GET | `/api/v1/admin/templates/{id}/versions/{version}/package` | `CommerceController.java` |
| GET | `/api/v1/me/template-purchases` | `CommercePaymentsController.java` |
| POST | `/api/v1/templates/{id}/orders` | `CommercePaymentsController.java` |
| POST | `/api/v1/template-purchases/{id}/verify` | `CommercePaymentsController.java` |
| POST | `/api/v1/template-purchases/{id}/reconcile` | `CommercePaymentsController.java` |
| POST | `/api/v1/template-purchases/{id}/disputes` | `CommercePaymentsController.java` |
| GET | `/api/v1/template-purchases/{id}/download` | `CommercePaymentsController.java` |
| POST | `/api/v1/commerce/razorpay/webhook` | `CommercePaymentsController.java` |
| GET | `/api/v1/admin/template-purchases/attention` | `CommercePaymentsController.java` |
| POST | `/api/v1/admin/template-purchases/{id}/bind-order` | `CommercePaymentsController.java` |
| POST | `/api/v1/admin/template-purchases/{id}/reconcile` | `CommercePaymentsController.java` |
| POST | `/api/v1/admin/template-purchases/{id}/resolve` | `CommercePaymentsController.java` |
| GET | `/api/v1/me/saved-components` | `ComponentBookmarksController.java` |
| POST | `/api/v1/components/{slug}/save` | `ComponentBookmarksController.java` |
| DELETE | `/api/v1/components/{slug}/save` | `ComponentBookmarksController.java` |
| GET | `/api/v1/components/slots/pricing` | `ComponentSlotsController.java` |
| GET | `/api/v1/me/component-slot-purchases` | `ComponentSlotsController.java` |
| POST | `/api/v1/me/component-slot-purchases` | `ComponentSlotsController.java` |
| POST | `/api/v1/me/component-slot-purchases/{id}/verify` | `ComponentSlotsController.java` |
| POST | `/api/v1/me/component-slot-purchases/{id}/reconcile` | `ComponentSlotsController.java` |
| GET | `/api/v1/admin/component-slot-purchases` | `ComponentSlotsController.java` |
| PUT | `/api/v1/admin/component-slot-pricing` | `ComponentSlotsController.java` |
| POST | `/api/v1/admin/component-slot-purchases/{id}/reconcile` | `ComponentSlotsController.java` |
| POST | `/api/v1/admin/component-slot-purchases/{id}/bind-order` | `ComponentSlotsController.java` |
| POST | `/api/v1/components/razorpay/webhook` | `ComponentSlotsController.java` |
| GET | `/api/v1/components` | `ComponentsController.java` |
| GET | `/api/v1/components/{slug}` | `ComponentsController.java` |
| GET | `/api/v1/components/{slug}/preview` | `ComponentsController.java` |
| GET | `/api/v1/components/{slug}/source` | `ComponentsController.java` |
| POST | `/api/v1/me/components/{id}/preview` | `ComponentsController.java` |
| GET | `/api/v1/components/{slug}/versions` | `ComponentsController.java` |
| GET | `/api/v1/me/components` | `ComponentsController.java` |
| GET | `/api/v1/me/components/{id}` | `ComponentsController.java` |
| POST | `/api/v1/me/components` | `ComponentsController.java` |
| PATCH | `/api/v1/me/components/{id}` | `ComponentsController.java` |
| POST | `/api/v1/me/components/{id}/source` | `ComponentsController.java` |
| POST | `/api/v1/me/components/{id}/{action}` | `ComponentsController.java` |
| GET | `/api/v1/admin/components/{id}` | `ComponentsController.java` |
| GET | `/api/v1/admin/components/review` | `ComponentsController.java` |
| POST | `/api/v1/admin/components/{id}/review` | `ComponentsController.java` |
| GET | `/api/v1/me/college-projects` | `ComponentsController.java` |
| PUT | `/api/v1/me/college-projects/{id}` | `ComponentsController.java` |
| POST | `/api/v1/admin/college-projects/{id}/review` | `ComponentsController.java` |
| GET | `/api/v1/engagements` | `DeliveryController.java` |
| POST | `/api/v1/engagements` | `DeliveryController.java` |
| GET | `/api/v1/engagements/{id}` | `DeliveryController.java` |
| POST | `/api/v1/engagements/{id}/proposals` | `DeliveryController.java` |
| POST | `/api/v1/engagements/{id}/proposals/{proposalId}/{action}` | `DeliveryController.java` |
| POST | `/api/v1/engagements/{id}/milestones/{milestoneId}/{action}` | `DeliveryController.java` |
| POST | `/api/v1/engagements/{id}/completion` | `DeliveryController.java` |
| POST | `/api/v1/engagements/{id}/disputes` | `DeliveryController.java` |
| GET | `/api/v1/admin/delivery/disputes` | `DeliveryController.java` |
| POST | `/api/v1/admin/delivery/disputes/{id}/resolve` | `DeliveryController.java` |
| GET | `/api/v1/college-projects` | `EducationController.java` |
| GET | `/api/v1/college-projects/{slug}` | `EducationController.java` |
| GET | `/api/v1/me/education-releases` | `EducationController.java` |
| GET | `/api/v1/me/education-releases/{id}` | `EducationController.java` |
| POST | `/api/v1/me/college-projects/{productId}/releases` | `EducationController.java` |
| PATCH | `/api/v1/me/education-releases/{id}` | `EducationController.java` |
| POST | `/api/v1/me/education-releases/{id}/source` | `EducationController.java` |
| POST | `/api/v1/me/education-releases/{id}/submit` | `EducationController.java` |
| PATCH | `/api/v1/me/education-releases/{id}/academic` | `EducationController.java` |
| GET | `/api/v1/admin/education-releases` | `EducationController.java` |
| GET | `/api/v1/admin/education-releases/{id}` | `EducationController.java` |
| POST | `/api/v1/admin/education-releases/{id}/package` | `EducationController.java` |
| POST | `/api/v1/admin/education-releases/{id}/review` | `EducationController.java` |
| GET | `/api/v1/education-releases/{id}/source-offer` | `EducationController.java` |
| GET | `/api/v1/education-releases/{id}/source` | `EducationController.java` |
| GET | `/api/v1/hosting/config` | `HostingController.java` |
| GET | `/api/v1/me/hosting/sources` | `HostingController.java` |
| GET | `/api/v1/me/hosting` | `HostingController.java` |
| GET | `/api/v1/me/hosting/{id}` | `HostingController.java` |
| POST | `/api/v1/me/hosting` | `HostingController.java` |
| POST | `/api/v1/me/hosting/{id}/submit` | `HostingController.java` |
| POST | `/api/v1/me/hosting/{id}/deploy` | `HostingController.java` |
| POST | `/api/v1/me/hosting/{id}/withdraw` | `HostingController.java` |
| POST | `/api/v1/me/hosting/{id}/reconcile` | `HostingController.java` |
| GET | `/api/v1/me/hosting/{id}/package` | `HostingController.java` |
| GET | `/api/v1/admin/hosting` | `HostingController.java` |
| GET | `/api/v1/admin/hosting/{id}/package` | `HostingController.java` |
| POST | `/api/v1/admin/hosting/{id}/review` | `HostingController.java` |
| POST | `/api/v1/admin/hosting/{id}/reconcile` | `HostingController.java` |
| POST | `/api/v1/admin/hosting/{id}/withdraw` | `HostingController.java` |
| GET | `/api/v1/hosting/products/{productId}` | `HostingController.java` |
| GET | `/api/v1/hosting/gateway/{deploymentId}` | `HostingController.java` |
| GET | `/api/v1/me/inquiries/{id}` | `ClientController.java` |
| POST | `/api/v1/me/inquiries/{id}/decision` | `ClientController.java` |
| POST | `/api/v1/me/inquiries/{id}/review` | `ClientController.java` |
| POST | `/api/v1/me/inquiries/{id}/confirmation-link` | `ClientController.java` |
| POST | `/api/v1/me/inquiries/{id}/not-hired` | `ClientController.java` |
| POST | `/api/v1/inquiries` | `InquiriesController.java` |
| GET | `/api/v1/developer/inquiries` | `InquiriesController.java` |
| GET | `/api/v1/developer/inquiries/{id}` | `InquiriesController.java` |
| POST | `/api/v1/inquiries/{id}/{action}` | `InquiriesController.java` |
| POST | `/api/v1/reports` | `InquiriesController.java` |
| GET | `/api/v1/developer/analytics` | `InquiriesController.java` |
| GET | `/api/v1/notifications` | `InquiriesController.java` |
| POST | `/api/v1/inquiries/{id}/confirm-hire` | `InquiryContractController.java` |
| POST | `/api/v1/inquiries/{id}/confirm-completion` | `InquiryContractController.java` |
| POST | `/api/v1/inquiries/{id}/not-hired` | `InquiryContractController.java` |
| POST | `/api/v1/inquiries/{id}/review` | `InquiryContractController.java` |
| GET | `/api/v1/lab-manifests` | `LabController.java` |
| GET | `/api/v1/lab-quota` | `LabController.java` |
| POST | `/api/v1/lab-runs` | `LabController.java` |
| GET | `/api/v1/lab-runs/{id}` | `LabController.java` |
| POST | `/api/v1/lab-runs/{id}/stop` | `LabController.java` |
| POST | `/api/v1/lab-runs/{id}/requests` | `LabController.java` |
| GET | `/api/v1/lab-runs/{id}/events` | `LabController.java` |
| GET | `/api/v1/admin/labs` | `LabController.java` |
| POST | `/api/v1/admin/labs/pause-admissions` | `LabController.java` |
| POST | `/api/v1/admin/labs/limits` | `LabController.java` |
| POST | `/api/v1/admin/labs/certifications` | `LabController.java` |
| GET | `/api/v1/maintenance/config` | `MaintenanceController.java` |
| GET | `/api/v1/maintenance/sources` | `MaintenanceController.java` |
| GET | `/api/v1/maintenance` | `MaintenanceController.java` |
| POST | `/api/v1/maintenance` | `MaintenanceController.java` |
| GET | `/api/v1/maintenance/{id}` | `MaintenanceController.java` |
| POST | `/api/v1/maintenance/{id}/{action}` | `MaintenanceController.java` |
| POST | `/api/v1/maintenance/{id}/billing` | `MaintenanceController.java` |
| POST | `/api/v1/maintenance/{id}/billing/confirm` | `MaintenanceController.java` |
| POST | `/api/v1/maintenance/{id}/billing/refresh` | `MaintenanceController.java` |
| POST | `/api/v1/maintenance/{id}/billing/cancel` | `MaintenanceController.java` |
| POST | `/api/v1/maintenance/{id}/requests` | `MaintenanceController.java` |
| POST | `/api/v1/maintenance/{id}/requests/{requestId}/{action}` | `MaintenanceController.java` |
| GET | `/api/v1/admin/maintenance/attention` | `MaintenanceController.java` |
| POST | `/api/v1/admin/maintenance/{id}/{action}` | `MaintenanceController.java` |
| POST | `/api/v1/maintenance/razorpay/webhook` | `MaintenanceController.java` |
| POST | `/api/v1/developer/products/{product}/media` | `MediaController.java` |
| POST | `/api/v1/developer/products/{product}/media/upload-request` | `MediaController.java` |
| POST | `/api/v1/developer/products/{product}/media/complete` | `MediaController.java` |
| GET | `/api/v1/developer/products/{product}/proof` | `MediaController.java` |
| PATCH | `/api/v1/developer/products/{product}/media` | `MediaController.java` |
| DELETE | `/api/v1/developer/products/{product}/media/{id}` | `MediaController.java` |
| GET | `/api/v1/media/{id}` | `MediaController.java` |
| POST | `/api/v1/admin/reports/{id}/triage` | `ModerationController.java` |
| GET | `/api/v1/me/moderation-decisions` | `ModerationController.java` |
| POST | `/api/v1/appeals` | `ModerationController.java` |
| GET | `/api/v1/me/appeals` | `ModerationController.java` |
| GET | `/api/v1/admin/appeals` | `ModerationController.java` |
| POST | `/api/v1/admin/appeals/{id}/decision` | `ModerationController.java` |
| GET | `/api/v1/admin/email-jobs` | `ModerationController.java` |
| POST | `/api/v1/admin/email-jobs/{id}/retry` | `ModerationController.java` |
| GET | `/api/v1/payments/config` | `PaymentController.java` |
| GET | `/api/v1/engagements/{id}/payments` | `PaymentController.java` |
| POST | `/api/v1/milestones/{id}/payment-order` | `PaymentController.java` |
| POST | `/api/v1/payments/{id}/verify` | `PaymentController.java` |
| POST | `/api/v1/payments/{id}/reconcile` | `PaymentController.java` |
| POST | `/api/v1/payments/razorpay/webhook` | `PaymentController.java` |
| GET | `/api/v1/admin/payments/accounts` | `PaymentController.java` |
| POST | `/api/v1/admin/payments/accounts` | `PaymentController.java` |
| GET | `/api/v1/admin/payments/attention` | `PaymentController.java` |
| POST | `/api/v1/admin/payments/{id}/reconcile` | `PaymentController.java` |
| POST | `/api/v1/admin/payments/{id}/bind-order` | `PaymentController.java` |
| GET | `/api/v1/developer/products/{id}/access` | `PrivateProjectsController.java` |
| POST | `/api/v1/developer/products/{id}/access` | `PrivateProjectsController.java` |
| DELETE | `/api/v1/developer/products/{id}/access` | `PrivateProjectsController.java` |
| GET | `/api/v1/private/products/{id}` | `PrivateProjectsController.java` |
| GET | `/api/v1/products` | `ProductsController.java` |
| GET | `/api/v1/products/{slug}` | `ProductsController.java` |
| GET | `/api/v1/builders/{slug}` | `ProductsController.java` |
| GET | `/api/v1/builders/{slug}/reviews` | `ProductsController.java` |
| GET | `/api/v1/developer/products` | `ProductsController.java` |
| POST | `/api/v1/developer/products` | `ProductsController.java` |
| PATCH | `/api/v1/developer/products/{id}` | `ProductsController.java` |
| POST | `/api/v1/developer/products/{id}/{action}` | `ProductsController.java` |
| POST | `/api/v1/products/{id}/save` | `ProductsController.java` |
| DELETE | `/api/v1/products/{id}/save` | `ProductsController.java` |
| GET | `/api/v1/me/saved-products` | `ProductsController.java` |
| GET | `/api/v1/categories` | `ProductsController.java` |
| GET | `/api/v1/technologies` | `ProductsController.java` |
| PUT | `/api/v1/developer/profile` | `ProfileController.java` |
| POST | `/api/v1/developer/profile/submit` | `ProfileController.java` |
| GET | `/api/v1/publishing-slots/pricing` | `PublishingSlotsController.java` |
| GET | `/api/v1/me/publishing-slots` | `PublishingSlotsController.java` |
| GET | `/api/v1/me/publishing-slot-purchases` | `PublishingSlotsController.java` |
| POST | `/api/v1/me/publishing-slot-purchases` | `PublishingSlotsController.java` |
| POST | `/api/v1/me/publishing-slot-purchases/{id}/verify` | `PublishingSlotsController.java` |
| POST | `/api/v1/me/publishing-slot-purchases/{id}/reconcile` | `PublishingSlotsController.java` |
| GET | `/api/v1/admin/publishing-slot-purchases` | `PublishingSlotsController.java` |
| PUT | `/api/v1/admin/publishing-slots/{pool}/pricing` | `PublishingSlotsController.java` |
| POST | `/api/v1/admin/publishing-slot-purchases/{id}/reconcile` | `PublishingSlotsController.java` |
| POST | `/api/v1/admin/publishing-slot-purchases/{id}/bind-order` | `PublishingSlotsController.java` |
| GET | `/api/v1/teams` | `TeamsController.java` |
| POST | `/api/v1/teams` | `TeamsController.java` |
| GET | `/api/v1/teams/{id}` | `TeamsController.java` |
| GET | `/api/v1/me/teams` | `TeamsController.java` |
| GET | `/api/v1/teams/{id}/workspace` | `TeamsController.java` |
| PUT | `/api/v1/teams/{id}` | `TeamsController.java` |
| GET | `/api/v1/teams/{id}/candidates` | `TeamsController.java` |
| POST | `/api/v1/teams/{id}/invitations` | `TeamsController.java` |
| POST | `/api/v1/team-invitations/{id}/respond` | `TeamsController.java` |
| PATCH | `/api/v1/teams/{id}/members/{userId}` | `TeamsController.java` |
| DELETE | `/api/v1/teams/{id}/members/{userId}` | `TeamsController.java` |
| POST | `/api/v1/teams/{id}/roles` | `TeamsController.java` |
| PATCH | `/api/v1/teams/{id}/roles/{roleId}` | `TeamsController.java` |
| POST | `/api/v1/teams/{id}/roles/{roleId}/applications` | `TeamsController.java` |
| POST | `/api/v1/teams/{id}/applications/{applicationId}/decision` | `TeamsController.java` |
| POST | `/api/v1/teams/{id}/leads` | `TeamsController.java` |
| POST | `/api/v1/teams/{id}/leads/{leadId}/respond` | `TeamsController.java` |
| PATCH | `/api/v1/teams/{id}/leads/{leadId}` | `TeamsController.java` |
| GET | `/api/v1/teams/{id}/leads/{leadId}/notes` | `TeamsController.java` |
| POST | `/api/v1/teams/{id}/projects` | `TeamsController.java` |
| DELETE | `/api/v1/teams/{id}/projects/{productId}` | `TeamsController.java` |
| POST | `/api/v1/teams/{id}/staffing` | `TeamsController.java` |
| PATCH | `/api/v1/teams/{id}/staffing/{staffingId}` | `TeamsController.java` |
| GET | `/api/v1/admin/teams` | `TeamsController.java` |
| POST | `/api/v1/admin/teams/{id}/moderate` | `TeamsController.java` |
| GET | `/api/v1/developer/trust` | `TrustController.java` |
| PUT | `/api/v1/developer/availability` | `TrustController.java` |
| POST | `/api/v1/developer/products/{id}/verification` | `TrustController.java` |
| GET | `/api/v1/admin/trust` | `TrustController.java` |
| POST | `/api/v1/admin/verifications/{id}/{action}` | `TrustController.java` |
| POST | `/api/v1/admin/earned-capacity/{id}` | `TrustController.java` |
| GET | `/api/v1/products/{slug}/similar-builders` | `TrustController.java` |
