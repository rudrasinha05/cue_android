package com.rudrasinha.cue.data

import androidx.room.withTransaction
import android.util.Base64
import com.rudrasinha.cue.reminders.ReminderScheduler
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import java.time.Instant
import java.util.UUID
import org.json.JSONObject
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
    @SerialName("tone_id") val toneId: String,
    @SerialName("chain_enabled") val chainEnabled: Boolean = false,
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

@Serializable
private data class CloudHistoryBatch(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("first_at_millis") val firstAtMillis: Long,
    @SerialName("last_at_millis") val lastAtMillis: Long,
    @SerialName("event_count") val eventCount: Int,
    val checksum: String,
    @SerialName("payload_base64") val payloadBase64: String,
    @SerialName("search_index") val searchIndex: String,
    @SerialName("created_at_millis") val createdAtMillis: Long
)

class CloudCommitments(private val database: CueDatabase, private val client: SupabaseClient,
    private val scheduler: ReminderScheduler) {
    // The same UUID is used locally and remotely. Repeated sign-in safely retries the upload.
    suspend fun restoreAndClaim(userId: String) {
        val dao = database.commitments()
        val history = database.history()
        dao.guestRecords().forEach { local ->
            val existing = client.from("commitments").select {
                filter { eq("id", local.id); eq("user_id", userId) }
            }.decodeList<CloudCommitment>()
            if (existing.isEmpty()) client.from("commitments").insert(local.toCloud(userId))
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
        val rescheduled = mutableListOf<Pair<CommitmentEntity?, CommitmentEntity>>()
        database.withTransaction {
            remote.forEach { record ->
                val old = dao.byId(record.id)
                if (old?.dirty != true) {
                    val next = record.toLocal()
                    if (old != next) {
                        dao.upsert(listOf(next))
                        rescheduled += old to next
                    }
                }
            }
        }
        rescheduled.forEach { (old, next) ->
            old?.let { scheduler.cancel(it); scheduler.silence(it) }
            scheduler.schedule(next)
        }
        val remoteSources = client.from("sources").select {
            filter { eq("user_id", userId) }
        }.decodeList<CloudSource>()
        val remoteBatches = client.from("history_batches").select {
            filter { eq("user_id", userId) }
        }.decodeList<CloudHistoryBatch>()
        val remoteEvents = client.from("reminder_events").select {
            filter { eq("user_id", userId) }
        }.decodeList<CloudEvent>()
        // Verify a complete batch before exposing it or skipping its raw cloud events.
        remoteBatches.forEach { record ->
            val batch = record.toLocal(userId)
            HistoryArchive.read(batch)
            history.insertBatch(batch)
        }
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
            val remote = client.from("commitments").select {
                filter { eq("id", local.id); eq("user_id", userId) }
            }.decodeList<CloudCommitment>().singleOrNull()
            if (remote == null) {
                client.from("commitments").insert(local.toCloud(userId))
                dao.markSynced(local.id, userId, local.updatedAtMillis)
            } else if (remote.matches(local)) {
                dao.markSynced(local.id, userId, local.updatedAtMillis)
            } else if (syncDecision(local.syncedAtMillis,
                    Instant.parse(remote.updatedAt).toEpochMilli()) == SyncDecision.UPLOAD) {
                val changed = client.from("commitments").update(local.toCloud(userId)) {
                    select()
                    filter {
                        eq("id", local.id)
                        eq("user_id", userId)
                        eq("updated_at", remote.updatedAt)
                    }
                }.decodeList<CloudCommitment>()
                if (changed.size != 1) error("Reminder changed on another device. Retry sync.")
                dao.markSynced(local.id, userId, local.updatedAtMillis)
            } else {
                val key = "conflict:${local.id}:${local.updatedAtMillis}:${remote.updatedAt}"
                val snapshot = JSONObject().put("title", local.title).put("status", local.status)
                    .put("details", local.details).put("due_at_millis", local.dueAtMillis)
                    .put("timezone", local.timezone).put("tone_id", local.toneId)
                    .put("chain_enabled", local.chainEnabled)
                    .put("winner_updated_at", remote.updatedAt).toString()
                val applied = database.withTransaction {
                    // A newer local edit can appear while the network request is in flight.
                    if (dao.byId(local.id)?.updatedAtMillis == local.updatedAtMillis) {
                        history.insertEvent(ReminderEventEntity(
                            UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8)).toString(),
                            userId, local.id, "sync_conflict", snapshot, "sync", key,
                            System.currentTimeMillis(), true))
                        dao.upsert(listOf(remote.toLocal()))
                        true
                    } else false
                }
                if (applied) {
                    scheduler.cancel(local)
                    scheduler.silence(local)
                    scheduler.schedule(remote.toLocal())
                }
            }
        }
        history.pendingSources(userId).forEach { local ->
            client.from("sources").upsert(local.toCloud(userId))
            history.markSourceSynced(userId, local.id)
        }
        history.pendingEvents(userId).forEach { local ->
            insertEventIfAbsent(local.toCloud(userId))
            history.markEventSynced(userId, local.id)
        }
        HistoryArchive(database).compact(userId)
        history.batches(userId).forEach { batch ->
            val existing = client.from("history_batches").select {
                filter { eq("id", batch.id); eq("user_id", userId) }
            }.decodeList<CloudHistoryBatch>().singleOrNull()
            if (existing == null) {
                HistoryArchive.read(batch)
                client.from("history_batches").insert(batch.toCloud(userId))
            } else require(existing.checksum == batch.checksum &&
                existing.eventCount == batch.eventCount) { "Cloud history archive differs from this device." }
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
    toneId = toneId,
    chainEnabled = chainEnabled,
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
    updatedAtMillis = Instant.parse(updatedAt).toEpochMilli(),
    toneId = toneId,
    chainEnabled = chainEnabled,
    syncedAtMillis = Instant.parse(updatedAt).toEpochMilli()
)

private fun CloudCommitment.matches(local: CommitmentEntity) =
    title == local.title && details == local.details &&
        dueAt?.let { Instant.parse(it).toEpochMilli() } == local.dueAtMillis &&
        timezone == local.timezone && toneId == local.toneId &&
        chainEnabled == local.chainEnabled && status == local.status &&
        Instant.parse(updatedAt).toEpochMilli() == local.updatedAtMillis

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

private fun HistoryBatchEntity.toCloud(userId: String) = CloudHistoryBatch(
    id, userId, firstAtMillis, lastAtMillis, eventCount, checksum,
    Base64.encodeToString(payload, Base64.NO_WRAP), searchIndex, createdAtMillis
)

private fun CloudHistoryBatch.toLocal(expectedOwner: String): HistoryBatchEntity {
    require(userId == expectedOwner) { "History belongs to another account." }
    return HistoryBatchEntity(id, userId, firstAtMillis, lastAtMillis, eventCount, checksum,
        Base64.decode(payloadBase64, Base64.DEFAULT), createdAtMillis, searchIndex)
}
