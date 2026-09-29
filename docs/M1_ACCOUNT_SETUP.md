# M1 account setup

Cue's dedicated Supabase project is `gukhuakkguzzhrvtyszx` in BABA GROUPS, region `ap-south-1`. Its project URL and **publishable** key are compiled into the Android app; these are public client identifiers. The server-side secret and Google OAuth client secret must stay in Google Cloud/Supabase, never in the repository or APK.

The [`cue_m1_owner_scoped_store` migration](M1_CLOUD_SCHEMA.md) created `commitments`, `sources`, and `reminder_events`. Each table has owner-scoped RLS and explicit `authenticated` grants; `anon` has no table access. `reminder_events` allows insert and select only. The Android sync uses stable UUIDs for guest records, uploads them idempotently after sign-in, then restores the signed-in user's records; sign-out clears account-bound Room rows. Completed and archived commitments remain available to restore.

## Enable Google sign-in

1. In Google Cloud, configure the Cue app's consent screen with `openid`, email, and profile scopes. Create a **Web application** OAuth client and an **Android** OAuth client for `com.rudrasinha.cue`. Register the debug SHA-1 and later the release SHA-1 for the Android client. Keep the Web client secret outside Git.
2. In the **Cue** Supabase project, enable Auth → Providers → Google. Enter the Web client ID and secret and add the Android client ID as required by the provider's client ID settings. Use the Supabase callback URL `https://gukhuakkguzzhrvtyszx.supabase.co/auth/v1/callback` for the Web client.
3. Put the **Web client ID** (not the secret or Android client ID) in a local Gradle property: `cueGoogleWebClientId=...apps.googleusercontent.com`. The user-level `~/.gradle/gradle.properties` is suitable; do not commit it. Re-sync Android Studio. Until this value is configured, the Google button remains disabled with a setup explanation.
4. On a device with Google Play services, test sign-in, a local guest record migration, sign-out, and signing back into the same account. Verify that no account data is shown in guest mode and that the history reappears after the returning sign-in. Mark M1 complete only when these checks and the debug build pass.

M1 currently lacks a working Google OAuth provider configuration and end-to-end device evidence. The visible account UI and backend schema alone do not pass the milestone.
