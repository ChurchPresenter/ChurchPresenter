package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.animation_crossfade
import churchpresenter.composeapp.generated.resources.bilingual_layout
import churchpresenter.composeapp.generated.resources.bilingual_left_right
import churchpresenter.composeapp.generated.resources.bilingual_top_bottom
import churchpresenter.composeapp.generated.resources.bottom
import churchpresenter.composeapp.generated.resources.enabled
import churchpresenter.composeapp.generated.resources.end_of_song_spacing
import churchpresenter.composeapp.generated.resources.fade_in
import churchpresenter.composeapp.generated.resources.fade_out
import churchpresenter.composeapp.generated.resources.full_screen
import churchpresenter.composeapp.generated.resources.left
import churchpresenter.composeapp.generated.resources.lower_third_size
import churchpresenter.composeapp.generated.resources.milliseconds_suffix
import churchpresenter.composeapp.generated.resources.right
import churchpresenter.composeapp.generated.resources.number_before_title
import churchpresenter.composeapp.generated.resources.show_title
import churchpresenter.composeapp.generated.resources.show_number
import churchpresenter.composeapp.generated.resources.every_page
import churchpresenter.composeapp.generated.resources.first_page
import churchpresenter.composeapp.generated.resources.none
import churchpresenter.composeapp.generated.resources.show_song_number_before_title
import churchpresenter.composeapp.generated.resources.song_auto_repeat_chorus
import churchpresenter.composeapp.generated.resources.song_chunk
import churchpresenter.composeapp.generated.resources.song_chunk_line
import churchpresenter.composeapp.generated.resources.song_chunk_verse
import churchpresenter.composeapp.generated.resources.song_element_look_ahead
import churchpresenter.composeapp.generated.resources.song_element_lyrics
import churchpresenter.composeapp.generated.resources.song_element_next_section
import churchpresenter.composeapp.generated.resources.song_element_number
import churchpresenter.composeapp.generated.resources.song_element_title
import churchpresenter.composeapp.generated.resources.song_language_bilingual
import churchpresenter.composeapp.generated.resources.song_language_both
import churchpresenter.composeapp.generated.resources.song_language_primary
import churchpresenter.composeapp.generated.resources.song_language_scope
import churchpresenter.composeapp.generated.resources.song_language_secondary
import churchpresenter.composeapp.generated.resources.song_language_single
import churchpresenter.composeapp.generated.resources.song_languages
import churchpresenter.composeapp.generated.resources.song_lyrics_layout
import churchpresenter.composeapp.generated.resources.song_preview_label
import churchpresenter.composeapp.generated.resources.song_preview_look_ahead
import churchpresenter.composeapp.generated.resources.song_title_slide
import churchpresenter.composeapp.generated.resources.song_transition_and_markers
import churchpresenter.composeapp.generated.resources.text_margins
import churchpresenter.composeapp.generated.resources.top
import churchpresenter.composeapp.generated.resources.transition_duration
import churchpresenter.composeapp.generated.resources.vertical_alignment
import churchpresenter.composeapp.generated.resources.word_wrap
import org.churchpresenter.app.churchpresenter.composables.LabeledCheckbox
import org.churchpresenter.app.churchpresenter.composables.LabeledControl
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.LocalSegmentedButtonTone
import org.churchpresenter.app.churchpresenter.composables.SegmentedButton
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonTone
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbar
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbarGutter
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.app.churchpresenter.composables.SlimSlider
import org.churchpresenter.app.churchpresenter.composables.VerticalAlignmentButtons
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.isLiveOutput
import org.churchpresenter.app.churchpresenter.utils.rememberSystemFonts
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

/** The rail is a fixed column of cards; the styling side takes whatever is left. */
private val RAIL_MIN_WIDTH = 260.dp
private val RAIL_MAX_WIDTH = 360.dp

private const val MARGIN_MAX = 500
private const val END_OF_SONG_MAX = 20
private const val TRANSITION_MIN_MS = 100f
private const val TRANSITION_MAX_MS = 2000f
private const val TRANSITION_STEP_MS = 50f

private val TARGET_BUTTON_WIDTH = 110.dp
private val ELEMENT_TAB_WIDTH = 104.dp
private val SCOPE_BUTTON_WIDTH = 82.dp

/** Five tabs are wider than a narrowed dialog's styling pane, so past that they fold onto two rows. */
private const val ELEMENT_TAB_COMPACT_COLUMNS = 3

