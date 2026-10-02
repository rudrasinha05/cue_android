# Cue release gate

`develop` is the build source. CI must pass debug compile, unit tests, lint and an unsigned release AAB before a Play candidate is signed. The AAB artifact from CI is **not uploadable** until signed with the private upload key.

## Device acceptance

- Update an existing installation without clearing data. Confirm Room migration, Google return login, account isolation and guest reminder preservation.
- On Android 8, 13, 14 and 15 where available, run [reminder checks](M2_DEVICE_CHECKS.md) and [capture/history checks](M3_CAPTURE_HISTORY_CHECKS.md), including denied/revoked permissions, offline alerts, overlay drag/drop, screen-session stop, 20 sounds, themes and alarm actions.
- On two signed-in devices, sync an older verified archive, restore its exact event count/checksum on the second device, test a conflicting offline edit and export the complete account JSON.
- Review every screen at normal and large font size in light/dark palettes with TalkBack. Compare the final visual design with the approved reference on a phone. Keep the existing five-tab footer layout.

## Play candidate

- Configure the app's upload key outside Git and build a **signed** release AAB. Keep the same key for updates. Increment `versionCode` for each Play upload.
- Complete Play Console declarations for exact alarms, full-screen alarm intents, overlay, foreground services, notification listener and per-session screen projection; verify the final permission copy and store listing/privacy policy.
- Provider-backed Ask AI and opt-in email access require separate service configuration and explicit data-sharing scope. Signed-in cloud deletion requires a reviewed owner-scoped backend path. Do not claim these features are live while blocked.
- Only after device acceptance and a signed bundle, merge the accepted `develop` commit to `main` and start Play internal testing.
