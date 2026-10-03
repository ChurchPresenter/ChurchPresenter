package org.churchpresenter.songs

import kotlinx.coroutines.Dispatchers
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongSettings
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Following an Instance Link primary: the library is mirrored from a catalog with no lyrics, and
 * each song's lyrics are fetched on demand the first time it is selected.
 *
 * This is exercised with plain fakes — a catalog of [SongItem]s and a stand-in `fetchLyrics` lambda
 * — driving the real remote code path (`setInstanceLinkSource` → `selectSong` →
 * `fetchRemoteDetailIfNeeded`). The app's conversion from the wire DTOs is `RemoteSongCatalogTest`.
 * The fetch runs on the ViewModel's `Dispatchers.Main` scope, so every wait here ends on a positive
 * signal — the populated lyrics, or the fetch counter being bumped from inside the fetch itself.
 * The cases where nothing is launched at all assert straight away, because `selectSong` returns
 * before starting any coroutine on those paths.
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
    private fun catalog(number: String = "0042", title: String = "Grace") =
        listOf(SongItem(number = number, title = title, songbook = "Hymnal"))

    /** A song's raw lyrics, a verse and a chorus, as the app rebuilds them from the primary's detail. */
    private fun detail(number: String) = listOf("[Verse]", "verse line $number", "{Chorus}", "chorus line")

    /** A follower whose fetch is [detailFor], counting every call. */
    private fun follower(detailFor: (String, String) -> List<String>?): SongsViewModel {
        val vm = SongsViewModel(
            AppSettings(songSettings = SongSettings(storageDirectory = dir.absolutePath)),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
            enableFolderWatcher = false,
        )
        created.add(vm)
        vm.setInstanceLinkSource(
            active = true,
            catalog = catalog(),
            fetchLyrics = { n, s -> fetchCount.incrementAndGet(); detailFor(n, s) },
        )
        awaitUntil("the mirrored catalog") { vm.filteredSongItems.value.isNotEmpty() }
        return vm
    }

    /**
     * Asserts [what] has already happened.
     *
     * The view model is built on an immediate dispatcher for both its scope and its file reads, so a
     * load is complete by the time the constructor or the call returns — there is nothing to wait
     * for. This used to poll a wall clock for up to 5s, which is what made these tests fail on a
     * loaded CI runner (issue #56): the condition was right, the coroutine just had not been
     * scheduled yet. Nothing here now depends on timing.
     */
    private fun awaitUntil(what: String, condition: () -> Boolean) {
        if (!condition()) throw AssertionError("expected $what to have completed synchronously")
    }

    // ── The fetch happens and its result is formatted ────────────────────────────

    @Test
    fun `selecting a song fetches its lyrics`() {
        val vm = follower { _, _ -> detail("0042") }

        vm.selectSong(0)

        awaitUntil("the fetched lyrics") { vm.filteredSongItems.value[0].lyrics.isNotEmpty() }
        assertEquals(
            listOf("[Verse]", "verse line 0042", "{Chorus}", "chorus line"),
            vm.filteredSongItems.value[0].lyrics,
            "the fetched lyrics are the song's",
        )
        assertEquals(1, fetchCount.get())
    }

    // ── The no-op branches ───────────────────────────────────────────────────────

    @Test
    fun `a fetch that returns nothing leaves the song without lyrics`() {
        val vm = follower { _, _ -> null }

        vm.selectSong(0)
        // Wait on the fetch actually happening rather than on an EDT round trip: the fetch is
        // launched on Dispatchers.Main and suspends, so draining the EDT once proves nothing about
        // whether it has run. The counter is bumped inside the fetch itself, so it is the signal.
        awaitUntil("the fetch to be attempted") { fetchCount.get() == 1 }

        // Nothing sets lyrics on the detail == null path, so once the fetch has run this is settled.
        assertTrue(vm.filteredSongItems.value[0].lyrics.isEmpty(), "no detail means nothing to show")
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
        val vm = SongsViewModel(
            AppSettings(songSettings = SongSettings(storageDirectory = dir.absolutePath)),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
            enableFolderWatcher = false,
        )
        created.add(vm)
        vm.setInstanceLinkSource(active = true, catalog = catalog(), fetchLyrics = null)
        awaitUntil("the mirrored catalog") { vm.filteredSongItems.value.isNotEmpty() }

        // selectSong returns before launching anything when there is no fetch function, so there
        // is nothing to wait for.
        vm.selectSong(0)

        assertTrue(vm.filteredSongItems.value[0].lyrics.isEmpty())
        assertEquals(0, fetchCount.get(), "there is no fetch function to call")
    }

    @Test
    fun `losing the link puts the local library back in place of the mirrored catalog`() {
        val vm = follower { n, _ -> detail(n) }
        assertEquals(listOf("Grace"), vm.filteredSongItems.value.map { it.title })

        vm.setInstanceLinkSource(active = false, catalog = null, fetchLyrics = null)

        assertTrue(vm.filteredSongItems.value.none { it.title == "Grace" }, "the primary's songs must not linger")
    }

    @Test
    fun `losing a link that was never up changes nothing`() {
        val vm = SongsViewModel(
            AppSettings(songSettings = SongSettings(storageDirectory = dir.absolutePath)),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
            enableFolderWatcher = false,
        ).also { created.add(it) }
        val before = vm.filteredSongItems.value

        vm.setInstanceLinkSource(active = false, catalog = null, fetchLyrics = null)

        assertEquals(before, vm.filteredSongItems.value)
    }

    @Test
    fun `selecting an out-of-range index does not fetch`() {
        val vm = follower { _, _ -> detail("0042") }

        // An out-of-range index is rejected before any fetch is launched.
        vm.selectSong(999)

        assertEquals(0, fetchCount.get(), "there is no song at that index to fetch")
    }
}
