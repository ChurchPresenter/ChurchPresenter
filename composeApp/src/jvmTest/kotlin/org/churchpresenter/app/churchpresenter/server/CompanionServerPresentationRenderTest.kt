package org.churchpresenter.app.churchpresenter.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.testPort

/**
 * What the server does when a presentation lands in the schedule: load the deck, rasterise every
 * slide, and publish a catalogue the phone can browse.
 *
 * The existing presentation suites all start from slide bytes that were handed to the server
 * already. This one starts from a **file on disk** and goes through `PresentationLoader` and
 * `DeckRasterizer` for real, which is the half a mobile client actually depends on — it asks for
 * `/api/presentations` and gets whatever the render produced, or an empty list and no explanation.
 *
 * A PDF built with PDFBox is the deck, following `PresentationTabRealDeckTest`: it is a real format
 * the loader supports, costs a few milliseconds a page, and needs no Office file checked into the
 * repository.
 *
 * The render happens on a background coroutine and the schedule update returns immediately, so every
 * assertion waits for the catalogue entry to appear rather than for the call to return.
 *
 * `user.home` is swapped for the class: the slide disk cache lives under it, and a cache entry left
 * by an earlier run would make a re-render indistinguishable from a hit.
 */
class CompanionServerPresentationRenderTest {

    private lateinit var client: HttpClient

    companion object {
        private lateinit var server: CompanionServer
        private lateinit var tempHome: File
        private lateinit var deckDir: File
        private var realHome: String? = null
        private val PORT = testPort(39_890)

        @JvmStatic
        @BeforeClass
        fun startServer() {
            TestSingletons.latchToTestHome()
            TestSingletons.latchSkikoNativeLibrary()
            realHome = System.getProperty("user.home")
            tempHome = Files.createTempDirectory("cp-presentation-render-home").toFile()
            System.setProperty("user.home", tempHome.absolutePath)

            deckDir = Files.createTempDirectory("cp-presentation-render-decks").toFile()

            server = CompanionServer()
            server.start(port = PORT)
            runBlocking {
                withTimeoutOrNull(10_000) {
                    while (!server.isRunning.value || server.serverUrl.value.isBlank()) {
                        kotlinx.coroutines.delay(25)
                    }
                    true
                }
            } ?: error("companion server did not start")

            // Rasterising the first deck builds PDFBox's on-disk font cache (~1.5s on a cold CI
            // runner). Pay it here rather than letting it land on whichever test happens to run
            // first, where it would read as a per-test cost.
            val warmUp = pdf(pages = 1)
            val warmUpPresentationId = warmUp.absolutePath.hashCode().toUInt().toString(16)
            server.updateSchedule(
                listOf(
                    ScheduleItem.PresentationItem(
                        id = "warm-up", filePath = warmUp.absolutePath,
                        fileName = warmUp.nameWithoutExtension, slideCount = 1, fileType = "pdf",
                    )
                )
            )
            val warmed = runBlocking {
                withTimeoutOrNull(60_000) {
                    while (server.presentations._presentationCatalogs[warmUpPresentationId] == null) {
                        delay(50)
                    }
                    true
                } ?: false
            }
            check(warmed) { "the companion server never rendered the warm-up deck" }
            server.updateSchedule(emptyList())
        }

        @JvmStatic
        @AfterClass
        fun stopServer() {
            server.stop()
            realHome?.let { System.setProperty("user.home", it) }
            tempHome.deleteRecursively()
            deckDir.deleteRecursively()
        }

        private var counter = 0

        /** A real PDF with one legible line per page — enough for the rasteriser to produce a slide. */
        fun pdf(pages: Int): File {
            val file = File(deckDir, "deck${counter++}.pdf")
            PDDocument().use { doc ->
                repeat(pages) { index ->
                    val page = PDPage()
                    doc.addPage(page)
                    PDPageContentStream(doc, page).use { stream ->
                        stream.beginText()
                        stream.setFont(PDType1Font.HELVETICA_BOLD, 36f)
                        stream.newLineAtOffset(72f, 500f)
                        stream.showText("Slide ${index + 1}")
                        stream.endText()
                    }
                }
                doc.save(file)
            }
            return file
        }
    }

    @AfterTest
    fun closeClient() {
        server.updateSchedule(emptyList())
        if (::client.isInitialized) client.close()
    }