/**
 * The Song tab of the settings dialog.
 *
 * A song slide draws five things -- its number, its title, the lyrics, the look-ahead line and the
 * next-section marker -- and each carries a full appearance profile on each of the two outputs. Ten
 * profiles laid out as columns of controls is more scrolling than anyone can hold in their head, so
 * the tab keeps one set of controls and two selectors above them -- which element, which output --
 * and shows what the current selection actually looks like in the preview between them.
 *
 * The rail on the left holds what belongs to the slide as a whole rather than to any one element:
 * whether there is a title slide, how the lyrics are laid out, how the slide arrives and leaves,
 * and the margins it sits inside.
 */
@Composable
fun SongSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    presenterManager: PresenterManager? = null,
) {
    val availableFonts = rememberSystemFonts()
    var target by remember { mutableStateOf(SongStyleTarget.FULL_SCREEN) }
    var element by remember { mutableStateOf(SongStyleElement.LYRICS) }
    var showLookAhead by remember { mutableStateOf(false) }

    // The rail scrolls on its own rather than the tab scrolling as a whole: four cards do not fit
    // the dialog's height on a small laptop, and when the whole Row scrolled they took the preview
    // and the controls it illustrates down below the fold with them.
    val scrollState = rememberScrollState()
    // The Bible and Song tabs fill the selected segment with the accent, matching the song
    // editor's pane tabs. The provider covers the whole tab, so the rail, the typography panels
    // and the shared preview rows all agree without threading a colour through any of them.
    CompositionLocalProvider(LocalSegmentedButtonTone provides SegmentedButtonTone.ACCENT) {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),
        ) {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.widthIn(min = RAIL_MIN_WIDTH, max = RAIL_MAX_WIDTH).fillMaxHeight()) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .verticalScroll(scrollState)
                            .padding(end = SettingsScrollbarGutter),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SongTitleSlideSection(settings, onSettingsChange)
                        SongLyricsLayoutSection(settings, onSettingsChange)
                        SongTransitionSection(settings, onSettingsChange)
                        LowerThirdHeightSection(
                            percent = settings.songSettings.lowerThirdHeightPercent,
                            onPercentChange = { percent ->
                                onSettingsChange { s ->
                                    s.copy(songSettings = s.songSettings.copy(lowerThirdHeightPercent = percent))
                                }
                            },
                        )
                        SongMarginsSection(settings, onSettingsChange)
                    }
                    SettingsScrollbar(scrollState)
                }
                SongStylePane(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    target = target,
                    onTargetChange = { target = it },
                    element = element,
                    onElementChange = { element = it },
                    showLookAhead = showLookAhead,
                    onShowLookAheadChange = { showLookAhead = it },
                    availableFonts = availableFonts,
                    presenterManager = presenterManager,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

/** Whether a song opens with a slide naming it, and whether that slide carries its number. */
@Composable
private fun SongTitleSlideSection(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    SettingsSection(title = stringResource(Res.string.song_title_slide)) {
        LabeledCheckbox(
            checked = settings.songSettings.titleSlideEnabled,
            onCheckedChange = { on ->
                onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleSlideEnabled = on)) }
            },
            controlModifier = Modifier.size(24.dp),
            label = stringResource(Res.string.enabled),
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).testTag("song_titleSlideEnabled"),
            style = MaterialTheme.typography.bodyMedium,
        )
        LabeledCheckbox(
            checked = settings.songSettings.titleSlideShowSongNumber,
            onCheckedChange = { on ->
                onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleSlideShowSongNumber = on)) }
            },
            enabled = settings.songSettings.titleSlideEnabled,
            controlModifier = Modifier.size(24.dp),
            label = stringResource(Res.string.show_song_number_before_title),
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).testTag("song_titleSlideShowSongNumber"),
            style = MaterialTheme.typography.bodyMedium,
            color = if (settings.songSettings.titleSlideEnabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
    }
}

