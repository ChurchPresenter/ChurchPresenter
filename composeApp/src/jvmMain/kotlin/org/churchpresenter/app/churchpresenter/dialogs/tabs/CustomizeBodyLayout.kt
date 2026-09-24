package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.content_bible_translations_all
import churchpresenter.composeapp.generated.resources.output_profile_output_panel
import churchpresenter.composeapp.generated.resources.preview
import churchpresenter.composeapp.generated.resources.preview_sample_long
import churchpresenter.composeapp.generated.resources.preview_sample_medium
import churchpresenter.composeapp.generated.resources.preview_sample_short
import kotlin.math.ceil
import org.churchpresenter.app.churchpresenter.composables.SegmentedButton
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbar
import org.churchpresenter.app.churchpresenter.utils.OutputSize
import org.churchpresenter.bible.defaultTranslationAbbreviation
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.OutputProfile
import org.jetbrains.compose.resources.stringResource

/**
 * The Customize dialog's two right-hand columns: the element controls, and the picture they change.
 *
 * Split out of `ProjectionCustomizeDialog.kt` alongside `CustomizeRail.kt`; see that file's note.
 */

/**
 * The preview column is what gets a fixed width; the controls take everything left over.
 *
 * It was the other way round -- controls pinned at 430dp, preview flexible -- and 430dp is not
 * enough for a row of this form's controls. Every cell here is a fixed-size field or button group,
 * so the ones that stretch (the spacing sliders, the shadow fields) were left with whatever the
 * fixed ones did not take, which on several elements was about 90dp each. The picture, meanwhile,
 * is drawn to an aspect ratio and gains nothing from the extra width: past a point it is simply a
 * larger copy of the same frame. So the picture is bounded and the form is given the remainder --
 * about 636dp of a 1240dp dialog.
 */
private val PREVIEW_WIDTH = 426.dp

/** How tall the picture may grow, leaving the rest of the column to the settings beneath it. */
private val STAGE_MAX_HEIGHT = 230.dp

/** The five preset shapes and Custom, sharing the column's width. */
private val SHAPE_SEGMENTS = PreviewShapePreset.entries.size + 1

/**
 * The element controls -- the left of the two panes [CustomizeBody] used to draw as one Row.
 *
 * The stage monitor takes the whole width instead. Its pane is a zone layout picker and a per-zone
 * style list — it has no elements to chip and draws its own preview inside itself, and it has no
 * preview column beside it either, which is what tells [ProfileEditor] not to show one.
 */
@Composable
internal fun CustomizeControls(
    pane: CustomizePane,
    element: CustomizeElement?,
    elements: List<CustomizeElement>,
    draft: AppSettings,
    profile: OutputProfile,
    translationIndex: Int,
    onTranslationChange: (Int) -> Unit,
    onElementChange: (CustomizeElement) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    /** A change to the profile's content selection rather than to its styling -- see [SongCustomizePane]. */
    onProfileFieldChange: (OutputProfile) -> Unit,
) {
    if (pane == CustomizePane.STAGE_MONITOR) {
        StageMonitorSettingsTab(settings = draft, onSettingsChange = onSettingsChange)
        return
    }
    if (pane.isWholeForm) {
        ProfileFormPane(pane = pane, draft = draft, onSettingsChange = onSettingsChange)
        return
    }
    Column(modifier = Modifier.fillMaxHeight()) {
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
        CustomizePaneContent(
            pane = pane,
            element = element,
            translationIndex = translationIndex,
            draft = draft,
            profile = profile,
            onSettingsChange = onSettingsChange,
            onProfileFieldChange = onProfileFieldChange,
        )
    }
}

/**
 * The picture, its caption, and the settings that belong to it rather than to one element.
 *
 * A sibling of the rest of [ProfileEditor]'s content rather than nested inside it, so its top edge
 * lines up with the Profiles tab's own rail rather than starting further down, under the name/
 * display-mode header.
 */
