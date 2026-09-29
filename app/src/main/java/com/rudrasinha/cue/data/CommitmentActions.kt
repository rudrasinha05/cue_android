package com.rudrasinha.cue.data

import com.rudrasinha.cue.reminders.ReminderScheduler
import java.time.ZoneId
import java.util.UUID

class CommitmentActions(
    private val dao: CommitmentDao,
    private val scheduler: ReminderScheduler,
    private val cloud: CloudCommitments
) {
    suspend fun save(ownerId: String, id: String?, title: String, details: String?, dueAt: Long?): Boolean {
        require(title.isNotBlank()) { "Add a title." }
        require(dueAt == null || dueAt > System.currentTimeMillis()) { "Choose a future time." }
        val old = id?.let { dao.byId(it) }
        require(id == null || old?.ownerId == ownerId) { "Commitment is unavailable." }
        val now = System.currentTimeMillis()
        val item = CommitmentEntity(
            id = old?.id ?: UUID.randomUUID().toString(), ownerId = ownerId,
            title = title.trim(), details = details?.trim()?.ifBlank { null },
            dueAtMillis = dueAt, timezone = ZoneId.systemDefault().id, status = "active",
            updatedAtMillis = maxOf(now, (old?.updatedAtMillis ?: 0L) + 1),
            dirty = ownerId != "guest"
        )
        dao.upsert(listOf(item))
        scheduler.schedule(item)
        return sync(ownerId)
    }

    suspend fun complete(ownerId: String, id: String): Boolean = change(ownerId, id) {
        it.copy(status = "completed", dueAtMillis = null)
    }

    suspend fun archive(ownerId: String, id: String): Boolean = change(ownerId, id) {
        it.copy(status = "archived", dueAtMillis = null)
    }

    suspend fun snooze(ownerId: String, id: String): Boolean = change(ownerId, id) {
        it.copy(status = "active", dueAtMillis = System.currentTimeMillis() + 10 * 60_000L)
    }

    private suspend fun change(
        ownerId: String, id: String, transform: (CommitmentEntity) -> CommitmentEntity
    ): Boolean {
        val old = dao.byId(id) ?: error("Commitment is unavailable.")
        require(old.ownerId == ownerId && old.status == "active") { "Commitment is unavailable." }
        val next = transform(old).copy(
            updatedAtMillis = maxOf(System.currentTimeMillis(), old.updatedAtMillis + 1),
            dirty = ownerId != "guest"
        )
        dao.upsert(listOf(next))
        scheduler.schedule(next)
        return sync(ownerId)
    }

    private suspend fun sync(ownerId: String): Boolean =
        ownerId == "guest" || runCatching { cloud.syncPending(ownerId) }.isSuccess
}
