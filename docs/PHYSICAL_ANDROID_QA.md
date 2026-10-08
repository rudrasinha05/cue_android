# Cue physical Android QA

This is **P5 phone acceptance**, not a claim that an emulator run proves phone behavior.
The automated smoke tests launch the app and test an **in-memory** database. They do not delete your existing Cue reminders or accounts.

## Run on a USB-connected phone

1. Open the latest `develop` branch on your PC in Android Studio (JDK 17, Android SDK 35).
2. Connect an unlocked Android phone, enable USB debugging, and approve the computer.
3. Open PowerShell at the repository root and run:
   `powershell -ExecutionPolicy Bypass -File .\tools\cue-phone-smoke.ps1`
   For multiple devices pass `-Serial DEVICE_ID`.
4. Read `qa-results/cue-phone-*.txt` for the instrumented test result. The script uses `adb install -r` and **never uninstalls or clears app data**. If an installed Cue APK has an incompatible signature, stop instead of uninstalling it.

## Real-device tests still requiring observation

- [ ] Existing data survives an in-place upgrade.
- [ ] Create an offline alarm due in 3–5 minutes; check sound, snooze, dismiss and duplicate prevention.
- [ ] Reboot and check future alarms restore; revoke and re-enable exact-alarm access.
- [ ] Preview 20 sounds, per-reminder tones and repeat counts (1, 3, 5).
- [ ] Enable signed-in floating bubble; test drag, edge snap, bottom close, size, opacity and sign-out.
- [ ] Hold bubble to Paste text/image/file; cross-app drag where supported; Share → Cue fallback.
- [ ] On Android 14+, test consented screen scan, empty-screen timeout, stop, orientation and permission denial.
- [ ] Revoke notification and overlay permissions; confirm no crash and accurate indicators.
- [ ] Test two-device sync and archive verification on disposable accounts; never delete production data.
- [ ] Check all themes, large text and TalkBack.

For detailed expected behavior read `docs/M2_DEVICE_CHECKS.md` and `docs/M3_CAPTURE_HISTORY_CHECKS.md`.
Phone testing cannot be performed remotely through GitHub; this script runs on the actual PC connected to the phone.
