package com.rudrasinha.cue.planning

import com.rudrasinha.cue.data.CommitmentEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DayBlock(val startMinute: Int, val endMinute: Int, val title: String,
    val kind: Kind, val reminderId: String? = null) {
    enum class Kind { ROUTINE, REMINDER, FLEXIBLE, OPEN }
}

/** Fixed reminder instants stay fixed; open time and flexible work fill the remaining day. */
fun buildDailyPlan(items: List<CommitmentEntity>, day: LocalDate, wake: Int = 420,
    bed: Int = 1320, zone: ZoneId = ZoneId.systemDefault()): List<DayBlock> {
    require(wake in 0..1080 && bed in 480..1439 && bed - wake >= 360)
    val active = items.filter { it.status == "active" }
    val fixed = active.mapNotNull { item ->
        item.dueAtMillis?.let { due ->
            val local = Instant.ofEpochMilli(due).atZone(zone)
            if (local.toLocalDate() != day) null else {
                val minute = local.hour * 60 + local.minute
                DayBlock(minute, minute + 20, item.title, DayBlock.Kind.REMINDER, item.id)
            }
        }
    }.sortedWith(compareBy<DayBlock> { it.startMinute }.thenBy { it.reminderId })
    val occupied = fixed.toMutableList()
    fun addIfFree(start: Int, length: Int, title: String) {
        if (start < wake || start + length > bed) return
        if (occupied.none { start < it.endMinute && start + length > it.startMinute })
            occupied += DayBlock(start, start + length, title, DayBlock.Kind.ROUTINE)
    }
    addIfFree(wake, 40, "Morning reset")
    addIfFree((wake + 60).coerceAtMost(600), 30, "Breakfast")
    addIfFree(780, 40, "Lunch")
    addIfFree((bed - 180).coerceAtLeast(1080), 40, "Dinner")
    addIfFree(bed - 45, 45, "Wind down · bedtime")

    active.filter { it.dueAtMillis == null }.sortedWith(compareBy<CommitmentEntity> { it.updatedAtMillis }
        .thenBy { it.id }).forEach { item ->
        var start = wake + 45
        while (start + 30 <= bed) {
            val blocker = occupied.filter { start < it.endMinute && start + 30 > it.startMinute }
                .minByOrNull { it.endMinute }
            if (blocker == null) {
                occupied += DayBlock(start, start + 30, item.title, DayBlock.Kind.FLEXIBLE, item.id)
                break
            }
            start = maxOf(start + 5, blocker.endMinute)
        }
    }
    val inside = occupied.filter { it.startMinute >= wake && it.startMinute < bed }
        .sortedWith(compareBy<DayBlock> { it.startMinute }
            .thenBy { if (it.kind == DayBlock.Kind.REMINDER) 0 else 1 })
    val plan = mutableListOf<DayBlock>()
    var cursor = wake
    inside.forEach { block ->
        if (block.startMinute > cursor)
            plan += DayBlock(cursor, block.startMinute, "Open time", DayBlock.Kind.OPEN)
        plan += block
        cursor = maxOf(cursor, block.endMinute)
    }
    if (cursor < bed) plan += DayBlock(cursor, bed, "Open time", DayBlock.Kind.OPEN)
    // A reminder outside waking hours still appears at its original time.
    return (plan + fixed.filter { it.startMinute < wake || it.startMinute >= bed })
        .sortedWith(compareBy<DayBlock> { it.startMinute }
            .thenBy { if (it.kind == DayBlock.Kind.REMINDER) 0 else 1 })
}
