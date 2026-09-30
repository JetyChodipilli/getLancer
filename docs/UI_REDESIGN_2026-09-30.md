# Workspace and preview redesign — 30 September 2026

The personal, trust and team workspaces now use one layout with a persistent desktop navigation rail, compact page headings, consistent metric cards and horizontal shadcn tabs. Preview account and reset controls live in a labeled disclosure. The team overview groups members, shared products and availability. Profile fields are grouped by purpose; editing dialogs have one content column and a bounded scrolling body. Inquiry and report forms use the same spacing and field treatment.

The six frontend catalog illustrations were rebuilt as original, aligned application screens. Real projects still render their uploaded media; a missing screenshot produces a placeholder, never an illustration. The discovery gallery keeps its four-column masonry layout and the existing brand assets.

## Design references

UI UX Pro Max supplied accessibility, field grouping, feedback and responsive guidance. Shadcn Dashboard Free and Shadcn Dashboard Template informed navigation, tabs and form patterns using the primitives already installed. The requested dashboard template bundle was unavailable, so no template source or MSW backend was copied. Awesome Design MD's Cursor reference informed the quiet surfaces and typography, adapted to getLancer's existing white and cobalt palette. DESIGN.md defines the implementation rules.

## Verification and limits

- TypeScript and the production build pass.
- All 54 frontend regressions pass, including connected empty catalogs, backend failures, preview redirects and authentication separation.
- Cloud-browser desktop review covered discovery, all three workspace areas, tabs, account switching, repository-review simulation, editing dialogs and the report form. Escape closes the editing dialog.
- The cloud browser enforces a fixed desktop viewport. Phone and landscape breakpoints were reviewed in source; a device or resizable-browser visual check remains necessary.
- No Java backend or API contract changed. This deployment publishes only the frontend, with DEMO_MODE=true and no BACKEND_URL. Configuring BACKEND_URL continues to select real services and block preview routes; empty/error responses never fall back to samples.
- This redesign does not certify every outstanding V2 feature or a production rollout of the backend.
