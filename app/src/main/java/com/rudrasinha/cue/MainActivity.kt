package com.rudrasinha.cue

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rudrasinha.cue.data.CommitmentDao
import com.rudrasinha.cue.data.CueDatabase
import com.rudrasinha.cue.settings.ThemePreference
import com.rudrasinha.cue.settings.ThemeStore
import com.rudrasinha.cue.ui.CueTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themeStore = ThemeStore(applicationContext)
        val commitments = CueDatabase.get(applicationContext).commitments()
        setContent { CueApp(themeStore, commitments) }
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
private fun CueApp(themeStore: ThemeStore, commitments: CommitmentDao) {
    val theme by themeStore.mode.collectAsState(initial = ThemePreference.SYSTEM)
    val items by commitments.observeActive().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable { mutableStateOf(Tab.TODAY) }

    CueTheme(theme) {
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
                            Text("GUEST", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
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
                Tab.TODAY -> TodayScreen(items.size, padding)
                Tab.UPCOMING -> EmptyScreen("Upcoming", "Your future commitments will appear here.", Icons.Default.CalendarMonth, padding)
                Tab.AI -> EmptyScreen("Ask Cue", "Your conversations will appear here.", Icons.Default.AutoAwesome, padding)
                Tab.INBOX -> EmptyScreen("Inbox", "Nothing needs your review right now.", Icons.Default.Inbox, padding)
                Tab.YOU -> YouScreen(theme, { scope.launch { themeStore.set(it) } }, padding)
            }
        }
    }
}

@Composable
private fun TodayScreen(activeCount: Int, padding: PaddingValues) {
    val date = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())) }
    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
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
                    Text("$activeCount active", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold)
                    Text("commitments in your local space",
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        if (activeCount == 0) {
            Spacer(Modifier.height(32.dp))
            Text("A little room to breathe", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Your day is clear. When you add a commitment, it will show up here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun YouScreen(theme: ThemePreference, onTheme: (ThemePreference) -> Unit, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
        Text("Your space", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Using Cue as a guest", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    }
}
