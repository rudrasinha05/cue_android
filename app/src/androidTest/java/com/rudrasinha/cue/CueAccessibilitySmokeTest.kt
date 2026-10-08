package com.rudrasinha.cue

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Basic semantic-label availability in the real app on default/large font emulator runs.
 * Does not claim to replace human TalkBack or visual layout testing.
 */
@RunWith(AndroidJUnit4::class)
class CueAccessibilitySmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun coreNavigationHasVisibleTextAndClickSemantics() {
        compose.waitUntil(20_000) {
            try { compose.onAllNodes(hasText("You")).fetchSemanticsNodes().isNotEmpty() }
            catch (e: IllegalStateException) {
                if (e.message?.contains("No compose hierarchies found") == true) false else throw e
            }
        }
        listOf("Reminders", "Upcoming", "Inbox", "You").forEach { title ->
            assertTrue("Missing semantic label: $title",
                compose.onAllNodes(hasText(title)).fetchSemanticsNodes().isNotEmpty())
        }
        val controls = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue("No discoverable clickable controls", controls.isNotEmpty())
        val named = controls.count { node ->
            val text = node.config.getOrNull(SemanticsProperties.Text)
            val descriptions = node.config.getOrNull(SemanticsProperties.ContentDescription)
            !text.isNullOrEmpty() || !descriptions.isNullOrEmpty()
        }
        assertTrue("Too few named clickable UI controls for screen-reader navigation", named >= 4)
    }
}
