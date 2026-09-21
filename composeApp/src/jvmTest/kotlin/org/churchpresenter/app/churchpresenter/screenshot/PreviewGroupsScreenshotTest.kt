@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.composables.LivePreviewPanel
import org.churchpresenter.app.churchpresenter.composables.PreviewGroupsPopover
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.PreviewGroup
import org.churchpresenter.settings.PreviewGroupShape
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test

/** The preview panel's groups and the gear's editor, in both themes. */
class PreviewGroupsScreenshotTest {

    private val screen0 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_SCREEN, 0)
    private val bs0 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_BROWSER_SOURCE, 0)
    private val bs1 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_BROWSER_SOURCE, 1)
    private val ndi0 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_NDI, 0)

    private fun projection(
        vararg groups: PreviewGroup,
        showLabels: Boolean = true,
        showModes: Boolean = true,
    ) = ProjectionSettings(
        browserSourceOutputs = listOf(ScreenAssignment(browserSourceName = "Lobby"), ScreenAssignment()),
        ndiOutputs = listOf(ScreenAssignment(ndiEnabled = true)),
        previewGroups = groups.toList(),
        showOutputLabels = showLabels,
        showOutputModes = showModes,
    )

    private fun grid(shape: PreviewGroupShape, vararg members: String) =
        PreviewGroup("g", shape = shape, members = members.toList())

    @Composable
    private fun Panel(proj: ProjectionSettings) {
        Box(Modifier.width(PANEL_WIDTH)) {
            LivePreviewPanel(
                presenterManager = PresenterManager(),
                appSettings = AppSettings(projectionSettings = proj),
            )
        }
    }

    @Composable
    private fun Editor(proj: ProjectionSettings) {
        PreviewGroupsPopover(expanded = true, onDismiss = {}, proj = proj, onChange = {})
    }

    // ── The panel ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `panel with no groups`() = captureComponent(SECTION, "panel_no_groups") { Panel(projection()) }

    @Test
    fun `panel with a 2x2 group`() = captureComponent(SECTION, "panel_group_2x2") {
        Panel(projection(grid(PreviewGroupShape.TWO_BY_TWO, screen0, bs0, bs1, ndi0)))
    }

    @Test
    fun `panel with a 3x1 group`() = captureComponent(SECTION, "panel_group_3x1") {
        Panel(projection(grid(PreviewGroupShape.THREE_BY_ONE, screen0, bs0, bs1)))
    }

    @Test
    fun `panel with a 1x3 group`() = captureComponent(SECTION, "panel_group_1x3") {
        Panel(projection(grid(PreviewGroupShape.ONE_BY_THREE, screen0, bs0, bs1)))
    }

    @Test
    fun `panel with two groups, one hidden`() = captureComponent(SECTION, "panel_two_groups_one_hidden") {
        Panel(
            projection(
                PreviewGroup("a", shape = PreviewGroupShape.TWO_BY_ONE, members = listOf(screen0, bs0)),
                PreviewGroup("b", shape = PreviewGroupShape.ONE_BY_ONE, members = listOf(bs1), hidden = true),
                PreviewGroup("c", shape = PreviewGroupShape.ONE_BY_ONE, members = listOf(ndi0)),
            ),
        )
    }

    @Test
    fun `panel with labels and display type off`() = captureComponent(SECTION, "panel_group_bare") {
        Panel(
            projection(
                grid(PreviewGroupShape.TWO_BY_TWO, screen0, bs0, bs1, ndi0),
                showLabels = false,
                showModes = false,
            ),
        )
    }

    // ── The editor ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `editor with no groups`() = captureComponent(SECTION, "editor_empty", rootIndex = 1) { Editor(projection()) }

    @Test
    fun `editor with two groups`() = captureComponent(SECTION, "editor_groups", rootIndex = 1) {
        Editor(
            projection(
                PreviewGroup("a", shape = PreviewGroupShape.TWO_BY_TWO, members = listOf(screen0, bs0)),
                PreviewGroup("b", shape = PreviewGroupShape.ONE_BY_ONE, members = listOf(bs1, ndi0), hidden = true),
            ),
        )
    }

    private companion object {
        const val SECTION = "previewGroups"
        val PANEL_WIDTH = 320.dp
    }
}
