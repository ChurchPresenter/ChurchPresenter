package org.churchpresenter.app.churchpresenter.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.utils.Constants
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.app.churchpresenter.testPort

/**
 * Handing a background image or video to a follower instance.
 *
 * An Instance Link follower that opted into mirroring backgrounds fetches each slot's asset through
 * this endpoint, so the mapping from slot name to setting is load-bearing: six slots, each with an
 * image and a video, and the *wrong* mapping shows the overflow room a different background from the
 * main auditorium rather than failing visibly. Every slot is checked in both media kinds.
 *
 * The two 404s matter as much. A slot with nothing configured and a slot whose file has since been
 * deleted must both be refused, because a follower that receives an empty 200 caches it and shows
 * nothing at all.
 *
 * The browser-source page's own not-found paths are here too — it is addressed by the 1-based number
 * the operator sees in Projection Settings, and an index that does not map to a configured output has
 * to say so rather than serving a blank overlay into someone's stream.
 */
class CompanionServerBackgroundAssetTest {

    private lateinit var client: HttpClient

    companion object {
        private lateinit var server: CompanionServer
        private var port: Int = 0
        private lateinit var assetDir: File

        /** Every drawable background slot, so no test can quietly cover only some of them. */
        private val SLOTS = listOf(
            Constants.BACKGROUND_SLOT_DEFAULT,
            Constants.BACKGROUND_SLOT_DEFAULT_LOWER_THIRD,
            Constants.BACKGROUND_SLOT_BIBLE,
            Constants.BACKGROUND_SLOT_BIBLE_LOWER_THIRD,
            Constants.BACKGROUND_SLOT_SONG,
            Constants.BACKGROUND_SLOT_SONG_LOWER_THIRD,
        )

        @JvmStatic
        @BeforeClass
        fun startServer() {
            assetDir = Files.createTempDirectory("cp-backgrounds").toFile()
            server = CompanionServer()
            server.start(port = testPort(39_729))
            port = runBlocking {
                withTimeoutOrNull(10_000) {
                    while (!server.isRunning.value || server.serverUrl.value.isBlank()) {
                        kotlinx.coroutines.delay(25)
                    }
                    server.serverUrl.value.substringAfterLast(':').toInt()
                }
            } ?: error("server did not start")
        }

        @JvmStatic
        @AfterClass
        fun stopServer() {
            runCatching { server.stop() }
            runCatching { assetDir.deleteRecursively() }
        }
    }

    @BeforeTest
    fun openClient() {
        client = HttpClient(CIO)
        server.updateBackgroundSettings(BackgroundSettings())
        server.updateBrowserSourceOutputs(emptyList())
    }

    @AfterTest
    fun closeClient() {
        runCatching { client.close() }
    }

    // ── Harness ─────────────────────────────────────────────────────────────────

    private fun url(path: String) = "http://127.0.0.1:$port$path"

    private fun get(path: String): HttpResponse = runBlocking { client.get(url(path)) }
    private fun HttpResponse.text(): String = runBlocking { bodyAsText() }
    private fun HttpResponse.bytes(): ByteArray = runBlocking { body<ByteArray>() }

    /** A real file on disk whose contents identify which slot it was written for. */
    private fun assetFile(name: String): File =
        File(assetDir, name).also { it.writeText("contents of $name") }

    private fun asset(slot: String, video: Boolean = false): HttpResponse =
        get("${Constants.ENDPOINT_BACKGROUNDS}/asset/$slot" + if (video) "?type=video" else "")

    // ── Every slot, both media kinds ─────────────────────────────────────────────

    @Test
    fun `each slot serves its own image`() {
        // A distinct file per slot, so a mis-mapped slot serves identifiably wrong contents rather
        // than passing because every slot happened to point at the same picture.
        val images = SLOTS.associateWith { assetFile("$it-image.png") }
        server.updateBackgroundSettings(
            BackgroundSettings(
                defaultBackgroundImage = images.getValue(Constants.BACKGROUND_SLOT_DEFAULT).absolutePath,
                defaultLowerThirdBackgroundImage =
                    images.getValue(Constants.BACKGROUND_SLOT_DEFAULT_LOWER_THIRD).absolutePath,
                bibleBackground = BackgroundConfig(
                    backgroundImage = images.getValue(Constants.BACKGROUND_SLOT_BIBLE).absolutePath,
                ),
                bibleLowerThirdBackground = BackgroundConfig(
                    backgroundImage = images.getValue(Constants.BACKGROUND_SLOT_BIBLE_LOWER_THIRD).absolutePath,
                ),
                songBackground = BackgroundConfig(
                    backgroundImage = images.getValue(Constants.BACKGROUND_SLOT_SONG).absolutePath,
                ),
                songLowerThirdBackground = BackgroundConfig(
                    backgroundImage = images.getValue(Constants.BACKGROUND_SLOT_SONG_LOWER_THIRD).absolutePath,
                ),
            )
        )

        SLOTS.forEach { slot ->
            val response = asset(slot)
            assertEquals(HttpStatusCode.OK, response.status, "slot $slot")
            assertEquals("contents of $slot-image.png", response.text(), "slot $slot served the wrong file")
        }
    }

