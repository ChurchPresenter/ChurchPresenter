package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import churchpresenter.composeapp.generated.resources.bottom
import churchpresenter.composeapp.generated.resources.enabled
import churchpresenter.composeapp.generated.resources.full_screen
import churchpresenter.composeapp.generated.resources.lower_third_size
import churchpresenter.composeapp.generated.resources.number_before_title
import churchpresenter.composeapp.generated.resources.show_title
import churchpresenter.composeapp.generated.resources.show_number
import churchpresenter.composeapp.generated.resources.every_page
import churchpresenter.composeapp.generated.resources.first_page
import churchpresenter.composeapp.generated.resources.none
import churchpresenter.composeapp.generated.resources.show_song_number_before_title
import churchpresenter.composeapp.generated.resources.song_chunk
import churchpresenter.composeapp.generated.resources.song_chunk_line
import churchpresenter.composeapp.generated.resources.song_chunk_verse
import churchpresenter.composeapp.generated.resources.song_show_on_title_slide
import churchpresenter.composeapp.generated.resources.song_target_title_slide
import churchpresenter.composeapp.generated.resources.song_language_scope
import churchpresenter.composeapp.generated.resources.song_number_corner
import churchpresenter.composeapp.generated.resources.song_number_offset_x
import churchpresenter.composeapp.generated.resources.song_number_offset_y
import churchpresenter.composeapp.generated.resources.song_preview_label
import churchpresenter.composeapp.generated.resources.song_preview_look_ahead
import org.churchpresenter.theme.components.DropdownSelector
import org.churchpresenter.app.churchpresenter.composables.LabeledCheckbox
import org.churchpresenter.app.churchpresenter.composables.LabeledControl
import org.churchpresenter.app.churchpresenter.composables.LocalSegmentedButtonTone
import org.churchpresenter.app.churchpresenter.composables.SegmentedButton
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonTone
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbar
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbarGutter
import org.churchpresenter.app.churchpresenter.composables.SliderNumberField
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.rememberSystemFonts
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongNumberOffset
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource
import java.io.File

/** The positions of the switch above the preview: the title slide, or one of the two outputs. */
private enum class StyleSwitch { TITLE_SLIDE, FULL_SCREEN, LOWER_THIRD }

/** The rail is a fixed column of cards; the styling side takes whatever is left. */
private val RAIL_MIN_WIDTH = 260.dp
private val RAIL_MAX_WIDTH = 360.dp

private val TARGET_BUTTON_WIDTH = 110.dp

/** Narrower than the output switch: two words, and the row already carries three controls. */
private val LANGUAGE_BUTTON_WIDTH = 92.dp
/** How a row reads while the title slide is off: present, but plainly not in charge of anything. */
private val ELEMENT_TAB_WIDTH = 104.dp
private val SCOPE_BUTTON_WIDTH = 82.dp

