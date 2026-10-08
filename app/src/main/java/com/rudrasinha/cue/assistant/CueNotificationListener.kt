package com.rudrasinha.cue.assistant

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.rudrasinha.cue.CaptureIntake
import com.rudrasinha.cue.ScreenReminderCandidate
import com.rudrasinha.cue.settings.ThemeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Explicit opt-in. Only a single actionable future deadline is passed to intake. */
class CueNotificationListener : NotificationListenerService() {
    private val work = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recent = LinkedHashSet<String>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName ||
            (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        work.launch {
            if (ThemeStore(applicationContext).notificationIntelligence.first()) {
                val extras = sbn.notification.extras ?: return@launch
                val lines = buildList {
                    extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.let(::add)
                    extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.let(::add)
                    extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.let(::add)
                    extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { add(it.toString()) }
                }.filter { it.length <= 500 }.distinct().joinToString("\n").take(4000)
                val candidate = ScreenReminderCandidate.select(lines) ?: return@launch
                val key = "${sbn.key}|$candidate"
                synchronized(recent) {
                    if (!recent.add(key)) return@launch
                    if (recent.size > 128) recent.remove(recent.first())
                }
                CaptureIntake(applicationContext).accept(candidate, "notification",
                    sbn.packageName)
            }
        }
    }

    override fun onDestroy() {
        work.cancel()
        super.onDestroy()
    }
}
