package org.churchpresenter.sharedui.utils

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import org.churchpresenter.theme.LocalThemeCustomization
import org.churchpresenter.theme.ThemeCustomization
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An operator window opens at its declared size times the interface size, and centred for that size.
 *
 * The tests run headless, where there is no screen to keep the window on, so the size is the declared
 * one scaled and nothing else; [dialogSizeWithin] and its own tests cover keeping it on a real screen.
 */
@OptIn(ExperimentalTestApi::class)
class UiScaledWindowsTest {

    private fun sizeAt(scale: Float, width: Int, height: Int): DpSize {
        var size = DpSize.Zero
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalThemeCustomization provides ThemeCustomization(uiScale = scale)) {
                    size = uiScaledWindowSize(width.dp, height.dp)
                }
            }
            waitForIdle()
        }
        return size
    }

    @Test
    fun `at the default size a window keeps its declared size`() {
        assertEquals(DpSize(600.dp, 450.dp), sizeAt(1f, 600, 450))
    }

    @Test
    fun `a larger interface opens the window larger by the same factor`() {
        assertEquals(DpSize(900.dp, 675.dp), sizeAt(1.5f, 600, 450))
        assertEquals(DpSize(480.dp, 360.dp), sizeAt(0.8f, 600, 450))
    }

    @Test
    fun `a dialog is centred on the main window at its scaled size`() {
        val main = WindowState(position = WindowPosition(100.dp, 100.dp), size = DpSize(2000.dp, 1200.dp))
        var state: DialogState? = null
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalThemeCustomization provides ThemeCustomization(uiScale = 2f)) {
                    state = rememberCenteredDialogState(main, 400.dp, 300.dp)
                }
            }
            waitForIdle()
        }
        assertEquals(DpSize(800.dp, 600.dp), state!!.size)
        assertEquals(WindowPosition(100.dp + 600.dp, 100.dp + 300.dp), state!!.position)
    }
}
