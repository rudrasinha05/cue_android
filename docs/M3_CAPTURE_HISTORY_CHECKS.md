# Cue capture and history device checks

The CI gate assembles a debug APK and runs the compressed-history round-trip and corruption tests. It does not replace these phone checks. Run on `develop` without clearing app data so Room v2 → v4 migration is exercised.

1. Open an existing install. Confirm its reminders and alerts still exist, then create, edit, snooze, complete and archive reminders. Inbox should show each change, title, time and original source.
2. From another app, **Share** text to Cue; select text and use **Cue** in the selection menu. Each should open the same compact review sheet. Cancel without saving, then retry and save. Source text should be visible in Inbox after saving.
3. In AI, use **Talk to Cue** and inspect the transcription before saving. Test a phone without a speech recognizer or with no network: the app should show a useful message and manual capture should still work.
4. Import a TXT, CSV/TSV, Markdown and DOCX document. Inspect the extracted text, cancel one import, and save another. Revoking document permission should not erase saved history.
5. In airplane mode, save/edit reminders, then reconnect and sign in. On another signed-in device, verify the reminder, source and event timeline. Sign out and confirm another account cannot see them.
6. With at least 20 synced events older than 30 days, trigger a sync. Confirm Inbox still shows every event in order; kill/restart Cue and repeat. The GZIP payload must pass event count and SHA-256 checks. Cloud originals are retained until server-side compaction is implemented.

Pending capture work: PDF/image OCR, floating overlay, notification panel and the shared six actions. Pending history work: cloud archive compaction, source deduplication, import metadata/permissions and account deletion/export flows.
