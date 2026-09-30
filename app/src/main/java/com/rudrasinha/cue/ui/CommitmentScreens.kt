package com.rudrasinha.cue.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rudrasinha.cue.data.CommitmentEntity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormat = DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a")

@Composable
fun CommitmentListScreen(
    active: List<CommitmentEntity>, completed: List<CommitmentEntity>, upcoming: Boolean,
    message: String?, exactAvailable: Boolean, notificationsAllowed: Boolean, onExactAccess: () -> Unit,
    onSave: (String?, String, String?, Long?) -> Unit,
    onComplete: (String) -> Unit, onSnooze: (String) -> Unit, onArchive: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf<CommitmentEntity?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var showCompleted by rememberSaveable { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val visible = if (upcoming) active.filter { (it.dueAtMillis ?: 0L) > now } else active
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val accent = if (dark) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
    val deepAccent = accent.copy(red = accent.red * .55f, green = accent.green * .55f,
        blue = accent.blue * .55f)
    val onAccent = if (dark) Color.White else MaterialTheme.colorScheme.onPrimary

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        Text(if (upcoming) "COMING UP" else "YOUR SPACE", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(if (upcoming) "Upcoming reminders" else "Your reminders",
            style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(if (upcoming) "Everything you have scheduled ahead." else "A simple place for what matters next.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(22.dp))

        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(listOf(accent, deepAccent)))) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 48.dp, y = (-58).dp)
                .size(180.dp).background(onAccent.copy(alpha = .08f), CircleShape))
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = onAccent.copy(alpha = .16f)) {
                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null,
                        tint = onAccent, modifier = Modifier.padding(10.dp).size(24.dp))
                }
                Spacer(Modifier.height(22.dp))
                Text(if (upcoming) "YOUR NEXT MOMENTS" else "A LITTLE ROOM TO BREATHE",
                    style = MaterialTheme.typography.labelSmall, color = onAccent.copy(alpha = .8f),
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(if (upcoming) "${visible.size} on the horizon" else "${active.size} on your mind",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    color = onAccent)
                Text(if (upcoming) "Everything you need, right on time."
                    else "Keep your plans here. We'll help you remember.",
                    style = MaterialTheme.typography.bodyMedium, color = onAccent.copy(alpha = .85f))
                Spacer(Modifier.height(24.dp))
                Button(onClick = { editing = null; editorOpen = true },
                    colors = ButtonDefaults.buttonColors(containerColor = onAccent, contentColor = accent)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("New reminder", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (!notificationsAllowed && active.any { it.dueAtMillis != null }) {
            Spacer(Modifier.height(14.dp))
            Notice("Notifications are off. Enable them in Android settings to receive alerts.")
        }
        if (!exactAvailable && active.any { it.dueAtMillis != null }) {
            Spacer(Modifier.height(12.dp))
            Notice("Android may deliver some alerts late.") {
                TextButton(onClick = onExactAccess) { Text("Allow precise timing") }
            }
        }
        message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(26.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (upcoming) "Scheduled" else "All reminders", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${visible.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        if (visible.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text(if (upcoming) "Nothing scheduled yet" else "A clear mind starts here",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(if (upcoming) "Set a date and time on a reminder to see it here."
                        else "Try ‘Call home’ or ‘Take medicine’. Add a time if you want an alert.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        visible.forEach { item ->
            key(item.id) {
                ReminderCard(item, now, onEdit = { editing = item; editorOpen = true },
                    onComplete = { onComplete(item.id) }, onSnooze = { onSnooze(item.id) },
                    onArchive = { onArchive(item.id) })
                Spacer(Modifier.height(12.dp))
            }
        }
        if (!upcoming && completed.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { showCompleted = !showCompleted }) {
                Text("Completed (${completed.size}) ${if (showCompleted) "⌃" else "⌄"}")
            }
            if (showCompleted) completed.forEach { item ->
                Text("✓  ${item.title}", Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(28.dp))
    }
    if (editorOpen) ReminderEditor(editing, { editorOpen = false }) { title, details, due ->
        onSave(editing?.id, title, details, due)
        editorOpen = false
    }
}

@Composable
private fun Notice(text: String, action: @Composable (() -> Unit)? = null) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(text, style = MaterialTheme.typography.bodySmall)
            action?.invoke()
        }
    }
}

@Composable
private fun ReminderCard(
    item: CommitmentEntity, now: Long,
    onEdit: () -> Unit, onComplete: () -> Unit, onSnooze: () -> Unit, onArchive: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(item.dueAtMillis?.let { if (it <= now) "Overdue · ${formatTime(it)}" else formatTime(it) }
                    ?: "No alert set", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.height(12.dp))
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            item.details?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onComplete) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(17.dp))
                    Text("  Done")
                }
                TextButton(onClick = onEdit) { Text("Edit") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More reminder actions")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (item.dueAtMillis != null) DropdownMenuItem(text = { Text("Remind in 10 minutes") },
                        onClick = { menuOpen = false; onSnooze() })
                    DropdownMenuItem(text = { Text("Archive reminder") },
                        onClick = { menuOpen = false; onArchive() })
                }
            }
        }
    }
}

@Composable
private fun ReminderEditor(
    item: CommitmentEntity?, onDismiss: () -> Unit, onSave: (String, String?, Long?) -> Unit
) {
    val context = LocalContext.current
    var title by remember(item?.id) { mutableStateOf(item?.title.orEmpty()) }
    var details by remember(item?.id) { mutableStateOf(item?.details.orEmpty()) }
    var due by remember(item?.id) { mutableStateOf(item?.dueAtMillis) }
    AlertDialog(
        onDismissRequest = onDismiss, shape = RoundedCornerShape(28.dp),
        title = { Text(if (item == null) "New reminder" else "Edit reminder") },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Reminder title") }, placeholder = { Text("e.g. Call home") },
                    singleLine = true)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(details, { details = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Note (optional)") }, maxLines = 3)
                Spacer(Modifier.height(16.dp))
                Text(due?.let { "Alert · ${formatTime(it)}" } ?: "No alert set",
                    style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = {
                    val start = due?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
                        ?: LocalDateTime.now().plusMinutes(5).atZone(ZoneId.systemDefault())
                    DatePickerDialog(context, { _, year, month, day ->
                        TimePickerDialog(context, { _, hour, minute ->
                            due = LocalDateTime.of(year, month + 1, day, hour, minute)
                                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        }, start.hour, start.minute, false).show()
                    }, start.year, start.monthValue - 1, start.dayOfMonth).show()
                }) { Text(if (due == null) "Choose date & time" else "Change date & time") }
                if (due != null) TextButton(onClick = { due = null }) { Text("Remove alert") }
                if (due == null) Text("You can save this without an alert.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (due != null && due!! <= System.currentTimeMillis()) {
                    Text("Choose a future time.", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(title.trim(), details.trim().ifBlank { null }, due) },
                enabled = title.isNotBlank() && (due == null || due!! > System.currentTimeMillis())) {
                Text("Save reminder")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatTime(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault()).format(timeFormat)
