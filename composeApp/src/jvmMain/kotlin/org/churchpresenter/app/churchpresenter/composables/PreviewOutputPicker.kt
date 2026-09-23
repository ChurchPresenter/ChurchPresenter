package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.browser_source_output_label
import churchpresenter.composeapp.generated.resources.ic_warning
import churchpresenter.composeapp.generated.resources.ndi_output_numbered
import churchpresenter.composeapp.generated.resources.preview_output
import churchpresenter.composeapp.generated.resources.preview_output_mode_disabled_warning
import churchpresenter.composeapp.generated.resources.screen_number
import org.churchpresenter.app.churchpresenter.BuildConfig
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.presenter.showsContentFor
import org.churchpresenter.app.churchpresenter.utils.DevFlags
import org.churchpresenter.app.churchpresenter.utils.FallbackOutputSize
import org.churchpresenter.app.churchpresenter.utils.OutputKind
import org.churchpresenter.app.churchpresenter.utils.PreviewOutput
import org.churchpresenter.app.churchpresenter.utils.outputSizeOf
import org.churchpresenter.app.churchpresenter.utils.rememberScreenDevices
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.profileFor
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.theme.components.DropdownSelector

/** How wide the picker itself is, regardless of how much room its container offers. */
private val PREVIEW_OUTPUT_PICKER_WIDTH = 200.dp

/**
 * Real, non-primary monitors plus DeckLink devices -- see `ProjectionSettingsTab`'s own copy.
 *
 * Internal rather than private: `screenAssignmentIsOpen` (`SettingsPreviewOnScreen.kt`) needs the
 * same dev-fallback math to answer a related but different question -- see its own doc comment.
 */
@Composable
internal fun rememberRealWindowCount(): Int {
    val deckLinkCount = remember { if (DeckLinkManager.isAvailable()) DeckLinkManager.listDevices().size else 0 }
    return ((rememberScreenDevices().size - 1) + deckLinkCount).coerceAtLeast(0)
}

/**
 * Every live output, in the order the live preview panel lists them, marked with whether it is
 * actually routing [mode] right now.
 *
 * Physical screens first, then Browser Sources, then the NDI outputs that are actually enabled --
 * a disabled one sends nothing, so previewing its shape would describe a feed the network is not
 * receiving. Screens set to "None" are skipped for the same reason. An output that exists but has
 * this content switched off is still listed -- [PreviewOutput.showsMode] says so -- because the
 * operator may still want to preview or style that output's shape; [mode] of `null` means no single
 * content type applies (a Background "Default" surface, say), so nothing is ever marked off.
 *
 * Also includes any dev-fallback window(s): on a single-monitor dev machine, `main.kt` opens
 * `devWindowCount` extra windowed outputs whose assignment slots are auto-resolved to "None" only
 * because no hardware exists to assign them to -- `LivePreviewPanel` exempts those slots from the
 * "None" skip for the same reason, and this does the same, or they would be open at runtime but
 * invisible here.
 *
 * @param realWindowCount hoisted for the same reason `ProjectionSettingsTab` hoists its own screen
 *   detection: a headless test has no real displays to count, so its default (real non-primary
 *   monitors plus DeckLink devices) is always 0 there, making every test environment look like a
 *   single-monitor dev machine. Passing a positive count lets a test simulate a real display existing.
 */
