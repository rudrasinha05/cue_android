package com.rudrasinha.cue.reminders

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rudrasinha.cue.data.CueDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        val id = intent.getStringExtra(AlarmPlaybackService.EXTRA_ID) ?: run { finish(); return }
        val due = intent.getLongExtra("due", -1L)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val item = CueDatabase.get(applicationContext).commitments().byId(id)
            val owner = ReminderScheduler(applicationContext).activeOwnerId()
            withContext(Dispatchers.Main) {
                if (item == null || item.dueAtMillis != due || item.status != "active" ||
                    (item.ownerId != "guest" && item.ownerId != owner)) { finish(); return@withContext }
                setContent {
                    MaterialTheme {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            Column(Modifier.fillMaxSize().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center) {
                                Text("CUE REMINDER", color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.height(24.dp))
                                Text(item.title, style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold)
                                item.details?.let { Text(it, Modifier.padding(top = 12.dp),
                                    style = MaterialTheme.typography.bodyLarge) }
                                Spacer(Modifier.height(38.dp))
                                Button(onClick = { act(AlarmActionReceiver.SNOOZE, id, due) },
                                    modifier = Modifier.fillMaxWidth().height(56.dp)) {
                                    Text("Snooze 10 minutes")
                                }
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(onClick = { act(AlarmActionReceiver.DISMISS, id, due) },
                                    modifier = Modifier.fillMaxWidth().height(56.dp)) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun act(actionName: String, id: String, due: Long) {
        sendBroadcast(Intent(this, AlarmActionReceiver::class.java).apply {
            action = actionName
            putExtra(AlarmPlaybackService.EXTRA_ID, id)
            putExtra("due", due)
        })
        finish()
    }
}
