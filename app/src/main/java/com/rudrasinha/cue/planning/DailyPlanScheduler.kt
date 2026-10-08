package com.rudrasinha.cue.planning

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.rudrasinha.cue.R
import com.rudrasinha.cue.assistant.AssistantControls
import com.rudrasinha.cue.assistant.CueAction
import com.rudrasinha.cue.data.CueDatabase
import com.rudrasinha.cue.reminders.ReminderReceiver
import com.rudrasinha.cue.reminders.ReminderScheduler
import com.rudrasinha.cue.settings.ThemeStore
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first

class DailyPlanScheduler(private val context: Context) {
    companion object { const val ACTION = "com.rudrasinha.cue.DAY_PLAN" }
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private fun pending() = PendingIntent.getBroadcast(context, 2901,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    suspend fun sync() {
        alarms.cancel(pending())
        val settings = ThemeStore(context)
        if (!settings.dailyPlanEnabled.first()) return
        val wake = settings.wakeMinute.first()
        val zone = ZoneId.systemDefault()
        val now = java.time.ZonedDateTime.now(zone)
        var next = LocalDate.now(zone).atTime(LocalTime.of(wake / 60, wake % 60)).atZone(zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending())
    }

    suspend fun onAlarm() {
        val settings = ThemeStore(context)
        if (settings.dailyPlanEnabled.first()) notifyToday()
        sync()
    }

    suspend fun notifyToday() {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return
        if (!notifications.areNotificationsEnabled()) return
        notifications.createNotificationChannel(NotificationChannel("cue_day_plan",
            "Cue daily schedule", NotificationManager.IMPORTANCE_DEFAULT))
        if (notifications.getNotificationChannel("cue_day_plan")?.importance ==
            NotificationManager.IMPORTANCE_NONE) return
        val owner = ReminderScheduler(context).activeOwnerId() ?: "guest"
        val items = CueDatabase.get(context).commitments().observeActive(owner).first()
        val settings = ThemeStore(context)
        val plan = buildDailyPlan(items, LocalDate.now(), settings.wakeMinute.first(),
            settings.bedMinute.first())
        val fixed = plan.count { it.kind == DayBlock.Kind.REMINDER }
        val flexible = plan.count { it.kind == DayBlock.Kind.FLEXIBLE }
        val notification = Notification.Builder(context, "cue_day_plan")
            .setSmallIcon(R.drawable.ic_cue_foreground)
            .setContentTitle("Your day is ready")
            .setContentText(if (fixed + flexible == 0) "A clear day. Your schedule is open."
                else "$fixed timed reminders · $flexible flexible tasks. Tap to see your day.")
            .setContentIntent(PendingIntent.getActivity(context, 2902,
                AssistantControls.shortcutIntent(context, CueAction.DAY.key),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true).build()
        try { notifications.notify(2903, notification) }
        catch (_: SecurityException) { /* Permission can change after the check. */ }
    }
}
