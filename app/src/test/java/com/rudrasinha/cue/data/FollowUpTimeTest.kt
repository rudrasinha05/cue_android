package com.rudrasinha.cue.data

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FollowUpTimeTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    @Test fun keepsPreferredLocalTimeAndMovesByCalendarDay() {
        val now = Instant.parse("2026-10-02T04:00:00Z").toEpochMilli()
        val prior = Instant.parse("2026-10-01T13:30:00Z").toEpochMilli()
        val tomorrow = FollowUpTime.at(now, prior, 1, zone)
        val week = FollowUpTime.at(now, prior, 7, zone)
        assertEquals("2026-10-03T13:30:00Z", Instant.ofEpochMilli(tomorrow).toString())
        assertEquals("2026-10-09T13:30:00Z", Instant.ofEpochMilli(week).toString())
    }

    @Test fun untimedReminderDefaultsToNineAndRejectsArbitraryDelay() {
        val now = Instant.parse("2026-10-02T04:00:00Z").toEpochMilli()
        assertEquals("2026-10-03T03:30:00Z", Instant.ofEpochMilli(
            FollowUpTime.at(now, null, 1, zone)).toString())
        assertThrows(IllegalArgumentException::class.java) {
            FollowUpTime.at(now, null, 0, zone)
        }
    }
}
