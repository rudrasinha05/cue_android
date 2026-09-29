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

@Dao
interface CommitmentDao {
    @Query("SELECT * FROM commitments WHERE ownerId = :ownerId AND status = 'active' ORDER BY dueAtMillis ASC")
    fun observeActive(ownerId: String): Flow<List<CommitmentEntity>>

    @Query("SELECT * FROM commitments WHERE ownerId = :ownerId AND status = 'completed' ORDER BY updatedAtMillis DESC")
    fun observeCompleted(ownerId: String): Flow<List<CommitmentEntity>>

    @Query("SELECT * FROM commitments WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): CommitmentEntity?

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

@Database(entities = [CommitmentEntity::class], version = 2, exportSchema = true)
abstract class CueDatabase : RoomDatabase() {
    abstract fun commitments(): CommitmentDao

    companion object {
        @Volatile private var instance: CueDatabase? = null
        private val migration1to2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE commitments ADD COLUMN dirty INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): CueDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, CueDatabase::class.java, "cue.db")
                .addMigrations(migration1to2).build().also { instance = it }
        }
    }
}
