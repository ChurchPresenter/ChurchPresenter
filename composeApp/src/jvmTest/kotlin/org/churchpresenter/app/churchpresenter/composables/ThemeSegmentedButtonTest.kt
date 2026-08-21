package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The three-way Light/Dark/System theme picker — a thin [SegmentedButton] wrapper whose own
 * logic is entirely the mapping from each segment to a [ThemeMode]. [SegmentedButton]'s own
 * mechanics (click wiring, hover tooltip) are already proven in `SegmentedButtonTest`; these
 * tests exist to pin the mapping this file adds on top of that.
 */
@OptIn(ExperimentalTestApi::class)
class ThemeSegmentedButtonTest {

    @Test
    fun `all three theme options are shown`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ThemeSegmentedButton(selectedTheme = ThemeMode.SYSTEM, onThemeChange = { })
            }
        }
        onNodeWithText("☀").assertExists("the light-theme option must be shown")
        onNodeWithText("🌙").assertExists("the dark-theme option must be shown")
        onNodeWithText("⚙").assertExists("the system-theme option must be shown")
    }

    @Test
    fun `selecting the sun option reports ThemeMode LIGHT`() = runComposeUiTest {
        var chosen: ThemeMode? = null
        setContent {
            MaterialTheme {
                ThemeSegmentedButton(selectedTheme = ThemeMode.SYSTEM, onThemeChange = { chosen = it })
            }
        }
        onNodeWithText("☀").performClick()
        assertEquals(ThemeMode.LIGHT, chosen)
    }

    @Test
    fun `selecting the moon option reports ThemeMode DARK`() = runComposeUiTest {
        var chosen: ThemeMode? = null
        setContent {
            MaterialTheme {
                ThemeSegmentedButton(selectedTheme = ThemeMode.SYSTEM, onThemeChange = { chosen = it })
            }
        }
        onNodeWithText("🌙").performClick()
        assertEquals(ThemeMode.DARK, chosen)
    }

    @Test
    fun `selecting the gear option reports ThemeMode SYSTEM`() = runComposeUiTest {
        var chosen: ThemeMode? = null
        setContent {
            MaterialTheme {
                ThemeSegmentedButton(selectedTheme = ThemeMode.LIGHT, onThemeChange = { chosen = it })
            }
        }
        onNodeWithText("⚙").performClick()
        assertEquals(ThemeMode.SYSTEM, chosen)
    }

    @Test
    fun `hovering an option shows its tooltip text`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ThemeSegmentedButton(selectedTheme = ThemeMode.SYSTEM, onThemeChange = { })
            }
        }
        onNodeWithText("Light Theme", useUnmergedTree = true).assertDoesNotExist()

        onNodeWithText("☀").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(600)
        waitForIdle()

        onNodeWithText("Light Theme", useUnmergedTree = true).assertExists("the tooltip must name the option hovered")
    }

    @Test
    fun `the modifier passed by the caller reaches the button row`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ThemeSegmentedButton(
                    selectedTheme = ThemeMode.SYSTEM,
                    onThemeChange = { },
                    modifier = Modifier.testTag("theme-picker"),
                )
            }
        }
        onNodeWithTag("theme-picker").assertExists("the caller's modifier must be applied to the segmented row")
    }
}
