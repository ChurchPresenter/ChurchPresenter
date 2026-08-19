package org.churchpresenter.app.churchpresenter.viewmodel

import kotlinx.coroutines.Dispatchers

import core.models.songs.SongFileParser
import core.models.songs.SongItem
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.SongSettings
import org.churchpresenter.app.churchpresenter.utils.Constants
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Section and line navigation across an EMPTY section.
 *
 * A song can carry a section header with no lines under it (a placeholder "Bridge", a blank verse).
 * Navigation must step over such a section rather than landing the operator on a blank slide — the
 * `while` loops in the navigate* methods skip empty sections, and those skip iterations are only
 * reached when an empty section actually sits between two sung ones. The library tests use songs
 * with no empty sections, so those skip branches never ran.
 */
class SongsViewModelNavigationEdgeTest {

    private lateinit var dir: File
    private val created = mutableListOf<SongsViewModel>()

    @BeforeTest
    fun createLibrary() {
        dir = Files.createTempDirectory("cp-songs-nav-edge-test").toFile()
        // Verse 1 (two lines) — Bridge (no lines) — Verse 2 (one line).
        writeSong(
            songbook = "Hymnal", number = "0001", title = "Empty Middle",
            lyrics = listOf(
                "[Verse 1]", "V1 line one", "V1 line two",
                "[Bridge]",            // header with no lines -> an empty section
                "[Verse 2]", "V2 line one",
            ),
        )
        // Five sections, so a selection late in this song is out of range in the one above.
        writeSong(
            songbook = "Hymnal", number = "0002", title = "Long Song",
            lyrics = (1..5).flatMap { listOf("[Verse $it]", "V$it line one") },
        )
    }

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        dir.deleteRecursively()
    }

    private fun writeSong(songbook: String, number: String, title: String, lyrics: List<String>) {
        val target = File(File(dir, songbook), "$number - $title.song")
        SongFileParser().writeSongFile(
            SongItem(number = number, title = title, songbook = songbook, lyrics = lyrics),
            target.absolutePath,
        )
    }

    private fun viewModel(): SongsViewModel {
        val vm = SongsViewModel(
            AppSettings(songSettings = SongSettings(storageDirectory = dir.absolutePath)),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
            enableFolderWatcher = false,
        )
        created.add(vm)
        // Immediate dispatchers, so the load is done by the time the constructor returns.
        if (vm.filteredSongItems.value.isEmpty()) throw AssertionError("songs did not load synchronously")
        vm.selectByTitle("Empty Middle")
        return vm
    }

    /** Selects by title — the order the two songs load in is not guaranteed. */
    private fun SongsViewModel.selectByTitle(title: String) {
        val index = filteredSongItems.value.indexOfFirst { it.title == title }
        assertTrue(index >= 0, "no song titled $title")
        selectSong(index)
    }

    @Test
    fun `the song really has an empty middle section`() {
        // The precondition the other tests rely on: the empty header survives parsing as its own
        // section (index 1), between the two sung ones.
        val sections = viewModel().getLyricSections()
        assertEquals(3, sections.size)
        assertTrue(sections[1].lines.isEmpty(), "the Bridge header carries no lines")
    }

    @Test
    fun `stepping a section forward skips the empty one`() {
        val vm = viewModel() // starts on section 0

        assertTrue(vm.navigateNextSection())

        assertEquals(2, vm.selectedSectionIndex.value, "the empty section at index 1 is stepped over")
    }

    @Test
    fun `stepping a section back skips the empty one`() {
        val vm = viewModel()
        vm.navigateNextSection() // now on section 2

        assertTrue(vm.navigatePreviousSection())

        assertEquals(0, vm.selectedSectionIndex.value)
    }

    @Test
    fun `stepping a line off the end of a section skips the empty section`() {
        val vm = viewModel() // section 0, line 0
        vm.navigateNextLine() // -> section 0, line 1 (last line of Verse 1)

        assertTrue(vm.navigateNextLine(), "off the end of Verse 1 it must cross to a sung section")

        assertEquals(2, vm.selectedSectionIndex.value, "the empty Bridge is skipped, landing on Verse 2")
        assertEquals(0, vm.selectedLineIndex.value)
    }

    @Test
    fun `stepping a line back off the start of a section skips the empty section`() {
        val vm = viewModel()
        vm.navigateNextSection() // section 2, line 0

        assertTrue(vm.navigatePreviousLine(), "off the start of Verse 2 it must cross back to a sung section")

        assertEquals(0, vm.selectedSectionIndex.value, "the empty Bridge is skipped, landing on Verse 1")
        assertEquals(1, vm.selectedLineIndex.value, "and resumes at the last line of that section")
    }

    // ── A section selection that outlives the song it indexes ───────────────────
    //
    // The section list is recomputed from the selected song on every call, so anything that puts a
    // different — shorter — song under the same row leaves the index pointing past the end. Walking
    // *down* from such an index used to read past the end of the list on its very first step and
    // kill the app (Sentry 042c9c37: "Index 3 out of bounds for length 3", released 26.4.203).

    /** Leaves only the three-section song, with the selection on section 4 of the five-section one. */
    private fun SongsViewModel.selectLateThenFilterToShortSong() {
        selectByTitle("Long Song")
        selectSection(4)
        assertEquals(4, selectedSectionIndex.value, "the long song really does reach section 4")

        updateSearchQuery("Empty")

        assertEquals(1, filteredSongItems.value.size, "only the short song survives the search")
        assertEquals(3, getLyricSections().size, "and it is the three-section song")
    }

    @Test
    fun `stepping a section back after a shorter song takes the row does not read past the end`() {
        val vm = viewModel()
        vm.selectLateThenFilterToShortSong()

        vm.navigatePreviousSection() // threw IndexOutOfBoundsException before the selection was clamped

        assertTrue(
            vm.selectedSectionIndex.value in vm.getLyricSections().indices,
            "the selection lands inside the song that is actually selected",
        )
    }

    @Test
    fun `stepping a line back after a shorter song takes the row does not read past the end`() {
        val vm = viewModel()
        vm.selectLateThenFilterToShortSong()

        vm.navigatePreviousLine()

        assertTrue(vm.selectedSectionIndex.value in vm.getLyricSections().indices)
    }

    @Test
    fun `a search that swaps in a shorter song pulls the section selection back into it`() {
        val vm = viewModel()

        vm.selectLateThenFilterToShortSong()

        assertEquals(2, vm.selectedSectionIndex.value, "clamped to the last section of the short song")
        assertEquals(0, vm.selectedLineIndex.value, "and to that section's first line")
    }

    @Test
    fun `a search that swaps in a song with shorter sections pulls the line selection back in`() {
        // The section index survives untouched here — the new song has more sections, not fewer —
        // so the line index is the only thing left pointing outside the song. Verse 1 has two lines
        // in "Empty Middle" and one in "Long Song", and a line index of 1 in a one-line section
        // shows nothing at all while the list highlights a line that is not there.
        val vm = viewModel()
        vm.selectByTitle("Empty Middle")
        vm.selectSection(0)
        vm.setLineIndex(1)

        vm.updateSearchQuery("Long")

        assertEquals("Long Song", vm.filteredSongItems.value[vm.selectedSongIndex.value].title)
        assertEquals(0, vm.selectedSectionIndex.value, "the section index was already in range")
        assertEquals(0, vm.selectedLineIndex.value, "the line index is pulled into the shorter verse")
    }

    @Test
    fun `a re-sort that puts a shorter song under the selection re-clamps it`() {
        val vm = viewModel()
        vm.updateSort(Constants.SORT_TITLE) // ascending: Empty Middle, Long Song
        vm.selectByTitle("Long Song")
        vm.selectSection(4)

        vm.updateSort(Constants.SORT_TITLE) // same column again -> descending, the songs swap rows

        assertEquals("Empty Middle", vm.filteredSongItems.value[vm.selectedSongIndex.value].title)
        assertEquals(2, vm.selectedSectionIndex.value)
    }

    @Test
    fun `selecting a section past the end lands on the last real section`() {
        val vm = viewModel() // the three-section song

        vm.selectSection(9) // e.g. a stale index replayed by Back-to-Live or pushed from a phone

        assertEquals(2, vm.selectedSectionIndex.value)
    }

    @Test
    fun `selecting section -1 still selects the whole-song slide`() {
        val vm = viewModel()

        vm.selectSection(-1)

        assertEquals(-1, vm.selectedSectionIndex.value, "-1 is the title slide, not an out-of-range index")
    }
}
