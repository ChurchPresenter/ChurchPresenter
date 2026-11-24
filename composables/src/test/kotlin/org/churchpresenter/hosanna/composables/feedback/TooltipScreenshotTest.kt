package org.churchpresenter.hosanna.composables.feedback

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performMouseInput
import org.churchpresenter.hosanna.composables.button.IconButton
import org.churchpresenter.hosanna.composables.testing.hosannaPopup
import org.churchpresenter.hosanna.composables.testing.settle
import org.churchpresenter.hosanna.composables.testing.testIcon
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TooltipScreenshotTest {

    private val section = "feedback/tooltip"

    private fun ComposeUiTest.hover() {
        onNodeWithContentDescription("Control").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(500)
        settle()
    }

    @Composable
    private fun Tip(text: String, shortcut: String? = null, enabled: Boolean = true) {
        Tooltip(text, shortcut = shortcut) { IconButton(testIcon(), "Control", {}, enabled = enabled) }
    }

    @Test
    fun label() = hosannaPopup(section, "label", keepHover = true, drive = { hover() }) { Tip("Open") }

    @Test
    fun shortcut() = hosannaPopup(section, "shortcut", keepHover = true, drive = { hover() }) {
        Tip("Settings", "⌘,")
    }

    @Test
    fun disabled() = hosannaPopup(section, "disabled", keepHover = true, drive = { hover() }) {
        Tip("Connect a Stream Deck first", enabled = false)
    }

    @Test
    fun wrapping() = hosannaPopup(section, "wrapping", keepHover = true, drive = { hover() }) {
        Tip("Connect a Stream Deck first. It shows up here as soon as Companion sees it.")
    }
}
