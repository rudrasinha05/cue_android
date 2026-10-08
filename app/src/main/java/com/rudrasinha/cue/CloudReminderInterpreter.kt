package com.rudrasinha.cue

import android.content.Context
import com.rudrasinha.cue.auth.CueAuth
import com.rudrasinha.cue.reminders.ReminderScheduler
import com.rudrasinha.cue.settings.ThemeStore
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Sends a single short, actionable line only when this signed-in account opted in. */
internal class CloudReminderInterpreter(private val context: Context) {
    suspend fun find(text: String): ConfidentReminder? {
        val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        if (lines.size != 1) return null
        val line = lines.single()
        if (line.length !in 10..300 || !actionableReminder(line) || !explicitDateTimeSpan(line))
            return null
        val owner = ReminderScheduler(context).activeOwnerId() ?: return null
        if (owner == "guest" || ThemeStore(context).cloudAnalysisOwner.first() != owner) return null
        val session = CueAuth(context).client.auth.sessionStatus.first {
            it !is SessionStatus.Initializing
        } as? SessionStatus.Authenticated ?: return null
        if (session.session.user?.id != owner) return null
        return withContext(Dispatchers.IO) {
            val connection = URL("${BuildConfig.SUPABASE_URL}/functions/v1/cue-analyze")
                .openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 8000
                connection.readTimeout = 10000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
                connection.setRequestProperty("Authorization", "Bearer ${session.session.accessToken}")
                val body = JSONObject().put("line", line)
                    .put("timezone", ZoneId.systemDefault().id)
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                if (connection.responseCode !in 200..299) return@withContext null
                val output = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val due = runCatching { Instant.parse(output.getString("due_at")).toEpochMilli() }
                    .getOrNull() ?: return@withContext null
                val now = System.currentTimeMillis()
                if (due <= now || due > now + 366L * 86_400_000L) null
                else ConfidentReminder(line.take(100), due)
            } finally { connection.disconnect() }
        }
    }
}
