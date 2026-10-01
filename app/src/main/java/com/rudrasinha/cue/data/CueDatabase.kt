package com.rudrasinha.cue.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "commitments", indices = [Index(value = ["ownerId", "dueAtMillis"])])
data class CommitmentEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val title: String,
    val details: String?,
    val dueAtMillis: Long?,
    val timezone: String,
    val status: String,
    val updatedAtMillis: Long,
    @ColumnInfo(defaultValue = "0") val dirty: Boolean = false
)

@Entity(tableName = "sources", indices = [Index(value = ["ownerId", "commitmentId"]), Index(value = ["ownerId", "originType", "originKey"], unique = true)])
data class SourceEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val commitmentId: String,
    val originType: String,
    val originKey: String?,
    val title: String?,
    val excerpt: String?,
    val originalUri: String?,
    val permissionState: String = "available",
    val capturedAtMillis: Long,
    val dirty: Boolean = false
)

@Entity(tableName = "reminder_events", indices = [Index(value = ["ownerId", "commitmentId", "occurredAtMillis"]), Index(value = ["ownerId", "idempotencyKey"], unique = true)])
data class ReminderEventEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val commitmentId: String,
    val eventType: String,
    val changeData: String,
    val actor: String,
    val idempotencyKey: String,
    val occurredAtMillis: Long,
    val dirty: Boolean = false
)

@Entity(tableName = "history_batches", indices = [Index(value = ["ownerId", "lastAtMillis"])])
data class HistoryBatchEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val firstAtMillis: Long,
    val lastAtMillis: Long,
    val eventCount: Int,
    val checksum: String,
    val payload: ByteArray,
    val createdAtMillis: Long
)

@Dao
interface CommitmentDao {
    @Query("SELECT * FROM commitments WHERE ownerId = :ownerId AND status = 'active' ORDER BY dueAtMillis ASC")
    fun observeActive(ownerId: String): Flow<List<CommitmentEntity>>

    @Query("SELECT * FROM commitments WHERE ownerId = :ownerId AND status = 'completed' ORDER BY updatedAtMillis DESC")
    fun observeCompleted(ownerId: String): Flow<List<CommitmentEntity>>

