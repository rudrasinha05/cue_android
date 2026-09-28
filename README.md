# Cue

Cue is an Android reminder assistant that captures commitments, links duplicate sources, schedules reliable local reminders, and helps plan the day. The floating assistant is also called **Cue**.

## Project status

**M0 — repository and Android Studio foundation.** This commit establishes the build configuration, app identity, architecture contract, milestone gates, and CI. Reminder, account, overlay, AI, import, and integration features are not implemented yet.

## Open in Android Studio

1. Clone `https://github.com/rudrasinha05/cue_android.git` (or use **Get from VCS** in Android Studio).
2. Open the repository root. Use JDK 17 and install Android SDK 35 through SDK Manager.
3. Sync Gradle, then run the `app` configuration on an Android 8.0+ emulator or device.

The repository includes the Gradle wrapper, so no system Gradle installation is needed. Do not commit `local.properties`, signing keys, Supabase secrets, or AI provider keys.

## Working agreement

- `main` is the stable integration branch. Work on a short-lived feature branch and merge only after its milestone checks pass.
- Follow [the frozen architecture](docs/ARCHITECTURE.md) and [milestone acceptance criteria](docs/MILESTONES.md). Put new ideas into the later-phase backlog before changing active milestone scope.
- All reminder entry points call the same domain pipeline. The Android app owns local alarm execution; remote AI services never fire reminders.
- Log feature status honestly. A visible screen or mock data does not count as a completed integration.
