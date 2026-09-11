@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongCreditStyle
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The title slide as `SongPresenter` draws it: which lines are on it, in which language, where the
 * number sits, how the block is placed, and that each line takes its own element's profile -- on
 * the screen and on the band.
 */
class SongPresenterTitleSlideRenderTest {

    private val screen = Modifier.size(1920.dp, 1080.dp)

    private fun titleSlide(
        secondaryTitle: String = "О, благодать",
        author: String = "John Newton",
        composer: String = "William Walker",
        ccli: String = "22025",
        bpm: Int = 84,
    ) = LyricSection(
        type = Constants.SECTION_TYPE_TITLE_SLIDE,
        title = "Amazing Grace",
        secondaryTitle = secondaryTitle,
        songNumber = 427,
        author = author,
        composer = composer,
        ccli = ccli,
        bpm = bpm,
        lines = listOf("427 – Amazing Grace"),
    )

    private fun settings(song: SongSettings = SongSettings()) = AppSettings(songSettings = song)

    private fun present(
        appSettings: AppSettings,
        section: LyricSection = titleSlide(),
        isLowerThird: Boolean = false,
        language: String = Constants.SONG_LANG_PRIMARY,
        block: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(screen) {
                    SongPresenter(
                        lyricSection = section,
                        appSettings = appSettings,
                        isLowerThird = isLowerThird,
                        allLyricSections = listOf(section),
                        displaySectionIndex = 0,
                        languageOverride = language,
                    )
                }
            }
        }
        block()
    }

    private fun ComposeUiTest.assertShown(text: String) = onNodeWithText(text, substring = true).assertExists(text)
    private fun ComposeUiTest.assertAbsent(text: String) =
        onAllNodesWithText(text, substring = true).assertCountEquals(0)

    // ── What is on the slide ──────────────────────────────────────────────────

    @Test
    fun `by default the slide carries the number and title, the author and the composer`() =
        present(settings()) {
            assertShown("427")
            assertShown("Amazing Grace")
            assertShown("John Newton")
            assertShown("William Walker")
            assertAbsent("CCLI")
            assertAbsent("BPM")
        }

    @Test
    fun `the licence and the tempo appear once switched on`() =
        present(settings(SongSettings(titleSlideShowCcli = true, titleSlideShowTempo = true))) {
            assertShown("CCLI #22025")
            assertShown("84 BPM")
        }

    @Test
    fun `an element switched off is not drawn`() =
        present(settings(SongSettings(titleSlideShowAuthor = false, titleSlideShowSongNumber = false))) {
            assertAbsent("John Newton")
            assertAbsent("427")
            assertShown("William Walker")
        }

    @Test
    fun `no lyric-slide furniture is drawn on it`() =
        present(settings(SongSettings(showEndOfSongIndicator = true))) {
            // The end-of-song marker, drawn under the last lyric section, is not the title slide's.
            assertAbsent("*")
        }

    // ── The number's place ────────────────────────────────────────────────────

    @Test
    fun `the number shares the title's line by default`() = present(settings()) {
        onNodeWithText("427", substring = true).assertTextContains("Amazing Grace", substring = true)
    }

    @Test
    fun `switched off, the number is a line of its own`() =
        present(settings(SongSettings(titleSlideNumberBeforeTitle = false))) {
            onNodeWithText("427").assertExists()
            onNodeWithText("Amazing Grace").assertExists()
        }

    // ── Languages ─────────────────────────────────────────────────────────────

    @Test
    fun `a primary-language output shows one title`() = present(settings()) {
        assertShown("Amazing Grace")
        assertAbsent("благодать")
    }

    @Test
    fun `a secondary-language output shows the translation`() =
        present(settings(), language = Constants.SONG_LANG_SECONDARY) {
            assertShown("О, благодать")
            assertAbsent("Amazing Grace")
        }

    @Test
    fun `an output showing both languages shows both titles`() =
        present(settings(), language = Constants.SONG_LANG_BOTH) {
            assertShown("Amazing Grace")
            assertShown("О, благодать")
        }

    // ── Placement ─────────────────────────────────────────────────────────────

    @Test
    fun `the block sits where the title slide's own alignment puts it`() {
        var top = 0f
        var bottom = 0f
        present(settings(SongSettings(titleSlideVerticalAlignment = Constants.TOP))) {
            top = onNodeWithText("Amazing Grace", substring = true).fetchSemanticsNode().boundsInRoot.top
        }
        present(settings(SongSettings(titleSlideVerticalAlignment = Constants.BOTTOM))) {
            bottom = onNodeWithText("Amazing Grace", substring = true).fetchSemanticsNode().boundsInRoot.top
        }
        assertTrue(top < 300f, "top-aligned title at $top")
        assertTrue(bottom > top + 300f, "bottom-aligned title at $bottom, top-aligned at $top")
    }

    @Test
    fun `the lyrics' alignment does not move it`() {
        var middle = 0f
        var lyricsTop = 0f
        present(settings(SongSettings(lyricsAlignment = Constants.MIDDLE))) {
            middle = onNodeWithText("Amazing Grace", substring = true).fetchSemanticsNode().boundsInRoot.top
        }
        present(settings(SongSettings(lyricsAlignment = Constants.TOP))) {
            lyricsTop = onNodeWithText("Amazing Grace", substring = true).fetchSemanticsNode().boundsInRoot.top
        }
        assertTrue(middle == lyricsTop, "the lyrics' alignment moved the title slide: $middle vs $lyricsTop")
    }

    @Test
    fun `on the band the block sits at the bottom whatever the alignment says`() {
        present(settings(SongSettings(titleSlideVerticalAlignment = Constants.TOP)), isLowerThird = true) {
            val title = onNodeWithText("Amazing Grace", substring = true).fetchSemanticsNode().boundsInRoot
            assertTrue(title.top > 1080 * 0.6f, "band title at ${title.top}")
        }
    }

    // ── Profiles ──────────────────────────────────────────────────────────────

    @Test
    fun `each credit is drawn in its own size, and the band in the band's`() {
        val song = SongSettings(
            titleSlideAuthor = SongCreditStyle(fontSize = 80),
            titleSlideComposer = SongCreditStyle(fontSize = 20),
            titleSlideAuthorLowerThird = SongCreditStyle(fontSize = 20),
        )
        var author = 0f
        var composer = 0f
        var bandAuthor = 0f
        present(settings(song)) {
            author = onNodeWithText("John Newton").fetchSemanticsNode().boundsInRoot.height
            composer = onNodeWithText("William Walker").fetchSemanticsNode().boundsInRoot.height
        }
        present(settings(song), isLowerThird = true) {
            bandAuthor = onNodeWithText("John Newton").fetchSemanticsNode().boundsInRoot.height
        }
        assertTrue(author > composer * 2, "author $author vs composer $composer")
        assertTrue(bandAuthor < author / 2, "band author $bandAuthor vs screen $author")
    }

    @Test
    fun `the title takes the title profile, not the lyrics'`() {
        var title = 0f
        var lyric = 0f
        val song = SongSettings(
            titleFontSize = 20, lyricsFontSize = 70, lyricsFontSizeAutoFit = false,
            titleSlideShowSongNumber = false,
        )
        present(settings(song)) {
            title = onNodeWithText("Amazing Grace").fetchSemanticsNode().boundsInRoot.height
        }
        present(
            settings(song),
            section = LyricSection(type = Constants.SECTION_TYPE_VERSE, lines = listOf("How sweet the sound")),
        ) {
            lyric = onNodeWithText("How sweet the sound").fetchSemanticsNode().boundsInRoot.height
        }
        assertTrue(title < lyric / 2, "title $title vs lyric $lyric")
    }
}
