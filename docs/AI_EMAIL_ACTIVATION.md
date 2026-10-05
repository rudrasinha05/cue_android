# Cue AI and email activation contract

These integrations remain off until the owner approves the exact data flow and configures credentials. The Android app currently uses local extraction and explicit Share → Cue for email text; neither requires a mailbox permission or sends reminder text to Gemini.

## Optional cloud deadline analysis (draft, not deployed)

- Proposed destination: Google Gemini API through Cue's Supabase Edge Function `cue-analyze`. The draft source is in `supabase/functions/cue-analyze/index.ts`; it is **not deployed or called by Android**.
- Proposed per-request payload: **one possible reminder line, at most 300 characters**, the device timezone, and server time. This could be a short excerpt of on-screen OCR, selected/shared text, voice transcription, or imported content. No complete frame, image, full document, Inbox history, account email address, or mailbox content is sent. Even one line can contain private information.
- The signed-in user must explicitly opt in under You before any cloud request. Default stays local-only. The UI must name Supabase and Google Gemini, show the exact scope, provide a way to turn it off, and avoid cloud requests when off or signed out.
- The server verifies the user token against Supabase Auth, bounds input/output, and returns only a proposed future due time. The Android confidence gate must validate it before creating a reminder. Neither the function nor Gemini writes reminders or their history.
- A `GEMINI_API_KEY` belongs only in the function's server-side secret store. No client APK, Git commit or chat message should contain it. A per-instance request cap in the draft is **not** a global abuse limit; add a durable per-owner rate limit and verify authentication, refusal, timeout, malformed output and offline fallbacks before activation.
- Automatic approval review rejected deployment because sending private reminder text to Gemini and using custom token verification need explicit approval for this destination and data scope. Do not deploy or wire the Android client before that approval. There are currently no Edge Functions in the Cue project.

## Email

- Current explicit path: the user shares selected email text from their mail app to Cue; Cue records a source excerpt and asks for review. This has no ongoing mailbox access.
- Proposed automatic path: a separate Google OAuth grant for Gmail read-only access, enabled only from You. The app must explain that the grant can read mailbox content, filter for possible future deadlines, and let the user revoke access. A matching email should save a short source excerpt and message ID; no full body in reminder history.
- Activation requires OAuth consent-screen configuration and any Google verification applicable to the Gmail restricted scope, token management, a revoke/disconnect flow, and end-to-end tests on a test mailbox. Google Sign-In's existing ID token is not a Gmail access token.

No provider or Gmail access should be presented as live before these gates pass.
