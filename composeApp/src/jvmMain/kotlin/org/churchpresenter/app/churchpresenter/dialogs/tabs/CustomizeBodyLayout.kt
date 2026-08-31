package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.customize_hint_off
import churchpresenter.composeapp.generated.resources.customize_hint_on
import churchpresenter.composeapp.generated.resources.customize_hint_stage_off
import churchpresenter.composeapp.generated.resources.customize_hint_stage_on
import churchpresenter.composeapp.generated.resources.preview
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import org.jetbrains.compose.resources.stringResource

/**
 * The Customize dialog's two right-hand columns: the element controls, and the picture they change.
 *
 * Split out of `ProjectionCustomizeDialog.kt` alongside `CustomizeRail.kt`; see that file's note.
 */

private val CONTROLS_WIDTH = 430.dp

private const val FOLLOWING_GLOBAL_ALPHA = 0.45f

/**
 * The two right-hand columns: the element controls, and the picture they change.
 *
 * The stage monitor takes the whole width instead. Its pane is a zone layout picker and a per-zone
 * style list — it has no elements to chip and draws its own preview inside itself, so splitting the
 * space would leave one half empty and squeeze the half that is used.
 */
@Composable
internal fun CustomizeBody(
    pane: CustomizePane,
    element: CustomizeElement?,
    elements: List<CustomizeElement>,
    live: Boolean,
    draft: AppSettings,
    assignment: ScreenAssignment,
    onElementChange: (CustomizeElement) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    if (pane == CustomizePane.STAGE_MONITOR) {
        DimmedWhenFollowing(live, Modifier.fillMaxSize()) {
            StageMonitorSettingsTab(settings = draft, onSettingsChange = onSettingsChange)
        }
        return
    }
    Row(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.width(CONTROLS_WIDTH).fillMaxHeight()) {
            CustomizeElementChips(elements, element, onElementChange)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DimmedWhenFollowing(live, Modifier.fillMaxSize()) {
                CustomizePaneContent(pane, element, draft, onSettingsChange)
            }
        }
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        CustomizePreviewColumn(pane, element, live, draft, assignment, onSettingsChange)
    }
}

/** The picture, its caption, and the settings that belong to it rather than to one element. */
@Composable
private fun CustomizePreviewColumn(
    pane: CustomizePane,
    element: CustomizeElement?,
    live: Boolean,
    draft: AppSettings,
    assignment: ScreenAssignment,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CustomizeCaption(stringResource(Res.string.preview))
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = displayModeLabel(assignment.displayMode),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // Never dimmed with the controls: this is what the screen shows, which is just as true when
        // the category is following the global settings as when it has its own.
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Width only, capped so the stage's own aspect ratio cannot make it taller than the
            // space between the caption and the strip -- the same shape the settings tabs give
            // their previews, with a height cap added because this column, unlike theirs, does not
            // scroll.
            val output = previewOutputSize(draft)
            CustomizeStagePanel(
                pane = pane,
                element = element,
                settings = draft,
                assignment = assignment,
                slot = PreviewSampleSlot.MEDIUM,
                modifier = Modifier.width(minOf(maxWidth, maxHeight * output.aspectRatio)),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        DimmedWhenFollowing(live, Modifier.fillMaxWidth()) {
            CustomizeCategoryStrip(pane, draft, assignment, onSettingsChange)
        }
    }
}

/** The chips above the control column — which element of this category is being styled. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomizeElementChips(
    elements: List<CustomizeElement>,
    selected: CustomizeElement?,
    onSelect: (CustomizeElement) -> Unit,
) {
    if (elements.isEmpty()) return
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(CUSTOMIZE_ELEMENT_ROW_TAG),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        elements.forEach { entry ->
            FilterChip(
                selected = entry == selected,
                onClick = { onSelect(entry) },
                label = {
                    Text(
                        text = entry.label(),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                shape = RoundedCornerShape(7.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = entry == selected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = Color.Transparent,
                ),
                modifier = Modifier.height(26.dp).testTag(elementChipTag(entry.name)),
            )
        }
    }
}

/**
 * [content], dimmed and swallowing input while this category is following the global settings.
 *
 * Swallows clicks rather than disabling each control: the panes are whole settings forms, and there
 * is no `enabled` to thread through a hundred of them. What is on screen stays true — those are the
 * global values, which is what the output draws — without inviting an edit that would silently
 * switch the category on.
 */
@Composable
private fun DimmedWhenFollowing(live: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier) {
        Box(modifier = Modifier.alpha(if (live) 1f else FOLLOWING_GLOBAL_ALPHA)) { content() }
        if (!live) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            )
        }
    }
}

@Composable
internal fun paneHint(pane: CustomizePane, overridden: Boolean): String =
    if (pane == CustomizePane.STAGE_MONITOR) {
        if (overridden) stringResource(Res.string.customize_hint_stage_on)
        else stringResource(Res.string.customize_hint_stage_off)
    } else {
        if (overridden) stringResource(Res.string.customize_hint_on)
        else stringResource(Res.string.customize_hint_off)
    }

/** The pane that edits this category, showing [element]. */
@Composable
private fun CustomizePaneContent(
    pane: CustomizePane,
    element: CustomizeElement?,
    draft: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val shown = element ?: return
    when (pane) {
        CustomizePane.BIBLE -> BibleCustomizePane(shown, draft, onSettingsChange)
        CustomizePane.SONGS -> SongCustomizePane(shown, draft, onSettingsChange)
        CustomizePane.BACKGROUND -> BackgroundCustomizePane(shown, draft, onSettingsChange)
        CustomizePane.DICTIONARY -> DictionaryCustomizePane(shown, draft, onSettingsChange)
        // Handled by CustomizeBody, which gives it the whole width instead of this column.
        CustomizePane.STAGE_MONITOR -> Unit
    }
}

/** Test handle for the row of element chips above the control column. */
internal const val CUSTOMIZE_ELEMENT_ROW_TAG = "customize_element_row"
