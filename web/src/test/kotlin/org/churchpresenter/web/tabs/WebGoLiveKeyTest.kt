@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.web.tabs

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputLook
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.SlideLook
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Go Live key on the Web tab: Enter on the tab root puts the typed address on screen, as the Go
 * Live button does; Enter in the address bar is the address bar's.
 */
class WebGoLiveKeyTest {

    private val liveCapable: (AppSettings) -> AppSettings = {
        it.copy(
            projectionSettings = ProjectionSettings(
                screenAssignments = listOf(ScreenAssignment(targetDisplay = 1, activeProfileId = PROFILE)),
                outputProfiles = listOf(
                    OutputProfile(id = PROFILE, name = "Main", look = OutputLook(slide = SlideLook(web = true))),
                ),
            ),
        )
    }

    /** Types [address] into the address bar, then hands the keyboard back to the tab root. */
    private fun ComposeUiTest.typeAddressAndLeaveTheBar(address: String) {
        onNodeWithText(WebLabel.URL_PLACEHOLDER_DEFAULT).performTextReplacement(address)
        // A press on a part of the tab that takes no keyboard of its own gives it to the root.
        onNodeWithText(WebLabel.PREVIEW_HINT).performMouseInput { click() }
        waitForIdle()
    }

    private fun ComposeUiTest.pressEnter() {
        onRoot().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
    }

    @Test
    fun `enter on the tab root puts the typed address on screen`() = webTab(
        settings = liveCapable,
        hasSecondaryDisplay = true,
    ) { presenter, _ ->
        typeAddressAndLeaveTheBar("example.com")

        pressEnter()

        assertEquals(Presenting.WEBSITE, presenter.onAir.value)
        assertEquals("https://example.com", presenter.websiteUrl.value)
    }

    @Test
    fun `enter typed in the address bar does not go live`() = webTab(
        settings = liveCapable,
        hasSecondaryDisplay = true,
    ) { presenter, _ ->
        onNodeWithText(WebLabel.URL_PLACEHOLDER_DEFAULT).performTextReplacement("example.com")
        onNodeWithText("example.com").requestFocus()

        onNodeWithText("example.com").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertEquals(Presenting.NONE, presenter.onAir.value, "Enter there loads the page, it does not go live")
    }

    @Test
    fun `with no second screen enter does nothing`() = webTab(settings = liveCapable) { presenter, _ ->
        typeAddressAndLeaveTheBar("example.com")

        pressEnter()

        assertEquals(Presenting.NONE, presenter.onAir.value)
        assertEquals("", presenter.websiteUrl.value)
    }

    private companion object {
        const val PROFILE = "main"
    }
}
