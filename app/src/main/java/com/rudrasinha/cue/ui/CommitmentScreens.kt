package com.rudrasinha.cue.ui

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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rudrasinha.cue.data.CommitmentEntity
import com.rudrasinha.cue.data.CaptureOrigin
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
    defaultTone: String, onSave: suspend (String?, String, String?, Long?, CaptureOrigin, String) -> Unit,
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
                    TextButton(onClick = { selectedDate = today; visibleMonth = YearMonth.from(today) }) {
                        Text("Today", color = violet)
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
                    TextButton(onClick = onDayPlan,
                        modifier = Modifier.align(Alignment.End)) {
                        Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("View my day")
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
        onDismiss = { editorOpen = false; if (externalDraft != null) onCaptureDismiss() }) { title, details, due, tone ->
        onSave(editing?.id, title, details, due, draftOrigin, tone)
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
    onEdit: () -> Unit, onComplete: () -> Unit, onSnooze: () -> Unit,
    onFollowUp: (Long) -> Unit, onChain: (List<Long>?) -> Unit,
    onArchive: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    var deletePrompt by remember(item.id) { mutableStateOf(false) }
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
                Text(item.title, color = ivory, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                item.details?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = muted, style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    onDismiss: () -> Unit, onSave: suspend (String, String?, Long?, String) -> Unit) {
    val initialDue = (item?.dueAtMillis ?: suggestedDue)?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
    }
    val suggestedStart = remember { LocalDateTime.now().plusMinutes(10) }
    val startDate = maxOf(initialDate, suggestedStart.toLocalDate())
    var title by remember(item?.id, draftId) { mutableStateOf(item?.title ?: suggestedTitle) }
    var details by remember(item?.id, draftId) { mutableStateOf(item?.details ?: suggestedDetails) }
    var chosenTone by remember(item?.id, draftId) { mutableStateOf(item?.toneId ?: defaultTone) }
    var tonePickerOpen by remember { mutableStateOf(false) }
    var alertEnabled by remember(item?.id, draftId) { mutableStateOf(initialDue != null || item == null) }
    var chosenDate by remember(item?.id) { mutableStateOf(initialDue?.toLocalDate() ?: startDate) }
    var month by remember(item?.id) { mutableStateOf(YearMonth.from(chosenDate)) }
    var showCalendar by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var timeMode by remember { mutableStateOf("input") }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val saveScope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val initialTime = initialDue?.toLocalTime() ?: suggestedStart.toLocalTime()
    val timeState = rememberTimePickerState(initialHour = initialTime.hour,
        initialMinute = initialTime.minute, is24Hour = false)
    val due = if (alertEnabled) chosenDate.atTime(timeState.hour, timeState.minute)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() else null
    val valid = title.isNotBlank() && (due == null || due > System.currentTimeMillis())

    run {
        ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = cardColor, contentColor = ivory,
            dragHandle = { Surface(shape = CircleShape, color = muted,
                modifier = Modifier.padding(top = 12.dp).size(width = 38.dp, height = 4.dp)) {} }) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 18.dp)) {
                Spacer(Modifier.height(10.dp))
                Text(if (item == null) "NEW REMINDER" else "EDIT REMINDER",
                    color = violet, style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(value = title, onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Reminder") },
                    placeholder = { Text("What should Cue remind you about?") },
                    singleLine = true, shape = RoundedCornerShape(16.dp))
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(value = details, onValueChange = { details = it },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Details (optional)") },
                    placeholder = { Text("Add a note or context") }, maxLines = 3,
                    shape = RoundedCornerShape(16.dp))
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Set an alert", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = alertEnabled, onCheckedChange = { alertEnabled = it })
                }
                if (alertEnabled) {
                    Spacer(Modifier.height(12.dp))
                    Text("Date", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Surface(onClick = {
                        showCalendar = !showCalendar; showTime = false
                        focusManager.clearFocus(); keyboard?.hide()
                    }, shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = null,
                                tint = violet, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(chosenDate.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")),
                                color = ivory, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Change date",
                                tint = muted, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Time", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Surface(onClick = {
                        showTime = !showTime; showCalendar = false
                        focusManager.clearFocus(); keyboard?.hide()
                    }, shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Schedule, contentDescription = null,
                                tint = violet, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(LocalTime.of(timeState.hour, timeState.minute)
                                .format(DateTimeFormatter.ofPattern("h:mm a")),
                                modifier = Modifier.weight(1f), color = ivory)
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Change time",
                                tint = muted, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Ringtone", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Surface(onClick = { tonePickerOpen = true; focusManager.clearFocus(); keyboard?.hide() },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(top = 9.dp)) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = violet,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(ReminderTones.selected(chosenTone).label, color = ivory,
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Choose reminder sound",
                                tint = muted, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (tonePickerOpen) ReminderToneChoices(chosenTone, onSelect = { chosenTone = it },
                        onDismiss = { tonePickerOpen = false })
                    if (showCalendar) {
                        Spacer(Modifier.height(14.dp))
                        CalendarGrid(month, chosenDate, emptyMap(),
                            onPrevious = { month = month.minusMonths(1) },
                            onNext = { month = month.plusMonths(1) },
                            onDate = { chosenDate = it; showCalendar = false },
                            earliest = LocalDate.now())
                    }
                    if (showTime) {
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            TextButton(onClick = { timeMode = "input" }) {
                                Text("Type time", color = if (timeMode == "input") violet else muted)
                            }
                            TextButton(onClick = {
                                timeMode = "dial"; focusManager.clearFocus(); keyboard?.hide()
                            }) {
                                Text("Use dial", color = if (timeMode == "dial") violet else muted)
                            }
                        }
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (timeMode == "input") TimeInput(state = timeState)
                            else TimePicker(state = timeState)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                val total = (timeState.hour * 60 + timeState.minute - 5 + 1440) % 1440
                                timeState.hour = total / 60; timeState.minute = total % 60
                            }) { Text("− 5 min") }
                            Text(LocalTime.of(timeState.hour, timeState.minute)
                                .format(DateTimeFormatter.ofPattern("h:mm a")),
                                modifier = Modifier.padding(horizontal = 8.dp)
                                    .pointerInput(timeState) {
                                        var distance = 0f
                                        detectVerticalDragGestures(
                                            onDragEnd = { distance = 0f },
                                            onVerticalDrag = { change, amount ->
                                                change.consume()
                                                distance += amount
                                                if (kotlin.math.abs(distance) >= 24f) {
                                                    val delta = if (distance < 0f) 5 else -5
                                                    val total = (timeState.hour * 60 +
                                                        timeState.minute + delta + 1440) % 1440
                                                    timeState.hour = total / 60
                                                    timeState.minute = total % 60
                                                    distance = 0f
                                                }
                                            })
                                    })
                            TextButton(onClick = {
                                val total = (timeState.hour * 60 + timeState.minute + 5) % 1440
                                timeState.hour = total / 60; timeState.minute = total % 60
                            }) { Text("+ 5 min") }
                        }
                        Text("Swipe the time up or down to adjust it.", color = muted,
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { showTime = false },
                            modifier = Modifier.align(Alignment.End)) { Text("Done") }
                    }
                    if (due != null && due <= System.currentTimeMillis()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Choose a future date and time.", color = coral,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(24.dp))
                saveError?.let { Text(it, color = coral, style = MaterialTheme.typography.bodySmall) }
                Button(onClick = {
                    if (!saving) saveScope.launch {
                        saving = true
                        saveError = null
                        try { onSave(title.trim(), details.trim().ifBlank { null }, due, chosenTone) }
                        catch (e: Exception) {
                            saveError = e.message ?: "Could not save. Please try again."
                        } finally { saving = false }
                    }
                }, enabled = valid && !saving, modifier = Modifier.fillMaxWidth()) {
                    Text(if (saving) "Saving…" else if (item == null) "Save reminder" else "Save changes")
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

private fun formatTime(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault()).format(timeFormat)
