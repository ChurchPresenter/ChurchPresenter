package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import org.churchpresenter.app.churchpresenter.utils.rememberSystemFonts
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputStyleScope
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.ScreenAssignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.composables.LabeledControl
import org.churchpresenter.app.churchpresenter.composables.SegmentedButton
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.song_style_language
import org.jetbrains.compose.resources.stringResource

/**
 * The Song pane, showing whichever element the chips above it have selected.
 *
 * The margins, the fades and the band's geometry have moved under the preview into
 * [CustomizeCategoryStrip]: they belong to the slide rather than to the lyrics or the title, and a
 * copy of them under all five chips would be five copies of one setting.
 */
@Composable
internal fun SongCustomizePane(
    element: CustomizeElement,
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    /**
     * This screen's own song language, which lives on its [ScreenAssignment] rather than in
     * `SongSettings` -- so it is read and written past [onSettingsChange], whose edits this dialog
     * stores as a `SongSettings` difference and nothing else.
     */
    songMode: String,
    /** The languages this screen has been told to show, which decide which of them can be styled. */
    songTranslations: List<Int>,
    onSongModeChange: (String) -> Unit,
) {
    val scope = LocalOutputStyleScope.current
    val target =
        if (scope == OutputStyleScope.LOWER_THIRD) SongStyleTarget.LOWER_THIRD else SongStyleTarget.FULL_SCREEN
    val fonts = rememberSystemFonts()
    val song = settings.songSettings

    PaneScaffold {
        val titleSlideView = element == CustomizeElement.SONG_TITLE_SLIDE
        // Which of the two languages the controls below write. A song carries its lyrics and its
        // title twice, and each half has a profile of its own, so this screen can be told to draw
        // the second language differently from the first -- the same choice the global Song tab
        // offers, for this one output.
        var language by remember { mutableStateOf(SongStyleLanguage.PRIMARY) }
        // The title slide draws six things and the chips above have one seat for all of them, so it
        // keeps a selector of its own. The lyric slides' elements each have a chip already.
        var slideElement by remember { mutableStateOf(SongStyleElement.TITLE) }
        val styleElement = if (titleSlideView) slideElement else element.toSongStyleElement()
        // Only where there is a second language on this screen and an element that has a second
        // profile for it. Everywhere else the switch would offer a choice with one answer.
        val styleLanguages = styleLanguagesFor(songMode, songTranslations)
        val hasSecondLanguage = styleLanguages.size > 1 && styleElement in SECOND_LANGUAGE_ELEMENTS
        val editingLanguage = if (hasSecondLanguage && language in styleLanguages) {
            language
        } else {
            styleLanguages.first()
        }

        if (titleSlideView) {
            SongTitleSlideEnabledRow(settings, onSettingsChange, showVerticalAlignment = !target.isLowerThird)
            SegmentedButton(
                items = TITLE_SLIDE_ELEMENTS.map { SegmentedButtonItem(it, it.label()) },
                selectedValue = slideElement,
                onValueChange = { slideElement = it },
                buttonWidth = TITLE_SLIDE_CHIP_WIDTH,
                buttonHeight = 30.dp,
                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                compactColumns = TITLE_SLIDE_CHIP_COLUMNS,
            )
        }

        SongElementOptions(
            settings = settings,
            onSettingsChange = onSettingsChange,
            element = styleElement,
            target = target,
            titleSlideView = titleSlideView,
            outputMode = songMode,
            onOutputModeChange = onSongModeChange,
        )
        // The same panel the Song settings tab draws, reading and writing the same profile. Two
        // surfaces over one definition: a control added to the tab is in the dialog the same day.
        if (hasSecondLanguage) {
            LabeledControl(stringResource(Res.string.song_style_language)) {
                SegmentedButton(
                    items = styleLanguages.map { SegmentedButtonItem(it, it.nameLabel()) },
                    selectedValue = editingLanguage,
                    onValueChange = { language = it },
                    buttonWidth = STYLE_LANGUAGE_BUTTON_WIDTH,
                    buttonHeight = 30.dp,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                )
            }
        }
        // Keyed on the language too: one set of controls stands for two stored profiles, and
        // without this Compose keeps the subtree across the switch and hands each control the state
        // -- and the write-back lambda -- of the profile that held its slot before.
        key(editingLanguage) {
            SongTypographyPanel(
                element = styleElement,
                style = song.elementStyle(styleElement, target, editingLanguage),
                onStyleChange = { edited ->
                    onSettingsChange { s ->
                        s.copy(
                            songSettings = s.songSettings
                                .withElementStyle(styleElement, target, editingLanguage, edited),
                        )
                    }
                },
                onReset = {
                    onSettingsChange { s ->
                        s.copy(songSettings = s.songSettings.withElementReset(styleElement, target, editingLanguage))
                    }
                },
                availableFonts = fonts,
                onTitleSlide = titleSlideView,
                numberInCorner = song.numberCorner(target.isLowerThird) != Constants.NONE,
                // Where the lyric block sits on the slide, beside the horizontal alignment -- one
                // value for the song rather than one per element, so it is written straight onto
                // `SongSettings` rather than into a profile. Only the lyrics carry it: the
                // look-ahead and the next section sit under the line they follow, and the title
                // slide has its own control above.
                blockAlignment = if (!titleSlideView && styleElement == SongStyleElement.LYRICS) {
                    {
                        BlockVerticalAlignmentControl(
                            selected = song.lyricsAlignment,
                            onSelect = { v ->
                                onSettingsChange { s ->
                                    s.copy(songSettings = s.songSettings.copy(lyricsAlignment = v))
                                }
                            },
                        )
                    }
                } else {
                    null
                },
            )
        }
        if (!titleSlideView && styleElement == SongStyleElement.LYRICS) {
            // How the languages sit against each other, on the same terms as the vertical alignment:
            // one value for the song, written straight onto `SongSettings`, and drawn on the lyrics
            // because the lyrics are what carry a second language. One value serves both shapes,
            // exactly as `SongPresenter` reads it -- a vertical band ignores side-by-side and stacks
            // regardless, having no width to split.
            //
            // Gated on this profile's language mode and nothing else: how many languages a song
            // actually has is a property of the song, read at render time, so settings cannot know
            // it. A profile narrowed to one language has nothing to arrange.
            if (songMode == Constants.SONG_LANG_BOTH) {
                BilingualLayoutRow(
                    selected = song.bilingualLayout,
                    onSelect = { v ->
                        onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(bilingualLayout = v)) }
                    },
                )
            }
        }
    }
}

/** Which stored profile a Songs chip stands for. */
private fun CustomizeElement.toSongStyleElement(): SongStyleElement = when (this) {
    CustomizeElement.SONG_TITLE -> SongStyleElement.TITLE
    CustomizeElement.SONG_NUMBER -> SongStyleElement.NUMBER
    CustomizeElement.SONG_LOOK_AHEAD -> SongStyleElement.LOOK_AHEAD
    CustomizeElement.SONG_NEXT_SECTION -> SongStyleElement.NEXT_SECTION
    else -> SongStyleElement.LYRICS
}
/** Whether this screen opens a song with a title slide, and where that slide's block sits. */
@Composable
private fun SongTitleSlideEnabledRow(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    showVerticalAlignment: Boolean,
) {
    SongTitleSlideSection(settings, onSettingsChange, showVerticalAlignment)
}

/** Wide enough for "Composer", the longest of the six. */
private val TITLE_SLIDE_CHIP_WIDTH = 86.dp

/** Six chips are wider than this column, so past three they fold onto another row. */
private const val TITLE_SLIDE_CHIP_COLUMNS = 3

/** Wide enough for "Secondary" without an ellipsis. */
private val STYLE_LANGUAGE_BUTTON_WIDTH = 82.dp


