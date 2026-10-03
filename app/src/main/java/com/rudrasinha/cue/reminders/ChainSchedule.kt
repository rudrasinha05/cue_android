package com.rudrasinha.cue.reminders

/** Fixed pre-alerts never shift the canonical reminder's due time. */
internal object ChainSchedule {
    val offsetsMinutes = listOf(1440L, 60L)
    val presets = linkedMapOf(
        "1 day + 1 hour" to offsetsMinutes,
        "2 days + 3 hours" to listOf(2880L, 180L),
        "1 week + 1 day + 1 hour" to listOf(10080L, 1440L, 60L)
    )

    fun parse(value: String): List<Long> = value.split(',').mapNotNull { it.toLongOrNull() }
        .filter { it in 5L..10080L }.distinct().sortedDescending()
        .take(3).ifEmpty { offsetsMinutes }

    fun encode(value: List<Long>): String {
        require(value.isNotEmpty() && value.size <= 3 && value.distinct().size == value.size &&
            value.all { it in 5L..10080L })
        return value.sortedDescending().joinToString(",")
    }

    fun upcoming(dueAtMillis: Long, nowMillis: Long,
        offsets: List<Long> = offsetsMinutes): List<Pair<Long, Long>> =
        offsets.mapNotNull { offset ->
            val at = dueAtMillis - offset * 60_000L
            if (at > nowMillis) offset to at else null
        }
}
