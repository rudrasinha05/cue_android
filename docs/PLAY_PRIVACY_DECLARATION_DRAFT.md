# CUE Google Play data / privacy declaration — DRAFT FOR OWNER REVIEW

**This is an unpublished working draft, not a legal approval or live privacy-policy URL.** Before publication the developer must specify legal/developer identity, contact email, jurisdiction/region, hosting URL, service providers actually used and verified retention/data-deletion terms.

## Product data flows inferred from current source

- Reminders and alarm preferences can live on the device in Android Room/DataStore without sign-in.
- With optional Google sign-in, owner-scoped reminders, source references, history records and compressed history batches sync to the existing Supabase project. CUE profile may include email, display name, gender and optional mobile number.
- The optional cloud date interpreter sends user-supplied reminder text and timezone to a Supabase Edge Function, which may transmit them to Google's Gemini service for interpretation. Verify provider secrets, actual request logging and retention terms before final disclosures.
- The speech recognizer is launched via Android speech-recognition services; provider-side voice handling depends on the user's device/recognizer. CUE should not promise all speech data stays on-device.
- Imported image/document content and permission-granted screen frames are locally processed for text/reminder extraction. Confirm exactly which extracted excerpts, source links and accepted events are persisted or synced. Do not claim that no extracted content is stored.
- Opt-in notification listener can examine permitted notification content to identify reminders; explain its scope, enablement and revocation in the privacy policy.
- Calendar access is optional; verify whether the selected event text is retained in accepted reminders.
- Account deletion and data export exist in app. An **external request resource** for account deletion must also be published and verified. Deleting a CUE account does not delete the user's Google identity.

## Form completion / publication gate

1. Owner verifies factual data processing and third-party processors.
2. Publish a public privacy-policy URL; add a discoverable privacy policy within CUE.
3. Publish a public account-deletion request/help page identifying CUE and a working method to request deletion without using the app; include link in Play Console.
4. Answer Play Console Data safety questions based on actual behavior and geographic deployment. Do not claim no data collection merely because features are optional.
5. Review Play permissions for exact alarms, full-screen intents, overlay, notification listener, calendar and foreground services/media projection. Disclose voluntary opt-in and system-consent limitations.
6. Document retention, contact address and how user requests are verified/fulfilled.

Official: https://support.google.com/googleplay/android-developer/answer/10144311
and https://support.google.com/googleplay/android-developer/answer/13327111
