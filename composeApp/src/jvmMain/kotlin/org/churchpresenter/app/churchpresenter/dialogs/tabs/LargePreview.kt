package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.preview_layout_done
import org.churchpresenter.app.churchpresenter.presenter.LocalPresentedBlocks
import org.churchpresenter.app.churchpresenter.presenter.PresentedBlock
import org.churchpresenter.app.churchpresenter.utils.OutputSize
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.KeyButton
import org.jetbrains.compose.resources.stringResource

private const val SCRIM_ALPHA = 0.94f
private val SIDE_ROOM = 120.dp
private val LARGEST_PREVIEW = 1600.dp
private val TOOLBAR_WIDTH = 720.dp

/**
 * The preview across the whole window, over a dark scrim: the same toolbar, the same Adjust handles,
 * at the largest size the window allows -- `min(window width − 120, height × ratio, 1600)` -- so each
 * pointer step moves a value less. Done or Esc closes it.
 */
@Composable
internal fun LargePreview(
    pane: CustomizePane,
    title: String,
    element: CustomizeElement?,
    draft: AppSettings,
    profile: OutputProfile,
    onProfileChange: (OutputProfile) -> Unit,
    slot: PreviewSampleSlot,
    onSlotChange: (PreviewSampleSlot) -> Unit,
    backgroundMode: PreviewBackgroundMode,
    onBackgroundModeChange: (PreviewBackgroundMode) -> Unit,
    adjustModel: AdjustModel?,
    adjust: Boolean,
    onAdjustChange: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val drawsText = pane == CustomizePane.BIBLE || pane == CustomizePane.SONGS
    val blocks = remember { mutableStateMapOf<PresentedBlock, Rect>() }
    Popup(
        onDismissRequest = onClose,
        properties = PopupProperties(focusable = true),
        onKeyEvent = { event ->
            (event.type == KeyEventType.KeyDown && event.key == Key.Escape).also { if (it) onClose() }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
                .padding(24.dp)
                .testTag(LARGE_PREVIEW_TAG),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = TOOLBAR_WIDTH)
                    .fillMaxWidth()
                    .background(profilesPalette().rail, AppShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    KeyButton(onClick = onClose, modifier = Modifier.testTag(LARGE_PREVIEW_DONE_TAG)) {
                        Text(stringResource(Res.string.preview_layout_done), fontSize = 12.sp)
                    }
                }
                PreviewToolbar(
                    pageLabel = pane.navLabel(),
                    profile = profile,
                    onProfileChange = onProfileChange,
                    shapeState = rememberPreviewShapeState(profile),
                    backgroundMode = backgroundMode.takeIf { pane.hasPreviewBackground() },
                    onBackgroundModeChange = onBackgroundModeChange,
                    slot = slot.takeIf { drawsText },
                    onSlotChange = onSlotChange,
                    trailing = { adjustModel?.positions?.let { ResetPositionsKey(it, note = false) } },
                )
                if (adjustModel != null) AdjustSwitch(
                    adjust,
                    onAdjustChange,
                    adjustModel.band != null,
                    adjustModel.hasBlocks,
                    boxesOnly = adjustModel.boxesOnly,
                    noBoxes = adjustModel.boxesOnly && adjustModel.boxes == null,
                )
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val output = OutputSize(profile.previewWidth, profile.previewHeight)
                val width = minOf(maxWidth - SIDE_ROOM, maxHeight * output.aspectRatio, LARGEST_PREVIEW)
                Box(Modifier.width(width)) { CompositionLocalProvider(LocalPresentedBlocks provides blocks) {
                    CustomizeStagePanel(
                        pane = pane,
                        element = element,
                        settings = draft,
                        profile = profile,
                        output = output,
                        slot = slot,
                        modifier = Modifier.fillMaxWidth(),
                        backgroundMode = backgroundMode,
                    )
                    if (adjust && adjustModel != null) PreviewAdjustOverlay(adjustModel, width, output)
                } }
            }
        }
    }
}

/** Test handles for the large preview and its Done. */
internal const val LARGE_PREVIEW_TAG = "profile_large_preview"
internal const val LARGE_PREVIEW_DONE_TAG = "profile_large_preview_done"
