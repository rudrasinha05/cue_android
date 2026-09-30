# M2 device checks

M2 is in progress on `feature/m2-commitments-alarms`. Do not merge this draft until these checks pass on an Android device or emulator. Update the app in place so the Room v1→v2 migration can be checked with an existing M1 guest commitment.

1. Open **Reminders** and confirm the previous M1 item remains. Tap **New reminder** and add a title, optional note, and an alert 3–5 minutes ahead. Allow notifications on Android 13+ and tap **Allow precise timing** if Android shows that option. The new item appears in Reminders and Upcoming.
2. Turn on airplane mode before the due time. The notification must appear with the correct title. Turn airplane mode off. Edit the title and time of another item, then use **More → Remind in 10 minutes**; only the latest scheduled alert should fire.
3. Create an item due in more than 10 minutes, then reboot the device. Confirm the future alert still fires. Repeat after changing the system time or timezone if available.
4. Tap **Done** on an item with a future alert and verify no alert fires. **More → Archive reminder** should remove the item from the active list; completed items appear under **Completed** on Reminders.
5. Sign in, edit a reminder while offline, then return online and sign out. Sign back into the same account: the edit must remain and sync, without showing the other account's records in guest mode. Check the edit on a second device if available.
6. Deny the notification permission once and verify Cue tells you alerts cannot be displayed. With exact-alarm access unavailable, the app keeps an inexact alarm and explains the possible delay. For an exact-alarm permission change, schedule an alert with access granted, revoke access before it fires, and wait for the backup alert. When access stays granted, confirm the backup does not post a duplicate after the exact alert.

The current flow has one alert per reminder. Internally, the canonical record remains a Commitment; the app calls it a reminder. History/source timeline, compression, and multi-device conflict resolution follow the frozen later milestones. A successful debug build alone does not pass M2.
