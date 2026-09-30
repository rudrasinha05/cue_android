package com.rudrasinha.cue.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryArchiveTest {
    private val events = (1..40).map { index ->
        ReminderEventEntity("event-$index", "account-1", "reminder-1", "updated",
            "{\"title\":\"A recurring reminder\",\"note\":\"Details $index\"}", "app",
            "key-$index", index.toLong())
    }

    @Test fun archiveRoundTripsAllFieldsAndActuallyCompresses() {
        val batch = HistoryArchive.pack("account-1", events.reversed())
        val restored = HistoryArchive.read(batch)
        assertEquals(events, restored)
        assertEquals(events.size, batch.eventCount)
        assertTrue(batch.payload.size < events.sumOf { it.changeData.length + it.id.length })
        assertEquals(batch.id, HistoryArchive.pack("account-1", events).id)
    }

    @Test fun corruptOrMisownedBatchCannotSilentlyDropHistory() {
        val batch = HistoryArchive.pack("account-1", events)
        assertThrows(IllegalArgumentException::class.java) {
            HistoryArchive.read(batch.copy(checksum = "wrong"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            HistoryArchive.read(batch.copy(ownerId = "account-2"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            HistoryArchive.pack("account-2", events)
        }
    }
}
