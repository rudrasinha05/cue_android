package com.rudrasinha.cue.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountDataTest {
    @Test fun archiveRecoveryKeepsExactEventsOnceInChronologicalOrder() {
        fun event(id: String, time: Long) = ReminderEventEntity(id, "owner", "reminder",
            "updated", "{}", "app", id, time)
        val first = event("a", 100)
        val second = event("b", 200)
        val third = event("c", 300)
        val archive = HistoryArchive.pack("owner", listOf(second, first))
        assertEquals(listOf(first, second, third), AccountData.mergeEvents(
            listOf(third, second), HistoryArchive.read(archive)))
    }
}
