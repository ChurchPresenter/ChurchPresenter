@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.app.churchpresenter.composables.QUICK_BACKGROUND_HEADER_TAG
import org.churchpresenter.app.churchpresenter.composables.QUICK_BACKGROUND_RESET_TAG
import org.churchpresenter.app.churchpresenter.dialogs.clearGroupItemTag
import org.churchpresenter.core.models.companion.CompanionSurfacePlacement
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ClearGroup
import org.churchpresenter.settings.CompanionSatelliteSettings
import org.churchpresenter.settings.PreviewLayout
import org.churchpresenter.settings.QuickBackground
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The right-hand sidebar drawn on its own: every control over the preview that a headless window
 * can press, and the Companion surfaces routed to it. The Message, Props and clear-group editors
 * open real windows and are left closed.
 */
class PreviewSidebarControlsTest {

    @BeforeTest
    fun latch() {
        TestSingletons.latchSkikoHostOs()
        TestSingletons.latchToTestHome()
    }

    private class Seen {
        var clears = 0
        val picked = mutableListOf<QuickBackground?>()
        val changes = mutableListOf<(AppSettings) -> AppSettings>()
        val surfaces = mutableListOf<Pair<String, CompanionSurfacePlacement>>()
        fun applied(start: AppSettings) = changes.fold(start) { s, change -> change(s) }
    }

    private val calm = QuickBackground(id = "q1", label = "Calm")

    private fun sidebar(
        settings: AppSettings,
        manager: PresenterManager = PresenterManager(),
        devMode: Boolean = false,
        block: ComposeUiTest.(Seen) -> Unit,
    ) = runComposeUiTest {
        val seen = Seen()
        setContent {
            MaterialTheme {
                PreviewSidebar(
                    geometry = PreviewPanelGeometry(
                        collapsed = false,
                        visibleFraction = 1f,
                        previewPanelPx = 360f,
                        maxPreviewPx = 600f,
                    ),
                    state = PreviewSidebarState(
                        appSettings = settings,
                        livePreviewAppSettings = settings,
                        activeQuickBackground = calm,
                        serverUrl = "http://127.0.0.1:1",
                        qaDisplayUrl = "",
                        showControl = SidebarShowControl(devMode = devMode),
                    ),
                    actions = PreviewSidebarActions(
                        onClearDisplay = { seen.clears++ },
                        onQuickBackgroundPicked = { seen.picked += it },
                        onSettingsChange = { seen.changes += it },
                    ),
                    presenterManager = manager,
                    sttManager = null,
                    companionSurface = { connection, placement ->
                        seen.surfaces += connection.name to placement
                        Text("surface ${connection.name}")
                    },
                )
            }
        }
        waitForIdle()
        block(seen)
    }

    @Test
    fun `the display toggle and the clear button reach the output`() {
        val manager = PresenterManager()
        sidebar(AppSettings(), manager) { seen ->
            val before = manager.showPresenterWindow.value
            onNodeWithContentDescription("Toggle Presenter Displays").performClick()
            waitForIdle()
            assertEquals(!before, manager.showPresenterWindow.value)
            onNodeWithContentDescription("Clear Display").performClick()
            waitForIdle()
            assertEquals(1, seen.clears)
        }
    }

    @Test
    fun `the quick background tray folds and resets through the sidebar`() {
        val settings = AppSettings(quickBackgrounds = listOf(calm), quickBackgroundsExpanded = true)
        sidebar(settings) { seen ->
            onNodeWithTag(QUICK_BACKGROUND_HEADER_TAG).performClick()
            waitForIdle()
            assertEquals(false, seen.applied(settings).quickBackgroundsExpanded)
            onNodeWithTag(QUICK_BACKGROUND_RESET_TAG).performClick()
            waitForIdle()
            assertEquals(listOf<QuickBackground?>(null), seen.picked)
        }
    }

    @Test
    fun `the preview settings switch a label option and open the layout editor`() {
        val layout = PreviewLayout(id = "l1", name = "Main")
        val settings = AppSettings().let {
            it.copy(
                projectionSettings = it.projectionSettings.copy(
                    previewLayouts = listOf(layout),
                    activePreviewLayout = "l1",
                    previewLayoutFillsPanel = true,
                ),
            )
        }
        sidebar(settings) { seen ->
            onNodeWithContentDescription("Preview Settings").performClick()
            waitForIdle()
            onNodeWithTag("preview_show_labels").performClick()
            waitForIdle()
            assertEquals(
                !settings.projectionSettings.showOutputLabels,
                seen.applied(settings).projectionSettings.showOutputLabels,
            )
            onNodeWithTag("preview_edit_layout").performClick()
            waitForIdle()
            onNodeWithTag("preview_layout_done").performClick()
            waitForIdle()
            onNodeWithTag("preview_layout_done").assertDoesNotExist()
        }
    }

