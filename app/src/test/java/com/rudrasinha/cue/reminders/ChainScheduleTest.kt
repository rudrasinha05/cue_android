package com.rudrasinha.cue.reminders

import org.junit.Assert.assertEquals
import org.junit.Test

class ChainScheduleTest {
    @Test fun onlyFutureNudgesAreScheduled() {
        val now = 1_000_000_000L
        assertEquals(listOf(1440L to now + 60_000L, 60L to now + 82_860_000L),
            ChainSchedule.upcoming(now + 86_460_000L, now))
        assertEquals(listOf(60L to now + 60_000L),
            ChainSchedule.upcoming(now + 3_660_000L, now))
        assertEquals(emptyList<Pair<Long, Long>>(), ChainSchedule.upcoming(now + 60_000L, now))
    }

    @Test fun selectedOffsetsAreCanonicalAndDoNotSchedulePastStages() {
        val selected = listOf(180L, 2880L)
        assertEquals("2880,180", ChainSchedule.encode(selected))
        assertEquals(listOf(2880L, 180L), ChainSchedule.parse("2880,180"))
        assertEquals(listOf(180L to 10_060_000L),
            ChainSchedule.upcoming(20_860_000L, 10_000_000L, ChainSchedule.parse("2880,180")))
    }
}
