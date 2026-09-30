# Cue: five delivery phases

Cue ships in **five phases total**. A phase passes only after its real behavior and failure paths work on a device or emulator. A debug build or a static screen is not acceptance evidence. The architecture remains the product contract; these phases group work without dropping features.

| Phase | Deliverable | Acceptance gate |
|---|---|---|
| P1 — Foundation | Repository/CI, Android identity, design system, six palettes with light/dark/system, navigation, guest Room store, Google login/logout, account-scoped Supabase sync, migration | Fresh import and debug launch; visual review on phone; accessible, finished screens and states; guest data retained on sign-in, returning account restored, sign-out isolated |
| P2 — Reminders | Create/edit/complete/archive/snooze, offline alerts, exact/inexact fallback, reboot/timezone restore, reference-inspired Reminders dashboard and selectable month calendar in Upcoming | End-to-end on-device creation and alert, calendar filtering, airplane-mode firing, no duplicate after snooze or permission change, migration preserves existing data |
| P3 — Capture and controls | Tap-to-talk and natural-language review, import PDF/image/DOC/table, selected text/share, floating Cue and notification panel with the same six actions, permission controls | Each entry point produces reviewed reminders through the shared pipeline; overlay drag/snap/logout, panel actions, offline fallback and revocation work |
| P4 — Intelligence and planning | Source links and deduplication, searchable reminder/global history with lossless compressed archive, notification suggestions, opt-in email integration, reminder chains, daily plan, optional wake word | One event across sources creates one reminder; sources and full history survive sync/compaction; AI uncertainty enters review; chain and plan changes preserve fixed times; unsupported wake word degrades cleanly |
| P5 — Finish and release | Multi-device conflict recovery, RLS/security and data export/deletion, performance, accessibility, permission/Play review, device matrix and signed release | Full regression passes, compressed-history round trip/count/checksum and owner isolation pass, accessible visual QA accepted, signed release builds |

**Current status:** P1 functional checks for login, logout, returning account, guest migration, and theme persistence were accepted on device. The first UI pass was too bare; visual acceptance is open and the design refresh is being done alongside P2. P2 implementation is on the single working branch `develop`; device checks in [M2_DEVICE_CHECKS.md](M2_DEVICE_CHECKS.md) are still required. P3–P5 have not started. No extra phases are planned.

All user-facing screens call the core item a **reminder**. The internal canonical entity and database table remain `Commitment` / `commitments` so existing data and sync are preserved.
