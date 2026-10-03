package com.rudrasinha.cue.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rudrasinha.cue.data.CommitmentEntity
import com.rudrasinha.cue.planning.DayBlock
import com.rudrasinha.cue.planning.buildDailyPlan
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

fun timeLabel(minute: Int): String = LocalTime.of((minute / 60) % 24, minute % 60)
    .format(DateTimeFormatter.ofPattern("h:mm a"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyPlanSheet(items: List<CommitmentEntity>, wake: Int, bed: Int, onDismiss: () -> Unit) {
    val today = LocalDate.now()
    val plan = remember(items, today, wake, bed) { buildDailyPlan(items, today, wake, bed) }
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 8.dp)) {
            Text("Your day, in one view", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            Text("${timeLabel(wake)} to ${timeLabel(bed)} · fixed reminders stay at their time",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 24.dp))
            plan.forEach { block ->
                val accent = when (block.kind) {
                    DayBlock.Kind.REMINDER -> MaterialTheme.colorScheme.primary
                    DayBlock.Kind.FLEXIBLE -> MaterialTheme.colorScheme.secondary
                    DayBlock.Kind.ROUTINE -> MaterialTheme.colorScheme.tertiary
                    DayBlock.Kind.OPEN -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(shape = RoundedCornerShape(21.dp), color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(when (block.kind) {
                            DayBlock.Kind.REMINDER -> Icons.Filled.Schedule
                            DayBlock.Kind.FLEXIBLE -> Icons.Filled.CheckCircle
                            else -> Icons.Filled.AutoAwesome
                        }, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                        Column(Modifier.padding(start = 13.dp)) {
                            Text(block.title, style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold)
                            Text("${timeLabel(block.startMinute)} – ${timeLabel(block.endMinute)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
