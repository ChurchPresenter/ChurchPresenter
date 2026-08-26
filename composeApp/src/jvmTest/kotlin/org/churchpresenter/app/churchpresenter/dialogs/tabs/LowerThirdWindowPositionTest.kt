@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.StreamingSettings
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Drives the lower third's window padding — the four insets around the mock TV screen.
 *
 * Moved out of `LowerThirdSettingsTabTest` with the section itself, which is now per output and
 * reached from Projection → Customize rather than from the Lower Third tab.
 *
 * `NumberSettingsTextField` keeps its own text and only calls back when the value is in range, so a
 * field reading back a new number proves nothing on its own — every test here closes the loop by
 * re-rendering from the settings that came out, where the field can only show what was stored.
 */
class LowerThirdWindowPositionTest {

    private fun settingsWith(change: StreamingSettings.() -> StreamingSettings): AppSettings =
        AppSettings().let { it.copy(streamingSettings = it.streamingSettings.change()) }

    private fun windowPosition(
        initial: AppSettings = AppSettings(),
        block: ComposeUiTest.(get: () -> AppSettings) -> Unit,
    ) = runComposeUiTest {
        var current = initial
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(current) }
                LowerThirdWindowPositionSection(
                    settings = state,
                    onSettingsChange = { transform -> state = transform(state); current = state },
                )
            }
        }
        block { current }
    }

    private fun ComposeUiTest.retype(showing: Int, to: Int) {
        onNode(hasSetTextAction() and hasText(showing.toString())).performTextReplacement(to.toString())
        waitForIdle()
    }

    @Test
    fun `the diagram labels the lower third band and every margin field`() = windowPosition { _ ->
        onNodeWithText("Window Position").assertExists("the section must be captioned")
        onNodeWithText("Lower Third").assertExists("the band in the screen diagram must be labelled")
        for (edge in listOf("LEFT", "TOP", "RIGHT", "BOTTOM")) {
            onNodeWithText(edge).assertExists("the $edge margin field must be captioned")
        }
    }

    @Test
    fun `each margin field writes its own value back`() {
        // The four share a default, so give each one a value only it holds.
        val distinct = settingsWith { copy(windowLeft = 41, windowTop = 42, windowRight = 43, windowBottom = 44) }
        var saved = AppSettings()
        windowPosition(initial = distinct) { get ->
            retype(showing = 41, to = 11)
            assertEquals(11, get().streamingSettings.windowLeft, "the left margin must be stored")

            retype(showing = 42, to = 22)
            assertEquals(22, get().streamingSettings.windowTop, "the top margin must be stored")

            retype(showing = 43, to = 33)
            assertEquals(33, get().streamingSettings.windowRight, "the right margin must be stored")

            retype(showing = 44, to = 55)
            assertEquals(55, get().streamingSettings.windowBottom, "the bottom margin must be stored")

            assertEquals(11, get().streamingSettings.windowLeft, "and none of them disturbed a neighbour")
            assertEquals(33, get().streamingSettings.windowRight)
            saved = get()
        }
        windowPosition(initial = saved) { _ ->
            for (value in listOf(11, 22, 33, 55)) {
                onNode(hasSetTextAction() and hasText(value.toString()))
                    .assertExists("a fresh render must show the stored margin $value")
            }
        }
    }

    @Test
    fun `a margin outside the allowed range is not stored`() {
        var saved = AppSettings()
        windowPosition(initial = settingsWith { copy(windowLeft = 41) }) { get ->
            retype(showing = 41, to = 99999)
            assertEquals(41, get().streamingSettings.windowLeft, "99999 is outside 0..10000")
            // The field itself echoes the rejected entry — that is the widget's own state, not
            // anything that was stored.
            onNode(hasSetTextAction() and hasText("99999")).assertExists()
            saved = get()
        }
        windowPosition(initial = saved) { _ ->
            onAllNodes(hasSetTextAction() and hasText("99999")).assertCountEquals(0)
            onNode(hasSetTextAction() and hasText("41"))
                .assertExists("a fresh render shows the value that survived, not the rejected one")
        }
    }

    @Test
    fun `the band diagram renders whatever height is configured`() {
        for (percent in listOf(10, 33, 60)) {
            val settings = AppSettings().let {
                it.copy(projectionSettings = it.projectionSettings.copy(lowerThirdHeightPercent = percent))
            }
            windowPosition(initial = settings) { _ ->
                onNodeWithText("Lower Third")
                    .assertExists("the band must be drawn and labelled at $percent%")
            }
        }
    }
}