/** How the lyrics sit on the slide, and how many languages they are shown in. */
@Composable
private fun SongLyricsLayoutSection(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val song = settings.songSettings
    // Written to each output's own song mode rather than to SongSettings: the song-level language
    // fields are overridden by that mode at every real call site, so a control writing them would
    // restrict nothing. See SongOutputLanguage.kt.
    val bilingual = settings.songIsBilingual
    SettingsSection(title = stringResource(Res.string.song_lyrics_layout)) {
        LabeledCheckbox(
            checked = song.wordWrap,
            onCheckedChange = { on ->
                onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(wordWrap = on)) }
            },
            controlModifier = Modifier.size(24.dp),
            label = stringResource(Res.string.word_wrap),
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        // Off means "present the sections as the file has them", which is the only way to place a
        // chorus deliberately — before verse 1, or after verse 2 alone.
        LabeledCheckbox(
            checked = song.autoRepeatChorus,
            onCheckedChange = { on ->
                onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(autoRepeatChorus = on)) }
            },
            controlModifier = Modifier.size(24.dp),
            label = stringResource(Res.string.song_auto_repeat_chorus),
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).testTag("song_autoRepeatChorus"),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(Res.string.vertical_alignment).removeSuffix(":"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            VerticalAlignmentButtons(
                selectedAlignment = song.lyricsAlignment,
                onAlignmentChange = { value ->
                    onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsAlignment = value)) }
                },
                topValue = Constants.TOP,
                middleValue = Constants.MIDDLE,
                bottomValue = Constants.BOTTOM,
            )
        }
        ControlColumn(stringResource(Res.string.song_languages), Modifier.fillMaxWidth()) {
            SegmentedButton(
                items = listOf(
                    SegmentedButtonItem(false, stringResource(Res.string.song_language_single)),
                    SegmentedButtonItem(true, stringResource(Res.string.song_language_bilingual)),
                ),
                selectedValue = bilingual,
                onValueChange = { wantsBoth ->
                    onSettingsChange { s -> s.withSongBilingual(wantsBoth) }
                },
                buttonWidth = 120.dp,
                buttonHeight = 32.dp,
                fontSize = MaterialTheme.typography.labelSmall.fontSize,
            )
        }
        // Only meaningful with two languages on screen, so it follows the switch above rather than
        // standing there offering a choice that changes nothing.
        if (bilingual) {
            ControlColumn(stringResource(Res.string.bilingual_layout), Modifier.fillMaxWidth()) {
                SegmentedButton(
                    items = listOf(
                        SegmentedButtonItem(
                            Constants.BILINGUAL_SIDE_BY_SIDE,
                            stringResource(Res.string.bilingual_left_right),
                        ),
                        SegmentedButtonItem(
                            Constants.BILINGUAL_TOP_BOTTOM,
                            stringResource(Res.string.bilingual_top_bottom),
                        ),
                    ),
                    selectedValue = song.bilingualLayout,
                    onValueChange = { value ->
                        onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(bilingualLayout = value)) }
                    },
                    buttonWidth = 120.dp,
                    buttonHeight = 32.dp,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                )
            }
        }
    }
}

