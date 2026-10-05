package com.rudrasinha.cue.assistant

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.Service
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.ClipData
import android.content.ClipboardManager
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
import android.widget.EditText
import android.widget.TextView
import com.rudrasinha.cue.CaptureIntake
import com.rudrasinha.cue.importedText
import com.rudrasinha.cue.reminders.ReminderScheduler
import com.rudrasinha.cue.settings.ThemeStore
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class FloatingCueService : Service() {
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }
    private val state by lazy { getSharedPreferences("cue_floating_window", Context.MODE_PRIVATE) }
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                expanded = false
                quickEntry = false
                pasteEntry = false
                if (root != null) showWindow()
            } else if (intent.action == Intent.ACTION_USER_PRESENT && root != null) showWindow()
        }
    }
    private val windows by lazy { getSystemService(WindowManager::class.java) }
    private val handler = Handler(Looper.getMainLooper())
    private val captureScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var root: FrameLayout? = null
    private var expanded = false
    private var quickEntry = false
    private var pasteEntry = false
    private var closeTarget: TextView? = null
    private var rightEdge = true
    private var centerY = 0
    private var opacity = 0.82f
    private var sizeDp = 64
    private var panel = false
    private var accent = Color.rgb(145, 118, 244)
    private var onAccent = Color.WHITE
    private var surface = Color.rgb(37, 34, 53)
    private var onSurface = Color.WHITE
    private val size get() = dp(sizeDp)
    private val menuWidth get() = dp(if (quickEntry) 260 else if (pasteEntry) 190 else 300)
    private val menuHeight get() = dp(if (quickEntry) 210 else if (pasteEntry) 135 else 300)
    private val hidden get() = dp(14)
    private val screenWidth get() = resources.displayMetrics.widthPixels
    private val screenHeight get() = resources.displayMetrics.heightPixels
    private val permissionCheck = object : Runnable {
        override fun run() {
            if (!Settings.canDrawOverlays(this@FloatingCueService)) stopSelf()
            else handler.postDelayed(this, 5000)
        }
    }
    private val routineCheck = object : Runnable {
        override fun run() {
            captureScope.launch {
                ReminderScheduler(applicationContext).activeOwnerId()?.let { owner ->
                    runCatching { UsagePatternStore(applicationContext).capture(owner) }
                }
            }
            handler.postDelayed(this, 60 * 60_000L)
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

    override fun onCreate() {
        super.onCreate()
        rightEdge = state.getBoolean("right_edge", true)
        centerY = state.getInt("center_y", 0)
        opacity = state.getFloat("opacity", opacity)
        sizeDp = state.getInt("size_dp", sizeDp).coerceIn(48, 88)
        panel = state.getBoolean("panel", false)
        accent = state.getInt("accent", accent)
        onAccent = state.getInt("on_accent", onAccent)
        surface = state.getInt("surface", surface)
        onSurface = state.getInt("on_surface", onSurface)
        registerReceiver(unlockReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        })
    }

    private fun persistState() {
        state.edit().putBoolean("right_edge", rightEdge).putInt("center_y", centerY)
            .putFloat("opacity", opacity).putInt("size_dp", sizeDp)
            .putBoolean("panel", panel)
            .putInt("accent", accent).putInt("on_accent", onAccent)
            .putInt("surface", surface).putInt("on_surface", onSurface).apply()
    }

    private fun locked() = keyguard.isKeyguardLocked

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        opacity = (intent?.getFloatExtra(AssistantControls.EXTRA_OPACITY, opacity) ?: opacity)
            .coerceIn(0.35f, 1f)
        val nextSize = (intent?.getIntExtra(AssistantControls.EXTRA_SIZE, sizeDp) ?: sizeDp)
            .coerceIn(48, 88)
        val sizeChanged = nextSize != sizeDp
        sizeDp = nextSize
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
        persistState()
        if (intent == null) {
            captureScope.launch {
                val enabled = ThemeStore(applicationContext).floatingCue.first()
                val owner = ReminderScheduler(applicationContext).activeOwnerId()
                handler.post {
                    if (!enabled || owner == null || !Settings.canDrawOverlays(this@FloatingCueService)) stopSelf()
                    else showWindow()
                }
            }
        } else if (root == null || colorsChanged || sizeChanged) showWindow() else root?.findViewWithTag<View>("bubble")?.alpha =
            if (expanded) 1f else opacity
        handler.removeCallbacks(permissionCheck)
        handler.postDelayed(permissionCheck, 5000)
        handler.removeCallbacks(routineCheck)
        handler.post(routineCheck)
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (screenHeight < menuHeight + dp(76)) {
            expanded = false
            quickEntry = false
        }
        showWindow()
    }

    override fun onDestroy() {
        handler.removeCallbacks(permissionCheck)
        handler.removeCallbacks(routineCheck)
        unregisterReceiver(unlockReceiver)
        hideCloseTarget()
        captureScope.cancel()
        root?.let { runCatching { windows.removeViewImmediate(it) } }
        root = null
        super.onDestroy()
    }

    // The touch listener calls performClick() for taps and a click listener handles accessibility actions.
    @SuppressLint("ClickableViewAccessibility")
    private fun showWindow() {
        root?.let { runCatching { windows.removeViewImmediate(it) } }
        root = null
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        val width = if (expanded) menuWidth else size
        val height = if (expanded) menuHeight else size
        windowParams.flags = if ((quickEntry || pasteEntry) && expanded)
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        windowParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
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
        if (expanded) {
            if (quickEntry) addQuickEntry(frame)
            else if (pasteEntry) addPasteOption(frame)
            else addActions(frame)
        }
        val bubble = TextView(this).apply {
            tag = "bubble"
            text = "cue"
            textSize = (18f * sizeDp / 64f).coerceIn(14f, 24f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            contentDescription = "Floating Cue. Tap for actions, hold to paste, or drag to bottom to close."
            setTextColor(onAccent)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(accent, Color.rgb(Color.red(accent) * 3 / 4,
                    Color.green(accent) * 3 / 4, Color.blue(accent) * 3 / 4))).apply {
                cornerRadius = size / 2f
                setStroke(dp(1), accent)
            }
            elevation = dp(12).toFloat()
            alpha = if (expanded) 1f else opacity
            setOnClickListener {
                if (locked()) return@setOnClickListener
                expanded = !expanded
                if (!expanded) { quickEntry = false; pasteEntry = false }
                showWindow()
            }
        }
        bubble.setOnDragListener { view, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> !locked() && event.clipDescription != null
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
                    if (locked()) return@setOnDragListener false
                    view.scaleX = 1f; view.scaleY = 1f
                    (view as TextView).text = "✓"
                    val clip = event.clipData ?: return@setOnDragListener false
                    captureClip(clip, "drop")
                    handler.postDelayed({ (view as TextView).text = "cue" }, 1300)
                    true
                }
                else -> true
            }
        }
        val bubbleX = if (expanded && !quickEntry) (width - size) / 2
            else if (expanded && rightEdge) width - size else 0
        if (!(expanded && quickEntry)) frame.addView(bubble, FrameLayout.LayoutParams(size, size).apply {
            leftMargin = bubbleX
            topMargin = if (expanded && pasteEntry) dp(5)
                else if (expanded) (height - size) / 2 else 0
        })
        bubble.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f
            var downY = 0f
            var startX = 0
            var startY = 0
            var dragged = false
            var longPressed = false
            var hold: Runnable? = null
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                if (locked()) return true
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX; downY = event.rawY
                        startX = windowParams.x; startY = windowParams.y
                        dragged = false
                        longPressed = false
                        hold = Runnable {
                            if (!dragged && !expanded && !locked()) {
                                longPressed = true
                                pasteEntry = true
                                expanded = true
                                showWindow()
                            }
                        }.also { handler.postDelayed(it, 550) }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (abs(event.rawX - downX) > dp(8) || abs(event.rawY - downY) > dp(8)) {
                            dragged = true
                            hold?.let(handler::removeCallbacks)
                        }
                        if (dragged && !expanded) {
                            showCloseTarget()
                            windowParams.x = (startX + (event.rawX - downX).toInt())
                                .coerceIn(-hidden, screenWidth - size + hidden)
                            windowParams.y = (startY + (event.rawY - downY).toInt())
                                .coerceIn(dp(28), screenHeight - size - dp(48))
                            windows.updateViewLayout(frame, windowParams)
                            closeTarget?.alpha = if (windowParams.y + size / 2 >= screenHeight - dp(130)) 1f else .65f
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        hold?.let(handler::removeCallbacks)
                        if (dragged) {
                            if (!expanded) {
                                if (windowParams.y + size / 2 >= screenHeight - dp(130)) {
                                    hideCloseTarget()
                                    view.animate().scaleX(.2f).scaleY(.2f).alpha(0f)
                                        .setDuration(180).withEndAction {
                                            captureScope.launch {
                                                ThemeStore(applicationContext).setFloatingCue(false)
                                                handler.post { stopSelf() }
                                            }
                                        }.start()
                                    return true
                                }
                                hideCloseTarget()
                                rightEdge = windowParams.x + size / 2 > screenWidth / 2
                                centerY = windowParams.y + size / 2
                                persistState()
                                showWindow()
                            }
                        } else if (!longPressed) view.performClick()
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        hold?.let(handler::removeCallbacks)
                        hideCloseTarget()
                        return true
                    }
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
        val diameter = dp(48)
        val radius = dp(101)
        val cx = menuWidth / 2
        val cy = menuHeight / 2
        CueAction.entries.forEachIndexed { index, action ->
            val chip = TextView(this).apply {
                text = action.symbol
                contentDescription = action.label
                textSize = 25f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(onSurface)
                background = GradientDrawable().apply {
                    setColor(surface)
                    cornerRadius = diameter / 2f
                    setStroke(dp(1), accent)
                }
                elevation = dp(8).toFloat()
                setOnClickListener {
                    if (locked()) return@setOnClickListener
                    if (action == CueAction.QUICK) {
                        quickEntry = true
                        showWindow()
                        return@setOnClickListener
                    }
                    expanded = false
                    showWindow()
                    startActivity(AssistantControls.shortcutIntent(this@FloatingCueService, action.key))
                }
            }
            val angle = Math.toRadians(-90.0 + index * 360.0 / CueAction.entries.size)
            frame.addView(chip, FrameLayout.LayoutParams(diameter, diameter).apply {
                leftMargin = cx + (radius * cos(angle)).toInt() - diameter / 2
                topMargin = cy + (radius * sin(angle)).toInt() - diameter / 2
            })
        }
    }

    private fun showCloseTarget() {
        if (closeTarget != null) return
        val target = TextView(this).apply {
            text = "×  Release to close"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(onAccent)
            background = GradientDrawable().apply {
                setColor(accent); cornerRadius = dp(28).toFloat()
            }
            alpha = .65f
        }
        val params = WindowManager.LayoutParams(dp(184), dp(56),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(46)
        }
        runCatching { windows.addView(target, params); closeTarget = target }
    }

    private fun hideCloseTarget() {
        closeTarget?.let { runCatching { windows.removeViewImmediate(it) } }
        closeTarget = null
    }

    private fun addPasteOption(frame: FrameLayout) {
        val paste = TextView(this).apply {
            text = "▣  Paste"
            contentDescription = "Paste copied text, image or document into Cue"
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(onSurface)
            background = GradientDrawable().apply {
                setColor(surface); cornerRadius = dp(20).toFloat()
                setStroke(dp(1), accent)
            }
            setOnClickListener {
                if (locked()) return@setOnClickListener
                val clip = getSystemService(ClipboardManager::class.java).primaryClip
                if (clip == null || clip.itemCount == 0)
                    CaptureIntake(applicationContext).failure("Clipboard is empty or unavailable. Copy an item first.")
                else captureClip(clip, "paste") {
                    pasteEntry = false
                    expanded = false
                    showWindow()
                }
                if (clip == null || clip.itemCount == 0) {
                    pasteEntry = false; expanded = false; showWindow()
                }
            }
        }
        frame.addView(paste, FrameLayout.LayoutParams(menuWidth - dp(20), dp(48)).apply {
            leftMargin = dp(10); topMargin = dp(78)
        })
    }

    private fun captureClip(clip: ClipData, type: String, onFinished: (() -> Unit)? = null) {
        val items = (0 until minOf(clip.itemCount, 8)).map(clip::getItemAt)
        captureScope.launch {
            val parts = mutableListOf<String>()
            var firstUri: String? = null
            var blockedFiles = 0
            for (item in items) {
                val uri = item.uri ?: item.intent?.data
                if (uri != null) {
                    try {
                        val draft = importedText(applicationContext, uri)
                        parts += draft.text
                        if (firstUri == null) firstUri = uri.toString()
                    } catch (_: Exception) { blockedFiles++ }
                } else {
                    val value = item.text?.toString() ?: item.intent
                        ?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() ?: item.htmlText?.let {
                        android.text.Html.fromHtml(it, android.text.Html.FROM_HTML_MODE_COMPACT).toString()
                    }
                    if (!value.isNullOrBlank()) parts += value
                }
            }
            val intake = CaptureIntake(applicationContext)
            try {
                if (parts.isNotEmpty()) intake.accept(parts.joinToString("\n").take(4000), type,
                    if (type == "paste") "Floating Cue paste" else "Floating Cue drop", firstUri)
                if (blockedFiles > 0) intake.failure(
                    "$blockedFiles file(s) could not be read from this app. Use Share → Cue for those files.")
                else if (parts.isEmpty()) intake.failure(
                    "No readable item was provided. Copy it, then hold Cue and tap Paste, or use Share → Cue.")
            } catch (e: Exception) {
                intake.failure(e.message ?: "Could not analyze this item.")
            } finally {
                if (onFinished != null) handler.post { onFinished() }
            }
        }
    }

    private fun addQuickEntry(frame: FrameLayout) {
        val input = EditText(this).apply {
            hint = "What should Cue remind you? Add a date or time."
            textSize = 15f
            setTextColor(onSurface)
            setHintTextColor(onSurface)
            background = GradientDrawable().apply {
                setColor(surface); cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(14), dp(8), dp(14), dp(8))
            minLines = 2
            maxLines = 3
        }
        frame.addView(input, FrameLayout.LayoutParams(menuWidth - dp(22), dp(100)).apply {
            leftMargin = dp(11); topMargin = dp(12)
        })
        val save = TextView(this).apply {
            text = "Save reminder"
            gravity = Gravity.CENTER
            setTextColor(onAccent)
            background = GradientDrawable().apply {
                setColor(accent); cornerRadius = dp(16).toFloat()
            }
            setOnClickListener {
                val value = input.text.toString().trim()
                if (value.isBlank()) { input.error = "Write a reminder first"; return@setOnClickListener }
                quickEntry = false
                expanded = false
                showWindow()
                captureScope.launch {
                    try { CaptureIntake(applicationContext).accept(value, "bubble") }
                    catch (e: Exception) {
                        CaptureIntake(applicationContext).failure(e.message ?: "Could not save your reminder.")
                    }
                }
            }
        }
        frame.addView(save, FrameLayout.LayoutParams(menuWidth - dp(22), dp(48)).apply {
            leftMargin = dp(11); topMargin = dp(120)
        })
        val cancel = TextView(this).apply {
            text = "Cancel"
            gravity = Gravity.CENTER
            setTextColor(onSurface)
            setOnClickListener { quickEntry = false; expanded = true; showWindow() }
        }
        frame.addView(cancel, FrameLayout.LayoutParams(menuWidth - dp(22), dp(34)).apply {
            leftMargin = dp(11); topMargin = dp(172)
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
