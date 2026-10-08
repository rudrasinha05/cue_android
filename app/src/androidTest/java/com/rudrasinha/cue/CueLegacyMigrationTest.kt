package com.rudrasinha.cue

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rudrasinha.cue.data.CueDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Real Room v1→v9 upgrade on an isolated database. Never opens cue.db. */
@RunWith(AndroidJUnit4::class)
class CueLegacyMigrationTest {
    @Test fun versionOneRemindersAndDefaultsSurviveUpgradeAndReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "cue-migration-qa-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        try {
            // v1 schema from the original released repository commit.
            val old = SQLiteDatabase.openOrCreateDatabase(file, null)
            try {
                old.execSQL("""CREATE TABLE IF NOT EXISTS commitments (
                    id TEXT NOT NULL PRIMARY KEY,
                    ownerId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    details TEXT,
                    dueAtMillis INTEGER,
                    timezone TEXT NOT NULL,
                    status TEXT NOT NULL,
                    updatedAtMillis INTEGER NOT NULL)""".trimIndent())
                old.execSQL("CREATE INDEX IF NOT EXISTS index_commitments_ownerId_dueAtMillis ON commitments (ownerId, dueAtMillis)")
                old.execSQL("""INSERT INTO commitments
                    (id, ownerId, title, details, dueAtMillis, timezone, status, updatedAtMillis)
                    VALUES ('qa-legacy', 'guest', 'Migration reminder', 'Keep my details',
                    4102444800000, 'Asia/Kolkata', 'active', 1730000000000)""".trimIndent())
                old.version = 1
            } finally {
                old.close()
            }
            var upgraded = CueDatabase.openDatabase(context, name)
            try {
                assertEquals(9, upgraded.openHelper.readableDatabase.version)
                val item = upgraded.commitments().byId("qa-legacy")
                assertNotNull("Existing v1 reminder must survive migration", item)
                assertEquals("Migration reminder", item?.title)
                assertEquals("Keep my details", item?.details)
                assertEquals("guest", item?.ownerId)
                assertEquals(4102444800000L, item?.dueAtMillis)
                assertEquals("Asia/Kolkata", item?.timezone)
                assertEquals("active", item?.status)
                assertFalse(item!!.dirty)
                assertEquals("default", item.toneId)
                assertFalse(item.chainEnabled)
                assertEquals("1440,60", item.chainOffsets)
                assertEquals(0L, item.syncedAtMillis)
                assertEquals(1, upgraded.commitments().guestRecords().size)
            } finally { upgraded.close() }
            upgraded = CueDatabase.openDatabase(context, name)
            try {
                assertNotNull("Migrated reminder must survive a fresh database open",
                    upgraded.commitments().byId("qa-legacy"))
                assertTrue(upgraded.commitments().guestRecords().any { it.id == "qa-legacy" })
            } finally { upgraded.close() }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
