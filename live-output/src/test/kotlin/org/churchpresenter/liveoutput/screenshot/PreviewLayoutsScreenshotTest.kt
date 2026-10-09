@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.liveoutput.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.liveoutput.preview.LivePreviewPanel
import org.churchpresenter.liveoutput.preview.PreviewGroupsPopover
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.PreviewArea
import org.churchpresenter.settings.PreviewLayout
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.SPLIT_ACROSS
import org.churchpresenter.settings.SPLIT_DOWN
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.splitArea
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import org.churchpresenter.sharedui.screenshot.captureComponent

/** The preview panel as its layout draws and edits it, and the gear's layout list, in both themes. */
class PreviewLayoutsScreenshotTest {

    private val screen0 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_SCREEN, 0)
    private val bs0 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_BROWSER_SOURCE, 0)
    private val bs1 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_BROWSER_SOURCE, 1)
    private val ndi0 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_NDI, 0)

    private fun leaf(output: String = "") = PreviewArea(output = output)

    private fun across(vararg outputs: String) = splitArea(SPLIT_ACROSS, outputs.map { leaf(it) })

    private val twoByTwo get() = splitArea(SPLIT_DOWN, listOf(across(screen0, bs0), across(bs1, ndi0)))

    private val tallBesideThree get() = PreviewArea(
        split = SPLIT_ACROSS,
        children = listOf(
            PreviewArea(output = screen0, place = Constants.TOP),
            splitArea(SPLIT_DOWN, listOf(leaf(bs0), leaf(bs1), leaf(ndi0))),
        ),
        ratios = listOf(0.6f, 0.4f),
    )

    private fun projection(
        vararg roots: PreviewArea,
        showLabels: Boolean = true,
        showModes: Boolean = true,
        fills: Boolean = false,
        listUnplaced: Boolean = false,
    ) = ProjectionSettings(
        browserSourceOutputs = listOf(ScreenAssignment(browserSourceName = "Lobby"), ScreenAssignment()),
        ndiOutputs = listOf(ScreenAssignment(ndiEnabled = true)),
        previewLayouts = roots.mapIndexed { i, root -> PreviewLayout(id = "l$i", root = root) },
        activePreviewLayout = if (roots.isEmpty()) "" else "l0",
        previewLayoutFillsPanel = fills,
        listUnplacedOutputs = listUnplaced,
        showOutputLabels = showLabels,
        showOutputModes = showModes,
    )

    @Composable
    private fun Panel(proj: ProjectionSettings, editing: Boolean = false, height: Dp? = null) {
        val size = Modifier.width(PANEL_WIDTH).then(if (height != null) Modifier.height(height) else Modifier)
        Box(size) {
            LivePreviewPanel(
                presenterManager = PresenterManager(),
                appSettings = AppSettings(projectionSettings = proj),
                editingLayout = editing,
            )
        }
    }

    @Composable
    private fun Editor(proj: ProjectionSettings) {
        PreviewGroupsPopover(expanded = true, onDismiss = {}, proj = proj, onChange = {})
    }

    // ── The panel ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `panel with no layout`() = captureComponent(SECTION, "panel_no_layout") { Panel(projection()) }

    @Test
    fun `panel with a 2x2 layout`() = captureComponent(SECTION, "panel_2x2") { Panel(projection(twoByTwo)) }

    @Test
    fun `panel with three across`() = captureComponent(SECTION, "panel_3x1") {
        Panel(projection(across(screen0, bs0, bs1)))
    }

    @Test
    fun `panel with a tall area beside three`() = captureComponent(SECTION, "panel_tall_3") {
        Panel(projection(tallBesideThree))
    }

    @Test
    fun `panel listing the outputs its layout leaves out`() = captureComponent(SECTION, "panel_list_unplaced") {
        Panel(projection(across(screen0, bs0), listUnplaced = true))
    }

    @Test
    fun `panel with labels and display type off`() = captureComponent(SECTION, "panel_bare") {
        Panel(projection(twoByTwo, showLabels = false, showModes = false))
    }

    @Test
    fun `a layout filling the panel`() = captureComponent(SECTION, "panel_fills") {
        Panel(projection(tallBesideThree, fills = true), height = FILL_HEIGHT)
    }

    @Test
    fun `the panel while its layout is edited`() = captureComponent(SECTION, "panel_editing") {
        Panel(projection(splitArea(SPLIT_DOWN, listOf(across(screen0, bs0), leaf()))), editing = true)
    }

    // ── The gear ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `gear with no layout`() = captureComponent(SECTION, "gear_no_layout", rootIndex = 1) {
        Editor(projection())
    }

    @Test
    fun `gear with two layouts`() = captureComponent(SECTION, "gear_layouts", rootIndex = 1) {
        Editor(projection(twoByTwo, tallBesideThree, fills = true))
    }

    private companion object {
        const val SECTION = "previewLayouts"
        val PANEL_WIDTH = 320.dp
        val FILL_HEIGHT = 300.dp
    }
}
