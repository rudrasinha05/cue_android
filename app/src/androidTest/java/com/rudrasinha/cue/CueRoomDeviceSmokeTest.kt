package com.rudrasinha.cue

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rudrasinha.cue.data.CommitmentEntity
import com.rudrasinha.cue.data.CueDatabase
import com.rudrasinha.cue.data.ReminderEventEntity
import com.rudrasinha.cue.data.SourceEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Real SQLite/Room device checks using a disposable in-memory database. */
@RunWith(AndroidJUnit4::class)
class CueRoomDeviceSmokeTest {
    @Test fun remindersAndHistoryAreOwnerScoped() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CueDatabase::class.java).build()
        try {
            val due = System.currentTimeMillis() + 600_000L
            val a = CommitmentEntity("qa-a", "qa-owner-a", "Pay bill", null,
                due, "Asia/Kolkata", "active", 100L)
            val b = a.copy(id = "qa-b", ownerId = "qa-owner-b")
            database.commitments().upsert(listOf(a, b))
            assertEquals(listOf(a), database.commitments().activeAtDue("qa-owner-a", due))
            assertEquals(listOf(b), database.commitments().activeAtDue("qa-owner-b", due))
            database.history().insertSource(SourceEntity("qa-source", "qa-owner-a",
                "qa-a", "share", "qa-key", "Bill", "Pay bill", null,
                capturedAtMillis = 100L))
            database.history().insertEvent(ReminderEventEntity("qa-event", "qa-owner-a",
                "qa-a", "created", "{}", "test", "qa-event", 100L))
            assertEquals("qa-a",
                database.history().sourceByOrigin("qa-owner-a", "share", "qa-key")?.commitmentId)
            assertNull(database.history().sourceByOrigin("qa-owner-b", "share", "qa-key"))
            assertEquals(1, database.history().eventsForOwner("qa-owner-a").size)
            assertEquals(0, database.history().eventsForOwner("qa-owner-b").size)
        } finally { database.close() }
    }
}
