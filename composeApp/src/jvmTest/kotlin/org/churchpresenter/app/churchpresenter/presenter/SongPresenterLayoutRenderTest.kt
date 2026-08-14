package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.SongSettings
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test

/**
 * Where the title and song number land relative to the verse, and how the auto-fit search folds
 * their reserved height and the look-ahead pairing into the size it picks.
 */
@OptIn(ExperimentalTestApi::class)
class SongPresenterLayoutRenderTest {

    private val screen = Modifier.size(1920.dp, 1080.dp)

    private fun section(
        lines: List<String> = listOf("Amazing grace how sweet the sound"),
        secondaryLines: List<String> = emptyList(),
        title: String = "Amazing Grace",
        number: Int = 42,
        header: String = "[Verse 1]",
    ) = LyricSection(
        header = header,
        title = title,
        songNumber = number,
        type = Constants.SECTION_TYPE_VERSE,
        lines = lines,
        secondaryLines = secondaryLines,
    )

    private fun present(
        appSettings: AppSettings,
        lyricSection: LyricSection = section(),
        lookAheadEnabled: Boolean = false,
        allSections: List<LyricSection> = emptyList(),
        displaySectionIndex: Int = -1,
        isLowerThird: Boolean = false,
        block: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(screen) {
                    SongPresenter(
                        lyricSection = lyricSection,
                        appSettings = appSettings,
                        isLowerThird = isLowerThird,
                        lookAheadEnabled = lookAheadEnabled,
                        allLyricSections = allSections,
                        displaySectionIndex = displaySectionIndex,
                    )
                }
            }
        }
        block()
    }

    // ── Title and number sharing a position ─────────────────────────────────────

    @Test
    fun `title and number in the same row when aligned the same way`() {
        val settings = AppSettings(
            songSettings = SongSettings(
                titlePosition = Constants.ABOVE_VERSE, titleDisplay = Constants.EVERY_PAGE,
                songNumberPosition = Constants.ABOVE_VERSE, showNumber = Constants.EVERY_PAGE,
                titleHorizontalAlignment = Constants.LEFT, songNumberHorizontalAlignment = Constants.LEFT,
                songNumberBeforeTitle = true,
            ),
        )
        present(settings) {
            onNodeWithText("Amazing Grace", substring = true).assertExists()
            onNodeWithText("42", substring = true).assertExists()
        }
    }

    @Test
    fun `the title follows the number when number-before-title is turned off`() {
        val settings = AppSettings(
            songSettings = SongSettings(
                titlePosition = Constants.ABOVE_VERSE, titleDisplay = Constants.EVERY_PAGE,
                songNumberPosition = Constants.ABOVE_VERSE, showNumber = Constants.EVERY_PAGE,
                titleHorizontalAlignment = Constants.CENTER, songNumberHorizontalAlignment = Constants.CENTER,
                songNumberBeforeTitle = false,
            ),
        )
        present(settings) {
            onNodeWithText("Amazing Grace", substring = true).assertExists()
            onNodeWithText("42", substring = true).assertExists()
        }
    }

    @Test
    fun `title and number right aligned still share the row`() {
        val settings = AppSettings(
            songSettings = SongSettings(
                titlePosition = Constants.ABOVE_VERSE, titleDisplay = Constants.EVERY_PAGE,
                songNumberPosition = Constants.ABOVE_VERSE, showNumber = Constants.EVERY_PAGE,
                titleHorizontalAlignment = Constants.RIGHT, songNumberHorizontalAlignment = Constants.RIGHT,
                songNumberBeforeTitle = true,
            ),
        )
        present(settings) {
            onNodeWithText("Amazing Grace", substring = true).assertExists()
            onNodeWithText("42", substring = true).assertExists()
        }
    }

    @Test
    fun `title and number stack when their alignments differ`() {
        val settings = AppSettings(
            songSettings = SongSettings(
                titlePosition = Constants.ABOVE_VERSE, titleDisplay = Constants.EVERY_PAGE,
                songNumberPosition = Constants.ABOVE_VERSE, showNumber = Constants.EVERY_PAGE,
                titleHorizontalAlignment = Constants.LEFT, songNumberHorizontalAlignment = Constants.RIGHT,
            ),
        )
        present(settings) {
            onNodeWithText("Amazing Grace", substring = true).assertExists()
            onNodeWithText("42", substring = true).assertExists()
        }
    }

    @Test
    fun `an unnumbered song still shows a title configured alongside a number`() {
        val settings = AppSettings(
            songSettings = SongSettings(
                titlePosition = Constants.ABOVE_VERSE, titleDisplay = Constants.EVERY_PAGE,
                songNumberPosition = Constants.ABOVE_VERSE, showNumber = Constants.EVERY_PAGE,
            ),
        )
        present(settings, lyricSection = section(number = 0)) {
            onNodeWithText("Amazing Grace", substring = true).assertExists()
            onNodeWithText("0", substring = true).assertDoesNotExist()
        }
    }

    // ── Auto-fit reserving space for title/number above the verse ──────────────

    @Test
    fun `auto-fit reserves height for a title and number placed above the verse`() {
        val settings = AppSettings(
            songSettings = SongSettings(
                titlePosition = Constants.ABOVE_VERSE, titleDisplay = Constants.EVERY_PAGE,
                songNumberPosition = Constants.ABOVE_VERSE, showNumber = Constants.EVERY_PAGE,
            ),
        )
        val first = section(title = "Amazing Grace", number = 42)
        val second = section(
            title = "How Great Thou Art",
            number = 7,
            lines = listOf("Then sings my soul"),
            header = "[Verse 2]",
        )
        present(settings, lyricSection = first, allSections = listOf(first, second)) {
            onNodeWithText("Amazing grace how sweet the sound", substring = true).assertExists()
        }
    }

    @Test
    fun `auto-fit runs over the plain section list when look-ahead is off`() {
        val first = section()
        val second = section(lines = listOf("second section line"), header = "[Verse 2]")
        present(AppSettings(), lyricSection = first, allSections = listOf(first, second), lookAheadEnabled = false) {
            onNodeWithText("Amazing grace how sweet the sound", substring = true).assertExists()
        }
    }

    @Test
    fun `look-ahead line mode pairs each line with the next line for auto-fit`() {
        val settings = AppSettings(songSettings = SongSettings(lookAheadDisplayMode = Constants.SONG_DISPLAY_MODE_LINE))
        val first = section(
            lines = listOf("first line", "second line"),
            secondaryLines = listOf("first other", "second other"),
        )
        val second = section(lines = listOf("third line"), header = "[Verse 2]")
        present(
            settings,
            lyricSection = first,
            allSections = listOf(first, second),
            lookAheadEnabled = true,
            displaySectionIndex = 0,
        ) {
            onNodeWithText("first line", substring = true).assertExists()
        }
    }

    @Test
    fun `look-ahead line mode on a lower third pairs lines for auto-fit`() {
        val settings =
            AppSettings(songSettings = SongSettings(lowerThirdLookAheadDisplayMode = Constants.SONG_DISPLAY_MODE_LINE))
        val first = section(lines = listOf("first line", "second line"))
        val second = section(lines = listOf("third line"), header = "[Verse 2]")
        present(
            settings,
            lyricSection = first,
            allSections = listOf(first, second),
            lookAheadEnabled = true,
            displaySectionIndex = 0,
            isLowerThird = true,
        ) {
            onNodeWithText("first line", substring = true).assertExists()
        }
    }

    @Test
    fun `side-by-side bilingual auto-fit halves the reference width`() {
        val settings = AppSettings(songSettings = SongSettings(bilingualLayout = Constants.BILINGUAL_SIDE_BY_SIDE))
        val bilingual = section(
            lines = listOf("Amazing grace how sweet the sound"),
            secondaryLines = listOf("Chudnaya blagodat"),
        )
        present(settings, lyricSection = bilingual, allSections = listOf(bilingual)) {
            onNodeWithText("Amazing grace how sweet the sound", substring = true).assertExists()
            onNodeWithText("Chudnaya blagodat", substring = true).assertExists()
        }
    }

    @Test
    fun `top-bottom bilingual auto-fit on a lower third halves the reference height`() {
        val settings = AppSettings(songSettings = SongSettings(bilingualLayout = Constants.BILINGUAL_TOP_BOTTOM))
        val bilingual = section(
            lines = listOf("Amazing grace how sweet the sound"),
            secondaryLines = listOf("Chudnaya blagodat"),
        )
        present(settings, lyricSection = bilingual, allSections = listOf(bilingual), isLowerThird = true) {
            onNodeWithText("Amazing grace how sweet the sound", substring = true).assertExists()
        }
    }
}
