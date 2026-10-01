package com.rudrasinha.cue.ui

import android.media.MediaPlayer
import android.media.RingtoneManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rudrasinha.cue.reminders.ReminderTone
import com.rudrasinha.cue.reminders.ReminderTones

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderTonePicker(selected: String, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { player?.release() } }
    fun play(tone: ReminderTone) {
        player?.release()
        player = runCatching {
            if (tone.resource != null) MediaPlayer.create(context, tone.resource)
            else MediaPlayer().apply {
                setDataSource(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                prepare()
            }
        }.getOrNull()?.also { sound ->
            sound.setOnCompletionListener { it.reset() }
            runCatching { sound.start() }
        }
    }
    Text("Reminder sound", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Text("Choose a sound for new reminder alerts. Preview each one before picking.",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
    Card(shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(ReminderTones.selected(selected).label, fontWeight = FontWeight.SemiBold)
                Text(if (selected == ReminderTones.DEFAULT) "System default" else "Cue sound · ${selected.toIntOrNull() ?: 1} of 20",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { open = true }) { Text("Choose") }
        }
    }
    if (open) ModalBottomSheet(onDismissRequest = { open = false }) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text("Choose reminder sound", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text("Tap to preview and select.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 540.dp)) {
                items(ReminderTones.choices, key = { it.id }) { tone ->
                    Row(Modifier.fillMaxWidth().clickable {
                        play(tone)
                        onSelect(tone.id)
                    }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected == tone.id, onClick = {
                            play(tone)
                            onSelect(tone.id)
                        })
                        Text(tone.label, Modifier.padding(start = 12.dp),
                            style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
