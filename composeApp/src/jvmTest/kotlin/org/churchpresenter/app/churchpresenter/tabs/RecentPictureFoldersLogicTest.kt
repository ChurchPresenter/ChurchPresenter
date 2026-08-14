package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.app.churchpresenter.RecentFilesSwap
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [RecentPictureFolders]'s own logic — add, pin, clear, and the JSON round-trip behind them.
 *
 * [PicturesTabRecentFoldersTest] covers what the bar renders; this covers what happens when a
 * button is actually pressed. [RecentPictureFolders.file] and [RecentPictureFolders.pinnedFile] are
 * repointed at a temp directory for the duration of each test, so `add`/`togglePin`/`clear` run for
 * real without ever touching the developer's own recent/pinned JSON files under `~/.churchpresenter`.
 */
class RecentPictureFoldersLogicTest {

    private val swap = RecentFilesSwap(
        readPaths = { RecentPictureFolders.file to RecentPictureFolders.pinnedFile },
        writePaths = { f, p -> RecentPictureFolders.file = f; RecentPictureFolders.pinnedFile = p },
        entries = RecentPictureFolders.folders,
        pinned = RecentPictureFolders.pinned,
        prefix = "cp-recent-picture-folders",
    )

    @BeforeTest
    fun setUp() = swap.install()

    @AfterTest
    fun tearDown() = swap.restore()

    private fun path(name: String) = "/Volumes/Services/photos/$name"

    // ── add ─────────────────────────────────────────────────────────────────────

    @Test
    fun `add puts a new folder at the front`() {
        RecentPictureFolders.add(path("Baptism"))
        RecentPictureFolders.add(path("Advent"))

        assertEquals(listOf(path("Advent"), path("Baptism")), RecentPictureFolders.folders)
    }

    @Test
    fun `adding a folder already in the list moves it to the front instead of duplicating it`() {
        RecentPictureFolders.add(path("Baptism"))
        RecentPictureFolders.add(path("Advent"))
        RecentPictureFolders.add(path("Baptism"))

        assertEquals(listOf(path("Baptism"), path("Advent")), RecentPictureFolders.folders)
    }

    @Test
    fun `the list is capped at ten, dropping the oldest`() {
        (1..11).forEach { RecentPictureFolders.add(path("Folder $it")) }

        assertEquals(10, RecentPictureFolders.folders.size)
        assertTrue(path("Folder 11") in RecentPictureFolders.folders, "the newest survives")
        assertFalse(path("Folder 1") in RecentPictureFolders.folders, "the oldest is evicted")
    }

    @Test
    fun `add persists to disk and reloads on the next load`() {
        RecentPictureFolders.add(path("Advent"))
        RecentPictureFolders.add(path("Baptism"))
        RecentPictureFolders.folders.clear()

        RecentPictureFolders.load()

        assertEquals(listOf(path("Baptism"), path("Advent")), RecentPictureFolders.folders)
    }

    // ── togglePin ───────────────────────────────────────────────────────────────

    @Test
    fun `togglePin pins an unpinned folder to the front`() {
        RecentPictureFolders.togglePin(path("Every Week"))

        assertEquals(listOf(path("Every Week")), RecentPictureFolders.pinned)
    }

    @Test
    fun `togglePin unpins an already-pinned folder`() {
        RecentPictureFolders.togglePin(path("Every Week"))
        RecentPictureFolders.togglePin(path("Every Week"))

        assertTrue(RecentPictureFolders.pinned.isEmpty())
    }

    @Test
    fun `pinning a second folder puts it ahead of the first`() {
        RecentPictureFolders.togglePin(path("Every Week"))
        RecentPictureFolders.togglePin(path("Every Month"))

        assertEquals(listOf(path("Every Month"), path("Every Week")), RecentPictureFolders.pinned)
    }

    @Test
    fun `togglePin persists to disk and reloads on the next load`() {
        RecentPictureFolders.togglePin(path("Every Week"))
        RecentPictureFolders.pinned.clear()

        RecentPictureFolders.load()

        assertEquals(listOf(path("Every Week")), RecentPictureFolders.pinned)
    }

    // ── clear ───────────────────────────────────────────────────────────────────

    @Test
    fun `clear empties the recent list`() {
        RecentPictureFolders.add(path("Advent"))
        RecentPictureFolders.add(path("Baptism"))

        RecentPictureFolders.clear()

        assertTrue(RecentPictureFolders.folders.isEmpty())
    }

    @Test
    fun `clear keeps pinned folders even though they were also recent`() {
        RecentPictureFolders.add(path("Advent"))
        RecentPictureFolders.add(path("Every Week"))
        RecentPictureFolders.togglePin(path("Every Week"))

        RecentPictureFolders.clear()

        assertEquals(listOf(path("Every Week")), RecentPictureFolders.folders)
    }

    @Test
    fun `clear persists to disk and reloads on the next load`() {
        RecentPictureFolders.add(path("Advent"))
        RecentPictureFolders.add(path("Every Week"))
        RecentPictureFolders.togglePin(path("Every Week"))
        RecentPictureFolders.clear()
        RecentPictureFolders.folders.clear()

        RecentPictureFolders.load()

        assertEquals(listOf(path("Every Week")), RecentPictureFolders.folders)
    }

    // ── load ────────────────────────────────────────────────────────────────────

    @Test
    fun `load leaves the lists alone when neither file exists yet`() {
        RecentPictureFolders.folders.add(path("Untouched"))

        RecentPictureFolders.load()

        assertEquals(listOf(path("Untouched")), RecentPictureFolders.folders)
    }

    @Test
    fun `load caps what it reads back at ten`() {
        RecentPictureFolders.file.parentFile.mkdirs()
        val eleven = (1..11).map { "\"${path("Folder $it")}\"" }
        RecentPictureFolders.file.writeText("[${eleven.joinToString(",")}]")

        RecentPictureFolders.load()

        assertEquals(10, RecentPictureFolders.folders.size)
    }

    @Test
    fun `load recovers from a corrupt file instead of throwing`() {
        RecentPictureFolders.file.parentFile.mkdirs()
        RecentPictureFolders.file.writeText("not valid json")
        RecentPictureFolders.folders.add(path("Untouched"))

        RecentPictureFolders.load()

        assertEquals(listOf(path("Untouched")),
            RecentPictureFolders.folders,
            "a bad file must not wipe what was already there")
    }
}
