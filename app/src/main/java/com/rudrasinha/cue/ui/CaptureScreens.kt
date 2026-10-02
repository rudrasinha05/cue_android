package com.rudrasinha.cue.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rudrasinha.cue.data.ReminderEventEntity
import com.rudrasinha.cue.assistant.CueAction
import com.rudrasinha.cue.data.SourceEntity
import com.rudrasinha.cue.data.HistoryArchive
import com.rudrasinha.cue.data.HistoryBatchEntity
import com.rudrasinha.cue.data.HistoryBatchSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val night: Color @Composable get() = MaterialTheme.colorScheme.background
private val purple: Color @Composable get() = MaterialTheme.colorScheme.primary
private val pale: Color @Composable get() = MaterialTheme.colorScheme.onSurface
private val subdued: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val panel: Color @Composable get() = MaterialTheme.colorScheme.surface
private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantActionSheet(onDismiss: () -> Unit, onAction: (CueAction) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = night,
        contentColor = pale, dragHandle = { BottomSheetDefaults.DragHandle(color = subdued) }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
            Text("Cue at your fingertips", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = pale)
            Text("Choose what you want to do.", color = subdued,
                modifier = Modifier.padding(top = 6.dp, bottom = 20.dp))
            CueAction.entries.forEach { action ->
                val icon = when (action) {
                    CueAction.VOICE -> Icons.Filled.Mic
                    CueAction.QUICK -> Icons.Filled.EditNote
                    CueAction.IMPORT -> Icons.Filled.DocumentScanner
                    CueAction.ASK -> Icons.Filled.AutoAwesome
                    CueAction.DAY -> Icons.Filled.Today
                    CueAction.SETTINGS -> Icons.Filled.Tune
                }
                Surface(onClick = { onAction(action) }, shape = RoundedCornerShape(20.dp),
                    color = panel, modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp)) {
                    Row(Modifier.padding(horizontal = 17.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = null, tint = purple,
                            modifier = Modifier.size(25.dp))
                        Text(action.label, modifier = Modifier.weight(1f).padding(start = 16.dp),
                            color = pale, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = subdued)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
fun CaptureHub(onVoice: () -> Unit, onDocument: () -> Unit, onQuick: () -> Unit,
    message: String?,
    modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(night,
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = .35f), night)))
        .verticalScroll(rememberScrollState()).padding(22.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Catch a thought.", color = pale, style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold)
        Text("Turn it into a reminder in a few taps.", color = subdued,
            modifier = Modifier.padding(top = 8.dp, bottom = 28.dp))
        message?.let { Text(it, color = purple, modifier = Modifier.padding(bottom = 12.dp)) }
        Surface(shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, purple.copy(alpha = .55f))) {
            Column(Modifier.fillMaxWidth().padding(22.dp)) {
                Icon(Icons.Filled.Mic, contentDescription = null, tint = purple,
                    modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(15.dp))
                Text("Say it out loud", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = pale)
                Text("Speak a reminder, then check the words and choose an alert.",
                    color = subdued, modifier = Modifier.padding(vertical = 10.dp))
                Button(onClick = onVoice, colors = ButtonDefaults.buttonColors(
                    containerColor = purple,
                    contentColor = MaterialTheme.colorScheme.onPrimary)) { Text("Talk to Cue") }
            }
        }
        Spacer(Modifier.height(14.dp))
        CaptureOption(Icons.Filled.EditNote, "Quick reminder",
            "Write it down and add a date if you need one.", onQuick)
        Spacer(Modifier.height(12.dp))
        CaptureOption(Icons.Filled.Description, "Import a document",
            "Read text, tables, DOCX, PDF or an image and review it.", onDocument)
        Spacer(Modifier.height(24.dp))
        Text("You can also share text from another app or select text and tap Cue.",
            color = subdued, style = MaterialTheme.typography.bodyMedium)
        Text("Long documents show their first 4,000 characters for review.",
            color = subdued, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 9.dp))
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun CaptureOption(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), color = panel,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, contentDescription = null, tint = purple,
                    modifier = Modifier.padding(12.dp).size(24.dp))
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(title, color = pale, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = subdued, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = subdued)
        }
    }
}

