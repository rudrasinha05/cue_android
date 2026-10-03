package com.rudrasinha.cue.data

import java.util.Locale

/** Conservative match: identical due time and normalized title, scoped to active owner records. */
object ReminderMatch {
    fun normalizedTitle(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")

    private val filler = setOf("remind", "reminder", "me", "to", "the", "a", "an", "please", "my")
    private val verbs = mapOf(
        "pay" to "pay", "payment" to "pay", "paying" to "pay",
        "submit" to "submit", "submission" to "submit", "send" to "send", "sending" to "send",
        "email" to "email", "mail" to "email", "call" to "call", "phone" to "call",
        "buy" to "buy", "purchase" to "buy", "book" to "book", "booking" to "book",
        "schedule" to "book", "renew" to "renew", "renewal" to "renew",
        "collect" to "collect", "collection" to "collect", "pick" to "collect"
    )
    private val objects = mapOf(
        "bills" to "bill", "fees" to "fee", "documents" to "document",
        "tickets" to "ticket", "subscriptions" to "subscription"
    )

    /** A strict local fallback: one matching action and identical object words at the same due time. */
    private fun equivalentAction(value: String): Pair<String, List<String>>? {
        val words = normalizedTitle(value).split(' ').filter { it.isNotBlank() && it !in filler }
        val actions = words.mapNotNull { verbs[it] }.distinct()
        if (actions.size != 1) return null
        val objectWords = words.filter { it !in verbs }.map { objects[it] ?: it }.sorted()
        return if (objectWords.isEmpty()) null else actions.single() to objectWords
    }

    fun existing(items: List<CommitmentEntity>, title: String): CommitmentEntity? {
        val normalized = normalizedTitle(title)
        if (normalized.isEmpty()) return null
        val action = equivalentAction(title)
        return items.firstOrNull { normalizedTitle(it.title) == normalized ||
            (action != null && equivalentAction(it.title) == action) }
    }
}
