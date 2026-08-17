package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.app.churchpresenter.RecentFilesSwap
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [RecentMediaFiles]'s own logic — add, pin, clear, and the JSON round-trip behind them.
 *
 * Same shape as [RecentPictureFoldersLogicTest]. [RecentMediaFiles.file] and
 * [RecentMediaFiles.pinnedFile] are repointed at a temp directory for the duration of each test, so
 * `add`/`togglePin`/`clear` run for real without ever touching the developer's own recent/pinned
 * JSON files under `~/.churchpresenter`.
 */
class RecentMediaFilesLogicTest {

    private val swap = RecentFilesSwap(
        readPaths = { RecentMediaFiles.file to RecentMediaFiles.pinnedFile },
        writePaths = { f, p -> RecentMediaFiles.file = f; RecentMediaFiles.pinnedFile = p },
        entries = RecentMediaFiles.paths,
        pinned = RecentMediaFiles.pinned,
        prefix = "cp-recent-media-files",
    )

    @BeforeTest
    fun setUp() = swap.install()

    @AfterTest
    fun tearDown() = swap.restore()

    private fun path(name: String) = "/Volumes/Services/media/$name.mp4"

    // ── add ─────────────────────────────────────────────────────────────────────

    @Test
    fun `add puts a new file at the front`() {
        RecentMediaFiles.add(path("baptism"))
        RecentMediaFiles.add(path("worship"))

        assertEquals(listOf(path("worship"), path("baptism")), RecentMediaFiles.paths)
    }

    @Test
    fun `adding a file already in the list moves it to the front instead of duplicating it`() {
        RecentMediaFiles.add(path("baptism"))
        RecentMediaFiles.add(path("worship"))
        RecentMediaFiles.add(path("baptism"))

        assertEquals(listOf(path("baptism"), path("worship")), RecentMediaFiles.paths)
    }

    @Test
    fun `the list is capped at ten, dropping the oldest`() {
        (1..11).forEach { RecentMediaFiles.add(path("clip $it")) }

        assertEquals(10, RecentMediaFiles.paths.size)
        assertTrue(path("clip 11") in RecentMediaFiles.paths, "the newest survives")
        assertFalse(path("clip 1") in RecentMediaFiles.paths, "the oldest is evicted")
    }

    @Test
    fun `add persists to disk and reloads on the next load`() {
        RecentMediaFiles.add(path("worship"))
        RecentMediaFiles.add(path("baptism"))
        RecentMediaFiles.paths.clear()

        RecentMediaFiles.load()

        assertEquals(listOf(path("baptism"), path("worship")), RecentMediaFiles.paths)
    }

    // ── togglePin ───────────────────────────────────────────────────────────────

    @Test
    fun `togglePin pins an unpinned file to the front`() {
        RecentMediaFiles.togglePin(path("intro loop"))

        assertEquals(listOf(path("intro loop")), RecentMediaFiles.pinned)
    }

    @Test
    fun `togglePin unpins an already-pinned file`() {
        RecentMediaFiles.togglePin(path("intro loop"))
        RecentMediaFiles.togglePin(path("intro loop"))

        assertTrue(RecentMediaFiles.pinned.isEmpty())
    }

    @Test
    fun `pinning a second file puts it ahead of the first`() {
        RecentMediaFiles.togglePin(path("intro loop"))
        RecentMediaFiles.togglePin(path("outro loop"))

        assertEquals(listOf(path("outro loop"), path("intro loop")), RecentMediaFiles.pinned)
    }

    @Test
    fun `togglePin persists to disk and reloads on the next load`() {
        RecentMediaFiles.togglePin(path("intro loop"))
        RecentMediaFiles.pinned.clear()

        RecentMediaFiles.load()

        assertEquals(listOf(path("intro loop")), RecentMediaFiles.pinned)
    }

    // ── clear ───────────────────────────────────────────────────────────────────

    @Test
    fun `clear empties the recent list`() {
        RecentMediaFiles.add(path("worship"))
        RecentMediaFiles.add(path("baptism"))

        RecentMediaFiles.clear()

        assertTrue(RecentMediaFiles.paths.isEmpty())
    }

    @Test
    fun `clear keeps pinned files even though they were also recent`() {
        RecentMediaFiles.add(path("worship"))
        RecentMediaFiles.add(path("intro loop"))
        RecentMediaFiles.togglePin(path("intro loop"))

        RecentMediaFiles.clear()

        assertEquals(listOf(path("intro loop")), RecentMediaFiles.paths)
    }

    @Test
    fun `clear persists to disk and reloads on the next load`() {
        RecentMediaFiles.add(path("worship"))
        RecentMediaFiles.add(path("intro loop"))
        RecentMediaFiles.togglePin(path("intro loop"))
        RecentMediaFiles.clear()
        RecentMediaFiles.paths.clear()

        RecentMediaFiles.load()

        assertEquals(listOf(path("intro loop")), RecentMediaFiles.paths)
    }

    // ── load ────────────────────────────────────────────────────────────────────

    @Test
    fun `load leaves the lists alone when neither file exists yet`() {
        RecentMediaFiles.paths.add(path("untouched"))

        RecentMediaFiles.load()

        assertEquals(listOf(path("untouched")), RecentMediaFiles.paths)
    }

    @Test
    fun `load caps what it reads back at ten`() {
        RecentMediaFiles.file.parentFile.mkdirs()
        val eleven = (1..11).map { "\"${path("clip $it")}\"" }
        RecentMediaFiles.file.writeText("[${eleven.joinToString(",")}]")

        RecentMediaFiles.load()

        assertEquals(10, RecentMediaFiles.paths.size)
    }

    @Test
    fun `load recovers from a corrupt file instead of throwing`() {
        RecentMediaFiles.file.parentFile.mkdirs()
        RecentMediaFiles.file.writeText("not valid json")
        RecentMediaFiles.paths.add(path("untouched"))

        RecentMediaFiles.load()

        assertEquals(
            listOf(path("untouched")),
            RecentMediaFiles.paths,
            "a bad file must not wipe what was already there",
        )
    }
}
