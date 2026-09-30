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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Search
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
import com.rudrasinha.cue.data.CommitmentEntity
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

@Composable
fun CommitmentListScreen(
    active: List<CommitmentEntity>, completed: List<CommitmentEntity>, upcoming: Boolean,
    message: String?, exactAvailable: Boolean, notificationsAllowed: Boolean,
    onExactAccess: () -> Unit, onNotificationAccess: () -> Unit,
    onSave: (String?, String, String?, Long?) -> Unit,
    onComplete: (String) -> Unit, onSnooze: (String) -> Unit, onArchive: (String) -> Unit,
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
    var editorOpen by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val now = System.currentTimeMillis()
    fun dateOf(item: CommitmentEntity): LocalDate? = item.dueAtMillis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
    }
    fun openEditor(item: CommitmentEntity? = null, suggestedTitle: String = "") {
        editing = item
        draftTitle = suggestedTitle
        editorOpen = true
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
            listOf(canvasTop, canvasMiddle, canvasTop)))) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (upcoming) "Calendar" else "Reminder", color = ivory,
                        style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" }) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search reminders", tint = ivory)
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Sort reminders", tint = ivory)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Sort by time") },
                                onClick = { sortByTitle = false; menuOpen = false })
                            DropdownMenuItem(text = { Text("Sort by title") },
                                onClick = { sortByTitle = true; menuOpen = false })
                        }
                    }
                }
                if (searchOpen) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = query, onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Search reminders") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ivory, unfocusedTextColor = ivory,
                            focusedLabelColor = violet, unfocusedLabelColor = muted,
                            focusedBorderColor = violet, unfocusedBorderColor = muted))
                }
                Spacer(Modifier.height(22.dp))

                if (upcoming) {
                    Surface(shape = RoundedCornerShape(28.dp), color = cardColor) {
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
                    Spacer(Modifier.height(14.dp))
                    TextButton(onClick = { selectedDate = today; visibleMonth = YearMonth.from(today) }) {
                        Text("Jump to today", color = violet)
                    }
                } else {
                    ReminderView.entries.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { view ->
                                CategoryTile(view, matching(view).size,
                                    selected = selectedView == view,
                                    modifier = Modifier.weight(1f)) { selectedView = view }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
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

                Spacer(Modifier.height(if (upcoming) 12.dp else 18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (upcoming) selectedDate.format(dayFormat)
                        else if (selectedView == ReminderView.ALL) "Your reminders" else selectedView.label,
                        color = ivory, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${visibleItems.size}", color = muted, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
                if (visibleItems.isEmpty()) {
                    EmptyReminderState(
                        when {
                            query.isNotBlank() -> "No reminders match your search."
                            upcoming -> "Nothing planned for this day."
                            isCompleted -> "Completed reminders will appear here."
                            selectedView != ReminderView.ALL -> "Nothing here yet."
                            else -> "A fresh start. Add something you want to remember."
                        })
                } else {
                    val sections = if (!upcoming && selectedView == ReminderView.ALL) listOf(
                        "Past" to visibleItems.filter { (it.dueAtMillis ?: Long.MAX_VALUE) < now },
                        "Today" to visibleItems.filter { dateOf(it) == today &&
                            (it.dueAtMillis ?: 0L) >= now },
                        "Coming up" to visibleItems.filter { dateOf(it)?.isAfter(today) == true },
                        "No alert" to visibleItems.filter { it.dueAtMillis == null }
                    ) else listOf("" to visibleItems)
                    sections.forEach { (heading, items) ->
                        if (items.isNotEmpty()) {
                            if (heading.isNotEmpty()) {
                                Text(heading, color = muted, style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 10.dp))
                            }
                            items.forEach { item ->
                                key(item.id) {
                                    ReminderCard(item, now, isCompleted, onEdit = { openEditor(item) },
                                        onComplete = { onComplete(item.id) }, onSnooze = { onSnooze(item.id) },
                                        onArchive = { onArchive(item.id) })
                                    Spacer(Modifier.height(12.dp))
                                }
                            }
                        }
                    }
                }

                if (!upcoming && active.isEmpty() && query.isBlank() && selectedView == ReminderView.ALL) {
                    Spacer(Modifier.height(24.dp))
                    Text("A few ideas", color = muted, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    listOf("Call home", "Pick up groceries", "Plan my week").forEach { idea ->
                        Surface(onClick = { openEditor(suggestedTitle = idea) },
                            shape = RoundedCornerShape(22.dp), color = cardColor,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = amber)
                                Spacer(Modifier.width(16.dp))
                                Text(idea, color = ivory, modifier = Modifier.weight(1f))
                                Icon(Icons.Filled.Add, contentDescription = null, tint = muted)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(104.dp))
            }
            Button(onClick = { openEditor() },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF373142), contentColor = ivory),
                border = BorderStroke(1.dp, Color(0xFF5B526A)),
                contentPadding = PaddingValues(horizontal = 26.dp, vertical = 16.dp)) {
                Text("Add reminder", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(30.dp))
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        }
    }

    if (editorOpen) ReminderEditor(editing, draftTitle,
        if (upcoming) selectedDate else today,
        onDismiss = { editorOpen = false }) { title, details, due ->
        onSave(editing?.id, title, details, due)
        editorOpen = false
    }
}

@Composable
private fun CategoryTile(view: ReminderView, count: Int, selected: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.height(112.dp).border(1.dp,
        if (selected) view.tint.copy(alpha = .75f) else Color.Transparent,
        RoundedCornerShape(26.dp)).background(cardColor, RoundedCornerShape(26.dp))
        .clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween) {
        Icon(view.icon, contentDescription = null, tint = view.tint, modifier = Modifier.size(27.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(view.label, color = if (selected) ivory else muted,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            Text("$count", color = ivory, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyReminderState(text: String) {
    Surface(shape = RoundedCornerShape(24.dp), color = cardColor) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = violet)
            Spacer(Modifier.width(14.dp))
            Text(text, color = muted, style = MaterialTheme.typography.bodyMedium)
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
    onEdit: () -> Unit, onComplete: () -> Unit, onSnooze: () -> Unit, onArchive: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(24.dp), color = cardColor) {
        Row(Modifier.fillMaxWidth().padding(start = 15.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Top) {
            IconButton(onClick = onComplete, enabled = !completed, modifier = Modifier.size(38.dp)) {
                Icon(if (completed) Icons.Filled.CheckCircle else Icons.Filled.Check,
                    contentDescription = if (completed) "Completed" else "Mark ${item.title} done",
                    tint = if (completed) aqua else muted,
                    modifier = Modifier.size(25.dp).border(1.5.dp,
                        if (completed) Color.Transparent else muted, CircleShape).padding(3.dp))
            }
            Column(Modifier.weight(1f).padding(start = 8.dp, top = 2.dp)
                .clickable(enabled = !completed, onClick = onEdit)) {
                Text(item.title, color = ivory, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium)
                item.details?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(5.dp))
                    Text(it, color = muted, style = MaterialTheme.typography.bodySmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                Text(when {
                    completed -> "Done"
                    item.dueAtMillis == null -> "No alert"
                    item.dueAtMillis < now -> "Past · ${formatTime(item.dueAtMillis)}"
                    else -> formatTime(item.dueAtMillis)
                }, color = when {
                    completed -> aqua
                    item.dueAtMillis == null -> violet
                    item.dueAtMillis < now -> coral
                    else -> amber
                }, style = MaterialTheme.typography.bodySmall)
            }
            if (!completed) Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Actions for ${item.title}",
                        tint = muted)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Edit") },
                        onClick = { menuOpen = false; onEdit() })
                    if (item.dueAtMillis != null) DropdownMenuItem(
                        text = { Text("Remind in 10 minutes") },
                        onClick = { menuOpen = false; onSnooze() })
                    DropdownMenuItem(text = { Text("Archive") },
                        onClick = { menuOpen = false; onArchive() })
                }
            }
        }
    }
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
private fun ReminderEditor(item: CommitmentEntity?, suggestedTitle: String, initialDate: LocalDate,
    onDismiss: () -> Unit, onSave: (String, String?, Long?) -> Unit) {
    val initialDue = item?.dueAtMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
    val suggestedStart = remember { LocalDateTime.now().plusMinutes(10) }
    val startDate = maxOf(initialDate, suggestedStart.toLocalDate())
    var title by remember(item?.id, suggestedTitle) { mutableStateOf(item?.title ?: suggestedTitle) }
    var details by remember(item?.id) { mutableStateOf(item?.details.orEmpty()) }
    var alertEnabled by remember(item?.id) { mutableStateOf(initialDue != null) }
    var chosenDate by remember(item?.id) { mutableStateOf(initialDue?.toLocalDate() ?: startDate) }
    var month by remember(item?.id) { mutableStateOf(YearMonth.from(chosenDate)) }
    var showCalendar by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val initialTime = initialDue?.toLocalTime() ?: suggestedStart.toLocalTime()
    val timeState = rememberTimePickerState(initialHour = initialTime.hour,
        initialMinute = initialTime.minute, is24Hour = false)
    val due = if (alertEnabled) chosenDate.atTime(timeState.hour, timeState.minute)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() else null
    val valid = title.isNotBlank() && (due == null || due > System.currentTimeMillis())

    MaterialTheme(colorScheme = darkColorScheme(primary = violet, surface = Color(0xFF24212D),
        onSurface = ivory, onSurfaceVariant = muted, outline = muted)) {
        ModalBottomSheet(onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF24212D), contentColor = ivory,
            dragHandle = { Surface(shape = CircleShape, color = Color(0xFF6A6078),
                modifier = Modifier.padding(top = 12.dp).size(width = 38.dp, height = 4.dp)) {} }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
                Spacer(Modifier.height(14.dp))
                Text(if (item == null) "New reminder" else "Edit reminder", color = ivory,
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Keep it in mind, right on time.", color = muted,
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(22.dp))
                OutlinedTextField(value = title, onValueChange = { title = it },
                    label = { Text("What do you need to remember?") },
                    placeholder = { Text("e.g. Call home") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = details, onValueChange = { details = it },
                    label = { Text("Add a note (optional)") }, maxLines = 3,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                Spacer(Modifier.height(22.dp))
                Surface(onClick = {
                    alertEnabled = !alertEnabled
                    showCalendar = false
                    showTime = false
                }, shape = RoundedCornerShape(18.dp), color = Color(0xFF373144)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, tint = sky)
                        Spacer(Modifier.width(14.dp))
                        Text("Set an alert", color = ivory, modifier = Modifier.weight(1f))
                        Text(if (alertEnabled) "On" else "Off", color = if (alertEnabled) violet else muted,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
                if (alertEnabled) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(onClick = { showCalendar = !showCalendar; showTime = false },
                            shape = RoundedCornerShape(16.dp), color = Color(0xFF373144),
                            modifier = Modifier.weight(1f)) {
                            Text(chosenDate.format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                                color = ivory, modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        Surface(onClick = { showTime = !showTime; showCalendar = false },
                            shape = RoundedCornerShape(16.dp), color = Color(0xFF373144)) {
                            Text(LocalTime.of(timeState.hour, timeState.minute)
                                .format(DateTimeFormatter.ofPattern("h:mm a")),
                                color = ivory, modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodyMedium)
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
                Button(onClick = { onSave(title.trim(), details.trim().ifBlank { null }, due) },
                    enabled = valid, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = violet,
                        contentColor = canvasTop)) {
                    Text(if (item == null) "Add reminder" else "Save changes",
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault()).format(timeFormat)
