# Milestones and acceptance gates

No milestone is called complete until its behavior is demonstrated on device or emulator and its core failure paths are checked. Screens with static sample content do not pass a feature gate.

| Gate | Scope | Acceptance evidence |
|---|---|---|
| M0 | Repository, wrapper, CI, architecture, app identity | Fresh Android Studio import; debug build and launch |
| M1 | Theme, navigation, guest/auth, local and cloud store | Cue Default + five color themes in System/Light/Dark; Google login/logout, guest migration and retained history |
| M2 | Commitment CRUD, local scheduling | Add/edit/complete/snooze; alert fires offline; reboot recovery |
| M3 | Voice and natural-language capture | Tap-to-talk creates/updates via shared pipeline; ambiguity review |
| M4 | Floating Cue | Login gate, in-app activation, overlay grant, drag/snap/edge, opacity, six actions, logout removal |
| M5 | Notification assistant | Independent access toggle; equivalent six shared actions |
| M6 | Deduplication, provenance, and history | Same event from multiple sources appears once with all source links; reminder and global History show creation, edits, delivery, snoozes, completion, and merges, including archived reminders and unavailable source states |
| M7 | PDF/image/DOC/table import | Extraction preview, corrections, multi-event import, duplicate merge |
| M8 | Share/selected text/user-approved capture | No silent cross-app capture; candidate review and source link |
| M9 | Notification intelligence | Opt-in listener, per-app filtering, revocation, suggestion policy |
| M10 | Email integration | OAuth, minimum scope, sync cursor/retry, consent and revoke |
| M11 | Chains, escalation, waiting for reply | Bounded alerts; completion cancels children and follow-ups |
| M12 | Daily planner | Fixed/movable constraints, tomorrow briefing, carry forward without copies |
| M13 | Optional wake word | Explicit opt-in, visible platform-compliant listening, graceful unsupported state |
| M14 | Sync/security/performance and compressed archive | Multi-device conflict tests, RLS review, offline recovery, data export/deletion; old history stored in lossless versioned batches, round-trip/count/checksum verified, searchable metadata indexed, selected ranges restored, failure recovery and user isolation tested |
| M15 | Release QA | Device matrix, accessibility, permissions, Play policy, signed release |

Active milestone: **M1**. M0 debug build and on-device launch passed. Cue's own Supabase project and owner-scoped schema exist. The owner confirmed Google sign-in on device after the nonce fix, and the Android debug CI build passed (`e71c800`). Sign-out isolation, returning sign-in, theme persistence, and guest record migration still need on-device evidence. The app has no commitment creation UI yet, so the guest migration gate cannot be demonstrated through the UI until a focused test path or M2 creation flow exists. M1 remains open.
