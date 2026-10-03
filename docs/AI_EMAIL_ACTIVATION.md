# Cue AI and email activation contract

These integrations remain off until the owner approves the exact data flow and configures credentials. The Android app currently uses local extraction and explicit Share → Cue for email text; neither requires a mailbox permission or sends reminder text to Gemini.

## Ask AI

- Destination: Google Gemini API through a Cue Supabase Edge Function. Keep the Gemini API key in server-side secrets, never in the APK or Git.
- Proposed per-request payload: the question the user types, and only the reminder titles and due times the user explicitly selects for that question. No full Inbox history, raw emails, screen frames, document bodies, account email address, or background capture data.
- Show the destination and selected fields before the first request, with a Cancel action; an empty reminder selection sends only the typed question. Do not send a request if consent is declined.
- Return a draft answer or reminder suggestion for review. The provider must never create, change or delete a reminder directly. Rate limit and bound the prompt/response sizes; errors fall back to local manual entry.
- Activation requires a server-side `GEMINI_API_KEY`, a working authenticated Edge Function, data-sharing approval for this payload, and a device test of consent, refusal, offline and error states.

## Email

- Current explicit path: the user shares selected email text from their mail app to Cue; Cue records a source excerpt and asks for review. This has no ongoing mailbox access.
- Proposed automatic path: a separate Google OAuth grant for Gmail read-only access, enabled only from You. The app must explain that the grant can read mailbox content, filter for possible future deadlines, and let the user revoke access. A matching email should save a short source excerpt and message ID; no full body in reminder history.
- Activation requires OAuth consent-screen configuration and any Google verification applicable to the Gmail restricted scope, token management, a revoke/disconnect flow, and end-to-end tests on a test mailbox. Google Sign-In's existing ID token is not a Gmail access token.

No provider or Gmail access should be presented as live before these gates pass.
