package com.rudrasinha.cue.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rudrasinha.cue.data.CommitmentEntity
import com.rudrasinha.cue.data.CaptureOrigin
import com.rudrasinha.cue.data.SourceEntity
import com.rudrasinha.cue.reminders.ReminderTones
import com.rudrasinha.cue.reminders.ChainSchedule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val canvasTop: Color @Composable get() = MaterialTheme.colorScheme.background
private val cardColor: Color @Composable get() = MaterialTheme.colorScheme.surface
private val ivory: Color @Composable get() = MaterialTheme.colorScheme.onSurface
private val muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val violet: Color @Composable get() = MaterialTheme.colorScheme.primary
private val coral: Color @Composable get() = MaterialTheme.colorScheme.error
private val sky: Color @Composable get() = MaterialTheme.colorScheme.secondary
private val aqua: Color @Composable get() = MaterialTheme.colorScheme.primary
private val timeFormat = DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a")
private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM")

private enum class ReminderView(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Filled.CalendarMonth),
    SCHEDULED("Scheduled", Icons.Filled.Schedule),
    PAST("Past", Icons.Filled.History),
    NO_ALERT("No alert", Icons.Filled.NotificationsOff),
    COMPLETED("Completed", Icons.Filled.CheckCircle),
    ALL("All", Icons.Filled.ViewAgenda)
}

private val ReminderView.tint: Color @Composable get() = when (this) {
    ReminderView.TODAY, ReminderView.PAST -> coral
    ReminderView.SCHEDULED -> sky
    ReminderView.NO_ALERT -> violet
    ReminderView.COMPLETED -> muted
    ReminderView.ALL -> aqua
}

data class CaptureDraft(val id: String, val text: String, val origin: CaptureOrigin,
    val suggestedDueAtMillis: Long? = null)