/** Wide enough for "Bottom Right" and the chevron, so no corner reads ellipsized. */
private val CORNER_DROPDOWN_WIDTH = 118.dp
private val NUMBER_OFFSET_FIELD_WIDTH = 56.dp

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
    /** Where the band generator saves; null hides its button and leaves the picker. */
    bibleLowerThirdsDir: File? = null,
) {
    val availableFonts = rememberSystemFonts()
    var target by remember { mutableStateOf(SongStyleTarget.FULL_SCREEN) }
    var element by remember { mutableStateOf(SongStyleElement.LYRICS) }
    // Which language of a bilingual song is being styled. Only the lyrics have two, so picking the
    // second narrows the element strip to them -- see `SongElementRow`.
    var language by remember { mutableStateOf(SongStyleLanguage.PRIMARY) }
    var showLookAhead by remember { mutableStateOf(false) }
    // Styling the title slide rather than the lyric slides -- for whichever output `target` names.
    // A view over the same two outputs, not a third one: the title slide is drawn on the band as
    // well as on the screen, with its own profiles for each. Only offered while there is a title
    // slide at all, so switching it off in the rail drops the tab back onto the lyric slides.
    var titleSlideView by remember { mutableStateOf(false) }
    val onTitleSlide = titleSlideView && settings.songSettings.titleSlideEnabled
    // A song with one language on screen has no second profile to style, so that drops the switch
    // and the panel back onto the first. The title slide keeps it: it draws *both* titles when the
    // output shows both languages, and each has a profile of its own.
    val bilingual = settings.songIsBilingual
    val styleLanguages = songStyleLanguages()
    val editingLanguage = if (bilingual && language in styleLanguages) language else SongStyleLanguage.PRIMARY

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
                        LowerThirdAnimationSection(
                            settings, onSettingsChange, bibleLowerThirdsDir, scope = BackgroundScope.SONG_LOWER_THIRD,
                        )
                        SongMarginsSection(settings, onSettingsChange)
                        ContentRegionSection(
                            region = settings.songSettings.layoutExtras.contentRegion,
                            onRegionChange = { region ->
                                onSettingsChange { s ->
                                    s.copy(
                                        songSettings = s.songSettings.copy(
                                            layoutExtras = s.songSettings.layoutExtras.copy(contentRegion = region),
                                        ),
                                    )
                                }
                            },
                        )
                        SongSectionLabelSection(settings, onSettingsChange)
                    }
                    SettingsScrollbar(scrollState)
                }
                SongStylePane(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    target = target,
                    onTargetChange = {
                        target = it
                        titleSlideView = false
                        // A credit has no lyric-slide profile to fall back to.
                        if (element.isCredit) element = SongStyleElement.LYRICS
                    },
                    element = element,
                    onElementChange = { element = it },
                    language = editingLanguage,
                    onLanguageChange = { picked ->
                        language = picked
                        // The second language is the lyrics and the title and nothing else; landing
                        // on it while the Number tab was selected would show a strip with nowhere to
                        // be. The title slide has no lyrics, so there it is the title.
                        if (picked.isTranslation) {
                            // The title slide has no lyrics, so there the second language is the
                            // title alone.
                            val offered = if (onTitleSlide) {
                                listOf(SongStyleElement.TITLE)
                            } else {
                                SECOND_LANGUAGE_ELEMENTS
                            }
                            if (element !in offered) element = offered.first()
                        }
                    },
                    bilingual = bilingual,
                    titleSlideView = onTitleSlide,
                    onTitleSlideView = {
                        titleSlideView = true
                        if (!element.onTitleSlide) element = SongStyleElement.TITLE
                    },
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

/** The output switch, the preview, the element tabs and the controls under them. */
@Composable
private fun SongStylePane(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    target: SongStyleTarget,
    onTargetChange: (SongStyleTarget) -> Unit,
    element: SongStyleElement,
    onElementChange: (SongStyleElement) -> Unit,
    language: SongStyleLanguage,
    onLanguageChange: (SongStyleLanguage) -> Unit,
    bilingual: Boolean,
    titleSlideView: Boolean,
    onTitleSlideView: () -> Unit,
    showLookAhead: Boolean,
    onShowLookAheadChange: (Boolean) -> Unit,
    availableFonts: List<String>,
    presenterManager: PresenterManager?,
    modifier: Modifier = Modifier,
) {
    var sampleSlot by remember { mutableStateOf(PreviewSampleSlot.MEDIUM) }
    var previewOnScreen by remember { mutableStateOf(false) }
    val lyricSections = songSampleSections(sampleSlot)
    // The look-ahead and next-section elements only appear on a look-ahead slide, so selecting one
    // turns the preview's look-ahead on whatever the checkbox says. Editing a control whose effect
    // is not on screen is the thing this tab exists to stop. A title slide has no look-ahead.
    val previewLookAhead = !titleSlideView && (
        showLookAhead ||
            element == SongStyleElement.LOOK_AHEAD ||
            element == SongStyleElement.NEXT_SECTION
        )
    // Somewhere to put it. Not gated on the *mode* of that output: the preview switches every live
    // one to whichever the tab is styling for its duration, so a hall with a single full-screen
    // projector can still be shown what its lower third would look like. `hasAnyOpenOutput` counts a
    // single-monitor dev machine's dev-fallback window too -- see its own doc comment.
    val hasOutputForTarget = hasAnyOpenOutput(settings)
    // What the preview is handed, which is not quite what is stored: an element switched off is
    // turned on while its own tab is selected, so styling it is never styling something invisible.
    // See `shownForPreview`. Everything below still reads `settings` -- this copy is for the
    // picture only, exactly as `previewLookAhead` is.
    val previewSettings = remember(settings, element, target, titleSlideView) {
        val song = if (titleSlideView) {
            settings.songSettings.shownOnTitleSlideForPreview(element)
        } else {
            settings.songSettings.shownForPreview(element, target)
        }
        settings.copy(songSettings = song)
    }
    // The title slide in front of the lyric sections, exactly as the songs tab sends a song out --
    // built from the preview's own copy of the settings so the element being styled is on it.
    val sampleSections = if (titleSlideView) {
        listOf(titleSlideSample(previewSettings.songSettings, sampleSlot)) + lyricSections
    } else {
        lyricSections
    }
    OnScreenPreviewEffect(
        active = previewOnScreen,
        settings = previewSettings,
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
            language = language,
            onLanguageChange = onLanguageChange,
            bilingual = bilingual,
            titleSlideView = titleSlideView,
            onTitleSlideView = onTitleSlideView,
            showLookAhead = previewLookAhead,
            onShowLookAheadChange = onShowLookAheadChange,
            lookAheadForced = previewLookAhead && !showLookAhead,
        )
        SongPreviewWithOutputPicker(
            settings = settings,
            onSettingsChange = onSettingsChange,
            target = target,
            bilingual = bilingual,
            previewSettings = previewSettings,
            previewLookAhead = previewLookAhead,
            sampleSections = sampleSections,
            titleSlideView = titleSlideView,
        )
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
            language = language,
            availableFonts = availableFonts,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            titleSlideView = titleSlideView,
        )
    }
}

