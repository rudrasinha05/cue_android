package com.rudrasinha.cue.assistant

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.os.Build
import com.rudrasinha.cue.settings.ThemeStore
import com.rudrasinha.cue.data.CueDatabase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opt-in, on-device aggregate. No raw app timeline or screen text is kept. */
class UsagePatternStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("cue_routine_profile", Context.MODE_PRIVATE)

    fun hasAccess(): Boolean = context.getSystemService(AppOpsManager::class.java)
        .checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
        AppOpsManager.MODE_ALLOWED

    suspend fun capture(ownerId: String) {
        if (!ThemeStore(context).usageLearningEnabled.first() || !hasAccess()) return
        val reminderHours = withContext(Dispatchers.IO) {
            val history = CueDatabase.get(context).history().eventsForOwner(ownerId)
            val bins = IntArray(24)
            history.asSequence().filter { it.eventType in setOf("created", "updated", "follow_up") }
                .toList().takeLast(200).forEach { item ->
                    val due = runCatching { JSONObject(item.changeData).optLong("due_at_millis") }
                        .getOrDefault(0L)
                    if (due > 0) {
                        val hour = Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).hour
                        bins[hour] = minOf(30, bins[hour] + 1)
                    }
                }
            JSONArray().apply { bins.forEach { put(it) } }
        }
        val now = System.currentTimeMillis()
        val key = "owner:$ownerId"
        synchronized(prefs) {
            val profile = runCatching { JSONObject(prefs.getString(key, "{}")) }.getOrDefault(JSONObject())
            val last = profile.optLong("last", now - 24 * 60 * 60_000L)
            if (now - last < 30 * 60_000L) return
            val hours = profile.optJSONArray("hours") ?: JSONArray().apply { repeat(24) { put(0) } }
            val days = profile.optJSONArray("days") ?: JSONArray()
            val apps = profile.optJSONObject("apps") ?: JSONObject()
            val seen = mutableSetOf<String>()
            repeat(days.length()) { index -> seen += days.optString(index) }
            val events = context.getSystemService(UsageStatsManager::class.java)
                .queryEvents(maxOf(last + 1, now - 24 * 60 * 60_000L), now)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val foreground = if (Build.VERSION.SDK_INT >= 29)
                    UsageEvents.Event.ACTIVITY_RESUMED else UsageEvents.Event.MOVE_TO_FOREGROUND
                if (event.eventType != foreground ||
                    event.packageName == context.packageName) continue
                val stamp = Instant.ofEpochMilli(event.timeStamp).atZone(ZoneId.systemDefault())
                hours.put(stamp.hour, minOf(100, hours.optInt(stamp.hour) + 1))
                seen += stamp.toLocalDate().toString()
                val name = event.packageName ?: continue
                if (apps.has(name) || apps.length() < 12)
                    apps.put(name, minOf(100, apps.optInt(name) + 1))
            }
            val recentDays = seen.sorted().takeLast(14)
            profile.put("hours", hours).put("reminderHours", reminderHours)
                .put("apps", apps).put("days", JSONArray(recentDays))
                .put("last", now)
            prefs.edit().putString(key, profile.toString()).apply()
        }
    }

    fun summary(ownerId: String): String {
        val profile = runCatching { JSONObject(prefs.getString("owner:$ownerId", "{}")) }
            .getOrDefault(JSONObject())
        val hours = profile.optJSONArray("hours") ?: return "No routine learned yet"
        val hour = (8..21).maxByOrNull { hours.optInt(it) } ?: return "No routine learned yet"
        val count = (0..23).sumOf(hours::optInt)
        return if (count < 10 || (profile.optJSONArray("days")?.length() ?: 0) < 2)
            "Learning from app activity on this device"
        else "Active around ${if (hour % 12 == 0) 12 else hour % 12}${if (hour < 12) "am" else "pm"} · ${profile.optJSONArray("days")?.length() ?: 0} days sampled"
    }

    /** Only for an explicit undated reminder; never moves an extracted deadline. */
    fun nextRoutineTime(ownerId: String, now: Long = System.currentTimeMillis()): Long? {
        val profile = runCatching { JSONObject(prefs.getString("owner:$ownerId", "{}")) }
            .getOrDefault(JSONObject())
        val hours = profile.optJSONArray("hours") ?: return null
        val reminders = profile.optJSONArray("reminderHours") ?: JSONArray()
        if (((0..23).sumOf(hours::optInt) < 10 ||
            (profile.optJSONArray("days")?.length() ?: 0) < 2) &&
            (0..23).sumOf(reminders::optInt) < 5) return null
        fun score(hour: Int) = hours.optInt(hour) + 2 * reminders.optInt(hour)
        val max = (8..21).maxOf(::score)
        val zone = ZoneId.systemDefault()
        val local = Instant.ofEpochMilli(now).atZone(zone)
        for (offset in 0..1) for (hour in 8..21) {
            if (score(hour) < maxOf(3, max * 2 / 3)) continue
            val option = LocalDate.from(local).plusDays(offset.toLong()).atTime(hour, 0)
                .atZone(zone).toInstant().toEpochMilli()
            if (option > now + 45 * 60_000L && option <= now + 24 * 60 * 60_000L)
                return option
        }
        return null
    }

    fun clear(ownerId: String) { prefs.edit().remove("owner:$ownerId").apply() }
}
