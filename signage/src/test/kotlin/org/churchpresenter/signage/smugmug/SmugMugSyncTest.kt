package org.churchpresenter.signage.smugmug

import kotlinx.coroutines.test.runTest
import org.churchpresenter.signage.tab.SmugMugPlaylist
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.writeText
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A slideshow's folder filled with a random set, picked again when due, and kept when a pick fails. */
@OptIn(ExperimentalPathApi::class)
class SmugMugSyncTest {
    private val root: Path = createTempDirectory("smugmug-sync")
    private var now = 1_000_000L
    private var key = "KEY"
    private val smugMug = FakeSmugMug(
        listOf(
            FakeAlbum("a1", "Harvest", listOf("p1", "p2", "p3")),
            FakeAlbum("a2", "Seminar", listOf("p4", "p5")),
        ),
    )
    private val sync = SmugMugSync(root, { key }, smugMug, Random(3), { now })
    private val show = SmugMugPlaylist.create("Photos").copy(site = "photos.example.org", photoCount = 3)
    private val dir: Path = root.resolve(show.path)

    @AfterTest
    fun cleanUp() = root.deleteRecursively()

    private fun photos(): List<String> = if (!dir.exists()) emptyList() else {
        dir.listDirectoryEntries().map { it.name }.filterNot { it.startsWith(".") }.sorted()
    }

    @Test
    fun `a pick fills the folder in its order and remembers it`() = runTest {
        assertTrue(sync.isDue(show))
        val state = sync.pick(show)
        assertEquals(3, state.photos)
        assertNull(state.error)
        val names = photos()
        assertEquals(listOf("0001", "0002", "0003"), names.map { it.substringBefore('-') })
        assertTrue(names.all { it.endsWith(".jpg") })
        assertEquals(state, sync.state(show))
        assertFalse(sync.isDue(show))
        assertEquals(setOf("a1", "a2"), state.albums.keys)
    }

    @Test
    fun `a pick says how far it has got, galleries first and then photos`() = runTest {
        val seen = mutableListOf<SmugMugProgress>()
        sync.pick(show) { seen += it }
        val listing = seen.filter { it.stage == SmugMugProgress.Stage.LISTING }
        val downloading = seen.filter { it.stage == SmugMugProgress.Stage.DOWNLOADING }
        assertEquals(listOf(0, 1, 2), listing.map { it.done })
        assertTrue(listing.all { it.total == 2 })
        assertEquals(listOf(0, 1, 2, 3), downloading.map { it.done }.sorted())
        assertTrue(downloading.all { it.total == 3 })
        assertEquals(listing + downloading, seen, "every gallery is listed before the first photo")
    }

    @Test
    fun `the next pick reuses the photos it has and lists only galleries that changed`() = runTest {
        sync.pick(show.copy(photoCount = 5))
        smugMug.asked.clear()
        smugMug.downloads.clear()
        smugMug.albums = smugMug.albums.map { if (it.key == "a2") it.copy(updated = "later") else it }
        sync.pick(show.copy(photoCount = 5))
        assertTrue(smugMug.downloads.isEmpty(), "every photo was in the set already")
        assertTrue(smugMug.asked.none { "/album/a1!images" in it })
        assertTrue(smugMug.asked.any { "/album/a2!images" in it })
        assertEquals(5, photos().size)
    }

    @Test
    fun `a failed pick keeps the set it had and waits before trying the same again`() = runTest {
        sync.pick(show)
        val before = photos()
        now += 7 * 3_600_000L
        assertTrue(sync.isDue(show))
        smugMug.failure = IOException("offline")
        val failed = sync.pick(show)
        assertEquals("offline", failed.error)
        assertEquals(before, photos())
        assertEquals(3, failed.photos)
        assertFalse(sync.isDue(show))
        // A change to what it picks for is tried at once; the same settings after the wait.
        assertTrue(sync.isDue(show.copy(photoCount = 4)))
        now += SmugMugSync.RETRY_MS
        assertTrue(sync.isDue(show))
    }

    @Test
    fun `no key, no photos, or no photo saved is an error, and nothing is asked without a key`() = runTest {
        key = " "
        assertEquals(SmugMugSync.NO_KEY, sync.pick(show).error)
        assertTrue(smugMug.asked.isEmpty())
        key = "KEY"
        smugMug.albums = listOf(FakeAlbum("e", "Empty", emptyList()))
        assertEquals(SmugMugSync.NO_PHOTOS, sync.pick(show.copy(photoCount = 9)).error)
        smugMug.albums = listOf(FakeAlbum("a", "One", listOf("x1", "x2")))
        smugMug.brokenPhotos += setOf("x1", "x2")
        assertEquals(SmugMugSync.NOTHING_SAVED, sync.pick(show.copy(photoCount = 8)).error)
        assertTrue(photos().isEmpty())
    }

    @Test
    fun `a photo that will not download is left out of the set`() = runTest {
        smugMug.brokenPhotos += "p1"
        val state = sync.pick(show.copy(photoCount = 5))
        assertEquals(4, state.photos)
        assertTrue(photos().none { "p1" in it })
    }

    @Test
    fun `its time up, it picks again`() = runTest {
        sync.pick(show)
        now += show.refreshHours * 3_600_000L - 1
        assertFalse(sync.isDue(show))
        now += 1
        assertTrue(sync.isDue(show))
    }

    @Test
    fun `a removed slideshow's photos and half-made set are deleted`() = runTest {
        sync.pick(show)
        root.resolve("SmugMug/.${dir.name}.new").createDirectories().resolve("half.jpg").writeText("x")
        sync.forget(show)
        assertFalse(dir.exists())
        assertFalse(root.resolve("SmugMug/.${dir.name}.new").exists())
    }

    @Test
    fun `an unreadable state is treated as none`() {
        dir.createDirectories().resolve(SmugMugSync.STATE_FILE).writeText("{ not json")
        assertEquals(SmugMugState(), sync.state(show))
        assertNotNull(sync.state(show))
    }
}
