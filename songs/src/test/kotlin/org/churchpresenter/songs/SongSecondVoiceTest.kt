package org.churchpresenter.songs

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.Dispatchers
import org.churchpresenter.core.models.songs.SongFileParser
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTextOverrides
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A song's second voice in the Songs feature: the editor's Voice 2 button, the slide the Output
 * tab previews, `[voice: 2]` blocks becoming slides, and Next stepping over a line and its answer.
 */
class SongSecondVoiceTest {

    // ── The Voice 2 button ──────────────────────────────────────────────────────

    private fun value(text: String, start: Int, end: Int = start) = TextFieldValue(text, TextRange(start, end))

    @Test
    fun `words selected on one line are wrapped and stay selected`() {
        val text = "Was blind, but now I see"
        val start = text.indexOf("now")

        val out = toggleSecondVoice(value(text, start, text.length))

        assertEquals("Was blind, but 2>(now I see)", out.text)
        assertEquals("now I see", out.text.substring(out.selection.start, out.selection.end))
    }

    @Test
    fun `a caret with no selection marks its whole line, and again unmarks it`() {
        val text = "Amazing grace\nThat saved a wretch\nI once was lost"
        val caret = text.indexOf("saved")

        val marked = toggleSecondVoice(value(text, caret))
        assertEquals("Amazing grace\n2>That saved a wretch\nI once was lost", marked.text)
        assertEquals(caret + 2, marked.selection.start)

        val unmarked = toggleSecondVoice(marked)
        assertEquals(text, unmarked.text)
        assertEquals(caret, unmarked.selection.start)
    }

    @Test
    fun `a selection across lines marks every line in it but the blank ones`() {
        val text = "one\n\ntwo\nthree"

        val out = toggleSecondVoice(value(text, 1, text.indexOf("two") + 1))

        assertEquals("2>one\n\n2>two\nthree", out.text)
        assertEquals(0, out.selection.start, "the selection covers the lines it marked")
    }

    @Test
    fun `lines that are all marked are unmarked together`() {
        val out = toggleSecondVoice(value("2>one\n  2> two", 0, 12))

        assertEquals("one\ntwo", out.text)
    }

    @Test
    fun `a blank selection on one line marks the line rather than wrapping nothing`() {
        assertEquals("2>a  b", toggleSecondVoice(value("a  b", 1, 3)).text)
    }

    // ── [voice: n] blocks into slides ───────────────────────────────────────────

    @Test
    fun `a voice block's lines become voice-2 lines and the directives make no line`() {
        val slides = lyricSlidesOf(
            listOf("[Verse 1]", "one", "[voice: 2]", "two", "three", "[voice: 1]", "four", "[Chorus]", "five"),
            "Song",
            "1",
        )

        assertEquals(listOf("one", "2>two", "2>three", "four"), slides[0].lines)
        assertEquals(listOf("five"), slides[1].lines)
    }

    @Test
    fun `a voice block ends at the next header`() {
        val slides = lyricSlidesOf(listOf("[Verse 1]", "[voice: 2]", "two", "[Verse 2]", "plain"), "Song", "1")

        assertEquals(listOf("2>two"), slides[0].lines)
        assertEquals(listOf("plain"), slides[1].lines)
    }

    @Test
    fun `a line that is markup alone shows nothing and is dropped`() {
        val slides = lyricSlidesOf(listOf("[Verse 1]", "words", "2>", "2>()"), "Song", "1")

        assertEquals(listOf("words"), slides[0].lines)
    }

    @Test
    fun `a voice block's chord lines lose the markup`() {
        val slides = lyricSlidesOf(listOf("[Verse 1]", "[G]one", "[voice: 2]", "[C]two"), "Song", "1")

        assertEquals(listOf("[G]one", "[C]two"), slides[0].chordLines)
        assertEquals(listOf("one", "2>two"), slides[0].lines)
    }

    // ── The slide under the caret ───────────────────────────────────────────────

    private val lyrics = "[Verse 1]\nAmazing grace\n2>That saved\nI once was lost\n\n[Verse 2]\nT'was grace"

    @Test
    fun `nothing to show is null`() {
        assertNull(caretSlideOf("", 0, "Song"))
        assertNull(caretSlideOf("[Verse 1]\n\n", 3, "Song"))
    }

    @Test
    fun `the caret's slide and line are found`() {
        val onAnswer = caretSlideOf(lyrics, lyrics.indexOf("saved"), "Song")!!
        assertEquals(0, onAnswer.index)
        assertEquals(1, onAnswer.lineIndex)
        assertEquals("2>That saved", onAnswer.section.lines[onAnswer.lineIndex])

        val second = caretSlideOf(lyrics, lyrics.length, "Song")!!
        assertEquals(1, second.index)
        assertEquals(0, second.lineIndex)
        assertEquals(2, second.slides.size)
    }

    @Test
    fun `a caret on a header or past the end stays in range`() {
        assertEquals(0, caretSlideOf(lyrics, 0, "Song")!!.index)
        assertEquals(1, caretSlideOf(lyrics, 9_999, "Song")!!.index)
        assertEquals(0, caretSlideOf(lyrics, -5, "Song")!!.lineIndex)
    }

