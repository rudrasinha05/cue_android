package com.rudrasinha.cue.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountDataTest {
    @Test fun divergentDuplicateMustNotBeSilentlyExported() {
        val event = ReminderEventEntity("id", "user", "item", "updated",
            """{"title":"Before"}""", "app", "key", 100)
        val conflicting = event.copy(changeData = """{"title":"After"}""")
        assertThrows(IllegalArgumentException::class.java) {
            AccountData.mergeEvents(listOf(event), listOf(conflicting))
        }
        assertEquals(listOf(event), AccountData.mergeEvents(listOf(event), listOf(event.copy(dirty = true))))
    }

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