/** Which output is being styled, what the preview draws, and the output's own size. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SongTargetSwitchRow(
    settings: AppSettings,
    target: SongStyleTarget,
    onTargetChange: (SongStyleTarget) -> Unit,
    language: SongStyleLanguage,
    onLanguageChange: (SongStyleLanguage) -> Unit,
    bilingual: Boolean,
    titleSlideView: Boolean,
    onTitleSlideView: () -> Unit,
    showLookAhead: Boolean,
    onShowLookAheadChange: (Boolean) -> Unit,
    /** On because the selected element needs it, so the box is shown ticked and left alone. */
    lookAheadForced: Boolean,
) {
    // Flowing rather than a hard row: four language buttons beside the output switch and the preview
    // controls are wider than a narrow pane, and a Row clips what does not fit.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        itemVerticalAlignment = Alignment.CenterVertically,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // The title slide, when there is one, ahead of the two outputs: a view of the output that
        // is selected on the right, not a third output, which the divider is there to say. While
        // it is the view, neither output reads as selected -- the scope note at the end of the row
        // names which one the slide is being styled for.
        // One switch of three: the title slide, when there is one, ahead of the two outputs. The
        // slide is a view of the output selected beside it, not a third output -- while it is the
        // view neither output reads as selected, and the scope note at the end of the row names
        // which one the slide is being styled for.
        SegmentedButton(
            items = listOfNotNull(
                SegmentedButtonItem(StyleSwitch.TITLE_SLIDE, stringResource(Res.string.song_target_title_slide))
                    .takeIf { settings.songSettings.titleSlideEnabled },
                SegmentedButtonItem(StyleSwitch.FULL_SCREEN, stringResource(Res.string.full_screen)),
                SegmentedButtonItem(StyleSwitch.LOWER_THIRD, stringResource(Res.string.lower_third_size)),
            ),
            selectedValue = when {
                titleSlideView -> StyleSwitch.TITLE_SLIDE
                target.isLowerThird -> StyleSwitch.LOWER_THIRD
                else -> StyleSwitch.FULL_SCREEN
            },
            onValueChange = { picked ->
                when (picked) {
                    StyleSwitch.TITLE_SLIDE -> onTitleSlideView()
                    StyleSwitch.FULL_SCREEN -> onTargetChange(SongStyleTarget.FULL_SCREEN)
                    StyleSwitch.LOWER_THIRD -> onTargetChange(SongStyleTarget.LOWER_THIRD)
                }
            },
            buttonWidth = TARGET_BUTTON_WIDTH,
            buttonHeight = 34.dp,
            fontSize = MaterialTheme.typography.labelLarge.fontSize,
            modifier = Modifier.testTag("song_style_switch"),
        )
        // Which language is being styled, in the place the Bible tab puts its translation chips and
        // for the same reason: a second profile needs somewhere to be selected before it can be
        // edited. Only with two languages on screen -- with one, there is no second profile to
        // reach, and a title slide draws one title whatever the lyrics do.
        //
        // "1st / 2nd" rather than "Primary / Secondary", which is what the Lang row below the
        // element tabs already says. That row picks the languages the *output shows*; this one picks
        // whose *look* is being edited, and sharing a word left the tab with two controls reading
        // "Secondary" that answer different questions.
        if (bilingual && !titleSlideView) {
            SegmentedButton(
                items = songStyleLanguages().map { SegmentedButtonItem(it, it.ordinalLabel()) },
                selectedValue = language,
                onValueChange = onLanguageChange,
                buttonWidth = LANGUAGE_BUTTON_WIDTH,
                buttonHeight = 34.dp,
                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                modifier = Modifier.testTag("song_language_switch"),
            )
        }
        // For the picture only. Not stored: it decides what this preview draws, not what the output
        // shows, which is settled per slide by the song and the schedule. A title slide has no
        // look-ahead, so the switch goes with it.
        if (!titleSlideView) {
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
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = songScopeNote(settings, target, titleSlideView),
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
    /** Which language is being styled; the second is the lyrics and nothing else. */
    language: SongStyleLanguage = SongStyleLanguage.PRIMARY,
    /**
     * The title slide's elements rather than the lyric slides'. What sits under the tabs changes
     * with it: the title slide has no chunk, no language scope and no first-page/every-page
     * question, only whether each element is on it at all.
     */
    titleSlideView: Boolean = false,
) {
    val song = settings.songSettings
    // The lyrics and the title are the two things a bilingual song carries twice, so those are the
    // tabs the second language offers -- and on the title slide, which has no lyrics, just the
    // title. Kept rather than hidden: the row is where the panel says what it is pointed at, and a
    // strip that vanished on one switch and came back on the other reads as a glitch.
    val elements = when {
        language.isTranslation && titleSlideView -> listOf(SongStyleElement.TITLE)
        language.isTranslation -> SECOND_LANGUAGE_ELEMENTS
        titleSlideView -> TITLE_SLIDE_ELEMENTS
        else -> LYRIC_SLIDE_ELEMENTS
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SegmentedButton(
            items = elements.map { SegmentedButtonItem(it, it.label()) },
            selectedValue = element,
            onValueChange = onElementChange,
            buttonWidth = ELEMENT_TAB_WIDTH,
            buttonHeight = 34.dp,
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
            compactColumns = ELEMENT_TAB_COMPACT_COLUMNS,
        )
        Spacer(Modifier.weight(1f))
    }
    SongElementOptions(settings, onSettingsChange, element, target, language, titleSlideView)
}

/**
 * Everything under the element chips that belongs to the selected element -- what a slide holds,
 * which languages it shows, when the number and the title appear and where the number sits.
 *
 * Split from [SongElementRow] so the per-output Customize dialog can draw the same controls without
 * the chip strip, which it has one of its own. Two surfaces, one definition: a control added here
 * appears in both, which is the only way they stay in step.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SongElementOptions(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    element: SongStyleElement,
    target: SongStyleTarget,
    language: SongStyleLanguage = SongStyleLanguage.PRIMARY,
    titleSlideView: Boolean = false,
    /** See [SongLanguageScopeButtons]: set by the per-output dialog, absent on the global tab. */
    outputMode: String? = null,
    onOutputModeChange: ((String) -> Unit)? = null,
) {
    val song = settings.songSettings
    if (titleSlideView) {
        SongTitleSlideOptions(settings, onSettingsChange, element, target, outputMode, onOutputModeChange)
        return
    }
    // The chunk and the language scope belong to the output rather than to a language, and the
    // first language's panel already carries them -- a second copy here would be the same control
    // twice. So would the show/position row, which is the number's and the title's alone.
    if (language.isTranslation) return
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
            SongLanguageScopeButtons(settings, onSettingsChange, target, outputMode, onOutputModeChange)
        }
    }
    SongAppearanceRow(settings, onSettingsChange, element, target)
    if (element == SongStyleElement.NUMBER) {
        SongNumberOffsetControls(settings, onSettingsChange, target)
    }
}

