package com.rudrasinha.cue.assistant

import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.DragEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.rudrasinha.cue.CaptureIntake
import com.rudrasinha.cue.importedText
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FloatingCueService : Service() {
    private val windows by lazy { getSystemService(WindowManager::class.java) }
    private val handler = Handler(Looper.getMainLooper())
    private val captureScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var root: FrameLayout? = null
    private var expanded = false
    private var rightEdge = true
    private var centerY = 0
    private var opacity = 0.82f
    private var panel = false
    private var accent = Color.rgb(145, 118, 244)
    private var onAccent = Color.WHITE
    private var surface = Color.rgb(37, 34, 53)
    private var onSurface = Color.WHITE
    private val size get() = dp(64)
    private val menuWidth get() = dp(260)
    private val menuHeight get() = dp(390)
    private val hidden get() = dp(14)
    private val screenWidth get() = resources.displayMetrics.widthPixels
    private val screenHeight get() = resources.displayMetrics.heightPixels
    private val permissionCheck = object : Runnable {
        override fun run() {
            if (!Settings.canDrawOverlays(this@FloatingCueService)) stopSelf()
            else handler.postDelayed(this, 5000)
        }
    }
    private val windowParams = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        format = PixelFormat.TRANSLUCENT
        gravity = Gravity.TOP or Gravity.START
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        opacity = (intent?.getFloatExtra(AssistantControls.EXTRA_OPACITY, opacity) ?: opacity)
            .coerceIn(0.35f, 1f)
        panel = intent?.getBooleanExtra(AssistantControls.EXTRA_PANEL, panel) ?: panel
        val newAccent = intent?.getIntExtra(AssistantControls.EXTRA_ACCENT, accent) ?: accent
        val newOnAccent = intent?.getIntExtra(AssistantControls.EXTRA_ON_ACCENT, onAccent) ?: onAccent
        val newSurface = intent?.getIntExtra(AssistantControls.EXTRA_SURFACE, surface) ?: surface
        val newOnSurface = intent?.getIntExtra(AssistantControls.EXTRA_ON_SURFACE, onSurface) ?: onSurface
        val colorsChanged = newAccent != accent || newOnAccent != onAccent ||
            newSurface != surface || newOnSurface != onSurface
        accent = newAccent; onAccent = newOnAccent
        surface = newSurface; onSurface = newOnSurface
        try {
            startForeground(AssistantControls.FLOATING_ID,
                AssistantControls.notification(this, withActions = panel, floating = true))
        } catch (_: RuntimeException) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (centerY == 0) centerY = screenHeight / 2
        if (root == null || colorsChanged) showWindow() else root?.findViewWithTag<View>("bubble")?.alpha =
            if (expanded) 1f else opacity
        handler.removeCallbacks(permissionCheck)
        handler.postDelayed(permissionCheck, 5000)
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (screenHeight < menuHeight + dp(76)) expanded = false
        showWindow()
    }

    override fun onDestroy() {
        handler.removeCallbacks(permissionCheck)
        captureScope.cancel()
        root?.let { runCatching { windows.removeViewImmediate(it) } }
        root = null
        super.onDestroy()
    }

    private fun showWindow() {
        root?.let { runCatching { windows.removeViewImmediate(it) } }
        root = null
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        val width = if (expanded) menuWidth else size
        val height = if (expanded) menuHeight else size
        val minimum = height / 2 + dp(28)
        val maximum = screenHeight - height / 2 - dp(48)
        centerY = if (maximum >= minimum) centerY.coerceIn(minimum, maximum) else screenHeight / 2
        windowParams.width = width
        windowParams.height = height
        windowParams.x = if (expanded) {
            if (rightEdge) screenWidth - width else 0
        } else if (rightEdge) screenWidth - width + hidden else -hidden
        windowParams.y = centerY - height / 2

        val frame = FrameLayout(this)
        if (expanded) addActions(frame)
        val bubble = TextView(this).apply {
            tag = "bubble"
            text = "cue"
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            contentDescription = "Floating Cue. Double tap for actions; drag to move."
            setTextColor(onAccent)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(accent, Color.rgb(Color.red(accent) * 3 / 4,
                    Color.green(accent) * 3 / 4, Color.blue(accent) * 3 / 4))).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), accent)
            }
            elevation = dp(12).toFloat()
            alpha = if (expanded) 1f else opacity
            setOnClickListener {
                if (!expanded && screenHeight < menuHeight + dp(76)) {
                    startActivity(AssistantControls.shortcutIntent(this@FloatingCueService,
                        AssistantControls.OPEN_MENU))
                } else {
                    expanded = !expanded
                    showWindow()
                }
            }
        }
        bubble.setOnDragListener { view, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> event.clipDescription != null
                DragEvent.ACTION_DRAG_ENTERED -> {
                    view.scaleX = 1.18f; view.scaleY = 1.18f
                    (view as TextView).text = "↓"
                    true
                }
                DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                    view.scaleX = 1f; view.scaleY = 1f
                    (view as TextView).text = "cue"
                    true
                }
                DragEvent.ACTION_DROP -> {
                    view.scaleX = 1f; view.scaleY = 1f
                    (view as TextView).text = "✓"
                    val clip = event.clipData
                    if (clip == null || clip.itemCount == 0) return@setOnDragListener false
                    val intake = CaptureIntake(applicationContext)
                    (0 until minOf(clip.itemCount, 8)).forEach { index ->
                        val item = clip.getItemAt(index)
                        val uri = item.uri
                        val text = item.text?.toString() ?: item.htmlText?.let {
                            android.text.Html.fromHtml(it, android.text.Html.FROM_HTML_MODE_COMPACT).toString()
                        }
                        captureScope.launch {
                            try {
                                if (uri != null) {
                                    val draft = importedText(applicationContext, uri)
                                    intake.accept(draft.text, "drop", draft.origin.title, uri.toString())
                                } else if (!text.isNullOrBlank()) intake.accept(text, "drop")
                                else intake.failure("This app didn't provide readable data. Try Share → Cue.")
                            } catch (_: SecurityException) {
                                intake.failure("The source app did not grant image access. Use Share → Cue.")
                            } catch (e: Exception) {
                                intake.failure(e.message ?: "Try sharing this item with Cue instead.")
                            }
                        }
                    }
                    handler.postDelayed({ (view as TextView).text = "cue" }, 1300)
                    true
                }
                else -> true
            }
        }
        val bubbleX = if (expanded && rightEdge) width - size else 0
        frame.addView(bubble, FrameLayout.LayoutParams(size, size).apply {
            leftMargin = bubbleX
            topMargin = if (expanded) (height - size) / 2 else 0
        })
        bubble.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f
            var downY = 0f
            var startX = 0
            var startY = 0
            var dragged = false
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX; downY = event.rawY
                        startX = windowParams.x; startY = windowParams.y
                        dragged = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (abs(event.rawX - downX) > dp(8) || abs(event.rawY - downY) > dp(8))
                            dragged = true
                        if (dragged && !expanded) {
                            windowParams.x = (startX + (event.rawX - downX).toInt())
                                .coerceIn(-hidden, screenWidth - size + hidden)
                            windowParams.y = (startY + (event.rawY - downY).toInt())
                                .coerceIn(dp(28), screenHeight - size - dp(48))
                            windows.updateViewLayout(frame, windowParams)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (dragged) {
                            if (!expanded) {
                                rightEdge = windowParams.x + size / 2 > screenWidth / 2
                                centerY = windowParams.y + size / 2
                                showWindow()
                            }
                        } else view.performClick()
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> return true
                }
                return false
            }
        })
        try {
            windows.addView(frame, windowParams)
            root = frame
        } catch (_: RuntimeException) {
            stopSelf()
        }
    }

    private fun addActions(frame: FrameLayout) {
        val offsets = intArrayOf(56, 78, 84, 84, 78, 56)
        val tops = intArrayOf(12, 72, 132, 208, 268, 328)
        CueAction.entries.forEachIndexed { index, action ->
            val chip = TextView(this).apply {
                text = "${action.symbol}   ${action.label}"
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(15), 0, dp(8), 0)
                setTextColor(onSurface)
                background = GradientDrawable().apply {
                    setColor(surface)
                    cornerRadius = dp(18).toFloat()
                    setStroke(dp(1), accent)
                }
                elevation = dp(8).toFloat()
                setOnClickListener {
                    expanded = false
                    showWindow()
                    startActivity(AssistantControls.shortcutIntent(this@FloatingCueService, action.key))
                }
            }
            val offset = dp(offsets[index])
            frame.addView(chip, FrameLayout.LayoutParams(dp(175), dp(50)).apply {
                leftMargin = if (rightEdge) menuWidth - dp(175) - offset else offset
                topMargin = dp(tops[index])
            })
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
