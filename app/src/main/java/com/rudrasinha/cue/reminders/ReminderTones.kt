package com.rudrasinha.cue.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import com.rudrasinha.cue.R

data class ReminderTone(val id: String, val label: String, val resource: Int? = null)

/** Channel IDs stay stable because Android freezes a channel's sound after creation. */
object ReminderTones {
    const val DEFAULT = "default"
    val choices = listOf(
        ReminderTone(DEFAULT, "System default"),
        ReminderTone("01", "Dawn", R.raw.cue_tone_01),
        ReminderTone("02", "Spark", R.raw.cue_tone_02),
        ReminderTone("03", "Ripple", R.raw.cue_tone_03),
        ReminderTone("04", "Bloom", R.raw.cue_tone_04),
        ReminderTone("05", "Orbit", R.raw.cue_tone_05),
        ReminderTone("06", "Drift", R.raw.cue_tone_06),
        ReminderTone("07", "Halo", R.raw.cue_tone_07),
        ReminderTone("08", "Echo", R.raw.cue_tone_08),
        ReminderTone("09", "Prism", R.raw.cue_tone_09),
        ReminderTone("10", "Breeze", R.raw.cue_tone_10),
        ReminderTone("11", "Pebble", R.raw.cue_tone_11),
        ReminderTone("12", "Rise", R.raw.cue_tone_12),
        ReminderTone("13", "Nova", R.raw.cue_tone_13),
        ReminderTone("14", "Lantern", R.raw.cue_tone_14),
        ReminderTone("15", "Tide", R.raw.cue_tone_15),
        ReminderTone("16", "Glimmer", R.raw.cue_tone_16),
        ReminderTone("17", "Soft Bell", R.raw.cue_tone_17),
        ReminderTone("18", "Focus", R.raw.cue_tone_18),
        ReminderTone("19", "Morning", R.raw.cue_tone_19),
        ReminderTone("20", "Evening", R.raw.cue_tone_20)
    )

    fun selected(id: String): ReminderTone = choices.firstOrNull { it.id == id } ?: choices.first()
    fun channelId(id: String): String = selected(id).let { tone ->
        if (tone.resource == null) "cue_reminders" else "cue_reminders_tone_${tone.id}"
    }

    fun ensureChannel(context: Context, id: String): String {
        val tone = selected(id)
        val channelId = channelId(tone.id)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(channelId,
                if (tone.resource == null) "Cue reminders" else "Cue reminders · ${tone.label}",
                NotificationManager.IMPORTANCE_HIGH)
            if (tone.resource != null) {
                val name = "cue_tone_${tone.id}"
                val uri = Uri.parse("android.resource://${context.packageName}/raw/$name")
                val audio = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                channel.setSound(uri, audio)
            }
            manager.createNotificationChannel(channel)
        }
        return channelId
    }
}
