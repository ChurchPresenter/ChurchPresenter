package org.churchpresenter.companionserver

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.settings.BackgroundSettings
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
}
