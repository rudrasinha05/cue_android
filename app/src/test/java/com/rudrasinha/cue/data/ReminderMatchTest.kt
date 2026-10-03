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

    @Test fun equivalentWordingLinksOnlySameActionAndObject() {
        val existing = reminder("Pay electricity bill")
        assertEquals(existing, ReminderMatch.existing(listOf(existing), "Electricity bill payment"))
        assertNull(ReminderMatch.existing(listOf(existing), "Pay internet bill"))
        assertNull(ReminderMatch.existing(listOf(existing), "Submit electricity bill"))
        assertNull(ReminderMatch.existing(listOf(existing), "Pay electricity bill and rent"))
    }

    @Test fun safeSynonymsStillPreserveDifferentObjects() {
        val existing = reminder("Renew subscription")
        assertEquals(existing, ReminderMatch.existing(listOf(existing), "My subscription renewal"))
        assertNull(ReminderMatch.existing(listOf(existing), "Renew insurance"))
        assertNull(ReminderMatch.existing(listOf(existing), "Cancel subscription"))
        val bill = reminder("Pay electricity bills")
        assertEquals(bill, ReminderMatch.existing(listOf(bill), "Electricity bill payment"))
    }
}
