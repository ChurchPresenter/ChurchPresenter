package org.churchpresenter.songs

import kotlinx.coroutines.Dispatchers
import org.churchpresenter.core.models.songs.SongFileParser
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongsViewModelStateCoverageTest {

    private lateinit var dir: File
    private val dirs = mutableListOf<File>()
    private val created = mutableListOf<SongsViewModel>()

    @BeforeTest
    fun createLibrary() {
        dir = newDir()
        write(dir, SongItem("1", "Amazing Grace", "Hymnal", lyrics = listOf("[Verse 1]", "grace line")))
        write(dir, SongItem("2", "Be Thou My Vision", "Hymnal", lyrics = listOf("[Verse 1]", "vision line")))
        write(
            dir,
            SongItem(
                "3", "How Great Thou Art", "Chorus Book",
                lyrics = listOf("[Verse 1]", "great line"),
                secondaryTitle = "Wie gross bist du",
                secondaryLyrics = listOf("[Verse 1]", "gross zeile"),
            ),
        )
    }

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        dirs.forEach { it.deleteRecursively() }
        dirs.clear()
    }

    private fun newDir(): File = Files.createTempDirectory("cp-songs-state-cov").toFile().also { dirs += it }

    private fun write(root: File, song: SongItem) {
        val file = File(File(root, song.songbook), "${song.number} - ${song.title}.song")
        SongFileParser().writeSongFile(song, file.absolutePath)
    }

    private fun settings(root: File = dir, favorites: List<String> = emptyList()) =
        AppSettings(songSettings = SongSettings(storageDirectory = root.absolutePath), songFavorites = favorites)

    private fun viewModel(
        settings: AppSettings = settings(),
        onLoaded: ((List<SongItem>) -> Unit)? = null,
        log: (String, Map<String, Any?>) -> Unit = { _, _ -> },
        watch: Boolean = false,
    ): SongsViewModel = SongsViewModel(
        settings,
        onSongsLoaded = onLoaded,
        dispatcher = Dispatchers.Unconfined,
        ioDispatcher = Dispatchers.Unconfined,
        enableFolderWatcher = watch,
        remoteSyncLog = log,
    ).also { created += it }

    private fun emptyViewModel() = viewModel(settings(newDir()))

    private val SongsViewModel.titles get() = filteredSongItems.value.map { it.title }

    private fun SongsViewModel.selectedTitle() = filteredSongItems.value[selectedSongIndex.value].title

    // ── Loading ──────────────────────────────────────────────────────────────────

    @Test
    fun `the loaded callback receives the library`() {
        val loaded = mutableListOf<List<SongItem>>()
        viewModel(onLoaded = { loaded += it })

        assertTrue(loaded.isNotEmpty())
        assertEquals(3, loaded.last().size)
    }

    @Test
    fun `loading finishes with the loading flag down`() {
        assertFalse(viewModel().isLoading.value)
    }

    @Test
    fun `the songs data holds every loaded song`() {
        assertEquals(3, viewModel().songsData.value.getSongCount())
    }

    @Test
    fun `a second view model over the same folder reads the saved cache`() {
        val first = viewModel().titles.sorted()

        val second = viewModel().titles.sorted()

        assertEquals(first, second)
    }

    @Test
    fun `a view model watching its folder still loads and disposes cleanly`() {
        val vm = viewModel(watch = true)

        assertEquals(3, vm.filteredSongItems.value.size)
        vm.dispose()
    }

    @Test
    fun `disposing twice is harmless`() {
        val vm = viewModel()
        vm.dispose()
        vm.dispose()
        assertEquals(3, vm.filteredSongItems.value.size)
    }

    @Test
    fun `reloading after dispose leaves the list as it was`() {
        val vm = viewModel()
        vm.dispose()
        write(dir, SongItem("9", "Late Song", "Hymnal", lyrics = listOf("[Verse 1]", "late")))

        vm.loadSongs()

        assertFalse("Late Song" in vm.titles)
    }

    @Test
    fun `new settings load the library from the new folder`() {
        val other = newDir()
        write(other, SongItem("7", "Other Song", "Elsewhere", lyrics = listOf("[Verse 1]", "x")))
        val vm = viewModel()

        vm.updateSettings(settings(other))

        assertEquals(listOf("Other Song"), vm.titles)
        assertEquals(listOf("Elsewhere"), vm.songbooks.value)
    }

    @Test
    fun `new settings replace the favorites`() {
        val vm = viewModel()
        vm.toggleFavorite("Hymnal::1")

        vm.updateSettings(settings(favorites = listOf("Chorus Book::3")))

        assertEquals(setOf("Chorus Book::3"), vm.favorites.value)
    }

    @Test
    fun `favorites start from the settings`() {
        val vm = viewModel(settings(favorites = listOf("Hymnal::2")))

        assertEquals(setOf("Hymnal::2"), vm.favorites.value)
        assertEquals(listOf("Be Thou My Vision"), vm.getFavoriteSongs().map { it.title })
    }

    // ── Play counts and sorting ──────────────────────────────────────────────────

    @Test
    fun `attaching play counts while sorted by plays re-sorts at once`() {
        val vm = viewModel()
        vm.updateSort(Constants.SORT_PLAY_COUNT)
        val unsorted = vm.titles

        vm.setPlayCounts { id -> if (id == "Chorus Book::3") 0 else if (id == "Hymnal::1") 5 else 2 }

        assertEquals(listOf("How Great Thou Art", "Be Thou My Vision", "Amazing Grace"), vm.titles)
        assertEquals(3, unsorted.size)
    }

    @Test
    fun `attaching play counts under another sort leaves the order alone`() {
        val vm = viewModel()
        vm.updateSort(Constants.SORT_TITLE)
        val before = vm.titles

        vm.setPlayCounts { 1 }

        assertEquals(before, vm.titles)
    }

    @Test
    fun `play count descending puts the most sung first`() {
        val vm = viewModel()
        vm.setPlayCounts { id -> if (id == "Hymnal::2") 9 else 0 }

        vm.updateSort(Constants.SORT_PLAY_COUNT)
        vm.updateSort(Constants.SORT_PLAY_COUNT)

        assertFalse(vm.sortAscending.value)
        assertEquals("Be Thou My Vision", vm.titles.first())
    }

    @Test
    fun `favorites descending puts the favorites last`() {
        val vm = viewModel()
        vm.toggleFavorite("Hymnal::1")

        vm.updateSort(Constants.SORT_FAVORITES)
        assertEquals("Amazing Grace", vm.titles.first())
        vm.updateSort(Constants.SORT_FAVORITES)

        assertEquals("Amazing Grace", vm.titles.last())
    }

    @Test
    fun `the sort indicator is blank for a column not sorted by`() {
        val vm = viewModel()
        vm.updateSort(Constants.SORT_TITLE)

        assertEquals("", vm.getSortIndicator(Constants.SORT_NUMBER))
        assertEquals(" ↑", vm.getSortIndicator(Constants.SORT_TITLE))
    }

    // ── Filters ──────────────────────────────────────────────────────────────────

    @Test
    fun `the query and filter type are held as typed`() {
        val vm = viewModel()

        vm.updateSearchQuery("  grace ")
        vm.updateFilterType(Constants.STARTS_WITH)

        assertEquals("  grace ", vm.searchQuery.value)
        assertEquals(Constants.STARTS_WITH, vm.filterType.value)
    }

    @Test
    fun `clearing the songbook lists every book again`() {
        val vm = viewModel()
        vm.updateSelectedSongbook("Hymnal")
        assertEquals(2, vm.filteredSongItems.value.size)

        vm.updateSelectedSongbook("")

        assertEquals(3, vm.filteredSongItems.value.size)
    }

    @Test
    fun `a selection past the narrowed list goes back to the top`() {
        val vm = viewModel()
        vm.selectSong(2)

        vm.updateSearchQuery("Vision")

        assertEquals(0, vm.selectedSongIndex.value)
        assertEquals("Be Thou My Vision", vm.selectedTitle())
    }

    @Test
    fun `a number query under an unknown filter type leaves the list whole`() {
        val vm = viewModel()
        vm.updateFilterType("nonsense")

        vm.updateSearchQuery("1")

        assertEquals(3, vm.filteredSongItems.value.size)
    }

    @Test
    fun `contains finds a song by its translated title`() {
        val vm = viewModel()

        vm.updateSearchQuery("gross bist")

        assertEquals(listOf("How Great Thou Art"), vm.titles)
    }

    @Test
    fun `starts-with finds a song by its translated title`() {
        val vm = viewModel()
        vm.updateFilterType(Constants.STARTS_WITH)

        vm.updateSearchQuery("wie")

        assertEquals(listOf("How Great Thou Art"), vm.titles)
    }

    @Test
    fun `exact match finds a song by its whole translated title`() {
        val vm = viewModel()
        vm.updateFilterType(Constants.EXACT_MATCH)

        vm.updateSearchQuery("wie gross bist du")

        assertEquals(listOf("How Great Thou Art"), vm.titles)
    }

    @Test
    fun `a search match is the same on a second ask`() {
        val vm = viewModel()
        vm.updateSearchQuery("grace")
        val song = vm.filteredSongItems.value.first()

        val first = vm.searchMatchFor(song)

        assertNotNull(first)
        assertEquals(first, vm.searchMatchFor(song))
    }

    // ── Selection and navigation ─────────────────────────────────────────────────

    @Test
    fun `a song is selected by its id alone`() {
        val vm = viewModel()

        assertTrue(vm.selectSongById("Hymnal::2"))
        assertEquals("Be Thou My Vision", vm.selectedTitle())
    }

    @Test
    fun `selecting by id reveals a song the search hid`() {
        val vm = viewModel()
        vm.updateSearchQuery("Grace")

        assertTrue(vm.selectSongById("Chorus Book::3"))

        assertEquals("", vm.searchQuery.value)
        assertEquals("How Great Thou Art", vm.selectedTitle())
    }

    @Test
    fun `an unknown id selects nothing`() {
        assertFalse(viewModel().selectSongById("Nowhere::99"))
    }

    @Test
    fun `a stale id falls back to songbook and number`() {
        val vm = viewModel()

        assertTrue(vm.selectSongByDetails(2, "", "Hymnal", songId = "Gone::2"))

        assertEquals("Be Thou My Vision", vm.selectedTitle())
    }

    @Test
    fun `selecting a song resets section and line`() {
        val vm = viewModel()
        vm.selectSection(-1)
        vm.setLineIndex(4)

        vm.selectSong(1)

        assertEquals(0, vm.selectedSectionIndex.value)
        assertEquals(0, vm.selectedLineIndex.value)
    }

    @Test
    fun `the line index is set as given`() {
        val vm = viewModel()

        vm.setLineIndex(3)

        assertEquals(3, vm.selectedLineIndex.value)
    }

    @Test
    fun `stepping to the next song starts at the whole-song slide`() {
        val vm = viewModel()
        vm.selectSong(0)

        assertTrue(vm.navigateNextSong())

        assertEquals(1, vm.selectedSongIndex.value)
        assertEquals(-1, vm.selectedSectionIndex.value)
    }

    @Test
    fun `stepping to the previous song starts at the whole-song slide`() {
        val vm = viewModel()
        vm.selectSong(2)

        assertTrue(vm.navigatePreviousSong())

        assertEquals(1, vm.selectedSongIndex.value)
        assertEquals(-1, vm.selectedSectionIndex.value)
    }

    @Test
    fun `an empty library has no song to step to`() {
        val vm = emptyViewModel()

        assertFalse(vm.navigateNextSong())
        assertFalse(vm.navigatePreviousSong())
    }

    @Test
    fun `an empty library has no section or line to step to`() {
        val vm = emptyViewModel()

        assertFalse(vm.navigateNextSection())
        assertFalse(vm.navigatePreviousSection())
        assertFalse(vm.navigateNextLine())
        assertFalse(vm.navigatePreviousLine())
    }

    @Test
    fun `a selection past the end has no sections`() {
        val vm = viewModel()

        vm.selectSong(99)

        assertTrue(vm.getLyricSections().isEmpty())
        assertNull(vm.getSelectedLyricSection())
    }

    @Test
    fun `the whole-song slide carries the song's translation`() {
        val vm = viewModel()
        vm.selectSongById("Chorus Book::3")

        val song = assertNotNull(vm.getSelectedSong())

        assertEquals(Constants.SECTION_TYPE_SONG, song.type)
        assertEquals(3, song.songNumber)
        assertEquals("Wie gross bist du", song.translations.first().title)
        assertTrue("gross zeile" in song.translations.first().lines)
    }

    @Test
    fun `sections of an arbitrary song need no selection`() {
        val vm = emptyViewModel()

        val sections = vm.getLyricSections(
            SongItem("5", "Loose", lyrics = listOf("[Verse 1]", "one", "[Verse 2]", "two")),
        )

        assertEquals(listOf(listOf("one"), listOf("two")), sections.map { it.lines })
        assertTrue(sections.last().isLastSection)
        assertFalse(sections.first().isLastSection)
    }
}
