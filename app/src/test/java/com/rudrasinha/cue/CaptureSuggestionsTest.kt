package com.rudrasinha.cue

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureSuggestionsTest {
    private val clock = Clock.fixed(Instant.parse("2026-09-30T10:00:00Z"), ZoneId.of("UTC"))

    @Test fun tomorrowAtNinePmIsOnlyASuggestion() {
        assertEquals(Instant.parse("2026-10-01T21:00:00Z").toEpochMilli(),
            suggestedDue("Call Mom tomorrow at 9 pm", clock))
    }

    @Test fun todayWith24HourTimeWorks() {
        assertEquals(Instant.parse("2026-09-30T18:45:00Z").toEpochMilli(),
            suggestedDue("Pay bill today 18:45", clock))
    }

    @Test fun ambiguousOrPastTextNeedsManualReview() {
        assertNull(suggestedDue("Call Mom sometime tomorrow", clock))
        assertNull(suggestedDue("Today at 8 am", clock))
        assertNull(suggestedDue("Bring 9 apples tomorrow", clock))
    }
}
