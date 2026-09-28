# Milestones and acceptance gates

No milestone is called complete until its behavior is demonstrated on device or emulator and its core failure paths are checked. Screens with static sample content do not pass a feature gate.

| Gate | Scope | Acceptance evidence |
|---|---|---|
| M0 | Repository, wrapper, CI, architecture, app identity | Fresh Android Studio import; debug build and launch |
| M1 | Theme, navigation, guest/auth, local and cloud store | System/Light/Dark; guest flow; login/logout and retained history |
| M2 | Commitment CRUD, local scheduling | Add/edit/complete/snooze; alert fires offline; reboot recovery |
| M3 | Voice and natural-language capture | Tap-to-talk creates/updates via shared pipeline; ambiguity review |
| M4 | Floating Cue | Login gate, in-app activation, overlay grant, drag/snap/edge, opacity, six actions, logout removal |
| M5 | Notification assistant | Independent access toggle; equivalent six shared actions |
| M6 | Deduplication and provenance | Same event from multiple sources appears once with all source links |
| M7 | PDF/image/DOC/table import | Extraction preview, corrections, multi-event import, duplicate merge |
| M8 | Share/selected text/user-approved capture | No silent cross-app capture; candidate review and source link |
| M9 | Notification intelligence | Opt-in listener, per-app filtering, revocation, suggestion policy |
| M10 | Email integration | OAuth, minimum scope, sync cursor/retry, consent and revoke |
| M11 | Chains, escalation, waiting for reply | Bounded alerts; completion cancels children and follow-ups |
| M12 | Daily planner | Fixed/movable constraints, tomorrow briefing, carry forward without copies |
| M13 | Optional wake word | Explicit opt-in, visible platform-compliant listening, graceful unsupported state |
| M14 | Sync/security/performance | Multi-device conflict tests, RLS review, offline recovery, data export/deletion |
| M15 | Release QA | Device matrix, accessibility, permissions, Play policy, signed release |

Active milestone: **M1**. The M0 debug build passed in GitHub Actions; an on-device launch check remains. M1's local shell and storage are in progress. Its account and cloud acceptance checks remain blocked until a Cue-specific Supabase project is chosen and connected.
