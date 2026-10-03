package com.rudrasinha.cue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenReminderCandidateTest {
    @Test fun findsOneDeadlineAmongScreenChrome() {
        assertEquals("Submit project tomorrow 5:30 pm", ScreenReminderCandidate.select(
            "Home\nInbox\nSettings\nSubmit  project tomorrow 5:30 pm\nBattery 84%"))
    }

    @Test fun ignoresAmbiguousAndUnrelatedClockText() {
        assertNull(ScreenReminderCandidate.select("Meet Tara tomorrow 10 am\nPay rent tomorrow 6 pm"))
        assertNull(ScreenReminderCandidate.select("Home\n10:30\nMeeting\nTomorrow"))
        assertNull(ScreenReminderCandidate.select("Submit report soon"))
    }
}
