package org.churchpresenter.app.churchpresenter.dialogs

import org.churchpresenter.resources.generated.resources.Res
import kotlinx.coroutines.runBlocking
import org.churchpresenter.app.churchpresenter.data.StockMediaClient
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val REAL_BUNDLED_IMAGE = "mountains_34448034.jpg"

class LocalLibraryEntriesTest {

    private var realHome: String? = null
    private lateinit var tempHome: File

    @BeforeTest
    fun setUpHome() {
        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-local-library-home").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
    }

    @AfterTest
    fun tearDownHome() {
        realHome?.let { System.setProperty("user.home", it) }
    }

    @Test
    fun `downloaded files come before bundled entries`() {
        val entries = libraryEntries(
            downloadedFiles = listOf(File("sunset.jpg")),
            bundledFileNames = listOf("mountains.jpg"),
            searchQuery = "",
        )

        assertEquals(listOf("sunset.jpg", "mountains.jpg"), entries.map { it.name })
    }

    @Test
    fun `a bundled entry already downloaded under the same name is not duplicated`() {
        val entries = libraryEntries(
            downloadedFiles = listOf(File("sunset.jpg")),
            bundledFileNames = listOf("sunset.jpg", "mountains.jpg"),
            searchQuery = "",
        )

        assertEquals(listOf("sunset.jpg", "mountains.jpg"), entries.map { it.name })
        assertTrue(
            entries.first { it.name == "sunset.jpg" } is DownloadedEntry,
            "the downloaded copy wins over the bundled one",
        )
    }

    @Test
    fun `bundled entries are sorted by name`() {
        val entries = libraryEntries(
            downloadedFiles = emptyList(),
            bundledFileNames = listOf("zebra.jpg", "apple.jpg"),
            searchQuery = "",
        )

        assertEquals(listOf("apple.jpg", "zebra.jpg"), entries.map { it.name })
    }

    @Test
    fun `a blank search query keeps everything`() {
        val entries = libraryEntries(
            downloadedFiles = listOf(File("sunset.jpg")),
            bundledFileNames = listOf("mountains.jpg"),
            searchQuery = "   ",
        )

        assertEquals(2, entries.size)
    }

    @Test
    fun `a search query filters by name, case-insensitively`() {
        val entries = libraryEntries(
            downloadedFiles = listOf(File("Sunset.jpg")),
            bundledFileNames = listOf("Mountains.jpg"),
            searchQuery = "sun",
        )

        assertEquals(listOf("Sunset.jpg"), entries.map { it.name })
    }

    @Test
    fun `a search query matching nothing yields an empty list`() {
        val entries = libraryEntries(
            downloadedFiles = listOf(File("sunset.jpg")),
            bundledFileNames = emptyList(),
            searchQuery = "no such file",
        )

        assertTrue(entries.isEmpty())
    }

    @Test
    fun `a downloaded entry's key is its absolute path`() {
        val file = File("sunset.jpg")
        val entry = DownloadedEntry(file)

        assertEquals(file.absolutePath, entry.key)
    }

    @Test
    fun `two bundled entries with the same name have the same key`() {
        assertEquals(BundledEntry("sunset.jpg").key, BundledEntry("sunset.jpg").key)
    }

    @Test
    fun `a downloaded and a bundled entry never collide on key even with the same name`() {
        val downloaded = DownloadedEntry(File("sunset.jpg"))
        val bundled = BundledEntry("sunset.jpg")

        assertTrue(
            downloaded.key != bundled.key,
            "LazyVerticalGrid keys its items by this; a collision would confuse recomposition",
        )
    }

    @Test
    fun `materializeBundledEntry copies the resource into the stock library folder`() = runBlocking {
        val file = materializeBundledEntry(REAL_BUNDLED_IMAGE)

        assertEquals(
            File(tempHome, ".churchpresenter/stock-backgrounds/$REAL_BUNDLED_IMAGE").absolutePath,
            file.absolutePath,
        )
        assertTrue(file.exists())
        assertTrue(file.readBytes().isNotEmpty())
    }

    @Test
    fun `materializeBundledEntry does not overwrite a file that already exists`() = runBlocking {
        val dir = File(tempHome, ".churchpresenter/stock-backgrounds")
        dir.mkdirs()
        val existing = File(dir, REAL_BUNDLED_IMAGE)
        existing.writeBytes(byteArrayOf(1, 2, 3))

        val file = materializeBundledEntry(REAL_BUNDLED_IMAGE)

        assertEquals(listOf<Byte>(1, 2, 3), file.readBytes().toList())
    }

