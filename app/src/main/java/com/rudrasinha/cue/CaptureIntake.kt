package com.rudrasinha.cue

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.rudrasinha.cue.auth.CueAuth
import com.rudrasinha.cue.data.CaptureOrigin
import com.rudrasinha.cue.data.CloudCommitments
import com.rudrasinha.cue.data.CommitmentActions
import com.rudrasinha.cue.data.CueDatabase
import com.rudrasinha.cue.data.ReminderMatch
import com.rudrasinha.cue.reminders.ReminderScheduler
import com.rudrasinha.cue.settings.ThemeStore
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Accepts user-dropped or selected content. Only an unambiguous future time auto-saves. */
class CaptureIntake(private val context: Context) {
    companion object { private val saveLock = Mutex() }
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    suspend fun accept(text: String, type: String, titleHint: String? = null, uri: String? = null): Boolean {
        val content = text.trim().take(4000)
        if (content.isEmpty()) { acknowledge("Nothing readable found", "Try sharing text or a clearer image."); return false }
        val lines = content.lineSequence().map(String::trim).filter(String::isNotBlank).take(21).toList()
        val mailCandidate = if (type == "mail" && lines.size in 2..20) {
            val actions = lines.filter(::actionableReminder)
            val dates = lines.filter { explicitDateTimeSpan(it) || suggestedDue(it) != null }
            if (actions.size == 1 && dates.size == 1 && actions.single() != dates.single())
                "${actions.single()} ${dates.single()}" else null
        } else null
        val analysisText = mailCandidate ?: content
        val candidate = LocalReminderInterpreter.find(analysisText) ?: runCatching {
            CloudReminderInterpreter(context).find(analysisText)
        }.getOrNull()
        val title = candidate?.title ?: content.lineSequence()
            .firstOrNull { it.isNotBlank() }?.trim()?.take(100) ?: "New reminder"
        // An explicit bubble entry or drop is a reminder even when no time can be inferred.
        if (candidate == null && (type == "bubble" || type == "drop") &&
            content.length <= 250 && content.lineSequence().count() <= 5) {
            val ownerId = ReminderScheduler(context).activeOwnerId() ?: "guest"
            val database = CueDatabase.get(context)
            val tone = ThemeStore(context).reminderTone.first()
            val scheduler = ReminderScheduler(context)
            val actions = CommitmentActions(database, scheduler,
                CloudCommitments(database, CueAuth(context).client, scheduler))
            val key = fingerprint(content)
            val result = saveLock.withLock {
                if (database.history().sourceByOrigin(ownerId, type, key) != null) "Already saved"
                else {
                    actions.save(ownerId, null, title, content.take(2000), null,
                        CaptureOrigin(type, titleHint ?: "Floating Cue", content.take(2000), uri, key), tone)
                    "Reminder set"
                }
            }
            acknowledge(result, "$title · No alert set")
            return true
        }
        if (candidate == null) {
            if (type == "screen") return false // Ignore uncertain background content without interrupting the user.
            acknowledge("Review a possible reminder", title,
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_SEND
                    this.type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, content)
                    putExtra("com.rudrasinha.cue.REVIEW_CAPTURE", true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                })
            return false
        }
        val due = candidate.dueAtMillis
        val ownerId = ReminderScheduler(context).activeOwnerId() ?: "guest"
        val database = CueDatabase.get(context)
        val defaultTone = ThemeStore(context).reminderTone.first()
        val result = saveLock.withLock {
            val key = fingerprint("${title.lowercase()}|$due|${content.lowercase()}")
            val actions = CommitmentActions(database, ReminderScheduler(context),
                CloudCommitments(database, CueAuth(context).client, ReminderScheduler(context)))
            val details = if (type == "screen") null else content.take(2000)
            val excerpt = if (type == "screen") title else content.take(2000)
            val origin = CaptureOrigin(type, titleHint, excerpt, uri, key)
            val existing = ReminderMatch.existing(database.commitments().activeAtDue(ownerId, due), title)
            if (database.history().sourceByOrigin(ownerId, type, key) != null) "Already saved"
            else if (existing != null) {
                if (actions.linkSource(ownerId, existing.id, origin)) "Source linked to reminder"
                else "Already saved"
            } else {
                val synced = actions.save(ownerId, null, title, details, due, origin, defaultTone)
                if (synced) "Reminder added" else "Saved on this device"
            }
        }
        val label = Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM · h:mm a"))
        acknowledge(result, "$title · $label")
        return true
    }

    fun failure(message: String) = acknowledge("Cue couldn't read that item", message)
    fun noScreenReminder() = acknowledge("No clear reminder found", "Nothing was added from this screen.")

    private fun fingerprint(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.trim().lowercase().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun acknowledge(title: String, body: String, open: Intent? = null) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return
        if (!notificationManager.areNotificationsEnabled()) return
        notificationManager.createNotificationChannel(NotificationChannel("cue_capture",
            "Cue capture results", NotificationManager.IMPORTANCE_DEFAULT))
        if (notificationManager.getNotificationChannel("cue_capture")?.importance ==
            NotificationManager.IMPORTANCE_NONE) return
        val intent = open ?: Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val id = (System.currentTimeMillis() and Int.MAX_VALUE.toLong()).toInt()
        val notice = Notification.Builder(context, "cue_capture")
            .setSmallIcon(R.drawable.ic_cue_foreground)
            .setContentTitle(title)
            .setContentText(body.take(180))
            .setStyle(Notification.BigTextStyle().bigText(body.take(500)))
            .setContentIntent(PendingIntent.getActivity(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true).build()
        try { notificationManager.notify(id, notice) }
        catch (_: SecurityException) { /* Permission can be revoked during processing. */ }
    }
}
