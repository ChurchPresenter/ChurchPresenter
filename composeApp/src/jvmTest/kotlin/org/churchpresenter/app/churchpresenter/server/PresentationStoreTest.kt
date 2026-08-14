package org.churchpresenter.app.churchpresenter.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PresentationStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val broadcasts = mutableListOf<WebSocketMessage>()

    private fun store() = PresentationStore(
        Json { ignoreUnknownKeys = true },
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    ) { broadcasts.add(it) }

    private fun slides(vararg names: String): List<File> {
        val dir = temp.newFolder(names.hashCode().toString())
        return names.map { File(dir, it).apply { writeBytes(byteArrayOf(1, 2, 3)) } }
    }

    @Test
    fun `a presentation is catalogued and its file path recorded`() {
        val s = store()
        s.updatePresentation("p1", "/decks/sermon.pptx", "sermon.pptx", "pptx", slides("0.jpg", "1.jpg"))

        assertEquals("/decks/sermon.pptx", s._presentationFilePaths["p1"])
        assertEquals(2, s._presentationCatalogs["p1"]?.slideTotal)
        assertEquals(1, s._presentationCatalog.value.total)
    }

    @Test
    fun `slide bytes are cached so a phone can fetch them without touching disk again`() {
        val s = store()
        s.updatePresentation("p1", "/decks/a.pptx", "a.pptx", "pptx", slides("0.jpg", "1.jpg", "2.jpg"))
        assertEquals(3, s._slideBytes["p1"]?.size)
    }

    @Test
    fun `publishing a presentation tells connected clients`() {
        broadcasts.clear()
        val s = store()
        s.updatePresentation("p1", "/decks/a.pptx", "a.pptx", "pptx", slides("0.jpg"))
        assertTrue(
            broadcasts.any {
                it.type == org.churchpresenter.app.churchpresenter.utils.Constants.WS_EVENT_PRESENTATION_UPDATED
            }
        )
    }

    @Test
    fun `a blank file path is not recorded, because there is no file to serve`() {
        val s = store()
        s.updatePresentation("p1", "", "a.pptx", "pptx", slides("0.jpg"))
        assertNull(s._presentationFilePaths["p1"])
    }

    @Test
    fun `presenter notes travel with the presentation`() {
        val s = store()
        s.updatePresentation("p1", "/d/a.pptx", "a.pptx", "pptx", slides("0.jpg"), listOf("hello", "world"))
        assertEquals(listOf("hello", "world"), s._presentationNotes["p1"])
    }

    @Test
    fun `slides that vanish before the read are skipped rather than crashing`() {
        val s = store()
        val files = slides("0.jpg")
        files.forEach { it.delete() }
        s.updatePresentation("p1", "/d/a.pptx", "a.pptx", "pptx", files)
        assertNull(s._slideBytes["p1"])
    }

    @Test
    fun `only the five most recent presentations keep their slide bytes`() {
        // The cache is bounded so a long schedule cannot grow the heap without limit; the oldest
        // deck's bytes go first, and its notes go with them.
        val s = store()
        repeat(6) { i ->
            s.updatePresentation("p$i", "/d/$i.pptx", "$i.pptx", "pptx", slides("$i-0.jpg"), listOf("note$i"))
        }
        assertFalse(s._slideBytes.containsKey("p0"), "the oldest deck should have been evicted")
        assertNull(s._presentationNotes["p0"])
        assertTrue(s._slideBytes.containsKey("p5"))
        assertEquals(5, s._slideBytes.size)
    }

    @Test
    fun `re-publishing a presentation refreshes it rather than evicting others`() {
        val s = store()
        repeat(5) { i -> s.updatePresentation("p$i", "/d/$i.pptx", "$i.pptx", "pptx", slides("$i.jpg")) }
        s.updatePresentation("p0", "/d/0.pptx", "0.pptx", "pptx", slides("0-again.jpg"))
        assertEquals(5, s._slideBytes.size)
        assertTrue(s._slideBytes.containsKey("p0"))
    }

    @Test
    fun `the catalogue starts empty`() {
        val s = store()
        assertEquals(0, s._presentationCatalog.value.total)
        assertTrue(s._slideBytes.isEmpty())
        assertNull(s._lastDeviceUploadedPresentationId)
    }

    @Test
    fun `the last device upload is remembered so the next one can replace it`() {
        val s = store()
        s._lastDeviceUploadedPresentationId = "uploaded-1"
        assertEquals("uploaded-1", s._lastDeviceUploadedPresentationId)
    }

    @Test
    fun `rendering a file that is not there does nothing`() {
        val s = store()
        s.renderPresentationForServer("ghost", File(temp.root, "missing.pptx").absolutePath)
        assertNull(s._slideBytes["ghost"])
        assertNull(s._presentationCatalogs["ghost"])
    }

    @Test
    fun `a file that is not a real deck is reported rather than crashing the server`() {
        // A corrupt or mislabelled upload must not take the companion API down with it.
        val s = store()
        val junk = temp.newFile("corrupt.pptx").apply { writeBytes(ByteArray(64) { 0x7A }) }
        s.renderPresentationForServer("corrupt", junk.absolutePath)
        assertNull(s._slideBytes["corrupt"])
    }

    @Test
    fun `an unsupported extension is refused without slides`() {
        val s = store()
        val notADeck = temp.newFile("notes.txt").apply { writeText("just notes") }
        s.renderPresentationForServer("txt", notADeck.absolutePath)
        assertNull(s._slideBytes["txt"])
    }
}
