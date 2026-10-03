package com.rudrasinha.cue

import java.time.Clock
import java.time.LocalDate
import java.util.Locale

/** Only clear English date/time phrases are suggested; the user confirms in the editor. */
internal fun suggestedDue(text: String, clock: Clock = Clock.systemDefaultZone()): Long? {
    val normalized = text.lowercase(Locale.ROOT)
    val day = when {
        Regex("\\btomorrow\\b").containsMatchIn(normalized) -> LocalDate.now(clock).plusDays(1)
        Regex("\\btoday\\b").containsMatchIn(normalized) -> LocalDate.now(clock)
        else -> return null
    }
    val twelveHour = Regex("\\b(1[0-2]|0?[1-9])(?::([0-5]\\d))?\\s*(am|pm)\\b")
        .find(normalized)
    val hour: Int
    val minute: Int
    if (twelveHour != null) {
        hour = twelveHour.groupValues[1].toInt() % 12 +
            if (twelveHour.groupValues[3] == "pm") 12 else 0
        minute = twelveHour.groupValues[2].toIntOrNull() ?: 0
    } else {
        val military = Regex("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b").find(normalized)
            ?: return null
        hour = military.groupValues[1].toInt()
        minute = military.groupValues[2].toInt()
    }
    return day.atTime(hour, minute).atZone(clock.zone).toInstant().toEpochMilli()
        .takeIf { it > clock.millis() }
}

internal data class ConfidentReminder(val title: String, val dueAtMillis: Long)

/** Only an explicit action or appointment is safe to create without confirmation. */
internal fun actionableReminder(line: String): Boolean = Regex(
    "\\b(call|meet|meeting|appointment|pay|submit|send|email|book|buy|pick up|" +
        "collect|renew|visit|take|bring|attend|register|deadline|due|interview|" +
        "exam|flight|train|doctor|dentist|bill|class|event|webinar|follow up)\\b",
    RegexOption.IGNORE_CASE).containsMatchIn(line)

/** A model annotation must contain the date and clock time together, not separate UI fragments. */
internal fun explicitDateTimeSpan(span: String): Boolean {
    val date = Regex("\\b(today|tomorrow|mon(day)?|tue(sday)?|wed(nesday)?|" +
        "thu(rsday)?|fri(day)?|sat(urday)?|sun(day)?|jan(uary)?|feb(ruary)?|mar(ch)?|" +
        "apr(il)?|may|jun(e)?|jul(y)?|aug(ust)?|sep(tember)?|oct(ober)?|" +
        "nov(ember)?|dec(ember)?|[0-3]?\\d[/.-][01]?\\d)\\b", RegexOption.IGNORE_CASE)
    val time = Regex("\\b(1[0-2]|0?[1-9])(?::[0-5]\\d)?\\s*(am|pm)\\b|" +
        "\\b([01]?\\d|2[0-3]):[0-5]\\d\\b", RegexOption.IGNORE_CASE)
    return date.containsMatchIn(span) && time.containsMatchIn(span)
}

/** Require the date and time on one line, so OCR does not combine unrelated screen content. */
internal fun confidentReminder(text: String, clock: Clock = Clock.systemDefaultZone()): ConfidentReminder? {
    val candidates = text.lineSequence().map(String::trim).filter(String::isNotBlank)
        .mapNotNull { line -> if (!actionableReminder(line)) null else
            suggestedDue(line, clock)?.let { ConfidentReminder(line.take(100), it) } }
        .take(2).toList()
    return candidates.singleOrNull()
}
