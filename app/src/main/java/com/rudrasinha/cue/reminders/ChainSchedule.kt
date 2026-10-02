package com.rudrasinha.cue.reminders

/** Fixed pre-alerts never shift the canonical reminder's due time. */
internal object ChainSchedule {
    val offsetsMinutes = listOf(1440L, 60L)

    fun upcoming(dueAtMillis: Long, nowMillis: Long): List<Pair<Long, Long>> =
        offsetsMinutes.mapNotNull { offset ->
            val at = dueAtMillis - offset * 60_000L
            if (at > nowMillis) offset to at else null
        }
}
