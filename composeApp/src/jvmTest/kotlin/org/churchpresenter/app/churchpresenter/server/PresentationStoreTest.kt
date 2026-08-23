package org.churchpresenter.app.churchpresenter.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.churchpresenter.settings.utils.Constants

class PresentationStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    companion object {
        private lateinit var tempHome: File
        private var realHome: String? = null

        /**
         * The slide disk cache lives under `user.home`, and the render tests below assert on
         * whether a deck was parsed again — a cache entry left by an earlier run would make a skip
         * indistinguishable from a hit.
         */
        @JvmStatic
        @BeforeClass
        fun isolateHome() {
            TestSingletons.latchToTestHome()
            realHome = System.getProperty("user.home")
            tempHome = Files.createTempDirectory("cp-presentation-store-home").toFile()
            System.setProperty("user.home", tempHome.absolutePath)
        }

        @JvmStatic
        @AfterClass
        fun restoreHome() {
            realHome?.let { System.setProperty("user.home", it) }
            tempHome.deleteRecursively()
        }
    }

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
            broadcasts.any { it.type == Constants.WS_EVENT_PRESENTATION_UPDATED },
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

    /** A one-page PDF — a real deck the loader handles, in a few milliseconds and no fixture file. */
    private fun writeRealDeck(file: File) {
        PDDocument().use { doc ->
            doc.addPage(PDPage())
            doc.save(file)
        }
    }

    @Test
    fun `a deck that fails to load is not parsed again while it is unchanged`() {
        // Nothing lands in _slideBytes when a load fails, so the caller's "have I got this one?"
        // guard is false for ever after: every schedule update would re-open the file through POI
        // and send another warning. The failure is remembered instead.
        val s = store()
        val deck = temp.newFile("bad.pdf").apply { writeText("not a deck at all") }
        s.renderPresentationForServer("bad", deck.absolutePath)

        assertEquals(deck.lastModified(), s._failedPresentationLoads[deck.path])
        assertNull(s._slideBytes["bad"])

        // Repair the file but leave its timestamp where it was: a second render must skip it
        // entirely, which is only observable if the now-valid deck still produces no slides.
        val stamp = deck.lastModified()
        writeRealDeck(deck)
        assertTrue(deck.setLastModified(stamp), "the test needs to control the timestamp")

        s.renderPresentationForServer("bad", deck.absolutePath)
        assertNull(s._slideBytes["bad"], "the remembered failure should have skipped the loader")
    }

    @Test
    fun `a repaired deck is picked up once its timestamp moves`() {
        // The other half of the same rule: remembering a failure must not strand a deck the user
        // has since fixed and re-saved, without restarting the app.
        val s = store()
        val deck = temp.newFile("repaired.pdf").apply { writeText("not a deck at all") }
        s.renderPresentationForServer("repaired", deck.absolutePath)
        assertTrue(s._failedPresentationLoads.containsKey(deck.path))

        writeRealDeck(deck)
        assertTrue(deck.setLastModified(deck.lastModified() + 2_000))

        s.renderPresentationForServer("repaired", deck.absolutePath)
        assertEquals(1, s._slideBytes["repaired"]?.size)
        assertFalse(s._failedPresentationLoads.containsKey(deck.path), "the failure is forgotten once it loads")
    }
}
