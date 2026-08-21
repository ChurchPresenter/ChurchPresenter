package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.SongSettings
import org.churchpresenter.app.churchpresenter.models.songs.LyricSection
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test

/**
 * The two song layouts the everyday verse-on-a-slide tests don't reach: look-ahead and line-by-line.
 *
 * Look-ahead is for the band — the current section plus a preview of what's coming, so the
 * transition stays smooth. Its whole layout branch is separate from the normal one, and if the
 * "next" preview silently stops rendering the musicians fly blind while the congregation sees no
 * difference. Line-by-line mode shows one line at a time (common on a lower third); the failure that
 * matters is showing the wrong line — every other line of the section must stay off screen.
 *
 * Both assert on the lyric text that lands on screen, so the whole selection path runs and nothing
 * races a crossfade.
 */
@OptIn(ExperimentalTestApi::class)
class SongPresenterModeRenderTest {

    private val screen = Modifier.size(1920.dp, 1080.dp)

    private fun section(vararg lines: String, header: String = "[Verse 1]") = LyricSection(
        header = header,
        title = "Amazing Grace",
        songNumber = 42,
        type = Constants.SECTION_TYPE_VERSE,
        lines = lines.toList(),
        secondaryLines = emptyList(),
    )

    @Test
    fun `look-ahead shows the current section and a preview of the next`() = runComposeUiTest {
        val current = section("Amazing grace how sweet the sound")
        val next = section("That saved a wretch like me", header = "[Verse 2]")
        setContent {
            Box(screen) {
                SongPresenter(
                    lyricSection = current,
                    appSettings = AppSettings(),   // lookAheadDisplayMode defaults to whole-verse
                    lookAheadEnabled = true,
                    allLyricSections = listOf(current, next),
                    displaySectionIndex = 0,
                )
            }
        }
        onNodeWithText(
            "Amazing grace how sweet the sound",
            substring = true,
        ).assertExists("the section being sung must be on screen")
        onNodeWithText("That saved a wretch like me", substring = true)
            .assertExists("the band's look-ahead preview of the next section must render")
    }

    @Test
    fun `line mode shows only the selected line and hides the rest`() = runComposeUiTest {
        val lineMode =
            AppSettings(songSettings = SongSettings(fullscreenDisplayMode = Constants.SONG_DISPLAY_MODE_LINE))
        setContent {
            Box(screen) {
                SongPresenter(
                    lyricSection = section("first line", "second line", "third line"),
                    appSettings = lineMode,
                    displayLineIndex = 1,
                )
            }
        }
        onNodeWithText("second line", substring = true).assertExists("the selected line must be the one shown")
        onNodeWithText("first line", substring = true).assertDoesNotExist()
        onNodeWithText("third line", substring = true).assertDoesNotExist()
    }

    // ── Look-ahead while the screen is in line-by-line mode ─────────────────────
    //
    // With look-ahead on, the WHOLE screen switches to look-ahead's own display mode, so
    // `lookAheadDisplayMode` decides line-vs-verse for the main text too. In line mode the preview
    // is the next LINE rather than the next section, and it has to roll from the end of one section
    // into the start of the next. Getting that wrong shows the band a line they have already sung.

    private val lineLookAhead = AppSettings(
        songSettings = SongSettings(lookAheadDisplayMode = Constants.SONG_DISPLAY_MODE_LINE),
    )

    @Test
    fun `line-mode look-ahead previews the next line of the same section`() = runComposeUiTest {
        val current = section("first line", "second line", "third line")
        val next = section("next section line", header = "[Verse 2]")
        setContent {
            Box(screen) {
                SongPresenter(
                    lyricSection = current,
                    appSettings = lineLookAhead,
                    lookAheadEnabled = true,
                    allLyricSections = listOf(current, next),
                    displaySectionIndex = 0,
                    displayLineIndex = 0,
                )
            }
        }
        onNodeWithText("first line", substring = true).assertExists("the line being sung")
        onNodeWithText("second line", substring = true).assertExists("previewed from the same section")
        onNodeWithText("third line", substring = true).assertDoesNotExist()
        // the next section is not due yet
        onNodeWithText("next section line", substring = true).assertDoesNotExist()
    }