@Composable
fun CommitmentListScreen(
    active: List<CommitmentEntity>, completed: List<CommitmentEntity>, upcoming: Boolean,
    message: String?, exactAvailable: Boolean, notificationsAllowed: Boolean,
    onExactAccess: () -> Unit, onNotificationAccess: () -> Unit,
    defaultTone: String, onSave: suspend (String?, String, String?, Long?, CaptureOrigin, String, CaptureOrigin?) -> Unit,
    attachments: List<SourceEntity>,
    onComplete: (String) -> Unit, onSnooze: (String) -> Unit,
    onFollowUp: (String, Long) -> Unit, onChain: (String, List<Long>?) -> Unit,
    onArchive: (String) -> Unit,
    onDelete: (String) -> Unit,
    signedIn: Boolean, onSync: () -> Unit, onSettings: () -> Unit,
    onDayPlan: () -> Unit,
    externalDraft: CaptureDraft?, onCaptureDismiss: () -> Unit,
    focusTodayToken: Int = 0,
    onVoice: () -> Unit, onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedView by remember { mutableStateOf(ReminderView.ALL) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var visibleMonth by remember { mutableStateOf(YearMonth.now()) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var sortByTitle by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CommitmentEntity?>(null) }
    var draftTitle by remember { mutableStateOf("") }
    var draftDetails by remember { mutableStateOf("") }
    var draftDue by remember { mutableStateOf<Long?>(null) }
    var draftOrigin by remember { mutableStateOf(CaptureOrigin("manual")) }
    var draftId by remember { mutableStateOf("") }
    var editorOpen by remember { mutableStateOf(false) }
    LaunchedEffect(focusTodayToken) {
        if (focusTodayToken > 0) {
            selectedView = ReminderView.TODAY
            selectedDate = LocalDate.now()
            visibleMonth = YearMonth.now()
        }
    }
    val today = LocalDate.now()
    val now = System.currentTimeMillis()
    fun dateOf(item: CommitmentEntity): LocalDate? = item.dueAtMillis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
    }
    fun openEditor(item: CommitmentEntity? = null, suggestedTitle: String = "") {
        editing = item
        draftTitle = suggestedTitle
        draftDetails = ""
        draftDue = null
        draftOrigin = CaptureOrigin("manual")
        draftId = java.util.UUID.randomUUID().toString()
        editorOpen = true
    }
    LaunchedEffect(externalDraft?.id) {
        if (externalDraft != null) {
            editing = null
            draftTitle = externalDraft.text.lineSequence().firstOrNull().orEmpty().take(100)
            draftDetails = externalDraft.text.take(2000).takeIf { it != draftTitle }.orEmpty()
            draftDue = externalDraft.suggestedDueAtMillis
            draftOrigin = externalDraft.origin
            draftId = externalDraft.id
            editorOpen = true
        }
    }
    fun matching(view: ReminderView): List<CommitmentEntity> = when (view) {
        ReminderView.TODAY -> active.filter { dateOf(it) == today }
        ReminderView.SCHEDULED -> active.filter { (it.dueAtMillis ?: 0L) > now }
        ReminderView.PAST -> active.filter { (it.dueAtMillis ?: Long.MAX_VALUE) < now }
        ReminderView.NO_ALERT -> active.filter { it.dueAtMillis == null }
        ReminderView.COMPLETED -> completed
        ReminderView.ALL -> active
    }
    val selectedItems = if (upcoming) active.filter { dateOf(it) == selectedDate }
        else matching(selectedView)
    val visibleItems = selectedItems.filter { it.title.contains(query, ignoreCase = true) ||
        it.details?.contains(query, ignoreCase = true) == true }
        .let { list -> if (sortByTitle) list.sortedBy { it.title.lowercase() }
            else list.sortedWith(compareBy<CommitmentEntity> { it.dueAtMillis ?: Long.MAX_VALUE }
                .thenBy { it.title.lowercase() }) }
    val isCompleted = !upcoming && selectedView == ReminderView.COMPLETED

    run {
        Box(modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(canvasTop, MaterialTheme.colorScheme.primaryContainer.copy(alpha = .35f), canvasTop)))) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)) {
                Spacer(Modifier.height(26.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (upcoming) "Calendar" else "Reminders",
                        color = ivory, style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" },
                        modifier = Modifier.size(44.dp).background(cardColor, CircleShape)) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search reminders", tint = ivory)
                    }
                    Spacer(Modifier.width(8.dp))
                    Box {
                        IconButton(onClick = { menuOpen = true },
                            modifier = Modifier.size(44.dp).background(cardColor, CircleShape)) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = ivory)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(if (signedIn) "Sync now" else "Sign in to sync") },
                                onClick = {
                                    menuOpen = false
                                    if (signedIn) onSync() else onSettings()
                                })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Sort by time") },
                                trailingIcon = { if (!sortByTitle) Icon(Icons.Filled.Check, null) },
                                onClick = { sortByTitle = false; menuOpen = false })
                            DropdownMenuItem(text = { Text("Sort by title") },
                                trailingIcon = { if (sortByTitle) Icon(Icons.Filled.Check, null) },
                                onClick = { sortByTitle = true; menuOpen = false })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Settings") },
                                onClick = { menuOpen = false; onSettings() })
                        }
                    }
                }
                if (searchOpen) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(value = query, onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text("Find a reminder") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ivory, unfocusedTextColor = ivory,
                            focusedPlaceholderColor = muted, unfocusedPlaceholderColor = muted,
                            focusedBorderColor = violet, unfocusedBorderColor = muted))
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onVoice, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Speak")
                    }
                    OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import")
                    }
                }
                Spacer(Modifier.height(18.dp))
                if (upcoming) {
                    Surface(shape = RoundedCornerShape(28.dp), color = cardColor,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp)) {
                            CalendarGrid(visibleMonth, selectedDate,
                                active.mapNotNull(::dateOf).groupingBy { it }.eachCount(),
                                onPrevious = {
                                    visibleMonth = visibleMonth.minusMonths(1)
                                    selectedDate = visibleMonth.atDay(1)
                                }, onNext = {
                                    visibleMonth = visibleMonth.plusMonths(1)
                                    selectedDate = visibleMonth.atDay(1)
                                }, onDate = { selectedDate = it })
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { selectedDate = today; visibleMonth = YearMonth.from(today) }) {
                            Text("Today", color = violet)
                        }
                        TextButton(onClick = onDayPlan) {
                            Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("View my day")
                        }
                    }
                } else {
                    val next = active.filter { (it.dueAtMillis ?: 0L) > now }
                        .minByOrNull { it.dueAtMillis ?: Long.MAX_VALUE }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(onClick = { selectedView = ReminderView.TODAY },
                            modifier = Modifier.weight(1f).height(132.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.primaryContainer) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Text("Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${matching(ReminderView.TODAY).size} reminders",
                                    style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                        Surface(onClick = {
                            if (next != null) selectedView = ReminderView.SCHEDULED else onDayPlan()
                        }, modifier = Modifier.weight(1f).height(132.dp),
                            shape = RoundedCornerShape(24.dp), color = cardColor,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Text("Next", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(next?.title ?: "Your schedule is clear", maxLines = 2,
                                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                next?.dueAtMillis?.let { Text(formatTime(it), color = violet,
                                    style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ReminderView.entries.size) { index ->
                            val view = ReminderView.entries[index]
                            FilterChip(selected = selectedView == view,
                                onClick = { selectedView = view },
                                label = { Text("${view.label} ${matching(view).size}") })
                        }
                    }
                }
                if (!notificationsAllowed && active.any { it.dueAtMillis != null }) {
                    Notice("Notifications are off. Alerts won't appear.",
                        "Open settings", onNotificationAccess)
                }
                if (!exactAvailable && active.any { it.dueAtMillis != null }) {
                    Notice("Some alerts may arrive a little late.",
                        "Allow precise timing", onExactAccess)
                }
                message?.let { Text(it, color = violet, modifier = Modifier.padding(vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (upcoming) selectedDate.format(dayFormat)
                            else if (selectedView == ReminderView.ALL) "Your list"
                            else selectedView.label,
                            color = ivory, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold)
                        Text(if (upcoming) "Your plan for this day"
                            else if (isCompleted) "The things you finished"
                            else "Tap a reminder to edit it",
                            color = muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text("${visibleItems.size}", color = ivory,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (visibleItems.isEmpty()) {
                    EmptyReminderState(when {
                        query.isNotBlank() -> "No reminders match your search."
                        upcoming -> "Nothing planned for this day."
                        isCompleted -> "Completed reminders will appear here."
                        selectedView != ReminderView.ALL -> "Nothing here yet."
                        else -> "A clear day. Add something you want to remember."
                    }, onAdd = { openEditor() })
                } else {
                    val sections = if (!upcoming && selectedView == ReminderView.ALL) listOf(
                        "OVERDUE" to visibleItems.filter { (it.dueAtMillis ?: Long.MAX_VALUE) < now },
                        "TODAY" to visibleItems.filter { dateOf(it) == today &&
                            (it.dueAtMillis ?: 0L) >= now },
                        "COMING UP" to visibleItems.filter { dateOf(it)?.isAfter(today) == true },
                        "NO ALERT" to visibleItems.filter { it.dueAtMillis == null }
                    ) else listOf("" to visibleItems)
                    sections.forEach { (heading, sectionItems) ->
                        if (sectionItems.isNotEmpty()) {
                            if (heading.isNotEmpty()) {
                                Text(heading, color = muted, style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 13.dp, bottom = 11.dp))
                            }
                            sectionItems.forEach { item ->
                                key(item.id) {
                                    ReminderCard(item, now, isCompleted,
                                        dayNumber = if (!isCompleted && dateOf(item) == today)
                                            visibleItems.filter { dateOf(it) == today }.indexOf(item) + 1
                                        else null,
                                        attachments = attachments.filter { it.commitmentId == item.id && it.originType == "attachment" },
                                        onEdit = { openEditor(item) },
                                        onComplete = { onComplete(item.id) },
                                        onSnooze = { onSnooze(item.id) },
                                        onFollowUp = { days -> onFollowUp(item.id, days) },
                                        onChain = { offsets -> onChain(item.id, offsets) },
                                        onArchive = { onArchive(item.id) },
                                        onDelete = { onDelete(item.id) })
                                    Spacer(Modifier.height(10.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(108.dp))
            }
            Surface(onClick = { openEditor() },
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(horizontal = 32.dp, vertical = 16.dp)
                    .widthIn(max = 420.dp).fillMaxWidth().height(62.dp),
                shape = RoundedCornerShape(28.dp), color = cardColor,
                shadowElevation = 9.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("+  Add reminder", color = ivory, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Add, contentDescription = null, tint = violet)
                }
            }
        }
    }

    if (editorOpen) key(draftId) { ReminderEditor(editing, draftTitle, draftDetails, draftDue, draftId,
        if (upcoming) selectedDate else today, defaultTone,
        onDismiss = { editorOpen = false; if (externalDraft != null) onCaptureDismiss() }) { title, details, due, tone, attachment ->
        onSave(editing?.id, title, details, due, draftOrigin, tone, attachment)
        editorOpen = false
        query = ""
        searchOpen = false
        if (upcoming) {
            selectedDate = due?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                ?: selectedDate
            visibleMonth = YearMonth.from(selectedDate)
        } else selectedView = ReminderView.ALL
        if (externalDraft != null) onCaptureDismiss()
    } }
}

@Composable
private fun CategoryCard(view: ReminderView, count: Int, selected: Boolean, compact: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.height(if (compact) 116.dp else 126.dp),
        shape = RoundedCornerShape(24.dp), color = cardColor,
        border = BorderStroke(1.dp, if (selected) violet else MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = if (selected) 4.dp else 0.dp) {
        Column(Modifier.fillMaxSize().padding(if (compact) 12.dp else 18.dp),
            verticalArrangement = Arrangement.SpaceBetween) {
            Icon(view.icon, contentDescription = null, tint = view.tint,
                modifier = Modifier.size(if (compact) 25.dp else 28.dp))
            if (compact) {
                Text(view.label, color = ivory, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(count.toString(), color = if (selected) violet else muted,
                    fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(view.label, color = ivory, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f), maxLines = 1)
                    Text(count.toString(), color = if (selected) violet else muted,
                        fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun EmptyReminderState(text: String, onAdd: () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = cardColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = violet,
                    modifier = Modifier.padding(16.dp).size(30.dp))
            }
            Spacer(Modifier.height(17.dp))
            Text("Space for what matters", color = ivory,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(text, color = muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(15.dp))
            TextButton(onClick = onAdd) { Text("Create a reminder", color = violet) }
        }
    }
}

@Composable
private fun Notice(text: String, actionLabel: String, onAction: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(text, color = ivory, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onAction) { Text(actionLabel, color = violet) }
        }
    }
}

@Composable
private fun ReminderCard(item: CommitmentEntity, now: Long, completed: Boolean,
    dayNumber: Int?,
    attachments: List<SourceEntity>,
    onEdit: () -> Unit, onComplete: () -> Unit, onSnooze: () -> Unit,
    onFollowUp: (Long) -> Unit, onChain: (List<Long>?) -> Unit,
    onArchive: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    var deletePrompt by remember(item.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val accent = when {
        completed -> aqua
        item.dueAtMillis == null -> violet
        item.dueAtMillis < now -> coral
        else -> sky
    }
    val label = when {
        completed -> "Completed"
        item.dueAtMillis == null -> "Anytime"
        item.dueAtMillis < now -> "Overdue · ${formatTime(item.dueAtMillis)}"
        else -> formatTime(item.dueAtMillis)
    }
    Surface(shape = RoundedCornerShape(26.dp), color = cardColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 20.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onComplete, enabled = !completed, modifier = Modifier.size(36.dp)) {
                Icon(if (completed) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (completed) "Completed" else "Mark ${item.title} done",
                    tint = if (completed) aqua else muted, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)
                .clickable(enabled = !completed, onClick = onEdit)) {
                if (dayNumber != null && dayNumber > 0) Text("TODAY · $dayNumber",
                    color = accent, style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold)
                Text(item.title, color = ivory, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                item.details?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = muted, style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                attachments.forEach { file ->
                    Text("▣  ${file.title ?: "Attachment"}", color = sky,
                        style = MaterialTheme.typography.labelSmall, maxLines = 1,
                        modifier = Modifier.clickable {
                            file.originalUri?.let { uri -> runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    setDataAndType(Uri.parse(uri),
                                        context.contentResolver.getType(Uri.parse(uri)) ?: "*/*")
                                })
                            } }
                        })
                }
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (item.dueAtMillis == null) Icons.Filled.NotificationsOff
                        else Icons.Filled.Schedule, contentDescription = null,
                        tint = accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(label, color = accent, style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (item.chainEnabled && !completed) Text("Nudges · " +
                    (ChainSchedule.presets.entries.firstOrNull {
                        it.value == ChainSchedule.parse(item.chainOffsets) }?.key ?: "Custom"),
                    color = muted, style = MaterialTheme.typography.labelSmall)
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Actions for ${item.title}",
                        tint = muted)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (!completed) {
                        DropdownMenuItem(text = { Text("Edit") },
                            onClick = { menuOpen = false; onEdit() })
                        if (item.dueAtMillis != null) DropdownMenuItem(
                            text = { Text("Remind in 10 minutes") },
                            onClick = { menuOpen = false; onSnooze() })
                        if (item.chainEnabled) DropdownMenuItem(
                            text = { Text("Turn off extra nudges") },
                            onClick = { menuOpen = false; onChain(null) })
                        if (item.dueAtMillis != null && item.dueAtMillis > now) {
                            ChainSchedule.presets.forEach { (label, offsets) ->
                                DropdownMenuItem(text = { Text("Nudges · $label") },
                                    trailingIcon = { if (item.chainEnabled &&
                                        ChainSchedule.parse(item.chainOffsets) == offsets)
                                        Icon(Icons.Filled.Check, null) },
                                    onClick = { menuOpen = false; onChain(offsets) })
                            }
                        }
                        DropdownMenuItem(text = { Text("Follow up tomorrow") },
                            onClick = { menuOpen = false; onFollowUp(1) })
                        DropdownMenuItem(text = { Text("Follow up next week") },
                            onClick = { menuOpen = false; onFollowUp(7) })
                        DropdownMenuItem(text = { Text("Archive") },
                            onClick = { menuOpen = false; onArchive() })
                        HorizontalDivider()
                    }
                    if (completed) {
                        DropdownMenuItem(text = { Text("Follow up tomorrow") },
                            onClick = { menuOpen = false; onFollowUp(1) })
                        DropdownMenuItem(text = { Text("Follow up next week") },
                            onClick = { menuOpen = false; onFollowUp(7) })
                    }
                    DropdownMenuItem(
                        text = { Text("Delete", color = coral) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = coral) },
                        onClick = { menuOpen = false; deletePrompt = true })
                }
            }
        }
    }
    if (deletePrompt) AlertDialog(
        onDismissRequest = { deletePrompt = false },
        title = { Text("Delete this reminder?") },
        text = { Text("It will disappear from your reminder lists. Its history stays in Inbox.") },
        confirmButton = {
            TextButton(onClick = { deletePrompt = false; onDelete() }) {
                Text("Delete", color = coral)
            }
        },
        dismissButton = {
            TextButton(onClick = { deletePrompt = false }) { Text("Cancel") }
        },
        containerColor = cardColor, titleContentColor = ivory, textContentColor = muted
    )
}

@Composable
private fun CalendarGrid(month: YearMonth, selectedDate: LocalDate,
    counts: Map<LocalDate, Int>, onPrevious: () -> Unit, onNext: () -> Unit,
    onDate: (LocalDate) -> Unit, earliest: LocalDate? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            color = ivory, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f))
        IconButton(onClick = onPrevious) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month", tint = ivory)
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next month", tint = ivory)
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth()) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
            Box(Modifier.weight(1f).height(30.dp), contentAlignment = Alignment.Center) {
                Text(day, color = muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    val offset = month.atDay(1).dayOfWeek.value - 1
    val totalCells = ((offset + month.lengthOfMonth() + 6) / 7) * 7
    repeat(totalCells / 7) { week ->
        Row(Modifier.fillMaxWidth()) {
            repeat(7) { dayOfWeek ->
                val number = week * 7 + dayOfWeek - offset + 1
                val date = if (number in 1..month.lengthOfMonth()) month.atDay(number) else null
                val enabled = date != null && (earliest == null || !date.isBefore(earliest))
                val chosen = date == selectedDate
                Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp)
                    .background(if (chosen) violet else Color.Transparent, RoundedCornerShape(14.dp))
                    .clickable(enabled = enabled) { date?.let(onDate) },
                    contentAlignment = Alignment.Center) {
                    if (date != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$number", color = when {
                            !enabled -> muted.copy(alpha = .35f)
                            chosen -> MaterialTheme.colorScheme.onPrimary
                            else -> ivory
                        }, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal)
                        if ((counts[date] ?: 0) > 0) {
                            Spacer(Modifier.height(2.dp))
                            Box(Modifier.size(4.dp).background(
                                if (chosen) MaterialTheme.colorScheme.onPrimary else coral, CircleShape))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderEditor(item: CommitmentEntity?, suggestedTitle: String, suggestedDetails: String,
    suggestedDue: Long?,
    draftId: String, initialDate: LocalDate, defaultTone: String,
    onDismiss: () -> Unit, onSave: suspend (String, String?, Long?, String, CaptureOrigin?) -> Unit) {
    val context = LocalContext.current
    var attachment by remember(item?.id, draftId) { mutableStateOf<CaptureOrigin?>(null) }
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null }
            }.getOrNull() ?: uri.lastPathSegment ?: "File"
            attachment = CaptureOrigin("attachment", name, uri = uri.toString(), key = uri.toString())
        }
    }
    val initialDue = (item?.dueAtMillis ?: suggestedDue)?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
    }
    val start = remember { LocalDateTime.now().plusMinutes(10) }
    var title by remember(item?.id, draftId) { mutableStateOf(item?.title ?: suggestedTitle) }
    var details by remember(item?.id, draftId) { mutableStateOf(item?.details ?: suggestedDetails) }
    var notesOpen by remember(item?.id, draftId) { mutableStateOf(details.isNotBlank()) }
    var tone by remember(item?.id, draftId) { mutableStateOf(item?.toneId ?: defaultTone) }
    var alert by remember(item?.id, draftId) { mutableStateOf(initialDue != null || item == null) }
    var date by remember(item?.id, draftId) {
        mutableStateOf(initialDue?.toLocalDate() ?: maxOf(initialDate, start.toLocalDate()))
    }
    var hour by remember(item?.id, draftId) { mutableIntStateOf(initialDue?.hour ?: start.hour) }
    var minute by remember(item?.id, draftId) { mutableIntStateOf(initialDue?.minute ?: start.minute) }
    var month by remember(item?.id, draftId) { mutableStateOf(YearMonth.from(date)) }
    var dateDialog by remember { mutableStateOf(false) }
    var timeDialog by remember { mutableStateOf(false) }
    var toneDialog by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val due = if (alert) date.atTime(hour, minute).atZone(ZoneId.systemDefault())
        .toInstant().toEpochMilli() else null
    val valid = title.isNotBlank() && (due == null || due > System.currentTimeMillis())
    val colors = MaterialTheme.colorScheme

    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.background, contentColor = colors.onBackground,
        dragHandle = { Surface(shape = CircleShape, color = colors.outlineVariant,
            modifier = Modifier.padding(top = 12.dp).size(width = 38.dp, height = 4.dp)) {} }) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (item == null) "New reminder" else "Edit reminder",
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("One thought, one clear alert.", color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = onDismiss, enabled = !saving) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(10.dp))
                Surface(shape = RoundedCornerShape(26.dp), color = colors.surface,
                    border = BorderStroke(1.dp, colors.outlineVariant)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(13.dp), color = colors.primaryContainer) {
                                Icon(Icons.Filled.Edit, contentDescription = null, tint = colors.primary,
                                    modifier = Modifier.padding(11.dp).size(21.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text("What should I remind you?", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(value = title, onValueChange = { if (it.length <= 160) title = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            placeholder = { Text("e.g. Pay electricity bill") },
                            label = { Text("Reminder") }, shape = RoundedCornerShape(16.dp))
                        if (notesOpen) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(value = details, onValueChange = { details = it },
                                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
                                label = { Text("Notes (optional)") },
                                shape = RoundedCornerShape(16.dp))
                        } else TextButton(onClick = { notesOpen = true }) {
                            Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Add a note")
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Surface(shape = RoundedCornerShape(22.dp), color = colors.surface,
                    border = BorderStroke(1.dp, colors.outlineVariant)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Image or document", style = MaterialTheme.typography.titleMedium)
                        Text(attachment?.title ?: "Keep a file with this reminder",
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        TextButton(onClick = { attachmentPicker.launch(arrayOf("*/*")) }) {
                            Text(if (attachment == null) "Add file" else "Change file")
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Surface(shape = RoundedCornerShape(26.dp), color = colors.surface,
                    border = BorderStroke(1.dp, colors.outlineVariant)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(13.dp), color = colors.primaryContainer) {
                                Icon(Icons.Filled.NotificationsActive, contentDescription = null,
                                    tint = colors.primary, modifier = Modifier.padding(11.dp).size(21.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Alert", style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text(if (alert) "Cue will ring at your chosen time" else "Save without ringing",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant)
                            }
                            Switch(checked = alert, onCheckedChange = { alert = it })
                        }
                        if (alert) {
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = date == LocalDate.now(),
                                    onClick = { date = LocalDate.now(); month = YearMonth.from(date) },
                                    label = { Text("Today") })
                                FilterChip(selected = date == LocalDate.now().plusDays(1),
                                    onClick = {
                                        date = LocalDate.now().plusDays(1)
                                        month = YearMonth.from(date)
                                    }, label = { Text("Tomorrow") })
                                FilterChip(selected = date != LocalDate.now() &&
                                    date != LocalDate.now().plusDays(1),
                                    onClick = { dateDialog = true; focus.clearFocus(); keyboard?.hide() },
                                    label = { Text("Pick date") })
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(onClick = {
                                    dateDialog = true; focus.clearFocus(); keyboard?.hide()
                                }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(17.dp),
                                    color = colors.primaryContainer.copy(alpha = .38f)) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text("DATE", style = MaterialTheme.typography.labelSmall,
                                            color = colors.onSurfaceVariant)
                                        Spacer(Modifier.height(4.dp))
                                        Text(date.format(DateTimeFormatter.ofPattern("d MMM, EEE")),
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1, color = colors.onSurface)
                                    }
                                }
                                Surface(onClick = {
                                    timeDialog = true; focus.clearFocus(); keyboard?.hide()
                                }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(17.dp),
                                    color = colors.primaryContainer.copy(alpha = .38f)) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text("TIME", style = MaterialTheme.typography.labelSmall,
                                            color = colors.onSurfaceVariant)
                                        Spacer(Modifier.height(4.dp))
                                        Text(LocalTime.of(hour, minute)
                                            .format(DateTimeFormatter.ofPattern("h:mm a")),
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1, color = colors.onSurface)
                                    }
                                }
                            }
                            if (due != null && due <= System.currentTimeMillis()) {
                                Spacer(Modifier.height(9.dp))
                                Text("Choose a future time to turn on the alert.", color = colors.error,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(14.dp))
                            Surface(onClick = { toneDialog = true; focus.clearFocus(); keyboard?.hide() },
                                shape = RoundedCornerShape(16.dp),
                                color = colors.surfaceVariant.copy(alpha = .65f)) {
                                Row(Modifier.fillMaxWidth().padding(13.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.MusicNote, null, tint = colors.primary,
                                        modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text("Sound", style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f))
                                    Text(ReminderTones.selected(tone).label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant)
                                    Icon(Icons.Filled.ChevronRight, "Choose sound",
                                        tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            Surface(color = colors.background, shadowElevation = 10.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    saveError?.let { Text(it, color = colors.error,
                        style = MaterialTheme.typography.bodySmall) }
                    Button(onClick = {
                        if (!saving) scope.launch {
                            saving = true; saveError = null
                            try { onSave(title.trim(), details.trim().ifBlank { null }, due, tone, attachment) }
                            catch (e: Exception) {
                                saveError = e.message ?: "Could not save. Please try again."
                            } finally { saving = false }
                        }
                    }, enabled = valid && !saving, modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(18.dp)) {
                        Icon(Icons.Filled.Check, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (saving) "Saving…" else if (item == null) "Save reminder" else "Save changes")
                    }
                }
            }
        }
    }
    if (toneDialog) ReminderToneChoices(tone, onSelect = { tone = it },
        onDismiss = { toneDialog = false })
    if (dateDialog) AlertDialog(onDismissRequest = { dateDialog = false },
        title = { Text("Choose a date") },
        text = { Column(Modifier.fillMaxWidth()) { CalendarGrid(month, date, emptyMap(),
            onPrevious = { month = month.minusMonths(1) },
            onNext = { month = month.plusMonths(1) },
            onDate = { date = it; dateDialog = false }, earliest = LocalDate.now()) } },
        confirmButton = { TextButton(onClick = { dateDialog = false }) { Text("Done") } })
    if (timeDialog) {
        var mode by remember { mutableStateOf("input") }
        val picker = rememberTimePickerState(initialHour = hour, initialMinute = minute,
            is24Hour = false)
        AlertDialog(onDismissRequest = { timeDialog = false },
            title = { Text("Choose a time") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row {
                        TextButton(onClick = { mode = "input" }) { Text("Type") }
                        TextButton(onClick = { mode = "dial" }) { Text("Dial") }
                    }
                    if (mode == "input") TimeInput(state = picker) else TimePicker(state = picker)
                    Text("You can type the numbers or use the dial.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = {
                hour = picker.hour; minute = picker.minute; timeDialog = false
            }) { Text("Set time") } },
            dismissButton = { TextButton(onClick = { timeDialog = false }) { Text("Cancel") } })
    }
}

private fun formatTime(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault()).format(timeFormat)
