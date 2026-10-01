package com.rudrasinha.cue.assistant

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.rudrasinha.cue.MainActivity
import com.rudrasinha.cue.R

enum class CueAction(val key: String, val label: String, val symbol: String) {
    VOICE("voice", "Voice Reminder", "◉"),
    QUICK("quick", "Quick Reminder", "+"),
    IMPORT("import", "Import / Scan", "▤"),
    ASK("ask", "Ask AI", "✦"),
    DAY("day", "My Day", "▦"),
    SETTINGS("settings", "Assistant Settings", "⚙");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key }
    }
}

object AssistantControls {
    const val EXTRA_ACTION = "com.rudrasinha.cue.assistant.ACTION"
    const val OPEN_MENU = "menu"
    const val EXTRA_OPACITY = "opacity"
    const val EXTRA_PANEL = "panel"
    const val CHANNEL_ID = "cue_controls"
    const val PANEL_ID = 2041
    const val FLOATING_ID = 2042

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    fun canPost(context: Context): Boolean {
        val notifications = manager(context)
        return (Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            notifications.areNotificationsEnabled() &&
            notifications.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun channel(context: Context) {
        manager(context).createNotificationChannel(NotificationChannel(CHANNEL_ID,
            "Cue assistant controls", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shortcuts you enable in Cue"
        })
    }

    fun shortcutIntent(context: Context, key: String): Intent = Intent(context, MainActivity::class.java).apply {
        action = "com.rudrasinha.cue.assistant.$key"
        putExtra(EXTRA_ACTION, key)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

    private fun shortcut(context: Context, key: String, code: Int): PendingIntent =
        PendingIntent.getActivity(context, code, shortcutIntent(context, key),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun notification(context: Context, withActions: Boolean, floating: Boolean): Notification {
        channel(context)
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cue_foreground)
            .setContentTitle(if (floating) "Floating Cue is on" else "Cue shortcuts")
            .setContentText("Tap for all six actions")
            .setContentIntent(shortcut(context, OPEN_MENU, 100))
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
        if (withActions) builder
            .addAction(android.R.drawable.ic_btn_speak_now, "Voice", shortcut(context, CueAction.VOICE.key, 101))
            .addAction(android.R.drawable.ic_menu_edit, "Quick", shortcut(context, CueAction.QUICK.key, 102))
            .addAction(android.R.drawable.ic_menu_more, "More", shortcut(context, OPEN_MENU, 103))
        return builder.build()
    }

    fun updatePanel(context: Context, enabled: Boolean, floating: Boolean) {
        val notifications = manager(context)
        if (!enabled || floating || !canPost(context)) {
            notifications.cancel(PANEL_ID)
        } else {
            notifications.notify(PANEL_ID, notification(context, withActions = true, floating = false))
        }
    }
}
