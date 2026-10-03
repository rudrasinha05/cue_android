package com.rudrasinha.cue.data

import androidx.room.withTransaction
import com.rudrasinha.cue.reminders.ReminderScheduler
import com.rudrasinha.cue.reminders.ReminderTones
import com.rudrasinha.cue.reminders.ChainSchedule
import org.json.JSONObject
import java.time.ZoneId
import java.util.UUID

data class CaptureOrigin(
    val type: String,
    val title: String? = null,
    val excerpt: String? = null,
    val uri: String? = null,
    val key: String? = null
)

class CommitmentActions(
    private val database: CueDatabase,
    private val scheduler: ReminderScheduler,
    private val cloud: CloudCommitments
) {
    private val dao get() = database.commitments()

    suspend fun save(ownerId: String, id: String?, title: String, details: String?, dueAt: Long?,
        origin: CaptureOrigin = CaptureOrigin("manual"), toneId: String = ReminderTones.DEFAULT): Boolean {
        require(title.isNotBlank()) { "Add a title." }
        require(dueAt == null || dueAt > System.currentTimeMillis()) { "Choose a future time." }
        val old = id?.let { dao.byId(it) }
        require(id == null || old?.ownerId == ownerId) { "Reminder is unavailable." }
        val now = System.currentTimeMillis()
        val item = CommitmentEntity(
            id = old?.id ?: UUID.randomUUID().toString(), ownerId = ownerId,
            title = title.trim(), details = details?.trim()?.ifBlank { null },
            dueAtMillis = dueAt, timezone = ZoneId.systemDefault().id, status = "active",
            updatedAtMillis = maxOf(now, (old?.updatedAtMillis ?: 0L) + 1),
            dirty = ownerId != "guest", toneId = ReminderTones.selected(toneId).id,
            chainEnabled = old?.chainEnabled ?: false,
            chainOffsets = old?.chainOffsets ?: "1440,60",
            syncedAtMillis = old?.syncedAtMillis ?: 0L
        )
        database.withTransaction {
            dao.upsert(listOf(item))
            if (old == null) database.history().insertSource(SourceEntity(
                id = UUID.randomUUID().toString(), ownerId = ownerId, commitmentId = item.id,
                originType = origin.type, originKey = origin.key,
                title = origin.title, excerpt = origin.excerpt?.take(2000),
                originalUri = origin.uri, capturedAtMillis = now, dirty = ownerId != "guest"
            ))
            database.history().insertEvent(event(item, if (old == null) "created" else "updated"))
        }
        if (old != null) { scheduler.cancel(old); scheduler.silence(old) }
        scheduler.schedule(item)
        return sync(ownerId)
    }

    /** Attach a second capture to an existing reminder without creating another alarm. */
    suspend fun linkSource(ownerId: String, id: String, origin: CaptureOrigin): Boolean {
        val key = origin.key ?: return false
        val reminder = dao.byId(id) ?: return false
        if (reminder.ownerId != ownerId || reminder.status != "active") return false
        val inserted = database.withTransaction {
            if (database.history().sourceByOrigin(ownerId, origin.type, key) != null) return@withTransaction false
            val now = System.currentTimeMillis()
            val source = SourceEntity(UUID.randomUUID().toString(), ownerId, id, origin.type, key,
                origin.title, origin.excerpt?.take(2000), origin.uri,
                capturedAtMillis = now, dirty = ownerId != "guest")
            if (database.history().insertSource(source) == -1L) return@withTransaction false
            database.history().insertEvent(ReminderEventEntity(UUID.randomUUID().toString(), ownerId,
                id, "source_added", JSONObject().put("source_id", source.id)
                    .put("source_type", origin.type).put("title", reminder.title).toString(),
                "capture", "source:${source.id}", now, ownerId != "guest"))
            true
        }
        if (inserted) sync(ownerId)
        return inserted
    }

    suspend fun complete(ownerId: String, id: String): Boolean = change(ownerId, id, "completed") {
        it.copy(status = "completed", dueAtMillis = null)
    }

    suspend fun archive(ownerId: String, id: String): Boolean = change(ownerId, id, "archived") {
        it.copy(status = "archived", dueAtMillis = null)
    }

    suspend fun setChain(ownerId: String, id: String, offsets: List<Long>?): Boolean =
        change(ownerId, id, "chain_changed") { old ->
            require(offsets == null || (old.dueAtMillis != null && old.dueAtMillis > System.currentTimeMillis())) {
                "Add a future time before enabling reminder nudges."
            }
            old.copy(chainEnabled = offsets != null,
                chainOffsets = offsets?.let(ChainSchedule::encode) ?: old.chainOffsets)
        }

    suspend fun snooze(ownerId: String, id: String): Boolean = change(ownerId, id, "snoozed") {
        it.copy(status = "active", dueAtMillis = System.currentTimeMillis() + 10 * 60_000L)
    }

    suspend fun followUp(ownerId: String, id: String, days: Long): Boolean {
        val previous = dao.byId(id) ?: error("Reminder is unavailable.")
        require(previous.ownerId == ownerId && previous.status in listOf("active", "completed")) {
            "Reminder is unavailable."
        }
        val nextDue = FollowUpTime.at(System.currentTimeMillis(), previous.dueAtMillis,
            days, ZoneId.systemDefault())
        return change(ownerId, id, "follow_up", allowCompleted = true) {
            it.copy(status = "active", dueAtMillis = nextDue)
        }
    }

    suspend fun delete(ownerId: String, id: String): Boolean =
        change(ownerId, id, "deleted", allowCompleted = true) {
            it.copy(status = "cancelled", dueAtMillis = null)
        }

    private suspend fun change(
        ownerId: String, id: String, type: String, allowCompleted: Boolean = false,
        transform: (CommitmentEntity) -> CommitmentEntity
    ): Boolean {
        val old = dao.byId(id) ?: error("Reminder is unavailable.")
        require(old.ownerId == ownerId &&
            (old.status == "active" || (allowCompleted && old.status == "completed"))) {
            "Reminder is unavailable."
        }
        val next = transform(old).copy(
            updatedAtMillis = maxOf(System.currentTimeMillis(), old.updatedAtMillis + 1),
            dirty = ownerId != "guest"
        )
        database.withTransaction {
            dao.upsert(listOf(next))
            database.history().insertEvent(event(next, type))
        }
        scheduler.cancel(old)
        scheduler.silence(old)
        scheduler.schedule(next)
        return sync(ownerId)
    }

    private suspend fun sync(ownerId: String): Boolean =
        ownerId == "guest" || runCatching {
            cloud.syncPending(ownerId)
            HistoryArchive(database).compact(ownerId)
        }.isSuccess

    private fun event(item: CommitmentEntity, type: String): ReminderEventEntity {
        val id = UUID.randomUUID().toString()
        val snapshot = JSONObject().put("title", item.title).put("status", item.status)
            .put("details", item.details).put("due_at_millis", item.dueAtMillis)
            .put("timezone", item.timezone).put("tone_id", item.toneId)
            .put("chain_enabled", item.chainEnabled).put("chain_offsets", item.chainOffsets).toString()
        return ReminderEventEntity(id, item.ownerId, item.id, type, snapshot, "app", id,
            item.updatedAtMillis, item.ownerId != "guest")
    }
}