@Composable
fun HistoryScreen(events: List<ReminderEventEntity>, sources: List<SourceEntity>,
    batches: List<HistoryBatchSummary>,
    loadBatch: suspend (String) -> HistoryBatchEntity?,
    onOpenSource: (String) -> Boolean,
    modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<ReminderEventEntity?>(null) }
    var sourceError by remember { mutableStateOf(false) }
    val sourceByReminder = remember(sources) { sources.groupBy { it.commitmentId } }
    var openedBatchId by remember { mutableStateOf<String?>(null) }
    val openedBatch = batches.firstOrNull { it.id == openedBatchId }
    val openedEvents by produceState<Result<List<ReminderEventEntity>>?>(null,
        openedBatch?.id, openedBatch?.ownerId) {
        value = null
        if (openedBatch != null) {
            value = runCatching {
                val saved = withContext(Dispatchers.IO) { loadBatch(openedBatch.id) }
                    ?: error("Archive no longer available")
                withContext(Dispatchers.Default) { HistoryArchive.read(saved) }
            }
        }
    }
    val matchingBatches = batches.filter { batch ->
        query.isBlank() || batch.searchIndex.isBlank() || batch.searchIndex.contains(query, true) ||
            sources.any { it.commitmentId in batch.searchIndex &&
                (it.title.orEmpty().contains(query, true) || it.excerpt.orEmpty().contains(query, true)) }
    }
    fun sourceFor(event: ReminderEventEntity): SourceEntity? {
        val linked = sourceByReminder[event.commitmentId].orEmpty()
        val sourceId = event.snapshot().optString("source_id")
        return linked.firstOrNull { it.id == sourceId } ?: linked.firstOrNull()
    }
    fun eventLabel(event: ReminderEventEntity): String =
        if (event.eventType == "source_added") "Source added"
        else if (event.eventType == "sync_conflict") "Earlier device edit saved"
        else event.eventType.replaceFirstChar { it.uppercase() }

    val allEvents = (events + openedEvents?.getOrDefault(emptyList()).orEmpty())
        .distinctBy { it.id }.sortedWith(compareByDescending<ReminderEventEntity> { it.occurredAtMillis }
            .thenByDescending { it.id })
    val visible = allEvents.filter { event ->
        val text = event.snapshot().optString("title")
        query.isBlank() || text.contains(query, true) || event.eventType.contains(query, true) ||
            sourceByReminder[event.commitmentId].orEmpty().any {
                it.title.orEmpty().contains(query, true) || it.excerpt.orEmpty().contains(query, true)
            }
    }
    Column(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(night,
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = .35f), night)))
        .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        Text("Your history", color = pale, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineLarge)
        Text("Every change and where a reminder came from.", color = subdued,
            modifier = Modifier.padding(top = 7.dp, bottom = 20.dp))
        OutlinedTextField(query, { query = it }, singleLine = true,
            placeholder = { Text("Search recent and indexed archives") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = pale, unfocusedTextColor = pale, cursorColor = purple,
                focusedBorderColor = purple, unfocusedBorderColor = subdued,
                focusedPlaceholderColor = subdued, unfocusedPlaceholderColor = subdued,
                focusedLeadingIconColor = purple, unfocusedLeadingIconColor = subdued))
        Spacer(Modifier.height(18.dp))
        if (matchingBatches.isNotEmpty()) {
            Text("OLDER HISTORY", color = subdued, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
            matchingBatches.forEach { batch ->
                val open = openedBatchId == batch.id
                Surface(shape = RoundedCornerShape(20.dp), color = panel,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        .clickable { openedBatchId = if (open) null else batch.id }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Inventory2, contentDescription = null, tint = purple)
                        Column(Modifier.weight(1f).padding(start = 13.dp)) {
                            Text("${batch.eventCount} saved changes", color = pale,
                                fontWeight = FontWeight.SemiBold)
                            Text("${Instant.ofEpochMilli(batch.firstAtMillis).atZone(ZoneId.systemDefault()).format(dateFormat)} – " +
                                Instant.ofEpochMilli(batch.lastAtMillis).atZone(ZoneId.systemDefault()).format(dateFormat),
                                color = subdued, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (open) "Close archive" else "Open archive", tint = subdued)
                    }
                }
            }
            if (openedBatch != null && openedEvents == null) Text("Opening older history…",
                color = subdued, modifier = Modifier.padding(bottom = 12.dp))
            if (openedEvents?.isFailure == true) Text("This archive could not be verified. Sync your account and try again.",
                color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp))
            Spacer(Modifier.height(10.dp))
        }
        if (visible.isEmpty()) {
            Surface(shape = RoundedCornerShape(22.dp), color = panel) {
                Text(if (batches.isNotEmpty()) "Open an older archive to see its saved changes."
                    else if (query.isBlank()) "Your saved reminder changes will appear here."
                    else "No history matches your search.", color = subdued,
                    modifier = Modifier.fillMaxWidth().padding(20.dp))
            }
        }
        visible.forEach { event ->
            val source = sourceFor(event)
            Surface(shape = RoundedCornerShape(22.dp), color = panel,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    .clickable { selected = event; sourceError = false }) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.Top) {
                    Icon(if (event.eventType == "created") Icons.Filled.AddCircle
                        else Icons.Filled.History, contentDescription = null, tint = purple,
                        modifier = Modifier.size(25.dp))
                    Column(Modifier.weight(1f).padding(start = 13.dp)) {
                        Text(event.snapshot().optString("title", "Reminder"), color = pale,
                            fontWeight = FontWeight.SemiBold, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        Text("${eventLabel(event)} · ${event.dateLabel()}",
                            color = subdued, style = MaterialTheme.typography.bodySmall)
                        if (source != null) Text("From ${source.originType}${source.title?.let { " · $it" }.orEmpty()} · ${sourceByReminder[event.commitmentId]?.size ?: 1} sources",
                            color = purple, style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = "View history detail", tint = subdued)
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
    selected?.let { event ->
        val source = sourceFor(event)
        AlertDialog(onDismissRequest = { selected = null },
            containerColor = panel, titleContentColor = pale, textContentColor = subdued,
            title = { Text(event.snapshot().optString("title", "Reminder")) },
            text = {
                Column {
                    Text("${eventLabel(event)} · ${event.dateLabel()}")
                    val snapshot = event.snapshot()
                    if (event.eventType == "sync_conflict") Text(
                        "Another device had a newer edit. This history entry preserves the earlier version.",
                        modifier = Modifier.padding(top = 10.dp))
                    snapshot.optString("details").takeIf { it.isNotBlank() && it != "null" }?.let {
                        Text(it, modifier = Modifier.padding(top = 10.dp))
                    }
                    snapshot.optLong("due_at_millis").takeIf { it > 0 }?.let {
                        Text("Alert: ${Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(dateFormat)}",
                            modifier = Modifier.padding(top = 10.dp))
                    }
                    source?.let {
                        Text("Source: ${it.originType}${it.title?.let { title -> " · $title" }.orEmpty()}",
                            modifier = Modifier.padding(top = 12.dp))
                        it.excerpt?.takeIf(String::isNotBlank)?.let { excerpt ->
                            Text(excerpt.take(500), modifier = Modifier.padding(top = 8.dp))
                        }
                        it.originalUri?.takeIf { uri -> uri.startsWith("content://") }?.let { uri ->
                            TextButton(onClick = { sourceError = !onOpenSource(uri) }) {
                                Text("Open original on this device")
                            }
                        }
                        if (sourceError) Text("Original isn't available here. The saved excerpt is above.",
                            color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }, confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } })
    }
}

private fun ReminderEventEntity.snapshot(): JSONObject =
    runCatching { JSONObject(changeData) }.getOrDefault(JSONObject())

private fun ReminderEventEntity.dateLabel(): String = Instant.ofEpochMilli(occurredAtMillis)
    .atZone(ZoneId.systemDefault()).format(dateFormat)
