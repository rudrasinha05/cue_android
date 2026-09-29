# Cue

Cue is an Android reminder assistant that captures commitments, links duplicate sources, schedules reliable local reminders, and helps plan the day. The floating assistant is also called **Cue**.

## Project status

**M0 built; M1 in progress.** The app has a five-tab shell, a persistent System/Light/Dark setting, Cue Default plus five selectable color themes, and a guest-local Room database. Cue has its own Supabase project and an owner-scoped cloud schema. Android Google sign-in and guest migration code need OAuth configuration and on-device verification before M1 can pass. Reminder creation, overlay, AI, import, and integrations are not implemented yet.

## Open in Android Studio

1. Clone `https://github.com/rudrasinha05/cue_android.git` (or use **Get from VCS** in Android Studio).
2. Open the repository root. Use JDK 17 and install Android SDK 35 through SDK Manager.
3. Sync Gradle, then run the `app` configuration on an Android 8.0+ emulator or device.

The repository includes the Gradle wrapper, so no system Gradle installation is needed. Do not commit `local.properties`, signing keys, Supabase secrets, or AI provider keys.

## M1 account setup

See [Cue account setup](docs/M1_ACCOUNT_SETUP.md) for the Google Cloud and Supabase settings needed to enable the sign-in button and verify guest migration. The APK contains only Cue's public Supabase URL and publishable key; it never contains the Google client secret.

## Working agreement

- `main` is the stable integration branch. Work on a short-lived feature branch and merge only after its milestone checks pass.
- Follow [the frozen architecture](docs/ARCHITECTURE.md) and [milestone acceptance criteria](docs/MILESTONES.md). Put new ideas into the later-phase backlog before changing active milestone scope.
- All reminder entry points call the same domain pipeline. The Android app owns local alarm execution; remote AI services never fire reminders.
- Log feature status honestly. A visible screen or mock data does not count as a completed integration.
