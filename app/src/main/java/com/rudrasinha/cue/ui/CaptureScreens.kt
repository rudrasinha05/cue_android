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
import com.rudrasinha.cue.data.SourceEntity
import com.rudrasinha.cue.data.HistoryArchive
import com.rudrasinha.cue.data.HistoryBatchEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONObject

private val night = Color(0xFF101017)
private val purple = Color(0xFFB69CFF)
private val pale = Color(0xFFF8F6FF)
private val subdued = Color(0xFFADA9BA)
private val panel = Color(0xFF20202A)
private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a")

@Composable
fun CaptureHub(onVoice: () -> Unit, onDocument: () -> Unit, onQuick: () -> Unit,
    message: String?,
    modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(night, Color(0xFF29243A), night)))
        .verticalScroll(rememberScrollState()).padding(22.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Catch a thought.", color = pale, style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold)
        Text("Turn it into a reminder in a few taps.", color = subdued,
            modifier = Modifier.padding(top = 8.dp, bottom = 28.dp))
        message?.let { Text(it, color = purple, modifier = Modifier.padding(bottom = 12.dp)) }
        Surface(shape = RoundedCornerShape(28.dp), color = Color(0xFF3A3154),
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
                    containerColor = purple, contentColor = night)) { Text("Talk to Cue") }
            }
        }
        Spacer(Modifier.height(14.dp))
        CaptureOption(Icons.Filled.EditNote, "Quick reminder",
            "Write it down and add a date if you need one.", onQuick)
        Spacer(Modifier.height(12.dp))
        CaptureOption(Icons.Filled.Description, "Import a document",
            "Pick text, CSV, TSV, Markdown or DOCX to review.", onDocument)
        Spacer(Modifier.height(24.dp))
        Text("You can also share text from another app or select text and tap Cue.",
            color = subdued, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun CaptureOption(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), color = panel,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = Color(0xFF393449)) {
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
    batches: List<HistoryBatchEntity>,
    modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<ReminderEventEntity?>(null) }
    val sourceByReminder = remember(sources) { sources.groupBy { it.commitmentId } }
    val archived = remember(batches) { batches.map { runCatching { HistoryArchive.read(it) } } }
    val allEvents = (events + archived.flatMap { it.getOrDefault(emptyList()) })
        .distinctBy { it.id }.sortedWith(compareByDescending<ReminderEventEntity> { it.occurredAtMillis }
            .thenByDescending { it.id })
    val visible = allEvents.filter { event ->
        val text = event.snapshot().optString("title")
        query.isBlank() || text.contains(query, true) || event.eventType.contains(query, true) ||
            sourceByReminder[event.commitmentId].orEmpty().any {
                it.title.orEmpty().contains(query, true) || it.excerpt.orEmpty().contains(query, true)
            }
    }
    Column(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(night, Color(0xFF29243A), night)))
        .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        Text("Your history", color = pale, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineLarge)
        Text("Every change and where a reminder came from.", color = subdued,
            modifier = Modifier.padding(top = 7.dp, bottom = 20.dp))
        if (archived.any { it.isFailure }) Text("Some older history needs repair. Sync your account and try again.",
            color = Color(0xFFFFC76B), modifier = Modifier.padding(bottom = 12.dp))
        OutlinedTextField(query, { query = it }, singleLine = true,
            placeholder = { Text("Search titles, changes or sources") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
        Spacer(Modifier.height(18.dp))
        if (visible.isEmpty()) {
            Surface(shape = RoundedCornerShape(22.dp), color = panel) {
                Text(if (query.isBlank()) "Your saved reminder changes will appear here."
                    else "No history matches your search.", color = subdued,
                    modifier = Modifier.fillMaxWidth().padding(20.dp))
            }
        }
        visible.forEach { event ->
            val source = sourceByReminder[event.commitmentId]?.firstOrNull()
            Surface(shape = RoundedCornerShape(22.dp), color = panel,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    .clickable { selected = event }) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.Top) {
                    Icon(if (event.eventType == "created") Icons.Filled.AddCircle
                        else Icons.Filled.History, contentDescription = null, tint = purple,
                        modifier = Modifier.size(25.dp))
                    Column(Modifier.weight(1f).padding(start = 13.dp)) {
                        Text(event.snapshot().optString("title", "Reminder"), color = pale,
                            fontWeight = FontWeight.SemiBold, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        Text("${event.eventType.replaceFirstChar { it.uppercase() }} · ${event.dateLabel()}",
                            color = subdued, style = MaterialTheme.typography.bodySmall)
                        if (source != null) Text("From ${source.originType}${source.title?.let { " · $it" }.orEmpty()}",
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
        val source = sourceByReminder[event.commitmentId]?.firstOrNull()
        AlertDialog(onDismissRequest = { selected = null },
            title = { Text(event.snapshot().optString("title", "Reminder")) },
            text = {
                Column {
                    Text("${event.eventType.replaceFirstChar { it.uppercase() }} · ${event.dateLabel()}")
                    val snapshot = event.snapshot()
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
                    }
                }
            }, confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } })
    }
}

private fun ReminderEventEntity.snapshot(): JSONObject =
    runCatching { JSONObject(changeData) }.getOrDefault(JSONObject())

private fun ReminderEventEntity.dateLabel(): String = Instant.ofEpochMilli(occurredAtMillis)
    .atZone(ZoneId.systemDefault()).format(dateFormat)
