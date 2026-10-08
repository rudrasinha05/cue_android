package com.rudrasinha.cue.data

import androidx.room.withTransaction
import com.rudrasinha.cue.reminders.ReminderScheduler
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/** Exports exact recent and archived events for one profile. */
class AccountData(private val database: CueDatabase, private val scheduler: ReminderScheduler,
    private val cloud: CloudCommitments) {
    companion object {
        fun mergeEvents(current: List<ReminderEventEntity>, archived: List<ReminderEventEntity>): List<ReminderEventEntity> {
            // The same immutable event may appear both in recent sync and an archive.
            // Never silently prefer one if their persisted content differs.
            val unique = linkedMapOf<String, ReminderEventEntity>()
            (current + archived).forEach { event ->
                val previous = unique[event.id]
                require(previous == null || previous.copy(dirty = false) == event.copy(dirty = false)) {
                    "Conflicting history event ${event.id}; export cancelled to protect your data."
                }
                if (previous == null) unique[event.id] = event
            }
            return unique.values.sortedWith(compareBy<ReminderEventEntity> { it.occurredAtMillis }
                .thenBy { it.id })
        }
    }

    suspend fun export(ownerId: String): ByteArray {
        if (ownerId != "guest") cloud.restoreAndClaim(ownerId)
        val reminders = database.commitments().allForOwner(ownerId)
        val sources = database.history().sourcesForOwner(ownerId)
        val current = database.history().eventsForOwner(ownerId)
        val archived = database.history().batches(ownerId).flatMap(HistoryArchive::read)
        val events = mergeEvents(current, archived)
        val root = JSONObject().put("format", "cue-export-v1")
            .put("exported_at", Instant.now().toString())
            .put("reminders", JSONArray().apply { reminders.forEach { item ->
                put(JSONObject().put("id", item.id).put("title", item.title)
                    .put("details", item.details ?: JSONObject.NULL)
                    .put("due_at_millis", item.dueAtMillis ?: JSONObject.NULL)
                    .put("timezone", item.timezone).put("status", item.status)
                    .put("tone_id", item.toneId).put("chain_enabled", item.chainEnabled)
                    .put("chain_offsets", item.chainOffsets)
                    .put("updated_at_millis", item.updatedAtMillis))
            } })
            .put("sources", JSONArray().apply { sources.forEach { source ->
                put(JSONObject().put("id", source.id).put("reminder_id", source.commitmentId)
                    .put("type", source.originType).put("origin_key", source.originKey ?: JSONObject.NULL)
                    .put("title", source.title ?: JSONObject.NULL)
                    .put("excerpt", source.excerpt ?: JSONObject.NULL)
                    .put("original_uri", source.originalUri ?: JSONObject.NULL)
                    .put("permission_state", source.permissionState)
                    .put("captured_at_millis", source.capturedAtMillis))
            } })
            .put("history", JSONArray().apply { events.forEach { event ->
                put(JSONObject().put("id", event.id).put("reminder_id", event.commitmentId)
                    .put("type", event.eventType).put("change_data", event.changeData)
                    .put("actor", event.actor).put("idempotency_key", event.idempotencyKey)
                    .put("occurred_at_millis", event.occurredAtMillis))
            } })
        return root.toString(2).toByteArray(Charsets.UTF_8)
    }

    suspend fun delete(ownerId: String) {
        if (ownerId != "guest") cloud.deleteAccountData()
        deleteLocal(ownerId)
    }

    suspend fun deleteLocal(ownerId: String) {
        scheduler.cancelOwner(ownerId)
        database.withTransaction {
            database.history().deleteBatchesForOwner(ownerId)
            database.history().deleteEventsForOwner(ownerId)
            database.history().deleteSourcesForOwner(ownerId)
            database.commitments().deleteForOwner(ownerId)
        }
    }
}
