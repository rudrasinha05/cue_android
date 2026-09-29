# Cue architecture v1.1

This document freezes the core behavior agreed before implementation. Internal packages can change when a milestone requires it, provided these contracts remain intact. New features go to the backlog for a later phase.

## Product and identity

- Android app, floating control, and notification assistant are all named **Cue**.
- App package: `com.rudrasinha.cue`. Native Kotlin and Jetpack Compose UI. Appearance follows System, Light, or Dark. Independently, the user selects Cue Default or one of five color themes: Ocean, Forest, Sunset, Rose, Midnight. Every color theme supports all three appearance modes; preferences persist on the device.
- Guest users can make local reminders. Sign-in is mandatory to enable the floating Cue. Activation is available only from the original app and requires explicit Android overlay permission.
- The user chooses floating Cue, notification panel assistant, both, or neither. Turning either surface off never deletes reminders.

## Boundaries

```text
Share / text / voice / email / notification / document / calendar
    -> normalize + extract + confidence
    -> duplicate decision (create / merge / update / suggest / ignore)
    -> unified commitment + source links
    -> reminder chain + local Android scheduler + daily planner
    -> app / floating Cue / notification panel
```

All entry points invoke one domain pipeline and one quick-action system. A commitment is the canonical task, event, deadline, follow-up, or routine; multiple source records may point to it. A reminder chain belongs to one commitment. Completion and cancellation atomically stop outstanding alerts. Deduplication decisions are transactional on the backend and idempotent on-device.

## Android and backend

- Room stores the local execution copy and pending sync operations. DataStore holds preferences. Exact time-sensitive user reminders use AlarmManager with the platform's applicable permission path; WorkManager handles durable maintenance and retry, never exact firing. Restore schedules after reboot and relevant timezone changes.
- Supabase Auth, PostgreSQL with per-user RLS, Storage, and server functions provide account sync and integrations. No service-role key or AI provider key enters the APK. The repository does not assume an existing Supabase project; setup is a later milestone.
- Google sign-in is the primary account path through Android Credential Manager and Supabase Auth. Guest mode remains available for local reminders. On sign-in, guest commitments migrate once to the authenticated owner using stable IDs; account history is restored on returning sign-in. Sign-out stops the floating Cue and removes account-bound local access while retaining server history.
- AI is accessed through a provider-neutral server gateway. Extraction produces structured candidates and provenance; deterministic scheduling fires from local data even when the network or AI service fails.
- Per-source opt-in governs email, calendar, notification access, microphone, overlay, and import permissions. Request permissions when the feature is enabled. Avoid unrestricted SMS or Accessibility scraping. Raw third-party content is retained only as needed under explicit settings.
- A foreground or user-visible path must be used for any supported wake-word mode; tap-to-talk is the guaranteed voice path.

## Shared six actions

Voice Reminder, Quick Reminder, Import/Scan, Ask AI, My Day, Assistant Settings. Floating Cue displays them in an inward-expanding radial menu based on screen position. It can drag, snap, partly hide at an edge, and has user-adjustable collapsed opacity; expanded actions stay readable. Notification mode exposes the same actions through platform-appropriate notification buttons and a tap-through action sheet.

## Data contract

The canonical entities are `Commitment`, `Occurrence`, `ReminderChain`, `Alert`, `Source`, `ReminderEvent`, `HistoryBatch`, `Candidate`, `DuplicateDecision`, `DailyPlan`, `PlanBlock`, `IntegrationConnection`, `Device`, and `UserPreference`. Every entity is scoped to a user or guest-local profile. Each source has a stable origin ID/hash for ingestion idempotency. Dates store instants plus original timezone and user intent. Ambiguous dates remain candidates for confirmation.

## Reminder history and source references

- Every commitment exposes a chronological **History** view, including completed, cancelled, and archived reminders. A global History screen can search and filter by date, status, and source. Each timeline item shows what changed, when, and how; a **Sources** section links to the original input when the user still has access. Returning account users can retrieve their synced history.
- A `Source` belongs to the user's profile and links to one canonical commitment; several sources can link to the same commitment after deduplication. Store source type (manual, voice, share, document, email, calendar, notification, etc.), a stable external ID or content fingerprint, captured time, short user-readable excerpt/title, and an optional authorized URI or storage reference. Keep the source's original identifier through a merge, and record the merge in history. If a source is deleted or access is revoked, show its retained metadata and an unavailable state rather than a broken link. Raw source content follows the user's permission and retention settings.
- `ReminderEvent` is append-only and scoped to the user and commitment. Record creation, edits, import/merge, schedule changes, alert delivery, snooze, completion, cancellation, and sync conflict resolution with an event ID, occurred-at time, event type, minimal before/after change, actor or entry point, linked source IDs, and idempotency key. Retries must not duplicate events. Do not copy full email/document bodies or secrets into the event stream. History is an audit trail for future reference; the current `Commitment` and `ReminderChain` remain the source of truth for scheduling.
- **History must be compressed.** Keep recent events directly queryable. Compact older events into immutable `HistoryBatch` records per user and bounded time/commitment range using a versioned, lossless codec (initially GZIP over canonical serialized events). Each batch has its owner, commitment ID, first/last event time, event count, schema/codec versions, and checksum. Index this small metadata plus searchable titles/status/source pointers; do not scan or decompress every batch to show the history list. Decompress only selected ranges or reminder details. Compression must preserve exact events, order, timestamps, and source IDs; an AI summary may be shown as a convenience but never replace the recoverable record.
- Compaction is idempotent: write the compressed batch, verify count/checksum and round-trip decoding, then atomically mark the original range compacted and remove its uncompressed copies. A failed job leaves the original events available. Sync batches and metadata under per-user access rules; Room caches current/recent history for offline use, while older batches are fetched on demand and may be cached locally. Export reconstructs the full history; account/data deletion removes events, batches, indexes, and source references. Sign-out clears account-bound local caches without deleting the server archive.

## Product invariants

1. One commitment can have multiple sources; no duplicate user-facing reminders for the same event.
2. Manual reminders and already scheduled alerts work offline.
3. Fixed events remain fixed in planning; movable tasks may be rescheduled without duplication.
4. AI confidence gates automation and uncertain imports enter review.
5. Sign-out removes the floating control and account-bound access without deleting synced history.
6. Permission revocation degrades the relevant feature and leaves existing local reminders intact.
7. UI surfaces never write alarm/database state independently of the shared domain use cases.
8. Every reminder retains a traceable source and reconstructable change history. Compaction cannot silently lose events or change the active reminder schedule.
