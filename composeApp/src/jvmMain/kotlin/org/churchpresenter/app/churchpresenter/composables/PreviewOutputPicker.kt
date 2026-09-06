package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.browser_source_output_label
import churchpresenter.composeapp.generated.resources.ndi_output_numbered
import churchpresenter.composeapp.generated.resources.preview_output
import churchpresenter.composeapp.generated.resources.screen_number
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.presenter.showsContentFor
import org.churchpresenter.app.churchpresenter.utils.FallbackOutputSize
import org.churchpresenter.app.churchpresenter.utils.OutputKind
import org.churchpresenter.app.churchpresenter.utils.PreviewOutput
import org.churchpresenter.app.churchpresenter.utils.outputSizeOf
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

/**
 * Every output that shows [mode], in the order the live preview panel lists them.
 *
 * Physical screens first, then Browser Sources, then the NDI outputs that are actually enabled --
 * a disabled one sends nothing, so previewing its shape would describe a feed the network is not
 * receiving. Screens set to "None" are skipped for the same reason.
 */
@Composable
fun outputsShowing(settings: AppSettings, mode: Presenting): List<PreviewOutput> {
    val proj = settings.projectionSettings
    val outputs = mutableListOf<PreviewOutput>()

    proj.screenAssignments.forEachIndexed { index, assignment ->
        if (assignment.targetDisplay == Constants.KEY_TARGET_NONE) return@forEachIndexed
        if (!showsContentFor(mode, assignment)) return@forEachIndexed
        outputs += PreviewOutput(
            key = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_SCREEN, index),
            label = proj.screenLabelOr(assignment, stringResource(Res.string.screen_number, index + 1)),
            size = outputSizeOf(assignment, OutputKind.SCREEN),
        )
    }
    proj.browserSourceOutputs.forEachIndexed { index, output ->
        if (!showsContentFor(mode, output)) return@forEachIndexed
        outputs += PreviewOutput(
            key = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_BROWSER_SOURCE, index),
            label = output.browserSourceLabelOr(
                stringResource(Res.string.browser_source_output_label, index + 1)
            ),
            size = outputSizeOf(output, OutputKind.BROWSER_SOURCE),
        )
    }
    proj.ndiOutputs.forEachIndexed { index, output ->
        if (!output.ndiEnabled || !showsContentFor(mode, output)) return@forEachIndexed
        outputs += PreviewOutput(
            key = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_NDI, index),
            label = output.ndiLabelOr(stringResource(Res.string.ndi_output_numbered, index + 1)),
            size = outputSizeOf(output, OutputKind.NDI),
        )
    }
    return outputs
}

/**
 * The output a tab is previewing: the operator's stored pick, or the first one showing that content.
 *
 * A stored key that matches nothing -- a monitor unplugged, an output deleted -- falls back to the
 * first without clearing the stored value, so the choice comes back when the output does. With no
 * output showing this content at all, 1920x1080 stands in rather than nothing being drawn.
 */
@Composable
fun rememberPreviewOutput(settings: AppSettings, tabId: String, mode: Presenting): PreviewOutput {
    val outputs = outputsShowing(settings, mode)
    val storedKey = settings.projectionSettings.previewOutputSelections[tabId]
    return outputs.firstOrNull { it.key == storedKey }
        ?: outputs.firstOrNull()
        ?: PreviewOutput(key = "", label = "", size = FallbackOutputSize)
}

/**
 * Lets the operator say which output a tab's preview stands for.
 *
 * **Draws nothing when there is at most one candidate**, which is the ordinary single-output rig:
 * there is no choice to make, and a permanent row of chrome on five tabs would earn nothing. It
 * appears only once a second differently-routed output exists, which is exactly when the preview's
 * shape becomes ambiguous.
 */
@Composable
fun PreviewOutputPicker(
    settings: AppSettings,
    tabId: String,
    mode: Presenting,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val outputs = outputsShowing(settings, mode)
    if (outputs.size < 2) return

    val selected = rememberPreviewOutput(settings, tabId, mode)
    DropdownSelector(
        label = stringResource(Res.string.preview_output),
        value = selected.key,
        options = outputs.map { it.key to it.label },
        onValueChange = { key ->
            onSettingsChange { s ->
                s.copy(
                    projectionSettings = s.projectionSettings.copy(
                        previewOutputSelections = s.projectionSettings.previewOutputSelections + (tabId to key)
                    )
                )
            }
        },
        modifier = modifier,
        compact = true,
    )
}
