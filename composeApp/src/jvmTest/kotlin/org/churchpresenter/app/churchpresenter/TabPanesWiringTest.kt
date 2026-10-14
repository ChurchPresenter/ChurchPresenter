@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.CompanionSatelliteSettings
import org.churchpresenter.sharedui.models.Tabs
import kotlin.test.Test
import kotlin.test.assertTrue

class TabPanesWiringTest : MainDesktopComposeHarness() {

    @Test
    fun `the crossword sequence opens the crossword tab`() = root(showingOnly(Tabs.BIBLE)) { _ ->
        listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionLeft, Key.DirectionRight).forEach { press(it) }
        onAllNodesWithText("Crossword")[0].performClick()
        waitForIdle()

        waitUntil {
            listOf("Check Answers", "Across").any {
                onAllNodesWithText(it, substring = true).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun `a companion connection shown in a tab gets its pane`() {
        val settings = showingOnly(Tabs.COMPANION_SURFACE).copy(
            companionSatelliteConnections = listOf(CompanionSatelliteSettings(host = "127.0.0.1", showInTab = true)),
        )
        root(settings) { _ ->
            assertTrue(onAllNodesWithText("Companion", substring = true).fetchSemanticsNodes().isNotEmpty())
        }
    }
}
