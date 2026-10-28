# Paper & Pixel implementation review

The approved P4 homepage is the design reference: a charcoal exhibition of working software followed by white component, college and learning chapters. Published project content and embedded preview documents keep their own identity. Shared site navigation, forms, task surfaces and feedback use the new getLancer design system.

## Review findings and repairs

| Finding | Repair | Verification |
|---|---|---|
| Old grid selectors constrained search/menu to one44px column and overlapped Sign in | A stable navigation-bar ID enforces flex layout; search/menu reserve their own96px group | Fresh1440px browser measurement shows28px separation from Sign in; mobile layouts reviewed |
| Legacy public/workspace selectors retained backdrop blur | Prefix-order correction preserves the native reset in the production CSS; a scoped outer-surface material guard overrides older route rules | Compiled stylesheet inspected; final material assertions remain enforced |
| Correcting a validation error on blur could move the password toggle before pointer-up | Already-invalid fields revalidate while typing; moving focus to the password disclosure does not revalidate the field | Auth regression suite and actual invalid-submit/password-disclosure browser flow |
| Nested select labels made the intended label ambiguous to automation | Separate labels and explicit IDs for filters and the component specimen | Search/filter/history and interactive component tests |
| Some mobile controls were below16px or44px | Restored specimen select styling and raised editable controls/touch targets | Four-width computed-style and bounds review, including320px |
| Failed logout had no recoverable response | Single-flight logout, disabled pending action, retained account on failure and dismissible alert | Browser fixture verifies POST/header, retained account and focus restoration |
| Malformed session/auth responses could be treated as usable data | Guarded session fields; auth responses require the actual backend contract before success | Typecheck, API/auth regressions and source contract review |
| Unexpected internal errors could disclose diagnostics through messages or field errors | Internal error text and field details are masked; typed domain recovery instructions remain | API negative controls include private-looking diagnostic text and unconfirmed-payment instructions |

## Visual review

Discovery was reviewed at1440,768,375 and320px with actual catalogue props, long queries, three featured previews and six illustrative collection entries. Search, selection and local feedback interactions work without horizontal overflow or page errors. The original Cutframe logo was rendered at16/32px and large sizes, reviewed in one colour and reversed, and exported as inert SVG geometry.

Auth, project detail, components and preview workspaces were reviewed in the production Worker. Preview/sample disclosures remain explicit; account service failures do not fabricate authentication or live business records. Form summaries take focus after failed submission, then let the user move directly to the invalid control without losing input.

## Security review boundary

An independent source review found no introduced or worsened security finding in the changed frontend code. Session/CSRF authority, nonce-bound CSP, safe external links, source/download integrity and backend permissions remain in place. Fourteen new SVGs contain only local geometry and titles; package manifests and lockfiles are unchanged. Browser CSP tests use positive and negative controls. This review does not substitute for live OAuth, merchant payment or database execution.

See `VALIDATION.md` for reproducible commands and the exact local/CI evidence boundaries. The repository-wide pre-existing lint backlog is separate from the enforced changed-file lint gate; no ESLint rule was disabled.