/** What the title slide draws, for the element selected -- its own view of [SongElementOptions]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SongTitleSlideOptions(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    element: SongStyleElement,
    target: SongStyleTarget,
    outputMode: String? = null,
    onOutputModeChange: ((String) -> Unit)? = null,
) {
    val song = settings.songSettings
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LabeledCheckbox(
                checked = song.shownOnTitleSlide(element) == true,
                onCheckedChange = { on ->
                    onSettingsChange { s -> s.copy(songSettings = s.songSettings.withShownOnTitleSlide(element, on)) }
                },
                label = stringResource(Res.string.song_show_on_title_slide),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("song_show_on_title_slide"),
            )
            // The number only: on the title's row ahead of it, or on a row of its own above.
            if (element == SongStyleElement.NUMBER) {
                LabeledCheckbox(
                    checked = song.titleSlideNumberBeforeTitle,
                    onCheckedChange = { on ->
                        onSettingsChange { s ->
                            s.copy(songSettings = s.songSettings.copy(titleSlideNumberBeforeTitle = on))
                        }
                    },
                    enabled = song.titleSlideShowSongNumber,
                    label = stringResource(Res.string.show_song_number_before_title),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("song_titleSlideNumberBeforeTitle"),
                )
            }
            // The title only: the output's language, the same setting the lyric slides read,
            // decides which of the song's titles the slide opens with -- both, or one of them.
            // Nothing else on the slide has a translation.
            if (element == SongStyleElement.TITLE) {
                LabeledControl(stringResource(Res.string.song_language_scope)) {
                    SongLanguageScopeButtons(settings, onSettingsChange, target, outputMode, onOutputModeChange)
                }
            }
        }
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
        // The number only. A corner takes it out of the row it shares with the title, so there is
        // no such choice to offer for the title itself.
        if (element == SongStyleElement.NUMBER) {
            val corner = settings.songSettings.numberCorner(target.isLowerThird)
            LabeledControl(stringResource(Res.string.song_number_corner)) {
                DropdownSelector(
                    label = "",
                    value = corner,
                    options = songNumberCornerOptions(),
                    onValueChange = { newCorner ->
                        onSettingsChange { s ->
                            s.copy(songSettings = s.songSettings.withNumberCorner(target.isLowerThird, newCorner))
                        }
                    },
                    compact = true,
                    modifier = Modifier.width(CORNER_DROPDOWN_WIDTH).testTag("song_number_corner"),
                )
            }
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
 * A free nudge on top of [SongAppearanceRow]'s corner, so the number can be walked into wherever a
 * background image's own box for it actually is. Meaningless with no corner chosen, and drawn as
 * its own full-width block rather than folded into that row's [FlowRow] -- a [SliderNumberField]
 * needs real width to be usable, which a row of otherwise-compact controls doesn't have to spare.
 */
