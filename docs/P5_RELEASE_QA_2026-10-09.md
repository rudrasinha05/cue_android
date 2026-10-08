# P5 release acceptance evidence — 2026-10-09

Scope: frozen Cue Android P5 release QA. Do not merge to main before all mandatory gates pass. This document separates **verified evidence**, **new test coverage (awaiting CI)** and **not yet verifiable**.

## Evidence already available

- **Physical phone, Android 16/API 36:** owner-provided run of commit `fa40217`: Gradle build, both APK installs and 2/2 instrumented navigation/Room tests passed. Local report: `qa-results/cue-phone-20261009-023612.txt` (on owner's Windows PC, not uploaded to GitHub).
- **Emulator Android 15/API 35 and Android 16/API 36:** GitHub `37844241024` was green for both targets: https://github.com/rudrasinha05/cue_android/actions/runs/37844241024
- **Debug/unit/lint/unsigned release bundle:** `d9da8ffe` passed GitHub Android quality build, e.g. https://github.com/rudrasinha05/cue_android/actions/runs/37844247911. An **unsigned** AAB is not a Play-ready signed release.
- **Owner functional acceptance:** owner marked floating Cue, alarms/reminders, capture/AI and cloud/history completed. Treat as owner feedback, not evidence of all corner cases on all Android versions.
- **Security schema (read-only live audit):** `commitments`, `sources`, `reminder_events`, `history_batches` have RLS with `user_id = auth.uid()` predicates. Public owner-scoped deletion/prune RPC wrappers are security-invoker and grant EXECUTE to `authenticated`, not `anon`; private security-definer implementations check `auth.uid()`. This static audit **does not execute deletion or prove two-account isolation**.
- **New work on develop:** isolated original v1→current v9 Room data-retention test (opens an automatically deleted scratch DB, never `cue.db`), isolated temporary alarm schedule/cancel test, Compose accessibility semantics smoke and CI UI rerun at larger system font. Their results must be taken from their own newer GitHub run, not inferred from the old green runs.

## October 2026 Play target compatibility

The earlier API 35 target was insufficient for new Android phone apps submitted after 31 August 2026. P5 now upgrades compile/target to API 36 with AGP 8.10.1 and Gradle 8.11.1; **a new build and real-device regression are required** before approval. Old green API 35-target runs do not prove this build works. Minimum Android Studio for API 36 is Meerkat 2024.3.1 Patch 1 (or newer).

## Remaining blocking test evidence

| Gate | Status | Only accepted evidence |
|---|---|---|
| Real in-place upgrade of owner's previously installed Cue app | Not separately verified | In-place install preserves previously existing reminders, history and preferences; backup first; no uninstall |
| Room v1→v9 isolated upgrade | New instrumented test added; CI needed | Both API 35/36 job results, no schema-validation errors |
| Exact alarm denial/recovery, reboot/timezone, airplane mode | Owner says alarm feature tested; edge cases not separately evidenced | Phone observations including one-time delivery after reboot/permission revoke |
| UI accessibility | Large-font automated smoke added, TalkBack human audit open | API 35/36 large-font test plus actual TalkBack/contrast and clipping review |
| Two-account deletion end-to-end | **Blocked: no disposable authenticated accounts in this environment** | Export A and B, cancel A deletion, delete A, verify B unchanged, repeat offline failure; never use real user account |
| Play privacy and external account deletion page | Draft only | Public accessible accurate privacy-policy and account deletion URLs, included in Play Console and app as required |
| Signed AAB | Blocked: owner-held private upload key | AAB signed and locally verified with `jarsigner`; record certificate fingerprint safely, no secrets in repository |
| Play internal track | Blocked: owner Play Console access and signed AAB | Track release accepted; Play-generated test link works and tester install passes |

## No-destructive-test guarantee

- QA instrumentation uses random scratch database files and a temporary alarm which is cancelled in `finally`.
- Local `qa-results/` is ignored by Git; do not upload raw logs containing tokens, user text or identity data.
- Never create a real-auth user, delete account data, revoke real-device permissions, or reboot the user's phone without explicit awareness and access.
- Do not mark the Play release ready solely because a CI build is green.

## Play policy release checklist

Google Play developer policies require a privacy-policy URL and clear data-handling disclosures. Apps offering accounts need both **in-app** account deletion and an **external deletion-request resource**. CUE's account delete control is present in-app, but a published, discoverable external resource and its Console entry have not been evidenced. Exact alarms, full-screen alarm intents and other sensitive permissions need accurate declarations and eligibility review. These are publisher actions, not code tests.

Official policy sources:
- https://support.google.com/googleplay/android-developer/answer/10144311
- https://support.google.com/googleplay/android-developer/answer/13327111
- https://support.google.com/googleplay/android-developer/answer/18258653
- https://support.google.com/googleplay/android-developer/answer/9845334
