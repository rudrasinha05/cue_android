package com.rudrasinha.cue

import android.app.Activity
import android.app.NotificationManager
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.media.projection.MediaProjectionManager
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rudrasinha.cue.data.CommitmentDao
import com.rudrasinha.cue.assistant.AssistantControls
import com.rudrasinha.cue.assistant.CueAction
import com.rudrasinha.cue.assistant.FloatingCueService
import com.rudrasinha.cue.assistant.ScreenInsightService
import com.rudrasinha.cue.data.CommitmentActions
import com.rudrasinha.cue.data.CueDatabase
import com.rudrasinha.cue.data.CloudCommitments
import com.rudrasinha.cue.data.CaptureOrigin
import com.rudrasinha.cue.data.HistoryArchive
import com.rudrasinha.cue.auth.CueAuth
import com.rudrasinha.cue.settings.ThemePreference
import com.rudrasinha.cue.settings.ColorTheme
import com.rudrasinha.cue.settings.ThemeStore
import com.rudrasinha.cue.ui.CueTheme
import com.rudrasinha.cue.ui.CommitmentListScreen
import com.rudrasinha.cue.ui.CaptureDraft
import com.rudrasinha.cue.ui.CaptureHub
import com.rudrasinha.cue.ui.AssistantActionSheet
import com.rudrasinha.cue.ui.HistoryScreen
import com.rudrasinha.cue.ui.themeSwatch
import com.rudrasinha.cue.reminders.ReminderScheduler
import com.rudrasinha.cue.planning.DailyPlanScheduler
import com.rudrasinha.cue.ui.DailyPlanSheet
import com.rudrasinha.cue.ui.timeLabel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.UUID
import androidx.compose.runtime.LaunchedEffect
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus

class MainActivity : ComponentActivity() {
    private val intakeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var permissionEpoch by mutableIntStateOf(0)
    private var captureDraft by mutableStateOf<CaptureDraft?>(null)
    private var shortcutAction by mutableStateOf<String?>(null)

