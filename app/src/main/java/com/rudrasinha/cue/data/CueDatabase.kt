package com.rudrasinha.cue.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
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
    @Query("SELECT * FROM commitments WHERE ownerId = 'guest' AND status = 'active' ORDER BY dueAtMillis ASC")
    fun observeActive(): Flow<List<CommitmentEntity>>
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
