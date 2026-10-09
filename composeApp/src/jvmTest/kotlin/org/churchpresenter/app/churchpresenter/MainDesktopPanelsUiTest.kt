@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The main screen's three panels drawn on their own, with stand-in contents: the two collapse
 * toggles and the two drag handles, and which saved layout -- windowed or maximized -- each writes.
 */
class MainDesktopPanelsUiTest {

    private val collapse = "Collapse Schedule"
    private val expand = "Expand Schedule"

    private class Seen {
        val changes = mutableListOf<(AppSettings) -> AppSettings>()
        val geometries = mutableListOf<PreviewPanelGeometry>()
        fun applied(start: AppSettings = AppSettings()) = changes.fold(start) { s, change -> change(s) }
    }

    private fun panels(
        placement: WindowPlacement? = null,
        settings: AppSettings = AppSettings(),
        block: ComposeUiTest.(Seen) -> Unit,
    ) = runComposeUiTest {
        val seen = Seen()
        setContent {
            val window = placement?.let { WindowState(placement = it) }
            CompositionLocalProvider(LocalMainWindowState provides window) {
                MaterialTheme {
                    MainDesktopPanels(
                        appSettings = settings,
                        onSettingsChange = { seen.changes += it },
                        slots = MainDesktopPanelSlots(
                            scheduleSidebar = { modifier -> Box(modifier.testTag("schedule")) },
                            mainTabArea = { modifier -> Box(modifier.testTag("tabs")) },
                            previewSidebar = { geometry ->
                                seen.geometries += geometry
                                Box(Modifier.fillMaxHeight().testTag("preview"))
                            },
                        ),
                    )
                }
            }
        }
        waitForIdle()
        block(seen)
    }

    /** Drags the handle whose collapse toggle is [index] by [dx], starting above the toggle. */
    private fun ComposeUiTest.dragHandle(index: Int, dx: Float) {
        val toggle = onAllNodesWithContentDescription(collapse)[index].fetchSemanticsNode().boundsInRoot
        val start = Offset(toggle.center.x, toggle.top - 80f)
        onRoot().performMouseInput {
            moveTo(start)
            press()
            repeat(5) { moveBy(Offset(dx / 5, 0f)) }
            release()
        }
        waitForIdle()
    }

    @Test
    fun `collapsing both panels writes the maximized layout when no window is known`() = panels { seen ->
        onAllNodesWithContentDescription(collapse)[0].performClick()
        waitForIdle()
        onAllNodesWithContentDescription(collapse)[0].performClick()
        waitForIdle()
        val settings = seen.applied()
        assertTrue(settings.maximizedLayout.schedulePanelCollapsed)
        assertTrue(settings.maximizedLayout.previewPanelCollapsed)
        assertEquals(AppSettings().windowedLayout, settings.windowedLayout, "the windowed layout is left alone")
        assertEquals(2, onAllNodesWithContentDescription(expand).fetchSemanticsNodes().size)
        assertTrue(seen.geometries.last().collapsed)
    }

    @Test
    fun `a floating window collapses and reopens the windowed layout`() =
        panels(placement = WindowPlacement.Floating) { seen ->
            onAllNodesWithContentDescription(collapse)[0].performClick()
            waitForIdle()
            assertTrue(seen.applied().windowedLayout.schedulePanelCollapsed)
            onAllNodesWithContentDescription(expand)[0].performClick()
            waitForIdle()
            assertEquals(false, seen.applied().windowedLayout.schedulePanelCollapsed)
            onNodeWithTag("schedule").assertExists()
        }

    @Test
    fun `dragging the schedule handle saves the new schedule width`() =
        panels(placement = WindowPlacement.Floating) { seen ->
            val before = AppSettings().windowedLayout.schedulePanelWidthDp
            dragHandle(index = 0, dx = 60f)
            val saved = seen.applied().windowedLayout.schedulePanelWidthDp
            assertNotEquals(before, saved, "the drag's end persists the width it reached")
        }

    @Test
    fun `dragging the preview handle left widens and saves the preview`() = panels { seen ->
        val before = AppSettings().maximizedLayout.previewPanelWidthDp
        dragHandle(index = 1, dx = -60f)
        val saved = seen.applied().maximizedLayout.previewPanelWidthDp
        assertNotEquals(before, saved)
        assertTrue(seen.geometries.last().previewPanelPx > 0f)
    }

    @Test
    fun `new settings and a new handler are taken without leaving`() = runComposeUiTest {
        var settings by mutableStateOf(AppSettings())
        var latest = 0
        var onChange by mutableStateOf<((AppSettings) -> AppSettings) -> Unit>({ latest = 1 })
        setContent {
            MaterialTheme {
                MainDesktopPanels(
                    appSettings = settings,
                    onSettingsChange = onChange,
                    slots = MainDesktopPanelSlots(
                        scheduleSidebar = { modifier -> Box(modifier.testTag("schedule")) },
                        mainTabArea = { modifier -> Box(modifier.testTag("tabs")) },
                        previewSidebar = { Box(Modifier.fillMaxHeight().testTag("preview")) },
                    ),
                )
            }
        }
        waitForIdle()
        settings = AppSettings(quickBackgroundsExpanded = true)
        waitForIdle()
        onChange = { latest = 2 }
        waitForIdle()
        onAllNodesWithContentDescription(collapse)[1].performClick()
        waitForIdle()
        assertEquals(2, latest, "the toggle after the swap reaches the new handler")
    }
}
