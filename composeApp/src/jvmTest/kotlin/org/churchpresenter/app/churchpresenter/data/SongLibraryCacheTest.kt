package org.churchpresenter.app.churchpresenter.data

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Scanning a song library, and the cache that keeps it fast.
 *
 * A library is a folder of `.song` files with one subfolder per songbook, and it is re-scanned on
 * every startup — thousands of files for a church with a real library, which is why parsed songs
 * are cached and re-read only when a file's modification time has changed.
 *
 * That last part is the whole risk. Cache too eagerly and an operator's edit never reaches the
 * screen, with nothing to suggest why; cache too little and startup crawls. The cache is also keyed
 * on the library folder, so pointing the app at a different folder has to invalidate it rather than
 * serve the previous library's songs.
 *
 * [SongFileParserTest] covers parsing one file; this covers the scan and the cache around it.
 * The cache file's path is resolved once when the class loads, so — as with recents — it is read
 * back by reflection rather than by swapping `user.home`.
 */
class SongLibraryCacheTest {

    private lateinit var library: File
    private val parser = SongFileParser()

    /**
     * The cache's own path. A private property of a companion object is compiled to a private
     * *static* field on the enclosing class, which is where this reads it from.
     */
    private val cacheFile: File by lazy {
        SongFileParser::class.java
            .getDeclaredField("cacheFile")
            .apply { isAccessible = true }
            .get(null) as File
    }

    @BeforeTest
    fun createLibrary() {
        library = Files.createTempDirectory("cp-song-library-test").toFile()
        cacheFile.delete()
    }

    @AfterTest
    fun cleanUp() {
        library.deleteRecursively()
        cacheFile.delete()
    }

    /** Writes a `.song` file, optionally inside a songbook subfolder. */
    private fun song(name: String, title: String = name, songbook: String? = null, lyric: String = "A line"): File {
        val dir = songbook?.let { File(library, it).also { d -> d.mkdirs() } } ?: library
        return File(dir, "$name.song").also {
            it.writeText("[Primary]\ntitle: $title\n\n$lyric\n", Charsets.UTF_8)
        }
    }

    private fun scan(cache: Map<String, CachedSong> = emptyMap()) =
        parser.loadSongsFromDirectory(library.absolutePath, cache)

    // ── Scanning a library ──────────────────────────────────────────────────────

    @Test
    fun `every song file in the folder is found`() {
        song("0001 - Amazing Grace")
        song("0002 - How Great Thou Art")

        assertEquals(2, scan().size)
    }

    @Test
    fun `songs are read in file-name order`() {
        song("0003 - Third")
        song("0001 - First")
        song("0002 - Second")

        assertEquals(
            listOf("0001 - First", "0002 - Second", "0003 - Third"),
            scan().map { File(it.song.sourceFile).nameWithoutExtension },
            "the library list is built in this order",
        )
    }

    @Test
    fun `a subfolder becomes the songbook`() {
        song("0001 - Amazing Grace", songbook = "Hymnal")

        assertEquals("Hymnal", scan().single().song.songbook)
    }

    @Test
    fun `a song in the root has no songbook`() {
        song("0001 - Loose Song")

        assertEquals("", scan().single().song.songbook, "a loose file belongs to no songbook")
    }

    @Test
    fun `a nested songbook keeps its whole path`() {
        song("0001 - Deep", songbook = "Hymnal/Advent")

        assertEquals(
            "Hymnal/Advent",
            scan().single().song.songbook,
            "songbooks nest, and the full path is what tells two Advent folders apart",
        )
    }

    @Test
    fun `songbooks are visited in name order after the root`() {
        song("0009 - Root Song")
        song("0001 - B song", songbook = "Beta")
        song("0001 - A song", songbook = "Alpha")

        assertEquals(
            listOf("", "Alpha", "Beta"),
            scan().map { it.song.songbook },
            "the root is read first, then each songbook in order",
        )
    }

    @Test
    fun `files that are not songs are ignored`() {
        song("0001 - Amazing Grace")
        File(library, "notes.txt").writeText("not a song")
        File(library, "library.sps").writeText("not a song either")

        assertEquals(1, scan().size)
    }

    @Test
    fun `a folder that is not there scans to nothing`() {
        assertTrue(parser.loadSongsFromDirectory(File(library, "missing").absolutePath).isEmpty())
    }

    @Test
    fun `a path that is a file scans to nothing`() {
        val notAFolder = song("0001 - Amazing Grace")

        assertTrue(parser.loadSongsFromDirectory(notAFolder.absolutePath).isEmpty())
    }

    @Test
    fun `an empty library scans to nothing`() {
        assertTrue(scan().isEmpty())
    }

    // ── Reusing what was parsed last time ───────────────────────────────────────

    @Test
    fun `an unchanged song is taken from the cache`() {
        val file = song("0001 - Amazing Grace", title = "Amazing Grace")
        val firstScan = scan()
        val cache = firstScan.associateBy { it.song.sourceFile }

        // The cached entry says something different from the file: if it is reused, this is what
        // comes back, which is how the reuse is observable at all.
        val doctored = cache.mapValues { (_, entry) -> entry.copy(song = entry.song.copy(title = "From the cache")) }
        val secondScan = parser.loadSongsFromDirectory(library.absolutePath, doctored)

        assertEquals("From the cache", secondScan.single().song.title, "an unchanged file must not be re-parsed")
        assertEquals(file.lastModified(), secondScan.single().lastModified)
    }

