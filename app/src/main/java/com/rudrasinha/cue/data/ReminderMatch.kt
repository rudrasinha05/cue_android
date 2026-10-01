package com.rudrasinha.cue.data

import java.util.Locale

/** Conservative match: identical due time and normalized title, scoped to active owner records. */
object ReminderMatch {
    fun normalizedTitle(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")

    fun existing(items: List<CommitmentEntity>, title: String): CommitmentEntity? {
        val normalized = normalizedTitle(title)
        if (normalized.isEmpty()) return null
        return items.firstOrNull { normalizedTitle(it.title) == normalized }
    }
}
