package com.rudrasinha.cue.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.rudrasinha.cue.MainActivity
import com.rudrasinha.cue.R
import com.rudrasinha.cue.data.CommitmentEntity
import com.rudrasinha.cue.data.CueDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderScheduler(private val context: Context) {
    companion object {
        private const val PRIMARY = "com.rudrasinha.cue.REMIND"
        private const val BACKUP = "com.rudrasinha.cue.REMIND_BACKUP"
    }
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val preferences = context.getSharedPreferences("cue_active_owner", Context.MODE_PRIVATE)

    fun activeOwnerId(): String? = preferences.getString("owner", null)

    fun setActiveOwner(id: String?) {
        preferences.edit().putString("owner", id).apply()
    }

    fun exactAvailable(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    fun cancel(item: CommitmentEntity) {
        alarms.cancel(pending(item.id, item.dueAtMillis ?: 0L, PRIMARY))
        alarms.cancel(pending(item.id, item.dueAtMillis ?: 0L, BACKUP))
    }

    fun schedule(item: CommitmentEntity) {
        cancel(item)
        val due = item.dueAtMillis ?: return
        if (item.status != "active" || due <= System.currentTimeMillis()) return
        val operation = pending(item.id, due, PRIMARY)
        if (exactAvailable()) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, operation)
                // This survives a later revocation of exact-alarm access.
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due + 5 * 60_000L,
                    pending(item.id, due, BACKUP))
                return
            } catch (_: SecurityException) { /* Special access may have changed. */ }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pending(item.id, due, BACKUP))
    }

    suspend fun cancelOwner(id: String) {
        CueDatabase.get(context).commitments().ownerAlarms(id).forEach(::cancel)
    }

    suspend fun restore() {
        val dao = CueDatabase.get(context).commitments()
        val now = System.currentTimeMillis()
        dao.futureAlarms("guest", now).forEach(::schedule)
        activeOwnerId()?.let { owner -> dao.futureAlarms(owner, now).forEach(::schedule) }
    }

    fun wasDelivered(item: CommitmentEntity): Boolean =
        preferences.getLong("delivered_${item.id}", -1L) == item.dueAtMillis

    fun markDelivered(item: CommitmentEntity) {
        preferences.edit().putLong("delivered_${item.id}", item.dueAtMillis ?: -1L).commit()
        cancel(item)
    }

    private fun pending(id: String, due: Long, actionName: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = actionName
            data = Uri.parse("cue://commitment/$id")
            putExtra("id", id)
            putExtra("due", due)
        }
        return PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    companion object { private val deliveryLock = Any() }
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val scheduler = ReminderScheduler(context)
                if (intent.action == "com.rudrasinha.cue.REMIND" ||
                    intent.action == "com.rudrasinha.cue.REMIND_BACKUP") {
                    val id = intent.getStringExtra("id") ?: return@launch
                    val item = CueDatabase.get(context).commitments().byId(id) ?: return@launch
                    if (item.status != "active" || item.dueAtMillis != intent.getLongExtra("due", -1L)) return@launch
                    if (item.ownerId != "guest" && item.ownerId != scheduler.activeOwnerId()) return@launch
                    synchronized(deliveryLock) {
                        if (!scheduler.wasDelivered(item) && showNotification(context, item)) {
                            scheduler.markDelivered(item)
                        }
                    }
                } else {
                    scheduler.restore()
                }
            } finally {
                result.finish()
            }
        }
    }

    private fun showNotification(context: Context, item: CommitmentEntity): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("cue_reminders", "Cue reminders",
            NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(context, item.id.hashCode(),
            Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notice = Notification.Builder(context, "cue_reminders")
            .setSmallIcon(R.drawable.ic_cue_foreground)
            .setContentTitle(item.title)
            .setContentText(item.details?.takeIf { it.isNotBlank() } ?: "It's time for your reminder.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        return try { manager.notify(item.id.hashCode(), notice); true }
        catch (_: SecurityException) { false }
    }
}
