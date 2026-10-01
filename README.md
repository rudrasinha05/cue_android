# Cue

Cue is an Android reminder assistant that helps you capture what matters, get local alerts, and keep your reminders when you sign in. The floating assistant is also called **Cue**.

## Project status

**Five delivery phases: P1 foundation functionally accepted, visual review open; P2 reminders in device QA; P3/P4 implementation ongoing.** The app has a five-tab shell, persistent System/Light/Dark and six app-wide palettes, guest-local Room storage, Google sign-in and owner-scoped Supabase sync. Reminders can be edited, completed, snoozed, deleted after confirmation and alerted offline; Upcoming has a month calendar. The AI tab offers voice transcription, quick capture, text/table/DOCX import and Latin-script OCR for PDF/images. Android Share and selected text open the review sheet. Floating Cue accepts cross-app text and supported files dropped directly onto its bubble; clear actionable future times can save a reminder and notify the result, while uncertain content opens review from the notification. Local ML date extraction is English only and may download a model; there is no provider-backed Ask AI answer yet. Inbox shows source and change history; older synced local events are losslessly compressed. You can opt into a daily schedule notification and start/stop a one-app screen text analysis session on Android 14+ through the system's capture picker. The system picker may also offer the whole display. No video is saved. Device QA, provider-backed intelligence and release checks remain pending. See [the roadmap](docs/MILESTONES.md).

## Open in Android Studio

1. Clone `https://github.com/rudrasinha05/cue_android.git` (or use **Get from VCS** in Android Studio).
2. Switch to `develop`, then open the repository root. Use JDK 17 and install Android SDK 35 through SDK Manager.
3. Sync Gradle, then run the `app` configuration on an Android 8.0+ emulator or device.

The repository includes the Gradle wrapper, so no system Gradle installation is needed. Do not commit `local.properties`, signing keys, Supabase secrets, or AI provider keys.

## M1 account setup

See [Cue account setup](docs/M1_ACCOUNT_SETUP.md) for the Google Cloud and Supabase settings needed to enable the sign-in button and verify guest migration. The APK contains only Cue's public Supabase URL and publishable key; it never contains the Google client secret.

## Working agreement

- `develop` is the single branch for ongoing implementation and Android Studio sync. `main` holds accepted releases. Merge from `develop` to `main` after the relevant acceptance gates; do not create a branch per phase.
- Follow [the frozen architecture](docs/ARCHITECTURE.md) and [phase acceptance criteria](docs/MILESTONES.md). Put new ideas into the later-phase backlog before changing active phase scope.
- All reminder entry points call the same domain pipeline. The Android app owns local alarm execution; remote AI services never fire reminders.
- Log feature status honestly. A visible screen or mock data does not count as a completed integration.

In **You → Reminder sound**, choose System default or one of 20 built-in sounds. Floating Cue stays active as an opted-in foreground service when Cue is backgrounded. On a secure lock screen, use the ongoing notification to unlock and return to Cue; Android can hide overlays behind the keyguard.
