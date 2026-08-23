package org.churchpresenter.companionserver

import org.churchpresenter.core.models.schedule.ScheduleItem
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Turning what a phone sends into a schedule item, when the phone did not send enough to build one.
 *
 * A companion app adds a picture folder or a deck **by id**, not by path — it has never seen the
 * desktop's filesystem. The server resolves that id against what it already knows: the picture
 * catalogues it has published, the decks it has been told about, and failing both, the schedule
 * itself. `RemoteItemDtoTest` covers the other direction, where the phone sends a path and the DTO
 * maps straight across; this covers the half that needs the server's own state.
 *
 * The order matters and is asserted: picture first, then presentation, then the plain mapping. An
 * item that resolves as neither must still become *something* rather than being dropped, because a
 * dropped add looks to the operator exactly like a phone that never sent one.
 *
 * No server is started — `parseRemoteItem` is an ordinary method, and binding Netty to exercise it
 * would only make the suite slower.
 */
class RemoteItemResolutionTest {

    private val server = CompanionServer()
    private val temp = Files.createTempDirectory("remote-item").toFile()

    @AfterTest
    fun cleanUp() {
        runCatching { server.stop() }
        temp.deleteRecursively()
    }

    private fun request(body: String) = """{"item":$body}"""

    private fun deckFile(name: String) = File(temp, name).also { it.writeText("deck") }

    // ── Pictures resolved by folder id ──────────────────────────────────────────

    @Test
    fun `a folder id the server has published resolves to that folder`() {
        val folder = File(temp, "photos").also { it.mkdirs() }
        server.updatePictures("f1", "Holiday Club", folder.absolutePath, listOf(deckFile("a.jpg")))

        val item = server.parseRemoteItem(request("""{"id":"i1","folder-id":"f1"}"""))

        val picture = assertIs<ScheduleItem.PictureItem>(item)
        assertEquals(folder.absolutePath, picture.folderPath, "the path comes from the catalogue, not the phone")
        assertEquals("Holiday Club", picture.folderName)
        assertEquals(1, picture.imageCount)
        assertEquals("i1", picture.id)
    }

    @Test
    fun `a folder id with no catalogue behind it does not become a picture`() {
        val item = server.parseRemoteItem(request("""{"id":"i1","folder-id":"never-published"}"""))

        assertTrue(item !is ScheduleItem.PictureItem, "guessing a path for an unknown folder would add a broken row")
    }

    @Test
    fun `a folder sent with its own path is taken at face value`() {
        val folder = File(temp, "own-path").also { it.mkdirs() }
        server.updatePictures("f2", "Catalogued", folder.absolutePath, emptyList())

        val item = server.parseRemoteItem(
            request("""{"id":"i1","folder-id":"f2","folderPath":"/somewhere/else","folderName":"Sent"}"""),
        )

        val picture = assertIs<ScheduleItem.PictureItem>(item)
        assertEquals("/somewhere/else", picture.folderPath, "an explicit path is not second-guessed")
    }

    @Test
    fun `a picture added without an id is given one`() {
        val folder = File(temp, "no-id").also { it.mkdirs() }
        server.updatePictures("f3", "Folder", folder.absolutePath, emptyList())

        val picture = assertIs<ScheduleItem.PictureItem>(
            server.parseRemoteItem(request("""{"folder-id":"f3"}""")),
        )

        assertTrue(picture.id.isNotBlank(), "a row with no id cannot be reordered or removed afterwards")
    }

    // ── Decks resolved by id ────────────────────────────────────────────────────

    @Test
    fun `a deck resolves by path the moment it is registered, before its catalogue exists`() {
        val deck = deckFile("sermon.pptx")
        server.updatePresentation("d1", deck.absolutePath, "Sermon.pptx", "pptx", listOf(deckFile("s1.png")))

        // No wait: updatePresentation records the path synchronously and builds the catalogue on a
        // coroutine, so this is the window in which the deck is addressable but not yet described.
        val pres = assertIs<ScheduleItem.PresentationItem>(
            server.parseRemoteItem(request("""{"id":"d1","type":"presentation"}""")),
        )

        assertEquals(deck.absolutePath, pres.filePath, "the path is what makes the row usable at all")
    }

