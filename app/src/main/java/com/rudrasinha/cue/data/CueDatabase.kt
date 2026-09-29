package com.rudrasinha.cue.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Room
import androidx.room.RoomDatabase
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
    val updatedAtMillis: Long
)

@Dao
interface CommitmentDao {
    @Query("SELECT * FROM commitments WHERE ownerId = :ownerId AND status = 'active' ORDER BY dueAtMillis ASC")
    fun observeActive(ownerId: String): Flow<List<CommitmentEntity>>

    @Query("SELECT * FROM commitments WHERE ownerId = 'guest'")
    suspend fun guestRecords(): List<CommitmentEntity>

    @Query("UPDATE commitments SET ownerId = :userId WHERE ownerId = 'guest'")
    suspend fun claimGuestRecords(userId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(records: List<CommitmentEntity>)

    @Query("DELETE FROM commitments WHERE ownerId = :userId")
    suspend fun removeAccountCache(userId: String)
}

@Database(entities = [CommitmentEntity::class], version = 1, exportSchema = true)
abstract class CueDatabase : RoomDatabase() {
    abstract fun commitments(): CommitmentDao

    companion object {
        @Volatile private var instance: CueDatabase? = null

        fun get(context: Context): CueDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, CueDatabase::class.java, "cue.db")
                .build().also { instance = it }
        }
    }
}