/** How a slide arrives and leaves, and how far the end-of-song marker sits from the last line. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SongTransitionSection(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val song = settings.songSettings
    val msSuffix = stringResource(Res.string.milliseconds_suffix)
    SettingsSection(title = stringResource(Res.string.song_transition_and_markers)) {
        ControlColumn(stringResource(Res.string.transition_duration), Modifier.fillMaxWidth()) {
            SlimSlider(
                value = song.transitionDuration,
                onValueChange = { raw ->
                    val snapped = (raw / TRANSITION_STEP_MS).toInt() * TRANSITION_STEP_MS
                    onSettingsChange { s ->
                        s.copy(songSettings = s.songSettings.copy(transitionDuration = snapped))
                    }
                },
                valueRange = TRANSITION_MIN_MS..TRANSITION_MAX_MS,
                modifier = Modifier.fillMaxWidth(),
                trailingLabel = "${song.transitionDuration.toInt()}$msSuffix",
            )
        }
        // Wraps rather than one hard row: "Fade In / Fade Out / Crossfade" is three short
        // labels in English and three long ones in most translations -- in Russian the first
        // two took the whole width and squeezed the third into a single-character column,
        // which drew its label vertically.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            LabeledCheckbox(
                checked = song.fadeIn,
                onCheckedChange = {
                    onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(fadeIn = it)) }
                },
                controlModifier = Modifier.size(24.dp),
                label = stringResource(Res.string.fade_in),
                style = MaterialTheme.typography.bodySmall,
            )
            LabeledCheckbox(
                checked = song.fadeOut,
                onCheckedChange = {
                    onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(fadeOut = it)) }
                },
                controlModifier = Modifier.size(24.dp),
                label = stringResource(Res.string.fade_out),
                style = MaterialTheme.typography.bodySmall,
            )
            LabeledCheckbox(
                checked = song.crossfade,
                onCheckedChange = {
                    onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(crossfade = it)) }
                },
                controlModifier = Modifier.size(24.dp),
                label = stringResource(Res.string.animation_crossfade),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(Res.string.end_of_song_spacing).removeSuffix(":"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            NumberSettingsTextField(
                initialText = song.endOfSongIndicatorSpacing,
                onValueChange = { value ->
                    onSettingsChange { s ->
                        s.copy(songSettings = s.songSettings.copy(endOfSongIndicatorSpacing = value))
                    }
                },
                range = 0..END_OF_SONG_MAX,
            )
        }
    }
}

/** The four margins, as a plain grid; the preview above shows what they do to the text. */
@Composable
private fun SongMarginsSection(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val song = settings.songSettings
    fun field(label: String, value: Int, apply: (SongSettings, Int) -> SongSettings): @Composable () -> Unit = {
        ControlColumn(label) {
            NumberSettingsTextField(
                modifier = Modifier.fillMaxWidth(),
                initialText = value,
                onValueChange = { typed ->
                    onSettingsChange { s -> s.copy(songSettings = apply(s.songSettings, typed)) }
                },
                range = 0..MARGIN_MAX,
            )
        }
    }
    SettingsSection(title = stringResource(Res.string.text_margins)) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    field(stringResource(Res.string.top), song.marginTop) { s, v -> s.copy(marginTop = v) }()
                }
                Box(Modifier.weight(1f)) {
                    field(stringResource(Res.string.left), song.marginLeft) { s, v -> s.copy(marginLeft = v) }()
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    field(stringResource(Res.string.right), song.marginRight) { s, v -> s.copy(marginRight = v) }()
                }
                Box(Modifier.weight(1f)) {
                    field(stringResource(Res.string.bottom), song.marginBottom) { s, v -> s.copy(marginBottom = v) }()
                }
            }
        }
    }
}

