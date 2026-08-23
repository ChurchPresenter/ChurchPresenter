package org.churchpresenter.companionserver

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstanceLinkClientFetchGuardTest {

    private val clients = mutableListOf<InstanceLinkClient>()
    private var server: CompanionServer? = null
    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-link-fetch").toFile()
    }

    @AfterTest
    fun cleanUp() {
        clients.forEach { runCatching { it.dispose() } }
        clients.clear()
        runCatching { server?.stop() }
        server = null
        dir.deleteRecursively()
    }

    private fun client() = InstanceLinkClient(
        onStatusChanged = {},
        onScheduleUpdated = {},
        onLiveStateUpdated = {},
        onDisplayCleared = {},
        onSongSectionSelected = {},
        onPresentationSlideChanged = { _, _, _, _, _ -> },
        onSongsUpdated = {},
    ).also { clients.add(it) }

    private fun startPrimary(apiKey: String): Int {
        val started = CompanionServer()
        started.updateApiKey(enabled = apiKey.isNotEmpty(), key = apiKey)
        started.start(port = testPort(39_840))
        server = started
        return runBlocking {
            withTimeoutOrNull(10_000) {
                while (!started.isRunning.value || started.serverUrl.value.isBlank()) {
                    kotlinx.coroutines.delay(25)
                }
                started.serverUrl.value.substringAfterLast(':').toInt()
            }
        } ?: error("server did not start")
    }

    private fun awaitUntil(what: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    @Test
    fun `every fetch is refused before the link has ever been opened`() = runBlocking {
        val client = client()

        assertNull(client.fetchSongDetail("42", "Hymns"))
        assertNull(client.fetchPictureImageBytes("folder-1", 0))
        assertNull(client.fetchPresentationSlideBytes("deck-1", 0))
        assertNull(client.fetchBibleFile())
        assertNull(client.fetchSecondaryBibleFile())
        assertNull(client.fetchLowerThirdJson("speaker"))
        assertNull(client.fetchBackgroundSettings())
        assertNull(client.fetchBackgroundAsset("default", isVideo = false))
        assertTrue(client.fetchBibleTranslations().isEmpty())
    }

    @Test
    fun `a media stream url cannot be built before the link has ever been opened`() {
        assertNull(client().mediaStreamUrl("media-1"))
    }

    @Test
    fun `a media stream url carries the api key as a query parameter`() {
        val client = client()
        client.connect(host = "127.0.0.1", port = 1234, apiKey = "s3cret", deviceId = "d", reconnectDelayMs = 60_000)

        val url = assertNotNull(client.mediaStreamUrl("media-1"))

        assertTrue(url.startsWith("http://127.0.0.1:1234"), url)
        assertTrue(url.contains("media-1"), url)
        assertTrue(url.contains("s3cret"), url)
    }

    @Test
    fun `a media stream url without an api key carries no query parameter`() {
        val client = client()
        client.connect(host = "127.0.0.1", port = 1234, apiKey = "", deviceId = "d", reconnectDelayMs = 60_000)

        val url = assertNotNull(client.mediaStreamUrl("media-1"))

        assertTrue(url.endsWith("media-1"), url)
    }

    @Test
    fun `a follower carrying the api key is served every asset`() {
        val primaryPort = startPrimary(apiKey = "s3cret")
        val primary = requireNotNull(server)
        val bibleFile = SpbFixture.spbFile(dir, name = "p.spb")
        primary.updateBible(SpbFixture.loadedBible(dir), "KJV", filePath = bibleFile.absolutePath)
        primary.updateSongs(
            listOf(
                SongItem(
                    number = "42", title = "Amazing Grace", songbook = "Hymns",
                    lyrics = listOf("[Verse 1]", "Amazing grace"),
                ),
            ),
        )
        val image = File(dir, "a.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        primary.updatePictures("folder-1", "Slides", dir.absolutePath, listOf(image))

        val client = client()
        client.connect(
            host = "127.0.0.1", port = primaryPort, apiKey = "s3cret",
            deviceId = "follower", reconnectDelayMs = 60_000,
        )
        awaitUntil("the bible to be fetchable with the key") {
            runBlocking { client.fetchBibleFile() } != null
        }

        runBlocking {
            assertEquals(
                listOf<Byte>(1, 2, 3, 4),
                assertNotNull(client.fetchPictureImageBytes("folder-1", 0)).toList(),
            )
            assertEquals("Amazing Grace", assertNotNull(client.fetchSongDetail("42", "Hymns")).title)
        }
    }

    @Test
    fun `a follower carrying the api key is served every bible module the primary has`() {
        // The bible endpoints are separate from the assets above and each attaches the key on its
        // own. A follower mirroring the primary's translations downloads the whole set through
        // these three, so a key missing from any one of them leaves the follower with a partial
        // library and no error to explain it.
        val primaryPort = startPrimary(apiKey = "s3cret")
        val primary = requireNotNull(server)
        val primaryFile = SpbFixture.spbFile(dir, name = "p.spb")
        val secondaryFile = SpbFixture.spbFile(
            dir,
            name = "s.spb",
            content = SpbFixture.sampleContent(title = "Secondary Bible"),
        )
        primary.updateBible(SpbFixture.loadedBible(dir), "KJV", filePath = primaryFile.absolutePath)
        primary.updateSecondaryBibleFilePath(secondaryFile.absolutePath)
        primary.updateBibleFilePaths(listOf(primaryFile.absolutePath, secondaryFile.absolutePath))

        val client = client()
        client.connect(
            host = "127.0.0.1", port = primaryPort, apiKey = "s3cret",
            deviceId = "follower", reconnectDelayMs = 60_000,
        )
        awaitUntil("the bible to be fetchable with the key") {
            runBlocking { client.fetchBibleFile() } != null
        }

        runBlocking {
            assertEquals(
                secondaryFile.readBytes().toList(),
                assertNotNull(client.fetchSecondaryBibleFile()).toList(),
            )
            assertEquals(
                listOf("p.spb", "s.spb"),
                client.fetchBibleTranslations().map { it.first },
                "the manifest order is the presentation order and has to survive the download",
            )
        }
    }

    @Test
    fun `a follower without the api key is refused by a protected primary`() {
        val primaryPort = startPrimary(apiKey = "s3cret")
        val primary = requireNotNull(server)
        val bibleFile = SpbFixture.spbFile(dir, name = "p.spb")
        primary.updateBible(SpbFixture.loadedBible(dir), "KJV", filePath = bibleFile.absolutePath)

        val client = client()
        client.connect(
            host = "127.0.0.1", port = primaryPort, apiKey = "",
            deviceId = "follower", reconnectDelayMs = 60_000,
        )

        assertNull(
            runBlocking { client.fetchBibleFile() },
            "an unauthenticated follower must not be handed the primary's bible",
        )
        assertTrue(runBlocking { client.fetchBibleTranslations() }.isEmpty())
    }

    @Test
    fun `a fetch against a primary that is not listening fails without throwing`() = runBlocking {
        val client = client()
        client.connect(
            host = "127.0.0.1", port = 1, apiKey = "",
            deviceId = "follower", reconnectDelayMs = 60_000,
        )

        assertNull(client.fetchBibleFile())
        assertNull(client.fetchSongDetail("42", ""))
        assertNull(client.fetchBackgroundSettings())
        assertNull(client.fetchBackgroundAsset("default", isVideo = true))
        assertTrue(client.fetchBibleTranslations().isEmpty())
    }

    @Test
    fun `a lower third that the primary does not have is a clean miss`() {
        val primaryPort = startPrimary(apiKey = "")

        val client = client()
        client.connect(
            host = "127.0.0.1", port = primaryPort, apiKey = "",
            deviceId = "follower", reconnectDelayMs = 60_000,
        )

        assertNull(
            runBlocking { client.fetchLowerThirdJson("Speaker Name") },
            "a name with a space must still form a valid request, so this is a 404 and not a throw",
        )
    }

    @Test
    fun `a background slot with nothing configured is a clean miss`() {
        val primaryPort = startPrimary(apiKey = "")

        val client = client()
        client.connect(
            host = "127.0.0.1", port = primaryPort, apiKey = "",
            deviceId = "follower", reconnectDelayMs = 60_000,
        )

        runBlocking {
            assertNull(client.fetchBackgroundAsset("default", isVideo = false))
            assertNull(client.fetchBackgroundAsset("default", isVideo = true))
        }
    }

    @Test
    fun `a presentation slide the primary has never rendered is a clean miss`() {
        val primaryPort = startPrimary(apiKey = "")

        val client = client()
        client.connect(
            host = "127.0.0.1", port = primaryPort, apiKey = "",
            deviceId = "follower", reconnectDelayMs = 60_000,
        )

        assertNull(runBlocking { client.fetchPresentationSlideBytes("no-such-deck", 0) })
    }

    @Test
    fun `a secondary bible the primary has not configured is a clean miss`() {
        val primaryPort = startPrimary(apiKey = "")

        val client = client()
        client.connect(
            host = "127.0.0.1", port = primaryPort, apiKey = "",
            deviceId = "follower", reconnectDelayMs = 60_000,
        )

        assertNull(runBlocking { client.fetchSecondaryBibleFile() })
    }

    @Test
    fun `a follower carrying the api key is served the primary's backgrounds`() {
        val primaryPort = startPrimary(apiKey = "s3cret")
        val primary = requireNotNull(server)
        primary.updateBackgroundSettings(BackgroundSettings(defaultBackgroundColor = "#123456"))

        val client = client()
        client.connect(
            host = "127.0.0.1", port = primaryPort, apiKey = "s3cret",
            deviceId = "follower", reconnectDelayMs = 60_000,
        )

        val mirrored = assertNotNull(runBlocking { client.fetchBackgroundSettings() })
        assertEquals("#123456", mirrored.defaultBackgroundColor)
    }

    @Test
    fun `a follower without the api key is refused the primary's backgrounds`() {
        val primaryPort = startPrimary(apiKey = "s3cret")
        requireNotNull(server).updateBackgroundSettings(BackgroundSettings(defaultBackgroundColor = "#123456"))

        val client = client()
        client.connect(
            host = "127.0.0.1", port = primaryPort, apiKey = "",
            deviceId = "follower", reconnectDelayMs = 60_000,
        )

        runBlocking {
            assertNull(client.fetchBackgroundSettings())
            assertNull(client.fetchBackgroundAsset("default", isVideo = false))
            assertNull(client.fetchLowerThirdJson("Speaker Name"))
            assertNull(client.fetchPresentationSlideBytes("deck-1", 0))
        }
    }

    // ── The other half: what a follower gets when the primary DOES have the asset ────
    //
    // Everything above this line is a refusal or a miss. Those are the paths that matter most for
    // resilience, but they are also the paths that pass when the fetch is broken outright — a client
    // that always returned null would satisfy all of them. These pin the successful fetch.

    /** A follower already connected to a primary on [port], with no API key in play. */
    private fun connectedFollower(port: Int): InstanceLinkClient = client().also {
        it.connect(host = "127.0.0.1", port = port, apiKey = "", deviceId = "follower", reconnectDelayMs = 60_000)
    }

    @Test
    fun `a lower third the primary has is served whole, spaces in its name and all`() {
        val primaryPort = startPrimary(apiKey = "")
        val lottie = """{"v":"5.7.4","fr":30,"ip":0,"op":60,"w":1920,"h":1080,"layers":[]}"""
        // The space is the point: the client swaps URLEncoder's "+" for "%20" precisely because a
        // path segment does not decode "+" back to a space, and this is the only test that would
        // notice if that swap were removed.
        File(dir, "Speaker Name.json").writeText(lottie)
        requireNotNull(server).updateAtemConfig(org.churchpresenter.settings.AtemSettings(), dir.absolutePath)

        val client = connectedFollower(primaryPort)

        assertEquals(
            lottie,
            assertNotNull(runBlocking { client.fetchLowerThirdJson("Speaker Name") }).decodeToString(),
            "the follower plays the primary's animation, so it has to arrive byte for byte",
        )
    }

    @Test
    fun `a lower third is found whatever case the follower asks in`() {
        val primaryPort = startPrimary(apiKey = "")
        val lottie = """{"v":"5.7.4","fr":30,"ip":0,"op":60,"w":10,"h":10,"layers":[]}"""
        File(dir, "Welcome.json").writeText(lottie)
        requireNotNull(server).updateAtemConfig(org.churchpresenter.settings.AtemSettings(), dir.absolutePath)

        val client = connectedFollower(primaryPort)

        assertNotNull(runBlocking { client.fetchLowerThirdJson("welcome") })
    }

    @Test
    fun `a rendered slide is served to the follower byte for byte`() {
        val primaryPort = startPrimary(apiKey = "")
        val slide = File(dir, "slide0.jpg").apply { writeBytes(byteArrayOf(9, 8, 7)) }
        val deck = File(dir, "deck.pptx").apply { writeText("deck") }
        requireNotNull(server).updatePresentation("d1", deck.absolutePath, "Deck.pptx", "pptx", listOf(slide))

        val client = connectedFollower(primaryPort)

        awaitUntil("the slide to be rendered and cached") {
            runBlocking { client.fetchPresentationSlideBytes("d1", 0) } != null
        }
        assertEquals(
            listOf<Byte>(9, 8, 7),
            assertNotNull(runBlocking { client.fetchPresentationSlideBytes("d1", 0) }).toList(),
        )
    }

    @Test
    fun `a slide index past the end of the deck is a clean miss rather than the wrong slide`() {
        val primaryPort = startPrimary(apiKey = "")
        val slide = File(dir, "only.jpg").apply { writeBytes(byteArrayOf(1)) }
        val deck = File(dir, "one.pptx").apply { writeText("deck") }
        requireNotNull(server).updatePresentation("d2", deck.absolutePath, "One.pptx", "pptx", listOf(slide))

        val client = connectedFollower(primaryPort)

        awaitUntil("the deck to be cached") { runBlocking { client.fetchPresentationSlideBytes("d2", 0) } != null }
        assertNull(runBlocking { client.fetchPresentationSlideBytes("d2", 9) })
    }

    @Test
    fun `the secondary bible the primary has configured is served`() {
        val primaryPort = startPrimary(apiKey = "")
        val secondary = SpbFixture.spbFile(dir, name = "secondary.spb")
        requireNotNull(server).updateSecondaryBibleFilePath(secondary.absolutePath)

        val client = connectedFollower(primaryPort)

        assertEquals(
            secondary.readBytes().size,
            assertNotNull(runBlocking { client.fetchSecondaryBibleFile() }).size,
            "a follower caching a truncated module would show wrong verses, not fail loudly",
        )
    }

    @Test
    fun `a configured background image is served to a follower mirroring backgrounds`() {
        val primaryPort = startPrimary(apiKey = "")
        val image = File(dir, "bg.jpg").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        requireNotNull(server).updateBackgroundSettings(
            BackgroundSettings(defaultBackgroundImage = image.absolutePath),
        )

        val client = connectedFollower(primaryPort)

        assertEquals(
            listOf<Byte>(4, 5, 6),
            assertNotNull(
                runBlocking { client.fetchBackgroundAsset(Constants.BACKGROUND_SLOT_DEFAULT, isVideo = false) },
            ).toList(),
        )
    }

    @Test
    fun `the primary's background settings are readable by a follower`() {
        val primaryPort = startPrimary(apiKey = "")
        val image = File(dir, "bg2.jpg").apply { writeBytes(byteArrayOf(1)) }
        requireNotNull(server).updateBackgroundSettings(
            BackgroundSettings(defaultBackgroundImage = image.absolutePath),
        )

        val client = connectedFollower(primaryPort)

        assertEquals(
            image.absolutePath,
            assertNotNull(runBlocking { client.fetchBackgroundSettings() }).defaultBackgroundImage,
        )
    }
}
