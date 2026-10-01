package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.runtime.Composable
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.preview_layout_template_one
import org.churchpresenter.strings.generated.resources.preview_layout_template_tall_first
import org.churchpresenter.strings.generated.resources.preview_layout_template_tall_last
import org.churchpresenter.settings.PreviewArea
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.SPLIT_ACROSS
import org.churchpresenter.settings.SPLIT_DOWN
import org.churchpresenter.settings.splitArea
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

/** A starting point for a new layout: what the template is called, and the areas it builds. */
internal class PreviewLayoutTemplate(val label: String, val build: (List<String>) -> PreviewArea)

/** The two counts the templates are built from: two by two, three across, a tall area beside two or three. */
private const val SMALL = 2
private const val LARGE = 3

/** Share of a "tall" template's width the tall area takes. */
private const val TALL_SHARE = 0.36f

/**
 * The templates New layout offers -- the grid shapes preview groups had, and a tall area beside a
 * stack -- each filling its areas with the panel's outputs in order, the rest left empty.
 */
@Composable
internal fun previewLayoutTemplates(): List<PreviewLayoutTemplate> {
    fun grid(columns: Int, rows: Int) = PreviewLayoutTemplate("$columns×$rows") { outputs ->
        val cells = outputs.iterator()
        val rowAreas = List(rows) {
            splitArea(SPLIT_ACROSS, List(columns) { PreviewArea(output = if (cells.hasNext()) cells.next() else "") })
        }
        if (rows == 1) rowAreas.first() else splitArea(SPLIT_DOWN, rowAreas)
    }
    fun tall(stacked: Int, tallFirst: Boolean, label: String) = PreviewLayoutTemplate(label) { outputs ->
        val tallArea = PreviewArea(output = outputs.getOrElse(0) { "" }, place = Constants.TOP)
        val stack = splitArea(SPLIT_DOWN, List(stacked) { PreviewArea(output = outputs.getOrElse(it + 1) { "" }) })
        val children = if (tallFirst) listOf(tallArea, stack) else listOf(stack, tallArea)
        val shares = if (tallFirst) listOf(TALL_SHARE, 1f - TALL_SHARE) else listOf(1f - TALL_SHARE, TALL_SHARE)
        PreviewArea(split = SPLIT_ACROSS, children = children, ratios = shares)
    }
    return listOf(
        PreviewLayoutTemplate(stringResource(Res.string.preview_layout_template_one)) { outputs ->
            PreviewArea(output = outputs.firstOrNull().orEmpty())
        },
        grid(columns = SMALL, rows = SMALL),
        grid(columns = LARGE, rows = 1),
        grid(columns = 1, rows = LARGE),
        tall(SMALL, tallFirst = true, stringResource(Res.string.preview_layout_template_tall_first, SMALL)),
        tall(LARGE, tallFirst = true, stringResource(Res.string.preview_layout_template_tall_first, LARGE)),
        tall(LARGE, tallFirst = false, stringResource(Res.string.preview_layout_template_tall_last, LARGE)),
    )
}

/** Every output the panel can show, by its preview key, in the order the panel lists them. */
internal fun previewOutputKeys(proj: ProjectionSettings): List<String> {
    fun keys(kind: String, count: Int) = List(count) { Constants.previewOutputKey(kind, it) }
    return keys(Constants.PREVIEW_OUTPUT_SCREEN, proj.screenAssignments.size) +
        keys(Constants.PREVIEW_OUTPUT_BROWSER_SOURCE, proj.browserSourceOutputs.size) +
        keys(Constants.PREVIEW_OUTPUT_NDI, proj.ndiOutputs.size) +
        keys(Constants.PREVIEW_OUTPUT_OMT, proj.omtOutputs.size)
}
