package org.churchpresenter.signage.smugmug

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reading an account as a visitor: finding it, its galleries and photos, and a photo's size. */
class SmugMugClientTest {
    private val smugMug = FakeSmugMug(
        listOf(
            FakeAlbum("a1", "Harvest", listOf("p1", "p2", "p3"), unshown = listOf("v1" to "video", "h1" to "hidden")),
            FakeAlbum("a2", "Seminar", listOf("p4")),
            FakeAlbum("a3", "Retreat", listOf("p5", "p6")),
        ),
    )
    private val client = SmugMugClient(smugMug, "KEY")

    @Test
    fun `a custom domain is found from the account's link, with or without www`() = runTest {
        assertEquals("Grace", client.nickname("https://photos.example.org"))
        assertEquals("Grace", client.nickname("www.photos.example.org/Slideshow/x"))
        assertTrue(smugMug.asked.all { "APIKey=KEY" in it })
    }

    @Test
    fun `a nickname or a smugmug address needs no lookup`() = runTest {
        assertEquals("grace", client.nickname("grace"))
        assertEquals("grace", client.nickname("https://grace.smugmug.com/Events"))
        assertTrue(smugMug.asked.isEmpty())
    }

    @Test
    fun `a site SmugMug does not know says so`() = runTest {
        val unknown = assertFailsWith<IOException> { client.nickname("https://elsewhere.example.net") }
        assertEquals("SmugMug does not know elsewhere.example.net", unknown.message)
        assertFailsWith<IOException> { client.nickname(" ") }
        smugMug.failure = SmugMugStatusException(401)
        assertEquals(401, assertFailsWith<SmugMugStatusException> { client.nickname("photos.example.org") }.status)
    }

    @Test
    fun `galleries and photos are read page by page, and a visitor's missing privacy is public`() = runTest {
        val albums = client.albums("Grace")
        assertEquals(listOf("a1", "a2", "a3"), albums.map { it.key })
        assertEquals("Public", albums.first().privacy)
        assertEquals(3, albums.first().imageCount)
        assertEquals(listOf("p1", "p2", "p3"), client.photoKeys(albums.first()))
    }

    @Test
    fun `a photo's size is asked for under its first version and fills a 1080p screen`() = runTest {
        val size = client.size("p1")!!
        assertEquals(2048 to 1365, size.width to size.height)
        assertEquals("jpg", size.extension)
        assertTrue(smugMug.asked.single().contains("/image/p1-0!sizedetails"))
    }

    @Test
    fun `only open galleries with photos are used, and none left out`() {
        val albums = listOf(
            SmugMugAlbum("pub", "Public", "Public", imageCount = 3),
            SmugMugAlbum("unl", "Unlisted", "Unlisted", imageCount = 3),
            SmugMugAlbum("pw", "Locked", "Public", securityType = "Password", imageCount = 3),
            SmugMugAlbum("hint", "Hinted", "Public", passwordHint = "the usual", imageCount = 3),
            SmugMugAlbum("empty", "Empty", "Public"),
            SmugMugAlbum("out", "Left out", "Public", imageCount = 3),
        )
        assertEquals(listOf("pub"), SmugMugPick.albumsToUse(albums, false, setOf("out")).map { it.key })
        assertEquals(listOf("pub", "unl"), SmugMugPick.albumsToUse(albums, true, setOf("out")).map { it.key })
    }

    @Test
    fun `a pick takes each photo at most once, as many as asked or all there are`() {
        val keys = (1..50).map { "k$it" } + "k1"
        val picked = SmugMugPick.pick(keys, 10, Random(7))
        assertEquals(10, picked.toSet().size)
        assertEquals(picked, SmugMugPick.pick(keys, 10, Random(7)))
        assertEquals(50, SmugMugPick.pick(keys, 500, Random(7)).size)
        assertTrue(SmugMugPick.pick(keys, -1, Random(7)).isEmpty())
    }

    @Test
    fun `a photo smaller than a screen is taken at its largest, and never above 4K`() {
        fun size(name: String, width: Int, height: Int) = buildJsonObject {
            putJsonObject(name) {
                put("Url", "https://photos.smugmug.com/$name.png?x=1")
                put("Width", width)
                put("Height", height)
            }
        }
        val small = buildJsonObject {
            size("ImageSizeSmall", 400, 300).forEach { (k, v) -> put(k, v) }
            size("ImageSizeLarge", 1024, 768).forEach { (k, v) -> put(k, v) }
            size("ImageSizeOriginal", 1200, 900).forEach { (k, v) -> put(k, v) }
            put("ImageUrlTemplate", "ignored")
        }
        val best = SmugMugPick.bestSize(small)!!
        assertEquals(1024 to "png", best.width to best.extension)
        assertNull(SmugMugPick.bestSize(size("ImageSize6K", 6144, 4096)))
    }
}
