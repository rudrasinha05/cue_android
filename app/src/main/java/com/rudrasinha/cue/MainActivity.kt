package com.rudrasinha.cue

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.rudrasinha.cue.data.CommitmentEntity
import com.rudrasinha.cue.data.CueDatabase
import com.rudrasinha.cue.data.CloudCommitments
import com.rudrasinha.cue.auth.CueAuth
import com.rudrasinha.cue.settings.ThemePreference
import com.rudrasinha.cue.settings.ColorTheme
import com.rudrasinha.cue.settings.ThemeStore
import com.rudrasinha.cue.ui.CueTheme
import com.rudrasinha.cue.ui.themeSwatch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themeStore = ThemeStore(applicationContext)
        val database = CueDatabase.get(applicationContext)
        val auth = CueAuth(applicationContext)
        val cloud = CloudCommitments(database, auth.client)
        setContent { CueApp(themeStore, database.commitments(), auth, cloud, this) }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Default.Today),
    UPCOMING("Upcoming", Icons.Default.CalendarMonth),
    AI("AI", Icons.Default.AutoAwesome),
    INBOX("Inbox", Icons.Default.Inbox),
    YOU("You", Icons.Default.AccountCircle)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CueApp(
    themeStore: ThemeStore,
    commitments: CommitmentDao,
    auth: CueAuth,
    cloud: CloudCommitments,
    activity: Activity
) {
    val theme by themeStore.mode.collectAsState(initial = ThemePreference.SYSTEM)
    val colorTheme by themeStore.colorTheme.collectAsState(initial = ColorTheme.DEFAULT)
    val session by auth.client.auth.sessionStatus.collectAsState(initial = SessionStatus.Initializing)
    val userId = (session as? SessionStatus.Authenticated)?.session?.user?.id
    val ownerId = userId ?: "guest"
    val items by remember(ownerId) { commitments.observeActive(ownerId) }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var accountMessage by remember { mutableStateOf<String?>(null) }
    var accountBusy by remember { mutableStateOf(false) }
    var guestMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        if (userId != null) {
            accountBusy = true
            try {
                cloud.restoreAndClaim(userId)
                accountMessage = "Account synced."
            } catch (e: Exception) {
                accountMessage = "Sync paused: ${e.message ?: "check your connection"}"
            } finally {
                accountBusy = false
            }
        }
    }

    CueTheme(theme, colorTheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("cue", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium) },
                    actions = {
                        Surface(
                            modifier = Modifier.padding(end = 20.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(if (userId == null) "GUEST" else "SIGNED IN", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                )
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
                Tab.TODAY -> TodayScreen(items, userId == null, guestMessage, { title ->
                    scope.launch {
                        try {
                            val now = System.currentTimeMillis()
                            commitments.upsert(listOf(CommitmentEntity(
                                id = UUID.randomUUID().toString(), ownerId = "guest", title = title,
                                details = null, dueAtMillis = null, timezone = ZoneId.systemDefault().id,
                                status = "active", updatedAtMillis = now
                            )))
                            guestMessage = null
                        } catch (e: Exception) {
                            guestMessage = "Could not save locally: ${e.message ?: "try again"}"
                        }
                    }
                }, padding)
                Tab.UPCOMING -> EmptyScreen("Upcoming", "Your future commitments will appear here.", Icons.Default.CalendarMonth, padding)
                Tab.AI -> EmptyScreen("Ask Cue", "Your conversations will appear here.", Icons.Default.AutoAwesome, padding)
                Tab.INBOX -> EmptyScreen("Inbox", "Nothing needs your review right now.", Icons.Default.Inbox, padding)
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
                                userId?.let { cloud.clearAccountCache(it) }
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
private fun TodayScreen(
    commitments: List<CommitmentEntity>, isGuest: Boolean, guestMessage: String?,
    onAddGuest: (String) -> Unit, padding: PaddingValues
) {
    val date = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())) }
    var newTitle by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(22.dp))
        Text(date.uppercase(), color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Today, in focus", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("A clear view of what matters next.", color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(28.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(20.dp))
                Column {
                    Text("${commitments.size} active", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold)
                    Text("commitments in your space",
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        if (isGuest) {
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = newTitle, onValueChange = { newTitle = it },
                label = { Text("Guest commitment") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                onAddGuest(newTitle.trim())
                newTitle = ""
            }, enabled = newTitle.isNotBlank()) { Text("Save on this device") }
            Text("Sign in to sync this commitment.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            guestMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        if (commitments.isEmpty()) {
            Spacer(Modifier.height(32.dp))
            Text("A little room to breathe", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Your day is clear. Saved commitments will show up here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Spacer(Modifier.height(24.dp))
            commitments.forEach { commitment ->
                Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Text(commitment.title, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun EmptyScreen(title: String, description: String, icon: ImageVector, padding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding).padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(52.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
        Text("Your space", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(if (signedIn) "Your synced Cue account" else "Using Cue as a guest",
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
        accountMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(32.dp))
        Text("Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Choose how Cue looks on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ThemePreference.entries.forEachIndexed { index, value ->
                SegmentedButton(
                    selected = theme == value,
                    onClick = { onTheme(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, ThemePreference.entries.size),
                    label = { Text(value.label) }
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text("Color theme", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Cue Default plus five palettes. Your choice stays on this device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        LazyRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            items(ColorTheme.entries) { option ->
                Card(
                    modifier = Modifier.width(112.dp).height(94.dp).clickable { onColorTheme(option) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(
                        2.dp,
                        if (colorTheme == option) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Surface(
                            modifier = Modifier.size(30.dp),
                            shape = CircleShape,
                            color = themeSwatch(option)
                        ) {}
                        Spacer(Modifier.height(8.dp))
                        Text(option.label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
