package com.rudrasinha.cue

import android.app.Activity
import android.app.NotificationManager
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rudrasinha.cue.data.CommitmentDao
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
import com.rudrasinha.cue.ui.HistoryScreen
import com.rudrasinha.cue.ui.themeSwatch
import com.rudrasinha.cue.reminders.ReminderScheduler
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import androidx.compose.runtime.LaunchedEffect
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus

class MainActivity : ComponentActivity() {
    private var permissionEpoch by mutableIntStateOf(0)
    private var captureDraft by mutableStateOf<CaptureDraft?>(null)

    fun queueCapture(draft: CaptureDraft) { captureDraft = draft }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedText(intent)?.let(::queueCapture)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureDraft = sharedText(intent)
        val themeStore = ThemeStore(applicationContext)
        val database = CueDatabase.get(applicationContext)
        val auth = CueAuth(applicationContext)
        val cloud = CloudCommitments(database, auth.client)
        setContent { CueApp(themeStore, database, auth, cloud, this, permissionEpoch,
            captureDraft, { captureDraft = null }) }
    }

    override fun onResume() {
        super.onResume()
        permissionEpoch++
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
    onCaptureDismiss: () -> Unit
) {
    val commitments = database.commitments()
    val theme by themeStore.mode.collectAsState(initial = ThemePreference.SYSTEM)
    val colorTheme by themeStore.colorTheme.collectAsState(initial = ColorTheme.DEFAULT)
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
    fun notificationsEnabled(): Boolean {
        val manager = activity.getSystemService(NotificationManager::class.java)
        val granted = Build.VERSION.SDK_INT < 33 ||
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val channel = manager.getNotificationChannel("cue_reminders")
        return granted && manager.areNotificationsEnabled() &&
            channel?.importance != NotificationManager.IMPORTANCE_NONE
    }
    var notificationAllowed by remember { mutableStateOf(notificationsEnabled()) }
    LaunchedEffect(permissionEpoch) { notificationAllowed = notificationsEnabled() }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationAllowed = granted && notificationsEnabled()
        if (!granted) reminderMessage = "Allow notifications in Android settings to see alerts."
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
        Scaffold(
            topBar = {
                if (selected != Tab.TODAY && selected != Tab.UPCOMING) {
                    val darkSection = selected == Tab.AI || selected == Tab.INBOX
                    TopAppBar(
                        title = { Text("cue", fontWeight = FontWeight.Bold,
                            color = if (darkSection) Color(0xFFF8F6FF) else Color.Unspecified,
                            style = MaterialTheme.typography.headlineMedium) },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor =
                            if (darkSection) Color(0xFF101017) else MaterialTheme.colorScheme.background),
                        actions = {
                            Surface(
                                modifier = Modifier.padding(end = 20.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = if (darkSection) Color(0xFF383049)
                                    else MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(if (userId == null) "On this device" else "Sync on",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    color = if (darkSection) Color(0xFFB69CFF) else Color.Unspecified,
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
                    captureDraft, onCaptureDismiss,
                    Modifier.padding(padding)
                )
                Tab.AI -> CaptureHub(onVoice = {
                    try {
                        voiceLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "What would you like to remember?")
                        })
                    } catch (_: android.content.ActivityNotFoundException) {
                        reminderMessage = "Speech recognition isn't available on this device."
                    }
                }, onDocument = {
                    documentLauncher.launch(arrayOf("text/plain", "text/csv", "text/tab-separated-values",
                        "text/markdown", "application/pdf", "image/jpeg", "image/png", "image/webp",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                }, onQuick = {
                    (activity as MainActivity).queueCapture(CaptureDraft(UUID.randomUUID().toString(), "",
                        CaptureOrigin("manual")))
                }, message = reminderMessage, modifier = Modifier.padding(padding))
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
                    padding
                )
            }
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
    theme: ThemePreference,
    colorTheme: ColorTheme,
    onTheme: (ThemePreference) -> Unit,
    onColorTheme: (ColorTheme) -> Unit,
    signedIn: Boolean,
    accountBusy: Boolean,
    accountMessage: String?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
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
