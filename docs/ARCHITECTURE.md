# Cue architecture v1.1

This document records the product contracts. Internal packages may change while those contracts stay intact. The work remains grouped into five phases.

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

**User-facing language:** call the item a **reminder** in navigation, forms, alerts, and help text. Commitment remains the internal canonical entity and database concept.

## Android and backend

- Room stores the local execution copy and pending sync operations. DataStore holds preferences. Exact time-sensitive user reminders use AlarmManager with the platform's applicable permission path; WorkManager handles durable maintenance and retry, never exact firing. Restore schedules after reboot and relevant timezone changes.
- Cue's dedicated Supabase project (`gukhuakkguzzhrvtyszx`, BABA GROUPS, `ap-south-1`) provides Auth and PostgreSQL with per-user RLS. Storage and server functions are added when integrations require them. No service-role key, Google client secret, or AI provider key enters the APK.
- Google sign-in is the primary account path through Android Credential Manager and Supabase Auth. Guest mode remains available for local reminders. On sign-in, guest commitments migrate once to the authenticated owner using stable IDs; account history is restored on returning sign-in. Sign-out stops the floating Cue and removes account-bound local access while retaining server history.
- AI is accessed through a provider-neutral server gateway. Extraction produces structured candidates and provenance; deterministic scheduling fires from local data even when the network or AI service fails.
- Local ML Kit OCR and English date entity extraction can suggest a future time on the device. Auto-save requires a single actionable reminder line and a future time. Ambiguous content goes to review (or is ignored in a background screen session). A model download may be required; its failure never blocks manual reminders. Provider-backed interpretation and multi-source semantic deduplication remain separate work.
- Per-source opt-in governs email, calendar, notification access, microphone, overlay, and import permissions. Request permissions when the feature is enabled. Avoid unrestricted SMS or Accessibility scraping. Raw third-party content is retained only as needed under explicit settings.
- Floating Cue accepts a global drag onto its collapsed bubble without opening the app. Plain/HTML text and supported readable image, text, PDF, and DOCX URIs use the shared intake. Source apps must offer cross-app drag and grant URI access; unsupported/restricted drops show a notification and can use Android Share → Cue instead. Successful/duplicate/review outcomes are acknowledged in Cue's capture notification channel.
- Screen analysis starts only from the You button with Android MediaProjection consent on every session. On Android 14+, the user chooses a single app in the system picker; Cue does not claim to enforce an app-only selection when Android offers the whole display. A foreground notification has a Stop action, frames are sampled for on-device OCR, and no video or complete screen dump is retained. Only the accepted reminder line and source metadata enter history. Stopping, system revocation, or sign-out ends the session; this is not an Accessibility service or a permanent per-app background permission.
- Daily schedule is opt-in in You. Wake and bed times bound the draft day; fixed reminders keep their original time, routine anchors avoid fixed events, and no-due active reminders occupy free slots without creating new canonical reminders. Active unfinished items appear again when the next day's plan is rebuilt. A local morning alarm posts a schedule notification if notification access is available. The plan is derived from current reminders and preferences; it does not rewrite reminder due times.
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

### Reminder sounds and floating Cue

The You screen offers the system default and 20 bundled OGG notification sounds. The default sound is stored in DataStore and copied into each new reminder. The alarm receiver uses that reminder’s saved tone; the silent alarm channel lets the playback service ring for ten seconds. Android notification settings may silence the channel.

Floating Cue runs as an opt-in foreground service with a persistent notification. It stores bubble position and appearance and requests a sticky restart after a process kill, then checks saved opt-in and signed-in owner before restoring. Temporary auth and preference loading no longer disables it when the activity is recreated. Touches and drag/drop are ignored while the keyguard is locked. Application overlays sit below critical system windows, so secure lock screens may hide the bubble; a public lock-screen notification offers an unlock-to-open route. Android notification privacy settings control whether that notification is visible.

Capture deduplication first scopes active reminders by owner and exact due time, then compares case, punctuation and whitespace normalized titles. A repeated actionable capture links a new source and `source_added` event to the existing reminder; repeat deliveries of one source key do not add another. Different dates, times or actions remain separate. This deliberately conservative match does not infer semantic equivalence from AI.

A manual follow-up acts on the same canonical reminder. The user may move an active or completed reminder to tomorrow or next week, retaining an active reminder’s prior time of day (9 am for completed or untimed reminders). The transition emits a `follow_up` history event and replaces the old local alarm through the shared scheduler. It preserves one active alarm per reminder; previous alert dates remain reconstructable from history. A future multi-alert chain still needs its own synchronized entity.

### Independent alarm tones and alarm controls

Each commitment owns a `toneId`, set on manual creation or copied from the user's current default when an AI capture saves a reminder. Editing one reminder's tone does not change any other reminder. Room migration 4→5 and the additive cloud `commitments.tone_id` column default existing records to `default`. The shared alarm channel is silent; an exact-alarm-triggered media playback foreground service loops the chosen tone for 10 seconds, or stops sooner on Snooze/Dismiss. The alarm notification provides both actions and a full-screen alarm activity where Android permits it; when full-screen intent access is denied, the high-priority notification remains the supported action surface. Dismiss stops the current alert without completing the reminder; Snooze moves its due time 10 minutes ahead and records history. Editing, completion, deletion and sign-out stop outstanding playback. Android system notification, sound, Do Not Disturb, volume and foreground-service controls can restrict delivery.
