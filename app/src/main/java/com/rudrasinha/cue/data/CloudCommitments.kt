package com.rudrasinha.cue.data

import androidx.room.withTransaction
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class CloudCommitment(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val details: String?,
    @SerialName("due_at") val dueAt: String?,
    val timezone: String,
    val status: String,
    @SerialName("updated_at") val updatedAt: String
)

class CloudCommitments(private val database: CueDatabase, private val client: SupabaseClient) {
    // The same UUID is used locally and remotely. Repeated sign-in safely retries the upload.
    suspend fun restoreAndClaim(userId: String) {
        val dao = database.commitments()
        dao.guestRecords().forEach { local ->
            client.from("commitments").upsert(local.toCloud(userId))
        }
        database.withTransaction { dao.claimGuestRecords(userId) }

        syncPending(userId)

        val remote = client.from("commitments").select {
            filter { eq("user_id", userId) }
        }.decodeList<CloudCommitment>()
        database.withTransaction {
            remote.forEach { record ->
                if (dao.byId(record.id)?.dirty != true) dao.upsert(listOf(record.toLocal()))
            }
        }
    }

    suspend fun syncPending(userId: String) {
        val dao = database.commitments()
        dao.pending(userId).forEach { local ->
            client.from("commitments").upsert(local.toCloud(userId))
            dao.markSynced(local.id, userId, local.updatedAtMillis)
        }
    }

    suspend fun clearAccountCache(userId: String) {
        database.commitments().removeAccountCache(userId)
    }
}

private fun CommitmentEntity.toCloud(userId: String) = CloudCommitment(
    id = id,
    userId = userId,
    title = title,
    details = details,
    dueAt = dueAtMillis?.let { Instant.ofEpochMilli(it).toString() },
    timezone = timezone,
    status = status,
    updatedAt = Instant.ofEpochMilli(updatedAtMillis).toString()
)

private fun CloudCommitment.toLocal() = CommitmentEntity(
    id = id,
    ownerId = userId,
    title = title,
    details = details,
    dueAtMillis = dueAt?.let { Instant.parse(it).toEpochMilli() },
    timezone = timezone,
    status = status,
    updatedAtMillis = Instant.parse(updatedAt).toEpochMilli()
)
