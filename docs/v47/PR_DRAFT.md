# Start V4.7 isolated lab control plane and workbench

The marketplace now has the initial separate lab platform: signed source/image manifests, private quota reservations, fenced start/stop/operation commands, bounded replay, a workbench and MFA-protected operator pause controls. Uncertain provider responses retain the original request identity and resource reservations; source revocation and isolation loss close routing. Source/setup remains free when execution is unavailable.

Runtime admission is disabled by default, and this branch installs no executable manifests or hosted workers. V4.7 remains in progress: the separate restricted build/provider service, real KVM/network/cleanup/restore measurements, Redis licence review, dated budget approval and soak remain required before activation. See IMPLEMENTATION.md and ADMISSION_GATES.md. V4.8 educational labs are outside this change.

Validation: production build and TypeScript pass; 177 Node tests and 17 Java protocol/export/architecture tests pass; security source contract with 273 routes passes. Fourteen real-PostgreSQL lab regressions and production-role permission checks are written and compile, but no native disposable database is available locally. Twenty-one browser cases are discovered/type-checked but have not executed because Chromium is missing and official downloads are truncated. CI/SAST/container checks on the exact published tree are required. No existing SAST finding scope, expiry or required regression was relaxed.

Migration: apply V31, then rerun the reviewed database permission provisioning before serving with the runtime role. Keep APP_LABS_ENABLED=false. No production merge or activation is requested by this draft.
