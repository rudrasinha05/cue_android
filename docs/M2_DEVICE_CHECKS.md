# M2 device checks

M2 is in progress on `feature/m2-commitments-alarms`. Do not merge this draft until these checks pass on an Android device or emulator. Update the app in place so the Room v1→v2 migration can be checked with an existing M1 guest commitment.

1. Open **Today** and confirm the previous M1 commitment remains. Add a new commitment with a title, details, and a time 3–5 minutes ahead. Allow notifications on Android 13+ and allow exact alarms through **Allow exact reminder timing** if Android shows that option. The new item appears on Today and Upcoming.
2. Turn on airplane mode before the due time. The notification must appear with the correct title. Turn airplane mode off. Edit the title and time of another item, then use **+10 min**; only the latest scheduled alert should fire.
3. Create an item due in more than 10 minutes, then reboot the device. Confirm the future alert still fires. Repeat after changing the system time or timezone if available.
4. Tap **Done** on an item with a future alert and verify no alert fires. **Archive** should remove the item from the active list; completed items appear in the Completed section on Today.
5. Sign in, edit a commitment while offline, then return online and sign out. Sign back into the same account: the edit must remain and sync, without showing the other account's records in guest mode. Check the edit on a second device if available.
6. Deny the notification permission once and verify Cue tells you alerts cannot be displayed. With exact-alarm access unavailable, the app keeps an inexact alarm and explains the possible delay.

The current flow has one alert per commitment. History/source timeline, compression, and multi-device conflict resolution follow the frozen later milestones. A successful debug build alone does not pass M2.
