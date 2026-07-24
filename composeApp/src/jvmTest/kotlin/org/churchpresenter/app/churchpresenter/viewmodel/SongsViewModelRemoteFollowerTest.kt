package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.SongSettings
import org.churchpresenter.app.churchpresenter.server.SongCatalogResponse
import org.churchpresenter.app.churchpresenter.server.SongDetailDto
import org.churchpresenter.app.churchpresenter.server.SongDto
import org.churchpresenter.app.churchpresenter.server.SongSectionDto
import org.churchpresenter.app.churchpresenter.server.SongbookEntry
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Following an Instance Link primary: the library is mirrored from a catalog with no lyrics, and
 * each song's lyrics are fetched on demand the first time it is selected.
 *
 * This is exercised with plain fakes — a real [SongCatalogResponse] and a stand-in `fetchDetail`
 * lambda returning a constructed [SongDetailDto] — driving the real remote code path
 * (`setInstanceLinkSource` → `selectSong` → `fetchRemoteDetailIfNeeded` → `toRawLyrics`). The fetch
 * runs on the ViewModel's `Dispatchers.Main` scope (the Swing EDT under test), so success is awaited
 * on the populated lyrics and the no-op cases are drained with `invokeAndWait` rather than a sleep.
 */
class SongsViewModelRemoteFollowerTest {

    private lateinit var dir: File
    private val created = mutableListOf<SongsViewModel>()
    private val fetchCount = AtomicInteger(0)

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-songs-remote-test").toFile()
        fetchCount.set(0)
    }

    @AfterTest
    fun tearDown() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        dir.deleteRecursively()
    }

    /** A one-song catalog (no lyrics — the follower fetches those on demand). */
    private fun catalog(number: String = "0042", title: String = "Grace") = SongCatalogResponse(
        songBook = listOf(
            SongbookEntry(bookName = "Hymnal", songTotal = 1, songs = listOf(SongDto(number = number, title = title))),
        ),
        songBooks = 1, total = 1,
    )

    /** A detail with one verse and one chorus, to exercise both header branches of toRawLyrics. */
    private fun detail(number: String) = SongDetailDto(
        number = number, title = "Grace", songbook = "Hymnal", tune = "", author = "", composer = "",
        sectionTotal = 2,
        sections = listOf(
            SongSectionDto(type = "verse", lines = listOf("verse line")),
            SongSectionDto(type = "chorus", lines = listOf("chorus line")),
        ),
    )

    /** A follower whose fetch is [detailFor], counting every call. */
    private fun follower(detailFor: (String, String) -> SongDetailDto?): SongsViewModel {
        val vm = SongsViewModel(AppSettings(songSettings = SongSettings(storageDirectory = dir.absolutePath)))
        created.add(vm)
        vm.setInstanceLinkSource(
            active = true,
            catalog = catalog(),
            fetchDetail = { n, s -> fetchCount.incrementAndGet(); detailFor(n, s) },
        )
        awaitUntil("the mirrored catalog") { vm.filteredSongItems.value.isNotEmpty() }
        return vm
    }

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    /** Drains the EDT so a fetch launched on Dispatchers.Main has run to completion. */
    private fun drainMain() = SwingUtilities.invokeAndWait {}

    // ── The fetch happens and its result is formatted ────────────────────────────

    @Test
    fun `selecting a song fetches its lyrics and rebuilds the raw header format`() {
        val vm = follower { _, _ -> detail("0042") }

        vm.selectSong(0)

        awaitUntil("the fetched lyrics") { vm.filteredSongItems.value[0].lyrics.isNotEmpty() }
        assertEquals(
            listOf("[Verse]", "verse line", "{Chorus}", "chorus line"),
            vm.filteredSongItems.value[0].lyrics,
            "the API's section types become the parser's [Verse]/{Chorus} headers",
        )
        assertEquals(1, fetchCount.get())
    }

    // ── The no-op branches ───────────────────────────────────────────────────────

    @Test
    fun `a fetch that returns nothing leaves the song without lyrics`() {
        val vm = follower { _, _ -> null }

        vm.selectSong(0)
        drainMain() // let the launched fetch run and take the detail == null branch

        assertTrue(vm.filteredSongItems.value[0].lyrics.isEmpty(), "no detail means nothing to show")
        assertEquals(1, fetchCount.get(), "the fetch was attempted")
    }

    @Test
    fun `a song that already has its lyrics is not fetched again`() {
        val vm = follower { _, _ -> detail("0042") }
        vm.selectSong(0)
        awaitUntil("the fetched lyrics") { vm.filteredSongItems.value[0].lyrics.isNotEmpty() }
        val afterFirst = fetchCount.get()

        vm.selectSong(0) // lyrics now present — the guard returns before launching a fetch

        assertEquals(afterFirst, fetchCount.get(), "re-selecting a loaded song must not re-hit the primary")
    }

    @Test
    fun `with no fetch function configured a selection fetches nothing`() {
        val vm = SongsViewModel(AppSettings(songSettings = SongSettings(storageDirectory = dir.absolutePath)))
        created.add(vm)
        vm.setInstanceLinkSource(active = true, catalog = catalog(), fetchDetail = null)
        awaitUntil("the mirrored catalog") { vm.filteredSongItems.value.isNotEmpty() }

        vm.selectSong(0)
        drainMain()

        assertTrue(vm.filteredSongItems.value[0].lyrics.isEmpty())
        assertEquals(0, fetchCount.get(), "there is no fetch function to call")
    }

    @Test
    fun `selecting an out-of-range index does not fetch`() {
        val vm = follower { _, _ -> detail("0042") }

        vm.selectSong(999)
        drainMain()

        assertEquals(0, fetchCount.get(), "there is no song at that index to fetch")
    }
}
