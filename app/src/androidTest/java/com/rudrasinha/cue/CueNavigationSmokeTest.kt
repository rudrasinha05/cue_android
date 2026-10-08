package com.rudrasinha.cue

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Non-destructive UI smoke: never changes signed-in reminders or preferences. */
@RunWith(AndroidJUnit4::class)
class CueNavigationSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun coreTabsAndHelpRemainReachable() {
        // On slower phones the activity may launch before a Compose root is attached.
        // Treat only that startup race as "not ready" and keep polling; do not
        // suppress unexpected test or application errors.
        try {
            compose.waitUntil(timeoutMillis = 20_000) {
                try {
                    compose.onAllNodes(hasText("You")).fetchSemanticsNodes().isNotEmpty()
                } catch (error: IllegalStateException) {
                    if (error.message?.contains("No compose hierarchies found") == true) false
                    else throw error
                }
            }
        } catch (timeout: ComposeTimeoutException) {
            val diagnostic = runCatching {
                val activity = compose.activity
                "lifecycle=${activity.lifecycle.currentState}, " +
                    "windowAttached=${activity.window.decorView.isAttachedToWindow}, " +
                    "windowFocus=${activity.window.decorView.hasWindowFocus()}"
            }.getOrElse { "Activity unavailable: ${it.javaClass.simpleName} ${it.message}" }
            throw AssertionError("Cue navigation UI is not visible on this device: $diagnostic. " +
                "Check screen lock, top activity and AndroidRuntime logcat.", timeout)
        }
        compose.onNodeWithText("You").performClick()
        compose.onNodeWithText("Your space").assertExists()
        compose.onNodeWithText("Inbox").performClick()
        compose.onNodeWithText("You").performClick()
        compose.onNodeWithText("About Cue").assertExists()
        compose.onNodeWithText("User manual").assertExists()
    }
}