@Composable
private fun SongNumberOffsetControls(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    target: SongStyleTarget,
) {
    val corner = settings.songSettings.numberCorner(target.isLowerThird)
    if (corner == Constants.NONE) return
    val offset = settings.songSettings.numberOffset(target.isLowerThird)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ControlColumn(stringResource(Res.string.song_number_offset_x), Modifier.weight(1f)) {
            SliderNumberField(
                value = offset.xPercent,
                range = SongNumberOffset.PERCENT_RANGE,
                onValueChange = { value ->
                    onSettingsChange { s ->
                        s.copy(
                            songSettings = s.songSettings.withNumberOffset(
                                target.isLowerThird, offset.copy(xPercent = value),
                            ),
                        )
                    }
                },
                fieldWidth = NUMBER_OFFSET_FIELD_WIDTH,
                modifier = Modifier.testTag("song_number_offset_x"),
            )
        }
        ControlColumn(stringResource(Res.string.song_number_offset_y), Modifier.weight(1f)) {
            SliderNumberField(
                value = offset.yPercent,
                range = SongNumberOffset.PERCENT_RANGE,
                onValueChange = { value ->
                    onSettingsChange { s ->
                        s.copy(
                            songSettings = s.songSettings.withNumberOffset(
                                target.isLowerThird, offset.copy(yPercent = value),
                            ),
                        )
                    }
                },
                fieldWidth = NUMBER_OFFSET_FIELD_WIDTH,
                modifier = Modifier.testTag("song_number_offset_y"),
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
