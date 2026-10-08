package com.rudrasinha.cue

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Conservative row parser: a row needs an explicit weekday, start time, and faculty code. */
internal object ProfessorTimetable {
    data class Lecture(val title: String, val day: DayOfWeek, val start: LocalTime, val row: String)
    private val weekdays = mapOf("mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY,
        "wed" to DayOfWeek.WEDNESDAY, "thu" to DayOfWeek.THURSDAY,
        "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY,
        "sun" to DayOfWeek.SUNDAY)
    private val dayPattern = Regex("\\b(mon(?:day)?|tue(?:sday)?|wed(?:nesday)?|thu(?:rsday)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)\\b", RegexOption.IGNORE_CASE)
    private val timePattern = Regex("\\b(0?[1-9]|1[0-9]|2[0-3])[:.](\\d{2})\\s*(am|pm)?\\b", RegexOption.IGNORE_CASE)

    fun matches(text: String, codes: String): List<Lecture> {
        val tokens = codes.split(',', ';', '\n').map { it.trim() }.filter { it.length >= 2 }
        if (tokens.isEmpty()) return emptyList()
        return text.lineSequence().take(300).mapNotNull { line ->
            val day = dayPattern.find(line)?.value?.lowercase()?.take(3)?.let(weekdays::get)
                ?: return@mapNotNull null
            val time = timePattern.find(line) ?: return@mapNotNull null
            val token = tokens.firstOrNull { code ->
                Regex("(?<![\\p{L}\\p{N}])${Regex.escape(code)}(?![\\p{L}\\p{N}])",
                    RegexOption.IGNORE_CASE).containsMatchIn(line)
            } ?: return@mapNotNull null
            val rawHour = time.groupValues[1].toInt()
            val minute = time.groupValues[2].toInt()
            val ampm = time.groupValues[3].lowercase()
            if (minute > 59 || (ampm.isNotEmpty() && rawHour !in 1..12)) return@mapNotNull null
            val hour = if (ampm == "pm") rawHour % 12 + 12
                else if (ampm == "am") rawHour % 12 else rawHour
            Lecture("Lecture · $token", day, LocalTime.of(hour, minute), line.trim().take(200))
        }.distinctBy { Triple(it.day, it.start, it.title) }.toList()
    }

    fun nextFourWeeks(lecture: Lecture, today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()): List<Long> = (0..4).mapNotNull { week ->
        val daysAhead = (lecture.day.value - today.dayOfWeek.value + 7) % 7 + week * 7
        today.plusDays(daysAhead.toLong()).atTime(lecture.start).atZone(zone).toInstant()
            .toEpochMilli().takeIf { it > System.currentTimeMillis() }
    }.take(4)
}
