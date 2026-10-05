# Cue

Cue is an Android reminder assistant that helps you capture what matters, get local alerts, and keep your reminders when you sign in. The floating assistant is also called **Cue**.

## Project status

**Device acceptance and release gates remain open.** Cue has four tabs (Reminders, Upcoming, Inbox, You), persistent System/Light/Dark and six palettes, guest-local Room storage, Google sign-in and owner-scoped Supabase sync. Reminders can be edited, completed, snoozed, deleted and alerted offline; Upcoming has a month calendar. Voice and document capture are available on Reminders and in the opted-in floating bubble. Android Share and selected text use automatic local capture when a single actionable future time is clear; uncertain content offers review from a notification. Floating Cue accepts dropped text and attempts supported files, but external apps may withhold URI access, in which case Share → Cue is the fallback. Local date extraction supports explicit English and a few Hinglish phrases, with ML Kit English entity extraction as a fallback; it is not general-purpose provider AI. Inbox shows source and compressed change history. The You section has an opt-in daily plan, notification deadline detection, and Android 14+ consented shared-screen OCR. No video is saved. Device QA and provider-backed intelligence remain pending. See [the roadmap](docs/MILESTONES.md).

## Open in Android Studio

1. Clone `https://github.com/rudrasinha05/cue_android.git` (or use **Get from VCS** in Android Studio).
2. Switch to `develop`, then open the repository root. Use JDK 17 and install Android SDK 35 through SDK Manager.
3. Sync Gradle, then run the `app` configuration on an Android 8.0+ emulator or device.

The repository includes the Gradle wrapper, so no system Gradle installation is needed. Do not commit `local.properties`, signing keys, Supabase secrets, or AI provider keys.

CI attaches a debug APK, test/lint reports and an unsigned release AAB to each run. See the [release gate](docs/RELEASE_GATE.md) before using an AAB for Play.

## M1 account setup

In You, **Export my Cue data** saves a JSON copy of the current profile, including exact decoded history. Guest users can delete local data; signed-in users can confirm owner-scoped cloud and local Cue data deletion. Verify the signed-in flow with two test accounts before release.

See [Cue account setup](docs/M1_ACCOUNT_SETUP.md) for the Google Cloud and Supabase settings needed to enable the sign-in button and verify guest migration. The APK contains only Cue's public Supabase URL and publishable key; it never contains the Google client secret.

## Working agreement

- `develop` is the single branch for ongoing implementation and Android Studio sync. `main` holds accepted releases. Merge from `develop` to `main` after the relevant acceptance gates; do not create a branch per phase.
- Follow [the frozen architecture](docs/ARCHITECTURE.md) and [phase acceptance criteria](docs/MILESTONES.md). Put new ideas into the later-phase backlog before changing active phase scope.
- All reminder entry points call the same domain pipeline. The Android app owns local alarm execution; remote AI services never fire reminders.
- Log feature status honestly. A visible screen or mock data does not count as a completed integration.

In **You → Settings → Alerts and sounds**, choose System default or one of 20 built-in sounds and 1–5 ring cycles (default 3). Floating Cue stays active as an opted-in foreground service when Cue is backgrounded. On a secure lock screen, use the ongoing notification to unlock and return to Cue; Android can hide overlays behind the keyguard.