    @Test
    fun `an edited song is read again`() {
        val file = song("0001 - Amazing Grace", title = "Amazing Grace")
        val cache = scan().associateBy { it.song.sourceFile }
            .mapValues { (_, entry) -> entry.copy(song = entry.song.copy(title = "Stale")) }

        file.writeText("[Primary]\ntitle: Amazing Grace (revised)\n\nA line\n", Charsets.UTF_8)
        file.setLastModified(file.lastModified() + 5_000)

        assertEquals(
            "Amazing Grace (revised)",
            parser.loadSongsFromDirectory(library.absolutePath, cache).single().song.title,
            "an edit the operator just made has to reach the screen",
        )
    }

    @Test
    fun `a song added since the last scan is picked up`() {
        song("0001 - First")
        val cache = scan().associateBy { it.song.sourceFile }

        song("0002 - Second")

        assertEquals(2, parser.loadSongsFromDirectory(library.absolutePath, cache).size)
    }

    @Test
    fun `a song deleted since the last scan is dropped`() {
        val doomed = song("0001 - First")
        song("0002 - Second")
        val cache = scan().associateBy { it.song.sourceFile }

        doomed.delete()

        assertEquals(
            listOf("0002 - Second"),
            parser.loadSongsFromDirectory(library.absolutePath, cache).map { it.song.title },
            "the cache must not resurrect a song the operator removed",
        )
    }

    @Test
    fun `a cache entry for another file is not used`() {
        song("0001 - Amazing Grace", title = "Amazing Grace")
        val strayEntry = CachedSong(
            song = SongItem(number = "9", title = "Wrong Song", songbook = "", sourceFile = "/elsewhere/other.song"),
            lastModified = 0L,
        )

        val scanned = parser.loadSongsFromDirectory(library.absolutePath, mapOf(strayEntry.song.sourceFile to strayEntry))

        assertEquals("Amazing Grace", scanned.single().song.title, "entries are matched by path, not by position")
    }

    // ── The cache on disk ───────────────────────────────────────────────────────

    @Test
    fun `a saved cache is read back for the same library`() {
        song("0001 - Amazing Grace", title = "Amazing Grace")
        val scanned = scan()

        SongFileParser.saveSongCache(library.absolutePath, scanned)

        assertEquals(
            listOf("Amazing Grace"),
            SongFileParser.loadSongCache(library.absolutePath)?.map { it.title },
        )
    }

    @Test
    fun `the cache map is keyed by each song's own file`() {
        val file = song("0001 - Amazing Grace")
        SongFileParser.saveSongCache(library.absolutePath, scan())

        val map = SongFileParser.loadCachedSongMap(library.absolutePath)

        assertEquals(setOf(file.absolutePath), map.keys, "the scan looks entries up by absolute path")
        assertEquals(file.lastModified(), map.getValue(file.absolutePath).lastModified)
    }

    @Test
    fun `pointing the app at another library ignores the cache`() {
        song("0001 - Amazing Grace")
        SongFileParser.saveSongCache(library.absolutePath, scan())

        val otherLibrary = File(library.parentFile, "another-library").absolutePath

        assertNull(
            SongFileParser.loadSongCache(otherLibrary),
            "the cache belongs to one folder; serving it for another would show the wrong library",
        )
        assertTrue(SongFileParser.loadCachedSongMap(otherLibrary).isEmpty())
    }

    @Test
    fun `no cache file yet is not an error`() {
        assertNull(SongFileParser.loadSongCache(library.absolutePath))
        assertTrue(SongFileParser.loadCachedSongMap(library.absolutePath).isEmpty())
    }

    @Test
    fun `a corrupt cache file is ignored rather than fatal`() {
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText("not json at all")

        assertNull(SongFileParser.loadSongCache(library.absolutePath))
        assertTrue(SongFileParser.loadCachedSongMap(library.absolutePath).isEmpty(), "a bad cache means a full re-scan")
    }

    @Test
    fun `a cache written by an older build is still readable`() {
        // Older caches held only the song list, with no modification times to check against.
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText(
            """{"storageDirectory":"${library.absolutePath}","songs":[""" +
                """{"number":"1","title":"From an old cache","songbook":"Hymnal"}],"cachedSongs":[]}"""
        )

        assertEquals(
            listOf("From an old cache"),
            SongFileParser.loadSongCache(library.absolutePath)?.map { it.title },
        )
        assertTrue(
            SongFileParser.loadCachedSongMap(library.absolutePath).isEmpty(),
            "with no timestamps there is nothing to reuse, so every file is re-parsed",
        )
    }

    @Test
    fun `an empty cache reads as no cache at all`() {
        SongFileParser.saveSongCache(library.absolutePath, emptyList())

        assertNull(
            SongFileParser.loadSongCache(library.absolutePath),
            "an empty library and a missing cache are the same thing to the caller",
        )
    }

    @Test
    fun `saving replaces whatever was cached before`() {
        song("0001 - First", title = "First")
        SongFileParser.saveSongCache(library.absolutePath, scan())

        song("0002 - Second", title = "Second")
        SongFileParser.saveSongCache(library.absolutePath, scan())

        assertEquals(
            listOf("First", "Second"),
            SongFileParser.loadSongCache(library.absolutePath)?.map { it.title },
        )
    }

    @Test
    fun `a full round trip through the cache reuses every song`() {
        song("0001 - First", songbook = "Hymnal")
        song("0002 - Second", songbook = "Hymnal")
        SongFileParser.saveSongCache(library.absolutePath, scan())

        val reused = parser.loadSongsFromDirectory(
            library.absolutePath,
            SongFileParser.loadCachedSongMap(library.absolutePath),
        )

        assertEquals(2, reused.size)
        assertTrue(reused.all { it.song.songbook == "Hymnal" }, "this is the whole startup path, end to end")
    }
}