    @Query("SELECT * FROM commitments WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): CommitmentEntity?

    @Query("SELECT * FROM commitments WHERE ownerId = :ownerId AND status = 'active' AND title = :title AND dueAtMillis = :due LIMIT 1")
    suspend fun sameActiveReminder(ownerId: String, title: String, due: Long): CommitmentEntity?

    @Query("SELECT * FROM commitments WHERE ownerId = :ownerId AND status = 'active' AND dueAtMillis > :now")
    suspend fun futureAlarms(ownerId: String, now: Long): List<CommitmentEntity>

    @Query("SELECT * FROM commitments WHERE ownerId = :ownerId AND status = 'active' AND dueAtMillis IS NOT NULL")
    suspend fun ownerAlarms(ownerId: String): List<CommitmentEntity>

    @Query("SELECT * FROM commitments WHERE ownerId = :ownerId AND dirty = 1 ORDER BY updatedAtMillis")
    suspend fun pending(ownerId: String): List<CommitmentEntity>

    @Query("UPDATE commitments SET dirty = 0 WHERE id = :id AND ownerId = :ownerId AND updatedAtMillis = :version")
    suspend fun markSynced(id: String, ownerId: String, version: Long)

    @Query("SELECT * FROM commitments WHERE ownerId = 'guest'")
    suspend fun guestRecords(): List<CommitmentEntity>

    @Query("UPDATE commitments SET ownerId = :userId, dirty = 0 WHERE ownerId = 'guest'")
    suspend fun claimGuestRecords(userId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(records: List<CommitmentEntity>)

    @Query("DELETE FROM commitments WHERE ownerId = :userId AND dirty = 0")
    suspend fun removeAccountCache(userId: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM sources WHERE ownerId = :ownerId ORDER BY capturedAtMillis DESC")
    fun observeSources(ownerId: String): Flow<List<SourceEntity>>

    @Query("SELECT * FROM reminder_events WHERE ownerId = :ownerId ORDER BY occurredAtMillis DESC")
    fun observeEvents(ownerId: String): Flow<List<ReminderEventEntity>>

    @Query("SELECT * FROM history_batches WHERE ownerId = :ownerId ORDER BY lastAtMillis DESC")
    fun observeBatches(ownerId: String): Flow<List<HistoryBatchEntity>>

    @Query("SELECT * FROM history_batches WHERE ownerId = :ownerId")
    suspend fun batches(ownerId: String): List<HistoryBatchEntity>

    @Query("SELECT * FROM reminder_events WHERE ownerId = :ownerId AND dirty = 0 AND occurredAtMillis < :cutoff ORDER BY occurredAtMillis, id LIMIT 100")
    suspend fun compactable(ownerId: String, cutoff: Long): List<ReminderEventEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBatch(batch: HistoryBatchEntity): Long

    @Query("DELETE FROM reminder_events WHERE ownerId = :ownerId AND dirty = 0 AND id IN (:ids)")
    suspend fun removeArchivedEvents(ownerId: String, ids: List<String>)

    @Query("SELECT * FROM sources WHERE ownerId = :ownerId AND dirty = 1 ORDER BY capturedAtMillis")
    suspend fun pendingSources(ownerId: String): List<SourceEntity>

    @Query("SELECT * FROM reminder_events WHERE ownerId = :ownerId AND dirty = 1 ORDER BY occurredAtMillis")
    suspend fun pendingEvents(ownerId: String): List<ReminderEventEntity>

    @Query("SELECT * FROM sources WHERE ownerId = 'guest'")
    suspend fun guestSources(): List<SourceEntity>

    @Query("SELECT * FROM reminder_events WHERE ownerId = 'guest'")
    suspend fun guestEvents(): List<ReminderEventEntity>

    @Query("UPDATE sources SET ownerId = :userId, dirty = 0 WHERE ownerId = 'guest'")
    suspend fun claimGuestSources(userId: String)

    @Query("UPDATE reminder_events SET ownerId = :userId, dirty = 0 WHERE ownerId = 'guest'")
    suspend fun claimGuestEvents(userId: String)

    @Query("UPDATE sources SET dirty = 0 WHERE ownerId = :ownerId AND id = :id")
    suspend fun markSourceSynced(ownerId: String, id: String)

    @Query("UPDATE reminder_events SET dirty = 0 WHERE ownerId = :ownerId AND id = :id")
    suspend fun markEventSynced(ownerId: String, id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: ReminderEventEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSource(source: SourceEntity)

    @Query("DELETE FROM sources WHERE ownerId = :ownerId AND dirty = 0")
    suspend fun removeSourceCache(ownerId: String)

    @Query("DELETE FROM reminder_events WHERE ownerId = :ownerId AND dirty = 0")
    suspend fun removeEventCache(ownerId: String)
}

@Database(entities = [CommitmentEntity::class, SourceEntity::class, ReminderEventEntity::class,
    HistoryBatchEntity::class], version = 4, exportSchema = true)
abstract class CueDatabase : RoomDatabase() {
    abstract fun commitments(): CommitmentDao
    abstract fun history(): HistoryDao

    companion object {
        @Volatile private var instance: CueDatabase? = null
        private val migration1to2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE commitments ADD COLUMN dirty INTEGER NOT NULL DEFAULT 0")
            }
        }
        private val migration2to3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS sources (id TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL, commitmentId TEXT NOT NULL, originType TEXT NOT NULL, originKey TEXT, title TEXT, excerpt TEXT, originalUri TEXT, permissionState TEXT NOT NULL, capturedAtMillis INTEGER NOT NULL, dirty INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sources_ownerId_commitmentId ON sources (ownerId, commitmentId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sources_ownerId_originType_originKey ON sources (ownerId, originType, originKey)")
                db.execSQL("CREATE TABLE IF NOT EXISTS reminder_events (id TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL, commitmentId TEXT NOT NULL, eventType TEXT NOT NULL, changeData TEXT NOT NULL, actor TEXT NOT NULL, idempotencyKey TEXT NOT NULL, occurredAtMillis INTEGER NOT NULL, dirty INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reminder_events_ownerId_commitmentId_occurredAtMillis ON reminder_events (ownerId, commitmentId, occurredAtMillis)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_reminder_events_ownerId_idempotencyKey ON reminder_events (ownerId, idempotencyKey)")
            }
        }
        private val migration3to4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS history_batches (id TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL, firstAtMillis INTEGER NOT NULL, lastAtMillis INTEGER NOT NULL, eventCount INTEGER NOT NULL, checksum TEXT NOT NULL, payload BLOB NOT NULL, createdAtMillis INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_history_batches_ownerId_lastAtMillis ON history_batches (ownerId, lastAtMillis)")
            }
        }

        fun get(context: Context): CueDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, CueDatabase::class.java, "cue.db")
                .addMigrations(migration1to2, migration2to3, migration3to4).build().also { instance = it }
        }
    }
}
