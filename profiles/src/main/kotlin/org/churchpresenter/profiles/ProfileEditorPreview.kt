package org.churchpresenter.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.profile_adjust
import org.churchpresenter.strings.generated.resources.profile_adjust_guide
import org.churchpresenter.strings.generated.resources.profile_adjust_guide_band
import org.churchpresenter.strings.generated.resources.profile_adjust_guide_blocks
import org.churchpresenter.strings.generated.resources.profile_adjust_guide_boxes
import org.churchpresenter.strings.generated.resources.profile_adjust_no_boxes
import org.churchpresenter.strings.generated.resources.profile_page_title
import org.churchpresenter.strings.generated.resources.profile_preview_larger
import org.churchpresenter.strings.generated.resources.profile_reset_positions
import org.churchpresenter.strings.generated.resources.profile_reset_positions_sub
import org.churchpresenter.sharedui.presenter.LocalPresentedBlocks
import org.churchpresenter.sharedui.presenter.PresentedBlock
import org.churchpresenter.sharedui.utils.OutputSize
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.TextBoxOptions
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.components.RaisedSwitch
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource

/**
 * What the Adjust handles can reach on the page being edited, pointed where its Text rows are --
 * null on every page they have nothing to do on.
 */
@Composable
internal fun adjustModelFor(
    pane: CustomizePane?,
    draft: AppSettings,
    profile: OutputProfile,
    element: Adjustable<CustomizeElement?>,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    /** The Bible page's translation and where the Songs page's Text rows point, for the preview to pick. */
    translation: Adjustable<Int>,
    songTargets: SongTargets,
): AdjustModel? = when (pane) {
    CustomizePane.BIBLE -> bibleAdjustModel(
        draft,
        profile,
        Adjustable(element.value ?: CustomizeElement.BIBLE_TEXT, element.onChange),
        onSettingsChange,
        translation,
    )
    CustomizePane.SONGS -> songAdjustModel(draft, profile, songTargets, onSettingsChange)
    CustomizePane.CAPTIONS -> draft.sttSettings.let { stt ->
        boxesOnlyAdjustModel(pageBoxTargets(stt.textBoxes, stt.textBoxOptions) { boxes ->
            onSettingsChange { s -> s.copy(sttSettings = s.sttSettings.copy(textBoxes = boxes)) }
        })
    }
    CustomizePane.SUBTITLES -> draft.mediaSettings.let { media ->
        boxesOnlyAdjustModel(pageBoxTargets(media.textBoxes, media.textBoxOptions) { boxes ->
            onSettingsChange { s -> s.copy(mediaSettings = s.mediaSettings.copy(textBoxes = boxes)) }
        })
    }
    CustomizePane.QA -> draft.qaSettings.let { qa ->
        boxesOnlyAdjustModel(pageBoxTargets(qa.textBoxes, qa.textBoxOptions) { boxes ->
            onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(textBoxes = boxes)) }
        })
    }
    CustomizePane.DICTIONARY -> draft.dictionarySettings.let { ds ->
        boxesOnlyAdjustModel(pageBoxTargets(ds.textBoxes, ds.textBoxOptions) { boxes ->
            onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(textBoxes = boxes)) }
        })
    }
    CustomizePane.STAGE_MONITOR -> draft.stageMonitorSettings.let { sm ->
        boxesOnlyAdjustModel(pageBoxTargets(sm.textBoxes, sm.textBoxOptions) { boxes ->
            onSettingsChange { s -> s.copy(stageMonitorSettings = s.stageMonitorSettings.copy(textBoxes = boxes)) }
        })
    }
    else -> null
}

/**
 * The boxes of a single-form page, each turned on one a handle -- written back through [write] as
 * the page's whole map -- or null while none is on.
 */
private fun pageBoxTargets(
    boxes: Map<String, TextBox>,
    options: TextBoxOptions,
    write: (Map<String, TextBox>) -> Unit,
): BoxTargets? {
    val handles = boxes.filterValues { it.enabled }.map { (key, box) ->
        BoxHandle(key = key, box = box, onChange = { changed -> write(boxes + (key to changed)) }, onPick = {})
    }
    return if (handles.isEmpty()) null else BoxTargets(handles, selected = null, options = options)
}

/**
 * The preview column as the editor draws it: the picture with its Adjust handles, Larger, and the
 * context card -- and the large preview while it is open. The sample, background mode and Adjust
 * are this session's checking, held here and never written to the profile.
 */