@Composable
internal fun CustomizePreviewColumn(
    pane: CustomizePane?,
    element: CustomizeElement?,
    draft: AppSettings,
    profile: OutputProfile,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onProfileFieldChange: (OutputProfile) -> Unit,
    onNavigate: (CustomizePane, CustomizeElement) -> Unit,
    slot: PreviewSampleSlot,
    onSlotChange: (PreviewSampleSlot) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(PREVIEW_WIDTH)
            .fillMaxHeight()
            // `surface`, not `surfaceVariant`. Every boxed field in the app -- the number fields,
            // the colour swatches, the dropdowns -- fills itself with `surfaceVariant`, so a column
            // painted that colour is the exact tone of the controls standing on it and they read as
            // holes in it rather than as fields. `surface` puts a step between the two and keeps
            // the three layers the dialog is built from distinct: the card the rail and the
            // controls sit on, the page beneath this column, and the fields on top of it.
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CustomizeCaption(stringResource(Res.string.preview))
            Spacer(modifier = Modifier.weight(1f))
            // The shape named beside the mode, never a resolution on its own: a profile is not tied to
            // one output's real size, and "1920×1080" here read exactly like the target-display
            // pickers on the Projection tab.
            Text(
                text = "${displayModeLabel(profile.displayMode)} · " +
                    previewShapeLabel(profile.previewWidth, profile.previewHeight),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // How much text the picture stands in for. A layout that reads perfectly against one verse
        // can overflow against a long one, and this is the only way to check that without putting
        // the real thing live -- the samples themselves never went anywhere, but the selector that
        // reached them did, leaving every preview stuck on MEDIUM.
        // Only where there is sample text to lengthen: the caption, subtitle, question and card
        // samples are one fixed piece each.
        if (pane != null && !pane.isWholeForm) Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
            ChoiceControl(
                options = listOf(
                    PreviewSampleSlot.SHORT.name to stringResource(Res.string.preview_sample_short),
                    PreviewSampleSlot.MEDIUM.name to stringResource(Res.string.preview_sample_medium),
                    PreviewSampleSlot.LONG.name to stringResource(Res.string.preview_sample_long),
                ),
                selected = slot.name,
                buttonWidth = (PREVIEW_WIDTH - 24.dp) / PreviewSampleSlot.entries.size,
                onSelect = { picked -> onSlotChange(PreviewSampleSlot.valueOf(picked)) },
            )
        }
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            PreviewShapeChooser(
                profile = profile,
                onProfileChange = onProfileFieldChange,
                segmentWidth = (PREVIEW_WIDTH - 24.dp) / SHAPE_SEGMENTS,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // Never dimmed with the controls: this is what the screen shows, which is just as true when
        // the category is following the global settings as when it has its own.
        // The picture takes the height its own aspect ratio asks for and no more, so the settings
        // below it start directly under it rather than after a band of empty column.
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val output = OutputSize(profile.previewWidth, profile.previewHeight)
            val stageWidth = Modifier.width(minOf(maxWidth, STAGE_MAX_HEIGHT * output.aspectRatio))
            // Everything hidden still draws the screen, empty, so the column keeps its place rather
            // than the editor jumping to fill it.
            if (pane == null) {
                Box(
                    modifier = stageWidth
                        .aspectRatio(output.aspectRatio)
                        .background(Color(PREVIEW_BACKGROUND), RoundedCornerShape(6.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
                )
            } else {
                CustomizeStagePanel(
                    pane = pane,
                    element = element,
                    settings = draft,
                    profile = profile,
                    output = output,
                    slot = slot,
                    modifier = stageWidth,
                )
            }
        }
        if (pane == null) return@Column
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // Scrolls, and takes what the picture left. These rows come and go with the category and
        // the chip -- Songs on a lyric slide draws five of them, the dictionary one -- so the block
        // has no height it can be given in advance, and the ones past the fold were simply cut off.
        //
        // With the bar every other scrolling surface in the app draws: without it nothing says the
        // rows continue below the fold, and an operator has no reason to look for them.
        val stripScroll = rememberScrollState()
        // Named, so it reads as the settings of the picture above rather than of the element chip in
        // the column beside it. Background has no strip, so it gets no caption either.
        if (pane == CustomizePane.BIBLE || pane == CustomizePane.SONGS) {
            Box(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)) {
                CustomizeCaption(stringResource(Res.string.output_profile_output_panel, pane.label()))
            }
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            CustomizeCategoryStrip(
                pane = pane,
                element = element,
                settings = draft,
                onSettingsChange = onSettingsChange,
                onNavigate = onNavigate,
                modifier = Modifier.verticalScroll(stripScroll),
            )
            SettingsScrollbar(stripScroll)
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
        // "All" leads, as it does on the Songs pane's language row: styling the whole stack at once
        // is the usual case, and picking one translation is how it is given a look of its own.
        items = listOf(
            SegmentedButtonItem(
                value = ALL_TRANSLATIONS,
                label = stringResource(Res.string.content_bible_translations_all),
                testTag = translationChipTag(ALL_TRANSLATIONS),
            ),
        ) + translations.mapIndexed { index, translation ->
            val abbreviation = translation.customAbbreviation.ifBlank {
                defaultTranslationAbbreviation(title = "", fileName = translation.fileName)
            }
            SegmentedButtonItem(
                value = index,
                label = "${index + 1} · $abbreviation",
                testTag = translationChipTag(index),
            )
        },
        selected = effectiveTranslationIndex(selected, translations.size),
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
internal fun <T> CustomizeSelectorRow(
    items: List<SegmentedButtonItem<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    /** The tinted band behind it -- off where the row sits in a band of its own already. */
    banded: Boolean = true,
    /** How tall each segment is. */
    segmentHeight: Dp = SELECTOR_HEIGHT,
    /** The labels' size; the element chips use the small one. */
    fontSize: TextUnit = MaterialTheme.typography.labelSmall.fontSize,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (banded) {
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                } else {
                    Modifier
                },
            ),
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
                        buttonHeight = segmentHeight,
                        fontSize = fontSize,
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

/** The pane that edits this category, showing [element]. */
@Composable
private fun CustomizePaneContent(
    pane: CustomizePane,
    element: CustomizeElement?,
    translationIndex: Int,
    draft: AppSettings,
    profile: OutputProfile,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onProfileFieldChange: (OutputProfile) -> Unit,
) {
    val shown = element ?: return
    // Keyed on what the column is pointed at. One set of controls stands for many stored profiles,
    // and without this Compose keeps the subtree across a switch and hands each control the state
    // -- and the write-back lambda -- of whichever control held its slot before. Picking the second
    // translation and typing a font size then wrote it to the first. The global Bible tab keys its
    // own panel for exactly this reason.
    key(pane, shown, translationIndex) {
        when (pane) {
            CustomizePane.BIBLE -> BibleCustomizePane(
                element = shown,
                translationIndex = translationIndex,
                settings = draft,
                profile = profile,
                onSettingsChange = onSettingsChange,
            )
            CustomizePane.SONGS -> SongCustomizePane(
                element = shown,
                settings = draft,
                onSettingsChange = onSettingsChange,
                songMode = profile.songMode,
                songTranslations = profile.songTranslations,
                onSongModeChange = { onProfileFieldChange(profile.copy(songMode = it)) },
            )
            CustomizePane.BACKGROUND -> BackgroundCustomizePane(
                element = shown,
                settings = draft,
                profile = profile,
                onSettingsChange = onSettingsChange,
                onProfileFieldChange = onProfileFieldChange,
            )
            // Handled by CustomizeControls, which gives each the whole width instead of this column.
            CustomizePane.STAGE_MONITOR,
            CustomizePane.CAPTIONS,
            CustomizePane.SUBTITLES,
            CustomizePane.QA,
            CustomizePane.DICTIONARY,
            -> Unit
        }
    }
}

/** Test handle for the row of element chips above the control column. */
internal const val CUSTOMIZE_ELEMENT_ROW_TAG = "customize_element_row"

/** Test handle for the Bible pane's translation selector. */
internal const val CUSTOMIZE_TRANSLATION_ROW_TAG = "customize_translation_row"

/** Test handle for one translation chip, by its position in the stack. */
internal fun translationChipTag(index: Int): String = "customize_translation_$index"