    fun queueCapture(draft: CaptureDraft) { captureDraft = draft }
    private fun readSharedUri(incoming: Intent?) {
        if (incoming?.action != Intent.ACTION_SEND || sharedText(incoming) != null) return
        val uri = if (Build.VERSION.SDK_INT >= 33)
            incoming.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else @Suppress("DEPRECATION") incoming.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        val source = uri ?: incoming.clipData?.getItemAt(0)?.uri ?: return
        intakeScope.launch {
            runCatching { withContext(Dispatchers.IO) { importedText(this@MainActivity, source) } }
                .onSuccess(::queueCapture)
                .onFailure { CaptureIntake(applicationContext).failure(
                    it.message ?: "Could not read the shared item.") }
        }
    }
    private fun consumeShortcut() {
        shortcutAction = null
        intent?.removeExtra(AssistantControls.EXTRA_ACTION)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedText(intent)?.let(::queueCapture)
        readSharedUri(intent)
        shortcutAction = intent.getStringExtra(AssistantControls.EXTRA_ACTION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureDraft = sharedText(intent)
        readSharedUri(intent)
        shortcutAction = intent?.getStringExtra(AssistantControls.EXTRA_ACTION)
        val themeStore = ThemeStore(applicationContext)
        val database = CueDatabase.get(applicationContext)
        val auth = CueAuth(applicationContext)
        val cloud = CloudCommitments(database, auth.client)
        setContent { CueApp(themeStore, database, auth, cloud, this, permissionEpoch,
            captureDraft, { captureDraft = null }, shortcutAction, ::consumeShortcut) }
    }

    override fun onResume() {
        super.onResume()
        permissionEpoch++
    }

    override fun onDestroy() {
        intakeScope.cancel()
        super.onDestroy()
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Reminders", Icons.Default.NotificationsActive),
    UPCOMING("Upcoming", Icons.Default.CalendarMonth),
    AI("AI", Icons.Default.AutoAwesome),
    INBOX("Inbox", Icons.Default.Inbox),
    YOU("You", Icons.Default.AccountCircle)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CueApp(
    themeStore: ThemeStore,
    database: CueDatabase,
    auth: CueAuth,
    cloud: CloudCommitments,
    activity: Activity,
    permissionEpoch: Int,
    captureDraft: CaptureDraft?,
    onCaptureDismiss: () -> Unit,
    shortcutAction: String?,
    onShortcutConsumed: () -> Unit
) {
    val commitments = database.commitments()
    val theme by themeStore.mode.collectAsState(initial = ThemePreference.SYSTEM)
    val colorTheme by themeStore.colorTheme.collectAsState(initial = ColorTheme.DEFAULT)
    val floatingEnabled by themeStore.floatingCue.collectAsState(initial = false)
    val panelEnabled by themeStore.notificationPanel.collectAsState(initial = false)
    val floatingOpacity by themeStore.floatingOpacity.collectAsState(initial = 0.82f)
    val screenRunning by ScreenInsightService.running.collectAsState()
    val dailyPlanEnabled by themeStore.dailyPlanEnabled.collectAsState(initial = false)
    val wakeMinute by themeStore.wakeMinute.collectAsState(initial = 420)
    val bedMinute by themeStore.bedMinute.collectAsState(initial = 1320)
    val session by auth.client.auth.sessionStatus.collectAsState(initial = SessionStatus.Initializing)
    val userId = (session as? SessionStatus.Authenticated)?.session?.user?.id
    val ownerId = userId ?: "guest"
    val items by remember(ownerId) { commitments.observeActive(ownerId) }.collectAsState(initial = emptyList())
    val completed by remember(ownerId) { commitments.observeCompleted(ownerId) }.collectAsState(initial = emptyList())
    val history by remember(ownerId) { database.history().observeEvents(ownerId) }.collectAsState(initial = emptyList())
    val sources by remember(ownerId) { database.history().observeSources(ownerId) }.collectAsState(initial = emptyList())
    val batches by remember(ownerId) { database.history().observeBatches(ownerId) }.collectAsState(initial = emptyList())
    val scheduler = remember { ReminderScheduler(activity.applicationContext) }
    val actions = remember { CommitmentActions(database, scheduler, cloud) }
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var todayFocusToken by remember { mutableIntStateOf(0) }
    var actionSheetOpen by remember { mutableStateOf(false) }
    var dayPlanOpen by remember { mutableStateOf(false) }
    var controlMessage by remember { mutableStateOf<String?>(null) }
    var pendingOverlayEnable by remember { mutableStateOf(false) }
    var pendingFloatingEnable by remember { mutableStateOf(false) }
    var pendingPanelEnable by remember { mutableStateOf(false) }
    var pendingDayEnable by remember { mutableStateOf(false) }
    var pendingScreenStart by remember { mutableStateOf(false) }
    LaunchedEffect(captureDraft?.id) { if (captureDraft != null) selected = Tab.TODAY }
    var accountMessage by remember { mutableStateOf<String?>(null) }
    var accountBusy by remember { mutableStateOf(false) }
    var reminderMessage by remember { mutableStateOf<String?>(null) }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()?.trim()
        if (!spoken.isNullOrBlank()) activity.let {
            (it as MainActivity).queueCapture(CaptureDraft(UUID.randomUUID().toString(), spoken,
                CaptureOrigin("voice", "Voice note", spoken), suggestedDue(spoken)))
        } else if (result.resultCode == Activity.RESULT_OK) reminderMessage = "No speech was captured. Try again."
    }
    val documentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                reminderMessage = "Reading your document…"
                runCatching { activity.contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                val draft = withContext(Dispatchers.IO) { importedText(activity, uri) }
                reminderMessage = null
                (activity as MainActivity).queueCapture(draft)
            } catch (e: Exception) {
                reminderMessage = e.message ?: "Could not read that document."
                selected = Tab.AI
            }
        }
    }
    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            try {
                activity.startForegroundService(Intent(activity, ScreenInsightService::class.java).apply {
                    putExtra(ScreenInsightService.EXTRA_RESULT, result.resultCode)
                    putExtra(ScreenInsightService.EXTRA_CONSENT, result.data)
                })
                controlMessage = "Analyzing the app you selected. Stop from Cue or the notification."
            } catch (e: RuntimeException) {
                controlMessage = e.message ?: "Could not start screen analysis."
            }
        }
    }
    fun channelNotificationsEnabled(id: String): Boolean {
        val manager = activity.getSystemService(NotificationManager::class.java)
        val granted = Build.VERSION.SDK_INT < 33 ||
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val channel = manager.getNotificationChannel(id)
        return granted && manager.areNotificationsEnabled() &&
            channel?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun notificationsEnabled() = channelNotificationsEnabled("cue_reminders")
    fun captureNotificationsEnabled() = channelNotificationsEnabled("cue_capture")
    fun dayNotificationsEnabled() = channelNotificationsEnabled("cue_day_plan")
    fun screenNotificationsEnabled() = channelNotificationsEnabled("cue_screen")
    var notificationAllowed by remember { mutableStateOf(notificationsEnabled()) }
    LaunchedEffect(permissionEpoch) { notificationAllowed = notificationsEnabled() }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationAllowed = granted && notificationsEnabled()
        if (!granted) reminderMessage = "Allow notifications in Android settings to see alerts."
        if (pendingPanelEnable) {
            pendingPanelEnable = false
            if (granted && AssistantControls.canPost(activity)) {
                scope.launch { themeStore.setNotificationPanel(true) }
            } else controlMessage = "Allow Cue notifications to show the shortcut panel."
        }
        if (pendingFloatingEnable) {
            pendingFloatingEnable = false
            if (granted && captureNotificationsEnabled()) {
                if (!Settings.canDrawOverlays(activity)) {
                    pendingOverlayEnable = true
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${activity.packageName}")))
                } else scope.launch { themeStore.setFloatingCue(true) }
            } else controlMessage = "Allow Cue notifications for drop results."
        }
        if (pendingDayEnable) {
            pendingDayEnable = false
            if (granted && dayNotificationsEnabled()) scope.launch {
                themeStore.setDailyPlanEnabled(true)
                DailyPlanScheduler(activity).notifyToday()
            }
            else controlMessage = "Allow notifications for your morning schedule."
        }
        if (pendingScreenStart) {
            pendingScreenStart = false
            if (granted && Build.VERSION.SDK_INT >= 34 && screenNotificationsEnabled()) {
                projectionLauncher.launch(activity.getSystemService(MediaProjectionManager::class.java)
                    .createScreenCaptureIntent())
            } else controlMessage = "Notifications are needed for visible screen analysis controls."
        }
    }

    LaunchedEffect(permissionEpoch, floatingEnabled, panelEnabled) {
        if (pendingFloatingEnable && captureNotificationsEnabled()) {
            pendingFloatingEnable = false
            if (!Settings.canDrawOverlays(activity)) {
                pendingOverlayEnable = true
                activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${activity.packageName}")))
            } else themeStore.setFloatingCue(true)
        }
        if (pendingOverlayEnable && Settings.canDrawOverlays(activity)) {
            pendingOverlayEnable = false
            themeStore.setFloatingCue(true)
        }
        if (pendingPanelEnable && AssistantControls.canPost(activity)) {
            pendingPanelEnable = false
            themeStore.setNotificationPanel(true)
        }
        if (floatingEnabled && !Settings.canDrawOverlays(activity)) {
            themeStore.setFloatingCue(false)
            controlMessage = "Floating Cue stopped because its display permission is off."
        }
        if (panelEnabled && !AssistantControls.canPost(activity)) {
            themeStore.setNotificationPanel(false)
            controlMessage = "Notification shortcuts stopped because notifications are off."
        }
    }

    LaunchedEffect(dailyPlanEnabled, wakeMinute, bedMinute, permissionEpoch) {
        DailyPlanScheduler(activity).sync()
    }

    fun dispatchShortcut(action: CueAction) {
        when (action) {
            CueAction.VOICE -> try {
                voiceLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "What would you like to remember?")
                })
            } catch (_: android.content.ActivityNotFoundException) {
                selected = Tab.AI
                reminderMessage = "Speech recognition isn't available. Use Quick reminder instead."
            }
            CueAction.QUICK -> (activity as MainActivity).queueCapture(
                CaptureDraft(UUID.randomUUID().toString(), "", CaptureOrigin("manual")))
            CueAction.IMPORT -> documentLauncher.launch(arrayOf("text/plain", "text/csv",
                "text/tab-separated-values", "text/markdown", "application/pdf", "image/jpeg",
                "image/png", "image/webp",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            CueAction.ASK -> selected = Tab.AI
            CueAction.DAY -> { selected = Tab.TODAY; todayFocusToken++; dayPlanOpen = true }
            CueAction.SETTINGS -> selected = Tab.YOU
        }
    }
    LaunchedEffect(shortcutAction) {
        val key = shortcutAction ?: return@LaunchedEffect
        onShortcutConsumed()
        if (key == AssistantControls.OPEN_MENU) actionSheetOpen = true
        else CueAction.from(key)?.let(::dispatchShortcut)
    }

    fun perform(success: String, action: suspend () -> Boolean) {
        scope.launch {
            try {
                reminderMessage = if (action()) success else "Saved on this device; account sync is pending."
            } catch (e: Exception) {
                reminderMessage = e.message ?: "Could not save reminder."
            }
        }
    }

    LaunchedEffect(userId, session is SessionStatus.Initializing) {
        if (session is SessionStatus.Initializing) return@LaunchedEffect
        scheduler.setActiveOwner(userId)
        if (userId != null) {
            accountBusy = true
            try {
                cloud.restoreAndClaim(userId)
                HistoryArchive(database).compact(userId)
                accountMessage = "Account synced."
            } catch (e: Exception) {
                accountMessage = "Sync paused: ${e.message ?: "check your connection"}"
            } finally {
                accountBusy = false
                scheduler.restore()
            }
        } else scheduler.restore()
    }

    CueTheme(theme, colorTheme) {
        val colors = MaterialTheme.colorScheme
        LaunchedEffect(userId, floatingEnabled, panelEnabled, floatingOpacity, permissionEpoch,
            colors.primary, colors.onPrimary, colors.surface, colors.onSurface) {
            val floatingActive = userId != null && floatingEnabled && Settings.canDrawOverlays(activity)
            if (floatingActive) {
                try {
                    activity.startForegroundService(Intent(activity, FloatingCueService::class.java).apply {
                        putExtra(AssistantControls.EXTRA_OPACITY, floatingOpacity)
                        putExtra(AssistantControls.EXTRA_PANEL, panelEnabled)
                        putExtra(AssistantControls.EXTRA_ACCENT, colors.primary.toArgb())
                        putExtra(AssistantControls.EXTRA_ON_ACCENT, colors.onPrimary.toArgb())
                        putExtra(AssistantControls.EXTRA_SURFACE, colors.surface.toArgb())
                        putExtra(AssistantControls.EXTRA_ON_SURFACE, colors.onSurface.toArgb())
                    })
                } catch (e: RuntimeException) {
                    controlMessage = e.message ?: "Could not start floating Cue."
                    themeStore.setFloatingCue(false)
                }
            } else activity.stopService(Intent(activity, FloatingCueService::class.java))
            AssistantControls.updatePanel(activity, panelEnabled, floatingActive)
        }
        Scaffold(
            topBar = {
                if (selected != Tab.TODAY && selected != Tab.UPCOMING) {
                    TopAppBar(
                        title = { Text("cue", fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.headlineMedium) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background),
                        actions = {
                            Surface(
                                modifier = Modifier.padding(end = 20.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(if (userId == null) "On this device" else "Sync on",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    )
                }
            },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selected == tab,
                            onClick = { selected = tab },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        ) { padding ->
            when (selected) {
                Tab.TODAY, Tab.UPCOMING -> CommitmentListScreen(
                    items, completed, selected == Tab.UPCOMING, reminderMessage,
                    scheduler.exactAvailable(), notificationAllowed, {
                        activity.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:${activity.packageName}")))
                    },
                    {
                        activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                        })
                    },
                    { id, title, details, due, origin ->
                        perform("Reminder saved.") { actions.save(ownerId, id, title, details, due, origin) }
                        if (due != null && Build.VERSION.SDK_INT >= 33 &&
                            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    { id -> perform("Marked done.") { actions.complete(ownerId, id) } },
                    { id -> perform("Reminder moved 10 minutes ahead.") { actions.snooze(ownerId, id) } },
                    { id -> perform("Reminder archived.") { actions.archive(ownerId, id) } },
                    { id -> perform("Reminder deleted.") { actions.delete(ownerId, id) } },
                    userId != null,
                    {
                        if (userId != null && !accountBusy) scope.launch {
                            accountBusy = true
                            try {
                                cloud.restoreAndClaim(userId)
                                scheduler.restore()
                                reminderMessage = "Reminders synced."
                            } catch (e: Exception) {
                                reminderMessage = "Sync paused: ${e.message ?: "check your connection"}"
                            } finally { accountBusy = false }
                        }
                    },
                    { selected = Tab.YOU },
                    { dayPlanOpen = true },
                    captureDraft, onCaptureDismiss, todayFocusToken,
                    Modifier.padding(padding)
                )
                Tab.AI -> CaptureHub(onVoice = { dispatchShortcut(CueAction.VOICE) },
                    onDocument = { dispatchShortcut(CueAction.IMPORT) },
                    onQuick = { dispatchShortcut(CueAction.QUICK) },
                    message = reminderMessage, modifier = Modifier.padding(padding))
                Tab.INBOX -> HistoryScreen(history, sources, batches, onOpenSource = { value ->
                    runCatching {
                        val uri = Uri.parse(value)
                        activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, activity.contentResolver.getType(uri) ?: "*/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        })
                    }.isSuccess
                }, modifier = Modifier.padding(padding))
                Tab.YOU -> YouScreen(
                    theme, colorTheme,
                    { scope.launch { themeStore.set(it) } },
                    { scope.launch { themeStore.setColorTheme(it) } },
                    userId != null, accountBusy, accountMessage,
                    {
                        scope.launch {
                            accountBusy = true
                            accountMessage = null
                            try { auth.signIn(activity) }
                            catch (e: Exception) { accountMessage = e.message ?: "Sign-in failed." }
                            finally { accountBusy = false }
                        }
                    },
                    {
                        scope.launch {
                            accountBusy = true
                            try {
                                activity.stopService(Intent(activity, FloatingCueService::class.java))
                                activity.stopService(Intent(activity, ScreenInsightService::class.java))
                                themeStore.setFloatingCue(false)
                                auth.signOut()
                                userId?.let {
                                    scheduler.cancelOwner(it)
                                    scheduler.setActiveOwner(null)
                                    cloud.clearAccountCache(it)
                                }
                                accountMessage = null
                            } catch (e: Exception) { accountMessage = e.message ?: "Sign-out failed." }
                            finally { accountBusy = false }
                        }
                    },
                    floatingEnabled, panelEnabled, floatingOpacity,
                    Settings.canDrawOverlays(activity), controlMessage,
                    { enable ->
                        if (!enable) scope.launch { themeStore.setFloatingCue(false) }
                        else if (!captureNotificationsEnabled()) {
                            pendingFloatingEnable = true
                            if (Build.VERSION.SDK_INT >= 33 &&
                                activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED)
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                            })
                        }
                        else if (!Settings.canDrawOverlays(activity)) {
                            pendingOverlayEnable = true
                            activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${activity.packageName}")))
                        } else scope.launch { themeStore.setFloatingCue(true) }
                    },
                    { enable ->
                        if (!enable) scope.launch { themeStore.setNotificationPanel(false) }
                        else if (!AssistantControls.canPost(activity)) {
                            pendingPanelEnable = true
                            if (Build.VERSION.SDK_INT >= 33 &&
                                activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                            })
                        } else scope.launch { themeStore.setNotificationPanel(true) }
                    },
                    { value -> scope.launch { themeStore.setFloatingOpacity(value) } },
                    dailyPlanEnabled, wakeMinute, bedMinute,
                    { enable ->
                        if (!enable) scope.launch { themeStore.setDailyPlanEnabled(false) }
                        else if (Build.VERSION.SDK_INT >= 33 &&
                            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED) {
                            pendingDayEnable = true
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else if (!dayNotificationsEnabled()) activity.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                            })
                        else scope.launch {
                            themeStore.setDailyPlanEnabled(true)
                            DailyPlanScheduler(activity).notifyToday()
                        }
                    },
                    { wake, bed -> scope.launch {
                        if (wake in 0..1080 && bed in 480..1439 && bed - wake >= 360)
                            themeStore.setDayHours(wake, bed)
                        else controlMessage = "Choose a bedtime at least six hours after waking."
                    } },
                    { dayPlanOpen = true },
                    screenRunning,
                    {
                        if (screenRunning) activity.stopService(Intent(activity, ScreenInsightService::class.java))
                        else if (Build.VERSION.SDK_INT < 34) controlMessage =
                            "Choose-one-app analysis needs Android 14 or later."
                        else if (Build.VERSION.SDK_INT >= 33 &&
                            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED) {
                            pendingScreenStart = true
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else if (!screenNotificationsEnabled()) activity.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                            })
                        } else projectionLauncher.launch(
                            activity.getSystemService(MediaProjectionManager::class.java)
                                .createScreenCaptureIntent())
                    },
                    padding
                )
            }
        }
        if (actionSheetOpen) AssistantActionSheet(onDismiss = { actionSheetOpen = false }) { action ->
            actionSheetOpen = false
            dispatchShortcut(action)
        }
        if (dayPlanOpen) DailyPlanSheet(items, wakeMinute, bedMinute) { dayPlanOpen = false }
    }
}

