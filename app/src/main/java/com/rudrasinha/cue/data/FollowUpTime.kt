package com.rudrasinha.cue.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Moves the same reminder to a future local date while retaining its preferred time of day. */
object FollowUpTime {
    fun at(nowMillis: Long, previousDueMillis: Long?, days: Long, zone: ZoneId): Long {
        require(days == 1L || days == 7L)
        val preferred = previousDueMillis?.let {
            Instant.ofEpochMilli(it).atZone(zone).toLocalTime()
        } ?: LocalTime.of(9, 0)
        val target = LocalDate.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
            .plusDays(days).atTime(preferred.hour, preferred.minute)
            .atZone(zone).toInstant().toEpochMilli()
        require(target > nowMillis)
        return target
    }
}
