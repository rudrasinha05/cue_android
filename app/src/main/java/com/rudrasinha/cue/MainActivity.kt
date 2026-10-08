package com.rudrasinha.cue

import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.media.projection.MediaProjectionManager
import android.media.projection.MediaProjectionConfig
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
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
import com.rudrasinha.cue.assistant.CueNotificationListener
import com.rudrasinha.cue.assistant.UsagePatternStore
import com.rudrasinha.cue.data.CommitmentActions
import com.rudrasinha.cue.data.CueDatabase
import com.rudrasinha.cue.data.CloudCommitments
import com.rudrasinha.cue.data.CaptureOrigin
import com.rudrasinha.cue.data.HistoryArchive
import com.rudrasinha.cue.data.AccountData
import com.rudrasinha.cue.data.CueProfile
import com.rudrasinha.cue.data.CueProfileStore
import com.rudrasinha.cue.auth.CueAuth
import com.rudrasinha.cue.auth.signInErrorMessage
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
import com.rudrasinha.cue.reminders.ReminderTones
import com.rudrasinha.cue.ui.ReminderTonePicker
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
    private fun processSharedText(incoming: Intent?) {
        val draft = sharedText(incoming) ?: return
        if (incoming?.getBooleanExtra("com.rudrasinha.cue.REVIEW_CAPTURE", false) == true) {
            queueCapture(draft)
        } else intakeScope.launch {
            try { CaptureIntake(applicationContext).accept(draft.text, draft.origin.type,
                draft.origin.title, draft.origin.uri) }
            catch (e: Exception) { CaptureIntake(applicationContext).failure(
                e.message ?: "Could not process the shared text.") }
        }
    }
    private fun readSharedUri(incoming: Intent?) {
        if (incoming?.action != Intent.ACTION_SEND || sharedText(incoming) != null) return
        val uri = if (Build.VERSION.SDK_INT >= 33)
            incoming.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else @Suppress("DEPRECATION") incoming.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        val source = uri ?: incoming.clipData?.getItemAt(0)?.uri ?: return
        intakeScope.launch {
            runCatching { withContext(Dispatchers.IO) { importedText(this@MainActivity, source) } }
                .onSuccess { draft ->
                    try { CaptureIntake(applicationContext).accept(draft.text, draft.origin.type,
                        draft.origin.title, draft.origin.uri) }
                    catch (e: Exception) { CaptureIntake(applicationContext).failure(
                        e.message ?: "Could not process the shared file.") }
                }
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
        processSharedText(intent)
        readSharedUri(intent)
        shortcutAction = intent.getStringExtra(AssistantControls.EXTRA_ACTION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            processSharedText(intent)
            readSharedUri(intent)
        }
        shortcutAction = intent?.getStringExtra(AssistantControls.EXTRA_ACTION)
        val themeStore = ThemeStore(applicationContext)
        val database = CueDatabase.get(applicationContext)
        val auth = CueAuth(applicationContext)
        val cloud = CloudCommitments(database, auth.client, ReminderScheduler(applicationContext))
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
    val floatingPreference by themeStore.floatingCue.collectAsState(initial = null)
    val floatingEnabled = floatingPreference == true
    val reminderTone by themeStore.reminderTone.collectAsState(initial = ReminderTones.DEFAULT)
    val toneRepeats by themeStore.toneRepeats.collectAsState(initial = 3)
    val panelEnabled by themeStore.notificationPanel.collectAsState(initial = false)
    val floatingOpacity by themeStore.floatingOpacity.collectAsState(initial = 0.82f)
    val floatingSize by themeStore.floatingSize.collectAsState(initial = 64)
    val screenRunning by ScreenInsightService.running.collectAsState()
    val dailyPlanEnabled by themeStore.dailyPlanEnabled.collectAsState(initial = false)
    val notificationIntelligence by themeStore.notificationIntelligence.collectAsState(initial = false)
    val usageLearning by themeStore.usageLearningEnabled.collectAsState(initial = false)
    val cloudAnalysisOwner by themeStore.cloudAnalysisOwner.collectAsState(initial = null)
    val wakeMinute by themeStore.wakeMinute.collectAsState(initial = 420)
    val bedMinute by themeStore.bedMinute.collectAsState(initial = 1320)
    val session by auth.client.auth.sessionStatus.collectAsState(initial = SessionStatus.Initializing)
    val userId = (session as? SessionStatus.Authenticated)?.session?.user?.id
    val ownerId = userId ?: "guest"
    val accessToken = (session as? SessionStatus.Authenticated)?.session?.accessToken
    var profile by remember(userId) { mutableStateOf<CueProfile?>(null) }
    var profileBusy by remember(userId) { mutableStateOf(false) }
    var profileError by remember(userId) { mutableStateOf<String?>(null) }
    val profileStore = remember { CueProfileStore() }
    LaunchedEffect(userId, accessToken) {
        if (userId != null && accessToken != null) {
            profileBusy = true
            try { profile = profileStore.load(accessToken); profileError = null }
            catch (e: Exception) { profileError = "Profile could not load. Check your connection." }
            finally { profileBusy = false }
        }
    }
    val items by remember(ownerId) { commitments.observeActive(ownerId) }.collectAsState(initial = emptyList())
    val completed by remember(ownerId) { commitments.observeCompleted(ownerId) }.collectAsState(initial = emptyList())
    val history by remember(ownerId) { database.history().observeEvents(ownerId) }.collectAsState(initial = emptyList())
    val sources by remember(ownerId) { database.history().observeSources(ownerId) }.collectAsState(initial = emptyList())
    val batches by remember(ownerId) { database.history().observeBatches(ownerId) }.collectAsState(initial = emptyList())
    val scheduler = remember { ReminderScheduler(activity.applicationContext) }
    val actions = remember { CommitmentActions(database, scheduler, cloud) }
    val usagePatterns = remember { UsagePatternStore(activity.applicationContext) }
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var todayFocusToken by remember { mutableIntStateOf(0) }
    var actionSheetOpen by remember { mutableStateOf(false) }
    var dayPlanOpen by remember { mutableStateOf(false) }
    var pendingUsageAccess by remember { mutableStateOf(false) }
    var usageRequestEpoch by remember { mutableIntStateOf(-1) }
    var usageProfileEpoch by remember { mutableIntStateOf(0) }
    var controlMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(permissionEpoch, pendingUsageAccess) {
        if (pendingUsageAccess && permissionEpoch > usageRequestEpoch) {
            pendingUsageAccess = false
            if (usagePatterns.hasAccess()) {
                themeStore.setUsageLearning(true)
                withContext(Dispatchers.IO) { usagePatterns.capture(ownerId) }
                usageProfileEpoch++
            } else controlMessage = "Usage Access was not enabled; routine learning remains off."
        }
    }
    LaunchedEffect(permissionEpoch, usageLearning) {
        if (usageLearning && !usagePatterns.hasAccess()) themeStore.setUsageLearning(false)
    }
    var pendingOverlayEnable by remember { mutableStateOf(false) }
    var pendingFloatingEnable by remember { mutableStateOf(false) }
    var pendingPanelEnable by remember { mutableStateOf(false) }
    var pendingDayEnable by remember { mutableStateOf(false) }
    var pendingScreenStart by remember { mutableStateOf(false) }
    var pendingNotificationAccess by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(captureDraft?.id) { if (captureDraft != null) selected = Tab.TODAY }
    var accountMessage by remember { mutableStateOf<String?>(null) }
    var accountBusy by remember { mutableStateOf(false) }
    var reminderMessage by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val payload = pendingExport
        pendingExport = null
        if (uri != null && payload != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    activity.contentResolver.openOutputStream(uri)?.use { it.write(payload) }
                        ?: error("Could not open the export destination.")
                }
                accountMessage = "Cue data exported."
            } catch (e: Exception) {
                accountMessage = "Export failed: ${e.message ?: "could not write file"}"
            } finally { accountBusy = false }
        } else accountBusy = false
    }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()?.trim()
        if (!spoken.isNullOrBlank()) scope.launch {
            try { CaptureIntake(activity.applicationContext).accept(spoken, "voice", "Voice note") }
            catch (e: Exception) { reminderMessage = e.message ?: "Could not process your voice note." }
        } else if (result.resultCode == Activity.RESULT_OK) reminderMessage = "No speech was captured. Try again."
    }
    var timetableDraft by remember { mutableStateOf<CaptureDraft?>(null) }
    var facultyCodes by remember { mutableStateOf("") }
    var timetableSaving by remember { mutableStateOf(false) }
    var calendarEvents by remember { mutableStateOf<List<GoogleCalendarImport.Event>?>(null) }
    var chosenCalendarKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var calendarBusy by remember { mutableStateOf(false) }
    var mailImportOpen by remember { mutableStateOf(false) }
    var mailImportText by remember { mutableStateOf("") }
    fun readGoogleCalendar() {
        scope.launch {
            calendarBusy = true
            try {
                calendarEvents = withContext(Dispatchers.IO) {
                    GoogleCalendarImport.upcoming(activity)
                }
                chosenCalendarKeys = emptySet()
            } catch (e: Exception) {
                controlMessage = "Could not read Google Calendar: ${e.message ?: "try again"}"
            } finally { calendarBusy = false }
        }
    }
    val calendarPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) readGoogleCalendar()
        else controlMessage = "Calendar permission is needed to review Google events."
    }
    val documentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                reminderMessage = "Reading your document…"
                runCatching { activity.contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                val draft = withContext(Dispatchers.IO) { importedText(activity, uri) }
                reminderMessage = null
                val filename = draft.origin.title.orEmpty()
                if (filename.endsWith(".xlsx", true) || filename.endsWith(".csv", true) ||
                    filename.endsWith(".tsv", true)) {
                    timetableDraft = draft
                } else CaptureIntake(activity.applicationContext).accept(draft.text,
                    draft.origin.type, draft.origin.title, draft.origin.uri)
            } catch (e: Exception) {
                reminderMessage = e.message ?: "Could not read that document."
            }
        }
    }
    var scanOncePending by remember { mutableStateOf(false) }
    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            try {
                activity.startForegroundService(Intent(activity, ScreenInsightService::class.java).apply {
                    putExtra(ScreenInsightService.EXTRA_RESULT, result.resultCode)
                    putExtra(ScreenInsightService.EXTRA_CONSENT, result.data)
                    putExtra(ScreenInsightService.EXTRA_ONCE, scanOncePending)
                })
                if (scanOncePending) activity.moveTaskToBack(true)
                else controlMessage = "Analyzing the screen you shared. Stop from Cue or the notification."
            } catch (e: RuntimeException) {
                controlMessage = e.message ?: "Could not start screen analysis."
            }
        }
        scanOncePending = false
    }
    fun channelNotificationsEnabled(id: String): Boolean {
        val manager = activity.getSystemService(NotificationManager::class.java)
        val granted = Build.VERSION.SDK_INT < 33 ||
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val channel = manager.getNotificationChannel(id)
        return granted && manager.areNotificationsEnabled() &&
            channel?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun notificationsEnabled() = channelNotificationsEnabled(ReminderTones.ALARM_CHANNEL)
    fun captureNotificationsEnabled() = channelNotificationsEnabled("cue_capture")
    fun dayNotificationsEnabled() = channelNotificationsEnabled("cue_day_plan")
    fun screenNotificationsEnabled() = channelNotificationsEnabled("cue_screen")
    val listenerComponent = remember { ComponentName(activity, CueNotificationListener::class.java) }
    val listenerAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
        activity.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(listenerComponent)
    var notificationAllowed by remember { mutableStateOf(notificationsEnabled()) }
    LaunchedEffect(permissionEpoch, reminderTone) { notificationAllowed = notificationsEnabled() }
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
    LaunchedEffect(permissionEpoch, notificationIntelligence) {
        if (pendingNotificationAccess && permissionEpoch > 0) {
            pendingNotificationAccess = false
            if (listenerAccess && captureNotificationsEnabled())
                themeStore.setNotificationIntelligence(true)
            else controlMessage = "Allow Cue notification access to enable deadline detection."
        }
        if (notificationIntelligence && !listenerAccess) {
            themeStore.setNotificationIntelligence(false)
            controlMessage = "Notification access was removed; suggestions are off."
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
                reminderMessage = "Speech recognition isn't available. Use Quick reminder instead."
            }
            CueAction.QUICK -> (activity as MainActivity).queueCapture(
                CaptureDraft(UUID.randomUUID().toString(), "", CaptureOrigin("manual")))
            CueAction.IMPORT -> documentLauncher.launch(arrayOf("text/plain", "text/csv",
                "text/tab-separated-values", "text/markdown", "application/pdf", "image/jpeg",
                "image/png", "image/webp",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            CueAction.SCREEN -> {
                if (Build.VERSION.SDK_INT >= 34) {
                    scanOncePending = true
                    projectionLauncher.launch(activity.getSystemService(MediaProjectionManager::class.java)
                        .createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()))
                } else controlMessage = "Scan this screen needs Android 14 or newer."
            }
            CueAction.DAY -> { selected = Tab.TODAY; todayFocusToken++; dayPlanOpen = true }
            CueAction.SETTINGS -> { selected = Tab.YOU; settingsOpen = true }
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
        LaunchedEffect(userId, floatingEnabled, panelEnabled, floatingOpacity, floatingSize, permissionEpoch,
            colors.primary, colors.onPrimary, colors.surface, colors.onSurface) {
            if (floatingPreference == null || session is SessionStatus.Initializing) return@LaunchedEffect
            val floatingActive = userId != null && floatingEnabled && Settings.canDrawOverlays(activity)
            if (floatingActive) {
                try {
                    activity.startForegroundService(Intent(activity, FloatingCueService::class.java).apply {
                        putExtra(AssistantControls.EXTRA_OPACITY, floatingOpacity)
                        putExtra(AssistantControls.EXTRA_SIZE, floatingSize)
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
                            onClick = { selected = tab; settingsOpen = false },
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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            activity.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:${activity.packageName}")))
                    },
                    {
                        activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                        })
                    },
                    reminderTone,
                    { id, title, details, due, origin, tone, attachment ->
                        val synced = actions.save(ownerId, id, title, details, due, origin, tone, attachment)
                        reminderMessage = if (synced) "Reminder saved."
                            else "Saved on this device; account sync is pending."
                        if (due != null && Build.VERSION.SDK_INT >= 33 &&
                            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    sources,
                    { id -> perform("Marked done.") { actions.complete(ownerId, id) } },
                    { id -> perform("Reminder moved 10 minutes ahead.") { actions.snooze(ownerId, id) } },
                    { id, days -> perform("Follow-up scheduled.") { actions.followUp(ownerId, id, days) } },
                    { id, offsets -> perform(if (offsets != null) "Extra nudges scheduled." else "Extra nudges off.") {
                        actions.setChain(ownerId, id, offsets)
                    } },
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
                    { selected = Tab.YOU; settingsOpen = true },
                    { dayPlanOpen = true },
                    captureDraft, onCaptureDismiss, todayFocusToken,
                    onVoice = { dispatchShortcut(CueAction.VOICE) },
                    onImport = { dispatchShortcut(CueAction.IMPORT) },
                    modifier = Modifier.padding(padding)
                )
                Tab.INBOX -> HistoryScreen(history, sources, batches,
                    loadBatch = { id -> database.history().batchById(ownerId, id) },
                    onOpenSource = { value ->
                    runCatching {
                        val uri = Uri.parse(value)
                        activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, activity.contentResolver.getType(uri) ?: "*/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        })
                    }.isSuccess
                }, modifier = Modifier.padding(padding))
                Tab.YOU -> YouScreen(
                    settingsOpen, { settingsOpen = it },
                    (session as? SessionStatus.Authenticated)?.session?.user?.email,
                    userId,
                    profile, profileBusy, profileError,
                    { name, gender, mobile ->
                        if (accessToken != null && !profileBusy) scope.launch {
                            profileBusy = true
                            try {
                                profile = profileStore.save(accessToken, name, gender, mobile)
                                profileError = null
                            } catch (e: Exception) {
                                profileError = e.message ?: "Could not save your profile."
                            } finally { profileBusy = false }
                        }
                    },
                    theme, colorTheme,
                    { scope.launch { themeStore.set(it) } },
                    { scope.launch { themeStore.setColorTheme(it) } },
                    userId != null, accountBusy, accountMessage,
                    {
                        scope.launch {
                            accountBusy = true
                            accountMessage = null
                            try { auth.signIn(activity) }
                            catch (e: Exception) { accountMessage = signInErrorMessage(e) }
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
                    {
                        if (!accountBusy) scope.launch {
                            accountBusy = true
                            try {
                                pendingExport = withContext(Dispatchers.IO) {
                                    AccountData(database, scheduler, cloud).export(ownerId)
                                }
                                exportLauncher.launch("cue-export-${java.time.LocalDate.now()}.json")
                            } catch (e: Exception) {
                                pendingExport = null
                                accountBusy = false
                                accountMessage = "Export failed: ${e.message ?: "try again online"}"
                            }
                        }
                    },
                    {
                        if (!accountBusy) scope.launch {
                            accountBusy = true
                            try {
                                activity.stopService(Intent(activity, FloatingCueService::class.java))
                                activity.stopService(Intent(activity, ScreenInsightService::class.java))
                                themeStore.setFloatingCue(false)
                                themeStore.setNotificationIntelligence(false)
                                themeStore.setDailyPlanEnabled(false)
                                themeStore.setUsageLearning(false)
                                usagePatterns.clear(ownerId)
                                AccountData(database, scheduler, cloud).delete(ownerId)
                                accountMessage = "Your Cue reminders and history were deleted."
                            } catch (e: Exception) {
                                accountMessage = "Deletion failed: ${e.message ?: "try again online"}"
                            } finally { accountBusy = false }
                        }
                    },
                    {
                        if (userId != null && accessToken != null && !accountBusy) scope.launch {
                            accountBusy = true
                            accountMessage = null
                            try {
                                profileStore.deleteAccount(accessToken)
                                activity.stopService(Intent(activity, FloatingCueService::class.java))
                                activity.stopService(Intent(activity, ScreenInsightService::class.java))
                                themeStore.setFloatingCue(false)
                                themeStore.setCloudAnalysisOwner(null)
                                themeStore.setUsageLearning(false)
                                usagePatterns.clear(userId)
                                AccountData(database, scheduler, cloud).deleteLocal(userId)
                                cloud.clearAccountCache(userId)
                                scheduler.setActiveOwner(null)
                                val signedOut = runCatching { auth.signOut() }.isSuccess
                                accountMessage = if (signedOut)
                                    "Your Cue account and its data were deleted."
                                else "Cue account deleted, but local sign-out did not finish. Restart Cue; if the old profile still appears, tap Sign out."
                            } catch (e: Exception) {
                                accountMessage = e.message ?: "Account deletion failed. Try again online."
                            } finally { accountBusy = false }
                        }
                    },
                    floatingEnabled, panelEnabled, floatingOpacity, floatingSize,
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
                    { value -> scope.launch { themeStore.setFloatingSize(value) } },
                    Build.VERSION.SDK_INT < 34 || activity.getSystemService(NotificationManager::class.java)
                        .canUseFullScreenIntent(),
                    {
                        if (Build.VERSION.SDK_INT >= 34) runCatching {
                            activity.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                Uri.parse("package:${activity.packageName}")))
                        }.onFailure { controlMessage = "Alarm popup settings aren't available on this device." }
                    },
                    reminderTone, { tone ->
                        scope.launch {
                            ReminderTones.ensureChannel(activity, tone)
                            themeStore.setReminderTone(tone)
                        }
                    },
                    toneRepeats, { count -> scope.launch { themeStore.setToneRepeats(count) } },
                    dailyPlanEnabled, wakeMinute, bedMinute,
                    { enable ->
                        if (!enable) scope.launch { themeStore.setDailyPlanEnabled(false) }
                        else if (Build.VERSION.SDK_INT >= 33 &&
                            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED) {
                            pendingDayEnable = true
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else if (!dayNotificationsEnabled()) {
                            activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                            })
                        } else scope.launch {
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
                            "Screen analysis needs Android 14 or later."
                        else if (Build.VERSION.SDK_INT >= 33 &&
                            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED) {
                            pendingScreenStart = true
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else if (!screenNotificationsEnabled()) {
                            activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                            })
                        } else projectionLauncher.launch(
                            activity.getSystemService(MediaProjectionManager::class.java)
                                .createScreenCaptureIntent())
                    },
                    notificationIntelligence && listenerAccess,
                    { enable ->
                        if (!enable) scope.launch { themeStore.setNotificationIntelligence(false) }
                        else if (!captureNotificationsEnabled()) {
                            controlMessage = "Allow Cue notifications to receive reminder confirmations."
                            activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                            })
                        } else if (!listenerAccess) {
                            pendingNotificationAccess = true
                            activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        } else scope.launch { themeStore.setNotificationIntelligence(true) }
                    },
                    cloudAnalysisOwner == userId && userId != null,
                    { enable -> scope.launch { themeStore.setCloudAnalysisOwner(if (enable) userId else null) } },
                    usageLearning, remember(ownerId, usageProfileEpoch, permissionEpoch) {
                        usagePatterns.summary(ownerId)
                    },
                    { enable ->
                        if (!enable) scope.launch { themeStore.setUsageLearning(false) }
                        else if (usagePatterns.hasAccess()) scope.launch {
                            themeStore.setUsageLearning(true)
                            withContext(Dispatchers.IO) { usagePatterns.capture(ownerId) }
                            usageProfileEpoch++
                        } else {
                            pendingUsageAccess = true
                            usageRequestEpoch = permissionEpoch
                            activity.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                    },
                    { scope.launch {
                        themeStore.setUsageLearning(false)
                        usagePatterns.clear(ownerId)
                        usageProfileEpoch++
                    } },
                    calendarBusy,
                    {
                        if (activity.checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
                            PackageManager.PERMISSION_GRANTED) readGoogleCalendar()
                        else calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                    },
                    { mailImportText = ""; mailImportOpen = true },
                    padding
                )
            }
        }
        if (actionSheetOpen) AssistantActionSheet(onDismiss = { actionSheetOpen = false }) { action ->
            actionSheetOpen = false
            dispatchShortcut(action)
        }
        if (dayPlanOpen) DailyPlanSheet(items, wakeMinute, bedMinute) { dayPlanOpen = false }
        if (mailImportOpen) AlertDialog(onDismissRequest = { mailImportOpen = false },
            title = { Text("Import from an email") },
            text = { Column {
                Text("Copy the relevant booking or interview message from Gmail and paste it here. Cue will add a reminder only when it finds a clear future time.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(mailImportText, onValueChange = { mailImportText = it.take(4000) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                    label = { Text("Paste message") })
            } },
            confirmButton = { TextButton(enabled = mailImportText.isNotBlank(), onClick = {
                val selectedMail = mailImportText
                mailImportOpen = false
                scope.launch {
                    try { CaptureIntake(activity.applicationContext).accept(selectedMail, "mail", "Pasted email") }
                    catch (e: Exception) { reminderMessage = e.message ?: "Could not analyze this message." }
                }
            }) { Text("Analyze message") } },
            dismissButton = { TextButton(onClick = { mailImportOpen = false }) { Text("Cancel") } })
        calendarEvents?.let { events ->
            AlertDialog(onDismissRequest = { if (!calendarBusy) calendarEvents = null },
                title = { Text("Import Google events") },
                text = { Column {
                    Text("Choose ticket bookings, interviews or other events. Cue will alert before their start. Only selected events are saved.")
                    Spacer(Modifier.height(12.dp))
                    if (events.isEmpty()) Text("No upcoming events found in a Google calendar on this device.")
                    Column(Modifier.heightIn(max = 390.dp).verticalScroll(rememberScrollState())) {
                        events.forEach { event ->
                            val label = java.time.Instant.ofEpochMilli(event.startsAt)
                                .atZone(java.time.ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("d MMM · h:mm a"))
                            Row(Modifier.fillMaxWidth().clickable {
                                chosenCalendarKeys = if (event.key in chosenCalendarKeys)
                                    chosenCalendarKeys - event.key else chosenCalendarKeys + event.key
                            }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = event.key in chosenCalendarKeys,
                                    onCheckedChange = { checked ->
                                        chosenCalendarKeys = if (checked) chosenCalendarKeys + event.key
                                            else chosenCalendarKeys - event.key
                                    })
                                Column {
                                    Text(event.title, fontWeight = FontWeight.SemiBold)
                                    Text("$label · ${event.calendar}",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                } },
                confirmButton = { TextButton(enabled = chosenCalendarKeys.isNotEmpty() && !calendarBusy,
                    onClick = { scope.launch {
                        calendarBusy = true
                        try {
                            var added = 0
                            events.filter { it.key in chosenCalendarKeys }.forEach { event ->
                                if (database.history().sourceByOrigin(ownerId, "google_calendar", event.key) == null) {
                                    actions.save(ownerId, null, event.title,
                                        listOfNotNull(event.location,
                                            "Event: " + java.time.Instant.ofEpochMilli(event.startsAt)
                                                .atZone(java.time.ZoneId.systemDefault())).joinToString(" · "),
                                        event.alertAt,
                                        CaptureOrigin("google_calendar", event.calendar, event.title,
                                            null, event.key), reminderTone)
                                    added++
                                }
                            }
                            reminderMessage = "$added Google Calendar reminders added."
                            calendarEvents = null
                        } catch (e: Exception) {
                            controlMessage = e.message ?: "Calendar import failed."
                        } finally { calendarBusy = false }
                    } }) { Text(if (calendarBusy) "Importing…" else "Add selected") } },
                dismissButton = { TextButton(onClick = { calendarEvents = null }) { Text("Cancel") } })
        }
        timetableDraft?.let { draft ->
            val matches = ProfessorTimetable.matches(draft.text, facultyCodes)
            AlertDialog(onDismissRequest = { if (!timetableSaving) timetableDraft = null },
                title = { Text("Your lecture timetable") },
                text = { Column {
                    Text("Enter your faculty abbreviation exactly as it appears in the file. Separate multiple codes with commas. Cue will use only matching rows with a weekday and start time.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(facultyCodes, onValueChange = { facultyCodes = it },
                        label = { Text("My code, e.g. RS") }, singleLine = true)
                    Spacer(Modifier.height(12.dp))
                    Text(if (matches.isEmpty()) "No matching lecture rows found. Check the code and file layout. Nothing will be added."
                        else "${matches.size} matching slots · up to four upcoming dates per slot")
                    matches.take(6).forEach { Text("${it.day.name.lowercase().replaceFirstChar(Char::uppercase)} · ${it.start} · ${it.title}") }
                } },
                confirmButton = { TextButton(enabled = matches.isNotEmpty() && !timetableSaving,
                    onClick = { scope.launch {
                        timetableSaving = true
                        try {
                            var added = 0
                            matches.forEach { lecture ->
                                ProfessorTimetable.nextFourWeeks(lecture).forEach { due ->
                                    val key = "timetable:${draft.origin.uri}:${lecture.day}:${lecture.start}:${lecture.title}:$due"
                                    if (database.history().sourceByOrigin(ownerId, "timetable", key) == null) {
                                        actions.save(ownerId, null, lecture.title, lecture.row, due,
                                            CaptureOrigin("timetable", draft.origin.title, lecture.row,
                                                draft.origin.uri, key), reminderTone)
                                        added++
                                    }
                                }
                            }
                            reminderMessage = "$added lecture reminders added for the next four weeks."
                            timetableDraft = null
                        } catch (e: Exception) {
                            reminderMessage = e.message ?: "Could not import timetable."
                        } finally { timetableSaving = false }
                    } }) { Text(if (timetableSaving) "Adding…" else "Add my lectures") } },
                dismissButton = { TextButton(onClick = { timetableDraft = null }) { Text("Cancel") } })
        }
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
    settingsOpen: Boolean,
    onSettingsOpen: (Boolean) -> Unit,
    accountEmail: String?,
    accountId: String?,
    profile: CueProfile?,
    profileBusy: Boolean,
    profileError: String?,
    onSaveProfile: (String, String, String) -> Unit,
    theme: ThemePreference,
    colorTheme: ColorTheme,
    onTheme: (ThemePreference) -> Unit,
    onColorTheme: (ColorTheme) -> Unit,
    signedIn: Boolean,
    accountBusy: Boolean,
    accountMessage: String?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onExportData: () -> Unit,
    onDeleteData: () -> Unit,
    onDeleteAccount: () -> Unit,
    floatingEnabled: Boolean,
    panelEnabled: Boolean,
    floatingOpacity: Float,
    floatingSize: Int,
    overlayAllowed: Boolean,
    controlMessage: String?,
    onFloating: (Boolean) -> Unit,
    onPanel: (Boolean) -> Unit,
    onOpacity: (Float) -> Unit,
    onSize: (Int) -> Unit,
    fullScreenAllowed: Boolean,
    onFullScreenAccess: () -> Unit,
    reminderTone: String,
    onReminderTone: (String) -> Unit,
    toneRepeats: Int,
    onToneRepeats: (Int) -> Unit,
    dailyPlanEnabled: Boolean,
    wakeMinute: Int,
    bedMinute: Int,
    onDailyPlan: (Boolean) -> Unit,
    onDayHours: (Int, Int) -> Unit,
    onViewDay: () -> Unit,
    screenRunning: Boolean,
    onScreenToggle: () -> Unit,
    notificationIntelligence: Boolean,
    onNotificationIntelligence: (Boolean) -> Unit,
    cloudAnalysisEnabled: Boolean,
    onCloudAnalysis: (Boolean) -> Unit,
    usageLearning: Boolean,
    usageSummary: String,
    onUsageLearning: (Boolean) -> Unit,
    onClearUsage: () -> Unit,
    calendarBusy: Boolean,
    onImportGoogleCalendar: () -> Unit,
    onImportMail: () -> Unit,
    padding: PaddingValues
) {
    var helpPage by remember { mutableStateOf<String?>(null) }
    var deleteDataPrompt by remember { mutableStateOf(false) }
    var deleteAccountPrompt by remember { mutableStateOf(false) }
    var cloudConsentPrompt by remember { mutableStateOf(false) }
    var editingProfile by remember(accountId) { mutableStateOf(false) }
    var editName by remember(accountId) { mutableStateOf("") }
    var editGender by remember(accountId) { mutableStateOf("") }
    var editMobile by remember(accountId) { mutableStateOf("") }
    LaunchedEffect(profile) {
        profile?.let {
            editName = it.name; editGender = it.gender; editMobile = it.contactMobile
            editingProfile = false
        }
    }
    if (helpPage != null) {
        androidx.activity.compose.BackHandler { helpPage = null }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp)) {
            TextButton(onClick = { helpPage = null }) { Text("← Back to You") }
            Text(helpPage!!, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            val sections = if (helpPage == "About Cue") listOf(
                "A little help, right on time" to "Cue helps you turn everyday information into useful reminders, with fewer steps. Our goal is to make remembering easier while you focus on your day.",
                "Made for your everyday life" to "Use Cue for lectures, appointments, travel, interviews and personal tasks. Add a reminder yourself, or give Cue text, an image or a document to interpret.",
                "You stay in control" to "Automatic capture needs the permissions and features you choose to enable. Check reminder details when information is unclear. You can edit or delete reminders and clear your learned routine.",
                "An honest promise" to "Cue is still improving. Imported information can be incomplete, and Android permissions or battery settings can affect delivery. Check essential deadlines; Cue cannot guarantee that every event will be detected."
            ) else listOf(
                "1 · Create your first reminder" to "Tap +. Write what you need to remember, choose the date and time, then select a ringtone and save. Selecting a tone previews it. Manual reminders do not send a separate creation notification.",
                "2 · Use Cue over other apps" to "In You → Settings, enable Floating Cue and allow Display over other apps. Tap the bubble for shortcuts. Drag it to move it; release over the bottom close target to switch it off. Enable it again in Settings.",
                "3 · Copy, paste or share" to "Copy text or a supported file, hold the bubble, then tap Paste. You can also drag an item onto Cue when the source app supports it. If a file cannot be read, use that app’s Share → Cue option. Copying an image does not always include the image file.",
                "4 · Let Cue interpret information" to "Use the bubble’s voice or screen shortcut, or share a message or document. Include a clear date and time where possible. Cue saves clear reminders and shows a notification; unclear information may need your review. Screen capture may require Android’s permission dialog.",
                "5 · Bring in your schedule" to "You → Import reminders lets you select Google Calendar events or paste a Gmail booking or interview message. This does not automatically read your whole inbox. For a lecture timetable, use the timetable import and enter your faculty abbreviation so only your slots are selected.",
                "6 · Find and manage reminders" to "Use the calendar and Upcoming to check dates. Open a reminder to review its details, edit it or delete it. When an alarm appears, choose Snooze to postpone it or Dismiss to stop it.",
                "7 · Make Cue yours" to "You → Settings contains themes, bubble size and opacity, ringtone and repeat settings, and optional capture features. Your profile and account controls are in You. Learn active hours is optional; Clear learned routine removes the local pattern.",
                "8 · If something does not work" to "Check notifications, alarm access and the permissions for the feature you enabled. Cloud interpretation needs internet. If a dropped file is inaccessible, share it to Cue instead. Review essential reminders after importing them."
            )
            sections.forEach { (title, body) ->
                Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp)) {
        if (settingsOpen) androidx.compose.material3.TextButton(onClick = { onSettingsOpen(false) }) {
            Text("←  Back to You")
        }
        Text(if (settingsOpen) "Settings" else "Your space",
            style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(if (settingsOpen) "Customize how Cue works for you."
            else "Your account and daily schedule.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(22.dp))
        if (!settingsOpen) {
        Card(shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text(if (signedIn) "Your reminders are synced" else "Your reminders stay on this device",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                if (signedIn) {
                    Text(profile?.name?.takeIf { it.isNotBlank() } ?: "Add your name",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Email · ${profile?.email?.takeIf { it.isNotBlank() } ?: accountEmail ?: "Loading…"}",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("Gender · ${profile?.gender?.takeIf { it.isNotBlank() } ?: "Optional"}",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("Contact mobile · ${profile?.contactMobile?.takeIf { it.isNotBlank() } ?: "Optional"}",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("Contact number is not verified for sign-in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.material3.TextButton(onClick = { editingProfile = !editingProfile }) {
                        Text(if (editingProfile) "Cancel editing" else "Edit profile")
                    }
                    if (editingProfile) {
                        androidx.compose.material3.OutlinedTextField(value = editName,
                            onValueChange = { if (it.length <= 80) editName = it },
                            label = { Text("Name") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedTextField(value = editGender,
                            onValueChange = { if (it.length <= 40) editGender = it },
                            label = { Text("Gender (optional)") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedTextField(value = editMobile,
                            onValueChange = { if (it.length <= 20) editMobile = it },
                            label = { Text("Contact mobile (optional)") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { onSaveProfile(editName, editGender, editMobile) },
                            enabled = !profileBusy && editName.isNotBlank()) { Text("Save profile") }
                    }
                    profileError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.height(8.dp))
                }
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
                Spacer(Modifier.height(14.dp))
                androidx.compose.material3.OutlinedButton(onClick = onExportData,
                    enabled = !accountBusy) { Text("Export my Cue data") }
                androidx.compose.material3.TextButton(
                    onClick = { deleteDataPrompt = true }, enabled = !accountBusy) {
                    Text(if (signedIn) "Delete my Cue data everywhere" else "Delete reminders on this device")
                }
                if (signedIn) {
                    androidx.compose.material3.TextButton(onClick = { deleteAccountPrompt = true },
                        enabled = !accountBusy) { Text("Delete Cue account") }
                    Text("Removes your Cue login profile and synced data. Your Google account is unaffected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Button(onClick = { onSettingsOpen(true) }) { Text("Open settings") }
        Spacer(Modifier.height(24.dp))
        Text("Import reminders", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Pick Google Calendar events, or paste a booking or interview message from Gmail.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onImportGoogleCalendar, enabled = !calendarBusy) {
            Text(if (calendarBusy) "Reading calendar…" else "Import Google Calendar")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onImportMail) { Text("Paste a Gmail message") }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { helpPage = "About Cue" }) { Text("About Cue") }
            OutlinedButton(onClick = { helpPage = "User manual" }) { Text("User manual") }
        }
        Spacer(Modifier.height(24.dp))
        Text("Your routine", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Card(shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Learn active hours", fontWeight = FontWeight.SemiBold)
                        Text("Optional Android Usage Access. Cue keeps only small hourly and app-count totals on this device, not a screen recording or raw timeline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = usageLearning, onCheckedChange = onUsageLearning)
                }
                if (usageLearning) {
                    Text(usageSummary, style = MaterialTheme.typography.bodySmall)
                    Text("After enough samples, undated reminders typed in the bubble can use an active hour. Explicit deadlines are never moved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onClearUsage) { Text("Clear learned routine") }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        }
        if (settingsOpen) {
        Spacer(Modifier.height(32.dp))
        Text("Bubble and shortcuts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Bring capture shortcuts to other apps or your notification panel.",
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
                    var draftSize by remember { mutableFloatStateOf(floatingSize.toFloat()) }
                    LaunchedEffect(floatingSize) { draftSize = floatingSize.toFloat() }
                    Text("Bubble size · ${draftSize.toInt()} dp",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp))
                    Slider(value = draftSize, onValueChange = { draftSize = it },
                        onValueChangeFinished = {
                            onSize(((draftSize.toInt() + 2) / 4 * 4).coerceIn(48, 88))
                        }, valueRange = 48f..88f, steps = 9)
                }
                androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Notification shortcuts", fontWeight = FontWeight.SemiBold)
                        Text("Voice and Quick actions, plus the capture menu.",
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
        Text("Alerts and sounds", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        ReminderTonePicker(reminderTone, onReminderTone)
        Spacer(Modifier.height(16.dp))
        Text("Play tone $toneRepeats times", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { count ->
                FilterChip(selected = toneRepeats == count, onClick = { onToneRepeats(count) },
                    label = { Text("$count") })
            }
        }
        Spacer(Modifier.height(16.dp))
        if (!fullScreenAllowed) Card(shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text("Alarm popup access", fontWeight = FontWeight.SemiBold)
                Text("Allow full-screen alerts for the alarm page. Snooze and Dismiss remain in the notification if this is off.",
                    style = MaterialTheme.typography.bodySmall)
                androidx.compose.material3.TextButton(onClick = onFullScreenAccess) {
                    Text("Open Android settings")
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        } // Bubble, alerts and sound settings
        if (!settingsOpen) {
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
        }
        if (settingsOpen) {
        Spacer(Modifier.height(32.dp))
        Text("Screen and notification analysis", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Choose what to share in Android's picker. Android shows a screen-sharing indicator " +
            "while analysis is active; Cue cannot hide it. Consent is required each session.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Card(shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Improve reminder detection", fontWeight = FontWeight.SemiBold)
                        Text("Optional cloud analysis for clear possible reminders. Sends one candidate line (up to 300 characters) and your timezone to Cue's Supabase function and Google Gemini. A candidate can come from a shared document or screen, but no full file, screenshot or Inbox is sent. Off by default.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = signedIn && cloudAnalysisEnabled, enabled = signedIn,
                        onCheckedChange = { if (it) cloudConsentPrompt = true else onCloudAnalysis(false) })
                }
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (screenRunning) "Analysis is active" else "Analyze shared screen",
                        modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Switch(checked = screenRunning, onCheckedChange = { onScreenToggle() },
                        enabled = Build.VERSION.SDK_INT >= 34 || screenRunning)
                }
                Text("Choose Entire screen or one app in Android's picker. While enabled, Cue samples " +
                    "text every 15 seconds and saves only clear future reminders. No video or full screen text is saved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 7.dp))
                if (Build.VERSION.SDK_INT < 34) Text("Needs Android 14+.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        if (cloudConsentPrompt) androidx.compose.material3.AlertDialog(
            onDismissRequest = { cloudConsentPrompt = false },
            title = { Text("Enable cloud reminder analysis?") },
            text = { Text("Cue may send one likely reminder line (maximum 300 characters) and your timezone through Cue Supabase to Google Gemini. This is optional; turn it off here anytime. Other content stays on your device.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                cloudConsentPrompt = false; onCloudAnalysis(true)
            }) { Text("Enable") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = {
                cloudConsentPrompt = false
            }) { Text("Cancel") } })
        Spacer(Modifier.height(32.dp))
        Text("Notification reminders", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Card(shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Find deadlines in notifications", fontWeight = FontWeight.SemiBold)
                    Text("Optional Android notification access. Clear future reminders are saved " +
                        "and acknowledged; uncertain messages are ignored. Nothing is scanned while off.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = notificationIntelligence,
                    onCheckedChange = onNotificationIntelligence)
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
        } // Settings
    }
    if (deleteDataPrompt) androidx.compose.material3.AlertDialog(
        onDismissRequest = { deleteDataPrompt = false },
        title = { Text(if (signedIn) "Delete all synced Cue data?" else "Delete guest data?") },
        text = { Text(if (signedIn)
            "This permanently removes your synced reminders, sources and history from Cue and this device. Export first if you need a copy. Your Google account remains active."
            else "This deletes guest reminders and history on this device. This cannot be undone.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = {
            deleteDataPrompt = false; onDeleteData()
        }) { Text("Delete all") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = {
            deleteDataPrompt = false
        }) { Text("Cancel") } })
    if (deleteAccountPrompt) androidx.compose.material3.AlertDialog(
        onDismissRequest = { deleteAccountPrompt = false },
        title = { Text("Delete your Cue account?") },
        text = { Text("This permanently removes your Cue profile, reminders, sources and history. Export your data first if you need a copy. Your Google account will remain. This cannot be undone.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = {
            deleteAccountPrompt = false; onDeleteAccount()
        }) { Text("Delete Cue account", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = {
            deleteAccountPrompt = false
        }) { Text("Cancel") } })
}
