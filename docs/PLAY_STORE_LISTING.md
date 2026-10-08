# Cue — Google Play listing and declarations (release candidate)

**Status:** Owner approval and Play Console submission required. This is copy-ready content, not a claim that a Play release has been uploaded.

## Listing

**App name:** Cue — Smart Reminders

**Short description (under 80 characters):** Capture reminders from text, voice and your day. Stay on schedule.

**Full description**

Cue is a flexible reminder companion designed to reduce the steps between noticing something important and remembering it later.

Create and manage reminders with a calendar, different alert sounds, snooze and dismiss controls. Capture a reminder by typing, voice, or by sharing selected text from another app. Import supported documents and photos to extract actionable dates; Cue asks you to review unclear information.

Turn on the optional floating Cue shortcut to access capture tools while using other apps. You control notification and overlay permissions. With explicit Android consent, Cue can analyze text on a shared screen for a potential reminder. Cloud-assisted interpretation is optional and requires sign-in and an internet connection.

Use reminders locally without an account. Sign in with Google if you want optional cloud sync, account recovery and history. Manage your data in Cue's You section, including export and deletion.

Some features depend on Android permissions and whether another app allows content sharing. Critical appointments should be checked after import.

## Permission disclosures to validate in Play Console

- **Exact alarm:** time-specific user-created reminders; fall back to inexact alarms if permission unavailable.
- **Full-screen intent:** visible alarm interface for reminders; notification actions remain a fallback.
- **Notification listener:** optional reminder suggestions from notifications, with explicit settings consent.
- **Display over other apps:** user-enabled floating Cue shortcut; not enabled by default without permission.
- **MediaProjection:** explicit per-session OS capture consent; local screen OCR with stop control; no recorded video saved.
- **Foreground services:** user-requested floating shortcut, screen-capture session and reminder sound playback; stop controls and service types must match behavior.
- **Calendar access:** optional selection and import of calendar events.
- **Google sign-in:** optional account restoration and synchronized records.

## Publisher-completion prerequisites

- Publisher contact email and support website, real privacy policy URL, external account-deletion request URL, approved app name, accurate data safety answers, screenshots and icon.
- Set age targeting, category, region, declarations and content rating in Play Console according to actual behavior.
- Verify upload key fingerprint against Play App Signing.
- Upload the signed `.aab` to an internal test track and validate installation/updates with invited testers. Never release directly to production before the acceptance gates pass.

Official Play policy links:
- https://support.google.com/googleplay/android-developer/answer/11926878
- https://support.google.com/googleplay/android-developer/answer/13327111
- https://support.google.com/googleplay/android-developer/answer/10144311
