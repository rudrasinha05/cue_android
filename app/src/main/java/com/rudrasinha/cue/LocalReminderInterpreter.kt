package com.rudrasinha.cue

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.entityextraction.DateTimeEntity
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Local date inference. The language model suggests dates; the confidence gate decides auto-save. */
internal object LocalReminderInterpreter {
    private val modelLock = Mutex()
    private val extractor by lazy {
        EntityExtraction.getClient(EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build())
    }
    private var ready = false
    private var lastAttempt = 0L

    suspend fun find(text: String): ConfidentReminder? {
        val lines = text.lineSequence().map(String::trim).filter(String::isNotBlank)
            .take(21).toList()
        if (lines.size > 20) return null // Too much context for unattended creation.
        val explicit = lines.mapNotNull { line ->
            if (actionableReminder(line)) suggestedDue(line)?.let { ConfidentReminder(line.take(100), it) }
            else null
        }
        if (explicit.size > 1) return null
        if (explicit.size == 1) return explicit.single()
        if (lines.none(::actionableReminder)) return null

        return withContext(Dispatchers.IO) {
            modelLock.withLock {
                if (!ready) {
                    val now = System.currentTimeMillis()
                    if (now - lastAttempt < 60 * 60 * 1000L) return@withLock null
                    lastAttempt = now
                    try { Tasks.await(extractor.downloadModelIfNeeded()); ready = true }
                    catch (_: Exception) { return@withLock null }
                }
                val candidates = mutableListOf<ConfidentReminder>()
                for (line in lines.filter(::actionableReminder)) {
                    if (line.length > 250) continue
                    val params = EntityExtractionParams.Builder(line)
                        .setPreferredLocale(Locale.US).build()
                    val annotations = try { Tasks.await(extractor.annotate(params)) }
                        catch (_: Exception) { return@withLock null }
                    val dates = annotations.filter { explicitDateTimeSpan(it.annotatedText) }
                        .flatMap { it.entities }.filter { it.type == Entity.TYPE_DATE_TIME }
                        .mapNotNull { it.asDateTimeEntity() }
                        .filter { it.dateTimeGranularity >= DateTimeEntity.GRANULARITY_HOUR &&
                            it.timestampMillis > System.currentTimeMillis() }
                    if (dates.size > 1) return@withLock null
                    dates.singleOrNull()?.let { candidates += ConfidentReminder(line.take(100), it.timestampMillis) }
                    if (candidates.size > 1) return@withLock null
                }
                candidates.singleOrNull()
            }
        }
    }
}
