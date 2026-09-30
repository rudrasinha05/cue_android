# Cue

Cue is an Android reminder assistant that helps you capture what matters, get local alerts, and keep your reminders when you sign in. The floating assistant is also called **Cue**.

## Project status

**Five delivery phases: P1 foundation functionally accepted, visual review open; P2 reminders in progress.** The app has a five-tab shell, persistent System/Light/Dark and six color themes, a guest-local Room database, Google sign-in and a dedicated owner-scoped Supabase project. A guest reminder can be created and migrated to the signed-in account. P2 adds editing, completion, snooze, and offline alerts. Overlay, AI, import, and integrations follow in P3–P5. See [the roadmap](docs/MILESTONES.md).

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
