@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.happypath

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.churchpresenter.bibletab.bibleFixture
import org.churchpresenter.schedule.SCHEDULE_ROW_CARD_TAG
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.Tabs
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleHappyPathTest : HappyPathHarness() {

    private val photos get() = File(dir, "Photos")

    private fun library(): AppSettings {
        song(1, "Opening Hymn", listOf("Verse 1" to "opening first line"))
        bible("test.spb", bibleFixture)
        photos.mkdirs()
        ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", File(photos, "a.png"))
        return settings(Tabs.SONGS, Tabs.BIBLE, Tabs.PICTURES, Tabs.PRESENTATION, bibleFile = "test.spb")
    }

    /** A song, a Bible verse and a picture folder, in that order. */
    private fun ComposeUiTest.buildService(app: HappyApp) {
        app.actions.addSong(1, "Opening Hymn", "Hymnal", "Hymnal::1")
        app.actions.addBibleVerse("John", 3, 16, "For God so loved the world.", "", 43)
        app.actions.addPicture(photos.absolutePath, "Photos", 1)
        waitForIdle()
    }

    private fun ComposeUiTest.rowTops(): List<Pair<String, Float>> =
        listOf("Opening Hymn", "John 3:16", "Photos").map { label ->
            label to onAllNodes(hasText(label, substring = true) and hasAnyAncestor(hasTestTag(SCHEDULE_ROW_CARD_TAG)))
                .fetchSemanticsNodes().first().boundsInRoot.top
        }

    private fun ComposeUiTest.rowOrder(): List<String> = rowTops().sortedBy { it.second }.map { it.first }

    @Test
    fun `a service is built in order`() = happyPath(library()) { app ->
        buildService(app)

        assertEquals(3, app.schedule().size)
        assertEquals(listOf("Opening Hymn", "John 3:16", "Photos"), rowOrder())
    }

    @Test
    fun `each row goes live in turn`() = happyPath(library()) { app ->
        buildService(app)

        takeRowLive("Opening Hymn")
        await("the song is on air") {
            app.presenter.slideContent.value == Presenting.LYRICS &&
                "opening first line" in app.presenter.displayedLyricSection.value.lines.joinToString()
        }

        takeRowLive("John 3:16")
        await("the verse is on air") {
            app.presenter.slideContent.value == Presenting.BIBLE &&
                app.presenter.selectedVerses.value.singleOrNull()?.verseNumber == 16
        }
        awaitOnOutput(app, "For God so loved the world.")

        takeRowLive("Photos")
        await("the picture is on air") {
            app.presenter.slideContent.value == Presenting.PICTURES &&
                app.presenter.displayedImagePath.value?.endsWith("a.png") == true
        }
    }

    @Test
    fun `a deck row goes live`() = happyPath(library()) { app ->
        val deck = File(dir, "Sermon.pdf")
        PDDocument().use { doc ->
            repeat(2) { doc.addPage(PDPage()) }
            doc.save(deck)
        }
        app.actions.addPresentation(deck.absolutePath, deck.name, 2, "pdf")
        waitForIdle()

        takeRowLive("Sermon.pdf")

        await("the deck is on air", timeoutMs = 15_000) {
            app.presenter.slideContent.value == Presenting.PRESENTATION && app.presenter.displayedSlide.value != null
        }
    }

    @Test
    fun `a row moved up moves, and undo puts it back`() = happyPath(library()) { app ->
        buildService(app)

        clickOnScheduleRow("Photos", "Move Up")

        await("the picture row moved above the verse") { rowOrder() == listOf("Opening Hymn", "Photos", "John 3:16") }

        undo()

        await("the order is back") { rowOrder() == listOf("Opening Hymn", "John 3:16", "Photos") }
    }

    @Test
    fun `a removed row comes back with undo`() = happyPath(library()) { app ->
        buildService(app)

        clickOnScheduleRow("John 3:16", "Remove")

        await("the verse row is gone") { app.schedule().size == 2 }

        undo()

        await("the verse row is back") { app.schedule().size == 3 }
        assertEquals(listOf("Opening Hymn", "John 3:16", "Photos"), rowOrder())
    }

    /** The schedule toolbar's Undo, named with its shortcut ("Undo (⌃Z)"). */
    private fun ComposeUiTest.undo() {
        onAllNodes(hasContentDescription("Undo", substring = true))[0].performClick()
        waitForIdle()
    }
}
