package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.profile_live_none
import churchpresenter.composeapp.generated.resources.profile_live_on
import churchpresenter.composeapp.generated.resources.profile_live_outputs
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbar
import org.churchpresenter.app.churchpresenter.utils.OutputSize
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.theme.AppShape
import org.jetbrains.compose.resources.stringResource

/** The preview column's width: a 400dp picture and its padding. */
internal val PREVIEW_COLUMN_WIDTH = 440.dp

/** How tall the picture may grow -- a portrait screen is drawn narrower rather than taller. */
private val STAGE_MAX_HEIGHT = 320.dp

/**
 * The right-hand column: the picture of the page being edited, and what it means. Nothing in it
 * changes the profile but the preview's own shape.
 *
 * [pane] is the appearance the picture stands for; on the profile's own pages it is the first thing
 * the profile shows, so the picture is always of something. [overlay] is drawn over the picture at
 * its own size -- the Adjust handles. [contextCard] is the card under it -- how the profile is
 * linked -- and [usedBy] fills the "Live on" card below that.
 */
@Composable
internal fun ProfilePreviewColumn(
    pane: CustomizePane?,
    pageLabel: String,
    element: CustomizeElement?,
    draft: AppSettings,
    profile: OutputProfile,
    usedBy: List<String>,
    onProfileFieldChange: (OutputProfile) -> Unit,
    slot: PreviewSampleSlot,
    onSlotChange: (PreviewSampleSlot) -> Unit,
    backgroundMode: PreviewBackgroundMode,
    onBackgroundModeChange: (PreviewBackgroundMode) -> Unit,
    onOpenOutputs: () -> Unit,
    modifier: Modifier = Modifier,
    toolbarActions: @Composable RowScope.() -> Unit = {},
    overlay: @Composable (previewWidth: Dp) -> Unit = {},
    underPreview: @Composable ColumnScope.() -> Unit = {},
    contextCard: @Composable () -> Unit = {},
) {
    val palette = profilesPalette()
    val scroll = rememberScrollState()
    val drawsText = pane == CustomizePane.BIBLE || pane == CustomizePane.SONGS
    Box(
        modifier = modifier
            .width(PREVIEW_COLUMN_WIDTH)
            .fillMaxHeight()
            .background(palette.rail),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PreviewToolbar(
                pageLabel = pageLabel,
                profile = profile,
                onProfileChange = onProfileFieldChange,
                shapeState = rememberPreviewShapeState(profile),
                // Only where there is a ground to switch, and sample text to lengthen.
                backgroundMode = backgroundMode.takeIf { pane.hasPreviewBackground() },
                onBackgroundModeChange = onBackgroundModeChange,
                slot = slot.takeIf { drawsText },
                onSlotChange = onSlotChange,
                actions = toolbarActions,
            )
            BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val output = OutputSize(profile.previewWidth, profile.previewHeight)
                val stageWidth = minOf(maxWidth, STAGE_MAX_HEIGHT * output.aspectRatio)
                Box(Modifier.width(stageWidth)) {
                    if (pane == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(output.aspectRatio)
                                .background(Color(PREVIEW_BACKGROUND), AppShape(6.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppShape(6.dp)),
                        )
                    } else {
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
                    }
                    overlay(stageWidth)
                }
            }
            if (pane != null) {
                Text(text = previewNote(pane, draft, profile), fontSize = 11.sp, color = palette.faintText)
            }
            underPreview()
            contextCard()
            LiveOnCard(usedBy, onOpenOutputs)
        }
        SettingsScrollbar(scroll)
    }
}

/** A white card in the preview column: the context card and the Live on card. */
@Composable
internal fun PreviewSideCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val palette = profilesPalette()
    val shape = AppShape(10.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.card)
            .border(1.dp, palette.cardBorder, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** "Live on Screen 1. Changes show there right away." -- or that no output shows it yet. */
@Composable
private fun LiveOnCard(usedBy: List<String>, onOpenOutputs: () -> Unit) {
    PreviewSideCard(Modifier.testTag(LIVE_ON_CARD_TAG)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                Icons.Filled.Tv,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = if (usedBy.isEmpty()) {
                    stringResource(Res.string.profile_live_none)
                } else {
                    stringResource(Res.string.profile_live_on, usedBy.joinToString(", "))
                },
                fontSize = 12.sp,
                fontWeight = if (usedBy.isEmpty()) FontWeight.Normal else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            LinkText(stringResource(Res.string.profile_live_outputs), onOpenOutputs)
        }
    }
}

/** Test handle for the Live on card. */
internal const val LIVE_ON_CARD_TAG = "profile_live_on"
