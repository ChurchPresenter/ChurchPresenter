package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.customize_hint_off
import churchpresenter.composeapp.generated.resources.customize_hint_on
import churchpresenter.composeapp.generated.resources.customize_hint_stage_off
import churchpresenter.composeapp.generated.resources.customize_hint_stage_on
import churchpresenter.composeapp.generated.resources.preview
import org.churchpresenter.app.churchpresenter.composables.SegmentedButton
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import org.churchpresenter.bible.defaultTranslationAbbreviation
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.ScreenAssignment
import org.jetbrains.compose.resources.stringResource
import kotlin.math.ceil

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
    translationIndex: Int,
    onTranslationChange: (Int) -> Unit,
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
            // Bible only, and only with a stack worth choosing from: every other category has one
            // set of settings, so a selector above it would name a choice that does not exist.
            if (pane == CustomizePane.BIBLE) {
                CustomizeTranslationChips(
                    translations = draft.bibleSettings.translationList(),
                    selected = translationIndex,
                    onSelect = onTranslationChange,
                )
            }
            CustomizeElementChips(elements, element, onElementChange)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DimmedWhenFollowing(live, Modifier.fillMaxSize()) {
                CustomizePaneContent(pane, element, translationIndex, draft, onSettingsChange)
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
            .background(MaterialTheme.colorScheme.surfaceVariant),
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

/**
 * Which translation of the stack the Bible controls below are editing.
 *
 * Numbered and abbreviated exactly as the global Bible tab's own selector is (`1 · KJV`), because
 * they pick from the same ordered stack and an operator moving between the two should not have to
 * work out that they are the same list. One entry means no choice, so nothing is drawn.
 *
 * The module titles the global tab resolves its abbreviations from are not read here -- this dialog
 * opens no `.spb` -- so a translation that has never been given a custom abbreviation falls back to
 * the one derived from its file name, which is what the presenter draws for it anyway.
 */
@Composable
private fun CustomizeTranslationChips(
    translations: List<BibleTranslationSettings>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    if (translations.size < 2) return
    CustomizeSelectorRow(
        items = translations.mapIndexed { index, translation ->
            val abbreviation = translation.customAbbreviation.ifBlank {
                defaultTranslationAbbreviation(title = "", fileName = translation.fileName)
            }
            SegmentedButtonItem(
                value = index,
                label = "${index + 1} · $abbreviation",
                testTag = translationChipTag(index),
            )
        },
        selected = selected,
        onSelect = onSelect,
        modifier = Modifier.testTag(CUSTOMIZE_TRANSLATION_ROW_TAG),
    )
}

/** The selector above the control column — which element of this category is being styled. */
@Composable
private fun CustomizeElementChips(
    elements: List<CustomizeElement>,
    selected: CustomizeElement?,
    onSelect: (CustomizeElement) -> Unit,
) {
    if (elements.isEmpty()) return
    CustomizeSelectorRow(
        items = elements.map {
            SegmentedButtonItem(value = it, label = it.label(), testTag = elementChipTag(it.name))
        },
        selected = selected ?: elements.first(),
        onSelect = onSelect,
        modifier = Modifier.testTag(CUSTOMIZE_ELEMENT_ROW_TAG),
    )
}

/**
 * One segmented control spanning the column, folded onto as many rows as it takes.
 *
 * A segmented control rather than the row of filter chips this replaced: these are one choice from
 * a closed list, which is what a segmented control says and what a row of independent chips does
 * not -- and the same control the panes below already use for every other such list, so the
 * selector and the settings under it now read as one form.
 *
 * The width is shared evenly so the control ends flush with the column, and the list is broken into
 * rows of equal length rather than filling one row and leaving a stub: seven song elements across
 * this column go four and three, not four and three ragged against a full row's width.
 */
@Composable
private fun <T> CustomizeSelectorRow(
    items: List<SegmentedButtonItem<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        BoxWithConstraints {
            val available = maxWidth
            val perRow = (available / SELECTOR_MIN_SEGMENT).toInt().coerceIn(1, items.size)
            val rows = ceil(items.size / perRow.toDouble()).toInt().coerceAtLeast(1)
            val columns = ceil(items.size / rows.toDouble()).toInt().coerceAtLeast(1)
            // One control per row, each sized to the row it is in, rather than one control at a
            // single width: a last row holding fewer than the rest was otherwise left short of the
            // column, so the selector ended ragged where every other control ends flush.
            Column(verticalArrangement = Arrangement.spacedBy(SELECTOR_ROW_GAP)) {
                items.chunked(columns).forEach { row ->
                    SegmentedButton(
                        items = row,
                        selectedValue = selected,
                        onValueChange = onSelect,
                        buttonWidth = available / row.size,
                        buttonHeight = SELECTOR_HEIGHT,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                        // Two lines, because one of these labels is a sentence: "Reference &
                        // Transliteration" is 27 characters and was cut off mid-word at any width
                        // this column can give a third of itself.
                        maxLines = SELECTOR_MAX_LINES,
                    )
                }
            }
        }
    }
}

/** Narrower than this and a label such as "Next Section" has nowhere to go but off the end. */
private val SELECTOR_MIN_SEGMENT = 92.dp

/** Tall enough for the two lines below, so a selector does not change height with its longest label. */
private val SELECTOR_HEIGHT = 36.dp

private val SELECTOR_ROW_GAP = 4.dp

private const val SELECTOR_MAX_LINES = 2

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
    translationIndex: Int,
    draft: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val shown = element ?: return
    // Keyed on what the column is pointed at. One set of controls stands for many stored profiles,
    // and without this Compose keeps the subtree across a switch and hands each control the state
    // -- and the write-back lambda -- of whichever control held its slot before. Picking the second
    // translation and typing a font size then wrote it to the first. The global Bible tab keys its
    // own panel for exactly this reason.
    key(pane, shown, translationIndex) {
        when (pane) {
            CustomizePane.BIBLE -> BibleCustomizePane(shown, translationIndex, draft, onSettingsChange)
            CustomizePane.SONGS -> SongCustomizePane(shown, draft, onSettingsChange)
            CustomizePane.BACKGROUND -> BackgroundCustomizePane(shown, draft, onSettingsChange)
            CustomizePane.DICTIONARY -> DictionaryCustomizePane(shown, draft, onSettingsChange)
            // Handled by CustomizeBody, which gives it the whole width instead of this column.
            CustomizePane.STAGE_MONITOR -> Unit
        }
    }
}

/** Test handle for the row of element chips above the control column. */
internal const val CUSTOMIZE_ELEMENT_ROW_TAG = "customize_element_row"

/** Test handle for the Bible pane's translation selector. */
internal const val CUSTOMIZE_TRANSLATION_ROW_TAG = "customize_translation_row"

/** Test handle for one translation chip, by its position in the stack. */
internal fun translationChipTag(index: Int): String = "customize_translation_$index"
