package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import org.churchpresenter.app.churchpresenter.utils.rememberSystemFonts
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputStyleScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.composables.SegmentedButton
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem

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
) {
    val scope = LocalOutputStyleScope.current
    val target =
        if (scope == OutputStyleScope.LOWER_THIRD) SongStyleTarget.LOWER_THIRD else SongStyleTarget.FULL_SCREEN
    val fonts = rememberSystemFonts()
    val song = settings.songSettings

    PaneScaffold {
        val titleSlideView = element == CustomizeElement.SONG_TITLE_SLIDE
        // The title slide draws six things and the chips above have one seat for all of them, so it
        // keeps a selector of its own. The lyric slides' elements each have a chip already.
        var slideElement by remember { mutableStateOf(SongStyleElement.TITLE) }
        val styleElement = if (titleSlideView) slideElement else element.toSongStyleElement()

        if (titleSlideView) {
            SongTitleSlideEnabledRow(settings, onSettingsChange)
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
        )
        // The same panel the Song settings tab draws, reading and writing the same profile. Two
        // surfaces over one definition: a control added to the tab is in the dialog the same day.
        SongTypographyPanel(
            element = styleElement,
            style = song.elementStyle(styleElement, target),
            onStyleChange = { edited ->
                onSettingsChange { s ->
                    s.copy(songSettings = s.songSettings.withElementStyle(styleElement, target, edited))
                }
            },
            onReset = {
                onSettingsChange { s ->
                    s.copy(
                        songSettings = s.songSettings.withElementStyle(
                            styleElement,
                            target,
                            defaultSongElementStyle(styleElement, target),
                        ),
                    )
                }
            },
            availableFonts = fonts,
            onTitleSlide = titleSlideView,
        )
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
) {
    SongTitleSlideSection(settings, onSettingsChange)
}

/** Wide enough for "Composer", the longest of the six. */
private val TITLE_SLIDE_CHIP_WIDTH = 86.dp

/** Six chips are wider than this column, so past three they fold onto another row. */
private const val TITLE_SLIDE_CHIP_COLUMNS = 3