    @Test
    fun `once the catalogue is built the deck carries its name, slide count and type`() {
        val deck = deckFile("sermon2.pptx")
        server.updatePresentation("d1b", deck.absolutePath, "Sermon.pptx", "pptx", listOf(deckFile("s1.png")))

        waitFor("the deck catalogue to be built") {
            val item = server.parseRemoteItem(request("""{"id":"d1b","type":"presentation"}"""))
            (item as? ScheduleItem.PresentationItem)?.fileName == "Sermon.pptx"
        }

        val pres = assertIs<ScheduleItem.PresentationItem>(
            server.parseRemoteItem(request("""{"id":"d1b","type":"presentation"}""")),
        )
        assertEquals(deck.absolutePath, pres.filePath)
        assertEquals(1, pres.slideCount)
        assertEquals("pptx", pres.fileType)
    }

    /** Polls to a deadline and throws on expiry, so a timeout is a failure rather than a pass. */
    private fun waitFor(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("timed out waiting for $what")
    }

    @Test
    fun `a deck id with no type still resolves, because older clients omit it`() {
        val deck = deckFile("untyped.pptx")
        server.updatePresentation("d2", deck.absolutePath, "Untyped.pptx", "pptx", emptyList())

        val pres = assertIs<ScheduleItem.PresentationItem>(
            server.parseRemoteItem(request("""{"id":"d2"}""")),
        )

        assertEquals(deck.absolutePath, pres.filePath)
    }

    @Test
    fun `an id that names a schedule row is resolved from the schedule`() {
        val deck = deckFile("scheduled.pptx")
        server.updateSchedule(
            listOf(
                ScheduleItem.PresentationItem(
                    id = "row-1", filePath = deck.absolutePath, fileName = "Scheduled.pptx",
                    slideCount = 3, fileType = "pptx",
                ),
            ),
        )

        val pres = assertIs<ScheduleItem.PresentationItem>(
            server.parseRemoteItem(request("""{"id":"row-1","type":"presentation"}""")),
        )

        assertEquals(deck.absolutePath, pres.filePath)
        assertNotNull(pres.id)
        assertTrue(pres.id != "row-1", "adding a scheduled deck again makes a new row, it does not move the old one")
    }

    @Test
    fun `an id that is a file-path hash resolves too, which is what the phone actually sends`() {
        val deck = deckFile("hashed.pptx")
        server.updateSchedule(
            listOf(
                ScheduleItem.PresentationItem(
                    id = "row-2", filePath = deck.absolutePath, fileName = "Hashed.pptx",
                    slideCount = 1, fileType = "pptx",
                ),
            ),
        )
        val hash = deck.absolutePath.hashCode().toUInt().toString(16)

        val pres = assertIs<ScheduleItem.PresentationItem>(
            server.parseRemoteItem(request("""{"id":"$hash","type":"presentation"}""")),
        )

        assertEquals(deck.absolutePath, pres.filePath)
    }

    @Test
    fun `a deck id that resolves nowhere does not become a presentation`() {
        val item = server.parseRemoteItem(request("""{"id":"unknown-deck","type":"presentation"}"""))

        assertTrue(item !is ScheduleItem.PresentationItem, "a row pointing at no file would fail on go-live")
    }

    @Test
    fun `a deck sent with its own path skips the lookup entirely`() {
        val deck = deckFile("explicit.pptx")
        server.updatePresentation("d3", "/registered/elsewhere.pptx", "Registered.pptx", "pptx", emptyList())

        val pres = assertIs<ScheduleItem.PresentationItem>(
            server.parseRemoteItem(
                request("""{"id":"d3","type":"presentation","filePath":"${deck.absolutePath}"}"""),
            ),
        )

        assertEquals(deck.absolutePath, pres.filePath, "an explicit path wins over the registry")
    }

    @Test
    fun `a deck id sent with a blank id is not resolved`() {
        val deck = deckFile("blank-id.pptx")
        server.updatePresentation("", deck.absolutePath, "Blank.pptx", "pptx", emptyList())

        val item = server.parseRemoteItem(request("""{"id":"","type":"presentation"}"""))

        assertTrue(item !is ScheduleItem.PresentationItem, "a blank id matches everything and must match nothing")
    }

    @Test
    fun `an id that belongs to a deck is not claimed by a different type`() {
        val deck = deckFile("typed.pptx")
        server.updatePresentation("d4", deck.absolutePath, "Typed.pptx", "pptx", emptyList())

        val item = server.parseRemoteItem(request("""{"id":"d4","type":"song","songNumber":4,"title":"Hymn"}"""))

        assertIs<ScheduleItem.SongItem>(item)
    }

    // ── Neither ─────────────────────────────────────────────────────────────────

    @Test
    fun `a body that is not a remote item at all is refused rather than guessed at`() {
        assertNull(server.parseRemoteItem("""{"nonsense":true}"""))
        assertNull(server.parseRemoteItem("not json"))
    }
}
