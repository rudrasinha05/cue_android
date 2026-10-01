package com.rudrasinha.cue.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rudrasinha.cue.data.CommitmentEntity
import com.rudrasinha.cue.data.CaptureOrigin
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val canvasTop = Color(0xFF101017)
private val canvasMiddle = Color(0xFF29243A)
private val cardColor = Color(0xFF19191D)
private val ivory = Color(0xFFF8F6FF)
private val muted = Color(0xFFADA9BA)
private val violet = Color(0xFFB69CFF)
private val coral = Color(0xFFFF858A)
private val sky = Color(0xFF72B8FF)
private val amber = Color(0xFFFFC76B)
private val aqua = Color(0xFF62D0D1)
private val timeFormat = DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a")
private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM")

private enum class ReminderView(val label: String, val icon: ImageVector, val tint: Color) {
    TODAY("Today", Icons.Filled.CalendarMonth, coral),
    SCHEDULED("Scheduled", Icons.Filled.Schedule, sky),
    PAST("Past", Icons.Filled.History, amber),
    NO_ALERT("No alert", Icons.Filled.NotificationsOff, violet),
    COMPLETED("Completed", Icons.Filled.CheckCircle, muted),
    ALL("All", Icons.Filled.ViewAgenda, aqua)
}

data class CaptureDraft(val id: String, val text: String, val origin: CaptureOrigin,
    val suggestedDueAtMillis: Long? = null)

