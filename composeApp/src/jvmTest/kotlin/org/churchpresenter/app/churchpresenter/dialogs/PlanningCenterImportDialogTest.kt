package org.churchpresenter.app.churchpresenter.dialogs

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.churchpresenter.app.churchpresenter.BuildConfig
import org.churchpresenter.app.churchpresenter.data.PlanningCenterPrimaryBible
import java.awt.Rectangle
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The app's side of the Planning Center import: the services it hands `:planning-center` and the
 * slide counter inside them. The import itself is `:planning-center`'s own suite.
 *
 * Left uncovered, and deliberately: [PlanningCenterImportDialog] with `isVisible = true`. It opens a
 * `DialogWindow`, a real AWT window that throws `HeadlessException` under the suite's headless JVM,
 * and the song-editor slot is only reachable from inside that window.
 */
class PlanningCenterImportDialogTest {

    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-pco-wrapper").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun deckWithSlides(count: Int): File {
        val file = File(dir, "deck.pptx")
        XMLSlideShow().use { ppt ->
            repeat(count) { index ->
                ppt.createSlide().createTextBox().apply {
                    anchor = Rectangle(50, 50, 500, 120)
                    text = "Slide ${index + 1}"
                }
            }
            file.outputStream().use { ppt.write(it) }
        }
        return file
    }

    @Test
    fun `a deck's slide count is read from its metadata`() {
        assertEquals(3, countDeckSlides(deckWithSlides(3)))
    }

    @Test
    fun `a deck that is not there counts no slides`() {
        assertEquals(0, countDeckSlides(File(dir, "missing.pptx")))
    }

    @Test
    fun `a deck that will not parse counts no slides`() {
        val broken = File(dir, "broken.pptx").apply { writeText("this is not a presentation") }

        assertEquals(0, countDeckSlides(broken))
    }

    @Test
    fun `the services carry this build's OAuth client and count a deck's slides`() {
        val services = appPlanningCenterServices(PlanningCenterPrimaryBible())

        assertEquals(BuildConfig.PLANNING_CENTER_CLIENT_ID, services.clientId)
        assertEquals(BuildConfig.PLANNING_CENTER_CLIENT_SECRET, services.clientSecret)
        assertEquals(2, services.countSlides(deckWithSlides(2)))
    }
}
