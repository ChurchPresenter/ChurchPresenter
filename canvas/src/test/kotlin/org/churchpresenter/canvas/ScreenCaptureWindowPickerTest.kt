@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenCaptureWindowPickerTest {

    private fun panel(
        windows: () -> List<WindowInfo>,
        start: SceneSource.ScreenCaptureSource = windowSource(),
        block: ComposeUiTest.(get: () -> SceneSource.ScreenCaptureSource) -> Unit,
    ) = runComposeUiTest {
        var current = start
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(start) }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ScreenCaptureProperties(state, { updated ->
                        state = updated as SceneSource.ScreenCaptureSource
                        current = updated
                    }, listWindows = windows)
                }
            }
        }
        waitForIdle()
        block { current }
    }

    private fun windowSource(title: String = "") =
        SceneSource.ScreenCaptureSource(id = "c", name = "Stage", captureMode = "window", windowTitle = title)

    private val open = listOf(WindowInfo("Lyrics", 0x1f), WindowInfo("Slides", 0))

    @Test
    fun `choosing a window stores its title and its id in hex`() = panel({ open }) { get ->
        chooseFromDropdown("Lyrics", "Lyrics")

        assertEquals("Lyrics", get().windowTitle)
        assertEquals("0x1f", get().windowId)
    }

    @Test
    fun `a window with no id is stored by title alone`() = panel({ open }) { get ->
        chooseFromDropdown("Lyrics", "Slides")

        assertEquals("Slides", get().windowTitle)
        assertEquals("", get().windowId)
    }

    @Test
    fun `the window already chosen is the one shown`() =
        panel({ open }, windowSource(title = "Slides")) { _ ->
            onNodeWithText("Slides").assertExists()
        }

    @Test
    fun `refreshing asks the desktop again`() {
        var asked = 0
        panel({ asked++; open }) { _ ->
            onNodeWithText("Refresh Window List").performClick()
            waitForIdle()
        }
        assertEquals(2, asked)
    }

    @Test
    fun `a desktop with no windows offers no picker`() = panel({ emptyList() }) { _ ->
        onNodeWithText("Lyrics").assertDoesNotExist()
    }

    @Test
    fun `switching to region mode stores it`() = panel({ open }) { get ->
        chooseFromDropdown("Window", "Screen Region")

        assertEquals("region", get().captureMode)
    }
}
