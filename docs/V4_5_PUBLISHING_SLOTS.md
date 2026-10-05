# V4.5 publishing capacity

Approved builders receive three free active publishing places in each category:

| Category | Free places | Extra capacity |
| --- | --- | --- |
| Regular projects | 3 | Purchased PROJECT slots or existing earned showcase extras |
| College projects | 3 | The same PROJECT extras, usable for either project category |
| Templates | 3 | Purchased TEMPLATE slots |
| Components | 3 | Purchased COMPONENT slots |

The free allowances provide 12 places in total. Unused college places cannot fund a fourth regular project. A purchased project extra serves one regular or college project at a time. Project usage is `max(regular - 3, 0) + max(college - 3, 0) <= purchased + earned`.

College context marks the existing product as a college project. Its review status does not move it back to the regular category. Save context while the project is a draft, or convert an active project only when college capacity is available. Showcase and academic reviews still apply independently.

Drafts, pending entries and archives do not occupy active places. Archiving frees capacity; purchased places remain reusable. Template publishing consumes capacity on first approval or reactivation, not on each source version. Approved archived template releases can be reactivated when their seller and proof project remain eligible and a place is available.

Administrators set three independent INR prices for PROJECT, TEMPLATE and COMPONENT extras in **Workspace → Publishing slots**. The PROJECT price applies to either project category. Prices start disabled until configured; no default price or live provider approval is invented. Template source selling prices and publishing fees are separate. Components remain free for visitors.

Checkout reserves one category and its displayed price. Those facts cannot change after reservation. Idempotency keys cannot cross categories, and unresolved orders are unique per owner/category/provider mode. An old order at a different price is shown for renewed consent rather than silently charged. Provider capture is reconciled before granting capacity; test captures never add live places. Partial/full refunds remove that purchased place, and unresolved/lost disputes hold it. Capacity loss archives only excess records in the affected pool, retaining each project's three-free category allowance, prior licensed source access and published free component snapshots. Resolving a hold restores capacity; archived work is explicitly reactivated, never silently republished.

V25 preserves component purchase IDs, immutable receipt amounts/modes, ledgers and event history. Existing earned project extras continue to count. Existing active template capacity above three is retained through an immutable migration grant; future additional places require purchase. Roll back application code together with a compatible database release. Do not drop V25 or delete financial records after orders exist.

The existing signed `/api/v1/components/razorpay/webhook` remains the webhook for all publishing-slot purchases; it determines the pool from the immutable receipt. Existing component-only endpoints remain compatible. New APIs expose `/publishing-slots/pricing`, `/me/publishing-slots`, category-filtered `/me/publishing-slot-purchases`, and MFA-protected `/admin/publishing-slots/{pool}/pricing` and recovery operations.

Validation covers independent free capacity, concurrent activation/approval, archive reuse, college conversion, frozen purchase pools/prices, category-scoped refund handling, legacy grants, actor isolation, mobile layouts and disabled preview payments. CI also runs existing commerce, connected journeys and restore checks.
