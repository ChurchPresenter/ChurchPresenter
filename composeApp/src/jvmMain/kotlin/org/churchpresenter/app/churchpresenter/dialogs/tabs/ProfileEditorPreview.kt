package org.churchpresenter.app.churchpresenter.dialogs.tabs

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.profile_adjust
import churchpresenter.composeapp.generated.resources.profile_adjust_guide
import churchpresenter.composeapp.generated.resources.profile_adjust_guide_band
import churchpresenter.composeapp.generated.resources.profile_adjust_guide_blocks
import churchpresenter.composeapp.generated.resources.profile_page_title
import churchpresenter.composeapp.generated.resources.profile_preview_larger
import org.churchpresenter.app.churchpresenter.presenter.LocalPresentedBlocks
import org.churchpresenter.app.churchpresenter.presenter.PresentedBlock
import org.churchpresenter.app.churchpresenter.utils.OutputSize
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.components.RaisedSwitch
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
    element: CustomizeElement?,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    /** The Bible page's translation and where the Songs page's Text rows point, for the preview to pick. */
    translation: Adjustable<Int>,
    songTargets: SongTargets,
): AdjustModel? = when (pane) {
    CustomizePane.BIBLE ->
        bibleAdjustModel(draft, profile, element ?: CustomizeElement.BIBLE_TEXT, onSettingsChange, translation)
    CustomizePane.SONGS -> songAdjustModel(draft, profile, songTargets, onSettingsChange)
    else -> null
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
            )
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
internal fun AdjustSwitch(checked: Boolean, onChange: (Boolean) -> Unit, band: Boolean, blocks: Boolean = false) {
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
        if (checked) {
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

/** Test handles for Larger and the Adjust switch. */
internal const val PREVIEW_LARGER_TAG = "profile_preview_larger"
internal const val ADJUST_SWITCH_TAG = "profile_adjust_switch"
