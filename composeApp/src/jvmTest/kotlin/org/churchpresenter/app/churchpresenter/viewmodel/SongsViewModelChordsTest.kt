package org.churchpresenter.app.churchpresenter.viewmodel

import kotlinx.coroutines.Dispatchers
import org.churchpresenter.core.models.songs.SongFileParser
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What happens to a song's chords on the way to being presented.
 *
 * Two guarantees hold this together. `lines` is what the audience reads and is always stripped, so
 * no presenter can put a chord on screen; `chordLines` carries the same section as written, for the
 * stage monitor alone. And a section that is nothing but chords — an intro — is folded into the one
 * it leads into, because it has no words to show and would otherwise sit in the list as a blank
 * slide with the chorus auto-repeat inserting a chorus behind it.
 */
class SongsViewModelChordsTest {

    private lateinit var dir: File
    private val created = mutableListOf<SongsViewModel>()

    @BeforeTest
    fun createLibrary() {
        dir = Files.createTempDirectory("cp-songs-chords-test").toFile()
    }

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        dir.deleteRecursively()
    }

    private fun sectionsOf(lyrics: List<String>): List<LyricSection> {
        val target = File(File(dir, "Hymnal"), "0001 - Test.song")
        SongFileParser().writeSongFile(
            SongItem(number = "0001", title = "Test", songbook = "Hymnal", lyrics = lyrics),
            target.absolutePath,
        )
        val vm = SongsViewModel(
            AppSettings(songSettings = SongSettings(storageDirectory = dir.absolutePath)),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
            enableFolderWatcher = false,
        )
        created.add(vm)
        if (vm.filteredSongItems.value.isEmpty()) throw AssertionError("songs did not load synchronously")
        vm.selectSong(0)
        return vm.getLyricSections()
    }

    @Test
    fun `the words the audience sees never carry a chord`() {
        val sections = sectionsOf(listOf("[Verse 1]", "[G]one two [C]three"))

        assertEquals(listOf("one two three"), sections[0].lines)
    }

    @Test
    fun `the section keeps its lines as written for the band`() {
        val sections = sectionsOf(listOf("[Verse 1]", "[G]one two [C]three"))

        assertEquals(listOf("[G]one two [C]three"), sections[0].chordLines)
    }

    @Test
    fun `a song with no chords carries no chart at all`() {
        val sections = sectionsOf(listOf("[Verse 1]", "one two three"))

        assertTrue(sections[0].chordLines.isEmpty(), "otherwise the chord zone just repeats the words")
    }

    @Test
    fun `a chords-only line produces no slide`() {
        val sections = sectionsOf(listOf("[Verse 1]", "[Cm] [Bb]", "one two"))

        assertEquals(listOf("one two"), sections[0].lines)
    }

    @Test
    fun `an intro is folded into the section it leads into`() {
        val sections = sectionsOf(
            listOf("[Intro]", "[Cm] [Bb]", "[Verse 1]", "[G]one two"),
        )

        assertEquals(1, sections.size, "the intro is not a section of its own")
        assertEquals("[Verse 1]", sections[0].header)
    }

    @Test
    fun `the folded intro's chords lead the chart, with its name`() {
        val sections = sectionsOf(
            listOf("[Intro]", "[Cm] [Bb]", "[Verse 1]", "[G]one two"),
        )

        assertEquals(listOf("[Intro]", "[Cm] [Bb]", "[G]one two"), sections[0].chordLines)
    }

    @Test
    fun `folding the intro keeps the chorus behind the verse it belongs to`() {
        // A bracketed header is typed as a verse, so an intro left standing pulled the chorus
        // auto-repeat in front of verse 1.
        val sections = sectionsOf(
            listOf("[Intro]", "[Cm] [Bb]", "[Verse 1]", "[G]one two", "{Chorus}", "[C]three four"),
        )

        assertEquals(listOf("[Verse 1]", "{Chorus}"), sections.map { it.header })
    }

    @Test
    fun `a section without its own chords still charts its words under a folded intro`() {
        val sections = sectionsOf(listOf("[Intro]", "[Cm] [Bb]", "[Verse 1]", "one two"))

        assertEquals(listOf("[Intro]", "[Cm] [Bb]", "one two"), sections[0].chordLines)
    }

    @Test
    fun `an empty header with no chords is left alone`() {
        // Navigation already steps over an empty section; folding is only for chord-only ones.
        val sections = sectionsOf(listOf("[Verse 1]", "one", "[Bridge]", "[Verse 2]", "two"))

        assertEquals(3, sections.size)
        assertTrue(sections[1].lines.isEmpty())
        assertFalse(sections[1].chordLines.isNotEmpty())
    }
}
