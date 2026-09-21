# Interactive V1 demo

Open `/preview/workspace`, or choose **Try the interactive demo** on the disconnected login/signup page. No credentials are needed. Use **Test as** to switch between Alex (client), Leah and Daniel (builders), and Sam (administrator). These are fictional testing identities, not real authenticated accounts.

The demo reuses the existing visual components and follows the core V1 state transitions. It keeps sample state in this browser tab's session storage. Refresh retains progress; Reset demo clears it. If browser storage is blocked, the demo still runs in memory. Use fictional briefs and reviews.

## Journeys to test

1. **Discovery:** as Alex, filter by React and SaaS, search Stockroom, save it, open its preview and inspect the builder contribution/profile.
2. **Inquiry:** choose Build something similar, enter a brief/budget/timeline and send. Open My requests → Demo inbox → Confirm inquiry email. Before confirmation it is absent from the builder's qualified inbox.
3. **Client-confirmed work:** continue as Leah and mark responded → discussion → proposal sent → request hire confirmation. Continue as Alex to confirm or reject. After confirmation, Leah marks in progress and requests completion. Only Alex can confirm completion.
4. **Reviews:** after completion, Alex submits one review, choosing a named or anonymous identity. Continue as Sam to moderate it. Published reviews appear on the builder's project previews; pending/hidden reviews do not.
5. **Publishing:** as Leah, open Drafts & review → Customer portal, add sample proof, submit. As Sam, approve or request changes with a reason. Approval activates if capacity is available; otherwise it remains an approved draft. Leah starts with three active showcases: archive one before activating the fourth.
6. **Profile and moderation:** Alex can edit and submit a builder profile, then Sam reviews it. Anyone in the demo can report a project; Sam records a decision. Restricted projects leave discovery. The decision history records the sample actor and reason.
7. **Dual use:** Leah can explore Daniel's projects and send her own client request. A builder cannot inquire on their own project. Saves are separate for each sample person.
8. **Repeatability:** refresh, switch accounts, inspect history/outcomes, then use Reset demo to begin again.

## Boundary

This is flow/design UAT, **not connected integration acceptance**. It does not test real signup, passwords, MFA, OAuth, email delivery, files, private grants, expiry/rate limits, exports/deletion, backups or production security. The demo attaches illustrative proof instead of uploading files. Email confirmation is an explicitly labeled in-page simulation. The existing `/api/v1` and real account routes retain their normal backend requirements; they never fall back to fabricated authentication or demo records.

Template purchases, subscriptions and source-code downloads remain outside V1. No production data, accounts, mail, storage or analytics are changed by sample actions.

## Verification

`tests/demo-flow.test.mjs` exercises the complete inquiry/review loop, rejection paths, ownership checks, proof/review/three-slot gates, automatic activation, profile approval, report removal, dual-use accounts and independent saved state. The network is disabled during the core simulator test. Existing rendered-route tests ensure the real API still returns `BACKEND_NOT_CONFIGURED` when disconnected.

Run `npm test` for the built frontend checks. Use the actual Spring/PostgreSQL environment and all six journeys in `10_TESTING_QA.txt` before approving connected V1 operation.
