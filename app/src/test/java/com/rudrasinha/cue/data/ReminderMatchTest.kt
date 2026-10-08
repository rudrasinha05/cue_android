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

    @Test fun equivalentSchedulesMatchAtTheSameDueInstant() {
        val existing = reminder("Pay electricity bill tomorrow at 5:30 pm")
        assertEquals(existing, ReminderMatch.existing(listOf(existing),
            "Electricity bill payment by kal 17:30"))
        assertNull(ReminderMatch.existing(listOf(existing),
            "Pay internet bill tomorrow at 5:30 pm"))
    }

    @Test fun quantitiesAndDifferentActionsMustNotMerge() {
        val two = reminder("Buy 2 tickets tomorrow at 6 pm")
        assertNull(ReminderMatch.existing(listOf(two), "Purchase 3 tickets kal 18:00"))
        assertEquals(two, ReminderMatch.existing(listOf(two), "Purchase 2 tickets kal 18:00"))
        assertNull(ReminderMatch.existing(listOf(two), "Book 2 tickets tomorrow at 6 pm"))
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
