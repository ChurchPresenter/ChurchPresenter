@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.happypath

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.slides.data.RecentPresentationFiles
import java.awt.Color
import java.awt.Rectangle
import java.io.File
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** One solid color per slide, so the slide on air can be told by its middle pixel. */
private val SLIDE_COLORS = listOf(Color(220, 30, 30), Color(30, 200, 30), Color(30, 30, 220))

class PresentationHappyPathTest : HappyPathHarness() {

    private fun pdf(): File = File(dir, "Sermon.pdf").also { file ->
        PDDocument().use { doc ->
            SLIDE_COLORS.forEach { color ->
                val page = PDPage(PDRectangle(720f, 405f))
                doc.addPage(page)
                PDPageContentStream(doc, page).use { stream ->
                    stream.setNonStrokingColor(color.red / 255f, color.green / 255f, color.blue / 255f)
                    stream.addRect(0f, 0f, 720f, 405f)
                    stream.fill()
                }
            }
            doc.save(file)
        }
    }

    private fun pptx(): File = File(dir, "Announcements.pptx").also { file ->
        XMLSlideShow().use { ppt ->
            SLIDE_COLORS.forEach { color ->
                val slide = ppt.createSlide()
                slide.createAutoShape().apply {
                    anchor = Rectangle(0, 0, ppt.pageSize.width, ppt.pageSize.height)
                    fillColor = color
                }
            }
            file.outputStream().use { ppt.write(it) }
        }
    }

    private var savedRecent = emptyList<String>()

    // The Recent bar's list is JVM-wide; it is put back as it was.
    @BeforeTest
    fun saveRecentDecks() {
        savedRecent = RecentPresentationFiles.files.toList()
        RecentPresentationFiles.files.clear()
    }

    @AfterTest
    fun restoreRecentDecks() {
        RecentPresentationFiles.files.clear()
        RecentPresentationFiles.files.addAll(savedRecent)
    }

    /** The Presentation tab, with [deck] in its Recent bar. */
    private fun decks(deck: File): AppSettings {
        RecentPresentationFiles.files.add(deck.absolutePath)
        return settings(Tabs.PRESENTATION)
    }

    private fun ComposeUiTest.slideTiles(): Int =
        (1..SLIDE_COLORS.size).count { n ->
            onAllNodes(hasContentDescription("Slide $n"))
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

    /** Opens [deck] from the tab's Recent bar -- the file chooser is a native dialog. */
    private fun ComposeUiTest.openDeck(deck: File) {
        onAllNodes(hasText(deck.name))[0].performMouseInput { doubleClick() }
        waitForIdle()
        await("every slide has a tile", timeoutMs = 15_000) { slideTiles() == SLIDE_COLORS.size }
    }

    private fun ComposeUiTest.clickSlide(n: Int) {
        onAllNodes(hasContentDescription("Slide $n"))[0].performClick()
        waitForIdle()
    }

    private fun isSlide(app: HappyApp, n: Int): Boolean {
        val slide = app.presenter.displayedSlide.value?.toAwtImage() ?: return false
        val pixel = Color(slide.getRGB(slide.width / 2, slide.height / 2))
        val want = SLIDE_COLORS[n - 1]
        return abs(pixel.red - want.red) < 40 && abs(pixel.green - want.green) < 40 && abs(pixel.blue - want.blue) < 40
    }

    private fun ComposeUiTest.awaitSlide(app: HappyApp, n: Int) {
        await("slide $n is on air") {
            app.presenter.slideContent.value == Presenting.PRESENTATION && isSlide(app, n)
        }
    }

    @Test
    fun `a pptx deck shows a tile for every slide`() = happyPath(decks(pptx())) { _ ->
        openDeck(File(dir, "Announcements.pptx"))

        assertEquals(SLIDE_COLORS.size, slideTiles())
    }

    @Test
    fun `a pdf shows a tile for every page`() = happyPath(decks(pdf())) { _ ->
        openDeck(File(dir, "Sermon.pdf"))

        assertEquals(SLIDE_COLORS.size, slideTiles())
    }

    @Test
    fun `a slide goes live`() = happyPath(decks(pdf())) { app ->
        openDeck(File(dir, "Sermon.pdf"))
        clickSlide(2)
        goLive()

        awaitSlide(app, 2)
    }

    @Test
    fun `the live deck steps forward and back`() = happyPath(decks(pptx())) { app ->
        openDeck(File(dir, "Announcements.pptx"))
        clickSlide(1)
        goLive()
        awaitSlide(app, 1)

        clickTabControl("Next Image")
        awaitSlide(app, 2)

        clickTabControl("Previous Image")
        awaitSlide(app, 1)
    }

    @Test
    fun `a deck added to the schedule becomes a row`() = happyPath(decks(pdf())) { app ->
        openDeck(File(dir, "Sermon.pdf"))
        clickTabControl("Add to Schedule")

        await("the schedule holds the deck") { scheduleShows("Sermon (3 slides)") }
        assertEquals("Sermon (3 slides)", app.schedule().single().displayText)
    }
}