    @Test
    fun `each slot serves its own video`() {
        val videos = SLOTS.associateWith { assetFile("$it-video.mp4") }
        server.updateBackgroundSettings(
            BackgroundSettings(
                defaultBackgroundVideo = videos.getValue(Constants.BACKGROUND_SLOT_DEFAULT).absolutePath,
                defaultLowerThirdBackgroundVideo =
                    videos.getValue(Constants.BACKGROUND_SLOT_DEFAULT_LOWER_THIRD).absolutePath,
                bibleBackground = BackgroundConfig(
                    backgroundVideo = videos.getValue(Constants.BACKGROUND_SLOT_BIBLE).absolutePath,
                ),
                bibleLowerThirdBackground = BackgroundConfig(
                    backgroundVideo = videos.getValue(Constants.BACKGROUND_SLOT_BIBLE_LOWER_THIRD).absolutePath,
                ),
                songBackground = BackgroundConfig(
                    backgroundVideo = videos.getValue(Constants.BACKGROUND_SLOT_SONG).absolutePath,
                ),
                songLowerThirdBackground = BackgroundConfig(
                    backgroundVideo = videos.getValue(Constants.BACKGROUND_SLOT_SONG_LOWER_THIRD).absolutePath,
                ),
            )
        )

        SLOTS.forEach { slot ->
            val response = asset(slot, video = true)
            assertEquals(HttpStatusCode.OK, response.status, "slot $slot")
            assertEquals("contents of $slot-video.mp4", response.text(), "slot $slot served the wrong file")
        }
    }

    @Test
    fun `the image and the video of one slot are different assets`() {
        val image = assetFile("both-image.png")
        val video = assetFile("both-video.mp4")
        server.updateBackgroundSettings(
            BackgroundSettings(
                defaultBackgroundImage = image.absolutePath,
                defaultBackgroundVideo = video.absolutePath,
            )
        )

        assertEquals("contents of both-image.png", asset(Constants.BACKGROUND_SLOT_DEFAULT).text())
        assertEquals(
            "contents of both-video.mp4",
            asset(Constants.BACKGROUND_SLOT_DEFAULT, video = true).text(),
            "the type parameter is what picks between them",
        )
    }

    @Test
    fun `the bytes are served, not a description of them`() {
        val image = assetFile("real.png")
        server.updateBackgroundSettings(BackgroundSettings(defaultBackgroundImage = image.absolutePath))

        val response = asset(Constants.BACKGROUND_SLOT_DEFAULT)

        assertEquals(image.readBytes().size, response.bytes().size)
    }

    // ── The two refusals ────────────────────────────────────────────────────────

    @Test
    fun `a slot with nothing configured is a not-found`() {
        SLOTS.forEach { slot ->
            assertEquals(HttpStatusCode.NotFound, asset(slot).status, "slot $slot")
            assertEquals(HttpStatusCode.NotFound, asset(slot, video = true).status, "slot $slot video")
        }
    }

    @Test
    fun `a configured file that has since been deleted is a not-found`() {
        val image = assetFile("deleted.png")
        server.updateBackgroundSettings(BackgroundSettings(defaultBackgroundImage = image.absolutePath))
        assertEquals(HttpStatusCode.OK, asset(Constants.BACKGROUND_SLOT_DEFAULT).status)

        image.delete()

        // An empty 200 here would leave the follower caching nothing and showing nothing.
        assertEquals(HttpStatusCode.NotFound, asset(Constants.BACKGROUND_SLOT_DEFAULT).status)
    }

    @Test
    fun `a slot name the server does not know is a not-found`() {
        assertEquals(HttpStatusCode.NotFound, asset("not-a-slot").status)
    }

    // ── The browser-source overlay ──────────────────────────────────────────────

    @Test
    fun `a browser source that is not configured is a not-found`() {
        assertEquals(
            HttpStatusCode.NotFound,
            get("${Constants.ENDPOINT_BROWSER_SOURCE}/1").status,
            "nothing should be served into a stream by accident",
        )
    }

    @Test
    fun `a browser source index that is not a number is a not-found`() {
        assertEquals(HttpStatusCode.NotFound, get("${Constants.ENDPOINT_BROWSER_SOURCE}/abc").status)
    }

    @Test
    fun `browser sources are addressed from one, as the operator sees them`() {
        // Projection Settings calls the first one "Browser Source 1", so /0 must not resolve to it.
        assertEquals(HttpStatusCode.NotFound, get("${Constants.ENDPOINT_BROWSER_SOURCE}/0").status)
    }

    @Test
    fun `the backgrounds summary is served even with nothing configured`() {
        val response = get(Constants.ENDPOINT_BACKGROUNDS)

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.text().isNotBlank(), "a follower needs an answer to compare against")
    }
}
