package com.rudrasinha.cue.reminders

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rudrasinha.cue.auth.CueAuth
import com.rudrasinha.cue.data.CloudCommitments
import com.rudrasinha.cue.data.CommitmentActions
import com.rudrasinha.cue.data.CueDatabase
import com.rudrasinha.cue.data.ReminderEventEntity
import java.util.UUID
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AlarmActionReceiver : BroadcastReceiver() {
    companion object {
        const val SNOOZE = "com.rudrasinha.cue.alarm.SNOOZE"
        const val DISMISS = "com.rudrasinha.cue.alarm.DISMISS"
    }
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val id = intent.getStringExtra(AlarmPlaybackService.EXTRA_ID) ?: return@launch
                val due = intent.getLongExtra("due", -1L)
                val scheduler = ReminderScheduler(context)
                val database = CueDatabase.get(context)
                val item = database.commitments().byId(id) ?: return@launch
                if (item.dueAtMillis != due || item.status != "active" ||
                    (item.ownerId != "guest" && item.ownerId != scheduler.activeOwnerId())) return@launch
                context.getSystemService(NotificationManager::class.java).cancel(id.hashCode())
                runCatching { context.startService(Intent(context, AlarmPlaybackService::class.java).apply {
                    action = AlarmPlaybackService.ACTION_STOP
                    putExtra(AlarmPlaybackService.EXTRA_ID, id)
                }) }
                when (intent.action) {
                    SNOOZE -> CommitmentActions(database, scheduler,
                        CloudCommitments(database, CueAuth(context).client, scheduler)).snooze(item.ownerId, id)
                    DISMISS -> {
                        val key = "dismissed:$id:$due"
                        database.history().insertEvent(ReminderEventEntity(
                            UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8)).toString(),
                            item.ownerId, id, "dismissed",
                            JSONObject().put("title", item.title).put("due_at_millis", due).toString(),
                            "alarm", key, System.currentTimeMillis(), item.ownerId != "guest"))
                    }
                }
            } finally { pending.finish() }
        }
    }
}