    // ── Which profiles the previews draw with ───────────────────────────────────

    private fun profile(id: String, lowerThird: Boolean) = OutputProfile(
        id = id,
        name = id,
        displayMode =
            if (lowerThird) Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL else Constants.DISPLAY_MODE_FULLSCREEN,
    )

    @Test
    fun `the previews draw with the profiles the outputs follow first`() {
        val settings = AppSettings(
            projectionSettings = ProjectionSettings(
                outputProfiles = listOf(
                    profile("a", false),
                    profile("b", false),
                    profile("l1", true),
                    profile("l2", true),
                ),
                screenAssignments = listOf(
                    ScreenAssignment(activeProfileId = "l2"),
                    ScreenAssignment(activeProfileId = "b"),
                ),
            ),
        )

        val (full, band) = previewProfilesOf(settings)

        assertEquals("b", full?.id)
        assertEquals("l2", band?.id)
    }

    @Test
    fun `with no output on a kind the first of that kind is used, and none is null`() {
        val settings = AppSettings(
            projectionSettings = ProjectionSettings(
                outputProfiles = listOf(profile("a", false), profile("b", false)),
                screenAssignments = listOf(ScreenAssignment(activeProfileId = "missing")),
            ),
        )

        val (full, band) = previewProfilesOf(settings)

        assertEquals("a", full?.id)
        assertNull(band)
    }

    @Test
    fun `the verse and line switch sets every song display mode`() {
        val settings =
            AppSettings(songSettings = SongSettings(lowerThirdDisplayMode = Constants.SONG_DISPLAY_MODE_LINE))

        val verse = settings.withSongDisplayMode(Constants.SONG_DISPLAY_MODE_VERSE).songSettings
        val line = settings.withSongDisplayMode(Constants.SONG_DISPLAY_MODE_LINE).songSettings

        assertEquals(
            List(4) { Constants.SONG_DISPLAY_MODE_VERSE },
            verse.displayModes(),
        )
        assertEquals(
            List(4) { Constants.SONG_DISPLAY_MODE_LINE },
            line.displayModes(),
        )
    }

    private fun SongSettings.displayModes() =
        listOf(fullscreenDisplayMode, lowerThirdDisplayMode, lookAheadDisplayMode, lowerThirdLookAheadDisplayMode)

    // ── Navigating a song that has a second voice ───────────────────────────────

    private var dir: File? = null
    private val created = mutableListOf<SongsViewModel>()

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        dir?.deleteRecursively()
    }

    private fun viewModelOn(song: SongItem): SongsViewModel {
        val library = Files.createTempDirectory("cp-songs-second-voice").toFile().also { dir = it }
        SongFileParser().writeSongFile(song, File(File(library, "Hymnal"), "0001 - ${song.title}.song").absolutePath)
        val vm = SongsViewModel(
            AppSettings(songSettings = SongSettings(storageDirectory = library.absolutePath)),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
            enableFolderWatcher = false,
        )
        created.add(vm)
        assertTrue(vm.filteredSongItems.value.isNotEmpty(), "songs did not load synchronously")
        vm.selectSong(0)
        return vm
    }

    private val grace = SongItem(
        number = "0001",
        title = "Grace",
        songbook = "Hymnal",
        lyrics = listOf(
            "[Verse 1]", "Amazing grace", "2>That saved", "I once was lost",
            "[Verse 2]", "One", "[voice: 2]", "Two",
        ),
        textOverrides = SongTextOverrides(sizePercent = 120),
    )

    @Test
    fun `Next steps over a line and its answer together`() {
        val vm = viewModelOn(grace)

        assertTrue(vm.navigateNextLine())
        assertEquals(0, vm.selectedSectionIndex.value)
        assertEquals(2, vm.selectedLineIndex.value, "the voice-2 answer on line 1 shows with line 0")

        assertTrue(vm.navigateNextLine())
        assertEquals(1, vm.selectedSectionIndex.value)
        assertEquals(0, vm.selectedLineIndex.value)
        assertFalse(vm.navigateNextLine(), "Verse 2's two lines are one pair, the last on the song")
    }

    @Test
    fun `Previous lands on the start of a pair`() {
        val vm = viewModelOn(grace)
        vm.navigateNextLine()

        assertTrue(vm.navigatePreviousLine())
        assertEquals(0, vm.selectedLineIndex.value)

        vm.navigateNextSection()
        assertTrue(vm.navigatePreviousLine())
        assertEquals(0, vm.selectedSectionIndex.value)
        assertEquals(2, vm.selectedLineIndex.value, "back into Verse 1 at its last group")
    }

    @Test
    fun `the song's own look travels with its slides`() {
        val vm = viewModelOn(grace)

        assertTrue(vm.getLyricSections().all { it.textOverrides.sizePercent == 120 })
        assertEquals(120, vm.getSelectedSong()?.textOverrides?.sizePercent)
        val whole = vm.getSelectedSong()!!.lines
        assertTrue(whole.none { it.startsWith("[voice") }, "the whole-song slide drops directives")
        assertTrue("2>Two" in whole)
    }
}