/** The output switch, the preview, the element tabs and the controls under them. */
@Composable
private fun SongStylePane(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    target: SongStyleTarget,
    onTargetChange: (SongStyleTarget) -> Unit,
    element: SongStyleElement,
    onElementChange: (SongStyleElement) -> Unit,
    showLookAhead: Boolean,
    onShowLookAheadChange: (Boolean) -> Unit,
    availableFonts: List<String>,
    presenterManager: PresenterManager?,
    modifier: Modifier = Modifier,
) {
    var sampleSlot by remember { mutableStateOf(PreviewSampleSlot.MEDIUM) }
    var previewOnScreen by remember { mutableStateOf(false) }
    val sampleSections = songSampleSections(sampleSlot)
    // The look-ahead and next-section elements only appear on a look-ahead slide, so selecting one
    // turns the preview's look-ahead on whatever the checkbox says. Editing a control whose effect
    // is not on screen is the thing this tab exists to stop.
    val previewLookAhead = showLookAhead ||
        element == SongStyleElement.LOOK_AHEAD ||
        element == SongStyleElement.NEXT_SECTION
    // Somewhere to put it. Not gated on the *mode* of that output: the preview switches every live
    // one to whichever the tab is styling for its duration, so a hall with a single full-screen
    // projector can still be shown what its lower third would look like.
    val hasOutputForTarget = settings.projectionSettings.screenAssignments.any { it.isLiveOutput() }
    OnScreenPreviewEffect(
        active = previewOnScreen,
        settings = settings,
        presenterManager = presenterManager,
        outputs = PreviewOutputState(
            lowerThird = target.isLowerThird,
            songLookAhead = previewLookAhead,
            // No chart: only a stage monitor draws one, and this preview is a projection output.
            showChords = false,
        ),
        contentKey = sampleSections,
    ) { manager ->
        manager.setAllLyricSections(sampleSections)
        manager.setSongDisplaySectionIndex(0)
        manager.setSongDisplayLineIndex(-1)
        manager.setLyricSection(sampleSections.first())
        manager.setDisplayedLyricSection(sampleSections.first())
        manager.setPresentingMode(Presenting.LYRICS)
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SongTargetSwitchRow(
            settings = settings,
            target = target,
            onTargetChange = onTargetChange,
            showLookAhead = previewLookAhead,
            onShowLookAheadChange = onShowLookAheadChange,
            lookAheadForced = previewLookAhead && !showLookAhead,
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SongPreviewPanel(
                settings = settings,
                target = target,
                showLookAhead = previewLookAhead,
                showChords = false,
                sections = sampleSections,
                modifier = Modifier.width(
                    minOf(
                        maxWidth,
                        SETTINGS_PREVIEW_MAX_HEIGHT * previewOutputSize(settings).aspectRatio,
                    ),
                ),
            )
        }
        SettingsPreviewSampleRow(
            slot = sampleSlot,
            onSlotChange = { sampleSlot = it },
            onScreen = previewOnScreen,
            onScreenChange = { previewOnScreen = it },
            onScreenEnabled = presenterManager != null && hasOutputForTarget,
        )
        SongEditingCard(
            settings = settings,
            onSettingsChange = onSettingsChange,
            element = element,
            onElementChange = onElementChange,
            target = target,
            availableFonts = availableFonts,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

/** Which output is being styled, what the preview draws, and the output's own size. */
@Composable
private fun SongTargetSwitchRow(
    settings: AppSettings,
    target: SongStyleTarget,
    onTargetChange: (SongStyleTarget) -> Unit,
    showLookAhead: Boolean,
    onShowLookAheadChange: (Boolean) -> Unit,
    /** On because the selected element needs it, so the box is shown ticked and left alone. */
    lookAheadForced: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SegmentedButton(
            items = listOf(
                SegmentedButtonItem(SongStyleTarget.FULL_SCREEN, stringResource(Res.string.full_screen)),
                SegmentedButtonItem(SongStyleTarget.LOWER_THIRD, stringResource(Res.string.lower_third_size)),
            ),
            selectedValue = target,
            onValueChange = onTargetChange,
            buttonWidth = TARGET_BUTTON_WIDTH,
            buttonHeight = 34.dp,
            fontSize = MaterialTheme.typography.labelLarge.fontSize,
        )
        // For the picture only. Not stored: it decides what this preview draws, not what the output
        // shows, which is settled per slide by the song and the schedule.
        Text(
            text = stringResource(Res.string.song_preview_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LabeledCheckbox(
            checked = showLookAhead,
            onCheckedChange = onShowLookAheadChange,
            enabled = !lookAheadForced,
            label = stringResource(Res.string.song_preview_look_ahead),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = songScopeNote(settings, target),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** The element tabs, and the two things the lyrics carry that the other elements do not. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SongElementRow(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    element: SongStyleElement,
    onElementChange: (SongStyleElement) -> Unit,
    target: SongStyleTarget,
) {
    val song = settings.songSettings
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SegmentedButton(
            items = listOf(
                SegmentedButtonItem(SongStyleElement.NUMBER, stringResource(Res.string.song_element_number)),
                SegmentedButtonItem(SongStyleElement.TITLE, stringResource(Res.string.song_element_title)),
                SegmentedButtonItem(SongStyleElement.LYRICS, stringResource(Res.string.song_element_lyrics)),
                SegmentedButtonItem(SongStyleElement.LOOK_AHEAD, stringResource(Res.string.song_element_look_ahead)),
                SegmentedButtonItem(
                    SongStyleElement.NEXT_SECTION,
                    stringResource(Res.string.song_element_next_section),
                ),
            ),
            selectedValue = element,
            onValueChange = onElementChange,
            buttonWidth = ELEMENT_TAB_WIDTH,
            buttonHeight = 34.dp,
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
            compactColumns = ELEMENT_TAB_COMPACT_COLUMNS,
        )
        Spacer(Modifier.weight(1f))
    }
    // How much of the song a slide holds, and which languages it shows. Both belong to the output
    // rather than to an element, so they sit under the tabs rather than in the grid -- and on a row
    // of their own, because five element tabs plus both of these is wider than the pane and left
    // them crushed to a column of single letters.
    //
    // Flowing rather than a hard row: the two labelled groups are wider than a narrow pane, and a
    // `Row` clips rather than wraps, so the last option lost its right-hand half ("Secondary" drawn
    // as "Seco") with nothing to say the control continued past the edge. Each label is wrapped
    // with its own control so the pair moves as one -- flowing them separately puts a lone "Lang"
    // at the end of the first line and its buttons at the start of the next.
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LabeledControl(stringResource(Res.string.song_chunk)) {
        SegmentedButton(
            items = listOf(
                SegmentedButtonItem(Constants.SONG_DISPLAY_MODE_VERSE, stringResource(Res.string.song_chunk_verse)),
                SegmentedButtonItem(Constants.SONG_DISPLAY_MODE_LINE, stringResource(Res.string.song_chunk_line)),
            ),
            selectedValue = song.chunkFor(element, target),
            onValueChange = { mode ->
                onSettingsChange { s -> s.copy(songSettings = s.songSettings.withChunk(element, target, mode)) }
            },
            buttonWidth = SCOPE_BUTTON_WIDTH,
            buttonHeight = 30.dp,
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
        )
        }
        LabeledControl(stringResource(Res.string.song_language_scope)) {
        SegmentedButton(
            items = listOf(
                SegmentedButtonItem(Constants.SONG_LANG_BOTH, stringResource(Res.string.song_language_both)),
                SegmentedButtonItem(Constants.SONG_LANG_PRIMARY, stringResource(Res.string.song_language_primary)),
                SegmentedButtonItem(Constants.SONG_LANG_SECONDARY, stringResource(Res.string.song_language_secondary)),
            ),
            selectedValue = settings.songLanguageFor(target),
            onValueChange = { lang ->
                onSettingsChange { s -> s.withSongLanguage(target, lang) }
            },
            buttonWidth = SCOPE_BUTTON_WIDTH,
            buttonHeight = 30.dp,
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
        )
        }
    }
    SongAppearanceRow(settings, onSettingsChange, element, target)
}


/**
 * When the number or the title appears on [target]'s output, and which of the two leads.
 *
 * Absent for the other three elements: the lyrics *are* the slide, and the look-ahead lines follow
 * whether the output has a look-ahead at all -- so there is nothing here for them to answer, and a
 * control that writes nowhere is worse than no control.
 *
 * This is the pair of settings the tab's rewrite dropped. The columns that used to hold them were
 * left in the tree unreferenced, so the song number kept appearing on the lower third with nothing
 * anywhere in settings to turn it off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SongAppearanceRow(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    element: SongStyleElement,
    target: SongStyleTarget,
) {
    val show = settings.songSettings.showFor(element, target) ?: return
    // Flowing for the same reason as the chunk/language row above, and it matters most here: this
    // row exists only for the Number and Title elements, so those two were the only ones that ran
    // off the right edge of a narrow pane -- three options plus the ordering checkbox is the widest
    // line the card ever draws.
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LabeledControl(
            stringResource(
                if (element == SongStyleElement.NUMBER) Res.string.show_number else Res.string.show_title,
            ),
        ) {
        SegmentedButton(
            items = listOf(
                SegmentedButtonItem(Constants.NONE, stringResource(Res.string.none)),
                SegmentedButtonItem(Constants.FIRST_PAGE, stringResource(Res.string.first_page)),
                SegmentedButtonItem(Constants.EVERY_PAGE, stringResource(Res.string.every_page)),
            ),
            selectedValue = show,
            onValueChange = { value ->
                onSettingsChange { s ->
                    s.copy(songSettings = s.songSettings.withShow(element, target, value))
                }
            },
            buttonWidth = SCOPE_BUTTON_WIDTH,
            buttonHeight = 30.dp,
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
            modifier = Modifier.testTag("song_show_${element.name.lowercase()}"),
        )
        }
        // Only where the two share a position, which is the only case in which their order is a
        // question at all -- elsewhere the slide's own layout already answers it.
        if (element == SongStyleElement.NUMBER && settings.songSettings.numberSharesTitlePosition(target)) {
            LabeledCheckbox(
                checked = settings.songSettings.songNumberBeforeTitle,
                onCheckedChange = { on ->
                    onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(songNumberBeforeTitle = on)) }
                },
                label = stringResource(Res.string.number_before_title),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("song_songNumberBeforeTitle"),
            )
        }
    }
}

/**
 * Rounds the outer corners of a segmented row so its buttons read as one control.
 *
 * Kept here rather than moved with the tab's rewrite: the stage monitor's layout picker and the
 * song columns this tab replaced both still call it, and its test resolves it through this file.
 */
internal fun segmentedItemShape(index: Int, count: Int): Shape {
    val r = 4.dp
    return when {
        count == 1 -> RoundedCornerShape(r)
        index == 0 -> RoundedCornerShape(topStart = r, bottomStart = r, topEnd = 0.dp, bottomEnd = 0.dp)
        index == count - 1 -> RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = r, bottomEnd = r)
        else -> RoundedCornerShape(0.dp)
    }
}
