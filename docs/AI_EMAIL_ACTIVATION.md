# Cue AI and email activation contract

Cloud analysis has an explicit account-scoped opt-in and a deployed authenticated function. Gemini responses require a configured server-side key and real provider tests before release. Email access remains off; users can explicitly Share → Cue from an email app without mailbox permissions.

## Optional cloud deadline analysis

- Destination: Google Gemini API through Cue's Supabase Edge Function `cue-analyze`. Function version 1 is deployed with platform JWT verification enabled. Android calls it only after the signed-in owner explicitly enables **You → Settings → Improve reminder detection** and local extraction did not confidently find a time. The client sends one actionable line (10–300 characters) with date and clock time plus the timezone, never the full source.
- Proposed per-request payload: **one possible reminder line, at most 300 characters**, the device timezone, and server time. This could be a short excerpt of on-screen OCR, selected/shared text, voice transcription, or imported content. No complete frame, image, full document, Inbox history, account email address, or mailbox content is sent. Even one line can contain private information.
- The signed-in user must explicitly opt in under You before any cloud request. Default stays local-only. The UI must name Supabase and Google Gemini, show the exact scope, provide a way to turn it off, and avoid cloud requests when off or signed out.
- The server verifies the user token against Supabase Auth, bounds input/output, and returns only a proposed future due time. The Android confidence gate must validate it before creating a reminder. Neither the function nor Gemini writes reminders or their history.
- `GEMINI_API_KEY` is stored in Supabase Edge Function secrets, never the APK or Git. The function requires platform JWT verification, checks the caller's identity again, and atomically consumes a per-owner database quota (10 per minute, 200 per UTC day) before calling Gemini. If the provider or quota service is unavailable, the Android app falls back to local extraction. Provider/device QA still remains before release.
- **Live audit (2026-10-09):** The Cue project reports `cue-analyze` ACTIVE (version 5) with platform JWT verification enabled; `cue-delete-account` is also ACTIVE. The Android opt-in client and function source exist. Presence of `GEMINI_API_KEY`, successful provider results and phone QA were **not** verified. The feature must still pass privacy, consent, quota, data-handling and Play review before publication.

## Email

- Current explicit path: the user shares selected email text from their mail app to Cue; Cue records a source excerpt and asks for review. This has no ongoing mailbox access.
- Proposed automatic path: a separate Google OAuth grant for Gmail read-only access, enabled only from You. The app must explain that the grant can read mailbox content, filter for possible future deadlines, and let the user revoke access. A matching email should save a short source excerpt and message ID; no full body in reminder history.
- Activation requires OAuth consent-screen configuration and any Google verification applicable to the Gmail restricted scope, token management, a revoke/disconnect flow, and end-to-end tests on a test mailbox. Google Sign-In's existing ID token is not a Gmail access token.

No provider or Gmail access should be presented as live before these gates pass.
