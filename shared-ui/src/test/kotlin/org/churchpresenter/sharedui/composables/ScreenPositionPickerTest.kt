package org.churchpresenter.sharedui.composables

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The miniature screen a position is picked on: nine spots in reading order, the chosen one marked
 * and named beside the screen, and each spot naming itself on hover.
 */
@OptIn(ExperimentalTestApi::class)
class ScreenPositionPickerTest {

    private val positions = listOf(
        "tl" to "Top Left", "tc" to "Top Center", "tr" to "Top Right",
        "cl" to "Center Left", "c" to "Center", "cr" to "Center Right",
        "bl" to "Bottom Left", "bc" to "Bottom Center", "br" to "Bottom Right",
    )

    @Test
    fun `the chosen spot is marked and named, and another can be chosen`() = runComposeUiTest {
        var selected by mutableStateOf("c")
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                ScreenPositionPicker(positions, selected, onSelect = { selected = it })
            }
        }
        onNodeWithContentDescription("Center").assertIsSelected()
        onNodeWithText("Center").assertExists()

        onNodeWithContentDescription("Bottom Right").performClick()
        waitForIdle()

        assertEquals("br", selected)
        onNodeWithContentDescription("Bottom Right").assertIsSelected()
        onNodeWithContentDescription("Center").assertIsNotSelected()
        onNodeWithText("Bottom Right").assertExists()
    }

    @Test
    fun `a value it does not list is shown as it is`() = runComposeUiTest {
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.DARK) {
                ScreenPositionPicker(positions, "Somewhere", onSelect = {})
            }
        }
        onNodeWithText("Somewhere").assertExists()
    }

    @Test
    fun `a spot names itself when hovered`() = runComposeUiTest {
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                ScreenPositionPicker(positions, "c", onSelect = {})
            }
        }
        onNodeWithTag(screenPositionTag("tl")).performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(TOOLTIP_DELAY_MS)
        waitForIdle()

        onNodeWithText("Top Left").assertExists()
    }
}

/** Past `TooltipArea`'s open delay. */
private const val TOOLTIP_DELAY_MS = 600L
