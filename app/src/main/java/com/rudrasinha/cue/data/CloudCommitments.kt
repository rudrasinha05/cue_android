package com.rudrasinha.cue.data

import androidx.room.withTransaction
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

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

@Serializable
private data class CloudSource(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("commitment_id") val commitmentId: String,
    @SerialName("origin_type") val originType: String,
    @SerialName("origin_key") val originKey: String?,
    val title: String?, val excerpt: String?,
    @SerialName("original_uri") val originalUri: String?,
    @SerialName("permission_state") val permissionState: String,
    @SerialName("captured_at") val capturedAt: String
)

@Serializable
private data class CloudEvent(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("commitment_id") val commitmentId: String,
    @SerialName("event_type") val eventType: String,
    @SerialName("change_data") val changeData: JsonElement,
    val actor: String,
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("occurred_at") val occurredAt: String
)

class CloudCommitments(private val database: CueDatabase, private val client: SupabaseClient) {
    // The same UUID is used locally and remotely. Repeated sign-in safely retries the upload.
    suspend fun restoreAndClaim(userId: String) {
        val dao = database.commitments()
        val history = database.history()
        dao.guestRecords().forEach { local ->
            client.from("commitments").upsert(local.toCloud(userId))
        }
        history.guestSources().forEach { client.from("sources").upsert(it.toCloud(userId)) }
        history.guestEvents().forEach { insertEventIfAbsent(it.toCloud(userId)) }
        database.withTransaction {
            dao.claimGuestRecords(userId)
            history.claimGuestSources(userId)
            history.claimGuestEvents(userId)
        }

        syncPending(userId)

        val remote = client.from("commitments").select {
            filter { eq("user_id", userId) }
        }.decodeList<CloudCommitment>()
        database.withTransaction {
            remote.forEach { record ->
                if (dao.byId(record.id)?.dirty != true) dao.upsert(listOf(record.toLocal()))
            }
        }
        val remoteSources = client.from("sources").select {
            filter { eq("user_id", userId) }
        }.decodeList<CloudSource>()
        val remoteEvents = client.from("reminder_events").select {
            filter { eq("user_id", userId) }
        }.decodeList<CloudEvent>()
        val archivedIds = HistoryArchive(database).archivedIds(userId)
        database.withTransaction {
            remoteSources.forEach { history.insertSource(it.toLocal()) }
            remoteEvents.filterNot { it.id in archivedIds }.forEach { history.insertEvent(it.toLocal()) }
        }
    }

    suspend fun syncPending(userId: String) {
        val dao = database.commitments()
        val history = database.history()
        dao.pending(userId).forEach { local ->
            client.from("commitments").upsert(local.toCloud(userId))
            dao.markSynced(local.id, userId, local.updatedAtMillis)
        }
        history.pendingSources(userId).forEach { local ->
            client.from("sources").upsert(local.toCloud(userId))
            history.markSourceSynced(userId, local.id)
        }
        history.pendingEvents(userId).forEach { local ->
            insertEventIfAbsent(local.toCloud(userId))
            history.markEventSynced(userId, local.id)
        }
    }

    private suspend fun insertEventIfAbsent(event: CloudEvent) {
        val existing = client.from("reminder_events").select {
            filter { eq("id", event.id) }
        }.decodeList<CloudEvent>()
        if (existing.isEmpty()) client.from("reminder_events").insert(event)
    }

    suspend fun clearAccountCache(userId: String) {
        database.commitments().removeAccountCache(userId)
        database.history().removeSourceCache(userId)
        database.history().removeEventCache(userId)
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

private fun SourceEntity.toCloud(userId: String) = CloudSource(
    id, userId, commitmentId, originType, originKey, title, excerpt, originalUri,
    permissionState, Instant.ofEpochMilli(capturedAtMillis).toString()
)

private fun CloudSource.toLocal() = SourceEntity(
    id, userId, commitmentId, originType, originKey, title, excerpt, originalUri,
    permissionState, Instant.parse(capturedAt).toEpochMilli()
)

private fun ReminderEventEntity.toCloud(userId: String) = CloudEvent(
    id, userId, commitmentId, eventType, Json.parseToJsonElement(changeData), actor,
    idempotencyKey, Instant.ofEpochMilli(occurredAtMillis).toString()
)

private fun CloudEvent.toLocal() = ReminderEventEntity(
    id, userId, commitmentId, eventType, changeData.toString(), actor, idempotencyKey,
    Instant.parse(occurredAt).toEpochMilli()
)
