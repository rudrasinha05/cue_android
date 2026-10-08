package com.rudrasinha.cue

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
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodes(hasText("You")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("You").performClick()
        compose.onNodeWithText("Your space").assertExists()
        compose.onNodeWithText("Inbox").performClick()
        compose.onNodeWithText("You").performClick()
        compose.onNodeWithText("About Cue").assertExists()
        compose.onNodeWithText("User manual").assertExists()
    }
}
