package com.rudrasinha.cue.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.rudrasinha.cue.R

/** Plays each selected reminder sound for ten seconds, then releases the audio session. */
class AlarmPlaybackService : Service() {
    companion object {
        const val ACTION_PLAY = "com.rudrasinha.cue.alarm.PLAY"
        const val ACTION_STOP = "com.rudrasinha.cue.alarm.STOP"
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_TONE = "tone_id"
        private const val CHANNEL = "cue_alarm_playback"
        private const val NOTICE_ID = 2070
    }
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var activeId: String? = null
    private val stop = Runnable { stopSelf() }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            if (activeId == intent.getStringExtra(EXTRA_ID)) stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_PLAY) { stopSelf(); return START_NOT_STICKY }
        val id = intent.getStringExtra(EXTRA_ID) ?: run { stopSelf(); return START_NOT_STICKY }
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL,
            "Cue alarm playback", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) })
        startForeground(NOTICE_ID, Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_cue_foreground)
            .setContentTitle("Cue reminder ringing")
            .setContentText("Sound stops after 10 seconds")
            .setOngoing(true).build())
        activeId = id
        handler.removeCallbacks(stop)
        player?.release()
        player = null
        val tone = ReminderTones.selected(intent.getStringExtra(EXTRA_TONE).orEmpty())
        val sound = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                if (tone.resource != null) {
                    val name = "cue_tone_${tone.id}"
                    setDataSource(this@AlarmPlaybackService,
                        android.net.Uri.parse("android.resource://$packageName/raw/$name"))
                } else setDataSource(this@AlarmPlaybackService,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: android.net.Uri.parse("android.resource://$packageName/raw/cue_tone_01"))
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
        player = sound
        handler.postDelayed(stop, 10_000L)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(stop)
        player?.release()
        player = null
        super.onDestroy()
    }
}
