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

    @Test fun explicitHinglishTimeCanBeScheduledWithoutGuessingAmPm() {
        assertEquals(Instant.parse("2026-10-01T09:30:00Z").toEpochMilli(),
            suggestedDue("Kal subah 9:30 baje meeting yaad dilana", clock))
        assertEquals(Instant.parse("2026-09-30T17:00:00Z").toEpochMilli(),
            suggestedDue("Aaj shaam 5 baje bill pay", clock))
        assertNull(suggestedDue("Kal 5 baje bill pay", clock))
        assertNull(suggestedDue("Kal raat 2 baje call", clock))
    }

    @Test fun screenLinesCannotCombineUnrelatedDateAndTime() {
        assertNull(confidentReminder("Tomorrow's events\n9 pm weather update", clock))
        assertNull(confidentReminder("Pay today at 6 pm\nCall tomorrow at 9 pm", clock))
        assertEquals("Pay bill today at 6 pm",
            confidentReminder("Inbox\nPay bill today at 6 pm\nOther text", clock)?.title)
    }

    @Test fun passiveScreenTextDoesNotBecomeAReminder() {
        assertNull(confidentReminder("Weather today at 6 pm", clock))
        assertNull(confidentReminder("Today at 6 pm", clock))
        assertEquals("Call mom tomorrow at 9 pm",
            confidentReminder("Call mom tomorrow at 9 pm", clock)?.title)
    }

    @Test fun modelCannotUseAStandaloneTimeAsAFutureEvent() {
        org.junit.Assert.assertFalse(explicitDateTimeSpan("at 9 pm"))
        org.junit.Assert.assertFalse(explicitDateTimeSpan("October 3"))
        org.junit.Assert.assertTrue(explicitDateTimeSpan("Oct 3 at 9 pm"))
    }
}