    @Test
    fun `the clear layers menu clears a saved group`() {
        val group = ClearGroup(id = "g1", name = "Everything")
        val settings = AppSettings(clearGroups = listOf(group))
        val manager = PresenterManager()
        sidebar(settings, manager, devMode = true) { _ ->
            onNodeWithTag(CLEAR_LAYERS_BUTTON_TAG).performClick()
            waitForIdle()
            onNodeWithTag(clearGroupItemTag("g1")).performClick()
            waitForIdle()
            onNodeWithTag(clearGroupItemTag("g1")).assertDoesNotExist()
            assertTrue(manager.program.value.isEmpty())
        }
    }

    @Test
    fun `two right-sidebar surfaces get a chip each and the chosen one is drawn`() {
        val settings = AppSettings(
            companionSatelliteConnections = listOf(
                CompanionSatelliteSettings(id = "a", name = "Front", host = "10.0.0.1", showInRightSidebar = true),
                CompanionSatelliteSettings(id = "b", name = "Back", host = "10.0.0.2", showInRightSidebar = true),
                CompanionSatelliteSettings(id = "c", name = "Hidden", host = "", showInRightSidebar = true),
            ),
        )
        sidebar(settings) { seen ->
            assertEquals("Front" to CompanionSurfacePlacement.RIGHT_SIDEBAR, seen.surfaces.last())
            onNodeWithText("Back").performClick()
            waitForIdle()
            onNodeWithText("surface Back").assertExists()
            assertTrue(seen.surfaces.none { it.first == "Hidden" }, "a connection with no host is never drawn")
        }
    }

    @Test
    fun `new settings, handlers and output reach every control without leaving`() = runComposeUiTest {
        var settings by mutableStateOf(
            AppSettings(clearGroups = listOf(ClearGroup(id = "g", name = "All")), quickBackgrounds = listOf(calm)),
        )
        var manager by mutableStateOf(PresenterManager())
        var latest = 0
        var onChange by mutableStateOf<((AppSettings) -> AppSettings) -> Unit>({ latest = 1 })
        var geometry by mutableStateOf(PreviewPanelGeometry(false, 1f, 360f, 600f))
        setContent {
            MaterialTheme {
                PreviewSidebar(
                    geometry = geometry,
                    state = PreviewSidebarState(
                        appSettings = settings,
                        livePreviewAppSettings = settings,
                        activeQuickBackground = null,
                        serverUrl = "",
                        qaDisplayUrl = "",
                        showControl = SidebarShowControl(devMode = true),
                    ),
                    actions = PreviewSidebarActions(
                        onClearDisplay = {},
                        onQuickBackgroundPicked = {},
                        onSettingsChange = onChange,
                    ),
                    presenterManager = manager,
                    sttManager = null,
                    companionSurface = { _, _ -> },
                )
            }
        }
        waitForIdle()
        settings = settings.copy(quickBackgroundsExpanded = !settings.quickBackgroundsExpanded)
        waitForIdle()
        manager = PresenterManager()
        waitForIdle()
        onChange = { latest = 2 }
        waitForIdle()
        geometry = PreviewPanelGeometry(false, 1f, 420f, 600f)
        waitForIdle()
        onNodeWithTag(QUICK_BACKGROUND_HEADER_TAG).performClick()
        waitForIdle()
        assertEquals(2, latest, "a press after the swap reaches the new handler")
        geometry = PreviewPanelGeometry(true, 0f, 420f, 600f)
        waitForIdle()
        onNodeWithTag(QUICK_BACKGROUND_HEADER_TAG).assertDoesNotExist()
    }

    @Test
    fun `a preview set to fill the panel with no layout keeps its own height above a surface`() {
        val settings = AppSettings(
            companionSatelliteConnections = listOf(
                CompanionSatelliteSettings(id = "a", name = "Front", host = "10.0.0.1", showInRightSidebar = true),
            ),
        ).let { it.copy(projectionSettings = it.projectionSettings.copy(previewLayoutFillsPanel = true)) }
        sidebar(settings) { seen ->
            onNodeWithText("surface Front").assertExists()
            assertEquals(listOf("Front" to CompanionSurfacePlacement.RIGHT_SIDEBAR), seen.surfaces.distinct())
        }
    }
}
