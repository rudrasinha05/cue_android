package com.rudrasinha.cue

import android.content.Context
import android.provider.CalendarContract

/** Reads visible Google calendar events only after Android's calendar permission is granted. */
internal object GoogleCalendarImport {
    data class Event(val eventId: Long, val startsAt: Long, val title: String,
        val location: String?, val calendar: String) {
        val key get() = "google-calendar:$eventId:$startsAt"
        val alertAt: Long get() {
            val leadMinutes = when {
                Regex("flight|train|bus|ticket|travel|departure|boarding", RegexOption.IGNORE_CASE)
                    .containsMatchIn(title) -> 120L
                Regex("interview|exam|appointment", RegexOption.IGNORE_CASE)
                    .containsMatchIn(title) -> 30L
                else -> 15L
            }
            return maxOf(System.currentTimeMillis() + 60_000, startsAt - leadMinutes * 60_000)
        }
    }

    fun upcoming(context: Context): List<Event> {
        val now = System.currentTimeMillis()
        val until = now + 30L * 24 * 60 * 60 * 1000
        val columns = arrayOf(CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE)
        val events = mutableListOf<Event>()
        CalendarContract.Instances.query(context.contentResolver, columns, now, until)?.use { cursor ->
            while (cursor.moveToNext() && events.size < 100) {
                if (cursor.getString(5) != "com.google") continue
                val title = cursor.getString(2)?.trim()?.take(120).orEmpty()
                val start = cursor.getLong(1)
                if (title.isBlank() || start <= now + 60_000) continue
                events += Event(cursor.getLong(0), start, title,
                    cursor.getString(3)?.take(160), cursor.getString(4).orEmpty())
            }
        }
        return events.distinctBy(Event::key).sortedBy(Event::startsAt)
    }
}
