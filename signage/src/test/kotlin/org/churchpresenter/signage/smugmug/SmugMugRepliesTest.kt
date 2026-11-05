package org.churchpresenter.signage.smugmug

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.churchpresenter.signage.core.SignageJson
import java.io.IOException
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The less usual shapes a SmugMug reply can take, each read as the client should. */
class SmugMugRepliesTest {
    /** Answers each URL by the first of [replies] whose key it contains; records what was asked. */
    private class Replies(private val replies: Map<String, String>) : SmugMugHttp {
        val asked = mutableListOf<String>()

        override suspend fun json(url: String): JsonObject {
            asked += url
            val body = replies.entries.firstOrNull { it.key in url }?.value ?: throw SmugMugStatusException(404)
            return SignageJson.parseToJsonElement(body).jsonObject
        }

        override suspend fun download(url: String, to: Path) = throw IOException("not here")
    }

    @Test
    fun `an account named outright, or in a list, is found`() = runTest {
        val named = SmugMugClient(Replies(mapOf("weburilookup" to NAMED)), "K")
        assertEquals("grace", named.nickname("photos.example.org"))
        val listed = SmugMugClient(
            Replies(mapOf("weburilookup" to """{"Response":{"Items":[1,{"Uri":"/api/v2/user/hope!albums"}]}}""")),
            "K",
        )
        assertEquals("hope", listed.nickname("https://photos.example.org/"))
        val nothing = SmugMugClient(Replies(mapOf("weburilookup" to """{"Response":{"Items":["x"]}}""")), "K")
        val unknown = assertFailsWith<IOException> { nothing.nickname("photos.example.org") }
        assertEquals("SmugMug does not know photos.example.org", unknown.message)
    }

    @Test
    fun `smugmug's own pages are looked up, and an address that is not one is a nickname`() = runTest {
        val replies = Replies(mapOf("weburilookup" to """{"Response":{"Uri":"/api/v2/user/grace"}}"""))
        val client = SmugMugClient(replies, "K")
        assertEquals("grace", client.nickname("https://www.smugmug.com/gallery/x"))
        assertEquals(1, replies.asked.size)
        assertEquals("not a host", client.nickname("not a host"))
        assertEquals("http://bad host", client.nickname("http://bad host"))
    }

    @Test
    fun `a gallery without a key is skipped, missing parts filled in, and a whole next page followed`() = runTest {
        val replies = Replies(
            mapOf(
                "start=2" to """{"Response":{"Album":[{"AlbumKey":"b","Title":"Second","LastUpdated":"t"}]}}""",
                "!albums" to """{"Response":{"Album":[{"Name":"no key"},{"AlbumKey":"a","ImageCount":2},"x"],""" +
                    """"Pages":{"NextPage":"$NEXT"}}}""",
            ),
        )
        val albums = SmugMugClient(replies, "K").albums("g")
        assertEquals(listOf("a" to "", "b" to "Second"), albums.map { it.key to it.name })
        assertEquals(listOf("Public", "Public"), albums.map { it.privacy })
        assertEquals("t", albums.last().imagesLastUpdated)
        assertTrue(replies.asked.last().endsWith("start=2&APIKey=K"), "the key is not added twice")
    }

    @Test
    fun `photos a screen cannot show are left out, and a reply with no list has none`() = runTest {
        val replies = Replies(
            mapOf(
                "/album/a!images" to """{"Response":{"AlbumImage":[""" +
                    """{"ImageKey":"p","Processing":true},{"ImageKey":"q"},{"IsVideo":false}]}}""",
                "/album/e!images" to """{"Response":{}}""",
            ),
        )
        val client = SmugMugClient(replies, "K")
        assertEquals(listOf("q"), client.photoKeys(SmugMugAlbum("a", "A", "Public")))
        assertEquals(emptyList(), client.photoKeys(SmugMugAlbum("e", "E", "Public")))
    }

    @Test
    fun `a photo with no sizes has none, and a size missing a part is passed over`() = runTest {
        val client = SmugMugClient(
            Replies(
                mapOf(
                    "/image/none-0" to """{"Response":{}}""",
                    "/image/odd-0" to """{"Response":{"ImageSizeDetails":{""" +
                        """"ImageSizeX4Large":{"Url":null,"Width":2048,"Height":1365},""" +
                        """"ImageSizeX3Large":{"Url":"https://x/a","Width":1600},""" +
                        """"ImageSizeLarge":{"Url":"https://x/photo","Width":800,"Height":533},""" +
                        """"ImageSizeTiny":"not a size"}}}""",
                ),
            ),
            "K",
        )
        assertNull(client.size("none"))
        val size = client.size("odd")!!
        assertEquals(800 to "jpg", size.width to size.extension, "no extension in the URL: a jpg")
        assertNull(SmugMugPick.bestSize(JsonObject(emptyMap())))
    }

    private companion object {
        const val NAMED = """{"Response":{"User":{"NickName":"grace"}}}"""

        /** A next page given as a whole address, the key already in it. */
        const val NEXT = "https://api.smugmug.com/api/v2/user/g!albums?count=100&start=2&APIKey=K"
    }
}