    private fun http(): HttpClient {
        if (!::client.isInitialized) client = HttpClient(CIO)
        return client
    }

    private fun scheduleDeck(file: File, pages: Int, id: String = "item-${file.name}") {
        server.updateSchedule(
            listOf(
                ScheduleItem.PresentationItem(
                    id = id,
                    filePath = file.absolutePath,
                    fileName = file.nameWithoutExtension,
                    slideCount = pages,
                    fileType = "pdf",
                )
            )
        )
    }

    /**
     * Asks for one deck by its schedule-item id.
     *
     * Deliberately not `GET /api/presentations`: that list serves the catalogue the desktop app
     * pushes, not the one the server renders for itself, so it stays empty here however well the
     * render goes. The by-id route is the one the render populates — and the one a phone follows,
     * since the schedule gives it the item id before any slide exists.
     */
    private fun deck(scheduleItemId: String): Pair<HttpStatusCode, String> = runBlocking {
        val response = http().get(
            "http://127.0.0.1:$PORT${Constants.ENDPOINT_PRESENTATIONS}/$scheduleItemId"
        )
        response.status to response.bodyAsText()
    }

    /** The route 404s until the background render commits, so that first 200 is the signal. */
    private fun awaitDeck(scheduleItemId: String, timeoutMs: Long = 30_000): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val (status, body) = deck(scheduleItemId)
            if (status == HttpStatusCode.OK) return body
            Thread.sleep(50)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for '$scheduleItemId' to be rendered")
    }

    // ── Rendering a scheduled deck ──────────────────────────────────────────────

    @Test
    fun `a deck added to the schedule is rendered and published for the phone`() {
        val file = pdf(pages = 3)

        scheduleDeck(file, pages = 3, id = "deck-a")

        val body = awaitDeck("deck-a")
        assertTrue(body.contains(""""slide-total":3"""), "every page becomes a slide: $body")
        assertTrue(body.contains(""""file-type":"pdf""""), body)
        assertTrue(body.contains(file.nameWithoutExtension), body)
    }

    @Test
    fun `each rendered slide is served as a real image`() {
        val file = pdf(pages = 2)
        scheduleDeck(file, pages = 2, id = "deck-b")
        awaitDeck("deck-b")

        val id = file.absolutePath.hashCode().toUInt().toString(16)
        val response = runBlocking {
            http().get(
                "http://127.0.0.1:$PORT${Constants.ENDPOINT_PRESENTATIONS}/$id/slides/0",
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val bytes = runBlocking { response.readBytes() }
        assertTrue(bytes.size > 1_000, "a slide of ${bytes.size} bytes is not a rendered page")
        assertEquals(0xFF.toByte(), bytes[0], "JPEG magic byte 0")
        assertEquals(0xD8.toByte(), bytes[1], "JPEG magic byte 1")
    }

    @Test
    fun `a deck whose file has gone is skipped without taking the rest of the schedule with it`() {
        // Paired deliberately: a missing file produces nothing observable on its own, so the real
        // deck beside it is the signal that the schedule was processed at all.
        val missing = File(deckDir, "deleted-before-the-service.pdf")
        val real = pdf(pages = 1)

        server.updateSchedule(
            listOf(
                ScheduleItem.PresentationItem(
                    id = "gone", filePath = missing.absolutePath,
                    fileName = missing.nameWithoutExtension, slideCount = 4, fileType = "pdf",
                ),
                ScheduleItem.PresentationItem(
                    id = "here", filePath = real.absolutePath,
                    fileName = real.nameWithoutExtension, slideCount = 1, fileType = "pdf",
                ),
            )
        )

        awaitDeck("here")
        assertEquals(
            HttpStatusCode.NotFound, deck("gone").first,
            "a deck whose file vanished must not be advertised to the phone"
        )
    }

    @Test
    fun `a deck already rendered is served straight back rather than rendered again`() {
        val file = pdf(pages = 2)
        scheduleDeck(file, pages = 2, id = "first")
        awaitDeck("first")

        server.updateSchedule(emptyList())
        scheduleDeck(file, pages = 2, id = "second")

        val body = awaitDeck("second")
        assertTrue(body.contains(""""slide-total":2"""), "the cached render has to describe the same deck: $body")
    }
}
