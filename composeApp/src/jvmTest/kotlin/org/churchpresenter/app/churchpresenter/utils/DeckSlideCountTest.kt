package org.churchpresenter.app.churchpresenter.utils

import org.apache.poi.xslf.usermodel.XMLSlideShow
import java.awt.Rectangle
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The slide count the Planning Center import records for a deck it downloads. */
class DeckSlideCountTest {

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
}
