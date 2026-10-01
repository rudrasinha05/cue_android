package com.rudrasinha.cue.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderMatchTest {
    private fun reminder(title: String, owner: String = "me", due: Long = 100L) =
        CommitmentEntity("id", owner, title, null, due, "UTC", "active", 1L)

    @Test fun punctuationAndCaseInTheSameEventMatch() {
        val existing = reminder("Call Mom, tomorrow")
        assertEquals(existing, ReminderMatch.existing(listOf(existing), "  CALL mom - tomorrow! "))
    }

    @Test fun distinctActionDoesNotMatch() {
        assertNull(ReminderMatch.existing(listOf(reminder("Call Mom")), "Pay rent"))
        assertNull(ReminderMatch.existing(listOf(reminder("Call Mom")), "!!!"))
    }
}
