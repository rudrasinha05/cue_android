package com.rudrasinha.cue.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.view.WindowManager
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.rudrasinha.cue.CaptureIntake
import com.rudrasinha.cue.R
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** One user-approved projection session. Frames are sampled for local OCR, never stored as video. */
class ScreenInsightService : Service() {
    companion object {
        const val ACTION_STOP = "com.rudrasinha.cue.STOP_SCREEN_ANALYSIS"
        const val EXTRA_CONSENT = "projection_consent"
        const val EXTRA_RESULT = "projection_result"
        val running = MutableStateFlow(false)
        private const val NOTIFICATION_ID = 2301
    }
    private val thread = HandlerThread("cue-screen-ocr").apply { start() }
    private val handler = Handler(thread.looper)
    private val work = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val busy = AtomicBoolean(false)
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var lastSample = 0L
    private var lastText = ""
    @Volatile private var stopping = false
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() { stopSelf() }
        override fun onCapturedContentResize(width: Int, height: Int) {
            handler.post { if (!stopping) try { resize(width, height) }
                catch (_: RuntimeException) { stopSelf() } }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (Build.VERSION.SDK_INT < 34 || projection != null) return START_NOT_STICKY
        val consent = if (Build.VERSION.SDK_INT >= 33)
            intent?.getParcelableExtra(EXTRA_CONSENT, Intent::class.java)
        else @Suppress("DEPRECATION") intent?.getParcelableExtra<Intent>(EXTRA_CONSENT)
        val result = intent?.getIntExtra(EXTRA_RESULT, 0) ?: 0
        if (consent == null || result != android.app.Activity.RESULT_OK) {
            stopSelf(); return START_NOT_STICKY
        }
        try {
            val notification = notice()
            startForeground(NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            projection = getSystemService(MediaProjectionManager::class.java)
                .getMediaProjection(result, consent)
            projection?.registerCallback(callback, handler)
            val bounds = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            handler.post {
                if (!stopping) try {
                    resize(bounds.width(), bounds.height())
                    running.value = display != null
                } catch (e: RuntimeException) {
                    CaptureIntake(applicationContext).failure(
                        e.message ?: "Screen analysis could not start.")
                    stopSelf()
                }
            }
        } catch (e: Exception) {
            CaptureIntake(applicationContext).failure(
                e.message ?: "Screen analysis could not start. Try selecting one app again.")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun notice(): Notification {
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel("cue_screen",
            "Cue screen analysis", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 2302,
            Intent(this, ScreenInsightService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "cue_screen")
            .setSmallIcon(R.drawable.ic_cue_foreground)
            .setContentTitle("Cue is analyzing your selected app")
            .setContentText("Local screen text analysis · tap Stop any time")
            .setOngoing(true)
            .setContentIntent(AssistantControls.shortcutIntent(this, CueAction.SETTINGS.key).let {
                PendingIntent.getActivity(this, 2303, it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            })
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop)
            .build()
    }

    private fun resize(sourceWidth: Int, sourceHeight: Int) {
        if (sourceWidth <= 0 || sourceHeight <= 0) return
        val scale = minOf(1f, 1080f / sourceWidth, 1920f / sourceHeight)
        val width = maxOf(1, (sourceWidth * scale).toInt())
        val height = maxOf(1, (sourceHeight * scale).toInt())
        reader?.setOnImageAvailableListener(null, null)
        reader?.close()
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({ source -> onFrame(source) }, handler)
        }
        val existing = display
        if (existing == null) {
            display = projection?.createVirtualDisplay("Cue selected app", width, height,
                resources.displayMetrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader!!.surface, null, handler)
        } else {
            existing.resize(width, height, resources.displayMetrics.densityDpi)
            existing.setSurface(reader!!.surface)
        }
    }

    private fun onFrame(source: ImageReader) {
        val image = runCatching { source.acquireLatestImage() }.getOrNull() ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastSample < 15_000 || !busy.compareAndSet(false, true)) {
            image.close(); return
        }
        lastSample = now
        val bitmap = try {
            val plane = image.planes.firstOrNull()
            if (plane == null || plane.pixelStride != 4) null else {
                val paddedWidth = image.width + (plane.rowStride - image.width * 4) / 4
                val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
                padded.copyPixelsFromBuffer(plane.buffer)
                Bitmap.createBitmap(padded, 0, 0, image.width, image.height).also { padded.recycle() }
            }
        } catch (_: RuntimeException) { null }
        finally { image.close() }
        if (bitmap == null) { busy.set(false); return }
        work.launch {
            try {
                val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
                    .text.trim().take(4000)
                if (text.isNotBlank() && text != lastText) {
                    lastText = text
                    CaptureIntake(applicationContext).accept(text, "screen", "Selected app screen")
                }
            } catch (_: Exception) { /* A later frame can still be analyzed. */ }
            finally { bitmap.recycle(); busy.set(false) }
        }
    }

    override fun onDestroy() {
        stopping = true
        running.value = false
        work.cancel()
        display?.release()
        reader?.close()
        projection?.unregisterCallback(callback)
        projection?.stop()
        recognizer.close()
        thread.quitSafely()
        super.onDestroy()
    }
}
