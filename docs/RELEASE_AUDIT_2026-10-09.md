# Cue release audit — 2026-10-09

This is a checkpoint for the frozen five-phase Cue Android project, not a release approval. Work continues only on `develop`; `main` is the release branch. Do not merge until the existing gates pass.

## Verified from source / live project

- Earlier commit `93a491a` passed the GitHub Android workflow (debug APK, JUnit, lint and unsigned AAB).
- Code fixes on `develop`: clipboard and URI fallback (`4d3851f`), bounded one-shot screen scan (`78074e2`), time-wording-tolerant same-due reminder matching (`d3ccef4`) and unit tests (`196f3f6`).
- Cue Supabase project `gukhuakkguzzhrvtyszx` is ACTIVE_HEALTHY; functions `cue-analyze` (v5, JWT verified) and `cue-delete-account` (v1, JWT verified) are active. **Deployed does not imply Gemini API success.**
- Public `commitments`, `sources`, `reminder_events` and `history_batches` have RLS with owner-specific SELECT/INSERT/UPDATE predicates. Private analysis quota intentionally has no client policies. Private owner-scoped delete/prune RPCs derive `auth.uid()`.
- Security advisor notes: private quota has RLS without policies (expected for a non-client-readable private table); leaked-password protection disabled (review with the account administrator if password sign-in is later enabled).

## Remaining mandatory acceptance checks

- **Device alarms:** fresh install and in-place Room upgrade, offline exact/inexact firing, reboot/timezone reschedule, 20 sound previews, independent tones, 1–5 repeat cycles, snooze/dismiss, and no duplicate alerts.
- **Capture:** Android 8/13/14/15 as available; long-press clipboard text/image/document; global drag/drop from applications that grant URI access; Share → Cue fallback; notification-channel denial and permissions revocation.
- **Screen capture:** Android 14+ OS consent for each session, one-shot bounded lifetime, continuous screen session, Stop action, rotation, lock screen, OCR accuracy and clean tear-down. The Android system picker cannot be suppressed.
- **Cloud QA:** opt-in Gemini line processing with test account, rate limit and provider failure; second-account isolation, two-device archived history restore/count/checksum, edit conflicts, data export, account deletion and retry after network failure. Never exercise destructive tests on real user data.
- **Gmail automation:** currently **not enabled**. A verified OAuth Gmail grant (restricted-scope review where applicable), revocation and real mailbox QA are external prerequisites. Manual Gmail Paste and Share → Cue are available now.
- **Release:** accessibility/TalkBack/font-size/theme review, memory/battery/performance checks, Play policy/privacy declarations, developer contact and public privacy-policy URL, signing key held off Git, signed AAB, and internal test track acceptance.

## Evidence gate

GitHub CI after the above fixes must be green. The last verified pre-fix CI run is not evidence that the new commits compile. Android Studio/device tests and account-driven cloud operations cannot be inferred from source. Avoid claiming '100% done' until the checks have actual results; retain `main` unchanged.

## Explicitly out of active scope

Monetization and the previously discussed cute character reminder animations remain deferred and must not be silently implemented.