    @Test
    fun `line-mode look-ahead rolls into the next section on the last line`() = runComposeUiTest {
        // The moment the preview has to cross a section boundary — the one the band actually
        // needs, because it is where the key or the melody changes.
        val current = section("first line", "last line")
        val next = section("next section line", header = "[Verse 2]")
        setContent {
            Box(screen) {
                SongPresenter(
                    lyricSection = current,
                    appSettings = lineLookAhead,
                    lookAheadEnabled = true,
                    allLyricSections = listOf(current, next),
                    displaySectionIndex = 0,
                    displayLineIndex = 1,
                )
            }
        }
        onNodeWithText("last line", substring = true).assertExists()
        onNodeWithText("next section line", substring = true)
            .assertExists("at the end of a section the preview is the next section's opening line")
    }

    @Test
    fun `a next section with no lines is not previewed`() = runComposeUiTest {
        // Sections with a header and no lyrics under them exist in real files. Previewing one
        // blanks the look-ahead pane rather than showing what is coming, so it is skipped and the
        // preview falls back to the next line of the section being sung.
        val current = section("first line", "second line")
        val empty = LyricSection(
            header = "[Instrumental]",
            title = "Amazing Grace",
            songNumber = 42,
            type = Constants.SECTION_TYPE_VERSE,
            lines = emptyList(),
            secondaryLines = emptyList(),
        )
        setContent {
            Box(screen) {
                SongPresenter(
                    lyricSection = current,
                    appSettings = lineLookAhead,
                    lookAheadEnabled = true,
                    allLyricSections = listOf(current, empty),
                    displaySectionIndex = 0,
                    displayLineIndex = 0,
                )
            }
        }
        onNodeWithText("first line", substring = true).assertExists()
        onNodeWithText("second line", substring = true)
            .assertExists("with nothing to preview ahead, the next line here is what is coming")
    }

    @Test
    fun `the last section previews its own next line rather than nothing`() = runComposeUiTest {
        // No section follows, so the only thing left to look ahead to is inside this one.
        val only = section("first line", "second line")
        setContent {
            Box(screen) {
                SongPresenter(
                    lyricSection = only,
                    appSettings = lineLookAhead,
                    lookAheadEnabled = true,
                    allLyricSections = listOf(only),
                    displaySectionIndex = 0,
                    displayLineIndex = 0,
                )
            }
        }
        onNodeWithText("first line", substring = true).assertExists()
        onNodeWithText("second line", substring = true).assertExists()
    }

    // ── Asking for a language the section does not carry ────────────────────────

    @Test
    fun `a secondary-only screen falls back to the primary when there is no translation`() = runComposeUiTest {
        // An output set to show the secondary language is pointed at a song that has none — a
        // monolingual song in an otherwise bilingual set. Showing nothing would blank that output
        // for the whole song; the primary is the only useful thing left to draw.
        setContent {
            Box(screen) {
                SongPresenter(
                    lyricSection = section("Amazing grace how sweet the sound"),
                    appSettings = AppSettings(),
                    languageOverride = Constants.SONG_LANG_SECONDARY,
                )
            }
        }
        onNodeWithText("Amazing grace how sweet the sound", substring = true)
            .assertExists("a song with no translation still has to appear on a secondary-language output")
    }

