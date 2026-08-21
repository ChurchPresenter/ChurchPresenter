package org.churchpresenter.core.models.songs

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The parse cache that keeps startup off the disk.
 *
 * The cache file is one fixed path under the user's home — the build points the test JVM at a
 * scratch home so this cannot touch a real library — and it is keyed by the storage directory it
 * was written for. Handing back songs from a different folder is the failure that matters here.
 */
class SongCacheTest {

    private val cacheFile = File(System.getProperty("user.home"), ".churchpresenter/song_cache.json")

    @AfterTest
    fun clearCache() {
        cacheFile.delete()
    }

    private fun cachedSong(title: String, path: String) = CachedSong(
        song = SongItem(number = "0001", title = title, sourceFile = path),
        lastModified = 1_000L,
        fileSize = 42L,
    )

    @Test
    fun `what was saved for a directory is what is loaded back for it`() {
        SongFileParser.saveSongCache("/library", listOf(cachedSong("Amazing Grace", "/library/a.song")))

        assertEquals(listOf("Amazing Grace"), SongFileParser.loadSongCache("/library")?.map { it.title })
    }

    @Test
    fun `a cache written for another directory is not offered`() {
        SongFileParser.saveSongCache("/library", listOf(cachedSong("Amazing Grace", "/library/a.song")))

        assertNull(
            SongFileParser.loadSongCache("/somewhere-else"),
            "songs from one library must never be handed to another",
        )
        assertEquals(emptyMap(), SongFileParser.loadCachedSongMap("/somewhere-else"))
    }

    @Test
    fun `the map is keyed by each song's own file`() {
        val a = cachedSong("A", "/library/a.song")
        val b = cachedSong("B", "/library/b.song")
        SongFileParser.saveSongCache("/library", listOf(a, b))

        val map = SongFileParser.loadCachedSongMap("/library")

        assertEquals(setOf("/library/a.song", "/library/b.song"), map.keys)
        assertEquals("A", map.getValue("/library/a.song").song.title)
        assertEquals(1_000L, map.getValue("/library/a.song").lastModified)
        assertEquals(42L, map.getValue("/library/a.song").fileSize)
    }

    @Test
    fun `no cache file at all reads as nothing`() {
        cacheFile.delete()

        assertNull(SongFileParser.loadSongCache("/library"))
        assertEquals(emptyMap(), SongFileParser.loadCachedSongMap("/library"))
    }

    @Test
    fun `a corrupt cache file is ignored rather than thrown`() {
        cacheFile.parentFile.mkdirs()
        cacheFile.writeText("{ this is not json")

        assertNull(SongFileParser.loadSongCache("/library"))
        assertEquals(emptyMap(), SongFileParser.loadCachedSongMap("/library"))
    }

    @Test
    fun `an empty cache is nothing rather than an empty library`() {
        SongFileParser.saveSongCache("/library", emptyList())

        assertNull(
            SongFileParser.loadSongCache("/library"),
            "an empty list would tell the app the library is empty rather than unknown",
        )
    }

    @Test
    fun `a cache from an older build without timestamps still loads its songs`() {
        // Written by hand in the legacy shape: `songs` populated, `cachedSongs` absent.
        cacheFile.parentFile.mkdirs()
        cacheFile.writeText(
            """{"storageDirectory":"/library","songs":[{"number":"0001","title":"Legacy"}]}""",
        )

        assertEquals(listOf("Legacy"), SongFileParser.loadSongCache("/library")?.map { it.title })
        assertEquals(emptyMap(), SongFileParser.loadCachedSongMap("/library"))
    }

    @Test
    fun `saving somewhere unwritable is swallowed rather than crashing the load`() {
        // The cache is an optimisation: failing to write it must never take the app down.
        cacheFile.parentFile.mkdirs()
        cacheFile.delete()
        cacheFile.mkdirs() // a directory where the file should be — writeText cannot succeed

        SongFileParser.saveSongCache("/library", listOf(cachedSong("A", "/library/a.song")))

        assertTrue(cacheFile.isDirectory, "the failed write must have left the path alone")
        cacheFile.delete()
    }
}