@Composable
internal fun EditorPreview(
    pane: CustomizePane?,
    pageLabel: String,
    element: CustomizeElement?,
    draft: AppSettings,
    profile: OutputProfile,
    usedBy: List<String>,
    onProfileChange: (OutputProfile) -> Unit,
    onOpenOutputs: () -> Unit,
    adjustModel: AdjustModel?,
    contextCard: @Composable () -> Unit,
) {
    var slot by remember { mutableStateOf(PreviewSampleSlot.MEDIUM) }
    var backgroundMode by remember { mutableStateOf(PreviewBackgroundMode.ACTUAL) }
    var adjust by remember { mutableStateOf(false) }
    var large by remember { mutableStateOf(false) }
    val output = OutputSize(profile.previewWidth, profile.previewHeight)
    // Where the presenter drew each block, for the handles to find them. The large preview keeps
    // its own: both are on screen at once, at different sizes.
    val blocks = remember { mutableStateMapOf<PresentedBlock, Rect>() }
    CompositionLocalProvider(LocalPresentedBlocks provides blocks) { ProfilePreviewColumn(
        pane = pane,
        pageLabel = pageLabel,
        element = element,
        draft = draft,
        profile = profile,
        usedBy = usedBy,
        onProfileFieldChange = onProfileChange,
        slot = slot,
        onSlotChange = { slot = it },
        backgroundMode = backgroundMode,
        onBackgroundModeChange = { backgroundMode = it },
        onOpenOutputs = onOpenOutputs,
        // Not for the stage layout, whose picture is a miniature -- the page draws it to scale.
        toolbarActions = { if (pane != null && pane != CustomizePane.STAGE_MONITOR) LargerKey { large = true } },
        overlay = { width -> if (adjust && adjustModel != null) PreviewAdjustOverlay(adjustModel, width, output) },
        underPreview = {
            if (adjustModel != null) AdjustSwitch(
                adjust,
                { adjust = it },
                adjustModel.band != null,
                adjustModel.hasBlocks,
                boxesOnly = adjustModel.boxesOnly,
                noBoxes = adjustModel.boxesOnly && adjustModel.boxes == null,
            )
            adjustModel?.positions?.let { ResetPositionsKey(it) }
        },
        contextCard = contextCard,
    ) }
    if (large && pane != null) {
        LargePreview(
            pane = pane,
            title = stringResource(Res.string.profile_page_title, profile.displayName(), pageLabel),
            element = element,
            draft = draft,
            profile = profile,
            onProfileChange = onProfileChange,
            slot = slot,
            onSlotChange = { slot = it },
            backgroundMode = backgroundMode,
            onBackgroundModeChange = { backgroundMode = it },
            adjustModel = adjustModel,
            adjust = adjust,
            onAdjustChange = { adjust = it },
            onClose = { large = false },
        )
    }
}

/** Larger: opens the preview across the whole window. */
@Composable
internal fun LargerKey(onClick: () -> Unit) {
    KeyButton(
        onClick = onClick,
        modifier = Modifier.height(30.dp).testTag(PREVIEW_LARGER_TAG),
        contentPadding = PaddingValues(horizontal = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(Icons.Filled.OpenInFull, contentDescription = null, modifier = Modifier.size(13.dp))
            Text(stringResource(Res.string.profile_preview_larger), fontSize = 12.sp)
        }
    }
}

/** Adjust on preview, and -- while it is on -- what the handles do. */
@Composable
internal fun AdjustSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    band: Boolean,
    blocks: Boolean = false,
    /** A page with only text boxes to adjust, whose guide says so. */
    boxesOnly: Boolean = false,
    /** On such a page, whether none of its boxes is on yet -- the note says where to turn one on. */
    noBoxes: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                .testTag(ADJUST_SWITCH_TAG),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RaisedSwitch(checked = checked, onCheckedChange = null)
            Text(
                stringResource(Res.string.profile_adjust),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (checked && boxesOnly && noBoxes) {
            // Nothing on the preview to pick until a box is on, so say so plainly, not as a hint.
            Text(
                stringResource(Res.string.profile_adjust_no_boxes),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.semantic.warning,
                modifier = Modifier.testTag(ADJUST_NO_BOXES_TAG),
            )
        } else if (checked && boxesOnly) {
            Text(
                stringResource(Res.string.profile_adjust_guide_boxes),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = profilesPalette().faintText,
            )
        } else if (checked) {
            Text(
                stringResource(if (band) Res.string.profile_adjust_guide_band else Res.string.profile_adjust_guide),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = profilesPalette().faintText,
            )
            if (blocks) {
                Text(
                    stringResource(Res.string.profile_adjust_guide_blocks),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = profilesPalette().faintText,
                )
            }
        }
    }
}

/**
 * Reset positions: everything on the page moved on its own -- a translation, a reference, a song
 * element -- put back where the layout puts it, on this output. Always under the preview, so an
 * element dragged out of sight, with its handle, can still be brought back.
 */
@Composable
internal fun ResetPositionsKey(
    positions: PositionsReset,
    /** Its explanation beside it -- left off in a toolbar, where the key sits among others. */
    note: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        KeyButton(
            onClick = positions.onReset,
            enabled = positions.moved,
            modifier = Modifier.height(if (note) 30.dp else 28.dp).testTag(RESET_POSITIONS_TAG),
            contentPadding = PaddingValues(horizontal = 10.dp),
        ) {
            Text(stringResource(Res.string.profile_reset_positions), fontSize = if (note) 12.sp else 11.sp)
        }
        if (note) {
            Text(
                stringResource(Res.string.profile_reset_positions_sub),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = profilesPalette().faintText,
            )
        }
    }
}

/** Test handles for Larger and the Adjust switch. */
internal const val PREVIEW_LARGER_TAG = "profile_preview_larger"
internal const val ADJUST_SWITCH_TAG = "profile_adjust_switch"
internal const val RESET_POSITIONS_TAG = "profile_reset_positions"

/** Test handle for the note shown while Adjust is on and none of the page's boxes is. */
internal const val ADJUST_NO_BOXES_TAG = "profile_adjust_no_boxes"