    @Test
    fun `a secondary-only look-ahead falls back to the primary too`() = runComposeUiTest {
        // The preview resolves its language separately from the main text, so the same fallback
        // has to hold there or the band's pane goes blank while the congregation's does not.
        val current = section("Amazing grace how sweet the sound")
        val next = section("That saved a wretch like me", header = "[Verse 2]")
        setContent {
            Box(screen) {
                SongPresenter(
                    lyricSection = current,
                    appSettings = AppSettings(),
                    lookAheadEnabled = true,
                    allLyricSections = listOf(current, next),
                    displaySectionIndex = 0,
                    languageOverride = Constants.SONG_LANG_SECONDARY,
                )
            }
        }
        onNodeWithText("Amazing grace how sweet the sound", substring = true).assertExists()
        onNodeWithText("That saved a wretch like me", substring = true)
            .assertExists("the preview falls back for the same reason the main text does")
    }

    @Test
    fun `a secondary-only screen shows the translation when the song has one`() = runComposeUiTest {
        val bilingual = LyricSection(
            header = "[Verse 1]",
            title = "Amazing Grace",
            songNumber = 42,
            type = Constants.SECTION_TYPE_VERSE,
            lines = listOf("Amazing grace how sweet the sound"),
            secondaryLines = listOf("Удивительная благодать"),
        )
        setContent {
            Box(screen) {
                SongPresenter(
                    lyricSection = bilingual,
                    appSettings = AppSettings(),
                    languageOverride = Constants.SONG_LANG_SECONDARY,
                )
            }
        }

        onNodeWithText("Удивительная благодать", substring = true).assertExists()
        onNodeWithText("Amazing grace how sweet the sound", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a both-languages screen shows the translation alongside the original`() = runComposeUiTest {
        val bilingual = LyricSection(
            header = "[Verse 1]",
            title = "Amazing Grace",
            songNumber = 42,
            type = Constants.SECTION_TYPE_VERSE,
            lines = listOf("Amazing grace how sweet the sound"),
            secondaryLines = listOf("Удивительная благодать"),
        )
        setContent {
            Box(screen) {
                SongPresenter(
                    lyricSection = bilingual,
                    appSettings = AppSettings(),
                    languageOverride = Constants.SONG_LANG_BOTH,
                )
            }
        }

        onNodeWithText("Amazing grace how sweet the sound", substring = true).assertExists()
        onNodeWithText("Удивительная благодать", substring = true).assertExists()
    }

    @Test
    fun `a both-languages look-ahead previews the translation too`() = runComposeUiTest {
        val current = LyricSection(
            header = "[Verse 1]",
            title = "Amazing Grace",
            songNumber = 42,
            type = Constants.SECTION_TYPE_VERSE,
            lines = listOf("Amazing grace how sweet the sound"),
            secondaryLines = listOf("Удивительная благодать"),
        )
        val next = LyricSection(
            header = "[Verse 2]",
            title = "Amazing Grace",
            songNumber = 42,
            type = Constants.SECTION_TYPE_VERSE,
            lines = listOf("That saved a wretch like me"),
            secondaryLines = listOf("Спасён я ею был"),
        )
        setContent {
            Box(screen) {
                SongPresenter(
                    lyricSection = current,
                    appSettings = AppSettings(),
                    lookAheadEnabled = true,
                    allLyricSections = listOf(current, next),
                    displaySectionIndex = 0,
                    languageOverride = Constants.SONG_LANG_BOTH,
                )
            }
        }

        onNodeWithText("Спасён я ею был", substring = true).assertExists()
    }

    @Test
    fun `a primary-only screen leaves the translation off even when the song has one`() = runComposeUiTest {
        val bilingual = LyricSection(
            header = "[Verse 1]",
            title = "Amazing Grace",
            songNumber = 42,
            type = Constants.SECTION_TYPE_VERSE,
            lines = listOf("Amazing grace how sweet the sound"),
            secondaryLines = listOf("Удивительная благодать"),
        )
        setContent {
            Box(screen) {
                SongPresenter(
                    lyricSection = bilingual,
                    appSettings = AppSettings(),
                    languageOverride = Constants.SONG_LANG_PRIMARY,
                )
            }
        }

        onNodeWithText("Amazing grace how sweet the sound", substring = true).assertExists()
        onNodeWithText("Удивительная благодать", substring = true).assertDoesNotExist()
    }
}
