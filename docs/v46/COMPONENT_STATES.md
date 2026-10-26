# Curated component interaction acceptance

The executable contract is `scripts/component-state-contract.mjs`. It enumerates all 24 current seeds and all eight states, and rejects omissions, duplicate components and unexplained exclusions. `tests/browser/component-states.spec.ts` runs the same assertions in the regular desktop, phone and tablet browser projects. The independent command below renders each actual package's `index.html`, with its own CSP and script, without requiring the application server:

```sh
node --test tests/component-states.test.mjs
node scripts/component-state-contract.mjs --browser
```

The browser command prints each passing slug and its actual Chromium version only after the assertions execute. Source or matrix validation alone does not establish browser acceptance. Set `COMPONENT_STATE_BROWSER_EXECUTABLE` only when using an installed Chromium binary; otherwise use the project's pinned Playwright browser. Versions and package reproduction are recorded in `COMPONENT_SETUP.md`. These are synthetic browser-local recipes; they do not certify real account services, delivery, payments or production accessibility.

## Common pointer and keyboard contract

Each row includes `hover`, `focus` and `active` checks. The browser measures the computed hover ring on a representative enabled control and verifies a distinct pointer-down ring for `active`; it does not merely search for CSS selectors. `focus` checks every initially visible enabled button, link, input, select, textarea and summary: keyboard modality, actual focus and a visible outline of at least 2px. Component-specific actions below also assert selected/pressed state, expansion and focus transfer where present.

`disabled` and `loading` apply to the two simulated asynchronous operations. Both are verified before advancing the browser clock through the documented 700ms delay, followed by re-enablement and the final result. Other recipes perform synchronous local actions, so disabling or a loading interval would misrepresent their behavior.

`empty` covers a genuinely reachable empty result, missing session or blank user-entry path. Fixed nonempty synthetic content does not acquire an artificial empty state. `error` covers actual validation/failure branches; purely local navigation and toggles have no failing service to simulate. Native form validation is checked through `validity`, nonempty `validationMessage`, focus and prevention of success. Custom validation is checked through visible associated messages and `aria-invalid`, then correction and recovery.

## Per-component matrix

H/F/A means the common hover/focus/active browser checks above. `— sync` means no disabled/loading branch because the operation is synchronous. `— fixed` means the recipe has fixed nonempty content with no empty collection or entry workflow. `— local` means no fallible operation or user-data validation. Every exclusion is deliberate and the executable matrix contains the full reason.

| Component | hover / focus / active | disabled | loading | empty | error | success and reversible state |
|---|---|---|---|---|---|---|
| studio-navigation | H/F/A | — sync | — sync | — fixed | — local | Expand menu, select About with `aria-current`, hide menu again. |
| focus-sidebar | H/F/A | — sync | — sync | — fixed | — local | Projects heading/current marker, collapse and restore rail. |
| project-brief | H/F/A | — sync | — sync | Blank name/goal and confirmation. | Native required validation blocks blank name. | Save entered project, then reset fields and confirmation. |
| portfolio-card | H/F/A | — sync | — sync | — fixed | — local | Save/unsave pressed state and announcement; expand details. |
| quiet-sign-in | H/F/A | — sync | — sync | Blank required email rejected. | Wrong disclosed demo credentials show failure. | Password visibility toggle and successful synthetic login. |
| project-pulse | H/F/A | — sync | — sync | — fixed: supplied filters all have matching rows. | — local | Active filter has two rows; descending tasks starts with Research portal. |
| workspace-switcher | H/F/A | — sync | — sync | — fixed | — local | Workspace heading follows Research and action uses that context. |
| section-tabs | H/F/A | — sync | — sync | — fixed | — local | Files content and exactly one pressed section. |
| search-navigation | H/F/A | — sync | — sync | Unmatched search has no visible destinations and announces it. | — local | Search recovers Settings and opening it announces result. |
| folder-rail | H/F/A | — sync | — sync | — fixed | — local | Expand Assets, open Icons and update heading/announcement. |
| filter-rail | H/F/A | — sync | — sync | — fixed: both statuses contain sample rows. | — local | Archived shows one row; clear restores three. |
| settings-rail | H/F/A | — sync | — sync | — fixed | — local | Notifications selection exposes checkbox; unchecking announces disabled preference. |
| feedback-form | H/F/A | Submit disabled while saving, then enabled. | Sending label and saving announcement before completion. | Blank note rejected. | Associated error, invalid marker and input focus; corrected input clears error. | Valid feedback produces local-save announcement. |
| estimate-form | H/F/A | — sync | — sync | Blank required screens rejected. | Native minimum rejects zero. | Four screens plus API produces thirteen illustrative effort days. |
| step-form | H/F/A | — sync | — sync | Blank required name rejected. | Invalid first step stays visible. | Review preserves entered name; save result; Back restores input focus. |
| comparison-card | H/F/A | — sync | — sync | — fixed | — local | Studio/Starter selection and exactly one pressed plan. |
| expandable-case-study | H/F/A | — sync | — sync | — fixed | — local | Details expand; Helpful pressed state and recorded-feedback result. |
| task-card | H/F/A | — sync | — sync | — fixed | — local | Complete changes task text, pressed state and progress to100; undo restores0. |
| access-code-form | H/F/A | — sync | — sync | Empty code rejected. | Visible code error, invalid marker and focus; correct code clears error. | Disclosed sample code verified. |
| recovery-state | H/F/A | Recovery disabled while preparing, then enabled. | Preparing label and announcement before completion. | — fixed | — local: no identity lookup occurs. | Privacy-preserving synthetic result without claiming email delivery. |
| session-panel | H/F/A | — sync | — sync | Ending synthetic session reveals restart action and transfers focus. | — local | Restart restores active state and end-action focus. |
| activity-dashboard | H/F/A | — sync | — sync | — fixed: both periods have sample data. | — local |30-day range changes both counts and announcement. |
| sortable-inventory | H/F/A | — sync | — sync | Unmatched search removes rows and announces no items. | — local | Clear search restores rows; descending names starts with Research journal. |
| release-checklist | H/F/A | — sync | — sync | — fixed: three pending checks remain visible. | — local | All checked gives progress3 and ready message; undo gives2/3. |

## Evidence boundary

The matrix records applicable behavior, including explicit exclusions. The executable runner must pass against the final changed seed bytes before these states can be called verified. Tests assert no unhandled recipe script errors. They preserve synthetic credentials and use no real account, network API or merchant data. Automated keyboard and computed-style checks complement the separate human screen-reader, zoom and task-comprehension launch gate; they do not close it.
