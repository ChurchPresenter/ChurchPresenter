package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.data.SongFileParser
import org.churchpresenter.app.churchpresenter.data.SongItem
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.SongSettings
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
        val vm = SongsViewModel(AppSettings(songSettings = SongSettings(storageDirectory = dir.absolutePath)))
        created.add(vm)
        val deadline = System.currentTimeMillis() + 5_000
        while (vm.filteredSongItems.value.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        vm.selectSong(0)
        return vm
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
}
