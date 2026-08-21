@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test

/**
 * Covers the tooltips on the picker rows' icon buttons.
 *
 * Every button in those rows is an icon with no label, so the tooltip is the only thing that tells
 * an operator what it does — and it is a separate composable that runs only once the pointer has
 * rested on the button. Nothing else in the suite composes it.
 *
 * These tests hover and then wait for the tooltip's own text to appear, so each one ends on a
 * positive signal rather than on a timeout; the timeout is generous and only exists to fail the
 * test. The cost is `TooltipArea`'s hover delay, which keeps them the slowest tests in this group —
 * hence one per distinct tooltip rather than one per button that shows it.
 */
class BackgroundSettingsTabTooltipTest {

    private fun settingsWith(change: BackgroundSettings.() -> BackgroundSettings): AppSettings =
        AppSettings().let { it.copy(backgroundSettings = it.backgroundSettings.change()) }

    /** Hovers the button described by [description] and waits for its tooltip to be composed. */
    private fun ComposeUiTest.hoverAndAwaitTooltip(description: String) {
        onNodeWithContentDescription(description).performScrollTo()
        waitForIdle()
        onNodeWithContentDescription(description).performMouseInput { moveTo(center) }
        // The button's content description is the tooltip text, so the tooltip's own Text node makes
        // a second node carrying it — that second node appearing is the signal being waited for.
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText(description).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    @Test
    fun `resting on the library button explains what it does`() {
        backgroundTab(initial = settingsWith { copy(defaultBackgroundType = Constants.BACKGROUND_IMAGE) }) { _ ->
            val tooltip = "Browse downloaded library"
            onAllNodesWithText(tooltip).assertCountEquals(0)
            hoverAndAwaitTooltip(tooltip)
            onAllNodesWithText(tooltip).assertCountEquals(1)
        }
    }

    @Test
    fun `resting on the stock browse button explains what it does`() {
        backgroundTab(initial = settingsWith { copy(defaultBackgroundType = Constants.BACKGROUND_VIDEO) }) { _ ->
            val tooltip = "Browse stock photos/videos"
            onAllNodesWithText(tooltip).assertCountEquals(0)
            hoverAndAwaitTooltip(tooltip)
            onAllNodesWithText(tooltip).assertCountEquals(1)
        }
    }

    /**
     * The two ATEM upload buttons look identical apart from a one-character badge, so their tooltips
     * are the only thing that says which slot each one targets.
     */
    @Test
    fun `resting on an ATEM upload button names the slot it targets`() {
        val configured = AppSettings().let {
            it.copy(
                backgroundSettings = it.backgroundSettings.copy(
                    defaultBackgroundType = Constants.BACKGROUND_IMAGE,
                    defaultBackgroundImage = "/tmp/a.png",
                ),
                atemSettings = it.atemSettings.copy(host = "10.0.0.5"),
            )
        }
        backgroundTab(initial = configured) { _ ->
            val tooltip = "Upload to Background Slot 2"
            onAllNodesWithText(tooltip).assertCountEquals(0)
            hoverAndAwaitTooltip(tooltip)
            onAllNodesWithText(tooltip).assertCountEquals(1)
        }
    }
}
