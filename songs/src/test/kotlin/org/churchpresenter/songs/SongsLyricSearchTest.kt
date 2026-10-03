package org.churchpresenter.songs

import kotlinx.coroutines.Dispatchers
import org.churchpresenter.core.models.songs.SongFileParser
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTranslation
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SongsLyricSearchTest {

    private lateinit var dir: File
    private val created = mutableListOf<SongsViewModel>()

    @BeforeTest
    fun createLibrary() {
        dir = Files.createTempDirectory("cp-songs-lyric-search-test").toFile()
        song(
            number = "1",
            title = "Amazing Grace",
            lyrics = listOf("[Verse 1]", "[G]Amazing grace how [C]sweet the sound", "That saved a wretch like me"),
        )
        song(
            number = "2",
            title = "Be Thou My Vision",
            lyrics = listOf("[Verse 1]", "Be Thou my vision", "O Lord of my heart"),
            translations = listOf(SongTranslation(title = "Будь мне виденьем", lyrics = listOf("Сердца владыка"))),
        )
        song(
            number = "3",
            title = "Holy Holy Holy",
            lyrics = listOf("[Chorus]", "Lord God Almighty"),
        )
        song(
            number = "12",
            title = "Psalm 23",
            lyrics = listOf("[Verse 1]", "My shepherd leads me, 3 times over"),
        )
    }

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        dir.deleteRecursively()
    }

    private fun song(
        number: String,
        title: String,
        lyrics: List<String>,
        translations: List<SongTranslation> = emptyList(),
    ) {
        val item = SongItem(number = number, title = title, songbook = "Hymnal", lyrics = lyrics)
            .withTranslations(translations)
        SongFileParser().writeSongFile(item, File(File(dir, "Hymnal"), "$number - $title.song").absolutePath)
    }

    private fun viewModel(filterType: String = Constants.CONTAINS): SongsViewModel {
        val vm = SongsViewModel(
            AppSettings(songSettings = SongSettings(storageDirectory = dir.absolutePath)),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
            enableFolderWatcher = false,
        )
        created.add(vm)
        assertEquals(4, vm.filteredSongItems.value.size, "the library loads synchronously")
        vm.updateSort(Constants.SORT_NUMBER)
        vm.updateFilterType(filterType)
        return vm
    }

    private fun SongsViewModel.search(query: String): List<String> {
        updateSearchQuery(query)
        return filteredSongItems.value.map { it.title }
    }

    @Test
    fun `contains finds a song by a line of its lyrics`() {
        assertEquals(listOf("Amazing Grace"), viewModel().search("wretch like me"))
    }

    @Test
    fun `contains matches lyrics and titles together`() {
        assertEquals(listOf("Be Thou My Vision", "Holy Holy Holy"), viewModel().search("lord"))
    }

    @Test
    fun `chords do not break a phrase in the lyrics`() {
        assertEquals(listOf("Amazing Grace"), viewModel().search("how sweet the sound"))
    }

    @Test
    fun `a phrase running across two lines is found`() {
        assertEquals(listOf("Amazing Grace"), viewModel().search("the sound that saved"))
    }

    @Test
    fun `section headers are not searched`() {
        assertEquals(emptyList(), viewModel().search("Chorus"))
    }

    @Test
    fun `lyrics of a translation are searched`() {
        assertEquals(listOf("Be Thou My Vision"), viewModel().search("владыка"))
    }

    @Test
    fun `starts-with does not look inside lyrics`() {
        assertTrue(viewModel(Constants.STARTS_WITH).search("That saved").isEmpty())
    }

    @Test
    fun `exact match does not look inside lyrics`() {
        assertTrue(viewModel(Constants.EXACT_MATCH).search("Lord God Almighty").isEmpty())
    }

    // ── Digits are a song number ────────────────────────────────────────────────

    @Test
    fun `digits alone search only song numbers, not titles or lyrics`() {
        assertEquals(listOf("Holy Holy Holy"), viewModel().search("3"))
        assertEquals(emptyList(), viewModel().search("23"))
    }

    @Test
    fun `each filter type applies to the number`() {
        assertEquals(listOf("Amazing Grace", "Psalm 23"), viewModel().search("1"))
        assertEquals(listOf("Amazing Grace", "Psalm 23"), viewModel(Constants.STARTS_WITH).search("1"))
        assertEquals(listOf("Amazing Grace"), viewModel(Constants.EXACT_MATCH).search("1"))
    }

    @Test
    fun `digits beside letters are text, and search titles and lyrics`() {
        assertEquals(listOf("Psalm 23"), viewModel().search("psalm 23"))
    }

    // ── Where it matched ────────────────────────────────────────────────────────

    private fun SongsViewModel.matchFor(title: String) =
        searchMatchFor(filteredSongItems.value.first { it.title == title })

    @Test
    fun `a text search says where each song matched`() {
        val vm = viewModel()
        vm.search("lord")
        assertEquals(SongMatchKind.VERSE, vm.matchFor("Be Thou My Vision")?.kind)
        assertEquals("Chorus", vm.matchFor("Holy Holy Holy")?.sectionName)

        vm.search("владыка")
        assertEquals(1, vm.matchFor("Be Thou My Vision")?.languageIndex)

        vm.search("psalm")
        assertEquals(SongMatchKind.TITLE, vm.matchFor("Psalm 23")?.kind)
    }

    @Test
    fun `a number search and an empty box have nothing to say`() {
        val vm = viewModel()
        vm.search("12")
        assertEquals(null, vm.matchFor("Psalm 23"))
        vm.search("")
        assertEquals(null, vm.matchFor("Psalm 23"))
    }
}