@Composable
private fun EmptyScreen(title: String, description: String, icon: ImageVector, padding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding).padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(22.dp).size(40.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(20.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YouScreen(
    theme: ThemePreference,
    colorTheme: ColorTheme,
    onTheme: (ThemePreference) -> Unit,
    onColorTheme: (ColorTheme) -> Unit,
    signedIn: Boolean,
    accountBusy: Boolean,
    accountMessage: String?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    floatingEnabled: Boolean,
    panelEnabled: Boolean,
    floatingOpacity: Float,
    overlayAllowed: Boolean,
    controlMessage: String?,
    onFloating: (Boolean) -> Unit,
    onPanel: (Boolean) -> Unit,
    onOpacity: (Float) -> Unit,
    dailyPlanEnabled: Boolean,
    wakeMinute: Int,
    bedMinute: Int,
    onDailyPlan: (Boolean) -> Unit,
    onDayHours: (Int, Int) -> Unit,
    onViewDay: () -> Unit,
    screenRunning: Boolean,
    onScreenToggle: () -> Unit,
    padding: PaddingValues
) {
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Your space", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Account and appearance, just the way you like it.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(22.dp))
        Card(shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text(if (signedIn) "Your reminders are synced" else "Your reminders stay on this device",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Text(if (signedIn) "Your Google account keeps your reminders available when you return."
                    else "Sign in with Google to save and restore them across devices.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                if (signedIn) {
                    Button(onClick = onSignOut, enabled = !accountBusy) { Text("Sign out") }
                } else {
                    Button(onClick = onSignIn, enabled = !accountBusy && BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
                        Text("Continue with Google")
                    }
                    if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
                        Text("Google sign-in needs Cue's OAuth setup.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                accountMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
        }
        Spacer(Modifier.height(32.dp))
        Text("Cue controls", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Bring your six shortcuts to other apps or your notification panel.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Card(shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Floating Cue", fontWeight = FontWeight.SemiBold)
                        Text(if (signedIn) "Drag text or an image onto the bubble; results appear in notifications."
                            else "Sign in to enable floating Cue.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = signedIn && floatingEnabled,
                        enabled = signedIn && !accountBusy,
                        onCheckedChange = onFloating)
                }
                if (signedIn && !overlayAllowed && !floatingEnabled) Text(
                    "Android will ask for ‘Display over other apps’ when you switch this on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (signedIn && floatingEnabled) {
                    var draftOpacity by remember { mutableFloatStateOf(floatingOpacity) }
                    LaunchedEffect(floatingOpacity) { draftOpacity = floatingOpacity }
                    Text("Collapsed opacity · ${(draftOpacity * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 15.dp))
                    Slider(value = draftOpacity, onValueChange = { draftOpacity = it },
                        onValueChangeFinished = { onOpacity(draftOpacity) },
                        valueRange = 0.35f..1f)
                }
                androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Notification shortcuts", fontWeight = FontWeight.SemiBold)
                        Text("Voice and Quick actions, plus a menu with all six.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = panelEnabled, onCheckedChange = onPanel)
                }
                controlMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp)) }
            }
        }
        Spacer(Modifier.height(32.dp))
        Text("Daily schedule", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Cue builds your day around fixed reminders and your waking hours.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        var editingDayHour by remember { mutableStateOf<String?>(null) }
        Card(shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Morning plan notification", fontWeight = FontWeight.SemiBold)
                        Text("A fresh schedule when your day begins.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = dailyPlanEnabled, onCheckedChange = onDailyPlan)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.OutlinedButton(onClick = { editingDayHour = "wake" }) {
                        Text("Morning · ${timeLabel(wakeMinute)}")
                    }
                    androidx.compose.material3.OutlinedButton(onClick = { editingDayHour = "bed" }) {
                        Text("Bed · ${timeLabel(bedMinute)}")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onViewDay) { Text("View today's schedule") }
            }
        }
        editingDayHour?.let { which ->
            key(which) {
                val initial = if (which == "wake") wakeMinute else bedMinute
                val picker = rememberTimePickerState(initialHour = initial / 60,
                    initialMinute = initial % 60, is24Hour = false)
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { editingDayHour = null },
                    title = { Text(if (which == "wake") "Morning starts" else "Bedtime") },
                    text = { androidx.compose.material3.TimeInput(state = picker) },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            val minute = picker.hour * 60 + picker.minute
                            onDayHours(if (which == "wake") minute else wakeMinute,
                                if (which == "bed") minute else bedMinute)
                            editingDayHour = null
                        }) { Text("Save") }
                    }, dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { editingDayHour = null }) {
                            Text("Cancel")
                        }
                    })
            }
        }
        Spacer(Modifier.height(32.dp))
        Text("Screen analysis", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Choose one app to analyze during a session. Android asks for consent every time.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Card(shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text(if (screenRunning) "Analysis is active" else "Capture useful reminders",
                    fontWeight = FontWeight.SemiBold)
                Text("Select one app in Android's picker, not Entire screen. Cue samples text locally " +
                    "and only adds reminders with a clear future time. No video is saved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 7.dp, bottom = 15.dp))
                Button(onClick = onScreenToggle, enabled = Build.VERSION.SDK_INT >= 34 || screenRunning) {
                    Text(if (screenRunning) "Stop analysis" else "Choose app and start")
                }
                if (Build.VERSION.SDK_INT < 34) Text("Needs Android 14+ for app selection.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(32.dp))
        Text("Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Choose how Cue looks on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Card(shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                ThemePreference.entries.forEachIndexed { index, value ->
                    SegmentedButton(
                        selected = theme == value,
                        onClick = { onTheme(value) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemePreference.entries.size),
                        label = { Text(value.label) }
                    )
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Text("Color theme", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Pick a palette that feels like yours.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        ColorTheme.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                row.forEach { option ->
                    Card(
                        modifier = Modifier.weight(1f).height(104.dp).clickable { onColorTheme(option) },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(
                            2.dp,
                            if (colorTheme == option) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(Modifier.padding(15.dp)) {
                            Surface(modifier = Modifier.size(30.dp), shape = CircleShape,
                                color = themeSwatch(option)) {}
                            Spacer(Modifier.height(11.dp))
                            Text(option.label, style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (colorTheme == option) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