@Composable
fun outputsShowing(
    settings: AppSettings,
    mode: Presenting?,
    realWindowCount: Int = rememberRealWindowCount(),
): List<PreviewOutput> {
    val proj = settings.projectionSettings
    val outputs = mutableListOf<PreviewOutput>()

    // Mirrors LivePreviewPanel's own dev-fallback detection, so the picker lists exactly what
    // main.kt actually opens on this machine, dev fallback windows included.
    val devWindowedFallback = (!BuildConfig.IS_RELEASE || DevFlags.forceDevWindow) && realWindowCount == 0
    val displayCount = realWindowCount + if (devWindowedFallback) proj.devWindowCount.coerceAtLeast(1) else 0

    for (index in 0 until displayCount) {
        val assignment = proj.getAssignment(index)
        val isDevFallbackSlot = devWindowedFallback && index >= realWindowCount
        if (!isDevFallbackSlot && assignment.targetDisplay == Constants.KEY_TARGET_NONE) continue
        val profile = proj.profileFor(assignment) ?: OutputProfile()
        outputs += PreviewOutput(
            key = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_SCREEN, index),
            label = proj.screenLabelOr(assignment, stringResource(Res.string.screen_number, index + 1)),
            size = outputSizeOf(assignment, OutputKind.SCREEN),
            showsMode = mode == null || showsContentFor(mode, profile),
            assignment = assignment,
        )
    }
    proj.browserSourceOutputs.forEachIndexed { index, output ->
        val profile = proj.profileFor(output) ?: OutputProfile()
        outputs += PreviewOutput(
            key = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_BROWSER_SOURCE, index),
            label = output.browserSourceLabelOr(
                stringResource(Res.string.browser_source_output_label, index + 1)
            ),
            size = outputSizeOf(output, OutputKind.BROWSER_SOURCE),
            showsMode = mode == null || showsContentFor(mode, profile),
            assignment = output,
        )
    }
    proj.ndiOutputs.forEachIndexed { index, output ->
        if (!output.ndiEnabled) return@forEachIndexed
        val profile = proj.profileFor(output) ?: OutputProfile()
        outputs += PreviewOutput(
            key = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_NDI, index),
            label = output.ndiLabelOr(stringResource(Res.string.ndi_output_numbered, index + 1)),
            size = outputSizeOf(output, OutputKind.NDI),
            showsMode = mode == null || showsContentFor(mode, profile),
            assignment = output,
        )
    }
    return outputs
}

/**
 * The output a tab is previewing: the operator's stored pick, or the first live one -- preferring
 * one that actually shows [mode] over one that doesn't, so a fresh rig defaults to something useful.
 *
 * A stored key that matches nothing -- a monitor unplugged, an output deleted -- falls back the same
 * way, without clearing the stored value, so the choice comes back when the output does. With no
 * output at all, 1920x1080 stands in rather than nothing being drawn.
 */
@Composable
fun rememberPreviewOutput(
    settings: AppSettings,
    tabId: String,
    mode: Presenting?,
    realWindowCount: Int = rememberRealWindowCount(),
): PreviewOutput {
    val outputs = outputsShowing(settings, mode, realWindowCount)
    val storedKey = settings.projectionSettings.previewOutputSelections[tabId]
    return outputs.firstOrNull { it.key == storedKey }
        ?: outputs.firstOrNull { it.showsMode }
        ?: outputs.firstOrNull()
        ?: PreviewOutput(
            key = "",
            label = "",
            size = FallbackOutputSize,
            showsMode = true,
            assignment = ScreenAssignment(),
        )
}

/**
 * Lets the operator say which output a tab's preview stands for.
 *
 * **Draws nothing when there is at most one candidate**, which is the ordinary single-output rig:
 * there is no choice to make, and a permanent row of chrome on five tabs would earn nothing. It
 * appears only once a second live output exists, which is exactly when the preview's shape becomes
 * ambiguous. The row below the field warns when the *selected* output doesn't currently show this
 * content -- picking one is still allowed, since the operator may be styling ahead of switching it on.
 */
@Composable
fun PreviewOutputPicker(
    settings: AppSettings,
    tabId: String,
    mode: Presenting?,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    modifier: Modifier = Modifier,
    realWindowCount: Int = rememberRealWindowCount(),
) {
    val outputs = outputsShowing(settings, mode, realWindowCount)
    if (outputs.size < 2) return

    val selected = rememberPreviewOutput(settings, tabId, mode, realWindowCount)
    Column(modifier = modifier) {
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
            modifier = Modifier.width(PREVIEW_OUTPUT_PICKER_WIDTH),
            compact = true,
        )
        if (!selected.showsMode) {
            PreviewOutputWarning(stringResource(Res.string.preview_output_mode_disabled_warning))
        }
    }
}

/**
 * A small warning row under the picker, in the same shape as the conflict warning in
 * `ShortcutBindingRow.kt`. Public to this module so a tab with a narrower question than "is this
 * mode on" -- the Bible tab's per-output translation subset, say -- can report its own warning
 * alongside the picker's own, rather than reimplementing this row.
 */
@Composable
internal fun PreviewOutputWarning(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_warning),
            contentDescription = null,
            modifier = Modifier.size(11.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