@Composable
fun CommitmentListScreen(
    active: List<CommitmentEntity>, completed: List<CommitmentEntity>, upcoming: Boolean,
    message: String?, exactAvailable: Boolean, notificationsAllowed: Boolean,
    onExactAccess: () -> Unit, onNotificationAccess: () -> Unit,
    onSave: (String?, String, String?, Long?, CaptureOrigin) -> Unit,
    onComplete: (String) -> Unit, onSnooze: (String) -> Unit, onArchive: (String) -> Unit,
    onDelete: (String) -> Unit,
    signedIn: Boolean, onSync: () -> Unit, onSettings: () -> Unit,
    externalDraft: CaptureDraft?, onCaptureDismiss: () -> Unit,
    focusTodayToken: Int = 0,
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

    MaterialTheme(colorScheme = darkColorScheme(primary = violet, background = canvasTop,
        surface = cardColor, onSurface = ivory, onSurfaceVariant = muted)) {
        Box(modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(canvasTop, Color(0xFF1A1926), canvasTop)))) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)) {
                Spacer(Modifier.height(24.dp))
                Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")).uppercase(),
                    color = violet, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (upcoming) "Your calendar" else "Your reminders",
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
                Spacer(Modifier.height(24.dp))
                if (upcoming) {
                    Surface(shape = RoundedCornerShape(28.dp), color = cardColor,
                        border = BorderStroke(1.dp, Color(0xFF343140))) {
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
                    SummaryHero(active, today, now, onAdd = { openEditor() })
                    Spacer(Modifier.height(28.dp))
                    Text("EXPLORE", color = muted, style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (listOf(ReminderView.ALL) + ReminderView.entries.filter { it != ReminderView.ALL })
                            .forEach { view ->
                                FilterPill(view, matching(view).size, selectedView == view) {
                                    selectedView = view
                                }
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
                Spacer(Modifier.height(26.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (upcoming) selectedDate.format(dayFormat)
                            else if (selectedView == ReminderView.ALL) "Coming into focus"
                            else selectedView.label,
                            color = ivory, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold)
                        Text(if (upcoming) "Your plan for this day"
                            else if (isCompleted) "The things you finished"
                            else "One thing at a time",
                            color = muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Surface(shape = CircleShape, color = Color(0xFF343047)) {
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
                        "PAST" to visibleItems.filter { (it.dueAtMillis ?: Long.MAX_VALUE) < now },
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
            Button(onClick = { openEditor() },
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = 22.dp, bottom = 20.dp).height(58.dp),
                shape = RoundedCornerShape(21.dp),
                colors = ButtonDefaults.buttonColors(containerColor = violet, contentColor = canvasTop),
                contentPadding = PaddingValues(horizontal = 22.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Add reminder", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (editorOpen) key(draftId) { ReminderEditor(editing, draftTitle, draftDetails, draftDue, draftId,
        if (upcoming) selectedDate else today,
        onDismiss = { editorOpen = false; if (externalDraft != null) onCaptureDismiss() }) { title, details, due ->
        onSave(editing?.id, title, details, due, draftOrigin)
        editorOpen = false
        if (externalDraft != null) onCaptureDismiss()
    } }
}

@Composable
private fun SummaryHero(active: List<CommitmentEntity>, today: LocalDate, now: Long,
    onAdd: () -> Unit) {
    val todayCount = active.count { item ->
        item.dueAtMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() } == today
    }
    val next = active.filter { (it.dueAtMillis ?: 0L) > now }
        .minByOrNull { it.dueAtMillis ?: Long.MAX_VALUE }
    Box(Modifier.fillMaxWidth().height(184.dp).clip(RoundedCornerShape(30.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF493D77), Color(0xFF25243D))))
        .border(1.dp, Color(0xFF7468A1), RoundedCornerShape(30.dp))) {
        Box(Modifier.align(Alignment.TopEnd).offset(x = 56.dp, y = (-76).dp)
            .size(220.dp).background(ivory.copy(alpha = .055f), CircleShape))
        Box(Modifier.align(Alignment.BottomEnd).offset(x = 30.dp, y = 87.dp)
            .size(166.dp).background(ivory.copy(alpha = .045f), CircleShape))
        Column(Modifier.fillMaxSize().padding(23.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color(0xFFE2D7FF),
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("TODAY'S FOCUS", color = Color(0xFFE2D7FF),
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("${todayCount}", color = ivory,
                        style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    Text(if (todayCount == 1) "reminder for today" else "reminders for today",
                        color = Color(0xFFE1DDEE), style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = onAdd,
                    modifier = Modifier.size(48.dp).background(ivory, CircleShape)) {
                    Icon(Icons.Filled.Add, contentDescription = "Add reminder", tint = Color(0xFF302A49))
                }
            }
            Text(next?.let { "NEXT  ·  ${it.title.take(35)}" } ?: "THE DAY IS YOURS",
                color = Color(0xFFE2D7FF), style = MaterialTheme.typography.labelSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FilterPill(view: ReminderView, count: Int, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp),
        color = if (selected) violet else cardColor,
        border = BorderStroke(1.dp, if (selected) violet else Color(0xFF373541))) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(view.icon, contentDescription = null,
                tint = if (selected) canvasTop else view.tint, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text(view.label, color = if (selected) canvasTop else ivory,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            Spacer(Modifier.width(8.dp))
            Text(count.toString(), color = if (selected) canvasTop else muted,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun EmptyReminderState(text: String, onAdd: () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = cardColor,
        border = BorderStroke(1.dp, Color(0xFF35333F))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = Color(0xFF363047)) {
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
    Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF383049),
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
        item.dueAtMillis < now -> "Past · ${formatTime(item.dueAtMillis)}"
        else -> formatTime(item.dueAtMillis)
    }
    Surface(shape = RoundedCornerShape(24.dp), color = cardColor,
        border = BorderStroke(1.dp, Color(0xFF36333F))) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(54.dp)
                .background(accent, RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(13.dp))
            IconButton(onClick = onComplete, enabled = !completed, modifier = Modifier.size(36.dp)) {
                Icon(if (completed) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (completed) "Completed" else "Mark ${item.title} done",
                    tint = if (completed) aqua else muted, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f).padding(start = 8.dp)
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
                        DropdownMenuItem(text = { Text("Archive") },
                            onClick = { menuOpen = false; onArchive() })
                        HorizontalDivider()
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
                            chosen -> canvasTop
                            else -> ivory
                        }, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal)
                        if ((counts[date] ?: 0) > 0) {
                            Spacer(Modifier.height(2.dp))
                            Box(Modifier.size(4.dp).background(if (chosen) canvasTop else coral, CircleShape))
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
    draftId: String, initialDate: LocalDate,
    onDismiss: () -> Unit, onSave: (String, String?, Long?) -> Unit) {
    val initialDue = (item?.dueAtMillis ?: suggestedDue)?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
    }
    val suggestedStart = remember { LocalDateTime.now().plusMinutes(10) }
    val startDate = maxOf(initialDate, suggestedStart.toLocalDate())
    var title by remember(item?.id, draftId) { mutableStateOf(item?.title ?: suggestedTitle) }
    var details by remember(item?.id, draftId) { mutableStateOf(item?.details ?: suggestedDetails) }
    var alertEnabled by remember(item?.id) { mutableStateOf(initialDue != null) }
    var chosenDate by remember(item?.id) { mutableStateOf(initialDue?.toLocalDate() ?: startDate) }
    var month by remember(item?.id) { mutableStateOf(YearMonth.from(chosenDate)) }
    var showNote by remember(item?.id, draftId) { mutableStateOf(details.isNotBlank()) }
    var showCalendar by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val initialTime = initialDue?.toLocalTime() ?: suggestedStart.toLocalTime()
    val timeState = rememberTimePickerState(initialHour = initialTime.hour,
        initialMinute = initialTime.minute, is24Hour = false)
    val due = if (alertEnabled) chosenDate.atTime(timeState.hour, timeState.minute)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() else null
    val valid = title.isNotBlank() && (due == null || due > System.currentTimeMillis())

    MaterialTheme(colorScheme = darkColorScheme(primary = violet, surface = Color(0xFF20212A),
        onSurface = ivory, onSurfaceVariant = muted, outline = muted)) {
        ModalBottomSheet(onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF20212A), contentColor = ivory,
            dragHandle = { Surface(shape = CircleShape, color = Color(0xFF6A6078),
                modifier = Modifier.padding(top = 12.dp).size(width = 38.dp, height = 4.dp)) {} }) {
            LaunchedEffect(Unit) { titleFocus.requestFocus(); keyboard?.show() }
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 18.dp)) {
                Spacer(Modifier.height(10.dp))
                Text(if (item == null) "NEW REMINDER" else "EDIT REMINDER",
                    color = violet, style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(value = title, onValueChange = { title = it },
                        modifier = Modifier.weight(1f).focusRequester(titleFocus),
                        singleLine = true, cursorBrush = SolidColor(violet),
                        textStyle = MaterialTheme.typography.titleLarge.copy(color = ivory),
                        decorationBox = { inner ->
                            Box {
                                if (title.isEmpty()) Text("What do you want to remember?", color = muted,
                                    style = MaterialTheme.typography.titleLarge)
                                inner()
                            }
                        })
                    Spacer(Modifier.width(14.dp))
                    IconButton(onClick = { onSave(title.trim(), details.trim().ifBlank { null }, due) },
                        enabled = valid,
                        modifier = Modifier.size(52.dp).background(
                            if (valid) violet else Color(0xFF465062), CircleShape)) {
                        Icon(Icons.Filled.Check, contentDescription = "Save reminder",
                            tint = if (valid) canvasTop else muted, modifier = Modifier.size(28.dp))
                    }
                }
                if (showNote) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(value = details, onValueChange = { details = it },
                        label = { Text("Note") }, placeholder = { Text("Add details") },
                        modifier = Modifier.fillMaxWidth(), maxLines = 4,
                        shape = RoundedCornerShape(16.dp))
                }
                if (alertEnabled) {
                    Spacer(Modifier.height(12.dp))
                    Surface(onClick = {
                        showCalendar = !showCalendar; showTime = false
                        focusManager.clearFocus(); keyboard?.hide()
                    }, shape = RoundedCornerShape(16.dp), color = Color(0xFF353145),
                        modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null,
                                tint = violet, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(chosenDate.format(DateTimeFormatter.ofPattern("EEE, d MMM")) + " · " +
                                LocalTime.of(timeState.hour, timeState.minute)
                                    .format(DateTimeFormatter.ofPattern("h:mm a")),
                                color = ivory, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Change alert",
                                tint = muted, modifier = Modifier.size(18.dp))
                        }
                    }
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
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TimePicker(state = timeState)
                        }
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    QuickAction(Icons.Outlined.Notes, "Add note", showNote) { showNote = !showNote }
                    QuickAction(Icons.Filled.CalendarMonth, "Choose date", showCalendar) {
                        alertEnabled = true; showCalendar = !showCalendar; showTime = false
                        focusManager.clearFocus(); keyboard?.hide()
                    }
                    QuickAction(Icons.Filled.Schedule, "Choose time", showTime) {
                        alertEnabled = true; showTime = !showTime; showCalendar = false
                        focusManager.clearFocus(); keyboard?.hide()
                    }
                    QuickAction(Icons.Filled.NotificationsOff, "Toggle alert", !alertEnabled) {
                        alertEnabled = !alertEnabled; showCalendar = false; showTime = false
                    }
                    QuickAction(Icons.Filled.FormatListBulleted, "Add bulleted note", false) {
                        details += if (details.isBlank()) "• " else "\n• "
                        showNote = true
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, description: String, selected: Boolean,
    onClick: () -> Unit) {
    val label = when (description) {
        "Add note" -> "Note"
        "Choose date" -> "Date"
        "Choose time" -> "Time"
        "Toggle alert" -> "Alert"
        else -> "List"
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(59.dp).clickable(onClick = onClick)) {
        Surface(shape = RoundedCornerShape(16.dp), color =
            if (selected) Color(0xFF49405F) else Color(0xFF30303A),
            border = if (selected) BorderStroke(1.dp, violet) else null) {
            Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = description,
                    tint = if (selected) violet else ivory, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(label, color = if (selected) violet else muted,
            style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatTime(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault()).format(timeFormat)
