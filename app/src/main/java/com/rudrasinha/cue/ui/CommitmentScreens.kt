package com.rudrasinha.cue.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rudrasinha.cue.data.CommitmentEntity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormat = DateTimeFormatter.ofPattern("d MMM, h:mm a")

@Composable
fun CommitmentListScreen(
    active: List<CommitmentEntity>, completed: List<CommitmentEntity>, upcoming: Boolean,
    message: String?, exactAvailable: Boolean, onExactAccess: () -> Unit,
    onSave: (String?, String, String?, Long?) -> Unit,
    onComplete: (String) -> Unit, onSnooze: (String) -> Unit, onArchive: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf<CommitmentEntity?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    val visible = if (upcoming) active.filter { (it.dueAtMillis ?: 0L) > System.currentTimeMillis() } else active
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        Text(if (upcoming) "Upcoming" else "Today, in focus", style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold)
        Text(if (upcoming) "Your scheduled commitments" else "${active.size} active commitments",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { editing = null; editorOpen = true }) { Text("Add commitment") }
        if (!exactAvailable) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onExactAccess) { Text("Allow exact reminder timing") }
            Text("Until allowed, Android may deliver alerts later than selected.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(16.dp))
        if (visible.isEmpty()) Text("No commitments here yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        visible.forEach { item ->
            Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    item.details?.let { Text(it) }
                    Text(item.dueAtMillis?.let { "Remind: ${formatTime(it)}" } ?: "No alert scheduled",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { editing = item; editorOpen = true }) { Text("Edit") }
                        TextButton(onClick = { onComplete(item.id) }) { Text("Done") }
                        if (item.dueAtMillis != null) TextButton(onClick = { onSnooze(item.id) }) { Text("+10 min") }
                        TextButton(onClick = { onArchive(item.id) }) { Text("Archive") }
                    }
                }
            }
        }
        if (!upcoming && completed.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Completed", style = MaterialTheme.typography.titleLarge)
            completed.forEach { Text("✓ ${it.title}", Modifier.padding(vertical = 6.dp)) }
        }
    }
    if (editorOpen) CommitmentEditor(editing, {
        editorOpen = false
    }, { title, details, due ->
        onSave(editing?.id, title, details, due)
        editorOpen = false
    })
}

@Composable
private fun CommitmentEditor(
    item: CommitmentEntity?, onDismiss: () -> Unit, onSave: (String, String?, Long?) -> Unit
) {
    val context = LocalContext.current
    var title by remember(item?.id) { mutableStateOf(item?.title.orEmpty()) }
    var details by remember(item?.id) { mutableStateOf(item?.details.orEmpty()) }
    var due by remember(item?.id) { mutableStateOf(item?.dueAtMillis) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "New commitment" else "Edit commitment") },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(details, { details = it }, label = { Text("Details (optional)") })
                Spacer(Modifier.height(12.dp))
                Text(due?.let(::formatTime) ?: "No reminder time selected")
                TextButton(onClick = {
                    val start = due?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
                        ?: LocalDateTime.now().plusMinutes(5).atZone(ZoneId.systemDefault())
                    DatePickerDialog(context, { _, year, month, day ->
                        TimePickerDialog(context, { _, hour, minute ->
                            due = LocalDateTime.of(year, month + 1, day, hour, minute)
                                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        }, start.hour, start.minute, false).show()
                    }, start.year, start.monthValue - 1, start.dayOfMonth).show()
                }) { Text("Set date and time") }
                if (due != null) TextButton(onClick = { due = null }) { Text("Remove alert") }
                if (due != null && due!! <= System.currentTimeMillis()) {
                    Text("Choose a future time.", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(title.trim(), details, due) },
                enabled = title.isNotBlank() && (due == null || due!! > System.currentTimeMillis())) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatTime(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault()).format(timeFormat)
