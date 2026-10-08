package com.rudrasinha.cue.data

import androidx.room.withTransaction
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
private data class PackedEvent(
    val id: String, val ownerId: String, val commitmentId: String,
    val eventType: String, val changeData: String, val actor: String,
    val idempotencyKey: String, val occurredAtMillis: Long
) {
    fun toEntity() = ReminderEventEntity(id, ownerId, commitmentId, eventType,
        changeData, actor, idempotencyKey, occurredAtMillis)
}

/** Keeps the exact event payloads; the cloud still retains raw copies for account recovery. */
class HistoryArchive(private val database: CueDatabase) {
    suspend fun compact(ownerId: String) {
        if (ownerId == "guest") return
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        while (true) {
            val saved = database.withTransaction {
                val events = database.history().compactable(ownerId, cutoff)
                if (events.size < 20) return@withTransaction false
                val batch = pack(ownerId, events)
                // Verify lossless reconstruction BEFORE removing the original events.
                // If writing the batch fails the Room transaction keeps every raw event.
                val verified = read(batch)
                require(verified.map { it.id } == events.map { it.id }) {
                    "History verification failed before compaction."
                }
                val inserted = database.history().insertBatch(batch)
                if (inserted == -1L) {
                    // A retry must not trust a duplicate primary key with different/corrupt data.
                    val existing = database.history().batchById(ownerId, batch.id)
                        ?: error("Previously written history batch is missing.")
                    require(existing.checksum == batch.checksum &&
                        existing.eventCount == batch.eventCount &&
                        read(existing).map { it.id } == verified.map { it.id }) {
                        "Previously written history batch differs."
                    }
                }
                database.history().removeArchivedEvents(ownerId, events.map { it.id })
                true
            }
            if (!saved) break
        }
    }

    suspend fun archivedIds(ownerId: String): Set<String> = database.history().batches(ownerId)
        .flatMap { batch -> read(batch).map { it.id } }
        .toSet()

    companion object {
        fun pack(ownerId: String, events: List<ReminderEventEntity>): HistoryBatchEntity {
            require(events.isNotEmpty() && events.all { it.ownerId == ownerId && !it.dirty })
            val ordered = events.sortedWith(compareBy<ReminderEventEntity> { it.occurredAtMillis }
                .thenBy { it.id })
            val canonical = Json.encodeToString(ordered.map {
                PackedEvent(it.id, it.ownerId, it.commitmentId, it.eventType,
                    it.changeData, it.actor, it.idempotencyKey, it.occurredAtMillis)
            }).toByteArray(Charsets.UTF_8)
            val zipped = ByteArrayOutputStream().also { out ->
                GZIPOutputStream(out).use { it.write(canonical) }
            }.toByteArray()
            return HistoryBatchEntity(UUID.nameUUIDFromBytes(canonical).toString(), ownerId,
                ordered.first().occurredAtMillis, ordered.last().occurredAtMillis,
                ordered.size, canonical.sha256(), zipped, System.currentTimeMillis(),
                searchIndex = ordered.asSequence().flatMap { event ->
                    val title = runCatching { Json.parseToJsonElement(event.changeData)
                        .jsonObject["title"]?.jsonPrimitive?.contentOrNull.orEmpty() }.getOrDefault("")
                    sequenceOf(event.eventType.take(48), title.take(120), event.commitmentId)
                }.distinct().joinToString(" ").lowercase())
        }

        fun read(batch: HistoryBatchEntity): List<ReminderEventEntity> {
            val bytes = GZIPInputStream(ByteArrayInputStream(batch.payload)).use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(out.size() + count <= 2 * 1024 * 1024) { "History batch is too large." }
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
            require(bytes.sha256() == batch.checksum) { "History checksum mismatch." }
            val events = Json.decodeFromString<List<PackedEvent>>(bytes.toString(Charsets.UTF_8))
                .map { it.toEntity() }
            require(events.size == batch.eventCount && events.all { it.ownerId == batch.ownerId }) {
                "History count or owner mismatch."
            }
            return events
        }
    }
}

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this).joinToString("") { "%02x".format(it.toInt() and 0xff) }