    @Test
    fun `loadThumbnailBitmap decodes a downloaded image file`() = runBlocking {
        val file = File(tempHome, "photo.jpg")
        file.writeBytes(Res.readBytes("files/backgrounds/$REAL_BUNDLED_IMAGE"))

        val bitmap = loadThumbnailBitmap(DownloadedEntry(file))

        assertTrue(bitmap != null)
    }

    @Test
    fun `loadThumbnailBitmap returns null for a downloaded file that isn't an image`() = runBlocking {
        val file = File(tempHome, "not-an-image.jpg")
        file.writeBytes(byteArrayOf(1, 2, 3, 4))

        val bitmap = loadThumbnailBitmap(DownloadedEntry(file))

        assertNull(bitmap)
    }

    @Test
    fun `loadThumbnailBitmap returns null for a downloaded file that does not exist`() = runBlocking {
        val bitmap = loadThumbnailBitmap(DownloadedEntry(File(tempHome, "missing.jpg")))

        assertNull(bitmap)
    }

    @Test
    fun `loadThumbnailBitmap decodes a bundled resource`() = runBlocking {
        val bitmap = loadThumbnailBitmap(BundledEntry(REAL_BUNDLED_IMAGE))

        assertTrue(bitmap != null)
    }

    @Test
    fun `loadThumbnailBitmap returns null for a bundled name that has no matching resource`() = runBlocking {
        val bitmap = loadThumbnailBitmap(BundledEntry("does-not-exist.jpg"))

        assertNull(bitmap)
    }

    // ── scanDownloadedFiles ──────────────────────────────────────────────────────────────────────

    @Test
    fun `scanDownloadedFiles only picks up files matching the media type's extensions`() {
        File(tempHome, "photo.jpg").writeBytes(ByteArray(1))
        File(tempHome, "clip.mp4").writeBytes(ByteArray(1))
        File(tempHome, "notes.txt").writeBytes(ByteArray(1))

        val photos = scanDownloadedFiles(tempHome, StockMediaClient.StockMediaType.PHOTO)
        val videos = scanDownloadedFiles(tempHome, StockMediaClient.StockMediaType.VIDEO)

        assertEquals(listOf("photo.jpg"), photos.map { it.name })
        assertEquals(listOf("clip.mp4"), videos.map { it.name })
    }

    @Test
    fun `scanDownloadedFiles matches extensions case-insensitively`() {
        File(tempHome, "photo.JPG").writeBytes(ByteArray(1))

        val photos = scanDownloadedFiles(tempHome, StockMediaClient.StockMediaType.PHOTO)

        assertEquals(listOf("photo.JPG"), photos.map { it.name })
    }

    @Test
    fun `scanDownloadedFiles excludes subdirectories`() {
        File(tempHome, "some-folder.jpg").mkdirs()

        val photos = scanDownloadedFiles(tempHome, StockMediaClient.StockMediaType.PHOTO)

        assertTrue(photos.isEmpty())
    }

    @Test
    fun `scanDownloadedFiles sorts newest file first`() {
        val older = File(tempHome, "older.jpg").apply { writeBytes(ByteArray(1)) }
        val newer = File(tempHome, "newer.jpg").apply { writeBytes(ByteArray(1)) }
        older.setLastModified(1_000L)
        newer.setLastModified(2_000L)

        val photos = scanDownloadedFiles(tempHome, StockMediaClient.StockMediaType.PHOTO)

        assertEquals(listOf("newer.jpg", "older.jpg"), photos.map { it.name })
    }

    @Test
    fun `scanDownloadedFiles returns an empty list for a directory that does not exist`() {
        val missingDir = File(tempHome, "does-not-exist")

        val photos = scanDownloadedFiles(missingDir, StockMediaClient.StockMediaType.PHOTO)

        assertTrue(photos.isEmpty())
    }

    // ── loadBundledFileNames ─────────────────────────────────────────────────────────────────────

    @Test
    fun `loadBundledFileNames returns the real bundled background names for photos`() = runBlocking {
        val names = loadBundledFileNames(StockMediaClient.StockMediaType.PHOTO)

        assertTrue(names.isNotEmpty())
        assertTrue(REAL_BUNDLED_IMAGE in names)
        assertTrue(names.none { it.isBlank() }, "blank lines from the index file must be filtered out")
    }

    @Test
    fun `loadBundledFileNames is always empty for videos, since none are bundled`() = runBlocking {
        val names = loadBundledFileNames(StockMediaClient.StockMediaType.VIDEO)

        assertTrue(names.isEmpty())
    }
}
